package com.tuempresa.registro.controllers;

import com.tuempresa.registro.models.EntryLog;
import com.tuempresa.registro.models.Guardian;
import com.tuempresa.registro.models.Student;
import com.tuempresa.registro.services.StudentService;
import com.tuempresa.registro.utils.LicenseManager;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
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
import java.util.ResourceBundle;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Controlador de la vista principal del scanner.
 * Gestiona el escaneo de códigos de barras y muestra los resultados.
 */
public class ScannerController implements Initializable {

    private static final Logger logger = LoggerFactory.getLogger(ScannerController.class);

    // Formateador de fecha/hora
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("EEEE, dd 'de' MMMM 'de' yyyy - HH:mm:ss");

    // Tiempo en segundos para limpiar la pantalla automáticamente
    private static final int AUTO_CLEAR_SECONDS = 10;

    // Máximo de elementos en el historial
    private static final int MAX_HISTORY_ITEMS = 5;

    // Componentes FXML
    @FXML private Label schoolNameLabel;
    @FXML private Label dateTimeLabel;
    @FXML private Label todayScansLabel;
    @FXML private TextField barcodeInput;
    @FXML private Label instructionLabel;
    @FXML private VBox resultPanel;
    @FXML private Label studentNameLabel;
    @FXML private Label studentGradeLabel;
    @FXML private Label statusLabel;
    @FXML private Label activityStatusLabel;
    @FXML private VBox guardiansPanel;
    @FXML private VBox guardiansList;
    @FXML private VBox notFoundPanel;
    @FXML private Label notFoundLabel;
    @FXML private Label scannedCodeLabel;
    @FXML private ListView<String> historyListView;
    @FXML private Label statusIndicator;
    @FXML private Label connectionStatusLabel;
    @FXML private Label licenseInfoLabel;
    @FXML private StackPane shieldContainer;
    @FXML private ImageView schoolShieldImage;
    @FXML private Label shieldPlaceholder;

    // Servicios
    private StudentService studentService;
    private LicenseManager licenseManager;

    // Lista observable para el historial
    private ObservableList<String> historyItems;

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

        // Enfocar el campo de entrada al iniciar
        Platform.runLater(() -> barcodeInput.requestFocus());

