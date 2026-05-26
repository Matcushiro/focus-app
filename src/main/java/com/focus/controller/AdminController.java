package com.focus.controller;

import com.focus.database.DatabaseManager;
import com.focus.model.Movie;
import com.focus.service.LogManager;
import com.focus.service.SessionManager;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.FlowPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.net.URL;
import java.util.*;

public class AdminController implements Initializable {

    // --- Основные поля ---
    @FXML private TextField   titleField;
    @FXML private TextArea    descField;
    @FXML private TextField   posterField;
    @FXML private TextField   bannerField;
    @FXML private TextField   videoField;
    @FXML private TextField   ratingField;
    @FXML private TextField   yearField;
    @FXML private TextField   durationField;
    @FXML private TextField   directorField;
    @FXML private TextField   countryField;

    // Категория — фильм/сериал
    @FXML private ComboBox<String> categoryBox;

    // Жанры — мультивыбор через FlowPane с ToggleButton
    @FXML private FlowPane genresPane;
    private final Set<String>           selectedGenres = new LinkedHashSet<>();
    private final Map<String, ToggleButton> genreToggleMap = new LinkedHashMap<>();

    // Флаги
    @FXML private CheckBox nowPlayingCheck;
    @FXML private CheckBox latestCheck;
    @FXML private CheckBox topRatedCheck;
    @FXML private CheckBox popularCheck;
    @FXML private CheckBox kidsCheck;
    @FXML private CheckBox eveningCheck;
    @FXML private CheckBox turkishCheck;
    @FXML private CheckBox top10Check;
    @FXML private CheckBox featuredCheck;
    // Детский контент
    @FXML private CheckBox isKidsContentCheck;

    // Список фильмов/сериалов
    @FXML private ListView<String> moviesList;

    private static final List<String> ALL_GENRES = Arrays.asList(
            "Драма", "Комедия", "Триллер", "Боевик", "Фантастика",
            "Ужасы", "Мелодрама", "Анимация", "Документальный",
            "Криминал", "Приключения", "Биография", "Исторический",
            "Мюзикл", "Вестерн", "Семейный", "Романтика"
    );

    private final DatabaseManager db = DatabaseManager.getInstance();
    private List<Movie> allMovies = new ArrayList<>();
    private Movie editingMovie = null;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        categoryBox.setItems(FXCollections.observableArrayList(
                "FILM", "SERIES"
        ));
        categoryBox.setValue("FILM");

