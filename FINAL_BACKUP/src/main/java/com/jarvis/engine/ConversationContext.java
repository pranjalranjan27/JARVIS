package com.jarvis.engine;

/**
 * Maintains short-term conversational memory including repetition tracking
 * and an annoyance level that influences Jarvis's response strategy.
 */
public class ConversationContext {

    private String lastMessage = "";
    private int repetitionCount = 0;
    private int annoyanceLevel = 0;  // 0–10
    private int messageCount = 0;
    private IntentDetector.Intent lastIntent = IntentDetector.Intent.NONSENSE;

    /**
     * Updates context based on the latest user message and its detected intent.
     */
    public void update(String normalizedMessage, IntentDetector.Intent intent) {
        messageCount++;

        // Detect repetition
        if (normalizedMessage.equals(lastMessage)) {
            repetitionCount++;
            // Repetition is annoying
            annoyanceLevel = Math.min(10, annoyanceLevel + 2);
        } else {
            repetitionCount = 0;
            // New message — decay annoyance slightly
            annoyanceLevel = Math.max(0, annoyanceLevel - 1);
        }

        // Commands are inherently pushy
        if (intent == IntentDetector.Intent.COMMAND) {
            annoyanceLevel = Math.min(10, annoyanceLevel + 1);
        }

        // Small talk cools things down
        if (intent == IntentDetector.Intent.SMALL_TALK) {
            annoyanceLevel = Math.max(0, annoyanceLevel - 1);
        }

        lastMessage = normalizedMessage;
        lastIntent = intent;
    }

    public String getLastMessage() {
        return lastMessage;
    }

    public int getRepetitionCount() {
        return repetitionCount;
    }

    public int getAnnoyanceLevel() {
        return annoyanceLevel;
    }

    public int getMessageCount() {
        return messageCount;
    }

    public IntentDetector.Intent getLastIntent() {
        return lastIntent;
    }

    /**
     * Resets context (e.g., on conversation restart).
     */
    public void reset() {
        lastMessage = "";
        repetitionCount = 0;
        annoyanceLevel = 0;
        messageCount = 0;
        lastIntent = IntentDetector.Intent.NONSENSE;
    }
}
