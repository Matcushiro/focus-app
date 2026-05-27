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

/**
 * DetailController — страница детали фильма/сериала.
 */
public class DetailController implements Initializable {

    @FXML private ImageView backdropImage;
    @FXML private ImageView posterImage;
    @FXML private Label     titleLabel;
    @FXML private Label     ratingLabel;
    @FXML private Label     yearLabel;
    @FXML private Label     durationLabel;
    @FXML private Label     categoryLabel;
    @FXML private Label     descLabel;
    @FXML private Label     directorLabel;
    @FXML private Label     countryLabel;
    @FXML private Label     genresLabel;
    @FXML private HBox      genresBox;

    // Кнопка "В список" для обновления текста
    @FXML private javafx.scene.control.Button favoriteBtn;

    private Movie  currentMovie;
    private boolean isFav = false;
    private final DatabaseManager db = DatabaseManager.getInstance();

    @Override
    public void initialize(URL url, ResourceBundle rb) {}

    public void setMovie(Movie movie) {
        this.currentMovie = movie;
        fillData(movie);
        checkFavoriteAsync(movie);
    }

    private void fillData(Movie movie) {
        if (titleLabel    != null) titleLabel.setText(movie.getTitle());
        if (ratingLabel   != null) ratingLabel.setText(String.format("%.1f", movie.getRating()));
        if (yearLabel     != null) yearLabel.setText(String.valueOf(movie.getYear()));

        if (durationLabel != null) {
            if (movie.getDuration() > 0) {
                int hours   = movie.getDuration() / 60;
                int minutes = movie.getDuration() % 60;
                durationLabel.setText(hours > 0
                        ? hours + " ч " + minutes + " мин"
                        : minutes + " мин");
            } else {
                durationLabel.setText("");
            }
        }

        if (categoryLabel != null && movie.getCategory() != null) {
            categoryLabel.setText(switch (movie.getCategory()) {
                case "FILM"   -> "Фильм";
                case "SERIES" -> "Сериал";
                case "KIDS"   -> "Детское";
                default       -> "";
            });
        }

        if (descLabel != null) {
            descLabel.setText(
                    movie.getDescription() != null && !movie.getDescription().isBlank()
                            ? movie.getDescription()
                            : "Описание отсутствует"
            );
        }

        if (directorLabel != null) directorLabel.setText(movie.getDirector() != null ? movie.getDirector() : "—");
        if (countryLabel  != null) countryLabel.setText(movie.getCountry()   != null ? movie.getCountry()  : "—");
        if (genresLabel   != null) genresLabel.setText(movie.getGenres()     != null ? movie.getGenres()   : "—");

        // Загрузка изображений
        if (movie.getPosterPath() != null && !movie.getPosterPath().isEmpty()) {
            try {
                if (posterImage != null) {
                    posterImage.setImage(new Image(
                            "file:" + movie.getPosterPath(), 220, 330, false, true, true
                    ));
                }

                String bannerPath = (movie.getBannerPath() != null && !movie.getBannerPath().isEmpty())
                        ? movie.getBannerPath()
                        : movie.getPosterPath();

                if (backdropImage != null) {
                    backdropImage.setImage(new Image("file:" + bannerPath, true));
                    backdropImage.setFitHeight(300);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // Асинхронная проверка избранного
    private void checkFavoriteAsync(Movie movie) {
        if (!SessionManager.getInstance().isLoggedIn()) return;
        int userId = SessionManager.getInstance().getCurrentUser().getId();
        db.async(() -> db.isFavorite(userId, movie.getId()))
                .thenAccept(fav -> Platform.runLater(() -> {
                    isFav = fav;
                    updateFavoriteBtn();
                }))
                .exceptionally(e -> { e.printStackTrace(); return null; });
    }

    private void updateFavoriteBtn() {
        if (favoriteBtn != null) {
            favoriteBtn.setText(isFav ? "В избранном" : "+ В список");
        }
    }

    // Действия

    @FXML
    private void watchMovie() {
        if (currentMovie == null) return;

        if (SessionManager.getInstance().isLoggedIn()) {
            int    uid      = SessionManager.getInstance().getCurrentUser().getId();
            String username = SessionManager.getInstance().getCurrentUser().getUsername();
            // Асинхронная запись истории и лога
            db.asyncRun(() -> {
                db.addToHistory(uid, currentMovie.getId());
                db.logAction(uid, username, "WATCH_MOVIE", "Смотрит: " + currentMovie.getTitle());
            }).exceptionally(e -> { e.printStackTrace(); return null; });
        }

        if (currentMovie.getVideoPath() != null && !currentMovie.getVideoPath().isEmpty()) {
            openPlayer(currentMovie);
        } else {
            showAlert("Видео файл не прикреплён к этому фильму.");
        }
    }

    private void openPlayer(Movie movie) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/focus/fxml/player.fxml")
            );
            Parent page = loader.load();
            PlayerController ctrl = loader.getController();
            ctrl.setMovie(movie);
            BorderPane root = (BorderPane) titleLabel.getScene().getRoot();
            root.setCenter(page);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void addToFavorites() {
        if (!SessionManager.getInstance().isLoggedIn()) {
            showAlert("Войдите, чтобы добавить в избранное!");
            return;
        }

        int    userId   = SessionManager.getInstance().getCurrentUser().getId();
        String username = SessionManager.getInstance().getCurrentUser().getUsername();

        db.asyncRun(() -> {
            if (isFav) {
                db.removeFromFavorites(userId, currentMovie.getId());
            } else {
                db.addToFavorites(userId, currentMovie.getId());
                db.logAction(userId, username, "ADD_FAVORITE", currentMovie.getTitle());
            }
        }).thenRun(() -> Platform.runLater(() -> {
            isFav = !isFav;
            updateFavoriteBtn();
            showAlert(isFav ? "Добавлено в избранное!" : "Удалено из избранного");
        })).exceptionally(e -> { e.printStackTrace(); return null; });
    }

    @FXML
    private void goBack() {
        try {
            BorderPane root = (BorderPane) titleLabel.getScene().getRoot();

            // Пробуем получить MainController для определения предыдущей страницы
            MainController mainCtrl = (MainController) root.getUserData();
            String backPage = (mainCtrl != null) ? mainCtrl.getLastPage() : "home.fxml";

            // Если текущая страница та же, что и lastPage — идём на home
            if ("detail.fxml".equals(backPage)) backPage = "home.fxml";

            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/focus/fxml/" + backPage)
            );
            Node page = loader.load();
            root.setCenter(page);
        } catch (Exception e) {
            // Фallback — всегда home.fxml
            try {
                FXMLLoader loader = new FXMLLoader(
                        getClass().getResource("/com/focus/fxml/home.fxml")
                );
                Node page = loader.load();
                BorderPane root = (BorderPane) titleLabel.getScene().getRoot();
                root.setCenter(page);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    private void showAlert(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Focus");
            alert.setHeaderText(null);
            alert.setContentText(message);
            DialogPane dp = alert.getDialogPane();
            dp.setStyle("-fx-background-color: #1a1a1a;");
            Label content = (Label) dp.lookup(".content.label");
            if (content != null) content.setStyle("-fx-text-fill: white;");
            alert.showAndWait();
        });
    }
}
