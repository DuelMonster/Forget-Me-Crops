package com.fastharvester.util.log;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fastharvester.ModCommon;
import com.fastharvester.config.Config;

/**
 * LogUtils: centralized logging helpers for FastHarvester.
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
        if (Config.debugLogging) {
            LOG.debug("[{}] " + format, mergeArgs(args));
        }
    }

    public static void logDebug(String format, Throwable t) {
        if (Config.debugLogging) {
            String msg = "[" + ModCommon.MOD_NAME + "] " + format;
            LOG.debug(msg, t);
        }
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
