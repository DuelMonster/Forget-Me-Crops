package com.fastharvester;

// ❤️ Playful note: NeoForge initializer here, quietly cheering when mods boot.
// This little bootstrapping class wakes up the mod in NeoForge and whispers "go harvest".

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
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

/**
 * NeoForge mod initializer: bootstraps FastHarvester on the NeoForge loader,
 * registers config handlers, and wires the NeoForge farm ticker.
 */
@Mod(ModCommon.MOD_ID)
public final class NeoForgeModInitializer {
    

    /**
     * NeoForge mod initializer: register config listeners and initialize the farm ticker.
        *
        * @param modEventBus the mod event bus to register lifecycle listeners on
        * @param container the mod container instance provided by the loader
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
            Constants.logInfo("AutoConfig registered, holder={}", holder != null);

            // Sync loaded values into the shared Config object
            holder.registerLoadListener((h, d) -> {
                Config.applyServerSettings(d.tickInterval, d.frameRediscoveryInterval, d.scanRangeX, d.scanRangeZ,
                    d.durabilityMode, d.mendingNegation, d.debugLogging,
                    d.chestFullCooldownTicks, d.maxSpiralDurationTicks,
                    d.rotationMode, d.seedClutterMode, d.seedReservePerType);
                Config.applyClientSettings(d.harvestParticles);
                return InteractionResult.SUCCESS;
            });

            // When autoconfig saves, persist to our toml files.
            holder.registerSaveListener((h, d) -> {
                Config.applyServerSettings(d.tickInterval, d.frameRediscoveryInterval, d.scanRangeX, d.scanRangeZ,
                    d.durabilityMode, d.mendingNegation, d.debugLogging,
                    d.chestFullCooldownTicks, d.maxSpiralDurationTicks,
                    d.rotationMode, d.seedClutterMode, d.seedReservePerType);
                Config.applyClientSettings(d.harvestParticles);
                Config.save();
                return InteractionResult.SUCCESS;
            });
        } catch (Throwable t) {
            Constants.logDebug("AutoConfig/ClothConfig not available at runtime", t);
        }

        // Try to register a native NeoForge config screen provider so the "Config"
        // button in the mods list becomes enabled. We do this reflectively so
        // the code remains robust across loader API changes. Only attempt this
        // on a physical client where the client GUI classes are present.
        boolean isClient = true;
        try {
            Class.forName("net.minecraft.client.Minecraft", false, this.getClass().getClassLoader());
        } catch (ClassNotFoundException cnfe) {
            isClient = false;
        }

        if (isClient) try {
            Class<?> factoryClass = Class.forName("net.neoforged.neoforge.client.gui.IConfigScreenFactory");
            ClassLoader loader = factoryClass.getClassLoader();
            // Resolve our NeoForgeClothConfig.create(Screen) using the loader classloader so
            // returned Screen instances are created in the same loader and avoid
            // cross-classloader instanceof/return-type issues.
            final java.lang.reflect.Method[] neoForgeCreate = new java.lang.reflect.Method[1];
            try {
                Class<?> neoCfg = loader.loadClass("com.fastharvester.neoforge.NeoForgeClothConfig");
                Class<?> screenClass = loader.loadClass("net.minecraft.client.gui.screens.Screen");
                neoForgeCreate[0] = neoCfg.getMethod("create", screenClass);
            } catch (Throwable t2) {
                Constants.logDebug("Could not resolve NeoForgeClothConfig.create on loader", t2);
            }

            Object factory = Proxy.newProxyInstance(loader, new Class<?>[]{factoryClass}, (proxy, method, args) -> {
                    Constants.logInfo("IConfigScreenFactory invoked: {} with args={}", method.getName(), Arrays.toString(args));
                if ("createScreen".equals(method.getName()) && neoForgeCreate[0] != null) {
                    // Find the Screen argument (we compare by name to avoid classloader instanceof)
                    for (Object a : args) {
                        if (a == null) continue;
                        Class<?> argClass = a.getClass();
                        while (argClass != null) {
                            if ("net.minecraft.client.gui.screens.Screen".equals(argClass.getName())) {
                                try {
                                    Constants.logInfo("Creating ClothConfig screen for FastHarvester (parent loader={})", a.getClass().getName());
                                    return neoForgeCreate[0].invoke(null, a);
                                } catch (Throwable t3) {
                                    Constants.logDebug("Invocation of NeoForgeClothConfig.create failed", t3);
                                    return null;
                                }
                            }
                            argClass = argClass.getSuperclass();
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
                Constants.logInfo("Invoking {} on ModContainer {} with factoryClass={}, factoryClassLoader={}",
                    registerMethod.getName(), container.getClass().getName(), factoryClass.getName(), factory.getClass().getClassLoader());
                Class<?> secondParam = registerMethod.getParameterTypes()[1];
                Object secondArg;
                if (secondParam == java.util.function.Supplier.class) {
                    java.util.function.Supplier<?> supplier = () -> factory;
                    secondArg = supplier;
                    Constants.logDebug("Using Supplier wrapper for factory (Supplier overload detected)");
                } else {
                    secondArg = factory;
                }
                registerMethod.invoke(container, factoryClass, secondArg);
                Constants.logInfo("Registered NeoForge IConfigScreenFactory via {}", registerMethod.getName());
                Constants.logDebug("Registered factory types: {}", Arrays.toString(factory.getClass().getInterfaces()));
            } else {
                Constants.logDebug("Could not find ModContainer registration method; listing methods for debugging...");
                for (Method m : container.getClass().getMethods()) {
                    Constants.logDebug("ModContainer method: {} {}", m.getName(), Arrays.toString(m.getParameterTypes()));
                }
            }
        } catch (Throwable t) {
            Constants.logDebug("Reflective config-screen registration failed", t);
        } else {
            Constants.logDebug("Skipping NeoForge config-screen registration on non-client environment");
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
        Constants.logInfo("{} v{} loaded for NeoForge", ModCommon.MOD_NAME, ModCommon.MOD_VERSION);
        FastHarvester.init();
    }
}
