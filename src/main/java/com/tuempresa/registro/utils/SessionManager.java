package com.tuempresa.registro.utils;

import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.animation.Timeline;
import javafx.animation.KeyFrame;
import javafx.util.Duration;
import javafx.application.Platform;

import com.tuempresa.registro.models.AdminUser;
import com.tuempresa.registro.dao.DatabaseConnection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class SessionManager {

    private static final Logger logger = LoggerFactory.getLogger(SessionManager.class);

    private static SessionManager instance;

    // Observable properties for UI binding
    private final SimpleBooleanProperty sessionActive = new SimpleBooleanProperty(false);
    private final SimpleStringProperty currentUsername = new SimpleStringProperty("");
    private final SimpleStringProperty currentRole = new SimpleStringProperty("");
    private final SimpleIntegerProperty remainingSeconds = new SimpleIntegerProperty(0);
    private final SimpleStringProperty remainingTimeFormatted = new SimpleStringProperty("00:00");

    private AdminUser currentUser;
    private int timeoutMinutes = 15;
    private Timeline countdownTimeline;

    private SessionManager() {
        loadTimeoutFromDb();
    }

    public static synchronized SessionManager getInstance() {
        if (instance == null) {
            instance = new SessionManager();
        }
        return instance;
    }

    /**
     * Start a session with the given user and timeout.
     */
    public void startSession(AdminUser user, int timeoutMinutes) {
        this.currentUser = user;
        this.timeoutMinutes = timeoutMinutes;
        sessionActive.set(true);
        currentUsername.set(user.getUsername());
        currentRole.set(user.getRole());
        remainingSeconds.set(timeoutMinutes * 60);
        startCountdown();
        logger.info("Sesión iniciada: {} ({}), timeout: {} min",
                user.getUsername(), user.getRole(), timeoutMinutes);
    }

    /**
     * End the current session.
     */
    public void endSession() {
        stopCountdown();
        currentUser = null;
        sessionActive.set(false);
        currentUsername.set("");
        currentRole.set("");
        remainingSeconds.set(0);
        remainingTimeFormatted.set("00:00");
        logger.info("Sesión finalizada");
    }

    /**
     * Check if current session allows admin operations.
     */
    public boolean isAdminSessionActive() {
        return sessionActive.get() && currentUser != null && currentUser.isAdmin();
    }

    /**
     * Check if ANY session is active (admin or operator).
     */
    public boolean isSessionActive() {
        return sessionActive.get() && currentUser != null;
    }

    /**
     * Check if current user can modify data (only Administrador can).
     */
    public boolean canModifyData() {
        return isAdminSessionActive();
    }

    // Property getters for UI binding

    public SimpleBooleanProperty sessionActiveProperty() {
        return sessionActive;
    }

    public SimpleStringProperty currentUsernameProperty() {
        return currentUsername;
    }

    public SimpleStringProperty currentRoleProperty() {
        return currentRole;
    }

    public SimpleIntegerProperty remainingSecondsProperty() {
        return remainingSeconds;
    }

    public SimpleStringProperty remainingTimeFormattedProperty() {
        return remainingTimeFormatted;
    }

    public AdminUser getCurrentUser() {
        return currentUser;
    }

    public String getActiveUsername() {
        return currentUser != null ? currentUser.getUsername() : "Sistema";
    }

    public int getTimeoutMinutes() {
        return timeoutMinutes;
    }

    public void setTimeoutMinutes(int minutes) {
        this.timeoutMinutes = minutes;
        saveTimeoutToDb(minutes);
    }

    /**
     * Load session timeout from app_config table.
     */
    private void loadTimeoutFromDb() {
        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT value FROM app_config WHERE key = 'session_timeout_minutes'")) {
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        timeoutMinutes = Integer.parseInt(rs.getString("value"));
                        logger.info("Timeout de sesión cargado: {} minutos", timeoutMinutes);
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("No se pudo cargar timeout de sesión, usando default: {} min", timeoutMinutes);
        }
    }

    /**
     * Save session timeout to app_config table.
     */
    private void saveTimeoutToDb(int minutes) {
        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT OR REPLACE INTO app_config (key, value, description, updated_at) VALUES (?, ?, ?, CURRENT_TIMESTAMP)")) {
                stmt.setString(1, "session_timeout_minutes");
                stmt.setString(2, String.valueOf(minutes));
                stmt.setString(3, "Tiempo de vigencia de la sesión de administrador en minutos");
                stmt.executeUpdate();
            }
            logger.info("Timeout de sesión guardado: {} minutos", minutes);
        } catch (SQLException e) {
            logger.error("Error al guardar timeout de sesión", e);
        }
    }

    /**
     * Start the countdown timer that decrements every second.
     */
    private void startCountdown() {
        stopCountdown();
        countdownTimeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            int remaining = remainingSeconds.get() - 1;
            if (remaining <= 0) {
                endSession();
            } else {
                remainingSeconds.set(remaining);
                int mins = remaining / 60;
                int secs = remaining % 60;
                remainingTimeFormatted.set(String.format("%02d:%02d", mins, secs));
            }
        }));
        countdownTimeline.setCycleCount(Timeline.INDEFINITE);
        countdownTimeline.play();
        // Set initial formatted time
        int total = remainingSeconds.get();
        remainingTimeFormatted.set(String.format("%02d:%02d", total / 60, total % 60));
    }

    /**
     * Stop the countdown timer.
     */
    private void stopCountdown() {
        if (countdownTimeline != null) {
            countdownTimeline.stop();
            countdownTimeline = null;
        }
    }
}
