package com.fastharvester.neoforge;

import com.fastharvester.FastHarvester;
import net.neoforged.fml.common.Mod;

/**
 * FastHarvesterNeoForge: The futuristic doorman for FastHarvester on NeoForge!
 * <p>
 * This class is the official entrypoint for NeoForge. It sets up the mod and ensures the crops of tomorrow are ready to be harvested today.
 * </p>
 * <p>
 * Why does this matter? Because NeoForge wants to feel special, and this class rolls out the red carpet.
 * </p>
 */
@Mod("fastharvester")
public class FastHarvesterNeoForge {
    /**
     * Called by NeoForge when the mod is loaded. The future is now!
     */
    public FastHarvesterNeoForge() {
        FastHarvesterNeoForgeConfig.register();
        net.neoforged.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus().addListener(FastHarvesterNeoForgeConfig::onConfigReload);
        Config.load();
        FastHarvester.init();
    }
}
