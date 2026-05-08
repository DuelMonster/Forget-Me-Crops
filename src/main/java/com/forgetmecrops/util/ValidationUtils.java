package com.forgetmecrops.util;

import java.util.Collection;

/**
 * ValidationUtils: Common validation patterns extracted into reusable predicates.
 * <p>
 * Consolidates repeated null checks, isEmpty checks, and similar guard clauses
 * throughout the codebase. Reduces boilerplate and improves code clarity.
 * </p>
 */
public final class ValidationUtils {
    // Utility class. ValidationUtils doesn't need instances.
    private ValidationUtils() {}

    /**
     * Checks if any of the provided objects are null.
     * Useful for multi-parameter guard clauses like `if (isAnyNull(a, b, c)) return;`
     *
     * @param objects objects to check
     * @return true if any object is null, false otherwise
     */
    public static boolean isAnyNull(Object... objects) {
        for (Object obj : objects) {
            if (obj == null) return true;
        }
        return false;
    }

    /**
     * Checks if a collection is null or empty.
     *
     * @param collection collection to check
     * @return true if null or empty, false otherwise
     */
    public static boolean isNullOrEmpty(Collection<?> collection) {
        return collection == null || collection.isEmpty();
    }

    /**
     * Checks if a string is null or empty (including whitespace-only strings).
     *
     * @param str string to check
     * @return true if null, empty, or blank, false otherwise
     */
    public static boolean isNullOrBlank(String str) {
        return str == null || str.isBlank();
    }

    /**
     * Checks if an array is null or has zero length.
     *
     * @param array array to check
     * @return true if null or empty, false otherwise
     */
    public static boolean isNullOrEmpty(Object[] array) {
        return array == null || array.length == 0;
    }
}
