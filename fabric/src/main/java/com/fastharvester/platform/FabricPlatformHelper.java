package com.fastharvester.platform;

import com.fastharvester.platform.services.IPlatformHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantment;
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/**
 * FabricPlatformHelper: The Fabric fashionista of platform helpers!
 * <p>
 * This class tells the rest of the mod when it's running on Fabric, and how to play nice with other mods in the Fabric ecosystem.
 * </p>
 * <p>
 * Why does this matter? Because every platform wants to feel special, and Fabric is no exception.
 * </p>
 */
public class FabricPlatformHelper implements IPlatformHelper {
    /**
     * Returns the name of the platform. Spoiler: It's always "Fabric" here.
     */
    @Override
    public String getPlatformName() {
        return "Fabric";
    }

    /**
     * Checks if a mod is loaded in the Fabric universe. Because friends are important!
     */
    @Override
    public boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    /**
     * Are we in a development environment? (Or is this the real deal?)
     */
    @Override
    public boolean isDevelopmentEnvironment() {
        return FabricLoader.getInstance().isDevelopmentEnvironment();
    }
    
    @Override
    public java.util.Map<String, Integer> getEnchantments(ItemStack stack) {
        // Use common reflection fallback to avoid mapping-specific compile issues
        try {
            java.util.Map<String, Integer> res = com.fastharvester.util.ReflectionUtils.readEnchantmentsFromStack(stack);
            return (res == null) ? java.util.Collections.emptyMap() : res;
        } catch (Throwable t) {
            return java.util.Collections.emptyMap();
        }
    }

    @Override
    public java.util.List<ItemStack> getBlockDrops(Level level, BlockPos pos, BlockState state, ItemStack tool) {
        if (level == null || state == null) return java.util.Collections.emptyList();
        try {
            java.lang.reflect.Method[] methods = state.getClass().getMethods();
            for (java.lang.reflect.Method m : methods) {
                if (!"getDrops".equals(m.getName())) continue;
                Class<?>[] params = m.getParameterTypes();
                Object res = null;
                try {
                    if (params.length == 2) {
                        // try (Level, BlockPos)
                        try { res = m.invoke(state, level, pos); }
                        catch (Throwable t) { /* ignore */ }
                    }
                    if (res == null && params.length == 3) {
                        // try (Level, BlockPos, BlockEntity) with null
                        try { res = m.invoke(state, level, pos, (Object) null); } catch (Throwable t1) { /* ignore */ }
                        if (res == null) {
                            // try (Level, BlockPos, ItemStack)
                            try { res = m.invoke(state, level, pos, tool); } catch (Throwable t2) { /* ignore */ }
                        }
                        if (res == null) {
                            // try (Level, BlockPos, Random) using level.getRandom()
                            try { res = m.invoke(state, level, pos, level.getRandom()); } catch (Throwable t3) { /* ignore */ }
                        }
                    }
                } catch (Throwable ignored) {
                    continue;
                }
                if (res instanceof java.util.List) {
                    try {
                        @SuppressWarnings("unchecked")
                        java.util.List<ItemStack> asList = (java.util.List<ItemStack>) res;
                        return asList;
                    } catch (Throwable t) {
                        return java.util.Collections.emptyList();
                    }
                }
            }
        } catch (Throwable t) {
            // ignore and fallback
        }
        return java.util.Collections.emptyList();
    }
    
}
