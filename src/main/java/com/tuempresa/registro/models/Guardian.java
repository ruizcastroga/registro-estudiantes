package com.tuempresa.registro.models;

import java.time.LocalDateTime;

/**
 * Clase modelo que representa a un acudiente/tutor autorizado.
 * Los acudientes son las personas autorizadas para recoger a un estudiante.
 */
public class Guardian {

    private Long id;
    private Long studentId;
    private String name;
    private String relationship;
    private String idNumber;
    private String phone;
    private boolean authorized;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Referencia al estudiante (cargada cuando es necesario)
    private Student student;

    /**
     * Constructor vacío.
     */
    public Guardian() {
        this.authorized = true;
    }

    /**
     * Constructor con campos principales.
     *
     * @param studentId    ID del estudiante asociado
     * @param name         Nombre completo del acudiente
     * @param relationship Relación con el estudiante
     */
    public Guardian(Long studentId, String name, String relationship) {
        this();
        this.studentId = studentId;
        this.name = name;
        this.relationship = relationship;
    }

    /**
     * Constructor completo.
     *
     * @param id           ID del acudiente
     * @param studentId    ID del estudiante asociado
     * @param name         Nombre completo
     * @param relationship Relación con el estudiante
     * @param idNumber     Número de documento
     * @param phone        Teléfono de contacto
     * @param authorized   Si está autorizado
     */
    public Guardian(Long id, Long studentId, String name, String relationship,
                    String idNumber, String phone, boolean authorized) {
        this(studentId, name, relationship);
        this.id = id;
        this.idNumber = idNumber;
        this.phone = phone;
        this.authorized = authorized;
    }

    /**
     * Obtiene una descripción legible del acudiente.
     *
     * @return Descripción del acudiente (nombre - relación)
     */
    public String getDescription() {
        if (relationship != null && !relationship.isEmpty()) {
            return name + " (" + relationship + ")";
        }
        return name;
    }

    /**
     * Verifica si el acudiente puede recoger al estudiante.
     *
     * @return true si está autorizado
     */
    public boolean canPickupStudent() {
        return authorized;
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRelationship() {
        return relationship;
    }

    public void setRelationship(String relationship) {
        this.relationship = relationship;
    }

    public String getIdNumber() {
        return idNumber;
    }

    public void setIdNumber(String idNumber) {
        this.idNumber = idNumber;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public boolean isAuthorized() {
        return authorized;
    }

    public void setAuthorized(boolean authorized) {
        this.authorized = authorized;
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

    public Student getStudent() {
        return student;
    }

    public void setStudent(Student student) {
        this.student = student;
        if (student != null) {
            this.studentId = student.getId();
        }
    }

    @Override
    public String toString() {
        return "Guardian{" +
                "id=" + id +
                ", studentId=" + studentId +
                ", name='" + name + '\'' +
                ", relationship='" + relationship + '\'' +
                ", authorized=" + authorized +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Guardian guardian = (Guardian) o;
        return id != null && id.equals(guardian.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}
