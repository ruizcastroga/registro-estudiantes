package com.tuempresa.registro.controllers;

import com.tuempresa.registro.models.VisitorBadge;
import com.tuempresa.registro.models.VisitorLog;
import com.tuempresa.registro.services.VisitorService;
import com.tuempresa.registro.utils.SecurityManager;
import com.tuempresa.registro.utils.SessionManager;
import com.tuempresa.registro.models.AdminUser;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.stage.FileChooser;

import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

/**
 * Controlador de la vista de visitantes.
 * Gestiona el escaneo de carnés, registro de entradas/salidas,
 * administración de carnés e historial de visitas.
 */
public class VisitorController implements Initializable {

    private static final Logger logger = LoggerFactory.getLogger(VisitorController.class);
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("hh:mm:ss a");
    private static final int AUTO_CLEAR_SECONDS = 8;

    // Session bar
    @FXML private HBox sessionBar;
    @FXML private Label sessionUserLabel;
    @FXML private Label sessionRoleLabel;
    @FXML private Label sessionTimerLabel;
    @FXML private Button sessionLoginBtn;
    @FXML private Button sessionLogoutBtn;

    // Header
    @FXML private Label subtitleLabel;
    @FXML private Label insideCountLabel;
    @FXML private Label todayCountLabel;
    @FXML private Label availableCountLabel;


    // Panel lateral: dentro ahora
    @FXML private TableView<VisitorLog> insideTable;
    @FXML private TableColumn<VisitorLog, String> colInsideBadge;
    @FXML private TableColumn<VisitorLog, String> colInsideName;
    @FXML private TableColumn<VisitorLog, String> colInsideEntry;

    // Pestaña: carnés
    @FXML private TextField newBadgeCodeField;
    @FXML private TableView<VisitorBadge> badgesTable;
    @FXML private TableColumn<VisitorBadge, String> colBadgeCode;
    @FXML private TableColumn<VisitorBadge, String> colBadgeStatus;
    @FXML private TableColumn<VisitorBadge, String> colBadgeUpdated;
    @FXML private TableColumn<VisitorBadge, String> colBadgeActions;

    // Pestaña: historial
    @FXML private TextField logsSearchField;
    @FXML private TableView<VisitorLog> logsTable;
    @FXML private TableColumn<VisitorLog, String> colLogBadge;
    @FXML private TableColumn<VisitorLog, String> colLogId;
    @FXML private TableColumn<VisitorLog, String> colLogName;
    @FXML private TableColumn<VisitorLog, String> colLogJustification;
    @FXML private TableColumn<VisitorLog, String> colLogEntry;
    @FXML private TableColumn<VisitorLog, String> colLogExit;

    // Servicio y estado
    private VisitorService visitorService;
    private SecurityManager securityManager;
    private SessionManager sessionManager;

    private ObservableList<VisitorLog> insideList;
    private ObservableList<VisitorBadge> badgesList;
    private ObservableList<VisitorLog> logsList;
    private FilteredList<VisitorLog> filteredLogsList;
    private SortedList<VisitorLog> sortedLogsList;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        visitorService = new VisitorService();
        securityManager = SecurityManager.getInstance();
        sessionManager = SessionManager.getInstance();

        insideList = FXCollections.observableArrayList();
        badgesList = FXCollections.observableArrayList();
        logsList = FXCollections.observableArrayList();
        filteredLogsList = new FilteredList<>(logsList, p -> true);
        sortedLogsList = new SortedList<>(filteredLogsList);

        setupInsideTable();
        setupBadgesTable();
        setupLogsTable();
        setupLogsSearch();

