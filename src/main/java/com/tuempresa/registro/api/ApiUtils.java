package com.tuempresa.registro.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shared utilities for all API handlers: JSON serialization,
 * auth checking, query-param parsing, and standard error responses.
 */
public final class ApiUtils {

    public static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private ApiUtils() {}

    /** Serialize {@code body} to JSON and write a response. */
    public static void sendJson(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] bytes = MAPPER.writeValueAsBytes(body);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    /** Send a JSON error object: {"error": message}. */
    public static void sendError(HttpExchange exchange, int status, String message) throws IOException {
        Map<String, String> err = new LinkedHashMap<>();
        err.put("error", message);
        sendJson(exchange, status, err);
    }

    /**
     * Validate the X-API-Key header.
     * Returns true if valid; sends 401 and returns false if not.
     */
    public static boolean checkAuth(HttpExchange exchange, String expected) throws IOException {
        String key = exchange.getRequestHeaders().getFirst("X-API-Key");
        if (key == null || !key.equals(expected)) {
            sendError(exchange, 401, "Unauthorized – include a valid X-API-Key header");
            return false;
        }
        return true;
    }

    /** Read the request body as a Jackson JsonNode (array or object). */
    public static JsonNode readBody(HttpExchange exchange) throws IOException {
        return MAPPER.readTree(exchange.getRequestBody());
    }

    /** Get a single query-string parameter value, or null if absent. */
    public static String queryParam(HttpExchange exchange, String name) {
        String query = exchange.getRequestURI().getQuery();
        if (query == null) return null;
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2 && kv[0].equals(name)) return kv[1];
        }
        return null;
    }

    /** Null-safe text extraction from a JsonNode field. */
    public static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return (v == null || v.isNull()) ? null : v.asText(null);
    }

    /** Boolean extraction with a default. */
    public static boolean bool(JsonNode node, String field, boolean def) {
        JsonNode v = node.get(field);
        return (v == null || v.isNull()) ? def : v.asBoolean(def);
    }
}
