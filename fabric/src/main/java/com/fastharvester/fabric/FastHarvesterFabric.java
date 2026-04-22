package com.fastharvester.fabric;

// 🌿 Fabric bootstrap: wires up the mod on Fabric, gives a tiny wave on startup.
// Emotional aside: it secretly hopes players have snacks while waiting for crops to grow.

import com.fastharvester.Config;
import com.fastharvester.FastHarvester;
import net.fabricmc.api.ModInitializer;
import com.fastharvester.fabric.FabricFarmTicker;

public class FastHarvesterFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        Config.load();
        FastHarvester.init();
        // Start the Fabric farm ticker (discovers anchors and schedules scans)
        FabricFarmTicker.init();
    }
}
