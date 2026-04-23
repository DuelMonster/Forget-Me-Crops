package com.fastharvester;

/*
 * TestUtils — small helpers used by tests and debugging.
 * These comments are intentionally friendly, mildly emotional,
 * and occasionally sarcastic to keep future maintainers awake.
 */

// 🧪 TestUtils: small helpers that get yelled at by unit tests when they misbehave.
// Emotional tone: supportive but firm.

public final class TestUtils {
    private TestUtils() {}

    /**
     * Simple deterministic-ish loot calculator used by tests.
     * Humanized aside: it's modest but reliable — like a village duck.
     */
    public static int calculateLootSimple(Object cropType, int fortuneLevel) {
        int baseDrops = 1 + (int)(Math.random() * 2);
        int bonus = (int)(Math.random() * (fortuneLevel + 1));
        return baseDrops + bonus;
    }
}
