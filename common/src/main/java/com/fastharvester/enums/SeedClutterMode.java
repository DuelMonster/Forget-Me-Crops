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
