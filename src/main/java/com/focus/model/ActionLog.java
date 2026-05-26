package com.focus.model;

public class ActionLog {
    private int id;
    private String username;
    private String action;
    private String details;
    private String createdAt;

    public ActionLog(int id,
                     String username,
                     String action,
                     String details,
                     String createdAt) {
        this.id = id;
        this.username = username;
        this.action = action;
        this.details = details;
        this.createdAt = createdAt;
    }

    public int getId() { return id; }
    public String getUsername() { return username; }
    public String getAction() { return action; }
    public String getDetails() { return details; }
    public String getCreatedAt() { return createdAt; }

    @Override
    public String toString() {
        return createdAt + " | " +
                username  + " | " +
                action    + " | " +
                (details != null ? details : "");
    }
}