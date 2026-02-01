package com.tuempresa.registro.services;

import com.tuempresa.registro.dao.EntryLogDAO;
import com.tuempresa.registro.dao.GuardianDAO;
import com.tuempresa.registro.dao.StudentDAO;
import com.tuempresa.registro.models.EntryLog;
import com.tuempresa.registro.models.Guardian;
import com.tuempresa.registro.models.Student;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Servicio de negocio para la gestión de estudiantes.
 * Orquesta las operaciones entre DAOs y aplica reglas de negocio.
 */
public class StudentService {

    private static final Logger logger = LoggerFactory.getLogger(StudentService.class);

    private final StudentDAO studentDAO;
    private final GuardianDAO guardianDAO;
    private final EntryLogDAO entryLogDAO;

    /**
     * Constructor que inicializa los DAOs.
     */
    public StudentService() {
        this.studentDAO = new StudentDAO();
        this.guardianDAO = new GuardianDAO();
        this.entryLogDAO = new EntryLogDAO();
    }

    /**
     * Procesa un escaneo de código de barras.
     * Esta es la operación principal del sistema.
     *
     * @param barcode   Código de barras escaneado
     * @param scannedBy Usuario que realiza el escaneo
     * @return Resultado del escaneo con información del estudiante
     */
    public ScanResult processScan(String barcode, String scannedBy) {
        logger.info("Procesando escaneo: {} por {}", barcode, scannedBy);

        // Buscar estudiante por código de barras
        Optional<Student> studentOpt = studentDAO.findByBarcode(barcode);

        if (studentOpt.isEmpty()) {
            logger.warn("Estudiante no encontrado: {}", barcode);
            return new ScanResult(false, null, "Estudiante no encontrado", ScanResult.Status.NOT_FOUND);
        }

        Student student = studentOpt.get();

        // Cargar acudientes del estudiante
        List<Guardian> guardians = guardianDAO.findAuthorizedByStudentId(student.getId());
        student.setGuardians(guardians);

        // Verificar si el estudiante está activo o suspendido
        if ("suspended".equals(student.getStatus())) {
            logger.warn("Estudiante suspendido: {}", student.getFullName());
            return new ScanResult(true, student, "SUSPENDIDO", ScanResult.Status.SUSPENDED);
        }

        if ("inactive".equals(student.getStatus())) {
            logger.warn("Estudiante inactivo: {}", student.getFullName());
            return new ScanResult(true, student, "INACTIVO", ScanResult.Status.INACTIVE);
        }

        // Determinar si puede salir solo
        boolean canExit = !student.isRequiresGuardian();
        ScanResult.Status status = canExit ?
                ScanResult.Status.CAN_EXIT :
                ScanResult.Status.REQUIRES_GUARDIAN;

        String message = canExit ?
                "PUEDE SALIR" :
                "REQUIERE ACOMPAÑANTE";

        // Registrar el log de salida
        try {
            EntryLog log = new EntryLog(student.getId(), scannedBy);
            log.setLogType("exit");
            entryLogDAO.save(log);
            logger.info("Log de salida registrado para: {}", student.getFullName());
        } catch (SQLException e) {
            logger.error("Error al registrar log de salida", e);
        }

        return new ScanResult(true, student, message, status);
    }

    /**
     * Busca un estudiante por código de barras.
     *
     * @param barcode Código de barras
     * @return Optional con el estudiante
     */
    public Optional<Student> findByBarcode(String barcode) {
        return studentDAO.findByBarcode(barcode);
    }

    /**
     * Busca un estudiante por ID.
     *
     * @param id ID del estudiante
     * @return Optional con el estudiante
     */
    public Optional<Student> findById(Long id) {
        return studentDAO.findById(id);
    }

    /**
     * Obtiene todos los estudiantes activos.
     *
     * @return Lista de estudiantes activos
     */
    public List<Student> getAllActiveStudents() {
        return studentDAO.findAllActive();
    }

    /**
     * Obtiene todos los estudiantes.
     *
     * @return Lista de todos los estudiantes
     */
    public List<Student> getAllStudents() {
        return studentDAO.findAll();
    }

    /**
     * Busca estudiantes por nombre o código.
     *
     * @param searchTerm Término de búsqueda
     * @return Lista de estudiantes encontrados
     */
    public List<Student> searchStudents(String searchTerm) {
        return studentDAO.searchByName(searchTerm);
    }

    /**
     * Guarda un nuevo estudiante.
     *
     * @param student Estudiante a guardar
     * @return Estudiante guardado
     * @throws SQLException           Si ocurre error en BD
     * @throws IllegalArgumentException Si los datos son inválidos
     */
    public Student saveStudent(Student student) throws SQLException {
        // Validaciones
        validateStudent(student);

        // Verificar que el código no exista
        if (studentDAO.existsByBarcode(student.getBarcode())) {
            throw new IllegalArgumentException("Ya existe un estudiante con ese código de barras");
        }

        return studentDAO.save(student);
    }

