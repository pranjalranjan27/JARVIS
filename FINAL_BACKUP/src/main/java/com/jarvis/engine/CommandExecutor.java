package com.jarvis.engine;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Executes safe system commands when Jarvis decides to comply.
 * Supports opening and closing any application by name on Windows.
 */
public class CommandExecutor {

    // Map of known app names → Windows launch commands
    private static final Map<String, String[]> KNOWN_APPS = new LinkedHashMap<>();
    // Map of known app names → Windows process names (for taskkill)
    private static final Map<String, String> KNOWN_PROCESSES = new LinkedHashMap<>();

    static {
        // Browsers
        KNOWN_APPS.put("chrome",    new String[]{"cmd", "/c", "start", "chrome"});
        KNOWN_APPS.put("google chrome", new String[]{"cmd", "/c", "start", "chrome"});
        KNOWN_APPS.put("firefox",   new String[]{"cmd", "/c", "start", "firefox"});
        KNOWN_APPS.put("brave",     new String[]{"cmd", "/c", "start", "brave"});
        KNOWN_APPS.put("edge",      new String[]{"cmd", "/c", "start", "msedge"});
        KNOWN_APPS.put("opera",     new String[]{"cmd", "/c", "start", "opera"});

        // Communication
        KNOWN_APPS.put("discord",   new String[]{"cmd", "/c", "start", "", "discord:"});
        KNOWN_APPS.put("telegram",  new String[]{"cmd", "/c", "start", "", "tg:"});
        KNOWN_APPS.put("whatsapp",  new String[]{"cmd", "/c", "start", "", "whatsapp:"});
        KNOWN_APPS.put("slack",     new String[]{"cmd", "/c", "start", "slack"});
        KNOWN_APPS.put("teams",     new String[]{"cmd", "/c", "start", "", "msteams:"});
        KNOWN_APPS.put("microsoft teams", new String[]{"cmd", "/c", "start", "", "msteams:"});
        KNOWN_APPS.put("zoom",      new String[]{"cmd", "/c", "start", "", "zoommtg:"});
        KNOWN_APPS.put("skype",     new String[]{"cmd", "/c", "start", "", "skype:"});

        // Dev tools
        KNOWN_APPS.put("vscode",    new String[]{"cmd", "/c", "start", "", "code"});
        KNOWN_APPS.put("vs code",   new String[]{"cmd", "/c", "start", "", "code"});
        KNOWN_APPS.put("visual studio code", new String[]{"cmd", "/c", "start", "", "code"});
        KNOWN_APPS.put("terminal",  new String[]{"cmd", "/c", "start", "cmd"});
        KNOWN_APPS.put("cmd",       new String[]{"cmd", "/c", "start", "cmd"});
        KNOWN_APPS.put("powershell", new String[]{"cmd", "/c", "start", "powershell"});
        KNOWN_APPS.put("git bash",  new String[]{"cmd", "/c", "start", "", "git-bash"});

        // Productivity
        KNOWN_APPS.put("notepad",   new String[]{"notepad.exe"});
        KNOWN_APPS.put("calculator", new String[]{"calc.exe"});
        KNOWN_APPS.put("calc",      new String[]{"calc.exe"});
        KNOWN_APPS.put("paint",     new String[]{"mspaint.exe"});
        KNOWN_APPS.put("word",      new String[]{"cmd", "/c", "start", "winword"});
        KNOWN_APPS.put("excel",     new String[]{"cmd", "/c", "start", "excel"});
        KNOWN_APPS.put("powerpoint", new String[]{"cmd", "/c", "start", "powerpnt"});

        // Media & Entertainment
        KNOWN_APPS.put("spotify",   new String[]{"cmd", "/c", "start", "", "spotify:"});
        KNOWN_APPS.put("vlc",       new String[]{"cmd", "/c", "start", "vlc"});
        KNOWN_APPS.put("steam",     new String[]{"cmd", "/c", "start", "", "steam:"});
        KNOWN_APPS.put("epic games", new String[]{"cmd", "/c", "start", "", "com.epicgames.launcher:"});

        // System
        KNOWN_APPS.put("file explorer", new String[]{"explorer.exe"});
        KNOWN_APPS.put("explorer",  new String[]{"explorer.exe"});
        KNOWN_APPS.put("files",     new String[]{"explorer.exe"});
        KNOWN_APPS.put("settings",  new String[]{"cmd", "/c", "start", "ms-settings:"});
        KNOWN_APPS.put("task manager", new String[]{"taskmgr.exe"});
        KNOWN_APPS.put("control panel", new String[]{"control.exe"});

        // Process names for closing apps
        KNOWN_PROCESSES.put("chrome", "chrome.exe");
        KNOWN_PROCESSES.put("google chrome", "chrome.exe");
        KNOWN_PROCESSES.put("firefox", "firefox.exe");
        KNOWN_PROCESSES.put("brave", "brave.exe");
        KNOWN_PROCESSES.put("edge", "msedge.exe");
        KNOWN_PROCESSES.put("opera", "opera.exe");
        KNOWN_PROCESSES.put("discord", "Discord.exe");
        KNOWN_PROCESSES.put("telegram", "Telegram.exe");
        KNOWN_PROCESSES.put("whatsapp", "WhatsApp.Root.exe");
        KNOWN_PROCESSES.put("slack", "slack.exe");
        KNOWN_PROCESSES.put("teams", "ms-teams.exe");
        KNOWN_PROCESSES.put("microsoft teams", "ms-teams.exe");
        KNOWN_PROCESSES.put("zoom", "Zoom.exe");
        KNOWN_PROCESSES.put("skype", "Skype.exe");
        KNOWN_PROCESSES.put("vscode", "Code.exe");
        KNOWN_PROCESSES.put("vs code", "Code.exe");
        KNOWN_PROCESSES.put("notepad", "notepad.exe");
        KNOWN_PROCESSES.put("calculator", "CalculatorApp.exe");
        KNOWN_PROCESSES.put("paint", "mspaint.exe");
        KNOWN_PROCESSES.put("word", "WINWORD.EXE");
        KNOWN_PROCESSES.put("excel", "EXCEL.EXE");
        KNOWN_PROCESSES.put("powerpoint", "POWERPNT.EXE");
        KNOWN_PROCESSES.put("spotify", "Spotify.exe");
        KNOWN_PROCESSES.put("vlc", "vlc.exe");
        KNOWN_PROCESSES.put("steam", "steam.exe");
    }

