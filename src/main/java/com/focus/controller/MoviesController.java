package com.focus.controller;

import com.focus.database.DatabaseManager;
import com.focus.model.Movie;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
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

/**
 * MoviesController — вкладка «Фильмы».
 * Секции: Новинки, Популярные, Топ по рейтингу, Вечерние, Топ-10.
 * Поиск + фильтры по жанру/рейтингу/году.
 * Всё загружается асинхронно.
 */
public class MoviesController implements Initializable {

    // ===== Секции =====
    @FXML private HBox moviesLatestRow;
    @FXML private HBox moviesPopularRow;
    @FXML private HBox moviesTopRatedRow;
    @FXML private HBox moviesEveningRow;
    @FXML private HBox moviesTop10Row;

    // ===== Поиск и фильтр =====
    @FXML private FlowPane moviesGrid;
    @FXML private TextField searchField;
    @FXML private Button filterBtn;
    @FXML private Label resultsLabel;
    @FXML private VBox moviesSectionsBox;
    @FXML private VBox moviesSearchSection;

    private List<Movie> allMovies = new ArrayList<>();
    private final Set<String> selectedGenres = new HashSet<>();
    private String selectedRating = null;
    private String selectedYear   = null;
    private Popup filterPopup;

    private final DatabaseManager db = DatabaseManager.getInstance();

