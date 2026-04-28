package com.fastharvester.frame;
import com.fastharvester.Constants;
import com.fastharvester.config.Config;
import com.fastharvester.util.chest.ChestUtils;
import com.fastharvester.harvest.HarvestContext;
import com.fastharvester.util.hoe.FrameHoeReplacement;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/**
 * FrameRegistry: tracks discovered item-frame anchors per-dimension and schedules when they are due to run.
 */
public class FrameRegistry {
    private FrameRegistry() {}

    private static final Map<String, Map<BlockPos, FrameEntry>> framesByDimension = new HashMap<>();
    private static final Map<String, Set<BlockPos>> CHUNK_INDEX = new HashMap<>();
    private static final Map<String, Map<BlockPos, Integer>> PENDING_ROTATIONS = new HashMap<>();

    /**
     * Internal registry entry representing a discovered frame anchor.
     */
    public static class FrameEntry {
        /** Anchor metadata for this registered frame. */
        public final FrameScanner.Anchor anchor;
        /** Whether this anchor is currently active. */
        public boolean active;
        /** Ticks until the next scheduled run for this anchor. */
        public int ticksUntilNextRun;
        /** Last time this anchor was seen (ms since epoch). */
        public long lastSeenMs;
        /** Last game time at which a rotation was recorded (-1 if none). */
        public long lastRotationGameTime = -1L;
        /** Whether a full rotation animation is currently active for this frame. */
        public boolean animating = false;

        FrameEntry(FrameScanner.Anchor anchor) {
            this.anchor = anchor;
            this.active = anchor != null && anchor.hoe != null && !anchor.hoe.isEmpty();
            this.ticksUntilNextRun = Config.tickInterval;
            this.lastSeenMs = System.currentTimeMillis();
            this.lastRotationGameTime = -1L;
        }
    }

    /**
     * Register or refresh a frame anchor in the registry.
     *
     * @param dimensionId dimension identifier
     * @param framePos position of the frame anchor
     * @param chest associated container (may be null)
     * @param hoe stored hoe ItemStack (may be ItemStack.EMPTY)
     */
    public static synchronized void registerFrame(String dimensionId, BlockPos framePos, Container chest, ItemStack hoe) {
        Map<BlockPos, FrameEntry> map = framesByDimension.computeIfAbsent(dimensionId, k -> new HashMap<>());
        // Always store a defensive copy of the incoming ItemStack to avoid
        // accidental shared-mutation when callers retain references.
        FrameScanner.Anchor anchor = new FrameScanner.Anchor(chest, framePos, hoe == null ? ItemStack.EMPTY : hoe.copy());
        FrameEntry existing = map.get(framePos);
        if (existing == null) {
            map.put(framePos, new FrameEntry(anchor));
            if (anchor.hoe != null && !anchor.hoe.isEmpty()) {
                Constants.logDebug("[REG] Registered active frame at {} in {}.", framePos, dimensionId);
            } else {
                Constants.logDebug("[REG] Registered inactive frame at {} in {}.", framePos, dimensionId);
            }
        } else {
            // boolean wasActive = existing.active;
            if (hoe != null && !hoe.isEmpty()) existing.active = true;
            existing.lastSeenMs = System.currentTimeMillis();
            existing.ticksUntilNextRun = Math.min(existing.ticksUntilNextRun, Config.tickInterval);
            Constants.logDebug("[REG] Refreshed frame at {} in {}. active={}", framePos, dimensionId, existing.active);
        }
        try {
            long chunkKey = computeChunkKey(framePos);
            CHUNK_INDEX.computeIfAbsent(chunkRegistryKey(dimensionId, chunkKey), ignored -> new HashSet<>()).add(framePos);
        } catch (Throwable ignored) {}
    }

    /**
     * Unregister a frame anchor from the registry.
     *
     * @param dimensionId dimension identifier
     * @param framePos position of the frame anchor to remove
     */
    public static synchronized void unregisterFrame(String dimensionId, BlockPos framePos) {
        Map<BlockPos, FrameEntry> map = framesByDimension.get(dimensionId);
        if (map == null) return;
        if (map.remove(framePos) != null) {
            Constants.logDebug("[REG] Unregistered frame at {} in {}.", framePos, dimensionId);
        }
    }

    /**
     * Activate frames discovered in a chunk scan.
     *
     * @param dimensionId dimension identifier
     * @param chunkKey chunk key computed from a BlockPos
     * @param discoveredFrames list of frame positions discovered in the chunk
     */
    public static synchronized void activateChunkFrames(String dimensionId, long chunkKey, List<BlockPos> discoveredFrames) {
        String chunkRegistryKey = chunkRegistryKey(dimensionId, chunkKey);
        Set<BlockPos> known = CHUNK_INDEX.computeIfAbsent(chunkRegistryKey, ignored -> new HashSet<>());
        for (BlockPos p : discoveredFrames) known.add(p);
    }

