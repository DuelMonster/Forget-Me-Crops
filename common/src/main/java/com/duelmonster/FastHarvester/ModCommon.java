package com.duelmonster.fastharvester;

/**
 * Common mod entry point used by both Fabric and NeoForge.
 *
 * <p>All shared logic should be placed here. The class is intentionally
 * lightweight – it only registers the mod with the common mod loader
 * infrastructure. The real game logic lives in the loader‑specific
 * entrypoints.</p>
 */
public final class ModCommon {
    public static final String MOD_ID = "fastharvester";
    public static final String MOD_NAME = "FastHarvester";
    public static final String MOD_VERSION = "${mod_version}";

    private ModCommon() {
        // Utility class – no instances.
    }
}
