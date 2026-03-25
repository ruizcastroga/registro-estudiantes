package com.tuempresa.registro.dao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.stream.Collectors;

/**
 * Clase singleton para gestionar la conexión con la base de datos SQLite.
 * Proporciona métodos para obtener conexiones y para inicializar el schema.
 */
public class DatabaseConnection {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseConnection.class);

    // Nombre del archivo de base de datos
    private static final String DB_NAME = "registro_estudiantes.db";

    // Directorio de datos de la aplicación (en home del usuario)
    private static final String APP_DATA_DIR = System.getProperty("user.home") + File.separator + ".registro-estudiantes";

    // Ruta completa al archivo de base de datos
    private static final String DB_PATH = APP_DATA_DIR + File.separator + DB_NAME;

    // URL de conexión JDBC para SQLite
    private static final String DB_URL = "jdbc:sqlite:" + DB_PATH;

    // Ruta al archivo de schema SQL en resources
    private static final String SCHEMA_PATH = "/database/schema.sql";

    // Instancia singleton
    private static DatabaseConnection instance;

    // Conexión actual
    private Connection connection;

    /**
     * Constructor privado para implementar el patrón singleton.
     */
    private DatabaseConnection() {
        // Constructor privado
    }

    /**
     * Obtiene la instancia única de DatabaseConnection.
     *
     * @return Instancia de DatabaseConnection
     */
    public static synchronized DatabaseConnection getInstance() {
        if (instance == null) {
            instance = new DatabaseConnection();
        }
        return instance;
    }

    /**
     * Obtiene una conexión a la base de datos SQLite.
     * Si no existe una conexión activa, crea una nueva.
     *
     * @return Conexión activa a la base de datos
     * @throws SQLException Si ocurre un error al conectar
     */
    public Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            try {
                // Crear el directorio de datos si no existe
                File dataDir = new File(APP_DATA_DIR);
                if (!dataDir.exists()) {
                    boolean created = dataDir.mkdirs();
                    if (created) {
                        logger.info("Directorio de datos creado: {}", APP_DATA_DIR);
                    }
                }

                // Cargar el driver de SQLite (opcional en versiones modernas)
                Class.forName("org.sqlite.JDBC");

                // Crear la conexión
                connection = DriverManager.getConnection(DB_URL);

                // Habilitar claves foráneas en SQLite
                try (Statement stmt = connection.createStatement()) {
                    stmt.execute("PRAGMA foreign_keys = ON");
                }

                logger.info("Conexión a la base de datos establecida: {}", DB_PATH);

            } catch (ClassNotFoundException e) {
                logger.error("Driver SQLite no encontrado", e);
                throw new SQLException("Driver SQLite no encontrado", e);
            }
        }
        return connection;
    }

    /**
     * Inicializa la base de datos ejecutando el schema SQL y migraciones pendientes.
     *
     * @throws SQLException Si ocurre un error al ejecutar el schema
     * @throws IOException Si ocurre un error al leer el archivo de schema
     */
    public void initializeDatabase() throws SQLException, IOException {
        logger.info("Inicializando base de datos...");

        // Ejecutar schema base
        String schema = loadSchemaFile(SCHEMA_PATH);
        executeSqlScript(schema);

        // Ejecutar migraciones pendientes
        runMigrations();

        logger.info("Base de datos inicializada correctamente");
    }

    /**
     * Ejecuta migraciones pendientes según la versión actual de la BD.
     */
    private void runMigrations() throws IOException {
        int currentVersion = getDbVersion();
        logger.info("Versión actual de BD: {}", currentVersion);

        // Migración v2: staff, admin_users, activity_logs, audit trail
        if (currentVersion < 2) {
            logger.info("Ejecutando migración v2...");
            String migration = loadSchemaFile("/database/migration_v2.sql");
            executeSqlScript(migration);
            logger.info("Migración v2 completada");
        }
    }

    /**
     * Obtiene la versión actual del esquema de la base de datos.
     */
    private int getDbVersion() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT value FROM app_config WHERE key = 'db_version'")) {
            if (rs.next()) {
                return Integer.parseInt(rs.getString("value"));
            }
        } catch (Exception e) {
            logger.debug("No se encontró versión de BD, asumiendo v1");
        }
        return 1;
    }

    /**
     * Ejecuta un script SQL completo (múltiples sentencias separadas por ;).
     */
    private void executeSqlScript(String script) {
        if (script == null || script.trim().isEmpty()) return;

        // Limpiar comentarios
        StringBuilder cleanSql = new StringBuilder();
        for (String line : script.split("\n")) {
            String trimmedLine = line.trim();
            if (!trimmedLine.startsWith("--") && !trimmedLine.isEmpty()) {
                int commentIndex = line.indexOf("--");
                if (commentIndex > 0) {
                    cleanSql.append(line, 0, commentIndex);
                } else {
                    cleanSql.append(line);
                }
                cleanSql.append("\n");
            }
        }

        String[] statements = cleanSql.toString().split(";");
        int executedCount = 0;

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            for (String sql : statements) {
                String trimmedSql = sql.trim();
                if (!trimmedSql.isEmpty()) {
                    try {
                        stmt.execute(trimmedSql);
                        executedCount++;
                    } catch (SQLException e) {
                        logger.debug("Advertencia al ejecutar SQL: {}", e.getMessage());
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Error al ejecutar script SQL", e);
        }

        logger.debug("{} sentencias ejecutadas", executedCount);
    }

    /**
     * Carga el contenido de un archivo SQL desde los recursos.
     *
     * @param path Ruta al archivo SQL en resources
     * @return Contenido del archivo
     * @throws IOException Si ocurre un error al leer el archivo
     */
    private String loadSchemaFile(String path) throws IOException {
        try (InputStream inputStream = getClass().getResourceAsStream(path)) {
            if (inputStream == null) {
                logger.error("No se encontró el archivo: {}", path);
                throw new IOException("Archivo no encontrado: " + path);
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        }
    }

    /**
     * Cierra la conexión a la base de datos.
     */
    public void closeConnection() {
        if (connection != null) {
            try {
                if (!connection.isClosed()) {
                    connection.close();
                    logger.info("Conexión a la base de datos cerrada");
                }
            } catch (SQLException e) {
                logger.error("Error al cerrar la conexión", e);
            }
            connection = null;
        }
    }

    /**
     * Verifica si la conexión está activa.
     *
     * @return true si la conexión está activa, false en caso contrario
     */
    public boolean isConnected() {
        try {
            return connection != null && !connection.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }

    /**
     * Ejecuta una transacción con rollback automático en caso de error.
     *
     * @param transaction La transacción a ejecutar
     * @throws SQLException Si ocurre un error durante la transacción
     */
    public void executeTransaction(TransactionCallback transaction) throws SQLException {
        Connection conn = getConnection();
        boolean autoCommit = conn.getAutoCommit();

        try {
            conn.setAutoCommit(false);
            transaction.execute(conn);
            conn.commit();
            logger.debug("Transacción completada exitosamente");
        } catch (SQLException e) {
            logger.error("Error en transacción, ejecutando rollback", e);
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(autoCommit);
        }
    }

    /**
     * Interface funcional para ejecutar transacciones.
     */
    @FunctionalInterface
    public interface TransactionCallback {
        void execute(Connection connection) throws SQLException;
    }
}
