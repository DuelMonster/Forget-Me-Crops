package com.forgetmecrops.fabric.ticker;

// ⏱️ FarmTicker: politely pokes farms to scan on a schedule. Cheerful and punctual.
// Emotional aside: it measures time and whispers encouragement.

import com.forgetmecrops.config.Config;
import com.forgetmecrops.util.log.LogUtils;
import com.forgetmecrops.frame.FrameRegistry;
import com.forgetmecrops.frame.FrameScanner;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.List;
import java.util.Map;
import com.forgetmecrops.frame.CatchupManager;
import com.forgetmecrops.platform.adapter.FastItemFrameAdapterImpl;

public class FarmTicker {
    private FarmTicker() {}
    private static boolean tickSnapshotLogged = false;
    private static final int CATCHUP_TICKS = 40;
    private static final int DIRECT_SCAN_MAX_SPIRAL_TICKS = 1;
    private static final java.util.Map<String, Integer> rediscoveryCountdown = new java.util.HashMap<>();

    public static void init() {
        ServerChunkEvents.CHUNK_LOAD.register((ServerLevel level, LevelChunk chunk) -> {
            try {
                LogUtils.logTrace("[TICK] Chunk-load event for chunk {} in {}", chunk.getPos(), level.dimension().identifier().toString());
                String dimId = level.dimension().identifier().toString();
                int minX = chunk.getPos().getMinBlockX();
                int minZ = chunk.getPos().getMinBlockZ();
                int maxX = minX + 15;
                int maxZ = minZ + 15;
                AABB box = new AABB(minX, 0, minZ, maxX + 1, 256, maxZ + 1);

                List<ItemFrame> frames = level.getEntitiesOfClass(ItemFrame.class, box);
                LogUtils.logTrace("[TICK] Found {} item frames in chunk {} (deferring validation).", frames.size(), chunk.getPos());
                java.util.List<BlockPos> vanillaCandidates = new java.util.ArrayList<>();
                for (ItemFrame f : frames) {
                    try { vanillaCandidates.add(f.blockPosition()); } catch (Exception e) { LogUtils.logTrace("[TICK] Failed to read frame position while enqueuing candidates", e); }
                }
                if (!vanillaCandidates.isEmpty()) CatchupManager.enqueueVanillaPositions(level, dimId, vanillaCandidates);

                try {
                    Map<BlockPos, BlockEntity> blockEntities = chunk.getBlockEntities();
                    java.util.List<BlockPos> fifCandidates = new java.util.ArrayList<>();
                    for (Map.Entry<BlockPos, BlockEntity> e : blockEntities.entrySet()) {
                        BlockPos pos = e.getKey();
                        BlockEntity be = e.getValue();
                        if (be == null) continue;
                        if (!FastItemFrameAdapterImpl.isFastItemFrameBlockEntity(be)) continue;
                        fifCandidates.add(pos);
                    }
                    if (!fifCandidates.isEmpty()) CatchupManager.enqueueFifPositions(level, dimId, fifCandidates);
                    } catch (Exception t) {
                    LogUtils.logTrace("[FIF] FastItemFrames discovery failed", t);
                }

            } catch (Exception t) {
                LogUtils.logWarn("[TICK] Chunk-load discovery error", t);
            }
        });

        ServerChunkEvents.CHUNK_UNLOAD.register((ServerLevel level, LevelChunk chunk) -> {
            try {
                String dimId = level.dimension().identifier().toString();
                int minX = chunk.getPos().getMinBlockX();
                int minZ = chunk.getPos().getMinBlockZ();
                int maxX = minX + 15;
                int maxZ = minZ + 15;
                AABB box = new AABB(minX, 0, minZ, maxX + 1, 256, maxZ + 1);

                List<ItemFrame> frames = level.getEntitiesOfClass(ItemFrame.class, box, ef -> ef.getDirection() == Direction.UP);
                for (ItemFrame f : frames) {
                    try { FrameRegistry.unregisterFrame(dimId, f.blockPosition()); } catch (Exception e) { LogUtils.logTrace("[TICK] Failed to unregister frame during chunk unload", e); }
                }

                try {
                    Map<BlockPos, BlockEntity> blockEntities = chunk.getBlockEntities();
                    for (Map.Entry<BlockPos, BlockEntity> e : blockEntities.entrySet()) {
                        BlockPos pos = e.getKey();
                        BlockEntity be = e.getValue();
                        if (be == null) continue;
                        if (!FastItemFrameAdapterImpl.isFastItemFrameBlockEntity(be)) continue;
                        FrameRegistry.unregisterFrame(dimId, pos);
                    }
                } catch (Exception e) { LogUtils.logTrace("[TICK] Failed to process block entities during chunk unload", e); }
            } catch (Exception t) {
                LogUtils.logWarn("[TICK] Chunk-unload cleanup error", t);
            }
        });

        ServerLifecycleEvents.SERVER_STOPPING.register((MinecraftServer server) -> {
            try { LogUtils.logInfo("[TICK] Server stopping — clearing FrameRegistry."); FrameRegistry.clearAll(); } catch (Exception t) { LogUtils.logWarn("[TICK] Failed to clear FrameRegistry on server stop", t); }
        });

        ServerTickEvents.END_SERVER_TICK.register((MinecraftServer server) -> {
            try {
                for (ServerLevel level : server.getAllLevels()) {
                    String dimId = level.dimension().identifier().toString();
                    int rem = rediscoveryCountdown.getOrDefault(dimId, Config.getFrameRediscoveryInterval());
                    rem--;
                    if (rem <= 0) {
                        LogUtils.logTrace("[TICK] Running rediscovery pass for {}", dimId);
                        CatchupManager.queueLoadedFrames(level, dimId);
                        rem = Config.getFrameRediscoveryInterval();
                    }
                    rediscoveryCountdown.put(dimId, rem);
                    if (!tickSnapshotLogged) { CatchupManager.queueLoadedFrames(level, dimId); }
                    CatchupManager.processBatch(level, dimId, CATCHUP_TICKS);
                    var ready = FrameRegistry.tickAndCollectReady(dimId, level);
                    if (!ready.isEmpty()) {
                        LogUtils.logDebug("[TICK] {} anchors ready in {}", ready.size(), dimId);
                        FrameScanner scanner = new FrameScanner();
                        for (var anchor : ready) {
                            try {
                                if (Config.getMaxSpiralDurationTicks() <= DIRECT_SCAN_MAX_SPIRAL_TICKS) {
                                    try { scanner.scanFarm(anchor, level); } catch (Exception t) { LogUtils.logWarn("[TICK] Scan failed for " + anchor, t); }
                                } else { FrameScanner.submitScan(dimId, anchor, level); }
                            } catch (Exception t) { LogUtils.logWarn("[TICK] Failed to submit/execute scan for " + anchor, t); }
                        }
                    }
                    FrameScanner.tickScans(dimId, level);
                    tickSnapshotLogged = true;
                }
            } catch (Exception t) { LogUtils.logWarn("[TICK] Unexpected ticker error", t); }
        });
    }
}
