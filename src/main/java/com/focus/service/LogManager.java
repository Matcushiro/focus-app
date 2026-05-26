package com.focus.service;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class LogManager {

    // ИСПРАВЛЕНИЕ: thread-safe singleton
    private static volatile LogManager instance;
    private static final String LOG_FILE = "focus_logs.txt";
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private LogManager() {}

    public static LogManager getInstance() {
        if (instance == null) {
            synchronized (LogManager.class) {
                if (instance == null) {
                    instance = new LogManager();
                }
            }
        }
        return instance;
    }

    /**
     * Записывает произвольное сообщение в лог-файл.
     * Синхронизирован для корректной записи из разных потоков.
     */
    public synchronized void log(String message) {
        try (PrintWriter writer = new PrintWriter(
                new FileWriter(LOG_FILE, true))) {
            String timestamp = LocalDateTime.now().format(FORMATTER);
            writer.println("[" + timestamp + "] " + message);
        } catch (Exception e) {
            // Лог-ошибки не должны ронять приложение
            System.err.println("LogManager: не удалось записать лог: " + e.getMessage());
        }
    }

    public void log(String action, String username, String details) {
        log(username + " | " + action + " | " + details);
    }

    public void logSystem(String message) {
        log("SYSTEM | " + message);
    }
}
