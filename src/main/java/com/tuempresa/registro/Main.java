package com.tuempresa.registro;

import com.tuempresa.registro.api.ApiServer;
import com.tuempresa.registro.dao.DatabaseConnection;
import com.tuempresa.registro.utils.DialogUtils;
import com.tuempresa.registro.utils.LicenseManager;
import com.tuempresa.registro.utils.SecurityManager;
import com.tuempresa.registro.utils.SessionManager;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Optional;

/**
 * Clase principal de la aplicación de Registro de Estudiantes.
 * Inicializa JavaFX, la base de datos y valida la licencia.
 */
public class Main extends Application {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    private ApiServer apiServer;

    // Nombre de la aplicación
    private static final String APP_TITLE = "Sistema de Registro de Estudiantes";

    // Versión de la aplicación
    private static final String APP_VERSION = "1.0.0";

    // Dimensiones de la ventana
    private static final int WINDOW_WIDTH = 1024;
    private static final int WINDOW_HEIGHT = 700;

    /**
     * Punto de entrada principal de la aplicación.
     *
     * @param args Argumentos de línea de comandos
     */
    public static void main(String[] args) {
        logger.info("=".repeat(60));
        logger.info("Iniciando {} v{}", APP_TITLE, APP_VERSION);
        logger.info("=".repeat(60));

        launch(args);
    }

    /**
     * Método de inicio de JavaFX.
     *
     * @param primaryStage Ventana principal de la aplicación
     */
    @Override
    public void start(Stage primaryStage) {
        try {
            // Inicializar base de datos
            initializeDatabase();

            // Verificar configuración inicial del primer administrador
            if (!checkFirstRunSetup()) {
                logger.info("Usuario canceló configuración inicial. Cerrando aplicación.");
                Platform.exit();
                return;
            }

            // Validar licencia
            if (!validateLicense()) {
                // Si no hay licencia válida pero permite modo demo
                if (!confirmDemoMode()) {
                    logger.info("Usuario rechazó modo demo. Cerrando aplicación.");
                    Platform.exit();
                    return;
                }
            }

            // Iniciar el servidor de API REST
            startApiServer();

            // Cargar la vista principal
            loadMainView(primaryStage);

            logger.info("Aplicación iniciada correctamente");

        } catch (Exception e) {
            logger.error("Error fatal al iniciar la aplicación", e);
            showFatalError("Error al iniciar la aplicación", e.getMessage());
            Platform.exit();
        }
    }

    /**
     * Arranca el servidor de API REST embebido en el puerto 8080.
     */
    private void startApiServer() {
        try {
            apiServer = new ApiServer();
            apiServer.start();
        } catch (Exception e) {
            logger.warn("No se pudo iniciar el servidor de API en el puerto 8080: {}", e.getMessage());
            logger.warn("La aplicación funcionará normalmente pero sin la API REST.");
        }
    }

    /**
     * Inicializa la conexión a la base de datos y crea las tablas.
     *
     * @throws SQLException Si hay error de base de datos
     * @throws IOException  Si no se puede leer el schema
     */
    private void initializeDatabase() throws SQLException, IOException {
        logger.info("Inicializando base de datos...");

        DatabaseConnection dbConnection = DatabaseConnection.getInstance();
        dbConnection.initializeDatabase();

        logger.info("Base de datos inicializada correctamente");
    }

