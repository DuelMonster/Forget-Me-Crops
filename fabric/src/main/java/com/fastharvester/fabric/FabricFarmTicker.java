package com.fastharvester.fabric;

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

import com.fastharvester.CatchupManager;
import com.fastharvester.frame.FrameDiscovery;
import com.fastharvester.platform.adapter.FastItemFrameAdapterImpl;

/**
 * FabricFarmTicker: discovers anchors on chunk load and schedules scans on server tick.
 *
 * The ticker is deliberately cautious — it avoids doing heavy work on the
 * very first tick by delegating any existing-frame discovery to the
 * {@link com.fastharvester.CatchupManager} which processes positions over many ticks.
 *
 * Emotional aside: this exists because the server deserves a gentle wake-up, not a heart attack.
 */
public class FabricFarmTicker {
    /** Non-instantiable utility class; all members are static. */
    private FabricFarmTicker() {}
    private static boolean tickSnapshotLogged = false;
    private static final int CATCHUP_TICKS = 40;
    private static final java.util.Map<String, Integer> rediscoveryCountdown = new java.util.HashMap<>();

    /**
     * Initialize Fabric listeners for chunk load/unload and server tick processing.
     * Humanized aside: sets up gentle discovery and periodic scanning so farms behave like well-rested gardeners.
     */
    public static void init() {
        // Discover frames when chunks are loaded
        ServerChunkEvents.CHUNK_LOAD.register((ServerLevel level, LevelChunk chunk) -> {
            try {
                // Diagnostic: log that chunk-load handler ran for this chunk
                if (Config.debugLogging) Constants.LOG.info("[FastHarvester][TICK] Chunk-load event for chunk {} in {}", chunk.getPos(), level.dimension().identifier().toString());
                String dimId = level.dimension().identifier().toString();
                int minX = chunk.getPos().getMinBlockX();
                int minZ = chunk.getPos().getMinBlockZ();
                int maxX = minX + 15;
                int maxZ = minZ + 15;
                AABB box = new AABB(minX, 0, minZ, maxX + 1, 256, maxZ + 1);

                // Vanilla item frames
                List<ItemFrame> frames = level.getEntitiesOfClass(ItemFrame.class, box);
                if (Config.debugLogging) Constants.LOG.info("[FastHarvester][TICK] Found {} item frames in chunk {} (filtering for UP and hoes afterwards).", frames.size(), chunk.getPos());
                for (ItemFrame f : frames) {
                    try {
                        FrameDiscovery.registerVanillaFrameIfValid(dimId, level, f);
                    } catch (Throwable t) { Constants.LOG.warn("[FastHarvester][TICK] Per-frame processing error: {}", t.toString()); }
                }

                // FastItemFrames: iterate block entities and detect FIF block-entities by classname
                try {
                    Map<BlockPos, BlockEntity> blockEntities = chunk.getBlockEntities();
                    for (Map.Entry<BlockPos, BlockEntity> e : blockEntities.entrySet()) {
                        BlockPos pos = e.getKey();
                        BlockEntity be = e.getValue();
                        if (be == null) continue;
                        if (!FastItemFrameAdapterImpl.isFastItemFrameBlockEntity(be)) continue;
                        FrameDiscovery.registerFIFIfValid(dimId, level, be, pos);
                    }
                } catch (Throwable t) {
                    Constants.LOG.debug("[FastHarvester][FIF] FastItemFrames discovery failed: {}", t.toString());
                }

            } catch (Throwable t) {
                Constants.LOG.warn("[FastHarvester][TICK] Chunk-load discovery error: {}", t.toString());
            }
        });

        // Chunk unload: unregister frames when chunks are unloaded
        ServerChunkEvents.CHUNK_UNLOAD.register((ServerLevel level, LevelChunk chunk) -> {
            try {
                String dimId = level.dimension().identifier().toString();
                int minX = chunk.getPos().getMinBlockX();
                int minZ = chunk.getPos().getMinBlockZ();
                int maxX = minX + 15;
                int maxZ = minZ + 15;
                AABB box = new AABB(minX, 0, minZ, maxX + 1, 256, maxZ + 1);

                // Unregister vanilla item frames in this chunk
                List<ItemFrame> frames = level.getEntitiesOfClass(ItemFrame.class, box, ef -> ef.getDirection() == Direction.UP);
                for (ItemFrame f : frames) {
                    try { FrameRegistry.unregisterFrame(dimId, f.blockPosition()); } catch (Throwable ignored) {}
                }

                // Unregister FastItemFrames block-entities in this chunk
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
                Constants.LOG.warn("[FastHarvester][TICK] Chunk-unload cleanup error: {}", t.toString());
            }
        });

        // Server stopping: clear registry to free memory when the server/world is left
        ServerLifecycleEvents.SERVER_STOPPING.register((MinecraftServer server) -> {
            try {
                Constants.LOG.info("[FastHarvester][TICK] Server stopping — clearing FrameRegistry.");
                FrameRegistry.clearAll();
            } catch (Throwable t) {
                Constants.LOG.warn("[FastHarvester][TICK] Failed to clear FrameRegistry on server stop: {}", t.toString());
            }
        });

        // Server tick: decrement countdowns and run ready scans
        ServerTickEvents.END_SERVER_TICK.register((MinecraftServer server) -> {
            try {
                for (ServerLevel level : server.getAllLevels()) {
                    String dimId = level.dimension().identifier().toString();
                    int rem = rediscoveryCountdown.getOrDefault(dimId, Config.frameRediscoveryInterval);
                    rem--;
                    if (rem <= 0) {
                        Constants.LOG.info("[FastHarvester][TICK] Running rediscovery pass for {}", dimId);
                        CatchupManager.queueLoadedFrames(level, dimId);
                        rem = Config.frameRediscoveryInterval;
                    }
                    rediscoveryCountdown.put(dimId, rem);
                    // On first seen tick, capture a snapshot of loaded frames to catch-up
                    // (this is queued and processed across multiple ticks to avoid spikes).
                    if (!tickSnapshotLogged) {
                        CatchupManager.queueLoadedFrames(level, dimId);
                    }
                    // Process a small batch this tick to gradually register any pre-existing frames
                    CatchupManager.processBatch(level, dimId, CATCHUP_TICKS);
                        var ready = FrameRegistry.tickAndCollectReady(dimId, level);
                        if (!ready.isEmpty()) {
                            Constants.LOG.info("[FastHarvester][TICK] {} anchors ready in {}: {}", ready.size(), dimId, ready);
                            // Decide between synchronous scan (fast path) and tick-sliced scheduling
                            FrameScanner scanner = new FrameScanner();
                            for (var anchor : ready) {
                                try {
                                    if (Config.maxSpiralDurationTicks <= 1) {
                                        try {
                                            scanner.scanFarm(anchor, level);
                                        } catch (Throwable t) {
                                            Constants.LOG.warn("[FastHarvester][TICK] Scan failed for {}: {}", anchor, t.toString());
                                        }
                                    } else {
                                        FrameScanner.submitScan(dimId, anchor, level);
                                    }
                                } catch (Throwable t) {
                                    Constants.LOG.warn("[FastHarvester][TICK] Failed to submit/execute scan for {}: {}", anchor, t.toString());
                                }
                            }

                            // (tickScans moved out so it runs every tick)
                        }
                        // Tick scheduled scan tasks for this dimension (process one slice per active job)
                        FrameScanner.tickScans(dimId, level);
                    tickSnapshotLogged = true;
                }
            } catch (Throwable t) {
                Constants.LOG.warn("[FastHarvester][TICK] Unexpected ticker error: {}", t.toString());
            }
        });
    }

    
}
