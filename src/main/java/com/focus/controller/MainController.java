package com.focus.controller;

import com.focus.service.SessionManager;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;

import java.net.URL;
import java.util.ResourceBundle;

public class MainController implements Initializable {

    @FXML private ImageView logoImage;
    @FXML private TextField searchField;
    @FXML private Button profileBtn;
    @FXML private Button adminBtn;
    @FXML private BorderPane rootPane;

    private boolean isLoggedIn = false;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        loadLogo();

        Platform.runLater(() -> {
            if (rootPane != null) {
                rootPane.setUserData(this);
            }
        });

        if (SessionManager.getInstance().isLoggedIn()) {
            String username = SessionManager
                    .getInstance()
                    .getCurrentUser()
                    .getUsername();
            setLoggedIn(username);
        }
    }

    private void loadLogo() {
        try {
            Image logo = new Image(
                    getClass().getResourceAsStream(
                            "/com/focus/images/logo.png"
                    )
            );
            logoImage.setImage(logo);
        } catch (Exception e) {
            System.out.println("Логотип не найден!");
        }
    }

    @FXML private void goHome() { loadPage("home.fxml"); }
    @FXML private void goMovies() { loadPage("movies.fxml"); }
    @FXML private void goSeries() { loadPage("series.fxml"); }
    @FXML private void goKids() { loadPage("kids.fxml"); }

    @FXML
    private void goProfile() {
        if (isLoggedIn) {
            loadPage("profile.fxml");
        } else {
            loadPage("login.fxml");
        }
    }

    @FXML
    private void goAdmin() {
        if (SessionManager.getInstance().isAdmin()) {
            loadPage("admin_panel.fxml");
        } else {
            loadPage("login.fxml");
        }
    }

    @FXML
    private void goSearch() {
        loadPage("search.fxml");
    }

    public void setLoggedIn(String username) {
        isLoggedIn = true;
        profileBtn.setText("👤 " + username);

        if (SessionManager.getInstance().isAdmin()) {
            adminBtn.setVisible(true);
            adminBtn.setManaged(true);
        }
    }

    public void setLoggedOut() {
        isLoggedIn = false;
        profileBtn.setText("Войти");
        adminBtn.setVisible(false);
        adminBtn.setManaged(false);
    }

    private void loadPage(String fxmlFile) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource(
                            "/com/focus/fxml/" + fxmlFile
                    )
            );
            Node page = loader.load();
            rootPane.setCenter(page);
        } catch (Exception e) {
            System.out.println(
                    "Ошибка загрузки: " + fxmlFile
            );
            e.printStackTrace();
        }
    }

    private void loadSearchPage(String query) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource(
                            "/com/focus/fxml/search.fxml"
                    )
            );
            Node page = loader.load();
            SearchController controller =
                    loader.getController();
            controller.search(query);
            rootPane.setCenter(page);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}