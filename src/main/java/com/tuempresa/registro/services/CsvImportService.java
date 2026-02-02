package com.tuempresa.registro.services;

import com.tuempresa.registro.dao.GuardianDAO;
import com.tuempresa.registro.dao.StudentDAO;
import com.tuempresa.registro.models.Guardian;
import com.tuempresa.registro.models.Student;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Servicio para importar estudiantes desde archivos CSV.
 * Formato esperado del CSV:
 * codigo,nombre,apellido,grado,requiere_acompañante,tutor_nombre,tutor_relacion,tutor_telefono
 */
public class CsvImportService {

    private static final Logger logger = LoggerFactory.getLogger(CsvImportService.class);

    private final StudentDAO studentDAO;
    private final GuardianDAO guardianDAO;

    // Separadores soportados
    private static final char[] SEPARATORS = {',', ';', '\t'};

    public CsvImportService() {
        this.studentDAO = new StudentDAO();
        this.guardianDAO = new GuardianDAO();
    }

    /**
     * Resultado de la importación.
     */
    public static class ImportResult {
        private int totalRows;
        private int successCount;
        private int errorCount;
        private int skippedCount;
        private List<String> errors;

        public ImportResult() {
            this.errors = new ArrayList<>();
        }

        public int getTotalRows() { return totalRows; }
        public int getSuccessCount() { return successCount; }
        public int getErrorCount() { return errorCount; }
        public int getSkippedCount() { return skippedCount; }
        public List<String> getErrors() { return errors; }

        public void addError(String error) {
            this.errors.add(error);
            this.errorCount++;
        }

        public void incrementSuccess() { this.successCount++; }
        public void incrementSkipped() { this.skippedCount++; }
        public void setTotalRows(int total) { this.totalRows = total; }

        public String getSummary() {
            return String.format("Importación completada:\n" +
                    "- Total de filas: %d\n" +
                    "- Importados: %d\n" +
                    "- Omitidos (ya existen): %d\n" +
                    "- Errores: %d",
                    totalRows, successCount, skippedCount, errorCount);
        }
    }

    /**
     * Importa estudiantes desde un archivo CSV.
     *
     * @param file Archivo CSV a importar
     * @return Resultado de la importación
     */
    public ImportResult importFromCsv(File file) {
        ImportResult result = new ImportResult();

        if (file == null || !file.exists()) {
            result.addError("Archivo no encontrado");
            return result;
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {

            // Detectar separador leyendo la primera línea
            String headerLine = reader.readLine();
            if (headerLine == null || headerLine.trim().isEmpty()) {
                result.addError("El archivo está vacío");
                return result;
            }

            char separator = detectSeparator(headerLine);
            logger.info("Separador detectado: '{}'", separator == '\t' ? "TAB" : String.valueOf(separator));

            // Parsear encabezados
            String[] headers = parseCsvLine(headerLine, separator);
            int[] columnMapping = mapColumns(headers);

            if (columnMapping[0] == -1 || columnMapping[1] == -1 || columnMapping[2] == -1) {
                result.addError("El archivo debe tener columnas: codigo, nombre, apellido");
                return result;
            }

            // Procesar filas de datos
            String line;
            int rowNumber = 1;
            while ((line = reader.readLine()) != null) {
                rowNumber++;
                result.setTotalRows(rowNumber - 1);

                if (line.trim().isEmpty()) {
                    continue;
                }

                try {
                    processRow(line, separator, columnMapping, result, rowNumber);
                } catch (Exception e) {
                    result.addError("Fila " + rowNumber + ": " + e.getMessage());
                }
            }

            result.setTotalRows(rowNumber - 1);

        } catch (IOException e) {
            logger.error("Error al leer archivo CSV", e);
            result.addError("Error al leer el archivo: " + e.getMessage());
        }

        logger.info("Importación finalizada: {}", result.getSummary());
        return result;
    }

    /**
     * Detecta el separador usado en el CSV.
     */
    private char detectSeparator(String line) {
        for (char sep : SEPARATORS) {
            if (line.indexOf(sep) > 0) {
                return sep;
            }
        }
        return ','; // Default
    }

    /**
     * Parsea una línea CSV respetando comillas.
     */
    private String[] parseCsvLine(String line, char separator) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == separator && !inQuotes) {
                values.add(current.toString().trim());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        values.add(current.toString().trim());

        return values.toArray(new String[0]);
    }

