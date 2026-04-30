package com.fastharvester.neoforge;

// ❤️ NeoForge initializer moved into the neoforge package to keep loader-specific
// initialization code inside the module's namespace.

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.bus.api.IEventBus;
// ModConfigEvent handlers removed; NeoForge uses shared Config load/save.
// NeoForge now uses the shared `Config` class and Fabric-style toml filenames.
import com.fastharvester.neoforge.ticker.NeoForgeFarmTicker;
// AutoConfig is not used for NeoForge to avoid duplicate config sources.
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Locale;
import net.minecraft.world.InteractionResult;
import com.fastharvester.util.log.LogUtils;
import com.fastharvester.config.Config;
import com.fastharvester.FastHarvester;

/**
 * NeoForge mod initializer: bootstraps FastHarvester on the NeoForge loader,
 * registers config handlers, and wires the NeoForge farm ticker.
 */
@Mod(com.fastharvester.ModCommon.MOD_ID)
public final class NeoForgeModInitializer {

    @SuppressWarnings("null")
    public NeoForgeModInitializer(IEventBus modEventBus, ModContainer container) {
        // Use the shared `Config` files (fastharvester-client.toml / fastharvester-server.toml)
        // to remain consistent with the Fabric module. Avoid registering an
        // additional NeoForge ModConfigSpec to prevent duplicate/conflicting
        // configuration sources that make runtime changes non-deterministic.
        modEventBus.addListener(this::commonSetup);
        NeoForgeFarmTicker.init(net.neoforged.neoforge.common.NeoForge.EVENT_BUS);

        boolean isClient = true;
        try {
            Class.forName("net.minecraft.client.Minecraft", false, this.getClass().getClassLoader());
        } catch (ClassNotFoundException cnfe) {
            isClient = false;
        }

        if (isClient) try {
            Class<?> factoryClass = Class.forName("net.neoforged.neoforge.client.gui.IConfigScreenFactory");
            ClassLoader loader = factoryClass.getClassLoader();
            final java.lang.reflect.Method[] neoForgeCreate = new java.lang.reflect.Method[1];
            try {
                Class<?> neoCfg = loader.loadClass("com.fastharvester.neoforge.client.NeoForgeClothConfig");
                Class<?> screenClass = loader.loadClass("net.minecraft.client.gui.screens.Screen");
                neoForgeCreate[0] = neoCfg.getMethod("create", screenClass);
            } catch (Throwable t2) {
                LogUtils.logDebug("Could not resolve NeoForgeClothConfig.create on loader", t2);
            }

            Object factory = Proxy.newProxyInstance(loader, new Class<?>[]{factoryClass}, (proxy, method, args) -> {
                LogUtils.logInfo("IConfigScreenFactory invoked: {} with args={}", method.getName(), Arrays.toString(args));
                if ("createScreen".equals(method.getName()) && neoForgeCreate[0] != null) {
                    for (Object a : args) {
                        if (a == null) continue;
                        Class<?> argClass = a.getClass();
                        while (argClass != null) {
                            if ("net.minecraft.client.gui.screens.Screen".equals(argClass.getName())) {
                                try {
                                    LogUtils.logInfo("Creating ClothConfig screen for FastHarvester (parent loader={})", a.getClass().getName());
                                    return neoForgeCreate[0].invoke(null, a);
                                } catch (Throwable t3) {
                                    LogUtils.logDebug("Invocation of NeoForgeClothConfig.create failed", t3);
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
                String name = m.getName().toLowerCase(Locale.ROOT);
                if ((name.contains("extension") || name.contains("register") || name.contains("add")) && m.getParameterCount() == 2) {
                    Class<?> p0 = m.getParameterTypes()[0];
                    if (p0 == Class.class) {
                        registerMethod = m;
                        break;
                    }
                }
            }

            if (registerMethod != null) {
                LogUtils.logInfo("Invoking {} on ModContainer {} with factoryClass={}, factoryClassLoader=",
                    registerMethod.getName(), container.getClass().getName(), factoryClass.getName(), factory.getClass().getClassLoader());
                Class<?> secondParam = registerMethod.getParameterTypes()[1];
                Object secondArg;
                if (secondParam == java.util.function.Supplier.class) {
                    java.util.function.Supplier<?> supplier = () -> factory;
                    secondArg = supplier;
                    LogUtils.logDebug("Using Supplier wrapper for factory (Supplier overload detected)");
                } else {
                    secondArg = factory;
                }
                registerMethod.invoke(container, factoryClass, secondArg);
                LogUtils.logInfo("Registered NeoForge IConfigScreenFactory via {}", registerMethod.getName());
                LogUtils.logDebug("Registered factory types: {}", Arrays.toString(factory.getClass().getInterfaces()));
            } else {
                LogUtils.logDebug("Could not find ModContainer registration method; listing methods for debugging...");
                for (Method m : container.getClass().getMethods()) {
                    LogUtils.logDebug("ModContainer method: {} {}", m.getName(), Arrays.toString(m.getParameterTypes()));
                }
            }
        } catch (Throwable t) {
            LogUtils.logDebug("Reflective config-screen registration failed", t);
        } else {
            LogUtils.logDebug("Skipping NeoForge config-screen registration on non-client environment");
        }
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LogUtils.logInfo("{} v{} loaded for NeoForge", com.fastharvester.ModCommon.MOD_NAME, com.fastharvester.ModCommon.MOD_VERSION);
        try { Config.load(); } catch (Throwable t) { LogUtils.logWarn("Failed to load shared config", t); }
        // Apply programmatic logger-level changes if debugLogging is enabled so
        // debug traces are visible without requiring users to edit backend configs.
        try { LogUtils.applyConfiguredLogging(); } catch (Throwable t) { LogUtils.logDebug("applyConfiguredLogging failed", t); }
        FastHarvester.init();
    }
}
