package com.forgetmecrops.frame;
import com.forgetmecrops.util.log.LogUtils;
import com.forgetmecrops.config.Config;
// ChestUtils not used here; import removed by cleanup
import com.forgetmecrops.harvest.HarvestContext;
import com.forgetmecrops.util.hoe.FrameHoeReplacement;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/**
 * FrameRegistry: The master ledger of every known item-frame anchor in every loaded dimension!
 * <p>
 * Tracks all discovered anchors per dimension, manages their scan schedules, batches
 * pending frame rotations per tick, and maintains a chunk index so cleanup on chunk unload
 * is efficient. Think of it as the farm's HR department — it knows where everyone is,
 * when they're due to run, and who urgently needs a new hoe.
 * </p>
 * <p>
 * Thread safety: all public methods are synchronized. The ticker and catchup manager
 * both interact with the registry from different contexts, and synchronized maps
 * are a small price to pay for not losing anchors to a race condition.
 * </p>
 */
public class FrameRegistry {
    // Utility class. The registry manages itself; it does not need to be instantiated.
    private FrameRegistry() {}

    // Per-dimension map of known anchor positions to their FrameEntry metadata
    private static final Map<String, Map<BlockPos, FrameEntry>> framesByDimension = new HashMap<>();
    // Chunk key -> set of anchor positions discovered in that chunk; used for chunk-unload cleanup
    private static final Map<String, Set<BlockPos>> CHUNK_INDEX = new HashMap<>();
    // Per-dimension map of frame positions -> desired rotation values waiting to be flushed this tick
    private static final Map<String, Map<BlockPos, Integer>> PENDING_ROTATIONS = new HashMap<>();

    /**
     * FrameEntry: One frame anchor's complete dossier in the registry.
     * <p>
     * Tracks anchor metadata, activity state, scan countdown timer, last-seen wall-clock time,
     * rotation history, and whether a full animation sequence is currently running.
     * It's basically a sticky note that says "this frame exists, runs in N ticks, and is currently spinning."
     * </p>
     */
    public static class FrameEntry {
        /** The anchor describing this frame's position, linked chest, and current hoe. The whole picture. */
        public final FrameScanner.Anchor anchor;
        /** True if the anchor is currently active (has a valid hoe and should be scheduled to scan). */
        public boolean active;
        /** Countdown in server ticks until this anchor is next eligible to run a farm scan. */
        public int ticksUntilNextRun;
        /** Wall-clock timestamp (ms) of the last time this frame was seen. Used for staleness detection. */
        public long lastSeenMs;
        /** Game time of the last rotation event recorded for this frame; -1 if none has occurred this session. */
        public long lastRotationGameTime = -1L;
        /** True while a full-rotation animation sequence is in progress; scheduled rotations are blocked during animation. */
        public boolean animating = false;

        FrameEntry(FrameScanner.Anchor anchor) {
            this.anchor = anchor;
            this.active = anchor != null && anchor.hoe != null && !anchor.hoe.isEmpty();
            this.ticksUntilNextRun = Config.getTickInterval();
            this.lastSeenMs = System.currentTimeMillis();
            this.lastRotationGameTime = -1L;
        }
    }

