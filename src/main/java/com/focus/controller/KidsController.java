package com.focus.controller;

import com.focus.database.DatabaseManager;
import com.focus.model.Movie;
import javafx.application.Platform;
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
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Popup;

import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

/**
 * KidsController — детская вкладка.
 * Разделена на секции: баннер, все, фильмы, сериалы, популярные, новинки.
 * Всё загружается асинхронно через CompletableFuture.
 */
public class KidsController implements Initializable {

    // ===== Баннер =====
    @FXML private ImageView kidsBannerImage;
    @FXML private Label kidsBannerTitle;
    @FXML private Label kidsBannerDesc;
    @FXML private Label kidsBannerRating;
    @FXML private StackPane kidsBannerPane;

    // ===== Секции =====
    @FXML private HBox kidsAllRow;
    @FXML private HBox kidsFilmsRow;
    @FXML private HBox kidsSeriesRow;
    @FXML private HBox kidsPopularRow;
    @FXML private HBox kidsLatestRow;

    // ===== Поиск и фильтр =====
    @FXML private TextField kidsSearchField;
    @FXML private Button kidsFilterBtn;
    @FXML private Label kidsResultsLabel;

    // ===== Грид для поиска =====
    @FXML private FlowPane kidsSearchGrid;
    @FXML private VBox kidsSearchSection;
    @FXML private VBox kidsSectionsBox;

    private Movie featuredKidsMovie;
    private List<Movie> allKids = new ArrayList<>();
    private final Set<String> selectedGenres = new HashSet<>();
    private String selectedRating = null;
    private Popup filterPopup;

    private final DatabaseManager db = DatabaseManager.getInstance();

