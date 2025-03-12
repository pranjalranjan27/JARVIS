package com.jarvis;

import com.jarvis.api.ApiServer;
import com.jarvis.llm.OllamaService;

/**
 * Jarvis Application entry point.
 * Starts the API server with startup diagnostics.
 */
public class JarvisApp {

    public static void main(String[] args) {
        System.out.println("[JARVIS] Initializing...");

        // Wait for Ollama to become available (it may still be booting)
        OllamaService ollama = new OllamaService();
        boolean ollamaReady = false;
        int maxAttempts = 5;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            if (ollama.isAvailable()) {
                ollamaReady = true;
                System.out.println("[JARVIS] ✓ Ollama is reachable (attempt " + attempt + "/" + maxAttempts + ")");
                break;
            }
            System.out.println("[JARVIS] Waiting for Ollama... (attempt " + attempt + "/" + maxAttempts + ")");
            try {
                Thread.sleep(3000);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        if (!ollamaReady) {
            System.out.println("[JARVIS] ⚠ WARNING: Ollama not reachable after " + maxAttempts + " attempts.");
            System.out.println("[JARVIS]   Make sure Ollama is running: ollama serve");
            System.out.println("[JARVIS]   Commands and local queries will still work.");
        }

        // Start the API server regardless of Ollama status
        ApiServer server = new ApiServer();
        server.start();
    }
}
