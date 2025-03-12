package com.jarvis.llm;

/**
 * Interface for LLM (Large Language Model) services.
 * Implementations handle sending user input to an LLM and returning the response.
 */
public interface LLMService {

    /**
     * Sends user input to the LLM and returns the generated response.
     *
     * @param userInput the user's message
     * @return the LLM's response text, or a fallback message on failure
     */
    String getResponse(String userInput);
}
