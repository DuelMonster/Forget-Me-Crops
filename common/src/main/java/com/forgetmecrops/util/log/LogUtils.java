package com.forgetmecrops.util.log;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.forgetmecrops.ModCommon;
import com.forgetmecrops.config.Config;

/**
 * LogUtils: centralized logging helpers for ForgetMeCrops.
 */
public final class LogUtils {
    private LogUtils() {}

    public static final Logger LOG = LoggerFactory.getLogger(ModCommon.MOD_NAME);

    private static Object[] mergeArgs(Object... args) {
        if (args == null || args.length == 0) return new Object[] { ModCommon.MOD_NAME };
        Object[] out = new Object[args.length + 1];
        out[0] = ModCommon.MOD_NAME;
        System.arraycopy(args, 0, out, 1, args.length);
        return out;
    }

    public static void logInfo(String format, Object... args) {
        LOG.info("[{}] " + format, mergeArgs(args));
    }

    public static void logDebug(String format, Object... args) {
        if (Config.isDebugLogging()) {
            LOG.debug("[{}] " + format, mergeArgs(args));
        }
    }

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

    public static void logError(String format, Object... args) {
        LOG.error("[{}] " + format, mergeArgs(args));
    }

    public static void logError(String format, Throwable t) {
        String msg = "[" + ModCommon.MOD_NAME + "] " + format;
        LOG.error(msg, t);
    }

    public static void logWarn(String format, Object... args) {
        LOG.warn("[{}] " + format, mergeArgs(args));
    }

    public static void logWarn(String format, Throwable t) {
        String msg = "[" + ModCommon.MOD_NAME + "] " + format;
        LOG.warn(msg, t);
    }

    public static void logTrace(String format, Object... args) {
        LOG.trace("[{}] " + format, mergeArgs(args));
    }

    public static void logTrace(String format, Throwable t) {
        String msg = "[" + ModCommon.MOD_NAME + "] " + format;
        LOG.trace(msg, t);
    }
}
