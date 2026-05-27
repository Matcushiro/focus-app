package com.focus.database;

import com.focus.model.ActionLog;
import com.focus.model.Movie;
import com.focus.model.User;
import com.focus.service.LogManager;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * DatabaseManager — потокобезопасный менеджер SQLite.
 *
 * АРХИТЕКТУРА ИСПРАВЛЕНИЯ:
 * ---------------------------------------------------------
 * Исходная проблема: connection создавался в dbExecutor-потоке,
 * но не был volatile, из-за чего другие потоки могли видеть null.
 * Кроме того, initialize() могло гонять с логином при старте.
 *
 * Решение: один единственный private Connection, защищённый
 * synchronized-блоками. Все операции с БД идут через
 * getConnection() который гарантирует инициализацию.
 * Асинхронные *Async() методы используют отдельный executor
 * только для того, чтобы не блокировать FX-поток.
 * ---------------------------------------------------------
 */
public class DatabaseManager {

    // ── Singleton ──────────────────────────────────────────────────────────
    private static volatile DatabaseManager instance;

    // ── Соединение с БД (доступ только через getConnection()) ─────────────
    private volatile Connection connection;
    private volatile boolean    initialized = false;

    // ── Executor только для async-обёрток (не хранит connection!) ─────────
    private final ExecutorService asyncExecutor = Executors.newFixedThreadPool(
            2,
            r -> {
                Thread t = new Thread(r, "focus-db-async");
                t.setDaemon(true);
                return t;
            }
    );

    private static final String DB_PATH = "focus.db";
    private static final String DB_URL  = "jdbc:sqlite:" + DB_PATH;

    private DatabaseManager() {}

    public static DatabaseManager getInstance() {
        if (instance == null) {
            synchronized (DatabaseManager.class) {
                if (instance == null) {
                    instance = new DatabaseManager();
                }
            }
        }
        return instance;
    }

