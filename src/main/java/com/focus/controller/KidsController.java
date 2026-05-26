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
import javafx.stage.Popup;

import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

/**
 * KidsController — детская вкладка.
 * Секции: все, фильмы, сериалы, популярные, новинки.
 * Все данные загружаются асинхронно. Поиск + фильтры.
 *
 * ИСПРАВЛЕНИЯ:
 * - Устранён двойной запрос getKidsMoviesAsync() (был и для allKids, и для kidsAllRow)
 * - Поиск теперь корректно обрабатывает пустой список при незагруженных данных
 * - Навигация назад через общий навигатор
 */
public class KidsController implements Initializable {

    // ===== Секции =====
    @FXML private HBox kidsAllRow;
    @FXML private HBox kidsFilmsRow;
    @FXML private HBox kidsSeriesRow;
    @FXML private HBox kidsPopularRow;
    @FXML private HBox kidsLatestRow;

    // ===== Поиск и фильтр =====
    @FXML private TextField kidsSearchField;
    @FXML private Button    kidsFilterBtn;
    @FXML private Label     kidsResultsLabel;

    // ===== Грид для поиска =====
    @FXML private FlowPane kidsSearchGrid;
    @FXML private VBox     kidsSearchSection;
    @FXML private VBox     kidsSectionsBox;

    // ИСПРАВЛЕНИЕ: один общий список, заполняемый одним запросом
    private List<Movie> allKids = new ArrayList<>();

    private final Set<String>  selectedGenres = new HashSet<>();
    private String selectedRating = null;
    private Popup  filterPopup;

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
        // ИСПРАВЛЕНИЕ: один запрос для allKids — результат используется и для поиска, и для первой секции
        db.getKidsMoviesAsync()
                .thenAccept(list -> Platform.runLater(() -> {
                    allKids = list;
                    fillRow(kidsAllRow, list);
                }))
                .exceptionally(e -> { e.printStackTrace(); return null; });

        // Детские фильмы
        db.getKidsFilmsAsync()
                .thenAccept(list -> Platform.runLater(() -> fillRow(kidsFilmsRow, list)))
                .exceptionally(e -> { e.printStackTrace(); return null; });

        // Детские сериалы
        db.getKidsSeriesAsync()
                .thenAccept(list -> Platform.runLater(() -> fillRow(kidsSeriesRow, list)))
                .exceptionally(e -> { e.printStackTrace(); return null; });

        // Популярные детские
        db.getKidsPopularAsync()
                .thenAccept(list -> Platform.runLater(() -> fillRow(kidsPopularRow, list)))
                .exceptionally(e -> { e.printStackTrace(); return null; });

