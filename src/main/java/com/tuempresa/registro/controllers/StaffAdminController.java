package com.tuempresa.registro.controllers;

import com.tuempresa.registro.models.AdminUser;
import com.tuempresa.registro.models.StaffMember;
import com.tuempresa.registro.services.StaffService;
import com.tuempresa.registro.utils.SecurityManager;
import com.tuempresa.registro.utils.SessionManager;
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
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.sql.SQLException;
import java.util.Optional;
import java.util.ResourceBundle;

/**
 * Controlador para la gestión CRUD de miembros del personal.
 * Permite agregar, editar, eliminar y buscar personal.
 * Usa SessionManager para control de sesiones y permisos.
 */
public class StaffAdminController implements Initializable {

    private static final Logger logger = LoggerFactory.getLogger(StaffAdminController.class);

    // Session bar
    @FXML private HBox sessionBar;
    @FXML private Label sessionUserLabel;
    @FXML private Label sessionRoleLabel;
    @FXML private Label sessionTimerLabel;
    @FXML private Button sessionLoginBtn;
    @FXML private Button sessionLogoutBtn;

    // Componentes de la tabla
    @FXML private TableView<StaffMember> staffTable;
    @FXML private TableColumn<StaffMember, String> colBarcode;
    @FXML private TableColumn<StaffMember, String> colFirstName;
    @FXML private TableColumn<StaffMember, String> colLastName;
    @FXML private TableColumn<StaffMember, String> colDepartment;
    @FXML private TableColumn<StaffMember, String> colStatus;

    // Componentes de búsqueda
    @FXML private TextField searchField;

    // Componentes del formulario
    @FXML private Label formTitle;
    @FXML private TextField barcodeField;
    @FXML private TextField firstNameField;
    @FXML private TextField lastNameField;
    @FXML private TextField idNumberField;
    @FXML private TextField departmentField;
    @FXML private ComboBox<String> statusCombo;

    // Botones
    @FXML private Button editButton;
    @FXML private Button deleteButton;

    // Labels de estado
    @FXML private Label countLabel;
    @FXML private Label statusMessage;
    @FXML private Label validationMessage;

    // Servicios
    private StaffService staffService;
    private SessionManager sessionManager;

    // Datos
    private ObservableList<StaffMember> staffList;

    // Estado de edición
    private StaffMember currentStaff;
    private boolean isEditMode = false;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        logger.info("Inicializando StaffAdminController");

        staffService = new StaffService();
        sessionManager = SessionManager.getInstance();

        staffList = FXCollections.observableArrayList();

        setupTable();
        setupStatusCombo();
        setupListeners();
        loadStaff();
        setupSessionBar();
        clearForm();

