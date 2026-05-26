package com.focus.controller;

import com.focus.database.DatabaseManager;
import com.focus.model.User;
import com.focus.service.LogManager;
import com.focus.service.SessionManager;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;

import java.net.URL;
import java.util.ResourceBundle;

public class LoginController implements Initializable {

    @FXML private TextField loginUsername;
    @FXML private PasswordField loginPassword;
    @FXML private Label loginError;

    @FXML private TextField regUsername;
    @FXML private TextField regEmail;
    @FXML private PasswordField regPassword;
    @FXML private PasswordField regPasswordConfirm;
    @FXML private Label regError;

    @FXML private VBox loginForm;
    @FXML private VBox registerForm;
    @FXML private Button loginTabBtn;
    @FXML private Button regTabBtn;

    private final DatabaseManager db =
            DatabaseManager.getInstance();

    @Override
    public void initialize(URL url, ResourceBundle rb) {}

    @FXML
    private void showLogin() {
        loginForm.setVisible(true);
        loginForm.setManaged(true);
        registerForm.setVisible(false);
        registerForm.setManaged(false);
        loginTabBtn.getStyleClass().setAll("tab-btn-active");
        regTabBtn.getStyleClass().setAll("tab-btn");
    }

    @FXML
    private void showRegister() {
        loginForm.setVisible(false);
        loginForm.setManaged(false);
        registerForm.setVisible(true);
        registerForm.setManaged(true);
        loginTabBtn.getStyleClass().setAll("tab-btn");
        regTabBtn.getStyleClass().setAll("tab-btn-active");
    }

    @FXML
    private void login() {
        String username = loginUsername.getText().trim();
        String password = loginPassword.getText().trim();

        if (username.isEmpty() || password.isEmpty()) {
            loginError.setText("Заполните все поля!");
            return;
        }

        if (db.isUserBanned(username)) {
            loginError.setText(
                    "❌ Ваш аккаунт заблокирован!"
            );
            LogManager.getInstance().log(
                    "LOGIN_BLOCKED",
                    username,
                    "Попытка входа заблокированным пользователем"
            );
            return;
        }

        User user = db.loginUser(username, password);

        if (user != null) {
            goToHome(user);
        } else {
            loginError.setText(
                    "Неверный логин или пароль!"
            );
            LogManager.getInstance().log(
                    "LOGIN_FAILED",
                    username,
                    "Неудачная попытка входа"
            );
        }
    }

    @FXML
    private void register() {
        String username = regUsername.getText().trim();
        String email    = regEmail.getText().trim();
        String password = regPassword.getText().trim();
        String confirm  =
                regPasswordConfirm.getText().trim();

        if (username.isEmpty()
                || password.isEmpty()
                || email.isEmpty()) {
            regError.setText("Заполните все поля!");
            return;
        }

        if (!password.equals(confirm)) {
            regError.setText("Пароли не совпадают!");
            return;
        }

        if (password.length() < 6) {
            regError.setText(
                    "Пароль минимум 6 символов!"
            );
            return;
        }

        boolean success =
                db.registerUser(username, password, email);

        if (success) {
            LogManager.getInstance().log(
                    "REGISTER",
                    username,
                    "Новый пользователь зарегистрирован"
            );
            User user = db.loginUser(username, password);
            goToHome(user);
        } else {
            regError.setText(
                    "Пользователь уже существует!"
            );
        }
    }

    private void goToHome(User user) {
        try {
            SessionManager.getInstance()
                    .setCurrentUser(user);

            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource(
                            "/com/focus/fxml/home.fxml"
                    )
            );
            Node page = loader.load();

            BorderPane root =
                    (BorderPane) loginUsername
                            .getScene().getRoot();

            MainController mainCtrl =
                    (MainController) root.getUserData();
            if (mainCtrl != null) {
                mainCtrl.setLoggedIn(user.getUsername());
            }

            root.setCenter(page);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}