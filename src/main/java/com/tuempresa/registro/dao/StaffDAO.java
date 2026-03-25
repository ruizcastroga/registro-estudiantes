package com.tuempresa.registro.dao;

import com.tuempresa.registro.models.StaffMember;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object para la entidad StaffMember.
 * Proporciona operaciones CRUD completas para personal.
 */
public class StaffDAO {

    private static final Logger logger = LoggerFactory.getLogger(StaffDAO.class);

    private final DatabaseConnection dbConnection;

    /**
     * Constructor que inicializa la conexión a la base de datos.
     */
    public StaffDAO() {
        this.dbConnection = DatabaseConnection.getInstance();
    }

    /**
     * Busca un miembro del personal por su ID.
     *
     * @param id ID del miembro del personal
     * @return Optional con el miembro si existe
     */
    public Optional<StaffMember> findById(Long id) {
        String sql = "SELECT * FROM staff WHERE id = ?";

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, id);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToStaffMember(rs));
                }
            }

        } catch (SQLException e) {
            logger.error("Error al buscar personal por ID: {}", id, e);
        }

        return Optional.empty();
    }

    /**
     * Busca un miembro del personal por su código de barras.
     *
     * @param barcode Código de barras del carné
     * @return Optional con el miembro si existe
     */
    public Optional<StaffMember> findByBarcode(String barcode) {
        String sql = "SELECT * FROM staff WHERE barcode = ?";

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, barcode);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    StaffMember staff = mapResultSetToStaffMember(rs);
                    logger.debug("Personal encontrado: {} {} (estado: {})", staff.getFirstName(), staff.getLastName(), staff.getStatus());
                    return Optional.of(staff);
                }
            }

        } catch (SQLException e) {
            logger.error("Error al buscar personal por código de barras: {}", barcode, e);
        }

        logger.debug("No se encontró personal con código: {}", barcode);
        return Optional.empty();
    }

    /**
     * Obtiene todos los miembros del personal.
     *
     * @return Lista de todos los miembros del personal
     */
    public List<StaffMember> findAll() {
        String sql = "SELECT * FROM staff ORDER BY last_name, first_name";
        List<StaffMember> staffList = new ArrayList<>();

        try (Connection conn = dbConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                staffList.add(mapResultSetToStaffMember(rs));
            }

        } catch (SQLException e) {
            logger.error("Error al obtener todo el personal", e);
        }

        return staffList;
    }

    /**
     * Obtiene todos los miembros del personal activos.
     *
     * @return Lista de miembros del personal activos
     */
    public List<StaffMember> findAllActive() {
        String sql = "SELECT * FROM staff WHERE status = 'active' ORDER BY last_name, first_name";
        List<StaffMember> staffList = new ArrayList<>();

        try (Connection conn = dbConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                staffList.add(mapResultSetToStaffMember(rs));
            }

        } catch (SQLException e) {
            logger.error("Error al obtener personal activo", e);
        }

        return staffList;
    }

    /**
     * Busca miembros del personal por nombre, apellido, código de barras o número de identificación.
     *
     * @param searchTerm Término de búsqueda
     * @return Lista de miembros que coinciden
     */
    public List<StaffMember> searchByName(String searchTerm) {
        String sql = "SELECT * FROM staff WHERE " +
                "(first_name LIKE ? OR last_name LIKE ? OR barcode LIKE ? OR id_number LIKE ?) " +
                "ORDER BY last_name, first_name";

        List<StaffMember> staffList = new ArrayList<>();
        String searchPattern = "%" + searchTerm + "%";

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, searchPattern);
            stmt.setString(2, searchPattern);
            stmt.setString(3, searchPattern);
            stmt.setString(4, searchPattern);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    staffList.add(mapResultSetToStaffMember(rs));
                }
            }

        } catch (SQLException e) {
            logger.error("Error al buscar personal por nombre: {}", searchTerm, e);
        }

        return staffList;
    }

    /**
     * Guarda un nuevo miembro del personal en la base de datos.
     *
     * @param staff Miembro del personal a guardar
     * @return El miembro guardado con su ID asignado
     * @throws SQLException Si ocurre un error al guardar
     */
    public StaffMember save(StaffMember staff) throws SQLException {
        String sql = "INSERT INTO staff (barcode, first_name, last_name, id_number, " +
                "department, status, created_by, updated_by) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        Connection conn = dbConnection.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, staff.getBarcode());
            stmt.setString(2, staff.getFirstName());
            stmt.setString(3, staff.getLastName());
            stmt.setString(4, staff.getIdNumber());
            stmt.setString(5, staff.getDepartment());
            stmt.setString(6, staff.getStatus() != null ? staff.getStatus() : "active");
            stmt.setString(7, staff.getCreatedBy());
            stmt.setString(8, staff.getUpdatedBy());

            int affectedRows = stmt.executeUpdate();

            if (affectedRows > 0) {
                // SQLite: usar last_insert_rowid() para obtener el ID generado
                try (Statement idStmt = conn.createStatement();
                     ResultSet rs = idStmt.executeQuery("SELECT last_insert_rowid()")) {
                    if (rs.next()) {
                        staff.setId(rs.getLong(1));
                        staff.setCreatedAt(LocalDateTime.now());
                        logger.info("Personal guardado: {}", staff);
                    }
                }
            }

        } catch (SQLException e) {
            logger.error("Error al guardar personal: {}", staff, e);
            throw e;
        }

        return staff;
    }

    /**
     * Actualiza un miembro del personal existente.
     *
     * @param staff Miembro del personal con los datos actualizados
     * @return true si se actualizó correctamente
     * @throws SQLException Si ocurre un error al actualizar
     */
    public boolean update(StaffMember staff) throws SQLException {
        String sql = "UPDATE staff SET barcode = ?, first_name = ?, last_name = ?, " +
                "id_number = ?, department = ?, status = ?, updated_by = ?, " +
                "updated_at = CURRENT_TIMESTAMP WHERE id = ?";

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, staff.getBarcode());
            stmt.setString(2, staff.getFirstName());
            stmt.setString(3, staff.getLastName());
            stmt.setString(4, staff.getIdNumber());
            stmt.setString(5, staff.getDepartment());
            stmt.setString(6, staff.getStatus());
            stmt.setString(7, staff.getUpdatedBy());
            stmt.setLong(8, staff.getId());

            int affectedRows = stmt.executeUpdate();

            if (affectedRows > 0) {
                logger.info("Personal actualizado: {}", staff);
                return true;
            }

        } catch (SQLException e) {
            logger.error("Error al actualizar personal: {}", staff, e);
            throw e;
        }

        return false;
    }

    /**
     * Elimina un miembro del personal por su ID.
     *
     * @param id ID del miembro a eliminar
     * @return true si se eliminó correctamente
     * @throws SQLException Si ocurre un error al eliminar
     */
    public boolean delete(Long id) throws SQLException {
        String sql = "DELETE FROM staff WHERE id = ?";

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, id);
            int affectedRows = stmt.executeUpdate();

            if (affectedRows > 0) {
                logger.info("Personal eliminado, ID: {}", id);
                return true;
            }

        } catch (SQLException e) {
            logger.error("Error al eliminar personal, ID: {}", id, e);
            throw e;
        }

        return false;
    }

    /**
     * Verifica si existe un miembro del personal con el código de barras dado.
     *
     * @param barcode Código de barras a verificar
     * @return true si el código ya existe
     */
    public boolean existsByBarcode(String barcode) {
        String sql = "SELECT COUNT(*) FROM staff WHERE barcode = ?";

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
     * Cuenta el total de miembros del personal activos.
     *
     * @return Número total de miembros activos
     */
    public int countActive() {
        String sql = "SELECT COUNT(*) FROM staff WHERE status = 'active'";

        try (Connection conn = dbConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            if (rs.next()) {
                return rs.getInt(1);
            }

        } catch (SQLException e) {
            logger.error("Error al contar personal activo", e);
        }

        return 0;
    }

    /**
     * Mapea un ResultSet a un objeto StaffMember.
     *
     * @param rs ResultSet con los datos
     * @return Objeto StaffMember mapeado
     * @throws SQLException Si ocurre un error al leer los datos
     */
    private StaffMember mapResultSetToStaffMember(ResultSet rs) throws SQLException {
        StaffMember staff = new StaffMember();
        staff.setId(rs.getLong("id"));
        staff.setBarcode(rs.getString("barcode"));
        staff.setFirstName(rs.getString("first_name"));
        staff.setLastName(rs.getString("last_name"));
        staff.setIdNumber(rs.getString("id_number"));
        staff.setDepartment(rs.getString("department"));
        staff.setStatus(rs.getString("status"));
        staff.setCreatedBy(rs.getString("created_by"));
        staff.setUpdatedBy(rs.getString("updated_by"));

        // Convertir timestamps
        String createdAt = rs.getString("created_at");
        if (createdAt != null) {
            staff.setCreatedAt(LocalDateTime.parse(createdAt.replace(" ", "T")));
        }

        String updatedAt = rs.getString("updated_at");
        if (updatedAt != null) {
            staff.setUpdatedAt(LocalDateTime.parse(updatedAt.replace(" ", "T")));
        }

        return staff;
    }
}
