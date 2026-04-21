package com.fastharvester;

/**
 * 🌾 FastHarvester: The beating heart of your automated farming dreams! 🌾
 * <p>
 * This class is the main entry point for all the juicy, loader-agnostic logic that makes crops tremble and farmers cheer.
 * Loader-specific entrypoints call into here to kick off the magic. If you ever wondered where the fun begins, it's right here!
 * </p>
 * <p>
 * Why does this matter? Because without it, your crops would be lonely, unharvested, and probably a little sad.
 * </p>
 */
public class FastHarvester {
    /**
     * Creates a new FastHarvester. But you probably just want the static methods!
     */
    public FastHarvester() {}
    /**
     * The all-knowing, all-powerful config! Loader-specific code should fill this with love (and settings) before calling init().
     */
    public static Config CONFIG = new Config();

    /**
     * Initializes the core logic. Loader-specific entrypoints, this is your cue!
     * <p>
     * Call this after you've set up the config, loaded your snacks, and are ready for some serious farming action.
     * </p>
     */
    public static void init() {
        // Called by loader-specific entrypoints
        // Loader should populate CONFIG before calling this
        // (If you forget, the crops will know. And they will judge you.)

        // --- Guaranteed debug log for mod initialization ---
        Constants.LOG.info("[FastHarvester] Mod initialization started! If you see this, the core logic is alive and kicking.");
        if (CONFIG != null && CONFIG.debugLogging) {
            Constants.LOG.debug("[FastHarvester] Debug logging is ENABLED! Prepare for a flood of farming facts.");
        } else {
            Constants.LOG.info("[FastHarvester] Debug logging is OFF. For more details, set debugLogging=true in your config.");
        }
    }
}