        logger.info("ScannerController inicializado correctamente");
    }

    /**
     * Configura el campo de entrada del código de barras.
     */
    private void setupBarcodeInput() {
        // El scanner funciona como teclado, así que capturamos el input
        // y procesamos cuando se presiona Enter

        // Mantener el foco en el campo de entrada
        barcodeInput.focusedProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal) {
                // Si pierde el foco, recuperarlo después de un pequeño delay
                Platform.runLater(() -> {
                    if (!barcodeInput.isFocused()) {
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
     */
    @FXML
    private void onBarcodeScanned() {
        String barcode = barcodeInput.getText().trim();

        if (barcode.isEmpty()) {
            return;
        }

        logger.info("Código escaneado: {}", barcode);
        lastScannedCode = barcode;

        // Procesar el escaneo
        StudentService.ScanResult result = studentService.processScan(barcode, "Guardia");

        // Mostrar resultado
        displayScanResult(result, barcode);

        // Limpiar el campo de entrada
        barcodeInput.clear();

        // Actualizar estadísticas
        updateTodayScansCount();

        // Agregar al historial
        addToHistory(result, barcode);

        // Iniciar auto-limpieza
        autoClearTransition.playFromStart();

        // Mantener el foco
        barcodeInput.requestFocus();
    }

    /**
     * Muestra el resultado del escaneo en la interfaz.
     * Jerarquía de información:
     * 1. Estado de acceso (PUEDE SALIR / REQUIERE ACOMPAÑANTE / NO PUEDE ENTRAR)
     * 2. Estado de actividad (ACTIVO / INACTIVO / SUSPENDIDO) - siempre visible
     * 3. Lista de tutores legales - solo si requiere acompañante y está activo
     */
    private void displayScanResult(StudentService.ScanResult result, String barcode) {
        // Ocultar instrucciones
        instructionLabel.setVisible(false);

        if (result.isFound()) {
            Student student = result.getStudent();

            // Mostrar panel de resultado
            resultPanel.setVisible(true);
            resultPanel.setManaged(true);
            notFoundPanel.setVisible(false);
            notFoundPanel.setManaged(false);

            // Mostrar información del estudiante
            studentNameLabel.setText(student.getFullName());
            studentGradeLabel.setText(student.getGrade() != null ? student.getGrade() : "");

            // Aplicar estilos según el estado
            resultPanel.getStyleClass().removeAll("can-exit", "requires-guardian", "inactive", "suspended");
            statusLabel.getStyleClass().removeAll("status-ok", "status-warning", "status-inactive", "status-suspended");
            activityStatusLabel.getStyleClass().removeAll("status-active", "status-inactive", "status-suspended");

            // Siempre mostrar el estado de actividad
            activityStatusLabel.setVisible(true);
            activityStatusLabel.setManaged(true);

            // Verificar estado de actividad (inactivo/suspendido/activo)
            if (result.isSuspended()) {
                // Estudiante suspendido - medida de seguridad
                resultPanel.getStyleClass().add("suspended");
                statusLabel.setText("NO PUEDE ENTRAR");
                statusLabel.getStyleClass().add("status-suspended");
                activityStatusLabel.setText("SUSPENDIDO");
                activityStatusLabel.getStyleClass().add("status-suspended");
                // Ocultar tutores
                guardiansPanel.setVisible(false);
                guardiansPanel.setManaged(false);

            } else if (result.isInactive()) {
                // Estudiante inactivo - medida de seguridad (ya no pertenece a la institución)
                resultPanel.getStyleClass().add("inactive");
                statusLabel.setText("NO PUEDE ENTRAR");
                statusLabel.getStyleClass().add("status-inactive");
                activityStatusLabel.setText("INACTIVO");
                activityStatusLabel.getStyleClass().add("status-inactive");
                // Ocultar tutores
                guardiansPanel.setVisible(false);
                guardiansPanel.setManaged(false);

            } else if (result.requiresGuardian()) {
                // Requiere acompañante (activo)
                resultPanel.getStyleClass().add("requires-guardian");
                statusLabel.setText("REQUIERE ACOMPAÑANTE");
                statusLabel.getStyleClass().add("status-warning");
                activityStatusLabel.setText("ACTIVO");
                activityStatusLabel.getStyleClass().add("status-active");
                // Mostrar tutores legales
                displayGuardians(student.getGuardians());

            } else {
                // Puede salir solo (activo)
                resultPanel.getStyleClass().add("can-exit");
                statusLabel.setText("PUEDE SALIR");
                statusLabel.getStyleClass().add("status-ok");
                activityStatusLabel.setText("ACTIVO");
                activityStatusLabel.getStyleClass().add("status-active");
                // Ocultar guardianes
                guardiansPanel.setVisible(false);
                guardiansPanel.setManaged(false);
            }

        } else {
            // Mostrar panel de no encontrado
            resultPanel.setVisible(false);
            resultPanel.setManaged(false);
            notFoundPanel.setVisible(true);
            notFoundPanel.setManaged(true);
            activityStatusLabel.setVisible(false);
            activityStatusLabel.setManaged(false);

            scannedCodeLabel.setText("Código: " + barcode);
        }
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
    private void addToHistory(StudentService.ScanResult result, String barcode) {
        String historyEntry;
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));

        if (result.isFound()) {
            Student student = result.getStudent();
            String status = result.canExit() ? "[OK]" : "[REQ]";
            historyEntry = String.format("%s %s %s", time, status, student.getFullName());
        } else {
            historyEntry = String.format("%s [???] %s", time, barcode);
        }

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
        instructionLabel.setVisible(true);
        guardiansPanel.setVisible(false);
        guardiansPanel.setManaged(false);
        activityStatusLabel.setVisible(false);
        activityStatusLabel.setManaged(false);

        // Limpiar estilos
        resultPanel.getStyleClass().removeAll("can-exit", "requires-guardian", "inactive", "suspended");
    }

    /**
     * Actualiza el contador de escaneos del día.
     */
    private void updateTodayScansCount() {
        int count = studentService.countTodayScans();
        todayScansLabel.setText(String.valueOf(count));
    }

    /**
     * Carga el historial reciente.
     */
    private void loadRecentHistory() {
        List<EntryLog> recentLogs = studentService.getRecentScans(MAX_HISTORY_ITEMS);

        historyItems.clear();

        for (EntryLog log : recentLogs) {
            String status = log.getStudent() != null && !log.getStudent().isRequiresGuardian() ?
                    "[OK]" : "[REQ]";
            String name = log.getStudent() != null ?
                    log.getStudent().getFullName() : "ID: " + log.getStudentId();
            String entry = String.format("%s %s %s", log.getTimeOnly(), status, name);
            historyItems.add(entry);
        }
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

            stage.setScene(scene);
            stage.setTitle("Gestión de Estudiantes");

        } catch (IOException e) {
            logger.error("Error al abrir gestión de estudiantes", e);
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

            stage.setScene(scene);
            stage.setTitle("Registrar Nuevo Estudiante");

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