    /**
     * Reconcile chunk-indexed frames with a fresh discovery list.
     *
     * @param dimensionId dimension identifier
     * @param chunkKey chunk key
     * @param discoveredFrames list of discovered frame positions
     * @return list of positions that were deactivated
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
    public static synchronized void setCooldown(String dimensionId, BlockPos framePos, int ticks) {
        Map<BlockPos, FrameEntry> map = framesByDimension.get(dimensionId);
        if (map == null) return;
        FrameEntry fe = map.get(framePos);
        if (fe == null) return;
        fe.ticksUntilNextRun = Math.max(0, ticks);
        fe.active = true;
        Constants.logDebug("[REG] Set cooldown for {} in {}: {} ticks", framePos, dimensionId, ticks);
    }

    /**
     * Update the stored hoe for a registered frame anchor.
     *
     * @param dimensionId dimension identifier
     * @param framePos frame position
     * @param hoe replacement hoe ItemStack
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
            replacement.ticksUntilNextRun = 0;
        } else {
            replacement.ticksUntilNextRun = old.ticksUntilNextRun;
        }
        replacement.lastSeenMs = old.lastSeenMs;
        replacement.lastRotationGameTime = old.lastRotationGameTime;
        map.put(framePos, replacement);
        if (!old.active && replacement.active) {
            Constants.logDebug("[REG] Activated frame at {} in {} via replacement.", framePos, dimensionId);
        } else {
            Constants.logDebug("[REG] Updated hoe for {} in {}.", framePos, dimensionId);
        }
    }

    /**
     * Record a rotation attempt time for a registered frame.
     *
     * @param dimensionId dimension identifier
     * @param framePos frame position
     * @param gameTime game time of the attempt
     * @return true if the frame exists and the time was recorded
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
     * Schedule a rotation to be applied (batched per-tick).
     *
     * @param dimensionId dimension identifier
     * @param framePos frame position
     * @param rotation rotation value (0-7)
     * @param requestGameTime game time of the request
     */
    public static synchronized void scheduleRotation(String dimensionId, BlockPos framePos, int rotation, long requestGameTime) {
        Map<BlockPos, FrameEntry> frames = framesByDimension.get(dimensionId);
        if (frames != null) {
            FrameEntry fe = frames.get(framePos);
            if (fe != null) {
                // If a full rotation animation is active for this frame, skip scheduling
                if (fe.animating) {
                    try { Constants.logDebug("[ROT] scheduleRotation SKIP (animating) for {} in {} -> {} (gametime={})", framePos, dimensionId, rotation & 7, requestGameTime); } catch (Throwable ignored) {}
                    return;
                }
                fe.lastRotationGameTime = requestGameTime;
                try { Constants.logDebug("[ROT] scheduleRotation queued for {} in {} -> {} (gametime={})", framePos, dimensionId, rotation & 7, requestGameTime); } catch (Throwable ignored) {}
            }
        }
        Map<BlockPos, Integer> map = PENDING_ROTATIONS.computeIfAbsent(dimensionId, k -> new HashMap<>());
        map.put(framePos, rotation & 7);
    }

    /**
     * Mark/unmark a frame as currently animating. When animating, scheduled rotations are ignored.
     */
    public static synchronized void setAnimating(String dimensionId, BlockPos framePos, boolean animating) {
        Map<BlockPos, FrameEntry> map = framesByDimension.get(dimensionId);
        if (map == null) return;
        FrameEntry fe = map.get(framePos);
        if (fe == null) return;
        fe.animating = animating;
        try { Constants.logDebug("[ROT] setAnimating {} for {} in {}", animating, framePos, dimensionId); } catch (Throwable ignored) {}
        if (animating) {
            // remove any pending scheduled rotation for this frame to avoid conflicts
            Map<BlockPos, Integer> pending = PENDING_ROTATIONS.get(dimensionId);
            if (pending != null) {
                Integer prev = pending.remove(framePos);
                try { Constants.logDebug("[ROT] Removed pending rotation for {} in {} -> {}", framePos, dimensionId, prev); } catch (Throwable ignored) {}
            } else {
                try { Constants.logDebug("[ROT] No pending rotations map for {} in {}", framePos, dimensionId); } catch (Throwable ignored) {}
            }
        }
    }

    /** Clear all registry data across all dimensions. */
    public static synchronized void clearAll() {
        framesByDimension.clear();
        CHUNK_INDEX.clear();
        Constants.logDebug("[REG] Cleared all frame registry data.");
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

        Constants.logDebug("[REG] Cleared {} anchors and {} chunk entries for dimension {}.", removedAnchors, removedKeys.size(), dimensionId);
    }

    /**
     * Advance internal timers and collect anchors that are ready to run this tick.
     * Applies pending auto-replacements and flushes scheduled rotations.
     *
     * @param dimensionId dimension identifier
     * @param level server level context
     * @return list of anchors ready to run this tick
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
                if (stored == null || stored.isEmpty()) {
                    try {
                        HarvestContext ctx = new HarvestContext(fe.anchor, level, net.minecraft.world.item.ItemStack.EMPTY, fe.anchor.chest, null);
                        FrameHoeReplacement.tryReplaceBrokenHoe(ctx);
                    } catch (Throwable t) {
                        Constants.logDebug("[REG] Failed to attempt auto-replacement for " + pos, t);
                    }
                }
            } catch (Throwable ignored) {}
        }

        for (FrameEntry fe : map.values()) {
            if (!fe.active) continue;
            fe.ticksUntilNextRun--;
            if (fe.ticksUntilNextRun <= 0) {
                fe.ticksUntilNextRun = Config.tickInterval;
                ready.add(fe.anchor);
            }
        }

        try {
            Map<BlockPos, Integer> pending = PENDING_ROTATIONS.remove(dimensionId);
            if (pending != null && !pending.isEmpty()) {
                try { Constants.logDebug("[ROT] Flushing {} pending rotations for {}", pending.size(), dimensionId); } catch (Throwable ignored) {}
                for (Map.Entry<BlockPos, Integer> p : pending.entrySet()) {
                        try {
                            FrameEntry fe = map.get(p.getKey());
                            if (fe != null && fe.animating) {
                                try { Constants.logDebug("[ROT] Skipping pending rotation for {} because animating=true", p.getKey()); } catch (Throwable ignored) {}
                                continue;
                            }
                            FrameScanner.applyScheduledRotation(level, p.getKey(), p.getValue());
                        } catch (Throwable t) {
                            Constants.logDebug("[ROT] Failed to apply scheduled rotation for " + p.getKey(), t);
                        }
                    }
            }
        } catch (Throwable ignored) {}

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
