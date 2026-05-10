package com.forgetmecrops.platform.adapter;

import com.forgetmecrops.util.log.LogUtils;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.Container;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import java.util.List;
import java.util.Locale;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ChunkSource;

/**
 * FastItemFrameAdapterImpl: The reflection-powered detective for FastItemFrames integration!
 * <p>
 * Centralizes all the gnarly reflective access to the FastItemFrames mod's block-entities.
 * Performs a lazy, one-time API probe on first use (to avoid heavy classloading during init
 * or chunk-load handlers), tries a list of known fully-qualified class name candidates,
 * and falls back to package-prefix scanning if needed. Gives up gracefully if FIF isn't
 * installed — the farm works fine without it, just without FIF support.
 * </p>
 * <p>
 * Once the probe succeeds, discovered API method references are cached and all subsequent
 * calls use them directly. The probe is synchronized to handle concurrent chunk-load events.
 * Uses the INSTANCE singleton for adapter interface calls; static methods for direct helpers.
 * </p>
 */
public class FastItemFrameAdapterImpl implements FastItemFrameAdapter {

    /** Singleton instance for all reflective FastItemFrames adapter operations. */
    public static final FastItemFrameAdapterImpl INSTANCE = new FastItemFrameAdapterImpl();

    /** Utility: external instantiation blocked; use INSTANCE for all calls. */
    private FastItemFrameAdapterImpl() {}

