package com.forgetmecrops.frame;

import com.forgetmecrops.util.log.LogUtils;
import com.forgetmecrops.util.ExceptionHandler;
import com.forgetmecrops.util.LevelHeightBounds;
import com.forgetmecrops.util.ValidationUtils;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.entity.player.Player;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.Map;
import java.util.Queue;
import java.util.List;
import net.minecraft.world.level.chunk.LevelChunk;
import com.forgetmecrops.platform.adapter.FastItemFrameAdapterImpl;

/**
 * CatchupManager: The backlog handler that keeps first-tick discovery from melting the server!
 * <p>
 * When a chunk loads or the world starts for the first time, there may be many existing
 * item-frame anchors and FastItemFrames block-entities waiting to be discovered. Rather than
 * validating them all in a single tick (which would be extremely rude to TPS), CatchupManager
 * queues their positions for gradual processing spread across many ticks.
 * </p>
 * <p>
 * Maintains separate ConcurrentLinkedQueues for vanilla ItemFrame candidates and FIF
 * block-entity candidates per dimension. The ticker drains these queues at a controlled rate
 * so even heavily populated worlds don't cause a startup TPS spike. Your server will thank you.
 * The crops might even wave goodbye as they're harvested.
 * </p>
 */
public final class CatchupManager {
    // Utility class. The catchup manager catches up; it doesn't catch itself.
    private CatchupManager() {}

    // Per-dimension queues for vanilla ItemFrame positions awaiting validation
    private static final Map<String, Queue<BlockPos>> vanillaQueues = new ConcurrentHashMap<>();
    // Per-dimension queues for FastItemFrames block-entity positions awaiting validation
    private static final Map<String, Queue<BlockPos>> fifQueues = new ConcurrentHashMap<>();

    /**
     * Enqueues a list of vanilla ItemFrame positions discovered during a chunk-load event.
     * They'll be validated gradually by the ticker's drain loop — not all at once.
     * Positions are added to a ConcurrentLinkedQueue so concurrent chunk events don't collide.
     *
     * @param level     the source server level (used by drain callers, stored implicitly via dimId)
     * @param dimId     dimension identifier
     * @param positions list of candidate frame positions to validate later
     */
    public static void enqueueVanillaPositions(ServerLevel level, String dimId, List<BlockPos> positions) {
        if (ValidationUtils.isNullOrEmpty(positions) || dimId == null) return;
        Queue<BlockPos> q = vanillaQueues.computeIfAbsent(dimId, k -> new ConcurrentLinkedQueue<>());
        q.addAll(positions);
        ExceptionHandler.silentTry(() -> LogUtils.logDebug("[CATCHUP] Enqueued {} vanilla positions for {} (queue size={})", positions.size(), dimId, q.size()));
    }

    /**
     * Enqueues a list of FastItemFrames block-entity positions discovered during a chunk-load event.
     * Same gradual-processing pattern as vanilla positions — they drain in a controlled way.
     *
     * @param level     the source server level
     * @param dimId     dimension identifier
     * @param positions list of candidate FIF block-entity positions
     */
    public static void enqueueFifPositions(ServerLevel level, String dimId, List<BlockPos> positions) {
        if (ValidationUtils.isNullOrEmpty(positions) || dimId == null) return;
        Queue<BlockPos> q = fifQueues.computeIfAbsent(dimId, k -> new ConcurrentLinkedQueue<>());
        q.addAll(positions);
        ExceptionHandler.silentTry(() -> LogUtils.logDebug("[CATCHUP] Enqueued {} FIF positions for {} (queue size={})", positions.size(), dimId, q.size()));
    }

