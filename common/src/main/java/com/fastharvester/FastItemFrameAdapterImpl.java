package com.fastharvester;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.Container;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Default implementation of FastItemFrame adapter helpers.
 *
 * Centralizes reflective access to FastItemFrames block-entities and provides
 * convenience helpers used across Fabric/NeoForge/common code paths.
 *
 * Heads-up: this class is intentionally defensive — FastItemFrames can
 * vary between versions and obfuscations, so we try a few reflective tricks
 * before giving up. Think of it as the code equivalent of "try the doorknob."
 */
public class FastItemFrameAdapterImpl implements FastItemFrameAdapter {

    /** Singleton instance for reflective FastItemFrames access. */
    public static final FastItemFrameAdapterImpl INSTANCE = new FastItemFrameAdapterImpl();

    /** Utility: prevent external instantiation (use INSTANCE) */
    private FastItemFrameAdapterImpl() {}

    /**
     * Check whether the given `frame` (vanilla `ItemFrame` or a FIF block-entity)
     * appears to be holding a hoe and is associated with a container `chest`.
     *
     * Returns true for likely anchors; false if the heuristics do not match.
     * @param frame The frame or block-entity to inspect.
     * @param chest The linked chest object to validate association.
     * @return true if the inspected frame appears to be an anchor holding a hoe.
     */
    @Override
    public boolean isItemFrameWithHoe(Object frame, Object chest) {
        try {
            if (frame == null || chest == null) return false;
            ItemStack held = null;
            if (frame instanceof ItemFrame) {
                held = ((ItemFrame) frame).getItem();
                try {
                    if (((ItemFrame) frame).getDirection() != net.minecraft.core.Direction.UP) return false;
                } catch (Throwable ignored) {}
            } else if (frame instanceof BlockEntity) {
                held = extractHeldItem((BlockEntity) frame);
            } else {
                // fallback: try reflectively
                for (Method m : frame.getClass().getMethods()) {
                    String name = m.getName().toLowerCase();
                    if ((name.contains("getdisplayed") || name.contains("getheld") || name.contains("getitem")) && m.getParameterCount() == 0) {
                        Object res = m.invoke(frame);
                        if (res instanceof ItemStack) { held = (ItemStack) res; break; }
                    }
                }
            }
            if (held == null || held.isEmpty()) return false;
            if (!(held.getItem() instanceof HoeItem)) return false;
            if (!(chest instanceof Container)) return false;
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Heuristic to detect FastItemFrames block-entities by classname.
     * This is a best-effort check and intentionally permissive.
     * @param be Block entity to test.
     * @return true when the block entity appears to be a FastItemFrames BE.
     */
    public static boolean isFastItemFrameBlockEntity(BlockEntity be) {
        if (be == null) return false;
        try {
            String cls = be.getClass().getName().toLowerCase();
            if (cls.contains("fastitemframes")) return true;
        } catch (Throwable ignored) {}
        return false;
    }

    /**
     * Extract the held `ItemStack` from a FastItemFrames block-entity using
     * a combination of reflective getter checks and common field names.
     *
     * Returns null if no held item could be extracted.
     * @param be The block entity to inspect.
     * @return the extracted ItemStack when present, or null if none.
     */
    public static ItemStack extractHeldItem(BlockEntity be) {
        if (be == null) return null;
        try {
            for (Method m : be.getClass().getMethods()) {
                String name = m.getName().toLowerCase();
                if ((name.contains("getdisplayed") || name.contains("getheld") || name.contains("getitem")) && m.getParameterCount() == 0) {
                    Object res = m.invoke(be);
                    if (res instanceof ItemStack) return (ItemStack) res;
                }
            }
            // Try common field names
            String[] fields = new String[]{"item", "displayedItem", "heldItem", "stack"};
            for (String fn : fields) {
                try {
                    Field fld = be.getClass().getDeclaredField(fn);
                    fld.setAccessible(true);
                    Object v = fld.get(be);
                    if (v instanceof ItemStack) return (ItemStack) v;
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        return null;
    }

    /**
     * Read rotation from a FastItemFrames block-entity via method or field.
     * Falls back to 0 on any error.
     * @param be The FastItemFrames block-entity to read.
     * @return rotation value (0-7), or 0 on error.
     */
    public static int getRotation(BlockEntity be) {
        if (be == null) return 0;
        try {
            for (Method m : be.getClass().getMethods()) {
                String name = m.getName().toLowerCase();
                if ((name.contains("get") || name.contains("getitem")) && name.contains("rotation") && m.getParameterCount() == 0) {
                    Object r = m.invoke(be);
                    if (r instanceof Number) return ((Number) r).intValue() & 7;
                }
            }
            try {
                Field fld = be.getClass().getDeclaredField("rotation");
                fld.setAccessible(true);
                Object v = fld.get(be);
                if (v instanceof Number) return ((Number) v).intValue() & 7;
            } catch (Throwable ignored) {}
        } catch (Throwable ignored) {}
        return 0;
    }

    /**
     * Set rotation on a FastItemFrames block-entity using setter methods or
     * by writing a `rotation` field if available. Best-effort; ignores errors.
     * @param be The block entity to modify.
     * @param newRotation Rotation value to set (0-7).
     */
    public static void setRotation(BlockEntity be, int newRotation) {
        if (be == null) return;
        try {
            for (Method m : be.getClass().getMethods()) {
                String name = m.getName().toLowerCase();
                if (name.contains("set") && name.contains("rotation") && m.getParameterCount() == 1) {
                    Class<?> p = m.getParameterTypes()[0];
                    if (p == int.class || p == Integer.class) { m.invoke(be, newRotation); try { be.setChanged(); } catch (Throwable ignored) {} return; }
                    if (p == byte.class || p == Byte.class) { m.invoke(be, (byte) newRotation); try { be.setChanged(); } catch (Throwable ignored) {} return; }
                }
            }
            try {
                Field fld = be.getClass().getDeclaredField("rotation");
                fld.setAccessible(true);
                fld.setInt(be, newRotation & 7);
                try { be.setChanged(); } catch (Throwable ignored) {}
                return;
            } catch (Throwable ignored) {}
        } catch (Throwable ignored) {}
    }
}