    // Close/kill verbs
    private static final List<String> CLOSE_VERBS = Arrays.asList(
        "close", "kill", "quit", "exit", "stop", "shut", "end", "terminate"
    );

    private static final List<String> TIME_PHRASES = Arrays.asList(
        "what time", "what's the time", "whats the time", "current time",
        "tell me the time", "the time please", "time right now",
        "time is it", "clock", "what hour"
    );

    private static final List<String> DATE_PHRASES = Arrays.asList(
        "what date", "what's the date", "whats the date", "current date",
        "tell me the date", "the date today", "the date please",
        "date is it", "date today", "today's date", "todays date"
    );

    private static final List<String> DAY_PHRASES = Arrays.asList(
        "what day", "what's the day", "whats the day", "current day",
        "tell me the day", "day is it", "day today", "day of the week",
        "which day", "today's day"
    );

    private static final List<String> MONTH_PHRASES = Arrays.asList(
        "what month", "what's the month", "whats the month", "current month",
        "tell me the month", "month is it", "which month", "month right now"
    );

    private static final List<String> YEAR_PHRASES = Arrays.asList(
        "what year", "what's the year", "whats the year", "current year",
        "tell me the year", "year is it", "which year", "year right now"
    );

    // General knowledge: keyword phrases → factual answers
    private static final LinkedHashMap<List<String>, String> GENERAL_KNOWLEDGE = new LinkedHashMap<>();

