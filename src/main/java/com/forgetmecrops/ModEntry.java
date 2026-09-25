package com.forgetmecrops;

// ModEntry: unified entry point for both Fabric and NeoForge.
// Stonecutter comment conditions select the correct implementation at build time.

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
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import com.forgetmecrops.client.ConfigScreenFactoryBridge;
import com.forgetmecrops.ticker.FarmTicker;
import com.forgetmecrops.config.Config;
import com.forgetmecrops.util.log.LogUtils;*/ //?}

//? if fabric {
// ModEntry (Fabric): called by Fabric when Forget-Me-Crops loads.
public class ModEntry implements ModInitializer {
    public ModEntry() {}

    @Override
    public void onInitialize() {
        LogUtils.logInfo("[INIT] ModEntry.onInitialize starting; thread={}, java={}.", Thread.currentThread().getName(), System.getProperty("java.version", "unknown"));
        try {
            LogUtils.logInfo("[INIT] Loading config...");
            Config.load();
            LogUtils.logInfo("[INIT] Config load complete.");
        } catch (Throwable t) {
            LogUtils.logError("[INIT] Config.load() threw an exception", t);
            throw t;
        }

        try {
            LogUtils.logInfo("[INIT] Applying configured logging...");
            LogUtils.applyConfiguredLogging();
            LogUtils.logInfo("[INIT] Logging configuration applied.");
        } catch (Exception e) {
            LogUtils.logDebug("[INIT] Failed to apply configured logging early", e);
        }

        try {
            LogUtils.logInfo("[INIT] Starting ForgetMeCrops.init()...");
            ForgetMeCrops.init();
            LogUtils.logInfo("[INIT] ForgetMeCrops.init() completed.");
        } catch (Throwable t) {
            LogUtils.logError("[INIT] ForgetMeCrops.init() threw an exception", t);
            throw t;
        }

        try {
            LogUtils.logInfo("[INIT] Starting FarmTicker.init()...");
            FarmTicker.init();
            LogUtils.logInfo("[INIT] FarmTicker.init() completed.");
        } catch (Throwable t) {
            LogUtils.logError("[INIT] FarmTicker.init() threw an exception", t);
            throw t;
        }

        LogUtils.logInfo("[INIT] ModEntry.onInitialize complete.");
    }
}
//?} else {
/*@Mod(ModCommon.MOD_ID)
public final class ModEntry {

    public ModEntry(IEventBus modEventBus, ModContainer container) {
    container.registerExtensionPoint(
        IConfigScreenFactory.class,
        (java.util.function.Supplier<IConfigScreenFactory>) ConfigScreenFactoryBridge::new
    );
        modEventBus.addListener(this::commonSetup);
        FarmTicker.init(net.neoforged.neoforge.common.NeoForge.EVENT_BUS);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        Config.load();
        try {
            LogUtils.applyConfiguredLogging();
        } catch (Exception e) {
            LogUtils.logDebug("[INIT] Failed to apply configured logging early", e);
        }
        ForgetMeCrops.init();
    }
}*/ //?}