    /**
     * Verifica si es la primera ejecución y necesita crear el primer administrador.
     * Si no hay usuarios configurados, muestra el diálogo de configuración inicial.
     *
     * @return true si ya hay usuarios o se creó el primer administrador exitosamente
     */
    private boolean checkFirstRunSetup() {
        SecurityManager securityManager = SecurityManager.getInstance();

        if (securityManager.isPasswordConfigured()) {
            logger.info("Sistema ya configurado con usuarios existentes");
            return true;
        }

        logger.info("Primera ejecución - solicitando creación del primer administrador");

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Configuración Inicial");
        dialog.setHeaderText("Bienvenido al Sistema de Registro de Estudiantes");

        ButtonType configureButtonType = new ButtonType("Configurar", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(configureButtonType, ButtonType.CANCEL);

        VBox content = new VBox(15);
        content.setPadding(new Insets(20));

        Label infoLabel = new Label(
                "Esta es la primera vez que se ejecuta el sistema.\n" +
                "Debe crear un usuario Administrador para comenzar."
        );
        infoLabel.setWrapText(true);

        // Username field
        Label usernameLabel = new Label("Nombre de usuario:");
        TextField usernameField = new TextField();
        usernameField.setPromptText("Ingrese nombre de usuario");
        usernameField.setPrefWidth(300);

        // Password field
        Label passwordLabel = new Label("Contraseña (mínimo 4 caracteres):");
        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Ingrese contraseña");
        passwordField.setPrefWidth(300);

        // Confirm password field
        Label confirmLabel = new Label("Confirmar contraseña:");
        PasswordField confirmField = new PasswordField();
        confirmField.setPromptText("Repita la contraseña");
        confirmField.setPrefWidth(300);

        // Role (disabled, forced to Administrador)
        Label roleLabel = new Label("Rol:");
        ComboBox<String> roleComboBox = new ComboBox<>();
        roleComboBox.getItems().add("Administrador");
        roleComboBox.setValue("Administrador");
        roleComboBox.setDisable(true);
        roleComboBox.setPrefWidth(300);

        // Session timeout
        Label timeoutLabel = new Label("Tiempo de inactividad para cierre de sesión:");
        ComboBox<String> timeoutComboBox = new ComboBox<>();
        timeoutComboBox.getItems().addAll(
                "1 minuto", "2 minutos", "5 minutos", "15 minutos",
                "30 minutos", "45 minutos", "60 minutos"
        );
        timeoutComboBox.setValue("15 minutos");
        timeoutComboBox.setPrefWidth(300);

        // Warning label
        Label warningLabel = new Label(
                "Este usuario será Administrador con acceso completo. " +
                "Recuerda crear usuarios Operadores u otros Administradores desde el módulo de Ajustes."
        );
        warningLabel.setWrapText(true);
        warningLabel.setStyle("-fx-text-fill: #e67e22; -fx-font-weight: bold;");

        // Error label
        Label errorLabel = new Label();
        errorLabel.setStyle("-fx-text-fill: red;");

        content.getChildren().addAll(
                infoLabel,
                new Separator(),
                usernameLabel, usernameField,
                passwordLabel, passwordField,
                confirmLabel, confirmField,
                roleLabel, roleComboBox,
                timeoutLabel, timeoutComboBox,
                new Separator(),
                warningLabel,
                errorLabel
        );

        dialog.getDialogPane().setContent(content);

        // Deshabilitar botón hasta que haya entrada válida
        javafx.scene.Node configureButton = dialog.getDialogPane().lookupButton(configureButtonType);
        configureButton.setDisable(true);

        // Validar campos en tiempo real
        Runnable validateFields = () -> {
            String username = usernameField.getText().trim();
            String password = passwordField.getText();
            String confirm = confirmField.getText();

            boolean valid = !username.isEmpty() && password.length() >= 4 && password.equals(confirm);
            configureButton.setDisable(!valid);

            if (username.isEmpty() && !usernameField.getText().isEmpty()) {
                errorLabel.setText("El nombre de usuario no puede estar vacío");
            } else if (password.length() > 0 && password.length() < 4) {
                errorLabel.setText("La contraseña debe tener al menos 4 caracteres");
            } else if (!confirm.isEmpty() && !password.equals(confirm)) {
                errorLabel.setText("Las contraseñas no coinciden");
            } else {
                errorLabel.setText("");
            }
        };

        usernameField.textProperty().addListener((obs, oldVal, newVal) -> validateFields.run());
        passwordField.textProperty().addListener((obs, oldVal, newVal) -> validateFields.run());
        confirmField.textProperty().addListener((obs, oldVal, newVal) -> validateFields.run());

        Platform.runLater(usernameField::requestFocus);

        Optional<ButtonType> result = dialog.showAndWait();

        if (result.isPresent() && result.get() == configureButtonType) {
            String username = usernameField.getText().trim();
            String password = passwordField.getText();

            // Parse timeout minutes from selection
            String timeoutSelection = timeoutComboBox.getValue();
            int timeoutMinutes = Integer.parseInt(timeoutSelection.split(" ")[0]);

            if (securityManager.createFirstAdmin(username, password, timeoutMinutes)) {
                DialogUtils.alert(Alert.AlertType.INFORMATION, "Configuración Completada", null,
                        "Usuario " + username + " creado correctamente como Administrador.\n" +
                        "Recuerda crear usuarios Operadores u otros Administradores desde el módulo de Ajustes.");
                logger.info("Primer administrador '{}' creado exitosamente", username);
                return true;
            } else {
                showFatalError("Error de Configuración", "No se pudo crear el usuario administrador.");
                return false;
            }
        }

        return false;
    }

    /**
     * Valida la licencia del software.
     *
     * @return true si la licencia es válida
     */
    private boolean validateLicense() {
        logger.info("Validando licencia...");

        LicenseManager licenseManager = LicenseManager.getInstance();
        String machineId = licenseManager.getMachineId();

        logger.info("Machine ID: {}", machineId);

        // Por ahora, siempre permitimos modo demo
        // En producción, aquí se cargaría la licencia desde la BD
        // y se validaría con validateLicense()

        return false; // Simula que no hay licencia válida
    }

    /**
     * Muestra diálogo para confirmar uso en modo demo.
     *
     * @return true si el usuario acepta el modo demo
     */
    private boolean confirmDemoMode() {
        LicenseManager licenseManager = LicenseManager.getInstance();

        if (!licenseManager.isDemoMode()) {
            showLicenseError();
            return false;
        }

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Modo Demo");
        alert.setHeaderText("Licencia no encontrada");
        alert.setContentText(
                "La aplicación se ejecutará en modo demo.\n\n" +
                        "En este modo puede usar todas las funcionalidades sin restricciones.\n\n" +
                        "Machine ID: " + licenseManager.getMachineId() + "\n\n" +
                        "Para obtener una licencia, contacte al proveedor."
        );
        alert.getDialogPane().setMinWidth(460);
        alert.getDialogPane().setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);

        alert.getButtonTypes().clear();
        alert.getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }

