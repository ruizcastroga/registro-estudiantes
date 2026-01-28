package com.tuempresa.registro.dao;

import com.tuempresa.registro.models.Guardian;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object para la entidad Guardian.
 * Proporciona operaciones CRUD completas para acudientes/tutores.
 */
public class GuardianDAO {

    private static final Logger logger = LoggerFactory.getLogger(GuardianDAO.class);

    private final DatabaseConnection dbConnection;

    /**
     * Constructor que inicializa la conexión a la base de datos.
     */
    public GuardianDAO() {
        this.dbConnection = DatabaseConnection.getInstance();
    }

    /**
     * Busca un acudiente por su ID.
     *
     * @param id ID del acudiente
     * @return Optional con el acudiente si existe
     */
    public Optional<Guardian> findById(Long id) {
        String sql = "SELECT * FROM guardians WHERE id = ?";

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, id);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToGuardian(rs));
                }
            }

        } catch (SQLException e) {
            logger.error("Error al buscar acudiente por ID: {}", id, e);
        }

        return Optional.empty();
    }

    /**
     * Busca todos los acudientes de un estudiante.
     *
     * @param studentId ID del estudiante
     * @return Lista de acudientes del estudiante
     */
    public List<Guardian> findByStudentId(Long studentId) {
        String sql = "SELECT * FROM guardians WHERE student_id = ? ORDER BY name";
        List<Guardian> guardians = new ArrayList<>();

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, studentId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    guardians.add(mapResultSetToGuardian(rs));
                }
            }

        } catch (SQLException e) {
            logger.error("Error al buscar acudientes del estudiante: {}", studentId, e);
        }

        return guardians;
    }

    /**
     * Busca acudientes autorizados de un estudiante.
     *
     * @param studentId ID del estudiante
     * @return Lista de acudientes autorizados
     */
    public List<Guardian> findAuthorizedByStudentId(Long studentId) {
        String sql = "SELECT * FROM guardians WHERE student_id = ? AND authorized = 1 ORDER BY name";
        List<Guardian> guardians = new ArrayList<>();

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, studentId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    guardians.add(mapResultSetToGuardian(rs));
                }
            }

        } catch (SQLException e) {
            logger.error("Error al buscar acudientes autorizados del estudiante: {}", studentId, e);
        }

        return guardians;
    }

    /**
     * Busca un acudiente por su número de documento.
     *
     * @param idNumber Número de documento
     * @return Optional con el acudiente si existe
     */
    public Optional<Guardian> findByIdNumber(String idNumber) {
        String sql = "SELECT * FROM guardians WHERE id_number = ?";

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, idNumber);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToGuardian(rs));
                }
            }

        } catch (SQLException e) {
            logger.error("Error al buscar acudiente por documento: {}", idNumber, e);
        }

        return Optional.empty();
    }

    /**
     * Obtiene todos los acudientes.
     *
     * @return Lista de todos los acudientes
     */
    public List<Guardian> findAll() {
        String sql = "SELECT * FROM guardians ORDER BY name";
        List<Guardian> guardians = new ArrayList<>();

        try (Connection conn = dbConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                guardians.add(mapResultSetToGuardian(rs));
            }

        } catch (SQLException e) {
            logger.error("Error al obtener todos los acudientes", e);
        }

        return guardians;
    }

    /**
     * Guarda un nuevo acudiente en la base de datos.
     *
     * @param guardian Acudiente a guardar
     * @return El acudiente guardado con su ID asignado
     * @throws SQLException Si ocurre un error al guardar
     */
    public Guardian save(Guardian guardian) throws SQLException {
        String sql = "INSERT INTO guardians (student_id, name, relationship, " +
                "id_number, phone, authorized) VALUES (?, ?, ?, ?, ?, ?)";

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setLong(1, guardian.getStudentId());
            stmt.setString(2, guardian.getName());
            stmt.setString(3, guardian.getRelationship());
            stmt.setString(4, guardian.getIdNumber());
            stmt.setString(5, guardian.getPhone());
            stmt.setInt(6, guardian.isAuthorized() ? 1 : 0);

            int affectedRows = stmt.executeUpdate();

            if (affectedRows > 0) {
                try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        guardian.setId(generatedKeys.getLong(1));
                        guardian.setCreatedAt(LocalDateTime.now());
                        logger.info("Acudiente guardado: {}", guardian);
                    }
                }
            }

        } catch (SQLException e) {
            logger.error("Error al guardar acudiente: {}", guardian, e);
            throw e;
        }

        return guardian;
    }

    /**
     * Actualiza un acudiente existente.
     *
     * @param guardian Acudiente con los datos actualizados
     * @return true si se actualizó correctamente
     * @throws SQLException Si ocurre un error al actualizar
     */
    public boolean update(Guardian guardian) throws SQLException {
        String sql = "UPDATE guardians SET student_id = ?, name = ?, relationship = ?, " +
                "id_number = ?, phone = ?, authorized = ?, " +
                "updated_at = CURRENT_TIMESTAMP WHERE id = ?";

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, guardian.getStudentId());
            stmt.setString(2, guardian.getName());
            stmt.setString(3, guardian.getRelationship());
            stmt.setString(4, guardian.getIdNumber());
            stmt.setString(5, guardian.getPhone());
            stmt.setInt(6, guardian.isAuthorized() ? 1 : 0);
            stmt.setLong(7, guardian.getId());

            int affectedRows = stmt.executeUpdate();

            if (affectedRows > 0) {
                logger.info("Acudiente actualizado: {}", guardian);
                return true;
            }

        } catch (SQLException e) {
            logger.error("Error al actualizar acudiente: {}", guardian, e);
            throw e;
        }

        return false;
    }

    /**
     * Elimina un acudiente por su ID.
     *
     * @param id ID del acudiente a eliminar
     * @return true si se eliminó correctamente
     * @throws SQLException Si ocurre un error al eliminar
     */
    public boolean delete(Long id) throws SQLException {
        String sql = "DELETE FROM guardians WHERE id = ?";

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, id);
            int affectedRows = stmt.executeUpdate();

            if (affectedRows > 0) {
                logger.info("Acudiente eliminado, ID: {}", id);
                return true;
            }

        } catch (SQLException e) {
            logger.error("Error al eliminar acudiente, ID: {}", id, e);
            throw e;
        }

        return false;
    }

    /**
     * Elimina todos los acudientes de un estudiante.
     *
     * @param studentId ID del estudiante
     * @return Número de acudientes eliminados
     */
    public int deleteByStudentId(Long studentId) {
        String sql = "DELETE FROM guardians WHERE student_id = ?";

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, studentId);
            int affectedRows = stmt.executeUpdate();
            logger.info("Acudientes eliminados del estudiante {}: {}", studentId, affectedRows);
            return affectedRows;

        } catch (SQLException e) {
            logger.error("Error al eliminar acudientes del estudiante: {}", studentId, e);
        }

        return 0;
    }

    /**
     * Autoriza o desautoriza a un acudiente.
     *
     * @param id         ID del acudiente
     * @param authorized true para autorizar, false para desautorizar
     * @return true si se actualizó correctamente
     */
    public boolean setAuthorized(Long id, boolean authorized) {
        String sql = "UPDATE guardians SET authorized = ?, " +
                "updated_at = CURRENT_TIMESTAMP WHERE id = ?";

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, authorized ? 1 : 0);
            stmt.setLong(2, id);

            int affectedRows = stmt.executeUpdate();

            if (affectedRows > 0) {
                logger.info("Acudiente {} {}", id, authorized ? "autorizado" : "desautorizado");
                return true;
            }

        } catch (SQLException e) {
            logger.error("Error al cambiar autorización del acudiente: {}", id, e);
        }

        return false;
    }

    /**
     * Cuenta los acudientes autorizados de un estudiante.
     *
     * @param studentId ID del estudiante
     * @return Número de acudientes autorizados
     */
    public int countAuthorizedByStudentId(Long studentId) {
        String sql = "SELECT COUNT(*) FROM guardians WHERE student_id = ? AND authorized = 1";

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, studentId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }

        } catch (SQLException e) {
            logger.error("Error al contar acudientes autorizados del estudiante: {}", studentId, e);
        }

        return 0;
    }

    /**
     * Mapea un ResultSet a un objeto Guardian.
     *
     * @param rs ResultSet con los datos
     * @return Objeto Guardian mapeado
     * @throws SQLException Si ocurre un error al leer los datos
     */
    private Guardian mapResultSetToGuardian(ResultSet rs) throws SQLException {
        Guardian guardian = new Guardian();
        guardian.setId(rs.getLong("id"));
        guardian.setStudentId(rs.getLong("student_id"));
        guardian.setName(rs.getString("name"));
        guardian.setRelationship(rs.getString("relationship"));
        guardian.setIdNumber(rs.getString("id_number"));
        guardian.setPhone(rs.getString("phone"));
        guardian.setAuthorized(rs.getInt("authorized") == 1);

        // Convertir timestamps
        String createdAt = rs.getString("created_at");
        if (createdAt != null) {
            guardian.setCreatedAt(LocalDateTime.parse(createdAt.replace(" ", "T")));
        }

        String updatedAt = rs.getString("updated_at");
        if (updatedAt != null) {
            guardian.setUpdatedAt(LocalDateTime.parse(updatedAt.replace(" ", "T")));
        }

        return guardian;
    }
}