        // Новинки детских
        db.getKidsLatestAsync()
                .thenAccept(list -> Platform.runLater(() -> fillRow(kidsLatestRow, list)))
                .exceptionally(e -> { e.printStackTrace(); return null; });
    }

    // ===== Поиск =====

    private void setupSearch() {
        if (kidsSearchField != null) {
            kidsSearchField.textProperty().addListener(
                    (obs, oldVal, newVal) -> applySearch(newVal.trim())
            );
        }
    }

    private void applySearch(String query) {
        boolean searching = !query.isEmpty()
                || !selectedGenres.isEmpty()
                || (selectedRating != null && !selectedRating.equals("Любой"));

        if (kidsSectionsBox != null) {
            kidsSectionsBox.setVisible(!searching);
            kidsSectionsBox.setManaged(!searching);
        }
        if (kidsSearchSection != null) {
            kidsSearchSection.setVisible(searching);
            kidsSearchSection.setManaged(searching);
        }

        if (!searching) return;

        // ИСПРАВЛЕНИЕ: если данные ещё не загружены — показываем пустой список без краша
        if (allKids.isEmpty()) {
            if (kidsResultsLabel != null) kidsResultsLabel.setText("Загрузка данных...");
            return;
        }

        List<Movie> filtered = allKids.stream()
                .filter(m -> matchesSearch(m, query.toLowerCase()))
                .filter(this::matchesGenre)
                .filter(this::matchesRating)
                .collect(Collectors.toList());

        if (kidsSearchGrid != null) {
            kidsSearchGrid.getChildren().clear();
            for (Movie m : filtered) {
                kidsSearchGrid.getChildren().add(createCard(m));
            }
        }
        if (kidsResultsLabel != null) {
            kidsResultsLabel.setText("Найдено: " + filtered.size());
        }
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
                "-fx-background-color: #0f1a0f;" +
                        "-fx-border-color: #4CAF50;" +
                        "-fx-border-width: 1;" +
                        "-fx-border-radius: 8;" +
                        "-fx-background-radius: 8;" +
                        "-fx-padding: 16;" +
                        "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.85), 24, 0, 0, 6);"
        );
        box.setPrefWidth(320);

        Label title = new Label("🎛 Фильтры (Детское)");
        title.setStyle("-fx-text-fill: #ffffff; -fx-font-size: 14px; -fx-font-weight: bold;");

        Label genresLbl = new Label("Жанры:");
        genresLbl.setStyle("-fx-text-fill: #aaaaaa; -fx-font-size: 12px;");

        FlowPane genreFlow = new FlowPane(6, 6);
        genreFlow.setPrefWrapLength(288);

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
        ratingBox.setStyle(
                "-fx-background-color: #1a2d1a;" +
                        "-fx-text-fill: #ffffff;" +
                        "-fx-pref-width: 290;"
        );

        HBox btnRow = new HBox(8);
        btnRow.setAlignment(Pos.CENTER_RIGHT);

        Button resetBtn = new Button("Сбросить");
        resetBtn.setStyle(
                "-fx-background-color: #1a2d1a; -fx-text-fill: #aaaaaa;" +
                        "-fx-padding: 6 16; -fx-border-color: #3a5a3a; -fx-border-radius: 4;"
        );
        resetBtn.setOnAction(e -> {
            selectedGenres.clear();
            selectedRating = null;
            genreButtons.values().forEach(tb -> {
                tb.setSelected(false);
                tb.setStyle(genreStyle(false));
            });
            ratingBox.setValue("Любой");
            applySearch(kidsSearchField != null ? kidsSearchField.getText().trim() : "");
            updateFilterBtnStyle(false);
        });

        Button applyBtn = new Button("Применить");
        applyBtn.setStyle(
                "-fx-background-color: #4CAF50; -fx-text-fill: #ffffff;" +
                        "-fx-font-weight: bold; -fx-padding: 6 16;" +
                        "-fx-cursor: hand; -fx-border-radius: 4;"
        );
        applyBtn.setOnAction(e -> {
            selectedRating = ratingBox.getValue();
            applySearch(kidsSearchField != null ? kidsSearchField.getText().trim() : "");
            updateFilterBtnStyle(!selectedGenres.isEmpty()
                    || (selectedRating != null && !selectedRating.equals("Любой")));
            filterPopup.hide();
        });

        btnRow.getChildren().addAll(resetBtn, applyBtn);

        box.getChildren().addAll(
                title, new Separator(),
                genresLbl, genreFlow,
                ratingLbl, ratingBox,
                new Separator(), btnRow
        );

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

    /**
     * Обновляет визуальный стиль кнопки фильтра (активен/не активен).
     */
    private void updateFilterBtnStyle(boolean hasFilters) {
        if (kidsFilterBtn == null) return;
        if (hasFilters) {
            kidsFilterBtn.setText("🎛 Фильтры ●");
            kidsFilterBtn.setStyle(
                    "-fx-background-color: #4CAF50; -fx-text-fill: #ffffff;" +
                            "-fx-font-weight: bold; -fx-border-radius: 6; -fx-padding: 8 16; -fx-cursor: hand;"
            );
        } else {
            kidsFilterBtn.setText("🎛 Фильтры");
            kidsFilterBtn.setStyle("");  // сбрасываем к CSS-классу
        }
    }

    private String genreStyle(boolean selected) {
        return selected
                ? "-fx-background-color: #4CAF50; -fx-text-fill: #ffffff;" +
                  "-fx-border-color: #4CAF50; -fx-border-radius: 14; -fx-background-radius: 14;" +
                  "-fx-padding: 4 10; -fx-cursor: hand; -fx-font-size: 11px;"
                : "-fx-background-color: #1a2d1a; -fx-text-fill: #cccccc;" +
                  "-fx-border-color: #3a5a3a; -fx-border-radius: 14; -fx-background-radius: 14;" +
                  "-fx-padding: 4 10; -fx-cursor: hand; -fx-font-size: 11px;";
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
        card.getStyleClass().addAll("movie-card", "kids-card");
        card.setPrefWidth(150);

        // Постер
        ImageView poster = new ImageView();
        poster.setFitWidth(150);
        poster.setFitHeight(210);
        poster.setPreserveRatio(false);

        if (movie.getPosterPath() != null && !movie.getPosterPath().isEmpty()) {
            try {
                poster.setImage(new Image(
                        "file:" + movie.getPosterPath(),
                        150, 210, false, true, true
                ));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // Иконка детского контента в углу постера
        StackPane posterBox = new StackPane(poster);
        posterBox.getStyleClass().add("kids-poster-box");

        if (movie.isKids() || "KIDS".equals(movie.getCategory())) {
            Label kidsIcon = new Label("👶");
            kidsIcon.getStyleClass().add("kids-corner-icon");
            StackPane.setAlignment(kidsIcon, Pos.TOP_RIGHT);
            StackPane.setMargin(kidsIcon, new Insets(4, 4, 0, 0));
            posterBox.getChildren().add(kidsIcon);
        }

        // Тип контента
        String typeText = "SERIES".equals(movie.getCategory()) ? "📺 Сериал" : "🎬 Фильм";
        Label typeLbl = new Label(typeText);
        typeLbl.getStyleClass().add("kids-type-label");

        Label titleLbl = new Label(movie.getTitle());
        titleLbl.getStyleClass().addAll("card-title", "kids-card-title");
        titleLbl.setWrapText(true);
        titleLbl.setMaxWidth(145);

        Label ratingLbl = new Label("⭐ " + String.format("%.1f", movie.getRating()));
        ratingLbl.getStyleClass().addAll("card-rating", "kids-card-rating");

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

            // ИСПРАВЛЕНИЕ: безопасный поиск корня — берём первый доступный узел
            javafx.scene.Node ref = getAnyNode();
            if (ref == null) return;
            BorderPane root = (BorderPane) ref.getScene().getRoot();
            root.setCenter(page);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** Возвращает первый не-null FXML-узел для доступа к сцене */
    private javafx.scene.Node getAnyNode() {
        if (kidsAllRow      != null) return kidsAllRow;
        if (kidsSearchGrid  != null) return kidsSearchGrid;
        if (kidsSectionsBox != null) return kidsSectionsBox;
        return null;
    }
}
