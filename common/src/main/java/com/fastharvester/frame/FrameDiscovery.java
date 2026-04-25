package com.fastharvester.frame;
import com.fastharvester.Constants;
import com.fastharvester.Config;

import com.fastharvester.platform.adapter.FastItemFrameAdapterImpl;

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

/**
 * Centralized discovery and validation helpers for item-frame based anchors.
 */
public class FrameDiscovery {

    private FrameDiscovery() {}

    public static boolean registerVanillaFrameIfValid(String dimId, ServerLevel level, ItemFrame f) {
        try {
            try { Constants.LOG.debug("[FastHarvester][TICK] Frame at {} direction={}", f.blockPosition(), f.getDirection()); } catch (Throwable ignored) {}
            var held = f.getItem();
            if (held == null || held.isEmpty()) { Constants.LOG.debug("[FastHarvester][TICK] Frame {} holds nothing.", f.blockPosition()); return false; }
            try { Constants.LOG.debug("[FastHarvester][TICK] Frame {} holds item: {}", f.blockPosition(), held.getItem().getClass().getName()); } catch (Throwable ignored) {}
            if (f.getDirection() != Direction.UP) { Constants.LOG.debug("[FastHarvester][TICK] Frame {} skipped: not facing UP ({}).", f.blockPosition(), f.getDirection()); return false; }
            if (!(held.getItem() instanceof HoeItem)) { Constants.LOG.debug("[FastHarvester][TICK] Frame {} skipped: held item is not a hoe.", f.blockPosition()); return false; }
            BlockPos pos = f.blockPosition();
            BlockPos chestPos = pos;
            BlockEntity be = level.getBlockEntity(chestPos);
            if (!(be instanceof Container)) {
                BlockPos below = pos.below();
                BlockEntity beBelow = level.getBlockEntity(below);
                if (beBelow instanceof Container) { be = beBelow; chestPos = below; }
            }
            if (be instanceof Container chest) {
                boolean chestWaterlogged = false;
                try { BlockState cs = level.getBlockState(chestPos); chestWaterlogged = cs.getValue(BlockStateProperties.WATERLOGGED); } catch (Throwable ignored) {}
                boolean nearbyFarmlandCrop = isNearbyFarmlandCrop(level, chestPos, Math.min(5, Math.max(1, Config.scanRange)));
                if (nearbyFarmlandCrop && !chestWaterlogged) {
                    Constants.LOG.debug("[FastHarvester][TICK] Skipping anchor at {} in {}: chest not waterlogged but nearby farmland crops present.", pos, dimId);
                    return false;
                }
                Constants.LOG.info("[FastHarvester][TICK] Discovered anchor (vanilla) at {} in {}; registering.", pos, dimId);
                FrameRegistry.registerFrame(dimId, pos, chest, held.copy());
                return true;
            } else {
                Constants.LOG.debug("[FastHarvester][TICK] No container block-entity near frame pos {} (be={}).", f.blockPosition(), be == null ? "null" : be.getClass().getName());
                return false;
            }
        } catch (Throwable t) {
            Constants.LOG.warn("[FastHarvester][TICK] Per-frame processing error: {}", t.toString());
            return false;
        }
    }

    public static boolean registerFIFIfValid(String dimId, ServerLevel level, BlockEntity be, BlockPos pos) {
        try {
            net.minecraft.world.item.ItemStack held = FastItemFrameAdapterImpl.extractHeldItem(be);
            if (held == null || held.isEmpty()) return false;
            if (!(held.getItem() instanceof HoeItem)) return false;
            BlockPos chestPos = pos.below();
            var chestBe = level.getBlockEntity(chestPos);
            if (!(chestBe instanceof Container)) return false;
            Container chest = (Container) chestBe;
            boolean chestWaterlogged = false;
            try { BlockState cs = level.getBlockState(chestPos); chestWaterlogged = cs.getValue(BlockStateProperties.WATERLOGGED); } catch (Throwable ignored) {}
            boolean nearbyFarmlandCrop = isNearbyFarmlandCrop(level, chestPos, Math.min(5, Math.max(1, Config.scanRange)));
            if (nearbyFarmlandCrop && !chestWaterlogged) {
                Constants.LOG.debug("[FastHarvester][TICK] FIF anchor at {} in {} skipped: chest not waterlogged but nearby farmland crops present.", pos, dimId);
                return false;
            }
            Constants.LOG.info("[FastHarvester][TICK] Discovered anchor (FIF) at {} in {}; registering.", pos, dimId);
            FrameRegistry.registerFrame(dimId, pos, chest, held.copy());
            return true;
        } catch (Throwable t) {
            Constants.LOG.debug("[FastHarvester][FIF] FastItemFrames discovery failed: {}", t.toString());
            return false;
        }
    }

    public static boolean isNearbyFarmlandCrop(ServerLevel level, BlockPos chestPos, int r) {
        for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
            BlockState ns = level.getBlockState(chestPos.offset(dx, 0, dz));
            net.minecraft.world.level.block.Block b = ns.getBlock();
            if (b == Blocks.WHEAT || b == Blocks.BEETROOTS || b == Blocks.CARROTS || b == Blocks.POTATOES || b == Blocks.MELON_STEM || b == Blocks.PUMPKIN_STEM) return true;
        }
        return false;
    }
}
