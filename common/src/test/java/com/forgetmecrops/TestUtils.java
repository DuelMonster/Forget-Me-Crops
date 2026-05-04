package com.forgetmecrops;

/**
 * TestUtils: small helpers used by tests and debugging.
 * <p>
 * Contains lightweight deterministic helpers used by unit tests and local validation.
 * </p>
 */
public final class TestUtils {
    private TestUtils() {}

    /**
     * Simple deterministic-ish loot calculator used by tests.
     * @param cropType an identifier for the crop being tested
     * @param fortuneLevel the fortune level to simulate
     * @return the simulated number of drops produced
     */
    public static int calculateLootSimple(Object cropType, int fortuneLevel) {
        java.util.concurrent.ThreadLocalRandom rng = java.util.concurrent.ThreadLocalRandom.current();
        int baseDrops = 1 + rng.nextInt(2);
        int bonus = rng.nextInt(fortuneLevel + 1);
        return baseDrops + bonus;
    }
}
