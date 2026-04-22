package com.fastharvester.fabric;

// ⏱️ FabricFarmTicker: politely pokes farms to scan on a schedule. Cheerful and punctual.
// Emotional aside: it measures time and whispers encouragement.

import com.fastharvester.Config;
import com.fastharvester.Constants;
import com.fastharvester.FrameRegistry;
import com.fastharvester.FrameScanner;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.Container;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.chunk.LevelChunk;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

/**
 * FabricFarmTicker: discovers anchors on chunk load and schedules scans on server tick.
 */
public class FabricFarmTicker {
    private static boolean tickSnapshotLogged = false;
    public static void init() {
        // Discover frames when chunks are loaded
        ServerChunkEvents.CHUNK_LOAD.register((ServerLevel level, LevelChunk chunk) -> {
            try {
                // Diagnostic: log that chunk-load handler ran for this chunk
                Constants.LOG.info("[FastHarvester][TICK] Chunk-load event for chunk {} in {}", chunk.getPos(), level.dimension().identifier().toString());
                String dimId = level.dimension().identifier().toString();
                int minX = chunk.getPos().getMinBlockX();
                int minZ = chunk.getPos().getMinBlockZ();
                int maxX = minX + 15;
                int maxZ = minZ + 15;
                AABB box = new AABB(minX, 0, minZ, maxX + 1, 256, maxZ + 1);

                // Vanilla item frames
                List<ItemFrame> frames = level.getEntitiesOfClass(ItemFrame.class, box);
                Constants.LOG.info("[FastHarvester][TICK] Found {} item frames in chunk {} (filtering for UP and hoes afterwards).", frames.size(), chunk.getPos());
                for (ItemFrame f : frames) {
                    try {
                        try { Constants.LOG.info("[FastHarvester][TICK] Frame at {} direction={}", f.blockPosition(), f.getDirection()); } catch (Throwable ignored) {}
                        var held = f.getItem();
                        if (held == null || held.isEmpty()) { Constants.LOG.info("[FastHarvester][TICK] Frame {} holds nothing.", f.blockPosition()); continue; }
                        try { Constants.LOG.info("[FastHarvester][TICK] Frame {} holds item: {}", f.blockPosition(), held.getItem().getClass().getName()); } catch (Throwable ignored) {}
                        if (f.getDirection() != Direction.UP) { Constants.LOG.info("[FastHarvester][TICK] Frame {} skipped: not facing UP ({}).", f.blockPosition(), f.getDirection()); continue; }
                        if (!(held.getItem() instanceof HoeItem)) { Constants.LOG.info("[FastHarvester][TICK] Frame {} skipped: held item is not a hoe.", f.blockPosition()); continue; }
                        BlockPos pos = f.blockPosition();
                        BlockPos chestPos = pos;
                        BlockEntity be = level.getBlockEntity(chestPos);
                        if (!(be instanceof Container)) {
                            BlockPos below = pos.below();
                            BlockEntity beBelow = level.getBlockEntity(below);
                            if (beBelow instanceof Container) {
                                be = beBelow;
                                chestPos = below;
                            }
                        }
                        if (be instanceof Container chest) {
                            // Waterlogged-chest enforcement for nearby farmland crops (check at chest position)
                            boolean chestWaterlogged = false;
                            try { BlockState cs = level.getBlockState(chestPos); chestWaterlogged = cs.getValue(BlockStateProperties.WATERLOGGED); } catch (Throwable ignored) {}
                            boolean nearbyFarmlandCrop = false;
                            int r = Math.min(5, Math.max(1, Config.scanRange));
                            outer: for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
                                BlockState ns = level.getBlockState(chestPos.offset(dx, 0, dz));
                                net.minecraft.world.level.block.Block b = ns.getBlock();
                                if (b == Blocks.WHEAT || b == Blocks.BEETROOTS || b == Blocks.CARROTS || b == Blocks.POTATOES || b == Blocks.MELON_STEM || b == Blocks.PUMPKIN_STEM) { nearbyFarmlandCrop = true; break outer; }
                            }
                            if (nearbyFarmlandCrop && !chestWaterlogged) {
                                Constants.LOG.info("[FastHarvester][TICK] Skipping anchor at {} in {}: chest not waterlogged but nearby farmland crops present.", pos, dimId);
                                continue;
                            }

                            Constants.LOG.info("[FastHarvester][TICK] Discovered anchor (vanilla) at {} in {}; registering.", pos, dimId);
                            FrameRegistry.registerFrame(dimId, pos, chest, held.copy());
                        } else {
                            Constants.LOG.info("[FastHarvester][TICK] No container block-entity near frame pos {} (be={}).", f.blockPosition(), be == null ? "null" : be.getClass().getName());
                        }
                    } catch (Throwable t) { Constants.LOG.warn("[FastHarvester][TICK] Per-frame processing error: {}", t.toString()); }
                }

                // FastItemFrames: iterate block entities and detect FIF block-entities by classname
                try {
                    Map<BlockPos, BlockEntity> blockEntities = chunk.getBlockEntities();
                    for (Map.Entry<BlockPos, BlockEntity> e : blockEntities.entrySet()) {
                        BlockPos pos = e.getKey();
                        BlockEntity be = e.getValue();
                        if (be == null) continue;
                        String cls = be.getClass().getName().toLowerCase();
                        if (!cls.contains("fastitemframes")) continue;
                        // try to read displayed/held item reflectively
                        ItemStackWrapper held = extractHeldItemReflectively(be);
                        if (held == null || held.isEmpty()) continue;
                        if (!held.isHoe()) continue;
                        BlockPos chestPos = pos.below();
                        var chestBe = level.getBlockEntity(chestPos);
                        if (chestBe instanceof Container chest) {
                            // Waterlogged-enforcement similar as above
                            boolean chestWaterlogged = false;
                            try { BlockState cs = level.getBlockState(chestPos); chestWaterlogged = cs.getValue(BlockStateProperties.WATERLOGGED); } catch (Throwable ignored) {}
                            boolean nearbyFarmlandCrop = false;
                            int r = Math.min(5, Math.max(1, Config.scanRange));
                            outer2: for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
                                BlockState ns = level.getBlockState(chestPos.offset(dx, 0, dz));
                                net.minecraft.world.level.block.Block b = ns.getBlock();
                                if (b == Blocks.WHEAT || b == Blocks.BEETROOTS || b == Blocks.CARROTS || b == Blocks.POTATOES || b == Blocks.MELON_STEM || b == Blocks.PUMPKIN_STEM) { nearbyFarmlandCrop = true; break outer2; }
                            }
                            if (nearbyFarmlandCrop && !chestWaterlogged) {
                                Constants.LOG.info("[FastHarvester][TICK] Skipping FIF anchor at {} in {}: chest not waterlogged but nearby farmland crops present.", pos, dimId);
                                continue;
                            }

                            Constants.LOG.info("[FastHarvester][TICK] Discovered anchor (FIF) at {} in {}; registering.", pos, dimId);
                            FrameRegistry.registerFrame(dimId, pos, chest, held.toItemStack());
                        }
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
                        String cls = be.getClass().getName().toLowerCase();
                        if (!cls.contains("fastitemframes")) continue;
                        FrameRegistry.unregisterFrame(dimId, pos);
                    }
                } catch (Throwable ignored) {}
            } catch (Throwable t) {
                Constants.LOG.warn("[FastHarvester][TICK] Chunk-unload cleanup error: {}", t.toString());
            }
        });

        // Server tick: decrement countdowns and run ready scans
        ServerTickEvents.END_SERVER_TICK.register((MinecraftServer server) -> {
            try {
                for (ServerLevel level : server.getAllLevels()) {
                    String dimId = level.dimension().identifier().toString();
                    if (!tickSnapshotLogged) {
                        Constants.LOG.info("[FastHarvester][TICK] Snapshot: recorded frames in {}: {} (active {})", dimId, FrameRegistry.countRecordedFrames(dimId), FrameRegistry.countActiveFrames(dimId));
                        // One-time catch-up: scan currently-loaded vanilla ItemFrame entities in this level
                        try {
                            Constants.LOG.info("[FastHarvester][TICK] Performing one-time catch-up scan for loaded item frames in {}", dimId);
                            AABB worldBox = new AABB(-30000000, 0, -30000000, 30000000, 256, 30000000);
                            List<ItemFrame> loadedFrames = level.getEntitiesOfClass(ItemFrame.class, worldBox);
                            Constants.LOG.info("[FastHarvester][TICK] Catch-up found {} item frames in {}", loadedFrames.size(), dimId);
                            for (ItemFrame f : loadedFrames) {
                                try {
                                    try { Constants.LOG.info("[FastHarvester][TICK] Catch-up frame at {} direction={}", f.blockPosition(), f.getDirection()); } catch (Throwable ignored) {}
                                    var held = f.getItem();
                                    if (held == null || held.isEmpty()) { Constants.LOG.info("[FastHarvester][TICK] Catch-up frame {} holds nothing.", f.blockPosition()); continue; }
                                    try { Constants.LOG.info("[FastHarvester][TICK] Catch-up frame {} holds item: {}", f.blockPosition(), held.getItem().getClass().getName()); } catch (Throwable ignored) {}
                                    if (f.getDirection() != Direction.UP) { Constants.LOG.info("[FastHarvester][TICK] Catch-up frame {} skipped: not facing UP ({}).", f.blockPosition(), f.getDirection()); continue; }
                                    if (!(held.getItem() instanceof HoeItem)) { Constants.LOG.info("[FastHarvester][TICK] Catch-up frame {} skipped: held item is not a hoe.", f.blockPosition()); continue; }
                                    BlockPos pos = f.blockPosition();
                                    BlockPos chestPos = pos;
                                    BlockEntity be = level.getBlockEntity(chestPos);
                                    if (!(be instanceof Container)) {
                                        BlockPos below = pos.below();
                                        BlockEntity beBelow = level.getBlockEntity(below);
                                        if (beBelow instanceof Container) {
                                            be = beBelow;
                                            chestPos = below;
                                        }
                                    }
                                    if (be instanceof Container chest) {
                                        boolean chestWaterlogged = false;
                                        try { BlockState cs = level.getBlockState(chestPos); chestWaterlogged = cs.getValue(BlockStateProperties.WATERLOGGED); } catch (Throwable ignored) {}
                                        boolean nearbyFarmlandCrop = false;
                                        int r = Math.min(5, Math.max(1, Config.scanRange));
                                        outer3: for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
                                            BlockState ns = level.getBlockState(chestPos.offset(dx, 0, dz));
                                            net.minecraft.world.level.block.Block b = ns.getBlock();
                                            if (b == Blocks.WHEAT || b == Blocks.BEETROOTS || b == Blocks.CARROTS || b == Blocks.POTATOES || b == Blocks.MELON_STEM || b == Blocks.PUMPKIN_STEM) { nearbyFarmlandCrop = true; break outer3; }
                                        }
                                        if (nearbyFarmlandCrop && !chestWaterlogged) {
                                            Constants.LOG.info("[FastHarvester][TICK] Catch-up skipping anchor at {} in {}: chest not waterlogged but nearby farmland crops present.", pos, dimId);
                                            continue;
                                        }

                                        Constants.LOG.info("[FastHarvester][TICK] Catch-up discovered anchor (vanilla) at {} in {}; registering.", pos, dimId);
                                        FrameRegistry.registerFrame(dimId, pos, chest, held.copy());
                                    } else {
                                        Constants.LOG.info("[FastHarvester][TICK] Catch-up no container block-entity near frame pos {} (be={}).", f.blockPosition(), be == null ? "null" : be.getClass().getName());
                                    }
                                } catch (Throwable t) { Constants.LOG.warn("[FastHarvester][TICK] Catch-up per-frame processing error: {}", t.toString()); }
                            }
                        } catch (Throwable t) { Constants.LOG.warn("[FastHarvester][TICK] Catch-up discovery error: {}", t.toString()); }
                    }
                    var ready = FrameRegistry.tickAndCollectReady(dimId);
                    if (!ready.isEmpty()) {
                        Constants.LOG.info("[FastHarvester][TICK] {} anchors ready in {}: {}", ready.size(), dimId, ready);
                        FrameScanner scanner = new FrameScanner();
                        for (var anchor : ready) {
                            try {
                                scanner.scanFarm(anchor, level);
                            } catch (Throwable t) {
                                Constants.LOG.warn("[FastHarvester][TICK] Scan failed for {}: {}", anchor, t.toString());
                            }
                        }
                    }
                    tickSnapshotLogged = true;
                }
            } catch (Throwable t) {
                Constants.LOG.warn("[FastHarvester][TICK] Unexpected ticker error: {}", t.toString());
            }
        });
    }

    private static class ItemStackWrapper {
        private final Object raw;
        ItemStackWrapper(Object raw) { this.raw = raw; }
        boolean isEmpty() { try { Method m = raw.getClass().getMethod("isEmpty"); Object r = m.invoke(raw); return Boolean.TRUE.equals(r); } catch (Throwable t) { return false; } }
        boolean isHoe() { try { Method g = raw.getClass().getMethod("getItem"); Object it = g.invoke(raw); return it != null && it.getClass().getName().toLowerCase().contains("hoe"); } catch (Throwable t) { return false; } }
        net.minecraft.world.item.ItemStack toItemStack() { return (net.minecraft.world.item.ItemStack) raw; }
    }

    private static ItemStackWrapper extractHeldItemReflectively(BlockEntity be) {
        try {
            for (Method m : be.getClass().getMethods()) {
                String name = m.getName().toLowerCase();
                if (name.contains("getdisplayed") || name.contains("getheld") || name.contains("getitem")) {
                    if (m.getParameterCount() == 0) {
                        Object res = m.invoke(be);
                        if (res instanceof net.minecraft.world.item.ItemStack) return new ItemStackWrapper(res);
                    }
                }
            }
        } catch (Throwable t) { /* ignore */ }
        return null;
    }
}