        loadAllData();
        setupSessionBar();
    }

    // -----------------------------------------------------------------------
    // Configuración de tablas
    // -----------------------------------------------------------------------

    private void setupInsideTable() {
        colInsideBadge.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getBadgeCode()));
        colInsideName.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getDisplayName()));
        colInsideEntry.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getEntryTime() != null ? c.getValue().getEntryTime().format(TIME_FMT) : ""));
        insideTable.setItems(insideList);
    }

    private void setupBadgesTable() {
        colBadgeCode.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getCode()));
        colBadgeStatus.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getStatusDisplay()));
        colBadgeUpdated.setCellValueFactory(c -> {
            VisitorBadge b = c.getValue();
            String val = b.getUpdatedAt() != null ?
                    b.getUpdatedAt().format(DateTimeFormatter.ofPattern("dd/MM hh:mm a")) : "";
            return new SimpleStringProperty(val);
        });

        // Columna de acciones: botones Perdido / Disponible / Eliminar
        colBadgeActions.setCellFactory(col -> new TableCell<>() {
            private final Button btnLost = new Button("Perdido");
            private final Button btnAvailable = new Button("Disponible");
            private final Button btnDelete = new Button("Eliminar");
            private final HBox box = new HBox(5, btnLost, btnAvailable, btnDelete);

            {
                btnLost.getStyleClass().add("danger-button");
                btnAvailable.getStyleClass().add("small-button");
                btnDelete.getStyleClass().add("small-button");
                box.setPadding(new Insets(2));

                btnLost.setOnAction(e -> {
                    if (!requireSession()) return;
                    if (!sessionManager.canModifyData()) {
                        showAlert(Alert.AlertType.WARNING, "Sin Permisos",
                                "No tiene permisos para modificar carnés.");
                        return;
                    }
                    VisitorBadge badge = getTableView().getItems().get(getIndex());
                    visitorService.markBadgeLost(badge.getId());
                    loadAllData();
                });

                btnAvailable.setOnAction(e -> {
                    if (!requireSession()) return;
                    if (!sessionManager.canModifyData()) {
                        showAlert(Alert.AlertType.WARNING, "Sin Permisos",
                                "No tiene permisos para modificar carnés.");
                        return;
                    }
                    VisitorBadge badge = getTableView().getItems().get(getIndex());
                    visitorService.markBadgeAvailable(badge.getId());
                    loadAllData();
                });

                btnDelete.setOnAction(e -> {
                    if (!requireSession()) return;
                    if (!sessionManager.canModifyData()) {
                        showAlert(Alert.AlertType.WARNING, "Sin Permisos",
                                "No tiene permisos para eliminar carnés.");
                        return;
                    }
                    VisitorBadge badge = getTableView().getItems().get(getIndex());
                    try {
                        visitorService.deleteBadge(badge.getId());
                        loadAllData();
                    } catch (SQLException ex) {
                        showError("No se pudo eliminar el carné: " + ex.getMessage());
                    }
                });
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : box);
            }
        });

        badgesTable.setItems(badgesList);
    }

    private void setupLogsTable() {
        colLogBadge.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getBadgeCode()));
        colLogId.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getIdNumber()));
        colLogName.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getFullName()));
        colLogJustification.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getJustification() != null ? c.getValue().getJustification() : ""));
        colLogEntry.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getFormattedEntryTime()));
        colLogExit.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getFormattedExitTime()));
        sortedLogsList.comparatorProperty().bind(logsTable.comparatorProperty());
        logsTable.setItems(sortedLogsList);
    }

    private void setupLogsSearch() {
        logsSearchField.textProperty().addListener((obs, oldVal, newVal) -> {
            String filter = newVal == null ? "" : newVal.trim().toLowerCase();
            if (filter.isEmpty()) {
                filteredLogsList.setPredicate(p -> true);
            } else {
                filteredLogsList.setPredicate(log -> {
                    if (log.getBadgeCode() != null && log.getBadgeCode().toLowerCase().contains(filter)) return true;
                    if (log.getIdNumber() != null && log.getIdNumber().toLowerCase().contains(filter)) return true;
                    if (log.getFullName() != null && log.getFullName().toLowerCase().contains(filter)) return true;
                    if (log.getJustification() != null && log.getJustification().toLowerCase().contains(filter)) return true;
                    return false;
                });
            }
        });
    }


    // -----------------------------------------------------------------------
    // Gestión de carnés
    // -----------------------------------------------------------------------

    @FXML
    private void onAddBadge() {
        String code = newBadgeCodeField.getText().trim().toUpperCase();
        if (code.isEmpty()) {
            showError("Ingrese un código para el carné.");
            return;
        }
        if (!requireSession()) return;
        if (!sessionManager.canModifyData()) {
            showAlert(Alert.AlertType.WARNING, "Sin Permisos",
                    "No tiene permisos para agregar carnés.");
            return;
        }
        try {
            visitorService.createBadge(code);
            newBadgeCodeField.clear();
            loadBadgesTable();
            updateStats();
        } catch (SQLException e) {
            showError("Error al crear carné: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            showError(e.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // Importación masiva de carnés (CSV)
    // -----------------------------------------------------------------------

    @FXML
    private void onImportBadgesCsv() {
        if (!requireSession()) return;
        if (!sessionManager.canModifyData()) {
            showAlert(Alert.AlertType.WARNING, "Sin Permisos",
                    "No tiene permisos para importar carnés.");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Seleccionar archivo CSV de carnés");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Archivos CSV", "*.csv"),
                new FileChooser.ExtensionFilter("Todos los archivos", "*.*")
        );

        File file = fileChooser.showOpenDialog(badgesTable.getScene().getWindow());
        if (file == null) return;

        int imported = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {

            String line;
            boolean firstLine = true;
            int row = 0;
            while ((line = reader.readLine()) != null) {
                row++;
                line = line.trim();
                if (line.isEmpty()) continue;

                // Saltar encabezado (si contiene "codigo" o "code")
                if (firstLine) {
                    firstLine = false;
                    String lower = line.toLowerCase();
                    if (lower.contains("codigo") || lower.contains("code") || lower.contains("carne")) continue;
                }

                // Tomar primer campo (por si hay más columnas)
                String code = line.split("[,;\\t]")[0].trim().replaceAll("^\"|\"$", "").toUpperCase();
                if (code.isEmpty()) continue;

                try {
                    visitorService.createBadge(code);
                    imported++;
                } catch (IllegalArgumentException e) {
                    skipped++; // ya existe
                } catch (SQLException e) {
                    errors.add("Fila " + row + " (" + code + "): " + e.getMessage());
                }
            }
        } catch (IOException e) {
            showError("Error al leer el archivo: " + e.getMessage());
            return;
        }

        StringBuilder summary = new StringBuilder();
        summary.append("Importación completada:\n");
        summary.append("- Importados: ").append(imported).append("\n");
        summary.append("- Omitidos (ya existen): ").append(skipped).append("\n");
        summary.append("- Errores: ").append(errors.size());
        if (!errors.isEmpty()) {
            summary.append("\n\nErrores:\n");
            int max = Math.min(5, errors.size());
            for (int i = 0; i < max; i++) summary.append("- ").append(errors.get(i)).append("\n");
            if (errors.size() > 5) summary.append("... y ").append(errors.size() - 5).append(" más");
        }

        Alert alert = new Alert(errors.isEmpty() ? Alert.AlertType.INFORMATION : Alert.AlertType.WARNING);
        alert.setTitle("Resultado de Importación");
        alert.setHeaderText("Carnés importados");
        alert.setContentText(summary.toString());
        alert.showAndWait();

        loadBadgesTable();
        updateStats();
    }

    @FXML
    private void onGenerateBadgesTemplate() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Guardar plantilla CSV de carnés");
        fileChooser.setInitialFileName("plantilla_carnes_visitantes.csv");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Archivos CSV", "*.csv")
        );

        File file = fileChooser.showSaveDialog(badgesTable.getScene().getWindow());
        if (file == null) return;

        try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(
                new FileOutputStream(file), StandardCharsets.UTF_8))) {
            writer.print('\ufeff'); // BOM para Excel
            writer.println("codigo");
            writer.println("V-1");
            writer.println("V-2");
            writer.println("V-3");
            writer.println("VISITANTE-01");
            writer.println("VISITANTE-02");
            showInfo("Plantilla guardada en:\n" + file.getAbsolutePath());
        } catch (IOException e) {
            showError("No se pudo guardar la plantilla: " + e.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // Historial: purga
    // -----------------------------------------------------------------------

    @FXML
    private void onPurgeLogs() {
        // Mostrar popup de selección de rango de fechas
        Dialog<LocalDate> dialog = new Dialog<>();
        dialog.setTitle("Borrar Historial");
        dialog.setHeaderText("Seleccione hasta qué fecha borrar los registros");

        DatePicker datePicker = new DatePicker(LocalDate.now());
        datePicker.setPromptText("Borrar registros anteriores a...");

        VBox content = new VBox(10,
                new Label("Borrar todos los registros anteriores a:"),
                datePicker,
                new Label("Esta acción no se puede deshacer.")
        );
        content.setPadding(new Insets(15));
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        Platform.runLater(datePicker::requestFocus);

        dialog.setResultConverter(btn -> btn == ButtonType.OK ? datePicker.getValue() : null);

        Optional<LocalDate> result = dialog.showAndWait();
        if (result.isEmpty() || result.get() == null) return;

        LocalDate date = result.get();

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirmar borrado");
        confirm.setHeaderText("¿Borrar todos los registros anteriores al " + date + "?");
        confirm.setContentText("Esta acción no se puede deshacer.");
        Optional<ButtonType> res = confirm.showAndWait();
        if (res.isEmpty() || res.get() != ButtonType.OK) return;

        if (!requireSession()) return;
        if (!sessionManager.canModifyData()) {
            showAlert(Alert.AlertType.WARNING, "Sin Permisos",
                    "No tiene permisos para borrar registros históricos.");
            return;
        }

        int deleted = visitorService.purgeLogsBefore(date.atStartOfDay());
        showInfo("Se eliminaron " + deleted + " registros.");
        loadAllData();
    }

    @FXML
    private void onRefreshLogs() {
        loadLogsTable();
    }

    @FXML
    private void onExportLogs() {
        // --- Diálogo de selección de rango ---
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Exportar Historial");
        dialog.setHeaderText("Seleccione el rango de fechas a exportar");

        DatePicker fromPicker = new DatePicker(LocalDate.now().withDayOfMonth(1));
        DatePicker toPicker   = new DatePicker(LocalDate.now());
        fromPicker.setPromptText("Desde");
        toPicker.setPromptText("Hasta");

        VBox content = new VBox(10,
                new Label("Desde:"), fromPicker,
                new Label("Hasta:"),  toPicker
        );
        content.setPadding(new Insets(15));
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        Platform.runLater(fromPicker::requestFocus);

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) return;

        LocalDate from = fromPicker.getValue();
        LocalDate to   = toPicker.getValue();

        if (from == null || to == null) {
            showError("Debe seleccionar ambas fechas.");
            return;
        }
        if (from.isAfter(to)) {
            showError("La fecha de inicio debe ser anterior o igual a la fecha de fin.");
            return;
        }

        // --- Obtener registros ---
        List<VisitorLog> logs = visitorService.getLogsByDateRange(
                from.atStartOfDay(), to.atTime(23, 59, 59));

        if (logs.isEmpty()) {
            showInfo("No hay registros en el rango seleccionado.");
            return;
        }

        // --- Elegir destino ---
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Guardar exportación");
        fileChooser.setInitialFileName(
                "visitas_" + from + "_" + to + ".csv");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Archivo CSV", "*.csv"),
                new FileChooser.ExtensionFilter("Todos los archivos", "*.*")
        );

        File file = fileChooser.showSaveDialog(logsTable.getScene().getWindow());
        if (file == null) return;

        // --- Escribir CSV ---
        DateTimeFormatter csvFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy hh:mm a");
        try (PrintWriter pw = new PrintWriter(
                new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
            pw.print('\ufeff'); // BOM para Excel
            pw.println("Carné,Cédula,Nombre,Justificación,Entrada,Salida");
            for (VisitorLog log : logs) {
                pw.printf("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"%n",
                        esc(log.getBadgeCode()),
                        esc(log.getIdNumber()),
                        esc(log.getFullName()),
                        esc(log.getJustification()),
                        log.getEntryTime() != null ? log.getEntryTime().format(csvFmt) : "",
                        log.getExitTime()  != null ? log.getExitTime().format(csvFmt)  : "Dentro"
                );
            }
        } catch (IOException e) {
            showError("Error al guardar el archivo: " + e.getMessage());
            return;
        }

        showInfo("Exportación completada.\n" + logs.size() + " registros guardados en:\n" + file.getAbsolutePath());
    }

    /** Escapa comillas dobles para CSV. */
    private static String esc(String s) {
        return s == null ? "" : s.replace("\"", "\"\"");
    }

    // -----------------------------------------------------------------------
    // Sesión
    // -----------------------------------------------------------------------

    /**
     * Configura la barra de sesión vinculando las propiedades del SessionManager.
     */
    private void setupSessionBar() {
        sessionBar.setVisible(true);
        sessionBar.setManaged(true);

        // Bind labels to session properties
        sessionUserLabel.textProperty().bind(sessionManager.currentUsernameProperty());
        sessionRoleLabel.textProperty().bind(sessionManager.currentRoleProperty());
        sessionTimerLabel.textProperty().bind(sessionManager.remainingTimeFormattedProperty());

        // Show/hide login/logout buttons based on session state
        sessionManager.sessionActiveProperty().addListener((obs, wasActive, isActive) -> {
            sessionLoginBtn.setVisible(!isActive);
            sessionLoginBtn.setManaged(!isActive);
            sessionLogoutBtn.setVisible(isActive);
            sessionLogoutBtn.setManaged(isActive);
        });

        // Timer warning when < 120 seconds
        sessionManager.remainingSecondsProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal.intValue() > 0 && newVal.intValue() < 120) {
                sessionTimerLabel.setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
            } else {
                sessionTimerLabel.setStyle("");
            }
        });

        // Set initial button state
        boolean active = sessionManager.isSessionActive();
        sessionLoginBtn.setVisible(!active);
        sessionLoginBtn.setManaged(!active);
        sessionLogoutBtn.setVisible(active);
        sessionLogoutBtn.setManaged(active);
    }

    /**
     * Muestra diálogo de login con usuario y contraseña.
     * Loop en credenciales incorrectas hasta que el usuario cancele.
     */
    @FXML
    private void onLogin() {
        SecurityManager secMgr = SecurityManager.getInstance();

        while (true) {
            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.setTitle("Iniciar Sesión");
            dialog.setHeaderText("Ingrese sus credenciales");

            ButtonType loginButtonType = new ButtonType("Iniciar Sesión", ButtonBar.ButtonData.OK_DONE);
            dialog.getDialogPane().getButtonTypes().addAll(loginButtonType, ButtonType.CANCEL);

            VBox content = new VBox(10);
            content.setPadding(new Insets(20));

            TextField usernameField = new TextField();
            usernameField.setPromptText("Usuario");
            PasswordField passwordField = new PasswordField();
            passwordField.setPromptText("Contraseña");
            Label errorLabel = new Label();
            errorLabel.setStyle("-fx-text-fill: red;");

            content.getChildren().addAll(
                    new Label("Usuario:"), usernameField,
                    new Label("Contraseña:"), passwordField,
                    errorLabel
            );
            dialog.getDialogPane().setContent(content);
            Platform.runLater(usernameField::requestFocus);

            Optional<ButtonType> result = dialog.showAndWait();

            if (result.isEmpty() || result.get() == ButtonType.CANCEL) {
                return;
            }

            String username = usernameField.getText().trim();
            String password = passwordField.getText();

            Optional<AdminUser> authResult = secMgr.authenticate(username, password);

            if (authResult.isPresent()) {
                AdminUser adminUser = authResult.get();
                int timeoutMinutes = sessionManager.getTimeoutMinutes();
                sessionManager.startSession(adminUser, timeoutMinutes);

                showAlert(Alert.AlertType.INFORMATION, "Sesión Iniciada",
                        "Usuario " + username + " logueado correctamente. " +
                                "Tu sesión estará activa por " + timeoutMinutes + " minutos. " +
                                "Si terminas antes, recuerda cerrar la sesión para cuidar los datos.");
                return;
            } else {
                showAlert(Alert.AlertType.ERROR, "Error de Autenticación",
                        "Usuario o contraseña incorrectos. Intente de nuevo.");
            }
        }
    }

    /**
     * Cierra la sesión actual.
     */
    @FXML
    private void onLogout() {
        sessionManager.endSession();
    }

    /**
     * Verifica que haya una sesión activa. Muestra alerta si no.
     *
     * @return true si hay sesión activa
     */
    private boolean requireSession() {
        if (!sessionManager.isSessionActive()) {
            showAlert(Alert.AlertType.WARNING, "Sesión Requerida",
                    "Debe iniciar sesión para realizar esta operación.");
            return false;
        }
        return true;
    }

    // -----------------------------------------------------------------------
    // Navegación
    // -----------------------------------------------------------------------

    @FXML
    private void onManageStudents() {
        navigateTo("/fxml/student-crud.fxml", "Gestión de Estudiantes");
    }

    @FXML
    private void onManageStaff() {
        navigateTo("/fxml/staff-admin.fxml", "Administración de Personal");
    }

    @FXML
    private void onManageSettings() {
        navigateTo("/fxml/settings-view.fxml", "Ajustes del Sistema");
    }

    @FXML
    private void onManageCarne() {
        navigateTo("/fxml/carne-view.fxml", "Creador de Carné");
    }

    private void navigateTo(String fxmlPath, String title) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent root = loader.load();
            Stage stage = (Stage) badgesTable.getScene().getWindow();
            Scene scene = new Scene(root);
            scene.getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());
            stage.setMaximized(false);
            stage.setScene(scene);
            stage.setTitle(title);
            Platform.runLater(() -> stage.setMaximized(true));
        } catch (IOException e) {
            logger.error("Error al navegar a " + fxmlPath, e);
        }
    }

    @FXML
    private void onBack() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/scanner-view.fxml"));
            Parent root = loader.load();
            Stage stage = (Stage) badgesTable.getScene().getWindow();
            Scene scene = new Scene(root);
            scene.getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());
            stage.setMaximized(false);
            stage.setScene(scene);
            stage.setTitle("Sistema de Registro");
            Platform.runLater(() -> stage.setMaximized(true));
        } catch (IOException e) {
            logger.error("Error al volver al scanner", e);
        }
    }

    // -----------------------------------------------------------------------
    // Helpers de UI
    // -----------------------------------------------------------------------

    private void showError(String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }

    private void showInfo(String msg) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Información");
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    // -----------------------------------------------------------------------
    // Carga de datos
    // -----------------------------------------------------------------------

    private void loadAllData() {
        updateStats();
        loadInsideList();
        loadBadgesTable();
        loadLogsTable();
    }

    private void updateStats() {
        insideCountLabel.setText(String.valueOf(visitorService.countBadgesInUse()));
        todayCountLabel.setText(String.valueOf(visitorService.countTodayVisits()));
        availableCountLabel.setText(String.valueOf(visitorService.countAvailableBadges()));
    }

    private void loadInsideList() {
        insideList.setAll(visitorService.getCurrentlyInside());
    }

    private void loadBadgesTable() {
        badgesList.setAll(visitorService.getAllBadges());
    }

    private void loadLogsTable() {
        logsList.setAll(visitorService.getAllLogs());
    }
}