    /**
     * Actualiza un estudiante existente.
     *
     * @param student Estudiante a actualizar
     * @return true si se actualizó correctamente
     * @throws SQLException           Si ocurre error en BD
     * @throws IllegalArgumentException Si los datos son inválidos
     */
    public boolean updateStudent(Student student) throws SQLException {
        // Validaciones
        if (student.getId() == null) {
            throw new IllegalArgumentException("El estudiante debe tener un ID para actualizar");
        }
        validateStudent(student);

        return studentDAO.update(student);
    }

    /**
     * Elimina un estudiante.
     *
     * @param id ID del estudiante
     * @return true si se eliminó correctamente
     * @throws SQLException Si ocurre error en BD
     */
    public boolean deleteStudent(Long id) throws SQLException {
        return studentDAO.delete(id);
    }

    /**
     * Desactiva un estudiante (soft delete).
     *
     * @param id ID del estudiante
     * @return true si se desactivó correctamente
     */
    public boolean deactivateStudent(Long id) {
        return studentDAO.deactivate(id);
    }

    /**
     * Obtiene los acudientes de un estudiante.
     *
     * @param studentId ID del estudiante
     * @return Lista de acudientes
     */
    public List<Guardian> getStudentGuardians(Long studentId) {
        return guardianDAO.findByStudentId(studentId);
    }

    /**
     * Agrega un acudiente a un estudiante.
     *
     * @param guardian Acudiente a agregar
     * @return Acudiente guardado
     * @throws SQLException Si ocurre error en BD
     */
    public Guardian addGuardian(Guardian guardian) throws SQLException {
        return guardianDAO.save(guardian);
    }

    /**
     * Obtiene los últimos registros de escaneo.
     *
     * @param limit Número máximo de registros
     * @return Lista de registros recientes
     */
    public List<EntryLog> getRecentScans(int limit) {
        List<EntryLog> logs = entryLogDAO.findRecent(limit);

        // Enriquecer con datos del estudiante
        for (EntryLog log : logs) {
            studentDAO.findById(log.getStudentId()).ifPresent(log::setStudent);
        }

        return logs;
    }

    /**
     * Cuenta el total de estudiantes activos.
     *
     * @return Número de estudiantes activos
     */
    public int countActiveStudents() {
        return studentDAO.countActive();
    }

    /**
     * Cuenta los escaneos del día.
     *
     * @return Número de escaneos de hoy
     */
    public int countTodayScans() {
        return entryLogDAO.countToday();
    }

    /**
     * Valida los datos de un estudiante.
     *
     * @param student Estudiante a validar
     * @throws IllegalArgumentException Si los datos son inválidos
     */
    private void validateStudent(Student student) {
        if (student == null) {
            throw new IllegalArgumentException("El estudiante no puede ser nulo");
        }
        if (student.getBarcode() == null || student.getBarcode().trim().isEmpty()) {
            throw new IllegalArgumentException("El código de barras es obligatorio");
        }
        if (student.getFirstName() == null || student.getFirstName().trim().isEmpty()) {
            throw new IllegalArgumentException("El nombre es obligatorio");
        }
        if (student.getLastName() == null || student.getLastName().trim().isEmpty()) {
            throw new IllegalArgumentException("El apellido es obligatorio");
        }
    }

    /**
     * Clase que encapsula el resultado de un escaneo.
     */
    public static class ScanResult {
        private final boolean found;
        private final Student student;
        private final String message;
        private final Status status;

        public enum Status {
            CAN_EXIT,           // Puede salir solo
            REQUIRES_GUARDIAN,  // Requiere acompañante
            NOT_FOUND,          // No encontrado
            INACTIVE,           // Estudiante inactivo
            SUSPENDED           // Estudiante suspendido
        }

        public ScanResult(boolean found, Student student, String message, Status status) {
            this.found = found;
            this.student = student;
            this.message = message;
            this.status = status;
        }

        public boolean isFound() { return found; }
        public Student getStudent() { return student; }
        public String getMessage() { return message; }
        public Status getStatus() { return status; }

        public boolean canExit() {
            return status == Status.CAN_EXIT;
        }

        public boolean requiresGuardian() {
            return status == Status.REQUIRES_GUARDIAN;
        }

        public boolean isInactive() {
            return status == Status.INACTIVE;
        }

        public boolean isSuspended() {
            return status == Status.SUSPENDED;
        }

        public boolean isNotActive() {
            return status == Status.INACTIVE || status == Status.SUSPENDED;
        }
    }
}