        logger.info("StaffAdminController inicializado correctamente");
    }

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
     * Configura la tabla de personal.
     */
    private void setupTable() {
        colBarcode.setCellValueFactory(new PropertyValueFactory<>("barcode"));
        colFirstName.setCellValueFactory(new PropertyValueFactory<>("firstName"));
        colLastName.setCellValueFactory(new PropertyValueFactory<>("lastName"));
        colDepartment.setCellValueFactory(new PropertyValueFactory<>("department"));

        colStatus.setCellValueFactory(cellData -> {
            String status = cellData.getValue().getStatus();
            String displayStatus = "active".equals(status) ? "Activo" : "Inactivo";
            return new SimpleStringProperty(displayStatus);
        });

        staffTable.setItems(staffList);
        staffTable.setPlaceholder(new Label("No hay personal registrado"));
    }

    /**
     * Configura el ComboBox de estado.
     */
    private void setupStatusCombo() {
        statusCombo.getItems().addAll("Activo", "Inactivo");
        statusCombo.setValue("Activo");
    }

    /**
     * Configura los listeners de eventos.
     */
    private void setupListeners() {
        staffTable.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldSelection, newSelection) -> {
                    boolean hasSelection = newSelection != null;
                    editButton.setDisable(!hasSelection);
                    deleteButton.setDisable(!hasSelection);
                });

        // Búsqueda en tiempo real
        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            performSearch(newVal);
        });
    }

    /**
     * Carga la lista de personal.
     */
    private void loadStaff() {
        staffList.clear();
        staffList.addAll(staffService.getAllStaff());
        updateCountLabel();
        logger.debug("Cargados {} miembros del personal", staffList.size());
    }

    /**
     * Actualiza el label de conteo.
     */
    private void updateCountLabel() {
        int count = staffList.size();
        countLabel.setText(count + (count == 1 ? " miembro" : " miembros"));
    }

    // ========================
    // Manejadores de sesión
    // ========================

    /**
     * Muestra diálogo de login con usuario y contraseña.
     * Loop en credenciales incorrectas hasta que el usuario cancele.
     */
    @FXML
    private void onLogin() {
        SecurityManager securityManager = SecurityManager.getInstance();

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

            Optional<AdminUser> authResult = securityManager.authenticate(username, password);

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
        setStatusMessage("Sesión cerrada");
    }

    // ========================
    // Operaciones CRUD
    // ========================

    /**
     * Prepara el formulario para un nuevo miembro del personal.
     */
    @FXML
    private void onNewStaff() {
        isEditMode = false;
        currentStaff = null;
        clearForm();
        formTitle.setText("Nuevo Personal");
        barcodeField.setDisable(false);
        barcodeField.requestFocus();
        setStatusMessage("Ingrese los datos del nuevo miembro del personal");
    }

    /**
     * Carga el miembro seleccionado en el formulario para editar.
     */
    @FXML
    private void onEditStaff() {
        StaffMember selected = staffTable.getSelectionModel().getSelectedItem();

        if (selected == null) {
            showAlert(Alert.AlertType.WARNING, "Seleccione un registro",
                    "Debe seleccionar un miembro del personal de la tabla para editar.");
            return;
        }

        isEditMode = true;
        currentStaff = selected;
        loadStaffToForm(selected);
        formTitle.setText("Editar Personal");
        barcodeField.setDisable(true);
        firstNameField.requestFocus();
        setStatusMessage("Editando: " + selected.getFullName());
    }

    /**
     * Elimina el miembro del personal seleccionado.
     * Requiere sesión activa con permisos de modificación.
     */
    @FXML
    private void onDeleteStaff() {
        StaffMember selected = staffTable.getSelectionModel().getSelectedItem();

        if (selected == null) {
            return;
        }

        if (!requireSession()) {
            return;
        }

        if (!sessionManager.canModifyData()) {
            showAlert(Alert.AlertType.WARNING, "Sin Permisos",
                    "No tiene permisos para eliminar registros.");
            return;
        }

        // Confirmar eliminación
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirmar Eliminación");
        confirm.setHeaderText("¿Eliminar miembro del personal?");
        confirm.setContentText("¿Está seguro de eliminar a " + selected.getFullName() + "?\n" +
                "Esta acción no se puede deshacer.");

        Optional<ButtonType> result = confirm.showAndWait();

        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                staffService.deleteStaff(selected.getId());
                loadStaff();
                clearForm();
                setStatusMessage("Personal eliminado: " + selected.getFullName());
                logger.info("Personal eliminado: {}", selected);
            } catch (SQLException e) {
                logger.error("Error al eliminar personal", e);
                showAlert(Alert.AlertType.ERROR, "Error",
                        "No se pudo eliminar el miembro del personal: " + e.getMessage());
            }
        }
    }

    /**
     * Guarda o actualiza un miembro del personal.
     * Valida el formulario y verifica permisos de sesión.
     */
    @FXML
    private void onSave() {
        if (!validateForm()) {
            return;
        }

        if (!requireSession()) {
            return;
        }

        if (!sessionManager.canModifyData()) {
            showAlert(Alert.AlertType.WARNING, "Sin Permisos",
                    "No tiene permisos para modificar registros.");
            return;
        }

        try {
            StaffMember staff = isEditMode ? currentStaff : new StaffMember();

            staff.setBarcode(barcodeField.getText().trim());
            staff.setFirstName(firstNameField.getText().trim());
            staff.setLastName(lastNameField.getText().trim());
            staff.setIdNumber(idNumberField.getText().trim());
            staff.setDepartment(departmentField.getText().trim());
            staff.setStatus(mapStatusToDb(statusCombo.getValue()));

            if (isEditMode) {
                staff.setUpdatedBy(sessionManager.getActiveUsername());
                staffService.updateStaff(staff);
                setStatusMessage("Personal actualizado: " + staff.getFullName());
                logger.info("Personal actualizado: {}", staff);
            } else {
                staff.setCreatedBy(sessionManager.getActiveUsername());
                staffService.saveStaff(staff);
                setStatusMessage("Personal creado: " + staff.getFullName());
                logger.info("Personal creado: {}", staff);
            }

            loadStaff();
            clearForm();

        } catch (IllegalArgumentException e) {
            showValidationError(e.getMessage());
        } catch (SQLException e) {
            logger.error("Error al guardar personal", e);
            showAlert(Alert.AlertType.ERROR, "Error",
                    "No se pudo guardar el miembro del personal: " + e.getMessage());
        }
    }

    /**
     * Cancela la operación actual y limpia el formulario.
     */
    @FXML
    private void onCancel() {
        clearForm();
        staffTable.getSelectionModel().clearSelection();
        setStatusMessage("Operación cancelada");
    }

    // ========================
    // Búsqueda
    // ========================

    /**
     * Manejador para búsqueda.
     */
    @FXML
    private void onSearch() {
        performSearch(searchField.getText());
    }

    /**
     * Realiza la búsqueda en tiempo real.
     */
    private void performSearch(String searchTerm) {
        if (searchTerm == null || searchTerm.trim().isEmpty()) {
            loadStaff();
            return;
        }

        staffList.clear();
        staffList.addAll(staffService.searchStaff(searchTerm.trim()));
        updateCountLabel();
    }

    /**
     * Limpia el campo de búsqueda y recarga la lista completa.
     */
    @FXML
    private void onClearSearch() {
        searchField.clear();
        loadStaff();
        setStatusMessage("Filtro limpiado");
    }

    @FXML
    private void onShowHelp() {
        Alert helpDialog = new Alert(Alert.AlertType.INFORMATION);
        helpDialog.setTitle("Ayuda - Personal");
        helpDialog.setHeaderText("Administración de Personal");
        helpDialog.setContentText(
                "Desde esta pantalla puede gestionar los miembros del personal.\n\n" +
                "• Agregar: Clic en '+ Nuevo Personal' y complete los datos.\n" +
                "• Editar: Seleccione un registro y clic en 'Editar'.\n" +
                "• Eliminar: Seleccione un registro y clic en 'Eliminar'.\n" +
                "• Buscar: Escriba en el campo de búsqueda.\n\n" +
                "Todas las operaciones de modificación requieren una sesión de Administrador activa.");
        helpDialog.showAndWait();
    }

    // ========================
    // Navegación
    // ========================

    @FXML
    private void onBackToScanner() {
        try {
            logger.info("Volviendo al scanner");

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/scanner-view.fxml"));
            Parent root = loader.load();

            Stage stage = (Stage) staffTable.getScene().getWindow();
            Scene scene = new Scene(root);
            scene.getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());

            stage.setMaximized(false);
            stage.setScene(scene);
            stage.setTitle("Scanner de Estudiantes");
            Platform.runLater(() -> stage.setMaximized(true));

        } catch (IOException e) {
            logger.error("Error al volver al scanner", e);
        }
    }

    @FXML
    private void onManageStudents() {
        try {
            logger.info("Abriendo gestión de estudiantes");

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/student-crud.fxml"));
            Parent root = loader.load();

            Stage stage = (Stage) staffTable.getScene().getWindow();
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

    @FXML
    private void onManageVisitors() {
        try {
            logger.info("Abriendo control de visitantes");

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/visitor-control.fxml"));
            Parent root = loader.load();

            Stage stage = (Stage) staffTable.getScene().getWindow();
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

    @FXML
    private void onManageCarne() {
        try {
            logger.info("Abriendo Creador de Carné");

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/carne-view.fxml"));
            Parent root = loader.load();

            Stage stage = (Stage) staffTable.getScene().getWindow();
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

    @FXML
    private void onManageSettings() {
        try {
            logger.info("Abriendo configuración");

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/settings-view.fxml"));
            Parent root = loader.load();

            Stage stage = (Stage) staffTable.getScene().getWindow();
            Scene scene = new Scene(root);
            scene.getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());

            stage.setMaximized(false);
            stage.setScene(scene);
            stage.setTitle("Configuración");
            Platform.runLater(() -> stage.setMaximized(true));

        } catch (IOException e) {
            logger.error("Error al abrir configuración", e);
        }
    }

    // ========================
    // Métodos auxiliares
    // ========================

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

    /**
     * Carga los datos de un miembro del personal en el formulario.
     */
    private void loadStaffToForm(StaffMember staff) {
        barcodeField.setText(staff.getBarcode());
        firstNameField.setText(staff.getFirstName());
        lastNameField.setText(staff.getLastName());
        idNumberField.setText(staff.getIdNumber() != null ? staff.getIdNumber() : "");
        departmentField.setText(staff.getDepartment() != null ? staff.getDepartment() : "");
        statusCombo.setValue(mapStatusFromDb(staff.getStatus()));
    }

    /**
     * Limpia el formulario.
     */
    private void clearForm() {
        currentStaff = null;
        isEditMode = false;
        formTitle.setText("Nuevo Personal");

        barcodeField.clear();
        barcodeField.setDisable(false);
        firstNameField.clear();
        lastNameField.clear();
        idNumberField.clear();
        departmentField.clear();
        statusCombo.setValue("Activo");

        clearValidationError();
    }

    /**
     * Valida el formulario.
     */
    private boolean validateForm() {
        clearValidationError();

        if (barcodeField.getText().trim().isEmpty()) {
            showValidationError("El código de barras es obligatorio");
            barcodeField.requestFocus();
            return false;
        }

        if (firstNameField.getText().trim().isEmpty()) {
            showValidationError("El nombre es obligatorio");
            firstNameField.requestFocus();
            return false;
        }

        if (lastNameField.getText().trim().isEmpty()) {
            showValidationError("El apellido es obligatorio");
            lastNameField.requestFocus();
            return false;
        }

        return true;
    }

    private void showValidationError(String message) {
        validationMessage.setText(message);
        validationMessage.getStyleClass().add("error");
    }

    private void clearValidationError() {
        validationMessage.setText("");
        validationMessage.getStyleClass().remove("error");
    }

    private void setStatusMessage(String message) {
        statusMessage.setText(message);
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    private String mapStatusToDb(String displayStatus) {
        return switch (displayStatus) {
            case "Activo" -> "active";
            case "Inactivo" -> "inactive";
            default -> "active";
        };
    }

    private String mapStatusFromDb(String dbStatus) {
        return switch (dbStatus) {
            case "active" -> "Activo";
            default -> "Inactivo";
        };
    }
}
