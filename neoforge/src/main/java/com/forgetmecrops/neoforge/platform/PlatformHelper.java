package com.forgetmecrops.neoforge.platform;

// NeoForge-specific platform helper moved into the neoforge module namespace.

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.phys.AABB;
import com.forgetmecrops.platform.services.IPlatformHelper;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLLoader;
import com.forgetmecrops.util.log.LogUtils;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * PlatformHelper (NeoForge): Forget-Me-Crops' NeoForge-flavored platform service implementation!
 * <p>
 * Implements {@link com.forgetmecrops.platform.services.IPlatformHelper} for NeoForge, using
 * ModList and FMLLoader for runtime context checks, and delegating to PlatformReflective for
 * enchantment and block-drop queries.
 * </p>
 * <p>
 * The frame-item update strategy mirrors the Fabric version: try vanilla ItemFrame entities first,
 * then the FIF block-entity adapter, then reflective fallback. Always wrapped in try/catch.
 * Always politely. The frame will be updated, eventually.
 * </p>
 */
public class PlatformHelper implements IPlatformHelper {
    /** Public constructor required by the Java ServiceLoader mechanism. */
    public PlatformHelper() {}

    /**
     * Returns the name of the current platform: \"NeoForge\".
     * Used for debug logging and platform identification.
     *
     * @return \"NeoForge\"
     */
    @Override
    public String getPlatformName() { return "NeoForge"; }

    /**
     * Checks if a mod is loaded in the current NeoForge environment.
     * Uses ModList to query the mod loading state.
     *
     * @param modId the mod ID to check for
     * @return true if the mod is loaded; false otherwise
     */
    @Override
    public boolean isModLoaded(String modId) { return ModList.get().isLoaded(modId); }

    /**
     * Checks whether the current runtime is a development environment.
     * NeoForge's prod/dev distinction is inverted from Fabric's naming.
     *
     * @return true if not in production mode; false if production
     */
    @Override
    public boolean isDevelopmentEnvironment() { return !FMLLoader.getCurrent().isProduction(); }

    /**
     * Extracts enchantments from an ItemStack. Delegates to PlatformReflective
     * for loader-agnostic implementation.
     *
     * @param stack the ItemStack to read enchantments from
     * @return map of enchantment description IDs to levels; empty if none
     */
    @Override
    public java.util.Map<String, Integer> getEnchantments(net.minecraft.world.item.ItemStack stack) {
        return com.forgetmecrops.platform.PlatformReflective.extractEnchantments(stack);
    }

    /**
     * Calculates block drops using the reflective LootContext helper.
     * Properly applies tool enchantments (Fortune, Silk Touch, etc.) to the loot calculation.
     *
     * @param level the Level (ServerLevel for correct drops)
     * @param pos   the block position being harvested
     * @param state the block state
     * @param tool  the tool ItemStack affecting drop calculations
     * @return list of ItemStack drops; empty if loot calculation fails
     */
    @Override
    public java.util.List<net.minecraft.world.item.ItemStack> getBlockDrops(net.minecraft.world.level.Level level,
            net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state,
            net.minecraft.world.item.ItemStack tool) {
        return com.forgetmecrops.platform.PlatformReflective.getBlockDropsReflective(level, pos, state, tool);
    }

    /**
     * Updates a frame's held item via vanilla ItemFrame entity, FIF adapter, or reflective fallback.
     * The order of tries is: vanilla ItemFrame → FIF adapter → reflection. Robust, if verbose.
     *
     * @param level the Level context
     * @param pos   the frame position
     * @param stack the ItemStack to place in the frame
     */
    @Override
    public void updateFrameItem(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos,
            ItemStack stack) {
        if (level == null || pos == null) return;
        try {
            try {
                java.util.List<ItemFrame> frames = level.getEntitiesOfClass(ItemFrame.class,
                        new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1),
                        e -> true);
                if (!frames.isEmpty()) {
                    ItemFrame frame = frames.get(0);
                    frame.setItem(stack == null ? ItemStack.EMPTY : stack.copy());
                    return;
                }
            } catch (Exception e) { LogUtils.logTrace("[PLATFORM] Vanilla ItemFrame write path failed", e); }

            BlockEntity be = level.getBlockEntity(pos);
            if (be == null) return;
            try {
                boolean wrote = com.forgetmecrops.platform.adapter.FastItemFrameAdapterImpl.writeItemToBE(be, stack == null ? ItemStack.EMPTY : stack.copy());
                if (wrote) {
                    try { LogUtils.logDebug("[PLATFORM] updateFrameItem: adapter wrote item to BE {} at {}", be.getClass().getName(), pos); } catch (Exception e) { LogUtils.logTrace("[PLATFORM] Debug log emit failed", e); }
                    try { level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), 3); } catch (Exception e) { LogUtils.logTrace("[PLATFORM] sendBlockUpdated failed", e); }
                    return;
                }
            } catch (Exception t) { LogUtils.logDebug("[PLATFORM] updateFrameItem: adapter writeItemToBE failed: {}", t.getMessage()); }

            try {
                boolean wrote = com.forgetmecrops.platform.PlatformReflective.reflectiveUpdateFrameItemFallback(level, pos, be, stack == null ? ItemStack.EMPTY : stack.copy());
                if (wrote) return;
            } catch (Exception e) { LogUtils.logTrace("[PLATFORM] Reflective fallback update failed", e); }
        } catch (Exception t) {
            LogUtils.logDebug("[PLATFORM] updateFrameItem failed at " + pos, t);
        }
    }
}
