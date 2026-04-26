package com.fastharvester.platform;

// 🧭 FabricPlatformHelper: translates platform specifics into friendly instructions.
// It helps the mod keep calm and carry on across Fabric's APIs.

import com.fastharvester.platform.services.IPlatformHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import com.fastharvester.Constants;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.phys.AABB;

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
    /** Public no-arg constructor. */
    public FabricPlatformHelper() {}
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
    
    /**
     * Extract enchantments from an ItemStack and return them as a simple map.
     * Humanized aside: we pry into the stack to see what magical stickers it has.
     */
    @Override
    public java.util.Map<String, Integer> getEnchantments(ItemStack stack) {
        return com.fastharvester.platform.PlatformReflective.extractEnchantments(stack);
    }

    /**
     * Attempt to compute correct block drops using the platform's LootContext if possible.
     * Emotional aside: we try our best to produce realistic drops so players don't feel cheated.
     */
    @Override
    public java.util.List<ItemStack> getBlockDrops(Level level, BlockPos pos, BlockState state, ItemStack tool) {
        return com.fastharvester.platform.PlatformReflective.getBlockDropsReflective(level, pos, state, tool);
    }

    /**
     * Persist a frame-held item at the given position. Handles vanilla ItemFrame entities
     * and attempts reflective setters for FastItemFrames block-entities. Marks block-entity
     * changed and requests a block update on the server when possible.
     */
    @Override
    public void updateFrameItem(Level level, BlockPos pos, ItemStack stack) {
        if (level == null || pos == null) return;
        try {
            try { Constants.logDebug("[PLATFORM] updateFrameItem called: pos={} incomingItem={} damage={}", pos, stack == null ? "<null>" : stack.getItem(), stack == null ? -1 : stack.getDamageValue()); } catch (Throwable ignored) {}
            // Try vanilla ItemFrame entity first
            try {
                java.util.List<ItemFrame> frames = level.getEntitiesOfClass(ItemFrame.class,
                        new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1), e -> true);
                if (!frames.isEmpty()) {
                    ItemFrame frame = frames.get(0);
                    frame.setItem(stack == null ? ItemStack.EMPTY : stack.copy());
                    try { Constants.logDebug("[PLATFORM] updateFrameItem: wrote to vanilla ItemFrame: item={} damage={}", stack == null ? "<null>" : stack.getItem(), stack == null ? -1 : stack.getDamageValue()); } catch (Throwable ignored) {}
                    return;
                }
            } catch (Throwable ignored) {}

            // Try FastItemFrames block-entity or other custom frames via the adapter (preferred)
            BlockEntity be = level.getBlockEntity(pos);
            if (be == null) return;
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
