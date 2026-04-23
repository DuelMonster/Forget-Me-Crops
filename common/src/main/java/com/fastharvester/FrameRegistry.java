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
}
