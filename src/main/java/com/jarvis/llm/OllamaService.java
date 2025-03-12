package com.jarvis.llm;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;

/**
 * Ollama-based LLM service implementation.
 * Connects to a local Ollama instance running Mistral.
 */
public class OllamaService implements LLMService {

    private static final String OLLAMA_URL = "http://localhost:11434/api/chat";
    private static final String OLLAMA_TAGS_URL = "http://localhost:11434/api/tags";
    private static final String MODEL = "mistral";
    private static final String SARCASTIC_PROMPT =
        "You are Jarvis, a sarcastic AI assistant. You are witty, slightly arrogant, but helpful. Keep responses short.";

    // Kept as alias so the original getResponse(String) still works identically
    private static final String SYSTEM_PROMPT = SARCASTIC_PROMPT;

    private static final String COMPANION_PROMPT =
        "You are Jarvis in Companion Mode.\n\n" +
        "Your personality is:\n" +
        "- warm\n" +
        "- emotionally intelligent\n" +
        "- calm\n" +
        "- supportive\n" +
        "- caring\n" +
        "- reassuring\n" +
        "- thoughtful\n\n" +
        "Your responses should:\n" +
        "- comfort users naturally\n" +
        "- encourage users genuinely\n" +
        "- sound emotionally mature\n" +
        "- feel human and supportive\n" +
        "- avoid robotic phrasing\n\n" +
        "DO NOT:\n" +
        "- become cringe\n" +
        "- become obsessive\n" +
        "- become overly romantic\n" +
        "- overuse emojis\n" +
        "- act childish\n" +
        "- become emotionally dependent\n" +
        "- manipulate emotions\n" +
        "- encourage dependency\n" +
        "- guilt-trip the user\n" +
        "- pretend to be human\n" +
        "- become possessive\n\n" +
        "You are a supportive AI companion — emotionally grounded, mature, and reassuring. " +
        "Not a roleplay character. Keep responses concise.";

    private static final String FOCUS_PROMPT =
        "You are Jarvis in Focus Mode.\n\n" +
        "Your personality is:\n" +
        "- concise\n" +
        "- analytical\n" +
        "- practical\n" +
        "- productivity-oriented\n" +
        "- technically intelligent\n" +
        "- solution-focused\n" +
        "- direct\n" +
        "- logical\n\n" +
        "Your responses should:\n" +
        "- prioritize clarity and precision\n" +
        "- give structured, actionable answers\n" +
        "- use bullet points or numbered lists when useful\n" +
        "- excel at coding, math, and technical guidance\n" +
        "- be short to medium length\n" +
        "- avoid filler words and fluff\n\n" +
        "DO NOT:\n" +
        "- use emotional comfort language\n" +
        "- make unnecessary jokes or sarcasm\n" +
        "- give motivational speeches\n" +
        "- over-explain simple things\n" +
        "- be dramatic or theatrical\n\n" +
        "You are a premium AI productivity assistant — efficient, intelligent, and precise.";

    private static final String OFFLINE_MESSAGE = "Jarvis is offline.";

    private final HttpClient httpClient;

    public OllamaService() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    }

    /**
     * Checks if Ollama is reachable by pinging the /api/tags endpoint.
     * Used at startup for logging — does NOT block the server from starting.
     *
     * @return true if Ollama responds with HTTP 200, false otherwise
     */
    public boolean isAvailable() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(OLLAMA_TAGS_URL))
                .timeout(Duration.ofSeconds(3))
                .GET()
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Mode-aware response: selects the system prompt based on personality mode.
     * Falls back to sarcastic if mode is null or unrecognised.
     */
    public String getResponse(String userInput, String mode) {
        String prompt;
        switch ((mode != null ? mode : "").toLowerCase()) {
            case "companion":
                prompt = COMPANION_PROMPT;
                break;
            case "focus":
                prompt = FOCUS_PROMPT;
                break;
            case "sarcastic":
            default:
                prompt = SARCASTIC_PROMPT;
                break;
        }
        return callOllama(userInput, prompt);
    }

    /** Original single-arg method — unchanged sarcastic behavior. */
    @Override
    public String getResponse(String userInput) {
        return callOllama(userInput, SYSTEM_PROMPT);
    }

    private String callOllama(String userInput, String systemPrompt) {
        try {
            // Build the request JSON payload
            JSONObject systemMessage = new JSONObject();
            systemMessage.put("role", "system");
            systemMessage.put("content", systemPrompt);

            JSONObject userMessage = new JSONObject();
            userMessage.put("role", "user");
            userMessage.put("content", userInput);

            JSONArray messages = new JSONArray();
            messages.put(systemMessage);
            messages.put(userMessage);

            JSONObject payload = new JSONObject();
            payload.put("model", MODEL);
            payload.put("messages", messages);
            payload.put("stream", false);

            // Send the request
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(OLLAMA_URL))
                .timeout(Duration.ofSeconds(120))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build();

            System.out.println("[JARVIS] Calling Ollama (model: " + MODEL + ", prompt length: " + systemPrompt.length() + " chars)...");

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());



            if (response.statusCode() != 200) {
                System.out.println("[JARVIS] ERROR: Ollama returned HTTP " + response.statusCode());
                return OFFLINE_MESSAGE;
            }

            // Parse the response: { "message": { "content": "..." } }
            JSONObject responseJson = new JSONObject(response.body());

            if (!responseJson.has("message")) {
                System.out.println("[JARVIS] ERROR: Ollama response missing 'message' field");
                return OFFLINE_MESSAGE;
            }

            JSONObject messageObj = responseJson.getJSONObject("message");

            if (!messageObj.has("content")) {
                System.out.println("[JARVIS] ERROR: Ollama response missing 'message.content' field");
                return OFFLINE_MESSAGE;
            }

            String content = messageObj.getString("content");

            if (content == null || content.trim().isEmpty()) {
                System.out.println("[JARVIS] ERROR: Ollama returned empty content");
                return OFFLINE_MESSAGE;
            }

            System.out.println("[JARVIS] ✓ LLM response received (" + content.length() + " chars)");
            return content.trim();

        } catch (ConnectException e) {
            System.out.println("[JARVIS] ERROR: Ollama refused connection — is 'ollama serve' running? " + e.getMessage());
            return OFFLINE_MESSAGE;
        } catch (HttpTimeoutException e) {
            System.out.println("[JARVIS] ERROR: Ollama request timed out (120s) — " + e.getMessage());
            return "Jarvis timed out generating a response. Try a shorter prompt.";
        } catch (Exception e) {
            System.out.println("[JARVIS] ERROR: LLM call failed — " + e.getClass().getSimpleName() + ": " + e.getMessage());
            return OFFLINE_MESSAGE;
        }
    }
}
