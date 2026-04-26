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
            Constants.logInfo("[TICK] Queuing loaded item frames in {}", dimId);
            AABB worldBox = new AABB(-30000000, 0, -30000000, 30000000, 256, 30000000);
            List<ItemFrame> loadedFrames = level.getEntitiesOfClass(ItemFrame.class, worldBox);
            Deque<BlockPos> q = new ArrayDeque<>();
            for (ItemFrame f : loadedFrames) q.addLast(f.blockPosition());
            queues.put(dimId, q);
            initialCounts.put(dimId, q.size());
            Constants.logInfo("[TICK] Queued {} item frames for gradual processing in {}", q.size(), dimId);
        } catch (Throwable t) {
            Constants.logWarn("[TICK] Catch-up queueing error", t);
        }
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
        if (dq == null || dq.isEmpty()) return;
        int initial = initialCounts.getOrDefault(dimId, dq.size());
        int batch = Math.max(1, (initial + catchupTicks - 1) / catchupTicks);
        int processed = 0;
        while (processed < batch && !dq.isEmpty()) {
            BlockPos p = dq.removeFirst();
            try {
                List<ItemFrame> framesAtPos = level.getEntitiesOfClass(ItemFrame.class, new AABB(p));
                ItemFrame found = null;
                for (ItemFrame ff : framesAtPos) { if (ff.blockPosition().equals(p)) { found = ff; break; } }
                if (found == null) { Constants.logInfo("[TICK] Catch-up: no frame at {}", p); processed++; continue; }
                // Delegate validation & registration to FrameDiscovery
                FrameDiscovery.registerVanillaFrameIfValid(dimId, level, found);
            } catch (Throwable t) { Constants.logWarn("[TICK] Catch-up per-pos error", t); }
            processed++;
        }
        if (dq.isEmpty()) { Constants.logInfo("[TICK] Catch-up processing complete for {}", dimId); queues.remove(dimId); initialCounts.remove(dimId); }
    }
}
