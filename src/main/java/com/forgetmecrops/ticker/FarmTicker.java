package com.forgetmecrops.ticker;

// ⏱️ FarmTicker: politely pokes farms on a schedule, regardless of which loader is in charge.
// Stonecutter swaps the event-bus APIs at build time — Fabric gets lambdas on static buses,
// NeoForge gets method references on an IEventBus instance. Same heart, different nervous system.

import com.forgetmecrops.ForgetMeCrops;
import com.forgetmecrops.config.Config;
import com.forgetmecrops.util.log.LogUtils;
import com.forgetmecrops.frame.FrameRegistry;
import com.forgetmecrops.frame.FrameScanner;
import com.forgetmecrops.frame.CatchupManager;
import com.forgetmecrops.platform.adapter.FastItemFrameAdapterImpl;

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

//? if fabric {
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
//?} else {
/*import com.forgetmecrops.frame.FrameDiscovery;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;*/ //?}

/**
 * FarmTicker: discovers farm anchors on chunk load/unload and schedules scans on server tick.
 * <p>
 * The Fabric build listens on Fabric's static event buses (ServerChunkEvents, ServerTickEvents,
 * ServerLifecycleEvents). The NeoForge build registers method-reference listeners on the NeoForge
 * event bus. Same logical flow — chunk load enqueues candidates, tick drains the catchup queue,
 * chunk unload cleans up orphaned anchors, and server stop/world unload clears everything.
 * </p>
 * <p>
 * The gradual catchup queue (drained over ~{@code CATCHUP_TICKS} ticks) ensures that world
 * startup doesn't spike the server with simultaneous discovery work for every loaded chunk.
 * Thoughtful engineering for the sake of server TPS. You're welcome.
 * </p>
 */
public class FarmTicker {
    /** Non-instantiable utility class; all members are static. */
    private FarmTicker() {}

    /** True once the first tick snapshot has been logged (per session). Used for one-time startup logs. */
    private static boolean tickSnapshotLogged = false;

    /** Number of ticks to spread the initial chunk-load catchup queue over. */
    private static final int CATCHUP_TICKS = 40;

    /** Max spiral ticks to allow in a direct (non-catchup) single-frame scan pass. */
    private static final int DIRECT_SCAN_MAX_SPIRAL_TICKS = 1;

    /** Per-dimension countdown (ticks) until the next periodic rediscovery pass runs. */
    private static final java.util.Map<String, Integer> rediscoveryCountdown = new java.util.HashMap<>();

    // ═══════════════════════════════════════════════════════════
    //  Fabric implementation — active when modstitch.platform=loom
    // ═══════════════════════════════════════════════════════════

    //? if fabric {
    // Registers all Fabric lifecycle event listeners (chunk load/unload, server tick, server stop).
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

                var frames = level.getEntitiesOfClass(ItemFrame.class, box);
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

                var frames = level.getEntitiesOfClass(ItemFrame.class, box, ef -> ef.getDirection() == Direction.UP);
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

        // Announce debug-logging status now that the server is fully loaded and config is settled.
        // (We don't do this at mod init because config might still be in flux.)
        ServerLifecycleEvents.SERVER_STARTED.register((MinecraftServer server) -> {
            try { ForgetMeCrops.logDebugStatusAtWorldLoad(); } catch (Exception t) { LogUtils.logWarn("[TICK] Failed to announce debug logging status on server start", t); }
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
    //?} else {
    /*
    // ═══════════════════════════════════════════════════════════
    //  NeoForge implementation — active when modstitch.platform=moddevgradle
    // ═══════════════════════════════════════════════════════════

    // Registers all NeoForge event listeners that drive farm discovery and scanning.
    // Same logical flow as the Fabric ticker, but using NeoForge's event bus API.
    // @param bus the NeoForge event bus to register listeners on
    public static void init(IEventBus bus) {
        bus.addListener(FarmTicker::onChunkLoad);
        bus.addListener(FarmTicker::onChunkUnload);
        bus.addListener(FarmTicker::onServerTick);
        bus.addListener(FarmTicker::onServerStarted);
        bus.addListener(FarmTicker::onLevelUnload);
    }

    private static void onLevelUnload(LevelEvent.Unload event) {
        try {
            LogUtils.logInfo("[TICK] world unload — clearing entire FrameRegistry and active scans.");
            FrameRegistry.clearAll();
            try { FrameScanner.clearAllScans(); } catch (Throwable ignored) {}
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
                                try { scanner.scanFarm(anchor, level); } catch (Throwable t) { LogUtils.logWarn("[TICK] Scan failed for " + anchor, t); }
                            } else { FrameScanner.submitScan(dimId, anchor, level); }
                        } catch (Throwable t) { LogUtils.logWarn("[TICK] Failed to submit/execute scan for " + anchor, t); }
                    }
                }
                FrameScanner.tickScans(dimId, level);
                tickSnapshotLogged = true;
            }
        } catch (Throwable t) { LogUtils.logWarn("[TICK] Unexpected NeoForge ticker error", t); }
    }

    private static void onServerStarted(ServerStartedEvent event) {
        try {
            ForgetMeCrops.logDebugStatusAtWorldLoad();
        } catch (Throwable t) {
            LogUtils.logWarn("[TICK] Failed to announce debug logging status on server start", t);
        }
    }
    */ //?}
}
