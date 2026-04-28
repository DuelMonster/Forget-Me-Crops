package com.fastharvester.neoforge.platform;

// NeoForge-specific platform helper moved into the neoforge module namespace.

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.phys.AABB;
import com.fastharvester.platform.services.IPlatformHelper;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLLoader;
import com.fastharvester.util.log.LogUtils;
import net.minecraft.world.level.block.entity.BlockEntity;

public class NeoForgePlatformHelper implements IPlatformHelper {
    public NeoForgePlatformHelper() {}

    @Override
    public String getPlatformName() { return "NeoForge"; }

    @Override
    public boolean isModLoaded(String modId) { return ModList.get().isLoaded(modId); }

    @Override
    public boolean isDevelopmentEnvironment() { return !FMLLoader.getCurrent().isProduction(); }

    @Override
    public java.util.Map<String, Integer> getEnchantments(net.minecraft.world.item.ItemStack stack) {
        return com.fastharvester.platform.PlatformReflective.extractEnchantments(stack);
    }

    @Override
    public java.util.List<net.minecraft.world.item.ItemStack> getBlockDrops(net.minecraft.world.level.Level level,
            net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state,
            net.minecraft.world.item.ItemStack tool) {
        return com.fastharvester.platform.PlatformReflective.getBlockDropsReflective(level, pos, state, tool);
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
            } catch (Throwable ignored) {}

            BlockEntity be = level.getBlockEntity(pos);
            if (be == null) return;
            try {
                boolean wrote = com.fastharvester.platform.adapter.FastItemFrameAdapterImpl.writeItemToBE(be, stack == null ? ItemStack.EMPTY : stack.copy());
                if (wrote) {
                    try { LogUtils.logDebug("[PLATFORM] updateFrameItem: adapter wrote item to BE {} at {}", be.getClass().getName(), pos); } catch (Throwable ignored) {}
                    try { level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), 3); } catch (Throwable ignored) {}
                    return;
                }
            } catch (Throwable t) { try { LogUtils.logDebug("[PLATFORM] updateFrameItem: adapter writeItemToBE failed: {}", t.getMessage()); } catch (Throwable ignored) {} }

            try {
                boolean wrote = com.fastharvester.platform.PlatformReflective.reflectiveUpdateFrameItemFallback(level, pos, be, stack == null ? ItemStack.EMPTY : stack.copy());
                if (wrote) return;
            } catch (Throwable ignored) {}
        } catch (Throwable t) {
            LogUtils.logDebug("[PLATFORM] updateFrameItem failed at " + pos, t);
        }
    }
}
