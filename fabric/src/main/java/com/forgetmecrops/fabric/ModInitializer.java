package com.forgetmecrops.fabric;

// 🌿 Fabric bootstrap: wires up the mod on Fabric, gives a tiny wave on startup.
// Emotional aside: it secretly hopes players have snacks while waiting for crops to grow.

import com.forgetmecrops.config.Config;
import com.forgetmecrops.ForgetMeCrops;
import com.forgetmecrops.fabric.ticker.FarmTicker;
import com.forgetmecrops.util.log.LogUtils;

/**
 * Fabric ModInitializer: The very first thing Fabric runs when Forget-Me-Crops loads!
 * <p>
 * Loads config from disk, applies any programmatic debug-logging level changes,
 * kicks off the loader-agnostic ForgetMeCrops.init(), and registers the Fabric farm ticker.
 * This is the moment the mod wakes up, stretches, and starts watching your crops.
 * </p>
 * <p>
 * Registered as a {@code main} entrypoint in fabric.mod.json. Fabric calls this once
 * during startup; make it count.
 * </p>
 */
public class ModInitializer implements net.fabricmc.api.ModInitializer {
    /** Public no-arg constructor used by the Fabric mod loader entrypoint system. */
    public ModInitializer() {}
    /**
     * Fabric entrypoint: the very first thing called when the mod loads on Fabric.
     * Loads config from disk (or creates defaults), applies debug-logging configuration,
     * runs the common mod initialization (ForgetMeCrops.init), and starts the Fabric
     * farm ticker (chunk discovery + per-tick harvest scheduling).
     * This is where it all begins. Welcome to the farming drama.
     */
    @Override
    public void onInitialize() {
        Config.load();
        // Ensure programmatic debug-level setting is applied before core init
        try {
            LogUtils.applyConfiguredLogging();
        } catch (Exception e) {
            LogUtils.logDebug("[INIT] Failed to apply configured logging early", e);
        }
        ForgetMeCrops.init();
        // Start the Fabric farm ticker (discovers anchors and schedules scans)
        FarmTicker.init();
    }
}
