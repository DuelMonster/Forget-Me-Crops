package com.fastharvester.enums;

public enum DurabilityMode {
    NORMAL,
    IGNORE_UNBREAKING,
    NONE;

    public String configValue() {
        return name();
    }

    public static DurabilityMode fromConfigValue(String value) {
        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return NORMAL;
        }
    }
}
