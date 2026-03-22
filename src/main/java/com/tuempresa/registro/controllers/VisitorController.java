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

import java.io.IOException;
import java.net.URL;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
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
    @FXML private DatePicker purgeDatePicker;
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

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        visitorService = new VisitorService();
        securityManager = SecurityManager.getInstance();

        insideList = FXCollections.observableArrayList();
        badgesList = FXCollections.observableArrayList();
        logsList = FXCollections.observableArrayList();

        setupInsideTable();
        setupBadgesTable();
        setupLogsTable();

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
                    b.getUpdatedAt().format(DateTimeFormatter.ofPattern("dd/MM HH:mm")) : "";
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
        logsTable.setItems(logsList);
    }

    // -----------------------------------------------------------------------
    // Escaneo del carné
    // -----------------------------------------------------------------------

    @FXML
    private void onBadgeScanned() {
        String code = badgeInput.getText().trim();
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

            clearResultPanels();

            // Confirmar visualmente la entrada
            Label confirmLabel = new Label("ENTRADA REGISTRADA");
            confirmLabel.getStyleClass().addAll("status-label", "status-ok");
            Label nameLabel = new Label(log.getDisplayName());
            nameLabel.getStyleClass().add("student-name");
            Label badgeLbl = new Label("Carné: " + pendingBadge.getCode());
            badgeLbl.getStyleClass().add("student-grade");

            VBox tmpPanel = new VBox(10, confirmLabel, nameLabel, badgeLbl);
            tmpPanel.setAlignment(javafx.geometry.Pos.CENTER);
            tmpPanel.getStyleClass().addAll("result-panel", "can-exit");
            tmpPanel.setPadding(new Insets(20, 40, 20, 40));

            // Insertar dinámicamente sobre el área central
            entryFormPanel.getParent().getChildrenUnmodifiable();
            // Usar el exitResultPanel como contenedor reutilizable
            exitVisitorLabel.setText(log.getDisplayName());
            exitBadgeLabel.setText("Carné: " + pendingBadge.getCode());
            exitTimeLabel.setText("Entrada: " + LocalDateTime.now().format(
                    DateTimeFormatter.ofPattern("HH:mm:ss")));
            exitResultPanel.getStyleClass().removeAll("can-exit", "requires-guardian", "inactive");
            exitResultPanel.getStyleClass().add("can-exit");
            showPanel(exitResultPanel);

            pendingBadge = null;
            updateStats();
            loadInsideList();
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
        exitVisitorLabel.setText(log.getDisplayName());
        exitBadgeLabel.setText("Carné: " + badgeCode);
        exitTimeLabel.setText("Salida: " + LocalDateTime.now().format(TIME_FMT));
        exitResultPanel.getStyleClass().removeAll("can-exit", "requires-guardian", "inactive");
        exitResultPanel.getStyleClass().add("can-exit");
        showPanel(exitResultPanel);
        autoClear.playFromStart();
        badgeInput.requestFocus();
        loadInsideList();
        loadLogsTable();
    }

    // -----------------------------------------------------------------------
    // Gestión de carnés
    // -----------------------------------------------------------------------

    @FXML
    private void onAddBadge() {
        String code = newBadgeCodeField.getText().trim();
        if (code.isEmpty()) {
            showError("Ingrese un código para el carné.");
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
    // Historial: purga
    // -----------------------------------------------------------------------

    @FXML
    private void onPurgeLogs() {
        LocalDate date = purgeDatePicker.getValue();
        if (date == null) {
            showError("Seleccione una fecha para borrar los registros.");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirmar borrado");
        confirm.setHeaderText("¿Borrar todos los registros anteriores al " + date + "?");
        confirm.setContentText("Esta acción no se puede deshacer.");
        Optional<ButtonType> res = confirm.showAndWait();
        if (res.isEmpty() || res.get() != ButtonType.OK) return;

        if (!confirmAdminAction("borrar registros históricos")) return;

        int deleted = visitorService.purgeLogsBefore(date.atStartOfDay());
        showInfo("Se eliminaron " + deleted + " registros.");
        loadLogsTable();
    }

    @FXML
    private void onRefreshLogs() {
        loadLogsTable();
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
