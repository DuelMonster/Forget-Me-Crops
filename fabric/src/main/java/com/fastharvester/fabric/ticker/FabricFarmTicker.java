package com.fastharvester.fabric.ticker;

// ⏱️ FabricFarmTicker: politely pokes farms to scan on a schedule. Cheerful and punctual.
// Emotional aside: it measures time and whispers encouragement.

import com.fastharvester.Config;
import com.fastharvester.Constants;
import com.fastharvester.frame.FrameRegistry;
import com.fastharvester.frame.FrameScanner;

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
import com.fastharvester.frame.CatchupManager;
import com.fastharvester.platform.adapter.FastItemFrameAdapterImpl;

public class FabricFarmTicker {
    private FabricFarmTicker() {}
    private static boolean tickSnapshotLogged = false;
    private static final int CATCHUP_TICKS = 40;
    private static final java.util.Map<String, Integer> rediscoveryCountdown = new java.util.HashMap<>();

    public static void init() {
        ServerChunkEvents.CHUNK_LOAD.register((ServerLevel level, LevelChunk chunk) -> {
            try {
                Constants.logDebug("[TICK] Chunk-load event for chunk {} in {}", chunk.getPos(), level.dimension().identifier().toString());
                String dimId = level.dimension().identifier().toString();
                int minX = chunk.getPos().getMinBlockX();
                int minZ = chunk.getPos().getMinBlockZ();
                int maxX = minX + 15;
                int maxZ = minZ + 15;
                AABB box = new AABB(minX, 0, minZ, maxX + 1, 256, maxZ + 1);

                List<ItemFrame> frames = level.getEntitiesOfClass(ItemFrame.class, box);
                Constants.logDebug("[TICK] Found {} item frames in chunk {} (deferring validation).", frames.size(), chunk.getPos());
                java.util.List<BlockPos> vanillaCandidates = new java.util.ArrayList<>();
                for (ItemFrame f : frames) {
                    try { vanillaCandidates.add(f.blockPosition()); } catch (Throwable ignored) {}
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
                } catch (Throwable t) {
                    Constants.logDebug("[FIF] FastItemFrames discovery failed", t);
                }

            } catch (Throwable t) {
                Constants.logWarn("[TICK] Chunk-load discovery error", t);
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
                for (ItemFrame f : frames) { try { FrameRegistry.unregisterFrame(dimId, f.blockPosition()); } catch (Throwable ignored) {} }

                try {
                    Map<BlockPos, BlockEntity> blockEntities = chunk.getBlockEntities();
                    for (Map.Entry<BlockPos, BlockEntity> e : blockEntities.entrySet()) {
                        BlockPos pos = e.getKey();
                        BlockEntity be = e.getValue();
                        if (be == null) continue;
                        if (!FastItemFrameAdapterImpl.isFastItemFrameBlockEntity(be)) continue;
                        FrameRegistry.unregisterFrame(dimId, pos);
                    }
                } catch (Throwable ignored) {}
            } catch (Throwable t) {
                Constants.logWarn("[TICK] Chunk-unload cleanup error", t);
            }
        });

        ServerLifecycleEvents.SERVER_STOPPING.register((MinecraftServer server) -> {
            try { Constants.logInfo("[TICK] Server stopping — clearing FrameRegistry."); FrameRegistry.clearAll(); } catch (Throwable t) { Constants.logWarn("[TICK] Failed to clear FrameRegistry on server stop", t); }
        });

        ServerTickEvents.END_SERVER_TICK.register((MinecraftServer server) -> {
            try {
                for (ServerLevel level : server.getAllLevels()) {
                    String dimId = level.dimension().identifier().toString();
                    int rem = rediscoveryCountdown.getOrDefault(dimId, Config.frameRediscoveryInterval);
                    rem--;
                    if (rem <= 0) {
                        Constants.logDebug("[TICK] Running rediscovery pass for {}", dimId);
                        CatchupManager.queueLoadedFrames(level, dimId);
                        rem = Config.frameRediscoveryInterval;
                    }
                    rediscoveryCountdown.put(dimId, rem);
                    if (!tickSnapshotLogged) { CatchupManager.queueLoadedFrames(level, dimId); }
                    CatchupManager.processBatch(level, dimId, CATCHUP_TICKS);
                    var ready = FrameRegistry.tickAndCollectReady(dimId, level);
                    if (!ready.isEmpty()) {
                        Constants.logDebug("[TICK] {} anchors ready in {}: {}", ready.size(), dimId, ready);
                        FrameScanner scanner = new FrameScanner();
                        for (var anchor : ready) {
                            try {
                                if (Config.maxSpiralDurationTicks <= 1) {
                                    try { scanner.scanFarm(anchor, level); } catch (Throwable t) { Constants.logWarn("[TICK] Scan failed for " + anchor, t); }
                                } else { FrameScanner.submitScan(dimId, anchor, level); }
                            } catch (Throwable t) { Constants.logWarn("[TICK] Failed to submit/execute scan for " + anchor, t); }
                        }
                    }
                    FrameScanner.tickScans(dimId, level);
                    tickSnapshotLogged = true;
                }
            } catch (Throwable t) { Constants.logWarn("[TICK] Unexpected ticker error", t); }
        });
    }
}
