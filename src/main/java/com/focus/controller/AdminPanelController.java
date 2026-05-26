package com.focus.controller;

import com.focus.database.DatabaseManager;
import com.focus.model.ActionLog;
import com.focus.model.User;
import com.focus.service.LogManager;
import com.focus.service.SessionManager;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

public class AdminPanelController implements Initializable {

    // Шапка
    @FXML private Label adminNameLabel;

    // Пользователи
    @FXML private TableView<User> usersTable;
    @FXML private TableColumn<User, String> idCol;
    @FXML private TableColumn<User, String> usernameCol;
    @FXML private TableColumn<User, String> emailCol;
    @FXML private TableColumn<User, String> roleCol;
    @FXML private TableColumn<User, String> createdCol;
    @FXML private TableColumn<User, String> statusCol;
    @FXML private Label totalUsersLabel;

    // Логи
    @FXML private TableView<ActionLog> logsTable;
    @FXML private TableColumn<ActionLog, String> logIdCol;
    @FXML private TableColumn<ActionLog, String> logTimeCol;
    @FXML private TableColumn<ActionLog, String> logUserCol;
    @FXML private TableColumn<ActionLog, String> logActionCol;
    @FXML private TableColumn<ActionLog, String> logDetailsCol;

    // Статистика
    @FXML private Label totalMoviesLabel;
    @FXML private Label totalSeriesLabel;
    @FXML private Label totalUsersStatLabel;
    @FXML private Label totalLogsLabel;

    private final DatabaseManager db =
            DatabaseManager.getInstance();

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        // Проверяем что это администратор
        if (!SessionManager.getInstance().isAdmin()) {
            return;
        }

        String username = SessionManager.getInstance()
                .getCurrentUser().getUsername();
        adminNameLabel.setText("👑 " + username);

        setupUsersTable();
        setupLogsTable();
        loadUsers();
        loadLogs();
        loadStats();

