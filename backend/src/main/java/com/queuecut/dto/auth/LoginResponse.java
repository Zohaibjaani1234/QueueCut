package com.queuecut.dto.auth;

public class LoginResponse {

    private String token;
    private String type = "Bearer";
    private String username;
    private String fullName;
    private String role;
    private long expiresInMs;

    public LoginResponse() {
    }

    public LoginResponse(String token, String username, String fullName, String role, long expiresInMs) {
        this.token = token;
        this.username = username;
        this.fullName = fullName;
        this.role = role;
        this.expiresInMs = expiresInMs;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public long getExpiresInMs() {
        return expiresInMs;
    }

    public void setExpiresInMs(long expiresInMs) {
        this.expiresInMs = expiresInMs;
    }
}
