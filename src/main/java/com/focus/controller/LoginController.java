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
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.net.URL;
import java.util.ResourceBundle;
import java.util.regex.Pattern;

public class LoginController implements Initializable {

    // Форма входа
    @FXML private TextField      loginIdentifier;   // email или телефон или логин
    @FXML private PasswordField  loginPassword;
    @FXML private Label          loginError;
    @FXML private HBox           loginErrorBox;     // контейнер ошибки (скрыт по умолчанию)

    // Форма регистрации
    @FXML private TextField      regUsername;
    @FXML private TextField      regEmailOrPhone;   // email ИЛИ телефон
    @FXML private PasswordField  regPassword;
    @FXML private PasswordField  regPasswordConfirm;
    @FXML private Label          regError;
    @FXML private HBox           regErrorBox;       // контейнер ошибки (скрыт по умолчанию)
    @FXML private Label          regHint;           // подсказка под полем

    // Переключатели форм
    @FXML private VBox   loginForm;
    @FXML private VBox   registerForm;
    @FXML private Button loginTabBtn;
    @FXML private Button regTabBtn;

    // Паттерны для валидации
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private static final Pattern PHONE_PATTERN =
            Pattern.compile("^[+]?[0-9]{10,15}$");

    private final DatabaseManager db = DatabaseManager.getInstance();

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        // Подсказка при вводе email/телефона в регистрации
        if (regEmailOrPhone != null) {
            regEmailOrPhone.textProperty().addListener((obs, oldVal, newVal) ->
                    updateRegHint(newVal)
            );
        }
        // Очищаем ошибку при вводе
        if (loginIdentifier != null) {
            loginIdentifier.textProperty().addListener((obs, o, n) -> hideLoginError());
        }
        if (loginPassword != null) {
            loginPassword.textProperty().addListener((obs, o, n) -> hideLoginError());
        }
        if (regUsername != null) {
            regUsername.textProperty().addListener((obs, o, n) -> hideRegError());
        }
        if (regPassword != null) {
            regPassword.textProperty().addListener((obs, o, n) -> hideRegError());
        }
    }

    private void updateRegHint(String value) {
        if (regHint == null) return;
        if (value == null || value.isBlank()) {
            regHint.setText("Введите e-mail (user@mail.ru) или номер телефона (+79001234567)");
            regHint.setStyle("-fx-text-fill: #444444; -fx-font-size: 11px; -fx-padding: 0 0 0 4;");
        } else if (EMAIL_PATTERN.matcher(value.trim()).matches()) {
            regHint.setText("✅ Корректный e-mail");
            regHint.setStyle("-fx-text-fill: #4caf50; -fx-font-size: 11px; -fx-padding: 0 0 0 4;");
        } else if (PHONE_PATTERN.matcher(value.trim().replaceAll("[\\s\\-()]", "")).matches()) {
            regHint.setText("✅ Корректный номер телефона");
            regHint.setStyle("-fx-text-fill: #4caf50; -fx-font-size: 11px; -fx-padding: 0 0 0 4;");
        } else {
            regHint.setText("⚠ Введите корректный e-mail или телефон");
            regHint.setStyle("-fx-text-fill: #E65C00; -fx-font-size: 11px; -fx-padding: 0 0 0 4;");
        }
    }

    // ===== Переключение вкладок =====

    @FXML
    private void showLogin() {
        loginForm.setVisible(true);
        loginForm.setManaged(true);
        registerForm.setVisible(false);
        registerForm.setManaged(false);

        // Активная вкладка — оранжевая кнопка
        loginTabBtn.setStyle(
                "-fx-background-color: #E65C00;" +
                        "-fx-text-fill: #ffffff;" +
                        "-fx-font-size: 13px;" +
                        "-fx-font-weight: bold;" +
                        "-fx-background-radius: 9;" +
                        "-fx-cursor: hand;" +
                        "-fx-padding: 10 0;" +
                        "-fx-effect: dropshadow(gaussian, rgba(230,92,0,0.5), 12, 0, 0, 2);"
        );
        regTabBtn.setStyle(
                "-fx-background-color: transparent;" +
                        "-fx-text-fill: #888888;" +
                        "-fx-font-size: 13px;" +
                        "-fx-font-weight: bold;" +
                        "-fx-background-radius: 9;" +
                        "-fx-cursor: hand;" +
                        "-fx-padding: 10 0;"
        );
        hideLoginError();
    }

    @FXML
    private void showRegister() {
        loginForm.setVisible(false);
        loginForm.setManaged(false);
        registerForm.setVisible(true);
        registerForm.setManaged(true);

        // Активная вкладка — Регистрация
        regTabBtn.setStyle(
                "-fx-background-color: #E65C00;" +
                        "-fx-text-fill: #ffffff;" +
                        "-fx-font-size: 13px;" +
                        "-fx-font-weight: bold;" +
                        "-fx-background-radius: 9;" +
                        "-fx-cursor: hand;" +
                        "-fx-padding: 10 0;" +
                        "-fx-effect: dropshadow(gaussian, rgba(230,92,0,0.5), 12, 0, 0, 2);"
        );
        loginTabBtn.setStyle(
                "-fx-background-color: transparent;" +
                        "-fx-text-fill: #888888;" +
                        "-fx-font-size: 13px;" +
                        "-fx-font-weight: bold;" +
                        "-fx-background-radius: 9;" +
                        "-fx-cursor: hand;" +
                        "-fx-padding: 10 0;"
        );
        hideRegError();
    }

    // ===== Вход =====

    @FXML
    private void login() {
        String identifier = loginIdentifier.getText().trim();
        String password   = loginPassword.getText().trim();

        if (identifier.isEmpty() || password.isEmpty()) {
            showLoginError("Заполните все поля!");
            return;
        }

        if (db.isUserBannedByIdentifier(identifier)) {
            showLoginError("❌ Ваш аккаунт заблокирован!");
            LogManager.getInstance().log(
                    "LOGIN_BLOCKED", identifier,
                    "Попытка входа заблокированным пользователем"
            );
            return;
        }

        User user = db.loginUserByIdentifier(identifier, password);

        if (user != null) {
            goToHome(user);
        } else {
            showLoginError("Неверный логин / e-mail / телефон или пароль");
            LogManager.getInstance().log(
                    "LOGIN_FAILED", identifier,
                    "Неудачная попытка входа"
            );
        }
    }

    // ===== Регистрация =====

    @FXML
    private void register() {
        String username   = regUsername.getText().trim();
        String emailPhone = regEmailOrPhone.getText().trim();
        String password   = regPassword.getText().trim();
        String confirm    = regPasswordConfirm.getText().trim();

        if (username.isEmpty() || emailPhone.isEmpty() || password.isEmpty()) {
            showRegError("Заполните все обязательные поля!");
            return;
        }

        // Проверяем формат email или телефона
        String cleanPhone = emailPhone.replaceAll("[\\s\\-()]", "");
        boolean isEmail   = EMAIL_PATTERN.matcher(emailPhone).matches();
        boolean isPhone   = PHONE_PATTERN.matcher(cleanPhone).matches();

        if (!isEmail && !isPhone) {
            showRegError("Введите корректный e-mail или номер телефона!");
            return;
        }

        if (!password.equals(confirm)) {
            showRegError("Пароли не совпадают!");
            return;
        }

        if (password.length() < 6) {
            showRegError("Пароль должен содержать минимум 6 символов!");
            return;
        }

        String email = isEmail ? emailPhone : null;
        String phone = isPhone ? cleanPhone : null;

        boolean success = db.registerUser(username, password, email, phone);

        if (success) {
            LogManager.getInstance().log(
                    "REGISTER", username,
                    "Новый пользователь зарегистрирован (" +
                            (isEmail ? "email: " + email : "телефон: " + phone) + ")"
            );
            User user = db.loginUserByIdentifier(username, password);
            if (user != null) {
                goToHome(user);
            } else {
                showRegError("Ошибка входа после регистрации. Войдите вручную.");
                showLogin();
            }
        } else {
            showRegError("Пользователь с таким именем уже существует!");
        }
    }

    // ===== Вспомогательные =====

    /** Показывает ошибку в блоке входа */
    private void showLoginError(String message) {
        if (loginError != null) loginError.setText(message);
        if (loginErrorBox != null) {
            loginErrorBox.setVisible(true);
            loginErrorBox.setManaged(true);
        }
    }

    /** Скрывает блок ошибки входа */
    private void hideLoginError() {
        if (loginErrorBox != null) {
            loginErrorBox.setVisible(false);
            loginErrorBox.setManaged(false);
        }
    }

    /** Показывает ошибку в блоке регистрации */
    private void showRegError(String message) {
        if (regError != null) regError.setText(message);
        if (regErrorBox != null) {
            regErrorBox.setVisible(true);
            regErrorBox.setManaged(true);
        }
    }

    /** Скрывает блок ошибки регистрации */
    private void hideRegError() {
        if (regErrorBox != null) {
            regErrorBox.setVisible(false);
            regErrorBox.setManaged(false);
        }
    }

    private void goToHome(User user) {
        try {
            SessionManager.getInstance().setCurrentUser(user);

            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/focus/fxml/home.fxml")
            );
            Node page = loader.load();

            BorderPane root = (BorderPane) loginIdentifier.getScene().getRoot();

            MainController mainCtrl = (MainController) root.getUserData();
            if (mainCtrl != null) {
                mainCtrl.setLoggedIn(user.getUsername());
            }

            root.setCenter(page);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
