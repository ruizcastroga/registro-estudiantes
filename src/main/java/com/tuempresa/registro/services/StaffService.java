package com.tuempresa.registro.services;

import com.tuempresa.registro.dao.StaffDAO;
import com.tuempresa.registro.dao.ActivityLogDAO;
import com.tuempresa.registro.models.StaffMember;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class StaffService {
    private static final Logger logger = LoggerFactory.getLogger(StaffService.class);
    private final StaffDAO staffDAO;
    private final ActivityLogDAO activityLogDAO;

    public StaffService() {
        this.staffDAO = new StaffDAO();
        this.activityLogDAO = new ActivityLogDAO();
    }

    // processScan - for when staff badge is scanned at the gate
    // Returns a ScanResult similar to StudentService
    public ScanResult processScan(String barcode, String scannedBy) {
        Optional<StaffMember> opt = staffDAO.findByBarcode(barcode);
        if (opt.isEmpty()) {
            return new ScanResult(false, null, "Personal no encontrado", ScanResult.Status.NOT_FOUND);
        }
        StaffMember staff = opt.get();
        if (!staff.isActive()) {
            return new ScanResult(true, staff, "INACTIVO", ScanResult.Status.INACTIVE);
        }
        // Log the scan
        activityLogDAO.logActivity("staff", staff.getId(), staff.getFullName(), barcode, "exit", scannedBy);
        return new ScanResult(true, staff, "REGISTRADO", ScanResult.Status.REGISTERED);
    }

    public Optional<StaffMember> findByBarcode(String barcode) { return staffDAO.findByBarcode(barcode); }
    public List<StaffMember> getAllStaff() { return staffDAO.findAll(); }
    public List<StaffMember> getAllActiveStaff() { return staffDAO.findAllActive(); }
    public List<StaffMember> searchStaff(String searchTerm) { return staffDAO.searchByName(searchTerm); }

    public StaffMember saveStaff(StaffMember staff) throws SQLException {
        validateStaff(staff);
        if (staffDAO.existsByBarcode(staff.getBarcode())) {
            throw new IllegalArgumentException("Ya existe un miembro del personal con ese código");
        }
        return staffDAO.save(staff);
    }

    public boolean updateStaff(StaffMember staff) throws SQLException {
        if (staff.getId() == null) {
            throw new IllegalArgumentException("El miembro del personal debe tener un ID para actualizar");
        }
        validateStaff(staff);
        return staffDAO.update(staff);
    }

    public boolean deleteStaff(Long id) throws SQLException { return staffDAO.delete(id); }
    public int countActiveStaff() { return staffDAO.countActive(); }

    private void validateStaff(StaffMember staff) {
        if (staff == null) throw new IllegalArgumentException("El personal no puede ser nulo");
        if (staff.getBarcode() == null || staff.getBarcode().trim().isEmpty())
            throw new IllegalArgumentException("El código es obligatorio");
        if (staff.getFirstName() == null || staff.getFirstName().trim().isEmpty())
            throw new IllegalArgumentException("El nombre es obligatorio");
        if (staff.getLastName() == null || staff.getLastName().trim().isEmpty())
            throw new IllegalArgumentException("El apellido es obligatorio");
    }

    public static class ScanResult {
        public enum Status { REGISTERED, NOT_FOUND, INACTIVE }
        private final boolean found;
        private final StaffMember staff;
        private final String message;
        private final Status status;

        public ScanResult(boolean found, StaffMember staff, String message, Status status) {
            this.found = found; this.staff = staff; this.message = message; this.status = status;
        }
        public boolean isFound() { return found; }
        public StaffMember getStaff() { return staff; }
        public String getMessage() { return message; }
        public Status getStatus() { return status; }
    }
}
