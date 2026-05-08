package com.forgetmecrops.util;

/**
 * ExceptionHandler: Silent failure utilities for non-critical operations.
 * <p>
 * Consolidates the common pattern of try-catch blocks that silently ignore exceptions
 * (e.g., debug logging, effects, non-essential operations). Instead of scattering
 * try-catch blocks throughout the code, use these convenience methods to wrap
 * operations that shouldn't crash the mod if they fail.
 * </p>
 */
public final class ExceptionHandler {
    // Utility class. ExceptionHandler doesn't need instances.
    private ExceptionHandler() {}

    /**
     * Executes an action, silently ignoring any thrown exception.
     * Useful for non-critical operations that shouldn't break the mod.
     *
     * @param action the runnable to execute
     */
    public static void silentTry(Runnable action) {
        try {
            action.run();
        } catch (Throwable ignored) {
            // Intentionally ignored; logged at caller's discretion
        }
    }

    /**
     * Executes a function and returns its result, or a default value if any exception is thrown.
     * Useful for fallback-friendly operations.
     *
     * @param action         the function to execute
     * @param defaultValue   value to return if action throws
     * @param <T>            return type
     * @return the action's result, or defaultValue if action threw an exception
     */
    public static <T> T silentTry(SafeFunction<T> action, T defaultValue) {
        try {
            return action.apply();
        } catch (Throwable ignored) {
            return defaultValue;
        }
    }

    /**
     * A function that can throw checked or unchecked exceptions.
     *
     * @param <T> return type
     */
    @FunctionalInterface
    public interface SafeFunction<T> {
        T apply() throws Exception;
    }
}
