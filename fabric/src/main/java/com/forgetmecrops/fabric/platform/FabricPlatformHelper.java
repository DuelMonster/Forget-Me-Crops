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

public class FabricPlatformHelper implements IPlatformHelper {
    /** Public constructor required by service loading. */
    public FabricPlatformHelper() {}

    @Override
    public String getPlatformName() { return "Fabric"; }

    @Override
    public boolean isModLoaded(String modId) { return FabricLoader.getInstance().isModLoaded(modId); }

    @Override
    public boolean isDevelopmentEnvironment() { return FabricLoader.getInstance().isDevelopmentEnvironment(); }

    @Override
    public java.util.Map<String, Integer> getEnchantments(ItemStack stack) { return com.forgetmecrops.platform.PlatformReflective.extractEnchantments(stack); }

    @Override
    public java.util.List<ItemStack> getBlockDrops(Level level, BlockPos pos, BlockState state, ItemStack tool) {
        return com.forgetmecrops.platform.PlatformReflective.getBlockDropsReflective(level, pos, state, tool);
    }

    @Override
    public void updateFrameItem(Level level, BlockPos pos, ItemStack stack) {
        if (level == null || pos == null) return;
        try {
            try { LogUtils.logDebug("[PLATFORM] updateFrameItem called: pos={} incomingItem={} damage={}", pos, stack == null ? "<null>" : stack.getItem(), stack == null ? -1 : stack.getDamageValue()); } catch (Exception e) { LogUtils.logTrace("[PLATFORM] Debug log emit failed", e); }
            try {
                java.util.List<ItemFrame> frames = level.getEntitiesOfClass(ItemFrame.class,
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
