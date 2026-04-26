package com.fastharvester.platform.adapter;

import com.fastharvester.FastItemFrameAdapter;
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
 */
public class FastItemFrameAdapterImpl implements FastItemFrameAdapter {

    /** Singleton instance for reflective FastItemFrames access. */
    public static final FastItemFrameAdapterImpl INSTANCE = new FastItemFrameAdapterImpl();

    /** Utility: prevent external instantiation (use INSTANCE) */
    private FastItemFrameAdapterImpl() {}

    // --- API-first reflective bindings (preferred if available) ---
    private static volatile boolean apiAvailable = false;
    private static Class<?> apiClass = null;
    private static java.lang.reflect.Method apiGetDisplayedItem = null;
    private static java.lang.reflect.Method apiGetRotation = null;
    private static java.lang.reflect.Method apiSetRotation = null;

    static {
        // Candidate FastItemFrames implementation class names to probe for an API-first path.
        String[] candidates = new String[] {
                "com.fuzs.fastitemframes.block.entity.FastItemFrameBlockEntity",
                "com.fuzs.fastitemframes.block.FastItemFrameBlockEntity",
                "com.fuzs.fastitemframes.FastItemFrameBlockEntity",
                "fastitemframes.block.entity.FastItemFrameBlockEntity",
                "fastitemframes.FastItemFrameBlockEntity",
                "com.fuzs.fastitemframes.blockentity.FastItemFrameBlockEntity"
        };
        for (String cn : candidates) {
            try {
                Class<?> cls = Class.forName(cn, false, FastItemFrameAdapterImpl.class.getClassLoader());
                if (cls != null) {
                    apiClass = cls;
                    // find a getter for the displayed/held ItemStack
                    for (java.lang.reflect.Method m : cls.getMethods()) {
                        String name = m.getName().toLowerCase();
                        if (m.getParameterCount() == 0 && net.minecraft.world.item.ItemStack.class.isAssignableFrom(m.getReturnType())) {
                            if (name.contains("display") || name.contains("held") || name.contains("getitem") || name.contains("getstack")) {
                                apiGetDisplayedItem = m;
                                break;
                            }
                        }
                    }
                    // find rotation getter
                    for (java.lang.reflect.Method m : cls.getMethods()) {
                        String name = m.getName().toLowerCase();
                        if (m.getParameterCount() == 0 && (name.contains("rotation") || name.contains("rot"))) {
                            if (Number.class.isAssignableFrom(m.getReturnType()) || m.getReturnType().isPrimitive()) {
                                apiGetRotation = m;
                                break;
                            }
                        }
                    }
                    // find rotation setter
                    for (java.lang.reflect.Method m : cls.getMethods()) {
                        String name = m.getName().toLowerCase();
                        if (m.getParameterCount() == 1 && name.contains("set") && name.contains("rotation")) {
                            apiSetRotation = m;
                            break;
                        }
                    }
                    apiAvailable = true;
                    try { com.fastharvester.Constants.logInfo("[FIF] Detected FastItemFrames API class: {}", cn); } catch (Throwable ignored) {}
                    break;
                }
            } catch (Throwable ignored) {
            }
        }
    }

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
            // API-first: if the runtime FastItemFrames class was found and this BE is an instance, prefer API methods
            if (apiAvailable && apiClass != null && apiClass.isInstance(frame)) {
                try {
                    if (apiGetDisplayedItem != null) {
                        Object res = apiGetDisplayedItem.invoke(frame);
                        if (res instanceof ItemStack) held = (ItemStack) res;
                    }
                } catch (Throwable ignored) {}
            }
            if (frame instanceof ItemFrame) {
                held = ((ItemFrame) frame).getItem();
                try {
                    if (((ItemFrame) frame).getDirection() != net.minecraft.core.Direction.UP) return false;
                } catch (Throwable ignored) {}
            } else if (frame instanceof BlockEntity) {
                if (held == null) held = extractHeldItem((BlockEntity) frame);
            } else {
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
            if (apiAvailable && apiClass != null) {
                if (apiClass.isInstance(be)) return true;
            }
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
            // API-first extraction
            if (apiAvailable && apiClass != null && apiClass.isInstance(be) && apiGetDisplayedItem != null) {
                try {
                    Object res = apiGetDisplayedItem.invoke(be);
                    if (res instanceof ItemStack) return (ItemStack) res;
                } catch (Throwable ignored) {}
            }
            for (Method m : be.getClass().getMethods()) {
                String name = m.getName().toLowerCase();
                if ((name.contains("getdisplayed") || name.contains("getheld") || name.contains("getitem")) && m.getParameterCount() == 0) {
                    Object res = m.invoke(be);
                    if (res instanceof ItemStack) return (ItemStack) res;
                }
            }
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
            // API-first getter
            if (apiAvailable && apiClass != null && apiClass.isInstance(be) && apiGetRotation != null) {
                try {
                    Object r = apiGetRotation.invoke(be);
                    if (r instanceof Number) return ((Number) r).intValue() & 7;
                } catch (Throwable ignored) {}
            }
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
            // API-first setter
            if (apiAvailable && apiClass != null && apiClass.isInstance(be) && apiSetRotation != null) {
                try {
                    Class<?> p = apiSetRotation.getParameterTypes()[0];
                    if (p == int.class || p == Integer.class) { apiSetRotation.invoke(be, newRotation); try { be.setChanged(); } catch (Throwable ignored) {} return; }
                    if (p == byte.class || p == Byte.class) { apiSetRotation.invoke(be, (byte) newRotation); try { be.setChanged(); } catch (Throwable ignored) {} return; }
                } catch (Throwable ignored) {}
            }
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