    /**
     * Register or refresh a frame anchor in the registry.
     * If the frame is already known, refreshes its lastSeenMs timestamp and clamps its scan countdown.
     * If it's new, creates a fresh FrameEntry with the default tick interval before first scan.
     * Either way, updates the chunk index so chunk-unload cleanup knows to find it here.
     *
     * @param dimensionId dimension identifier string (e.g. "minecraft:overworld")
     * @param framePos    position of the frame anchor in the world
     * @param chest       the linked container receiving harvested crops (may be null for inactive frames)
     * @param hoe         the hoe held in the frame; empty = inactive anchor that can be activated later
     */
    public static synchronized void registerFrame(String dimensionId, BlockPos framePos, Container chest, ItemStack hoe) {
        Map<BlockPos, FrameEntry> map = framesByDimension.computeIfAbsent(dimensionId, k -> new HashMap<>());
        // Always store a defensive copy of the incoming ItemStack to avoid
        // accidental shared-mutation when callers retain references.
        FrameScanner.Anchor anchor = new FrameScanner.Anchor(chest, framePos, hoe == null ? ItemStack.EMPTY : hoe.copy());
        FrameEntry existing = map.get(framePos);
        if (existing == null) {
            FrameEntry fe = new FrameEntry(anchor);
            map.put(framePos, fe);
                String hoeDesc = anchor.hoe == null || anchor.hoe.isEmpty() ? "<empty>" : anchor.hoe.getItem().toString() + " x" + anchor.hoe.getCount();
                int chestId = anchor.chest == null ? 0 : System.identityHashCode(anchor.chest);
                LogUtils.logTrace("[REG] Registered {} frame at {} in {}. hoe={} chestId={} lastSeenMs={}",
                    (anchor.hoe != null && !anchor.hoe.isEmpty()) ? "active" : "inactive",
                    framePos, dimensionId, hoeDesc, chestId, fe.lastSeenMs);
        } else {
            if (hoe != null && !hoe.isEmpty()) existing.active = true;
            existing.lastSeenMs = System.currentTimeMillis();
            existing.ticksUntilNextRun = Math.min(existing.ticksUntilNextRun, Config.getTickInterval());
            String hoeDesc = hoe == null || hoe.isEmpty() ? "<empty>" : hoe.getItem().toString() + " x" + hoe.getCount();
            int chestId = existing.anchor.chest == null ? 0 : System.identityHashCode(existing.anchor.chest);
                LogUtils.logTrace("[REG] Refreshed frame at {} in {}. active={} hoe={} chestId={} lastSeenMs={}",
                    framePos, dimensionId, existing.active, hoeDesc, chestId, existing.lastSeenMs);
        }
        try {
            long chunkKey = computeChunkKey(framePos);
            CHUNK_INDEX.computeIfAbsent(chunkRegistryKey(dimensionId, chunkKey), ignored -> new HashSet<>()).add(framePos);
        } catch (Throwable ignored) {}
    }

    /**
     * Removes a frame anchor from the registry entirely.
     * Called when the frame entity or FIF block-entity is gone and we're sure it's not coming back.
     * Also called on validation failure during scan (bad chest, missing frame, etc.).
     *
     * @param dimensionId dimension identifier
     * @param framePos    position of the anchor to remove
     */
    public static synchronized void unregisterFrame(String dimensionId, BlockPos framePos) {
        Map<BlockPos, FrameEntry> map = framesByDimension.get(dimensionId);
        if (map == null) return;
        FrameEntry removed = map.remove(framePos);
        if (removed != null) {
            String hoeDesc = removed.anchor.hoe == null || removed.anchor.hoe.isEmpty() ? "<empty>" : removed.anchor.hoe.getItem().toString() + " x" + removed.anchor.hoe.getCount();
            int chestId = removed.anchor.chest == null ? 0 : System.identityHashCode(removed.anchor.chest);
            LogUtils.logTrace("[REG] Unregistered frame at {} in {}. hoe={} chestId={} lastSeenMs={}", framePos, dimensionId, hoeDesc, chestId, removed.lastSeenMs);
        }
    }

    /**
     * Adds newly discovered frame positions to the chunk index without touching registry entries.
     * Called after a chunk-load catchup scan to ensure the chunk index knows about
     * all detected frame positions (even those not yet fully validated/activated).
     *
     * @param dimensionId      dimension identifier
     * @param chunkKey         chunk key (from {@code computeChunkKey(framePos)})
     * @param discoveredFrames frame positions found during the catchup scan
     */
    public static synchronized void activateChunkFrames(String dimensionId, long chunkKey, List<BlockPos> discoveredFrames) {
        String chunkRegistryKey = chunkRegistryKey(dimensionId, chunkKey);
        Set<BlockPos> known = CHUNK_INDEX.computeIfAbsent(chunkRegistryKey, ignored -> new HashSet<>());
        for (BlockPos p : discoveredFrames) known.add(p);
    }

