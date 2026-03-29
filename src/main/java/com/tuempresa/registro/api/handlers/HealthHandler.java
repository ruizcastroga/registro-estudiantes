package com.tuempresa.registro.api.handlers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.tuempresa.registro.api.ApiUtils;
import com.tuempresa.registro.dao.DatabaseConnection;

import java.io.IOException;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

/** GET /api/health — server + DB status check. */
public class HealthHandler implements HttpHandler {

    private final String apiKey;

    public HealthHandler(String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!ApiUtils.checkAuth(exchange, apiKey)) return;

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "ok");
        body.put("version", "1.0.0");
        String dbStatus;
        try {
            DatabaseConnection.getInstance().getConnection();
            dbStatus = "connected";
        } catch (SQLException e) {
            dbStatus = "disconnected";
        }
        body.put("db", dbStatus);
        body.put("port", 8080);

        ApiUtils.sendJson(exchange, 200, body);
    }
}
