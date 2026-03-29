package com.tuempresa.registro.api.handlers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.tuempresa.registro.api.ApiUtils;
import com.tuempresa.registro.dao.EntryLogDAO;
import com.tuempresa.registro.models.EntryLog;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Handles scanner entry-log export:
 *   GET /api/entry-logs/export?from=YYYY-MM-DD&to=YYYY-MM-DD
 */
public class EntryLogsHandler implements HttpHandler {

    private final EntryLogDAO entryLogDAO;
    private final String apiKey;

    public EntryLogsHandler(String apiKey) {
        this.entryLogDAO = new EntryLogDAO();
        this.apiKey = apiKey;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!ApiUtils.checkAuth(exchange, apiKey)) return;

        String path   = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();

        if ("GET".equals(method) && path.endsWith("/export")) {
            handleExport(exchange);
        } else {
            ApiUtils.sendError(exchange, 404,
                    "Use GET /api/entry-logs/export?from=YYYY-MM-DD&to=YYYY-MM-DD");
        }
    }

    private void handleExport(HttpExchange exchange) throws IOException {
        String fromStr = ApiUtils.queryParam(exchange, "from");
        String toStr   = ApiUtils.queryParam(exchange, "to");

        LocalDate from;
        LocalDate to;

        try {
            from = (fromStr != null) ? LocalDate.parse(fromStr) : LocalDate.now().minusDays(30);
            to   = (toStr   != null) ? LocalDate.parse(toStr)   : LocalDate.now();
        } catch (DateTimeParseException e) {
            ApiUtils.sendError(exchange, 400, "Date format must be YYYY-MM-DD (e.g. 2026-03-01)");
            return;
        }

        List<EntryLog> logs = entryLogDAO.findByDateRange(from, to);
        List<Map<String, Object>> result = new ArrayList<>();

        for (EntryLog log : logs) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id",        log.getId());
            row.put("studentId", log.getStudentId());
            row.put("logType",   log.getLogType());
            row.put("entryTime", log.getFormattedEntryTime());
            row.put("scannedBy", log.getScannedBy());
            row.put("notes",     log.getNotes());
            result.add(row);
        }

        ApiUtils.sendJson(exchange, 200, result);
    }
}
