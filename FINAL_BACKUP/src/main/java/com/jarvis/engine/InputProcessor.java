package com.jarvis.engine;

/**
 * Normalizes raw user input for consistent downstream processing.
 */
public class InputProcessor {

    /**
     * Cleans and normalizes user input.
     * - Trims leading/trailing whitespace
     * - Converts to lowercase
     * - Collapses multiple spaces into one
     * - Strips trailing punctuation noise (multiple punctuation marks)
     */
    public String normalize(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return "";
        }

        String result = raw.trim().toLowerCase();

        // Collapse multiple spaces
        result = result.replaceAll("\\s+", " ");

        // Strip excessive trailing punctuation (e.g., "hello!!!" → "hello")
        // but keep a single ? or . or !
        result = result.replaceAll("([?!.])\\1+$", "$1");

        return result;
    }
}