    static {
        // Sun
        GENERAL_KNOWLEDGE.put(Arrays.asList("sun rise", "sun comes up", "sunrise direction", "where does the sun rise"),
            "The sun rises in the east.");
        GENERAL_KNOWLEDGE.put(Arrays.asList("sun set", "sun goes down", "sunset direction", "where does the sun set"),
            "The sun sets in the west.");

        // Moon
        GENERAL_KNOWLEDGE.put(Arrays.asList("moon come out", "moon show up", "moon at night", "when does the moon", "moon appear"),
            "The moon is typically visible at night, though it can sometimes be seen during the day too.");

        // Earth & Space
        GENERAL_KNOWLEDGE.put(Arrays.asList("earth revolve", "earth orbit", "earth go around", "earth rotate around"),
            "The Earth revolves around the Sun, completing one orbit approximately every 365.25 days.");
        GENERAL_KNOWLEDGE.put(Arrays.asList("how many planets", "number of planets", "planets in solar system"),
            "There are 8 planets in our solar system: Mercury, Venus, Earth, Mars, Jupiter, Saturn, Uranus, and Neptune.");
        GENERAL_KNOWLEDGE.put(Arrays.asList("closest star", "nearest star"),
            "The closest star to Earth is the Sun. The next closest is Proxima Centauri, about 4.24 light-years away.");
        GENERAL_KNOWLEDGE.put(Arrays.asList("speed of light", "how fast is light", "light speed"),
            "The speed of light in a vacuum is approximately 299,792,458 meters per second (about 3 x 10^8 m/s).");

        // Water
        GENERAL_KNOWLEDGE.put(Arrays.asList("water freeze", "freezing point", "water turn to ice"),
            "Water freezes at 0°C (32°F) at standard atmospheric pressure.");
        GENERAL_KNOWLEDGE.put(Arrays.asList("water boil", "boiling point"),
            "Water boils at 100°C (212°F) at standard atmospheric pressure.");

        // Colors / Nature
        GENERAL_KNOWLEDGE.put(Arrays.asList("sky blue", "color of the sky", "colour of the sky", "why is the sky"),
            "The sky appears blue because of Rayleigh scattering — shorter blue wavelengths of sunlight scatter more in the atmosphere.");
        GENERAL_KNOWLEDGE.put(Arrays.asList("grass green", "color of grass", "colour of grass", "why is grass"),
            "Grass is green because of chlorophyll, the pigment that absorbs sunlight for photosynthesis.");

        // Gravity
        GENERAL_KNOWLEDGE.put(Arrays.asList("gravity", "things fall down", "what goes up must"),
            "Gravity is the force that attracts objects toward the center of the Earth (or any other body with mass). On Earth, gravitational acceleration is about 9.8 m/s².");

        // Geography
        GENERAL_KNOWLEDGE.put(Arrays.asList("largest ocean", "biggest ocean"),
            "The Pacific Ocean is the largest and deepest ocean on Earth.");
        GENERAL_KNOWLEDGE.put(Arrays.asList("tallest mountain", "highest mountain", "mount everest"),
            "Mount Everest is the tallest mountain above sea level, standing at 8,849 meters (29,032 feet).");
        GENERAL_KNOWLEDGE.put(Arrays.asList("longest river", "biggest river"),
            "The Nile is traditionally considered the longest river at about 6,650 km, though some measurements put the Amazon slightly longer.");
        GENERAL_KNOWLEDGE.put(Arrays.asList("largest country", "biggest country"),
            "Russia is the largest country in the world by area, spanning over 17.1 million square kilometers.");
        GENERAL_KNOWLEDGE.put(Arrays.asList("largest continent", "biggest continent"),
            "Asia is the largest continent by both area and population.");

        // Science basics
        GENERAL_KNOWLEDGE.put(Arrays.asList("capital of india"),
            "The capital of India is New Delhi.");
        GENERAL_KNOWLEDGE.put(Arrays.asList("capital of usa", "capital of america", "capital of united states"),
            "The capital of the United States is Washington, D.C.");
        GENERAL_KNOWLEDGE.put(Arrays.asList("capital of uk", "capital of england", "capital of britain"),
            "The capital of the United Kingdom is London.");
        GENERAL_KNOWLEDGE.put(Arrays.asList("capital of france"),
            "The capital of France is Paris.");
        GENERAL_KNOWLEDGE.put(Arrays.asList("capital of japan"),
            "The capital of Japan is Tokyo.");
        GENERAL_KNOWLEDGE.put(Arrays.asList("how many continents", "number of continents"),
            "There are 7 continents: Asia, Africa, North America, South America, Antarctica, Europe, and Australia/Oceania.");
        GENERAL_KNOWLEDGE.put(Arrays.asList("how many oceans", "number of oceans"),
            "There are 5 oceans: Pacific, Atlantic, Indian, Southern (Antarctic), and Arctic.");

        // Human body
        GENERAL_KNOWLEDGE.put(Arrays.asList("bones in human body", "how many bones"),
            "An adult human body has 206 bones.");
        GENERAL_KNOWLEDGE.put(Arrays.asList("largest organ", "biggest organ"),
            "The skin is the largest organ of the human body.");
    }

