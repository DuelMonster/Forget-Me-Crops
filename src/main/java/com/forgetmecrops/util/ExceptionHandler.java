package com.forgetmecrops.util;

/**
 * 🤫 ExceptionHandler: The "let's pretend that didn't happen" department for non-critical failures.
 * <p>
 * You know that moment when debug logging fails? Or a particle effect doesn't render?
 * Or some weird edge case throws an exception that absolutely shouldn't crash the entire mod?
 * That's what ExceptionHandler is for. Wrap those risky-but-not-essential operations in silentTry()
 * and we'll catch any exception, log nothing (unless you want it to), and just... keep farming.
 * Your server stays alive. Crops keep harvesting. Everyone's happy.
 * </p>
 * <p>
 * Think of it as the mod's personal insurance policy against silly little failures turning into
 * catastrophic crashes. We're optimists, but we're not naive.
 * </p>
 */
public final class ExceptionHandler {
    // Utility class, non-instantiable by design. We're a static shrine to safe failure, not an object.
    private ExceptionHandler() {}

    /**
     * Execute an action and silently swallow any exception that gets thrown at us.
     * <p>
     * Perfect for operations where failure is annoying but not fatal. Debug logging that breaks?
     * Particle effects that fail? Niche compatibility checks? This is your friend.
     * </p>
     *
     * @param action the risky business to execute (will not crash even if it explodes)
     */
    public static void silentTry(Runnable action) {
        try {
            action.run();
        } catch (Throwable ignored) {
            // We're choosing to ignore this. It's not a bug, it's a feature. Sort of.
        }
    }

    /**
     * Execute a function, return its result, or give up and return a fallback value.
     * <p>
     * Like silentTry() but for operations that actually produce something valuable.
     * If things go sideways, we hand back the default value and carry on.
     * </p>
     *
     * @param action         the potentially dangerous function to execute
     * @param defaultValue   what to return if things go spectacularly wrong
     * @param <T>            the type of thing we're trying to get back
     * @return the function's result if successful, or defaultValue if things explode
     */
    public static <T> T silentTry(SafeFunction<T> action, T defaultValue) {
        try {
            return action.apply();
        } catch (Throwable ignored) {
            // Plan B: give them the default and move on with dignity
            return defaultValue;
        }
    }

    /**
     * A function interface that can throw literally any exception without us complaining about it.
     * <p>
     * Java's Throwable is our friend here — we're not picky about what breaks.
     * </p>
     *
     * @param <T> whatever type of magical result this function promises to produce
     */
    @FunctionalInterface
    public interface SafeFunction<T> {
        T apply() throws Exception;
    }
}
