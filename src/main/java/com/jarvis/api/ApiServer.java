package com.jarvis.api;

import com.jarvis.db.DatabaseManager;
import com.jarvis.engine.CommandExecutor;
import com.jarvis.engine.InputProcessor;
import com.jarvis.engine.IntentDetector;
import com.jarvis.llm.OllamaService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.json.JSONException;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.Executors;

/**
 * Lightweight HTTP API server for Jarvis.
 * Uses Java's built-in com.sun.net.httpserver with a 10-thread pool.
 * Exposes POST /chat endpoint for frontend communication.
 */
public class ApiServer {

    private static final int PORT = 8080;

    private final InputProcessor inputProcessor = new InputProcessor();
    private final IntentDetector intentDetector = new IntentDetector();
    private final CommandExecutor commandExecutor = new CommandExecutor();
    private final OllamaService ollamaService = new OllamaService();
    private final DatabaseManager databaseManager = new DatabaseManager();

    /**
     * Starts the API server on port 8080 with a fixed thread pool.
     */
    public void start() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
            server.setExecutor(Executors.newFixedThreadPool(10));
            server.createContext("/chat", new ChatHandler());
            server.createContext("/chat/start", new StartChatHandler());

            // Chat persistence API routes
            server.createContext("/chat/list", new ListChatsHandler());
            server.createContext("/chat/history", new ChatHistoryHandler());


            server.createContext("/chat/delete", new DeleteChatHandler());
            server.createContext("/", new StaticFileHandler());  // serve frontend

            System.out.println("Initializing Jarvis API routes...");
            System.out.println("Chat endpoint active at /chat");        
            
