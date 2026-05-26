package com.focus;

import com.focus.database.DatabaseManager;
import com.focus.service.LogManager;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

public class Main extends Application {

    @Override
    public void start(Stage stage) throws Exception {

        // Инициализация базы данных
        DatabaseManager.getInstance().initialize();

        // Системный лог запуска
        LogManager.getInstance().logSystem(
                "Приложение Focus запущено"
        );

        FXMLLoader loader = new FXMLLoader(
                getClass().getResource(
                        "/com/focus/fxml/main.fxml"
                )
        );

        BorderPane root = loader.load();

        Scene scene = new Scene(root, 1280, 720);
        scene.getStylesheets().add(
                getClass().getResource(
                        "/com/focus/css/style.css"
                ).toExternalForm()
        );

        stage.setTitle("Focus");
        stage.setMinWidth(1024);
        stage.setMinHeight(600);
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}