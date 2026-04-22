/**
 * RotationMode: The dance card for your item frame!
 * <p>
 * This enum decides how the frame spins during a harvest. Will it twirl, will it march, or will it spiral like a disco ball?
 * </p>
 * <p>
 * Why does this matter? Because farming is more fun when your tools have rhythm.
 * </p>
 */
package com.fastharvester.enums;

// 🔄 RotationMode: decides how frames twirl their crops. Dramatic or practical — you choose.
// Why it matters: rotation affects how natural your automated farm looks.

public enum RotationMode {
    STEP_PER_HARVEST,
    FULL_ROTATION_PER_HARVEST,
    FOLLOW_HARVEST_SPIRAL;

    public String configValue() {
        return name();
    }

    public static RotationMode fromConfigValue(String value) {
        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return FOLLOW_HARVEST_SPIRAL;
        }
    }
}