    /**
     * Reconciles the chunk index against a fresh discovery scan: deactivates any previously known
     * anchors that are no longer in the discovered list, then adds the new ones to the index.
     * Used during rediscovery passes to quietly retire stale anchors without unregistering them
     * — deactivated anchors won't be scheduled but are kept in case they come back.
     *
     * @param dimensionId      dimension identifier
     * @param chunkKey         chunk key
     * @param discoveredFrames freshly discovered frame positions in this chunk
     * @return list of positions that were deactivated (present before, absent now)
     */
    public static synchronized List<BlockPos> reconcileChunkFrames(String dimensionId, long chunkKey, List<BlockPos> discoveredFrames) {
        String chunkRegistryKey = chunkRegistryKey(dimensionId, chunkKey);
        Set<BlockPos> knownKeys = CHUNK_INDEX.computeIfAbsent(chunkRegistryKey, ignored -> new HashSet<>());
        Set<BlockPos> discoveredSet = new HashSet<>(discoveredFrames);
        List<BlockPos> deactivated = new ArrayList<>();
        for (BlockPos pos : new HashSet<>(knownKeys)) {
            if (discoveredSet.contains(pos)) {
                knownKeys.add(pos);
                continue;
            }
            Map<BlockPos, FrameEntry> map = framesByDimension.get(dimensionId);
            if (map != null) {
                FrameEntry fe = map.get(pos);
                if (fe != null && fe.active) {
                    fe.active = false;
                    deactivated.add(pos);
                }
            }
        }
        for (BlockPos p : discoveredFrames) knownKeys.add(p);
        return deactivated;
    }

    /**
     * Mark all frames from a chunk as inactive and return the list of deactivated positions.
     *
     * @param dimensionId dimension identifier
     * @param chunkKey chunk key
     * @return list of positions that were deactivated
     */
    public static synchronized List<BlockPos> markChunkInactive(String dimensionId, long chunkKey) {
        String chunkRegistryKey = chunkRegistryKey(dimensionId, chunkKey);
        Set<BlockPos> frameKeys = CHUNK_INDEX.get(chunkRegistryKey);
        if (frameKeys == null || frameKeys.isEmpty()) {
            return List.of();
        }

        List<BlockPos> deactivated = new ArrayList<>();
        Map<BlockPos, FrameEntry> map = framesByDimension.get(dimensionId);
        for (BlockPos pos : frameKeys) {
            if (map == null) continue;
            FrameEntry recorded = map.get(pos);
            if (recorded != null && recorded.active) {
                recorded.active = false;
                deactivated.add(pos);
            }
        }
        return deactivated;
    }

    /**
     * Set a cooldown (ticks until next run) for a registered frame.
     *
     * @param dimensionId dimension identifier
     * @param framePos frame position
     * @param ticks cooldown in ticks
     */
    /**
     * Set a temporary cooldown before this anchor is eligible to scan again.
     * Typically applied when the linked chest is full, a hoe replacement fails, or
     * any other condition that means retrying immediately would be pointless.
     * Also marks the frame as active in case it was previously inactive.
     *
     * @param dimensionId dimension identifier
     * @param framePos    frame position
     * @param ticks       cooldown duration in ticks (clamped to ≥0)
     */
    public static synchronized void setCooldown(String dimensionId, BlockPos framePos, int ticks) {
        Map<BlockPos, FrameEntry> map = framesByDimension.get(dimensionId);
        if (map == null) return;
        FrameEntry fe = map.get(framePos);
        if (fe == null) return;
        fe.ticksUntilNextRun = Math.max(0, ticks);
        fe.active = true;
        LogUtils.logTrace("[REG] Set cooldown for {} in {}: {} ticks", framePos, dimensionId, ticks);
    }

