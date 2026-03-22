package com.tuempresa.registro.dao;

import com.tuempresa.registro.models.VisitorBadge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object para carnés de visitantes.
 */
public class VisitorBadgeDAO {

    private static final Logger logger = LoggerFactory.getLogger(VisitorBadgeDAO.class);

    private final DatabaseConnection dbConnection;

    public VisitorBadgeDAO() {
        this.dbConnection = DatabaseConnection.getInstance();
    }

    public Optional<VisitorBadge> findById(Long id) {
        String sql = "SELECT * FROM visitor_badges WHERE id = ?";
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return Optional.of(map(rs));
            }
        } catch (SQLException e) {
            logger.error("Error al buscar carné por ID: {}", id, e);
        }
        return Optional.empty();
    }

    public Optional<VisitorBadge> findByCode(String code) {
        String sql = "SELECT * FROM visitor_badges WHERE code = ?";
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, code);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return Optional.of(map(rs));
            }
        } catch (SQLException e) {
            logger.error("Error al buscar carné por código: {}", code, e);
        }
        return Optional.empty();
    }

    public List<VisitorBadge> findAll() {
        String sql = "SELECT * FROM visitor_badges ORDER BY code";
        List<VisitorBadge> list = new ArrayList<>();
        try (Connection conn = dbConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) list.add(map(rs));
        } catch (SQLException e) {
            logger.error("Error al obtener todos los carnés", e);
        }
        return list;
    }

    public List<VisitorBadge> findAvailable() {
        String sql = "SELECT * FROM visitor_badges WHERE status = 'available' ORDER BY code";
        List<VisitorBadge> list = new ArrayList<>();
        try (Connection conn = dbConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) list.add(map(rs));
        } catch (SQLException e) {
            logger.error("Error al obtener carnés disponibles", e);
        }
        return list;
    }

    public int countByStatus(String status) {
        String sql = "SELECT COUNT(*) FROM visitor_badges WHERE status = ?";
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, status);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            logger.error("Error al contar carnés por estado: {}", status, e);
        }
        return 0;
    }

    public VisitorBadge save(VisitorBadge badge) throws SQLException {
        String sql = "INSERT INTO visitor_badges (code, status) VALUES (?, ?)";
        Connection conn = dbConnection.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, badge.getCode());
            stmt.setString(2, badge.getStatus());
            stmt.executeUpdate();
            try (Statement idStmt = conn.createStatement();
                 ResultSet rs = idStmt.executeQuery("SELECT last_insert_rowid()")) {
                if (rs.next()) {
                    badge.setId(rs.getLong(1));
                    badge.setCreatedAt(LocalDateTime.now());
                }
            }
        }
        logger.info("Carné guardado: {}", badge.getCode());
        return badge;
    }

    public boolean updateStatus(Long id, String newStatus) {
        String sql = "UPDATE visitor_badges SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?";
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, newStatus);
            stmt.setLong(2, id);
            boolean updated = stmt.executeUpdate() > 0;
            if (updated) logger.info("Carné {} → {}", id, newStatus);
            return updated;
        } catch (SQLException e) {
            logger.error("Error al actualizar estado del carné: {}", id, e);
        }
        return false;
    }

    public boolean delete(Long id) throws SQLException {
        String sql = "DELETE FROM visitor_badges WHERE id = ?";
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, id);
            boolean deleted = stmt.executeUpdate() > 0;
            if (deleted) logger.info("Carné eliminado, ID: {}", id);
            return deleted;
        }
    }

    public boolean existsByCode(String code) {
        String sql = "SELECT COUNT(*) FROM visitor_badges WHERE code = ?";
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, code);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            logger.error("Error al verificar código de carné: {}", code, e);
        }
        return false;
    }

    private VisitorBadge map(ResultSet rs) throws SQLException {
        VisitorBadge b = new VisitorBadge();
        b.setId(rs.getLong("id"));
        b.setCode(rs.getString("code"));
        b.setStatus(rs.getString("status"));
        String ca = rs.getString("created_at");
        if (ca != null) b.setCreatedAt(LocalDateTime.parse(ca.replace(" ", "T")));
        String ua = rs.getString("updated_at");
        if (ua != null) b.setUpdatedAt(LocalDateTime.parse(ua.replace(" ", "T")));
        return b;
    }
}
