package com.forgetmecrops.neoforge.ticker;

// 🌾 NeoForge ticker: quietly counts down and nudges farms to do their thing.
// Emotional state: hopeful. It believes in your crops.

import com.forgetmecrops.frame.FrameRegistry;
import com.forgetmecrops.frame.FrameScanner;
import com.forgetmecrops.util.log.LogUtils;
import com.forgetmecrops.config.Config;

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

import com.forgetmecrops.frame.CatchupManager;
import com.forgetmecrops.frame.FrameDiscovery;
import com.forgetmecrops.platform.adapter.FastItemFrameAdapterImpl;

/**
 * FarmTicker: discovers anchors on chunk load/unload and schedules scans on server tick.
 *
 * This ticker behaves similarly to the Fabric ticker but uses NeoForge event hooks.
 *
 * Emotional aside: it likes long walks on the server and gradual, polite discovery.
 * It queues pre-existing frames for gradual processing to avoid large single-tick work.
 */
public class FarmTicker {
    /** Non-instantiable utility class; all members are static. */
    private FarmTicker() {}
    private static boolean tickSnapshotLogged = false;
    private static final int CATCHUP_TICKS = 40;
    private static final int DIRECT_SCAN_MAX_SPIRAL_TICKS = 1;
    private static final java.util.Map<String, Integer> rediscoveryCountdown = new java.util.HashMap<>();
    /**
     * Initialize NeoForge listeners for chunk load/unload and server tick processing.
     * Emotional aside: behaves like Fabric's ticker but speaks NeoForge's dialect.
     *
     * @param bus event bus to register ticker listeners on
     */
    public static void init(IEventBus bus) {
        bus.addListener(FarmTicker::onChunkLoad);
        bus.addListener(FarmTicker::onChunkUnload);
        bus.addListener(FarmTicker::onServerTick);
        bus.addListener(FarmTicker::onLevelUnload);
    }

    private static void onLevelUnload(LevelEvent.Unload event) {
        try {
            LogUtils.logInfo("[TICK] world unload — clearing entire FrameRegistry and active scans.");
            FrameRegistry.clearAll();
            try { com.forgetmecrops.frame.FrameScanner.clearAllScans(); } catch (Throwable ignored) {}
            try {
                var levelAccessor = event.getLevel();
                if (levelAccessor instanceof ServerLevel level) {
                    String dimId = level.dimension().identifier().toString();
                    rediscoveryCountdown.remove(dimId);
                }
            } catch (Throwable ignored) {}
        } catch (Throwable t) {
            LogUtils.logWarn("[TICK] Failed to handle level unload", t);
        }
    }

    private static void onChunkUnload(ChunkEvent.Unload event) {
        try {
            var levelAccessor = event.getLevel();
            if (!(levelAccessor instanceof ServerLevel level)) return;
            LevelChunk lc = event.getChunk();

            String dimId = level.dimension().identifier().toString();
            int minX = lc.getPos().getMinBlockX();
            int minZ = lc.getPos().getMinBlockZ();
            int maxX = minX + 15;
            int maxZ = minZ + 15;
            AABB box = new AABB(minX, 0, minZ, maxX + 1, 256, maxZ + 1);

            List<ItemFrame> frames = level.getEntitiesOfClass(ItemFrame.class, box, ef -> ef.getDirection() == Direction.UP);
            for (ItemFrame f : frames) {
                try { FrameRegistry.unregisterFrame(dimId, f.blockPosition()); } catch (Throwable ignored) {}
            }

            try {
                for (var e : lc.getBlockEntities().entrySet()) {
                    BlockPos pos = e.getKey();
                    BlockEntity be = e.getValue();
                    if (be == null) continue;
                    if (!FastItemFrameAdapterImpl.isFastItemFrameBlockEntity(be)) continue;
                    FrameRegistry.unregisterFrame(dimId, pos);
                }
            } catch (Throwable ignored) {}
        } catch (Throwable t) {
            LogUtils.logWarn("[TICK] NeoForge chunk-unload cleanup error", t);
        }
    }

