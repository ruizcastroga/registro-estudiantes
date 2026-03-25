package com.tuempresa.registro.dao;

import com.tuempresa.registro.models.AdminUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object para la entidad AdminUser.
 * Proporciona operaciones CRUD completas para usuarios administrativos.
 */
public class AdminUserDAO {

    private static final Logger logger = LoggerFactory.getLogger(AdminUserDAO.class);

    private final DatabaseConnection dbConnection;

    /**
     * Constructor que inicializa la conexión a la base de datos.
     */
    public AdminUserDAO() {
        this.dbConnection = DatabaseConnection.getInstance();
    }

    /**
     * Busca un usuario administrativo por su ID.
     *
     * @param id ID del usuario
     * @return Optional con el usuario si existe
     */
    public Optional<AdminUser> findById(Long id) {
        String sql = "SELECT * FROM admin_users WHERE id = ?";

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, id);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToAdminUser(rs));
                }
            }

        } catch (SQLException e) {
            logger.error("Error al buscar usuario administrativo por ID: {}", id, e);
        }

        return Optional.empty();
    }

    /**
     * Busca un usuario administrativo por su nombre de usuario (case insensitive).
     *
     * @param username Nombre de usuario
     * @return Optional con el usuario si existe
     */
    public Optional<AdminUser> findByUsername(String username) {
        String sql = "SELECT * FROM admin_users WHERE LOWER(username) = LOWER(?)";

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, username);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    AdminUser user = mapResultSetToAdminUser(rs);
                    logger.debug("Usuario administrativo encontrado: {} (rol: {})", user.getUsername(), user.getRole());
                    return Optional.of(user);
                }
            }

        } catch (SQLException e) {
            logger.error("Error al buscar usuario administrativo por username: {}", username, e);
        }

        logger.debug("No se encontró usuario administrativo con username: {}", username);
        return Optional.empty();
    }

    /**
     * Obtiene todos los usuarios administrativos activos.
     *
     * @return Lista de usuarios activos
     */
    public List<AdminUser> findAll() {
        String sql = "SELECT * FROM admin_users WHERE is_active = 1 ORDER BY username";
        List<AdminUser> users = new ArrayList<>();

        try (Connection conn = dbConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                users.add(mapResultSetToAdminUser(rs));
            }

        } catch (SQLException e) {
            logger.error("Error al obtener usuarios administrativos activos", e);
        }

        return users;
    }

    /**
     * Obtiene todos los usuarios administrativos incluyendo inactivos.
     *
     * @return Lista de todos los usuarios
     */
    public List<AdminUser> findAllIncludingInactive() {
        String sql = "SELECT * FROM admin_users ORDER BY username";
        List<AdminUser> users = new ArrayList<>();

        try (Connection conn = dbConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                users.add(mapResultSetToAdminUser(rs));
            }

        } catch (SQLException e) {
            logger.error("Error al obtener todos los usuarios administrativos", e);
        }

        return users;
    }

    /**
     * Guarda un nuevo usuario administrativo en la base de datos.
     *
     * @param user Usuario a guardar
     * @return El usuario guardado con su ID asignado
     * @throws SQLException Si ocurre un error al guardar
     */
    public AdminUser save(AdminUser user) throws SQLException {
        String sql = "INSERT INTO admin_users (username, password_hash, role, is_active) " +
                "VALUES (?, ?, ?, ?)";

        Connection conn = dbConnection.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, user.getUsername());
            stmt.setString(2, user.getPasswordHash());
            stmt.setString(3, user.getRole());
            stmt.setInt(4, user.isActive() ? 1 : 0);

            int affectedRows = stmt.executeUpdate();

            if (affectedRows > 0) {
                // SQLite: usar last_insert_rowid() para obtener el ID generado
                try (Statement idStmt = conn.createStatement();
                     ResultSet rs = idStmt.executeQuery("SELECT last_insert_rowid()")) {
                    if (rs.next()) {
                        user.setId(rs.getLong(1));
                        user.setCreatedAt(LocalDateTime.now());
                        logger.info("Usuario administrativo guardado: {}", user);
                    }
                }
            }

        } catch (SQLException e) {
            logger.error("Error al guardar usuario administrativo: {}", user, e);
            throw e;
        }

        return user;
    }

    /**
     * Actualiza un usuario administrativo existente.
     *
     * @param user Usuario con los datos actualizados
     * @return true si se actualizó correctamente
     * @throws SQLException Si ocurre un error al actualizar
     */
    public boolean update(AdminUser user) throws SQLException {
        String sql = "UPDATE admin_users SET username = ?, password_hash = ?, role = ?, " +
                "is_active = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?";

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, user.getUsername());
            stmt.setString(2, user.getPasswordHash());
            stmt.setString(3, user.getRole());
            stmt.setInt(4, user.isActive() ? 1 : 0);
            stmt.setLong(5, user.getId());

            int affectedRows = stmt.executeUpdate();

            if (affectedRows > 0) {
                logger.info("Usuario administrativo actualizado: {}", user);
                return true;
            }

        } catch (SQLException e) {
            logger.error("Error al actualizar usuario administrativo: {}", user, e);
            throw e;
        }

        return false;
    }

    /**
     * Elimina un usuario administrativo por su ID.
     *
     * @param id ID del usuario a eliminar
     * @return true si se eliminó correctamente
     * @throws SQLException Si ocurre un error al eliminar
     */
    public boolean delete(Long id) throws SQLException {
        String sql = "DELETE FROM admin_users WHERE id = ?";

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, id);
            int affectedRows = stmt.executeUpdate();

            if (affectedRows > 0) {
                logger.info("Usuario administrativo eliminado, ID: {}", id);
                return true;
            }

        } catch (SQLException e) {
            logger.error("Error al eliminar usuario administrativo, ID: {}", id, e);
            throw e;
        }

        return false;
    }

    /**
     * Verifica si existe un usuario con el nombre de usuario dado.
     *
     * @param username Nombre de usuario a verificar
     * @return true si el nombre de usuario ya existe
     */
    public boolean existsByUsername(String username) {
        String sql = "SELECT COUNT(*) FROM admin_users WHERE LOWER(username) = LOWER(?)";

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, username);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }

        } catch (SQLException e) {
            logger.error("Error al verificar nombre de usuario: {}", username, e);
        }

        return false;
    }

    /**
     * Cuenta el total de usuarios administrativos.
     *
     * @return Número total de usuarios administrativos
     */
    public int countAll() {
        String sql = "SELECT COUNT(*) FROM admin_users";

        try (Connection conn = dbConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            if (rs.next()) {
                return rs.getInt(1);
            }

        } catch (SQLException e) {
            logger.error("Error al contar usuarios administrativos", e);
        }

        return 0;
    }

    /**
     * Mapea un ResultSet a un objeto AdminUser.
     *
     * @param rs ResultSet con los datos
     * @return Objeto AdminUser mapeado
     * @throws SQLException Si ocurre un error al leer los datos
     */
    private AdminUser mapResultSetToAdminUser(ResultSet rs) throws SQLException {
        AdminUser user = new AdminUser();
        user.setId(rs.getLong("id"));
        user.setUsername(rs.getString("username"));
        user.setPasswordHash(rs.getString("password_hash"));
        user.setRole(rs.getString("role"));
        user.setActive(rs.getInt("is_active") == 1);

        // Convertir timestamps
        String createdAt = rs.getString("created_at");
        if (createdAt != null) {
            user.setCreatedAt(LocalDateTime.parse(createdAt.replace(" ", "T")));
        }

        String updatedAt = rs.getString("updated_at");
        if (updatedAt != null) {
            user.setUpdatedAt(LocalDateTime.parse(updatedAt.replace(" ", "T")));
        }

        return user;
    }
}
