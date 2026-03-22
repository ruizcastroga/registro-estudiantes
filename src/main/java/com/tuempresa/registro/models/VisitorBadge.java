package com.tuempresa.registro.models;

import java.time.LocalDateTime;

/**
 * Representa un carné físico del sistema de visitantes.
 * Los carnés se prestan al entrar y se devuelven al salir.
 */
public class VisitorBadge {

    public static final String STATUS_AVAILABLE = "available";
    public static final String STATUS_IN_USE = "in_use";
    public static final String STATUS_LOST = "lost";

    private Long id;
    private String code;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public VisitorBadge() {
        this.status = STATUS_AVAILABLE;
    }

    public VisitorBadge(String code) {
        this();
        this.code = code;
    }

    public boolean isAvailable() {
        return STATUS_AVAILABLE.equals(status);
    }

    public boolean isInUse() {
        return STATUS_IN_USE.equals(status);
    }

    public boolean isLost() {
        return STATUS_LOST.equals(status);
    }

    public String getStatusDisplay() {
        return switch (status) {
            case STATUS_AVAILABLE -> "Disponible";
            case STATUS_IN_USE -> "En uso";
            case STATUS_LOST -> "Perdido";
            default -> status;
        };
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public String toString() {
        return "VisitorBadge{id=" + id + ", code='" + code + "', status='" + status + "'}";
    }
}
