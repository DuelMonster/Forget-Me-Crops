package com.duelmonster.fastharvester;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fabric specific entry point.
 *
 * <p>Fabric registers the mod via the {@link ModInitializer} interface.
 * The class simply logs a message and delegates any shared logic to the
 * common mod class. All heavy lifting is performed by the common
 * implementation.
 */
public final class FabricModInitializer implements ModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModCommon.MOD_NAME);

    @Override
    public void onInitialize() {
        LOGGER.info("{} v{} loaded for Fabric", ModCommon.MOD_NAME, ModCommon.MOD_VERSION);
        // Common logic would be invoked here if needed.
    }
}
