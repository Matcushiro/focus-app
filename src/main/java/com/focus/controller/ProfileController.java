package com.focus.controller;

import com.focus.database.DatabaseManager;
import com.focus.model.Movie;
import com.focus.model.User;
import com.focus.service.SessionManager;

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

    private final DatabaseManager db =
            DatabaseManager.getInstance();
    private User currentUser;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        currentUser = SessionManager.getInstance()
                .getCurrentUser();
        if (currentUser != null) {
            loadProfile();
        } else {
            goToLogin();
        }
    }

    private void loadProfile() {
        usernameLabel.setText(currentUser.getUsername());
        emailLabel.setText(
                currentUser.getEmail() != null
                        ? currentUser.getEmail()
                        : "Email не указан"
        );
        avatarLabel.setText(
                String.valueOf(
                        currentUser.getUsername().charAt(0)
                ).toUpperCase()
        );
        loadFavorites();
        loadHistory();
    }

    private void loadFavorites() {
        List<Movie> favs =
                db.getFavorites(currentUser.getId());
        favoritesCount.setText(
                String.valueOf(favs.size())
        );
        favoritesRow.getChildren().clear();
        if (favs.isEmpty()) {
            Label empty = new Label(
                    "Вы ещё ничего не добавили в избранное"
            );
            empty.getStyleClass().add("filter-label");
            favoritesRow.getChildren().add(empty);
            return;
        }
        for (Movie m : favs) {
            favoritesRow.getChildren().add(createCard(m));
        }
    }

    private void loadHistory() {
        List<Movie> history =
                db.getWatchHistory(currentUser.getId());
        watchedCount.setText(
                String.valueOf(history.size())
        );
        historyRow.getChildren().clear();
        if (history.isEmpty()) {
            Label empty = new Label(
                    "История просмотров пуста"
            );
            empty.getStyleClass().add("filter-label");
            historyRow.getChildren().add(empty);
            return;
        }
        for (Movie m : history) {
            historyRow.getChildren().add(createCard(m));
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
                    (BorderPane) usernameLabel
                            .getScene().getRoot();
            root.setCenter(page);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void editProfile() {
        editUsername.setText(currentUser.getUsername());
        editEmail.setText(
                currentUser.getEmail() != null
                        ? currentUser.getEmail() : ""
        );
        editForm.setVisible(true);
        editForm.setManaged(true);
    }

    @FXML
    private void cancelEdit() {
        editForm.setVisible(false);
        editForm.setManaged(false);
        editError.setText("");
    }

    @FXML
    private void saveProfile() {
        String username = editUsername.getText().trim();
        String email    = editEmail.getText().trim();
        String password = editPassword.getText().trim();

        if (username.isEmpty()) {
            editError.setText("Введите имя пользователя!");
            return;
        }

        boolean success = db.updateUser(
                currentUser.getId(), username, email,
                password.isEmpty() ? null : password
        );

        if (success) {
            currentUser.setUsername(username);
            currentUser.setEmail(email);
            SessionManager.getInstance()
                    .setCurrentUser(currentUser);
            loadProfile();
            cancelEdit();

            try {
                BorderPane root =
                        (BorderPane) usernameLabel
                                .getScene().getRoot();
                MainController mainCtrl =
                        (MainController) root.getUserData();
                if (mainCtrl != null) {
                    mainCtrl.setLoggedIn(username);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            editError.setText(
                    "Ошибка. Попробуйте другое имя."
            );
        }
    }

    @FXML
    private void logout() {
        SessionManager.getInstance().logout();
        try {
            BorderPane root =
                    (BorderPane) usernameLabel
                            .getScene().getRoot();
            MainController mainCtrl =
                    (MainController) root.getUserData();
            if (mainCtrl != null) {
                mainCtrl.setLoggedOut();
            }
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource(
                            "/com/focus/fxml/home.fxml"
                    )
            );
            Node page = loader.load();
            root.setCenter(page);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void goToLogin() {
        javafx.application.Platform.runLater(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(
                        getClass().getResource(
                                "/com/focus/fxml/login.fxml"
                        )
                );
                Node page = loader.load();
                BorderPane root =
                        (BorderPane) favoritesRow
                                .getScene().getRoot();
                root.setCenter(page);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}