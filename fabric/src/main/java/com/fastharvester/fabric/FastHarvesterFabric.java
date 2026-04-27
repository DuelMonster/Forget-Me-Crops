package com.fastharvester.fabric;

// 🌿 Fabric bootstrap: wires up the mod on Fabric, gives a tiny wave on startup.
// Emotional aside: it secretly hopes players have snacks while waiting for crops to grow.

import com.fastharvester.Config;
import com.fastharvester.FastHarvester;
import com.fastharvester.fabric.ticker.FabricFarmTicker;
import net.fabricmc.api.ModInitializer;

/**
 * Fabric bootstrap: wires up the mod on Fabric and initializes core systems.
 */
public class FastHarvesterFabric implements ModInitializer {
    /** Public no-arg constructor used by the mod loader. */
    public FastHarvesterFabric() {}
    /**
     * Fabric entrypoint: load config, init common logic, and start the farm ticker.
     * Emotional aside: this is the tiny handshake that starts all the farming drama.
     */
    @Override
    public void onInitialize() {
        Config.load();
        FastHarvester.init();
        // Start the Fabric farm ticker (discovers anchors and schedules scans)
        FabricFarmTicker.init();
    }
}
