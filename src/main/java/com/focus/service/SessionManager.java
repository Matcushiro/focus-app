package com.focus.service;

import com.focus.model.User;

public class SessionManager {

    // thread-safe singleton
    private static volatile SessionManager instance;
    private User currentUser;

    private SessionManager() {}

    public static SessionManager getInstance() {
        if (instance == null) {
            synchronized (SessionManager.class) {
                if (instance == null) {
                    instance = new SessionManager();
                }
            }
        }
        return instance;
    }

    public User getCurrentUser() { return currentUser; }

    public void setCurrentUser(User user) {
        this.currentUser = user;
        if (user != null) {
            LogManager.getInstance().log(
                    "LOGIN",
                    user.getUsername(),
                    "Пользователь вошёл в систему"
            );
        }
    }

    public boolean isLoggedIn() {
        return currentUser != null;
    }

    public boolean isAdmin() {
        return currentUser != null && currentUser.isAdmin();
    }

    public void logout() {
        if (currentUser != null) {
            LogManager.getInstance().log(
                    "LOGOUT",
                    currentUser.getUsername(),
                    "Пользователь вышел из системы"
            );
        }
        currentUser = null;
    }
}
