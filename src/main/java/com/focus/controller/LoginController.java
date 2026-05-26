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
import java.util.regex.Pattern;

public class LoginController implements Initializable {

    // Форма входа
    @FXML private TextField    loginIdentifier;   // email или телефон или логин
    @FXML private PasswordField loginPassword;
    @FXML private Label        loginError;

    // Форма регистрации
    @FXML private TextField     regUsername;
    @FXML private TextField     regEmailOrPhone;  // email ИЛИ телефон
    @FXML private PasswordField regPassword;
    @FXML private PasswordField regPasswordConfirm;
    @FXML private Label         regError;
    @FXML private Label         regHint;          // подсказка под полем

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
            regEmailOrPhone.textProperty().addListener((obs, oldVal, newVal) -> {
                updateRegHint(newVal);
            });
        }
    }

    private void updateRegHint(String value) {
        if (regHint == null) return;
        if (value == null || value.isBlank()) {
            regHint.setText("Введите e-mail (user@mail.ru) или номер телефона (+79001234567)");
            regHint.setStyle("-fx-text-fill: #777777; -fx-font-size: 11px;");
        } else if (EMAIL_PATTERN.matcher(value.trim()).matches()) {
            regHint.setText("✅ Корректный e-mail");
            regHint.setStyle("-fx-text-fill: #4caf50; -fx-font-size: 11px;");
        } else if (PHONE_PATTERN.matcher(value.trim().replaceAll("[\\s\\-()]", "")).matches()) {
            regHint.setText("✅ Корректный номер телефона");
            regHint.setStyle("-fx-text-fill: #4caf50; -fx-font-size: 11px;");
        } else {
            regHint.setText("⚠ Введите корректный e-mail или телефон");
            regHint.setStyle("-fx-text-fill: #E65C00; -fx-font-size: 11px;");
        }
    }

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
        String identifier = loginIdentifier.getText().trim();
        String password   = loginPassword.getText().trim();

        if (identifier.isEmpty() || password.isEmpty()) {
            loginError.setText("Заполните все поля!");
            return;
        }

        // Пробуем найти пользователя по логину, email или телефону
        if (db.isUserBannedByIdentifier(identifier)) {
            loginError.setText("❌ Ваш аккаунт заблокирован!");
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
            loginError.setText("Неверный логин/email/телефон или пароль!");
            LogManager.getInstance().log(
                    "LOGIN_FAILED", identifier,
                    "Неудачная попытка входа"
            );
        }
    }

    @FXML
    private void register() {
        String username   = regUsername.getText().trim();
        String emailPhone = regEmailOrPhone.getText().trim();
        String password   = regPassword.getText().trim();
        String confirm    = regPasswordConfirm.getText().trim();

        if (username.isEmpty() || emailPhone.isEmpty() || password.isEmpty()) {
            regError.setText("Заполните все поля!");
            return;
        }

        // Проверяем формат email или телефона
        String cleanPhone = emailPhone.replaceAll("[\\s\\-()]", "");
        boolean isEmail   = EMAIL_PATTERN.matcher(emailPhone).matches();
        boolean isPhone   = PHONE_PATTERN.matcher(cleanPhone).matches();

        if (!isEmail && !isPhone) {
            regError.setText("Введите корректный e-mail или номер телефона!");
            return;
        }

        if (!password.equals(confirm)) {
            regError.setText("Пароли не совпадают!");
            return;
        }

        if (password.length() < 6) {
            regError.setText("Пароль минимум 6 символов!");
            return;
        }

        // email = emailPhone если это email, иначе null; phone = cleanPhone если телефон
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
            if (user != null) goToHome(user);
            else {
                regError.setText("Ошибка входа после регистрации. Войдите вручную.");
                showLogin();
            }
        } else {
            regError.setText("Пользователь уже существует!");
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
