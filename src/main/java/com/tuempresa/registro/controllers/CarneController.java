package com.tuempresa.registro.controllers;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.oned.Code128Writer;
import com.tuempresa.registro.dao.DatabaseConnection;
import com.tuempresa.registro.models.StaffMember;
import com.tuempresa.registro.models.Student;
import com.tuempresa.registro.services.StaffService;
import com.tuempresa.registro.services.StudentService;
import com.tuempresa.registro.utils.SecurityManager;
import com.tuempresa.registro.utils.SessionManager;
import com.tuempresa.registro.models.AdminUser;
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
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.geometry.Insets;
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

    // --- Session bar ---
    @FXML private HBox sessionBar;
    @FXML private Label sessionUserLabel;
    @FXML private Label sessionRoleLabel;
    @FXML private Label sessionTimerLabel;
    @FXML private Button sessionLoginBtn;
    @FXML private Button sessionLogoutBtn;

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

    // --- Type selector ---
    @FXML private ToggleButton typeStudentBtn;
    @FXML private ToggleButton typeVisitorBtn;
    @FXML private ToggleButton typeStaffBtn;
    @FXML private Label searchSectionLabel;

    // --- Form sections ---
    @FXML private VBox studentFormSection;
    @FXML private VBox visitorFormSection;
    @FXML private VBox staffFormSection;
    @FXML private VBox photoSection;

    // --- Visitor form fields ---
    @FXML private TextField visitorBadgeCodeField;

    // --- Staff form fields ---
    @FXML private TextField staffCedulaField;
    @FXML private TextField staffNombreField;
    @FXML private TextField staffApellidoField;
    @FXML private TextField staffDepartamentoField;
    @FXML private ComboBox<String> staffEstadoCombo;

    // --- Staff search list ---
    @FXML private ListView<StaffMember> staffSearchResultsList;

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
    private StaffService staffService;
    private SecurityManager securityManager;
    private SessionManager sessionManager;

    // --- Estado ---
    private ObservableList<Student> searchResults;
    private ObservableList<StaffMember> staffSearchResults;
    // Estudiante actualmente cargado (null = modo nuevo)
    private Student currentStudent;
    private StaffMember currentStaff;
    // Foto seleccionada pendiente de guardar (modo nuevo)
    private File pendingPhotoFile;
    // Modo: true = editando datos de un estudiante existente (solo lectura)
    private boolean isExistingStudent = false;

    enum CardType { STUDENT, VISITOR, STAFF }
    private CardType currentCardType = CardType.STUDENT;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        logger.info("Inicializando CarneController");

        studentService = new StudentService();
        securityManager = SecurityManager.getInstance();
        sessionManager = SessionManager.getInstance();

        searchResults = FXCollections.observableArrayList();
        searchResultsList.setItems(searchResults);

        staffService = new StaffService();
        staffSearchResults = FXCollections.observableArrayList();
        staffSearchResultsList.setItems(staffSearchResults);
        staffSearchResultsList.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(StaffMember s, boolean empty) {
                super.updateItem(s, empty);
                setText(empty || s == null ? null : s.getFullName() + "  |  " + s.getIdNumber()
                        + (s.getDepartment() != null && !s.getDepartment().isEmpty() ? "  —  " + s.getDepartment() : ""));
            }
        });
        staffSearchResultsList.getSelectionModel().selectedItemProperty().addListener(
            (obs, old, selected) -> { if (selected != null) loadExistingStaff(selected); });
        staffEstadoCombo.getItems().addAll("Activo", "Inactivo");
        staffEstadoCombo.setValue("Activo");

        setupSearchList();

        setupSessionBar();
        loadSchoolName();
        loadSchoolShield();
        setFormEditable(false);

        logger.info("CarneController inicializado");
    }

    // ============================================================
    //  Session bar
    // ============================================================

    private void setupSessionBar() {
        if (sessionBar == null) return;
        SessionManager sm = SessionManager.getInstance();
        sm.sessionActiveProperty().addListener((obs, oldVal, newVal) -> {
            sessionBar.setVisible(true);
            sessionBar.setManaged(true);
            if (newVal) {
                sessionUserLabel.setText("Usuario: " + sm.getCurrentUser().getUsername());
                sessionRoleLabel.setText("Rol: " + sm.getCurrentUser().getRole());
                sessionLoginBtn.setVisible(false); sessionLoginBtn.setManaged(false);
                sessionLogoutBtn.setVisible(true); sessionLogoutBtn.setManaged(true);
            } else {
                sessionUserLabel.setText(""); sessionRoleLabel.setText("");
                sessionLoginBtn.setVisible(true); sessionLoginBtn.setManaged(true);
                sessionLogoutBtn.setVisible(false); sessionLogoutBtn.setManaged(false);
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
        sessionBar.setVisible(true);
        sessionBar.setManaged(true);
        if (sm.isSessionActive()) {
            sessionUserLabel.setText("Usuario: " + sm.getCurrentUser().getUsername());
            sessionRoleLabel.setText("Rol: " + sm.getCurrentUser().getRole());
            sessionTimerLabel.setText(sm.remainingTimeFormattedProperty().get());
            sessionLoginBtn.setVisible(false); sessionLoginBtn.setManaged(false);
            sessionLogoutBtn.setVisible(true); sessionLogoutBtn.setManaged(true);
        }
    }

    @FXML
    private void onLogin() {
        Dialog<String[]> dialog = new Dialog<>();
        dialog.setTitle("Iniciar Sesión");
        dialog.setHeaderText("Ingrese sus credenciales");
        ButtonType loginType = new ButtonType("Ingresar", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(loginType, ButtonType.CANCEL);

        javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
        grid.setHgap(10); grid.setVgap(10);
        grid.setPadding(new Insets(20));
        TextField userField = new TextField(); userField.setPromptText("Usuario");
        PasswordField passField = new PasswordField(); passField.setPromptText("Contraseña");
        Label errorLabel = new Label(); errorLabel.setStyle("-fx-text-fill: red;");
        grid.add(new Label("Usuario:"), 0, 0); grid.add(userField, 1, 0);
        grid.add(new Label("Contraseña:"), 0, 1); grid.add(passField, 1, 1);
        grid.add(errorLabel, 0, 2, 2, 1);
        dialog.getDialogPane().setContent(grid);
        Platform.runLater(userField::requestFocus);

        dialog.setResultConverter(btn -> btn == loginType ?
                new String[]{userField.getText(), passField.getText()} : null);

        while (true) {
            Optional<String[]> result = dialog.showAndWait();
            if (result.isEmpty()) return;
            String[] creds = result.get();
            Optional<AdminUser> authResult = securityManager.authenticate(creds[0], creds[1]);
            if (authResult.isPresent()) {
                int timeout = sessionManager.getTimeoutMinutes();
                sessionManager.startSession(authResult.get(), timeout);
                showAlert(Alert.AlertType.INFORMATION, "Sesión Iniciada",
                    "Usuario " + creds[0] + " logueado correctamente.\n" +
                    "Tu sesión estará activa por " + timeout + " minutos.\n" +
                    "Si terminas antes, recuerda cerrar la sesión para cuidar los datos.");
                return;
            }
            errorLabel.setText("Credenciales incorrectas. Intente de nuevo.");
        }
    }

    @FXML
    private void onLogout() {
        SessionManager.getInstance().endSession();
    }

    private boolean requireSession() {
        if (!sessionManager.isSessionActive()) {
            showAlert(Alert.AlertType.WARNING, "Sesión Requerida",
                "Debe iniciar sesión para realizar esta operación.");
            return false;
        }
        return true;
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

        // Búsqueda en tiempo real con textProperty (más robusto que onKeyReleased)
        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            clearSearchButton.setVisible(newVal != null && !newVal.isEmpty());
            if (newVal == null || newVal.trim().isEmpty()) {
                searchResults.clear();
                staffSearchResults.clear();
                return;
            }
            if (currentCardType == CardType.STAFF) {
                List<StaffMember> results = staffService.searchStaff(newVal.trim());
                staffSearchResults.setAll(results);
                if (results.size() == 1) {
                    Platform.runLater(() -> staffSearchResultsList.getSelectionModel().selectFirst());
                }
            } else {
                List<Student> results = studentService.searchStudents(newVal.trim());
                searchResults.setAll(results);
                // Auto-seleccionar si hay exactamente un resultado
                if (results.size() == 1) {
                    Platform.runLater(() -> searchResultsList.getSelectionModel().selectFirst());
                }
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
    //  Selector de tipo
    // ============================================================

    @FXML
    private void onTypeChanged() {
        if (typeStudentBtn.isSelected()) {
            currentCardType = CardType.STUDENT;
        } else if (typeVisitorBtn.isSelected()) {
            currentCardType = CardType.VISITOR;
        } else {
            currentCardType = CardType.STAFF;
        }
        if (!typeStudentBtn.isSelected() && !typeVisitorBtn.isSelected() && !typeStaffBtn.isSelected()) {
            typeStudentBtn.setSelected(true);
            currentCardType = CardType.STUDENT;
        }
        studentFormSection.setVisible(currentCardType == CardType.STUDENT);
        studentFormSection.setManaged(currentCardType == CardType.STUDENT);
        visitorFormSection.setVisible(currentCardType == CardType.VISITOR);
        visitorFormSection.setManaged(currentCardType == CardType.VISITOR);
        staffFormSection.setVisible(currentCardType == CardType.STAFF);
        staffFormSection.setManaged(currentCardType == CardType.STAFF);
        if (photoSection != null) {
            photoSection.setVisible(currentCardType == CardType.STUDENT);
            photoSection.setManaged(currentCardType == CardType.STUDENT);
        }
        searchResultsList.setVisible(currentCardType == CardType.STUDENT);
        searchResultsList.setManaged(currentCardType == CardType.STUDENT);
        staffSearchResultsList.setVisible(currentCardType == CardType.STAFF);
        staffSearchResultsList.setManaged(currentCardType == CardType.STAFF);
        searchSectionLabel.setText(currentCardType == CardType.STUDENT ? "Buscar estudiante existente"
                : currentCardType == CardType.STAFF ? "Buscar personal existente"
                : "Visitante (sin búsqueda)");
        currentStudent = null;
        currentStaff = null;
        isExistingStudent = false;
        clearCardPreview();
        clearValidation();
        searchResults.clear();
        if (staffSearchResults != null) staffSearchResults.clear();
        searchField.clear();
        clearSearchButton.setVisible(false);
        printButton.setDisable(true);
        setStatus(currentCardType == CardType.VISITOR ? "Ingrese el código del carné de visitante"
                : currentCardType == CardType.STAFF ? "Busque personal o complete los datos"
                : "Busque un estudiante o cree un nuevo carné");
        modeLabel.setText("");
        formTitle.setText("Datos del Carné");
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
            staffSearchResults.clear();
            return;
        }

        if (currentCardType == CardType.STAFF) {
            List<StaffMember> results = staffService.searchStaff(term);
            staffSearchResults.setAll(results);
        } else {
            List<Student> results = studentService.searchStudents(term);
            searchResults.setAll(results);
        }
    }

    @FXML
    private void onClearSearch() {
        searchField.clear();
        searchResults.clear();
        staffSearchResults.clear();
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

    private void loadExistingStaff(StaffMember staff) {
        currentStaff = staff;
        isExistingStudent = false;
        staffCedulaField.setText(staff.getIdNumber() != null ? staff.getIdNumber() : staff.getBarcode());
        staffNombreField.setText(staff.getFirstName());
        staffApellidoField.setText(staff.getLastName());
        staffDepartamentoField.setText(staff.getDepartment() != null ? staff.getDepartment() : "");
        staffEstadoCombo.setValue(staff.isActive() ? "Activo" : "Inactivo");
        staffCedulaField.setEditable(false);
        staffNombreField.setEditable(false);
        staffApellidoField.setEditable(false);
        staffDepartamentoField.setEditable(false);
        staffEstadoCombo.setDisable(true);
        formTitle.setText("Carné de personal existente");
        modeLabel.setText("(datos de solo lectura)");
        updateCardPreview();
        printButton.setDisable(false);
        setStatus("Personal cargado: " + staff.getFullName());
        clearValidation();
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

        if (visitorBadgeCodeField != null) visitorBadgeCodeField.clear();
        if (staffCedulaField != null) { staffCedulaField.clear(); staffCedulaField.setEditable(true); }
        if (staffNombreField != null) { staffNombreField.clear(); staffNombreField.setEditable(true); }
        if (staffApellidoField != null) { staffApellidoField.clear(); staffApellidoField.setEditable(true); }
        if (staffDepartamentoField != null) { staffDepartamentoField.clear(); staffDepartamentoField.setEditable(true); }
        if (staffEstadoCombo != null) { staffEstadoCombo.setValue("Activo"); staffEstadoCombo.setDisable(false); }
        if (staffSearchResultsList != null) staffSearchResultsList.getSelectionModel().clearSelection();
        currentStaff = null;
        modeLabel.setText(currentCardType == CardType.STUDENT ? "(nuevo estudiante)"
                : currentCardType == CardType.VISITOR ? "(nuevo visitante)"
                : "(nuevo personal)");

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
        updateCardPreview();
        if (currentCardType == CardType.VISITOR) {
            boolean hasData = visitorBadgeCodeField != null && !visitorBadgeCodeField.getText().trim().isEmpty();
            printButton.setDisable(!hasData);
        } else if (currentCardType == CardType.STAFF && currentStaff == null) {
            boolean hasData = staffCedulaField != null && !staffCedulaField.getText().trim().isEmpty()
                    && staffNombreField != null && !staffNombreField.getText().trim().isEmpty()
                    && staffApellidoField != null && !staffApellidoField.getText().trim().isEmpty();
            printButton.setDisable(!hasData);
        } else if (currentCardType == CardType.STUDENT && !isExistingStudent) {
            boolean hasData = !cedulaField.getText().trim().isEmpty()
                    && !firstNameField.getText().trim().isEmpty()
                    && !lastNameField.getText().trim().isEmpty();
            printButton.setDisable(!hasData);
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
        if (currentCardType == CardType.VISITOR) {
            String code = visitorBadgeCodeField != null ? visitorBadgeCodeField.getText().trim() : "";
            cardCedula.setText("Carné:  " + (code.isEmpty() ? "—" : code));
            cardNombre.setText("VISITANTE");
            cardApellido.setText("");
            cardGrado.setText("");
            if (!code.isEmpty()) {
                Image barcode = generateBarcode(code, 195, 60);
                if (barcode != null) { cardBarcodeView.setImage(barcode); cardBarcodeText.setText(code); }
            } else { cardBarcodeView.setImage(null); cardBarcodeText.setText(""); }
        } else if (currentCardType == CardType.STAFF) {
            String cedula = staffCedulaField != null ? staffCedulaField.getText().trim() : "";
            String nombre = staffNombreField != null ? staffNombreField.getText().trim() : "";
            String apellido = staffApellidoField != null ? staffApellidoField.getText().trim() : "";
            String depto = staffDepartamentoField != null ? staffDepartamentoField.getText().trim() : "";
            cardCedula.setText("Cédula:  " + (cedula.isEmpty() ? "—" : cedula));
            cardNombre.setText("Nombre:  " + (nombre.isEmpty() ? "—" : nombre));
            cardApellido.setText("Apellido:  " + (apellido.isEmpty() ? "—" : apellido));
            cardGrado.setText("Depto:  " + (depto.isEmpty() ? "—" : depto));
            if (!cedula.isEmpty()) {
                Image barcode = generateBarcode(cedula, 195, 60);
                if (barcode != null) { cardBarcodeView.setImage(barcode); cardBarcodeText.setText(cedula); }
            } else { cardBarcodeView.setImage(null); cardBarcodeText.setText(""); }
        } else {
            // STUDENT — existing logic
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
        if (currentCardType == CardType.VISITOR) {
            // Visitor: just validate badge code and print (no DB save needed)
            if (visitorBadgeCodeField.getText().trim().isEmpty()) {
                showValidation("El código del carné es obligatorio");
                visitorBadgeCodeField.requestFocus();
                return;
            }
            clearValidation();
            printCard();
            return;
        }
        if (currentCardType == CardType.STAFF) {
            if (currentStaff == null) {
                // Validate minimum fields for new staff card (just print, don't save)
                if (staffCedulaField.getText().trim().isEmpty()) {
                    showValidation("La cédula es obligatoria"); return;
                }
                if (staffNombreField.getText().trim().isEmpty()) {
                    showValidation("El nombre es obligatorio"); return;
                }
                if (staffApellidoField.getText().trim().isEmpty()) {
                    showValidation("El apellido es obligatorio"); return;
                }
            }
            clearValidation();
            printCard();
            return;
        }
        // STUDENT — existing logic
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
        if (!requireSession()) return false;
        if (!sessionManager.canModifyData()) {
            showAlert(Alert.AlertType.WARNING, "Sin Permisos",
                "No tiene permisos para guardar estudiantes.");
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
            student.setCreatedBy(sessionManager.getActiveUsername());
            student.setUpdatedBy(sessionManager.getActiveUsername());

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

    // ============================================================
    //  Navegación
    // ============================================================

    @FXML
    private void onBackToScanner() {
        navigateTo("/fxml/scanner-view.fxml", "Scanner");
    }

    @FXML
    private void onManageStudents() {
        navigateTo("/fxml/student-crud.fxml", "Gestión de Estudiantes");
    }

    @FXML
    private void onManageVisitors() {
        navigateTo("/fxml/visitor-view.fxml", "Control de Visitantes");
    }

    @FXML
    private void onManageStaff() {
        navigateTo("/fxml/staff-admin.fxml", "Gestión de Personal");
    }

    @FXML
    private void onManageSettings() {
        navigateTo("/fxml/settings-view.fxml", "Ajustes");
    }

    private void navigateTo(String fxmlPath, String title) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent root = loader.load();

            Stage stage = (Stage) searchField.getScene().getWindow();
            Scene scene = new Scene(root);
            scene.getStylesheets().add(
                    getClass().getResource("/css/styles.css").toExternalForm());

            stage.setMaximized(false);
            stage.setScene(scene);
            stage.setTitle(title);
            Platform.runLater(() -> stage.setMaximized(true));
        } catch (IOException e) {
            logger.error("Error al navegar a {}", fxmlPath, e);
        }
    }
}
