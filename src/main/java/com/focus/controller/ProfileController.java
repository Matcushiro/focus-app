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

/**
 * ProfileController — профиль пользователя.
 */
public class ProfileController implements Initializable {

    @FXML private Label    usernameLabel;
    @FXML private Label    emailLabel;
    @FXML private Label    avatarLabel;
    @FXML private Label    watchedCount;
    @FXML private Label    favoritesCount;
    @FXML private HBox     favoritesRow;
    @FXML private HBox     historyRow;

    @FXML private VBox          editForm;
    @FXML private TextField     editUsername;
    @FXML private TextField     editEmail;
    @FXML private PasswordField editPassword;
    @FXML private Label         editError;

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
        if (usernameLabel != null) usernameLabel.setText(currentUser.getUsername());

        if (emailLabel != null) {
            String contact = currentUser.getEmail() != null && !currentUser.getEmail().isBlank()
                    ? currentUser.getEmail()
                    : (currentUser.getPhone() != null && !currentUser.getPhone().isBlank()
                       ? currentUser.getPhone()
                       : "Контакт не указан");
            emailLabel.setText(contact);
        }

        if (avatarLabel != null) {
            avatarLabel.setText(
                    String.valueOf(currentUser.getUsername().charAt(0)).toUpperCase()
            );
        }

        // Скрываем форму редактирования при загрузке профиля
        if (editForm != null) {
            editForm.setVisible(false);
            editForm.setManaged(false);
        }

        loadFavoritesAsync();
        loadHistoryAsync();
    }

    private void loadFavoritesAsync() {
        db.async(() -> db.getFavorites(currentUser.getId()))
                .thenAccept(favs -> Platform.runLater(() -> {
                    if (favoritesCount != null) favoritesCount.setText(String.valueOf(favs.size()));
                    if (favoritesRow != null) {
                        favoritesRow.getChildren().clear();
                        if (favs.isEmpty()) {
                            Label empty = new Label("Вы ещё ничего не добавили в избранное");
                            empty.getStyleClass().add("filter-label");
                            favoritesRow.getChildren().add(empty);
                        } else {
                            for (Movie m : favs) favoritesRow.getChildren().add(createCard(m));
                        }
                    }
                }))
                .exceptionally(e -> { e.printStackTrace(); return null; });
    }

    private void loadHistoryAsync() {
        db.async(() -> db.getWatchHistory(currentUser.getId()))
                .thenAccept(history -> Platform.runLater(() -> {
                    if (watchedCount != null) watchedCount.setText(String.valueOf(history.size()));
                    if (historyRow != null) {
                        historyRow.getChildren().clear();
                        if (history.isEmpty()) {
                            Label empty = new Label("История просмотров пуста");
                            empty.getStyleClass().add("filter-label");
                            historyRow.getChildren().add(empty);
                        } else {
                            for (Movie m : history) historyRow.getChildren().add(createCard(m));
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

        Label rating = new Label(String.format("%.1f", movie.getRating()));
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
            BorderPane root = getRootPane();
            if (root != null) root.setCenter(page);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Редактирование профиля

    @FXML
    private void editProfile() {
        if (editUsername != null) editUsername.setText(currentUser.getUsername());
        if (editEmail    != null) editEmail.setText(
                currentUser.getEmail() != null ? currentUser.getEmail() : ""
        );
        if (editPassword != null) editPassword.clear();
        if (editError    != null) editError.setText("");

        if (editForm != null) {
            editForm.setVisible(true);
            editForm.setManaged(true);
        }
    }

    @FXML
    private void cancelEdit() {
        if (editForm  != null) { editForm.setVisible(false); editForm.setManaged(false); }
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
        if (!password.isEmpty() && password.length() < 6) {
            if (editError != null) editError.setText("Пароль минимум 6 символов!");
            return;
        }

        if (editError != null) editError.setText("Сохранение...");

        final String finalUsername = username;
        final String finalEmail    = email;
        final String finalPassword = password.isEmpty() ? null : password;

        db.async(() -> db.updateUser(
                currentUser.getId(),
                finalUsername,
                finalEmail,
                finalPassword
        )).thenAccept(success -> Platform.runLater(() -> {
            if (success) {
                currentUser.setUsername(finalUsername);
                currentUser.setEmail(finalEmail);
                SessionManager.getInstance().setCurrentUser(currentUser);

                // ИСПРАВЛЕНИЕ: обновляем имя в навбаре через MainController
                BorderPane root = getRootPane();
                if (root != null) {
                    MainController mainCtrl = (MainController) root.getUserData();
                    if (mainCtrl != null) mainCtrl.setLoggedIn(finalUsername);
                }

                loadProfile();
                cancelEdit();

                if (editError != null) editError.setText("Сохранено!");
            } else {
                if (editError != null) editError.setText("Ошибка: имя уже занято!");
            }
        })).exceptionally(e -> {
            e.printStackTrace();
            Platform.runLater(() -> {
                if (editError != null) editError.setText("Ошибка сохранения");
            });
            return null;
        });
    }

    // Выход

    @FXML
    private void logout() {
        SessionManager.getInstance().logout();

        BorderPane root = getRootPane();
        if (root != null) {
            MainController mainCtrl = (MainController) root.getUserData();
            if (mainCtrl != null) {
                mainCtrl.setLoggedOut();
                return;
            }
        }
        goToLogin();
    }

    private void goToLogin() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/focus/fxml/login.fxml")
            );
            Node page = loader.load();
            BorderPane root = getRootPane();
            if (root != null) root.setCenter(page);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private BorderPane getRootPane() {
        try {
            javafx.scene.Node ref = usernameLabel != null ? usernameLabel : avatarLabel;
            if (ref != null && ref.getScene() != null) {
                return (BorderPane) ref.getScene().getRoot();
            }
        } catch (Exception ignored) {}
        return null;
    }
}
