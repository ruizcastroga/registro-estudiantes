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
     * Inicializa la base de datos ejecutando el schema SQL.
     * Crea todas las tablas e índices si no existen.
     *
     * @throws SQLException Si ocurre un error al ejecutar el schema
     * @throws IOException Si ocurre un error al leer el archivo de schema
     */
    public void initializeDatabase() throws SQLException, IOException {
        logger.info("Inicializando base de datos...");

        String schema = loadSchema();

        if (schema == null || schema.trim().isEmpty()) {
            logger.error("El schema SQL está vacío o no se pudo cargar");
            throw new IOException("No se pudo cargar el schema SQL");
        }

        Connection conn = getConnection();

        // Primero, eliminar comentarios línea por línea
        StringBuilder cleanSchema = new StringBuilder();
        for (String line : schema.split("\n")) {
            String trimmedLine = line.trim();
            // Ignorar líneas que son solo comentarios
            if (!trimmedLine.startsWith("--") && !trimmedLine.isEmpty()) {
                // Eliminar comentarios al final de la línea
                int commentIndex = line.indexOf("--");
                if (commentIndex > 0) {
                    cleanSchema.append(line.substring(0, commentIndex));
                } else {
                    cleanSchema.append(line);
                }
                cleanSchema.append("\n");
            }
        }

        // Dividir el schema limpio en sentencias individuales
        String[] statements = cleanSchema.toString().split(";");

        int executedCount = 0;
        try (Statement stmt = conn.createStatement()) {
            for (String sql : statements) {
                String trimmedSql = sql.trim();

                // Ignorar sentencias vacías
                if (!trimmedSql.isEmpty()) {
                    try {
                        stmt.execute(trimmedSql);
                        executedCount++;
                        logger.debug("Ejecutado: {}",
                            trimmedSql.length() > 50 ? trimmedSql.substring(0, 50) + "..." : trimmedSql);
                    } catch (SQLException e) {
                        // Algunos errores son esperados (ej: tabla ya existe con INSERT OR IGNORE)
                        logger.debug("Advertencia al ejecutar SQL: {}", e.getMessage());
                    }
                }
            }
        }

        logger.info("Base de datos inicializada correctamente. {} sentencias ejecutadas.", executedCount);
    }

    /**
     * Carga el contenido del archivo de schema SQL desde los recursos.
     *
     * @return Contenido del archivo de schema
     * @throws IOException Si ocurre un error al leer el archivo
     */
    private String loadSchema() throws IOException {
        try (InputStream inputStream = getClass().getResourceAsStream(SCHEMA_PATH)) {
            if (inputStream == null) {
                logger.error("No se encontró el archivo de schema: {}", SCHEMA_PATH);
                throw new IOException("Archivo de schema no encontrado: " + SCHEMA_PATH);
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
