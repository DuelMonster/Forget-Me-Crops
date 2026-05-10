package com.forgetmecrops.frame;
import com.forgetmecrops.util.log.LogUtils;
import com.forgetmecrops.config.Config;
import com.forgetmecrops.harvest.CropRegistry;

import com.forgetmecrops.util.ExceptionHandler;
import com.forgetmecrops.platform.adapter.FIF;
import com.forgetmecrops.platform.adapter.FastItemFrameAdapterImpl;
import net.minecraft.world.level.chunk.LevelChunk;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

/**
 * FrameDiscovery: The talent scout that finds and validates item-frame farm anchors!
 * <p>
 * Centralizes all the logic for determining whether a vanilla ItemFrame or a FastItemFrames
 * block-entity qualifies as a valid farm anchor. That means: facing up, holding a hoe (or
 * empty for inactive registration), positioned above a container, with appropriate farmland
 * or Nether Wart soul sand nearby, and not waterlogged in a conflicting way.
 * </p>
 * <p>
 * Also contains the static BFS-based farm area scanners used to detect nearby farmland,
 * crop blocks, Nether Wart farms, and waterlogged chests. If FrameRegistry is HR,
 * FrameDiscovery is the background-check service that decides who gets hired as an anchor.
 * </p>
 */
public class FrameDiscovery {

    private FrameDiscovery() {}

