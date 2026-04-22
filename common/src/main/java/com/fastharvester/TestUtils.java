package com.fastharvester;

public final class TestUtils {
    private TestUtils() {}

    public static int calculateLootSimple(Object cropType, int fortuneLevel) {
        int baseDrops = 1 + (int)(Math.random() * 2);
        int bonus = (int)(Math.random() * (fortuneLevel + 1));
        return baseDrops + bonus;
    }
}
