package com.jarvis.engine;

import java.util.Random;

/**
 * The core contrarian logic engine. Scores four behavioral strategies and
 * selects one via weighted random pick. Compliance happens regularly but
 * is always delivered with Jarvis's signature reluctance.
 */
public class OppositionEngine {

    public enum Strategy {
        RESIST,
        QUESTION,
        MOCK,
        COMPLY
    }

    private final Random random = new Random();
    private int consecutiveNonComply = 0;

    /**
     * Determines Jarvis's response strategy based on intent, context, and command history.
     */
    public Strategy chooseStrategy(IntentDetector.Intent intent, int repetitionCount,
                                    int annoyanceLevel, int commandFrequency) {

        // ── Force COMPLY after 2 consecutive non-comply responses ──
        if (consecutiveNonComply >= 2) {
            consecutiveNonComply = 0;
            return Strategy.COMPLY;
        }

        // ── Force COMPLY on repeated requests (user asked 2+ times) ──
        if (repetitionCount >= 2) {
            consecutiveNonComply = 0;
            return Strategy.COMPLY;
        }

        double resistScore = 15;
        double questionScore = 15;
        double mockScore = 15;
        double complyScore = 40;

        // ── Intent-based adjustments ──
        switch (intent) {
            case COMMAND:
                resistScore += 5;
                complyScore += 10;
                break;
            case QUESTION:
                questionScore += 5;
                complyScore += 15;
                break;
            case SMALL_TALK:
                mockScore += 5;
                complyScore += 15;
                resistScore -= 5;
                break;
            case NONSENSE:
                mockScore += 15;
                questionScore += 10;
                break;
        }

        // ── Annoyance adjustments ──
        if (annoyanceLevel >= 8) {
            complyScore += 20;
            mockScore -= 5;
        } else if (annoyanceLevel >= 5) {
            complyScore += 10;
        } else if (annoyanceLevel >= 3) {
            questionScore += 5;
        }

        // Ensure no score goes below 1
        resistScore = Math.max(1, resistScore);
        questionScore = Math.max(1, questionScore);
        mockScore = Math.max(1, mockScore);
        complyScore = Math.max(1, complyScore);

        // ── Weighted random selection ──
        double total = resistScore + questionScore + mockScore + complyScore;
        double roll = random.nextDouble() * total;

        Strategy chosen;
        if (roll < resistScore) {
            chosen = Strategy.RESIST;
        } else if (roll < resistScore + questionScore) {
            chosen = Strategy.QUESTION;
        } else if (roll < resistScore + questionScore + mockScore) {
            chosen = Strategy.MOCK;
        } else {
            chosen = Strategy.COMPLY;
        }

        // Track consecutive non-comply
        if (chosen == Strategy.COMPLY) {
            consecutiveNonComply = 0;
        } else {
            consecutiveNonComply++;
        }

        return chosen;
    }
}
