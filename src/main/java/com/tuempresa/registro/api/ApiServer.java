package com.tuempresa.registro.api;

import com.sun.net.httpserver.HttpServer;
import com.tuempresa.registro.api.handlers.*;
import com.tuempresa.registro.dao.DatabaseConnection;
import com.tuempresa.registro.services.StaffService;
import com.tuempresa.registro.services.StudentService;
import com.tuempresa.registro.services.VisitorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.Executors;

/**
 * Embedded REST API server (JDK HttpServer on port 8080).
 * Starts alongside the JavaFX application and shares the same
 * database connection and service layer.
 *
 * All endpoints require the X-API-Key header.
 *
 * Endpoints:
 *   GET  /api/health
 *   GET  /api/students/export
 *   POST /api/students/import
 *   GET  /api/staff/export
 *   POST /api/staff/import
 *   GET  /api/visitors/badges/export
 *   POST /api/visitors/badges/import
 *   GET  /api/visitors/logs/export?from=YYYY-MM-DD&to=YYYY-MM-DD
 *   GET  /api/entry-logs/export?from=YYYY-MM-DD&to=YYYY-MM-DD
 */
public class ApiServer {

    private static final Logger logger = LoggerFactory.getLogger(ApiServer.class);
    public static final int PORT = 8080;

    /** Singleton — lets SettingsController read/regenerate the key at runtime. */
    private static ApiServer instance;

    private HttpServer server;
    private String apiKey;

    public ApiServer() {
        instance = this;
        this.apiKey = getOrCreateApiKey();
    }

    public static ApiServer getInstance() {
        return instance;
    }

    // -----------------------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------------------

    public void start() throws IOException {
        StudentService studentService = new StudentService();
        StaffService   staffService   = new StaffService();
        VisitorService visitorService = new VisitorService();

        server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/api/health",           new HealthHandler(apiKey));
        server.createContext("/api/students/",        new StudentsHandler(studentService, apiKey));
        server.createContext("/api/staff/",           new StaffHandler(staffService, apiKey));
        server.createContext("/api/visitors/",        new VisitorsHandler(visitorService, apiKey));
        server.createContext("/api/entry-logs/",      new EntryLogsHandler(apiKey));
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.start();

        logger.info("API server started on http://localhost:{}", PORT);
        logger.info("API Key: {}", apiKey);
    }

    public void stop() {
        if (server != null) {
            server.stop(1);
            logger.info("API server stopped");
        }
    }

    // -----------------------------------------------------------------------
    // API key management
    // -----------------------------------------------------------------------

    public String getApiKey() {
        return apiKey;
    }

    /**
     * Generate a new random key, persist it, and re-register all handler
     * contexts so the new key is enforced immediately without a restart.
     */
    public String regenerateApiKey() throws IOException {
        String newKey = UUID.randomUUID().toString();
        persistApiKey(newKey);
        this.apiKey = newKey;

        // Re-wire all contexts with the new key
        if (server != null) {
            server.removeContext("/api/health");
            server.removeContext("/api/students/");
            server.removeContext("/api/staff/");
            server.removeContext("/api/visitors/");
            server.removeContext("/api/entry-logs/");

            StudentService studentService = new StudentService();
            StaffService   staffService   = new StaffService();
            VisitorService visitorService = new VisitorService();

            server.createContext("/api/health",       new HealthHandler(newKey));
            server.createContext("/api/students/",    new StudentsHandler(studentService, newKey));
            server.createContext("/api/staff/",       new StaffHandler(staffService, newKey));
            server.createContext("/api/visitors/",    new VisitorsHandler(visitorService, newKey));
            server.createContext("/api/entry-logs/",  new EntryLogsHandler(newKey));
        }

        logger.info("API key regenerated");
        return newKey;
    }

    // -----------------------------------------------------------------------
    // Persistence helpers
    // -----------------------------------------------------------------------

    private String getOrCreateApiKey() {
        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();

            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT value FROM app_config WHERE key = 'api_key'")) {
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    String existing = rs.getString("value");
                    if (existing != null && !existing.isBlank()) return existing;
                }
            }

            // First run — generate and store a new key
            String newKey = UUID.randomUUID().toString();
            persistApiKey(newKey);
            return newKey;

        } catch (SQLException e) {
            logger.error("Could not read/write API key from DB, using session-only key", e);
            return UUID.randomUUID().toString();
        }
    }

    private void persistApiKey(String key) throws SQLException {
        try {
            Connection conn = DatabaseConnection.getInstance().getConnection();
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT OR REPLACE INTO app_config (key, value, description, updated_at) " +
                    "VALUES ('api_key', ?, 'Clave de autenticación para la API REST', CURRENT_TIMESTAMP)")) {
                ps.setString(1, key);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw e;
        }
    }
}