    /**
     * Queues ALL currently loaded frames in the given level for gradual catch-up processing.
     * Called during first-world-load or periodic rediscovery passes.
     * Vanilla frames are enqueued via position list; FIF block-entities are discovered by iterating
     * loaded chunks. Both paths add positions to their respective queues for deferred validation.
     * This method does NOT validate them immediately — that's the ticker's job.
     *
     * @param level the server level to scan for existing frames
     * @param dimId the dimension identifier for queue scoping
     */
    public static void queueLoadedFrames(ServerLevel level, String dimId) {
        if (level == null || dimId == null) return;

        try {
            int scanMinY = LevelHeightBounds.minY(level);
            int scanMaxYExclusive = LevelHeightBounds.maxYExclusive(level);
            LogUtils.logDebug(
                "[CATCHUP] queueLoadedFrames start for {} (scanY={}..{}, playerCount={})",
                dimId,
                scanMinY,
                scanMaxYExclusive,
                level.players().size()
            );

            java.util.List<ItemFrame> frames = level.getEntitiesOfClass(
                ItemFrame.class,
                new AABB(-30000000, scanMinY, -30000000, 30000000, scanMaxYExclusive, 30000000)
            );

            LogUtils.logDebug("[CATCHUP] queueLoadedFrames candidate vanilla frames in {}: {}", dimId, frames.size());

            Queue<BlockPos> q = vanillaQueues.computeIfAbsent(dimId, k -> new ConcurrentLinkedQueue<>());
            Queue<BlockPos> fq = fifQueues.computeIfAbsent(dimId, k -> new ConcurrentLinkedQueue<>());

            int vanillaDiscovered = 0;
            if (!ValidationUtils.isNullOrEmpty(frames)) {
                for (ItemFrame f : frames) {
                    ExceptionHandler.silentTry(() -> FrameDiscovery.registerVanillaFrameIfValid(dimId, level, f));
                    q.add(f.blockPosition());
                    vanillaDiscovered++;
                }
            }

            int fifDiscovered = 0;
            try {
                List<LevelChunk> loadedChunks = FastItemFrameAdapterImpl.getLoadedChunks(level);
                if (!ValidationUtils.isNullOrEmpty(loadedChunks)) {
                    for (LevelChunk chunk : loadedChunks) {
                        for (BlockEntity be : chunk.getBlockEntities().values()) {
                            if (!FastItemFrameAdapterImpl.isFastItemFrameBlockEntity(be)) continue;
                            BlockPos pos = be.getBlockPos();
                            ExceptionHandler.silentTry(() -> FrameDiscovery.registerFIFIfValid(dimId, level, be, pos));
                            fq.add(pos);
                            fifDiscovered++;
                        }
                    }
                }
            } catch (Throwable t) {
                ExceptionHandler.silentTry(() -> LogUtils.logTrace("[CATCHUP] FIF loaded-chunk scan failed: {}", t.getMessage()));
            }

            // Fallback: if reflective loaded-chunk scan returned nothing, do a
            // player-area scan for FIF block-entities to recover anchors after reload.
            if (fifDiscovered == 0) {
                try {
                    int fallbackRadiusBlocks = 32;
                    int step = 2;
                    java.util.List<? extends Player> players = level.players();

                    if (!ValidationUtils.isNullOrEmpty(players)) {
                        ExceptionHandler.silentTry(() -> LogUtils.logTrace("[CATCHUP] FIF fallback: scanning around {} players with radius {}", players.size(), fallbackRadiusBlocks));

                        for (Player pl : players) {
                            BlockPos center = pl.blockPosition();
                            int minX = center.getX() - fallbackRadiusBlocks;
                            int maxX = center.getX() + fallbackRadiusBlocks;
                            int minZ = center.getZ() - fallbackRadiusBlocks;
                            int maxZ = center.getZ() + fallbackRadiusBlocks;
                            int minY = Math.max(scanMinY, center.getY() - 2);
                            int maxY = Math.min(scanMaxYExclusive - 1, center.getY() + 2);

                            for (int x = minX; x <= maxX; x += step) {
                                for (int z = minZ; z <= maxZ; z += step) {
                                    for (int y = minY; y <= maxY; y++) {
                                        BlockPos pos = new BlockPos(x, y, z);
                                        BlockEntity be = level.getBlockEntity(pos);
                                        if (be == null) continue;
                                        if (!FastItemFrameAdapterImpl.isFastItemFrameBlockEntity(be)) continue;
                                        ExceptionHandler.silentTry(() -> FrameDiscovery.registerFIFIfValid(dimId, level, be, pos));
                                        fq.add(pos);
                                        fifDiscovered++;
                                    }
                                }
                            }
                        }
                    }

                    if (fifDiscovered > 0) {
                        try {
                            LogUtils.logDebug("[CATCHUP] FIF fallback found {} block-entities for {}", fifDiscovered, dimId);
                        } catch (Throwable ignored) {}
                    } else if (!ValidationUtils.isNullOrEmpty(players)) {
                        // One deeper pass around the first player if the coarse scan found nothing.
                        int thoroughRadius = 48;
                        int thoroughStep = 1;
                        Player first = players.get(0);

                        ExceptionHandler.silentTry(() -> LogUtils.logTrace("[CATCHUP] FIF thorough fallback: scanning around player {} with radius {}", first.getName().getString(), thoroughRadius));

                        BlockPos center = first.blockPosition();
                        int minX = center.getX() - thoroughRadius;
                        int maxX = center.getX() + thoroughRadius;
                        int minZ = center.getZ() - thoroughRadius;
                        int maxZ = center.getZ() + thoroughRadius;
                        int minY = Math.max(scanMinY, center.getY() - 3);
                        int maxY = Math.min(scanMaxYExclusive - 1, center.getY() + 3);

                        for (int x = minX; x <= maxX; x += thoroughStep) {
                            for (int z = minZ; z <= maxZ; z += thoroughStep) {
                                for (int y = minY; y <= maxY; y++) {
                                    BlockPos pos = new BlockPos(x, y, z);
                                    BlockEntity be = level.getBlockEntity(pos);
                                    if (be == null) continue;
                                    if (!FastItemFrameAdapterImpl.isFastItemFrameBlockEntity(be)) continue;
                                    ExceptionHandler.silentTry(() -> FrameDiscovery.registerFIFIfValid(dimId, level, be, pos));
                                    fq.add(pos);
                                    fifDiscovered++;
                                }
                            }
                        }

                        if (fifDiscovered > 0) {
                            try {
                                LogUtils.logDebug("[CATCHUP] FIF thorough fallback found {} block-entities for {}", fifDiscovered, dimId);
                            } catch (Throwable ignored) {}
                        }
                    }
                } catch (Throwable t) {
                    ExceptionHandler.silentTry(() -> LogUtils.logTrace("[CATCHUP] FIF fallback scan failed: {}", t.getMessage()));
                }
            }

            try {
                LogUtils.logDebug(
                    "[CATCHUP] queueLoadedFrames added {} vanilla frames and {} FIF block-entities for {} (vanillaQ={} fifQ={})",
                    vanillaDiscovered,
                    fifDiscovered,
                    dimId,
                    q.size(),
                    fq.size()
                );
            } catch (Throwable ignored) {}
        } catch (Throwable t) {
            LogUtils.logTrace("[CATCHUP] queueLoadedFrames failed", t);
        }
    }

