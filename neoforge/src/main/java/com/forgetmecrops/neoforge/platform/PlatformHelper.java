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

public class PlatformHelper implements IPlatformHelper {
    /** Public constructor required by service loading. */
    public PlatformHelper() {}

    @Override
    public String getPlatformName() { return "NeoForge"; }

    @Override
    public boolean isModLoaded(String modId) { return ModList.get().isLoaded(modId); }

    @Override
    public boolean isDevelopmentEnvironment() { return !FMLLoader.getCurrent().isProduction(); }

    @Override
    public java.util.Map<String, Integer> getEnchantments(net.minecraft.world.item.ItemStack stack) {
        return com.forgetmecrops.platform.PlatformReflective.extractEnchantments(stack);
    }

    @Override
    public java.util.List<net.minecraft.world.item.ItemStack> getBlockDrops(net.minecraft.world.level.Level level,
            net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state,
            net.minecraft.world.item.ItemStack tool) {
        return com.forgetmecrops.platform.PlatformReflective.getBlockDropsReflective(level, pos, state, tool);
    }

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
