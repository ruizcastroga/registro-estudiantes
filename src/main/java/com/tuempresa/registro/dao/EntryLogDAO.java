package com.tuempresa.registro.dao;

import com.tuempresa.registro.models.EntryLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object para la entidad EntryLog.
 * Proporciona operaciones para registrar y consultar entradas/salidas.
 */
public class EntryLogDAO {

    private static final Logger logger = LoggerFactory.getLogger(EntryLogDAO.class);

    private final DatabaseConnection dbConnection;

    private static final DateTimeFormatter SQL_DATETIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Constructor que inicializa la conexión a la base de datos.
     */
    public EntryLogDAO() {
        this.dbConnection = DatabaseConnection.getInstance();
    }

    /**
     * Busca un registro por su ID.
     *
     * @param id ID del registro
     * @return Optional con el registro si existe
     */
    public Optional<EntryLog> findById(Long id) {
        String sql = "SELECT * FROM entry_logs WHERE id = ?";

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, id);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToEntryLog(rs));
                }
            }

        } catch (SQLException e) {
            logger.error("Error al buscar registro por ID: {}", id, e);
        }

        return Optional.empty();
    }

    /**
     * Obtiene todos los registros de un estudiante.
     *
     * @param studentId ID del estudiante
     * @return Lista de registros del estudiante
     */
    public List<EntryLog> findByStudentId(Long studentId) {
        String sql = "SELECT * FROM entry_logs WHERE student_id = ? ORDER BY entry_time DESC";
        List<EntryLog> logs = new ArrayList<>();

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, studentId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    logs.add(mapResultSetToEntryLog(rs));
                }
            }

        } catch (SQLException e) {
            logger.error("Error al buscar registros del estudiante: {}", studentId, e);
        }

        return logs;
    }

    /**
     * Obtiene los registros de un día específico.
     *
     * @param date Fecha a consultar
     * @return Lista de registros del día
     */
    public List<EntryLog> findByDate(LocalDate date) {
        String sql = "SELECT * FROM entry_logs " +
                "WHERE DATE(entry_time) = ? " +
                "ORDER BY entry_time DESC";
        List<EntryLog> logs = new ArrayList<>();

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, date.toString());

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    logs.add(mapResultSetToEntryLog(rs));
                }
            }

        } catch (SQLException e) {
            logger.error("Error al buscar registros por fecha: {}", date, e);
        }

        return logs;
    }

    /**
     * Obtiene los registros de hoy.
     *
     * @return Lista de registros del día actual
     */
    public List<EntryLog> findToday() {
        return findByDate(LocalDate.now());
    }

    /**
     * Obtiene los últimos N registros (para el historial).
     *
     * @param limit Número máximo de registros
     * @return Lista de los últimos registros
     */
    public List<EntryLog> findRecent(int limit) {
        String sql = "SELECT el.*, s.first_name, s.last_name " +
                "FROM entry_logs el " +
                "JOIN students s ON el.student_id = s.id " +
                "ORDER BY el.entry_time DESC LIMIT ?";
        List<EntryLog> logs = new ArrayList<>();

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, limit);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    EntryLog log = mapResultSetToEntryLog(rs);
                    // Añadir info del estudiante al log
                    logs.add(log);
                }
            }

        } catch (SQLException e) {
            logger.error("Error al buscar registros recientes", e);
        }

        return logs;
    }

    /**
     * Obtiene los registros entre dos fechas.
     *
     * @param startDate Fecha inicial
     * @param endDate   Fecha final
     * @return Lista de registros en el rango
     */
    public List<EntryLog> findByDateRange(LocalDate startDate, LocalDate endDate) {
        String sql = "SELECT * FROM entry_logs " +
                "WHERE DATE(entry_time) BETWEEN ? AND ? " +
                "ORDER BY entry_time DESC";
        List<EntryLog> logs = new ArrayList<>();

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, startDate.toString());
            stmt.setString(2, endDate.toString());

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    logs.add(mapResultSetToEntryLog(rs));
                }
            }

        } catch (SQLException e) {
            logger.error("Error al buscar registros por rango de fechas: {} - {}", startDate, endDate, e);
        }

        return logs;
    }

    /**
     * Guarda un nuevo registro de entrada/salida.
     *
     * @param entryLog Registro a guardar
     * @return El registro guardado con su ID asignado
     * @throws SQLException Si ocurre un error al guardar
     */
    public EntryLog save(EntryLog entryLog) throws SQLException {
        String sql = "INSERT INTO entry_logs (student_id, guardian_id, entry_time, " +
                "exit_time, log_type, scanned_by, notes) VALUES (?, ?, ?, ?, ?, ?, ?)";

        Connection conn = dbConnection.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, entryLog.getStudentId());

            if (entryLog.getGuardianId() != null) {
                stmt.setLong(2, entryLog.getGuardianId());
            } else {
                stmt.setNull(2, Types.INTEGER);
            }

            stmt.setString(3, entryLog.getEntryTime() != null ?
                    entryLog.getEntryTime().format(SQL_DATETIME_FORMATTER) :
                    LocalDateTime.now().format(SQL_DATETIME_FORMATTER));

            if (entryLog.getExitTime() != null) {
                stmt.setString(4, entryLog.getExitTime().format(SQL_DATETIME_FORMATTER));
            } else {
                stmt.setNull(4, Types.VARCHAR);
            }

            stmt.setString(5, entryLog.getLogType() != null ? entryLog.getLogType() : "exit");
            stmt.setString(6, entryLog.getScannedBy());
            stmt.setString(7, entryLog.getNotes());

            int affectedRows = stmt.executeUpdate();

            if (affectedRows > 0) {
                // SQLite: usar last_insert_rowid() para obtener el ID generado
                try (Statement idStmt = conn.createStatement();
                     ResultSet rs = idStmt.executeQuery("SELECT last_insert_rowid()")) {
                    if (rs.next()) {
                        entryLog.setId(rs.getLong(1));
                        logger.info("Registro de entrada/salida guardado: {}", entryLog);
                    }
                }
            }

        } catch (SQLException e) {
            logger.error("Error al guardar registro: {}", entryLog, e);
            throw e;
        }

        return entryLog;
    }

    /**
     * Actualiza la hora de salida de un registro.
     *
     * @param id       ID del registro
     * @param exitTime Hora de salida
     * @return true si se actualizó correctamente
     */
    public boolean updateExitTime(Long id, LocalDateTime exitTime) {
        String sql = "UPDATE entry_logs SET exit_time = ? WHERE id = ?";

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, exitTime.format(SQL_DATETIME_FORMATTER));
            stmt.setLong(2, id);

            int affectedRows = stmt.executeUpdate();

            if (affectedRows > 0) {
                logger.info("Hora de salida actualizada para registro: {}", id);
                return true;
            }

        } catch (SQLException e) {
            logger.error("Error al actualizar hora de salida: {}", id, e);
        }

        return false;
    }

    /**
     * Elimina un registro por su ID.
     *
     * @param id ID del registro a eliminar
     * @return true si se eliminó correctamente
     */
    public boolean delete(Long id) {
        String sql = "DELETE FROM entry_logs WHERE id = ?";

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, id);
            int affectedRows = stmt.executeUpdate();

            if (affectedRows > 0) {
                logger.info("Registro eliminado, ID: {}", id);
                return true;
            }

        } catch (SQLException e) {
            logger.error("Error al eliminar registro, ID: {}", id, e);
        }

        return false;
    }

    /**
     * Cuenta los registros del día actual.
     *
     * @return Número de registros de hoy
     */
    public int countToday() {
        String sql = "SELECT COUNT(*) FROM entry_logs WHERE DATE(entry_time) = DATE('now', 'localtime')";

        try (Connection conn = dbConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            if (rs.next()) {
                return rs.getInt(1);
            }

        } catch (SQLException e) {
            logger.error("Error al contar registros del día", e);
        }

        return 0;
    }

    /**
     * Cuenta los registros de un estudiante en un día específico.
     *
     * @param studentId ID del estudiante
     * @param date      Fecha a consultar
     * @return Número de registros
     */
    public int countByStudentAndDate(Long studentId, LocalDate date) {
        String sql = "SELECT COUNT(*) FROM entry_logs " +
                "WHERE student_id = ? AND DATE(entry_time) = ?";

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, studentId);
            stmt.setString(2, date.toString());

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }

        } catch (SQLException e) {
            logger.error("Error al contar registros del estudiante: {}", studentId, e);
        }

        return 0;
    }

    /**
     * Obtiene el último registro de un estudiante.
     *
     * @param studentId ID del estudiante
     * @return Optional con el último registro
     */
    public Optional<EntryLog> findLastByStudentId(Long studentId) {
        String sql = "SELECT * FROM entry_logs WHERE student_id = ? " +
                "ORDER BY entry_time DESC LIMIT 1";

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, studentId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToEntryLog(rs));
                }
            }

        } catch (SQLException e) {
            logger.error("Error al buscar último registro del estudiante: {}", studentId, e);
        }

        return Optional.empty();
    }

    /**
     * Mapea un ResultSet a un objeto EntryLog.
     *
     * @param rs ResultSet con los datos
     * @return Objeto EntryLog mapeado
     * @throws SQLException Si ocurre un error al leer los datos
     */
    private EntryLog mapResultSetToEntryLog(ResultSet rs) throws SQLException {
        EntryLog entryLog = new EntryLog();
        entryLog.setId(rs.getLong("id"));
        entryLog.setStudentId(rs.getLong("student_id"));

        long guardianId = rs.getLong("guardian_id");
        if (!rs.wasNull()) {
            entryLog.setGuardianId(guardianId);
        }

        // Convertir entry_time
        String entryTime = rs.getString("entry_time");
        if (entryTime != null) {
            entryLog.setEntryTime(parseDateTime(entryTime));
        }

        // Convertir exit_time
        String exitTime = rs.getString("exit_time");
        if (exitTime != null) {
            entryLog.setExitTime(parseDateTime(exitTime));
        }

        entryLog.setLogType(rs.getString("log_type"));
        entryLog.setScannedBy(rs.getString("scanned_by"));
        entryLog.setNotes(rs.getString("notes"));

        return entryLog;
    }

    /**
     * Parsea una fecha/hora desde String a LocalDateTime.
     *
     * @param dateTimeStr String con la fecha/hora
     * @return LocalDateTime parseado
     */
    private LocalDateTime parseDateTime(String dateTimeStr) {
        try {
            // Intentar parsear con formato estándar SQL
            return LocalDateTime.parse(dateTimeStr.replace(" ", "T"));
        } catch (Exception e) {
            try {
                // Intentar con formato SQL datetime
                return LocalDateTime.parse(dateTimeStr, SQL_DATETIME_FORMATTER);
            } catch (Exception ex) {
                logger.warn("No se pudo parsear fecha: {}", dateTimeStr);
                return null;
            }
        }
    }
}
