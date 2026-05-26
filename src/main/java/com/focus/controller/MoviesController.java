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

public class MoviesController implements Initializable {

    @FXML private FlowPane moviesGrid;
    @FXML private ComboBox<String> genreFilter;
    @FXML private ComboBox<String> ratingFilter;
    @FXML private ComboBox<String> yearFilter;

    private final DatabaseManager db =
            DatabaseManager.getInstance();
    private List<Movie> allMovies;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        setupFilters();
        loadMovies();
    }

    private void setupFilters() {
        genreFilter.setItems(
                FXCollections.observableArrayList(
                        "Все", "Драма", "Комедия", "Триллер",
                        "Боевик", "Фантастика", "Ужасы",
                        "Мелодрама", "Анимация", "Документальный"
                )
        );
        ratingFilter.setItems(
                FXCollections.observableArrayList(
                        "Любой", "9+", "8+", "7+", "6+"
                )
        );
        yearFilter.setItems(
                FXCollections.observableArrayList(
                        "Любой", "2024", "2023", "2022",
                        "2021", "2020", "2010-2019", "2000-2009"
                )
        );
    }

    private void loadMovies() {
        allMovies = db.getAllMovies();
        showMovies(allMovies);
    }

    private void showMovies(List<Movie> movies) {
        moviesGrid.getChildren().clear();
        for (Movie movie : movies) {
            moviesGrid.getChildren().add(createCard(movie));
        }
    }

    @FXML
    private void applyFilters() {
        List<Movie> filtered = allMovies.stream()
                .filter(this::matchesGenre)
                .filter(this::matchesRating)
                .filter(this::matchesYear)
                .collect(Collectors.toList());
        showMovies(filtered);
    }

    private boolean matchesGenre(Movie movie) {
        String selected = genreFilter.getValue();
        if (selected == null
                || selected.equals("Все")
                || selected.isEmpty()) {
            return true;
        }
        return movie.getGenres() != null
                && movie.getGenres()
                .toLowerCase()
                .contains(selected.toLowerCase());
    }

    private boolean matchesRating(Movie movie) {
        String selected = ratingFilter.getValue();
        if (selected == null
                || selected.equals("Любой")
                || selected.isEmpty()) {
            return true;
        }
        double minRating = Double.parseDouble(
                selected.replace("+", "")
        );
        return movie.getRating() >= minRating;
    }

    private boolean matchesYear(Movie movie) {
        String selected = yearFilter.getValue();
        if (selected == null
                || selected.equals("Любой")
                || selected.isEmpty()) {
            return true;
        }
        if (selected.contains("-")) {
            String[] parts = selected.split("-");
            int from = Integer.parseInt(parts[0]);
            int to   = Integer.parseInt(parts[1]);
            return movie.getYear() >= from
                    && movie.getYear() <= to;
        }
        return movie.getYear() ==
                Integer.parseInt(selected);
    }

    @FXML
    private void resetFilters() {
        genreFilter.setValue(null);
        ratingFilter.setValue(null);
        yearFilter.setValue(null);
        showMovies(allMovies);
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
                    (BorderPane) moviesGrid
                            .getScene().getRoot();
            root.setCenter(page);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}