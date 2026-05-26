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

public class DatabaseManager {

    // === thread-safe singleton через volatile + double-checked locking ===
    private static volatile DatabaseManager instance;

    // connection создаётся и используется ТОЛЬКО внутри dbExecutor-потока
    private Connection connection;

    // Однопоточный исполнитель — ВСЕ операции с БД через один поток
    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "focus-db-thread");
        t.setDaemon(true);
        return t;
    });

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

    // ===== Асинхронная обёртка =====
    public <T> CompletableFuture<T> async(Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier, dbExecutor);
    }

    public CompletableFuture<Void> asyncRun(Runnable runnable) {
        return CompletableFuture.runAsync(runnable, dbExecutor);
    }

    // ===== Инициализация =====

    private void ensureDbFileExists() {
        File dbFile = new File(DB_PATH);
        File parentDir = dbFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            boolean created = parentDir.mkdirs();
            if (created) {
                System.out.println("Создана директория для БД: " + parentDir.getAbsolutePath());
            }
        }
        // Удаляем мусорный rollback-journal если он есть
        File journalFile = new File(DB_PATH + "-journal");
        if (journalFile.exists()) {
            boolean deleted = journalFile.delete();
            System.out.println(deleted
                    ? "Удалён устаревший journal-файл: " + journalFile.getAbsolutePath()
                    : "Не удалось удалить journal-файл: " + journalFile.getAbsolutePath());
        }
        if (!dbFile.exists()) {
            System.out.println("Файл БД не найден, будет создан автоматически: " + dbFile.getAbsolutePath());
        } else {
            System.out.println("Файл БД найден: " + dbFile.getAbsolutePath());
        }
    }

    /**
     * initialize() выполняется через dbExecutor, чтобы connection
     * создавался и использовался в одном и том же потоке.
     * Вызов блокирует main-поток до завершения инициализации.
     */
    public void initialize() {
        CompletableFuture<Void> future = asyncRun(() -> {
            try {
                ensureDbFileExists();
                connection = DriverManager.getConnection(DB_URL);
                // WAL — параллельный доступ на чтение
                connection.createStatement().execute("PRAGMA journal_mode=WAL");
                // Ждать до 5 секунд вместо немедленного SQLITE_BUSY
                connection.createStatement().execute("PRAGMA busy_timeout=5000");
                connection.createStatement().execute("PRAGMA foreign_keys=ON");
                // Увеличиваем кеш для производительности
                connection.createStatement().execute("PRAGMA cache_size=-8000");
                createTables();
                System.out.println("База данных готова! Путь: " + new File(DB_PATH).getAbsolutePath());
            } catch (SQLException e) {
                System.out.println("Ошибка инициализации БД: " + e.getMessage());
                e.printStackTrace();
            }
        });

        // Блокируемся в main-потоке пока БД не будет готова
        try {
            future.get();
        } catch (Exception e) {
            System.out.println("Критическая ошибка инициализации БД: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void createTables() throws SQLException {
        connection.createStatement().execute("""
            CREATE TABLE IF NOT EXISTS movies (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                description TEXT,
                poster_path TEXT,
                banner_path TEXT,
                video_path TEXT,
                trailer_path TEXT,
                rating REAL DEFAULT 0,
                year INTEGER,
                duration INTEGER,
                director TEXT,
                country TEXT,
                category TEXT,
                genres TEXT,
                is_now_playing INTEGER DEFAULT 0,
                is_latest INTEGER DEFAULT 0,
                is_top_rated INTEGER DEFAULT 0,
                is_popular INTEGER DEFAULT 0,
                is_kids INTEGER DEFAULT 0,
                is_evening INTEGER DEFAULT 0,
                is_turkish INTEGER DEFAULT 0,
                is_top10 INTEGER DEFAULT 0,
                is_featured INTEGER DEFAULT 0,
                is_kids_featured INTEGER DEFAULT 0,
                is_kids_popular INTEGER DEFAULT 0,
                is_kids_latest INTEGER DEFAULT 0,
                is_kids_recommended INTEGER DEFAULT 0
            )
        """);

        // Миграция новых колонок (если таблица уже существует)
        migrateColumn("is_kids_featured",    "INTEGER DEFAULT 0");
        migrateColumn("is_kids_popular",     "INTEGER DEFAULT 0");
        migrateColumn("is_kids_latest",      "INTEGER DEFAULT 0");
        migrateColumn("is_kids_recommended", "INTEGER DEFAULT 0");

        connection.createStatement().execute("""
            CREATE TABLE IF NOT EXISTS users (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                username TEXT NOT NULL UNIQUE,
                password TEXT NOT NULL,
                email TEXT,
                phone TEXT,
                role TEXT DEFAULT 'USER',
                created_at TEXT DEFAULT (datetime('now')),
                is_banned INTEGER DEFAULT 0
            )
        """);

        migrateUserColumn("phone", "TEXT");

        connection.createStatement().execute("""
            CREATE TABLE IF NOT EXISTS favorites (
                user_id INTEGER,
                movie_id INTEGER,
                PRIMARY KEY (user_id, movie_id)
            )
        """);

        connection.createStatement().execute("""
            CREATE TABLE IF NOT EXISTS watch_history (
                user_id INTEGER,
                movie_id INTEGER,
                watched_at TEXT DEFAULT (datetime('now')),
                PRIMARY KEY (user_id, movie_id)
            )
        """);

        connection.createStatement().execute("""
            CREATE TABLE IF NOT EXISTS action_logs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER,
                username TEXT,
                action TEXT NOT NULL,
                details TEXT,
                created_at TEXT DEFAULT (datetime('now'))
            )
        """);

        createDefaultAdmin();
    }

    private void migrateColumn(String columnName, String definition) {
        try {
            connection.createStatement().execute(
                    "ALTER TABLE movies ADD COLUMN " + columnName + " " + definition
            );
        } catch (SQLException ignored) {}
    }

    private void migrateUserColumn(String columnName, String definition) {
        try {
            connection.createStatement().execute(
                    "ALTER TABLE users ADD COLUMN " + columnName + " " + definition
            );
        } catch (SQLException ignored) {}
    }

    /**
     * Создаёт администратора по умолчанию, если ни одного ADMIN нет.
     * ВЫЗЫВАЕТСЯ ТОЛЬКО внутри dbExecutor (из createTables).
     */
    private void createDefaultAdmin() throws SQLException {
        ResultSet rs = connection.createStatement()
                .executeQuery("SELECT COUNT(*) FROM users WHERE role='ADMIN'");
        if (rs.next() && rs.getInt(1) == 0) {
            connection.createStatement().execute("""
                INSERT INTO users (username, password, email, role)
                VALUES ('admin', 'admin123', 'admin@focus.com', 'ADMIN')
            """);
            System.out.println("Администратор создан! Логин: admin / Пароль: admin123");
        } else {
            System.out.println("Администратор уже существует.");
        }
    }

    // =========================================================================
    // ===== ВАЖНО: все методы, использующие connection, должны вызываться  =====
    // =====        ТОЛЬКО внутри dbExecutor (через async / asyncRun).      =====
    // =====        Синхронные публичные методы оборачиваются в .get().     =====
    // =========================================================================

    // ===== Фильмы =====

    private void addMovieInternal(Movie movie) {
        try {
            PreparedStatement stmt = connection.prepareStatement("""
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
            System.out.println("Добавлен: " + movie.getTitle());
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void updateMovieInternal(Movie movie) {
        try {
            PreparedStatement stmt = connection.prepareStatement("""
                UPDATE movies SET
                    title=?, description=?, poster_path=?, banner_path=?, video_path=?, trailer_path=?,
                    rating=?, year=?, duration=?, director=?, country=?, category=?, genres=?,
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

    private void fillMovieStatement(PreparedStatement stmt, Movie m) throws SQLException {
        stmt.setString(1,  m.getTitle());
        stmt.setString(2,  m.getDescription());
        stmt.setString(3,  m.getPosterPath());
        stmt.setString(4,  m.getBannerPath());
        stmt.setString(5,  m.getVideoPath());
        stmt.setString(6,  m.getTrailerPath());
        stmt.setDouble(7,  m.getRating());
        stmt.setInt(8,     m.getYear());
        stmt.setInt(9,     m.getDuration());
        stmt.setString(10, m.getDirector());
        stmt.setString(11, m.getCountry());
        stmt.setString(12, m.getCategory());
        stmt.setString(13, m.getGenres());
        stmt.setInt(14, m.isNowPlaying()  ? 1 : 0);
        stmt.setInt(15, m.isLatest()      ? 1 : 0);
        stmt.setInt(16, m.isTopRated()    ? 1 : 0);
        stmt.setInt(17, m.isPopular()     ? 1 : 0);
        stmt.setInt(18, m.isKids()        ? 1 : 0);
        stmt.setInt(19, m.isEvening()     ? 1 : 0);
        stmt.setInt(20, m.isTurkish()     ? 1 : 0);
        stmt.setInt(21, m.isTop10()       ? 1 : 0);
        stmt.setInt(22, m.isFeatured()    ? 1 : 0);
        stmt.setInt(23, m.isKidsFeatured()    ? 1 : 0);
        stmt.setInt(24, m.isKidsPopular()     ? 1 : 0);
        stmt.setInt(25, m.isKidsLatest()      ? 1 : 0);
        stmt.setInt(26, m.isKidsRecommended() ? 1 : 0);
    }

    private void deleteMovieInternal(int id) {
        try {
            PreparedStatement delFav = connection.prepareStatement(
                    "DELETE FROM favorites WHERE movie_id=?");
            delFav.setInt(1, id);
            delFav.executeUpdate();

            PreparedStatement delHist = connection.prepareStatement(
                    "DELETE FROM watch_history WHERE movie_id=?");
            delHist.setInt(1, id);
            delHist.executeUpdate();

            PreparedStatement stmt = connection.prepareStatement(
                    "DELETE FROM movies WHERE id=?");
            stmt.setInt(1, id);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // ===== Публичные методы — все выполняются через dbExecutor =====

    public void addMovie(Movie movie) {
        try { asyncRun(() -> addMovieInternal(movie)).get(); }
        catch (Exception e) { e.printStackTrace(); }
    }

    public void updateMovie(Movie movie) {
        try { asyncRun(() -> updateMovieInternal(movie)).get(); }
        catch (Exception e) { e.printStackTrace(); }
    }

    public void deleteMovie(int id) {
        try { asyncRun(() -> deleteMovieInternal(id)).get(); }
        catch (Exception e) { e.printStackTrace(); }
    }

    // ===== Получение контента =====

    private List<Movie> getAllMoviesInternal()    { return getByQuery("SELECT * FROM movies WHERE category='FILM'"); }
    private List<Movie> getAllSeriesInternal()    { return getByQuery("SELECT * FROM movies WHERE category='SERIES'"); }
    private List<Movie> getKidsMoviesInternal()  { return getByQuery("SELECT * FROM movies WHERE category='KIDS' OR is_kids=1"); }
    private List<Movie> getKidsFilmsInternal()   { return getByQuery("SELECT * FROM movies WHERE (category='KIDS' OR is_kids=1) AND category != 'SERIES'"); }
    private List<Movie> getKidsSeriesInternal()  { return getByQuery("SELECT * FROM movies WHERE is_kids=1 AND category='SERIES'"); }
    private List<Movie> getKidsPopularInternal() { return getByQuery("SELECT * FROM movies WHERE (category='KIDS' OR is_kids=1) AND is_kids_popular=1"); }
    private List<Movie> getKidsLatestInternal()  { return getByQuery("SELECT * FROM movies WHERE (category='KIDS' OR is_kids=1) AND is_kids_latest=1"); }
    private List<Movie> getKidsRecommendedInternal() { return getByQuery("SELECT * FROM movies WHERE (category='KIDS' OR is_kids=1) AND is_kids_recommended=1"); }

    private Movie getKidsFeaturedInternal() {
        List<Movie> list = getByQuery("""
            SELECT * FROM movies
            WHERE (category='KIDS' OR is_kids=1) AND is_kids_featured=1
            ORDER BY RANDOM() LIMIT 1
        """);
        if (!list.isEmpty()) return list.get(0);
        List<Movie> fallback = getKidsMoviesInternal();
        return fallback.isEmpty() ? null : fallback.get(0);
    }

    private Movie getFeaturedMovieInternal() {
        List<Movie> list = getByQuery("""
            SELECT * FROM movies WHERE is_featured=1 ORDER BY RANDOM() LIMIT 1
        """);
        return list.isEmpty() ? null : list.get(0);
    }

    private Movie getMovieByIdInternal(int id) {
        try {
            PreparedStatement stmt = connection.prepareStatement("SELECT * FROM movies WHERE id=?");
            stmt.setInt(1, id);
            List<Movie> result = mapResultSet(stmt.executeQuery());
            return result.isEmpty() ? null : result.get(0);
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    private List<Movie> searchInternal(String query) {
        try {
            PreparedStatement stmt = connection.prepareStatement("""
                SELECT * FROM movies
                WHERE title LIKE ? OR description LIKE ? OR director LIKE ? OR genres LIKE ?
                ORDER BY rating DESC
            """);
            String q = "%" + query + "%";
            stmt.setString(1, q); stmt.setString(2, q);
            stmt.setString(3, q); stmt.setString(4, q);
            return mapResultSet(stmt.executeQuery());
        } catch (SQLException e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    // Синхронные публичные обёртки (блокируют вызывающий поток)
    public List<Movie> getAllMovies()        { try { return async(this::getAllMoviesInternal).get();    } catch (Exception e) { e.printStackTrace(); return new ArrayList<>(); } }
    public List<Movie> getAllSeries()        { try { return async(this::getAllSeriesInternal).get();    } catch (Exception e) { e.printStackTrace(); return new ArrayList<>(); } }
    public List<Movie> getKidsMovies()      { try { return async(this::getKidsMoviesInternal).get();  } catch (Exception e) { e.printStackTrace(); return new ArrayList<>(); } }
    public List<Movie> getKidsFilms()       { try { return async(this::getKidsFilmsInternal).get();   } catch (Exception e) { e.printStackTrace(); return new ArrayList<>(); } }
    public List<Movie> getKidsSeries()      { try { return async(this::getKidsSeriesInternal).get();  } catch (Exception e) { e.printStackTrace(); return new ArrayList<>(); } }
    public List<Movie> getKidsPopular()     { try { return async(this::getKidsPopularInternal).get(); } catch (Exception e) { e.printStackTrace(); return new ArrayList<>(); } }
    public List<Movie> getKidsLatest()      { try { return async(this::getKidsLatestInternal).get();  } catch (Exception e) { e.printStackTrace(); return new ArrayList<>(); } }
    public List<Movie> getKidsRecommended() { try { return async(this::getKidsRecommendedInternal).get(); } catch (Exception e) { e.printStackTrace(); return new ArrayList<>(); } }
    public Movie getKidsFeatured()          { try { return async(this::getKidsFeaturedInternal).get();    } catch (Exception e) { e.printStackTrace(); return null; } }
    public Movie getFeaturedMovie()         { try { return async(this::getFeaturedMovieInternal).get();   } catch (Exception e) { e.printStackTrace(); return null; } }
    public Movie getMovieById(int id)       { try { return async(() -> getMovieByIdInternal(id)).get();   } catch (Exception e) { e.printStackTrace(); return null; } }
    public List<Movie> search(String q)     { try { return async(() -> searchInternal(q)).get();          } catch (Exception e) { e.printStackTrace(); return new ArrayList<>(); } }

    public List<Movie> getNowPlaying() { try { return async(() -> getByFlag("is_now_playing")).get(); } catch (Exception e) { e.printStackTrace(); return new ArrayList<>(); } }
    public List<Movie> getLatest()     { try { return async(() -> getByFlag("is_latest")).get();      } catch (Exception e) { e.printStackTrace(); return new ArrayList<>(); } }
    public List<Movie> getTopRated()   { try { return async(() -> getByFlag("is_top_rated")).get();   } catch (Exception e) { e.printStackTrace(); return new ArrayList<>(); } }
    public List<Movie> getPopular()    { try { return async(() -> getByFlag("is_popular")).get();     } catch (Exception e) { e.printStackTrace(); return new ArrayList<>(); } }
    public List<Movie> getEvening()    { try { return async(() -> getByFlag("is_evening")).get();     } catch (Exception e) { e.printStackTrace(); return new ArrayList<>(); } }
    public List<Movie> getTurkish()    { try { return async(() -> getByFlag("is_turkish")).get();     } catch (Exception e) { e.printStackTrace(); return new ArrayList<>(); } }
    public List<Movie> getTop10()      { try { return async(() -> getByQuery("SELECT * FROM movies WHERE is_top10=1 ORDER BY rating DESC LIMIT 10")).get(); } catch (Exception e) { e.printStackTrace(); return new ArrayList<>(); } }

    // Асинхронные версии (для контроллеров, которые уже используют thenAccept / Platform.runLater)
    public CompletableFuture<List<Movie>> getAllMoviesAsync()        { return async(this::getAllMoviesInternal); }
    public CompletableFuture<List<Movie>> getAllSeriesAsync()        { return async(this::getAllSeriesInternal); }
    public CompletableFuture<List<Movie>> getKidsMoviesAsync()      { return async(this::getKidsMoviesInternal); }
    public CompletableFuture<List<Movie>> getKidsFilmsAsync()       { return async(this::getKidsFilmsInternal); }
    public CompletableFuture<List<Movie>> getKidsSeriesAsync()      { return async(this::getKidsSeriesInternal); }
    public CompletableFuture<List<Movie>> getKidsPopularAsync()     { return async(this::getKidsPopularInternal); }
    public CompletableFuture<List<Movie>> getKidsLatestAsync()      { return async(this::getKidsLatestInternal); }
    public CompletableFuture<List<Movie>> getKidsRecommendedAsync() { return async(this::getKidsRecommendedInternal); }
    public CompletableFuture<Movie>       getKidsFeaturedAsync()    { return async(this::getKidsFeaturedInternal); }
    public CompletableFuture<Movie>       getFeaturedMovieAsync()   { return async(this::getFeaturedMovieInternal); }
    public CompletableFuture<List<Movie>> getNowPlayingAsync()      { return async(() -> getByFlag("is_now_playing")); }
    public CompletableFuture<List<Movie>> getLatestAsync()          { return async(() -> getByFlag("is_latest")); }
    public CompletableFuture<List<Movie>> getTopRatedAsync()        { return async(() -> getByFlag("is_top_rated")); }
    public CompletableFuture<List<Movie>> getPopularAsync()         { return async(() -> getByFlag("is_popular")); }
    public CompletableFuture<List<Movie>> getEveningAsync()         { return async(() -> getByFlag("is_evening")); }
    public CompletableFuture<List<Movie>> getTurkishAsync()         { return async(() -> getByFlag("is_turkish")); }
    public CompletableFuture<List<Movie>> getTop10Async()           { return async(() -> getByQuery("SELECT * FROM movies WHERE is_top10=1 ORDER BY rating DESC LIMIT 10")); }
    public CompletableFuture<List<Movie>> searchAsync(String q)     { return async(() -> searchInternal(q)); }
    public CompletableFuture<Void>        addMovieAsync(Movie m)    { return asyncRun(() -> addMovieInternal(m)); }
    public CompletableFuture<Void>        updateMovieAsync(Movie m) { return asyncRun(() -> updateMovieInternal(m)); }
    public CompletableFuture<Void>        deleteMovieAsync(int id)  { return asyncRun(() -> deleteMovieInternal(id)); }

    // ===== Пользователи =====

    private boolean registerUserInternal(String username, String password, String email, String phone) {
        try {
            PreparedStatement stmt = connection.prepareStatement("""
                INSERT INTO users (username, password, email, phone) VALUES (?,?,?,?)
            """);
            stmt.setString(1, username);
            stmt.setString(2, password);
            stmt.setString(3, email);
            stmt.setString(4, phone);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    public boolean registerUser(String username, String password, String email, String phone) {
        try {
            return async(() -> registerUserInternal(username, password, email, phone)).get();
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean registerUser(String username, String password, String email) {
        return registerUser(username, password, email, null);
    }

    /**
     * ИСПРАВЛЕНИЕ ОСНОВНОЙ ОШИБКИ:
     * Этот метод теперь выполняется ВНУТРИ dbExecutor через async().get(),
     * поэтому connection гарантированно создан и таблицы существуют.
     */
    private User loginUserByIdentifierInternal(String identifier, String password) {
        try {
            PreparedStatement stmt = connection.prepareStatement("""
                SELECT * FROM users
                WHERE (username=? OR email=? OR phone=?) AND password=? AND is_banned=0
            """);
            stmt.setString(1, identifier);
            stmt.setString(2, identifier);
            stmt.setString(3, identifier);
            stmt.setString(4, password);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return mapUser(rs);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public User loginUserByIdentifier(String identifier, String password) {
        try {
            return async(() -> loginUserByIdentifierInternal(identifier, password)).get();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public User loginUser(String username, String password) {
        return loginUserByIdentifier(username, password);
    }

    private boolean isUserBannedByIdentifierInternal(String identifier) {
        try {
            PreparedStatement stmt = connection.prepareStatement("""
                SELECT is_banned FROM users WHERE username=? OR email=? OR phone=?
            """);
            stmt.setString(1, identifier);
            stmt.setString(2, identifier);
            stmt.setString(3, identifier);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt("is_banned") == 1;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean isUserBannedByIdentifier(String identifier) {
        try {
            return async(() -> isUserBannedByIdentifierInternal(identifier)).get();
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean isUserBanned(String username) {
        return isUserBannedByIdentifier(username);
    }

    private boolean updateUserInternal(int id, String username, String email, String newPassword) {
        try {
            if (newPassword != null && !newPassword.isEmpty()) {
                PreparedStatement stmt = connection.prepareStatement("""
                    UPDATE users SET username=?, email=?, password=? WHERE id=?
                """);
                stmt.setString(1, username);
                stmt.setString(2, email);
                stmt.setString(3, newPassword);
                stmt.setInt(4, id);
                stmt.executeUpdate();
            } else {
                PreparedStatement stmt = connection.prepareStatement("""
                    UPDATE users SET username=?, email=? WHERE id=?
                """);
                stmt.setString(1, username);
                stmt.setString(2, email);
                stmt.setInt(3, id);
                stmt.executeUpdate();
            }
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean updateUser(int id, String username, String email, String newPassword) {
        try {
            return async(() -> updateUserInternal(id, username, email, newPassword)).get();
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private List<User> getAllUsersInternal() {
        List<User> users = new ArrayList<>();
        try {
            ResultSet rs = connection.createStatement()
                    .executeQuery("SELECT * FROM users ORDER BY created_at DESC");
            while (rs.next()) users.add(mapUser(rs));
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return users;
    }

    public List<User> getAllUsers() {
        try {
            return async(this::getAllUsersInternal).get();
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    private void setBanUserInternal(int userId, boolean banned) {
        try {
            PreparedStatement stmt = connection.prepareStatement(
                    "UPDATE users SET is_banned=? WHERE id=?");
            stmt.setInt(1, banned ? 1 : 0);
            stmt.setInt(2, userId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void setBanUser(int userId, boolean banned) {
        try { asyncRun(() -> setBanUserInternal(userId, banned)).get(); }
        catch (Exception e) { e.printStackTrace(); }
    }

    private void deleteUserInternal(int userId) {
        try {
            PreparedStatement stmt = connection.prepareStatement(
                    "DELETE FROM users WHERE id=? AND role!='ADMIN'");
            stmt.setInt(1, userId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void deleteUser(int userId) {
        try { asyncRun(() -> deleteUserInternal(userId)).get(); }
        catch (Exception e) { e.printStackTrace(); }
    }

    private void setUserRoleInternal(int userId, String role) {
        try {
            PreparedStatement stmt = connection.prepareStatement(
                    "UPDATE users SET role=? WHERE id=?");
            stmt.setString(1, role);
            stmt.setInt(2, userId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void setUserRole(int userId, String role) {
        try { asyncRun(() -> setUserRoleInternal(userId, role)).get(); }
        catch (Exception e) { e.printStackTrace(); }
    }

    // ===== Избранное =====

    private void addToFavoritesInternal(int userId, int movieId) {
        try {
            PreparedStatement stmt = connection.prepareStatement("""
                INSERT OR IGNORE INTO favorites (user_id, movie_id) VALUES (?,?)
            """);
            stmt.setInt(1, userId);
            stmt.setInt(2, movieId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void addToFavorites(int userId, int movieId) {
        try { asyncRun(() -> addToFavoritesInternal(userId, movieId)).get(); }
        catch (Exception e) { e.printStackTrace(); }
    }

    private void removeFromFavoritesInternal(int userId, int movieId) {
        try {
            PreparedStatement stmt = connection.prepareStatement("""
                DELETE FROM favorites WHERE user_id=? AND movie_id=?
            """);
            stmt.setInt(1, userId);
            stmt.setInt(2, movieId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void removeFromFavorites(int userId, int movieId) {
        try { asyncRun(() -> removeFromFavoritesInternal(userId, movieId)).get(); }
        catch (Exception e) { e.printStackTrace(); }
    }

    private List<Movie> getFavoritesInternal(int userId) {
        try {
            PreparedStatement stmt = connection.prepareStatement("""
                SELECT m.* FROM movies m
                JOIN favorites f ON m.id=f.movie_id
                WHERE f.user_id=?
                ORDER BY m.title
            """);
            stmt.setInt(1, userId);
            return mapResultSet(stmt.executeQuery());
        } catch (SQLException e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    public List<Movie> getFavorites(int userId) {
        try {
            return async(() -> getFavoritesInternal(userId)).get();
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    private boolean isFavoriteInternal(int userId, int movieId) {
        try {
            PreparedStatement stmt = connection.prepareStatement("""
                SELECT COUNT(*) FROM favorites WHERE user_id=? AND movie_id=?
            """);
            stmt.setInt(1, userId);
            stmt.setInt(2, movieId);
            ResultSet rs = stmt.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean isFavorite(int userId, int movieId) {
        try {
            return async(() -> isFavoriteInternal(userId, movieId)).get();
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // ===== История =====

    private void addToHistoryInternal(int userId, int movieId) {
        try {
            PreparedStatement stmt = connection.prepareStatement("""
                INSERT OR REPLACE INTO watch_history (user_id, movie_id, watched_at)
                VALUES (?,?, datetime('now'))
            """);
            stmt.setInt(1, userId);
            stmt.setInt(2, movieId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void addToHistory(int userId, int movieId) {
        try { asyncRun(() -> addToHistoryInternal(userId, movieId)).get(); }
        catch (Exception e) { e.printStackTrace(); }
    }

    private List<Movie> getWatchHistoryInternal(int userId) {
        try {
            PreparedStatement stmt = connection.prepareStatement("""
                SELECT m.* FROM movies m
                JOIN watch_history h ON m.id=h.movie_id
                WHERE h.user_id=?
                ORDER BY h.watched_at DESC
            """);
            stmt.setInt(1, userId);
            return mapResultSet(stmt.executeQuery());
        } catch (SQLException e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    public List<Movie> getWatchHistory(int userId) {
        try {
            return async(() -> getWatchHistoryInternal(userId)).get();
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    // ===== Логи =====

    private void logActionInternal(int userId, String username, String action, String details) {
        try {
            PreparedStatement stmt = connection.prepareStatement("""
                INSERT INTO action_logs (user_id, username, action, details) VALUES (?,?,?,?)
            """);
            stmt.setInt(1, userId);
            stmt.setString(2, username);
            stmt.setString(3, action);
            stmt.setString(4, details);
            stmt.executeUpdate();
            LogManager.getInstance().log(username + " | " + action + " | " + details);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void logAction(int userId, String username, String action, String details) {
        try { asyncRun(() -> logActionInternal(userId, username, action, details)).get(); }
        catch (Exception e) { e.printStackTrace(); }
    }

    private List<ActionLog> getAllLogsInternal() {
        List<ActionLog> logs = new ArrayList<>();
        try {
            ResultSet rs = connection.createStatement().executeQuery("""
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
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return logs;
    }

    public List<ActionLog> getAllLogs() {
        try {
            return async(this::getAllLogsInternal).get();
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    // ===== Вспомогательные =====

    /**
     * ВНИМАНИЕ: getByFlag и getByQuery должны вызываться ТОЛЬКО изнутри dbExecutor!
     * (т.е. только из *Internal методов или из async-лямбд)
     */
    private List<Movie> getByFlag(String flag) {
        return getByQuery("SELECT * FROM movies WHERE " + flag + "=1");
    }

    private List<Movie> getByQuery(String query) {
        try {
            ResultSet rs = connection.createStatement().executeQuery(query);
            return mapResultSet(rs);
        } catch (SQLException e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    private List<Movie> mapResultSet(ResultSet rs) throws SQLException {
        List<Movie> movies = new ArrayList<>();
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
            m.setLatest(rs.getInt("is_latest") == 1);
            m.setTopRated(rs.getInt("is_top_rated") == 1);
            m.setPopular(rs.getInt("is_popular") == 1);
            m.setKids(rs.getInt("is_kids") == 1);
            m.setEvening(rs.getInt("is_evening") == 1);
            m.setTurkish(rs.getInt("is_turkish") == 1);
            m.setTop10(rs.getInt("is_top10") == 1);
            m.setFeatured(rs.getInt("is_featured") == 1);
            try { m.setKidsFeatured(rs.getInt("is_kids_featured") == 1); }    catch (Exception ignored) {}
            try { m.setKidsPopular(rs.getInt("is_kids_popular") == 1); }      catch (Exception ignored) {}
            try { m.setKidsLatest(rs.getInt("is_kids_latest") == 1); }        catch (Exception ignored) {}
            try { m.setKidsRecommended(rs.getInt("is_kids_recommended") == 1);} catch (Exception ignored) {}
            movies.add(m);
        }
        return movies;
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
