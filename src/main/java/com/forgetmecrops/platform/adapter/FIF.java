package com.forgetmecrops.platform.adapter;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.item.ItemStack;

/**
 * FIF: The polite front door to the FastItemFrames integration!
 * <p>
 * FIF (FastItemFrames) is an optional companion mod that replaces vanilla item frames with
 * block-entity-backed versions. This façade delegates everything to FastItemFrameAdapterImpl
 * and wraps each call in a try/catch so that FIF-related explosions stay contained.
 * </p>
 * <p>
 * Why a façade? Because callers shouldn't need to worry about whether FIF is even loaded.
 * You just call FIF.doTheThing() and we'll handle the existential uncertainty quietly.
 * </p>
 */
public final class FIF {
    // Utility class. No FIF instances. The irony is not lost on us.
    private FIF() {}

    /**
     * Returns true if the given object is a FastItemFrame and contains a hoe above the given chest.
     * Swallows all exceptions — if FIF isn't loaded, this safely returns false and life goes on.
     *
     * @param frame the candidate frame object to inspect
     * @param chest the chest context (used by the adapter for validation)
     * @return true if the frame holds a hoe and is FIF-backed; false if not or if FIF is absent
     */
    public static boolean isItemFrameWithHoe(Object frame, Object chest) {
        try { return FastItemFrameAdapterImpl.INSTANCE.isItemFrameWithHoe(frame, chest); } catch (Throwable ignored) { return false; }
    }

    /**
     * Returns true if the given BlockEntity is a FastItemFrame block-entity.
     * Safe to call even when the FIF mod is not installed — returns false gracefully.
     *
     * @param be the block-entity to test
     * @return true if it's a FIF block-entity; false otherwise
     */
    public static boolean isFastItemFrameBlockEntity(BlockEntity be) {
        try { return FastItemFrameAdapterImpl.isFastItemFrameBlockEntity(be); } catch (Throwable ignored) { return false; }
    }

    /**
     * Extracts the item currently held by a FastItemFrame block-entity.
     * Returns null if FIF is absent or the extraction fails for any reason.
     *
     * @param be the FIF block-entity to query
     * @return the held ItemStack, or null if extraction failed
     */
    public static ItemStack extractHeldItem(BlockEntity be) {
        try { return FastItemFrameAdapterImpl.extractHeldItem(be); } catch (Throwable ignored) { return null; }
    }

    /**
     * Returns the current rotation value (0-7) of a FastItemFrame block-entity.
     * Defaults to 0 on failure — rotation state is cosmetic enough that a silent fallback is fine.
     *
     * @param be the FIF block-entity to query
     * @return the rotation value, or 0 if FIF is absent or an error occurred
     */
    public static int getRotation(BlockEntity be) {
        try { return FastItemFrameAdapterImpl.getRotation(be); } catch (Throwable ignored) { return 0; }
    }

    /**
     * Sets the rotation of a FastItemFrame block-entity and reports whether the write actually stuck.
     * Silent no-ops are how cosmetic bugs become ghost stories, so this method tells callers whether
     * the adapter believes the rotation really landed.
     *
     * @param be          the FIF block-entity to update
     * @param newRotation the new rotation value to apply (0-7)
     * @return true if the adapter applied or verified the rotation; false otherwise
     */
    public static boolean setRotation(BlockEntity be, int newRotation) {
        try { return FastItemFrameAdapterImpl.setRotation(be, newRotation); } catch (Throwable ignored) { return false; }
    }

    /**
     * Writes an ItemStack to a FastItemFrame block-entity's held-item slot.
     * Returns false and stays quiet if FIF is absent or the write fails.
     *
     * @param be    the FIF block-entity to update
     * @param stack the ItemStack to write into the frame
     * @return true if the write succeeded; false otherwise
     */
    public static boolean writeItemToBE(BlockEntity be, ItemStack stack) {
        try { return FastItemFrameAdapterImpl.writeItemToBE(be, stack); } catch (Throwable ignored) { return false; }
    }
}
