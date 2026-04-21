package com.fastharvester.fabric;

import com.fastharvester.FastHarvester;
import net.fabricmc.api.ModInitializer;

/**
 * Fabric mod initializer. Wires up FastHarvester for Fabric.
 */
public class FastHarvesterFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        // Loader-specific config loading would go here
        FastHarvester.init();
    }
}
