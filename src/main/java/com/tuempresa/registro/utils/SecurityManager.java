package com.tuempresa.registro.utils;

import com.tuempresa.registro.dao.DatabaseConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.*;
import java.util.Base64;

/**
 * Gestor de seguridad para el sistema.
 * Maneja la contraseña de administrador para operaciones sensibles.
 */
public class SecurityManager {

    private static final Logger logger = LoggerFactory.getLogger(SecurityManager.class);

    private static SecurityManager instance;
    private final DatabaseConnection dbConnection;

    // Salt para el hash de contraseña
    private static final String SALT = "RegistroEstudiantes2024!";

    private SecurityManager() {
        this.dbConnection = DatabaseConnection.getInstance();
    }

    public static synchronized SecurityManager getInstance() {
        if (instance == null) {
            instance = new SecurityManager();
        }
        return instance;
    }

    /**
     * Verifica si ya existe una contraseña configurada en el sistema.
     *
     * @return true si ya hay contraseña configurada
     */
    public boolean isPasswordConfigured() {
        String sql = "SELECT value FROM app_config WHERE key = 'admin_password'";

        try (Connection conn = dbConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            if (rs.next()) {
                String value = rs.getString("value");
                return value != null && !value.isEmpty();
            }

        } catch (SQLException e) {
            logger.error("Error al verificar configuración de contraseña", e);
        }

        return false;
    }

    /**
     * Configura la contraseña del sistema por primera vez.
     *
     * @param password Contraseña a configurar
     * @return true si se configuró correctamente
     */
    public boolean setPassword(String password) {
        if (password == null || password.length() < 4) {
            logger.warn("Contraseña muy corta (mínimo 4 caracteres)");
            return false;
        }

        String hashedPassword = hashPassword(password);
        String sql = "INSERT OR REPLACE INTO app_config (key, value, description, updated_at) " +
                "VALUES ('admin_password', ?, 'Contraseña de administrador (hash)', CURRENT_TIMESTAMP)";

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, hashedPassword);
            stmt.executeUpdate();

            logger.info("Contraseña del sistema configurada correctamente");
            return true;

        } catch (SQLException e) {
            logger.error("Error al configurar contraseña", e);
            return false;
        }
    }

    /**
     * Verifica si la contraseña ingresada es correcta.
     *
     * @param password Contraseña a verificar
     * @return true si la contraseña es correcta
     */
    public boolean verifyPassword(String password) {
        if (password == null || password.isEmpty()) {
            return false;
        }

        String sql = "SELECT value FROM app_config WHERE key = 'admin_password'";

        try (Connection conn = dbConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            if (rs.next()) {
                String storedHash = rs.getString("value");
                String inputHash = hashPassword(password);
                return storedHash != null && storedHash.equals(inputHash);
            }

        } catch (SQLException e) {
            logger.error("Error al verificar contraseña", e);
        }

        return false;
    }

    /**
     * Cambia la contraseña del sistema.
     *
     * @param currentPassword Contraseña actual
     * @param newPassword     Nueva contraseña
     * @return true si se cambió correctamente
     */
    public boolean changePassword(String currentPassword, String newPassword) {
        if (!verifyPassword(currentPassword)) {
            logger.warn("Contraseña actual incorrecta");
            return false;
        }

        return setPassword(newPassword);
    }

    /**
     * Genera un hash SHA-256 de la contraseña con salt.
     *
     * @param password Contraseña en texto plano
     * @return Hash de la contraseña
     */
    private String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String saltedPassword = SALT + password + SALT;
            byte[] hash = digest.digest(saltedPassword.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            logger.error("Error al generar hash de contraseña", e);
            // Fallback simple (no recomendado en producción)
            return Base64.getEncoder().encodeToString(password.getBytes());
        }
    }
}
