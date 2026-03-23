package com.tuempresa.registro.dao;

import com.tuempresa.registro.models.VisitorLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object para registros de visitas.
 */
public class VisitorLogDAO {

    private static final Logger logger = LoggerFactory.getLogger(VisitorLogDAO.class);
    private static final DateTimeFormatter SQLITE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final DatabaseConnection dbConnection;

    public VisitorLogDAO() {
        this.dbConnection = DatabaseConnection.getInstance();
    }

    /**
     * Busca la visita activa (sin salida) de un carné específico.
     */
    public Optional<VisitorLog> findActiveByBadgeId(Long badgeId) {
        String sql = "SELECT vl.*, vb.code AS badge_code " +
                     "FROM visitor_logs vl " +
                     "JOIN visitor_badges vb ON vl.badge_id = vb.id " +
                     "WHERE vl.badge_id = ? AND vl.exit_time IS NULL " +
                     "ORDER BY vl.entry_time DESC LIMIT 1";
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, badgeId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return Optional.of(map(rs));
            }
        } catch (SQLException e) {
            logger.error("Error al buscar visita activa para carné: {}", badgeId, e);
        }
        return Optional.empty();
    }

    /**
     * Lista todos los visitantes que están actualmente dentro (sin salida registrada).
     */
    public List<VisitorLog> findCurrentlyInside() {
        String sql = "SELECT vl.*, vb.code AS badge_code " +
                     "FROM visitor_logs vl " +
                     "JOIN visitor_badges vb ON vl.badge_id = vb.id " +
                     "WHERE vl.exit_time IS NULL " +
                     "ORDER BY vl.entry_time DESC";
        List<VisitorLog> list = new ArrayList<>();
        try (Connection conn = dbConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) list.add(map(rs));
        } catch (SQLException e) {
            logger.error("Error al obtener visitantes dentro", e);
        }
        return list;
    }

    /**
     * Lista el historial de visitas (con y sin salida) ordenado por entrada DESC.
     */
    public List<VisitorLog> findAll() {
        String sql = "SELECT vl.*, vb.code AS badge_code " +
                     "FROM visitor_logs vl " +
                     "JOIN visitor_badges vb ON vl.badge_id = vb.id " +
                     "ORDER BY vl.entry_time DESC";
        List<VisitorLog> list = new ArrayList<>();
        try (Connection conn = dbConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) list.add(map(rs));
        } catch (SQLException e) {
            logger.error("Error al obtener historial de visitas", e);
        }
        return list;
    }

    /**
     * Registra una nueva entrada de visitante.
     */
    public VisitorLog save(VisitorLog log) throws SQLException {
        String sql = "INSERT INTO visitor_logs (badge_id, id_number, first_name, last_name, justification, entry_time) " +
                     "VALUES (?, ?, ?, ?, ?, ?)";
        LocalDateTime now = LocalDateTime.now();
        Connection conn = dbConnection.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, log.getBadgeId());
            stmt.setString(2, log.getIdNumber());
            stmt.setString(3, log.getFirstName());
            stmt.setString(4, log.getLastName());
            stmt.setString(5, log.getJustification());
            stmt.setString(6, now.format(SQLITE_FMT));
            stmt.executeUpdate();
            try (Statement idStmt = conn.createStatement();
                 ResultSet rs = idStmt.executeQuery("SELECT last_insert_rowid()")) {
                if (rs.next()) {
                    log.setId(rs.getLong(1));
                    log.setEntryTime(now);
                }
            }
        }
        logger.info("Entrada registrada: cédula={}, carné={}", log.getIdNumber(), log.getBadgeId());
        return log;
    }

    /**
     * Registra la salida de un visitante (por ID del log).
     */
    public boolean registerExit(Long logId) {
        String sql = "UPDATE visitor_logs SET exit_time = ? WHERE id = ?";
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, LocalDateTime.now().format(SQLITE_FMT));
            stmt.setLong(2, logId);
            boolean updated = stmt.executeUpdate() > 0;
            if (updated) logger.info("Salida registrada para log ID: {}", logId);
            return updated;
        } catch (SQLException e) {
            logger.error("Error al registrar salida, log ID: {}", logId, e);
        }
        return false;
    }

    /**
     * Elimina todos los registros anteriores a una fecha dada.
     */
    public int deleteBeforeDate(LocalDateTime cutoff) {
        String sql = "DELETE FROM visitor_logs WHERE datetime(entry_time) < datetime(?)";
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, cutoff.format(SQLITE_FMT));
            int deleted = stmt.executeUpdate();
            logger.info("Eliminados {} registros anteriores a {}", deleted, cutoff);
            return deleted;
        } catch (SQLException e) {
            logger.error("Error al limpiar registros antiguos", e);
        }
        return 0;
    }

    /**
     * Cuenta el total de visitas de hoy.
     */
    public int countToday() {
        String sql = "SELECT COUNT(*) FROM visitor_logs WHERE DATE(entry_time) = DATE('now', 'localtime')";
        try (Connection conn = dbConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            logger.error("Error al contar visitas de hoy", e);
        }
        return 0;
    }

    private VisitorLog map(ResultSet rs) throws SQLException {
        VisitorLog log = new VisitorLog();
        log.setId(rs.getLong("id"));
        log.setBadgeId(rs.getLong("badge_id"));
        log.setBadgeCode(rs.getString("badge_code"));
        log.setIdNumber(rs.getString("id_number"));
        log.setFirstName(rs.getString("first_name"));
        log.setLastName(rs.getString("last_name"));
        log.setJustification(rs.getString("justification"));

        String et = rs.getString("entry_time");
        if (et != null) log.setEntryTime(LocalDateTime.parse(et.replace(" ", "T")));

        String xt = rs.getString("exit_time");
        if (xt != null) log.setExitTime(LocalDateTime.parse(xt.replace(" ", "T")));

        return log;
    }
}
