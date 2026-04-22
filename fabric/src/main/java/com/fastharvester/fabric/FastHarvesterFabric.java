package com.fastharvester.fabric;

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
