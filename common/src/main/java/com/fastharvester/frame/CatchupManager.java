package com.fastharvester.frame;

import com.fastharvester.Constants;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.Map;
import java.util.Queue;
import java.util.List;

/**
 * CatchupManager: a small helper that queues discovered candidate positions (vanilla frames
 * and FIF block-entities) for gradual processing so discovery work is spread across ticks.
 */
public final class CatchupManager {
    private CatchupManager() {}

    private static final Map<String, Queue<BlockPos>> vanillaQueues = new ConcurrentHashMap<>();
    private static final Map<String, Queue<BlockPos>> fifQueues = new ConcurrentHashMap<>();

    public static void enqueueVanillaPositions(ServerLevel level, String dimId, List<BlockPos> positions) {
        if (positions == null || positions.isEmpty() || dimId == null) return;
        Queue<BlockPos> q = vanillaQueues.computeIfAbsent(dimId, k -> new ConcurrentLinkedQueue<>());
        q.addAll(positions);
        try { Constants.logDebug("[CATCHUP] Enqueued {} vanilla positions for {} (queue size={})", positions.size(), dimId, q.size()); } catch (Throwable ignored) {}
    }

    public static void enqueueFifPositions(ServerLevel level, String dimId, List<BlockPos> positions) {
        if (positions == null || positions.isEmpty() || dimId == null) return;
        Queue<BlockPos> q = fifQueues.computeIfAbsent(dimId, k -> new ConcurrentLinkedQueue<>());
        q.addAll(positions);
        try { Constants.logDebug("[CATCHUP] Enqueued {} FIF positions for {} (queue size={})", positions.size(), dimId, q.size()); } catch (Throwable ignored) {}
    }

    public static void queueLoadedFrames(ServerLevel level, String dimId) {
        try {
            if (level == null || dimId == null) return;
            // Light-weight discovery: add any loaded item frames' positions to the vanilla queue.
            java.util.List<ItemFrame> frames = level.getEntitiesOfClass(ItemFrame.class, new AABB(-30000000, 0, -30000000, 30000000, 256, 30000000));
            if (frames == null || frames.isEmpty()) return;
            Queue<BlockPos> q = vanillaQueues.computeIfAbsent(dimId, k -> new ConcurrentLinkedQueue<>());
            for (ItemFrame f : frames) { try { q.add(f.blockPosition()); } catch (Throwable ignored) {} }
            try { Constants.logDebug("[CATCHUP] queueLoadedFrames added {} frames for {} (queueSize={})", frames.size(), dimId, q.size()); } catch (Throwable ignored) {}
        } catch (Throwable t) {
            Constants.logDebug("[CATCHUP] queueLoadedFrames failed", t);
        }
    }

    public static void processBatch(ServerLevel level, String dimId, int maxToProcess) {
        if (level == null || dimId == null || maxToProcess <= 0) return;
        Queue<BlockPos> vq = vanillaQueues.computeIfAbsent(dimId, k -> new ConcurrentLinkedQueue<>());
        Queue<BlockPos> fq = fifQueues.computeIfAbsent(dimId, k -> new ConcurrentLinkedQueue<>());

        int processed = 0;
        while (processed < maxToProcess) {
            BlockPos p = vq.poll();
            if (p != null) {
                try {
                    java.util.List<ItemFrame> frames = level.getEntitiesOfClass(ItemFrame.class, new AABB(p.getX(), p.getY(), p.getZ(), p.getX()+1, p.getY()+1, p.getZ()+1));
                    if (frames != null && !frames.isEmpty()) {
                        for (ItemFrame f : frames) {
                            try { FrameDiscovery.registerVanillaFrameIfValid(dimId, level, f); } catch (Throwable ignored) {}
                        }
                    }
                } catch (Throwable t) { Constants.logDebug("[CATCHUP] Failed to process vanilla pos {}", t); }
                processed++;
                continue;
            }

            p = fq.poll();
            if (p != null) {
                try {
                    BlockEntity be = level.getBlockEntity(p);
                    if (be != null) { try { FrameDiscovery.registerFIFIfValid(dimId, level, be, p); } catch (Throwable ignored) {} }
                } catch (Throwable t) { Constants.logDebug("[CATCHUP] Failed to process FIF pos {}", t); }
                processed++;
                continue;
            }

            // nothing left to process
            break;
        }
        try { Constants.logDebug("[CATCHUP] processBatch({}, {}) processed {} entries", dimId, maxToProcess, processed); } catch (Throwable ignored) {}
    }
}
