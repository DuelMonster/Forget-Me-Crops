package com.forgetmecrops.util;

import java.util.Collection;

/**
 * ✅ ValidationUtils: The "is this thing real?" department for your null-check obsessions.
 * <p>
 * Guard clauses are everywhere in this codebase, and they should be. But instead of writing
 * the same "if this is null, bail out" check a hundred times, we've extracted the common
 * patterns here. Cleaner, more readable, less repetitive. Your future self will thank you
 * for not having to maintain yet another one-liner null check scattered across 47 files.
 * </p>
 */
public final class ValidationUtils {
    // Utility class, does not self-instantiate. We are tools, not objects. Tools don't need to exist as instances.
    private ValidationUtils() {}

    /**
     * Check if ANY of the provided objects are null.
     * <p>
     * Instead of writing: `if (a == null || b == null || c == null)` a thousand times,
     * write: `if (isAnyNull(a, b, c))`. We do the heavy lifting. You get readable code.
     * </p>
     *
     * @param objects the suspects we're checking for nullness (any one of them could be the culprit)
     * @return true if we find even one null lurking in there, false if they're all wholesome and non-null
     */
    public static boolean isAnyNull(Object... objects) {
        for (Object obj : objects) {
            if (obj == null) return true;
        }
        return false;
    }

    /**
     * Check if a collection is null OR if it's empty (the second mistake devs make after not checking for null).
     * <p>
     * Empty collections are almost as useless as null ones, and checking for both every time is tedious.
     * This covers both cases in one readable line.
     * </p>
     *
     * @param collection the collection to scrutinize (could be null, could be empty, could be sad)
     * @return true if it's null or has zero items, false if it's actually got something useful in it
     */
    public static boolean isNullOrEmpty(Collection<?> collection) {
        return collection == null || collection.isEmpty();
    }

    /**
     * Check if a string is null, empty, OR whitespace-only (the trickiest case of all).
     * <p>
     * A string with nothing but spaces is basically useless. We catch all three variants of uselessness
     * in one shot: null, "", or "   " all return true.
     * </p>
     *
     * @param str the string to evaluate (innocent until proven to be garbage)
     * @return true if it's null, empty, or just hanging out with a bunch of spaces doing nothing, false if it's got actual content
     */
    public static boolean isNullOrBlank(String str) {
        return str == null || str.isBlank();
    }

    /**
     * Check if an array is null or has kicked the bucket (length of zero).
     * <p>
     * Arrays don't have an isEmpty() method like collections do (because Java), so we check length.
     * Dead array? Null reference? Both return true. Useful array with stuff in it? Returns false.
     * </p>
     *
     * @param array the array to examine (could be null, could be empty, could be glorious)
     * @return true if it's null or has no items, false if there's actually something living in there
     */
    public static boolean isNullOrEmpty(Object[] array) {
        return array == null || array.length == 0;
    }
}
