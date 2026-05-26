package com.focus.controller;

import com.focus.database.DatabaseManager;
import com.focus.model.Movie;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.Popup;

import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

public class MoviesController implements Initializable {

    @FXML private FlowPane moviesGrid;
    @FXML private TextField searchField;
    @FXML private Button filterBtn;
    @FXML private Label resultsLabel;

    private final DatabaseManager db = DatabaseManager.getInstance();
    private List<Movie> allMovies;

    // Текущие активные фильтры
    private final Set<String> selectedGenres = new HashSet<>();
    private String selectedRating = null;
    private String selectedYear   = null;

    // Popup с фильтрами
    private Popup filterPopup;

    private static final List<String> GENRES = Arrays.asList(
            "Драма", "Комедия", "Триллер", "Боевик",
            "Фантастика", "Ужасы", "Мелодрама", "Анимация",
            "Документальный", "Криминал", "Приключения", "Биография"
    );

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        loadMovies();
        buildFilterPopup();

        // Поиск в реальном времени при наборе текста
        searchField.textProperty().addListener((obs, oldVal, newVal) -> applyFilters());
    }

    private void loadMovies() {
        allMovies = db.getAllMovies();
        showMovies(allMovies);
    }

    private void showMovies(List<Movie> movies) {
        moviesGrid.getChildren().clear();
        if (resultsLabel != null) {
            resultsLabel.setText("Найдено: " + movies.size());
        }
        for (Movie movie : movies) {
            moviesGrid.getChildren().add(createCard(movie));
        }
    }

    @FXML
    private void openFilterPopup() {
        if (filterPopup.isShowing()) {
            filterPopup.hide();
        } else {
            var bounds = filterBtn.localToScreen(filterBtn.getBoundsInLocal());
            filterPopup.show(filterBtn, bounds.getMinX(), bounds.getMaxY() + 4);
        }
    }

    private void buildFilterPopup() {
        filterPopup = new Popup();
        filterPopup.setAutoHide(true);

        VBox popupBox = new VBox(12);
        popupBox.setStyle(
                "-fx-background-color: #1a1a1a;" +
                        "-fx-border-color: #E65C00;" +
                        "-fx-border-width: 1;" +
                        "-fx-border-radius: 6;" +
                        "-fx-background-radius: 6;" +
                        "-fx-padding: 16;" +
                        "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.8), 20, 0, 0, 4);"
        );
        popupBox.setPrefWidth(320);

        // --- Заголовок ---
        Label title = new Label("🎛 Фильтры");
        title.setStyle("-fx-text-fill: #ffffff; -fx-font-size: 15px; -fx-font-weight: bold;");

        // --- Жанры (мультивыбор) ---
        Label genresLabel = new Label("Жанры:");
        genresLabel.setStyle("-fx-text-fill: #aaaaaa; -fx-font-size: 12px;");

        FlowPane genreFlow = new FlowPane(6, 6);
        genreFlow.setPrefWrapLength(290);

        Map<String, ToggleButton> genreButtons = new LinkedHashMap<>();
        for (String genre : GENRES) {
            ToggleButton tb = new ToggleButton(genre);
            tb.setSelected(selectedGenres.contains(genre));
            tb.setStyle(getGenreButtonStyle(tb.isSelected()));
            tb.selectedProperty().addListener((obs, old, selected) -> {
                if (selected) {
                    selectedGenres.add(genre);
                    tb.setStyle(getGenreButtonStyle(true));
                } else {
                    selectedGenres.remove(genre);
                    tb.setStyle(getGenreButtonStyle(false));
                }
            });
            genreButtons.put(genre, tb);
            genreFlow.getChildren().add(tb);
        }

        // --- Рейтинг ---
        Label ratingLabel = new Label("Минимальный рейтинг:");
        ratingLabel.setStyle("-fx-text-fill: #aaaaaa; -fx-font-size: 12px;");

        ComboBox<String> ratingBox = new ComboBox<>();
        ratingBox.setItems(FXCollections.observableArrayList(
                "Любой", "9+", "8+", "7+", "6+"
        ));
        ratingBox.setValue(selectedRating != null ? selectedRating : "Любой");
        ratingBox.setStyle(
                "-fx-background-color: #2a2a2a; -fx-text-fill: #ffffff;" +
                        "-fx-border-color: #444; -fx-border-radius: 4; -fx-pref-width: 290;"
        );

        // --- Год ---
        Label yearLabel = new Label("Год выпуска:");
        yearLabel.setStyle("-fx-text-fill: #aaaaaa; -fx-font-size: 12px;");

        ComboBox<String> yearBox = new ComboBox<>();
        yearBox.setItems(FXCollections.observableArrayList(
                "Любой", "2024", "2023", "2022", "2021", "2020",
                "2010-2019", "2000-2009"
        ));
        yearBox.setValue(selectedYear != null ? selectedYear : "Любой");
        yearBox.setStyle(
                "-fx-background-color: #2a2a2a; -fx-text-fill: #ffffff;" +
                        "-fx-border-color: #444; -fx-border-radius: 4; -fx-pref-width: 290;"
        );

        // --- Кнопки ---
        HBox btnRow = new HBox(8);
        btnRow.setAlignment(Pos.CENTER_RIGHT);

        Button resetBtn = new Button("Сбросить");
        resetBtn.setStyle(
                "-fx-background-color: #2a2a2a; -fx-text-fill: #aaaaaa;" +
                        "-fx-border-color: #555; -fx-border-radius: 4; -fx-padding: 6 16;"
        );
        resetBtn.setOnAction(e -> {
            selectedGenres.clear();
            selectedRating = null;
            selectedYear   = null;
            genreButtons.values().forEach(tb -> {
                tb.setSelected(false);
                tb.setStyle(getGenreButtonStyle(false));
            });
            ratingBox.setValue("Любой");
            yearBox.setValue("Любой");
            applyFilters();
            updateFilterButtonIndicator();
        });

        Button applyBtn = new Button("Применить");
        applyBtn.setStyle(
                "-fx-background-color: #E65C00; -fx-text-fill: #ffffff;" +
                        "-fx-font-weight: bold; -fx-border-radius: 4; -fx-padding: 6 16;" +
                        "-fx-cursor: hand;"
        );
        applyBtn.setOnAction(e -> {
            selectedRating = ratingBox.getValue();
            selectedYear   = yearBox.getValue();
            applyFilters();
            updateFilterButtonIndicator();
            filterPopup.hide();
        });

        btnRow.getChildren().addAll(resetBtn, applyBtn);

        popupBox.getChildren().addAll(
                title,
                new Separator(),
                genresLabel, genreFlow,
                ratingLabel, ratingBox,
                yearLabel, yearBox,
                new Separator(),
                btnRow
        );

        filterPopup.getContent().add(popupBox);
    }

    private String getGenreButtonStyle(boolean selected) {
        if (selected) {
            return "-fx-background-color: #E65C00; -fx-text-fill: #ffffff;" +
                    "-fx-border-color: #E65C00; -fx-border-radius: 14;" +
                    "-fx-background-radius: 14; -fx-padding: 4 10; -fx-cursor: hand;" +
                    "-fx-font-size: 11px;";
        } else {
            return "-fx-background-color: #2a2a2a; -fx-text-fill: #cccccc;" +
                    "-fx-border-color: #444; -fx-border-radius: 14;" +
                    "-fx-background-radius: 14; -fx-padding: 4 10; -fx-cursor: hand;" +
                    "-fx-font-size: 11px;";
        }
    }

    private void updateFilterButtonIndicator() {
        boolean hasFilters = !selectedGenres.isEmpty()
                || (selectedRating != null && !selectedRating.equals("Любой"))
                || (selectedYear   != null && !selectedYear.equals("Любой"));

        if (hasFilters) {
            filterBtn.setText("🎛 Фильтры ●");
            filterBtn.setStyle(
                    "-fx-background-color: #E65C00; -fx-text-fill: #ffffff;" +
                            "-fx-font-weight: bold; -fx-border-radius: 6;" +
                            "-fx-padding: 8 16; -fx-cursor: hand;"
            );
        } else {
            filterBtn.setText("🎛 Фильтры");
            filterBtn.setStyle(
                    "-fx-background-color: #2a2a2a; -fx-text-fill: #cccccc;" +
                            "-fx-border-color: #444; -fx-border-radius: 6;" +
                            "-fx-padding: 8 16; -fx-cursor: hand;"
            );
        }
    }

    private void applyFilters() {
        String query = searchField.getText().trim().toLowerCase();

        List<Movie> filtered = allMovies.stream()
                .filter(m -> matchesSearch(m, query))
                .filter(this::matchesGenre)
                .filter(this::matchesRating)
                .filter(this::matchesYear)
                .collect(Collectors.toList());

        showMovies(filtered);
    }

    private boolean matchesSearch(Movie movie, String query) {
        if (query.isEmpty()) return true;
        return (movie.getTitle()    != null && movie.getTitle().toLowerCase().contains(query))
                || (movie.getDirector() != null && movie.getDirector().toLowerCase().contains(query))
                || (movie.getGenres()   != null && movie.getGenres().toLowerCase().contains(query));
    }

    private boolean matchesGenre(Movie movie) {
        if (selectedGenres.isEmpty()) return true;
        if (movie.getGenres() == null) return false;
        String g = movie.getGenres().toLowerCase();
        return selectedGenres.stream().anyMatch(sg -> g.contains(sg.toLowerCase()));
    }

    private boolean matchesRating(Movie movie) {
        if (selectedRating == null || selectedRating.equals("Любой")) return true;
        double min = Double.parseDouble(selectedRating.replace("+", ""));
        return movie.getRating() >= min;
    }

    private boolean matchesYear(Movie movie) {
        if (selectedYear == null || selectedYear.equals("Любой")) return true;
        if (selectedYear.contains("-")) {
            String[] parts = selectedYear.split("-");
            int from = Integer.parseInt(parts[0]);
            int to   = Integer.parseInt(parts[1]);
            return movie.getYear() >= from && movie.getYear() <= to;
        }
        return movie.getYear() == Integer.parseInt(selectedYear);
    }

    private VBox createCard(Movie movie) {
        VBox card = new VBox(6);
        card.getStyleClass().add("movie-card");
        card.setPrefWidth(160);

        ImageView poster = new ImageView();
        poster.setFitWidth(160);
        poster.setFitHeight(240);
        poster.setPreserveRatio(false);

        if (movie.getPosterPath() != null && !movie.getPosterPath().isEmpty()) {
            try {
                poster.setImage(new Image(
                        "file:" + movie.getPosterPath(), 160, 240, false, true, true
                ));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        Label titleLbl = new Label(movie.getTitle());
        titleLbl.getStyleClass().add("card-title");
        titleLbl.setWrapText(true);
        titleLbl.setMaxWidth(150);

        Label ratingLbl = new Label("⭐ " + String.format("%.1f", movie.getRating()));
        ratingLbl.getStyleClass().add("card-rating");

        card.getChildren().addAll(poster, titleLbl, ratingLbl);
        card.setOnMouseClicked(e -> openDetail(movie));
        return card;
    }

    private void openDetail(Movie movie) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/focus/fxml/detail.fxml")
            );
            Parent page = loader.load();
            DetailController ctrl = loader.getController();
            ctrl.setMovie(movie);
            BorderPane root = (BorderPane) moviesGrid.getScene().getRoot();
            root.setCenter(page);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
