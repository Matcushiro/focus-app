package com.focus.controller;

import com.focus.database.DatabaseManager;
import com.focus.model.Movie;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HomeController implements Initializable {

    @FXML private ImageView bannerImage;
    @FXML private Label bannerTitle;
    @FXML private Label bannerDesc;
    @FXML private Label bannerRating;
    @FXML private StackPane bannerPane;

    // Смотрите сейчас - два ряда
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
    private final DatabaseManager db =
            DatabaseManager.getInstance();
    private final ExecutorService executor =
            Executors.newFixedThreadPool(4);

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        loadAllContent();
    }

    private void loadAllContent() {
        executor.submit(this::loadBanner);
        executor.submit(this::loadNowPlaying);
        executor.submit(this::loadLatest);
        executor.submit(this::loadTopRated);
        executor.submit(this::loadPopular);
        executor.submit(this::loadKids);
        executor.submit(this::loadEvening);
        executor.submit(this::loadTurkish);
        executor.submit(this::loadTop10);
    }

    // ===== Баннер =====

    private void loadBanner() {
        Movie movie = db.getFeaturedMovie();
        if (movie == null) return;
        featuredMovie = movie;

        Platform.runLater(() -> {
            bannerTitle.setText(movie.getTitle());
            bannerRating.setText(
                    "⭐ " + String.format(
                            "%.1f", movie.getRating()
                    )
            );

            String desc = movie.getDescription();
            if (desc != null && !desc.isEmpty()) {
                String[] sentences = desc.split("\\. ");
                String shortDesc = sentences.length >= 2
                        ? sentences[0] + ". " +
                          sentences[1] + "."
                        : desc;
                bannerDesc.setText(shortDesc);
            }

            String imagePath =
                    (movie.getBannerPath() != null
                            && !movie.getBannerPath().isEmpty())
                            ? movie.getBannerPath()
                            : movie.getPosterPath();

            if (imagePath != null
                    && !imagePath.isEmpty()) {
                try {
                    Image image = new Image(
                            "file:" + imagePath, true
                    );
                    bannerImage.setImage(image);
                    bannerImage.fitWidthProperty()
                            .bind(bannerPane.widthProperty());
                    bannerImage.setFitHeight(400);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    // ===== Секции =====

    private void loadNowPlaying() {
        List<Movie> movies = db.getNowPlaying();
        Platform.runLater(() -> {
            // Делим фильмы на два ряда
            int half = movies.size() / 2;
            List<Movie> first = movies.subList(
                    0, Math.min(half, movies.size())
            );
            List<Movie> second = movies.subList(
                    Math.min(half, movies.size()),
                    movies.size()
            );
            fillRowNowPlaying(nowPlayingRow, first);
            fillRowNowPlaying(nowPlayingRow2, second);
        });
    }

    private void loadLatest() {
        List<Movie> m = db.getLatest();
        Platform.runLater(() -> fillRow(latestRow, m));
    }

    private void loadTopRated() {
        List<Movie> m = db.getTopRated();
        Platform.runLater(() -> fillRow(topRatedRow, m));
    }

    private void loadPopular() {
        List<Movie> m = db.getPopular();
        Platform.runLater(() -> fillRow(popularRow, m));
    }

    private void loadKids() {
        List<Movie> m = db.getKidsMovies();
        Platform.runLater(() -> fillRow(kidsRow, m));
    }

    private void loadEvening() {
        List<Movie> m = db.getEvening();
        Platform.runLater(() -> fillRow(eveningRow, m));
    }

    private void loadTurkish() {
        List<Movie> m = db.getTurkish();
        Platform.runLater(() -> fillRow(turkishRow, m));
    }

    private void loadTop10() {
        List<Movie> m = db.getTop10();
        Platform.runLater(() -> fillRow(top10Row, m));
    }

    // ===== Карточки =====

    // Заполнение обычного ряда (с прокруткой)
    private void fillRow(HBox row, List<Movie> movies) {
        row.getChildren().clear();
        for (Movie movie : movies) {
            row.getChildren().add(
                    createCard(movie, 140, 200)
            );
        }
    }

    // Заполнение ряда "Смотрите сейчас" (без прокрутки)
    private void fillRowNowPlaying(HBox row,
                                   List<Movie> movies) {
        row.getChildren().clear();
        for (Movie movie : movies) {
            row.getChildren().add(
                    createNowPlayingCard(movie)
            );
        }
    }

    // Карточка для секции "Смотрите сейчас"
    private VBox createNowPlayingCard(Movie movie) {
        VBox card = new VBox(6);
        card.getStyleClass().add("now-playing-card");
        card.setPrefWidth(160);

        // Постер
        StackPane posterBox = new StackPane();
        posterBox.getStyleClass().add(
                "now-playing-poster-box"
        );
        posterBox.setPrefHeight(100);

        ImageView poster = new ImageView();
        poster.setFitWidth(160);
        poster.setFitHeight(100);
        poster.setPreserveRatio(false);

        if (movie.getPosterPath() != null
                && !movie.getPosterPath().isEmpty()) {
            try {
                poster.setImage(new Image(
                        "file:" + movie.getPosterPath(),
                        160, 100, false, true, true
                ));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        posterBox.getChildren().add(poster);

        // Название и рейтинг
        Label title = new Label(movie.getTitle());
        title.getStyleClass().add("now-playing-title");
        title.setWrapText(true);
        title.setMaxWidth(155);

        Label rating = new Label(
                "⭐ " + String.format(
                        "%.1f", movie.getRating()
                )
        );
        rating.getStyleClass().add("now-playing-rating");

        card.getChildren().addAll(
                posterBox, title, rating
        );
        card.setOnMouseClicked(e -> openDetail(movie));

        return card;
    }

    // Обычная карточка (для остальных секций)
    private VBox createCard(Movie movie,
                            int width,
                            int height) {
        VBox card = new VBox(6);
        card.getStyleClass().add("movie-card");
        card.setPrefWidth(width);

        ImageView poster = new ImageView();
        poster.setFitWidth(width);
        poster.setFitHeight(height);
        poster.setPreserveRatio(false);

        if (movie.getPosterPath() != null
                && !movie.getPosterPath().isEmpty()) {
            try {
                poster.setImage(new Image(
                        "file:" + movie.getPosterPath(),
                        width, height, false, true, true
                ));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        Label title = new Label(movie.getTitle());
        title.getStyleClass().add("card-title");
        title.setWrapText(true);
        title.setMaxWidth(width - 10);

        Label rating = new Label(
                "⭐ " + String.format(
                        "%.1f", movie.getRating()
                )
        );
        rating.getStyleClass().add("card-rating");

        card.getChildren().addAll(poster, title, rating);
        card.setOnMouseClicked(e -> openDetail(movie));

        return card;
    }

    @FXML
    private void watchFeatured() {
        if (featuredMovie != null) {
            openDetail(featuredMovie);
        }
    }

    @FXML
    private void addToList() {
        System.out.println("Добавлено в список");
    }

    private void openDetail(Movie movie) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource(
                            "/com/focus/fxml/detail.fxml"
                    )
            );
            Parent page = loader.load();
            DetailController ctrl =
                    loader.getController();
            ctrl.setMovie(movie);

            BorderPane root =
                    (BorderPane) bannerPane
                            .getScene().getRoot();
            root.setCenter(page);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}