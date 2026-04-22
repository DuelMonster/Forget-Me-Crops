package com.fastharvester.platform;

import com.fastharvester.platform.services.IPlatformHelper;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLLoader;

/**
 * NeoForgePlatformHelper: The futuristic friend of platform helpers!
 * <p>
 * This class helps the mod know when it's running on NeoForge, and how to check for other mods in the NeoForge universe.
 * </p>
 * <p>
 * Why does this matter? Because NeoForge is the new kid on the block, and it wants to be noticed.
 * </p>
 */
public class NeoForgePlatformHelper implements IPlatformHelper {
    /**
     * Returns the name of the platform. (Spoiler: It's "NeoForge"!)
     */
    @Override
    public String getPlatformName() {
        return "NeoForge";
    }

    /**
     * Checks if a mod is loaded in the NeoForge world. Because even the future needs friends.
     */
    @Override
    public boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    /**
     * Are we in a development environment? (Or is it time to show off?)
     */
    @Override
    public boolean isDevelopmentEnvironment() {
        return !FMLLoader.getCurrent().isProduction();
    }

    @Override
    public java.util.Map<String, Integer> getEnchantments(net.minecraft.world.item.ItemStack stack) {
        try {
            java.util.Map<String, Integer> res = com.fastharvester.util.ReflectionUtils.readEnchantmentsFromStack(stack);
            return (res == null) ? java.util.Collections.emptyMap() : res;
        } catch (Throwable t) {
            return java.util.Collections.emptyMap();
        }
    }

    @Override
    public java.util.List<net.minecraft.world.item.ItemStack> getBlockDrops(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state, net.minecraft.world.item.ItemStack tool) {
        if (level == null || state == null) return java.util.Collections.emptyList();
        try {
            java.lang.reflect.Method[] methods = state.getClass().getMethods();
            for (java.lang.reflect.Method m : methods) {
                if (!"getDrops".equals(m.getName())) continue;
                Object res = null;
                try {
                    Class<?>[] params = m.getParameterTypes();
                    if (params.length == 2) {
                        try { res = m.invoke(state, level, pos); } catch (Throwable t) { /* ignore */ }
                    }
                    if (res == null && params.length == 3) {
                        try { res = m.invoke(state, level, pos, (Object) null); } catch (Throwable t1) { /* ignore */ }
                        if (res == null) {
                            try { res = m.invoke(state, level, pos, tool); } catch (Throwable t2) { /* ignore */ }
                        }
                        if (res == null) {
                            try { res = m.invoke(state, level, pos, level.getRandom()); } catch (Throwable t3) { /* ignore */ }
                        }
                    }
                } catch (Throwable ignored) { continue; }
                if (res instanceof java.util.List) {
                    try {
                        @SuppressWarnings("unchecked")
                        java.util.List<net.minecraft.world.item.ItemStack> asList = (java.util.List<net.minecraft.world.item.ItemStack>) res;
                        return asList;
                    } catch (Throwable t) {
                        return java.util.Collections.emptyList();
                    }
                }
            }
        } catch (Throwable t) {
            // ignore
        }
        return java.util.Collections.emptyList();
    }
}

