/**
 * SeedClutterMode: The seed hoarder's dilemma!
 * <p>
 * This enum controls how many seeds you keep, toss, or cherish. Because sometimes you want a tidy chest, and sometimes you want enough seeds to plant a continent.
 * </p>
 * <p>
 * Why does this matter? Because inventory management is the real endgame.
 * </p>
 */
package com.fastharvester.enums;

// 🌱 SeedClutterMode: controls how stingy or generous the replanting is with seeds.
// Emotional note: sometimes the code wants to hoard seeds; this tames it.

public enum SeedClutterMode {
    NORMAL,
    REDUCED,
    NONE;

    public String configValue() {
        return name();
    }

    /**
     * Parse the config string into an enum value, falling back to REDUCED on error.
     */
    public static SeedClutterMode fromConfigValue(String value) {
        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return REDUCED;
        }
    }
}
