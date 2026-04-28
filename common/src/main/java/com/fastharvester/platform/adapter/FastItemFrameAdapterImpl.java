package com.fastharvester.platform.adapter;

import com.fastharvester.util.log.LogUtils;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.Container;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

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
    /** Whether a probe attempt has already been performed (successful or not). */
    private static volatile boolean probeAttempted = false;
    private static Class<?> apiClass = null;
    private static java.lang.reflect.Method apiGetDisplayedItem = null;
    private static java.lang.reflect.Method apiGetItems = null;
    private static java.lang.reflect.Method apiGetRotation = null;
    private static java.lang.reflect.Method apiSetRotation = null;
    private static java.lang.reflect.Method apiSetItem = null;
    private static java.lang.reflect.Method apiSetStack = null;
    private static java.lang.reflect.Method apiMarkUpdated = null;
    private static java.lang.reflect.Field apiItemsField = null;

    // API probe removed from static init to avoid heavy classloading during
    // chunk-load handlers. The adapter will fall back to heuristics and
    // perform any API-specific probing lazily when/if needed at runtime.

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
                    String name = m.getName().toLowerCase(Locale.ROOT);
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
        ensureApiProbed();
        if (be == null) return false;
        try {
            if (apiAvailable && apiClass != null) {
                if (apiClass.isInstance(be)) return true;
            }
            String cls = be.getClass().getName().toLowerCase(Locale.ROOT);
            if (cls.contains("fastitemframes")) return true;
        } catch (Throwable ignored) {}
        return false;
    }

    /**
     * Ensure we've attempted to discover and bind the FastItemFrames API reflectively.
     * This is a one-shot, lazy probe executed on first need to avoid heavy
     * classloading during init or chunk-load handlers. It caches discovered
     * classes and methods on success and logs an info-level message once.
     */
    private static void ensureApiProbed() {
        if (probeAttempted) return;
        synchronized (FastItemFrameAdapterImpl.class) {
            if (probeAttempted) return;
            probeAttempted = true;
            try {
                String[] candidates = new String[]{
                        "fuzs.fastitemframes.common.blockentity.FastItemFrameBlockEntity",
                        "fuzs.fastitemframes.common.blockentity.FastItemFrame",
                        "fuzs.fastitemframes.common.blockentity.FastItemFrameBlockEntityImpl",
                        "fuzs.fastitemframes.common.blockentity.ItemFrameBlockEntity"
                };
                ClassLoader loader = FastItemFrameAdapterImpl.class.getClassLoader();
                // Try explicit candidate FQCNs first.
                for (String cn : candidates) {
                    try {
                        Class<?> c = Class.forName(cn, false, loader);
                        if (c == null) continue;
                        apiClass = c;
                        // bind methods
                        bindApiMethods();
                        apiAvailable = true;
                        LogUtils.logInfo("[FIF] Detected FastItemFrames API class: {}", apiClass.getName());
                        return;
                    } catch (ClassNotFoundException ignored) {
                        // try next candidate
                    }
                }

                // If the explicit candidates didn't match, attempt a package-prefixed probe.
                String[] prefixes = new String[]{
                        "fuzs.fastitemframes",
                        "fuzs.fastitemframes.world.level.block.entity",
                        "fuzs.fastitemframes.world.level.blockentity",
                        "fuzs.fastitemframes.world.level",
                        "fuzs.fastitemframes.world",
                };
                String[] suffixes = new String[]{
                        "ItemFrameBlockEntity",
                        "FastItemFrameBlockEntity",
                        "FastItemFrame",
                        "ItemFrameBlockEntityImpl",
                        "FastItemFrameBlockEntityImpl",
                        "ItemFrameBE",
                        "FastItemFrameBE",
                        "ItemFrameBlockEntity"
                };
                for (String pre : prefixes) {
                    for (String suf : suffixes) {
                        String fq = pre + "." + suf;
                        try {
                            Class<?> c = Class.forName(fq, false, loader);
                            if (c == null) continue;
                            apiClass = c;
                            bindApiMethods();
                            apiAvailable = true;
                            LogUtils.logInfo("[FIF] Detected FastItemFrames API class via package probe: {}", apiClass.getName());
                            return;
                        } catch (ClassNotFoundException ignored) {
                            // try next
                        }
                    }
                }

                // nothing matched; keep probeAttempted=true to avoid repeated work
                LogUtils.logDebug("[FIF] FastItemFrames API not found during lazy probe.");
            } catch (Throwable t) {
                LogUtils.logDebug("[FIF] API probe failure", t);
            }
        }
    }

    /**
     * Bind commonly-named API methods on the discovered `apiClass`.
     * Best-effort: silences NoSuchMethod exceptions and proceeds gracefully.
     */
    private static void bindApiMethods() {
        if (apiClass == null) return;
        try {
            String[] getItemNames = new String[]{"getDisplayedItem", "getDisplayed", "getHeldItem", "getHeld", "getItem", "getItemStack"};
            for (String mname : getItemNames) {
                try { apiGetDisplayedItem = apiClass.getMethod(mname); break; } catch (NoSuchMethodException ignored) {}
            }
            // Try to locate a method that returns a List of items (getItems)
            for (Method m : apiClass.getMethods()) {
                try {
                    if (m.getParameterCount() == 0 && java.util.List.class.isAssignableFrom(m.getReturnType())) {
                        apiGetItems = m;
                        break;
                    }
                } catch (Throwable ignored) {}
            }
            String[] getRotNames = new String[]{"getRotation", "getRotationValue", "rotation", "getRot"};
            for (String mname : getRotNames) {
                try { apiGetRotation = apiClass.getMethod(mname); break; } catch (NoSuchMethodException ignored) {}
            }
            String[] setRotNames = new String[]{"setRotation", "setRotationValue", "setRot"};
            for (String mname : setRotNames) {
                for (Class<?> p : new Class<?>[]{int.class, Integer.class, byte.class, Byte.class}) {
                    try { apiSetRotation = apiClass.getMethod(mname, p); break; } catch (NoSuchMethodException ignored) {}
                }
                if (apiSetRotation != null) break;
            }

            // Search for setters and helpers similar to the original adapter
            for (Method method : apiClass.getMethods()) {
                try {
                    Class<?>[] params = method.getParameterTypes();
                    if (apiSetItem == null && ((params.length == 1 && net.minecraft.world.item.ItemStack.class.isAssignableFrom(params[0]))
                            || (params.length == 2 && net.minecraft.world.item.ItemStack.class.isAssignableFrom(params[0])
                            && (params[1] == boolean.class || params[1] == Boolean.class)))) {
                        String name = method.getName().toLowerCase(Locale.ROOT);
                        if (name.contains("set") && name.contains("item")) {
                            apiSetItem = method;
                        }
                    }

                    if (apiSetStack == null && params.length == 2 && method.getReturnType() == void.class
                            && (params[0] == int.class || params[0] == Integer.class)
                            && net.minecraft.world.item.ItemStack.class.isAssignableFrom(params[1])) {
                        apiSetStack = method;
                    }

                    if (apiMarkUpdated == null && params.length == 1 && method.getReturnType() == void.class) {
                        String p0 = params[0].getName();
                        if (p0.endsWith("ServerLevel") || p0.equals("net.minecraft.server.level.ServerLevel")) {
                            apiMarkUpdated = method;
                        }
                    }
                } catch (Throwable ignored) {}
            }

            // Try to find an 'items' field which may hold a List<ItemStack>
            try {
                java.lang.reflect.Field f = apiClass.getDeclaredField("items");
                if (f != null) {
                    f.setAccessible(true);
                    apiItemsField = f;
                }
            } catch (Throwable ignored) {}
        } catch (Throwable ignored) {}
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
        ensureApiProbed();
        if (be == null) return null;
        try {
            try { LogUtils.logDebug("[FIF] extractHeldItem: probing BE {} apiAvailable={}", be.getClass().getName(), apiAvailable); } catch (Throwable ignored) {}
            // API-first extraction
            if (apiAvailable && apiClass != null && apiClass.isInstance(be) && apiGetDisplayedItem != null) {
                try {
                    Object res = apiGetDisplayedItem.invoke(be);
                    if (res instanceof ItemStack) {
                        try { LogUtils.logDebug("[FIF] extractHeldItem: apiGetDisplayedItem returned item via {}", apiGetDisplayedItem.getName()); } catch (Throwable ignored) {}
                        return ((ItemStack) res).copy();
                    }
                } catch (Throwable ignored) {}
            }
            // If API exposes a list accessor, return the first slot if present
            if (apiAvailable && apiGetItems != null) {
                try {
                    Object items = apiGetItems.invoke(be);
                    if (items instanceof java.util.List<?> list && !list.isEmpty()) {
                            Object first = list.get(0);
                            if (first instanceof ItemStack) {
                                try { LogUtils.logDebug("[FIF] extractHeldItem: apiGetItems returned list size {}", list.size()); } catch (Throwable ignored) {}
                                return ((ItemStack) first).copy();
                            }
                        } else {
                            try { LogUtils.logDebug("[FIF] extractHeldItem: apiGetItems returned null/empty"); } catch (Throwable ignored) {}
                        }
                } catch (Throwable ignored) {}
            }

            // If the API class defines an 'items' field holding a list, try that
            if (apiItemsField != null) {
                try {
                    Object v = apiItemsField.get(be);
                    if (v instanceof java.util.List<?> lst && !lst.isEmpty()) {
                        Object first = lst.get(0);
                        if (first instanceof ItemStack) {
                            try { LogUtils.logDebug("[FIF] extractHeldItem: apiItemsField returned list size {}", lst.size()); } catch (Throwable ignored) {}
                            return ((ItemStack) first).copy();
                        }
                    } else {
                        try { LogUtils.logDebug("[FIF] extractHeldItem: apiItemsField returned null/empty"); } catch (Throwable ignored) {}
                    }
                } catch (Throwable ignored) {}
            }
            for (Method m : be.getClass().getMethods()) {
                String name = m.getName().toLowerCase(Locale.ROOT);
                if ((name.contains("getdisplayed") || name.contains("getheld") || name.contains("getitem")) && m.getParameterCount() == 0) {
                    try {
                        Object res = m.invoke(be);
                        if (res instanceof ItemStack) {
                            try { LogUtils.logDebug("[FIF] extractHeldItem: method {} returned ItemStack", m.getName()); } catch (Throwable ignored) {}
                            return ((ItemStack) res).copy();
                        }
                    } catch (Throwable ignored) {}
                }
            }
            String[] fields = new String[]{"item", "displayedItem", "heldItem", "stack", "items"};
            for (String fn : fields) {
                try {
                    Field fld = be.getClass().getDeclaredField(fn);
                    fld.setAccessible(true);
                    Object v = fld.get(be);
                    if (v instanceof ItemStack) {
                        try { LogUtils.logDebug("[FIF] extractHeldItem: field {} returned ItemStack", fn); } catch (Throwable ignored) {}
                        return ((ItemStack) v).copy();
                    }
                    if (v instanceof java.util.List<?> list && !list.isEmpty()) {
                        Object first = list.get(0);
                        if (first instanceof ItemStack) {
                            try { LogUtils.logDebug("[FIF] extractHeldItem: field {} returned list size {}", fn, list.size()); } catch (Throwable ignored) {}
                            return ((ItemStack) first).copy();
                        }
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        try { LogUtils.logDebug("[FIF] extractHeldItem: no held item found for BE {}", be == null ? "null" : be.getClass().getName()); } catch (Throwable ignored) {}
        return null;
    }

    /**
     * Read rotation from a FastItemFrames block-entity via method or field.
     * Falls back to 0 on any error.
     * @param be The FastItemFrames block-entity to read.
     * @return rotation value (0-7), or 0 on error.
     */
    public static int getRotation(BlockEntity be) {
        ensureApiProbed();
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
                String name = m.getName().toLowerCase(Locale.ROOT);
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
        ensureApiProbed();
        if (be == null) return;
        try {
            try { LogUtils.logDebug("[FIF] setRotation: entry be={} requestedRot={} apiAvailable={} apiSetRotation={}", be == null ? "null" : be.getClass().getName(), newRotation & 7, apiAvailable, apiSetRotation == null ? "<none>" : apiSetRotation.getName()); } catch (Throwable ignored) {}
            // API-first setter
            if (apiAvailable && apiClass != null && apiClass.isInstance(be) && apiSetRotation != null) {
                try {
                    Class<?> p = apiSetRotation.getParameterTypes()[0];
                    if (p == int.class || p == Integer.class) {
                        apiSetRotation.invoke(be, newRotation);
                        try { be.setChanged(); } catch (Throwable ignored) {}
                        try { invokeApiMarkUpdatedIfPresent(be); } catch (Throwable ignored) {}
                        try { int rb = getRotation(be); LogUtils.logDebug("[FIF] setRotation: apiSetRotation(int) applied on {} -> requested={} readBack={}", be.getClass().getName(), newRotation & 7, rb); } catch (Throwable ignored) {}
                        return;
                    }
                    if (p == byte.class || p == Byte.class) {
                        apiSetRotation.invoke(be, (byte) newRotation);
                        try { be.setChanged(); } catch (Throwable ignored) {}
                        try { invokeApiMarkUpdatedIfPresent(be); } catch (Throwable ignored) {}
                        try { int rb = getRotation(be); LogUtils.logDebug("[FIF] setRotation: apiSetRotation(byte) applied on {} -> requested={} readBack={}", be.getClass().getName(), newRotation & 7, rb); } catch (Throwable ignored) {}
                        return;
                    }
                } catch (Throwable ignored) {}
            }

            for (Method m : be.getClass().getMethods()) {
                String name = m.getName().toLowerCase(Locale.ROOT);
                if (name.contains("set") && name.contains("rotation") && m.getParameterCount() == 1) {
                    Class<?> p = m.getParameterTypes()[0];
                    try {
                        if (p == int.class || p == Integer.class) {
                            m.invoke(be, newRotation);
                            try { be.setChanged(); } catch (Throwable ignored) {}
                            try { invokeApiMarkUpdatedIfPresent(be); } catch (Throwable ignored) {}
                            try { int rb = getRotation(be); LogUtils.logDebug("[FIF] setRotation: reflected method {}(int) applied on {} -> requested={} readBack={}", m.getName(), be.getClass().getName(), newRotation & 7, rb); } catch (Throwable ignored) {}
                            return;
                        }
                        if (p == byte.class || p == Byte.class) {
                            m.invoke(be, (byte) newRotation);
                            try { be.setChanged(); } catch (Throwable ignored) {}
                            try { invokeApiMarkUpdatedIfPresent(be); } catch (Throwable ignored) {}
                            try { int rb = getRotation(be); LogUtils.logDebug("[FIF] setRotation: reflected method {}(byte) applied on {} -> requested={} readBack={}", m.getName(), be.getClass().getName(), newRotation & 7, rb); } catch (Throwable ignored) {}
                            return;
                        }
                    } catch (Throwable ignored) {}
                }
            }

            try {
                Field fld = be.getClass().getDeclaredField("rotation");
                fld.setAccessible(true);
                fld.setInt(be, newRotation & 7);
                try { be.setChanged(); } catch (Throwable ignored) {}
                try { invokeApiMarkUpdatedIfPresent(be); } catch (Throwable ignored) {}
                try { int rb = getRotation(be); LogUtils.logDebug("[FIF] setRotation: wrote 'rotation' field on {} -> requested={} readBack={}", be.getClass().getName(), newRotation & 7, rb); } catch (Throwable ignored) {}
                return;
            } catch (Throwable ignored) {}

            // Block-state fallback: update block-state rotation property when BE setters/fields are not available.
            try {
                Object levelObj = null;
                try {
                    Method gm = be.getClass().getMethod("getLevel");
                    if (gm != null) levelObj = gm.invoke(be);
                } catch (Throwable ignored) {}
                if (levelObj == null) {
                    try {
                        Field lf = be.getClass().getDeclaredField("level");
                        lf.setAccessible(true);
                        levelObj = lf.get(be);
                    } catch (Throwable ignored) {}
                }
                if (levelObj instanceof Level level) {
                    try {
                        BlockPos pos = null;
                        try {
                            Method gp = be.getClass().getMethod("getBlockPos");
                            if (gp != null) pos = (BlockPos) gp.invoke(be);
                        } catch (Throwable ignored) {}
                        if (pos == null) {
                            try {
                                Field pf = be.getClass().getDeclaredField("pos");
                                pf.setAccessible(true);
                                Object pval = pf.get(be);
                                if (pval instanceof BlockPos) pos = (BlockPos)pval;
                            } catch (Throwable ignored) {}
                        }
                        if (pos != null) {
                            BlockState state = level.getBlockState(pos);
                            try {
                                for (Property<?> prop : state.getProperties()) {
                                    String name = prop.getName().toLowerCase(Locale.ROOT);
                                    if (!(name.contains("rotation") || name.contains("rot"))) continue;
                                    Collection<?> values = prop.getPossibleValues();
                                    Object chosen = null;
                                    for (Object v : values) {
                                        if (v instanceof Number && ((Number)v).intValue() == (newRotation & 7)) { chosen = v; break; }
                                        if (v.toString().equalsIgnoreCase(String.valueOf(newRotation & 7))) { chosen = v; break; }
                                    }
                                    if (chosen != null) {
                                        @SuppressWarnings({ "rawtypes", "unchecked" })
                                        BlockState ns = state.setValue((Property) prop, (Comparable) chosen);
                                        level.setBlock(pos, ns, 3);
                                        try { be.setChanged(); } catch (Throwable ignored) {}
                                        try { invokeApiMarkUpdatedIfPresent(be); } catch (Throwable ignored) {}
                                        try { LogUtils.logDebug("[FIF] setRotation via block-state property {} -> {} on {}", prop.getName(), chosen, be.getClass().getName()); } catch (Throwable ignored) {}
                                        return;
                                    } else {
                                        List<?> list = new java.util.ArrayList<>(values);
                                        if (!list.isEmpty() && list.get(0) instanceof Number) {
                                            int idx = (newRotation & 7) % list.size();
                                            Object pick = list.get(idx);
                                            @SuppressWarnings({ "rawtypes", "unchecked" })
                                            BlockState ns = state.setValue((Property) prop, (Comparable) pick);
                                            level.setBlock(pos, ns, 3);
                                            try { be.setChanged(); } catch (Throwable ignored) {}
                                            try { invokeApiMarkUpdatedIfPresent(be); } catch (Throwable ignored) {}
                                            try { LogUtils.logDebug("[FIF] setRotation via block-state property {} -> {} (index {}) on {}", prop.getName(), pick, idx, be.getClass().getName()); } catch (Throwable ignored) {}
                                            return;
                                        }
                                    }
                                }
                            } catch (Throwable ignored) {}
                        }
                    } catch (Throwable ignored) {}
                }
            } catch (Throwable t) {
                try { LogUtils.logDebug("[FIF] setRotation block-state fallback failed: {}", t.getMessage()); } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
    }

    private static void invokeApiMarkUpdatedIfPresent(BlockEntity be) {
            if (apiMarkUpdated == null || be == null) {
            try { LogUtils.logDebug("[FIF] invokeApiMarkUpdatedIfPresent: no apiMarkUpdated or be=null (apiMarkUpdated={} be={})", apiMarkUpdated, be == null ? "null" : be.getClass().getName()); } catch (Throwable ignored) {}
            return;
        }
        try {
            Object levelObj = null;
            try {
                Method gm = be.getClass().getMethod("getLevel");
                if (gm != null) levelObj = gm.invoke(be);
            } catch (Throwable ignored) {}

            if (levelObj == null) {
                try {
                    Field lf = be.getClass().getDeclaredField("level");
                    lf.setAccessible(true);
                    levelObj = lf.get(be);
                } catch (Throwable ignored) {}
            }

            if (levelObj != null) {
                try {
                    try { LogUtils.logDebug("[FIF] invokeApiMarkUpdatedIfPresent: invoking apiMarkUpdated on {} with level {}", be.getClass().getName(), levelObj == null ? "null" : levelObj.getClass().getName()); } catch (Throwable ignored) {}
                        apiMarkUpdated.invoke(be, levelObj);
                        try { LogUtils.logDebug("[FIF] invokeApiMarkUpdatedIfPresent: apiMarkUpdated invoked successfully for {}", be.getClass().getName()); } catch (Throwable ignored) {}
                } catch (Throwable t) {
                        try { LogUtils.logDebug("[FIF] invokeApiMarkUpdatedIfPresent invocation failed: {}", t.getMessage()); } catch (Throwable ignored) {}
                }
            } else {
                    try { LogUtils.logDebug("[FIF] invokeApiMarkUpdatedIfPresent: could not resolve level object for BE {}", be.getClass().getName()); } catch (Throwable ignored) {}
            }
        } catch (Throwable t) {
                try { LogUtils.logDebug("[FIF] invokeApiMarkUpdatedIfPresent failed: {}", t.getMessage()); } catch (Throwable ignored) {}
        }
    }

    /**
     * Attempt to write an ItemStack into a FastItemFrames block-entity using API-first
     * methods and reflective fallbacks. Returns true on success.
     */
    @SuppressWarnings("null")
    public static boolean writeItemToBE(BlockEntity be, ItemStack stack) {
        ensureApiProbed();
        if (be == null) return false;
        try {
            try { LogUtils.logDebug("[FIF] writeItemToBE: attempting write to BE {} with item={} damage={}", be.getClass().getName(), stack == null ? "<null>" : stack.getItem(), stack == null ? -1 : stack.getDamageValue()); } catch (Throwable ignored) {}

            // API-first
            if (apiAvailable && apiClass != null && apiClass.isInstance(be)) {
                if (apiSetItem != null) {
                    try {
                        apiSetItem.setAccessible(true);
                        apiSetItem.invoke(be, stack == null ? ItemStack.EMPTY : stack.copy());
                        try { be.setChanged(); } catch (Throwable ignored) {}
                        try { invokeApiMarkUpdatedIfPresent(be); } catch (Throwable ignored) {}
                        try { LogUtils.logDebug("[FIF] writeItemToBE: wrote via apiSetItem on {}", be.getClass().getName()); } catch (Throwable ignored) {}
                        return true;
                    } catch (Throwable t) {
                        try { LogUtils.logDebug("[FIF] writeItemToBE: apiSetItem invocation failed: {}", t.getMessage()); } catch (Throwable ignored) {}
                    }
                }
                if (apiSetStack != null) {
                    try {
                        Class<?>[] pts = apiSetStack.getParameterTypes();
                        if (pts.length == 2 && (pts[0] == int.class || pts[0] == Integer.class)) {
                            apiSetStack.setAccessible(true);
                            apiSetStack.invoke(be, Integer.valueOf(0), stack == null ? ItemStack.EMPTY : stack.copy());
                            try { be.setChanged(); } catch (Throwable ignored) {}
                            try { invokeApiMarkUpdatedIfPresent(be); } catch (Throwable ignored) {}
                            try { LogUtils.logDebug("[FIF] writeItemToBE: wrote via apiSetStack(index,stack) on {}", be.getClass().getName()); } catch (Throwable ignored) {}
                            return true;
                        }
                    } catch (Throwable t) {
                        try { LogUtils.logDebug("[FIF] writeItemToBE: apiSetStack invocation failed: {}", t.getMessage()); } catch (Throwable ignored) {}
                    }
                }
            }

            // Method-based heuristics
            for (Method m : be.getClass().getMethods()) {
                String name = m.getName().toLowerCase(Locale.ROOT);
                if (!(name.contains("set") || name.contains("display") || name.contains("held") || name.contains("item"))) continue;
                if (m.getParameterCount() != 1) continue;
                Class<?> p = m.getParameterTypes()[0];
                try {
                    if (p.isAssignableFrom(stack.getClass()) || p.getName().toLowerCase(Locale.ROOT).contains("itemstack") || p == Object.class) {
                        m.setAccessible(true);
                        m.invoke(be, stack == null ? ItemStack.EMPTY : stack.copy());
                        try { be.setChanged(); } catch (Throwable ignored) {}
                        try { invokeApiMarkUpdatedIfPresent(be); } catch (Throwable ignored) {}
                        try { LogUtils.logDebug("[FIF] writeItemToBE: wrote via method {} on {}", m.getName(), be.getClass().getName()); } catch (Throwable ignored) {}
                        return true;
                    }
                } catch (Throwable t) {
                    try { LogUtils.logDebug("[FIF] writeItemToBE: method {} failed: {}", m.getName(), t.getMessage()); } catch (Throwable ignored) {}
                }
            }

            // Field-based heuristics
            String[] fields = new String[] {"item", "displayedItem", "heldItem", "stack", "items"};
            for (String fn : fields) {
                try {
                    Field fld = be.getClass().getDeclaredField(fn);
                    fld.setAccessible(true);
                    Class<?> ft = fld.getType();
                    if (ItemStack.class.isAssignableFrom(ft)) {
                        fld.set(be, stack == null ? ItemStack.EMPTY : stack.copy());
                        try { be.setChanged(); } catch (Throwable ignored) {}
                        try { invokeApiMarkUpdatedIfPresent(be); } catch (Throwable ignored) {}
                        try { LogUtils.logDebug("[FIF] writeItemToBE: wrote via field {} on {}", fn, be.getClass().getName()); } catch (Throwable ignored) {}
                        return true;
                    }
                    Object v = fld.get(be);
                    if (v instanceof java.util.List<?> lst) {
                        try {
                            java.util.List<Object> mutable = new java.util.ArrayList<>(lst.size());
                            mutable.addAll((java.util.Collection<?>) lst);
                            if (mutable.isEmpty()) mutable.add(stack == null ? ItemStack.EMPTY : stack.copy());
                            else mutable.set(0, stack == null ? ItemStack.EMPTY : stack.copy());
                            fld.set(be, mutable);
                            try { be.setChanged(); } catch (Throwable ignored) {}
                            try { invokeApiMarkUpdatedIfPresent(be); } catch (Throwable ignored) {}
                            try { LogUtils.logDebug("[FIF] writeItemToBE: wrote into list field {} on {}", fn, be.getClass().getName()); } catch (Throwable ignored) {}
                            return true;
                        } catch (Throwable t) {
                            try { LogUtils.logDebug("[FIF] writeItemToBE: failed to write list field {}: {}", fn, t.getMessage()); } catch (Throwable ignored) {}
                        }
                    }
                } catch (Throwable ignored) {}
            }
            try { LogUtils.logDebug("[FIF] writeItemToBE: no suitable setter/field found on {}", be.getClass().getName()); } catch (Throwable ignored) {}
        } catch (Throwable t) {
            try { LogUtils.logDebug("[FIF] writeItemToBE: unexpected failure: {}", t.getMessage()); } catch (Throwable ignored) {}
        }
        return false;
    }



}