        buildGenresPanel();
        loadMoviesList();
    }

    /** Создаём панель жанров с ToggleButton-тегами */
    private void buildGenresPanel() {
        genresPane.getChildren().clear();
        genreToggleMap.clear();

        for (String genre : ALL_GENRES) {
            ToggleButton tb = new ToggleButton(genre);
            tb.setStyle(genreStyle(false));
            tb.selectedProperty().addListener((obs, old, sel) -> {
                tb.setStyle(genreStyle(sel));
                if (sel) selectedGenres.add(genre);
                else     selectedGenres.remove(genre);
            });
            genreToggleMap.put(genre, tb);
            genresPane.getChildren().add(tb);
        }
    }

    private String genreStyle(boolean selected) {
        if (selected) {
            return "-fx-background-color: #E65C00; -fx-text-fill: #ffffff;" +
                    "-fx-border-color: #E65C00; -fx-background-radius: 14;" +
                    "-fx-border-radius: 14; -fx-padding: 4 12; -fx-cursor: hand;" +
                    "-fx-font-size: 11px;";
        } else {
            return "-fx-background-color: #2a2a2a; -fx-text-fill: #cccccc;" +
                    "-fx-border-color: #555; -fx-background-radius: 14;" +
                    "-fx-border-radius: 14; -fx-padding: 4 12; -fx-cursor: hand;" +
                    "-fx-font-size: 11px;";
        }
    }

    private void loadMoviesList() {
        allMovies = new ArrayList<>();
        allMovies.addAll(db.getAllMovies());
        allMovies.addAll(db.getAllSeries());
        allMovies.addAll(db.getKidsMovies());

        ObservableList<String> items = FXCollections.observableArrayList();
        for (Movie m : allMovies) {
            String type = "FILM".equals(m.getCategory()) ? "🎬" :
                    "SERIES".equals(m.getCategory()) ? "📺" : "👶";
            items.add(type + " " + m.getId() + " | " + m.getTitle()
                    + " (" + m.getYear() + ")");
        }
        moviesList.setItems(items);
    }

    @FXML
    private void selectPoster() {
        File file = openFileChooser("Выберите постер",
                new FileChooser.ExtensionFilter("Изображения", "*.jpg", "*.png", "*.jpeg"));
        if (file != null) posterField.setText(file.getAbsolutePath());
    }

    @FXML
    private void selectBanner() {
        File file = openFileChooser("Выберите баннер",
                new FileChooser.ExtensionFilter("Изображения", "*.jpg", "*.png", "*.jpeg"));
        if (file != null) bannerField.setText(file.getAbsolutePath());
    }

    @FXML
    private void selectVideo() {
        File file = openFileChooser("Выберите видео",
                new FileChooser.ExtensionFilter("Видео", "*.mp4", "*.mkv", "*.avi"));
        if (file != null) videoField.setText(file.getAbsolutePath());
    }

    private File openFileChooser(String title, FileChooser.ExtensionFilter filter) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters().add(filter);
        return chooser.showOpenDialog(new Stage());
    }

    @FXML
    private void saveMovie() {
        if (titleField.getText().isEmpty()) {
            showAlert("Введите название!");
            return;
        }
        if (selectedGenres.isEmpty()) {
            showAlert("Выберите хотя бы один жанр!");
            return;
        }

        Movie movie = new Movie();
        if (editingMovie != null) movie.setId(editingMovie.getId());

        movie.setTitle(titleField.getText());
        movie.setDescription(descField.getText());
        movie.setPosterPath(posterField.getText());
        movie.setBannerPath(bannerField.getText());
        movie.setVideoPath(videoField.getText());
        movie.setDirector(directorField.getText());
        movie.setCountry(countryField.getText());

        // Жанры — объединяем через запятую
        movie.setGenres(String.join(", ", selectedGenres));

        // Категория: если отмечен "детский контент" — KIDS, иначе FILM/SERIES
        String cat = categoryBox.getValue();
        if (isKidsContentCheck != null && isKidsContentCheck.isSelected()) {
            cat = "KIDS";
        }
        movie.setCategory(cat);

        try { movie.setRating(Double.parseDouble(ratingField.getText())); }
        catch (NumberFormatException e) { movie.setRating(0.0); }

        try { movie.setYear(Integer.parseInt(yearField.getText())); }
        catch (NumberFormatException e) { movie.setYear(2024); }

        try { movie.setDuration(Integer.parseInt(durationField.getText())); }
        catch (NumberFormatException e) { movie.setDuration(0); }

        movie.setNowPlaying(nowPlayingCheck.isSelected());
        movie.setLatest(latestCheck.isSelected());
        movie.setTopRated(topRatedCheck.isSelected());
        movie.setPopular(popularCheck.isSelected());
        movie.setKids(kidsCheck.isSelected());
        movie.setEvening(eveningCheck.isSelected());
        movie.setTurkish(turkishCheck.isSelected());
        movie.setTop10(top10Check.isSelected());
        movie.setFeatured(featuredCheck.isSelected());

        String actionType = editingMovie == null ? "ADD" : "UPDATE";
        String label      = "FILM".equals(categoryBox.getValue()) ? "фильм" : "сериал";

        if (editingMovie == null) {
            db.addMovie(movie);
            logAction("ADD_" + categoryBox.getValue(),
                    "Добавлен " + label + ": " + movie.getTitle());
            showAlert("✅ " + (label.equals("фильм") ? "Фильм" : "Сериал") + " добавлен!");
        } else {
            db.updateMovie(movie);
            logAction("UPDATE_" + categoryBox.getValue(),
                    "Обновлён " + label + ": " + movie.getTitle());
            showAlert("✅ " + (label.equals("фильм") ? "Фильм" : "Сериал") + " обновлён!");
            editingMovie = null;
        }

        clearForm();
        loadMoviesList();
    }

    @FXML
    private void editMovie() {
        int idx = moviesList.getSelectionModel().getSelectedIndex();
        if (idx < 0) { showAlert("Выберите элемент из списка!"); return; }

        editingMovie = allMovies.get(idx);
        fillForm(editingMovie);
    }

    @FXML
    private void deleteMovie() {
        int idx = moviesList.getSelectionModel().getSelectedIndex();
        if (idx < 0) { showAlert("Выберите элемент!"); return; }

        Movie movie = allMovies.get(idx);
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Удаление");
        confirm.setContentText("Удалить «" + movie.getTitle() + "»?");
        confirm.showAndWait().ifPresent(r -> {
            if (r == ButtonType.OK) {
                logAction("DELETE_MOVIE", "Удалён: " + movie.getTitle());
                db.deleteMovie(movie.getId());
                loadMoviesList();
            }
        });
    }

    @FXML
    private void clearForm() {
        editingMovie = null;
        titleField.clear();
        descField.clear();
        posterField.clear();
        bannerField.clear();
        videoField.clear();
        ratingField.clear();
        yearField.clear();
        durationField.clear();
        directorField.clear();
        countryField.clear();
        categoryBox.setValue("FILM");
        selectedGenres.clear();
        genreToggleMap.values().forEach(tb -> {
            tb.setSelected(false);
            tb.setStyle(genreStyle(false));
        });
        nowPlayingCheck.setSelected(false);
        latestCheck.setSelected(false);
        topRatedCheck.setSelected(false);
        popularCheck.setSelected(false);
        kidsCheck.setSelected(false);
        eveningCheck.setSelected(false);
        turkishCheck.setSelected(false);
        top10Check.setSelected(false);
        featuredCheck.setSelected(false);
        if (isKidsContentCheck != null) isKidsContentCheck.setSelected(false);
    }

    private void fillForm(Movie m) {
        titleField.setText(m.getTitle());
        descField.setText(m.getDescription());
        posterField.setText(m.getPosterPath());
        bannerField.setText(m.getBannerPath());
        videoField.setText(m.getVideoPath());
        ratingField.setText(String.valueOf(m.getRating()));
        yearField.setText(String.valueOf(m.getYear()));
        durationField.setText(String.valueOf(m.getDuration()));
        directorField.setText(m.getDirector());
        countryField.setText(m.getCountry());

        // Категория
        boolean isKidsContent = "KIDS".equals(m.getCategory());
        if (isKidsContent) {
            categoryBox.setValue("FILM"); // по умолчанию показываем FILM, но ставим флаг
            if (isKidsContentCheck != null) isKidsContentCheck.setSelected(true);
        } else {
            categoryBox.setValue(m.getCategory());
            if (isKidsContentCheck != null) isKidsContentCheck.setSelected(false);
        }

        // Жанры
        selectedGenres.clear();
        genreToggleMap.values().forEach(tb -> {
            tb.setSelected(false);
            tb.setStyle(genreStyle(false));
        });
        if (m.getGenres() != null) {
            for (String g : m.getGenres().split(",")) {
                String genre = g.trim();
                selectedGenres.add(genre);
                ToggleButton tb = genreToggleMap.get(genre);
                if (tb != null) {
                    tb.setSelected(true);
                    tb.setStyle(genreStyle(true));
                }
            }
        }

        nowPlayingCheck.setSelected(m.isNowPlaying());
        latestCheck.setSelected(m.isLatest());
        topRatedCheck.setSelected(m.isTopRated());
        popularCheck.setSelected(m.isPopular());
        kidsCheck.setSelected(m.isKids());
        eveningCheck.setSelected(m.isEvening());
        turkishCheck.setSelected(m.isTurkish());
        top10Check.setSelected(m.isTop10());
        featuredCheck.setSelected(m.isFeatured());
    }

    private void logAction(String action, String details) {
        if (SessionManager.getInstance().isLoggedIn()) {
            db.logAction(
                    SessionManager.getInstance().getCurrentUser().getId(),
                    SessionManager.getInstance().getCurrentUser().getUsername(),
                    action, details
            );
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
