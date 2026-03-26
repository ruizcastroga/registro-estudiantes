package com.tuempresa.registro.controllers;

import com.tuempresa.registro.dao.ActivityLogDAO;
import com.tuempresa.registro.models.AdminUser;
import com.tuempresa.registro.models.EntryLog;
import com.tuempresa.registro.models.Guardian;
import com.tuempresa.registro.models.StaffMember;
import com.tuempresa.registro.models.Student;
import com.tuempresa.registro.models.VisitorBadge;
import com.tuempresa.registro.services.StaffService;
import com.tuempresa.registro.services.StudentService;
import com.tuempresa.registro.services.VisitorService;
import com.tuempresa.registro.utils.LicenseManager;
import com.tuempresa.registro.utils.SecurityManager;
import com.tuempresa.registro.utils.SessionManager;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Controlador de la vista principal del scanner.
 * Gestiona el escaneo de códigos de barras y muestra los resultados.
 * Busca en estudiantes, personal y visitantes de forma unificada.
 */
public class ScannerController implements Initializable {

    private static final Logger logger = LoggerFactory.getLogger(ScannerController.class);

    // Formateador de fecha/hora
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("EEEE, dd 'de' MMMM 'de' yyyy - hh:mm:ss a");

    // Tiempo en segundos para limpiar la pantalla automáticamente
    private static final int AUTO_CLEAR_SECONDS = 10;

    // Máximo de elementos en el historial
    private static final int MAX_HISTORY_ITEMS = 5;

    // Componentes FXML - Generales
    @FXML private Label schoolNameLabel;
    @FXML private Label dateTimeLabel;
    @FXML private Label todayScansLabel;
    @FXML private TextField barcodeInput;
    @FXML private Label instructionLabel;
    @FXML private ListView<String> historyListView;
    @FXML private Label statusIndicator;
    @FXML private Label connectionStatusLabel;
    @FXML private Label licenseInfoLabel;
    @FXML private StackPane shieldContainer;
    @FXML private ImageView schoolShieldImage;
    @FXML private Label shieldPlaceholder;

    // Componentes FXML - Panel de resultado estudiante
    @FXML private VBox resultPanel;
    @FXML private Label studentNameLabel;
    @FXML private Label studentGradeLabel;
    @FXML private Label statusLabel;
    @FXML private Label activityStatusLabel;
    @FXML private VBox guardiansPanel;
    @FXML private VBox guardiansList;

    // Componentes FXML - Panel de resultado personal
    @FXML private VBox staffResultPanel;
    @FXML private Label staffNameLabel;
    @FXML private Label staffDepartmentLabel;
    @FXML private Label staffStatusLabel;

    // Componentes FXML - Panel de resultado visitante
    @FXML private VBox visitorResultPanel;
    @FXML private Label visitorNameLabel;
    @FXML private Label visitorBadgeLabel;
    @FXML private Label visitorEntryExitLabel;
    @FXML private VBox visitorEntryFormPanel;
    @FXML private TextField visitorCedulaField;
    @FXML private TextField visitorNombreField;
    @FXML private TextField visitorApellidoField;
    @FXML private TextField visitorJustificacionField;
    @FXML private Label visitorEntryValidationLabel;

    // Componentes FXML - Panel no encontrado
    @FXML private VBox notFoundPanel;
    @FXML private Label notFoundLabel;
    @FXML private Label scannedCodeLabel;
    @FXML private Button quickRegisterButton;

    // Componentes FXML - Otros
    @FXML private Button manageButton;
    @FXML private Button clearHistoryButton;

    // Componentes FXML - Barra de sesión
    @FXML private HBox sessionBar;
    @FXML private Label sessionUserLabel;
    @FXML private Label sessionRoleLabel;
    @FXML private Label sessionTimerLabel;
    @FXML private Button sessionLoginBtn;
    @FXML private Button sessionLogoutBtn;

    // Servicios
    private StudentService studentService;
    private StaffService staffService;
    private VisitorService visitorService;
    private ActivityLogDAO activityLogDAO;
    private LicenseManager licenseManager;

    // Lista observable para el historial
    private ObservableList<String> historyItems;

    // Carné de visitante actual (para formulario de entrada)
    private VisitorBadge currentVisitorBadge;

    // Indica si el formulario inline de visitante está activo (no recapturar foco a barcodeInput)
    private boolean visitorFormActive = false;

    // Timer para actualizar la hora
    private Timer clockTimer;

    // Transición para auto-limpiar
    private PauseTransition autoClearTransition;