    /**
     * Mapea las columnas del CSV a los campos esperados.
     * Retorna: [codigo, nombre, apellido, grado, requiere_acomp, tutor_nombre, tutor_rel, tutor_tel]
     */
    private int[] mapColumns(String[] headers) {
        int[] mapping = new int[]{-1, -1, -1, -1, -1, -1, -1, -1};

        for (int i = 0; i < headers.length; i++) {
            String header = headers[i].toLowerCase()
                    .replace("á", "a").replace("é", "e")
                    .replace("í", "i").replace("ó", "o")
                    .replace("ú", "u").replace("ñ", "n");

            if (header.contains("codigo") || header.contains("barcode") || header.contains("code")) {
                mapping[0] = i;
            } else if (header.contains("nombre") && !header.contains("apellido") && !header.contains("guardian") && !header.contains("tutor")) {
                mapping[1] = i;
            } else if (header.contains("apellido") || header.contains("last")) {
                mapping[2] = i;
            } else if (header.contains("grado") || header.contains("curso") || header.contains("grade")) {
                mapping[3] = i;
            } else if (header.contains("requiere") || header.contains("acompanante")) {
                if (!header.contains("nombre") && !header.contains("relacion") && !header.contains("telefono")) {
                    mapping[4] = i;
                }
            }

            // Columnas de tutor (también acepta guardian y acudiente para compatibilidad)
            if (header.contains("tutor") || header.contains("guardian") || header.contains("acudiente")) {
                if (header.contains("nombre") || header.contains("name")) {
                    mapping[5] = i;
                } else if (header.contains("relacion") || header.contains("parentesco")) {
                    mapping[6] = i;
                } else if (header.contains("telefono") || header.contains("phone") || header.contains("cel")) {
                    mapping[7] = i;
                }
            }
        }

        return mapping;
    }

    /**
     * Procesa una fila del CSV.
     */
    private void processRow(String line, char separator, int[] mapping, ImportResult result, int rowNumber) {
        String[] values = parseCsvLine(line, separator);

        // Extraer valores obligatorios
        String barcode = getValueSafe(values, mapping[0]);
        String firstName = getValueSafe(values, mapping[1]);
        String lastName = getValueSafe(values, mapping[2]);

        if (barcode.isEmpty() || firstName.isEmpty() || lastName.isEmpty()) {
            result.addError("Fila " + rowNumber + ": Faltan datos obligatorios (código, nombre, apellido)");
            return;
        }

        // Verificar si ya existe
        if (studentDAO.existsByBarcode(barcode)) {
            logger.debug("Estudiante ya existe: {}", barcode);
            result.incrementSkipped();
            return;
        }

        // Crear estudiante
        Student student = new Student();
        student.setBarcode(barcode);
        student.setFirstName(firstName);
        student.setLastName(lastName);
        student.setGrade(getValueSafe(values, mapping[3]));

        // Requiere acompañante
        String requiresGuardian = getValueSafe(values, mapping[4]).toLowerCase();
        boolean requires = requiresGuardian.isEmpty() ||
                requiresGuardian.equals("si") ||
                requiresGuardian.equals("sí") ||
                requiresGuardian.equals("1") ||
                requiresGuardian.equals("true") ||
                requiresGuardian.equals("yes");
        student.setRequiresGuardian(requires);
        student.setMinor(true);
        student.setStatus("active");

        try {
            // Guardar estudiante
            studentDAO.save(student);

            // Guardar guardián si hay datos
            String guardianName = getValueSafe(values, mapping[5]);
            if (!guardianName.isEmpty() && student.getId() != null) {
                Guardian guardian = new Guardian();
                guardian.setStudentId(student.getId());
                guardian.setName(guardianName);
                guardian.setRelationship(getValueSafe(values, mapping[6]));
                guardian.setPhone(getValueSafe(values, mapping[7]));
                guardian.setAuthorized(true);

                guardianDAO.save(guardian);
            }

            result.incrementSuccess();
            logger.debug("Importado: {} - {}", barcode, student.getFullName());

        } catch (SQLException e) {
            result.addError("Fila " + rowNumber + ": Error al guardar - " + e.getMessage());
        }
    }

    /**
     * Obtiene un valor de forma segura del array.
     */
    private String getValueSafe(String[] values, int index) {
        if (index < 0 || index >= values.length) {
            return "";
        }
        String value = values[index];
        // Remover comillas
        if (value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1);
        }
        return value.trim();
    }

    /**
     * Genera una plantilla CSV de ejemplo.
     *
     * @param file Archivo donde guardar la plantilla
     * @return true si se generó correctamente
     */
    public boolean generateTemplate(File file) {
        try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(
                new FileOutputStream(file), StandardCharsets.UTF_8))) {

            // BOM para Excel
            writer.print('\ufeff');

            // Encabezados
            writer.println("codigo,nombre,apellido,grado,requiere_acompanante,tutor_nombre,tutor_relacion,tutor_telefono");

            // Ejemplos
            writer.println("EST001,Juan,García López,5to Primaria,Si,María López,Madre,555-1234");
            writer.println("EST002,Ana,Martínez Pérez,3ro Secundaria,No,,,");
            writer.println("EST003,Pedro,Rodríguez Silva,1ro Primaria,Si,Roberto Rodríguez,Padre,555-5678");

            logger.info("Plantilla CSV generada: {}", file.getAbsolutePath());
            return true;

        } catch (IOException e) {
            logger.error("Error al generar plantilla CSV", e);
            return false;
        }
    }
}
