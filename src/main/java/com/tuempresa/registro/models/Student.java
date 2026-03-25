package com.tuempresa.registro.models;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Clase modelo que representa a un estudiante en el sistema.
 * Contiene toda la información necesaria para el control de salida.
 */
public class Student {

    private Long id;
    private String barcode;
    private String firstName;
    private String lastName;
    private String grade;
    private boolean isMinor;
    private boolean requiresGuardian;
    private String photoPath;
    private String status;
    private String createdBy;
    private String updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Lista de acudientes autorizados (cargada cuando es necesario)
    private List<Guardian> guardians;

    /**
     * Constructor vacío.
     */
    public Student() {
        this.guardians = new ArrayList<>();
        this.isMinor = true;
        this.requiresGuardian = true;
        this.status = "active";
    }

    /**
     * Constructor con campos principales.
     *
     * @param barcode   Código de barras único
     * @param firstName Nombre del estudiante
     * @param lastName  Apellido del estudiante
     */
    public Student(String barcode, String firstName, String lastName) {
        this();
        this.barcode = barcode;
        this.firstName = firstName;
        this.lastName = lastName;
    }

    /**
     * Constructor completo.
     *
     * @param id               ID del estudiante
     * @param barcode          Código de barras único
     * @param firstName        Nombre del estudiante
     * @param lastName         Apellido del estudiante
     * @param grade            Grado/Curso
     * @param isMinor          Si es menor de edad
     * @param requiresGuardian Si requiere acompañante
     * @param photoPath        Ruta a la foto
     * @param status           Estado del estudiante
     */
    public Student(Long id, String barcode, String firstName, String lastName,
                   String grade, boolean isMinor, boolean requiresGuardian,
                   String photoPath, String status) {
        this(barcode, firstName, lastName);
        this.id = id;
        this.grade = grade;
        this.isMinor = isMinor;
        this.requiresGuardian = requiresGuardian;
        this.photoPath = photoPath;
        this.status = status;
    }

    /**
     * Obtiene el nombre completo del estudiante.
     *
     * @return Nombre completo (nombre + apellido)
     */
    public String getFullName() {
        return firstName + " " + lastName;
    }

    /**
     * Verifica si el estudiante puede salir solo (sin acompañante).
     *
     * @return true si puede salir solo, false si requiere acompañante
     */
    public boolean canExitAlone() {
        return !requiresGuardian;
    }

    /**
     * Verifica si el estudiante está activo en el sistema.
     *
     * @return true si está activo
     */
    public boolean isActive() {
        return "active".equalsIgnoreCase(status);
    }

    // Getters y Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getBarcode() {
        return barcode;
    }

    public void setBarcode(String barcode) {
        this.barcode = barcode;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getGrade() {
        return grade;
    }

    public void setGrade(String grade) {
        this.grade = grade;
    }

    public boolean isMinor() {
        return isMinor;
    }

    public void setMinor(boolean minor) {
        isMinor = minor;
    }

    public boolean isRequiresGuardian() {
        return requiresGuardian;
    }

    public void setRequiresGuardian(boolean requiresGuardian) {
        this.requiresGuardian = requiresGuardian;
    }

    public String getPhotoPath() {
        return photoPath;
    }

    public void setPhotoPath(String photoPath) {
        this.photoPath = photoPath;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }

    public List<Guardian> getGuardians() {
        return guardians;
    }

    public void setGuardians(List<Guardian> guardians) {
        this.guardians = guardians;
    }

    public void addGuardian(Guardian guardian) {
        if (this.guardians == null) {
            this.guardians = new ArrayList<>();
        }
        this.guardians.add(guardian);
    }

    @Override
    public String toString() {
        return "Student{" +
                "id=" + id +
                ", barcode='" + barcode + '\'' +
                ", fullName='" + getFullName() + '\'' +
                ", grade='" + grade + '\'' +
                ", requiresGuardian=" + requiresGuardian +
                ", status='" + status + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Student student = (Student) o;
        return id != null && id.equals(student.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}
