package com.focus.controller;

import com.focus.database.DatabaseManager;
import com.focus.model.Movie;
import com.focus.service.SessionManager;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.FlowPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.net.URL;
import java.util.*;

/**
 * AdminController — управление контентом (добавление/редактирование/удаление фильмов и сериалов).
 * Все операции с БД выполняются асинхронно.
 */
public class AdminController implements Initializable {

    // ===== Основные поля =====
    @FXML private TextField titleField;
    @FXML private TextArea descField;
    @FXML private TextField posterField;
    @FXML private TextField bannerField;
    @FXML private TextField videoField;
    @FXML private TextField ratingField;
    @FXML private TextField yearField;
    @FXML private TextField durationField;
    @FXML private TextField directorField;
    @FXML private TextField countryField;

    // Категория — FILM / SERIES
    @FXML private ComboBox<String> categoryBox;

    // Жанры — мультивыбор через FlowPane с ToggleButton
    @FXML private FlowPane genresPane;
    private final Set<String> selectedGenres = new LinkedHashSet<>();
    private final Map<String, ToggleButton> genreToggleMap = new LinkedHashMap<>();

    // ===== Флаги секций (главные) =====
    @FXML private CheckBox nowPlayingCheck;
    @FXML private CheckBox latestCheck;
    @FXML private CheckBox topRatedCheck;
    @FXML private CheckBox popularCheck;
    @FXML private CheckBox eveningCheck;
    @FXML private CheckBox turkishCheck;
    @FXML private CheckBox top10Check;
    @FXML private CheckBox featuredCheck;

    // ===== Детский контент =====
    @FXML private CheckBox kidsCheck;           // is_kids — пометить как детское
    @FXML private CheckBox isKidsContentCheck;  // Категория KIDS
    @FXML private CheckBox kidsFeaturedCheck;   // Баннер в детской вкладке
    @FXML private CheckBox kidsPopularCheck;    // Популярное среди детских
    @FXML private CheckBox kidsLatestCheck;     // Новинки детских

    // ===== Список фильмов/сериалов =====
    @FXML private ListView<String> moviesList;
    @FXML private Label statusLabel;

    private static final List<String> ALL_GENRES = Arrays.asList(
            "Драма", "Комедия", "Триллер", "Боевик", "Фантастика",
            "Ужасы", "Мелодрама", "Анимация", "Документальный",
            "Криминал", "Приключения", "Биография", "Исторический",
            "Мюзикл", "Вестерн", "Семейный", "Романтика", "Сказка"
    );

