package com.fastharvester.neoforge;

// 🌾 NeoForge ticker: quietly counts down and nudges farms to do their thing.
// Emotional state: hopeful. It believes in your crops.

import com.fastharvester.FrameRegistry;
import com.fastharvester.FrameScanner;
import com.fastharvester.Constants;
import com.fastharvester.Config;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

import java.util.List;

import com.fastharvester.CatchupManager;
import com.fastharvester.FrameDiscovery;
import com.fastharvester.FastItemFrameAdapterImpl;

/**
 * NeoForgeFarmTicker: discovers anchors on chunk load/unload and schedules scans on server tick.
 *
 * This ticker behaves similarly to the Fabric ticker but uses NeoForge event hooks.
 *
 * Emotional aside: it likes long walks on the server and gradual, polite discovery.
 * It queues pre-existing frames for gradual processing to avoid large single-tick work.
 */
public class NeoForgeFarmTicker {
    private static boolean tickSnapshotLogged = false;
    private static final int CATCHUP_TICKS = 40;
    private static final java.util.Map<String, Integer> rediscoveryCountdown = new java.util.HashMap<>();
    /**
     * Initialize NeoForge listeners for chunk load/unload and server tick processing.
     * Emotional aside: behaves like Fabric's ticker but speaks NeoForge's dialect.
     */
    public static void init(IEventBus bus) {
        bus.addListener(NeoForgeFarmTicker::onChunkLoad);
        bus.addListener(NeoForgeFarmTicker::onChunkUnload);
        bus.addListener(NeoForgeFarmTicker::onServerTick);
        bus.addListener(NeoForgeFarmTicker::onLevelUnload);
    }

    private static void onLevelUnload(LevelEvent.Unload event) {
        try {
            Constants.LOG.info("[FastHarvester][TICK] world unload — clearing entire FrameRegistry.");
            FrameRegistry.clearAll();
        } catch (Throwable t) {
            Constants.LOG.warn("[FastHarvester][TICK] Failed to handle level unload: {}", t.toString());
        }
    }

    /**
     * Handle chunk unload events: unregister any anchors in the unloading chunk.
     * <p>
     * We clear out recorded anchors for the chunk so the registry doesn't hold stale references.
     * </p>
     * @param event Chunk unload event
     */
    private static void onChunkUnload(ChunkEvent.Unload event) {
        try {
            var levelAccessor = event.getLevel();
            if (!(levelAccessor instanceof ServerLevel level)) return;
            var chunk = event.getChunk();
            if (!(chunk instanceof LevelChunk lc)) return;

            String dimId = level.dimension().identifier().toString();
            int minX = lc.getPos().getMinBlockX();
            int minZ = lc.getPos().getMinBlockZ();
            int maxX = minX + 15;
            int maxZ = minZ + 15;
            AABB box = new AABB(minX, 0, minZ, maxX + 1, 256, maxZ + 1);

            // Unregister vanilla item frames
            List<ItemFrame> frames = level.getEntitiesOfClass(ItemFrame.class, box, ef -> ef.getDirection() == Direction.UP);
            for (ItemFrame f : frames) {
                try { FrameRegistry.unregisterFrame(dimId, f.blockPosition()); } catch (Throwable ignored) {}
            }

            // Unregister FIF block-entities (use adapter detection to avoid fragile string checks)
            try {
                for (var e : lc.getBlockEntities().entrySet()) {
                    BlockPos pos = e.getKey();
                    BlockEntity be = e.getValue();
                    if (be == null) continue;
                    if (!com.fastharvester.FastItemFrameAdapterImpl.isFastItemFrameBlockEntity(be)) continue;
                    FrameRegistry.unregisterFrame(dimId, pos);
                }
            } catch (Throwable ignored) {}
        } catch (Throwable t) {
            Constants.LOG.warn("[FastHarvester][TICK] NeoForge chunk-unload cleanup error: {}", t.toString());
        }
    }