    private static final List<String> GENRES = Arrays.asList(
            "Драма", "Комедия", "Триллер", "Боевик", "Фантастика",
            "Ужасы", "Мелодрама", "Анимация", "Документальный",
            "Криминал", "Приключения", "Биография"
    );

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        setupSearch();
        buildFilterPopup();
        loadAllAsync();
    }

    // ===== Асинхронная загрузка =====
    private void loadAllAsync() {
        // Все фильмы — для поиска
        db.getAllMoviesAsync()
                .thenAccept(list -> Platform.runLater(() -> allMovies = list))
                .exceptionally(e -> { e.printStackTrace(); return null; });

        // Новинки фильмов
        db.getLatestAsync()
                .thenAccept(list -> Platform.runLater(() -> {
                    List<Movie> films = filterByCategory(list, "FILM");
                    fillRow(moviesLatestRow, films);
                }))
                .exceptionally(e -> { e.printStackTrace(); return null; });

        // Популярные фильмы
        db.getPopularAsync()
                .thenAccept(list -> Platform.runLater(() -> {
                    List<Movie> films = filterByCategory(list, "FILM");
                    fillRow(moviesPopularRow, films);
                }))
                .exceptionally(e -> { e.printStackTrace(); return null; });

        // Топ по рейтингу
        db.getTopRatedAsync()
                .thenAccept(list -> Platform.runLater(() -> {
                    List<Movie> films = filterByCategory(list, "FILM");
                    fillRow(moviesTopRatedRow, films);
                }))
                .exceptionally(e -> { e.printStackTrace(); return null; });

        // Вечерние
        db.getEveningAsync()
                .thenAccept(list -> Platform.runLater(() -> fillRow(moviesEveningRow, list)))
                .exceptionally(e -> { e.printStackTrace(); return null; });

        // Топ-10
        db.getTop10Async()
                .thenAccept(list -> Platform.runLater(() -> fillRow(moviesTop10Row, list)))
                .exceptionally(e -> { e.printStackTrace(); return null; });
    }

    private List<Movie> filterByCategory(List<Movie> list, String category) {
        if (list == null) return new ArrayList<>();
        return list.stream()
                .filter(m -> category.equals(m.getCategory()))
                .collect(Collectors.toList());
    }

    // ===== Поиск =====
    private void setupSearch() {
        if (searchField != null) {
            searchField.textProperty().addListener((obs, o, n) -> applyFilters());
        }
    }

    @FXML
    private void openFilterPopup() {
        if (filterBtn == null || filterPopup == null) return;
        if (filterPopup.isShowing()) {
            filterPopup.hide();
        } else {
            var bounds = filterBtn.localToScreen(filterBtn.getBoundsInLocal());
            filterPopup.show(filterBtn, bounds.getMinX(), bounds.getMaxY() + 4);
        }
    }

    private void applyFilters() {
        String query = searchField != null ? searchField.getText().trim().toLowerCase() : "";
        boolean searching = !query.isEmpty()
                || !selectedGenres.isEmpty()
                || (selectedRating != null && !selectedRating.equals("Любой"))
                || (selectedYear   != null && !selectedYear.equals("Любой"));

        if (moviesSectionsBox != null) {
            moviesSectionsBox.setVisible(!searching);
            moviesSectionsBox.setManaged(!searching);
        }
        if (moviesSearchSection != null) {
            moviesSearchSection.setVisible(searching);
            moviesSearchSection.setManaged(searching);
        }

        if (!searching) return;

        List<Movie> filtered = allMovies.stream()
                .filter(m -> matchesSearch(m, query))
                .filter(this::matchesGenre)
                .filter(this::matchesRating)
                .filter(this::matchesYear)
                .collect(Collectors.toList());

        if (moviesGrid != null) {
            moviesGrid.getChildren().clear();
            for (Movie m : filtered) moviesGrid.getChildren().add(createCard(m));
        }
        if (resultsLabel != null) resultsLabel.setText("Найдено: " + filtered.size());
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

    // ===== Попап фильтров =====
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

        Label title = new Label("🎛 Фильтры");
        title.setStyle("-fx-text-fill: #ffffff; -fx-font-size: 15px; -fx-font-weight: bold;");

        Label genresLabel = new Label("Жанры:");
        genresLabel.setStyle("-fx-text-fill: #aaaaaa; -fx-font-size: 12px;");

        FlowPane genreFlow = new FlowPane(6, 6);
        genreFlow.setPrefWrapLength(290);

        Map<String, ToggleButton> genreButtons = new LinkedHashMap<>();
        for (String genre : GENRES) {
            ToggleButton tb = new ToggleButton(genre);
            tb.setSelected(selectedGenres.contains(genre));
            tb.setStyle(getGenreButtonStyle(tb.isSelected()));
            tb.selectedProperty().addListener((obs, old, sel) -> {
                tb.setStyle(getGenreButtonStyle(sel));
                if (sel) selectedGenres.add(genre);
                else     selectedGenres.remove(genre);
            });
            genreButtons.put(genre, tb);
            genreFlow.getChildren().add(tb);
        }

        Label ratingLabel = new Label("Минимальный рейтинг:");
        ratingLabel.setStyle("-fx-text-fill: #aaaaaa; -fx-font-size: 12px;");

        ComboBox<String> ratingBox = new ComboBox<>();
        ratingBox.setItems(FXCollections.observableArrayList("Любой", "9+", "8+", "7+", "6+"));
        ratingBox.setValue(selectedRating != null ? selectedRating : "Любой");
        ratingBox.setStyle("-fx-background-color: #2a2a2a; -fx-text-fill: #ffffff; -fx-border-color: #444; -fx-border-radius: 4; -fx-pref-width: 290;");

        Label yearLabel = new Label("Год выпуска:");
        yearLabel.setStyle("-fx-text-fill: #aaaaaa; -fx-font-size: 12px;");

        ComboBox<String> yearBox = new ComboBox<>();
        yearBox.setItems(FXCollections.observableArrayList(
                "Любой", "2025", "2024", "2023", "2022", "2021", "2020", "2010-2019", "2000-2009"
        ));
        yearBox.setValue(selectedYear != null ? selectedYear : "Любой");
        yearBox.setStyle("-fx-background-color: #2a2a2a; -fx-text-fill: #ffffff; -fx-border-color: #444; -fx-border-radius: 4; -fx-pref-width: 290;");

        HBox btnRow = new HBox(8);
        btnRow.setAlignment(Pos.CENTER_RIGHT);

        Button resetBtn = new Button("Сбросить");
        resetBtn.setStyle("-fx-background-color: #2a2a2a; -fx-text-fill: #aaaaaa; -fx-border-color: #555; -fx-border-radius: 4; -fx-padding: 6 16;");
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
            updateFilterBtn();
        });

        Button applyBtn = new Button("Применить");
        applyBtn.setStyle("-fx-background-color: #E65C00; -fx-text-fill: #ffffff; -fx-font-weight: bold; -fx-border-radius: 4; -fx-padding: 6 16; -fx-cursor: hand;");
        applyBtn.setOnAction(e -> {
            selectedRating = ratingBox.getValue();
            selectedYear   = yearBox.getValue();
            applyFilters();
            updateFilterBtn();
            filterPopup.hide();
        });

        btnRow.getChildren().addAll(resetBtn, applyBtn);

        popupBox.getChildren().addAll(
                title, new Separator(),
                genresLabel, genreFlow,
                ratingLabel, ratingBox,
                yearLabel, yearBox,
                new Separator(), btnRow
        );
        filterPopup.getContent().add(popupBox);
    }

    private String getGenreButtonStyle(boolean selected) {
        return selected
                ? "-fx-background-color: #E65C00; -fx-text-fill: #ffffff; -fx-border-color: #E65C00; -fx-border-radius: 14; -fx-background-radius: 14; -fx-padding: 4 10; -fx-cursor: hand; -fx-font-size: 11px;"
                : "-fx-background-color: #2a2a2a; -fx-text-fill: #cccccc; -fx-border-color: #444; -fx-border-radius: 14; -fx-background-radius: 14; -fx-padding: 4 10; -fx-cursor: hand; -fx-font-size: 11px;";
    }

    private void updateFilterBtn() {
        if (filterBtn == null) return;
        boolean hasFilters = !selectedGenres.isEmpty()
                || (selectedRating != null && !selectedRating.equals("Любой"))
                || (selectedYear   != null && !selectedYear.equals("Любой"));
        if (hasFilters) {
            filterBtn.setText("🎛 Фильтры ●");
            filterBtn.setStyle("-fx-background-color: #E65C00; -fx-text-fill: #ffffff; -fx-font-weight: bold; -fx-border-radius: 6; -fx-padding: 8 16; -fx-cursor: hand;");
        } else {
            filterBtn.setText("🎛 Фильтры");
            filterBtn.setStyle("-fx-background-color: #2a2a2a; -fx-text-fill: #cccccc; -fx-border-color: #444; -fx-border-radius: 6; -fx-padding: 8 16; -fx-cursor: hand;");
        }
    }

    // ===== Карточки =====
    private void fillRow(HBox row, List<Movie> movies) {
        if (row == null) return;
        row.getChildren().clear();
        for (Movie movie : movies) row.getChildren().add(createCard(movie));
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
            // Ищем корневой BorderPane через любой доступный узел
            javafx.scene.Node ref = moviesGrid != null ? moviesGrid : moviesLatestRow;
            BorderPane root = (BorderPane) ref.getScene().getRoot();
            root.setCenter(page);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
