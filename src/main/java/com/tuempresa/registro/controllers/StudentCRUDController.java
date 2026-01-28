package com.tuempresa.registro.controllers;

import com.tuempresa.registro.models.Guardian;
import com.tuempresa.registro.models.Student;
import com.tuempresa.registro.services.StudentService;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

        // Inicializar listas
        studentsList = FXCollections.observableArrayList();
        guardianDisplayList = FXCollections.observableArrayList();
        currentGuardians = new ArrayList<>();

        // Configurar tabla
        setupTable();

        // Configurar ComboBox de estado
        setupStatusCombo();

        // Configurar lista de acudientes
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
        // Configurar columnas
        colBarcode.setCellValueFactory(new PropertyValueFactory<>("barcode"));
        colFirstName.setCellValueFactory(new PropertyValueFactory<>("firstName"));
        colLastName.setCellValueFactory(new PropertyValueFactory<>("lastName"));
        colGrade.setCellValueFactory(new PropertyValueFactory<>("grade"));

        // Columna personalizada para "Requiere acompañante"
        colRequiresGuardian.setCellValueFactory(cellData -> {
            boolean requires = cellData.getValue().isRequiresGuardian();
            return new SimpleStringProperty(requires ? "Sí" : "No");
        });

        // Columna personalizada para estado
        colStatus.setCellValueFactory(cellData -> {
            String status = cellData.getValue().getStatus();
            String displayStatus = "active".equals(status) ? "Activo" :
                    "inactive".equals(status) ? "Inactivo" : status;
            return new SimpleStringProperty(displayStatus);
        });

        // Asignar datos
        studentsTable.setItems(studentsList);

        // Placeholder cuando no hay datos
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
        // Listener para selección en la tabla
        studentsTable.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldSelection, newSelection) -> {
                    boolean hasSelection = newSelection != null;
                    editButton.setDisable(!hasSelection);
                    deleteButton.setDisable(!hasSelection);
                });

        // Listener para selección en lista de acudientes
        guardiansListView.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldSelection, newSelection) -> {
                    removeGuardianButton.setDisable(newSelection == null);
                });

        // Vincular checkbox de menor con checkbox de requiere acompañante
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
        barcodeField.setDisable(true); // No permitir cambiar código en edición
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

        try {
            Student student = isEditMode ? currentStudent : new Student();

            // Cargar datos del formulario
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

            // Guardar acudientes
            saveGuardians(student);

            // Recargar lista y limpiar formulario
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
     * Manejador para agregar acudiente.
     */
    @FXML
    private void onAddGuardian() {
        // Diálogo simple para agregar acudiente
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Agregar Acudiente");
        dialog.setHeaderText("Ingrese los datos del acudiente");
        dialog.setContentText("Nombre del acudiente:");

        Optional<String> result = dialog.showAndWait();

        result.ifPresent(name -> {
            if (!name.trim().isEmpty()) {
                Guardian guardian = new Guardian();
                guardian.setName(name.trim());
                guardian.setAuthorized(true);

                currentGuardians.add(guardian);
                guardianDisplayList.add(guardian.getName());

                logger.debug("Acudiente agregado: {}", name);
            }
        });
    }

    /**
     * Manejador para eliminar acudiente.
     */
    @FXML
    private void onRemoveGuardian() {
        int selectedIndex = guardiansListView.getSelectionModel().getSelectedIndex();

        if (selectedIndex >= 0) {
            currentGuardians.remove(selectedIndex);
            guardianDisplayList.remove(selectedIndex);
            logger.debug("Acudiente eliminado del índice: {}", selectedIndex);
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
     *
     * @param barcode Código de barras escaneado
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

        // Cargar acudientes
        loadGuardians(student.getId());
    }

    /**
     * Carga los acudientes de un estudiante.
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
     * Guarda los acudientes de un estudiante.
     */
    private void saveGuardians(Student student) throws SQLException {
        // Por ahora solo guardamos los nuevos acudientes
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
     *
     * @return true si es válido
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

    /**
     * Muestra un error de validación.
     */
    private void showValidationError(String message) {
        validationMessage.setText(message);
        validationMessage.getStyleClass().add("error");
    }

    /**
     * Limpia el error de validación.
     */
    private void clearValidationError() {
        validationMessage.setText("");
        validationMessage.getStyleClass().remove("error");
    }

    /**
     * Establece el mensaje de estado.
     */
    private void setStatusMessage(String message) {
        statusMessage.setText(message);
    }

    /**
     * Muestra un diálogo de alerta.
     */
    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    /**
     * Mapea el estado del ComboBox al valor de la BD.
     */
    private String mapStatusToDb(String displayStatus) {
        return switch (displayStatus) {
            case "Activo" -> "active";
            case "Inactivo" -> "inactive";
            case "Suspendido" -> "suspended";
            default -> "active";
        };
    }

    /**
     * Mapea el estado de la BD al valor del ComboBox.
     */
    private String mapStatusFromDb(String dbStatus) {
        return switch (dbStatus) {
            case "active" -> "Activo";
            case "inactive" -> "Inactivo";
            case "suspended" -> "Suspendido";
            default -> "Activo";
        };
    }
}
