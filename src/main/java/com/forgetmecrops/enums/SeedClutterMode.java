package com.forgetmecrops.enums;

import java.util.Locale;

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
    /** Standard seed handling — seeds flow freely into the chest, no restrictions, maximum chaos.
     *  Great for players who enjoy an inbox full of 847 wheat seeds. */
    NORMAL,
    /** Trim excess seeds so the chest doesn't become a seed warehouse.
     *  Tidiness is next to godliness, and this mode enforces that politely. */
    REDUCED,
    /** Skip seed management entirely — no replanting, just raw chaotic harvesting.
     *  You're on your own. Good luck out there. */
    NONE;

    /**
     * Returns the config-file string for this mode — what players will see (and occasionally
     * misspell) in their forgetmecrops-server.toml.
     *
     * @return the config-safe string representation of this SeedClutterMode
     */
    public String configValue() {
        return name();
    }

    /**
     * Parses a config string back into a SeedClutterMode. Tolerates case differences like a
     * gracious host. Falls back to REDUCED on errors because some seed management is better
     * than none — your chest deserves at least a little dignity.
     *
     * @param value the raw config string (we'll uppercase it so you don't have to)
     * @return the matching SeedClutterMode, or REDUCED if parsing fails
     */
    public static SeedClutterMode fromConfigValue(String value) {
        try {
            return valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return REDUCED;
        }
    }
}
