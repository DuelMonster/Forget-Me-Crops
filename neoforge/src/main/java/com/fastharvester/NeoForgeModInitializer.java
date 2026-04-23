package com.fastharvester;

// ❤️ Playful note: NeoForge initializer here, quietly cheering when mods boot.
// This little bootstrapping class wakes up the mod in NeoForge and whispers "go harvest".

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
import com.fastharvester.neoforge.NeoForgeFarmTicker;

@Mod(ModCommon.MOD_ID)
public final class NeoForgeModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModCommon.MOD_NAME);

    /**
     * NeoForge mod initializer: register config listeners and initialize the farm ticker.
     */
    public NeoForgeModInitializer(IEventBus modEventBus, ModContainer container) {
        // Register config spec
        container.registerConfig(ModConfig.Type.COMMON, FastHarvesterNeoForgeConfig.SPEC);
        
        // Register config listeners
        modEventBus.addListener(this::onConfigLoading);
        modEventBus.addListener(this::onConfigReloading);
        modEventBus.addListener(this::commonSetup);
        // Register NeoForge-specific farm discovery and ticker on the runtime/event bus
        // Use the container's event bus rather than the incoming mod lifecycle bus so
        // runtime events (chunk/tick) are accepted by the bus type checker.
        try {
            var runtimeBus = container.getEventBus();
            NeoForgeFarmTicker.init(runtimeBus);
        } catch (IllegalArgumentException iae) {
            LOGGER.warn("NeoForgeFarmTicker registration skipped (runtime bus rejected events): {}", iae.toString());
        } catch (Throwable t) {
            LOGGER.warn("NeoForgeFarmTicker init threw; skipping runtime registration: {}", t.toString());
        }
    }

    /**
     * Handle config loading events and update runtime values.
     */
    private void onConfigLoading(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == FastHarvesterNeoForgeConfig.SPEC) {
            FastHarvesterNeoForgeConfig.update();
        }
    }
    
    /**
     * Handle config reload events and refresh in-memory config.
     */
    private void onConfigReloading(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == FastHarvesterNeoForgeConfig.SPEC) {
            FastHarvesterNeoForgeConfig.update();
        }
    }

    /**
     * Common setup invoked during mod initialization; boots core logic.
     */
    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("{} v{} loaded for NeoForge", ModCommon.MOD_NAME, ModCommon.MOD_VERSION);
        FastHarvester.init();
    }
}