    // Words to strip out when extracting the app name
    private static final List<String> NOISE_WORDS = Arrays.asList(
        "open", "launch", "start", "run", "execute", "please", "plz", "pls",
        "close", "kill", "quit", "exit", "stop", "shut", "end", "terminate",
        "can", "you", "could", "would", "will", "for", "me", "my", "the",
        "a", "an", "up", "it", "i", "want", "to", "need", "like",
        "bro", "bruh", "dude", "man", "hey", "help", "just", "now",
        "do", "give", "show", "get", "sir", "app", "application", "program",
        "microsoft", "google", "down", "okay", "ok", "alright", "some", "work",
        "date", "day", "month", "year", "time", "today", "current", "right",
        "tell", "know", "whats", "what", "is", "please", "week", "of"
    );

    // Pattern to find arithmetic expressions like "59+68", "100 - 30", "5 * 6", "100/4"
    private static final Pattern MATH_EXPRESSION = Pattern.compile(
        "(-?\\d+(?:\\.\\d+)?)\\s*([+\\-*/x×])\\s*(-?\\d+(?:\\.\\d+)?)"
    );

    // Keywords that indicate the user wants a calculation
    private static final List<String> MATH_KEYWORDS = Arrays.asList(
        "sum", "add", "plus", "subtract", "minus", "multiply", "divide",
        "calculate", "compute", "math", "calc", "total"
    );

    // Patterns for temporal arithmetic like "50 years ago", "3 months from now", "in 10 days"
    private static final Pattern TEMPORAL_AGO = Pattern.compile(
        "(\\d+)\\s+(year|month|week|day|hour|minute)s?\\s+ago");
    private static final Pattern TEMPORAL_FROM_NOW = Pattern.compile(
        "(\\d+)\\s+(year|month|week|day|hour|minute)s?\\s+(?:from now|later|ahead|in the future)");
    private static final Pattern TEMPORAL_IN_FUTURE = Pattern.compile(
        "in\\s+(\\d+)\\s+(year|month|week|day|hour|minute)s?");
    private static final Pattern TEMPORAL_BEFORE = Pattern.compile(
        "(\\d+)\\s+(year|month|week|day|hour|minute)s?\\s+before");
    private static final Pattern TEMPORAL_AFTER = Pattern.compile(
        "(\\d+)\\s+(year|month|week|day|hour|minute)s?\\s+after");

    /**
     * Attempts to execute the user's command.
     * Returns a result message describing what happened, or null.
     */
    public String execute(String normalizedInput) {
        try {
            if (normalizedInput == null || normalizedInput.isEmpty()) {
                return null;
            }

            // Check for math/calculation requests first
            String mathResult = tryMath(normalizedInput);
            if (mathResult != null) {
                return mathResult;
            }

            // Check for temporal arithmetic BEFORE simple phrase matching
            // (e.g., "what year was 50 years ago" should NOT just return the current year)
            String temporalResult = tryTemporalArithmetic(normalizedInput);
            if (temporalResult != null) {
                return temporalResult;
            }

            // Check for time-related queries
            for (String phrase : TIME_PHRASES) {
                if (normalizedInput.contains(phrase)) {
                    return getTimeResponse();
                }
            }

            // Check for date-related queries
            for (String phrase : DATE_PHRASES) {
                if (normalizedInput.contains(phrase)) {
                    return getDateResponse();
                }
            }

            // Check for day-related queries
            for (String phrase : DAY_PHRASES) {
                if (normalizedInput.contains(phrase)) {
                    return getDayResponse();
                }
            }

            // Check for month-related queries
            for (String phrase : MONTH_PHRASES) {
                if (normalizedInput.contains(phrase)) {
                    return getMonthResponse();
                }
            }

            // Check for year-related queries
            for (String phrase : YEAR_PHRASES) {
                if (normalizedInput.contains(phrase)) {
                    return getYearResponse();
                }
            }

            // Check for general knowledge queries
            String knowledgeResult = tryGeneralKnowledge(normalizedInput);
            if (knowledgeResult != null) {
                return knowledgeResult;
            }

            // Detect if this is a close/kill command
            boolean isClose = false;
            for (String verb : CLOSE_VERBS) {
                if (normalizedInput.contains(verb)) {
                    isClose = true;
                    break;
                }
            }

            // Try to extract the app name
            String appName = extractAppName(normalizedInput);
            if (appName != null && !appName.isEmpty()) {
                return isClose ? closeApp(appName) : launchApp(appName);
            }

            // Check for generic browser/web commands
            if (normalizedInput.contains("browser") || normalizedInput.contains("internet") ||
                normalizedInput.contains("web")) {
                return isClose ? closeApp("chrome") : launchApp("chrome");
            }

            return null; // No executable action identified
        } catch (Exception e) {
            System.out.println("[JARVIS] ERROR: Command execution failed — " + e.getMessage());
            return null;
        }
    }

