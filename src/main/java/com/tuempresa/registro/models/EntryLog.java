package com.tuempresa.registro.models;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Clase modelo que representa un registro de entrada/salida.
 * Almacena el historial de escaneos de estudiantes.
 */
public class EntryLog {

    private Long id;
    private Long studentId;
    private Long guardianId;
    private LocalDateTime entryTime;
    private LocalDateTime exitTime;
    private String logType;
    private String scannedBy;
    private String notes;

    // Referencias a objetos relacionados (cargadas cuando es necesario)
    private Student student;
    private Guardian guardian;

    // Formateador de fecha/hora para display
    private static final DateTimeFormatter DISPLAY_FORMATTER =
            DateTimeFormatter.ofPattern("dd/MM/yyyy hh:mm:ss a");

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("hh:mm:ss a");

    /**
     * Constructor vacío.
     */
    public EntryLog() {
        this.entryTime = LocalDateTime.now();
        this.logType = "exit";
    }

    /**
     * Constructor con campos principales.
     *
     * @param studentId ID del estudiante
     * @param scannedBy Usuario que realizó el escaneo
     */
    public EntryLog(Long studentId, String scannedBy) {
        this();
        this.studentId = studentId;
        this.scannedBy = scannedBy;
    }

    /**
     * Constructor completo.
     *
     * @param id         ID del registro
     * @param studentId  ID del estudiante
     * @param guardianId ID del acudiente (puede ser null)
     * @param entryTime  Hora de entrada/escaneo
     * @param exitTime   Hora de salida
     * @param logType    Tipo de registro ('entry' o 'exit')
     * @param scannedBy  Usuario que escaneó
     * @param notes      Notas adicionales
     */
    public EntryLog(Long id, Long studentId, Long guardianId, LocalDateTime entryTime,
                    LocalDateTime exitTime, String logType, String scannedBy, String notes) {
        this.id = id;
        this.studentId = studentId;
        this.guardianId = guardianId;
        this.entryTime = entryTime;
        this.exitTime = exitTime;
        this.logType = logType;
        this.scannedBy = scannedBy;
        this.notes = notes;
    }

    /**
     * Obtiene la hora de entrada formateada para mostrar.
     *
     * @return Hora formateada
     */
    public String getFormattedEntryTime() {
        return entryTime != null ? entryTime.format(DISPLAY_FORMATTER) : "";
    }

    /**
     * Obtiene solo la hora (sin fecha) formateada.
     *
     * @return Hora formateada
     */
    public String getTimeOnly() {
        return entryTime != null ? entryTime.format(TIME_FORMATTER) : "";
    }

    /**
     * Obtiene la hora de salida formateada para mostrar.
     *
     * @return Hora formateada o vacío si no hay salida registrada
     */
    public String getFormattedExitTime() {
        return exitTime != null ? exitTime.format(DISPLAY_FORMATTER) : "";
    }

    /**
     * Verifica si es un registro de entrada.
     *
     * @return true si es entrada
     */
    public boolean isEntry() {
        return "entry".equalsIgnoreCase(logType);
    }

    /**
     * Verifica si es un registro de salida.
     *
     * @return true si es salida
     */
    public boolean isExit() {
        return "exit".equalsIgnoreCase(logType);
    }

    /**
     * Obtiene un resumen del registro para mostrar en el historial.
     *
     * @return Resumen legible del registro
     */
    public String getSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append(getTimeOnly());
        sb.append(" - ");

        if (student != null) {
            sb.append(student.getFullName());
        } else {
            sb.append("Estudiante ID: ").append(studentId);
        }

        if (guardianId != null && guardian != null) {
            sb.append(" (con ").append(guardian.getName()).append(")");
        }

        return sb.toString();
    }

    // Getters y Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getStudentId() {
        return studentId;
    }

    public void setStudentId(Long studentId) {
        this.studentId = studentId;
    }

    public Long getGuardianId() {
        return guardianId;
    }

    public void setGuardianId(Long guardianId) {
        this.guardianId = guardianId;
    }

    public LocalDateTime getEntryTime() {
        return entryTime;
    }

    public void setEntryTime(LocalDateTime entryTime) {
        this.entryTime = entryTime;
    }

    public LocalDateTime getExitTime() {
        return exitTime;
    }

    public void setExitTime(LocalDateTime exitTime) {
        this.exitTime = exitTime;
    }

    public String getLogType() {
        return logType;
    }

    public void setLogType(String logType) {
        this.logType = logType;
    }

    public String getScannedBy() {
        return scannedBy;
    }

    public void setScannedBy(String scannedBy) {
        this.scannedBy = scannedBy;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Student getStudent() {
        return student;
    }

    public void setStudent(Student student) {
        this.student = student;
        if (student != null) {
            this.studentId = student.getId();
        }
    }

    public Guardian getGuardian() {
        return guardian;
    }

    public void setGuardian(Guardian guardian) {
        this.guardian = guardian;
        if (guardian != null) {
            this.guardianId = guardian.getId();
        }
    }

    @Override
    public String toString() {
        return "EntryLog{" +
                "id=" + id +
                ", studentId=" + studentId +
                ", logType='" + logType + '\'' +
                ", entryTime=" + getFormattedEntryTime() +
                ", scannedBy='" + scannedBy + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EntryLog entryLog = (EntryLog) o;
        return id != null && id.equals(entryLog.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}
