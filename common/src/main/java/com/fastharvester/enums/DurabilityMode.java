/**
 * DurabilityMode: The mood ring for your hoe!
 * <p>
 * This enum controls how much wear and tear your precious tool takes while farming. Choose wisely—your hoe's happiness depends on it.
 * </p>
 * <p>
 * Why does this matter? Because sometimes you want hardcore survival, and sometimes you just want to farm forever.
 * </p>
 */
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
