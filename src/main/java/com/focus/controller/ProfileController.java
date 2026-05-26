package com.focus.controller;

import com.focus.database.DatabaseManager;
import com.focus.model.Movie;
import com.focus.model.User;
import com.focus.service.SessionManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

public class ProfileController implements Initializable {

    @FXML private Label usernameLabel;
    @FXML private Label emailLabel;
    @FXML private Label avatarLabel;
    @FXML private Label watchedCount;
    @FXML private Label favoritesCount;
    @FXML private HBox favoritesRow;
    @FXML private HBox historyRow;

    @FXML private VBox editForm;
    @FXML private TextField editUsername;
    @FXML private TextField editEmail;
    @FXML private PasswordField editPassword;
    @FXML private Label editError;

    private final DatabaseManager db = DatabaseManager.getInstance();
    private User currentUser;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        currentUser = SessionManager.getInstance().getCurrentUser();

        if (currentUser != null) {
            loadProfile();
        } else {
            // Пользователь не вошёл — перенаправляем на логин
            Platform.runLater(this::goToLogin);
        }
    }

    private void loadProfile() {
        // Обновляем UI на JavaFX потоке
        if (usernameLabel != null) usernameLabel.setText(currentUser.getUsername());
        if (emailLabel != null) emailLabel.setText(
                currentUser.getEmail() != null && !currentUser.getEmail().isBlank()
                        ? currentUser.getEmail()
                        : "Email не указан"
        );
        if (avatarLabel != null) avatarLabel.setText(
                String.valueOf(currentUser.getUsername().charAt(0)).toUpperCase()
        );

        // Скрываем форму редактирования при загрузке профиля
        if (editForm != null) {
            editForm.setVisible(false);
            editForm.setManaged(false);
        }

        // Асинхронно загружаем избранное и историю
        loadFavoritesAsync();
        loadHistoryAsync();
    }

    private void loadFavoritesAsync() {
        db.async(() -> db.getFavorites(currentUser.getId()))
                .thenAccept(favs -> Platform.runLater(() -> {
                    if (favoritesCount != null)
                        favoritesCount.setText(String.valueOf(favs.size()));
                    if (favoritesRow != null) {
                        favoritesRow.getChildren().clear();
                        if (favs.isEmpty()) {
                            Label empty = new Label("Вы ещё ничего не добавили в избранное");
                            empty.getStyleClass().add("filter-label");
                            favoritesRow.getChildren().add(empty);
                        } else {
                            for (Movie m : favs) {
                                favoritesRow.getChildren().add(createCard(m));
                            }
                        }
                    }
                }))
                .exceptionally(e -> { e.printStackTrace(); return null; });
    }

    private void loadHistoryAsync() {
        db.async(() -> db.getWatchHistory(currentUser.getId()))
                .thenAccept(history -> Platform.runLater(() -> {
                    if (watchedCount != null)
                        watchedCount.setText(String.valueOf(history.size()));
                    if (historyRow != null) {
                        historyRow.getChildren().clear();
                        if (history.isEmpty()) {
                            Label empty = new Label("История просмотров пуста");
                            empty.getStyleClass().add("filter-label");
                            historyRow.getChildren().add(empty);
                        } else {
                            for (Movie m : history) {
                                historyRow.getChildren().add(createCard(m));
                            }
                        }
                    }
                }))
                .exceptionally(e -> { e.printStackTrace(); return null; });
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

        Label title = new Label(movie.getTitle());
        title.getStyleClass().add("card-title");
        title.setWrapText(true);
        title.setMaxWidth(150);

        Label rating = new Label("⭐ " + String.format("%.1f", movie.getRating()));
        rating.getStyleClass().add("card-rating");

        card.getChildren().addAll(poster, title, rating);
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
            BorderPane root = (BorderPane) usernameLabel.getScene().getRoot();
            root.setCenter(page);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ===== Редактирование профиля =====

    @FXML
    private void editProfile() {
        if (editUsername != null) editUsername.setText(currentUser.getUsername());
        if (editEmail != null) editEmail.setText(
                currentUser.getEmail() != null ? currentUser.getEmail() : ""
        );
        if (editPassword != null) editPassword.clear();
        if (editError != null) editError.setText("");

        if (editForm != null) {
            editForm.setVisible(true);
            editForm.setManaged(true);
        }
    }

    @FXML
    private void cancelEdit() {
        if (editForm != null) {
            editForm.setVisible(false);
            editForm.setManaged(false);
        }
        if (editError != null) editError.setText("");
    }

    @FXML
    private void saveProfile() {
        String username = editUsername != null ? editUsername.getText().trim() : "";
        String email    = editEmail    != null ? editEmail.getText().trim()    : "";
        String password = editPassword != null ? editPassword.getText().trim() : "";

        if (username.isEmpty()) {
            if (editError != null) editError.setText("Введите имя пользователя!");
            return;
        }

        if (editError != null) editError.setText("⏳ Сохранение...");

        final String finalUsername = username;
        final String finalEmail    = email;
        final String finalPassword = password;

        db.async(() -> db.updateUser(
                currentUser.getId(),
                finalUsername,
                finalEmail,
                finalPassword.isEmpty() ? null : finalPassword
        )).thenAccept(success -> Platform.runLater(() -> {
            if (success) {
                currentUser.setUsername(finalUsername);
                currentUser.setEmail(finalEmail);
                SessionManager.getInstance().setCurrentUser(currentUser);
                loadProfile();
                cancelEdit();

                // Обновляем имя пользователя в навигации
                try {
                    BorderPane root = (BorderPane) usernameLabel.getScene().getRoot();
                    MainController mainCtrl = (MainController) root.getUserData();
                    if (mainCtrl != null) {
                        mainCtrl.setLoggedIn(finalUsername);
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            } else {
                if (editError != null)
                    editError.setText("❌ Ошибка. Возможно, имя уже занято.");
            }
        })).exceptionally(e -> {
            e.printStackTrace();
            Platform.runLater(() -> {
                if (editError != null)
                    editError.setText("❌ Ошибка при сохранении. Попробуйте ещё раз.");
            });
            return null;
        });
    }

    @FXML
    private void logout() {
        SessionManager.getInstance().logout();
        try {
            BorderPane root = (BorderPane) usernameLabel.getScene().getRoot();
            MainController mainCtrl = (MainController) root.getUserData();
            if (mainCtrl != null) {
                mainCtrl.setLoggedOut();
            }
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/focus/fxml/home.fxml")
            );
            Node page = loader.load();
            root.setCenter(page);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void goToLogin() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/focus/fxml/login.fxml")
            );
            // Ищем корень через любой доступный узел
            Node refNode = (favoritesRow != null) ? favoritesRow
                    : (usernameLabel != null ? usernameLabel : null);
            if (refNode != null && refNode.getScene() != null) {
                Node page = loader.load();
                BorderPane root = (BorderPane) refNode.getScene().getRoot();
                root.setCenter(page);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
