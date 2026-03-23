package com.tuempresa.registro.controllers;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.oned.Code128Writer;
import com.tuempresa.registro.dao.DatabaseConnection;
import com.tuempresa.registro.models.Student;
import com.tuempresa.registro.services.StudentService;
import com.tuempresa.registro.utils.SecurityManager;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.print.PrinterJob;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

/**
 * Controlador del módulo Creador de Carné.
 * Permite buscar un estudiante existente o ingresar datos nuevos
 * para generar e imprimir el carné físico (CR80).
 */
public class CarneController implements Initializable {

    private static final Logger logger = LoggerFactory.getLogger(CarneController.class);

    // Directorio de datos de la aplicación
    private static final String APP_DATA_DIR =
            System.getProperty("user.home") + File.separator + ".registro-estudiantes";

    // --- Componentes del panel izquierdo (formulario) ---
    @FXML private TextField searchField;
    @FXML private Button clearSearchButton;
    @FXML private ListView<Student> searchResultsList;
    @FXML private Label modeLabel;
    @FXML private Label formTitle;
    @FXML private TextField cedulaField;
    @FXML private TextField firstNameField;
    @FXML private TextField lastNameField;
    @FXML private TextField gradeField;
    @FXML private ImageView photoPreview;
    @FXML private Label photoPickerLabel;
    @FXML private Button removePhotoButton;
    @FXML private Label validationLabel;
    @FXML private Button printButton;
    @FXML private Label statusLabel;

    // --- Componentes del carné (panel derecho) ---
    @FXML private VBox cardPane;
    @FXML private Label cardSchoolName;
    @FXML private Label cardCedula;
    @FXML private Label cardNombre;
    @FXML private Label cardApellido;
    @FXML private Label cardGrado;
    @FXML private ImageView cardPhotoView;
    @FXML private Label cardPhotoPlaceholder;
    @FXML private ImageView cardBarcodeView;
    @FXML private Label cardBarcodeText;
    @FXML private ImageView cardShieldView;
    @FXML private Label cardShieldPlaceholder;

    // --- Servicios ---
    private StudentService studentService;
    private SecurityManager securityManager;

    // --- Estado ---
    private ObservableList<Student> searchResults;
    // Estudiante actualmente cargado (null = modo nuevo)
    private Student currentStudent;
    // Foto seleccionada pendiente de guardar (modo nuevo)
    private File pendingPhotoFile;
    // Modo: true = editando datos de un estudiante existente (solo lectura)
    private boolean isExistingStudent = false;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        logger.info("Inicializando CarneController");

        studentService = new StudentService();
        securityManager = SecurityManager.getInstance();

        searchResults = FXCollections.observableArrayList();
        searchResultsList.setItems(searchResults);

        setupSearchList();
        loadSchoolName();
        loadSchoolShield();
        setFormEditable(false);

