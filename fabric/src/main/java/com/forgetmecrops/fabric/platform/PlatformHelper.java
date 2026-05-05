package com.forgetmecrops.fabric.platform;

import com.forgetmecrops.platform.services.IPlatformHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import com.forgetmecrops.util.log.LogUtils;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.phys.AABB;

/**
 * PlatformHelper (Fabric): Forget-Me-Crops' Fabric-flavored platform service implementation!
 * <p>
 * Implements {@link com.forgetmecrops.platform.services.IPlatformHelper} for the Fabric loader,
 * delegating to FabricLoader APIs for mod detection and dev-environment checks, and to
 * PlatformReflective for enchantment extraction and block-drop calculations.
 * </p>
 * <p>
 * The critical frame-item update path tries vanilla ItemFrame entities first,
 * then falls back to the FastItemFrames block-entity adapter, then to full reflection.
 * In that order. With that much try/catch. It will be fine.
 * </p>
 */
public class PlatformHelper implements IPlatformHelper {
    /** Public constructor required by the Java ServiceLoader mechanism. */
    public PlatformHelper() {}

    /**
     * Returns the name of the current platform: \"Fabric\".
     * Used for debug logging and platform identification.
     *
     * @return \"Fabric\"
     */
    @Override
    public String getPlatformName() { return "Fabric"; }

    /**
     * Checks if a mod is loaded in the current Fabric environment.
     * Delegates to FabricLoader's mod-detection system.
     *
     * @param modId the mod ID to check for (e.g. "cloth-config", "forgetmecrops")
     * @return true if the mod is loaded; false otherwise
     */
    @Override
    public boolean isModLoaded(String modId) { return FabricLoader.getInstance().isModLoaded(modId); }

    /**
     * Checks whether the current runtime is a development (non-production) environment.
     * Used to gate debug logging and other developer-only features.
     *
     * @return true if Fabric is running in dev mode; false if production
     */
    @Override
    public boolean isDevelopmentEnvironment() { return FabricLoader.getInstance().isDevelopmentEnvironment(); }

    /**
     * Extracts enchantments from an ItemStack as a {@code Map<String, Integer>}
     * keyed by description ID. Delegates to the platform-agnostic reflective helper.
     *
     * @param stack the ItemStack to read enchantments from
     * @return map of enchantment IDs to levels; empty map if none
     */
    @Override
    public java.util.Map<String, Integer> getEnchantments(ItemStack stack) { return com.forgetmecrops.platform.PlatformReflective.extractEnchantments(stack); }

    /**
     * Calculates block drops (loot) for the given block and tool context.
     * Uses the reflective LootContext API to remain compatible across versions.
     * Fortune and Silk Touch effects are properly applied to the tool-dependent calculation.
     *
     * @param level the Level (must be ServerLevel for correct results)
     * @param pos   the block position
     * @param state the block state being harvested
     * @param tool  the tool ItemStack (tool enchantments affect the drop table)
     * @return list of ItemStack drops; empty if loot context build fails
     */
    @Override
    public java.util.List<ItemStack> getBlockDrops(Level level, BlockPos pos, BlockState state, ItemStack tool) {
        return com.forgetmecrops.platform.PlatformReflective.getBlockDropsReflective(level, pos, state, tool);
    }

    /**
     * Updates the item held by a frame entity (vanilla ItemFrame) or FIF block-entity
     * at the given position. Tries three paths in order: vanilla ItemFrame setter,
     * FIF block-entity adapter, then reflective fallback. Wraps everything in try/catch
     * because sometimes frames are ornery.
     *
     * @param level the Level context
     * @param pos   the frame position
     * @param stack the ItemStack to place in the frame (empty to clear)
     */
    @Override
    @SuppressWarnings("null") // List.get() return is not annotated @NonNull, but the list is non-empty at this call site
    public void updateFrameItem(Level level, BlockPos pos, ItemStack stack) {
        if (level == null || pos == null) return;
        try {
            try { LogUtils.logDebug("[PLATFORM] updateFrameItem called: pos={} incomingItem={} damage={}", pos, stack == null ? "<null>" : stack.getItem(), stack == null ? -1 : stack.getDamageValue()); } catch (Exception e) { LogUtils.logTrace("[PLATFORM] Debug log emit failed", e); }
            try {
                var frames = level.getEntitiesOfClass(ItemFrame.class,
                        new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1), e -> true);
                if (!frames.isEmpty()) {
                    ItemFrame frame = frames.get(0);
                    frame.setItem(stack == null ? ItemStack.EMPTY : stack.copy());
                    try { LogUtils.logDebug("[PLATFORM] updateFrameItem: wrote to vanilla ItemFrame: item={} damage={}", stack == null ? "<null>" : stack.getItem(), stack == null ? -1 : stack.getDamageValue()); } catch (Exception e) { LogUtils.logTrace("[PLATFORM] Debug log emit failed", e); }
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
        } catch (Exception t) { LogUtils.logDebug("[PLATFORM] updateFrameItem failed at " + pos, t); }
    }
}
