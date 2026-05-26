package com.focus.controller;

import com.focus.database.DatabaseManager;
import com.focus.model.Movie;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

public class SearchController implements Initializable {

    @FXML private FlowPane resultsGrid;
    @FXML private Label resultLabel;
    @FXML private TextField searchField;

    private final DatabaseManager db =
            DatabaseManager.getInstance();

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        showAllMovies();
    }

    // Показать все фильмы при открытии
    private void showAllMovies() {
        List<Movie> all = new ArrayList<>();
        all.addAll(db.getAllMovies());
        all.addAll(db.getAllSeries());
        all.addAll(db.getKidsMovies());

        resultLabel.setText("Результаты поиска");
        showResults(all);
    }

    // Поиск по нажатию кнопки или Enter
    @FXML
    private void doSearch() {
        String query = searchField.getText().trim();

        if (query.isEmpty()) {
            showAllMovies();
            return;
        }

        List<Movie> results = db.search(query);

        resultLabel.setText(
                "Результаты поиска: \"" + query +
                        "\" — " + results.size() + " шт."
        );

        showResults(results);
    }

    // Вызывается из MainController
    public void search(String query) {
        if (searchField != null) {
            searchField.setText(query);
        }
        doSearch();
    }

    private void showResults(List<Movie> movies) {
        resultsGrid.getChildren().clear();

        if (movies.isEmpty()) {
            Label empty = new Label(
                    "Ничего не найдено"
            );
            empty.setStyle(
                    "-fx-text-fill: #888888;" +
                            "-fx-font-size: 16px;" +
                            "-fx-padding: 20px;"
            );
            resultsGrid.getChildren().add(empty);
            return;
        }

        for (Movie movie : movies) {
            resultsGrid.getChildren()
                    .add(createCard(movie));
        }
    }

    private VBox createCard(Movie movie) {
        VBox card = new VBox(6);
        card.getStyleClass().add("movie-card");
        card.setPrefWidth(160);

        ImageView poster = new ImageView();
        poster.setFitWidth(160);
        poster.setFitHeight(240);
        poster.setPreserveRatio(false);

        if (movie.getPosterPath() != null
                && !movie.getPosterPath().isEmpty()) {
            try {
                poster.setImage(new Image(
                        "file:" + movie.getPosterPath(),
                        160, 240, false, true, true
                ));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        Label title = new Label(movie.getTitle());
        title.getStyleClass().add("card-title");
        title.setWrapText(true);
        title.setMaxWidth(150);

        Label rating = new Label(
                "⭐ " + String.format(
                        "%.1f", movie.getRating()
                )
        );
        rating.getStyleClass().add("card-rating");

        Label year = new Label(
                String.valueOf(movie.getYear())
        );
        year.getStyleClass().add("card-year");

        card.getChildren().addAll(
                poster, title, rating, year
        );
        card.setOnMouseClicked(e -> openDetail(movie));

        return card;
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
                    (BorderPane) resultsGrid
                            .getScene().getRoot();
            root.setCenter(page);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}