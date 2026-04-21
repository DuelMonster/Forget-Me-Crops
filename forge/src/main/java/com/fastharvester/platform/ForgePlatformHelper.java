package com.fastharvester.platform;

import com.fastharvester.platform.services.IPlatformHelper;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLLoader;

/**
 * ForgePlatformHelper: The blacksmith of platform helpers!
 * <p>
 * This class lets the mod know when it's running on Forge, and how to check for other mods in the Forge family.
 * </p>
 * <p>
 * Why does this matter? Because Forge likes to do things its own way, and this helper keeps the peace.
 * </p>
 */
public class ForgePlatformHelper implements IPlatformHelper {
    /**
     * Returns the name of the platform. (Hint: It's "Forge"!)
     */
    @Override
    public String getPlatformName() {
        return "Forge";
    }

    /**
     * Checks if a mod is loaded in the Forge world. Because teamwork makes the dream work.
     */
    @Override
    public boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    /**
     * Are we in a development environment? (Or is it time to impress the world?)
     */
    @Override
    public boolean isDevelopmentEnvironment() {
        return !FMLLoader.isProduction();
    }
}
