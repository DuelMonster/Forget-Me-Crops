package com.forgetmecrops.util.log;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.forgetmecrops.ModCommon;
import com.forgetmecrops.config.Config;

/**
 * LogUtils: The mod's official mouthpiece for talking to the server console!
 * <p>
 * Provides centralized logging methods (info, debug, warn, error, trace) that all
 * prepend the mod name in brackets so you can find Forget-Me-Crops output at a glance
 * in a busy log file. Debug logging is gated behind the config flag so production
 * servers aren't drowned in farming minutiae from happy automated harvesters.
 * </p>
 * <p>
 * Also includes a best-effort method to programmatically set the log level to DEBUG
 * at runtime using reflection against both Log4j2 and Logback backends — because some
 * users don't want to edit logging configuration files just to diagnose an issue.
 * We tried to make debugging easy. Whether we succeeded is another matter.
 * </p>
 */
public final class LogUtils {
    // Utility class. The logger does not log itself. That would be recursion.
    private LogUtils() {}

    /** The actual logger instance — tagged with the mod name so every output line is attributable. */
    public static final Logger LOG = LoggerFactory.getLogger(ModCommon.MOD_NAME);

    /**
     * Prepends the mod name as the first positional argument so all format strings start with {@code [{}]}.
     * This is what makes every log line show the mod's name in brackets without duplicating that prefix
     * in every single log call. One helper. Endless clean log lines.
     */
    private static Object[] mergeArgs(Object... args) {
        if (args == null || args.length == 0) return new Object[] { ModCommon.MOD_NAME };
        Object[] out = new Object[args.length + 1];
        out[0] = ModCommon.MOD_NAME;
        System.arraycopy(args, 0, out, 1, args.length);
        return out;
    }

    /**
     * Logs a message at INFO level with the mod name prefix. Visible in all environments.
     * Use for important lifecycle events: registration, config load, startup, shutdown.
     */
    public static void logInfo(String format, Object... args) {
        LOG.info("[{}] " + format, mergeArgs(args));
    }

    /**
     * Logs a message at DEBUG level — only when {@code Config.debugLogging} is true.
     * Use for per-tick, per-farm, and per-harvest details that would flood the log in production.
     */
    public static void logDebug(String format, Object... args) {
        if (Config.isDebugLogging()) {
            LOG.debug("[{}] " + format, mergeArgs(args));
        }
    }

    /**
     * Logs an exception at DEBUG level — only when debug logging is enabled.
     * Useful for reflective failures and expected-but-noteworthy exceptions in the adapter chain.
     *
     * @param format the message format string (no positional args besides the throwable)
     * @param t      the throwable to attach to the log record
     */
    public static void logDebug(String format, Throwable t) {
        if (Config.isDebugLogging()) {
            String msg = "[" + ModCommon.MOD_NAME + "] " + format;
            LOG.debug(msg, t);
        }
    }

    /**
     * If `Config.debugLogging` is enabled, attempt to programmatically set the
     * logging backend's logger level to DEBUG so that debug traces are visible
     * without requiring users to change external logging configuration.
     *
     * This method uses reflection to support both Log4j2 and Logback (best-effort).
     */
    public static void applyConfiguredLogging() {
        if (!Config.isDebugLogging()) return;
        try {
            Class<?> configuratorClass = Class.forName("org.apache.logging.log4j.core.config.Configurator");
            Class<?> levelClass = Class.forName("org.apache.logging.log4j.Level");
            java.lang.reflect.Field debugField = levelClass.getField("DEBUG");
            Object debugLevel = debugField.get(null);
            java.lang.reflect.Method setLevel = configuratorClass.getMethod("setLevel", String.class, levelClass);
            setLevel.invoke(null, ModCommon.MOD_NAME, debugLevel);
            setLevel.invoke(null, "com.forgetmecrops", debugLevel);
            LOG.info("[{}] Programmatically set Log4j2 logger level to DEBUG", ModCommon.MOD_NAME);
            return;
        } catch (Throwable ignored) {
        }

        try {
            Class<?> loggerContextClass = Class.forName("ch.qos.logback.classic.LoggerContext");
            Class<?> levelClass = Class.forName("ch.qos.logback.classic.Level");
            Object loggerFactory = LoggerFactory.getILoggerFactory();
            if (loggerContextClass.isInstance(loggerFactory)) {
                java.lang.reflect.Method getLogger = loggerContextClass.getMethod("getLogger", String.class);
                Object rootLogger = getLogger.invoke(loggerFactory, "ROOT");
                Class<?> loggerClass = Class.forName("ch.qos.logback.classic.Logger");
                java.lang.reflect.Method setLevel = loggerClass.getMethod("setLevel", levelClass);
                java.lang.reflect.Field debugField = levelClass.getField("DEBUG");
                Object debugLevel = debugField.get(null);
                setLevel.invoke(rootLogger, debugLevel);
                LOG.info("[{}] Programmatically set Logback root logger level to DEBUG", ModCommon.MOD_NAME);
                return;
            }
        } catch (Throwable ignored) {
        }

        LOG.warn("[{}] Could not programmatically set logger level for debugLogging", ModCommon.MOD_NAME);
    }

    /**
     * Logs a message at ERROR level. This is the "something went wrong and we want to know" tier.
     * Always visible regardless of debug-logging setting. Use for unexpected failures.
     */
    public static void logError(String format, Object... args) {
        LOG.error("[{}] " + format, mergeArgs(args));
    }

    /** Logs an exception with message at ERROR level. Use when the stack trace is important for diagnosis. */
    public static void logError(String format, Throwable t) {
        String msg = "[" + ModCommon.MOD_NAME + "] " + format;
        LOG.error(msg, t);
    }

    /**
     * Logs a message at WARN level. Visible in all environments. Use for recoverable problems,
     * invalid config values, and anything the user should probably know about but won't crash the game.
     */
    public static void logWarn(String format, Object... args) {
        LOG.warn("[{}] " + format, mergeArgs(args));
    }

    /** Logs an exception at WARN level with a message. For "this is weird but we handled it" situations. */
    public static void logWarn(String format, Throwable t) {
        String msg = "[" + ModCommon.MOD_NAME + "] " + format;
        LOG.warn(msg, t);
    }

    /**
     * Logs at TRACE level. The most verbose tier — used sparingly for innermost-loop detail
     * that would overwhelm even DEBUG in a busy tick. Only visible when trace-level logging
     * is enabled at the backend level (not just via Config.debugLogging).
     */
    public static void logTrace(String format, Object... args) {
        LOG.trace("[{}] " + format, mergeArgs(args));
    }

    /** Logs an exception at TRACE level. Same constraints as the non-throwable variant. */
    public static void logTrace(String format, Throwable t) {
        String msg = "[" + ModCommon.MOD_NAME + "] " + format;
        LOG.trace(msg, t);
    }
}