    private static final List<String> GENRES = Arrays.asList(
            "Анимация", "Семейный", "Приключения", "Комедия",
            "Фантастика", "Сказка", "Музыкальный", "Документальный"
    );

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        setupSearch();
        buildFilterPopup();
        loadAllAsync();
    }

    // ===== Асинхронная загрузка =====

    private void loadAllAsync() {
        // Баннер
        db.getKidsFeaturedAsync().thenAccept(movie -> Platform.runLater(() -> {
            if (movie != null) {
                featuredKidsMovie = movie;
                showKidsBanner(movie);
            }
        }));

        // Все детские (для поиска)
        db.getKidsMoviesAsync().thenAccept(list -> Platform.runLater(() -> {
            allKids = list;
        }));

        // Все детские (первая секция)
        db.getKidsMoviesAsync().thenAccept(list -> Platform.runLater(() ->
                fillRow(kidsAllRow, list)
        ));

        // Детские фильмы
        db.getKidsFilmsAsync().thenAccept(list -> Platform.runLater(() ->
                fillRow(kidsFilmsRow, list)
        ));

        // Детские сериалы
        db.getKidsSeriesAsync().thenAccept(list -> Platform.runLater(() ->
                fillRow(kidsSeriesRow, list)
        ));

        // Популярные
        db.getKidsPopularAsync().thenAccept(list -> Platform.runLater(() ->
                fillRow(kidsPopularRow, list)
        ));

        // Новинки
        db.getKidsLatestAsync().thenAccept(list -> Platform.runLater(() ->
                fillRow(kidsLatestRow, list)
        ));
    }

    // ===== Баннер =====

    private void showKidsBanner(Movie movie) {
        if (kidsBannerTitle != null) kidsBannerTitle.setText(movie.getTitle());
        if (kidsBannerRating != null)
            kidsBannerRating.setText("⭐ " + String.format("%.1f", movie.getRating()));
        if (kidsBannerDesc != null && movie.getDescription() != null) {
            String[] sentences = movie.getDescription().split("\\. ");
            String shortDesc = sentences.length >= 2
                    ? sentences[0] + ". " + sentences[1] + "."
                    : movie.getDescription();
            kidsBannerDesc.setText(shortDesc);
        }
        if (kidsBannerImage != null) {
            String imagePath = (movie.getBannerPath() != null && !movie.getBannerPath().isEmpty())
                    ? movie.getBannerPath() : movie.getPosterPath();
            if (imagePath != null && !imagePath.isEmpty()) {
                try {
                    Image image = new Image("file:" + imagePath, true);
                    kidsBannerImage.setImage(image);
                    if (kidsBannerPane != null)
                        kidsBannerImage.fitWidthProperty().bind(kidsBannerPane.widthProperty());
                    kidsBannerImage.setFitHeight(350);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @FXML
    private void watchKidsFeatured() {
        if (featuredKidsMovie != null) openDetail(featuredKidsMovie);
    }

    // ===== Поиск =====

    private void setupSearch() {
        if (kidsSearchField != null) {
            kidsSearchField.textProperty().addListener((obs, o, n) -> applySearch(n.trim()));
        }
    }

    private void applySearch(String query) {
        boolean searching = !query.isEmpty() || !selectedGenres.isEmpty()
                || (selectedRating != null && !selectedRating.equals("Любой"));

        if (kidsSectionsBox != null) kidsSectionsBox.setVisible(!searching);
        if (kidsSectionsBox != null) kidsSectionsBox.setManaged(!searching);
        if (kidsSearchSection != null) kidsSearchSection.setVisible(searching);
        if (kidsSearchSection != null) kidsSearchSection.setManaged(searching);

        if (!searching) return;

        List<Movie> filtered = allKids.stream()
                .filter(m -> matchesSearch(m, query.toLowerCase()))
                .filter(this::matchesGenre)
                .filter(this::matchesRating)
                .collect(Collectors.toList());

        if (kidsSearchGrid != null) {
            kidsSearchGrid.getChildren().clear();
            for (Movie m : filtered) kidsSearchGrid.getChildren().add(createCard(m));
        }
        if (kidsResultsLabel != null)
            kidsResultsLabel.setText("Найдено: " + filtered.size());
    }

    private boolean matchesSearch(Movie movie, String query) {
        if (query.isEmpty()) return true;
        return (movie.getTitle() != null && movie.getTitle().toLowerCase().contains(query))
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

    // ===== Фильтр =====

    private void buildFilterPopup() {
        filterPopup = new Popup();
        filterPopup.setAutoHide(true);

        VBox box = new VBox(12);
        box.setStyle(
                "-fx-background-color: #1a2a1a;" +
                        "-fx-border-color: #4CAF50;" +
                        "-fx-border-width: 1;" +
                        "-fx-border-radius: 6;" +
                        "-fx-background-radius: 6;" +
                        "-fx-padding: 16;" +
                        "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.8), 20, 0, 0, 4);"
        );
        box.setPrefWidth(300);

        Label title = new Label("🎛 Фильтры (Детское)");
        title.setStyle("-fx-text-fill: #ffffff; -fx-font-size: 14px; -fx-font-weight: bold;");

        Label genresLbl = new Label("Жанры:");
        genresLbl.setStyle("-fx-text-fill: #aaaaaa; -fx-font-size: 12px;");

        FlowPane genreFlow = new FlowPane(6, 6);
        Map<String, ToggleButton> genreButtons = new LinkedHashMap<>();
        for (String genre : GENRES) {
            ToggleButton tb = new ToggleButton(genre);
            tb.setSelected(selectedGenres.contains(genre));
            tb.setStyle(genreStyle(tb.isSelected()));
            tb.selectedProperty().addListener((obs, old, sel) -> {
                tb.setStyle(genreStyle(sel));
                if (sel) selectedGenres.add(genre);
                else selectedGenres.remove(genre);
            });
            genreButtons.put(genre, tb);
            genreFlow.getChildren().add(tb);
        }

        Label ratingLbl = new Label("Мин. рейтинг:");
        ratingLbl.setStyle("-fx-text-fill: #aaaaaa; -fx-font-size: 12px;");

        ComboBox<String> ratingBox = new ComboBox<>();
        ratingBox.setItems(FXCollections.observableArrayList("Любой", "9+", "8+", "7+", "6+"));
        ratingBox.setValue(selectedRating != null ? selectedRating : "Любой");
        ratingBox.setStyle("-fx-background-color: #2a2a2a; -fx-text-fill: #ffffff; -fx-pref-width: 280;");

        HBox btnRow = new HBox(8);
        btnRow.setAlignment(Pos.CENTER_RIGHT);

        Button resetBtn = new Button("Сбросить");
        resetBtn.setStyle("-fx-background-color: #2a2a2a; -fx-text-fill: #aaaaaa; -fx-padding: 6 16;");
        resetBtn.setOnAction(e -> {
            selectedGenres.clear();
            selectedRating = null;
            genreButtons.values().forEach(tb -> { tb.setSelected(false); tb.setStyle(genreStyle(false)); });
            ratingBox.setValue("Любой");
            applySearch(kidsSearchField != null ? kidsSearchField.getText().trim() : "");
        });

        Button applyBtn = new Button("Применить");
        applyBtn.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: #ffffff; -fx-font-weight: bold; -fx-padding: 6 16; -fx-cursor: hand;");
        applyBtn.setOnAction(e -> {
            selectedRating = ratingBox.getValue();
            applySearch(kidsSearchField != null ? kidsSearchField.getText().trim() : "");
            filterPopup.hide();
        });

        btnRow.getChildren().addAll(resetBtn, applyBtn);
        box.getChildren().addAll(title, new Separator(), genresLbl, genreFlow, ratingLbl, ratingBox, new Separator(), btnRow);
        filterPopup.getContent().add(box);
    }

    @FXML
    private void openKidsFilterPopup() {
        if (kidsFilterBtn == null) return;
        if (filterPopup.isShowing()) {
            filterPopup.hide();
        } else {
            var bounds = kidsFilterBtn.localToScreen(kidsFilterBtn.getBoundsInLocal());
            filterPopup.show(kidsFilterBtn, bounds.getMinX(), bounds.getMaxY() + 4);
        }
    }

    private String genreStyle(boolean selected) {
        return selected
                ? "-fx-background-color: #4CAF50; -fx-text-fill: #ffffff; -fx-border-color: #4CAF50; -fx-border-radius: 14; -fx-background-radius: 14; -fx-padding: 4 10; -fx-cursor: hand; -fx-font-size: 11px;"
                : "-fx-background-color: #1e2d1e; -fx-text-fill: #cccccc; -fx-border-color: #3a3a3a; -fx-border-radius: 14; -fx-background-radius: 14; -fx-padding: 4 10; -fx-cursor: hand; -fx-font-size: 11px;";
    }

    // ===== Карточки =====

    private void fillRow(HBox row, List<Movie> movies) {
        if (row == null) return;
        row.getChildren().clear();
        for (Movie movie : movies) {
            row.getChildren().add(createCard(movie));
        }
    }

    private VBox createCard(Movie movie) {
        VBox card = new VBox(6);
        card.getStyleClass().add("movie-card");
        card.setPrefWidth(150);

        // Постер
        ImageView poster = new ImageView();
        poster.setFitWidth(150);
        poster.setFitHeight(210);
        poster.setPreserveRatio(false);
        if (movie.getPosterPath() != null && !movie.getPosterPath().isEmpty()) {
            try {
                poster.setImage(new Image(
                        "file:" + movie.getPosterPath(), 150, 210, false, true, true
                ));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // Иконка детского контента
        StackPane posterBox = new StackPane(poster);
        if (movie.isKids() || "KIDS".equals(movie.getCategory())) {
            Label kidsIcon = new Label("👶");
            kidsIcon.setStyle("-fx-font-size: 16px;");
            StackPane.setAlignment(kidsIcon, Pos.TOP_RIGHT);
            StackPane.setMargin(kidsIcon, new Insets(4, 4, 0, 0));
            posterBox.getChildren().add(kidsIcon);
        }

        // Тип контента
        String typeText = "SERIES".equals(movie.getCategory()) ? "📺 Сериал" : "🎬 Фильм";
        Label typeLbl = new Label(typeText);
        typeLbl.setStyle("-fx-text-fill: #4CAF50; -fx-font-size: 10px; -fx-font-weight: bold;");

        Label titleLbl = new Label(movie.getTitle());
        titleLbl.getStyleClass().add("card-title");
        titleLbl.setWrapText(true);
        titleLbl.setMaxWidth(145);

        Label ratingLbl = new Label("⭐ " + String.format("%.1f", movie.getRating()));
        ratingLbl.getStyleClass().add("card-rating");

        card.getChildren().addAll(posterBox, typeLbl, titleLbl, ratingLbl);
        card.setOnMouseClicked(e -> openDetail(movie));
        return card;
    }

    // ===== Навигация =====

    private void openDetail(Movie movie) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/focus/fxml/detail.fxml")
            );
            Parent page = loader.load();
            DetailController ctrl = loader.getController();
            ctrl.setMovie(movie);
            BorderPane root = (BorderPane) (kidsAllRow != null
                    ? kidsAllRow.getScene().getRoot()
                    : kidsSearchGrid.getScene().getRoot());
            root.setCenter(page);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
