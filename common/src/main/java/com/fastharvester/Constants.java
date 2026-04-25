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
	 * The one and only mod ID. If you change this, the universe (and your mod) may collapse.
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
}
