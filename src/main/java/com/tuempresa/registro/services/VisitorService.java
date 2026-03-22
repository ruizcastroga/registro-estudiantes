package com.tuempresa.registro.services;

import com.tuempresa.registro.dao.VisitorBadgeDAO;
import com.tuempresa.registro.dao.VisitorLogDAO;
import com.tuempresa.registro.models.VisitorBadge;
import com.tuempresa.registro.models.VisitorLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Lógica de negocio del módulo de visitantes.
 *
 * Flujo principal:
 *   - Escanear carné disponible → pedir cédula → registrar entrada (carné queda "in_use")
 *   - Escanear carné en uso → registrar salida (carné queda "available")
 *   - Marcar carné perdido requiere contraseña admin
 */
public class VisitorService {

    private static final Logger logger = LoggerFactory.getLogger(VisitorService.class);

    public enum ScanStatus {
        /** Carné disponible: pedir cédula para registrar entrada */
        BADGE_AVAILABLE,
        /** Carné en uso: se registró la salida exitosamente */
        EXIT_REGISTERED,
        /** Carné marcado como perdido */
        BADGE_LOST,
        /** Código no existe en el sistema */
        BADGE_NOT_FOUND,
        /** Error inesperado */
        ERROR
    }

    public static class ScanResult {
        private final ScanStatus status;
        private final VisitorBadge badge;
        private final VisitorLog log;
        private final String message;

        public ScanResult(ScanStatus status, VisitorBadge badge, VisitorLog log, String message) {
            this.status = status;
            this.badge = badge;
            this.log = log;
            this.message = message;
        }

        public ScanStatus getStatus() { return status; }
        public VisitorBadge getBadge() { return badge; }
        public VisitorLog getLog() { return log; }
        public String getMessage() { return message; }
    }

    private final VisitorBadgeDAO badgeDAO;
    private final VisitorLogDAO logDAO;

    public VisitorService() {
        this.badgeDAO = new VisitorBadgeDAO();
        this.logDAO = new VisitorLogDAO();
    }

    /**
     * Procesa el escaneo de un carné:
     * - Si está disponible: devuelve BADGE_AVAILABLE para que el UI pida la cédula.
     * - Si está en uso: registra la salida y devuelve EXIT_REGISTERED.
     * - Si está perdido o no existe: informa al guardia.
     */
    public ScanResult processBadgeScan(String badgeCode) {
        Optional<VisitorBadge> opt = badgeDAO.findByCode(badgeCode);

        if (opt.isEmpty()) {
            return new ScanResult(ScanStatus.BADGE_NOT_FOUND, null, null,
                    "Código de carné no registrado en el sistema: " + badgeCode);
        }

        VisitorBadge badge = opt.get();

        return switch (badge.getStatus()) {
            case VisitorBadge.STATUS_AVAILABLE ->
                new ScanResult(ScanStatus.BADGE_AVAILABLE, badge, null,
                        "Carné disponible. Ingrese la cédula del visitante.");

            case VisitorBadge.STATUS_IN_USE -> {
                Optional<VisitorLog> activeLog = logDAO.findActiveByBadgeId(badge.getId());
                if (activeLog.isPresent()) {
                    VisitorLog log = activeLog.get();
                    logDAO.registerExit(log.getId());
                    log.setExitTime(LocalDateTime.now());
                    badgeDAO.updateStatus(badge.getId(), VisitorBadge.STATUS_AVAILABLE);
                    badge.setStatus(VisitorBadge.STATUS_AVAILABLE);
                    yield new ScanResult(ScanStatus.EXIT_REGISTERED, badge, log,
                            "Salida registrada para: " + log.getDisplayName());
                } else {
                    // Estado inconsistente: marcar disponible
                    badgeDAO.updateStatus(badge.getId(), VisitorBadge.STATUS_AVAILABLE);
                    yield new ScanResult(ScanStatus.BADGE_AVAILABLE, badge, null,
                            "Carné disponible. Ingrese la cédula del visitante.");
                }
            }

            case VisitorBadge.STATUS_LOST ->
                new ScanResult(ScanStatus.BADGE_LOST, badge, null,
                        "Este carné está marcado como perdido. Contacte al administrador.");

            default ->
                new ScanResult(ScanStatus.ERROR, badge, null,
                        "Estado desconocido del carné.");
        };
    }

    /**
     * Registra la entrada de un visitante con los datos proporcionados.
     * Llama este método después de que BADGE_AVAILABLE confirmó el carné.
     */
    public VisitorLog registerEntry(VisitorBadge badge, String idNumber,
                                    String firstName, String lastName,
                                    String justification) throws SQLException {
        VisitorLog log = new VisitorLog(badge.getId(), idNumber);
        log.setFirstName(firstName == null || firstName.isBlank() ? null : firstName.trim());
        log.setLastName(lastName == null || lastName.isBlank() ? null : lastName.trim());
        log.setJustification(justification == null || justification.isBlank() ? null : justification.trim());

        logDAO.save(log);
        log.setBadgeCode(badge.getCode());

        badgeDAO.updateStatus(badge.getId(), VisitorBadge.STATUS_IN_USE);
        badge.setStatus(VisitorBadge.STATUS_IN_USE);

        logger.info("Entrada registrada: cédula={}, carné={}", idNumber, badge.getCode());
        return log;
    }

    /**
     * Marca un carné como perdido (requiere que el admin ya verificó la contraseña).
     */
    public boolean markBadgeLost(Long badgeId) {
        return badgeDAO.updateStatus(badgeId, VisitorBadge.STATUS_LOST);
    }

    /**
     * Restaura un carné perdido a disponible (requiere que el admin ya verificó la contraseña).
     */
    public boolean markBadgeAvailable(Long badgeId) {
        return badgeDAO.updateStatus(badgeId, VisitorBadge.STATUS_AVAILABLE);
    }

    /**
     * Crea un nuevo carné en el sistema.
     */
    public VisitorBadge createBadge(String code) throws SQLException {
        if (badgeDAO.existsByCode(code)) {
            throw new IllegalArgumentException("Ya existe un carné con el código: " + code);
        }
        VisitorBadge badge = new VisitorBadge(code);
        return badgeDAO.save(badge);
    }

    /**
     * Elimina un carné del sistema (solo si está disponible, requiere admin).
     */
    public boolean deleteBadge(Long badgeId) throws SQLException {
        return badgeDAO.delete(badgeId);
    }

    public List<VisitorBadge> getAllBadges() {
        return badgeDAO.findAll();
    }

    public List<VisitorLog> getCurrentlyInside() {
        return logDAO.findCurrentlyInside();
    }

    public List<VisitorLog> getAllLogs() {
        return logDAO.findAll();
    }

    /**
     * Elimina registros históricos anteriores a la fecha indicada.
     */
    public int purgeLogsBefore(LocalDateTime cutoff) {
        return logDAO.deleteBeforeDate(cutoff);
    }

    public int countAvailableBadges() {
        return badgeDAO.countByStatus(VisitorBadge.STATUS_AVAILABLE);
    }

    public int countBadgesInUse() {
        return badgeDAO.countByStatus(VisitorBadge.STATUS_IN_USE);
    }

    public int countTodayVisits() {
        return logDAO.countToday();
    }
}
