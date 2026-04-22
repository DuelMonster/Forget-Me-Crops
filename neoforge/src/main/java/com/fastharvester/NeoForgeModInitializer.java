package com.fastharvester;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.neoforged.bus.api.IEventBus;
import com.fastharvester.ModCommon;
import com.fastharvester.FastHarvester;
import net.neoforged.fml.event.config.ModConfigEvent;
import com.fastharvester.neoforge.FastHarvesterNeoForgeConfig;

@Mod(ModCommon.MOD_ID)
public final class NeoForgeModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModCommon.MOD_NAME);

    public NeoForgeModInitializer(IEventBus modEventBus, ModContainer container) {
        // Register config spec
        container.registerConfig(ModConfig.Type.COMMON, FastHarvesterNeoForgeConfig.SPEC);
        
        // Register config listeners
        modEventBus.addListener(this::onConfigLoading);
        modEventBus.addListener(this::onConfigReloading);
        modEventBus.addListener(this::commonSetup);
    }

    private void onConfigLoading(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == FastHarvesterNeoForgeConfig.SPEC) {
            FastHarvesterNeoForgeConfig.update();
        }
    }
    
    private void onConfigReloading(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == FastHarvesterNeoForgeConfig.SPEC) {
            FastHarvesterNeoForgeConfig.update();
        }
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("{} v{} loaded for NeoForge", ModCommon.MOD_NAME, ModCommon.MOD_VERSION);
        FastHarvester.init();
    }
}