    // Último código escaneado (para registro rápido)
    private String lastScannedCode;

    /**
     * Inicializa el controlador después de cargar el FXML.
     */
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        logger.info("Inicializando ScannerController");

        // Inicializar servicios
        studentService = new StudentService();
        staffService = new StaffService();
        visitorService = new VisitorService();
        activityLogDAO = new ActivityLogDAO();
        licenseManager = LicenseManager.getInstance();

        // Configurar lista de historial
        historyItems = FXCollections.observableArrayList();
        historyListView.setItems(historyItems);

        // Configurar campo de entrada
        setupBarcodeInput();

        // Iniciar reloj
        startClock();

        // Cargar datos iniciales
        loadInitialData();

        // Configurar transición de auto-limpieza
        setupAutoClear();

        // Configurar información de licencia
        setupLicenseInfo();

        // Cargar escudo del colegio si existe
        loadSchoolShield();

        // Configurar barra de sesión
        setupSessionBar();

        // Enfocar el campo de entrada al iniciar
        Platform.runLater(() -> barcodeInput.requestFocus());

        logger.info("ScannerController inicializado correctamente");
    }

    /**
     * Configura la barra de sesión con listeners para cambios de estado.
     */
    private void setupSessionBar() {
        SessionManager sm = SessionManager.getInstance();
        sm.sessionActiveProperty().addListener((obs, oldVal, newVal) -> {
            sessionBar.setVisible(true);
            sessionBar.setManaged(true);
            if (newVal) {
                sessionUserLabel.setText("Usuario: " + sm.getCurrentUser().getUsername());
                sessionRoleLabel.setText("Rol: " + sm.getCurrentUser().getRole());
                sessionLoginBtn.setVisible(false);
                sessionLoginBtn.setManaged(false);
                sessionLogoutBtn.setVisible(true);
                sessionLogoutBtn.setManaged(true);
            } else {
                sessionUserLabel.setText("");
                sessionRoleLabel.setText("");
                sessionLoginBtn.setVisible(true);
                sessionLoginBtn.setManaged(true);
                sessionLogoutBtn.setVisible(false);
                sessionLogoutBtn.setManaged(false);
                sessionTimerLabel.setText("");
            }
        });
        sm.remainingTimeFormattedProperty().addListener((obs, oldVal, newVal) -> {
            sessionTimerLabel.setText(newVal);
            int remaining = sm.remainingSecondsProperty().get();
            if (remaining <= 120 && remaining > 0) {
                sessionTimerLabel.getStyleClass().setAll("session-timer-warning");
                sessionBar.getStyleClass().setAll("session-bar", "session-bar-warning");
            } else {
                sessionTimerLabel.getStyleClass().setAll("session-timer-label");
                sessionBar.getStyleClass().setAll("session-bar");
            }
        });
        // Show bar with login button if no active session
        if (sm.isSessionActive()) {
            sessionBar.setVisible(true);
            sessionBar.setManaged(true);
            sessionUserLabel.setText("Usuario: " + sm.getCurrentUser().getUsername());
            sessionRoleLabel.setText("Rol: " + sm.getCurrentUser().getRole());
            sessionTimerLabel.setText(sm.remainingTimeFormattedProperty().get());
            sessionLoginBtn.setVisible(false);
            sessionLoginBtn.setManaged(false);
            sessionLogoutBtn.setVisible(true);
            sessionLogoutBtn.setManaged(true);
        } else {
            sessionBar.setVisible(true);
            sessionBar.setManaged(true);
        }
    }

    /**
     * Configura el campo de entrada del código de barras.
     */
    private void setupBarcodeInput() {
        // El scanner funciona como teclado, así que capturamos el input
        // y procesamos cuando se presiona Enter

        // Mantener el foco en el campo de entrada (solo cuando el formulario de visitante no está activo)
        barcodeInput.focusedProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal && !visitorFormActive) {
                Platform.runLater(() -> {
                    if (!barcodeInput.isFocused() && !visitorFormActive) {
                        barcodeInput.requestFocus();
                    }
                });
            }
        });
    }

    /**
     * Inicia el reloj que actualiza la hora cada segundo.
     */
    private void startClock() {
        clockTimer = new Timer(true);
        clockTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                Platform.runLater(() -> {
                    dateTimeLabel.setText(LocalDateTime.now().format(DATE_TIME_FORMATTER));
                });
            }
        }, 0, 1000);
    }

    /**
     * Carga los datos iniciales (estadísticas, historial).
     */
    private void loadInitialData() {
        // Actualizar conteo de escaneos del día
        updateTodayScansCount();

        // Cargar historial reciente
        loadRecentHistory();

        // Mostrar nombre del colegio (por ahora hardcodeado)
        schoolNameLabel.setText("Sistema de Registro de Estudiantes");
    }

    /**
     * Configura la transición de auto-limpieza.
     */
    private void setupAutoClear() {
        autoClearTransition = new PauseTransition(Duration.seconds(AUTO_CLEAR_SECONDS));
        autoClearTransition.setOnFinished(event -> clearResult());
    }

    /**
     * Configura la información de licencia.
     */
    private void setupLicenseInfo() {
        if (licenseManager.isDemoMode()) {
            licenseInfoLabel.setText("Modo Demo");
            licenseInfoLabel.getStyleClass().add("demo-mode");
        } else {
            licenseInfoLabel.setText("Licencia Activa");
            licenseInfoLabel.getStyleClass().add("licensed-mode");
        }
    }

    /**
     * Manejador del evento cuando se escanea un código de barras.
     * Se dispara al presionar Enter en el campo de entrada.
     * Busca en estudiantes, personal y visitantes de forma unificada.
     */
    @FXML
    private void onBarcodeScanned() {
        String barcode = barcodeInput.getText().trim();

        if (barcode.isEmpty()) {
            return;
        }

        logger.info("Código escaneado: {}", barcode);
        lastScannedCode = barcode;

        // Ocultar instrucciones
        instructionLabel.setVisible(false);

        // 1. Intentar como estudiante
        StudentService.ScanResult studentResult = studentService.processScan(barcode, "Guardia");
        if (studentResult.isFound()) {
            displayStudentResult(studentResult, barcode);
            addToHistory("student", studentResult.getStudent().getFullName(),
                    studentResult.canExit() ? "[OK]" : "[REQ]");
            barcodeInput.clear();
            updateTodayScansCount();
            autoClearTransition.playFromStart();
            barcodeInput.requestFocus();
            return;
        }

        // 2. Intentar como personal
        StaffService.ScanResult staffResult = staffService.processScan(barcode, "Guardia");
        if (staffResult.isFound()) {
            displayStaffResult(staffResult, barcode);
            addToHistory("staff", staffResult.getStaff().getFullName(), "[PER]");
            barcodeInput.clear();
            updateTodayScansCount();
            autoClearTransition.playFromStart();
            barcodeInput.requestFocus();
            return;
        }

        // 3. Intentar como carné de visitante
        VisitorService.ScanResult visitorResult = visitorService.processBadgeScan(barcode);
        if (visitorResult.getStatus() != VisitorService.ScanStatus.BADGE_NOT_FOUND) {
            displayVisitorResult(visitorResult, barcode);
            addToHistory("visitor", barcode, "[VIS]");
            barcodeInput.clear();
            updateTodayScansCount();
            autoClearTransition.playFromStart();
            barcodeInput.requestFocus();
            return;
        }

        // 4. No encontrado en ninguna tabla
        displayNotFound(barcode);
        addToHistory(null, barcode, "[???]");

        // Limpiar el campo de entrada
        barcodeInput.clear();

        // Actualizar estadísticas
        updateTodayScansCount();

        // Iniciar auto-limpieza
        autoClearTransition.playFromStart();

        // Mantener el foco
        barcodeInput.requestFocus();
    }

    /**
     * Muestra el resultado del escaneo de un estudiante.
     */
    private void displayStudentResult(StudentService.ScanResult result, String barcode) {
        hideAllResultPanels();

        Student student = result.getStudent();

        // Mostrar panel de resultado estudiante
        resultPanel.setVisible(true);
        resultPanel.setManaged(true);

        // Mostrar información del estudiante
        studentNameLabel.setText(student.getFullName());
        studentGradeLabel.setText(student.getGrade() != null ? student.getGrade() : "");

        // Aplicar estilos según el estado
        resultPanel.getStyleClass().removeAll("can-exit", "requires-guardian", "inactive");
        statusLabel.getStyleClass().removeAll("status-ok", "status-warning", "status-inactive");
        activityStatusLabel.getStyleClass().removeAll("status-active", "status-inactive");

        // Siempre mostrar el estado de actividad
        activityStatusLabel.setVisible(true);
        activityStatusLabel.setManaged(true);

        // Verificar estado de actividad (inactivo/activo)
        if (result.isInactive()) {
            resultPanel.getStyleClass().add("inactive");
            statusLabel.setText("NO PUEDE ENTRAR");
            statusLabel.getStyleClass().add("status-inactive");
            activityStatusLabel.setText("INACTIVO");
            activityStatusLabel.getStyleClass().add("status-inactive");
            guardiansPanel.setVisible(false);
            guardiansPanel.setManaged(false);

        } else if (result.requiresGuardian()) {
            resultPanel.getStyleClass().add("requires-guardian");
            statusLabel.setText("REQUIERE ACOMPAÑANTE");
            statusLabel.getStyleClass().add("status-warning");
            activityStatusLabel.setText("ACTIVO");
            activityStatusLabel.getStyleClass().add("status-active");
            displayGuardians(student.getGuardians());

        } else {
            resultPanel.getStyleClass().add("can-exit");
            statusLabel.setText("PUEDE SALIR");
            statusLabel.getStyleClass().add("status-ok");
            activityStatusLabel.setText("ACTIVO");
            activityStatusLabel.getStyleClass().add("status-active");
            guardiansPanel.setVisible(false);
            guardiansPanel.setManaged(false);
        }
    }

    /**
     * Muestra el resultado del escaneo de un miembro del personal.
     */
    private void displayStaffResult(StaffService.ScanResult result, String barcode) {
        hideAllResultPanels();

        StaffMember staff = result.getStaff();

        if (staffResultPanel != null) {
            staffResultPanel.setVisible(true);
            staffResultPanel.setManaged(true);

            if (staffNameLabel != null) {
                staffNameLabel.setText(staff.getFullName());
            }
            if (staffDepartmentLabel != null) {
                staffDepartmentLabel.setText(staff.getDepartment() != null ? staff.getDepartment() : "");
            }
            if (staffStatusLabel != null) {
                staffStatusLabel.getStyleClass().removeAll("status-ok", "status-inactive");
                if (result.getStatus() == StaffService.ScanResult.Status.INACTIVE) {
                    staffStatusLabel.setText("INACTIVO");
                    staffStatusLabel.getStyleClass().add("status-inactive");
                } else {
                    staffStatusLabel.setText("REGISTRADO");
                    staffStatusLabel.getStyleClass().add("status-ok");
                }
            }
        } else {
            // Fallback: usar panel de estudiantes para mostrar info del personal
            resultPanel.setVisible(true);
            resultPanel.setManaged(true);
            resultPanel.getStyleClass().removeAll("can-exit", "requires-guardian", "inactive");

            studentNameLabel.setText(staff.getFullName());
            studentGradeLabel.setText("PERSONAL");

            activityStatusLabel.setVisible(true);
            activityStatusLabel.setManaged(true);

            statusLabel.getStyleClass().removeAll("status-ok", "status-warning", "status-inactive");
            activityStatusLabel.getStyleClass().removeAll("status-active", "status-inactive");

            if (result.getStatus() == StaffService.ScanResult.Status.INACTIVE) {
                resultPanel.getStyleClass().add("inactive");
                statusLabel.setText("INACTIVO");
                statusLabel.getStyleClass().add("status-inactive");
                activityStatusLabel.setText("INACTIVO");
                activityStatusLabel.getStyleClass().add("status-inactive");
            } else {
                resultPanel.getStyleClass().add("can-exit");
                statusLabel.setText("REGISTRADO");
                statusLabel.getStyleClass().add("status-ok");
                activityStatusLabel.setText("ACTIVO");
                activityStatusLabel.getStyleClass().add("status-active");
            }

            guardiansPanel.setVisible(false);
            guardiansPanel.setManaged(false);
        }
    }

    /**
     * Muestra el resultado del escaneo de un carné de visitante.
     */
    private void displayVisitorResult(VisitorService.ScanResult result, String barcode) {
        hideAllResultPanels();

        visitorResultPanel.setVisible(true);
        visitorResultPanel.setManaged(true);
        visitorResultPanel.getStyleClass().removeAll("can-exit", "requires-guardian", "inactive");
        visitorEntryExitLabel.getStyleClass().removeAll("status-ok", "status-warning", "status-inactive");

        // Ocultar formulario de entrada por defecto
        visitorEntryFormPanel.setVisible(false);
        visitorEntryFormPanel.setManaged(false);

        switch (result.getStatus()) {
            case BADGE_AVAILABLE -> {
                currentVisitorBadge = result.getBadge();
                visitorNameLabel.setText("VISITANTE - Carné: " + barcode);
                visitorBadgeLabel.setText("Carné disponible — ingrese los datos para registrar entrada");
                visitorEntryExitLabel.setText("CARNÉ DISPONIBLE");
                visitorEntryExitLabel.getStyleClass().add("status-ok");
                visitorResultPanel.getStyleClass().add("can-exit");

                // Mostrar formulario inline de entrada
                visitorCedulaField.clear();
                visitorNombreField.clear();
                visitorApellidoField.clear();
                visitorJustificacionField.clear();
                visitorEntryValidationLabel.setVisible(false);
                visitorEntryValidationLabel.setManaged(false);
                visitorEntryFormPanel.setVisible(true);
                visitorEntryFormPanel.setManaged(true);
                visitorFormActive = true;
                autoClearTransition.stop(); // No auto-limpiar mientras el formulario está activo
                Platform.runLater(() -> visitorCedulaField.requestFocus());
            }
            case EXIT_REGISTERED -> {
                String visitorName = result.getLog() != null ? result.getLog().getDisplayName() : barcode;
                visitorNameLabel.setText("VISITANTE - " + visitorName);
                visitorBadgeLabel.setText("Carné: " + barcode);
                visitorEntryExitLabel.setText("SALIDA REGISTRADA");
                visitorEntryExitLabel.getStyleClass().add("status-ok");
                visitorResultPanel.getStyleClass().add("can-exit");
            }
            case BADGE_LOST -> {
                visitorNameLabel.setText("VISITANTE - Carné: " + barcode);
                visitorBadgeLabel.setText("Carné perdido");
                visitorEntryExitLabel.setText("CARNÉ PERDIDO — Contacte al administrador");
                visitorEntryExitLabel.getStyleClass().add("status-inactive");
                visitorResultPanel.getStyleClass().add("inactive");
            }
            default -> {
                visitorNameLabel.setText("VISITANTE - Carné: " + barcode);
                visitorBadgeLabel.setText(result.getMessage());
                visitorEntryExitLabel.setText("ERROR");
                visitorEntryExitLabel.getStyleClass().add("status-warning");
                visitorResultPanel.getStyleClass().add("requires-guardian");
            }
        }
    }

    /**
     * Confirma la entrada del visitante desde el formulario inline del scanner.
     */
    @FXML
    private void onConfirmVisitorEntry() {
        if (currentVisitorBadge == null) return;

        String cedula = visitorCedulaField.getText().trim();
        if (cedula.isEmpty()) {
            visitorEntryValidationLabel.setText("La cédula es obligatoria");
            visitorEntryValidationLabel.setVisible(true);
            visitorEntryValidationLabel.setManaged(true);
            visitorCedulaField.requestFocus();
            return;
        }

        try {
            com.tuempresa.registro.models.VisitorLog log = visitorService.registerEntry(
                currentVisitorBadge,
                cedula,
                visitorNombreField.getText(),
                visitorApellidoField.getText(),
                visitorJustificacionField.getText()
            );

            // Mostrar éxito
            visitorEntryFormPanel.setVisible(false);
            visitorEntryFormPanel.setManaged(false);
            String name = log.getDisplayName();
            visitorNameLabel.setText("VISITANTE - " + (name != null && !name.isBlank() ? name : cedula));
            visitorBadgeLabel.setText("Carné: " + currentVisitorBadge.getCode() + " | Cédula: " + cedula);
            visitorEntryExitLabel.setText("ENTRADA REGISTRADA");

            visitorFormActive = false;
            addToHistory("visitor", currentVisitorBadge.getCode(), "[VIS ENTRADA]");
            updateTodayScansCount();
            autoClearTransition.playFromStart();
            currentVisitorBadge = null;

        } catch (Exception e) {
            logger.error("Error al registrar entrada del visitante", e);
            visitorEntryValidationLabel.setText("Error: " + e.getMessage());
            visitorEntryValidationLabel.setVisible(true);
            visitorEntryValidationLabel.setManaged(true);
        }
    }

    /**
     * Cancela el formulario de entrada de visitante.
     */
    @FXML
    private void onCancelVisitorEntry() {
        visitorFormActive = false;
        hideAllResultPanels();
        barcodeInput.clear();
        barcodeInput.requestFocus();
        currentVisitorBadge = null;
    }

    /**
     * Muestra el panel de no encontrado.
     */
    private void displayNotFound(String barcode) {
        hideAllResultPanels();

        notFoundPanel.setVisible(true);
        notFoundPanel.setManaged(true);
        activityStatusLabel.setVisible(false);
        activityStatusLabel.setManaged(false);

        scannedCodeLabel.setText("Código: " + barcode);
    }

    /**
     * Oculta todos los paneles de resultado.
     */
    private void hideAllResultPanels() {
        resultPanel.setVisible(false);
        resultPanel.setManaged(false);
        notFoundPanel.setVisible(false);
        notFoundPanel.setManaged(false);
        if (staffResultPanel != null) {
            staffResultPanel.setVisible(false);
            staffResultPanel.setManaged(false);
        }
        if (visitorResultPanel != null) {
            visitorResultPanel.setVisible(false);
            visitorResultPanel.setManaged(false);
        }
        if (visitorEntryFormPanel != null) {
            visitorEntryFormPanel.setVisible(false);
            visitorEntryFormPanel.setManaged(false);
        }
        guardiansPanel.setVisible(false);
        guardiansPanel.setManaged(false);
        activityStatusLabel.setVisible(false);
        activityStatusLabel.setManaged(false);
    }

    /**
     * Muestra la lista de tutores legales autorizados.
     */
    private void displayGuardians(List<Guardian> guardians) {
        if (guardians == null || guardians.isEmpty()) {
            guardiansPanel.setVisible(false);
            guardiansPanel.setManaged(false);
            return;
        }

        guardiansPanel.setVisible(true);
        guardiansPanel.setManaged(true);

        guardiansList.getChildren().clear();

        for (Guardian guardian : guardians) {
            Label guardianLabel = new Label(guardian.getDescription());
            guardianLabel.getStyleClass().add("guardian-item");
            guardiansList.getChildren().add(guardianLabel);
        }
    }

    /**
     * Agrega un escaneo al historial.
     */
    private void addToHistory(String type, String name, String tag) {
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("hh:mm:ss a"));
        String historyEntry = String.format("%s %s %s", time, tag, name);

        // Agregar al inicio de la lista
        historyItems.add(0, historyEntry);

        // Mantener solo los últimos N elementos
        while (historyItems.size() > MAX_HISTORY_ITEMS) {
            historyItems.remove(historyItems.size() - 1);
        }
    }

    /**
     * Limpia el resultado mostrado y vuelve al estado inicial.
     */
    private void clearResult() {
        resultPanel.setVisible(false);
        resultPanel.setManaged(false);
        notFoundPanel.setVisible(false);
        notFoundPanel.setManaged(false);
        if (staffResultPanel != null) {
            staffResultPanel.setVisible(false);
            staffResultPanel.setManaged(false);
        }
        instructionLabel.setVisible(true);
        guardiansPanel.setVisible(false);
        guardiansPanel.setManaged(false);
        activityStatusLabel.setVisible(false);
        activityStatusLabel.setManaged(false);

        // Limpiar estilos
        resultPanel.getStyleClass().removeAll("can-exit", "requires-guardian", "inactive");
    }

    /**
     * Actualiza el contador de escaneos del día usando ActivityLogDAO.
     */
    private void updateTodayScansCount() {
        int count = activityLogDAO.countToday();
        todayScansLabel.setText(String.valueOf(count));
    }

    /**
     * Carga el historial reciente usando ActivityLogDAO.
     */
    private void loadRecentHistory() {
        List<String> recentEntries = activityLogDAO.getRecentFormatted(MAX_HISTORY_ITEMS);

        historyItems.clear();
        historyItems.addAll(recentEntries);
    }

    /**
     * Manejador para limpiar el historial.
     */
    @FXML
    private void onClearHistory() {
        historyItems.clear();
        logger.info("Historial limpiado");
    }

    /**
     * Manejador para iniciar sesión.
     * Muestra un diálogo con campos de usuario y contraseña.
     */
    @FXML
    private void onLogin() {
        SecurityManager securityManager = SecurityManager.getInstance();
        SessionManager sessionManager = SessionManager.getInstance();

        while (true) {
            // Crear diálogo de login
            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.setTitle("Iniciar Sesión");
            dialog.setHeaderText("Ingrese sus credenciales");

            ButtonType loginButtonType = new ButtonType("Iniciar Sesión", ButtonBar.ButtonData.OK_DONE);
            dialog.getDialogPane().getButtonTypes().addAll(loginButtonType, ButtonType.CANCEL);

            GridPane grid = new GridPane();
            grid.setHgap(10);
            grid.setVgap(10);
            grid.setPadding(new Insets(20, 150, 10, 10));

            TextField usernameField = new TextField();
            usernameField.setPromptText("Usuario");
            PasswordField passwordField = new PasswordField();
            passwordField.setPromptText("Contraseña");

            grid.add(new Label("Usuario:"), 0, 0);
            grid.add(usernameField, 1, 0);
            grid.add(new Label("Contraseña:"), 0, 1);
            grid.add(passwordField, 1, 1);

            dialog.getDialogPane().setContent(grid);

            Platform.runLater(usernameField::requestFocus);

            Optional<ButtonType> dialogResult = dialog.showAndWait();

            if (dialogResult.isEmpty() || dialogResult.get() == ButtonType.CANCEL) {
                break; // Usuario canceló
            }

            String username = usernameField.getText().trim();
            String password = passwordField.getText();

            Optional<AdminUser> authResult = securityManager.authenticate(username, password);

            if (authResult.isPresent()) {
                AdminUser user = authResult.get();
                int timeout = sessionManager.getTimeoutMinutes();
                sessionManager.startSession(user, timeout);

                Alert successAlert = new Alert(Alert.AlertType.INFORMATION);
                successAlert.setTitle("Sesión Iniciada");
                successAlert.setHeaderText(null);
                successAlert.setContentText(
                        "Usuario " + user.getUsername() + " logueado correctamente. " +
                        "Tu sesión estará activa por " + timeout + " minutos. " +
                        "Si terminas antes, recuerda cerrar la sesión para cuidar los datos."
                );
                successAlert.showAndWait();
                break;
            } else {
                Alert errorAlert = new Alert(Alert.AlertType.ERROR);
                errorAlert.setTitle("Error de Autenticación");
                errorAlert.setHeaderText(null);
                errorAlert.setContentText("Usuario o contraseña incorrectos. Intente nuevamente.");
                errorAlert.showAndWait();
                // Loop continues - show dialog again
            }
        }

        barcodeInput.requestFocus();
    }

    /**
     * Manejador para cerrar sesión.
     */
    @FXML
    private void onLogout() {
        SessionManager.getInstance().endSession();
        barcodeInput.requestFocus();
    }

    /**
     * Manejador para abrir la gestión de estudiantes.
     */
    @FXML
    private void onManageStudents() {
        try {
            logger.info("Abriendo gestión de estudiantes");

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/student-crud.fxml"));
            Parent root = loader.load();

            Stage stage = (Stage) barcodeInput.getScene().getWindow();
            Scene scene = new Scene(root);
            scene.getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());

            stage.setMaximized(false);
            stage.setScene(scene);
            stage.setTitle("Gestión de Estudiantes");
            Platform.runLater(() -> stage.setMaximized(true));

        } catch (IOException e) {
            logger.error("Error al abrir gestión de estudiantes", e);
        }
    }

    /**
     * Manejador para abrir el módulo de control de visitantes.
     */
    @FXML
    private void onManageVisitors() {
        try {
            logger.info("Abriendo control de visitantes");

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/visitor-view.fxml"));
            Parent root = loader.load();

            Stage stage = (Stage) barcodeInput.getScene().getWindow();
            Scene scene = new Scene(root);
            scene.getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());

            stage.setMaximized(false);
            stage.setScene(scene);
            stage.setTitle("Control de Visitantes");
            Platform.runLater(() -> stage.setMaximized(true));

        } catch (IOException e) {
            logger.error("Error al abrir control de visitantes", e);
        }
    }

    /**
     * Manejador para abrir el módulo Creador de Carné.
     */
    @FXML
    private void onManageCarne() {
        try {
            logger.info("Abriendo Creador de Carné");

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/carne-view.fxml"));
            Parent root = loader.load();

            Stage stage = (Stage) barcodeInput.getScene().getWindow();
            Scene scene = new Scene(root);
            scene.getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());

            stage.setMaximized(false);
            stage.setScene(scene);
            stage.setTitle("Creador de Carné");
            Platform.runLater(() -> stage.setMaximized(true));

        } catch (IOException e) {
            logger.error("Error al abrir Creador de Carné", e);
        }
    }

    /**
     * Manejador para abrir la gestión de personal.
     */
    @FXML
    private void onManageStaff() {
        try {
            logger.info("Abriendo gestión de personal");

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/staff-admin.fxml"));
            Parent root = loader.load();

            Stage stage = (Stage) barcodeInput.getScene().getWindow();
            Scene scene = new Scene(root);
            scene.getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());

            stage.setMaximized(false);
            stage.setScene(scene);
            stage.setTitle("Gestión de Personal");
            Platform.runLater(() -> stage.setMaximized(true));

        } catch (IOException e) {
            logger.error("Error al abrir gestión de personal", e);
        }
    }

    /**
     * Manejador para abrir la configuración del sistema.
     */
    @FXML
    private void onManageSettings() {
        try {
            logger.info("Abriendo configuración del sistema");

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/settings-view.fxml"));
            Parent root = loader.load();

            Stage stage = (Stage) barcodeInput.getScene().getWindow();
            Scene scene = new Scene(root);
            scene.getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());

            stage.setMaximized(false);
            stage.setScene(scene);
            stage.setTitle("Configuración");
            Platform.runLater(() -> stage.setMaximized(true));

        } catch (IOException e) {
            logger.error("Error al abrir configuración del sistema", e);
        }
    }

    /**
     * Manejador para registro rápido de nuevo estudiante.
     */
    @FXML
    private void onQuickRegister() {
        try {
            logger.info("Iniciando registro rápido para código: {}", lastScannedCode);

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/student-crud.fxml"));
            Parent root = loader.load();

            // Pasar el código escaneado al controlador CRUD
            StudentCRUDController crudController = loader.getController();
            crudController.initForQuickRegister(lastScannedCode);

            Stage stage = (Stage) barcodeInput.getScene().getWindow();
            Scene scene = new Scene(root);
            scene.getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());

            stage.setMaximized(false);
            stage.setScene(scene);
            stage.setTitle("Registrar Nuevo Estudiante");
            Platform.runLater(() -> stage.setMaximized(true));

        } catch (IOException e) {
            logger.error("Error al abrir registro rápido", e);
        }
    }

    /**
     * Carga el escudo del colegio desde el directorio de configuración.
     * Busca en: ~/.registro-estudiantes/school_shield.png
     * Si no existe, muestra el placeholder.
     */
    private void loadSchoolShield() {
        try {
            // Buscar en el directorio de configuración del usuario
            String userHome = System.getProperty("user.home");
            File shieldFile = new File(userHome, ".registro-estudiantes/school_shield.png");

            if (shieldFile.exists() && shieldFile.isFile()) {
                try (InputStream is = new FileInputStream(shieldFile)) {
                    Image shieldImage = new Image(is);
                    schoolShieldImage.setImage(shieldImage);
                    schoolShieldImage.setVisible(true);
                    shieldPlaceholder.setVisible(false);
                    logger.info("Escudo del colegio cargado desde: {}", shieldFile.getAbsolutePath());
                }
            } else {
                // No hay escudo personalizado, mostrar placeholder
                schoolShieldImage.setVisible(false);
                shieldPlaceholder.setVisible(true);
                logger.debug("No se encontró escudo del colegio en: {}", shieldFile.getAbsolutePath());
            }
        } catch (Exception e) {
            logger.warn("Error al cargar escudo del colegio: {}", e.getMessage());
            schoolShieldImage.setVisible(false);
            shieldPlaceholder.setVisible(true);
        }
    }

    /**
     * Manejador para cuando se hace clic en el área del escudo.
     * Abre un FileChooser para seleccionar una imagen.
     */
    @FXML
    private void onShieldClick() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Seleccionar Escudo del Colegio");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Imágenes", "*.png", "*.jpg", "*.jpeg", "*.gif"),
                new FileChooser.ExtensionFilter("PNG", "*.png"),
                new FileChooser.ExtensionFilter("JPEG", "*.jpg", "*.jpeg")
        );

        Stage stage = (Stage) shieldContainer.getScene().getWindow();
        File selectedFile = fileChooser.showOpenDialog(stage);

        if (selectedFile != null) {
            saveShieldImage(selectedFile);
        }
    }

    /**
     * Guarda la imagen seleccionada como escudo del colegio.
     */
    private void saveShieldImage(File sourceFile) {
        try {
            String userHome = System.getProperty("user.home");
            File configDir = new File(userHome, ".registro-estudiantes");

            // Crear directorio si no existe
            if (!configDir.exists()) {
                configDir.mkdirs();
            }

            File destFile = new File(configDir, "school_shield.png");

            // Copiar archivo
            Files.copy(sourceFile.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

            logger.info("Escudo guardado en: {}", destFile.getAbsolutePath());

            // Recargar el escudo
            loadSchoolShield();

        } catch (Exception e) {
            logger.error("Error al guardar escudo del colegio", e);
        }
    }

    /**
     * Limpia recursos al cerrar el controlador.
     */
    public void cleanup() {
        if (clockTimer != null) {
            clockTimer.cancel();
        }
        if (autoClearTransition != null) {
            autoClearTransition.stop();
        }
    }
}