    private static void onChunkLoad(ChunkEvent.Load event) {
        try {
            var levelAccessor = event.getLevel();
            if (!(levelAccessor instanceof ServerLevel level)) return;
            LevelChunk lc = event.getChunk();

            int minX = lc.getPos().getMinBlockX();
            int minZ = lc.getPos().getMinBlockZ();
            int maxX = minX + 15;
            int maxZ = minZ + 15;
            AABB box = new AABB(minX, 0, minZ, maxX + 1, 256, maxZ + 1);

            List<ItemFrame> frames = level.getEntitiesOfClass(ItemFrame.class, box);
            String dimId = level.dimension().identifier().toString();
            LogUtils.logTrace("[TICK] NeoForge found {} item frames in chunk {}.", frames.size(), lc.getPos());
                for (ItemFrame f : frames) {
                try {
                    FrameDiscovery.registerVanillaFrameIfValid(dimId, level, f);
                } catch (Throwable t) { LogUtils.logWarn("[TICK] NeoForge per-frame processing error", t); }
            }

            try {
                for (var e : lc.getBlockEntities().entrySet()) {
                    BlockPos pos = e.getKey();
                    BlockEntity be = e.getValue();
                    if (be == null) continue;
                    if (!FastItemFrameAdapterImpl.isFastItemFrameBlockEntity(be)) continue;
                    FrameDiscovery.registerFIFIfValid(dimId, level, be, pos);
                }
            } catch (Throwable t) {
                LogUtils.logDebug("[FIF] NeoForge FIF discovery failed", t);
            }

        } catch (Throwable t) {
            LogUtils.logWarn("[TICK] NeoForge chunk-load discovery error", t);
        }
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        try {
            if (!event.hasTime()) return;
            MinecraftServer server = event.getServer();
            if (server == null) return;
            for (ServerLevel level : server.getAllLevels()) {
                String dimId = level.dimension().identifier().toString();
                    int rem = rediscoveryCountdown.getOrDefault(dimId, Config.getFrameRediscoveryInterval());
                    rem--;
                    if (rem <= 0) {
                            LogUtils.logTrace("[TICK] NeoForge rediscovery pass for {}", dimId);
                            CatchupManager.queueLoadedFrames(level, dimId);
                            rem = Config.getFrameRediscoveryInterval();
                        }
                    rediscoveryCountdown.put(dimId, rem);
                if (!tickSnapshotLogged) {
                    LogUtils.logTrace("[TICK] NeoForge initial snapshot queueLoadedFrames for {}", dimId);
                    CatchupManager.queueLoadedFrames(level, dimId);
                }
                CatchupManager.processBatch(level, dimId, CATCHUP_TICKS);

                var ready = FrameRegistry.tickAndCollectReady(dimId, level);
                    if (!ready.isEmpty()) {
                    LogUtils.logDebug("[TICK] {} anchors ready in {}", ready.size(), dimId);
                    FrameScanner scanner = new FrameScanner();
                    for (var anchor : ready) {
                        try {
                            if (Config.getMaxSpiralDurationTicks() <= DIRECT_SCAN_MAX_SPIRAL_TICKS) {
                                        try {
                                            scanner.scanFarm(anchor, level);
                                        } catch (Throwable t) {
                                            LogUtils.logWarn("[TICK] Scan failed for " + anchor, t);
                                        }
                            } else {
                                FrameScanner.submitScan(dimId, anchor, level);
                            }
                        } catch (Throwable t) {
                            LogUtils.logWarn("[TICK] Failed to submit/execute scan for " + anchor, t);
                        }
                    }
                }

                FrameScanner.tickScans(dimId, level);
                tickSnapshotLogged = true;
            }
        } catch (Throwable t) {
            LogUtils.logWarn("[TICK] NeoForge ticker error", t);
        }
    }

    
}
