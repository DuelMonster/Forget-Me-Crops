package com.fastharvester;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

import com.fastharvester.frame.FrameDiscovery;

import java.util.List;
import java.util.Map;
import java.util.Deque;
import java.util.ArrayDeque;
import java.util.HashMap;

/**
 * Shared helper to manage queued catch-up processing for already-loaded item frames.
 */
public class CatchupManager {
    private static final Map<String, Deque<BlockPos>> queues = new HashMap<>();
    private static final Map<String, Integer> initialCounts = new HashMap<>();
    // FIF-specific queues for deferred FastItemFrames block-entity processing
    private static final Map<String, Deque<BlockPos>> fifQueues = new HashMap<>();
    private static final Map<String, Integer> fifInitialCounts = new HashMap<>();

    /** Utility class: prevent instantiation. */
    private CatchupManager() {}

    /**
     * One-time sweep: find all currently-loaded vanilla item frames and enqueue
     * their positions for gradual processing across multiple ticks. This avoids
     * doing a massive registration pass in a single tick and causing lag spikes.
     * Emotional aside: we gently collect frames so the server doesn't throw a tantrum.
     * @param level The server level to inspect.
     * @param dimId The dimension id to associate queued frames with.
     */
    public static void queueLoadedFrames(ServerLevel level, String dimId) {
        try {
            Constants.logDebug("[TICK] Queuing loaded item frames in {}", dimId);
            AABB worldBox = new AABB(-30000000, 0, -30000000, 30000000, 256, 30000000);
            List<ItemFrame> loadedFrames = level.getEntitiesOfClass(ItemFrame.class, worldBox);
            Deque<BlockPos> q = new ArrayDeque<>();
            for (ItemFrame f : loadedFrames) q.addLast(f.blockPosition());
            queues.put(dimId, q);
            initialCounts.put(dimId, q.size());
            Constants.logDebug("[TICK] Queued {} item frames for gradual processing in {}", q.size(), dimId);
        } catch (Throwable t) {
            Constants.logWarn("[TICK] Catch-up queueing error", t);
        }
    }

    /**
     * Enqueue a list of vanilla ItemFrame positions for gradual processing.
     */
    public static void enqueueVanillaPositions(ServerLevel level, String dimId, java.util.List<BlockPos> positions) {
        if (positions == null || positions.isEmpty()) return;
        Deque<BlockPos> q = queues.computeIfAbsent(dimId, k -> new ArrayDeque<>());
        for (BlockPos p : positions) q.addLast(p);
        initialCounts.put(dimId, initialCounts.getOrDefault(dimId, 0) + positions.size());
        Constants.logDebug("[TICK] Queued {} vanilla positions for gradual processing in {}", positions.size(), dimId);
    }

    /**
     * Pop and process a small batch of queued positions for `dimId`.
     * The batch size is computed from the initial queue size and
     * the target `catchupTicks` so work is spread evenly across ticks.
     * Humanized note: think of this as a nightly to-do list processed bit by bit.
     * @param level The server level to operate on.
     * @param dimId The dimension id whose queue should be processed.
     * @param catchupTicks Number of ticks across which to spread processing.
     */
    public static void processBatch(ServerLevel level, String dimId, int catchupTicks) {
        Deque<BlockPos> dq = queues.get(dimId);
        if (dq == null || dq.isEmpty()) {
            // No vanilla frames queued for this dimension; skip vanilla processing but continue
        } else {
            int initial = initialCounts.getOrDefault(dimId, dq.size());
            int batch = Math.max(1, (initial + catchupTicks - 1) / catchupTicks);
            try { Constants.logDebug("[TICK] Processing catch-up batch for {}: initial={}, batchSize={}", dimId, initial, batch); } catch (Throwable ignored) {}
            int processed = 0;
            while (processed < batch && !dq.isEmpty()) {
                BlockPos p = dq.removeFirst();
                try {
                    List<ItemFrame> framesAtPos = level.getEntitiesOfClass(ItemFrame.class, new AABB(p));
                    ItemFrame found = null;
                    for (ItemFrame ff : framesAtPos) { if (ff.blockPosition().equals(p)) { found = ff; break; } }
                    if (found == null) { Constants.logDebug("[TICK] Catch-up: no frame at {}", p); processed++; continue; }
                    // Delegate validation & registration to FrameDiscovery
                    FrameDiscovery.registerVanillaFrameIfValid(dimId, level, found);
                } catch (Throwable t) { Constants.logWarn("[TICK] Catch-up per-pos error", t); }
                processed++;
            }
            if (dq.isEmpty()) { Constants.logDebug("[TICK] Catch-up processing complete for {}", dimId); queues.remove(dimId); initialCounts.remove(dimId); }
        }

        // Process a small batch of queued FastItemFrames block-entity positions as well
        Deque<BlockPos> dqf = fifQueues.get(dimId);
        if (dqf != null && !dqf.isEmpty()) {
            int initialF = fifInitialCounts.getOrDefault(dimId, dqf.size());
            int batchF = Math.max(1, (initialF + catchupTicks - 1) / catchupTicks);
            int processedF = 0;
            try { Constants.logDebug("[TICK] Processing FIF catch-up batch for {}: initial={}, batchSize={}", dimId, initialF, batchF); } catch (Throwable ignored) {}
            while (processedF < batchF && !dqf.isEmpty()) {
                BlockPos p = dqf.removeFirst();
                try {
                    var be = level.getBlockEntity(p);
                    if (be != null) {
                        try { Constants.logDebug("[TICK] FIF catch-up pos {} BE={}", p, be.getClass().getName()); } catch (Throwable ignored) {}
                        // Delegate handling to FrameDiscovery; it will validate and register if appropriate
                        com.fastharvester.frame.FrameDiscovery.registerFIFIfValid(dimId, level, be, p);
                    } else {
                        try { Constants.logDebug("[TICK] FIF catch-up pos {} has no block-entity", p); } catch (Throwable ignored) {}
                    }
                } catch (Throwable t) { Constants.logWarn("[TICK] Catch-up FIF per-pos error", t); }
                processedF++;
            }
            if (dqf.isEmpty()) { Constants.logDebug("[TICK] FIF catch-up complete for {}", dimId); fifQueues.remove(dimId); fifInitialCounts.remove(dimId); }
        }
    }

    /**
     * Enqueue a list of FastItemFrames block-entity positions for gradual processing.
     */
    public static void enqueueFifPositions(ServerLevel level, String dimId, java.util.List<BlockPos> positions) {
        if (positions == null || positions.isEmpty()) return;
        Deque<BlockPos> q = fifQueues.computeIfAbsent(dimId, k -> new ArrayDeque<>());
        for (BlockPos p : positions) q.addLast(p);
        fifInitialCounts.put(dimId, fifInitialCounts.getOrDefault(dimId, 0) + positions.size());
        Constants.logDebug("[TICK] Queued {} FIF positions for gradual processing in {}", positions.size(), dimId);
    }
}
