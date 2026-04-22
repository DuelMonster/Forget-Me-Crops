package com.fastharvester;

// 🌐 ModCommon: loader-agnostic boot glue. It keeps the mod's identity intact across environments.
// Why it matters: cross-platform calmness.

/**
 * ModCommon: The universal handshake for FastHarvester!
 * <p>
 * This class is the glue that binds Fabric, NeoForge, and any other loader together in harmony. It holds the mod's identity and version—think of it as the mod's passport and birth certificate.
 * </p>
 * <p>
 * Why does this matter? Because every loader wants to know who you are, and this class makes sure you never forget your own name (or version).
 * </p>
 */
public final class ModCommon {
    /**
     * The mod's unique ID. Don't leave home without it!
     */
    public static final String MOD_ID = "fastharvester";

    /**
     * The mod's display name. For when you want to look fancy in logs.
     */
    public static final String MOD_NAME = "FastHarvester";

    /**
     * The mod's version. Always know what flavor of FastHarvester you're running.
     */
    public static final String MOD_VERSION = "${mod_version}";

    /**
     * Private constructor: No one gets to make an instance of this class. It's too cool for that.
     */
    /**
     * Private constructor: No one gets to make an instance of this class. It's too cool for that.
     */
    private ModCommon() {
        // Utility class – no instances allowed! (Seriously, don't try.)
    }
}