    /**
     * Swaps the stored hoe on an existing registry entry for a new one.
     * Rebuilds the FrameEntry (Anchor is immutable) while preserving all other state like
     * the existing scan countdown, last-seen time, and rotation history.
     * If the frame had no hoe and now has one, sets ticksUntilNextRun to 0 for an immediate scan.
     *
     * @param dimensionId dimension identifier
     * @param framePos    frame position
     * @param hoe         the new hoe stack to store (empty = anchor goes inactive)
     */
    public static synchronized void updateHoe(String dimensionId, BlockPos framePos, ItemStack hoe) {
        Map<BlockPos, FrameEntry> map = framesByDimension.get(dimensionId);
        if (map == null) return;
        FrameEntry old = map.get(framePos);
        if (old == null) return;

        FrameScanner.Anchor newAnchor = new FrameScanner.Anchor(old.anchor.chest, framePos, hoe == null ? ItemStack.EMPTY : hoe.copy());
        FrameEntry replacement = new FrameEntry(newAnchor);
        replacement.active = old.active || (hoe != null && !hoe.isEmpty());
        if (hoe != null && !hoe.isEmpty()) {
            boolean oldHadHoe = old.anchor != null && old.anchor.hoe != null && !old.anchor.hoe.isEmpty();
            if (!oldHadHoe) {
                // Hoe became available (e.g. chest auto-replacement): run immediately.
                replacement.ticksUntilNextRun = 0;
            } else {
                replacement.ticksUntilNextRun = Math.max(0, old.ticksUntilNextRun);
            }
        } else {
            replacement.ticksUntilNextRun = old.ticksUntilNextRun;
        }
        replacement.lastSeenMs = old.lastSeenMs;
        replacement.lastRotationGameTime = old.lastRotationGameTime;
        map.put(framePos, replacement);
        String oldHoeDesc = old.anchor.hoe == null || old.anchor.hoe.isEmpty() ? "<empty>" : old.anchor.hoe.getItem().toString() + " x" + old.anchor.hoe.getCount();
        String newHoeDesc = hoe == null || hoe.isEmpty() ? "<empty>" : hoe.getItem().toString() + " x" + hoe.getCount();
        int chestId = old.anchor.chest == null ? 0 : System.identityHashCode(old.anchor.chest);
        if (!old.active && replacement.active) {
            LogUtils.logTrace("[REG] Activated frame at {} in {} via replacement. oldHoe={} newHoe={} chestId={}", framePos, dimensionId, oldHoeDesc, newHoeDesc, chestId);
        } else {
            LogUtils.logTrace("[REG] Updated hoe for {} in {}. oldHoe={} newHoe={} chestId={}", framePos, dimensionId, oldHoeDesc, newHoeDesc, chestId);
        }
    }

    /**
     * Records the game time of a rotation event for cooldown tracking. Always returns true
     * if the entry exists — the return value indicates whether to proceed, not whether the
     * rotation was blocked. Rotation-rate gating is a concern of the caller.
     *
     * @param dimensionId dimension identifier
     * @param framePos    frame position
     * @param gameTime    the game time to record
     * @return true if the entry was found (and time was updated); true even if not found (no-block)
     */
    public static synchronized boolean tryRotation(String dimensionId, BlockPos framePos, long gameTime) {
        Map<BlockPos, FrameEntry> map = framesByDimension.get(dimensionId);
        if (map == null) return true;
        FrameEntry fe = map.get(framePos);
        if (fe == null) return true;
        fe.lastRotationGameTime = gameTime;
        return true;
    }

    /**
     * Queues a rotation to be flushed to the frame entity at the end of the current tick batch.
     * Batching rotations prevents per-frame entity lookups during time-critical scan loops.
     * If the frame is currently animating, the rotation is silently dropped —
     * the animation sequence manages its own rotation steps and doesn't appreciate interference.
     *
     * @param dimensionId     dimension identifier
     * @param framePos        frame position
     * @param rotation        the rotation to apply (0–7, masked internally)
     * @param requestGameTime the game time this rotation was requested (for staleness tracking)
     */
    public static synchronized void scheduleRotation(String dimensionId, BlockPos framePos, int rotation, long requestGameTime) {
        Map<BlockPos, FrameEntry> frames = framesByDimension.get(dimensionId);
        if (frames != null) {
            FrameEntry fe = frames.get(framePos);
            if (fe != null) {
                // If a full rotation animation is active for this frame, skip scheduling
                if (fe.animating) {
                    try { LogUtils.logDebug("[ROT] scheduleRotation SKIP (animating) for {} in {} -> {} (gametime={})", framePos, dimensionId, rotation & 7, requestGameTime); } catch (Throwable ignored) {}
                    return;
                }
                fe.lastRotationGameTime = requestGameTime;
                try { LogUtils.logDebug("[ROT] scheduleRotation queued for {} in {} -> {} (gametime={})", framePos, dimensionId, rotation & 7, requestGameTime); } catch (Throwable ignored) {}
            }
        }
        Map<BlockPos, Integer> map = PENDING_ROTATIONS.computeIfAbsent(dimensionId, k -> new HashMap<>());
        map.put(framePos, rotation & 7);
    }

