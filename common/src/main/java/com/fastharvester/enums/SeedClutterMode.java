package com.fastharvester.enums;

/**
 * SeedClutterMode: The seed hoarder's dilemma!
 * <p>
 * This enum controls how many seeds you keep, toss, or cherish. Because sometimes you want a tidy chest, and sometimes you want enough seeds to plant a continent.
 * </p>
 * <p>
 * Why does this matter? Because inventory management is the real endgame.
 * </p>
 */
public enum SeedClutterMode {
    /** Normal seed handling; default behavior. */
    NORMAL,
    /** Reduce seed clutter by being more conservative when replanting. */
    REDUCED,
    /** Disable seed clutter handling; do not replant automatically. */
    NONE;

    /**
     * Return the string value used in config files for this mode.
     * @return the config string for this SeedClutterMode
     */
    public String configValue() {
        return name();
    }

    /**
     * Parse the config string into an enum value, falling back to REDUCED on error.
     * @param value the string value read from config
     * @return the corresponding SeedClutterMode, or REDUCED if unknown
     */
    public static SeedClutterMode fromConfigValue(String value) {
        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return REDUCED;
        }
    }
}
