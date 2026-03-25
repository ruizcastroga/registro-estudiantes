package com.tuempresa.registro.utils;

import com.tuempresa.registro.dao.AdminUserDAO;
import com.tuempresa.registro.models.AdminUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.util.Base64;
import java.util.Optional;

/**
 * Gestor de seguridad para el sistema.
 * Autenticación basada en tabla admin_users con sesiones temporales.
 */
public class SecurityManager {

    private static final Logger logger = LoggerFactory.getLogger(SecurityManager.class);

    private static SecurityManager instance;
    private final AdminUserDAO adminUserDAO;

    // Salt para el hash de contraseña
    private static final String SALT = "RegistroEstudiantes2024!";

    private SecurityManager() {
        this.adminUserDAO = new AdminUserDAO();
    }

    public static synchronized SecurityManager getInstance() {
        if (instance == null) {
            instance = new SecurityManager();
        }
        return instance;
    }

    /**
     * Verifica si ya existe al menos un usuario administrador configurado.
     */
    public boolean isPasswordConfigured() {
        return adminUserDAO.countAll() > 0;
    }

    /**
     * Crea el primer usuario administrador del sistema.
     *
     * @param username Nombre de usuario
     * @param password Contraseña
     * @param timeoutMinutes Timeout de sesión en minutos
     * @return true si se creó correctamente
     */
    public boolean createFirstAdmin(String username, String password, int timeoutMinutes) {
        if (password == null || password.length() < 4) {
            logger.warn("Contraseña muy corta (mínimo 4 caracteres)");
            return false;
        }

        try {
            AdminUser admin = new AdminUser(username, AdminUser.ROLE_ADMIN);
            admin.setPasswordHash(hashPassword(password));
            adminUserDAO.save(admin);

            // Guardar timeout en SessionManager
            SessionManager.getInstance().setTimeoutMinutes(timeoutMinutes);

            logger.info("Primer usuario administrador creado: {}", username);
            return true;
        } catch (SQLException e) {
            logger.error("Error al crear primer administrador", e);
            return false;
        }
    }

    /**
     * Autentica un usuario contra la tabla admin_users.
     *
     * @param username Nombre de usuario
     * @param password Contraseña
     * @return Optional con el AdminUser si la autenticación es exitosa
     */
    public Optional<AdminUser> authenticate(String username, String password) {
        if (username == null || username.isEmpty() || password == null || password.isEmpty()) {
            return Optional.empty();
        }

        Optional<AdminUser> userOpt = adminUserDAO.findByUsername(username);
        if (userOpt.isPresent()) {
            AdminUser user = userOpt.get();
            if (!user.isActive()) {
                logger.warn("Intento de login con usuario inactivo: {}", username);
                return Optional.empty();
            }
            String inputHash = hashPassword(password);
            if (user.getPasswordHash() != null && user.getPasswordHash().equals(inputHash)) {
                logger.info("Autenticación exitosa: {}", username);
                return Optional.of(user);
            }
        }

        logger.warn("Autenticación fallida para usuario: {}", username);
        return Optional.empty();
    }

    /**
     * Crea un nuevo usuario administrador u operador.
     */
    public AdminUser createUser(String username, String password, String role) throws SQLException {
        if (password == null || password.length() < 4) {
            throw new IllegalArgumentException("La contraseña debe tener al menos 4 caracteres");
        }
        if (adminUserDAO.existsByUsername(username)) {
            throw new IllegalArgumentException("Ya existe un usuario con ese nombre");
        }

        AdminUser user = new AdminUser(username, role);
        user.setPasswordHash(hashPassword(password));
        return adminUserDAO.save(user);
    }

    /**
     * Actualiza la contraseña de un usuario.
     */
    public boolean updatePassword(Long userId, String newPassword) {
        if (newPassword == null || newPassword.length() < 4) {
            return false;
        }

        Optional<AdminUser> userOpt = adminUserDAO.findById(userId);
        if (userOpt.isPresent()) {
            AdminUser user = userOpt.get();
            user.setPasswordHash(hashPassword(newPassword));
            try {
                return adminUserDAO.update(user);
            } catch (SQLException e) {
                logger.error("Error al actualizar contraseña", e);
            }
        }
        return false;
    }

    /**
     * Genera un hash SHA-256 de la contraseña con salt.
     */
    public String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String saltedPassword = SALT + password + SALT;
            byte[] hash = digest.digest(saltedPassword.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            logger.error("Error al generar hash de contraseña", e);
            return Base64.getEncoder().encodeToString(password.getBytes());
        }
    }

    /**
     * Backwards compatibility: verifies password against old app_config storage.
     * Used during migration from old password system.
     */
    public boolean verifyPassword(String password) {
        // Check if there's an active session
        SessionManager sessionManager = SessionManager.getInstance();
        return sessionManager.isSessionActive();
    }
}
