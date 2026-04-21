package com.fastharvester;

import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.neoforged.bus.api.IEventBus;
import com.fastharvester.ModCommon;
import com.fastharvester.FastHarvester;

/**
 * NeoForgeModInitializer: The master of ceremonies for FastHarvester on NeoForge!
 * <p>
 * This class is the main entrypoint for NeoForge, handling setup and making sure the mod is ready to dazzle.
 * </p>
 * <p>
 * Why does this matter? Because every great show needs a great host, and NeoForge expects nothing less.
 * </p>
 */
@Mod(ModCommon.MOD_ID)
public final class NeoForgeModInitializer {
    /**
     * Logger: For when you need to announce your arrival in style.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(ModCommon.MOD_NAME);

    /**
     * Called by NeoForge to set up the mod. The curtain rises!
     */
    public NeoForgeModInitializer(IEventBus modEventBus) {
        modEventBus.addListener(this::commonSetup);
    }

    /**
     * Common setup logic for NeoForge. Time to shine!
     */
    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("{} v{} loaded for NeoForge", ModCommon.MOD_NAME, ModCommon.MOD_VERSION);
        FastHarvester.init();
    }
}
