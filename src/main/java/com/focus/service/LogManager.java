package com.focus.service;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class LogManager {

    private static LogManager instance;
    private static final String LOG_FILE = "focus_logs.txt";
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private LogManager() {}

    public static LogManager getInstance() {
        if (instance == null) {
            instance = new LogManager();
        }
        return instance;
    }

    public void log(String message) {
        try (PrintWriter writer = new PrintWriter(
                new FileWriter(LOG_FILE, true))) {
            String timestamp = LocalDateTime.now()
                    .format(FORMATTER);
            writer.println("[" + timestamp + "] " + message);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void log(String action,
                    String username,
                    String details) {
        log(username + " | " + action + " | " + details);
    }

    public void logSystem(String message) {
        log("SYSTEM | " + message);
    }
}