package com.fastharvester.platform.adapter;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Small façade and centralisation point for FastItemFrames adapter usage.
 * Delegates to `FastItemFrameAdapterImpl` and provides safe wrappers.
 */
public final class FIF {
    private FIF() {}

    public static boolean isItemFrameWithHoe(Object frame, Object chest) {
        try { return FastItemFrameAdapterImpl.INSTANCE.isItemFrameWithHoe(frame, chest); } catch (Throwable ignored) { return false; }
    }

    public static boolean isFastItemFrameBlockEntity(BlockEntity be) {
        try { return FastItemFrameAdapterImpl.isFastItemFrameBlockEntity(be); } catch (Throwable ignored) { return false; }
    }

    public static ItemStack extractHeldItem(BlockEntity be) {
        try { return FastItemFrameAdapterImpl.extractHeldItem(be); } catch (Throwable ignored) { return null; }
    }

    public static int getRotation(BlockEntity be) {
        try { return FastItemFrameAdapterImpl.getRotation(be); } catch (Throwable ignored) { return 0; }
    }

    public static void setRotation(BlockEntity be, int newRotation) {
        try { FastItemFrameAdapterImpl.setRotation(be, newRotation); } catch (Throwable ignored) {}
    }

    public static boolean writeItemToBE(BlockEntity be, ItemStack stack) {
        try { return FastItemFrameAdapterImpl.writeItemToBE(be, stack); } catch (Throwable ignored) { return false; }
    }
}
