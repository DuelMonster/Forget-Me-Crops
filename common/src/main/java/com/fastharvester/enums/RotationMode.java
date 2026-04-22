package com.fastharvester.enums;

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
