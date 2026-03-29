package com.tuempresa.registro.controllers;

import com.tuempresa.registro.api.ApiServer;
import com.tuempresa.registro.dao.AdminUserDAO;
import com.tuempresa.registro.models.AdminUser;
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

import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import java.io.IOException;
import java.net.URL;
import java.sql.SQLException;
import java.util.Optional;
import java.util.ResourceBundle;

/**
 * Controlador para la vista de ajustes del sistema.
 * Gestiona la configuración general y la administración de usuarios.
 * Requiere una sesión activa de Administrador para acceder.
 */
public class SettingsController implements Initializable {

    private static final Logger logger = LoggerFactory.getLogger(SettingsController.class);

    // Session bar
    @FXML private HBox sessionBar;
    @FXML private Label sessionUserLabel;
    @FXML private Label sessionRoleLabel;
    @FXML private Label sessionTimerLabel;
    @FXML private Button sessionLoginBtn;
    @FXML private Button sessionLogoutBtn;

    // Config tab
    @FXML private ComboBox<String> timeoutCombo;
    @FXML private Label settingsStatusLabel;

    // Users tab - table
    @FXML private TableView<AdminUser> usersTable;
    @FXML private TableColumn<AdminUser, String> colUsername;
    @FXML private TableColumn<AdminUser, String> colRole;
    @FXML private TableColumn<AdminUser, Boolean> colActive;

    // Users tab - form
    @FXML private Label userFormTitle;
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private ComboBox<String> roleCombo;
    @FXML private CheckBox activeCheck;
    @FXML private ComboBox<String> userTimeoutCombo;

    // Users tab - buttons
    @FXML private Button editUserButton;
    @FXML private Button deleteUserButton;

    // API tab
    @FXML private TextField apiKeyField;
    @FXML private Label apiStatusLabel;

    // Footer
    @FXML private Label statusMessage;
    @FXML private Label userValidationMessage;

    // Services and state
    private SecurityManager securityManager;
    private SessionManager sessionManager;
    private AdminUserDAO adminUserDAO;
    private ObservableList<AdminUser> usersList;

    // Edit state
    private AdminUser currentEditUser;
    private boolean isEditMode = false;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        logger.info("Inicializando SettingsController");

        securityManager = SecurityManager.getInstance();
        sessionManager = SessionManager.getInstance();
        adminUserDAO = new AdminUserDAO();
        usersList = FXCollections.observableArrayList();

        // Check admin session
        if (!sessionManager.isAdminSessionActive()) {
            Platform.runLater(() -> {
                showAlert(Alert.AlertType.WARNING, "Acceso Denegado",
                        "Solo los administradores pueden acceder a los ajustes del sistema.");
                onBackToScanner();
            });
            return;
        }

        setupSessionBar();
        setupTimeoutCombo();
        setupRoleCombo();
        setupUserTimeoutCombo();
        setupUsersTable();
        setupListeners();
        loadUsers();
        clearUserForm();
        setupApiTab();