    /**
     * Muestra error de licencia inválida.
     */
    private void showLicenseError() {
        DialogUtils.alert(Alert.AlertType.ERROR, "Error de Licencia",
                "Licencia inválida o expirada",
                "No se pudo validar la licencia del software.\n\n" +
                "Por favor, contacte al administrador para obtener una licencia válida.");
    }

    /**
     * Carga la vista principal del scanner.
     *
     * @param primaryStage Ventana principal
     * @throws IOException Si no se puede cargar el FXML
     */
    private void loadMainView(Stage primaryStage) throws IOException {
        logger.info("Cargando vista principal...");

        // Cargar FXML
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/scanner-view.fxml"));
        Parent root = loader.load();

        // Crear escena
        Scene scene = new Scene(root, WINDOW_WIDTH, WINDOW_HEIGHT);

        // Cargar estilos CSS
        String cssPath = getClass().getResource("/css/styles.css").toExternalForm();
        scene.getStylesheets().add(cssPath);

        // Configurar ventana
        primaryStage.setTitle(APP_TITLE);
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(800);
        primaryStage.setMinHeight(600);

        // Intentar cargar ícono (si existe)
        try {
            primaryStage.getIcons().add(new Image(getClass().getResourceAsStream("/images/icon.png")));
        } catch (Exception e) {
            logger.debug("No se encontró ícono de aplicación");
        }

        // Manejar cierre de ventana
        primaryStage.setOnCloseRequest(event -> {
            logger.info("Cerrando aplicación...");
            cleanup();
        });

        // Mostrar ventana y maximizar después del render
        primaryStage.show();
        Platform.runLater(() -> primaryStage.setMaximized(true));

        logger.info("Vista principal cargada");
    }

    /**
     * Limpia recursos al cerrar la aplicación.
     */
    private void cleanup() {
        if (apiServer != null) {
            apiServer.stop();
        }
        try {
            DatabaseConnection.getInstance().closeConnection();
            logger.info("Conexión a base de datos cerrada");
        } catch (Exception e) {
            logger.error("Error al cerrar conexión", e);
        }
    }

    /**
     * Muestra un error fatal y cierra la aplicación.
     *
     * @param title   Título del error
     * @param message Mensaje de error
     */
    private void showFatalError(String title, String message) {
        DialogUtils.alert(Alert.AlertType.ERROR, "Error Fatal", title,
                message + "\n\nLa aplicación se cerrará.");
    }

    /**
     * Método llamado cuando la aplicación se detiene.
     */
    @Override
    public void stop() {
        logger.info("Aplicación detenida");
        cleanup();
    }
}