            server.start();
            System.out.println("Jarvis API server started on port " + PORT);
            System.out.println("Open http://localhost:" + PORT + " in your browser.");
        } catch (IOException e) {
            System.out.println("[JARVIS] ERROR: Failed to start server — " + e.getMessage());
        }
    }

    /**
     * Returns the OllamaService instance (used for startup checks).
     */
    public OllamaService getOllamaService() {
        return ollamaService;
    }

    private class ChatHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) {
            try {
                addCorsHeaders(exchange);

                // Handle CORS preflight
                if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                    exchange.sendResponseHeaders(204, -1);
                    return;
                }

                // Only accept POST
                if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    sendJsonResponse(exchange, 405, "Method not allowed. Use POST.");
                    return;
                }

                // Step 1: Validate Content-Type
                String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
                if (contentType == null || !contentType.contains("application/json")) {
                    sendJsonResponse(exchange, 400, "Invalid request format. Send JSON.");
                    return;
                }

                // Step 2: Read and parse JSON body
                String requestBody = readRequestBody(exchange.getRequestBody());

                JSONObject json;
                try {
                    json = new JSONObject(requestBody);
                } catch (JSONException e) {
                    System.out.println("[JARVIS] ERROR: Malformed JSON — " + e.getMessage());
                    sendJsonResponse(exchange, 400, "I didn't catch that. Try again.");
                    return;
                }

                // Step 3: Extract and validate message
                String rawMessage = json.optString("message", "").trim();
                int chatId = json.optInt("chatId", -1);
                String mode = json.has("mode") ? json.getString("mode") : "sarcastic";
                if (rawMessage.isEmpty()) {
                    System.out.println("[JARVIS] ERROR: Empty or missing message field");
                    sendJsonResponse(exchange, 400, "Missing message field");
                    return;
                }



                if ("ping".equalsIgnoreCase(rawMessage)) {
                    sendJsonResponse(exchange, 200, "pong");
                    return;
                }

                if (chatId <= 0) {
                    sendJsonResponse(exchange, 400, "Missing chatId.");
                    return;
                }

                // Step 4: Normalize input
                String normalizedInput = inputProcessor.normalize(rawMessage);
                if (normalizedInput == null) {
                    normalizedInput = "";
                }

                String safeFallback = "Jarvis is having trouble processing that.";
                String response;

                try {
                    // Step 5: Detect intent
                    IntentDetector.Intent intent = intentDetector.detectIntent(normalizedInput);
                    System.out.println("[JARVIS] Detected intent: " + intent.name());

                    databaseManager.saveMessage(chatId, "user", rawMessage, mode);

                    try {
                        String cmdResult = commandExecutor.execute(normalizedInput);
                        if (cmdResult != null) {
                            boolean isAppAction = cmdResult.startsWith("Opening ") || cmdResult.startsWith("Closing ")
                                               || cmdResult.startsWith("I tried to open ") || cmdResult.startsWith("I tried to close ");

                            if (isAppAction && intent != IntentDetector.Intent.COMMAND) {
                                System.out.println("[JARVIS] → LLM RESPONSE (blocked app action for intent: " + intent.name() + ")");
                                response = safeOllamaResponse(rawMessage, safeFallback, mode);
                            } else {
                                System.out.println("[JARVIS] → COMMAND EXECUTED: " + cmdResult);
                                response = cmdResult;
                            }
                        } else {
                            System.out.println("[JARVIS] → LLM RESPONSE (intent: " + intent.name() + ")");
                            response = safeOllamaResponse(rawMessage, safeFallback, mode);
                        }
                    } catch (Exception e) {
                        System.out.println("[JARVIS] ERROR: Command processing failed — " + e.getMessage());
                        response = safeFallback;
                    }
                } catch (Exception e) {
                    System.out.println("[JARVIS] ERROR: Processing failed — " + e.getMessage());
                    response = safeFallback;
                }

                databaseManager.saveMessage(chatId, "assistant", response, mode);

                // Step 7: Return response
                sendJsonResponse(exchange, 200, response);

            } catch (Exception e) {
                e.printStackTrace();
                String errorResponse = "{\"response\":\"Internal server error\"}";
                try {
                    sendJsonString(exchange, 500, errorResponse);
                } catch (IOException ignored) {
                    // Nothing more we can do
                }
            } finally {
                exchange.close();
            }
        }
    }

    private class StartChatHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) {
            try {
                addCorsHeaders(exchange);
                if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                    exchange.sendResponseHeaders(204, -1);
                    return;
                }

                if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    sendJsonResponse(exchange, 405, "Method not allowed. Use POST.");
                    return;
                }

                String requestBody = readRequestBody(exchange.getRequestBody());
                String title = null;
                if (requestBody != null && !requestBody.trim().isEmpty()) {
                    try {
                        JSONObject json = new JSONObject(requestBody);
                        title = json.optString("title", null);
                    } catch (JSONException e) {
                        sendJsonResponse(exchange, 400, "Invalid request format. Send JSON.");
                        return;
                    }
                }

                int chatId = databaseManager.createChat(title);
                JSONObject responseJson = new JSONObject();
                responseJson.put("chatId", chatId);
                sendJsonString(exchange, 200, responseJson.toString());
            } catch (Exception e) {
                e.printStackTrace();
                String errorResponse = "{\"response\":\"Internal server error\"}";
                try {
                    sendJsonString(exchange, 500, errorResponse);
                } catch (IOException ignored) {
                    // Nothing more we can do
                }
            } finally {
                exchange.close();
            }
        }
    }

    private class ListChatsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) {
            try {
                addCorsHeaders(exchange);
                if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                    exchange.sendResponseHeaders(204, -1);
                    return;
                }

                if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    sendJsonResponse(exchange, 405, "Method not allowed. Use GET.");
                    return;
                }

                List<DatabaseManager.ChatSummary> chats = databaseManager.getAllChats();
                JSONArray response = new JSONArray();
                for (DatabaseManager.ChatSummary chat : chats) {
                    JSONObject item = new JSONObject();
                    item.put("id", chat.getId());
                    item.put("title", chat.getTitle());
                    response.put(item);
                }
                sendJsonString(exchange, 200, response.toString());
            } catch (Exception e) {
                e.printStackTrace();
                String errorResponse = "{\"response\":\"Internal server error\"}";
                try {
                    sendJsonString(exchange, 500, errorResponse);
                } catch (IOException ignored) {
                    // Nothing more we can do
                }
            } finally {
                exchange.close();
            }
        }
    }

    private class ChatHistoryHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) {
            try {
                addCorsHeaders(exchange);
                if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                    exchange.sendResponseHeaders(204, -1);
                    return;
                }

                if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    sendJsonResponse(exchange, 405, "Method not allowed. Use GET.");
                    return;
                }

                Integer chatId = getQueryParamInt(exchange, "chatId");
                if (chatId == null || chatId <= 0) {
                    sendJsonResponse(exchange, 400, "Missing chatId.");
                    return;
                }

                List<DatabaseManager.MessageRecord> messages = databaseManager.getMessages(chatId);
                JSONArray response = new JSONArray();
                for (DatabaseManager.MessageRecord message : messages) {
                    JSONObject item = new JSONObject();
                    item.put("role", message.getRole());
                    item.put("content", message.getContent());
                    item.put("mode", message.getMode());
                    response.put(item);
                }
                sendJsonString(exchange, 200, response.toString());
            } catch (Exception e) {
                e.printStackTrace();
                String errorResponse = "{\"response\":\"Internal server error\"}";
                try {
                    sendJsonString(exchange, 500, errorResponse);
                } catch (IOException ignored) {
                    // Nothing more we can do
                }
            } finally {
                exchange.close();
            }
        }
    }

    private class DeleteChatHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) {
            try {
                addCorsHeaders(exchange);
                if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                    exchange.sendResponseHeaders(204, -1);
                    return;
                }

                if (!"DELETE".equalsIgnoreCase(exchange.getRequestMethod())) {
                    sendJsonResponse(exchange, 405, "Method not allowed. Use DELETE.");
                    return;
                }

                Integer chatId = getQueryParamInt(exchange, "chatId");
                if (chatId == null || chatId <= 0) {
                    sendJsonString(exchange, 400, "{\"success\":false,\"error\":\"Missing chatId.\"}");
                    return;
                }

                System.out.println("[JARVIS] Deleting chat: " + chatId);
                boolean deleted = databaseManager.deleteChat(chatId);

                JSONObject responseJson = new JSONObject();
                responseJson.put("success", deleted);
                sendJsonString(exchange, deleted ? 200 : 404, responseJson.toString());
            } catch (Exception e) {
                e.printStackTrace();
                String errorResponse = "{\"success\":false,\"error\":\"Internal server error\"}";
                try {
                    sendJsonString(exchange, 500, errorResponse);
                } catch (IOException ignored) {
                    // Nothing more we can do
                }
            } finally {
                exchange.close();
            }
        }
    }

    private void addCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
    }

    private void sendJsonResponse(HttpExchange exchange, int statusCode, String message) throws IOException {
        JSONObject responseJson = new JSONObject();
        responseJson.put("response", message);
        sendJsonString(exchange, statusCode, responseJson.toString());
    }

    private void sendJsonString(HttpExchange exchange, int statusCode, String responseJson) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        byte[] bytes = responseJson.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private String readRequestBody(InputStream is) throws IOException {
        return new String(is.readAllBytes(), StandardCharsets.UTF_8);
    }

    private Integer getQueryParamInt(HttpExchange exchange, String key) {
        String query = exchange.getRequestURI().getQuery();
        if (query == null || query.isEmpty()) {
            return null;
        }
        String[] parts = query.split("&");
        for (String part : parts) {
            String[] kv = part.split("=", 2);
            if (kv.length == 2 && key.equals(kv[0])) {
                try {
                    return Integer.parseInt(kv[1]);
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    private String safeOllamaResponse(String rawMessage, String fallback, String mode) {
        try {
            String response = ollamaService.getResponse(rawMessage, mode);
            if (response == null || response.trim().isEmpty()) {
                return fallback;
            }
            if ("Jarvis is offline.".equalsIgnoreCase(response.trim())) {
                return fallback;
            }
            return response;
        } catch (Exception e) {
            System.out.println("[JARVIS] ERROR: LLM call failed — " + e.getMessage());
            return fallback;
        }
    }

    /**
     * Serves static frontend files (index.html, styles.css, script.js)
     * from the frontend/ directory next to the working directory.
     * This makes the UI same-origin as the API — no CORS issues.
     */
    private static class StaticFileHandler implements HttpHandler {

        // Resolve frontend directory relative to process working directory
        private static final Path FRONTEND_DIR =
            Paths.get(System.getProperty("user.dir"), "frontend").toAbsolutePath();

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                addCorsHeaders(exchange);

                if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                    exchange.sendResponseHeaders(204, -1);
                    return;
                }

                String uriPath = exchange.getRequestURI().getPath();

                // Default to index.html
                if ("/".equals(uriPath) || uriPath.isEmpty()) {
                    uriPath = "/index.html";
                }

                // Strip leading slash, resolve file
                Path filePath = FRONTEND_DIR.resolve(uriPath.substring(1)).normalize();

                // Security: disallow path traversal outside frontend dir
                if (!filePath.startsWith(FRONTEND_DIR)) {
                    exchange.sendResponseHeaders(403, -1);
                    return;
                }

                if (!Files.exists(filePath) || Files.isDirectory(filePath)) {
                    byte[] body = "Not found".getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(404, body.length);
                    exchange.getResponseBody().write(body);
                    return;
                }

                String contentType = getContentType(filePath.getFileName().toString());
                exchange.getResponseHeaders().set("Content-Type", contentType);

                byte[] bytes = Files.readAllBytes(filePath);
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }

            } catch (Exception e) {
                System.out.println("[JARVIS] Static file error: " + e.getMessage());
                exchange.sendResponseHeaders(500, -1);
            } finally {
                exchange.close();
            }
        }

        private static void addCorsHeaders(HttpExchange exchange) {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
        }

        private static String getContentType(String filename) {
            if (filename.endsWith(".html")) return "text/html; charset=UTF-8";
            if (filename.endsWith(".css"))  return "text/css; charset=UTF-8";
            if (filename.endsWith(".js"))   return "application/javascript; charset=UTF-8";
            if (filename.endsWith(".json")) return "application/json";
            if (filename.endsWith(".png"))  return "image/png";
            if (filename.endsWith(".ico"))  return "image/x-icon";
            return "application/octet-stream";
        }
    }
}
