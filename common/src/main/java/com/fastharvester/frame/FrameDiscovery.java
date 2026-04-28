package com.fastharvester.frame;
import com.fastharvester.util.log.LogUtils;
import com.fastharvester.config.Config;

import com.fastharvester.platform.adapter.FIF;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.Container;

/**
 * Centralized discovery and validation helpers for item-frame based anchors.
 */
public class FrameDiscovery {

    private FrameDiscovery() {}

    /**
     * Inspect a vanilla `ItemFrame` and register it as an anchor if valid.
     *
     * @param dimId dimension identifier
     * @param level server level containing the frame
     * @param f the ItemFrame entity to inspect
     * @return true if the frame was registered
     */
    public static boolean registerVanillaFrameIfValid(String dimId, ServerLevel level, ItemFrame f) {
        try {
            try { LogUtils.logDebug("[TICK] Frame at {} direction={}", f.blockPosition(), f.getDirection()); } catch (Throwable ignored) {}
            var held = f.getItem();
            if (f.getDirection() != Direction.UP) { LogUtils.logDebug("[TICK] Frame {} skipped: not facing UP ({}).", f.blockPosition(), f.getDirection()); return false; }
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
                int rX = Math.min(5, Math.max(1, Config.scanRangeX));
                int rZ = Math.min(5, Math.max(1, Config.scanRangeZ));
                boolean nearbyFarmlandCrop = isNearbyFarmlandCrop(level, chestPos, rX, rZ);
                if (nearbyFarmlandCrop && !chestWaterlogged) {
                    LogUtils.logDebug("[TICK] Skipping anchor at {} in {}: chest not waterlogged but nearby farmland crops present.", pos, dimId);
                    return false;
                }
                if (held == null || held.isEmpty()) {
                    try { LogUtils.logDebug("[TICK] Discovered empty frame at {} above chest {}; registering inactive.", pos, chestPos); } catch (Throwable ignored) {}
                    FrameRegistry.registerFrame(dimId, pos, chest, ItemStack.EMPTY);
                    return true;
                }
                try { LogUtils.logDebug("[TICK] Frame {} holds item: {}", pos, held.getItem().getClass().getName()); } catch (Throwable ignored) {}
                if (!(held.getItem() instanceof HoeItem)) { LogUtils.logDebug("[TICK] Frame {} skipped: held item is not a hoe.", pos); return false; }
                LogUtils.logDebug("[TICK] Discovered anchor (vanilla) at {} in {}; registering active.", pos, dimId);
                FrameRegistry.registerFrame(dimId, pos, chest, held.copy());
                return true;
            } else {
                LogUtils.logDebug("[TICK] No container block-entity near frame pos {} (be={}).", f.blockPosition(), be == null ? "null" : be.getClass().getName());
                return false;
            }
        } catch (Throwable t) {
            LogUtils.logWarn("[TICK] Per-frame processing error", t);
            return false;
        }
    }

    /**
     * Inspect a FastItemFrame block-entity and register it as an anchor if valid.
     *
     * @param dimId dimension identifier
     * @param level server level containing the frame
     * @param be the block-entity backing the FIF
     * @param pos position of the frame
     * @return true if the FIF anchor was registered
     */
    public static boolean registerFIFIfValid(String dimId, ServerLevel level, BlockEntity be, BlockPos pos) {
        try {
            try { LogUtils.logDebug("[FIF] Inspecting potential FIF at {} in {} (be={})", pos, dimId, be == null ? "null" : be.getClass().getName()); } catch (Throwable ignored) {}
            net.minecraft.world.item.ItemStack held = FIF.extractHeldItem(be);
            if (held != null && !held.isEmpty()) {
                try { LogUtils.logDebug("[FIF] Held item at {}: {} x{}", pos, held.getItem().getClass().getName(), held.getCount()); } catch (Throwable ignored) {}
                boolean isHoe = held.getItem() instanceof HoeItem;
                try { LogUtils.logDebug("[FIF] Held is HoeItem: {}", isHoe); } catch (Throwable ignored) {}
                if (!isHoe) return false;
            } else {
                try { LogUtils.logDebug("[FIF] No held item at {} (held == null or empty) — will register inactive if chest valid.", pos); } catch (Throwable ignored) {}
            }
            BlockPos chestPos = pos.below();
            var chestBe = level.getBlockEntity(chestPos);
            try { LogUtils.logDebug("[FIF] BlockEntity at chest pos {}: {}", chestPos, chestBe == null ? "null" : chestBe.getClass().getName()); } catch (Throwable ignored) {}
            if (!(chestBe instanceof Container)) {
                try { LogUtils.logDebug("[FIF] No Container at {} — aborting FIF anchor.", chestPos); } catch (Throwable ignored) {}
                return false;
            }
            Container chest = (Container) chestBe;
            boolean chestWaterlogged = false;
            BlockState cs = null;
            try { cs = level.getBlockState(chestPos); chestWaterlogged = cs.getValue(BlockStateProperties.WATERLOGGED); } catch (Throwable ignored) {}
            try { LogUtils.logDebug("[FIF] Chest waterlogged at {}: {}, blockState={}", chestPos, chestWaterlogged, cs == null ? "null" : cs.getBlock().getClass().getName()); } catch (Throwable ignored) {}
            int rX = Math.min(5, Math.max(1, Config.scanRangeX));
            int rZ = Math.min(5, Math.max(1, Config.scanRangeZ));
            boolean nearbyFarmlandCrop = isNearbyFarmlandCrop(level, chestPos, rX, rZ);
            try { LogUtils.logDebug("[FIF] nearbyFarmlandCrop={}, chestWaterlogged={}", nearbyFarmlandCrop, chestWaterlogged); } catch (Throwable ignored) {}
            if (nearbyFarmlandCrop && !chestWaterlogged) {
                LogUtils.logDebug("[TICK] FIF anchor at {} in {} skipped: chest not waterlogged but nearby farmland crops present.", pos, dimId);
                return false;
            }
            if (held == null || held.isEmpty()) {
                try { LogUtils.logDebug("[FIF] Registering inactive FIF anchor at {} above chest {}.", pos, chestPos); } catch (Throwable ignored) {}
                FrameRegistry.registerFrame(dimId, pos, chest, ItemStack.EMPTY);
                return true;
            }
            LogUtils.logDebug("[TICK] Discovered anchor (FIF) at {} in {}; registering active.", pos, dimId);
            FrameRegistry.registerFrame(dimId, pos, chest, held.copy());
            return true;
        } catch (Throwable t) {
            LogUtils.logDebug("[FIF] FastItemFrames discovery failed", t);
            return false;
        }
    }

    /**
     * Return whether there are farmland crops near the given chest position.
     *
        * @param level server level for lookup
        * @param chestPos position to inspect around
        * @param rX search radius along the X axis
        * @param rZ search radius along the Z axis
     * @return true if a farmland crop is nearby
     */
    public static boolean isNearbyFarmlandCrop(ServerLevel level, BlockPos chestPos, int rX, int rZ) {
        for (int dx = -rX; dx <= rX; dx++) for (int dz = -rZ; dz <= rZ; dz++) {
            BlockState ns = level.getBlockState(chestPos.offset(dx, 0, dz));
            net.minecraft.world.level.block.Block b = ns.getBlock();
            if (com.fastharvester.harvest.CropRegistry.isCropBlock(b)) return true;
        }
        return false;
    }
}