        logger.info("CarneController inicializado");
    }

    // ============================================================
    //  Configuración inicial
    // ============================================================

    /**
     * Configura el ListView de resultados de búsqueda para mostrar nombre + cédula.
     */
    private void setupSearchList() {
        searchResultsList.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Student s, boolean empty) {
                super.updateItem(s, empty);
                if (empty || s == null) {
                    setText(null);
                } else {
                    setText(s.getFullName() + "  |  " + s.getBarcode()
                            + (s.getGrade() != null && !s.getGrade().isEmpty()
                                ? "  —  " + s.getGrade() : ""));
                }
            }
        });

        searchResultsList.getSelectionModel().selectedItemProperty().addListener(
                (obs, old, selected) -> {
                    if (selected != null) {
                        loadExistingStudent(selected);
                    }
                });
    }

    /**
     * Lee el nombre del colegio desde app_config.
     */
    private void loadSchoolName() {
        String name = getConfigValue("school_name");
        if (name != null && !name.isBlank()) {
            cardSchoolName.setText(name);
        }
    }

    /**
     * Lee un valor de la tabla app_config.
     */
    private String getConfigValue(String key) {
        String sql = "SELECT value FROM app_config WHERE key = ?";
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, key);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("value");
                }
            }
        } catch (SQLException e) {
            logger.warn("No se pudo leer app_config key={}: {}", key, e.getMessage());
        }
        return null;
    }

    /**
     * Carga el escudo del colegio desde el directorio de configuración.
     */
    private void loadSchoolShield() {
        try {
            File shieldFile = new File(APP_DATA_DIR, "school_shield.png");
            if (shieldFile.exists()) {
                try (InputStream is = new FileInputStream(shieldFile)) {
                    Image img = new Image(is);
                    cardShieldView.setImage(img);
                    cardShieldView.setVisible(true);
                    cardShieldPlaceholder.setVisible(false);
                }
            }
        } catch (Exception e) {
            logger.warn("No se pudo cargar el escudo: {}", e.getMessage());
        }
    }

    // ============================================================
    //  Búsqueda
    // ============================================================

    @FXML
    private void onSearch() {
        String term = searchField.getText().trim();
        clearSearchButton.setVisible(!term.isEmpty());

        if (term.isEmpty()) {
            searchResults.clear();
            return;
        }

        List<Student> results = studentService.searchStudents(term);
        searchResults.setAll(results);
    }

    @FXML
    private void onClearSearch() {
        searchField.clear();
        searchResults.clear();
        clearSearchButton.setVisible(false);
    }

    // ============================================================
    //  Carga de datos
    // ============================================================

    /**
     * Carga un estudiante existente seleccionado de la lista.
     */
    private void loadExistingStudent(Student student) {
        currentStudent = student;
        isExistingStudent = true;
        pendingPhotoFile = null;

        cedulaField.setText(student.getBarcode());
        firstNameField.setText(student.getFirstName());
        lastNameField.setText(student.getLastName());
        gradeField.setText(student.getGrade() != null ? student.getGrade() : "");

        setFormEditable(false);
        formTitle.setText("Carné de estudiante existente");
        modeLabel.setText("(datos de solo lectura — editar en Gestionar Estudiantes)");

        // Cargar foto si existe
        loadStudentPhoto(student);

        updateCardPreview();
        printButton.setDisable(false);
        setStatus("Estudiante cargado: " + student.getFullName());
        clearValidation();
    }

    /**
     * Carga la foto del estudiante si tiene una guardada.
     */
    private void loadStudentPhoto(Student student) {
        cardPhotoView.setImage(null);
        cardPhotoPlaceholder.setVisible(true);
        photoPreview.setImage(null);
        photoPickerLabel.setVisible(true);
        removePhotoButton.setVisible(false);

        if (student.getPhotoPath() != null && !student.getPhotoPath().isBlank()) {
            File photoFile = new File(student.getPhotoPath());
            if (photoFile.exists()) {
                try (InputStream is = new FileInputStream(photoFile)) {
                    Image img = new Image(is);
                    cardPhotoView.setImage(img);
                    cardPhotoPlaceholder.setVisible(false);
                    photoPreview.setImage(img);
                    photoPickerLabel.setVisible(false);
                    removePhotoButton.setVisible(false);
                } catch (Exception e) {
                    logger.warn("No se pudo cargar la foto del estudiante: {}", e.getMessage());
                }
            }
        }
    }

    // ============================================================
    //  Acciones del formulario
    // ============================================================

    /** Limpia todo para crear un nuevo carné. */
    @FXML
    private void onNewStudent() {
        currentStudent = null;
        isExistingStudent = false;
        pendingPhotoFile = null;

        cedulaField.clear();
        firstNameField.clear();
        lastNameField.clear();
        gradeField.clear();

        cardPhotoView.setImage(null);
        cardPhotoPlaceholder.setVisible(true);
        photoPreview.setImage(null);
        photoPickerLabel.setVisible(true);
        removePhotoButton.setVisible(false);

        searchResultsList.getSelectionModel().clearSelection();

        setFormEditable(true);
        formTitle.setText("Nuevo Carné");
        modeLabel.setText("(nuevo estudiante)");
        printButton.setDisable(true);

        clearCardPreview();
        clearValidation();
        setStatus("Complete los datos para el nuevo carné");

        Platform.runLater(() -> cedulaField.requestFocus());
    }

    /** Cancela y limpia el formulario. */
    @FXML
    private void onCancel() {
        onNewStudent();
        setStatus("Operación cancelada");
    }

    /** Llamado cuando el usuario escribe en cualquier campo del formulario. */
    @FXML
    private void onFormFieldChanged() {
        if (!isExistingStudent) {
            updateCardPreview();
            boolean hasMinimumData = !cedulaField.getText().trim().isEmpty()
                    && !firstNameField.getText().trim().isEmpty()
                    && !lastNameField.getText().trim().isEmpty();
            printButton.setDisable(!hasMinimumData);
        }
    }

    /**
     * Abre el selector de foto del estudiante.
     * Tanto para estudiantes existentes como nuevos.
     */
    @FXML
    private void onSelectPhoto() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Seleccionar Foto del Estudiante");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Imágenes", "*.png", "*.jpg", "*.jpeg"),
                new FileChooser.ExtensionFilter("PNG", "*.png"),
                new FileChooser.ExtensionFilter("JPEG", "*.jpg", "*.jpeg")
        );

        Stage stage = (Stage) searchField.getScene().getWindow();
        File selected = chooser.showOpenDialog(stage);

        if (selected != null) {
            try (InputStream is = new FileInputStream(selected)) {
                Image img = new Image(is);
                photoPreview.setImage(img);
                photoPickerLabel.setVisible(false);
                removePhotoButton.setVisible(true);

                // Actualizar la vista previa del carné
                try (InputStream is2 = new FileInputStream(selected)) {
                    cardPhotoView.setImage(new Image(is2));
                    cardPhotoPlaceholder.setVisible(false);
                }

                pendingPhotoFile = selected;

                // Si es estudiante existente, guardar la foto inmediatamente
                if (isExistingStudent && currentStudent != null) {
                    savePhotoForStudent(currentStudent, selected);
                }

            } catch (Exception e) {
                logger.error("Error al cargar la foto seleccionada", e);
                setStatus("Error al cargar la foto");
            }
        }
    }

    /** Quita la foto seleccionada. */
    @FXML
    private void onRemovePhoto() {
        pendingPhotoFile = null;
        photoPreview.setImage(null);
        photoPickerLabel.setVisible(true);
        removePhotoButton.setVisible(false);
        cardPhotoView.setImage(null);
        cardPhotoPlaceholder.setVisible(true);
    }

    // ============================================================
    //  Guardar foto
    // ============================================================

    /**
     * Copia la foto al directorio de la app y actualiza el photo_path del estudiante en la BD.
     */
    private void savePhotoForStudent(Student student, File sourceFile) {
        try {
            File photosDir = new File(APP_DATA_DIR, "photos");
            if (!photosDir.exists()) {
                photosDir.mkdirs();
            }

            String ext = sourceFile.getName().contains(".")
                    ? sourceFile.getName().substring(sourceFile.getName().lastIndexOf('.'))
                    : ".jpg";
            File dest = new File(photosDir, "student_" + student.getId() + ext);

            Files.copy(sourceFile.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);

            student.setPhotoPath(dest.getAbsolutePath());
            studentService.updateStudent(student);
            logger.info("Foto guardada para estudiante {}: {}", student.getId(), dest.getAbsolutePath());

        } catch (Exception e) {
            logger.error("Error al guardar la foto del estudiante", e);
            setStatus("Error al guardar la foto: " + e.getMessage());
        }
    }

    // ============================================================
    //  Vista previa del carné
    // ============================================================

    /**
     * Actualiza todos los campos de la vista previa del carné con los datos del formulario.
     */
    private void updateCardPreview() {
        String cedula = cedulaField.getText().trim();
        String nombre = firstNameField.getText().trim();
        String apellido = lastNameField.getText().trim();
        String grado = gradeField.getText().trim();

        cardCedula.setText("Cédula:  " + (cedula.isEmpty() ? "—" : cedula));
        cardNombre.setText("Nombre:  " + (nombre.isEmpty() ? "—" : nombre));
        cardApellido.setText("Apellido:  " + (apellido.isEmpty() ? "—" : apellido));
        cardGrado.setText("Grado:  " + (grado.isEmpty() ? "—" : grado));

        // Generar código de barras si hay cédula
        if (!cedula.isEmpty()) {
            Image barcode = generateBarcode(cedula, 195, 60);
            if (barcode != null) {
                cardBarcodeView.setImage(barcode);
                cardBarcodeText.setText(cedula);
            }
        } else {
            cardBarcodeView.setImage(null);
            cardBarcodeText.setText("");
        }
    }

    /**
     * Limpia la vista previa del carné a su estado vacío.
     */
    private void clearCardPreview() {
        cardCedula.setText("Cédula:");
        cardNombre.setText("Nombre:");
        cardApellido.setText("Apellido:");
        cardGrado.setText("Grado:");
        cardBarcodeView.setImage(null);
        cardBarcodeText.setText("");
        cardPhotoView.setImage(null);
        cardPhotoPlaceholder.setVisible(true);
    }

    // ============================================================
    //  Generación de código de barras (ZXing Code 128)
    // ============================================================

    /**
     * Genera una imagen Code 128 a partir de un valor usando ZXing.
     * No requiere librerías Swing — usa PixelWriter de JavaFX directamente.
     */
    private Image generateBarcode(String value, int width, int height) {
        try {
            Code128Writer writer = new Code128Writer();
            BitMatrix matrix = writer.encode(value, BarcodeFormat.CODE_128, width, height);

            WritableImage image = new WritableImage(width, height);
            PixelWriter pw = image.getPixelWriter();

            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    pw.setColor(x, y, matrix.get(x, y) ? Color.BLACK : Color.WHITE);
                }
            }

            return image;

        } catch (Exception e) {
            logger.error("Error al generar código de barras para: {}", value, e);
            return null;
        }
    }

    // ============================================================
    //  Impresión
    // ============================================================

    /**
     * Imprime el carné.
     * Si es un estudiante nuevo, lo guarda en la BD antes de imprimir.
     */
    @FXML
    private void onPrint() {
        if (!isExistingStudent) {
            // Validar datos del nuevo estudiante
            if (!validateNewStudentForm()) {
                return;
            }
            // Guardar el nuevo estudiante (requiere contraseña)
            if (!saveNewStudent()) {
                return;
            }
        }

        printCard();
    }

    /**
     * Valida los campos del formulario para un nuevo estudiante.
     */
    private boolean validateNewStudentForm() {
        if (cedulaField.getText().trim().isEmpty()) {
            showValidation("La cédula es obligatoria");
            cedulaField.requestFocus();
            return false;
        }
        if (firstNameField.getText().trim().isEmpty()) {
            showValidation("El nombre es obligatorio");
            firstNameField.requestFocus();
            return false;
        }
        if (lastNameField.getText().trim().isEmpty()) {
            showValidation("El apellido es obligatorio");
            lastNameField.requestFocus();
            return false;
        }
        clearValidation();
        return true;
    }

    /**
     * Guarda el nuevo estudiante en la BD previo a imprimir.
     * Requiere contraseña de administrador.
     *
     * @return true si se guardó correctamente
     */
    private boolean saveNewStudent() {
        if (!verifyPasswordDialog("Guardar Nuevo Estudiante")) {
            return false;
        }

        try {
            Student student = new Student();
            student.setBarcode(cedulaField.getText().trim());
            student.setFirstName(firstNameField.getText().trim());
            student.setLastName(lastNameField.getText().trim());
            student.setGrade(gradeField.getText().trim());
            student.setMinor(true);
            student.setRequiresGuardian(true);
            student.setStatus("active");

            Student saved = studentService.saveStudent(student);

            // Guardar foto si se seleccionó
            if (pendingPhotoFile != null) {
                savePhotoForStudent(saved, pendingPhotoFile);
                saved = studentService.findById(saved.getId()).orElse(saved);
            }

            currentStudent = saved;
            isExistingStudent = true;
            pendingPhotoFile = null;
            setFormEditable(false);

            setStatus("Estudiante guardado: " + saved.getFullName());
            logger.info("Nuevo estudiante guardado desde Creador de Carné: {}", saved);
            return true;

        } catch (IllegalArgumentException e) {
            showValidation(e.getMessage());
            return false;
        } catch (SQLException e) {
            logger.error("Error al guardar el nuevo estudiante", e);
            showAlert(Alert.AlertType.ERROR, "Error al guardar",
                    "No se pudo guardar el estudiante: " + e.getMessage());
            return false;
        }
    }

    /**
     * Lanza el diálogo de impresión de JavaFX y envía el carné a la impresora.
     */
    private void printCard() {
        PrinterJob job = PrinterJob.createPrinterJob();

        if (job == null) {
            showAlert(Alert.AlertType.WARNING, "Sin impresora",
                    "No se encontró ninguna impresora disponible.");
            return;
        }

        boolean proceed = job.showPrintDialog(cardPane.getScene().getWindow());

        if (proceed) {
            // Tomar una instantánea del carné para imprimir a calidad de pantalla
            WritableImage snapshot = cardPane.snapshot(null, null);
            javafx.scene.image.ImageView printNode = new javafx.scene.image.ImageView(snapshot);

            double printWidth = job.getJobSettings().getPageLayout().getPrintableWidth();
            double printHeight = job.getJobSettings().getPageLayout().getPrintableHeight();

            // Escalar manteniendo el ratio CR80
            double scale = Math.min(printWidth / snapshot.getWidth(),
                                    printHeight / snapshot.getHeight());
            printNode.setFitWidth(snapshot.getWidth() * scale);
            printNode.setFitHeight(snapshot.getHeight() * scale);
            printNode.setPreserveRatio(true);

            boolean success = job.printPage(printNode);
            job.endJob();

            if (success) {
                setStatus("Carné impreso correctamente");
                logger.info("Carné impreso para: {}",
                        currentStudent != null ? currentStudent.getFullName() : cedulaField.getText());
            } else {
                setStatus("La impresión fue cancelada o falló");
            }
        }
    }

    // ============================================================
    //  Utilidades UI
    // ============================================================

    private void setFormEditable(boolean editable) {
        cedulaField.setEditable(editable);
        firstNameField.setEditable(editable);
        lastNameField.setEditable(editable);
        gradeField.setEditable(editable);

        String style = editable ? "" : "-fx-background-color: #f5f5f5;";
        cedulaField.setStyle(style);
        firstNameField.setStyle(style);
        lastNameField.setStyle(style);
        gradeField.setStyle(style);
    }

    private void setStatus(String message) {
        statusLabel.setText(message);
    }

    private void showValidation(String message) {
        validationLabel.setText(message);
    }

    private void clearValidation() {
        validationLabel.setText("");
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    private boolean verifyPasswordDialog(String action) {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Verificación de Seguridad");
        dialog.setHeaderText(action);

        ButtonType okType = new ButtonType("OK", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(okType, ButtonType.CANCEL);

        javafx.scene.layout.VBox content = new javafx.scene.layout.VBox(10);
        content.setPadding(new javafx.geometry.Insets(20));
        Label label = new Label("Ingresa la contraseña:");
        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Contraseña");
        content.getChildren().addAll(label, passwordField);
        dialog.getDialogPane().setContent(content);

        Platform.runLater(passwordField::requestFocus);

        dialog.setResultConverter(btn -> btn == okType ? passwordField.getText() : null);

        Optional<String> result = dialog.showAndWait();
        if (result.isPresent()) {
            if (securityManager.verifyPassword(result.get())) {
                return true;
            }
            showAlert(Alert.AlertType.ERROR, "Contraseña incorrecta",
                    "La contraseña ingresada no es correcta.");
        }
        return false;
    }

    // ============================================================
    //  Navegación
    // ============================================================

    @FXML
    private void onBackToScanner() {
        navigateTo("/fxml/scanner-view.fxml", "Scanner de Estudiantes");
    }

    @FXML
    private void onManageStudents() {
        navigateTo("/fxml/student-crud.fxml", "Gestión de Estudiantes");
    }

    private void navigateTo(String fxmlPath, String title) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent root = loader.load();

            Stage stage = (Stage) searchField.getScene().getWindow();
            Scene scene = new Scene(root);
            scene.getStylesheets().add(
                    getClass().getResource("/css/styles.css").toExternalForm());

            stage.setScene(scene);
            stage.setTitle(title);
            Platform.runLater(() -> stage.setMaximized(true));
        } catch (IOException e) {
            logger.error("Error al navegar a {}", fxmlPath, e);
        }
    }
}
