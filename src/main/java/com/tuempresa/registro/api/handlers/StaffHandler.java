package com.tuempresa.registro.api.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.tuempresa.registro.api.ApiUtils;
import com.tuempresa.registro.models.StaffMember;
import com.tuempresa.registro.services.StaffService;

import java.io.IOException;
import java.sql.SQLException;
import java.util.*;

/**
 * Handles staff import/export endpoints:
 *   GET  /api/staff/export
 *   POST /api/staff/import
 */
public class StaffHandler implements HttpHandler {

    private final StaffService staffService;
    private final String apiKey;

    public StaffHandler(StaffService staffService, String apiKey) {
        this.staffService = staffService;
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
                    "Use GET /api/staff/export or POST /api/staff/import");
        }
    }

    // -----------------------------------------------------------------------
    // Export
    // -----------------------------------------------------------------------

    private void handleExport(HttpExchange exchange) throws IOException {
        List<StaffMember> staff = staffService.getAllStaff();
        List<Map<String, Object>> result = new ArrayList<>();

        for (StaffMember m : staff) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("idNumber",   m.getIdNumber());
            row.put("firstName",  m.getFirstName());
            row.put("lastName",   m.getLastName());
            row.put("department", m.getDepartment());
            row.put("status",     m.getStatus());
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
            ApiUtils.sendError(exchange, 400, "Body must be a JSON array of staff objects");
            return;
        }

        int imported = 0, skipped = 0;
        List<String> errors = new ArrayList<>();

        for (int i = 0; i < body.size(); i++) {
            JsonNode item = body.get(i);
            String idNumber  = ApiUtils.text(item, "idNumber");
            String firstName = ApiUtils.text(item, "firstName");
            String lastName  = ApiUtils.text(item, "lastName");

            if (idNumber == null || idNumber.isBlank()) {
                errors.add("Row " + (i + 1) + ": 'idNumber' (cédula) is required");
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
                StaffMember m = new StaffMember();
                m.setIdNumber(idNumber.trim());
                // barcode = cédula (same as the UI rule)
                m.setBarcode(idNumber.trim());
                m.setFirstName(firstName.trim());
                m.setLastName(lastName.trim());
                String dept = ApiUtils.text(item, "department");
                if (dept != null) m.setDepartment(dept.trim());
                String status = ApiUtils.text(item, "status");
                m.setStatus(status != null ? status : "active");
                m.setCreatedBy("api");
                m.setUpdatedBy("api");

                staffService.saveStaff(m);
                imported++;

            } catch (SQLException e) {
                String msg = e.getMessage() != null && e.getMessage().contains("UNIQUE")
                        ? "idNumber '" + idNumber + "' already exists"
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