    private final DatabaseManager db = DatabaseManager.getInstance();
    private List<Movie> allMovies = new ArrayList<>();
    private Movie editingMovie = null;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        if (categoryBox != null) {
            categoryBox.setItems(FXCollections.observableArrayList("FILM", "SERIES", "KIDS"));
            categoryBox.setValue("FILM");
            // При смене категории — обновляем видимость детских флагов
            categoryBox.valueProperty().addListener((obs, o, n) -> updateKidsFieldsVisibility(n));
        }
        buildGenresPanel();
        loadMoviesListAsync();
    }

    // ===== Панель жанров =====

    private void buildGenresPanel() {
        if (genresPane == null) return;
        genresPane.getChildren().clear();
        genreToggleMap.clear();
        for (String genre : ALL_GENRES) {
            ToggleButton tb = new ToggleButton(genre);
            tb.setStyle(genreStyle(false));
            tb.selectedProperty().addListener((obs, old, sel) -> {
                tb.setStyle(genreStyle(sel));
                if (sel) selectedGenres.add(genre);
                else selectedGenres.remove(genre);
            });
            genreToggleMap.put(genre, tb);
            genresPane.getChildren().add(tb);
        }
    }

    private String genreStyle(boolean selected) {
        return selected
                ? "-fx-background-color: #E65C00; -fx-text-fill: #ffffff; -fx-border-color: #E65C00; -fx-background-radius: 14; -fx-border-radius: 14; -fx-padding: 4 12; -fx-cursor: hand; -fx-font-size: 11px;"
                : "-fx-background-color: #2a2a2a; -fx-text-fill: #cccccc; -fx-border-color: #555; -fx-background-radius: 14; -fx-border-radius: 14; -fx-padding: 4 12; -fx-cursor: hand; -fx-font-size: 11px;";
    }

    private void updateKidsFieldsVisibility(String category) {
        boolean isKids = "KIDS".equals(category);
        if (isKidsContentCheck != null) {
            isKidsContentCheck.setSelected(isKids);
            isKidsContentCheck.setDisable(isKids);
        }
        setKidsCheckboxesVisible(isKids);
    }

    private void setKidsCheckboxesVisible(boolean visible) {
        if (kidsFeaturedCheck != null) {
            kidsFeaturedCheck.setVisible(true); // всегда видны, просто активны при kids
        }
    }

    // ===== Загрузка списка =====

    private void loadMoviesListAsync() {
        db.async(() -> {
            List<Movie> films = db.getAllMovies();
            List<Movie> series = db.getAllSeries();
            List<Movie> kids = db.getKidsMovies();
            List<Movie> all = new ArrayList<>();
            all.addAll(films);
            all.addAll(series);
            // kids может пересекаться — добавляем только уникальные по id
            Set<Integer> ids = new HashSet<>();
            films.forEach(m -> ids.add(m.getId()));
            series.forEach(m -> ids.add(m.getId()));
            for (Movie k : kids) {
                if (!ids.contains(k.getId())) all.add(k);
            }
            return all;
        }).thenAccept(list -> Platform.runLater(() -> {
            allMovies = list;
            ObservableList<String> items = FXCollections.observableArrayList();
            for (Movie m : allMovies) {
                String typeIcon = switch (m.getCategory() != null ? m.getCategory() : "") {
                    case "FILM" -> "🎬";
                    case "SERIES" -> "📺";
                    case "KIDS" -> "👶";
                    default -> "🎬";
                };
                String kidsIcon = m.isKids() ? " 👶" : "";
                items.add(typeIcon + " " + m.getId() + " | " + m.getTitle() + " (" + m.getYear() + ")" + kidsIcon);
            }
            if (moviesList != null) moviesList.setItems(items);
        })).exceptionally(e -> { e.printStackTrace(); return null; });
    }

    // ===== Выбор файлов =====

    @FXML
    private void selectPoster() {
        File file = openFileChooser("Выберите постер",
                new FileChooser.ExtensionFilter("Изображения", "*.jpg", "*.png", "*.jpeg", "*.webp"));
        if (file != null && posterField != null) posterField.setText(file.getAbsolutePath());
    }

    @FXML
    private void selectBanner() {
        File file = openFileChooser("Выберите баннер",
                new FileChooser.ExtensionFilter("Изображения", "*.jpg", "*.png", "*.jpeg", "*.webp"));
        if (file != null && bannerField != null) bannerField.setText(file.getAbsolutePath());
    }

    @FXML
    private void selectVideo() {
        File file = openFileChooser("Выберите видео",
                new FileChooser.ExtensionFilter("Видео", "*.mp4", "*.mkv", "*.avi"));
        if (file != null && videoField != null) videoField.setText(file.getAbsolutePath());
    }

    private File openFileChooser(String title, FileChooser.ExtensionFilter filter) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters().add(filter);
        return chooser.showOpenDialog(new Stage());
    }

    // ===== Сохранение =====

    @FXML
    private void saveMovie() {
        if (titleField == null || titleField.getText().isBlank()) {
            showAlert("Введите название!");
            return;
        }
        if (selectedGenres.isEmpty()) {
            showAlert("Выберите хотя бы один жанр!");
            return;
        }

        Movie movie = buildMovieFromForm();

        setStatus("⏳ Сохранение...");
        if (editingMovie == null) {
            db.addMovieAsync(movie).thenRun(() -> Platform.runLater(() -> {
                logAction("ADD_" + movie.getCategory(), "Добавлено: " + movie.getTitle());
                showAlert("✅ Сохранено: " + movie.getTitle());
                clearForm();
                loadMoviesListAsync();
                setStatus("✅ Готово");
            })).exceptionally(e -> { e.printStackTrace(); Platform.runLater(() -> showAlert("❌ Ошибка: " + e.getMessage())); return null; });
        } else {
            movie.setId(editingMovie.getId());
            db.updateMovieAsync(movie).thenRun(() -> Platform.runLater(() -> {
                logAction("UPDATE_" + movie.getCategory(), "Обновлено: " + movie.getTitle());
                showAlert("✅ Обновлено: " + movie.getTitle());
                editingMovie = null;
                clearForm();
                loadMoviesListAsync();
                setStatus("✅ Готово");
            })).exceptionally(e -> { e.printStackTrace(); Platform.runLater(() -> showAlert("❌ Ошибка: " + e.getMessage())); return null; });
        }
    }

    private Movie buildMovieFromForm() {
        Movie movie = new Movie();
        movie.setTitle(titleField != null ? titleField.getText().trim() : "");
        movie.setDescription(descField != null ? descField.getText() : "");
        movie.setPosterPath(posterField != null ? posterField.getText() : "");
        movie.setBannerPath(bannerField != null ? bannerField.getText() : "");
        movie.setVideoPath(videoField != null ? videoField.getText() : "");
        movie.setDirector(directorField != null ? directorField.getText() : "");
        movie.setCountry(countryField != null ? countryField.getText() : "");
        movie.setGenres(String.join(", ", selectedGenres));

        // Категория
        String cat = categoryBox != null ? categoryBox.getValue() : "FILM";
        if (isKidsContentCheck != null && isKidsContentCheck.isSelected() && !"KIDS".equals(cat)) {
            cat = "KIDS";
        }
        movie.setCategory(cat);

        // Числовые поля
        try { movie.setRating(Double.parseDouble(ratingField.getText())); } catch (Exception ignored) { movie.setRating(0.0); }
        try { movie.setYear(Integer.parseInt(yearField.getText())); } catch (Exception ignored) { movie.setYear(2024); }
        try { movie.setDuration(Integer.parseInt(durationField.getText())); } catch (Exception ignored) { movie.setDuration(0); }

        // Флаги секций
        movie.setNowPlaying(isChecked(nowPlayingCheck));
        movie.setLatest(isChecked(latestCheck));
        movie.setTopRated(isChecked(topRatedCheck));
        movie.setPopular(isChecked(popularCheck));
        movie.setKids(isChecked(kidsCheck));
        movie.setEvening(isChecked(eveningCheck));
        movie.setTurkish(isChecked(turkishCheck));
        movie.setTop10(isChecked(top10Check));
        movie.setFeatured(isChecked(featuredCheck));

        // Флаги детского контента
        movie.setKidsFeatured(isChecked(kidsFeaturedCheck));
        movie.setKidsPopular(isChecked(kidsPopularCheck));
        movie.setKidsLatest(isChecked(kidsLatestCheck));

        return movie;
    }

    private boolean isChecked(CheckBox cb) {
        return cb != null && cb.isSelected();
    }

    // ===== Редактирование =====

    @FXML
    private void editMovie() {
        if (moviesList == null) return;
        int idx = moviesList.getSelectionModel().getSelectedIndex();
        if (idx < 0 || idx >= allMovies.size()) {
            showAlert("Выберите запись из списка!");
            return;
        }
        editingMovie = allMovies.get(idx);
        fillForm(editingMovie);
        setStatus("✏️ Редактирование: " + editingMovie.getTitle());
    }

    // ===== Удаление =====

    @FXML
    private void deleteMovie() {
        if (moviesList == null) return;
        int idx = moviesList.getSelectionModel().getSelectedIndex();
        if (idx < 0 || idx >= allMovies.size()) {
            showAlert("Выберите запись для удаления!");
            return;
        }
        Movie movie = allMovies.get(idx);
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Удаление");
        confirm.setHeaderText(null);
        confirm.setContentText("Удалить «" + movie.getTitle() + "»?");
        confirm.showAndWait().ifPresent(r -> {
            if (r == ButtonType.OK) {
                setStatus("⏳ Удаление...");
                db.deleteMovieAsync(movie.getId()).thenRun(() -> Platform.runLater(() -> {
                    logAction("DELETE_MOVIE", "Удалено: " + movie.getTitle());
                    loadMoviesListAsync();
                    setStatus("🗑️ Удалено: " + movie.getTitle());
                })).exceptionally(e -> { e.printStackTrace(); return null; });
            }
        });
    }

    // ===== Очистка формы =====

    @FXML
    private void clearForm() {
        editingMovie = null;
        safeSet(titleField, "");
        if (descField != null) descField.clear();
        safeSet(posterField, "");
        safeSet(bannerField, "");
        safeSet(videoField, "");
        safeSet(ratingField, "");
        safeSet(yearField, "");
        safeSet(durationField, "");
        safeSet(directorField, "");
        safeSet(countryField, "");
        if (categoryBox != null) categoryBox.setValue("FILM");
        selectedGenres.clear();
        genreToggleMap.values().forEach(tb -> { tb.setSelected(false); tb.setStyle(genreStyle(false)); });
        setChecked(nowPlayingCheck, false);
        setChecked(latestCheck, false);
        setChecked(topRatedCheck, false);
        setChecked(popularCheck, false);
        setChecked(kidsCheck, false);
        setChecked(eveningCheck, false);
        setChecked(turkishCheck, false);
        setChecked(top10Check, false);
        setChecked(featuredCheck, false);
        setChecked(isKidsContentCheck, false);
        setChecked(kidsFeaturedCheck, false);
        setChecked(kidsPopularCheck, false);
        setChecked(kidsLatestCheck, false);
        setStatus("Форма очищена");
    }

    // ===== Заполнение формы при редактировании =====

    private void fillForm(Movie m) {
        safeSet(titleField, m.getTitle());
        if (descField != null) descField.setText(m.getDescription());
        safeSet(posterField, m.getPosterPath());
        safeSet(bannerField, m.getBannerPath());
        safeSet(videoField, m.getVideoPath());
        safeSet(ratingField, String.valueOf(m.getRating()));
        safeSet(yearField, String.valueOf(m.getYear()));
        safeSet(durationField, String.valueOf(m.getDuration()));
        safeSet(directorField, m.getDirector());
        safeSet(countryField, m.getCountry());

        // Категория
        String cat = m.getCategory();
        if (categoryBox != null) {
            categoryBox.setValue(cat != null ? cat : "FILM");
        }
        boolean isKids = "KIDS".equals(cat) || m.isKids();
        setChecked(isKidsContentCheck, isKids);

        // Жанры
        selectedGenres.clear();
        genreToggleMap.values().forEach(tb -> { tb.setSelected(false); tb.setStyle(genreStyle(false)); });
        if (m.getGenres() != null) {
            for (String g : m.getGenres().split(",")) {
                String genre = g.trim();
                selectedGenres.add(genre);
                ToggleButton tb = genreToggleMap.get(genre);
                if (tb != null) { tb.setSelected(true); tb.setStyle(genreStyle(true)); }
            }
        }

        // Флаги
        setChecked(nowPlayingCheck, m.isNowPlaying());
        setChecked(latestCheck, m.isLatest());
        setChecked(topRatedCheck, m.isTopRated());
        setChecked(popularCheck, m.isPopular());
        setChecked(kidsCheck, m.isKids());
        setChecked(eveningCheck, m.isEvening());
        setChecked(turkishCheck, m.isTurkish());
        setChecked(top10Check, m.isTop10());
        setChecked(featuredCheck, m.isFeatured());
        setChecked(kidsFeaturedCheck, m.isKidsFeatured());
        setChecked(kidsPopularCheck, m.isKidsPopular());
        setChecked(kidsLatestCheck, m.isKidsLatest());
    }

    // ===== Вспомогательные =====

    private void safeSet(TextField field, String value) {
        if (field != null) field.setText(value != null ? value : "");
    }

    private void setChecked(CheckBox cb, boolean val) {
        if (cb != null) cb.setSelected(val);
    }

    private void setStatus(String msg) {
        if (statusLabel != null) Platform.runLater(() -> statusLabel.setText(msg));
    }

    private void logAction(String action, String details) {
        if (SessionManager.getInstance().isLoggedIn()) {
            db.asyncRun(() -> db.logAction(
                    SessionManager.getInstance().getCurrentUser().getId(),
                    SessionManager.getInstance().getCurrentUser().getUsername(),
                    action, details
            ));
        }
    }

    private void showAlert(String msg) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Focus Admin");
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }
}