    /**
     * Attempts to evaluate a math expression in the input.
     * Supports +, -, *, / with two operands.
     */
    private String tryMath(String input) {
        // Check if input contains math-related keywords or an arithmetic operator
        boolean hasMathKeyword = false;
        for (String keyword : MATH_KEYWORDS) {
            if (input.contains(keyword)) {
                hasMathKeyword = true;
                break;
            }
        }

        // Also check for raw arithmetic operators between numbers
        boolean hasArithmeticExpr = MATH_EXPRESSION.matcher(input).find();

        if (!hasMathKeyword && !hasArithmeticExpr) {
            return null;
        }

        Matcher matcher = MATH_EXPRESSION.matcher(input);
        if (matcher.find()) {
            try {
                double a = Double.parseDouble(matcher.group(1));
                String op = matcher.group(2);
                double b = Double.parseDouble(matcher.group(3));
                double result;

                switch (op) {
                    case "+":  result = a + b; break;
                    case "-":  result = a - b; break;
                    case "*":
                    case "x":
                    case "×":  result = a * b; break;
                    case "/":
                        if (b == 0) return "Division by zero? Even I won't attempt that.";
                        result = a / b;
                        break;
                    default:   return null;
                }

                // Format: drop decimal if it's a whole number
                String formatted = (result == Math.floor(result) && !Double.isInfinite(result))
                    ? String.valueOf((long) result)
                    : String.format("%.4f", result).replaceAll("0+$", "").replaceAll("\\.$", "");

                return "The answer is " + formatted + ".";
            } catch (NumberFormatException e) {
                return null;
            }
        }

        return null;
    }

