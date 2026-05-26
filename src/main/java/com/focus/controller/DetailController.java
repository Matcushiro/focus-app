package com.focus.controller;

import com.focus.database.DatabaseManager;
import com.focus.model.Movie;
import com.focus.service.SessionManager;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;

import java.net.URL;
import java.util.ResourceBundle;

public class DetailController implements Initializable {

    @FXML private ImageView backdropImage;
    @FXML private ImageView posterImage;
    @FXML private Label titleLabel;
    @FXML private Label ratingLabel;
    @FXML private Label yearLabel;
    @FXML private Label durationLabel;
    @FXML private Label categoryLabel;
    @FXML private Label descLabel;
    @FXML private Label directorLabel;
    @FXML private Label countryLabel;
    @FXML private Label genresLabel;
    @FXML private HBox genresBox;

    private Movie currentMovie;
    private final DatabaseManager db =
            DatabaseManager.getInstance();

    @Override
    public void initialize(URL url, ResourceBundle rb) {}

    public void setMovie(Movie movie) {
        this.currentMovie = movie;
        fillData(movie);
    }

    private void fillData(Movie movie) {
        titleLabel.setText(movie.getTitle());

        ratingLabel.setText(
                "⭐ " + String.format(
                        "%.1f", movie.getRating()
                )
        );

        yearLabel.setText(String.valueOf(movie.getYear()));

        if (movie.getDuration() > 0) {
            int hours   = movie.getDuration() / 60;
            int minutes = movie.getDuration() % 60;
            durationLabel.setText(
                    hours > 0
                            ? hours + " ч " + minutes + " мин"
                            : minutes + " мин"
            );
        }

        if (movie.getCategory() != null) {
            switch (movie.getCategory()) {
                case "FILM"   ->
                        categoryLabel.setText("Фильм");
                case "SERIES" ->
                        categoryLabel.setText("Сериал");
                case "KIDS"   ->
                        categoryLabel.setText("Детское");
                default ->
                        categoryLabel.setText("");
            }
        }

        descLabel.setText(
                movie.getDescription() != null
                        ? movie.getDescription()
                        : "Описание отсутствует"
        );

        directorLabel.setText(
                movie.getDirector() != null
                        ? movie.getDirector() : "—"
        );

        countryLabel.setText(
                movie.getCountry() != null
                        ? movie.getCountry() : "—"
        );

        genresLabel.setText(
                movie.getGenres() != null
                        ? movie.getGenres() : "—"
        );

        if (movie.getPosterPath() != null
                && !movie.getPosterPath().isEmpty()) {
            try {
                posterImage.setImage(new Image(
                        "file:" + movie.getPosterPath(),
                        220, 330, false, true, true
                ));

                String bannerPath =
                        (movie.getBannerPath() != null
                                && !movie.getBannerPath().isEmpty())
                                ? movie.getBannerPath()
                                : movie.getPosterPath();

                backdropImage.setImage(
                        new Image("file:" + bannerPath, true)
                );
                backdropImage.setFitHeight(300);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @FXML
    private void watchMovie() {
        if (currentMovie == null) return;

        if (SessionManager.getInstance().isLoggedIn()) {
            db.addToHistory(
                    SessionManager.getInstance()
                            .getCurrentUser().getId(),
                    currentMovie.getId()
            );
            db.logAction(
                    SessionManager.getInstance()
                            .getCurrentUser().getId(),
                    SessionManager.getInstance()
                            .getCurrentUser()
                            .getUsername(),
                    "WATCH_MOVIE",
                    "Смотрит: " + currentMovie.getTitle()
            );
        }

        if (currentMovie.getVideoPath() != null
                && !currentMovie.getVideoPath().isEmpty()) {
            openPlayer(currentMovie);
        } else {
            showAlert(
                    "Видео файл не прикреплён к этому фильму."
            );
        }
    }

    private void openPlayer(Movie movie) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource(
                            "/com/focus/fxml/player.fxml"
                    )
            );
            Parent page = loader.load();
            PlayerController ctrl =
                    loader.getController();
            ctrl.setMovie(movie);

            BorderPane root =
                    (BorderPane) titleLabel
                            .getScene().getRoot();
            root.setCenter(page);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void addToFavorites() {
        if (!SessionManager.getInstance().isLoggedIn()) {
            showAlert(
                    "Войдите чтобы добавить в избранное!"
            );
            return;
        }

        int userId = SessionManager.getInstance()
                .getCurrentUser().getId();

        if (db.isFavorite(userId, currentMovie.getId())) {
            db.removeFromFavorites(
                    userId, currentMovie.getId()
            );
            showAlert("Удалено из избранного");
        } else {
            db.addToFavorites(
                    userId, currentMovie.getId()
            );
            db.logAction(
                    userId,
                    SessionManager.getInstance()
                            .getCurrentUser()
                            .getUsername(),
                    "ADD_FAVORITE",
                    currentMovie.getTitle()
            );
            showAlert("✅ Добавлено в избранное!");
        }
    }

    @FXML
    private void goBack() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource(
                            "/com/focus/fxml/home.fxml"
                    )
            );
            Node page = loader.load();
            BorderPane root =
                    (BorderPane) titleLabel
                            .getScene().getRoot();
            root.setCenter(page);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showAlert(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(
                    Alert.AlertType.INFORMATION
            );
            alert.setTitle("Focus");
            alert.setHeaderText(null);
            alert.setContentText(message);

            DialogPane dp = alert.getDialogPane();
            dp.setStyle(
                    "-fx-background-color: #1a1a1a;"
            );
            Label content = (Label) dp.lookup(
                    ".content.label"
            );
            if (content != null) {
                content.setStyle(
                        "-fx-text-fill: white;"
                );
            }
            alert.showAndWait();
        });
    }
}