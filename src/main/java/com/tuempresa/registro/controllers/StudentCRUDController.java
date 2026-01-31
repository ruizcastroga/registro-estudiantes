package com.tuempresa.registro.controllers;

import com.tuempresa.registro.models.Guardian;
import com.tuempresa.registro.models.Student;
import com.tuempresa.registro.services.CsvImportService;
import com.tuempresa.registro.services.StudentService;
import com.tuempresa.registro.utils.SecurityManager;
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
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

/**
 * Controlador para la gestión CRUD de estudiantes.
 * Permite agregar, editar, eliminar y buscar estudiantes.
 * Incluye verificación de contraseña para operaciones sensibles.
 */
public class StudentCRUDController implements Initializable {

    private static final Logger logger = LoggerFactory.getLogger(StudentCRUDController.class);

    // Componentes de la tabla
    @FXML private TableView<Student> studentsTable;
    @FXML private TableColumn<Student, String> colBarcode;
    @FXML private TableColumn<Student, String> colFirstName;
    @FXML private TableColumn<Student, String> colLastName;
    @FXML private TableColumn<Student, String> colGrade;
    @FXML private TableColumn<Student, String> colRequiresGuardian;
    @FXML private TableColumn<Student, String> colStatus;

    // Componentes de búsqueda
    @FXML private TextField searchField;

    // Componentes del formulario
    @FXML private Label formTitle;
    @FXML private TextField barcodeField;
    @FXML private TextField firstNameField;
    @FXML private TextField lastNameField;
    @FXML private TextField gradeField;
    @FXML private CheckBox isMinorCheck;
    @FXML private CheckBox requiresGuardianCheck;
    @FXML private ComboBox<String> statusCombo;
    @FXML private ListView<String> guardiansListView;

    // Botones
    @FXML private Button editButton;
    @FXML private Button deleteButton;
    @FXML private Button removeGuardianButton;

    // Labels de estado
    @FXML private Label countLabel;
    @FXML private Label validationMessage;
    @FXML private Label statusMessage;

    // Servicios
    private StudentService studentService;
    private SecurityManager securityManager;
    private CsvImportService csvImportService;

    // Datos
    private ObservableList<Student> studentsList;
    private ObservableList<String> guardianDisplayList;
    private List<Guardian> currentGuardians;

    // Estado de edición
    private Student currentStudent;
    private boolean isEditMode = false;

    /**
     * Inicializa el controlador después de cargar el FXML.
     */
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        logger.info("Inicializando StudentCRUDController");

        // Inicializar servicios
        studentService = new StudentService();
        securityManager = SecurityManager.getInstance();
        csvImportService = new CsvImportService();

        // Inicializar listas
        studentsList = FXCollections.observableArrayList();
        guardianDisplayList = FXCollections.observableArrayList();
        currentGuardians = new ArrayList<>();

        // Configurar tabla
        setupTable();

        // Configurar ComboBox de estado
        setupStatusCombo();

        // Configurar lista de guardianes
        guardiansListView.setItems(guardianDisplayList);

        // Cargar datos
        loadStudents();

        // Configurar listeners
        setupListeners();

        // Estado inicial del formulario
        clearForm();