        // Логируем вход в панель
        db.logAction(
                SessionManager.getInstance()
                        .getCurrentUser().getId(),
                username,
                "ADMIN_PANEL_OPEN",
                "Открыта панель администратора"
        );
    }

    // ===== Настройка таблицы пользователей =====

    private void setupUsersTable() {
        idCol.setCellValueFactory(data ->
                new SimpleStringProperty(
                        String.valueOf(data.getValue().getId())
                )
        );
        usernameCol.setCellValueFactory(data ->
                new SimpleStringProperty(
                        data.getValue().getUsername()
                )
        );
        emailCol.setCellValueFactory(data ->
                new SimpleStringProperty(
                        data.getValue().getEmail() != null
                                ? data.getValue().getEmail()
                                : "—"
                )
        );
        roleCol.setCellValueFactory(data ->
                new SimpleStringProperty(
                        data.getValue().getRole()
                )
        );
        createdCol.setCellValueFactory(data ->
                new SimpleStringProperty(
                        data.getValue().getCreatedAt() != null
                                ? data.getValue().getCreatedAt()
                                : "—"
                )
        );
        statusCol.setCellValueFactory(data ->
                new SimpleStringProperty(
                        data.getValue().isBanned()
                                ? "🚫 Забанен"
                                : "✅ Активен"
                )
        );

        // Цвет строки если забанен
        usersTable.setRowFactory(tv -> {
            TableRow<User> row = new TableRow<>();
            row.itemProperty().addListener((obs, old, user) -> {
                if (user != null && user.isBanned()) {
                    row.setStyle(
                            "-fx-background-color: #2a1a1a;"
                    );
                } else {
                    row.setStyle("");
                }
            });
            return row;
        });
    }

    // ===== Настройка таблицы логов =====

    private void setupLogsTable() {
        logIdCol.setCellValueFactory(data ->
                new SimpleStringProperty(
                        String.valueOf(data.getValue().getId())
                )
        );
        logTimeCol.setCellValueFactory(data ->
                new SimpleStringProperty(
                        data.getValue().getCreatedAt()
                )
        );
        logUserCol.setCellValueFactory(data ->
                new SimpleStringProperty(
                        data.getValue().getUsername()
                )
        );
        logActionCol.setCellValueFactory(data ->
                new SimpleStringProperty(
                        data.getValue().getAction()
                )
        );
        logDetailsCol.setCellValueFactory(data ->
                new SimpleStringProperty(
                        data.getValue().getDetails() != null
                                ? data.getValue().getDetails()
                                : "—"
                )
        );
    }

    // ===== Загрузка данных =====

    private void loadUsers() {
        List<User> users = db.getAllUsers();
        ObservableList<User> data =
                FXCollections.observableArrayList(users);
        usersTable.setItems(data);
        totalUsersLabel.setText(
                String.valueOf(users.size())
        );
    }

    private void loadLogs() {
        List<ActionLog> logs = db.getAllLogs();
        ObservableList<ActionLog> data =
                FXCollections.observableArrayList(logs);
        logsTable.setItems(data);
    }

    private void loadStats() {
        int movies = db.getAllMovies().size();
        int series = db.getAllSeries().size();
        int users  = db.getAllUsers().size();
        int logs   = db.getAllLogs().size();

        totalMoviesLabel.setText(String.valueOf(movies));
        totalSeriesLabel.setText(String.valueOf(series));
        totalUsersStatLabel.setText(String.valueOf(users));
        totalLogsLabel.setText(String.valueOf(logs));
    }

    // ===== Действия с пользователями =====

    @FXML
    private void banUser() {
        User selected = usersTable
                .getSelectionModel().getSelectedItem();

        if (selected == null) {
            showAlert("Выберите пользователя!");
            return;
        }
        if (selected.isAdmin()) {
            showAlert("Нельзя забанить администратора!");
            return;
        }

        db.setBanUser(selected.getId(), true);
        db.logAction(
                SessionManager.getInstance()
                        .getCurrentUser().getId(),
                SessionManager.getInstance()
                        .getCurrentUser().getUsername(),
                "USER_BANNED",
                "Забанен пользователь: " + selected.getUsername()
        );

        showAlert("✅ Пользователь забанен: "
                + selected.getUsername());
        loadUsers();
        loadLogs();
    }

    @FXML
    private void unbanUser() {
        User selected = usersTable
                .getSelectionModel().getSelectedItem();

        if (selected == null) {
            showAlert("Выберите пользователя!");
            return;
        }

        db.setBanUser(selected.getId(), false);
        db.logAction(
                SessionManager.getInstance()
                        .getCurrentUser().getId(),
                SessionManager.getInstance()
                        .getCurrentUser().getUsername(),
                "USER_UNBANNED",
                "Разбанен пользователь: "
                        + selected.getUsername()
        );

        showAlert("✅ Пользователь разбанен: "
                + selected.getUsername());
        loadUsers();
        loadLogs();
    }

    @FXML
    private void makeAdmin() {
        User selected = usersTable
                .getSelectionModel().getSelectedItem();

        if (selected == null) {
            showAlert("Выберите пользователя!");
            return;
        }

        db.setUserRole(selected.getId(), "ADMIN");
        db.logAction(
                SessionManager.getInstance()
                        .getCurrentUser().getId(),
                SessionManager.getInstance()
                        .getCurrentUser().getUsername(),
                "ROLE_CHANGED",
                selected.getUsername() + " → ADMIN"
        );

        showAlert("✅ " + selected.getUsername()
                + " теперь администратор!");
        loadUsers();
        loadLogs();
    }

    @FXML
    private void makeUser() {
        User selected = usersTable
                .getSelectionModel().getSelectedItem();

        if (selected == null) {
            showAlert("Выберите пользователя!");
            return;
        }

        db.setUserRole(selected.getId(), "USER");
        db.logAction(
                SessionManager.getInstance()
                        .getCurrentUser().getId(),
                SessionManager.getInstance()
                        .getCurrentUser().getUsername(),
                "ROLE_CHANGED",
                selected.getUsername() + " → USER"
        );

        showAlert("✅ Роль изменена на USER");
        loadUsers();
        loadLogs();
    }

    @FXML
    private void deleteUser() {
        User selected = usersTable
                .getSelectionModel().getSelectedItem();

        if (selected == null) {
            showAlert("Выберите пользователя!");
            return;
        }
        if (selected.isAdmin()) {
            showAlert("Нельзя удалить администратора!");
            return;
        }

        Alert confirm = new Alert(
                Alert.AlertType.CONFIRMATION
        );
        confirm.setTitle("Удаление");
        confirm.setContentText(
                "Удалить пользователя: "
                        + selected.getUsername() + "?"
        );
        confirm.showAndWait().ifPresent(r -> {
            if (r == ButtonType.OK) {
                db.logAction(
                        SessionManager.getInstance()
                                .getCurrentUser().getId(),
                        SessionManager.getInstance()
                                .getCurrentUser()
                                .getUsername(),
                        "USER_DELETED",
                        "Удалён пользователь: "
                                + selected.getUsername()
                );
                db.deleteUser(selected.getId());
                loadUsers();
                loadLogs();
            }
        });
    }

    @FXML
    private void refreshUsers() {
        loadUsers();
    }

    @FXML
    private void refreshLogs() {
        loadLogs();
    }

    // ===== Экспорт логов в файл =====

    @FXML
    private void exportLogs() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Сохранить логи");
        chooser.setInitialFileName("focus_logs_export.txt");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter(
                        "Текстовый файл", "*.txt"
                )
        );

        java.io.File file =
                chooser.showSaveDialog(new Stage());

        if (file != null) {
            try (PrintWriter writer =
                         new PrintWriter(
                                 new FileWriter(file))) {

                writer.println(
                        "=== FOCUS — Экспорт логов ==="
                );
                writer.println();

                List<ActionLog> logs = db.getAllLogs();
                for (ActionLog log : logs) {
                    writer.println(log.toString());
                }

                writer.println();
                writer.println(
                        "Всего записей: " + logs.size()
                );

                showAlert(
                        "✅ Логи сохранены в: "
                                + file.getAbsolutePath()
                );

            } catch (Exception e) {
                e.printStackTrace();
                showAlert("❌ Ошибка сохранения файла!");
            }
        }
    }

    private void showAlert(String message) {
        Alert alert = new Alert(
                Alert.AlertType.INFORMATION
        );
        alert.setTitle("Focus Admin");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}