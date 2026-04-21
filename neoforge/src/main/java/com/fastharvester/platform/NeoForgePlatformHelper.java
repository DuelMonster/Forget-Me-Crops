package com.fastharvester.platform;

import com.fastharvester.platform.services.IPlatformHelper;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLLoader;

/**
 * NeoForgePlatformHelper: The futuristic friend of platform helpers!
 * <p>
 * This class helps the mod know when it's running on NeoForge, and how to check for other mods in the NeoForge universe.
 * </p>
 * <p>
 * Why does this matter? Because NeoForge is the new kid on the block, and it wants to be noticed.
 * </p>
 */
public class NeoForgePlatformHelper implements IPlatformHelper {
    /**
     * Returns the name of the platform. (Spoiler: It's "NeoForge"!)
     */
    @Override
    public String getPlatformName() {
        return "NeoForge";
    }

    /**
     * Checks if a mod is loaded in the NeoForge world. Because even the future needs friends.
     */
    @Override
    public boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    /**
     * Are we in a development environment? (Or is it time to show off?)
     */
    @Override
    public boolean isDevelopmentEnvironment() {
        return !FMLLoader.getCurrent().isProduction();
    }
}
