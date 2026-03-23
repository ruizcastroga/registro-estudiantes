package com.tuempresa.registro.controllers;

import com.tuempresa.registro.models.VisitorBadge;
import com.tuempresa.registro.models.VisitorLog;
import com.tuempresa.registro.services.VisitorService;
import com.tuempresa.registro.utils.SecurityManager;
import javafx.animation.PauseTransition;
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
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.stage.FileChooser;

import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
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

    // Header
    @FXML private Label subtitleLabel;
    @FXML private Label insideCountLabel;
    @FXML private Label todayCountLabel;
    @FXML private Label availableCountLabel;

    // Scanner
    @FXML private TextField badgeInput;
    @FXML private Label instructionLabel;

    // Paneles de resultado
    @FXML private VBox entryFormPanel;
    @FXML private Label entryBadgeLabel;
    @FXML private TextField idNumberField;
    @FXML private TextField firstNameField;
    @FXML private TextField lastNameField;
    @FXML private TextField justificationField;
    @FXML private Label entryValidationLabel;

    @FXML private VBox exitResultPanel;
    @FXML private Label exitStatusLabel;
    @FXML private Label exitVisitorLabel;
    @FXML private Label exitBadgeLabel;
    @FXML private Label exitTimeLabel;

    @FXML private VBox notFoundPanel;
    @FXML private Label notFoundCodeLabel;

    @FXML private VBox lostPanel;

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
    private VisitorBadge pendingBadge; // carné que espera que el guardia ingrese la cédula
    private PauseTransition autoClear;

    private ObservableList<VisitorLog> insideList;
    private ObservableList<VisitorBadge> badgesList;
    private ObservableList<VisitorLog> logsList;
    private FilteredList<VisitorLog> filteredLogsList;
    private SortedList<VisitorLog> sortedLogsList;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        visitorService = new VisitorService();
        securityManager = SecurityManager.getInstance();

        insideList = FXCollections.observableArrayList();
        badgesList = FXCollections.observableArrayList();
        logsList = FXCollections.observableArrayList();
        filteredLogsList = new FilteredList<>(logsList, p -> true);
        sortedLogsList = new SortedList<>(filteredLogsList);

        setupInsideTable();
        setupBadgesTable();
        setupLogsTable();
        setupLogsSearch();

        autoClear = new PauseTransition(Duration.seconds(AUTO_CLEAR_SECONDS));
        autoClear.setOnFinished(e -> clearResultPanels());

        loadAllData();

        Platform.runLater(() -> badgeInput.requestFocus());
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
                    VisitorBadge badge = getTableView().getItems().get(getIndex());
                    if (confirmAdminAction("marcar el carné '" + badge.getCode() + "' como perdido")) {
                        visitorService.markBadgeLost(badge.getId());
                        loadAllData();
                    }
                });

                btnAvailable.setOnAction(e -> {
                    VisitorBadge badge = getTableView().getItems().get(getIndex());
                    if (confirmAdminAction("restaurar el carné '" + badge.getCode() + "' a disponible")) {
                        visitorService.markBadgeAvailable(badge.getId());
                        loadAllData();
                    }
                });

                btnDelete.setOnAction(e -> {
                    VisitorBadge badge = getTableView().getItems().get(getIndex());
                    if (confirmAdminAction("eliminar el carné '" + badge.getCode() + "'")) {
                        try {
                            visitorService.deleteBadge(badge.getId());
                            loadAllData();
                        } catch (SQLException ex) {
                            showError("No se pudo eliminar el carné: " + ex.getMessage());
                        }
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
    // Escaneo del carné
    // -----------------------------------------------------------------------

    @FXML
    private void onBadgeScanned() {
        String code = badgeInput.getText().trim().toUpperCase();
        badgeInput.clear();

        if (code.isEmpty()) {
            badgeInput.requestFocus();
            return;
        }

        logger.info("Carné escaneado: {}", code);
        VisitorService.ScanResult result = visitorService.processBadgeScan(code);

        clearResultPanels();

        switch (result.getStatus()) {
            case BADGE_AVAILABLE -> showEntryForm(result.getBadge());
            case EXIT_REGISTERED -> showExitResult(result.getLog(), result.getBadge().getCode());
            case BADGE_LOST -> showPanel(lostPanel);
            case BADGE_NOT_FOUND -> {
                notFoundCodeLabel.setText("Código: " + code);
                showPanel(notFoundPanel);
            }
            case ERROR -> showError(result.getMessage());
        }

        updateStats();
        loadInsideList();
    }

    // -----------------------------------------------------------------------
    // Registro de entrada
    // -----------------------------------------------------------------------

    private void showEntryForm(VisitorBadge badge) {
        pendingBadge = badge;
        entryBadgeLabel.setText("Carné: " + badge.getCode());
        idNumberField.clear();
        firstNameField.clear();
        lastNameField.clear();
        justificationField.clear();
        hideValidationError();
        showPanel(entryFormPanel);
        Platform.runLater(() -> idNumberField.requestFocus());
        // No iniciar auto-clear: hay un formulario pendiente
        autoClear.stop();
    }

    @FXML
    private void onConfirmEntry() {
        if (pendingBadge == null) return;

        String idNumber = idNumberField.getText().trim();
        if (idNumber.isEmpty()) {
            showValidationError("La cédula es obligatoria.");
            idNumberField.requestFocus();
            return;
        }

        try {
            VisitorLog log = visitorService.registerEntry(
                    pendingBadge,
                    idNumber,
                    firstNameField.getText(),
                    lastNameField.getText(),
                    justificationField.getText()
            );

            String registeredBadgeCode = pendingBadge.getCode();
            clearResultPanels();

            exitStatusLabel.setText("ENTRADA REGISTRADA");
            exitVisitorLabel.setText(log.getDisplayName());
            exitBadgeLabel.setText("Carné: " + registeredBadgeCode);
            exitTimeLabel.setText("Entrada: " + LocalDateTime.now().format(TIME_FMT));
            exitResultPanel.getStyleClass().removeAll("can-exit", "requires-guardian", "inactive");
            exitResultPanel.getStyleClass().add("can-exit");
            showPanel(exitResultPanel);

            pendingBadge = null;
            updateStats();
            loadInsideList();
            loadBadgesTable();
            loadLogsTable();
            autoClear.playFromStart();
            badgeInput.requestFocus();

        } catch (SQLException e) {
            logger.error("Error al registrar entrada", e);
            showValidationError("Error al guardar: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            showValidationError(e.getMessage());
        }
    }

    @FXML
    private void onCancelEntry() {
        pendingBadge = null;
        clearResultPanels();
        badgeInput.requestFocus();
    }

    // -----------------------------------------------------------------------
    // Resultado de salida
    // -----------------------------------------------------------------------

    private void showExitResult(VisitorLog log, String badgeCode) {
        exitStatusLabel.setText("SALIDA REGISTRADA");
        exitVisitorLabel.setText(log.getDisplayName());
        exitBadgeLabel.setText("Carné: " + badgeCode);
        exitTimeLabel.setText("Salida: " + LocalDateTime.now().format(TIME_FMT));
        exitResultPanel.getStyleClass().removeAll("can-exit", "requires-guardian", "inactive");
        exitResultPanel.getStyleClass().add("can-exit");
        showPanel(exitResultPanel);
        autoClear.playFromStart();
        badgeInput.requestFocus();
        loadInsideList();
        loadBadgesTable();
        loadLogsTable();
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
        if (!confirmAdminAction("agregar el carné '" + code + "'")) {
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
        if (!confirmAdminAction("importar carnés desde CSV")) return;

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

        if (!confirmAdminAction("borrar registros históricos")) return;

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
    // Navegación
    // -----------------------------------------------------------------------

    @FXML
    private void onBack() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/scanner-view.fxml"));
            Parent root = loader.load();
            Stage stage = (Stage) badgeInput.getScene().getWindow();
            Scene scene = new Scene(root);
            scene.getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());
            stage.setScene(scene);
            stage.setTitle("Sistema de Registro");
            stage.setMaximized(true);
        } catch (IOException e) {
            logger.error("Error al volver al scanner", e);
        }
    }

    // -----------------------------------------------------------------------
    // Helpers de UI
    // -----------------------------------------------------------------------

    private void showPanel(VBox panel) {
        instructionLabel.setVisible(false);
        instructionLabel.setManaged(false);
        panel.setVisible(true);
        panel.setManaged(true);
    }

    private void clearResultPanels() {
        for (VBox panel : List.of(entryFormPanel, exitResultPanel, notFoundPanel, lostPanel)) {
            panel.setVisible(false);
            panel.setManaged(false);
        }
        instructionLabel.setVisible(true);
        instructionLabel.setManaged(true);
        pendingBadge = null;
        autoClear.stop();
    }

    private void showValidationError(String msg) {
        entryValidationLabel.setText(msg);
        entryValidationLabel.setVisible(true);
        entryValidationLabel.setManaged(true);
    }

    private void hideValidationError() {
        entryValidationLabel.setVisible(false);
        entryValidationLabel.setManaged(false);
    }

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

    /**
     * Muestra un diálogo de verificación de contraseña admin.
     * Retorna true si la contraseña es correcta.
     */
    private boolean confirmAdminAction(String action) {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Verificación de Administrador");
        dialog.setHeaderText("Para " + action + "\nIngrese la contraseña de administrador:");

        PasswordField passField = new PasswordField();
        passField.setPromptText("Contraseña");
        VBox content = new VBox(8, new Label("Contraseña:"), passField);
        content.setPadding(new Insets(10));
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        Platform.runLater(passField::requestFocus);

        dialog.setResultConverter(btn -> btn == ButtonType.OK ? passField.getText() : null);

        Optional<String> result = dialog.showAndWait();
        if (result.isEmpty() || result.get() == null) return false;

        if (!securityManager.verifyPassword(result.get())) {
            showError("Contraseña incorrecta.");
            return false;
        }
        return true;
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