    /**
     * Handle chunk load events: discover vanilla frames and FastItemFrames in the loaded chunk.
     * <p>
     * Newly discovered frames are validated and registered so they can be scheduled for harvesting.
     * </p>
     * @param event Chunk load event
     */
    private static void onChunkLoad(ChunkEvent.Load event) {
        try {
            var levelAccessor = event.getLevel();
            if (!(levelAccessor instanceof ServerLevel level)) return;
            var chunk = event.getChunk();
            if (!(chunk instanceof LevelChunk lc)) return;

            int minX = lc.getPos().getMinBlockX();
            int minZ = lc.getPos().getMinBlockZ();
            int maxX = minX + 15;
            int maxZ = minZ + 15;
            AABB box = new AABB(minX, 0, minZ, maxX + 1, 256, maxZ + 1);

            List<ItemFrame> frames = level.getEntitiesOfClass(ItemFrame.class, box);
            String dimId = level.dimension().identifier().toString();
            if (Config.debugLogging) Constants.LOG.info("[FastHarvester][TICK] NeoForge found {} item frames in chunk {}.", frames.size(), lc.getPos());
            for (ItemFrame f : frames) {
                try {
                    FrameDiscovery.registerVanillaFrameIfValid(dimId, level, f);
                } catch (Throwable t) { Constants.LOG.warn("[FastHarvester][TICK] NeoForge per-frame processing error: {}", t.toString()); }
            }

            // FastItemFrames: attempt block-entity enumeration on the chunk
            try {
                for (var e : lc.getBlockEntities().entrySet()) {
                    BlockPos pos = e.getKey();
                    BlockEntity be = e.getValue();
                    if (be == null) continue;
                    if (!FastItemFrameAdapterImpl.isFastItemFrameBlockEntity(be)) continue;
                    FrameDiscovery.registerFIFIfValid(dimId, level, be, pos);
                }
            } catch (Throwable t) {
                Constants.LOG.debug("[FastHarvester][FIF] NeoForge FIF discovery failed: {}", t.toString());
            }

        } catch (Throwable t) {
            Constants.LOG.warn("[FastHarvester][TICK] NeoForge chunk-load discovery error: {}", t.toString());
        }
    }

    /**
     * Server tick handler: process catch-up batches, decrement frame timers, and run scans.
     * <p>
     * This spreads discovery and scanning across ticks to avoid single-tick spikes.
     * </p>
     * @param event Server tick event
     */
    private static void onServerTick(ServerTickEvent.Post event) {
        try {
            if (!event.hasTime()) return;
            MinecraftServer server = event.getServer();
            if (server == null) return;
            for (ServerLevel level : server.getAllLevels()) {
                String dimId = level.dimension().identifier().toString();
                    int rem = rediscoveryCountdown.getOrDefault(dimId, Config.frameRediscoveryInterval);
                    rem--;
                    if (rem <= 0) {
                        Constants.LOG.info("[FastHarvester][TICK] NeoForge rediscovery pass for {}", dimId);
                        CatchupManager.queueLoadedFrames(level, dimId);
                        rem = Config.frameRediscoveryInterval;
                    }
                    rediscoveryCountdown.put(dimId, rem);
                // Capture a one-time snapshot and then process a small catch-up batch
                // each tick so we don't overload the server on startup.
                if (!tickSnapshotLogged) {
                    CatchupManager.queueLoadedFrames(level, dimId);
                }
                CatchupManager.processBatch(level, dimId, CATCHUP_TICKS);

                var ready = FrameRegistry.tickAndCollectReady(dimId, level);
                if (!ready.isEmpty()) {
                    Constants.LOG.info("[FastHarvester][TICK] {} anchors ready in {}: {}", ready.size(), dimId, ready);
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
                }

                // Process one tick-slice for active scan jobs in this dimension
                FrameScanner.tickScans(dimId, level);
                tickSnapshotLogged = true;
            }
        } catch (Throwable t) {
            Constants.LOG.warn("[FastHarvester][TICK] NeoForge ticker error: {}", t.toString());
        }
    }

    
}
