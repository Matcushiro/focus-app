package com.focus.controller;

import com.focus.database.DatabaseManager;
import com.focus.model.Movie;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;

/**
 * HomeController — главная вкладка.
 */
public class HomeController implements Initializable {

    // ===== Баннер =====
    @FXML private ImageView bannerImage;
    @FXML private Label bannerTitle;
    @FXML private Label bannerDesc;
    @FXML private Label bannerRating;
    @FXML private StackPane bannerPane;

    // ===== Секции =====
    @FXML private HBox nowPlayingRow;
    @FXML private HBox nowPlayingRow2;
    @FXML private HBox latestRow;
    @FXML private HBox topRatedRow;
    @FXML private HBox popularRow;
    @FXML private HBox kidsRow;
    @FXML private HBox eveningRow;
    @FXML private HBox turkishRow;
    @FXML private HBox top10Row;

    private Movie featuredMovie;
    private final DatabaseManager db = DatabaseManager.getInstance();

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        loadAllContentAsync();
    }

    // Асинхронная загрузка всего контента

    private void loadAllContentAsync() {
        // Баннер
        db.getFeaturedMovieAsync()
                .thenAccept(movie -> Platform.runLater(() -> {
                    if (movie != null) {
                        featuredMovie = movie;
                        showBanner(movie);
                    }
                }))
                .exceptionally(e -> { e.printStackTrace(); return null; });

        // Смотрите сейчас (два ряда)
        db.getNowPlayingAsync()
                .thenAccept(movies -> Platform.runLater(() -> {
                    int half = movies.size() / 2;
                    fillRowNowPlaying(nowPlayingRow,  movies.subList(0, Math.min(half, movies.size())));
                    fillRowNowPlaying(nowPlayingRow2, movies.subList(Math.min(half, movies.size()), movies.size()));
                }))
                .exceptionally(e -> { e.printStackTrace(); return null; });

        // Новинки
        db.getLatestAsync()
                .thenAccept(movies -> Platform.runLater(() -> fillRow(latestRow, movies)))
                .exceptionally(e -> { e.printStackTrace(); return null; });

        // Топ по рейтингу
        db.getTopRatedAsync()
                .thenAccept(movies -> Platform.runLater(() -> fillRow(topRatedRow, movies)))
                .exceptionally(e -> { e.printStackTrace(); return null; });

        // Популярные
        db.getPopularAsync()
                .thenAccept(movies -> Platform.runLater(() -> fillRow(popularRow, movies)))
                .exceptionally(e -> { e.printStackTrace(); return null; });

        // Детские (превью на главной)
        db.getKidsMoviesAsync()
                .thenAccept(movies -> Platform.runLater(() -> fillRow(kidsRow, movies)))
                .exceptionally(e -> { e.printStackTrace(); return null; });

        // Вечерние
        db.getEveningAsync()
                .thenAccept(movies -> Platform.runLater(() -> fillRow(eveningRow, movies)))
                .exceptionally(e -> { e.printStackTrace(); return null; });

        // Турецкие
        db.getTurkishAsync()
                .thenAccept(movies -> Platform.runLater(() -> fillRow(turkishRow, movies)))
                .exceptionally(e -> { e.printStackTrace(); return null; });

        // Топ-10
        db.getTop10Async()
                .thenAccept(movies -> Platform.runLater(() -> fillRow(top10Row, movies)))
                .exceptionally(e -> { e.printStackTrace(); return null; });
    }

    // Баннер

    private void showBanner(Movie movie) {
        bannerTitle.setText(movie.getTitle());
        bannerRating.setText(String.format("%.1f", movie.getRating()));

        String desc = movie.getDescription();
        if (desc != null && !desc.isEmpty()) {
            String[] sentences = desc.split("\\. ");
            String shortDesc = sentences.length >= 2
                    ? sentences[0] + ". " + sentences[1] + "."
                    : desc;
            bannerDesc.setText(shortDesc);
        }

        String imagePath = (movie.getBannerPath() != null && !movie.getBannerPath().isEmpty())
                ? movie.getBannerPath() : movie.getPosterPath();
        if (imagePath != null && !imagePath.isEmpty()) {
            try {
                Image image = new Image("file:" + imagePath, true);
                bannerImage.setImage(image);
                bannerImage.fitWidthProperty().bind(bannerPane.widthProperty());
                bannerImage.setFitHeight(400);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // Заполнение рядов

    private void fillRow(HBox row, List<Movie> movies) {
        if (row == null) return;
        row.getChildren().clear();
        for (Movie movie : movies) {
            row.getChildren().add(createCard(movie, 140, 200));
        }
    }

    private void fillRowNowPlaying(HBox row, List<Movie> movies) {
        if (row == null) return;
        row.getChildren().clear();
        for (Movie movie : movies) {
            row.getChildren().add(createNowPlayingCard(movie));
        }
    }

    // Карточки

    private VBox createNowPlayingCard(Movie movie) {
        VBox card = new VBox(6);
        card.getStyleClass().add("now-playing-card");
        card.setPrefWidth(160);

        StackPane posterBox = new StackPane();
        posterBox.getStyleClass().add("now-playing-poster-box");
        posterBox.setPrefHeight(100);

        ImageView poster = new ImageView();
        poster.setFitWidth(160);
        poster.setFitHeight(100);
        poster.setPreserveRatio(false);
        if (movie.getPosterPath() != null && !movie.getPosterPath().isEmpty()) {
            try {
                poster.setImage(new Image(
                        "file:" + movie.getPosterPath(), 160, 100, false, true, true
                ));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        posterBox.getChildren().add(poster);

        Label title = new Label(movie.getTitle());
        title.getStyleClass().add("now-playing-title");
        title.setWrapText(true);
        title.setMaxWidth(155);

        Label rating = new Label(String.format("%.1f", movie.getRating()));
        rating.getStyleClass().add("now-playing-rating");

        card.getChildren().addAll(posterBox, title, rating);
        card.setOnMouseClicked(e -> openDetail(movie));
        return card;
    }

    private VBox createCard(Movie movie, int width, int height) {
        VBox card = new VBox(6);
        card.getStyleClass().add("movie-card");
        card.setPrefWidth(width);

        ImageView poster = new ImageView();
        poster.setFitWidth(width);
        poster.setFitHeight(height);
        poster.setPreserveRatio(false);
        if (movie.getPosterPath() != null && !movie.getPosterPath().isEmpty()) {
            try {
                poster.setImage(new Image(
                        "file:" + movie.getPosterPath(), width, height, false, true, true
                ));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        Label title = new Label(movie.getTitle());
        title.getStyleClass().add("card-title");
        title.setWrapText(true);
        title.setMaxWidth(width - 10);

        Label rating = new Label(String.format("%.1f", movie.getRating()));
        rating.getStyleClass().add("card-rating");

        card.getChildren().addAll(poster, title, rating);
        card.setOnMouseClicked(e -> openDetail(movie));
        return card;
    }

    // Действия

    @FXML
    private void watchFeatured() {
        if (featuredMovie != null) openDetail(featuredMovie);
    }

    @FXML
    private void addToList() {
        System.out.println("Добавлено в список");
    }

    private void openDetail(Movie movie) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/focus/fxml/detail.fxml")
            );
            Parent page = loader.load();
            DetailController ctrl = loader.getController();
            ctrl.setMovie(movie);
            BorderPane root = (BorderPane) bannerPane.getScene().getRoot();
            root.setCenter(page);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
