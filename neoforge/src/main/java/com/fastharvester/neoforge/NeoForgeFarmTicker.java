package com.fastharvester.neoforge;

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

            List<ItemFrame> frames = level.getEntitiesOfClass(ItemFrame.class, box, ef -> ef.getDirection() == Direction.UP);
            String dimId = level.dimension().identifier().toString();
            for (ItemFrame f : frames) {
                try {
                    var held = f.getItem();
                    if (held == null || held.isEmpty()) continue;
                    if (!(held.getItem() instanceof HoeItem)) continue;
                    BlockPos pos = f.blockPosition();
                    BlockEntity be = level.getBlockEntity(pos);
                    if (be instanceof Container chest) {
                        boolean chestWaterlogged = false;
                        try { BlockState cs = level.getBlockState(pos); chestWaterlogged = cs.getValue(BlockStateProperties.WATERLOGGED); } catch (Throwable ignored) {}
                        boolean nearbyFarmlandCrop = false;
                        int r = Math.min(5, Math.max(1, com.fastharvester.Config.scanRange));
                        outer: for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
                            BlockState ns = level.getBlockState(pos.offset(dx, 0, dz));
                            Block b = ns.getBlock();
                            if (b == Blocks.WHEAT || b == Blocks.BEETROOTS || b == Blocks.CARROTS || b == Blocks.POTATOES || b == Blocks.MELON_STEM || b == Blocks.PUMPKIN_STEM) { nearbyFarmlandCrop = true; break outer; }
                        }
                        if (nearbyFarmlandCrop && !chestWaterlogged) continue;

                        FrameRegistry.registerFrame(dimId, pos, chest, held.copy());
                    }
                } catch (Throwable t) { /* ignore per-frame */ }
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
                        if (nearbyFarmlandCrop && !chestWaterlogged) continue;

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
                var ready = FrameRegistry.tickAndCollectReady(dimId);
                if (!ready.isEmpty()) {
                    FrameScanner scanner = new FrameScanner();
                    for (var anchor : ready) {
                        try {
                            scanner.scanFarm(anchor, level);
                        } catch (Throwable t) {
                            Constants.LOG.warn("[FastHarvester][TICK] Scan failed for {}: {}", anchor, t.toString());
                        }
                    }
                }
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
