package com.duelmonster.fastharvester;

import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * NeoForge specific entry point.
 *
 * <p>NeoForge uses the {@link Mod} annotation to register the mod.
 * The class listens for the common setup event and logs a message.
 * Shared logic would be invoked from the common package.
 */
@Mod(ModCommon.MOD_ID)
public final class NeoForgeModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModCommon.MOD_NAME);

    public NeoForgeModInitializer() {
        // Register event listeners.
        net.neoforged.bus.api.IEventBus bus = net.neoforged.bus.api.FMLJavaModLoadingContext.get().getModEventBus();
        bus.addListener(this::commonSetup);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("{} v{} loaded for NeoForge", ModCommon.MOD_NAME, ModCommon.MOD_VERSION);
        // Common logic would be invoked here if needed.
    }
}
