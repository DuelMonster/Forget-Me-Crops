package com.fastharvester.enums;

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
    /** Normal durability handling (default). */
    NORMAL,
    /** Ignore the Unbreaking enchantment when calculating durability usage. */
    IGNORE_UNBREAKING,
    /** No durability loss; tools do not degrade. */
    NONE;

    /**
     * Return the string value used in config files for this mode.
     * @return the config string for this DurabilityMode
     */
    public String configValue() {
        return name();
    }

    /**
     * Parse a config string into DurabilityMode, defaulting to NORMAL on invalid input.
     * @param value the string value read from config
     * @return the corresponding DurabilityMode, or NORMAL if unknown
     */
    public static DurabilityMode fromConfigValue(String value) {
        try {
            return valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return NORMAL;
        }
    }
}
