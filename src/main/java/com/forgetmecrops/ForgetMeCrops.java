package com.forgetmecrops;

import com.forgetmecrops.config.Config;
import com.forgetmecrops.util.log.LogUtils;

/**
 * 🌾 Forget-Me-Crops: The mod's steadfast heart and the keeper of farming destiny!
 * <p>
 * This is where all the real magic lives — the loader-agnostic core logic that makes crops
 * tremble with fear and farmers cheer with joy. Fabric and NeoForge loader implementations
 * call into here with their entrypoints, passing the baton to this unified logic hub.
 * </p>
 * <p>
 * Think of it as the neutral ground where all farming happens, regardless of which loader
 * is playing traffic cop outside. This is the happy place where crops get harvested,
 * configs get respected, and everyone leaves with a smile.
 * </p>
 * <p>
 * Without this class? Your crops would be lonely, unharvested, and frankly pretty sad.
 * We're not here to let that happen.
 * </p>
 */
public class ForgetMeCrops {
    // Utility class — we do not instantiate. We are philosophy, not object.
    private ForgetMeCrops() {}

    /**
     * The all-knowing, all-powerful configuration object!
     * <p>
     * Loader-specific code is responsible for populating this BEFORE calling init().
     * We treat it as gospel truth after initialization. Respect it, fear it, love it.
     * </p>
     */
    public static final Config CONFIG = new Config();

    /**
     * Awakens the sleeping farming giant! This is the mod's true initialization ritual.
     * <p>
     * Loader-specific entrypoints, this is your moment! Call this after you've:
     * 1. Set up the config (CONFIG will be populated and ready)
     * 2. Lit your figurative candles
     * 3. Taken a deep breath
     * 4. Committed to automated farming
     *
     * If you call this without initializing CONFIG first, the crops will know. And they will judge.
     * </p>
     */
    public static void init() {
        // This method is called by loader-specific entrypoints (ModEntry for Fabric, different path for NeoForge)
        // The assumption: CONFIG has already been populated by the time we get here
        // The result: everything below this comment works with that assumption as gospel

        // --- Mandatory startup announcement ---
        // Let the world know we're alive. This line is our flag in the sand, our "we're here!"
        LogUtils.logInfo("Mod initialization started! If you see this, the core logic is alive and kicking.");

        // FastItemFrames adapter detection is deferred to first use, not here.
        // Why? Because classloading during startup is expensive. We'll probe when we actually need it.

        // Debug-status messaging is now emitted at server/world load time.
    }

    /**
     * 📢 Announces the current debug-logging status at server/world load time (not mod init).
     * <p>
     * Why the delay? Because during early mod initialization, the config might still be settling
     * and finalizing. We want to give an HONEST answer about whether debug logging is active.
     * So we wait until the server/world is actually loaded and ready to go — THEN we announce it.
     * Your log file will thank you for this restraint.
     * </p>
     */
    public static void logDebugStatusAtWorldLoad() {
        if (CONFIG != null && Config.isDebugLogging()) {
            LogUtils.logDebug("Debug logging is ENABLED! Buckle up for a flood of farming facts and farm-related trivia.");
        } else {
            LogUtils.logInfo("Debug logging is OFF. Want to see all the juicy details? Set debugLogging=true in your config file.");
        }
    }
}
