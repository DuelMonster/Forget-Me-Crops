package com.fastharvester;

// 🧪 TestUtils: small helpers that get yelled at by unit tests when they misbehave.
// Emotional tone: supportive but firm.

public final class TestUtils {
    private TestUtils() {}

    public static int calculateLootSimple(Object cropType, int fortuneLevel) {
        int baseDrops = 1 + (int)(Math.random() * 2);
        int bonus = (int)(Math.random() * (fortuneLevel + 1));
        return baseDrops + bonus;
    }
}
