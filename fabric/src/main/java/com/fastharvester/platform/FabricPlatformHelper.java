package com.fastharvester.platform;

import com.fastharvester.platform.services.IPlatformHelper;
import net.fabricmc.loader.api.FabricLoader;

/**
 * FabricPlatformHelper: The Fabric fashionista of platform helpers!
 * <p>
 * This class tells the rest of the mod when it's running on Fabric, and how to play nice with other mods in the Fabric ecosystem.
 * </p>
 * <p>
 * Why does this matter? Because every platform wants to feel special, and Fabric is no exception.
 * </p>
 */
public class FabricPlatformHelper implements IPlatformHelper {
    /**
     * Returns the name of the platform. Spoiler: It's always "Fabric" here.
     */
    @Override
    public String getPlatformName() {
        return "Fabric";
    }

    /**
     * Checks if a mod is loaded in the Fabric universe. Because friends are important!
     */
    @Override
    public boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    /**
     * Are we in a development environment? (Or is this the real deal?)
     */
    @Override
    public boolean isDevelopmentEnvironment() {
        return FabricLoader.getInstance().isDevelopmentEnvironment();
    }
}
