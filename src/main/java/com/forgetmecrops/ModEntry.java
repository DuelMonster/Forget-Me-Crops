package com.forgetmecrops;

// 🚀 ModEntry: the grand unified entry point! One class to rule both Fabric and NeoForge.
// Stonecutter comment conditions swap between implementations at build time so the loader
// sees exactly what it expects — no awkward cross-contamination of event buses or annotations.

//? if fabric {
import net.fabricmc.api.ModInitializer;
import com.forgetmecrops.ticker.FarmTicker;
import com.forgetmecrops.config.Config;
import com.forgetmecrops.util.log.LogUtils;
//?} else {
/*import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import com.forgetmecrops.ticker.FarmTicker;
import com.forgetmecrops.config.Config;
import com.forgetmecrops.util.log.LogUtils;*///?}

//? if fabric {
/**
 * ModEntry (Fabric): The very first thing Fabric calls when Forget-Me-Crops loads!
 * <p>
 * Loads config from disk, applies any programmatic debug-logging level changes,
 * kicks off the loader-agnostic {@link ForgetMeCrops#init()}, and registers the
 * Fabric farm ticker. This is where the farming drama begins on Fabric.
 * </p>
 */
public class ModEntry implements ModInitializer {
    /** Public no-arg constructor required by Fabric's entrypoint system. */
    public ModEntry() {}

    /**
     * Fabric entrypoint: the very first thing called when the mod loads.
     * Loads config, applies debug-logging configuration, runs common init,
     * and starts the Fabric farm ticker (chunk discovery + per-tick harvest scheduling).
     */
    @Override
    public void onInitialize() {
        Config.load();
        try {
            LogUtils.applyConfiguredLogging();
        } catch (Exception e) {
            LogUtils.logDebug("[INIT] Failed to apply configured logging early", e);
        }
        ForgetMeCrops.init();
        FarmTicker.init();
    }
}
//?} else {
/*@Mod(ModCommon.MOD_ID)
public final class ModEntry {

    /**
     * NeoForge mod constructor: registers event listeners and initializes the mod.
     * Called once by FML during mod construction. Registers the common-setup listener
     * and starts the farm ticker on the global NeoForge event bus.
     *
     * @param modEventBus the mod-specific event bus (used for FML lifecycle events)
     * @param container   the mod container (unused here but required by FML)
     * /
    public ModEntry(IEventBus modEventBus, ModContainer container) {
        modEventBus.addListener(this::commonSetup);
        FarmTicker.init(net.neoforged.neoforge.common.NeoForge.EVENT_BUS);
    }

    /**
     * Runs after FML setup completes — Minecraft is in a known-safe state here.
     * Loads config, applies debug-logging configuration, and runs common mod initialization.
     */
    private void commonSetup(FMLCommonSetupEvent event) {
        Config.load();
        try {
            LogUtils.applyConfiguredLogging();
        } catch (Exception e) {
            LogUtils.logDebug("[INIT] Failed to apply configured logging early", e);
        }
        ForgetMeCrops.init();
    }
}*///?}
