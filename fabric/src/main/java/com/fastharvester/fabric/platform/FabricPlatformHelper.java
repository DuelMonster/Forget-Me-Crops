package com.fastharvester.fabric.platform;

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

public class FabricPlatformHelper implements IPlatformHelper {
    public FabricPlatformHelper() {}

    @Override
    public String getPlatformName() { return "Fabric"; }

    @Override
    public boolean isModLoaded(String modId) { return FabricLoader.getInstance().isModLoaded(modId); }

    @Override
    public boolean isDevelopmentEnvironment() { return FabricLoader.getInstance().isDevelopmentEnvironment(); }

    @Override
    public java.util.Map<String, Integer> getEnchantments(ItemStack stack) { return com.fastharvester.platform.PlatformReflective.extractEnchantments(stack); }

    @Override
    public java.util.List<ItemStack> getBlockDrops(Level level, BlockPos pos, BlockState state, ItemStack tool) {
        return com.fastharvester.platform.PlatformReflective.getBlockDropsReflective(level, pos, state, tool);
    }

    @Override
    public void updateFrameItem(Level level, BlockPos pos, ItemStack stack) {
        if (level == null || pos == null) return;
        try {
            try { Constants.logDebug("[PLATFORM] updateFrameItem called: pos={} incomingItem={} damage={}", pos, stack == null ? "<null>" : stack.getItem(), stack == null ? -1 : stack.getDamageValue()); } catch (Throwable ignored) {}
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

            BlockEntity be = level.getBlockEntity(pos);
            if (be == null) return;
            try {
                boolean wrote = com.fastharvester.platform.adapter.FastItemFrameAdapterImpl.writeItemToBE(be, stack == null ? ItemStack.EMPTY : stack.copy());
                if (wrote) {
                    try { Constants.logDebug("[PLATFORM] updateFrameItem: adapter wrote item to BE {} at {}", be.getClass().getName(), pos); } catch (Throwable ignored) {}
                    try { level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), 3); } catch (Throwable ignored) {}
                    return;
                }
            } catch (Throwable t) { try { Constants.logDebug("[PLATFORM] updateFrameItem: adapter writeItemToBE failed: {}", t.getMessage()); } catch (Throwable ignored) {} }

            try {
                boolean wrote = com.fastharvester.platform.PlatformReflective.reflectiveUpdateFrameItemFallback(level, pos, be, stack == null ? ItemStack.EMPTY : stack.copy());
                if (wrote) return;
            } catch (Throwable ignored) {}
        } catch (Throwable t) { Constants.logDebug("[PLATFORM] updateFrameItem failed at " + pos, t); }
    }
}