    /**
     * Marks or unmarks a frame as currently playing a full rotation animation.
     * While animating=true, scheduled rotation updates are suppressed for this frame —
     * the animation sequence is in charge and doesn't want any outside interference.
     * On animation start, any queued pending rotation is removed to avoid a mid-animation glitch.
     */
    public static synchronized void setAnimating(String dimensionId, BlockPos framePos, boolean animating) {
        Map<BlockPos, FrameEntry> map = framesByDimension.get(dimensionId);
        if (map == null) return;
        FrameEntry fe = map.get(framePos);
        if (fe == null) return;
        fe.animating = animating;
        try { LogUtils.logTrace("[ROT] setAnimating {} for {} in {}", animating, framePos, dimensionId); } catch (Throwable ignored) {}
        if (animating) {
            // remove any pending scheduled rotation for this frame to avoid conflicts
            Map<BlockPos, Integer> pending = PENDING_ROTATIONS.get(dimensionId);
                if (pending != null) {
                Integer prev = pending.remove(framePos);
                try { LogUtils.logTrace("[ROT] Removed pending rotation for {} in {} -> {}", framePos, dimensionId, prev); } catch (Throwable ignored) {}
            } else {
                try { LogUtils.logTrace("[ROT] No pending rotations map for {} in {}", framePos, dimensionId); } catch (Throwable ignored) {}
            }
        }
    }

    /**
     * Clears the entire registry — all dimensions, all anchors, all chunk index entries.
     * Called on server stop/world unload to ensure a fresh state on next startup.
     * The agricultural HR department is officially closed for business. Everyone go home.
     */
    public static synchronized void clearAll() {
        framesByDimension.clear();
        CHUNK_INDEX.clear();
        LogUtils.logTrace("[REG] Cleared all frame registry data.");
    }

    /**
     * Clear registry entries for a single dimension.
     *
     * @param dimensionId dimension identifier
     */
    public static synchronized void clearDimension(String dimensionId) {
        if (dimensionId == null) return;
        Map<BlockPos, FrameEntry> removed = framesByDimension.remove(dimensionId);
        int removedAnchors = removed == null ? 0 : removed.size();

        String prefix = dimensionId + ":";
        List<String> removedKeys = new ArrayList<>();
        for (String key : new ArrayList<>(CHUNK_INDEX.keySet())) {
            if (key.startsWith(prefix)) {
                CHUNK_INDEX.remove(key);
                removedKeys.add(key);
            }
        }

        LogUtils.logTrace("[REG] Cleared {} anchors and {} chunk entries for dimension {}.", removedAnchors, removedKeys.size(), dimensionId);
    }

