package com.focus.model;

public class User {
    private int     id;
    private String  username;
    private String  password;
    private String  email;
    private String  phone;
    private String  role;
    private String  createdAt;
    private boolean isBanned;

    public User() {}

    public User(int id, String username, String email) {
        this.id       = id;
        this.username = username;
        this.email    = email;
        this.role     = "USER";
        this.isBanned = false;
    }

    public int    getId()       { return id; }
    public void   setId(int id) { this.id = id; }

    public String getUsername()         { return username; }
    public void   setUsername(String u) { this.username = u; }

    // геттер/сеттер для password
    public String getPassword()         { return password; }
    public void   setPassword(String p) { this.password = p; }

    public String getEmail()            { return email; }
    public void   setEmail(String e)    { this.email = e; }

    public String getPhone()            { return phone; }
    public void   setPhone(String p)    { this.phone = p; }

    public String getRole()             { return role; }
    public void   setRole(String r)     { this.role = r; }

    public String getCreatedAt()           { return createdAt; }
    public void   setCreatedAt(String ca)  { this.createdAt = ca; }

    public boolean isBanned()           { return isBanned; }
    public void    setBanned(boolean b) { this.isBanned = b; }

    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }

    public String getContactInfo() {
        if (email != null && !email.isBlank()) return email;
        if (phone != null && !phone.isBlank()) return phone;
        return "—";
    }
}