        logger.info("SettingsController inicializado correctamente");
    }

    // -----------------------------------------------------------------------
    // Session bar
    // -----------------------------------------------------------------------

    private void setupSessionBar() {
        sessionBar.setVisible(true);
        sessionBar.setManaged(true);

        sessionUserLabel.textProperty().bind(sessionManager.currentUsernameProperty());
        sessionRoleLabel.textProperty().bind(sessionManager.currentRoleProperty());
        sessionTimerLabel.textProperty().bind(sessionManager.remainingTimeFormattedProperty());

        // Bind visibility to session state
        sessionManager.sessionActiveProperty().addListener((obs, oldVal, newVal) -> {
            sessionLoginBtn.setVisible(!newVal);
            sessionLoginBtn.setManaged(!newVal);
            sessionLogoutBtn.setVisible(newVal);
            sessionLogoutBtn.setManaged(newVal);
        });

        // Set initial state
        boolean active = sessionManager.isSessionActive();
        sessionLoginBtn.setVisible(!active);
        sessionLoginBtn.setManaged(!active);
        sessionLogoutBtn.setVisible(active);
        sessionLogoutBtn.setManaged(active);
    }

    // -----------------------------------------------------------------------
    // Configuration tab
    // -----------------------------------------------------------------------

    private void setupTimeoutCombo() {
        timeoutCombo.getItems().addAll(
                "1 minuto",
                "2 minutos",
                "5 minutos",
                "15 minutos",
                "30 minutos",
                "45 minutos",
                "60 minutos"
        );

        // Set current value from SessionManager
        int currentTimeout = sessionManager.getTimeoutMinutes();
        String currentValue = currentTimeout == 1 ? "1 minuto" : currentTimeout + " minutos";
        if (timeoutCombo.getItems().contains(currentValue)) {
            timeoutCombo.setValue(currentValue);
        } else {
            timeoutCombo.setValue("15 minutos");
        }
    }

    @FXML
    private void onSaveSettings() {
        String selected = timeoutCombo.getValue();
        if (selected == null || selected.isEmpty()) {
            setSettingsStatus("Seleccione un tiempo de expiración");
            return;
        }

        int minutes = parseMinutesFromCombo(selected);
        sessionManager.setTimeoutMinutes(minutes);

        setSettingsStatus("Configuración guardada. Tiempo de expiración: " + minutes + " minutos.");
        setStatusMessage("Configuración guardada exitosamente");
        logger.info("Timeout de sesión actualizado a {} minutos", minutes);
    }

    private int parseMinutesFromCombo(String value) {
        // Extract number from strings like "1 minuto", "15 minutos"
        String[] parts = value.trim().split("\\s+");
        try {
            return Integer.parseInt(parts[0]);
        } catch (NumberFormatException e) {
            logger.warn("No se pudo parsear el timeout: {}", value);
            return 15;
        }
    }

    private void setSettingsStatus(String message) {
        if (settingsStatusLabel != null) {
            settingsStatusLabel.setText(message);
        }
    }

    // -----------------------------------------------------------------------
    // Users tab
    // -----------------------------------------------------------------------

    private void setupRoleCombo() {
        roleCombo.getItems().addAll(AdminUser.ROLE_ADMIN, AdminUser.ROLE_OPERATOR);
        roleCombo.setValue(AdminUser.ROLE_OPERATOR);
    }

    private void setupUserTimeoutCombo() {
        userTimeoutCombo.getItems().addAll(
                "1 minuto",
                "2 minutos",
                "5 minutos",
                "15 minutos",
                "30 minutos",
                "45 minutos",
                "60 minutos"
        );
        userTimeoutCombo.setValue(currentTimeoutLabel());
    }

    private String currentTimeoutLabel() {
        int t = sessionManager.getTimeoutMinutes();
        return t == 1 ? "1 minuto" : t + " minutos";
    }

    private void setupUsersTable() {
        colUsername.setCellValueFactory(new PropertyValueFactory<>("username"));
        colRole.setCellValueFactory(new PropertyValueFactory<>("role"));
        colActive.setCellValueFactory(new PropertyValueFactory<>("active"));

        // Display "Sí"/"No" for active column
        colActive.setCellFactory(column -> new TableCell<AdminUser, Boolean>() {
            @Override
            protected void updateItem(Boolean item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : (item ? "Sí" : "No"));
            }
        });

        usersTable.setItems(usersList);
        usersTable.setPlaceholder(new Label("Sin usuarios registrados"));
    }

    private void setupListeners() {
        usersTable.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldSelection, newSelection) -> {
                    boolean hasSelection = newSelection != null;
                    editUserButton.setDisable(!hasSelection);
                    deleteUserButton.setDisable(!hasSelection);
                });
    }

    private void loadUsers() {
        usersList.clear();
        usersList.addAll(adminUserDAO.findAllIncludingInactive());
        logger.debug("Cargados {} usuarios", usersList.size());
    }

    @FXML
    private void onNewUser() {
        isEditMode = false;
        currentEditUser = null;
        clearUserForm();
        userFormTitle.setText("Nuevo Usuario");
        usernameField.setDisable(false);
        usernameField.requestFocus();
        setStatusMessage("Ingrese los datos del nuevo usuario");
    }

    @FXML
    private void onEditUser() {
        AdminUser selected = usersTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert(Alert.AlertType.WARNING, "Seleccione un usuario",
                    "Debe seleccionar un usuario de la tabla para editar.");
            return;
        }

        isEditMode = true;
        currentEditUser = selected;
        userFormTitle.setText("Editar Usuario");

        usernameField.setText(selected.getUsername());
        usernameField.setDisable(true);
        passwordField.clear(); // Leave blank - only fill if changing password
        roleCombo.setValue(selected.getRole());
        activeCheck.setSelected(selected.isActive());
        userTimeoutCombo.setValue(currentTimeoutLabel());

        setStatusMessage("Editando usuario: " + selected.getUsername());
    }

    @FXML
    private void onDeleteUser() {
        AdminUser selected = usersTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert(Alert.AlertType.WARNING, "Seleccione un usuario",
                    "Debe seleccionar un usuario de la tabla para eliminar.");
            return;
        }

        // Don't allow deleting yourself
        AdminUser currentUser = sessionManager.getCurrentUser();
        if (currentUser != null && currentUser.getId().equals(selected.getId())) {
            showAlert(Alert.AlertType.WARNING, "Operación no permitida",
                    "No puede eliminar su propio usuario mientras tiene una sesión activa.");
            return;
        }

        // Confirm deletion
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirmar Eliminación");
        confirm.setHeaderText("¿Eliminar usuario?");
        confirm.setContentText("¿Está seguro de eliminar al usuario \"" + selected.getUsername() + "\"?\n" +
                "Esta acción no se puede deshacer.");

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                adminUserDAO.delete(selected.getId());
                loadUsers();
                clearUserForm();
                setStatusMessage("Usuario eliminado: " + selected.getUsername());
                logger.info("Usuario eliminado: {}", selected.getUsername());
            } catch (SQLException e) {
                logger.error("Error al eliminar usuario", e);
                showAlert(Alert.AlertType.ERROR, "Error",
                        "No se pudo eliminar el usuario: " + e.getMessage());
            }
        }
    }

    @FXML
    private void onSaveUser() {
        if (!validateUserForm()) {
            return;
        }

        String username = usernameField.getText().trim();
        String password = passwordField.getText();
        String role = roleCombo.getValue();
        boolean active = activeCheck.isSelected();

        try {
            if (isEditMode && currentEditUser != null) {
                // Update existing user
                currentEditUser.setRole(role);
                currentEditUser.setActive(active);

                // Update password only if a new one was provided
                if (password != null && !password.isEmpty()) {
                    if (password.length() < 4) {
                        showUserValidationError("La contraseña debe tener al menos 4 caracteres");
                        return;
                    }
                    currentEditUser.setPasswordHash(securityManager.hashPassword(password));
                }

                adminUserDAO.update(currentEditUser);
                setStatusMessage("Usuario actualizado: " + username);
                logger.info("Usuario actualizado: {}", username);
            } else {
                // Create new user
                securityManager.createUser(username, password, role);

                // If the user was created as inactive, update that
                if (!active) {
                    Optional<AdminUser> created = adminUserDAO.findByUsername(username);
                    if (created.isPresent()) {
                        AdminUser user = created.get();
                        user.setActive(false);
                        adminUserDAO.update(user);
                    }
                }

                setStatusMessage("Usuario creado: " + username);
                logger.info("Usuario creado: {}", username);
            }

            // Apply selected session timeout
            String selectedTimeout = userTimeoutCombo.getValue();
            if (selectedTimeout != null && !selectedTimeout.isEmpty()) {
                sessionManager.setTimeoutMinutes(parseMinutesFromCombo(selectedTimeout));
                // Sync the config tab combo
                timeoutCombo.setValue(selectedTimeout);
            }

            loadUsers();
            clearUserForm();

        } catch (IllegalArgumentException e) {
            showUserValidationError(e.getMessage());
        } catch (SQLException e) {
            logger.error("Error al guardar usuario", e);
            showAlert(Alert.AlertType.ERROR, "Error",
                    "No se pudo guardar el usuario: " + e.getMessage());
        }
    }

    @FXML
    private void onCancelUser() {
        clearUserForm();
        usersTable.getSelectionModel().clearSelection();
        setStatusMessage("Operación cancelada");
    }

    private void clearUserForm() {
        currentEditUser = null;
        isEditMode = false;
        userFormTitle.setText("Nuevo Usuario");
        usernameField.clear();
        usernameField.setDisable(false);
        passwordField.clear();
        roleCombo.setValue(AdminUser.ROLE_OPERATOR);
        activeCheck.setSelected(true);
        if (userTimeoutCombo != null) userTimeoutCombo.setValue(currentTimeoutLabel());
        clearUserValidationError();
    }

    private boolean validateUserForm() {
        clearUserValidationError();

        String username = usernameField.getText();
        if (username == null || username.trim().isEmpty()) {
            showUserValidationError("El nombre de usuario es obligatorio");
            usernameField.requestFocus();
            return false;
        }

        // Password is required for new users, optional for edits
        String password = passwordField.getText();
        if (!isEditMode) {
            if (password == null || password.isEmpty()) {
                showUserValidationError("La contraseña es obligatoria para nuevos usuarios");
                passwordField.requestFocus();
                return false;
            }
            if (password.length() < 4) {
                showUserValidationError("La contraseña debe tener al menos 4 caracteres");
                passwordField.requestFocus();
                return false;
            }
        }

        if (roleCombo.getValue() == null) {
            showUserValidationError("Debe seleccionar un rol");
            return false;
        }

        return true;
    }

    private void showUserValidationError(String message) {
        if (userValidationMessage != null) {
            userValidationMessage.setText(message);
            userValidationMessage.getStyleClass().add("error");
        }
    }

    private void clearUserValidationError() {
        if (userValidationMessage != null) {
            userValidationMessage.setText("");
            userValidationMessage.getStyleClass().remove("error");
        }
    }

    // -----------------------------------------------------------------------
    // Login / Logout
    // -----------------------------------------------------------------------

    @FXML
    private void onLogin() {
        Dialog<String[]> dialog = new Dialog<>();
        dialog.setTitle("Iniciar Sesión");
        dialog.setHeaderText("Ingrese sus credenciales");

        ButtonType loginButtonType = new ButtonType("Iniciar Sesión", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(loginButtonType, ButtonType.CANCEL);

        VBox content = new VBox(10);
        content.setPadding(new Insets(20));

        TextField userField = new TextField();
        userField.setPromptText("Usuario");
        PasswordField passField = new PasswordField();
        passField.setPromptText("Contraseña");

        content.getChildren().addAll(
                new Label("Usuario:"), userField,
                new Label("Contraseña:"), passField
        );
        dialog.getDialogPane().setContent(content);

        Platform.runLater(userField::requestFocus);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == loginButtonType) {
                return new String[]{userField.getText(), passField.getText()};
            }
            return null;
        });

        Optional<String[]> result = dialog.showAndWait();
        result.ifPresent(credentials -> {
            Optional<AdminUser> authenticated = securityManager.authenticate(credentials[0], credentials[1]);
            if (authenticated.isPresent()) {
                AdminUser user = authenticated.get();
                sessionManager.startSession(user, sessionManager.getTimeoutMinutes());
                setStatusMessage("Sesión iniciada: " + user.getUsername());
                logger.info("Sesión iniciada desde Settings: {}", user.getUsername());

                // If not admin, redirect back
                if (!user.isAdmin()) {
                    showAlert(Alert.AlertType.WARNING, "Acceso Denegado",
                            "Solo los administradores pueden acceder a los ajustes del sistema.");
                    onBackToScanner();
                }
            } else {
                showAlert(Alert.AlertType.ERROR, "Error de Autenticación",
                        "Usuario o contraseña incorrectos.");
            }
        });
    }

    @FXML
    private void onLogout() {
        sessionManager.endSession();
        setStatusMessage("Sesión cerrada");
        logger.info("Sesión cerrada desde Settings");

        // Navigate back since settings requires admin session
        onBackToScanner();
    }

    // -----------------------------------------------------------------------
    // API tab
    // -----------------------------------------------------------------------

    private void setupApiTab() {
        if (apiKeyField == null) return;
        ApiServer api = ApiServer.getInstance();
        if (api != null) {
            apiKeyField.setText(api.getApiKey());
        } else {
            apiKeyField.setText("(servidor no iniciado)");
        }
    }

    @FXML
    private void onCopyApiKey() {
        String key = apiKeyField.getText();
        if (key == null || key.isBlank()) return;
        ClipboardContent content = new ClipboardContent();
        content.putString(key);
        Clipboard.getSystemClipboard().setContent(content);
        apiStatusLabel.setText("Clave copiada al portapapeles.");
    }

    @FXML
    private void onRegenerateApiKey() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Regenerar clave API");
        confirm.setHeaderText("¿Regenerar la clave de API?");
        confirm.setContentText(
                "La clave actual dejará de funcionar inmediatamente.\n" +
                "Deberás actualizar todos los scripts y colecciones de Postman que la usen.");
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) return;

        try {
            ApiServer api = ApiServer.getInstance();
            if (api == null) {
                apiStatusLabel.setText("Error: el servidor de API no está activo.");
                return;
            }
            String newKey = api.regenerateApiKey();
            apiKeyField.setText(newKey);
            apiStatusLabel.setText("Clave regenerada correctamente.");
        } catch (Exception e) {
            apiStatusLabel.setText("Error al regenerar: " + e.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // Help
    // -----------------------------------------------------------------------

    @FXML
    private void onHelp() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Ayuda — Ajustes del Sistema");
        alert.setHeaderText("Cómo usar los Ajustes");
        alert.setContentText(
            "CONFIGURACIÓN GENERAL\n" +
            "• Tiempo de expiración de sesión: minutos de inactividad antes de cerrar sesión automáticamente.\n" +
            "• Guarde los cambios con 'Guardar Configuración'.\n\n" +
            "GESTIÓN DE USUARIOS\n" +
            "• Lista todos los usuarios del sistema con su rol y estado.\n" +
            "• Roles disponibles:\n" +
            "  - Administrador: acceso completo (agregar/editar/eliminar registros, cambiar ajustes).\n" +
            "  - Operador: solo puede ver registros y realizar escaneos.\n\n" +
            "AGREGAR / EDITAR USUARIO\n" +
            "• Usuario: nombre único de inicio de sesión.\n" +
            "• Contraseña: mínimo 4 caracteres. Déjela en blanco al editar para no cambiarla.\n" +
            "• Tiempo de sesión: configura el tiempo de expiración global al guardar.\n" +
            "• Activo: los usuarios inactivos no pueden iniciar sesión.\n\n" +
            "API REST\n" +
            "• La aplicación expone una API en el puerto 8080 para importar y exportar datos.\n" +
            "• La API Key se genera automáticamente al primer inicio. Cópiela con el botón 'Copiar'.\n" +
            "• Use 'Regenerar' si necesita invalidar la clave actual (por ejemplo, si fue comprometida).\n" +
            "• La nueva clave entra en vigor inmediatamente; la anterior deja de funcionar al instante.\n" +
            "• Consulte el archivo API.md en el repositorio para la guía completa de endpoints y Postman.\n\n" +
            "SEGURIDAD\n" +
            "• No puede eliminar su propio usuario mientras tiene sesión activa.\n" +
            "• Se requiere sesión de administrador para acceder a esta sección."
        );
        alert.showAndWait();
    }

    // -----------------------------------------------------------------------
    // Navigation
    // -----------------------------------------------------------------------

    @FXML
    private void onBackToScanner() {
        navigateTo("/fxml/scanner-view.fxml", "Scanner de Estudiantes");
    }

    @FXML
    private void onManageStudents() {
        navigateTo("/fxml/student-crud.fxml", "Gestión de Estudiantes");
    }

    @FXML
    private void onManageVisitors() {
        navigateTo("/fxml/visitor-view.fxml", "Gestión de Visitantes");
    }

    @FXML
    private void onManageStaff() {
        navigateTo("/fxml/staff-admin.fxml", "Gestión de Personal");
    }

    @FXML
    private void onManageCarne() {
        navigateTo("/fxml/carne-view.fxml", "Creador de Carné");
    }

    private void navigateTo(String fxmlPath, String title) {
        try {
            logger.info("Navegando a: {}", title);

            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent root = loader.load();

            Stage stage = (Stage) usersTable.getScene().getWindow();
            Scene scene = new Scene(root);
            scene.getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());

            stage.setMaximized(false);
            stage.setScene(scene);
            stage.setTitle(title);
            Platform.runLater(() -> stage.setMaximized(true));

        } catch (IOException e) {
            logger.error("Error al navegar a {}", title, e);
            showAlert(Alert.AlertType.ERROR, "Error de Navegación",
                    "No se pudo abrir la vista: " + title);
        }
    }

    // -----------------------------------------------------------------------
    // Utilities
    // -----------------------------------------------------------------------

    private void setStatusMessage(String message) {
        if (statusMessage != null) {
            statusMessage.setText(message);
        }
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