    /**
     * Advances all active frame countdown timers by one tick and returns any anchors
     * whose timer has reached zero (i.e., due for a scan this tick). Also:
     * - Attempts auto-replacement for frames with no hoe (chest may have received one)
     * - Flushes all queued pending rotations to their respective frame entities
     * <p>
     * The auto-replacement pass is best-effort: it tries each broken-hoe frame once per tick
     * without blocking scan collection. The replacement either sticks or it doesn't.
     * </p>
     *
     * @param dimensionId dimension identifier
     * @param level       the server level for block/entity lookups during rotation flush
     * @return list of anchors ready to scan this tick (in no particular order)
     */
    public static synchronized List<FrameScanner.Anchor> tickAndCollectReady(String dimensionId, net.minecraft.server.level.ServerLevel level) {
        List<FrameScanner.Anchor> ready = new ArrayList<>();
        Map<BlockPos, FrameEntry> map = framesByDimension.get(dimensionId);
        if (map == null) return ready;

        for (Map.Entry<BlockPos, FrameEntry> e : map.entrySet()) {
            BlockPos pos = e.getKey();
            FrameEntry fe = e.getValue();
            try {
                if (fe == null || fe.anchor == null) continue;
                net.minecraft.world.item.ItemStack stored = fe.anchor.hoe;
                net.minecraft.world.item.ItemStack liveHoe = net.minecraft.world.item.ItemStack.EMPTY;
                try {
                    if (level != null) {
                        liveHoe = FrameScanner.readHoeFromFrame(level, pos);
                    }
                } catch (Throwable ignored) {}

                if (liveHoe != null && !liveHoe.isEmpty()) {
                    if (stored == null || stored.isEmpty() || stored.getDamageValue() != liveHoe.getDamageValue() || stored.getCount() != liveHoe.getCount()) {
                        try { updateHoe(dimensionId, pos, liveHoe.copy()); } catch (Throwable ignored) {}
                    }
                    continue;
                }

                if (stored == null || stored.isEmpty()) {
                    try {
                        HarvestContext ctx = new HarvestContext(fe.anchor, level, net.minecraft.world.item.ItemStack.EMPTY, fe.anchor.chest, null);
                        FrameHoeReplacement.tryReplaceBrokenHoe(ctx);
                    } catch (Throwable t) {
                        LogUtils.logTrace("[REG] Failed to attempt auto-replacement for " + pos, t);
                    }
                }
            } catch (Throwable ignored) {}
        }

        for (FrameEntry fe : map.values()) {
            if (!fe.active) continue;
            fe.ticksUntilNextRun--;
            if (fe.ticksUntilNextRun <= 0) {
                int prevTicks = fe.ticksUntilNextRun;
                fe.ticksUntilNextRun = Config.getTickInterval();
                String hoeDesc = fe.anchor.hoe == null || fe.anchor.hoe.isEmpty() ? "<empty>" : fe.anchor.hoe.getItem().toString() + " x" + fe.anchor.hoe.getCount();
                int chestId = fe.anchor.chest == null ? 0 : System.identityHashCode(fe.anchor.chest);
                try { LogUtils.logTrace("[REG] Anchor ready: {} in {}. hoe={} chestId={} lastSeenMs={} prevTicks={}", fe.anchor.framePos, dimensionId, hoeDesc, chestId, fe.lastSeenMs, prevTicks); } catch (Throwable ignored) {}
                ready.add(fe.anchor);
            }
        }

        try {
            Map<BlockPos, Integer> pending = PENDING_ROTATIONS.remove(dimensionId);
                if (pending != null && !pending.isEmpty()) {
                try { LogUtils.logTrace("[ROT] Flushing {} pending rotations for {}", pending.size(), dimensionId); } catch (Throwable ignored) {}
                for (Map.Entry<BlockPos, Integer> p : pending.entrySet()) {
                        try {
                            FrameEntry fe = map.get(p.getKey());
                            if (fe != null && fe.animating) {
                                try { LogUtils.logTrace("[ROT] Skipping pending rotation for {} because animating=true", p.getKey()); } catch (Throwable ignored) {}
                                continue;
                            }
                            FrameScanner.applyScheduledRotation(level, p.getKey(), p.getValue());
                        } catch (Throwable t) {
                            LogUtils.logTrace("[ROT] Failed to apply scheduled rotation for " + p.getKey(), t);
                        }
                    }
            }
        } catch (Throwable ignored) {}

        try { LogUtils.logDebug("[REG] Collected {} anchors ready in {}", ready.size(), dimensionId); } catch (Throwable ignored) {}
        return ready;
    }

    /**
     * Count active anchors in the given dimension.
     *
     * @param dimensionId dimension identifier
     * @return number of active anchors
     */
    public static synchronized int countActiveFrames(String dimensionId) {
        Map<BlockPos, FrameEntry> map = framesByDimension.get(dimensionId);
        if (map == null) return 0;
        int cnt = 0;
        for (FrameEntry fe : map.values()) if (fe.active) cnt++;
        return cnt;
    }

    /**
     * Count recorded anchors (active or inactive) in the given dimension.
     *
     * @param dimensionId dimension identifier
     * @return number of recorded anchors
     */
    public static synchronized int countRecordedFrames(String dimensionId) {
        Map<BlockPos, FrameEntry> map = framesByDimension.get(dimensionId);
        return map == null ? 0 : map.size();
    }

    private static long computeChunkKey(BlockPos p) {
        long x = p.getX() >> 4;
        long z = p.getZ() >> 4;
        return (x & 0xffffffffL) << 32 | (z & 0xffffffffL);
    }

    private static String chunkRegistryKey(String dim, long chunkKey) { return dim + ":" + chunkKey; }
}
