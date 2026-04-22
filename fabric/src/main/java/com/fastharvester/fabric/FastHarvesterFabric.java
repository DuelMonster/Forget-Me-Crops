package com.fastharvester.fabric;

import com.fastharvester.FastHarvester;
import net.fabricmc.api.ModInitializer;

/**
 * FastHarvesterFabric: The grand entrance for FastHarvester on Fabric!
 * <p>
 * This class is the official greeter for the Fabric loader. It sets up the mod and makes sure everything is ready for a bountiful harvest.
 * </p>
 * <p>
 * Why does this matter? Because every party needs a host, and Fabric expects nothing less.
 * </p>
 */
public class FastHarvesterFabric implements ModInitializer {
    /**
     * Called by Fabric when the mod is loaded. Time to shine!
     */
    @Override
    public void onInitialize() {
        // TODO: Load config from file or Fabric config system here
        FastHarvester.init();
    }
}
