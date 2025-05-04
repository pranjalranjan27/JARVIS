package com.jarvis.engine;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Classifies user messages into one of four intents using keyword/pattern matching.
 */
public class IntentDetector {

    public enum Intent {
        COMMAND,
        QUESTION,
        SMALL_TALK,
        NONSENSE
    }
// Conversational intent detection for sarcastic interactions
    private static final List<String> COMMAND_VERBS = Arrays.asList(
        "open", "launch", "run", "start", "show", "close", "play",
        "execute", "stop", "shut", "delete", "create", "find",
        "search", "install", "download", "print", "calculate", "compute",
        "tell", "give", "get", "set", "make", "do", "send", "check"
    );

    // Phrases that imply the user wants something done (a request/command)
    private static final List<String> REQUEST_PHRASES = Arrays.asList(
        "i want", "i need", "i'd like", "i would like",
        "can you", "could you", "would you", "will you",
        "please", "plz", "pls",
        "give me", "tell me", "show me", "get me", "help me",
        "i want to know", "i need to know", "let me know",
        "i wanna", "i gotta", "i gotta know"
    );

    private static final List<String> QUESTION_STARTERS = Arrays.asList(
        "what", "who", "where", "when", "why", "how", "is", "are",
        "can", "could", "would", "should", "do", "does", "did",
        "will", "which", "tell me", "explain", "define"
    );

    // Keywords that signal a question when found anywhere in the input
    private static final List<String> QUESTION_KEYWORDS = Arrays.asList(
        "what time", "what date", "what day", "what is", "what's",
        "how much", "how many", "how long", "how far",
        "tell me the", "know the", "know what"
    );

    private static final List<String> SMALL_TALK_PHRASES = Arrays.asList(
        "hello", "hi", "hey", "howdy", "greetings", "sup",
        "good morning", "good afternoon", "good evening", "good night",
        "how are you", "how's it going", "what's up", "whats up",
        "thanks", "thank you", "bye", "goodbye", "see you",
        "nice", "cool", "okay", "ok", "sure", "great", "awesome",
        "lol", "haha", "hehe", "yo", "wassup", "bruh", "bruhh", "bro",
        "sorry", "my bad", "oops", "help",
        "dude", "man", "mate", "fam", "homie"
    );

    private static final Pattern QUESTION_MARK = Pattern.compile(".*\\?\\s*$");

    /**
     * Detects the intent of a normalized input string.
     */
    public Intent detectIntent(String normalizedInput) {
        if (normalizedInput == null || normalizedInput.isEmpty()) {
            return Intent.NONSENSE;
        }

        // Strip leading casual words like "bro", "bruh", "dude", "man", "hey" to get the real intent
        String stripped = stripCasualPrefix(normalizedInput);

        // Check for questions first — question mark is a strong signal
        if (QUESTION_MARK.matcher(normalizedInput).matches()) {
            return Intent.QUESTION;
        }

        // Check for question keywords anywhere in the input
        for (String keyword : QUESTION_KEYWORDS) {
            if (normalizedInput.contains(keyword)) {
                return Intent.QUESTION;
            }
        }

        // Check for request phrases (implies a command/question)
        for (String phrase : REQUEST_PHRASES) {
            if (normalizedInput.startsWith(phrase + " ") || normalizedInput.startsWith(phrase + ",") ||
                normalizedInput.equals(phrase) ||
                stripped.startsWith(phrase + " ") || stripped.startsWith(phrase + ",") ||
                stripped.equals(phrase)) {
                // If the request contains question-like words, it's a question
                if (normalizedInput.contains("know") || normalizedInput.contains("what") ||
                    normalizedInput.contains("when") || normalizedInput.contains("time") ||
                    normalizedInput.contains("weather") || normalizedInput.contains("who")) {
                    return Intent.QUESTION;
                }
                return Intent.COMMAND;
            }
        }

        // Check for question starters
        for (String starter : QUESTION_STARTERS) {
            if (normalizedInput.startsWith(starter + " ") || normalizedInput.equals(starter) ||
                stripped.startsWith(starter + " ") || stripped.equals(starter)) {
                return Intent.QUESTION;
            }
        }

        // Check for commands — starts with a command verb
        String firstWord = stripped.split("\\s+")[0];
        if (COMMAND_VERBS.contains(firstWord)) {
            return Intent.COMMAND;
        }
        // Also check original first word
        String origFirst = normalizedInput.split("\\s+")[0];
        if (COMMAND_VERBS.contains(origFirst)) {
            return Intent.COMMAND;
        }

        // Check for small talk (greetings, etc.)
        for (String phrase : SMALL_TALK_PHRASES) {
            if (normalizedInput.equals(phrase) || normalizedInput.startsWith(phrase + " ") ||
                normalizedInput.startsWith(phrase + ",") || normalizedInput.startsWith(phrase + "!")) {
                return Intent.SMALL_TALK;
            }
        }

        // Short messages (1-3 words) that didn't match anything are likely small talk
        int wordCount = normalizedInput.split("\\s+").length;
        if (wordCount <= 2) {
            return Intent.SMALL_TALK;
        }

        // Default: nonsense
        return Intent.NONSENSE;
    }

    /**
     * Strips casual/slang prefixes like "bro", "bruh", "dude", "man", "hey"
     * so we can detect the real intent beneath them.
     */
    private String stripCasualPrefix(String input) {
        String[] casualPrefixes = {"bro ", "bruh ", "bruhh ", "dude ", "man ", "mate ",
                                   "yo ", "hey ", "come on ", "common ", "come-on ",
                                   "seriously ", "like ", "ok ", "okay ", "so ",
                                   "no ", "nah ", "nope ", "but ", "and ", "just ", "wait ",
                                   "bro, ", "bruh, ", "bruhh, ", "dude, ", "man, ", "hey, ",
                                   "seriously, ", "like, ", "ok, ", "okay, ", "so, ",
                                   "no, ", "nah, ", "nope, ", "but, ", "and, ", "wait, "};
        String result = input;
        // Strip multiple casual prefixes (e.g., "bro seriously please tell me")
        boolean changed = true;
        while (changed) {
            changed = false;
            for (String prefix : casualPrefixes) {
                if (result.startsWith(prefix)) {
                    result = result.substring(prefix.length()).trim();
                    changed = true;
                    break;
                }
            }
        }
        return result;
    }
}