    /**
     * Handles temporal arithmetic queries like "50 years ago", "3 months from now", "in 10 days".
     */
    private String tryTemporalArithmetic(String input) {
        int amount = 0;
        String unit = null;
        boolean isPast = false;

        // Try "X units ago" / "X units before"
        Matcher m = TEMPORAL_AGO.matcher(input);
        boolean found = m.find();
        if (!found) {
            m = TEMPORAL_BEFORE.matcher(input);
            found = m.find();
        }
        if (found) {
            amount = Integer.parseInt(m.group(1));
            unit = m.group(2);
            isPast = true;
        } else {
            // Try "X units from now" / "X units later"
            m = TEMPORAL_FROM_NOW.matcher(input);
            found = m.find();
            if (!found) {
                m = TEMPORAL_IN_FUTURE.matcher(input);
                found = m.find();
            }
            if (!found) {
                m = TEMPORAL_AFTER.matcher(input);
                found = m.find();
            }
            if (found) {
                amount = Integer.parseInt(m.group(1));
                unit = m.group(2);
                isPast = false;
            }
        }

        if (unit == null || amount == 0) {
            return null;
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime target;

        if (isPast) {
            switch (unit) {
                case "year":   target = now.minusYears(amount); break;
                case "month":  target = now.minusMonths(amount); break;
                case "week":   target = now.minusWeeks(amount); break;
                case "day":    target = now.minusDays(amount); break;
                case "hour":   target = now.minusHours(amount); break;
                case "minute": target = now.minusMinutes(amount); break;
                default: return null;
            }
        } else {
            switch (unit) {
                case "year":   target = now.plusYears(amount); break;
                case "month":  target = now.plusMonths(amount); break;
                case "week":   target = now.plusWeeks(amount); break;
                case "day":    target = now.plusDays(amount); break;
                case "hour":   target = now.plusHours(amount); break;
                case "minute": target = now.plusMinutes(amount); break;
                default: return null;
            }
        }

        String direction = isPast ? "ago" : "from now";

        // Format the response based on what the user asked about
        if (input.contains("year")) {
            return amount + " " + unit + (amount > 1 ? "s" : "") + " " + direction
                + ", it was " + target.getYear() + ".";
        } else if (input.contains("date") || input.contains("day")) {
            String formatted = target.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy"));
            return amount + " " + unit + (amount > 1 ? "s" : "") + " " + direction
                + ", it was " + formatted + ".";
        } else if (input.contains("month")) {
            String formatted = target.format(DateTimeFormatter.ofPattern("MMMM yyyy"));
            return amount + " " + unit + (amount > 1 ? "s" : "") + " " + direction
                + ", it was " + formatted + ".";
        } else if (input.contains("time") || unit.equals("hour") || unit.equals("minute")) {
            String formatted = target.format(DateTimeFormatter.ofPattern("h:mm a"));
            return amount + " " + unit + (amount > 1 ? "s" : "") + " " + direction
                + ", it was " + formatted + ".";
        } else {
            // General fallback: show full date and time
            String formatted = target.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy 'at' h:mm a"));
            return amount + " " + unit + (amount > 1 ? "s" : "") + " " + direction
                + ", it was " + formatted + ".";
        }
    }

    /**
     * Extracts the app name from user input by stripping noise words.
     */
    private String extractAppName(String input) {
        // Remove punctuation
        String cleaned = input.replaceAll("[^a-zA-Z0-9\\s]", "").trim();
        String[] words = cleaned.split("\\s+");

        // Build app name from non-noise words
        StringBuilder appName = new StringBuilder();
        for (String word : words) {
            if (!NOISE_WORDS.contains(word.toLowerCase())) {
                if (appName.length() > 0) appName.append(" ");
                appName.append(word.toLowerCase());
            }
        }

        String result = appName.toString().trim();

        // Check if the full extracted name (or parts of it) match a known app
        // Try longest match first
        if (KNOWN_APPS.containsKey(result)) return result;

        // Try individual words against known apps
        for (String word : result.split("\\s+")) {
            if (KNOWN_APPS.containsKey(word)) return word;
        }

        // Only return known app names — don't try to generically launch unknown words
        return null;
    }

    /**
     * Launches an application by name.
     */
    private String launchApp(String appName) {
        try {
            String[] command;
            String displayName = appName.substring(0, 1).toUpperCase() + appName.substring(1);

            if (KNOWN_APPS.containsKey(appName)) {
                command = KNOWN_APPS.get(appName);
            } else {
                // Generic fallback: try to launch via cmd /c start
                command = new String[]{"cmd", "/c", "start", "", appName};
            }

            Runtime.getRuntime().exec(command);
            return "Opening " + displayName + ". Try to be productive this time.";

        } catch (IOException e) {
            return "I tried to open " + appName + ", but it failed. Not my fault.";
        }
    }

    /**
     * Closes/kills an application by name.
     * Uses taskkill /F /T to force-kill the process tree.
     * Falls back to window-title matching if the process name doesn't work.
     */
    private String closeApp(String appName) {
        String displayName = appName.substring(0, 1).toUpperCase() + appName.substring(1);
        String processName;

        if (KNOWN_PROCESSES.containsKey(appName)) {
            processName = KNOWN_PROCESSES.get(appName);
        } else {
            processName = appName + ".exe";
        }

        System.out.println("[JARVIS] Attempting to close app: " + appName);
        System.out.println("[JARVIS] Process target: " + processName);

        // Attempt 1: taskkill /F /T /IM <process.exe>
        if (tryTaskKill(processName)) {
            System.out.println("[JARVIS] Close command succeeded for: " + processName);
            return "Closing " + displayName + ". You're welcome.";
        }

        // Attempt 2: try case-insensitive wildcard via IMAGENAME filter
        // Some apps register with different casing or versioned names
        System.out.println("[JARVIS] Exact process name failed, trying wildcard filter...");
        if (tryTaskKillByFilter("IMAGENAME eq " + processName)) {
            System.out.println("[JARVIS] Close via filter succeeded for: " + processName);
            return "Closing " + displayName + ". You're welcome.";
        }

        // Attempt 3: try matching by window title (useful for UWP/Store apps)
        System.out.println("[JARVIS] Trying window title match for: " + displayName);
        if (tryTaskKillByFilter("WINDOWTITLE eq " + displayName)) {
            System.out.println("[JARVIS] Close via window title succeeded for: " + displayName);
            return "Closing " + displayName + ". You're welcome.";
        }

        // Attempt 4: dynamically search tasklist for a process containing the app name
        String dynamicProcess = findProcessByName(appName);
        if (dynamicProcess != null) {
            System.out.println("[JARVIS] Found dynamic process: " + dynamicProcess);
            if (tryTaskKill(dynamicProcess)) {
                System.out.println("[JARVIS] Close via dynamic lookup succeeded for: " + dynamicProcess);
                return "Closing " + displayName + ". You're welcome.";
            }
        }

        System.out.println("[JARVIS] All close attempts failed for: " + appName);
        return "I tried to close " + displayName + ", but it resisted. It may not be running.";
    }

    /**
     * Runs taskkill /F /T /IM <processName> and returns true if it succeeds (exit code 0).
     */
    private boolean tryTaskKill(String processName) {
        try {
            ProcessBuilder pb = new ProcessBuilder("taskkill", "/F", "/T", "/IM", processName);
            pb.redirectErrorStream(true);
            System.out.println("[JARVIS] Running: taskkill /F /T /IM " + processName);
            Process proc = pb.start();

            // Drain stdout+stderr to prevent pipe buffer deadlock
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(proc.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println("[JARVIS] taskkill output: " + line);
                }
            }

            int exitCode = proc.waitFor();
            System.out.println("[JARVIS] taskkill exit code: " + exitCode);
            return exitCode == 0;
        } catch (Exception e) {
            System.out.println("[JARVIS] taskkill failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Runs taskkill with a /FI filter expression and returns true if it succeeds.
     */
    private boolean tryTaskKillByFilter(String filter) {
        try {
            ProcessBuilder pb = new ProcessBuilder("taskkill", "/F", "/T", "/FI", filter);
            pb.redirectErrorStream(true);
            System.out.println("[JARVIS] Running: taskkill /F /T /FI \"" + filter + "\"");
            Process proc = pb.start();

            // Drain stdout+stderr to prevent pipe buffer deadlock
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(proc.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println("[JARVIS] taskkill filter output: " + line);
                }
            }

            int exitCode = proc.waitFor();
            System.out.println("[JARVIS] taskkill filter exit code: " + exitCode);
            return exitCode == 0;
        } catch (Exception e) {
            System.out.println("[JARVIS] taskkill filter failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Dynamically searches running processes (via tasklist) for one whose name
     * contains the given app name. Returns the first matching process name,
     * or null if none found.
     */
    private String findProcessByName(String appName) {
        try {
            ProcessBuilder pb = new ProcessBuilder("tasklist", "/NH", "/FO", "CSV");
            pb.redirectErrorStream(true);
            Process proc = pb.start();

            String searchTerm = appName.toLowerCase();
            String found = null;

            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(proc.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    // CSV format: "process.exe","PID","Session","#","Mem"
                    String lower = line.toLowerCase();
                    if (lower.contains(searchTerm) && lower.contains(".exe")) {
                        // Extract the process name from CSV
                        String trimmed = line.trim();
                        if (trimmed.startsWith("\"")) {
                            int end = trimmed.indexOf('"', 1);
                            if (end > 1) {
                                found = trimmed.substring(1, end);
                                break;
                            }
                        }
                    }
                }
            }

            proc.waitFor();
            if (found != null) {
                System.out.println("[JARVIS] Dynamic process lookup found: " + found + " for app: " + appName);
            } else {
                System.out.println("[JARVIS] Dynamic process lookup found nothing for: " + appName);
            }
            return found;
        } catch (Exception e) {
            System.out.println("[JARVIS] Dynamic process lookup failed: " + e.getMessage());
            return null;
        }
    }

    private String getTimeResponse() {
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("h:mm a"));
        return "It's " + time + ".";
    }

    private String getDateResponse() {
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMMM d, yyyy"));
        return "Today's date is " + date + ".";
    }

    private String getDayResponse() {
        String day = LocalDateTime.now().format(DateTimeFormatter.ofPattern("EEEE"));
        return "Today is " + day + ".";
    }

    private String getMonthResponse() {
        String month = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMMM yyyy"));
        return "It's " + month + ".";
    }

    private String getYearResponse() {
        String year = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy"));
        return "The current year is " + year + ".";
    }

    /**
     * Checks if the input matches any general knowledge entries.
     */
    private String tryGeneralKnowledge(String input) {
        for (Map.Entry<List<String>, String> entry : GENERAL_KNOWLEDGE.entrySet()) {
            for (String keyword : entry.getKey()) {
                if (input.contains(keyword)) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }
}

