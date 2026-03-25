package com.tuempresa.registro.models;

import java.time.LocalDateTime;

/**
 * Modelo de usuario administrador del sistema.
 */
public class AdminUser {

    public static final String ROLE_ADMIN = "Administrador";
    public static final String ROLE_OPERATOR = "Operador";

    private Long id;
    private String username;
    private String passwordHash;
    private String role;
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public AdminUser() {
        this.role = ROLE_ADMIN;
        this.active = true;
    }

    public AdminUser(String username, String role) {
        this();
        this.username = username;
        this.role = role;
    }

    public boolean isAdmin() {
        return ROLE_ADMIN.equals(role);
    }

    public boolean isOperator() {
        return ROLE_OPERATOR.equals(role);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public String toString() {
        return username + " (" + role + ")";
    }
}
