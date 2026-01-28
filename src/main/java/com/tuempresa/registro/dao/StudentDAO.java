package com.tuempresa.registro.dao;

import com.tuempresa.registro.models.Student;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object para la entidad Student.
 * Proporciona operaciones CRUD completas para estudiantes.
 */
public class StudentDAO {

    private static final Logger logger = LoggerFactory.getLogger(StudentDAO.class);

    private final DatabaseConnection dbConnection;

    /**
     * Constructor que inicializa la conexión a la base de datos.
     */
    public StudentDAO() {
        this.dbConnection = DatabaseConnection.getInstance();
    }

    /**
     * Busca un estudiante por su ID.
     *
     * @param id ID del estudiante
     * @return Optional con el estudiante si existe
     */
    public Optional<Student> findById(Long id) {
        String sql = "SELECT * FROM students WHERE id = ?";

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, id);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToStudent(rs));
                }
            }

        } catch (SQLException e) {
            logger.error("Error al buscar estudiante por ID: {}", id, e);
        }

        return Optional.empty();
    }

    /**
     * Busca un estudiante por su código de barras.
     *
     * @param barcode Código de barras del carné
     * @return Optional con el estudiante si existe
     */
    public Optional<Student> findByBarcode(String barcode) {
        String sql = "SELECT * FROM students WHERE barcode = ? AND status = 'active'";

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, barcode);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Student student = mapResultSetToStudent(rs);
                    logger.debug("Estudiante encontrado: {}", student.getFullName());
                    return Optional.of(student);
                }
            }

        } catch (SQLException e) {
            logger.error("Error al buscar estudiante por código de barras: {}", barcode, e);
        }

        logger.debug("No se encontró estudiante con código: {}", barcode);
        return Optional.empty();
    }

    /**
     * Obtiene todos los estudiantes.
     *
     * @return Lista de todos los estudiantes
     */
    public List<Student> findAll() {
        String sql = "SELECT * FROM students ORDER BY last_name, first_name";
        List<Student> students = new ArrayList<>();

        try (Connection conn = dbConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                students.add(mapResultSetToStudent(rs));
            }

        } catch (SQLException e) {
            logger.error("Error al obtener todos los estudiantes", e);
        }

        return students;
    }

    /**
     * Obtiene todos los estudiantes activos.
     *
     * @return Lista de estudiantes activos
     */
    public List<Student> findAllActive() {
        String sql = "SELECT * FROM students WHERE status = 'active' ORDER BY last_name, first_name";
        List<Student> students = new ArrayList<>();

        try (Connection conn = dbConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                students.add(mapResultSetToStudent(rs));
            }

        } catch (SQLException e) {
            logger.error("Error al obtener estudiantes activos", e);
        }

        return students;
    }

    /**
     * Busca estudiantes por nombre o apellido.
     *
     * @param searchTerm Término de búsqueda
     * @return Lista de estudiantes que coinciden
     */
    public List<Student> searchByName(String searchTerm) {
        String sql = "SELECT * FROM students WHERE " +
                "(first_name LIKE ? OR last_name LIKE ? OR barcode LIKE ?) " +
                "AND status = 'active' " +
                "ORDER BY last_name, first_name";

        List<Student> students = new ArrayList<>();
        String searchPattern = "%" + searchTerm + "%";

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, searchPattern);
            stmt.setString(2, searchPattern);
            stmt.setString(3, searchPattern);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    students.add(mapResultSetToStudent(rs));
                }
            }

        } catch (SQLException e) {
            logger.error("Error al buscar estudiantes por nombre: {}", searchTerm, e);
        }

        return students;
    }

    /**
     * Guarda un nuevo estudiante en la base de datos.
     *
     * @param student Estudiante a guardar
     * @return El estudiante guardado con su ID asignado
     * @throws SQLException Si ocurre un error al guardar
     */
    public Student save(Student student) throws SQLException {
        String sql = "INSERT INTO students (barcode, first_name, last_name, grade, " +
                "is_minor, requires_guardian, photo_path, status) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        Connection conn = dbConnection.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, student.getBarcode());
            stmt.setString(2, student.getFirstName());
            stmt.setString(3, student.getLastName());
            stmt.setString(4, student.getGrade());
            stmt.setInt(5, student.isMinor() ? 1 : 0);
            stmt.setInt(6, student.isRequiresGuardian() ? 1 : 0);
            stmt.setString(7, student.getPhotoPath());
            stmt.setString(8, student.getStatus() != null ? student.getStatus() : "active");

            int affectedRows = stmt.executeUpdate();

            if (affectedRows > 0) {
                // SQLite: usar last_insert_rowid() para obtener el ID generado
                try (Statement idStmt = conn.createStatement();
                     ResultSet rs = idStmt.executeQuery("SELECT last_insert_rowid()")) {
                    if (rs.next()) {
                        student.setId(rs.getLong(1));
                        student.setCreatedAt(LocalDateTime.now());
                        logger.info("Estudiante guardado: {}", student);
                    }
                }
            }

        } catch (SQLException e) {
            logger.error("Error al guardar estudiante: {}", student, e);
            throw e;
        }

        return student;
    }

    /**
     * Actualiza un estudiante existente.
     *
     * @param student Estudiante con los datos actualizados
     * @return true si se actualizó correctamente
     * @throws SQLException Si ocurre un error al actualizar
     */
    public boolean update(Student student) throws SQLException {
        String sql = "UPDATE students SET barcode = ?, first_name = ?, last_name = ?, " +
                "grade = ?, is_minor = ?, requires_guardian = ?, photo_path = ?, " +
                "status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?";

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, student.getBarcode());
            stmt.setString(2, student.getFirstName());
            stmt.setString(3, student.getLastName());
            stmt.setString(4, student.getGrade());
            stmt.setInt(5, student.isMinor() ? 1 : 0);
            stmt.setInt(6, student.isRequiresGuardian() ? 1 : 0);
            stmt.setString(7, student.getPhotoPath());
            stmt.setString(8, student.getStatus());
            stmt.setLong(9, student.getId());

            int affectedRows = stmt.executeUpdate();

            if (affectedRows > 0) {
                logger.info("Estudiante actualizado: {}", student);
                return true;
            }

        } catch (SQLException e) {
            logger.error("Error al actualizar estudiante: {}", student, e);
            throw e;
        }

        return false;
    }

    /**
     * Elimina un estudiante por su ID.
     * Nota: En producción, considera usar "soft delete" (cambiar status a 'inactive').
     *
     * @param id ID del estudiante a eliminar
     * @return true si se eliminó correctamente
     * @throws SQLException Si ocurre un error al eliminar
     */
    public boolean delete(Long id) throws SQLException {
        String sql = "DELETE FROM students WHERE id = ?";

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, id);
            int affectedRows = stmt.executeUpdate();

            if (affectedRows > 0) {
                logger.info("Estudiante eliminado, ID: {}", id);
                return true;
            }

        } catch (SQLException e) {
            logger.error("Error al eliminar estudiante, ID: {}", id, e);
            throw e;
        }

        return false;
    }

    /**
     * Desactiva un estudiante (soft delete).
     *
     * @param id ID del estudiante a desactivar
     * @return true si se desactivó correctamente
     */
    public boolean deactivate(Long id) {
        String sql = "UPDATE students SET status = 'inactive', " +
                "updated_at = CURRENT_TIMESTAMP WHERE id = ?";

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, id);
            int affectedRows = stmt.executeUpdate();

            if (affectedRows > 0) {
                logger.info("Estudiante desactivado, ID: {}", id);
                return true;
            }

        } catch (SQLException e) {
            logger.error("Error al desactivar estudiante, ID: {}", id, e);
        }

        return false;
    }

    /**
     * Cuenta el total de estudiantes activos.
     *
     * @return Número total de estudiantes activos
     */
    public int countActive() {
        String sql = "SELECT COUNT(*) FROM students WHERE status = 'active'";

        try (Connection conn = dbConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            if (rs.next()) {
                return rs.getInt(1);
            }

        } catch (SQLException e) {
            logger.error("Error al contar estudiantes activos", e);
        }

        return 0;
    }

    /**
     * Verifica si existe un estudiante con el código de barras dado.
     *
     * @param barcode Código de barras a verificar
     * @return true si el código ya existe
     */
    public boolean existsByBarcode(String barcode) {
        String sql = "SELECT COUNT(*) FROM students WHERE barcode = ?";

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, barcode);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }

        } catch (SQLException e) {
            logger.error("Error al verificar código de barras: {}", barcode, e);
        }

        return false;
    }

    /**
     * Mapea un ResultSet a un objeto Student.
     *
     * @param rs ResultSet con los datos
     * @return Objeto Student mapeado
     * @throws SQLException Si ocurre un error al leer los datos
     */
    private Student mapResultSetToStudent(ResultSet rs) throws SQLException {
        Student student = new Student();
        student.setId(rs.getLong("id"));
        student.setBarcode(rs.getString("barcode"));
        student.setFirstName(rs.getString("first_name"));
        student.setLastName(rs.getString("last_name"));
        student.setGrade(rs.getString("grade"));
        student.setMinor(rs.getInt("is_minor") == 1);
        student.setRequiresGuardian(rs.getInt("requires_guardian") == 1);
        student.setPhotoPath(rs.getString("photo_path"));
        student.setStatus(rs.getString("status"));

        // Convertir timestamps
        String createdAt = rs.getString("created_at");
        if (createdAt != null) {
            student.setCreatedAt(LocalDateTime.parse(createdAt.replace(" ", "T")));
        }

        String updatedAt = rs.getString("updated_at");
        if (updatedAt != null) {
            student.setUpdatedAt(LocalDateTime.parse(updatedAt.replace(" ", "T")));
        }

        return student;
    }
}
