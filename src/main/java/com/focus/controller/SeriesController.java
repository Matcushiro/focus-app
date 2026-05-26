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
 * SeriesController — вкладка «Сериалы».
 * Секции: Рекомендации (баннер), Новинки, Популярные, Топ по рейтингу, Турецкие.
 * Поиск + фильтры.
 * Всё загружается асинхронно.
 */
public class SeriesController implements Initializable {

    // ===== Баннер =====
    @FXML private ImageView seriesBannerImage;
    @FXML private Label seriesBannerTitle;
    @FXML private Label seriesBannerDesc;
    @FXML private Label seriesBannerRating;
    @FXML private StackPane seriesBannerPane;

    // ===== Секции =====
    @FXML private HBox seriesLatestRow;
    @FXML private HBox seriesPopularRow;
    @FXML private HBox seriesTopRatedRow;
    @FXML private HBox seriesTurkishRow;

    // ===== Поиск и фильтр =====
    @FXML private FlowPane moviesGrid;
    @FXML private TextField searchField;
    @FXML private Button filterBtn;
    @FXML private Label resultsLabel;
    @FXML private VBox seriesSectionsBox;
    @FXML private VBox seriesSearchSection;

    private Movie featuredSeries;
    private List<Movie> allSeries = new ArrayList<>();
    private final Set<String> selectedGenres = new HashSet<>();
    private String selectedRating = null;
    private Popup filterPopup;

    private final DatabaseManager db = DatabaseManager.getInstance();

