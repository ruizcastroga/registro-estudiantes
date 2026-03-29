package com.tuempresa.registro.api.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.tuempresa.registro.api.ApiUtils;
import com.tuempresa.registro.models.VisitorBadge;
import com.tuempresa.registro.models.VisitorLog;
import com.tuempresa.registro.services.VisitorService;

import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Handles visitor badge and log endpoints:
 *   GET  /api/visitors/badges/export
 *   POST /api/visitors/badges/import
 *   GET  /api/visitors/logs/export?from=YYYY-MM-DD&to=YYYY-MM-DD
 */
public class VisitorsHandler implements HttpHandler {

    private final VisitorService visitorService;
    private final String apiKey;

    public VisitorsHandler(VisitorService visitorService, String apiKey) {
        this.visitorService = visitorService;
        this.apiKey = apiKey;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!ApiUtils.checkAuth(exchange, apiKey)) return;

        String path   = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();

        if (path.contains("/badges/export") && "GET".equals(method)) {
            handleBadgesExport(exchange);
        } else if (path.contains("/badges/import") && "POST".equals(method)) {
            handleBadgesImport(exchange);
        } else if (path.contains("/logs/export") && "GET".equals(method)) {
            handleLogsExport(exchange);
        } else {
            ApiUtils.sendError(exchange, 404,
                    "Endpoints: GET /api/visitors/badges/export, " +
                    "POST /api/visitors/badges/import, " +
                    "GET /api/visitors/logs/export?from=YYYY-MM-DD&to=YYYY-MM-DD");
        }
    }

    // -----------------------------------------------------------------------
    // Badges export
    // -----------------------------------------------------------------------

    private void handleBadgesExport(HttpExchange exchange) throws IOException {
        List<VisitorBadge> badges = visitorService.getAllBadges();
        List<Map<String, Object>> result = new ArrayList<>();

        for (VisitorBadge b : badges) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("code",      b.getCode());
            row.put("status",    b.getStatus());
            row.put("createdAt", b.getCreatedAt());
            result.add(row);
        }

        ApiUtils.sendJson(exchange, 200, result);
    }

    // -----------------------------------------------------------------------
    // Badges import
    // -----------------------------------------------------------------------

    private void handleBadgesImport(HttpExchange exchange) throws IOException {
        JsonNode body;
        try {
            body = ApiUtils.readBody(exchange);
        } catch (Exception e) {
            ApiUtils.sendError(exchange, 400, "Invalid JSON: " + e.getMessage());
            return;
        }

        if (!body.isArray()) {
            ApiUtils.sendError(exchange, 400, "Body must be a JSON array of badge objects");
            return;
        }

        int imported = 0, skipped = 0;
        List<String> errors = new ArrayList<>();

        for (int i = 0; i < body.size(); i++) {
            JsonNode item = body.get(i);
            String code = ApiUtils.text(item, "code");

            if (code == null || code.isBlank()) {
                errors.add("Row " + (i + 1) + ": 'code' is required");
                skipped++;
                continue;
            }

            try {
                visitorService.createBadge(code.trim().toUpperCase());
                imported++;
            } catch (SQLException e) {
                String msg = e.getMessage() != null && e.getMessage().contains("UNIQUE")
                        ? "code '" + code + "' already exists"
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

    // -----------------------------------------------------------------------
    // Logs export
    // -----------------------------------------------------------------------

    private void handleLogsExport(HttpExchange exchange) throws IOException {
        String fromStr = ApiUtils.queryParam(exchange, "from");
        String toStr   = ApiUtils.queryParam(exchange, "to");

        LocalDateTime from;
        LocalDateTime to;

        try {
            from = (fromStr != null)
                    ? LocalDate.parse(fromStr).atStartOfDay()
                    : LocalDate.now().minusDays(30).atStartOfDay();
            to = (toStr != null)
                    ? LocalDate.parse(toStr).atTime(23, 59, 59)
                    : LocalDate.now().atTime(23, 59, 59);
        } catch (DateTimeParseException e) {
            ApiUtils.sendError(exchange, 400, "Date format must be YYYY-MM-DD (e.g. 2026-03-01)");
            return;
        }

        List<VisitorLog> logs = visitorService.getLogsByDateRange(from, to);
        List<Map<String, Object>> result = new ArrayList<>();

        for (VisitorLog log : logs) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("badge",         log.getBadgeCode());
            row.put("idNumber",      log.getIdNumber());
            row.put("firstName",     log.getFirstName());
            row.put("lastName",      log.getLastName());
            row.put("justification", log.getJustification());
            row.put("entryTime",     log.getEntryTime());
            row.put("exitTime",      log.getExitTime());
            result.add(row);
        }

        ApiUtils.sendJson(exchange, 200, result);
    }
}
