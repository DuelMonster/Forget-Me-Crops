package com.fastharvester.neoforge;

// 🌾 NeoForge ticker: quietly counts down and nudges farms to do their thing.
// Emotional state: hopeful. It believes in your crops.

import com.fastharvester.FrameRegistry;
import com.fastharvester.FrameScanner;
import com.fastharvester.Constants;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.Container;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;

import java.util.List;

public class NeoForgeFarmTicker {
    private static boolean tickSnapshotLogged = false;
    public static void init(IEventBus bus) {
        bus.addListener(NeoForgeFarmTicker::onChunkLoad);
        bus.addListener(NeoForgeFarmTicker::onChunkUnload);
        bus.addListener(NeoForgeFarmTicker::onServerTick);
    }

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

            // Unregister FIF block-entities
            try {
                for (var e : lc.getBlockEntities().entrySet()) {
                    BlockPos pos = e.getKey();
                    BlockEntity be = e.getValue();
                    if (be == null) continue;
                    String cls = be.getClass().getName().toLowerCase();
                    if (!cls.contains("fastitemframes")) continue;
                    FrameRegistry.unregisterFrame(dimId, pos);
                }
            } catch (Throwable ignored) {}
        } catch (Throwable t) {
            Constants.LOG.warn("[FastHarvester][TICK] NeoForge chunk-unload cleanup error: {}", t.toString());
        }
    }

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
            Constants.LOG.info("[FastHarvester][TICK] NeoForge found {} item frames in chunk {}.", frames.size(), lc.getPos());
            for (ItemFrame f : frames) {
                try {
                    try { Constants.LOG.info("[FastHarvester][TICK] NeoForge frame at {} direction={}", f.blockPosition(), f.getDirection()); } catch (Throwable ignored) {}
                    var held = f.getItem();
                    if (held == null || held.isEmpty()) { Constants.LOG.info("[FastHarvester][TICK] NeoForge frame {} holds nothing.", f.blockPosition()); continue; }
                    try { Constants.LOG.info("[FastHarvester][TICK] NeoForge frame {} holds item: {}", f.blockPosition(), held.getItem().getClass().getName()); } catch (Throwable ignored) {}
                    if (f.getDirection() != Direction.UP) { Constants.LOG.info("[FastHarvester][TICK] NeoForge frame {} skipped: not facing UP ({}).", f.blockPosition(), f.getDirection()); continue; }
                    if (!(held.getItem() instanceof HoeItem)) { Constants.LOG.info("[FastHarvester][TICK] NeoForge frame {} skipped: held item is not a hoe.", f.blockPosition()); continue; }
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
                        int r = Math.min(5, Math.max(1, com.fastharvester.Config.scanRange));
                        outer: for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
                            BlockState ns = level.getBlockState(chestPos.offset(dx, 0, dz));
                            Block b = ns.getBlock();
                            if (b == Blocks.WHEAT || b == Blocks.BEETROOTS || b == Blocks.CARROTS || b == Blocks.POTATOES || b == Blocks.MELON_STEM || b == Blocks.PUMPKIN_STEM) { nearbyFarmlandCrop = true; break outer; }
                        }
                        if (nearbyFarmlandCrop && !chestWaterlogged) {
                            Constants.LOG.info("[FastHarvester][TICK] NeoForge skipping anchor at {} in {}: chest not waterlogged but nearby farmland crops present.", pos, dimId);
                            continue;
                        }

                        Constants.LOG.info("[FastHarvester][TICK] NeoForge discovered anchor (vanilla) at {} in {}; registering.", pos, dimId);
                        FrameRegistry.registerFrame(dimId, pos, chest, held.copy());
                    } else {
                        Constants.LOG.info("[FastHarvester][TICK] NeoForge no container near frame pos {} (be={}).", f.blockPosition(), be == null ? "null" : be.getClass().getName());
                    }
                } catch (Throwable t) { Constants.LOG.warn("[FastHarvester][TICK] NeoForge per-frame processing error: {}", t.toString()); }
            }

            // FastItemFrames: attempt block-entity enumeration on the chunk
            try {
                for (var e : lc.getBlockEntities().entrySet()) {
                    BlockPos pos = e.getKey();
                    BlockEntity be = e.getValue();
                    if (be == null) continue;
                    String cls = be.getClass().getName().toLowerCase();
                    if (!cls.contains("fastitemframes")) continue;
                    // reflectively extract held item
                    net.minecraft.world.item.ItemStack held = extractHeldItemReflectively(be);
                    if (held == null || held.isEmpty()) continue;
                    if (!(held.getItem() instanceof HoeItem)) continue;
                    BlockPos chestPos = pos.below();
                    var chestBe = level.getBlockEntity(chestPos);
                    if (chestBe instanceof Container chest) {
                        boolean chestWaterlogged = false;
                        try { BlockState cs = level.getBlockState(chestPos); chestWaterlogged = cs.getValue(BlockStateProperties.WATERLOGGED); } catch (Throwable ignored) {}
                        boolean nearbyFarmlandCrop = false;
                        int r = Math.min(5, Math.max(1, com.fastharvester.Config.scanRange));
                        outer3: for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
                            BlockState ns = level.getBlockState(chestPos.offset(dx, 0, dz));
                            Block b = ns.getBlock();
                            if (b == Blocks.WHEAT || b == Blocks.BEETROOTS || b == Blocks.CARROTS || b == Blocks.POTATOES || b == Blocks.MELON_STEM || b == Blocks.PUMPKIN_STEM) { nearbyFarmlandCrop = true; break outer3; }
                        }
                        if (nearbyFarmlandCrop && !chestWaterlogged) {
                            Constants.LOG.info("[FastHarvester][TICK] Skipping FIF anchor at {} in {}: chest not waterlogged but nearby farmland crops present.", pos, dimId);
                            continue;
                        }

                        Constants.LOG.info("[FastHarvester][TICK] Discovered anchor (FIF) at {} in {}; registering.", pos, dimId);
                        FrameRegistry.registerFrame(dimId, pos, chest, held.copy());
                    }
                }
            } catch (Throwable t) {
                Constants.LOG.debug("[FastHarvester][FIF] NeoForge FIF discovery failed: {}", t.toString());
            }

        } catch (Throwable t) {
            Constants.LOG.warn("[FastHarvester][TICK] NeoForge chunk-load discovery error: {}", t.toString());
        }
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        try {
            if (!event.hasTime()) return;
            MinecraftServer server = event.getServer();
            if (server == null) return;
                    for (ServerLevel level : server.getAllLevels()) {
                    String dimId = level.dimension().identifier().toString();
                    if (!tickSnapshotLogged) {
                        Constants.LOG.info("[FastHarvester][TICK] NeoForge snapshot: recorded frames in {}: {} (active {})", dimId, FrameRegistry.countRecordedFrames(dimId), FrameRegistry.countActiveFrames(dimId));
                        try {
                            Constants.LOG.info("[FastHarvester][TICK] NeoForge performing one-time catch-up scan for loaded item frames in {}", dimId);
                            AABB worldBox = new AABB(-30000000, 0, -30000000, 30000000, 256, 30000000);
                            List<ItemFrame> loadedFrames = level.getEntitiesOfClass(ItemFrame.class, worldBox);
                            Constants.LOG.info("[FastHarvester][TICK] NeoForge catch-up found {} item frames in {}", loadedFrames.size(), dimId);
                            for (ItemFrame f : loadedFrames) {
                                try {
                                    try { Constants.LOG.info("[FastHarvester][TICK] NeoForge catch-up frame at {} direction={}", f.blockPosition(), f.getDirection()); } catch (Throwable ignored) {}
                                    var held = f.getItem();
                                    if (held == null || held.isEmpty()) { Constants.LOG.info("[FastHarvester][TICK] NeoForge catch-up frame {} holds nothing.", f.blockPosition()); continue; }
                                    try { Constants.LOG.info("[FastHarvester][TICK] NeoForge catch-up frame {} holds item: {}", f.blockPosition(), held.getItem().getClass().getName()); } catch (Throwable ignored) {}
                                    if (f.getDirection() != Direction.UP) { Constants.LOG.info("[FastHarvester][TICK] NeoForge catch-up frame {} skipped: not facing UP ({}).", f.blockPosition(), f.getDirection()); continue; }
                                    if (!(held.getItem() instanceof HoeItem)) { Constants.LOG.info("[FastHarvester][TICK] NeoForge catch-up frame {} skipped: held item is not a hoe.", f.blockPosition()); continue; }
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
                                        int r = Math.min(5, Math.max(1, com.fastharvester.Config.scanRange));
                                        outer4: for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
                                            BlockState ns = level.getBlockState(chestPos.offset(dx, 0, dz));
                                            Block b = ns.getBlock();
                                            if (b == Blocks.WHEAT || b == Blocks.BEETROOTS || b == Blocks.CARROTS || b == Blocks.POTATOES || b == Blocks.MELON_STEM || b == Blocks.PUMPKIN_STEM) { nearbyFarmlandCrop = true; break outer4; }
                                        }
                                        if (nearbyFarmlandCrop && !chestWaterlogged) {
                                            Constants.LOG.info("[FastHarvester][TICK] NeoForge catch-up skipping anchor at {} in {}: chest not waterlogged but nearby farmland crops present.", pos, dimId);
                                            continue;
                                        }

                                        Constants.LOG.info("[FastHarvester][TICK] NeoForge catch-up discovered anchor (vanilla) at {} in {}; registering.", pos, dimId);
                                        FrameRegistry.registerFrame(dimId, pos, chest, held.copy());
                                    } else {
                                        Constants.LOG.info("[FastHarvester][TICK] NeoForge catch-up no container near frame pos {} (be={}).", f.blockPosition(), be == null ? "null" : be.getClass().getName());
                                    }
                                } catch (Throwable t) { Constants.LOG.warn("[FastHarvester][TICK] NeoForge catch-up per-frame error: {}", t.toString()); }
                            }
                        } catch (Throwable t) { Constants.LOG.warn("[FastHarvester][TICK] NeoForge catch-up discovery error: {}", t.toString()); }
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
            Constants.LOG.warn("[FastHarvester][TICK] NeoForge ticker error: {}", t.toString());
        }
    }

    private static net.minecraft.world.item.ItemStack extractHeldItemReflectively(BlockEntity be) {
        try {
            for (var m : be.getClass().getMethods()) {
                String name = m.getName().toLowerCase();
                if (name.contains("getdisplayed") || name.contains("getheld") || name.contains("getitem")) {
                    if (m.getParameterCount() == 0) {
                        Object res = m.invoke(be);
                        if (res instanceof net.minecraft.world.item.ItemStack) return (net.minecraft.world.item.ItemStack) res;
                    }
                }
            }
        } catch (Throwable t) { /* ignore */ }
        return null;
    }
}
