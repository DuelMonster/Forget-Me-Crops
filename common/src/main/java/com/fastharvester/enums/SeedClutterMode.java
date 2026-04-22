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

public enum SeedClutterMode {
    NORMAL,
    REDUCED,
    NONE;

    public String configValue() {
        return name();
    }

    public static SeedClutterMode fromConfigValue(String value) {
        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return REDUCED;
        }
    }
}
