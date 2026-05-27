package com.focus.controller;

import com.focus.database.DatabaseManager;
import com.focus.model.ActionLog;
import com.focus.model.User;
import com.focus.service.LogManager;
import com.focus.service.SessionManager;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
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
    @FXML private TableColumn<User, String> contactCol; // email ИЛИ телефон
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

    private final DatabaseManager db = DatabaseManager.getInstance();

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        if (!SessionManager.getInstance().isAdmin()) return;

        String username = SessionManager.getInstance().getCurrentUser().getUsername();
        adminNameLabel.setText(username);

        setupUsersTable();
        setupLogsTable();

        // Загружаем данные асинхронно
        loadUsersAsync();
        loadLogsAsync();
        loadStatsAsync();

        db.asyncRun(() -> db.logAction(
                SessionManager.getInstance().getCurrentUser().getId(),
                username,
                "ADMIN_PANEL_OPEN",
                "Открыта панель администратора"
        )).exceptionally(e -> { e.printStackTrace(); return null; });
    }

    // Настройка таблицы пользователей
    private void setupUsersTable() {
        idCol.setCellValueFactory(data ->
                new SimpleStringProperty(String.valueOf(data.getValue().getId())));
        usernameCol.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getUsername()));

        // Показываем email или телефон
        contactCol.setCellValueFactory(data -> {
            User u = data.getValue();
            String email = u.getEmail();
            String phone = u.getPhone();
            if (email != null && !email.isBlank()) return new SimpleStringProperty(email);
            if (phone != null && !phone.isBlank()) return new SimpleStringProperty("📱 " + phone);
            return new SimpleStringProperty("—");
        });

        roleCol.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getRole()));
        createdCol.setCellValueFactory(data ->
                new SimpleStringProperty(
                        data.getValue().getCreatedAt() != null ? data.getValue().getCreatedAt() : "—"
                ));
        statusCol.setCellValueFactory(data ->
                new SimpleStringProperty(
                        data.getValue().isBanned() ? "Забанен" : "Активен"
                ));

        // Подсветка забаненных строк
        usersTable.setRowFactory(tv -> {
            TableRow<User> row = new TableRow<>();
            row.itemProperty().addListener((obs, old, user) -> {
                if (user != null && user.isBanned()) {
                    row.setStyle("-fx-background-color: #2a1a1a;");
                } else {
                    row.setStyle("");
                }
            });
            return row;
        });
    }

    // Настройка таблицы логов
    private void setupLogsTable() {
        logIdCol.setCellValueFactory(data ->
                new SimpleStringProperty(String.valueOf(data.getValue().getId())));
        logTimeCol.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getCreatedAt()));
        logUserCol.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getUsername()));
        logActionCol.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getAction()));
        logDetailsCol.setCellValueFactory(data ->
                new SimpleStringProperty(
                        data.getValue().getDetails() != null ? data.getValue().getDetails() : "—"
                ));
    }

    // Асинхронная загрузка данных
    private void loadUsersAsync() {
        db.async(db::getAllUsers)
                .thenAccept(users -> Platform.runLater(() -> {
                    usersTable.setItems(FXCollections.observableArrayList(users));
                    if (totalUsersLabel != null)
                        totalUsersLabel.setText(String.valueOf(users.size()));
                }))
                .exceptionally(e -> { e.printStackTrace(); return null; });
    }

    private void loadLogsAsync() {
        db.async(db::getAllLogs)
                .thenAccept(logs -> Platform.runLater(() ->
                        logsTable.setItems(FXCollections.observableArrayList(logs))
                ))
                .exceptionally(e -> { e.printStackTrace(); return null; });
    }

    private void loadStatsAsync() {
        db.async(() -> {
            int movies = db.getAllMovies().size();
            int series = db.getAllSeries().size();
            int users = db.getAllUsers().size();
            int logs = db.getAllLogs().size();
            return new int[]{movies, series, users, logs};
        }).thenAccept(stats -> Platform.runLater(() -> {
            if (totalMoviesLabel   != null) totalMoviesLabel.setText(String.valueOf(stats[0]));
            if (totalSeriesLabel   != null) totalSeriesLabel.setText(String.valueOf(stats[1]));
            if (totalUsersStatLabel!= null) totalUsersStatLabel.setText(String.valueOf(stats[2]));
            if (totalLogsLabel     != null) totalLogsLabel.setText(String.valueOf(stats[3]));
        })).exceptionally(e -> { e.printStackTrace(); return null; });
    }

    // Действия с пользователями
    @FXML
    private void banUser() {
        User selected = usersTable.getSelectionModel().getSelectedItem();
        if (selected == null) { showAlert("Выберите пользователя!"); return; }
        if (selected.isAdmin()) { showAlert("Нельзя забанить администратора!"); return; }

        db.asyncRun(() -> {
            db.setBanUser(selected.getId(), true);
            db.logAction(
                    SessionManager.getInstance().getCurrentUser().getId(),
                    SessionManager.getInstance().getCurrentUser().getUsername(),
                    "USER_BANNED",
                    "Забанен: " + selected.getUsername()
            );
        }).thenRun(() -> Platform.runLater(() -> {
            showAlert("Пользователь забанен: " + selected.getUsername());
            loadUsersAsync();
            loadLogsAsync();
        })).exceptionally(e -> { e.printStackTrace(); return null; });
    }

    @FXML
    private void unbanUser() {
        User selected = usersTable.getSelectionModel().getSelectedItem();
        if (selected == null) { showAlert("Выберите пользователя!"); return; }

        db.asyncRun(() -> {
            db.setBanUser(selected.getId(), false);
            db.logAction(
                    SessionManager.getInstance().getCurrentUser().getId(),
                    SessionManager.getInstance().getCurrentUser().getUsername(),
                    "USER_UNBANNED",
                    "Разбанен: " + selected.getUsername()
            );
        }).thenRun(() -> Platform.runLater(() -> {
            showAlert("Пользователь разбанен: " + selected.getUsername());
            loadUsersAsync();
            loadLogsAsync();
        })).exceptionally(e -> { e.printStackTrace(); return null; });
    }

    @FXML
    private void makeAdmin() {
        User selected = usersTable.getSelectionModel().getSelectedItem();
        if (selected == null) { showAlert("Выберите пользователя!"); return; }

        db.asyncRun(() -> {
            db.setUserRole(selected.getId(), "ADMIN");
            db.logAction(
                    SessionManager.getInstance().getCurrentUser().getId(),
                    SessionManager.getInstance().getCurrentUser().getUsername(),
                    "ROLE_CHANGED",
                    selected.getUsername() + " → ADMIN"
            );
        }).thenRun(() -> Platform.runLater(() -> {
            showAlert(selected.getUsername() + " теперь администратор!");
            loadUsersAsync();
            loadLogsAsync();
        })).exceptionally(e -> { e.printStackTrace(); return null; });
    }

    @FXML
    private void makeUser() {
        User selected = usersTable.getSelectionModel().getSelectedItem();
        if (selected == null) { showAlert("Выберите пользователя!"); return; }

        db.asyncRun(() -> {
            db.setUserRole(selected.getId(), "USER");
            db.logAction(
                    SessionManager.getInstance().getCurrentUser().getId(),
                    SessionManager.getInstance().getCurrentUser().getUsername(),
                    "ROLE_CHANGED",
                    selected.getUsername() + " → USER"
            );
        }).thenRun(() -> Platform.runLater(() -> {
            showAlert("Роль изменена на USER");
            loadUsersAsync();
            loadLogsAsync();
        })).exceptionally(e -> { e.printStackTrace(); return null; });
    }

    @FXML
    private void deleteUser() {
        User selected = usersTable.getSelectionModel().getSelectedItem();
        if (selected == null) { showAlert("Выберите пользователя!"); return; }
        if (selected.isAdmin()) { showAlert("Нельзя удалить администратора!"); return; }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Удаление");
        confirm.setContentText("Удалить пользователя: " + selected.getUsername() + "?");
        confirm.showAndWait().ifPresent(r -> {
            if (r == ButtonType.OK) {
                db.asyncRun(() -> {
                    db.logAction(
                            SessionManager.getInstance().getCurrentUser().getId(),
                            SessionManager.getInstance().getCurrentUser().getUsername(),
                            "USER_DELETED",
                            "Удалён: " + selected.getUsername()
                    );
                    db.deleteUser(selected.getId());
                }).thenRun(() -> Platform.runLater(() -> {
                    loadUsersAsync();
                    loadLogsAsync();
                })).exceptionally(e -> { e.printStackTrace(); return null; });
            }
        });
    }

    @FXML
    private void refreshUsers() {
        loadUsersAsync();
    }

    @FXML
    private void refreshLogs() {
        loadLogsAsync();
    }

    // ===== Кнопка: открыть управление контентом =====
    @FXML
    private void openContentManager() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/focus/fxml/admin.fxml")
            );
            Node page = loader.load();
            // Ищем корневой BorderPane через сцену
            BorderPane root = (BorderPane) adminNameLabel.getScene().getRoot();
            root.setCenter(page);
        } catch (Exception e) {
            e.printStackTrace();
            showAlert("Не удалось открыть управление контентом: " + e.getMessage());
        }
    }

    // Экспорт логов
    @FXML
    private void exportLogs() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Сохранить логи");
        chooser.setInitialFileName("focus_logs_export.txt");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Текстовый файл", "*.txt")
        );
        java.io.File file = chooser.showSaveDialog(new Stage());
        if (file != null) {
            db.async(db::getAllLogs)
                    .thenAccept(logs -> {
                        try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
                            writer.println("=== FOCUS — Экспорт логов ===");
                            writer.println();
                            for (ActionLog log : logs) {
                                writer.println(log.toString());
                            }
                            writer.println();
                            writer.println("Всего записей: " + logs.size());
                            Platform.runLater(() ->
                                    showAlert("Логи сохранены в: " + file.getAbsolutePath())
                            );
                        } catch (Exception ex) {
                            ex.printStackTrace();
                            Platform.runLater(() -> showAlert("Ошибка сохранения файла!"));
                        }
                    })
                    .exceptionally(e -> {
                        e.printStackTrace();
                        Platform.runLater(() -> showAlert("Ошибка загрузки логов!"));
                        return null;
                    });
        }
    }

    // Вспомогательные
    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Focus Admin");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
