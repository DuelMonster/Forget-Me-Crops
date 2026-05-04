package com.forgetmecrops.enums;

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
    /** Take exactly one rotation step per harvest. One click. Understated. Dignified. Professional. */
    SINGLE_STEP,
    /** Spin the entire 0-to-7 rotation cycle per harvest. Maximum drama. Maximum flair. Highly recommended. */
    FULL_ROTATION,
    /** Follow the spiral scan progression as it sweeps the farm — the frame dances in sync with the crops! */
    FOLLOW_ROTATION;

    /**
     * Returns the config-file string for this mode — exactly what will appear in forgetmecrops-server.toml.
     * Keep it clean; players will read this and judge us.
     *
     * @return the TOML-friendly lowercase string representation of this mode
     */
    public String configValue() {
        return name();
    }

    /**
     * Parses a raw config-file string back into a RotationMode. Case-insensitive, mercifully.
     * If the string is unrecognizable garbage, returns FOLLOW_ROTATION as the sensible default —
     * because even misconfigured farms deserve to keep spinning.
     *
     * @param value the raw config string read from the TOML file (we'll uppercase it for you)
     * @return the matching RotationMode, or FOLLOW_ROTATION if we can't figure out what you meant
     */
    public static RotationMode fromConfigValue(String value) {
        try {
            return valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return FOLLOW_ROTATION;
        }
    }
}
