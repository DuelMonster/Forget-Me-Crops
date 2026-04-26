package com.fastharvester.platform;

// 🤝 NeoForgePlatformHelper: bridge-builder and polite translator between mod logic and NeoForge quirks.
// It smiles, mediates, and sometimes uses reflection when feeling brave.

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.phys.AABB;
import com.fastharvester.platform.services.IPlatformHelper;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLLoader;
import net.minecraft.server.level.ServerLevel;
import com.fastharvester.Constants;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

/**
 * NeoForgePlatformHelper: The futuristic friend of platform helpers!
 * <p>
 * This class helps the mod know when it's running on NeoForge, and how to check
 * for other mods in the NeoForge universe.
 * </p>
 * <p>
 * Why does this matter? Because NeoForge is the new kid on the block, and it
 * wants to be noticed.
 * </p>
 */
public class NeoForgePlatformHelper implements IPlatformHelper {
    /** Public no-arg constructor. */
    public NeoForgePlatformHelper() {}
    /**
     * Returns the name of the platform. (Spoiler: It's "NeoForge"!)
     */
    @Override
    public String getPlatformName() {
        return "NeoForge";
    }

    /**
     * Checks if a mod is loaded in the NeoForge world. Because even the future
     * needs friends.
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

    /**
     * Extract enchantments from an ItemStack via reflection where necessary.
     * Humanized aside: we peek under the hood to see what spell levels are present.
     */
    @Override
    @SuppressWarnings({ "rawtypes" })
    public java.util.Map<String, Integer> getEnchantments(net.minecraft.world.item.ItemStack stack) {
        return com.fastharvester.platform.PlatformReflective.extractEnchantments(stack);
    }

    /**
     * Compute block drops using NeoForge's loot APIs when possible; otherwise fall
     * back to reflection.
     * Emotional aside: we do this so blocks give sensible loot and your chests stay
     * trustworthy.
     */
    @Override
    public java.util.List<net.minecraft.world.item.ItemStack> getBlockDrops(net.minecraft.world.level.Level level,
            net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state,
            net.minecraft.world.item.ItemStack tool) {
        return com.fastharvester.platform.PlatformReflective.getBlockDropsReflective(level, pos, state, tool);
    }

    /**
     * Persist a frame-held item at the given position. Handles vanilla ItemFrame
     * entities
     * and attempts reflective setters for FastItemFrames block-entities. Marks
     * block-entity
     * changed and requests a block update on the server when possible.
     */
    @SuppressWarnings("null")
    @Override
    public void updateFrameItem(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos,
            ItemStack stack) {
        if (level == null || pos == null)
            return;
        try {
            // Try vanilla ItemFrame entity first
            try {
                java.util.List<ItemFrame> frames = level.getEntitiesOfClass(ItemFrame.class,
                        new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1),
                        e -> true);
                if (!frames.isEmpty()) {
                    ItemFrame frame = frames.get(0);
                    frame.setItem(stack == null ? ItemStack.EMPTY : stack.copy());
                    return;
                }
            } catch (Throwable ignored) {
            }

            // Try FastItemFrames block-entity or other custom frames via the adapter (preferred)
            BlockEntity be = level.getBlockEntity(pos);
            if (be == null)
                return;
            try {
                boolean wrote = com.fastharvester.platform.adapter.FastItemFrameAdapterImpl.writeItemToBE(be, stack == null ? ItemStack.EMPTY : stack.copy());
                if (wrote) {
                    try { Constants.logDebug("[PLATFORM] updateFrameItem: adapter wrote item to BE {} at {}", be.getClass().getName(), pos); } catch (Throwable ignored) {}
                    try { level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), 3); } catch (Throwable ignored) {}
                    return;
                } else {
                    try { Constants.logDebug("[PLATFORM] updateFrameItem: adapter did not find a write path for BE {} at {}", be.getClass().getName(), pos); } catch (Throwable ignored) {}
                }
            } catch (Throwable t) {
                try { Constants.logDebug("[PLATFORM] updateFrameItem: adapter writeItemToBE failed: {}", t.getMessage()); } catch (Throwable ignored) {}
            }

            // Fallback: use shared reflective helper for BE method/field writes
            try {
                boolean wrote = com.fastharvester.platform.PlatformReflective.reflectiveUpdateFrameItemFallback(level, pos, be, stack == null ? ItemStack.EMPTY : stack.copy());
                if (wrote) return;
            } catch (Throwable ignored) {}
        } catch (Throwable t) {
            Constants.logDebug("[PLATFORM] updateFrameItem failed at " + pos, t);
        }
    }
}