    // ── Получение соединения (потокобезопасно) ─────────────────────────────
    private synchronized Connection getConnection() throws SQLException {
        if (!initialized || connection == null || connection.isClosed()) {
            initializeInternal();
        }
        return connection;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ИНИЦИАЛИЗАЦИЯ
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Публичный метод инициализации — вызывается из Main.start().
     * Синхронизирован, безопасен для любого потока.
     */
    public synchronized void initialize() {
        if (initialized) return;
        initializeInternal();
    }

    private void initializeInternal() {
        try {
            prepareDbFile();

            // Открываем соединение
            connection = DriverManager.getConnection(DB_URL);

            // Настройки SQLite
            try (Statement st = connection.createStatement()) {
                st.execute("PRAGMA journal_mode=WAL");
                st.execute("PRAGMA busy_timeout=5000");
                st.execute("PRAGMA foreign_keys=ON");
                st.execute("PRAGMA cache_size=-8000");
            }

            // Создаём таблицы и дефолтного администратора
            createTables();

            initialized = true;
            System.out.println("[DB] База данных готова: " + new File(DB_PATH).getAbsolutePath());

        } catch (SQLException e) {
            System.err.println("[DB] КРИТИЧЕСКАЯ ОШИБКА инициализации: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void prepareDbFile() {
        File dbFile = new File(DB_PATH);

        // Удаляем мусорный journal-файл если есть
        File journal = new File(DB_PATH + "-journal");
        if (journal.exists()) {
            if (journal.delete()) {
                System.out.println("[DB] Удалён устаревший journal-файл");
            }
        }

        if (!dbFile.exists()) {
            System.out.println("[DB] Файл БД не найден — будет создан: " + dbFile.getAbsolutePath());
        } else {
            System.out.println("[DB] Файл БД найден: " + dbFile.getAbsolutePath());
        }
    }

    private void createTables() throws SQLException {
        try (Statement st = connection.createStatement()) {

            // ── movies ────────────────────────────────────────────────────
            st.execute("""
                CREATE TABLE IF NOT EXISTS movies (
                    id               INTEGER PRIMARY KEY AUTOINCREMENT,
                    title            TEXT    NOT NULL,
                    description      TEXT,
                    poster_path      TEXT,
                    banner_path      TEXT,
                    video_path       TEXT,
                    trailer_path     TEXT,
                    rating           REAL    DEFAULT 0,
                    year             INTEGER,
                    duration         INTEGER,
                    director         TEXT,
                    country          TEXT,
                    category         TEXT,
                    genres           TEXT,
                    is_now_playing   INTEGER DEFAULT 0,
                    is_latest        INTEGER DEFAULT 0,
                    is_top_rated     INTEGER DEFAULT 0,
                    is_popular       INTEGER DEFAULT 0,
                    is_kids          INTEGER DEFAULT 0,
                    is_evening       INTEGER DEFAULT 0,
                    is_turkish       INTEGER DEFAULT 0,
                    is_top10         INTEGER DEFAULT 0,
                    is_featured      INTEGER DEFAULT 0,
                    is_kids_featured    INTEGER DEFAULT 0,
                    is_kids_popular     INTEGER DEFAULT 0,
                    is_kids_latest      INTEGER DEFAULT 0,
                    is_kids_recommended INTEGER DEFAULT 0
                )
            """);

            // ── users ─────────────────────────────────────────────────────
            st.execute("""
                CREATE TABLE IF NOT EXISTS users (
                    id         INTEGER PRIMARY KEY AUTOINCREMENT,
                    username   TEXT NOT NULL UNIQUE,
                    password   TEXT NOT NULL,
                    email      TEXT,
                    phone      TEXT,
                    role       TEXT    DEFAULT 'USER',
                    created_at TEXT    DEFAULT (datetime('now')),
                    is_banned  INTEGER DEFAULT 0
                )
            """);

            // ── favorites ─────────────────────────────────────────────────
            st.execute("""
                CREATE TABLE IF NOT EXISTS favorites (
                    user_id  INTEGER,
                    movie_id INTEGER,
                    PRIMARY KEY (user_id, movie_id)
                )
            """);

            // ── watch_history ─────────────────────────────────────────────
            st.execute("""
                CREATE TABLE IF NOT EXISTS watch_history (
                    user_id    INTEGER,
                    movie_id   INTEGER,
                    watched_at TEXT DEFAULT (datetime('now')),
                    PRIMARY KEY (user_id, movie_id)
                )
            """);

            // ── action_logs ───────────────────────────────────────────────
            st.execute("""
                CREATE TABLE IF NOT EXISTS action_logs (
                    id         INTEGER PRIMARY KEY AUTOINCREMENT,
                    user_id    INTEGER,
                    username   TEXT,
                    action     TEXT NOT NULL,
                    details    TEXT,
                    created_at TEXT DEFAULT (datetime('now'))
                )
            """);
        }

        // Миграции (безопасны — игнорируем ошибку если колонка уже есть)
        migrateColumn("movies", "is_kids_featured",    "INTEGER DEFAULT 0");
        migrateColumn("movies", "is_kids_popular",     "INTEGER DEFAULT 0");
        migrateColumn("movies", "is_kids_latest",      "INTEGER DEFAULT 0");
        migrateColumn("movies", "is_kids_recommended", "INTEGER DEFAULT 0");
        migrateColumn("users",  "phone",               "TEXT");

        // Создаём администратора по умолчанию
        createDefaultAdmin();
    }

    private void migrateColumn(String table, String column, String definition) {
        try (Statement st = connection.createStatement()) {
            st.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        } catch (SQLException ignored) {
            // Колонка уже существует — норма
        }
    }

    private void createDefaultAdmin() throws SQLException {
        try (Statement st = connection.createStatement()) {
            ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM users WHERE role='ADMIN'");
            if (rs.next() && rs.getInt(1) == 0) {
                connection.createStatement().execute("""
                    INSERT INTO users (username, password, email, role)
                    VALUES ('admin', 'admin123', 'admin@focus.com', 'ADMIN')
                """);
                System.out.println("[DB] Администратор создан! Логин: admin / Пароль: admin123");
            } else {
                System.out.println("[DB] Администратор уже существует.");
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ASYNC HELPERS
    // ═══════════════════════════════════════════════════════════════════════

    /** Запускает задачу в фоновом потоке, не блокируя FX-поток. */
    public <T> CompletableFuture<T> async(Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier, asyncExecutor);
    }

    public CompletableFuture<Void> asyncRun(Runnable runnable) {
        return CompletableFuture.runAsync(runnable, asyncExecutor);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ФИЛЬМЫ
    // ═══════════════════════════════════════════════════════════════════════

    public synchronized void addMovie(Movie movie) {
        try {
            PreparedStatement stmt = getConnection().prepareStatement("""
                INSERT INTO movies (
                    title, description, poster_path, banner_path, video_path, trailer_path,
                    rating, year, duration, director, country, category, genres,
                    is_now_playing, is_latest, is_top_rated, is_popular,
                    is_kids, is_evening, is_turkish, is_top10, is_featured,
                    is_kids_featured, is_kids_popular, is_kids_latest, is_kids_recommended
                ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """);
            fillMovieStatement(stmt, movie);
            stmt.executeUpdate();
            System.out.println("[DB] Фильм добавлен: " + movie.getTitle());
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public synchronized void updateMovie(Movie movie) {
        try {
            PreparedStatement stmt = getConnection().prepareStatement("""
                UPDATE movies SET
                    title=?, description=?, poster_path=?, banner_path=?,
                    video_path=?, trailer_path=?, rating=?, year=?, duration=?,
                    director=?, country=?, category=?, genres=?,
                    is_now_playing=?, is_latest=?, is_top_rated=?, is_popular=?,
                    is_kids=?, is_evening=?, is_turkish=?, is_top10=?, is_featured=?,
                    is_kids_featured=?, is_kids_popular=?, is_kids_latest=?, is_kids_recommended=?
                WHERE id=?
            """);
            fillMovieStatement(stmt, movie);
            stmt.setInt(27, movie.getId());
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public synchronized void deleteMovie(int id) {
        try {
            Connection c = getConnection();
            try (PreparedStatement s1 = c.prepareStatement("DELETE FROM favorites    WHERE movie_id=?");
                 PreparedStatement s2 = c.prepareStatement("DELETE FROM watch_history WHERE movie_id=?");
                 PreparedStatement s3 = c.prepareStatement("DELETE FROM movies        WHERE id=?")) {
                s1.setInt(1, id); s1.executeUpdate();
                s2.setInt(1, id); s2.executeUpdate();
                s3.setInt(1, id); s3.executeUpdate();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void fillMovieStatement(PreparedStatement s, Movie m) throws SQLException {
        s.setString(1,  m.getTitle());
        s.setString(2,  m.getDescription());
        s.setString(3,  m.getPosterPath());
        s.setString(4,  m.getBannerPath());
        s.setString(5,  m.getVideoPath());
        s.setString(6,  m.getTrailerPath());
        s.setDouble(7,  m.getRating());
        s.setInt(8,     m.getYear());
        s.setInt(9,     m.getDuration());
        s.setString(10, m.getDirector());
        s.setString(11, m.getCountry());
        s.setString(12, m.getCategory());
        s.setString(13, m.getGenres());
        s.setInt(14, m.isNowPlaying()      ? 1 : 0);
        s.setInt(15, m.isLatest()          ? 1 : 0);
        s.setInt(16, m.isTopRated()        ? 1 : 0);
        s.setInt(17, m.isPopular()         ? 1 : 0);
        s.setInt(18, m.isKids()            ? 1 : 0);
        s.setInt(19, m.isEvening()         ? 1 : 0);
        s.setInt(20, m.isTurkish()         ? 1 : 0);
        s.setInt(21, m.isTop10()           ? 1 : 0);
        s.setInt(22, m.isFeatured()        ? 1 : 0);
        s.setInt(23, m.isKidsFeatured()    ? 1 : 0);
        s.setInt(24, m.isKidsPopular()     ? 1 : 0);
        s.setInt(25, m.isKidsLatest()      ? 1 : 0);
        s.setInt(26, m.isKidsRecommended() ? 1 : 0);
    }

    // ── Геттеры фильмов ────────────────────────────────────────────────────

    public synchronized List<Movie> getAllMovies()        { return query("SELECT * FROM movies WHERE category='FILM'"); }
    public synchronized List<Movie> getAllSeries()        { return query("SELECT * FROM movies WHERE category='SERIES'"); }
    public synchronized List<Movie> getKidsMovies()       { return query("SELECT * FROM movies WHERE category='KIDS' OR is_kids=1"); }
    public synchronized List<Movie> getKidsFilms()        { return query("SELECT * FROM movies WHERE (category='KIDS' OR is_kids=1) AND category!='SERIES'"); }
    public synchronized List<Movie> getKidsSeries()       { return query("SELECT * FROM movies WHERE is_kids=1 AND category='SERIES'"); }
    public synchronized List<Movie> getKidsPopular()      { return query("SELECT * FROM movies WHERE (category='KIDS' OR is_kids=1) AND is_kids_popular=1"); }
    public synchronized List<Movie> getKidsLatest()       { return query("SELECT * FROM movies WHERE (category='KIDS' OR is_kids=1) AND is_kids_latest=1"); }
    public synchronized List<Movie> getKidsRecommended()  { return query("SELECT * FROM movies WHERE (category='KIDS' OR is_kids=1) AND is_kids_recommended=1"); }
    public synchronized List<Movie> getNowPlaying()       { return query("SELECT * FROM movies WHERE is_now_playing=1"); }
    public synchronized List<Movie> getLatest()           { return query("SELECT * FROM movies WHERE is_latest=1"); }
    public synchronized List<Movie> getTopRated()         { return query("SELECT * FROM movies WHERE is_top_rated=1"); }
    public synchronized List<Movie> getPopular()          { return query("SELECT * FROM movies WHERE is_popular=1"); }
    public synchronized List<Movie> getEvening()          { return query("SELECT * FROM movies WHERE is_evening=1"); }
    public synchronized List<Movie> getTurkish()          { return query("SELECT * FROM movies WHERE is_turkish=1"); }
    public synchronized List<Movie> getTop10()            { return query("SELECT * FROM movies WHERE is_top10=1 ORDER BY rating DESC LIMIT 10"); }

    public synchronized Movie getKidsFeatured() {
        List<Movie> list = query("SELECT * FROM movies WHERE (category='KIDS' OR is_kids=1) AND is_kids_featured=1 ORDER BY RANDOM() LIMIT 1");
        if (!list.isEmpty()) return list.get(0);
        List<Movie> fb = getKidsMovies();
        return fb.isEmpty() ? null : fb.get(0);
    }

    public synchronized Movie getFeaturedMovie() {
        List<Movie> list = query("SELECT * FROM movies WHERE is_featured=1 ORDER BY RANDOM() LIMIT 1");
        return list.isEmpty() ? null : list.get(0);
    }

    public synchronized Movie getMovieById(int id) {
        try {
            PreparedStatement s = getConnection().prepareStatement("SELECT * FROM movies WHERE id=?");
            s.setInt(1, id);
            List<Movie> r = mapResultSet(s.executeQuery());
            return r.isEmpty() ? null : r.get(0);
        } catch (SQLException e) { e.printStackTrace(); return null; }
    }

    public synchronized List<Movie> search(String keyword) {
        try {
            PreparedStatement s = getConnection().prepareStatement("""
                SELECT * FROM movies
                WHERE title LIKE ? OR description LIKE ? OR director LIKE ? OR genres LIKE ?
                ORDER BY rating DESC
            """);
            String q = "%" + keyword + "%";
            s.setString(1, q); s.setString(2, q); s.setString(3, q); s.setString(4, q);
            return mapResultSet(s.executeQuery());
        } catch (SQLException e) { e.printStackTrace(); return new ArrayList<>(); }
    }

    // ── Async версии ────────────────────────────────────────────────────────

    public CompletableFuture<List<Movie>> getAllMoviesAsync()       { return async(this::getAllMovies); }
    public CompletableFuture<List<Movie>> getAllSeriesAsync()       { return async(this::getAllSeries); }
    public CompletableFuture<List<Movie>> getKidsMoviesAsync()     { return async(this::getKidsMovies); }
    public CompletableFuture<List<Movie>> getKidsFilmsAsync()      { return async(this::getKidsFilms); }
    public CompletableFuture<List<Movie>> getKidsSeriesAsync()     { return async(this::getKidsSeries); }
    public CompletableFuture<List<Movie>> getKidsPopularAsync()    { return async(this::getKidsPopular); }
    public CompletableFuture<List<Movie>> getKidsLatestAsync()     { return async(this::getKidsLatest); }
    public CompletableFuture<List<Movie>> getKidsRecommendedAsync(){ return async(this::getKidsRecommended); }
    public CompletableFuture<Movie>       getKidsFeaturedAsync()   { return async(this::getKidsFeatured); }
    public CompletableFuture<Movie>       getFeaturedMovieAsync()  { return async(this::getFeaturedMovie); }
    public CompletableFuture<List<Movie>> getNowPlayingAsync()     { return async(this::getNowPlaying); }
    public CompletableFuture<List<Movie>> getLatestAsync()         { return async(this::getLatest); }
    public CompletableFuture<List<Movie>> getTopRatedAsync()       { return async(this::getTopRated); }
    public CompletableFuture<List<Movie>> getPopularAsync()        { return async(this::getPopular); }
    public CompletableFuture<List<Movie>> getEveningAsync()        { return async(this::getEvening); }
    public CompletableFuture<List<Movie>> getTurkishAsync()        { return async(this::getTurkish); }
    public CompletableFuture<List<Movie>> getTop10Async()          { return async(this::getTop10); }
    public CompletableFuture<List<Movie>> searchAsync(String q)    { return async(() -> search(q)); }
    public CompletableFuture<Void>        addMovieAsync(Movie m)   { return asyncRun(() -> addMovie(m)); }
    public CompletableFuture<Void>        updateMovieAsync(Movie m){ return asyncRun(() -> updateMovie(m)); }
    public CompletableFuture<Void>        deleteMovieAsync(int id) { return asyncRun(() -> deleteMovie(id)); }

    // ═══════════════════════════════════════════════════════════════════════
    // ПОЛЬЗОВАТЕЛИ
    // ═══════════════════════════════════════════════════════════════════════

    public synchronized boolean registerUser(String username, String password, String email, String phone) {
        try {
            PreparedStatement s = getConnection().prepareStatement("""
                INSERT INTO users (username, password, email, phone) VALUES (?,?,?,?)
            """);
            s.setString(1, username);
            s.setString(2, password);
            s.setString(3, email);
            s.setString(4, phone);
            s.executeUpdate();
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    public boolean registerUser(String username, String password, String email) {
        return registerUser(username, password, email, null);
    }

    /**
     * ГЛАВНОЕ ИСПРАВЛЕНИЕ:
     * Метод синхронизирован и использует getConnection(), который
     * гарантирует, что БД инициализирована и таблица users существует
     * вне зависимости от того, из какого потока вызывается метод.
     */
    public synchronized User loginUserByIdentifier(String identifier, String password) {
        try {
            PreparedStatement s = getConnection().prepareStatement("""
                SELECT * FROM users
                WHERE (username=? OR email=? OR phone=?) AND password=? AND is_banned=0
            """);
            s.setString(1, identifier);
            s.setString(2, identifier);
            s.setString(3, identifier);
            s.setString(4, password);
            ResultSet rs = s.executeQuery();
            if (rs.next()) return mapUser(rs);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public User loginUser(String username, String password) {
        return loginUserByIdentifier(username, password);
    }

    public synchronized boolean isUserBannedByIdentifier(String identifier) {
        try {
            PreparedStatement s = getConnection().prepareStatement("""
                SELECT is_banned FROM users WHERE username=? OR email=? OR phone=?
            """);
            s.setString(1, identifier);
            s.setString(2, identifier);
            s.setString(3, identifier);
            ResultSet rs = s.executeQuery();
            if (rs.next()) return rs.getInt("is_banned") == 1;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean isUserBanned(String username) {
        return isUserBannedByIdentifier(username);
    }

    public synchronized boolean updateUser(int id, String username, String email, String newPassword) {
        try {
            if (newPassword != null && !newPassword.isEmpty()) {
                PreparedStatement s = getConnection().prepareStatement("""
                    UPDATE users SET username=?, email=?, password=? WHERE id=?
                """);
                s.setString(1, username); s.setString(2, email);
                s.setString(3, newPassword); s.setInt(4, id);
                s.executeUpdate();
            } else {
                PreparedStatement s = getConnection().prepareStatement("""
                    UPDATE users SET username=?, email=? WHERE id=?
                """);
                s.setString(1, username); s.setString(2, email); s.setInt(3, id);
                s.executeUpdate();
            }
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public synchronized List<User> getAllUsers() {
        List<User> users = new ArrayList<>();
        try {
            ResultSet rs = getConnection().createStatement()
                    .executeQuery("SELECT * FROM users ORDER BY created_at DESC");
            while (rs.next()) users.add(mapUser(rs));
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return users;
    }

    public synchronized void setBanUser(int userId, boolean banned) {
        try {
            PreparedStatement s = getConnection().prepareStatement(
                    "UPDATE users SET is_banned=? WHERE id=?");
            s.setInt(1, banned ? 1 : 0);
            s.setInt(2, userId);
            s.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public synchronized void deleteUser(int userId) {
        try {
            PreparedStatement s = getConnection().prepareStatement(
                    "DELETE FROM users WHERE id=? AND role!='ADMIN'");
            s.setInt(1, userId);
            s.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public synchronized void setUserRole(int userId, String role) {
        try {
            PreparedStatement s = getConnection().prepareStatement(
                    "UPDATE users SET role=? WHERE id=?");
            s.setString(1, role);
            s.setInt(2, userId);
            s.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ИЗБРАННОЕ
    // ═══════════════════════════════════════════════════════════════════════

    public synchronized void addToFavorites(int userId, int movieId) {
        try {
            PreparedStatement s = getConnection().prepareStatement(
                    "INSERT OR IGNORE INTO favorites (user_id, movie_id) VALUES (?,?)");
            s.setInt(1, userId); s.setInt(2, movieId);
            s.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public synchronized void removeFromFavorites(int userId, int movieId) {
        try {
            PreparedStatement s = getConnection().prepareStatement(
                    "DELETE FROM favorites WHERE user_id=? AND movie_id=?");
            s.setInt(1, userId); s.setInt(2, movieId);
            s.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public synchronized List<Movie> getFavorites(int userId) {
        try {
            PreparedStatement s = getConnection().prepareStatement("""
                SELECT m.* FROM movies m
                JOIN favorites f ON m.id=f.movie_id
                WHERE f.user_id=?
                ORDER BY m.title
            """);
            s.setInt(1, userId);
            return mapResultSet(s.executeQuery());
        } catch (SQLException e) { e.printStackTrace(); return new ArrayList<>(); }
    }

    public synchronized boolean isFavorite(int userId, int movieId) {
        try {
            PreparedStatement s = getConnection().prepareStatement(
                    "SELECT COUNT(*) FROM favorites WHERE user_id=? AND movie_id=?");
            s.setInt(1, userId); s.setInt(2, movieId);
            ResultSet rs = s.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ИСТОРИЯ ПРОСМОТРОВ
    // ═══════════════════════════════════════════════════════════════════════

    public synchronized void addToHistory(int userId, int movieId) {
        try {
            PreparedStatement s = getConnection().prepareStatement("""
                INSERT OR REPLACE INTO watch_history (user_id, movie_id, watched_at)
                VALUES (?,?, datetime('now'))
            """);
            s.setInt(1, userId); s.setInt(2, movieId);
            s.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public synchronized List<Movie> getWatchHistory(int userId) {
        try {
            PreparedStatement s = getConnection().prepareStatement("""
                SELECT m.* FROM movies m
                JOIN watch_history h ON m.id=h.movie_id
                WHERE h.user_id=?
                ORDER BY h.watched_at DESC
            """);
            s.setInt(1, userId);
            return mapResultSet(s.executeQuery());
        } catch (SQLException e) { e.printStackTrace(); return new ArrayList<>(); }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ЛОГИ ДЕЙСТВИЙ
    // ═══════════════════════════════════════════════════════════════════════

    public synchronized void logAction(int userId, String username, String action, String details) {
        try {
            PreparedStatement s = getConnection().prepareStatement("""
                INSERT INTO action_logs (user_id, username, action, details) VALUES (?,?,?,?)
            """);
            s.setInt(1, userId);
            s.setString(2, username);
            s.setString(3, action);
            s.setString(4, details);
            s.executeUpdate();
            LogManager.getInstance().log(username + " | " + action + " | " + details);
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public synchronized List<ActionLog> getAllLogs() {
        List<ActionLog> logs = new ArrayList<>();
        try {
            ResultSet rs = getConnection().createStatement().executeQuery("""
                SELECT * FROM action_logs ORDER BY created_at DESC LIMIT 200
            """);
            while (rs.next()) {
                logs.add(new ActionLog(
                        rs.getInt("id"),
                        rs.getString("username"),
                        rs.getString("action"),
                        rs.getString("details"),
                        rs.getString("created_at")
                ));
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return logs;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ВСПОМОГАТЕЛЬНЫЕ
    // ═══════════════════════════════════════════════════════════════════════

    /** Выполняет SELECT-запрос. Вызывать только из synchronized-методов! */
    private List<Movie> query(String sql) {
        try {
            return mapResultSet(getConnection().createStatement().executeQuery(sql));
        } catch (SQLException e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    private List<Movie> mapResultSet(ResultSet rs) throws SQLException {
        List<Movie> list = new ArrayList<>();
        while (rs.next()) {
            Movie m = new Movie();
            m.setId(rs.getInt("id"));
            m.setTitle(rs.getString("title"));
            m.setDescription(rs.getString("description"));
            m.setPosterPath(rs.getString("poster_path"));
            m.setBannerPath(rs.getString("banner_path"));
            m.setVideoPath(rs.getString("video_path"));
            m.setTrailerPath(rs.getString("trailer_path"));
            m.setRating(rs.getDouble("rating"));
            m.setYear(rs.getInt("year"));
            m.setDuration(rs.getInt("duration"));
            m.setDirector(rs.getString("director"));
            m.setCountry(rs.getString("country"));
            m.setCategory(rs.getString("category"));
            m.setGenres(rs.getString("genres"));
            m.setNowPlaying(rs.getInt("is_now_playing") == 1);
            m.setLatest(rs.getInt("is_latest")          == 1);
            m.setTopRated(rs.getInt("is_top_rated")     == 1);
            m.setPopular(rs.getInt("is_popular")        == 1);
            m.setKids(rs.getInt("is_kids")              == 1);
            m.setEvening(rs.getInt("is_evening")        == 1);
            m.setTurkish(rs.getInt("is_turkish")        == 1);
            m.setTop10(rs.getInt("is_top10")            == 1);
            m.setFeatured(rs.getInt("is_featured")      == 1);
            try { m.setKidsFeatured(rs.getInt("is_kids_featured")       == 1); } catch (Exception ignored) {}
            try { m.setKidsPopular(rs.getInt("is_kids_popular")         == 1); } catch (Exception ignored) {}
            try { m.setKidsLatest(rs.getInt("is_kids_latest")           == 1); } catch (Exception ignored) {}
            try { m.setKidsRecommended(rs.getInt("is_kids_recommended") == 1); } catch (Exception ignored) {}
            list.add(m);
        }
        return list;
    }

    private User mapUser(ResultSet rs) throws SQLException {
        User u = new User();
        u.setId(rs.getInt("id"));
        u.setUsername(rs.getString("username"));
        u.setPassword(rs.getString("password"));
        u.setEmail(rs.getString("email"));
        try { u.setPhone(rs.getString("phone")); } catch (Exception ignored) {}
        u.setRole(rs.getString("role"));
        u.setCreatedAt(rs.getString("created_at"));
        u.setBanned(rs.getInt("is_banned") == 1);
        return u;
    }
}
