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
    private Connection connection;

    // Пул потоков для асинхронных операций с БД
    private final ExecutorService dbExecutor = Executors.newFixedThreadPool(4);

    // Путь к файлу базы данных
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

    /**
     * Гарантирует существование файла БД и всех родительских директорий.
     * Если файла нет — SQLite сам его создаст при подключении,
     * но директория должна существовать.
     */
    private void ensureDbFileExists() {
        File dbFile = new File(DB_PATH);
        File parentDir = dbFile.getParentFile();

        // Если путь содержит подпапки — создаём их
        if (parentDir != null && !parentDir.exists()) {
            boolean created = parentDir.mkdirs();
            if (created) {
                System.out.println("📁 Создана директория для БД: " + parentDir.getAbsolutePath());
            }
        }

        if (!dbFile.exists()) {
            System.out.println("📄 Файл БД не найден, будет создан автоматически: "
                    + dbFile.getAbsolutePath());
        } else {
            System.out.println("✅ Файл БД найден: " + dbFile.getAbsolutePath());
        }
    }

    public void initialize() {
        try {
            // ШАГ 1: Убеждаемся что директория существует (файл создаст SQLite сам)
            ensureDbFileExists();

            // ШАГ 2: Подключаемся (SQLite создаёт файл если его нет)
            connection = DriverManager.getConnection(DB_URL);

            // WAL-режим: улучшает параллельный доступ к SQLite
            connection.createStatement().execute("PRAGMA journal_mode=WAL");
            connection.createStatement().execute("PRAGMA foreign_keys=ON");

            // ШАГ 3: Создаём таблицы если нужно
            createTables();

            System.out.println("✅ База данных готова!");
        } catch (SQLException e) {
            System.out.println("❌ Ошибка инициализации БД: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void createTables() throws SQLException {
        // Таблица фильмов/сериалов
        connection.createStatement().execute("""
            CREATE TABLE IF NOT EXISTS movies (
                id          INTEGER PRIMARY KEY AUTOINCREMENT,
                title       TEXT NOT NULL,
                description TEXT,
                poster_path TEXT,
                banner_path TEXT,
                video_path  TEXT,
                trailer_path TEXT,
                rating      REAL DEFAULT 0,
                year        INTEGER,
                duration    INTEGER,
                director    TEXT,
                country     TEXT,
                category    TEXT,
                genres      TEXT,
                is_now_playing      INTEGER DEFAULT 0,
                is_latest           INTEGER DEFAULT 0,
                is_top_rated        INTEGER DEFAULT 0,
                is_popular          INTEGER DEFAULT 0,
                is_kids             INTEGER DEFAULT 0,
                is_evening          INTEGER DEFAULT 0,
                is_turkish          INTEGER DEFAULT 0,
                is_top10            INTEGER DEFAULT 0,
                is_featured         INTEGER DEFAULT 0,
                is_kids_featured    INTEGER DEFAULT 0,
                is_kids_popular     INTEGER DEFAULT 0,
                is_kids_latest      INTEGER DEFAULT 0
            )
        """);

        // Безопасная миграция новых колонок (если таблица уже существует без них)
        migrateColumn("is_kids_featured", "INTEGER DEFAULT 0");
        migrateColumn("is_kids_popular",  "INTEGER DEFAULT 0");
        migrateColumn("is_kids_latest",   "INTEGER DEFAULT 0");

        // Таблица пользователей
        connection.createStatement().execute("""
            CREATE TABLE IF NOT EXISTS users (
                id         INTEGER PRIMARY KEY AUTOINCREMENT,
                username   TEXT NOT NULL UNIQUE,
                password   TEXT NOT NULL,
                email      TEXT,
                phone      TEXT,
                role       TEXT DEFAULT 'USER',
                created_at TEXT DEFAULT (datetime('now')),
                is_banned  INTEGER DEFAULT 0
            )
        """);

        // Безопасная миграция поля phone
        migrateUserColumn("phone", "TEXT");

        connection.createStatement().execute("""
            CREATE TABLE IF NOT EXISTS favorites (
                user_id  INTEGER,
                movie_id INTEGER,
                PRIMARY KEY (user_id, movie_id)
            )
        """);

        connection.createStatement().execute("""
            CREATE TABLE IF NOT EXISTS watch_history (
                user_id    INTEGER,
                movie_id   INTEGER,
                watched_at TEXT DEFAULT (datetime('now')),
                PRIMARY KEY (user_id, movie_id)
            )
        """);

        connection.createStatement().execute("""
            CREATE TABLE IF NOT EXISTS action_logs (
                id         INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id    INTEGER,
                username   TEXT,
                action     TEXT NOT NULL,
                details    TEXT,
                created_at TEXT DEFAULT (datetime('now'))
            )
        """);

        createDefaultAdmin();
    }

    /** Безопасное добавление колонки в таблицу movies (если уже есть — игнорируем) */
    private void migrateColumn(String columnName, String definition) {
        try {
            connection.createStatement().execute(
                    "ALTER TABLE movies ADD COLUMN " + columnName + " " + definition
            );
        } catch (SQLException ignored) {}
    }

    /** Безопасное добавление колонки в таблицу users */
    private void migrateUserColumn(String columnName, String definition) {
        try {
            connection.createStatement().execute(
                    "ALTER TABLE users ADD COLUMN " + columnName + " " + definition
            );
        } catch (SQLException ignored) {}
    }

    private void createDefaultAdmin() throws SQLException {
        ResultSet rs = connection.createStatement()
                .executeQuery("SELECT COUNT(*) FROM users WHERE role='ADMIN'");
        if (rs.getInt(1) == 0) {
            connection.createStatement().execute("""
                INSERT INTO users (username, password, email, role)
                VALUES ('admin', 'admin123', 'admin@focus.com', 'ADMIN')
            """);
            System.out.println("✅ Администратор создан!");
            System.out.println("   Логин:  admin");
            System.out.println("   Пароль: admin123");
        }
    }

    // ===== Фильмы (синхронные) =====

    public void addMovie(Movie movie) {
        try {
            PreparedStatement stmt = connection.prepareStatement("""
                INSERT INTO movies (
                    title, description, poster_path, banner_path,
                    video_path, trailer_path, rating, year, duration,
                    director, country, category, genres,
                    is_now_playing, is_latest, is_top_rated, is_popular,
                    is_kids, is_evening, is_turkish, is_top10, is_featured,
                    is_kids_featured, is_kids_popular, is_kids_latest
                ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """);
            fillMovieStatement(stmt, movie);
            stmt.executeUpdate();
            System.out.println("✅ Добавлен: " + movie.getTitle());
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void updateMovie(Movie movie) {
        try {
            PreparedStatement stmt = connection.prepareStatement("""
                UPDATE movies SET
                    title=?, description=?, poster_path=?, banner_path=?,
                    video_path=?, trailer_path=?, rating=?, year=?, duration=?,
                    director=?, country=?, category=?, genres=?,
                    is_now_playing=?, is_latest=?, is_top_rated=?, is_popular=?,
                    is_kids=?, is_evening=?, is_turkish=?, is_top10=?, is_featured=?,
                    is_kids_featured=?, is_kids_popular=?, is_kids_latest=?
                WHERE id=?
            """);
            fillMovieStatement(stmt, movie);
            stmt.setInt(26, movie.getId());
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
        stmt.setInt(23, m.isKidsFeatured()? 1 : 0);
        stmt.setInt(24, m.isKidsPopular() ? 1 : 0);
        stmt.setInt(25, m.isKidsLatest()  ? 1 : 0);
    }

    public void deleteMovie(int id) {
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

    // ===== Получение контента =====

    public List<Movie> getAllMovies()  { return getByQuery("SELECT * FROM movies WHERE category='FILM'"); }
    public List<Movie> getAllSeries()  { return getByQuery("SELECT * FROM movies WHERE category='SERIES'"); }

    public List<Movie> getKidsMovies() {
        return getByQuery("SELECT * FROM movies WHERE category='KIDS' OR is_kids=1");
    }

    public List<Movie> getKidsFilms() {
        return getByQuery(
                "SELECT * FROM movies WHERE (category='KIDS' OR is_kids=1) AND category != 'SERIES'"
        );
    }

    public List<Movie> getKidsSeries() {
        return getByQuery(
                "SELECT * FROM movies WHERE is_kids=1 AND category='SERIES'"
        );
    }

    public List<Movie> getKidsPopular() {
        return getByQuery(
                "SELECT * FROM movies WHERE (category='KIDS' OR is_kids=1) AND is_kids_popular=1"
        );
    }

    public List<Movie> getKidsLatest() {
        return getByQuery(
                "SELECT * FROM movies WHERE (category='KIDS' OR is_kids=1) AND is_kids_latest=1"
        );
    }

    public Movie getKidsFeatured() {
        List<Movie> list = getByQuery("""
            SELECT * FROM movies
            WHERE (category='KIDS' OR is_kids=1) AND is_kids_featured=1
            ORDER BY RANDOM() LIMIT 1
        """);
        if (!list.isEmpty()) return list.get(0);
        List<Movie> fallback = getKidsMovies();
        return fallback.isEmpty() ? null : fallback.get(0);
    }

    public Movie getFeaturedMovie() {
        List<Movie> list = getByQuery("""
            SELECT * FROM movies WHERE is_featured=1 ORDER BY RANDOM() LIMIT 1
        """);
        return list.isEmpty() ? null : list.get(0);
    }

    public List<Movie> getNowPlaying() { return getByFlag("is_now_playing"); }
    public List<Movie> getLatest()     { return getByFlag("is_latest"); }
    public List<Movie> getTopRated()   { return getByFlag("is_top_rated"); }
    public List<Movie> getPopular()    { return getByFlag("is_popular"); }
    public List<Movie> getEvening()    { return getByFlag("is_evening"); }
    public List<Movie> getTurkish()    { return getByFlag("is_turkish"); }
    public List<Movie> getTop10()      {
        return getByQuery("SELECT * FROM movies WHERE is_top10=1 ORDER BY rating DESC LIMIT 10");
    }

    public List<Movie> search(String query) {
        try {
            PreparedStatement stmt = connection.prepareStatement("""
                SELECT * FROM movies
                WHERE title LIKE ? OR description LIKE ? OR director LIKE ? OR genres LIKE ?
                ORDER BY rating DESC
            """);
            String q = "%" + query + "%";
            stmt.setString(1, q);
            stmt.setString(2, q);
            stmt.setString(3, q);
            stmt.setString(4, q);
            return mapResultSet(stmt.executeQuery());
        } catch (SQLException e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    public Movie getMovieById(int id) {
        try {
            PreparedStatement stmt = connection.prepareStatement(
                    "SELECT * FROM movies WHERE id=?");
            stmt.setInt(1, id);
            List<Movie> result = mapResultSet(stmt.executeQuery());
            return result.isEmpty() ? null : result.get(0);
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    // ===== Асинхронные версии =====

    public CompletableFuture<List<Movie>> getAllMoviesAsync()    { return async(this::getAllMovies); }
    public CompletableFuture<List<Movie>> getAllSeriesAsync()    { return async(this::getAllSeries); }
    public CompletableFuture<List<Movie>> getKidsMoviesAsync()  { return async(this::getKidsMovies); }
    public CompletableFuture<List<Movie>> getKidsFilmsAsync()   { return async(this::getKidsFilms); }
    public CompletableFuture<List<Movie>> getKidsSeriesAsync()  { return async(this::getKidsSeries); }
    public CompletableFuture<List<Movie>> getKidsPopularAsync() { return async(this::getKidsPopular); }
    public CompletableFuture<List<Movie>> getKidsLatestAsync()  { return async(this::getKidsLatest); }
    public CompletableFuture<Movie>       getKidsFeaturedAsync(){ return async(this::getKidsFeatured); }
    public CompletableFuture<Movie>       getFeaturedMovieAsync(){ return async(this::getFeaturedMovie); }
    public CompletableFuture<List<Movie>> getNowPlayingAsync()  { return async(this::getNowPlaying); }
    public CompletableFuture<List<Movie>> getLatestAsync()      { return async(this::getLatest); }
    public CompletableFuture<List<Movie>> getTopRatedAsync()    { return async(this::getTopRated); }
    public CompletableFuture<List<Movie>> getPopularAsync()     { return async(this::getPopular); }
    public CompletableFuture<List<Movie>> getEveningAsync()     { return async(this::getEvening); }
    public CompletableFuture<List<Movie>> getTurkishAsync()     { return async(this::getTurkish); }
    public CompletableFuture<List<Movie>> getTop10Async()       { return async(this::getTop10); }
    public CompletableFuture<List<Movie>> searchAsync(String q) { return async(() -> search(q)); }

    public CompletableFuture<Void> addMovieAsync(Movie movie)    { return asyncRun(() -> addMovie(movie)); }
    public CompletableFuture<Void> updateMovieAsync(Movie movie) { return asyncRun(() -> updateMovie(movie)); }
    public CompletableFuture<Void> deleteMovieAsync(int id)      { return asyncRun(() -> deleteMovie(id)); }

    // ===== Пользователи =====

    public boolean registerUser(String username, String password, String email, String phone) {
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

    public boolean registerUser(String username, String password, String email) {
        return registerUser(username, password, email, null);
    }

    public User loginUserByIdentifier(String identifier, String password) {
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

    /** Алиас для обратной совместимости */
    public User loginUser(String username, String password) {
        return loginUserByIdentifier(username, password);
    }

    public boolean isUserBannedByIdentifier(String identifier) {
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

    /** Алиас для обратной совместимости */
    public boolean isUserBanned(String username) {
        return isUserBannedByIdentifier(username);
    }

    public boolean updateUser(int id, String username, String email, String newPassword) {
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

    public List<User> getAllUsers() {
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

    public void setBanUser(int userId, boolean banned) {
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

    public void deleteUser(int userId) {
        try {
            PreparedStatement stmt = connection.prepareStatement(
                    "DELETE FROM users WHERE id=? AND role!='ADMIN'");
            stmt.setInt(1, userId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void setUserRole(int userId, String role) {
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

    // ===== Избранное =====

    public void addToFavorites(int userId, int movieId) {
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

    public void removeFromFavorites(int userId, int movieId) {
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

    public List<Movie> getFavorites(int userId) {
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

    public boolean isFavorite(int userId, int movieId) {
        try {
            PreparedStatement stmt = connection.prepareStatement("""
                SELECT COUNT(*) FROM favorites WHERE user_id=? AND movie_id=?
            """);
            stmt.setInt(1, userId);
            stmt.setInt(2, movieId);
            ResultSet rs = stmt.executeQuery();
            return rs.getInt(1) > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    // ===== История =====

    public void addToHistory(int userId, int movieId) {
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

    public List<Movie> getWatchHistory(int userId) {
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

    // ===== Логи =====

    public void logAction(int userId, String username, String action, String details) {
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

    public List<ActionLog> getAllLogs() {
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

    // ===== Вспомогательные =====

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
            // Новые поля — с защитой от старых БД без этих колонок
            try { m.setKidsFeatured(rs.getInt("is_kids_featured") == 1); } catch (Exception ignored) {}
            try { m.setKidsPopular(rs.getInt("is_kids_popular")   == 1); } catch (Exception ignored) {}
            try { m.setKidsLatest(rs.getInt("is_kids_latest")     == 1); } catch (Exception ignored) {}
            movies.add(m);
        }
        return movies;
    }

    private User mapUser(ResultSet rs) throws SQLException {
        User u = new User();
        u.setId(rs.getInt("id"));
        u.setUsername(rs.getString("username"));
        u.setEmail(rs.getString("email"));
        try { u.setPhone(rs.getString("phone")); } catch (Exception ignored) {}
        u.setRole(rs.getString("role"));
        u.setCreatedAt(rs.getString("created_at"));
        u.setBanned(rs.getInt("is_banned") == 1);
        return u;
    }
}
