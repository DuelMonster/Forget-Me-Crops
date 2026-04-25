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
import net.neoforged.fml.event.config.ModConfigEvent;
import com.fastharvester.neoforge.FastHarvesterNeoForgeConfig;
import com.fastharvester.neoforge.NeoForgeFarmTicker;
import com.fastharvester.neoforge.FastHarvesterAutoConfig;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.Toml4jConfigSerializer;
import me.shedaniel.autoconfig.ConfigHolder;
import net.minecraft.client.gui.screens.Screen;
import com.fastharvester.neoforge.NeoForgeClothConfig;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import net.minecraft.world.InteractionResult;

@Mod(ModCommon.MOD_ID)
public final class NeoForgeModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModCommon.MOD_NAME);

    /**
     * NeoForge mod initializer: register config listeners and initialize the farm ticker.
     */
    @SuppressWarnings("null")
    public NeoForgeModInitializer(IEventBus modEventBus, ModContainer container) {
        // Register config spec
        container.registerConfig(ModConfig.Type.COMMON, FastHarvesterNeoForgeConfig.SPEC);
        
        // Register config listeners
        modEventBus.addListener(this::onConfigLoading);
        modEventBus.addListener(this::onConfigReloading);
        modEventBus.addListener(this::commonSetup);
        // Register NeoForge-specific farm discovery and ticker on the global NeoForge event bus.
        // This is the bus that `EventHooks` posts runtime events to (chunk/tick/etc.).
        NeoForgeFarmTicker.init(net.neoforged.neoforge.common.NeoForge.EVENT_BUS);

        // Register AutoConfig-backed config so ClothConfig/AutoConfig can supply
        // a native mods-list config button and generate the config screen.
        try {
            AutoConfig.register(FastHarvesterAutoConfig.class, Toml4jConfigSerializer::new);
            ConfigHolder<FastHarvesterAutoConfig> holder = AutoConfig.getConfigHolder(FastHarvesterAutoConfig.class);
            LOGGER.info("AutoConfig registered, holder={}", holder != null);

            // Sync loaded values into the shared Config object
            holder.registerLoadListener((h, d) -> {
                Config.applyServerSettings(d.tickInterval, d.frameRediscoveryInterval, d.scanRange,
                    d.durabilityMode, d.mendingNegation, d.debugLogging,
                    d.chestFullCooldownTicks, d.maxSpiralDurationTicks,
                    d.rotationMode, d.seedClutterMode, d.seedReservePerType);
                Config.applyClientSettings(d.harvestParticles);
                return InteractionResult.SUCCESS;
            });

            // When autoconfig saves, persist to our toml files.
            holder.registerSaveListener((h, d) -> {
                Config.applyServerSettings(d.tickInterval, d.frameRediscoveryInterval, d.scanRange,
                    d.durabilityMode, d.mendingNegation, d.debugLogging,
                    d.chestFullCooldownTicks, d.maxSpiralDurationTicks,
                    d.rotationMode, d.seedClutterMode, d.seedReservePerType);
                Config.applyClientSettings(d.harvestParticles);
                Config.save();
                return InteractionResult.SUCCESS;
            });
        } catch (Throwable t) {
            LOGGER.debug("AutoConfig/ClothConfig not available at runtime", t);
        }

        // Try to register a native NeoForge config screen provider so the "Config"
        // button in the mods list becomes enabled. We do this reflectively so
        // the code remains robust across loader API changes.
        try {
            Class<?> factoryClass = Class.forName("net.neoforged.neoforge.client.gui.IConfigScreenFactory");
            Object factory = Proxy.newProxyInstance(factoryClass.getClassLoader(), new Class<?>[]{factoryClass}, (proxy, method, args) -> {
                LOGGER.info("IConfigScreenFactory invoked: {} with args={}", method.getName(), Arrays.toString(args));
                if ("createScreen".equals(method.getName())) {
                    // defensive: attempt to locate Screen argument
                    for (Object a : args) {
                        if (a instanceof Screen) {
                            LOGGER.info("Creating ClothConfig screen for FastHarvester (parent={})", a.getClass().getName());
                            return NeoForgeClothConfig.create((Screen) a);
                        }
                    }
                }
                return null;
            });

            Method registerMethod = null;
            for (Method m : container.getClass().getMethods()) {
                String name = m.getName().toLowerCase();
                if ((name.contains("extension") || name.contains("register") || name.contains("add")) && m.getParameterCount() == 2) {
                    Class<?> p0 = m.getParameterTypes()[0];
                    if (p0 == Class.class) {
                        registerMethod = m;
                        break;
                    }
                }
            }

            if (registerMethod != null) {
                LOGGER.info("Invoking {} on ModContainer {} with factoryClass={}, factoryClassLoader={}",
                        registerMethod.getName(), container.getClass().getName(), factoryClass.getName(), factory.getClass().getClassLoader());
                registerMethod.invoke(container, factoryClass, factory);
                LOGGER.info("Registered NeoForge IConfigScreenFactory via {}", registerMethod.getName());
                LOGGER.debug("Registered factory types: {}", Arrays.toString(factory.getClass().getInterfaces()));
            } else {
                LOGGER.debug("Could not find ModContainer registration method; listing methods for debugging...");
                for (Method m : container.getClass().getMethods()) {
                    LOGGER.debug("ModContainer method: {} {}", m.getName(), Arrays.toString(m.getParameterTypes()));
                }
            }
        } catch (Throwable t) {
            LOGGER.debug("Reflective config-screen registration failed", t);
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
