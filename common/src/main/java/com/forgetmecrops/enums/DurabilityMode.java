package com.forgetmecrops.enums;

import java.util.Locale;

/**
 * DurabilityMode: The mood ring for your hoe!
 * <p>
 * This enum controls how much wear and tear your precious tool takes while farming. Choose wisely—your hoe's happiness depends on it.
 * </p>
 * <p>
 * Why does this matter? Because sometimes you want hardcore survival, and sometimes you just want to farm forever.
 * </p>
 */
public enum DurabilityMode {
    /** Normal durability — tools wear down over time, just like the farmer's will to keep farming manually.
     *  The Unbreaking enchantment is respected here because we're not monsters. */
    NORMAL,
    /** Ignore Unbreaking entirely — every harvest costs durability regardless of enchantments.
     *  For masochists, purists, or players who enjoy watching their hoe die. */
    IGNORE_UNBREAKING,
    /** Zero durability loss. Your hoe is immortal. An agricultural legend. Treat it accordingly. */
    NONE;

    /**
     * Returns the config-file string for this mode — the key that players will inevitably typo
     * at least once before getting it right.
     *
     * @return the TOML-safe string representation of this DurabilityMode
     */
    public String configValue() {
        return name();
    }

    /**
     * Parses a config-file string back into a DurabilityMode. Forgiving of case differences.
     * Falls back to NORMAL on invalid input, because we assume most players want their
     * tools to break like normal tools, not survive the heat death of the universe.
     *
     * @param value the raw string from the TOML config
     * @return the matching DurabilityMode, or NORMAL if parsing fails
     */
    public static DurabilityMode fromConfigValue(String value) {
        try {
            return valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return NORMAL;
        }
    }
}
