package com.forgetmecrops.fabric;

// 🌿 Fabric bootstrap: wires up the mod on Fabric, gives a tiny wave on startup.
// Emotional aside: it secretly hopes players have snacks while waiting for crops to grow.

import com.forgetmecrops.config.Config;
import com.forgetmecrops.ForgetMeCrops;
import com.forgetmecrops.fabric.ticker.FarmTicker;
import com.forgetmecrops.util.log.LogUtils;

/**
 * Fabric bootstrap: wires up the mod on Fabric and initializes core systems.
 */
public class ModInitializer implements net.fabricmc.api.ModInitializer {
    /** Public no-arg constructor used by the mod loader. */
    public ModInitializer() {}
    /**
     * Fabric entrypoint: load config, init common logic, and start the farm ticker.
     * Emotional aside: this is the tiny handshake that starts all the farming drama.
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