        logger.info("StudentCRUDController inicializado correctamente");
    }

    /**
     * Configura la tabla de estudiantes.
     */
    private void setupTable() {
        colBarcode.setCellValueFactory(new PropertyValueFactory<>("barcode"));
        colFirstName.setCellValueFactory(new PropertyValueFactory<>("firstName"));
        colLastName.setCellValueFactory(new PropertyValueFactory<>("lastName"));
        colGrade.setCellValueFactory(new PropertyValueFactory<>("grade"));

        colRequiresGuardian.setCellValueFactory(cellData -> {
            boolean requires = cellData.getValue().isRequiresGuardian();
            return new SimpleStringProperty(requires ? "Sí" : "No");
        });

        colStatus.setCellValueFactory(cellData -> {
            String status = cellData.getValue().getStatus();
            String displayStatus = "active".equals(status) ? "Activo" :
                    "inactive".equals(status) ? "Inactivo" : status;
            return new SimpleStringProperty(displayStatus);
        });

        studentsTable.setItems(studentsList);
        studentsTable.setPlaceholder(new Label("No hay estudiantes registrados"));
    }

    /**
     * Configura el ComboBox de estado.
     */
    private void setupStatusCombo() {
        statusCombo.getItems().addAll("Activo", "Inactivo", "Suspendido");
        statusCombo.setValue("Activo");
    }

    /**
     * Configura los listeners de eventos.
     */
    private void setupListeners() {
        studentsTable.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldSelection, newSelection) -> {
                    boolean hasSelection = newSelection != null;
                    editButton.setDisable(!hasSelection);
                    deleteButton.setDisable(!hasSelection);
                });

        guardiansListView.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldSelection, newSelection) -> {
                    removeGuardianButton.setDisable(newSelection == null);
                });

        isMinorCheck.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) {
                requiresGuardianCheck.setSelected(true);
            }
        });
    }

    /**
     * Carga la lista de estudiantes.
     */
    private void loadStudents() {
        studentsList.clear();
        studentsList.addAll(studentService.getAllStudents());
        updateCountLabel();
        logger.debug("Cargados {} estudiantes", studentsList.size());
    }

    /**
     * Actualiza el label de conteo.
     */
    private void updateCountLabel() {
        int count = studentsList.size();
        countLabel.setText(count + (count == 1 ? " estudiante" : " estudiantes"));
    }

    /**
     * Manejador para búsqueda.
     */
    @FXML
    private void onSearch() {
        String searchTerm = searchField.getText().trim();

        if (searchTerm.isEmpty()) {
            loadStudents();
            return;
        }

        studentsList.clear();
        studentsList.addAll(studentService.searchStudents(searchTerm));
        updateCountLabel();

        setStatusMessage("Búsqueda completada: " + studentsList.size() + " resultados");
    }

    /**
     * Manejador para limpiar búsqueda.
     */
    @FXML
    private void onClearSearch() {
        searchField.clear();
        loadStudents();
        setStatusMessage("Filtro limpiado");
    }

    /**
     * Manejador para nuevo estudiante.
     */
    @FXML
    private void onNewStudent() {
        isEditMode = false;
        currentStudent = null;
        clearForm();
        formTitle.setText("Nuevo Estudiante");
        barcodeField.setDisable(false);
        barcodeField.requestFocus();
        setStatusMessage("Ingrese los datos del nuevo estudiante");
    }

    /**
     * Manejador para editar estudiante.
     */
    @FXML
    private void onEditStudent() {
        Student selected = studentsTable.getSelectionModel().getSelectedItem();

        if (selected == null) {
            showAlert(Alert.AlertType.WARNING, "Seleccione un estudiante",
                    "Debe seleccionar un estudiante de la tabla para editar.");
            return;
        }

        isEditMode = true;
        currentStudent = selected;
        loadStudentToForm(selected);
        formTitle.setText("Editar Estudiante");
        barcodeField.setDisable(true);
        firstNameField.requestFocus();
        setStatusMessage("Editando: " + selected.getFullName());
    }

    /**
     * Manejador para eliminar estudiante.
     */
    @FXML
    private void onDeleteStudent() {
        Student selected = studentsTable.getSelectionModel().getSelectedItem();

        if (selected == null) {
            return;
        }

        // Verificar contraseña primero
        if (!verifyPasswordDialog("Eliminar Estudiante")) {
            return;
        }

        // Confirmar eliminación
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirmar Eliminación");
        confirm.setHeaderText("¿Eliminar estudiante?");
        confirm.setContentText("¿Está seguro de eliminar a " + selected.getFullName() + "?\n" +
                "Esta acción no se puede deshacer.");

        Optional<ButtonType> result = confirm.showAndWait();

        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                studentService.deleteStudent(selected.getId());
                loadStudents();
                clearForm();
                setStatusMessage("Estudiante eliminado: " + selected.getFullName());
                logger.info("Estudiante eliminado: {}", selected);
            } catch (SQLException e) {
                logger.error("Error al eliminar estudiante", e);
                showAlert(Alert.AlertType.ERROR, "Error", "No se pudo eliminar el estudiante: " + e.getMessage());
            }
        }
    }

    /**
     * Manejador para guardar estudiante.
     */
    @FXML
    private void onSave() {
        // Validar formulario
        if (!validateForm()) {
            return;
        }

        // Verificar contraseña
        String actionName = isEditMode ? "Editar Estudiante" : "Agregar Estudiante";
        if (!verifyPasswordDialog(actionName)) {
            return;
        }

        try {
            Student student = isEditMode ? currentStudent : new Student();

            student.setBarcode(barcodeField.getText().trim());
            student.setFirstName(firstNameField.getText().trim());
            student.setLastName(lastNameField.getText().trim());
            student.setGrade(gradeField.getText().trim());
            student.setMinor(isMinorCheck.isSelected());
            student.setRequiresGuardian(requiresGuardianCheck.isSelected());
            student.setStatus(mapStatusToDb(statusCombo.getValue()));

            if (isEditMode) {
                studentService.updateStudent(student);
                setStatusMessage("Estudiante actualizado: " + student.getFullName());
                logger.info("Estudiante actualizado: {}", student);
            } else {
                studentService.saveStudent(student);
                setStatusMessage("Estudiante creado: " + student.getFullName());
                logger.info("Estudiante creado: {}", student);
            }

            // Guardar guardianes
            saveGuardians(student);

            loadStudents();
            clearForm();

        } catch (IllegalArgumentException e) {
            showValidationError(e.getMessage());
        } catch (SQLException e) {
            logger.error("Error al guardar estudiante", e);
            showAlert(Alert.AlertType.ERROR, "Error", "No se pudo guardar el estudiante: " + e.getMessage());
        }
    }

    /**
     * Manejador para cancelar.
     */
    @FXML
    private void onCancel() {
        clearForm();
        studentsTable.getSelectionModel().clearSelection();
        setStatusMessage("Operación cancelada");
    }

    /**
     * Manejador para agregar guardián legal.
     */
    @FXML
    private void onAddGuardian() {
        // Crear diálogo personalizado para guardián
        Dialog<Guardian> dialog = new Dialog<>();
        dialog.setTitle("Agregar Guardián Legal");
        dialog.setHeaderText("Ingrese los datos del guardián legal");

        ButtonType addButtonType = new ButtonType("Agregar", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(addButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        TextField nameField = new TextField();
        nameField.setPromptText("Nombre completo");
        TextField relationshipField = new TextField();
        relationshipField.setPromptText("Ej: Madre, Padre, Abuelo");
        TextField phoneField = new TextField();
        phoneField.setPromptText("Teléfono de contacto");

        grid.add(new Label("Nombre:"), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(new Label("Relación:"), 0, 1);
        grid.add(relationshipField, 1, 1);
        grid.add(new Label("Teléfono:"), 0, 2);
        grid.add(phoneField, 1, 2);

        dialog.getDialogPane().setContent(grid);

        Platform.runLater(nameField::requestFocus);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == addButtonType) {
                if (nameField.getText().trim().isEmpty()) {
                    return null;
                }
                Guardian guardian = new Guardian();
                guardian.setName(nameField.getText().trim());
                guardian.setRelationship(relationshipField.getText().trim());
                guardian.setPhone(phoneField.getText().trim());
                guardian.setAuthorized(true);
                return guardian;
            }
            return null;
        });

        Optional<Guardian> result = dialog.showAndWait();

        result.ifPresent(guardian -> {
            currentGuardians.add(guardian);
            guardianDisplayList.add(guardian.getDescription());
            logger.debug("Guardián agregado: {}", guardian.getName());
        });
    }

    /**
     * Manejador para eliminar guardián.
     */
    @FXML
    private void onRemoveGuardian() {
        int selectedIndex = guardiansListView.getSelectionModel().getSelectedIndex();

        if (selectedIndex >= 0) {
            currentGuardians.remove(selectedIndex);
            guardianDisplayList.remove(selectedIndex);
            logger.debug("Guardián eliminado del índice: {}", selectedIndex);
        }
    }

    /**
     * Manejador para importar desde CSV.
     */
    @FXML
    private void onImportCsv() {
        // Verificar contraseña primero
        if (!verifyPasswordDialog("Importar Estudiantes")) {
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Seleccionar archivo CSV");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Archivos CSV", "*.csv"),
                new FileChooser.ExtensionFilter("Todos los archivos", "*.*")
        );

        File file = fileChooser.showOpenDialog(studentsTable.getScene().getWindow());

        if (file != null) {
            logger.info("Importando desde: {}", file.getAbsolutePath());

            CsvImportService.ImportResult result = csvImportService.importFromCsv(file);

            // Mostrar resultado
            Alert alert = new Alert(result.getErrorCount() > 0 ?
                    Alert.AlertType.WARNING : Alert.AlertType.INFORMATION);
            alert.setTitle("Resultado de Importación");
            alert.setHeaderText("Importación completada");

            StringBuilder content = new StringBuilder(result.getSummary());

            if (!result.getErrors().isEmpty()) {
                content.append("\n\nErrores:\n");
                int maxErrors = Math.min(5, result.getErrors().size());
                for (int i = 0; i < maxErrors; i++) {
                    content.append("- ").append(result.getErrors().get(i)).append("\n");
                }
                if (result.getErrors().size() > 5) {
                    content.append("... y ").append(result.getErrors().size() - 5).append(" errores más");
                }
            }

            alert.setContentText(content.toString());
            alert.showAndWait();

            // Recargar lista
            loadStudents();
            setStatusMessage("Importación: " + result.getSuccessCount() + " estudiantes importados");
        }
    }

    /**
     * Manejador para generar plantilla CSV.
     */
    @FXML
    private void onGenerateTemplate() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Guardar plantilla CSV");
        fileChooser.setInitialFileName("plantilla_estudiantes.csv");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Archivos CSV", "*.csv")
        );

        File file = fileChooser.showSaveDialog(studentsTable.getScene().getWindow());

        if (file != null) {
            if (csvImportService.generateTemplate(file)) {
                showAlert(Alert.AlertType.INFORMATION, "Plantilla generada",
                        "Plantilla guardada en:\n" + file.getAbsolutePath());
            } else {
                showAlert(Alert.AlertType.ERROR, "Error",
                        "No se pudo generar la plantilla");
            }
        }
    }

    /**
     * Manejador para volver al scanner.
     */
    @FXML
    private void onBackToScanner() {
        try {
            logger.info("Volviendo al scanner");

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/scanner-view.fxml"));
            Parent root = loader.load();

            Stage stage = (Stage) studentsTable.getScene().getWindow();
            Scene scene = new Scene(root);
            scene.getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());

            stage.setScene(scene);
            stage.setTitle("Scanner de Estudiantes");

        } catch (IOException e) {
            logger.error("Error al volver al scanner", e);
        }
    }

    /**
     * Inicializa el controlador para registro rápido con un código de barras.
     */
    public void initForQuickRegister(String barcode) {
        Platform.runLater(() -> {
            onNewStudent();
            barcodeField.setText(barcode);
            barcodeField.setDisable(true);
            firstNameField.requestFocus();
            setStatusMessage("Registrando estudiante con código: " + barcode);
        });
    }

    /**
     * Muestra diálogo para verificar contraseña.
     *
     * @param action Descripción de la acción a realizar
     * @return true si la contraseña es correcta
     */
    private boolean verifyPasswordDialog(String action) {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Verificación de Seguridad");
        dialog.setHeaderText(action);

        ButtonType okButtonType = new ButtonType("OK", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(okButtonType, ButtonType.CANCEL);

        VBox content = new VBox(10);
        content.setPadding(new Insets(20));

        Label label = new Label("Por favor ingresa la contraseña:");
        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Contraseña");
        passwordField.setPrefWidth(250);

        content.getChildren().addAll(label, passwordField);
        dialog.getDialogPane().setContent(content);

        Platform.runLater(passwordField::requestFocus);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == okButtonType) {
                return passwordField.getText();
            }
            return null;
        });

        Optional<String> result = dialog.showAndWait();

        if (result.isPresent()) {
            String password = result.get();
            if (securityManager.verifyPassword(password)) {
                return true;
            } else {
                showAlert(Alert.AlertType.ERROR, "Contraseña Incorrecta",
                        "La contraseña ingresada no es correcta.");
                return false;
            }
        }

        return false;
    }

    /**
     * Carga los datos de un estudiante en el formulario.
     */
    private void loadStudentToForm(Student student) {
        barcodeField.setText(student.getBarcode());
        firstNameField.setText(student.getFirstName());
        lastNameField.setText(student.getLastName());
        gradeField.setText(student.getGrade() != null ? student.getGrade() : "");
        isMinorCheck.setSelected(student.isMinor());
        requiresGuardianCheck.setSelected(student.isRequiresGuardian());
        statusCombo.setValue(mapStatusFromDb(student.getStatus()));

        loadGuardians(student.getId());
    }

    /**
     * Carga los guardianes de un estudiante.
     */
    private void loadGuardians(Long studentId) {
        currentGuardians.clear();
        guardianDisplayList.clear();

        if (studentId != null) {
            List<Guardian> guardians = studentService.getStudentGuardians(studentId);
            currentGuardians.addAll(guardians);

            for (Guardian g : guardians) {
                guardianDisplayList.add(g.getDescription());
            }
        }
    }

    /**
     * Guarda los guardianes de un estudiante.
     */
    private void saveGuardians(Student student) throws SQLException {
        for (Guardian guardian : currentGuardians) {
            if (guardian.getId() == null) {
                guardian.setStudentId(student.getId());
                studentService.addGuardian(guardian);
            }
        }
    }

    /**
     * Limpia el formulario.
     */
    private void clearForm() {
        currentStudent = null;
        isEditMode = false;
        formTitle.setText("Nuevo Estudiante");

        barcodeField.clear();
        barcodeField.setDisable(false);
        firstNameField.clear();
        lastNameField.clear();
        gradeField.clear();
        isMinorCheck.setSelected(true);
        requiresGuardianCheck.setSelected(true);
        statusCombo.setValue("Activo");

        currentGuardians.clear();
        guardianDisplayList.clear();

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

        // Validar que si requiere acompañante, tenga al menos un guardián
        if (requiresGuardianCheck.isSelected() && currentGuardians.isEmpty()) {
            showValidationError("Debe agregar al menos un guardián legal");
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
            case "Suspendido" -> "suspended";
            default -> "active";
        };
    }

    private String mapStatusFromDb(String dbStatus) {
        return switch (dbStatus) {
            case "active" -> "Activo";
            case "inactive" -> "Inactivo";
            case "suspended" -> "Suspendido";
            default -> "Activo";
        };
    }
}
