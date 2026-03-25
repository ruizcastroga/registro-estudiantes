package com.tuempresa.registro.dao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class ActivityLogDAO {
    private static final Logger logger = LoggerFactory.getLogger(ActivityLogDAO.class);
    private static final DateTimeFormatter SQLITE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final DatabaseConnection dbConnection;

    public ActivityLogDAO() {
        this.dbConnection = DatabaseConnection.getInstance();
    }

    // Log an activity (entry/exit)
    public void logActivity(String personType, Long personId, String personName,
                           String badgeCode, String logType, String scannedBy) {
        String sql = "INSERT INTO activity_logs (person_type, person_id, person_name, badge_code, log_type, scanned_by, created_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, personType);
            stmt.setLong(2, personId);
            stmt.setString(3, personName);
            stmt.setString(4, badgeCode);
            stmt.setString(5, logType);
            stmt.setString(6, scannedBy);
            stmt.setString(7, LocalDateTime.now().format(SQLITE_FMT));
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Error al registrar actividad", e);
        }
    }

    // Count today's total activity logs
    public int countToday() {
        String sql = "SELECT COUNT(*) FROM activity_logs WHERE DATE(created_at) = DATE('now', 'localtime')";
        try (Connection conn = dbConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            logger.error("Error al contar logs de hoy", e);
        }
        return 0;
    }

    // Count today's logs by person type
    public int countTodayByType(String personType) {
        String sql = "SELECT COUNT(*) FROM activity_logs WHERE person_type = ? AND DATE(created_at) = DATE('now', 'localtime')";
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, personType);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            logger.error("Error al contar logs de hoy por tipo", e);
        }
        return 0;
    }

    // Get recent activity (last N entries across all types)
    public List<String> getRecentFormatted(int limit) {
        String sql = "SELECT person_type, person_name, badge_code, log_type, created_at " +
                     "FROM activity_logs ORDER BY created_at DESC LIMIT ?";
        List<String> entries = new ArrayList<>();
        DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("hh:mm:ss a");
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, limit);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String type = rs.getString("person_type");
                    String name = rs.getString("person_name");
                    String time = "";
                    String ca = rs.getString("created_at");
                    if (ca != null) {
                        time = LocalDateTime.parse(ca.replace(" ", "T")).format(timeFmt);
                    }
                    String tag = switch (type) {
                        case "student" -> "[EST]";
                        case "staff" -> "[PER]";
                        case "visitor" -> "[VIS]";
                        default -> "[???]";
                    };
                    entries.add(time + " " + tag + " " + (name != null ? name : ""));
                }
            }
        } catch (SQLException e) {
            logger.error("Error al obtener actividad reciente", e);
        }
        return entries;
    }
}