    private static final List<String> GENRES = Arrays.asList(
            "Драма", "Комедия", "Триллер", "Криминал",
            "Фантастика", "Мелодрама", "Биография", "Исторический",
            "Приключения", "Ужасы"
    );

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        setupSearch();
        buildFilterPopup();
        loadAllAsync();
    }

    // ===== Асинхронная загрузка =====

    private void loadAllAsync() {
        // Все сериалы — для поиска
        db.getAllSeriesAsync()
                .thenAccept(list -> Platform.runLater(() -> allSeries = list))
                .exceptionally(e -> { e.printStackTrace(); return null; });

        // Рекомендованный баннер (из featured сериалов)
        db.getFeaturedMovieAsync()
                .thenAccept(movie -> Platform.runLater(() -> {
                    if (movie != null && "SERIES".equals(movie.getCategory())) {
                        featuredSeries = movie;
                        showBanner(movie);
                    }
                }))
                .exceptionally(e -> { e.printStackTrace(); return null; });

        // Новинки сериалов
        db.getLatestAsync()
                .thenAccept(list -> Platform.runLater(() -> {
                    List<Movie> series = filterByCategory(list, "SERIES");
                    fillRow(seriesLatestRow, series);
                }))
                .exceptionally(e -> { e.printStackTrace(); return null; });

        // Популярные сериалы
        db.getPopularAsync()
                .thenAccept(list -> Platform.runLater(() -> {
                    List<Movie> series = filterByCategory(list, "SERIES");
                    fillRow(seriesPopularRow, series);
                }))
                .exceptionally(e -> { e.printStackTrace(); return null; });

        // Топ по рейтингу
        db.getTopRatedAsync()
                .thenAccept(list -> Platform.runLater(() -> {
                    List<Movie> series = filterByCategory(list, "SERIES");
                    fillRow(seriesTopRatedRow, series);
                }))
                .exceptionally(e -> { e.printStackTrace(); return null; });

        // Турецкие сериалы
        db.getTurkishAsync()
                .thenAccept(list -> Platform.runLater(() -> fillRow(seriesTurkishRow, list)))
                .exceptionally(e -> { e.printStackTrace(); return null; });
    }

    private List<Movie> filterByCategory(List<Movie> list, String category) {
        if (list == null) return new ArrayList<>();
        return list.stream()
                .filter(m -> category.equals(m.getCategory()))
                .collect(Collectors.toList());
    }

    // ===== Баннер =====

    private void showBanner(Movie movie) {
        if (seriesBannerTitle != null) seriesBannerTitle.setText(movie.getTitle());
        if (seriesBannerRating != null)
            seriesBannerRating.setText("⭐ " + String.format("%.1f", movie.getRating()));
        if (seriesBannerDesc != null && movie.getDescription() != null) {
            String[] sentences = movie.getDescription().split("\\. ");
            String shortDesc = sentences.length >= 2
                    ? sentences[0] + ". " + sentences[1] + "."
                    : movie.getDescription();
            seriesBannerDesc.setText(shortDesc);
        }
        if (seriesBannerImage != null) {
            String imagePath = (movie.getBannerPath() != null && !movie.getBannerPath().isEmpty())
                    ? movie.getBannerPath() : movie.getPosterPath();
            if (imagePath != null && !imagePath.isEmpty()) {
                try {
                    Image image = new Image("file:" + imagePath, true);
                    seriesBannerImage.setImage(image);
                    if (seriesBannerPane != null)
                        seriesBannerImage.fitWidthProperty().bind(seriesBannerPane.widthProperty());
                    seriesBannerImage.setFitHeight(350);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @FXML
    private void watchSeriesFeatured() {
        if (featuredSeries != null) openDetail(featuredSeries);
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
        boolean searching = !query.isEmpty() || !selectedGenres.isEmpty()
                || (selectedRating != null && !selectedRating.equals("Любой"));

        if (seriesSectionsBox != null) {
            seriesSectionsBox.setVisible(!searching);
            seriesSectionsBox.setManaged(!searching);
        }
        if (seriesSearchSection != null) {
            seriesSearchSection.setVisible(searching);
            seriesSearchSection.setManaged(searching);
        }

        if (!searching) return;

        List<Movie> filtered = allSeries.stream()
                .filter(m -> matchesSearch(m, query))
                .filter(this::matchesGenre)
                .filter(this::matchesRating)
                .collect(Collectors.toList());

        if (moviesGrid != null) {
            moviesGrid.getChildren().clear();
            for (Movie m : filtered) moviesGrid.getChildren().add(createCard(m));
        }
        if (resultsLabel != null)
            resultsLabel.setText("Найдено: " + filtered.size());
    }

    private boolean matchesSearch(Movie movie, String query) {
        if (query.isEmpty()) return true;
        return (movie.getTitle() != null && movie.getTitle().toLowerCase().contains(query))
                || (movie.getDirector() != null && movie.getDirector().toLowerCase().contains(query))
                || (movie.getGenres() != null && movie.getGenres().toLowerCase().contains(query));
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
                else selectedGenres.remove(genre);
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

        HBox btnRow = new HBox(8);
        btnRow.setAlignment(Pos.CENTER_RIGHT);

        Button resetBtn = new Button("Сбросить");
        resetBtn.setStyle("-fx-background-color: #2a2a2a; -fx-text-fill: #aaaaaa; -fx-border-color: #555; -fx-border-radius: 4; -fx-padding: 6 16;");
        resetBtn.setOnAction(e -> {
            selectedGenres.clear();
            selectedRating = null;
            genreButtons.values().forEach(tb -> { tb.setSelected(false); tb.setStyle(getGenreButtonStyle(false)); });
            ratingBox.setValue("Любой");
            applyFilters();
            updateFilterBtn();
        });

        Button applyBtn = new Button("Применить");
        applyBtn.setStyle("-fx-background-color: #E65C00; -fx-text-fill: #ffffff; -fx-font-weight: bold; -fx-border-radius: 4; -fx-padding: 6 16; -fx-cursor: hand;");
        applyBtn.setOnAction(e -> {
            selectedRating = ratingBox.getValue();
            applyFilters();
            updateFilterBtn();
            filterPopup.hide();
        });

        btnRow.getChildren().addAll(resetBtn, applyBtn);
        popupBox.getChildren().addAll(title, new Separator(), genresLabel, genreFlow, ratingLabel, ratingBox, new Separator(), btnRow);
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
                || (selectedRating != null && !selectedRating.equals("Любой"));
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
            BorderPane root = (BorderPane) (moviesGrid != null
                    ? moviesGrid.getScene().getRoot()
                    : seriesBannerPane.getScene().getRoot());
            root.setCenter(page);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
