package com.forgetmecrops.frame;

import com.forgetmecrops.util.log.LogUtils;
import com.forgetmecrops.util.ExceptionHandler;
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
        ExceptionHandler.silentTry(() -> LogUtils.logTrace("[CATCHUP] Enqueued {} vanilla positions for {} (queue size={})", positions.size(), dimId, q.size()));
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
        ExceptionHandler.silentTry(() -> LogUtils.logTrace("[CATCHUP] Enqueued {} FIF positions for {} (queue size={})", positions.size(), dimId, q.size()));
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
        try {
            if (level == null || dimId == null) return;
            // Light-weight discovery: add any loaded item frames' positions to the vanilla queue.
            java.util.List<ItemFrame> frames = level.getEntitiesOfClass(ItemFrame.class, new AABB(-30000000, 0, -30000000, 30000000, 256, 30000000));
            Queue<BlockPos> q = vanillaQueues.computeIfAbsent(dimId, k -> new ConcurrentLinkedQueue<>());
            int vanillaDiscovered = 0;
            if (!ValidationUtils.isNullOrEmpty(frames)) {
                for (ItemFrame f : frames) {
                    ExceptionHandler.silentTry(() -> FrameDiscovery.registerVanillaFrameIfValid(dimId, level, f));
                    ExceptionHandler.silentTry(() -> q.add(f.blockPosition()));
                    vanillaDiscovered++;
                }
            }

            // FIF: iterate loaded chunks and register FastItemFrames block-entities immediately
            int fifDiscovered = 0;
            try {
                List<LevelChunk> loadedChunks = FastItemFrameAdapterImpl.getLoadedChunks(level);
                Queue<BlockPos> fq = fifQueues.computeIfAbsent(dimId, k -> new ConcurrentLinkedQueue<>());
                if (!ValidationUtils.isNullOrEmpty(loadedChunks)) {
                    for (LevelChunk chunk : loadedChunks) {
                        ExceptionHandler.silentTry(() -> {
                            for (BlockEntity be : chunk.getBlockEntities().values()) {
                                if (!FastItemFrameAdapterImpl.isFastItemFrameBlockEntity(be)) continue;
                                BlockPos pos = be.getBlockPos();
                                ExceptionHandler.silentTry(() -> FrameDiscovery.registerFIFIfValid(dimId, level, be, pos));
                                ExceptionHandler.silentTry(() -> fq.add(pos));
                                fifDiscovered++;
                            }
                        });
                    }
                }
                // Fallback: if reflective loaded-chunk scan returned nothing, do a
                // small radius player-area scan for FIF block-entities. This is
                // best-effort and intentionally conservative to avoid heavy work.
                if (fifDiscovered == 0) {
                    try {
                        int fallbackRadiusBlocks = 32; // ~2 chunks
                        int step = 2; // step to reduce checks
                        java.util.List<? extends Player> players = level.players();
                        if (!ValidationUtils.isNullOrEmpty(players)) {
                            ExceptionHandler.silentTry(() -> LogUtils.logTrace("[CATCHUP] FIF fallback: scanning around {} players with radius {}", players.size(), fallbackRadiusBlocks));
                            for (Player pl : players) {
                                ExceptionHandler.silentTry(() -> {
                                    BlockPos center = pl.blockPosition();
                                    int minX = center.getX() - fallbackRadiusBlocks;
                                    int maxX = center.getX() + fallbackRadiusBlocks;
                                    int minZ = center.getZ() - fallbackRadiusBlocks;
                                    int maxZ = center.getZ() + fallbackRadiusBlocks;
                                    int minY = Math.max(0, center.getY() - 2);
                                    int maxY = Math.min(255, center.getY() + 2);
                                    for (int x = minX; x <= maxX; x += step) {
                                        for (int z = minZ; z <= maxZ; z += step) {
                                            for (int y = minY; y <= maxY; y++) {
                                                BlockPos pos = new BlockPos(x, y, z);
                                                BlockEntity be = level.getBlockEntity(pos);
                                                if (be == null) continue;
                                                if (!FastItemFrameAdapterImpl.isFastItemFrameBlockEntity(be)) continue;
                                                ExceptionHandler.silentTry(() -> FrameDiscovery.registerFIFIfValid(dimId, level, be, pos));
                                                ExceptionHandler.silentTry(() -> fq.add(pos));
                                                fifDiscovered++;
                                            }
                                        }
                                    }
                                });
                            }
                        }
                        if (fifDiscovered > 0) {
                            ExceptionHandler.silentTry(() -> LogUtils.logDebug("[CATCHUP] FIF fallback found {} block-entities for {}", fifDiscovered, dimId));
                        } else {
                            // If the conservative fallback found nothing, perform a
                            // single, slightly more thorough pass (step=1, larger
                            // radius) to improve rediscovery reliability after
                            // world reloads where block-entities may be restored
                            // slightly later. This is still best-effort but helps
                            // catch cases where a coarse scan misses an exact pos.
                            ExceptionHandler.silentTry(() -> {
                                int thoroughRadius = 48; // broader radius
                                int thoroughStep = 1; // check every block
                                Player first = ValidationUtils.isNullOrEmpty(players) ? null : players.get(0);
                                if (first != null) {
                                    ExceptionHandler.silentTry(() -> LogUtils.logTrace("[CATCHUP] FIF thorough fallback: scanning around player {} with radius {}", first.getName().getString(), thoroughRadius));
                                    BlockPos center = first.blockPosition();
                                    int minX = center.getX() - thoroughRadius;
                                    int maxX = center.getX() + thoroughRadius;
                                    int minZ = center.getZ() - thoroughRadius;
                                    int maxZ = center.getZ() + thoroughRadius;
                                    int minY = Math.max(0, center.getY() - 3);
                                    int maxY = Math.min(255, center.getY() + 3);
                                    for (int x = minX; x <= maxX; x += thoroughStep) {
                                        for (int z = minZ; z <= maxZ; z += thoroughStep) {
                                            for (int y = minY; y <= maxY; y++) {
                                                BlockPos pos = new BlockPos(x, y, z);
                                                BlockEntity be = level.getBlockEntity(pos);
                                                if (be == null) continue;
                                                if (!FastItemFrameAdapterImpl.isFastItemFrameBlockEntity(be)) continue;
                                                ExceptionHandler.silentTry(() -> FrameDiscovery.registerFIFIfValid(dimId, level, be, pos));
                                                ExceptionHandler.silentTry(() -> fq.add(pos));
                                                fifDiscovered++;
                                            }
                                        }
                                    }
                                    if (fifDiscovered > 0) ExceptionHandler.silentTry(() -> LogUtils.logDebug("[CATCHUP] FIF thorough fallback found {} block-entities for {}", fifDiscovered, dimId));
                                }
                            });
                            }
                        }
                    } catch (Throwable t) {
                        try { LogUtils.logTrace("[CATCHUP] FIF fallback scan failed: {}", t.getMessage()); } catch (Throwable ignored) {}
                    }
                }
            } catch (Throwable t) {
                try { LogUtils.logTrace("[CATCHUP] FIF loaded-chunk scan failed: {}", t.getMessage()); } catch (Throwable ignored) {}
            }

            try { LogUtils.logTrace("[CATCHUP] queueLoadedFrames added {} vanilla frames and {} FIF block-entities for {} (vanillaQ={} fifQ={})", vanillaDiscovered, fifDiscovered, dimId, q.size(), fifQueues.computeIfAbsent(dimId, k -> new ConcurrentLinkedQueue<>()).size()); } catch (Throwable ignored) {}
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
                ExceptionHandler.silentTry(() -> {
                    java.util.List<ItemFrame> frames = level.getEntitiesOfClass(ItemFrame.class, new AABB(p.getX(), p.getY(), p.getZ(), p.getX()+1, p.getY()+1, p.getZ()+1));
                    if (!ValidationUtils.isNullOrEmpty(frames)) {
                        for (ItemFrame f : frames) {
                            ExceptionHandler.silentTry(() -> FrameDiscovery.registerVanillaFrameIfValid(dimId, level, f));
                        }
                    }
                }, () -> LogUtils.logTrace("[CATCHUP] Failed to process vanilla pos", new Exception()));
                processed++;
                continue;
            }

            p = fq.poll();
            if (p != null) {
                ExceptionHandler.silentTry(() -> {
                    BlockEntity be = level.getBlockEntity(p);
                    if (be != null) ExceptionHandler.silentTry(() -> FrameDiscovery.registerFIFIfValid(dimId, level, be, p));
                });
                processed++;
                continue;
            }

            // nothing left to process
            break;
        }
        ExceptionHandler.silentTry(() -> LogUtils.logTrace("[CATCHUP] processBatch({}, {}) processed {} entries", dimId, maxToProcess, processed));
    }
}