    /**
     * Processes at most {@code maxToProcess} queued candidate positions for the given dimension.
     *
     * @param level source level
     * @param dimId dimension id
     * @param maxToProcess maximum positions to process this tick
     */
    public static void processBatch(ServerLevel level, String dimId, int maxToProcess) {
        if (ValidationUtils.isAnyNull(level, dimId) || maxToProcess <= 0) return;
        Queue<BlockPos> vq = vanillaQueues.computeIfAbsent(dimId, k -> new ConcurrentLinkedQueue<>());
        Queue<BlockPos> fq = fifQueues.computeIfAbsent(dimId, k -> new ConcurrentLinkedQueue<>());

        int processed = 0;
        while (processed < maxToProcess) {
            BlockPos p = vq.poll();
            if (p != null) {
                try {
                    java.util.List<ItemFrame> frames = level.getEntitiesOfClass(ItemFrame.class, new AABB(p.getX(), p.getY(), p.getZ(), p.getX()+1, p.getY()+1, p.getZ()+1));
                    if (!ValidationUtils.isNullOrEmpty(frames)) {
                        for (ItemFrame f : frames) {
                            ExceptionHandler.silentTry(() -> FrameDiscovery.registerVanillaFrameIfValid(dimId, level, f));
                        }
                    }
                } catch (Throwable t) {
                    try {
                        LogUtils.logTrace("[CATCHUP] Failed to process vanilla pos {}: {}", p, t.getMessage());
                    } catch (Throwable ignored) {}
                }
                processed++;
                continue;
            }

            p = fq.poll();
            if (p != null) {
                try {
                    final BlockPos fp = p;
                    BlockEntity be = level.getBlockEntity(fp);
                    if (be != null) {
                        ExceptionHandler.silentTry(() -> FrameDiscovery.registerFIFIfValid(dimId, level, be, fp));
                    }
                } catch (Throwable ignored) {}
                processed++;
                continue;
            }

            // nothing left to process
            break;
        }
        try {
            if (processed > 0 || !vq.isEmpty() || !fq.isEmpty()) {
                LogUtils.logDebug(
                    "[CATCHUP] processBatch({}, {}) processed {} entries (remaining: vanillaQ={}, fifQ={})",
                    dimId,
                    maxToProcess,
                    processed,
                    vq.size(),
                    fq.size()
                );
            }
        } catch (Throwable ignored) {}
    }
}
