package com.focus.controller;

import com.focus.database.DatabaseManager;
import com.focus.model.Movie;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

public class SeriesController implements Initializable {

    @FXML private FlowPane moviesGrid;
    @FXML private ComboBox<String> genreFilter;
    @FXML private ComboBox<String> ratingFilter;

    private final DatabaseManager db =
            DatabaseManager.getInstance();
    private List<Movie> allSeries;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        setupFilters();
        loadSeries();
    }

    private void setupFilters() {
        genreFilter.setItems(
                FXCollections.observableArrayList(
                        "Все", "Драма", "Комедия", "Триллер",
                        "Криминал", "Фантастика", "Мелодрама"
                )
        );
        ratingFilter.setItems(
                FXCollections.observableArrayList(
                        "Любой", "9+", "8+", "7+", "6+"
                )
        );
    }

    private void loadSeries() {
        allSeries = db.getAllSeries();
        showSeries(allSeries);
    }

    private void showSeries(List<Movie> series) {
        moviesGrid.getChildren().clear();
        for (Movie s : series) {
            moviesGrid.getChildren().add(createCard(s));
        }
    }

    @FXML
    private void applyFilters() {
        List<Movie> filtered = allSeries.stream()
                .filter(m -> {
                    String g = genreFilter.getValue();
                    if (g == null || g.equals("Все"))
                        return true;
                    return m.getGenres() != null
                            && m.getGenres().toLowerCase()
                            .contains(g.toLowerCase());
                })
                .filter(m -> {
                    String r = ratingFilter.getValue();
                    if (r == null || r.equals("Любой"))
                        return true;
                    double min = Double.parseDouble(
                            r.replace("+", "")
                    );
                    return m.getRating() >= min;
                })
                .collect(Collectors.toList());
        showSeries(filtered);
    }

    @FXML
    private void resetFilters() {
        genreFilter.setValue(null);
        ratingFilter.setValue(null);
        showSeries(allSeries);
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

        card.getChildren().addAll(poster, title, rating);
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
                    (BorderPane) moviesGrid
                            .getScene().getRoot();
            root.setCenter(page);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}