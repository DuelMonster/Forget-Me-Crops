package com.fastharvester;

// 📚 FrameRegistry: keeps track of anchors like a diligent librarian who loves chest-and-hoe pairings.
// Why it matters: without registration, farms would be shy and un-scheduled.

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

    public static class FrameEntry {
        public final FrameScanner.Anchor anchor;
        public boolean active;
        public int ticksUntilNextRun;
        public long lastSeenMs;

        /**
         * FrameEntry: holds scheduling state for a discovered anchor.
         * Emotional aside: think of this as the anchor's calendar and mood tracker.
         */
        FrameEntry(FrameScanner.Anchor anchor) {
            this.anchor = anchor;
            this.active = true;
            this.ticksUntilNextRun = Config.tickInterval;
            this.lastSeenMs = System.currentTimeMillis();
        }
    }

    /**
     * Register or refresh an anchor discovered at the given frame position.
     * Humanized note: when a frame is found we either add it to the registry or
     * refresh its timer so it doesn't feel forgotten.
     */
    public static synchronized void registerFrame(String dimensionId, BlockPos framePos, Container chest, ItemStack hoe) {
        Map<BlockPos, FrameEntry> map = framesByDimension.computeIfAbsent(dimensionId, k -> new HashMap<>());
        FrameScanner.Anchor anchor = new FrameScanner.Anchor(chest, framePos, hoe);
        FrameEntry existing = map.get(framePos);
        if (existing == null) {
            map.put(framePos, new FrameEntry(anchor));
            Constants.LOG.info("[FastHarvester][REG] Registered frame at {} in {}.", framePos, dimensionId);
        } else {
            existing.active = true;
            existing.lastSeenMs = System.currentTimeMillis();
            existing.ticksUntilNextRun = Math.min(existing.ticksUntilNextRun, Config.tickInterval);
            Constants.LOG.info("[FastHarvester][REG] Refreshed frame at {} in {}.", framePos, dimensionId);
        }
        // Also maintain chunk index for discovered frames
        try {
            long chunkKey = computeChunkKey(framePos);
            CHUNK_INDEX.computeIfAbsent(chunkRegistryKey(dimensionId, chunkKey), ignored -> new HashSet<>()).add(framePos);
        } catch (Throwable ignored) {}
    }

    /**
     * Unregister an anchor when it's no longer present (e.g. chunk unload).
     * Emotional aside: we politely forget anchors that leave, so the registry stays tidy.
     */
    public static synchronized void unregisterFrame(String dimensionId, BlockPos framePos) {
        Map<BlockPos, FrameEntry> map = framesByDimension.get(dimensionId);
        if (map == null) return;
        if (map.remove(framePos) != null) {
            Constants.LOG.info("[FastHarvester][REG] Unregistered frame at {} in {}.", framePos, dimensionId);
        }
    }

    public static synchronized void activateChunkFrames(String dimensionId, long chunkKey, List<BlockPos> discoveredFrames) {
        String chunkRegistryKey = chunkRegistryKey(dimensionId, chunkKey);
        Set<BlockPos> known = CHUNK_INDEX.computeIfAbsent(chunkRegistryKey, ignored -> new HashSet<>());
        for (BlockPos p : discoveredFrames) known.add(p);
    }

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
     * Set a cooldown for the given anchor so it will not be retried for `ticks` ticks.
     */
    public static synchronized void setCooldown(String dimensionId, BlockPos framePos, int ticks) {
        Map<BlockPos, FrameEntry> map = framesByDimension.get(dimensionId);
        if (map == null) return;
        FrameEntry fe = map.get(framePos);
        if (fe == null) return;
        fe.ticksUntilNextRun = Math.max(0, ticks);
        fe.active = true;
        Constants.LOG.debug("[FastHarvester][REG] Set cooldown for {} in {}: {} ticks", framePos, dimensionId, ticks);
    }

    /**
     * Update the stored hoe for a registered frame anchor. Preserves scheduling state.
     */
    public static synchronized void updateHoe(String dimensionId, BlockPos framePos, ItemStack hoe) {
        Map<BlockPos, FrameEntry> map = framesByDimension.get(dimensionId);
        if (map == null) return;
        FrameEntry old = map.get(framePos);
        if (old == null) return;

        FrameScanner.Anchor newAnchor = new FrameScanner.Anchor(old.anchor.chest, framePos, hoe == null ? ItemStack.EMPTY : hoe.copy());
        FrameEntry replacement = new FrameEntry(newAnchor);
        replacement.active = old.active;
        replacement.ticksUntilNextRun = old.ticksUntilNextRun;
        replacement.lastSeenMs = old.lastSeenMs;
        map.put(framePos, replacement);
        Constants.LOG.info("[FastHarvester][REG] Updated hoe for {} in {}.", framePos, dimensionId);
    }

    /**
     * Clears all registry data. Call this when leaving a world to release memory and ensure a fresh registry for the next world.
     */
    public static synchronized void clearAll() {
        framesByDimension.clear();
        CHUNK_INDEX.clear();
        Constants.LOG.info("[FastHarvester][REG] Cleared all frame registry data.");
    }

    /**
     * Clears registry data for a single dimension. Removes all recorded anchors and chunk index entries for that dimension.
     */
    public static synchronized void clearDimension(String dimensionId) {
        if (dimensionId == null) return;
        Map<BlockPos, FrameEntry> removed = framesByDimension.remove(dimensionId);
        int removedAnchors = removed == null ? 0 : removed.size();

        // Remove chunk index entries for this dimension
        String prefix = dimensionId + ":";
        List<String> removedKeys = new ArrayList<>();
        for (String key : new ArrayList<>(CHUNK_INDEX.keySet())) {
            if (key.startsWith(prefix)) {
                CHUNK_INDEX.remove(key);
                removedKeys.add(key);
            }
        }

        Constants.LOG.info("[FastHarvester][REG] Cleared {} anchors and {} chunk entries for dimension {}.", removedAnchors, removedKeys.size(), dimensionId);
    }

    /**
     * Called once per server tick by the platform ticker. Decrements per-frame countdowns
     * and returns the anchors that are ready to run this tick.
     * Humanized aside: timers tick, expectations build, and the scanner gets to work.
     */
    public static synchronized List<FrameScanner.Anchor> tickAndCollectReady(String dimensionId) {
        List<FrameScanner.Anchor> ready = new ArrayList<>();
        Map<BlockPos, FrameEntry> map = framesByDimension.get(dimensionId);
        if (map == null) return ready;
        for (FrameEntry fe : map.values()) {
            if (!fe.active) continue;
            fe.ticksUntilNextRun--;
            if (fe.ticksUntilNextRun <= 0) {
                fe.ticksUntilNextRun = Config.tickInterval;
                ready.add(fe.anchor);
            }
        }
        return ready;
    }

    /**
     * Count currently active anchors in the given dimension.
     * @return number of active frames
     */
    public static synchronized int countActiveFrames(String dimensionId) {
        Map<BlockPos, FrameEntry> map = framesByDimension.get(dimensionId);
        if (map == null) return 0;
        int cnt = 0;
        for (FrameEntry fe : map.values()) if (fe.active) cnt++;
        return cnt;
    }

    /**
     * Count all recorded frames (active or not) in the given dimension.
     * @return total recorded frames
     */
    public static synchronized int countRecordedFrames(String dimensionId) {
        Map<BlockPos, FrameEntry> map = framesByDimension.get(dimensionId);
        return map == null ? 0 : map.size();
    }

    private static long computeChunkKey(BlockPos p) {
        long chunkX = p.getX() >> 4;
        long chunkZ = p.getZ() >> 4;
        return ((chunkX & 0xffffffffL) << 32) | (chunkZ & 0xffffffffL);
    }

    private static String chunkRegistryKey(String dimensionId, long chunkKey) {
        return dimensionId + ":" + chunkKey;
    }
}