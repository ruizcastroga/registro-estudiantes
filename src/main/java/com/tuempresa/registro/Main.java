package com.tuempresa.registro;

import com.tuempresa.registro.dao.DatabaseConnection;
import com.tuempresa.registro.utils.LicenseManager;
import com.tuempresa.registro.utils.SecurityManager;
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

            // Verificar configuración inicial de contraseña
            if (!checkPasswordSetup()) {
                logger.info("Usuario canceló configuración de contraseña. Cerrando aplicación.");
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
     * Verifica si es necesario configurar la contraseña inicial.
     * Si no hay contraseña configurada, muestra el diálogo de configuración.
     *
     * @return true si la contraseña está configurada o se configuró exitosamente
     */
    private boolean checkPasswordSetup() {
        SecurityManager securityManager = SecurityManager.getInstance();

        if (securityManager.isPasswordConfigured()) {
            logger.info("Contraseña del sistema ya configurada");
            return true;
        }

        logger.info("Primera ejecución - solicitando configuración de contraseña");
        return showPasswordSetupDialog(securityManager);
    }

    /**
     * Muestra el diálogo para configurar la contraseña inicial.
     *
     * @param securityManager Gestor de seguridad
     * @return true si se configuró la contraseña exitosamente
     */
    private boolean showPasswordSetupDialog(SecurityManager securityManager) {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Configuración Inicial");
        dialog.setHeaderText("Bienvenido al Sistema de Registro de Estudiantes");

        ButtonType configureButtonType = new ButtonType("Configurar", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(configureButtonType, ButtonType.CANCEL);

        VBox content = new VBox(15);
        content.setPadding(new Insets(20));

        Label infoLabel = new Label(
                "Esta es la primera vez que se ejecuta el sistema.\n" +
                "Por favor, configure una contraseña de administrador.\n\n" +
                "Esta contraseña se requerirá para:\n" +
                "• Agregar nuevos estudiantes\n" +
                "• Editar información existente\n" +
                "• Eliminar registros\n" +
                "• Importar datos desde CSV"
        );
        infoLabel.setWrapText(true);

        Label passwordLabel = new Label("Nueva contraseña (mínimo 4 caracteres):");
        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Ingrese contraseña");
        passwordField.setPrefWidth(300);

        Label confirmLabel = new Label("Confirmar contraseña:");
        PasswordField confirmField = new PasswordField();
        confirmField.setPromptText("Repita la contraseña");
        confirmField.setPrefWidth(300);

        Label errorLabel = new Label();
        errorLabel.setStyle("-fx-text-fill: red;");

        content.getChildren().addAll(
                infoLabel,
                new Separator(),
                passwordLabel, passwordField,
                confirmLabel, confirmField,
                errorLabel
        );

        dialog.getDialogPane().setContent(content);

        // Deshabilitar botón hasta que haya entrada válida
        javafx.scene.Node configureButton = dialog.getDialogPane().lookupButton(configureButtonType);
        configureButton.setDisable(true);

        // Validar campos en tiempo real
        passwordField.textProperty().addListener((obs, oldVal, newVal) -> {
            boolean valid = newVal.length() >= 4 && newVal.equals(confirmField.getText());
            configureButton.setDisable(!valid);
            if (!newVal.equals(confirmField.getText()) && !confirmField.getText().isEmpty()) {
                errorLabel.setText("Las contraseñas no coinciden");
            } else if (newVal.length() < 4 && newVal.length() > 0) {
                errorLabel.setText("La contraseña debe tener al menos 4 caracteres");
            } else {
                errorLabel.setText("");
            }
        });

        confirmField.textProperty().addListener((obs, oldVal, newVal) -> {
            boolean valid = passwordField.getText().length() >= 4 && passwordField.getText().equals(newVal);
            configureButton.setDisable(!valid);
            if (!newVal.equals(passwordField.getText())) {
                errorLabel.setText("Las contraseñas no coinciden");
            } else {
                errorLabel.setText("");
            }
        });

        Platform.runLater(passwordField::requestFocus);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == configureButtonType) {
                return passwordField.getText();
            }
            return null;
        });

        Optional<String> result = dialog.showAndWait();

        if (result.isPresent()) {
            String password = result.get();
            if (securityManager.setPassword(password)) {
                Alert successAlert = new Alert(Alert.AlertType.INFORMATION);
                successAlert.setTitle("Configuración Completada");
                successAlert.setHeaderText(null);
                successAlert.setContentText("La contraseña ha sido configurada exitosamente.\n" +
                        "Recuerde esta contraseña para futuras operaciones.");
                successAlert.showAndWait();
                logger.info("Contraseña del sistema configurada exitosamente");
                return true;
            } else {
                showFatalError("Error de Configuración", "No se pudo guardar la contraseña.");
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

        alert.getButtonTypes().clear();
        alert.getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }

    /**
     * Muestra error de licencia inválida.
     */
    private void showLicenseError() {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error de Licencia");
        alert.setHeaderText("Licencia inválida o expirada");
        alert.setContentText(
                "No se pudo validar la licencia del software.\n\n" +
                        "Por favor, contacte al administrador para obtener una licencia válida."
        );
        alert.showAndWait();
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

        // Mostrar ventana en pantalla completa
        primaryStage.setMaximized(true);
        primaryStage.show();

        logger.info("Vista principal cargada");
    }

    /**
     * Limpia recursos al cerrar la aplicación.
     */
    private void cleanup() {
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
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error Fatal");
        alert.setHeaderText(title);
        alert.setContentText(message + "\n\nLa aplicación se cerrará.");
        alert.showAndWait();
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
