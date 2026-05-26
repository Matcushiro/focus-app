package com.focus.model;

public class User {
    private int id;
    private String username;
    private String email;
    private String role;
    private String createdAt;
    private boolean isBanned;

    public User() {}

    public User(int id, String username, String email) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.role = "USER";
        this.isBanned = false;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() { return email; }
    public void setEmail(String email) {
        this.email = email;
    }

    public String getRole() { return role; }
    public void setRole(String role) {
        this.role = role;
    }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public boolean isBanned() { return isBanned; }
    public void setBanned(boolean banned) {
        this.isBanned = banned;
    }

    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }
}