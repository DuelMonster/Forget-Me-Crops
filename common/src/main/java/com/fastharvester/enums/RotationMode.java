package com.fastharvester.enums;

import java.util.Locale;

/**
 * RotationMode: The dance card for your item frame!
 * <p>
 * This enum decides how the frame spins during a harvest. Will it twirl, will it march, or will it spiral like a disco ball?
 * </p>
 * <p>
 * Why does this matter? Because farming is more fun when your tools have rhythm.
 * </p>
 */
public enum RotationMode {
    /** Advance one rotation step per full farm harvest. */
    STEP_PER_HARVEST,
    /** Perform exactly one full 0..7 rotation cycle per full farm harvest. */
    FULL_ROTATION_PER_HARVEST,
    /** Rotate to follow the spiral scan progression during harvest. */
    FOLLOW_HARVEST_SPIRAL;

    /**
     * Return the string value used in config files for this mode.
     * @return the config string for this RotationMode
     */
    public String configValue() {
        return name();
    }

    /**
     * Parse the config string into a RotationMode, defaulting to FOLLOW_HARVEST_SPIRAL on errors.
     * @param value the string value read from config
     * @return the corresponding RotationMode, or FOLLOW_HARVEST_SPIRAL if unknown
     */
    public static RotationMode fromConfigValue(String value) {
        try {
            return valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return FOLLOW_HARVEST_SPIRAL;
        }
    }
}
