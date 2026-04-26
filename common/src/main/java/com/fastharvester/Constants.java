package com.fastharvester;

// 🎯 Constants: constant companions who remind us of the mod's identity and logger etiquette.
// Emotional aside: they're small but proud.

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Constants: The VIP list for FastHarvester!
 * <p>
 * Here live the most important names and IDs in the mod. If you ever need to shout at the logger, this is where you get the megaphone.
 * </p>
 * <p>
 * Why does this matter? Because hardcoding strings everywhere is a recipe for chaos (and sad maintainers).
 * </p>
 */
public class Constants {
	/** Utility class: do not instantiate. */
	private Constants() {}

	/**
	 * The one and only mod ID. If you change this, the universe may collapse.
	 */
	public static final String MOD_ID = "FastHarvester";

	/**
	 * The mod's name. Say it loud, say it proud!
	 */
	public static final String MOD_NAME = "FastHarvester";

	/**
	 * Logger: For when you need to talk to the console, vent your frustrations, or just say hi.
	 */
	public static final Logger LOG = LoggerFactory.getLogger(MOD_NAME);

	private static Object[] mergeArgs(Object... args) {
		if (args == null || args.length == 0) return new Object[] { MOD_NAME };
		Object[] out = new Object[args.length + 1];
		out[0] = MOD_NAME;
		System.arraycopy(args, 0, out, 1, args.length);
		return out;
	}

	/**
	 * Log an informational message prefixed with the mod name.
	 *
	 * @param format SLF4J format string
	 * @param args format arguments
	 */
	public static void logInfo(String format, Object... args) {
		LOG.info("[{}] " + format, mergeArgs(args));
	}

	/**
	 * Log a debug message when debug logging is enabled.
	 *
	 * @param format SLF4J format string
	 * @param args format arguments
	 */
	public static void logDebug(String format, Object... args) {
		if (Config.debugLogging) {
			LOG.debug("[{}] " + format, mergeArgs(args));
		}
	}

	/**
	 * Log a debug message with an attached Throwable when debug logging is enabled.
	 *
	 * @param format message text
	 * @param t throwable to include in the log
	 */
	public static void logDebug(String format, Throwable t) {
		if (Config.debugLogging) {
			String msg = "[" + MOD_NAME + "] " + format;
			LOG.debug(msg, t);
		}
	}

	/**
	 * Log an error with formatted arguments.
	 *
	 * @param format SLF4J format string
	 * @param args format arguments
	 */
	public static void logError(String format, Object... args) {
		LOG.error("[{}] " + format, mergeArgs(args));
	}

	/**
	 * Log an error with an attached Throwable.
	 *
	 * @param format message text
	 * @param t throwable to include in the log
	 */
	public static void logError(String format, Throwable t) {
		String msg = "[" + MOD_NAME + "] " + format;
		LOG.error(msg, t);
	}

	/**
	 * Log a warning with formatted arguments.
	 *
	 * @param format SLF4J format string
	 * @param args format arguments
	 */
	public static void logWarn(String format, Object... args) {
		LOG.warn("[{}] " + format, mergeArgs(args));
	}

	/**
	 * Log a warning with an attached Throwable.
	 *
	 * @param format message text
	 * @param t throwable to include in the log
	 */
	public static void logWarn(String format, Throwable t) {
		String msg = "[" + MOD_NAME + "] " + format;
		LOG.warn(msg, t);
	}

	/**
	 * Log a trace message with formatted arguments.
	 *
	 * @param format SLF4J format string
	 * @param args format arguments
	 */
	public static void logTrace(String format, Object... args) {
		LOG.trace("[{}] " + format, mergeArgs(args));
	}

	/**
	 * Log a trace message with an attached Throwable.
	 *
	 * @param format message text
	 * @param t throwable to include in the log
	 */
	public static void logTrace(String format, Throwable t) {
		String msg = "[" + MOD_NAME + "] " + format;
		LOG.trace(msg, t);
	}
}