    /**
     * Validates and registers a vanilla {@link ItemFrame} entity as a farm anchor (if it qualifies).
     * Checks: direction must be UP, frame must be above a Container block-entity, nearby farmland
     * or Nether Wart soul sand must be present, and the chest must not be in a conflicting waterlogged state.
     * An empty frame still gets registered as an inactive anchor so it can be activated when a hoe is placed.
     * A non-hoe item in the frame disqualifies it immediately — we're not here to manage any old decoration.
     *
     * @param dimId dimension identifier string
     * @param level the server level containing the frame
     * @param f     the ItemFrame entity to validate
     * @return true if the frame passed all checks and was registered
     */
    public static boolean registerVanillaFrameIfValid(String dimId, ServerLevel level, ItemFrame f) {
        try {
            try { LogUtils.logDebug("[TICK] Frame at {} direction={}", f.blockPosition(), f.getDirection()); } catch (Throwable t) {}
            var held = f.getItem();
            if (f.getDirection() != Direction.UP) { LogUtils.logDebug("[TICK] Frame {} skipped: not facing UP ({}).", f.blockPosition(), f.getDirection()); return false; }
            BlockPos pos = f.blockPosition();
            try { LogUtils.logInfo("[TICK] registerVanillaFrameIfValid: pos={} dir={} held={} count={}", pos, f.getDirection(), (held == null || held.isEmpty()) ? "empty" : held.getItem().getClass().getName(), held == null ? 0 : held.getCount()); } catch (Throwable t) {}
            BlockPos chestPos = pos;
            BlockEntity be = level.getBlockEntity(chestPos);
            if (!(be instanceof Container)) {
                BlockPos below = pos.below();
                BlockEntity beBelow = level.getBlockEntity(below);
                if (beBelow instanceof Container) { be = beBelow; chestPos = below; }
            }
            if (be instanceof Container chest) {
                Container resolvedChest = resolveAnchorContainer(level, chestPos, be);
                if (resolvedChest != null) {
                    chest = resolvedChest;
                }
                boolean chestWaterlogged = false;
                try { BlockState cs = level.getBlockState(chestPos); chestWaterlogged = cs.getValue(BlockStateProperties.WATERLOGGED); } catch (Throwable t) {}
                int rX = Math.min(5, Math.max(1, Config.getScanRangeX()));
                int rZ = Math.min(5, Math.max(1, Config.getScanRangeZ()));
                boolean nearbyFarmlandSoil = isNearbyFarmlandSoil(level, chestPos, rX, rZ);
                boolean nearbyFarmlandCrop = isNearbyFarmlandCrop(level, chestPos, rX, rZ);
                boolean isNetherWartFarm = isNearbyNetherWartFarm(level, chestPos, rX, rZ);
                try { LogUtils.logInfo("[TICK] Chest check pos={} be={} waterlogged={} rX={} rZ={} nearbyFarmlandSoil={} nearbyFarmlandCrop={} isNetherWartFarm={}", chestPos, be.getClass().getName(), chestWaterlogged, rX, rZ, nearbyFarmlandSoil, nearbyFarmlandCrop, isNetherWartFarm); } catch (Throwable t) {}
                if (nearbyFarmlandCrop && !chestWaterlogged && !isNetherWartFarm) {
                    LogUtils.logDebug("[TICK] Skipping anchor at {} in {}: chest not waterlogged but nearby farmland crops present.", pos, dimId);
                    return false;
                }
                if (held == null || held.isEmpty()) {
                    try { LogUtils.logDebug("[TICK] Discovered empty frame at {} above chest {}; registering inactive.", pos, chestPos); } catch (Throwable t) {}
                    FrameRegistry.registerFrame(dimId, pos, chest, ItemStack.EMPTY);
                    return true;
                }
                try { LogUtils.logInfo("[TICK] Frame {} holds item: {} x{}", pos, held.getItem().getClass().getName(), held.getCount()); } catch (Throwable t) {}
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
     * Validates and registers a FastItemFrames (FIF) block-entity as a farm anchor.
     * Runs the same gauntlet as the vanilla frame path: held item must be a hoe (or empty for
     * inactive registration), the block below must be a Container, nearby farmland/Nether Wart
     * must be present, and the waterlogged chest check must not disqualify it.
     * <p>
     * Additionally emits extensive debug snapshots (game time, entity identity, chunk-load state)
     * to help diagnose FIF timing issues — because async loading order is genuinely cursed.
     * </p>
     *
     * @param dimId dimension identifier
     * @param level the server level containing the FIF block-entity
     * @param be    the block-entity to inspect
     * @param pos   position of the FIF block-entity
     * @return true if the FIF block-entity was validated and registered as an anchor
     */
    public static boolean registerFIFIfValid(String dimId, ServerLevel level, BlockEntity be, BlockPos pos) {
        try {
            try { LogUtils.logInfo("[FIF] Inspecting potential FIF at {} in {} (be={})", pos, dimId, be.getClass().getName()); } catch (Throwable t) {}
            // Diagnostic snapshot: game time, system time, block-entity identity and chunk-loaded state
            ExceptionHandler.silentTry(() -> {
                final java.util.concurrent.atomic.AtomicLong gameTime = new java.util.concurrent.atomic.AtomicLong(-1L);
                ExceptionHandler.silentTry(() -> gameTime.set(level.getGameTime()));
                long sysTime = System.currentTimeMillis();
                final java.util.concurrent.atomic.AtomicReference<BlockEntity> levelBeRef = new java.util.concurrent.atomic.AtomicReference<>(null);
                ExceptionHandler.silentTry(() -> levelBeRef.set(level.getBlockEntity(pos)));
                BlockEntity levelBe = levelBeRef.get();
                boolean beEquals = levelBe == be;
                int beHash = System.identityHashCode(be);
                int levelBeHash = levelBe == null ? -1 : System.identityHashCode(levelBe);
                final java.util.concurrent.atomic.AtomicBoolean chunkLoadedViaAdapter = new java.util.concurrent.atomic.AtomicBoolean(false);
                ExceptionHandler.silentTry(() -> {
                    int cx = pos.getX() >> 4;
                    int cz = pos.getZ() >> 4;
                    net.minecraft.world.level.ChunkPos targetChunk = new net.minecraft.world.level.ChunkPos(cx, cz);
                    for (LevelChunk lc : FastItemFrameAdapterImpl.getLoadedChunks(level)) {
                        if (lc != null && lc.getPos() != null && lc.getPos().equals(targetChunk)) { chunkLoadedViaAdapter.set(true); break; }
                    }
                });
                ExceptionHandler.silentTry(() -> LogUtils.logDebug("[FIF] DIAG register snapshot pos={} gametime={} systime={} beParamClass={} beParamHash={} levelBeClass={} levelBeHash={} beEquals={} chunkLoadedAdapter={}", pos, gameTime.get(), sysTime, be.getClass().getName(), beHash, levelBe == null ? "null" : levelBe.getClass().getName(), levelBeHash, beEquals, chunkLoadedViaAdapter.get()));
            });
            net.minecraft.world.item.ItemStack held = FIF.extractHeldItem(be);
            if (held != null && !held.isEmpty()) {
                try { LogUtils.logDebug("[FIF] Held item at {}: {} x{}", pos, held.getItem().getClass().getName(), held.getCount()); } catch (Throwable t) {}
                boolean isHoe = held.getItem() instanceof HoeItem;
                try { LogUtils.logDebug("[FIF] Held is HoeItem: {}", isHoe); } catch (Throwable t) {}
                if (!isHoe) return false;
            } else {
                try { LogUtils.logDebug("[FIF] No held item at {} (held == null or empty) — will register inactive if chest valid.", pos); } catch (Throwable t) {}
            }
            BlockPos chestPos = pos.below();
            var chestBe = level.getBlockEntity(chestPos);
            try { LogUtils.logDebug("[FIF] BlockEntity at chest pos {}: {}", chestPos, chestBe == null ? "null" : chestBe.getClass().getName()); } catch (Throwable t) {}
            if (!(chestBe instanceof Container)) {
                try { LogUtils.logDebug("[FIF] No Container at {} — aborting FIF anchor.", chestPos); } catch (Throwable t) {}
                return false;
            }
            Container chest = resolveAnchorContainer(level, chestPos, chestBe);
            if (chest == null) {
                try { LogUtils.logDebug("[FIF] Could not resolve usable container at {} — aborting FIF anchor.", chestPos); } catch (Throwable t) {}
                return false;
            }
            boolean chestWaterlogged = false;
            BlockState cs = null;
            try { cs = level.getBlockState(chestPos); chestWaterlogged = cs.getValue(BlockStateProperties.WATERLOGGED); } catch (Throwable t) {}
            try { LogUtils.logDebug("[FIF] Chest waterlogged at {}: {}, blockState={}", chestPos, chestWaterlogged, cs == null ? "null" : cs.getBlock().getClass().getName()); } catch (Throwable t) {}
            int rX = Math.min(5, Math.max(1, Config.getScanRangeX()));
            int rZ = Math.min(5, Math.max(1, Config.getScanRangeZ()));
            boolean nearbyFarmlandSoil = isNearbyFarmlandSoil(level, chestPos, rX, rZ);
            boolean nearbyFarmlandCrop = isNearbyFarmlandCrop(level, chestPos, rX, rZ);
            boolean isNetherWartFarm = isNearbyNetherWartFarm(level, chestPos, rX, rZ);
            try { LogUtils.logInfo("[FIF] chestPos={} be={} rX={} rZ={} nearbyFarmlandSoil={} nearbyFarmlandCrop={} chestWaterlogged={} isNetherWartFarm={}", chestPos, chestBe.getClass().getName(), rX, rZ, nearbyFarmlandSoil, nearbyFarmlandCrop, chestWaterlogged, isNetherWartFarm); } catch (Throwable t) {}
            if (nearbyFarmlandCrop && !chestWaterlogged && !isNetherWartFarm) {
                LogUtils.logDebug("[TICK] FIF anchor at {} in {} skipped: chest not waterlogged but nearby farmland crops present.", pos, dimId);
                return false;
            }
            if (held == null || held.isEmpty()) {
                try { LogUtils.logDebug("[FIF] Registering inactive FIF anchor at {} above chest {}.", pos, chestPos); } catch (Throwable t) {}
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
     * Returns whether there are crop blocks rooted in farmland near the given chest position.
     * Crops are expected one block above the chest-level scan row, so this checks y+1 offsets.
     * Used to disqualify anchors that appear above chest-level farmland without Nether Wart context.
     *
     * @param level    the server level for block lookups
     * @param chestPos the chest position to center the scan on
     * @param rX       search radius in the X direction
     * @param rZ       search radius in the Z direction
     * @return true if any recognized crop block is found in the scan area
     */
    public static boolean isNearbyFarmlandCrop(ServerLevel level, BlockPos chestPos, int rX, int rZ) {
        for (int dx = -rX; dx <= rX; dx++) for (int dz = -rZ; dz <= rZ; dz++) {
            // Crops rooted in farmland are above chest-level farmland blocks.
            BlockState ns = level.getBlockState(chestPos.offset(dx, 1, dz));
            Block b = ns.getBlock();
            Block rep = CropRegistry.canonicalCropBlock(b);
            if (rep != null && CropRegistry.isCropBlock(rep)) return true;
        }
        return false;
    }

    /**
     * Returns whether there is tilled farmland soil at the chest level near the given position.
     * Just checks for the farmland block (not whether it has any crops planted).
     * Used as an early indicator that this area might be a legitimate crop farm.
     *
     * @param level    the server level for block lookups
     * @param chestPos the chest position to center the scan on
     * @param rX       search radius in the X direction
     * @param rZ       search radius in the Z direction
     * @return true if any farmland block is found at the same Y as the chest
     */
    public static boolean isNearbyFarmlandSoil(ServerLevel level, BlockPos chestPos, int rX, int rZ) {
        for (int dx = -rX; dx <= rX; dx++) for (int dz = -rZ; dz <= rZ; dz++) {
            if (level.getBlockState(chestPos.offset(dx, 0, dz)).is(Blocks.FARMLAND)) return true;
        }
        return false;
    }

    /**
     * Returns whether this chest position is adjacent to a Nether Wart farm.
     * Checks for soul sand (which is the planting medium) at chest level, plus nether_wart
     * blocks one level above or at the same level as a fallback.
     * Having soul sand with no Nether Wart planted yet still counts — the farmer is prepared.
     *
     * @param level    the server level for block lookups
     * @param chestPos the chest position to scan around
     * @param rX       X radius
     * @param rZ       Z radius
     * @return true if soul sand or nether wart is detected nearby
     */
    private static boolean isNearbyNetherWartFarm(ServerLevel level, BlockPos chestPos, int rX, int rZ) {
        for (int dx = -rX; dx <= rX; dx++) for (int dz = -rZ; dz <= rZ; dz++) {
            BlockPos basePos = chestPos.offset(dx, 0, dz);
            // Treat prepared soul sand as a valid Nether Wart farm context, even if currently unplanted.
            if (level.getBlockState(basePos).is(Blocks.SOUL_SAND)) return true;
            // Nether Wart is planted above soul sand, so check the crop layer above chest level.
            if (level.getBlockState(basePos.above()).is(Blocks.NETHER_WART)) return true;
            // Keep same-level check as a fallback for unusual setups.
            if (level.getBlockState(basePos).is(Blocks.NETHER_WART)) return true;
        }
        return false;
    }

    /**
     * Resolves the most useful Container from a chest block-entity at the given position.
     * For double chests ({@link ChestBlockEntity}), this calls the Minecraft combined-container
     * factory so both halves are accessible as a single inventory.
     * For any other container type (hopper, barrel, etc.), returns the block-entity directly.
     * Returns null only if the block-entity is not a Container at all.
     *
     * @param level    the server level for block state lookups
     * @param chestPos position of the chest block-entity
     * @param chestBe  the block-entity to resolve
     * @return the best Container interface for this position, or null if none applicable
     */
    private static Container resolveAnchorContainer(ServerLevel level, BlockPos chestPos, BlockEntity chestBe) {
        if (!(chestBe instanceof Container baseContainer)) {
            return null;
        }

        try {
            if (chestBe instanceof ChestBlockEntity) {
                BlockState state = level.getBlockState(chestPos);
                if (state.getBlock() instanceof ChestBlock chestBlock) {
                    // Prefer the combined container so double chests expose both halves.
                    Container combined = ChestBlock.getContainer(chestBlock, state, level, chestPos, true);
                    if (combined != null) {
                        return combined;
                    }
                }
            }
        } catch (Throwable t) {}

        return baseContainer;
    }
}