    // --- API-first reflective bindings (preferred if available) ---
    /** True if the one-time API probe found and bound the FastItemFrames API class successfully. */
    private static volatile boolean apiAvailable = false;
    /** Whether a probe attempt has already been performed (successful or not). */
    private static volatile boolean probeAttempted = false;
    /** The runtime class for the FastItemFrames block-entity, once probed. null if not installed. */
    private static Class<?> apiClass = null;
    /** Reflective reference to FIF's getDisplayedItem() equivalent (may vary by version/loader). */
    private static java.lang.reflect.Method apiGetDisplayedItem = null;
    /** Reflective reference to FIF's getItems() multi-slot accessor (optional). */
    private static java.lang.reflect.Method apiGetItems = null;
    /** Reflective reference to FIF's getRotation() method for frame orientation. */
    private static java.lang.reflect.Method apiGetRotation = null;
    /** Reflective reference to FIF's setRotation() method for frame orientation. */
    private static java.lang.reflect.Method apiSetRotation = null;
    /** Reflective reference to FIF's setItem() method. */
    private static java.lang.reflect.Method apiSetItem = null;
    /** Reflective reference to FIF's setStack() or equivalent item-swap method. */
    private static java.lang.reflect.Method apiSetStack = null;
    /** Reflective reference to FIF's markUpdated() or sync method. */
    private static java.lang.reflect.Method apiMarkUpdated = null;
    /** Reflective reference to the internal items field, used as a last-resort accessor. */
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
                LogUtils.logTrace("[FIF] FastItemFrames API not found during lazy probe.");
            } catch (Throwable t) {
                LogUtils.logTrace("[FIF] API probe failure", t);
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
            try { LogUtils.logTrace("[FIF] extractHeldItem: probing BE {} apiAvailable={}", be.getClass().getName(), apiAvailable); } catch (Throwable ignored) {}
            // API-first extraction
            if (apiAvailable && apiClass != null && apiClass.isInstance(be) && apiGetDisplayedItem != null) {
                try {
                    Object res = apiGetDisplayedItem.invoke(be);
                    if (res instanceof ItemStack) {
                        try { LogUtils.logTrace("[FIF] extractHeldItem: apiGetDisplayedItem returned item via {}", apiGetDisplayedItem.getName()); } catch (Throwable ignored) {}
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
                                try { LogUtils.logTrace("[FIF] extractHeldItem: apiGetItems returned list size {}", list.size()); } catch (Throwable ignored) {}
                                return ((ItemStack) first).copy();
                            }
                        } else {
                            try { LogUtils.logTrace("[FIF] extractHeldItem: apiGetItems returned null/empty"); } catch (Throwable ignored) {}
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
                            try { LogUtils.logTrace("[FIF] extractHeldItem: apiItemsField returned list size {}", lst.size()); } catch (Throwable ignored) {}
                            return ((ItemStack) first).copy();
                        }
                    } else {
                        try { LogUtils.logTrace("[FIF] extractHeldItem: apiItemsField returned null/empty"); } catch (Throwable ignored) {}
                    }
                } catch (Throwable ignored) {}
            }
            for (Method m : be.getClass().getMethods()) {
                String name = m.getName().toLowerCase(Locale.ROOT);
                if ((name.contains("getdisplayed") || name.contains("getheld") || name.contains("getitem")) && m.getParameterCount() == 0) {
                    try {
                        Object res = m.invoke(be);
                        if (res instanceof ItemStack) {
                            try { LogUtils.logTrace("[FIF] extractHeldItem: method {} returned ItemStack", m.getName()); } catch (Throwable ignored) {}
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
                        try { LogUtils.logTrace("[FIF] extractHeldItem: field {} returned ItemStack", fn); } catch (Throwable ignored) {}
                        return ((ItemStack) v).copy();
                    }
                    if (v instanceof java.util.List<?> list && !list.isEmpty()) {
                        Object first = list.get(0);
                        if (first instanceof ItemStack) {
                            try { LogUtils.logTrace("[FIF] extractHeldItem: field {} returned list size {}", fn, list.size()); } catch (Throwable ignored) {}
                            return ((ItemStack) first).copy();
                        }
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        try { LogUtils.logTrace("[FIF] extractHeldItem: no held item found for BE {}", be.getClass().getName()); } catch (Throwable ignored) {}
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

            // Production FIF exposes rotation on the block state, not the block entity itself.
            try {
                Level level = be.getLevel();
                if (level != null) {
                    BlockState state = level.getBlockState(be.getBlockPos());
                    Property<?> rotationProp = findRotationProperty(state);
                    if (rotationProp != null) {
                        Object value = getPropertyValue(state, rotationProp);
                        int mapped = rotationValueToInt(rotationProp, value);
                        try { LogUtils.logTrace("[FIF] getRotation: block-state property {} on {} -> value={} mapped={}", rotationProp.getName(), be.getClass().getName(), value, mapped); } catch (Throwable ignored) {}
                        return mapped;
                    }
                }
            } catch (Throwable ignored) {}
        } catch (Throwable ignored) {}
        return 0;
    }

    /**
     * Set rotation on a FastItemFrames block-entity and report whether the write actually stuck.
     * <p>
     * The old project survived production packs by preferring real setters and falling back to
     * cycling the frame block's rotation property instead of guessing at raw property values.
     * We keep that same survival instinct here so version mismatches have fewer places to hide.
     * </p>
     * @param be The block entity to modify.
     * @param newRotation Rotation value to set (0-7).
     * @return true if the adapter applied or verified the requested rotation; false otherwise.
     */
    public static boolean setRotation(BlockEntity be, int newRotation) {
        ensureApiProbed();
        if (be == null) {
            try { LogUtils.logDebug("[FIF-DIAG] setRotation called with null block entity (requested={})", newRotation & 7); } catch (Throwable ignored) {}
            return false;
        }
        try {
            int requestedRotation = newRotation & 7;
            Level entryLevel = null;
            BlockPos entryPos = null;
            try { entryLevel = be.getLevel(); } catch (Throwable ignored) {}
            try { entryPos = be.getBlockPos(); } catch (Throwable ignored) {}
            try {
                LogUtils.logDebug("[FIF-DIAG] setRotation entry: beClass={} requested={} apiAvailable={} apiSetRotation={} levelNull={} pos={} clientSide={} loaded={} hasChunk={}",
                        be.getClass().getName(),
                        requestedRotation,
                        apiAvailable,
                        apiSetRotation == null ? "<none>" : apiSetRotation.getName(),
                        entryLevel == null,
                        entryPos,
                        entryLevel != null && entryLevel.isClientSide(),
                        entryLevel != null && entryPos != null && entryLevel.isLoaded(entryPos),
                        entryLevel != null && entryPos != null && entryLevel.getChunk(entryPos) != null);
            } catch (Throwable ignored) {}
            logRotationState(entryLevel, entryPos, "setRotation.pre");

            // API-first setter
            if (apiAvailable && apiClass != null && apiClass.isInstance(be) && apiSetRotation != null) {
                try {
                    Class<?> p = apiSetRotation.getParameterTypes()[0];
                    try { LogUtils.logDebug("[FIF-DIAG] setRotation trying api method {}({})", apiSetRotation.getName(), p.getName()); } catch (Throwable ignored) {}
                    if (p == int.class || p == Integer.class) {
                        apiSetRotation.invoke(be, requestedRotation);
                        markBlockEntityChanged(be, 3);
                        logRotationState(entryLevel, entryPos, "setRotation.post.api-int");
                        return verifyRotationWrite(be, requestedRotation, "apiSetRotation(int)");
                    }
                    if (p == byte.class || p == Byte.class) {
                        apiSetRotation.invoke(be, (byte) requestedRotation);
                        markBlockEntityChanged(be, 3);
                        logRotationState(entryLevel, entryPos, "setRotation.post.api-byte");
                        return verifyRotationWrite(be, requestedRotation, "apiSetRotation(byte)");
                    }
                    try { LogUtils.logDebug("[FIF-DIAG] setRotation api method {} has unsupported parameter type {}", apiSetRotation.getName(), p.getName()); } catch (Throwable ignored) {}
                } catch (Throwable ignored) {}
            }

            for (Method m : be.getClass().getMethods()) {
                String name = m.getName().toLowerCase(Locale.ROOT);
                if (name.contains("set") && name.contains("rotation") && m.getParameterCount() == 1) {
                    Class<?> p = m.getParameterTypes()[0];
                    try {
                        try { LogUtils.logDebug("[FIF-DIAG] setRotation trying reflected method {}({})", m.getName(), p.getName()); } catch (Throwable ignored) {}
                        if (p == int.class || p == Integer.class) {
                            m.invoke(be, requestedRotation);
                            markBlockEntityChanged(be, 3);
                            logRotationState(entryLevel, entryPos, "setRotation.post.reflection-int");
                            return verifyRotationWrite(be, requestedRotation, "reflected method " + m.getName() + "(int)");
                        }
                        if (p == byte.class || p == Byte.class) {
                            m.invoke(be, (byte) requestedRotation);
                            markBlockEntityChanged(be, 3);
                            logRotationState(entryLevel, entryPos, "setRotation.post.reflection-byte");
                            return verifyRotationWrite(be, requestedRotation, "reflected method " + m.getName() + "(byte)");
                        }
                    } catch (Throwable t) {
                        try { LogUtils.logDebug("[FIF-DIAG] setRotation reflected method {} failed: {}", m.getName(), throwableSummary(t)); } catch (Throwable ignored) {}
                    }
                }
            }

            try {
                Field fld = be.getClass().getDeclaredField("rotation");
                fld.setAccessible(true);
                fld.setInt(be, requestedRotation);
                markBlockEntityChanged(be, 3);
                try { LogUtils.logDebug("[FIF-DIAG] setRotation wrote field rotation directly"); } catch (Throwable ignored) {}
                logRotationState(entryLevel, entryPos, "setRotation.post.field");
                return verifyRotationWrite(be, requestedRotation, "field rotation");
            } catch (Throwable t) {
                try { LogUtils.logDebug("[FIF-DIAG] setRotation field write failed: {}", throwableSummary(t)); } catch (Throwable ignored) {}
            }

            // When setters vanish across versions, fall back to cycling the block-state property like the original project did.
            try {
                Level level = null;
                BlockPos pos = null;
                try { level = be.getLevel(); } catch (Throwable ignored) {}
                try { pos = be.getBlockPos(); } catch (Throwable ignored) {}

                if (level == null || pos == null) {
                    try {
                        LogUtils.logDebug("[FIF-DIAG] setRotation cannot enter block-state fallback (levelNull={}, posNull={})", level == null, pos == null);
                    } catch (Throwable ignored) {}
                } else {
                    BlockState state = level.getBlockState(pos);
                    try { LogUtils.logDebug("[FIF-DIAG] setRotation entering block-state cycle fallback at {} state={}", pos, describeRotationState(state)); } catch (Throwable ignored) {}
                    if (cycleRotationPropertyToTarget(level, pos, state, be, requestedRotation)) {
                        return true;
                    }
                    try { LogUtils.logDebug("[FIF-DIAG] setRotation block-state cycle fallback completed without success at {}", pos); } catch (Throwable ignored) {}
                }
            } catch (Throwable t) {
                try { LogUtils.logDebug("[FIF-DIAG] setRotation block-state fallback failed: {}", throwableSummary(t)); } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        try { LogUtils.logDebug("[FIF-DIAG] setRotation failed: no working write path for {}", be.getClass().getName()); } catch (Throwable ignored) {}
        return false;
    }

    // Mark the BE dirty and nudge the world so the visual state has a fighting chance to propagate.
    private static void markBlockEntityChanged(BlockEntity be, int updateFlags) {
        if (be == null) return;
        try { be.setChanged(); } catch (Throwable ignored) {}
        try { invokeApiMarkUpdatedIfPresent(be); } catch (Throwable ignored) {}
        try {
            Level level = be.getLevel();
            if (level == null) return;
            BlockPos pos = be.getBlockPos();
            BlockState state = level.getBlockState(pos);
            try { LogUtils.logDebug("[FIF-DIAG] markBlockEntityChanged pos={} flagsIn={} flagsApplied={} state={}", pos, updateFlags, updateFlags | 1, describeRotationState(state)); } catch (Throwable ignored) {}
            level.sendBlockUpdated(pos, state, state, updateFlags | 1);
        } catch (Throwable ignored) {}
    }

    // Read the rotation back from the block state directly (bypass BE cache).
    // Production FIF stores rotation on the block state, not the BE, so we must read
    // from the block state to verify writes, not from potentially stale BE methods.
    private static boolean verifyRotationWrite(BlockEntity be, int requestedRotation, String pathLabel) {
        try {
            int readBack = 0;
            String propName = "<none>";
            String propValue = "<none>";
            try {
                Level level = be.getLevel();
                if (level != null) {
                    BlockState state = level.getBlockState(be.getBlockPos());
                    Property<?> rotationProp = findRotationProperty(state);
                    if (rotationProp != null) {
                        Object value = getPropertyValue(state, rotationProp);
                        readBack = rotationValueToInt(rotationProp, value);
                        propName = rotationProp.getName();
                        propValue = String.valueOf(value);
                    } else {
                        try { LogUtils.logDebug("[FIF-DIAG] verifyRotationWrite: no rotation property found on state {}", state); } catch (Throwable ignored) {}
                    }
                }
            } catch (Throwable ignored) {}
            boolean matches = readBack == (requestedRotation & 7);
            try {
                LogUtils.logDebug("[FIF-DIAG] verifyRotationWrite path={} beClass={} requested={} readBack={} verified={} prop={} propValue={}",
                        pathLabel,
                        be.getClass().getName(),
                        requestedRotation & 7,
                        readBack,
                        matches,
                        propName,
                        propValue);
            } catch (Throwable ignored) {}
            return matches;
        } catch (Throwable ignored) {
            try { LogUtils.logDebug("[FIF-DIAG] verifyRotationWrite path={} beClass={} could not read back; assuming success", pathLabel, be.getClass().getName()); } catch (Throwable ignored2) {}
            return true;
        }
    }

    // Cycle the rotation property instead of jamming in a guessed raw value; that old trick was ugly, but it worked.
    private static boolean cycleRotationPropertyToTarget(Level level, BlockPos pos, BlockState state, BlockEntity be, int requestedRotation) {
        try {
            Property<?> rotationProp = findRotationProperty(state);
            if (rotationProp == null) {
                try { LogUtils.logDebug("[FIF-DIAG] cycleRotationPropertyToTarget: no rotation property found on {} at {} state={}", be.getClass().getName(), pos, state); } catch (Throwable ignored) {}
                return false;
            }

            List<?> values = new java.util.ArrayList<>(rotationProp.getPossibleValues());
            if (values.isEmpty()) return false;

            Object targetValue = findTargetRotationValue(values, requestedRotation);
            if (targetValue == null) {
                targetValue = values.get(requestedRotation % values.size());
            }
            try {
                LogUtils.logDebug("[FIF-DIAG] cycleRotationPropertyToTarget start pos={} prop={} requested={} target={} values={}",
                        pos,
                        rotationProp.getName(),
                        requestedRotation & 7,
                        targetValue,
                        values);
            } catch (Throwable ignored) {}

            BlockState cycledState = state;
            for (int i = 0; i < values.size(); i++) {
                Object currentValue = getPropertyValue(cycledState, rotationProp);
                try { LogUtils.logDebug("[FIF-DIAG] cycleRotationPropertyToTarget step={} current={} target={} state={}", i, currentValue, targetValue, describeRotationState(cycledState)); } catch (Throwable ignored) {}
                if (targetValue.equals(currentValue)) {
                    boolean setResult = level.setBlock(pos, cycledState, 3);
                    try { LogUtils.logDebug("[FIF-DIAG] cycleRotationPropertyToTarget setBlock result={} flags=3 at {}", setResult, pos); } catch (Throwable ignored) {}
                    markBlockEntityChanged(be, 3);
                    logRotationState(level, pos, "cycle.post-setBlock");
                    try { LogUtils.logDebug("[FIF-DIAG] setRotation: cycled block-state property {} to {} on {}", rotationProp.getName(), targetValue, be.getClass().getName()); } catch (Throwable ignored) {}
                    return verifyRotationWrite(be, requestedRotation, "cycled property " + rotationProp.getName());
                }
                cycledState = cycleProperty(cycledState, rotationProp);
            }
            try { LogUtils.logDebug("[FIF-DIAG] cycleRotationPropertyToTarget exhausted values without target match at {}", pos); } catch (Throwable ignored) {}
        } catch (Throwable ignored) {}
        return false;
    }

    private static String describeRotationState(BlockState state) {
        if (state == null) return "<null-state>";
        try {
            Property<?> rotationProp = findRotationProperty(state);
            if (rotationProp == null) {
                return "block=" + state.getBlock().getClass().getName() + ", rotationProp=<none>, full=" + state;
            }
            Object value = getPropertyValue(state, rotationProp);
            int mapped = rotationValueToInt(rotationProp, value);
            return "block=" + state.getBlock().getClass().getName() + ", rotationProp=" + rotationProp.getName() + ", value=" + value + ", mapped=" + mapped + ", full=" + state;
        } catch (Throwable t) {
            return "<state-describe-failed:" + throwableSummary(t) + ">";
        }
    }

    private static void logRotationState(Level level, BlockPos pos, String label) {
        if (level == null || pos == null) {
            try { LogUtils.logDebug("[FIF-DIAG] {} level/pos unavailable (levelNull={}, posNull={})", label, level == null, pos == null); } catch (Throwable ignored) {}
            return;
        }
        try {
            BlockState state = level.getBlockState(pos);
            LogUtils.logDebug("[FIF-DIAG] {} pos={} clientSide={} loaded={} hasChunk={} {}",
                    label,
                    pos,
                    level.isClientSide(),
                    level.isLoaded(pos),
                    level.getChunk(pos) != null,
                    describeRotationState(state));
        } catch (Throwable t) {
            try { LogUtils.logDebug("[FIF-DIAG] {} failed to snapshot state at {}: {}", label, pos, throwableSummary(t)); } catch (Throwable ignored) {}
        }
    }

    private static String throwableSummary(Throwable t) {
        if (t == null) return "<null>";
        String type = t.getClass().getSimpleName();
        String message = t.getMessage();
        if (message == null || message.isEmpty()) return type;
        return type + ": " + message;
    }

    // Find the property most likely to represent frame rotation without pretending every mod author names things sensibly.
    private static Property<?> findRotationProperty(BlockState state) {
        for (Property<?> prop : state.getProperties()) {
            String name = prop.getName().toLowerCase(Locale.ROOT);
            if (name.contains("rotation") || name.contains("rot")) {
                return prop;
            }
        }
        return null;
    }

    private static Object findTargetRotationValue(List<?> values, int requestedRotation) {
        for (Object value : values) {
            if (value instanceof Number && ((Number) value).intValue() == (requestedRotation & 7)) {
                return value;
            }
            if (String.valueOf(requestedRotation & 7).equalsIgnoreCase(String.valueOf(value))) {
                return value;
            }
        }
        return null;
    }

    private static int rotationValueToInt(Property<?> prop, Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue() & 7;
        }
        List<?> values = new java.util.ArrayList<>(prop.getPossibleValues());
        for (int i = 0; i < values.size(); i++) {
            Object candidate = values.get(i);
            if (candidate.equals(value)) {
                return i & 7;
            }
            if (String.valueOf(candidate).equalsIgnoreCase(String.valueOf(value))) {
                return i & 7;
            }
        }
        return 0;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object getPropertyValue(BlockState state, Property<?> prop) {
        return state.getValue((Property) prop);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static BlockState cycleProperty(BlockState state, Property<?> prop) {
        return state.cycle((Property) prop);
    }

    private static void invokeApiMarkUpdatedIfPresent(BlockEntity be) {
        if (apiMarkUpdated == null || be == null) {
            try { LogUtils.logTrace("[FIF] invokeApiMarkUpdatedIfPresent: no apiMarkUpdated or be=null (apiMarkUpdated={} be={})", apiMarkUpdated, be == null ? "null" : be.getClass().getName()); } catch (Throwable ignored) {}
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

            if (levelObj == null) {
                try { LogUtils.logTrace("[FIF] invokeApiMarkUpdatedIfPresent: could not resolve level object for BE {}", be.getClass().getName()); } catch (Throwable ignored) {}
                return;
            }

            try {
                try { LogUtils.logTrace("[FIF] invokeApiMarkUpdatedIfPresent: invoking apiMarkUpdated on {} with level {}", be.getClass().getName(), levelObj.getClass().getName()); } catch (Throwable ignored) {}
                apiMarkUpdated.invoke(be, levelObj);
                try { LogUtils.logTrace("[FIF] invokeApiMarkUpdatedIfPresent: apiMarkUpdated invoked successfully for {}", be.getClass().getName()); } catch (Throwable ignored) {}
            } catch (Throwable t) {
                try { LogUtils.logTrace("[FIF] invokeApiMarkUpdatedIfPresent invocation failed: {}", t.getMessage()); } catch (Throwable ignored) {}
            }
        } catch (Throwable t) {
            try { LogUtils.logTrace("[FIF] invokeApiMarkUpdatedIfPresent failed: {}", t.getMessage()); } catch (Throwable ignored) {}
        }
    }

    /**
     * Attempt to write an ItemStack into a FastItemFrames block-entity using API-first
     * methods and reflective fallbacks. Returns true on success.
     */
    public static boolean writeItemToBE(BlockEntity be, ItemStack stack) {
        ensureApiProbed();
        if (be == null) return false;
        ItemStack safeStack = stack == null ? ItemStack.EMPTY : stack.copy();
        try {
            try { LogUtils.logTrace("[FIF] writeItemToBE: attempting write to BE {} with item={} damage={}", be.getClass().getName(), safeStack.isEmpty() ? "<empty>" : safeStack.getItem(), safeStack.isEmpty() ? -1 : safeStack.getDamageValue()); } catch (Throwable ignored) {}

            // API-first
            if (apiAvailable && apiClass != null && apiClass.isInstance(be)) {
                if (apiSetItem != null) {
                    try {
                        apiSetItem.setAccessible(true);
                        apiSetItem.invoke(be, safeStack.copy());
                        try { be.setChanged(); } catch (Throwable ignored) {}
                        try { invokeApiMarkUpdatedIfPresent(be); } catch (Throwable ignored) {}
                        try { LogUtils.logTrace("[FIF] writeItemToBE: wrote via apiSetItem on {}", be.getClass().getName()); } catch (Throwable ignored) {}
                        return true;
                    } catch (Throwable t) {
                        try { LogUtils.logTrace("[FIF] writeItemToBE: apiSetItem invocation failed: {}", t.getMessage()); } catch (Throwable ignored) {}
                    }
                }
                if (apiSetStack != null) {
                    try {
                        Class<?>[] pts = apiSetStack.getParameterTypes();
                        if (pts.length == 2 && (pts[0] == int.class || pts[0] == Integer.class)) {
                            apiSetStack.setAccessible(true);
                            apiSetStack.invoke(be, Integer.valueOf(0), safeStack.copy());
                            try { be.setChanged(); } catch (Throwable ignored) {}
                            try { invokeApiMarkUpdatedIfPresent(be); } catch (Throwable ignored) {}
                            try { LogUtils.logTrace("[FIF] writeItemToBE: wrote via apiSetStack(index,stack) on {}", be.getClass().getName()); } catch (Throwable ignored) {}
                            return true;
                        }
                    } catch (Throwable t) {
                        try { LogUtils.logTrace("[FIF] writeItemToBE: apiSetStack invocation failed: {}", t.getMessage()); } catch (Throwable ignored) {}
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
                    if (p.isAssignableFrom(ItemStack.class) || p.getName().toLowerCase(Locale.ROOT).contains("itemstack") || p == Object.class) {
                        m.setAccessible(true);
                        m.invoke(be, safeStack.copy());
                        try { be.setChanged(); } catch (Throwable ignored) {}
                        try { invokeApiMarkUpdatedIfPresent(be); } catch (Throwable ignored) {}
                        try { LogUtils.logTrace("[FIF] writeItemToBE: wrote via method {} on {}", m.getName(), be.getClass().getName()); } catch (Throwable ignored) {}
                        return true;
                    }
                } catch (Throwable t) {
                    try { LogUtils.logTrace("[FIF] writeItemToBE: method {} failed: {}", m.getName(), t.getMessage()); } catch (Throwable ignored) {}
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
                        fld.set(be, safeStack.copy());
                        try { be.setChanged(); } catch (Throwable ignored) {}
                        try { invokeApiMarkUpdatedIfPresent(be); } catch (Throwable ignored) {}
                        try { LogUtils.logTrace("[FIF] writeItemToBE: wrote via field {} on {}", fn, be.getClass().getName()); } catch (Throwable ignored) {}
                        return true;
                    }
                    Object v = fld.get(be);
                    if (v instanceof java.util.List<?> lst) {
                        try {
                            java.util.List<Object> mutable = new java.util.ArrayList<>(lst.size());
                            mutable.addAll((java.util.Collection<?>) lst);
                            if (mutable.isEmpty()) mutable.add(safeStack.copy());
                            else mutable.set(0, safeStack.copy());
                            fld.set(be, mutable);
                            try { be.setChanged(); } catch (Throwable ignored) {}
                            try { invokeApiMarkUpdatedIfPresent(be); } catch (Throwable ignored) {}
                            try { LogUtils.logTrace("[FIF] writeItemToBE: wrote into list field {} on {}", fn, be.getClass().getName()); } catch (Throwable ignored) {}
                            return true;
                        } catch (Throwable t) {
                            try { LogUtils.logTrace("[FIF] writeItemToBE: failed to write list field {}: {}", fn, t.getMessage()); } catch (Throwable ignored) {}
                        }
                    }
                } catch (Throwable ignored) {}
            }
            try { LogUtils.logTrace("[FIF] writeItemToBE: no suitable setter/field found on {}", be.getClass().getName()); } catch (Throwable ignored) {}
        } catch (Throwable t) {
            try { LogUtils.logTrace("[FIF] writeItemToBE: unexpected failure: {}", t.getMessage()); } catch (Throwable ignored) {}
        }
        return false;
    }

    // --- Loaded-chunk iteration helpers (best-effort, reflective) ---
    private static volatile boolean chunkMapProbeAttempted = false;
    private static volatile java.lang.reflect.Field chunkMapField = null;
    private static volatile java.lang.reflect.Method getChunksMethod = null;
    private static volatile java.lang.reflect.Method getTickingChunkMethod = null;
    private static volatile java.lang.reflect.Method getFullChunkMethod = null;

    private static void ensureChunkMapProbed() {
        if (chunkMapProbeAttempted) return;
        synchronized (FastItemFrameAdapterImpl.class) {
            if (chunkMapProbeAttempted) return;
            chunkMapProbeAttempted = true;
            try {
                try {
                    chunkMapField = ChunkSource.class.getDeclaredField("chunkMap");
                    chunkMapField.setAccessible(true);
                } catch (Throwable ignored) {}

                if (chunkMapField != null) {
                    try {
                        Class<?> chunkMapClass = chunkMapField.getType();
                        getChunksMethod = chunkMapClass.getDeclaredMethod("getChunks");
                        getChunksMethod.setAccessible(true);
                    } catch (Throwable ignored) {}
                }
                // If the simple name probe failed, try scanning declared fields to
                // find any field whose type exposes an iterable-getChunks method.
                if (chunkMapField == null || getChunksMethod == null) {
                    try {
                        try { LogUtils.logTrace("[FIF] chunkMap probe simple lookup failed; scanning ChunkSource fields"); } catch (Throwable ignored) {}
                        for (java.lang.reflect.Field f : ChunkSource.class.getDeclaredFields()) {
                            try {
                                Class<?> t = f.getType();
                                for (java.lang.reflect.Method m : t.getDeclaredMethods()) {
                                    if (m.getParameterCount() == 0) {
                                        String mn = m.getName().toLowerCase(Locale.ROOT);
                                        if (mn.contains("getchunks") || mn.contains("chunks") || mn.contains("getchunk")) {
                                            // candidate found
                                            f.setAccessible(true);
                                            chunkMapField = f;
                                            getChunksMethod = m;
                                            getChunksMethod.setAccessible(true);
                                            try { LogUtils.logTrace("[FIF] chunkMap probe found candidate field {} with method {}", f.getName(), m.getName()); } catch (Throwable ignored) {}
                                            break;
                                        }
                                    }
                                }
                            } catch (Throwable ignored) {}
                            if (chunkMapField != null && getChunksMethod != null) break;
                        }
                    } catch (Throwable ignored) {}
                }
            } catch (Throwable ignored) {
                // silence
            }
        }
    }

    public static java.util.List<LevelChunk> getLoadedChunks(ServerLevel level) {
        java.util.List<LevelChunk> chunks = new java.util.ArrayList<>();
        if (level == null) return chunks;
        ensureChunkMapProbed();
        if (chunkMapField == null || getChunksMethod == null) {
            try { LogUtils.logTrace("[FIF] getLoadedChunks: chunkMapField or getChunksMethod missing (chunkMapField={} getChunksMethod={})", chunkMapField, getChunksMethod); } catch (Throwable ignored) {}
            return chunks;
        }
        try {
            Object chunkMap = chunkMapField.get(level.getChunkSource());
            Object holders = getChunksMethod.invoke(chunkMap);
            if (!(holders instanceof Iterable<?> iterable)) return chunks;
            for (Object holder : iterable) {
                LevelChunk lc = extractLoadedChunk(holder);
                if (lc != null) chunks.add(lc);
            }
            try { LogUtils.logTrace("[FIF] getLoadedChunks: collected {} loaded chunks", chunks.size()); } catch (Throwable ignored) {}
        } catch (Throwable t) {
            try { LogUtils.logTrace("[FIF] getLoadedChunks failed: {}", t.getMessage()); } catch (Throwable ignored) {}
        }
        return chunks;
    }

    private static LevelChunk extractLoadedChunk(Object holder) {
        if (holder == null) return null;
        try {
            if (holder instanceof LevelChunk) return (LevelChunk) holder;
        } catch (Throwable ignored) {}

        try {
            Method localGetTicking;
            synchronized (FastItemFrameAdapterImpl.class) {
                if (getTickingChunkMethod == null) {
                    getTickingChunkMethod = holder.getClass().getDeclaredMethod("getTickingChunk");
                    getTickingChunkMethod.setAccessible(true);
                }
                localGetTicking = getTickingChunkMethod;
            }
            Object chunk = localGetTicking.invoke(holder);
            if (chunk instanceof LevelChunk) return (LevelChunk) chunk;
        } catch (Throwable ignored) {}

        try {
            Method localGetFull;
            synchronized (FastItemFrameAdapterImpl.class) {
                if (getFullChunkMethod == null) {
                    getFullChunkMethod = holder.getClass().getDeclaredMethod("getFullChunk");
                    getFullChunkMethod.setAccessible(true);
                }
                localGetFull = getFullChunkMethod;
            }
            Object chunk = localGetFull.invoke(holder);
            if (chunk instanceof LevelChunk) return (LevelChunk) chunk;
        } catch (Throwable ignored) {}

        return null;
    }



}
