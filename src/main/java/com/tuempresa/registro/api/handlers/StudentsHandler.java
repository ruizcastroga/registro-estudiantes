package com.tuempresa.registro.api.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.tuempresa.registro.api.ApiUtils;
import com.tuempresa.registro.models.Student;
import com.tuempresa.registro.services.StudentService;

import java.io.IOException;
import java.sql.SQLException;
import java.util.*;

/**
 * Handles student import/export endpoints:
 *   GET  /api/students/export
 *   POST /api/students/import
 */
public class StudentsHandler implements HttpHandler {

    private final StudentService studentService;
    private final String apiKey;

    public StudentsHandler(StudentService studentService, String apiKey) {
        this.studentService = studentService;
        this.apiKey = apiKey;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!ApiUtils.checkAuth(exchange, apiKey)) return;

        String path   = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();

        if ("GET".equals(method) && path.endsWith("/export")) {
            handleExport(exchange);
        } else if ("POST".equals(method) && path.endsWith("/import")) {
            handleImport(exchange);
        } else {
            ApiUtils.sendError(exchange, 404,
                    "Use GET /api/students/export or POST /api/students/import");
        }
    }

    // -----------------------------------------------------------------------
    // Export
    // -----------------------------------------------------------------------

    private void handleExport(HttpExchange exchange) throws IOException {
        List<Student> students = studentService.getAllStudents();
        List<Map<String, Object>> result = new ArrayList<>();

        for (Student s : students) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("barcode",          s.getBarcode());
            row.put("firstName",        s.getFirstName());
            row.put("lastName",         s.getLastName());
            row.put("grade",            s.getGrade());
            row.put("isMinor",          s.isMinor());
            row.put("requiresGuardian", s.isRequiresGuardian());
            row.put("status",           s.getStatus());
            result.add(row);
        }

        ApiUtils.sendJson(exchange, 200, result);
    }

    // -----------------------------------------------------------------------
    // Import
    // -----------------------------------------------------------------------

    private void handleImport(HttpExchange exchange) throws IOException {
        JsonNode body;
        try {
            body = ApiUtils.readBody(exchange);
        } catch (Exception e) {
            ApiUtils.sendError(exchange, 400, "Invalid JSON: " + e.getMessage());
            return;
        }

        if (!body.isArray()) {
            ApiUtils.sendError(exchange, 400, "Body must be a JSON array of student objects");
            return;
        }

        int imported = 0, skipped = 0;
        List<String> errors = new ArrayList<>();

        for (int i = 0; i < body.size(); i++) {
            JsonNode item = body.get(i);
            String barcode   = ApiUtils.text(item, "barcode");
            String firstName = ApiUtils.text(item, "firstName");
            String lastName  = ApiUtils.text(item, "lastName");

            // Validate required fields
            if (barcode == null || barcode.isBlank()) {
                errors.add("Row " + (i + 1) + ": 'barcode' is required");
                skipped++;
                continue;
            }
            if (firstName == null || firstName.isBlank()) {
                errors.add("Row " + (i + 1) + ": 'firstName' is required");
                skipped++;
                continue;
            }
            if (lastName == null || lastName.isBlank()) {
                errors.add("Row " + (i + 1) + ": 'lastName' is required");
                skipped++;
                continue;
            }

            try {
                Student s = new Student(barcode.trim(), firstName.trim(), lastName.trim());
                String grade = ApiUtils.text(item, "grade");
                if (grade != null) s.setGrade(grade.trim());
                s.setMinor(ApiUtils.bool(item, "isMinor", true));
                s.setRequiresGuardian(ApiUtils.bool(item, "requiresGuardian", true));
                String status = ApiUtils.text(item, "status");
                s.setStatus(status != null ? status : "active");
                s.setCreatedBy("api");
                s.setUpdatedBy("api");

                studentService.saveStudent(s);
                imported++;

            } catch (SQLException e) {
                String msg = e.getMessage() != null && e.getMessage().contains("UNIQUE")
                        ? "barcode '" + barcode + "' already exists"
                        : e.getMessage();
                errors.add("Row " + (i + 1) + ": " + msg);
                skipped++;
            } catch (Exception e) {
                errors.add("Row " + (i + 1) + ": " + e.getMessage());
                skipped++;
            }
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("imported", imported);
        response.put("skipped",  skipped);
        response.put("errors",   errors);
        ApiUtils.sendJson(exchange, 200, response);
    }
}
