package com.focus.controller;

import com.focus.database.DatabaseManager;
import com.focus.model.Movie;
import com.focus.service.LogManager;
import com.focus.service.SessionManager;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

public class AdminController implements Initializable {

    @FXML private TextField titleField;
    @FXML private TextArea  descField;
    @FXML private TextField posterField;
    @FXML private TextField bannerField;
    @FXML private TextField videoField;
    @FXML private TextField ratingField;
    @FXML private TextField yearField;
    @FXML private TextField durationField;
    @FXML private TextField directorField;
    @FXML private TextField countryField;
    @FXML private TextField genresField;
    @FXML private ComboBox<String> categoryBox;
    @FXML private ListView<String> moviesList;

    @FXML private CheckBox nowPlayingCheck;
    @FXML private CheckBox latestCheck;
    @FXML private CheckBox topRatedCheck;
    @FXML private CheckBox popularCheck;
    @FXML private CheckBox kidsCheck;
    @FXML private CheckBox eveningCheck;
    @FXML private CheckBox turkishCheck;
    @FXML private CheckBox top10Check;
    @FXML private CheckBox featuredCheck;

    private final DatabaseManager db =
            DatabaseManager.getInstance();
    private List<Movie> allMovies = new ArrayList<>();
    private Movie editingMovie = null;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        categoryBox.setItems(
                FXCollections.observableArrayList(
                        "FILM", "SERIES", "KIDS"
                )
        );
        categoryBox.setValue("FILM");
        loadMoviesList();
    }

    private void loadMoviesList() {
        allMovies = new ArrayList<>();
        allMovies.addAll(db.getAllMovies());
        allMovies.addAll(db.getAllSeries());
        allMovies.addAll(db.getKidsMovies());

        ObservableList<String> items =
                FXCollections.observableArrayList();
        for (Movie m : allMovies) {
            items.add(
                    m.getId() + " | " +
                            m.getTitle() +
                            " (" + m.getYear() + ")"
            );
        }
        moviesList.setItems(items);
    }

    @FXML
    private void selectPoster() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Выберите постер");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter(
                        "Изображения", "*.jpg", "*.png", "*.jpeg"
                )
        );
        File file = chooser.showOpenDialog(new Stage());
        if (file != null) {
            posterField.setText(file.getAbsolutePath());
        }
    }

    @FXML
    private void selectBanner() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Выберите баннер");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter(
                        "Изображения", "*.jpg", "*.png", "*.jpeg"
                )
        );
        File file = chooser.showOpenDialog(new Stage());
        if (file != null) {
            bannerField.setText(file.getAbsolutePath());
        }
    }

    @FXML
    private void selectVideo() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Выберите видео");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter(
                        "Видео", "*.mp4", "*.mkv", "*.avi"
                )
        );
        File file = chooser.showOpenDialog(new Stage());
        if (file != null) {
            videoField.setText(file.getAbsolutePath());
        }
    }

    @FXML
    private void saveMovie() {
        if (titleField.getText().isEmpty()) {
            showAlert("Введите название фильма!");
            return;
        }

        Movie movie = new Movie();
        if (editingMovie != null) {
            movie.setId(editingMovie.getId());
        }

        movie.setTitle(titleField.getText());
        movie.setDescription(descField.getText());
        movie.setPosterPath(posterField.getText());
        movie.setBannerPath(bannerField.getText());
        movie.setVideoPath(videoField.getText());
        movie.setCategory(categoryBox.getValue());
        movie.setDirector(directorField.getText());
        movie.setCountry(countryField.getText());
        movie.setGenres(genresField.getText());

        try {
            movie.setRating(Double.parseDouble(
                    ratingField.getText()
            ));
        } catch (NumberFormatException e) {
            movie.setRating(0.0);
        }

        try {
            movie.setYear(Integer.parseInt(
                    yearField.getText()
            ));
        } catch (NumberFormatException e) {
            movie.setYear(2024);
        }

        try {
            movie.setDuration(Integer.parseInt(
                    durationField.getText()
            ));
        } catch (NumberFormatException e) {
            movie.setDuration(0);
        }

        movie.setNowPlaying(nowPlayingCheck.isSelected());
        movie.setLatest(latestCheck.isSelected());
        movie.setTopRated(topRatedCheck.isSelected());
        movie.setPopular(popularCheck.isSelected());
        movie.setKids(kidsCheck.isSelected());
        movie.setEvening(eveningCheck.isSelected());
        movie.setTurkish(turkishCheck.isSelected());
        movie.setTop10(top10Check.isSelected());
        movie.setFeatured(featuredCheck.isSelected());

        if (editingMovie == null) {
            db.addMovie(movie);

            // Логируем добавление
            if (SessionManager.getInstance().isLoggedIn()) {
                db.logAction(
                        SessionManager.getInstance()
                                .getCurrentUser().getId(),
                        SessionManager.getInstance()
                                .getCurrentUser()
                                .getUsername(),
                        "ADD_MOVIE",
                        "Добавлен фильм: " + movie.getTitle()
                );
            }
            showAlert("✅ Фильм добавлен!");
        } else {
            db.updateMovie(movie);

            // Логируем обновление
            if (SessionManager.getInstance().isLoggedIn()) {
                db.logAction(
                        SessionManager.getInstance()
                                .getCurrentUser().getId(),
                        SessionManager.getInstance()
                                .getCurrentUser()
                                .getUsername(),
                        "UPDATE_MOVIE",
                        "Обновлён фильм: " + movie.getTitle()
                );
            }
            showAlert("✅ Фильм обновлён!");
            editingMovie = null;
        }

        clearForm();
        loadMoviesList();
    }

    @FXML
    private void editMovie() {
        int idx = moviesList.getSelectionModel()
                .getSelectedIndex();
        if (idx < 0) {
            showAlert("Выберите фильм!");
            return;
        }
        editingMovie = allMovies.get(idx);
        fillForm(editingMovie);
    }

    private void fillForm(Movie m) {
        titleField.setText(m.getTitle());
        descField.setText(m.getDescription());
        posterField.setText(
                m.getPosterPath() != null
                        ? m.getPosterPath() : ""
        );
        bannerField.setText(
                m.getBannerPath() != null
                        ? m.getBannerPath() : ""
        );
        videoField.setText(
                m.getVideoPath() != null
                        ? m.getVideoPath() : ""
        );
        ratingField.setText(
                String.valueOf(m.getRating())
        );
        yearField.setText(
                String.valueOf(m.getYear())
        );
        durationField.setText(
                String.valueOf(m.getDuration())
        );
        directorField.setText(
                m.getDirector() != null
                        ? m.getDirector() : ""
        );
        countryField.setText(
                m.getCountry() != null
                        ? m.getCountry() : ""
        );
        genresField.setText(
                m.getGenres() != null
                        ? m.getGenres() : ""
        );
        categoryBox.setValue(m.getCategory());

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

    @FXML
    private void deleteMovie() {
        int idx = moviesList.getSelectionModel()
                .getSelectedIndex();
        if (idx < 0) {
            showAlert("Выберите фильм!");
            return;
        }

        Movie movie = allMovies.get(idx);
        Alert confirm = new Alert(
                Alert.AlertType.CONFIRMATION
        );
        confirm.setTitle("Удаление");
        confirm.setContentText(
                "Удалить: " + movie.getTitle() + "?"
        );
        confirm.showAndWait().ifPresent(r -> {
            if (r == ButtonType.OK) {
                // Логируем удаление
                if (SessionManager.getInstance()
                        .isLoggedIn()) {
                    db.logAction(
                            SessionManager.getInstance()
                                    .getCurrentUser().getId(),
                            SessionManager.getInstance()
                                    .getCurrentUser()
                                    .getUsername(),
                            "DELETE_MOVIE",
                            "Удалён фильм: " +
                                    movie.getTitle()
                    );
                }
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
        genresField.clear();
        categoryBox.setValue("FILM");

        nowPlayingCheck.setSelected(false);
        latestCheck.setSelected(false);
        topRatedCheck.setSelected(false);
        popularCheck.setSelected(false);
        kidsCheck.setSelected(false);
        eveningCheck.setSelected(false);
        turkishCheck.setSelected(false);
        top10Check.setSelected(false);
        featuredCheck.setSelected(false);
    }

    private void showAlert(String msg) {
        Alert alert = new Alert(
                Alert.AlertType.INFORMATION
        );
        alert.setContentText(msg);
        alert.showAndWait();
    }
}