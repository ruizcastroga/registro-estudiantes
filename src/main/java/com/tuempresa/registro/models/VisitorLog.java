package com.tuempresa.registro.models;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Representa un registro de visita (entrada/salida) en el sistema de visitantes.
 */
public class VisitorLog {

    private static final DateTimeFormatter DISPLAY_FORMATTER =
            DateTimeFormatter.ofPattern("dd/MM/yyyy hh:mm a");

    private Long id;
    private Long badgeId;
    private String badgeCode;       // Para mostrar en tablas (join)
    private String idNumber;        // Cédula (obligatorio)
    private String firstName;       // Nombre (opcional)
    private String lastName;        // Apellido (opcional)
    private String justification;   // Justificación de entrada (opcional)
    private LocalDateTime entryTime;
    private LocalDateTime exitTime; // null = aún dentro

    public VisitorLog() {}

    public VisitorLog(Long badgeId, String idNumber) {
        this.badgeId = badgeId;
        this.idNumber = idNumber;
        this.entryTime = LocalDateTime.now();
    }

    public boolean isInsideNow() {
        return exitTime == null;
    }

    public String getFullName() {
        if (firstName == null && lastName == null) return "";
        if (firstName == null) return lastName;
        if (lastName == null) return firstName;
        return firstName + " " + lastName;
    }

    public String getDisplayName() {
        String full = getFullName();
        return full.isEmpty() ? idNumber : full + " (" + idNumber + ")";
    }

    public String getFormattedEntryTime() {
        return entryTime != null ? entryTime.format(DISPLAY_FORMATTER) : "";
    }

    public String getFormattedExitTime() {
        return exitTime != null ? exitTime.format(DISPLAY_FORMATTER) : "Dentro";
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getBadgeId() { return badgeId; }
    public void setBadgeId(Long badgeId) { this.badgeId = badgeId; }

    public String getBadgeCode() { return badgeCode; }
    public void setBadgeCode(String badgeCode) { this.badgeCode = badgeCode; }

    public String getIdNumber() { return idNumber; }
    public void setIdNumber(String idNumber) { this.idNumber = idNumber; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getJustification() { return justification; }
    public void setJustification(String justification) { this.justification = justification; }

    public LocalDateTime getEntryTime() { return entryTime; }
    public void setEntryTime(LocalDateTime entryTime) { this.entryTime = entryTime; }

    public LocalDateTime getExitTime() { return exitTime; }
    public void setExitTime(LocalDateTime exitTime) { this.exitTime = exitTime; }

    @Override
    public String toString() {
        return "VisitorLog{id=" + id + ", badge='" + badgeCode + "', idNumber='" + idNumber + "'}";
    }
}
