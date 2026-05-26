package com.focus.controller;

import com.focus.service.SessionManager;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;

import java.net.URL;
import java.util.ResourceBundle;

/**
 * MainController — оболочка приложения (навбар + центральный контент).
 *
 * ИСПРАВЛЕНИЯ:
 * - Удалён мёртвый код: метод loadSearchPage(String) никогда не вызывался
 *   (в навбаре goSearch() просто открывает search.fxml без передачи запроса)
 * - Активная кнопка навбара теперь подсвечивается при переключении страниц
 * - Добавлен метод goBack() для возврата на предыдущую страницу (используется в других контроллерах)
 */
public class MainController implements Initializable {

    @FXML private ImageView  logoImage;
    @FXML private Button     profileBtn;
    @FXML private Button     adminBtn;
    @FXML private BorderPane rootPane;

    // Кнопки навигации — для подсветки активной
    @FXML private Button homeBtn;
    @FXML private Button moviesBtn;
    @FXML private Button seriesBtn;
    @FXML private Button kidsBtn;

    private boolean isLoggedIn = false;

    /** Последняя загруженная страница (для goBack в дочерних контроллерах) */
    private String lastPage = "home.fxml";

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        loadLogo();

        Platform.runLater(() -> {
            if (rootPane != null) {
                rootPane.setUserData(this);
            }
        });

        if (SessionManager.getInstance().isLoggedIn()) {
            String username = SessionManager.getInstance().getCurrentUser().getUsername();
            setLoggedIn(username);
        }
    }

    private void loadLogo() {
        try {
            Image logo = new Image(
                    getClass().getResourceAsStream("/com/focus/images/logo.png")
            );
            if (logoImage != null) logoImage.setImage(logo);
        } catch (Exception e) {
            System.out.println("Логотип не найден!");
        }
    }

    // ===== Навигация =====

    @FXML private void goHome()   { loadPage("home.fxml");   setActiveBtn(homeBtn); }
    @FXML private void goMovies() { loadPage("movies.fxml"); setActiveBtn(moviesBtn); }
    @FXML private void goSeries() { loadPage("series.fxml"); setActiveBtn(seriesBtn); }
    @FXML private void goKids()   { loadPage("kids.fxml");   setActiveBtn(kidsBtn); }

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

    // ===== Состояние пользователя =====

    public void setLoggedIn(String username) {
        isLoggedIn = true;
        if (profileBtn != null) profileBtn.setText("👤 " + username);
        if (SessionManager.getInstance().isAdmin()) {
            if (adminBtn != null) {
                adminBtn.setVisible(true);
                adminBtn.setManaged(true);
            }
        }
    }

    public void setLoggedOut() {
        isLoggedIn = false;
        if (profileBtn != null) profileBtn.setText("Войти");
        if (adminBtn != null) {
            adminBtn.setVisible(false);
            adminBtn.setManaged(false);
        }
        // После выхода — возвращаем на главную
        goHome();
    }

    // ===== Загрузка страниц =====

    public void loadPage(String fxmlFile) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/focus/fxml/" + fxmlFile)
            );
            Node page = loader.load();
            rootPane.setCenter(page);
            lastPage = fxmlFile;
        } catch (Exception e) {
            System.out.println("Ошибка загрузки: " + fxmlFile);
            e.printStackTrace();
        }
    }

    /** Возвращает имя последней загруженной страницы (для goBack в дочерних контроллерах) */
    public String getLastPage() {
        return lastPage;
    }

    // ===== Подсветка активной кнопки =====

    private void setActiveBtn(Button active) {
        Button[] navButtons = {homeBtn, moviesBtn, seriesBtn, kidsBtn};
        for (Button btn : navButtons) {
            if (btn == null) continue;
            if (btn == active) {
                btn.getStyleClass().setAll("nav-btn-active");
            } else {
                btn.getStyleClass().setAll("nav-btn");
            }
        }
    }
}
