package com.forgetmecrops.frame;
import com.forgetmecrops.util.log.LogUtils;
import com.forgetmecrops.config.Config;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Locale;

import com.forgetmecrops.platform.adapter.FIF;
import com.forgetmecrops.util.chest.ChestUtils;
import com.forgetmecrops.harvest.HarvestUtils;
import com.forgetmecrops.harvest.HarvestContext;
import com.forgetmecrops.harvest.CropRegistry;
import com.forgetmecrops.enums.RotationMode;
import java.util.HashMap;
import java.util.Comparator;
import java.util.List;
import java.util.ArrayList;
import java.util.Deque;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.Iterator;

/**
 * FrameScanner: The intrepid explorer of your blocky agricultural world!
 * <p>
 * This is the class that actually walks the spiral, harvests the crops, manages frame rotations,
 * checks anchor validity, and generally does the heavy lifting that makes the whole mod worth using.
 * Contains the spiral generator, the per-anchor scan logic, BFS farm discovery, and enough
 * reflection-powered hoe-reading code to make a regular programmer weep quietly.
 * </p>
 * <p>
 * Also holds the {@link FarmScanTask} infrastructure for tick-sliced scanning — because running
 * the full spiral in a single tick is the fastest way to a "server is lagging" complaint.
 * </p>
 */
public class FrameScanner {
    /** Maximum number of anchors processed in a single scan run. Prevents any one tick from becoming a lag monster. */
    public static final int MAX_FRAMES_PER_RUN = 24;

    /** The four cardinal horizontal directions: N/E/S/W.
     *  Extracted as a constant because allocating a fresh Direction[] array on every spiral step is wasteful and rude. */
    static final Direction[] HORIZ_DIRS = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    /** Default constructor. Required even though most of FrameScanner's interesting things are static methods. */
    public FrameScanner() {}

    /**
     * Anchor: A registered farm anchor — the cornerstone of every automated harvest operation.
     * <p>
     * Bundles together the item-frame position, its linked chest container, and the stored hoe.
     * Without an Anchor, the scanner doesn't know where to start, where to drop loot, or what
     * to use for harvesting. The Anchor knows all three. It's the whole reason we're here.
     * </p>
     */
    public static class Anchor {
        /** The chest/container linked to this anchor. All harvested drops end up here. */
        public final Container chest;
        /** The block position of the item frame acting as the anchor. This is ground zero for the spiral. */
        public final BlockPos framePos;
        /** The hoe currently associated with this anchor. The actual farming instrument. May be EMPTY if inactive. */
        public final ItemStack hoe;

        /**
         * Creates a new Anchor — the cornerstone of a farm operation.
         *
         * @param chest    the linked container where harvested crops will be deposited
         * @param framePos the block position of the anchoring item frame
         * @param hoe      the hoe to use for this farm; may be ItemStack.EMPTY if no hoe is present yet
         */
        public Anchor(Container chest, BlockPos framePos, ItemStack hoe) {
            this.chest = chest;
            this.framePos = framePos;
            this.hoe = hoe;
        }

        @Override
        public String toString() { return "Anchor[pos="+framePos+",hoe="+hoe+"]"; }
    }

    /**
     * Perform a full farm scan from the given anchor.
     *
     * @param anchor anchor to scan from
     * @param level level in which to run the scan
     * @return true if any crops were harvested
     */
    public boolean scanFarm(Anchor anchor, Level level) {
        LogUtils.logDebug("[SCAN] Starting farm scan from anchor: {}", anchor);
        if (anchor == null || anchor.chest == null || level == null) {
            LogUtils.logWarn("[SCAN] Anchor or environment missing, aborting scan.");
            return false;
        }

        int blocksScanned = 0;
        int cropsFound = 0;
        BlockPos center = anchor.framePos;
        String dimId = "";
        try { dimId = level.dimension().identifier().toString(); } catch (Throwable ignored) {}

        // Quick anchor validity check: ensure the item-frame (or FIF block-entity) still exists
        try {
            if (!isFrameStillPresent(level, center)) {
                try { LogUtils.logWarn("[SCAN] Anchor frame missing at {} in {}; unregistering and aborting scan.", center, dimId); } catch (Throwable ignored) {}
                try { FrameRegistry.unregisterFrame(dimId, center); } catch (Throwable ignored) {}
                return false;
            }
            if (!isChestStillValid(level, anchor)) {
                try { LogUtils.logWarn("[SCAN] Anchor chest missing/changed for {}; unregistering and aborting scan.", center); } catch (Throwable ignored) {}
                try { FrameRegistry.unregisterFrame(dimId, center); } catch (Throwable ignored) {}
                return false;
            }
        } catch (Throwable t) {
            LogUtils.logDebug("[SCAN] Anchor presence check failed for " + center, t);
        }

        int rangeX = Math.max(1, Config.getScanRangeX());
        int rangeZ = Math.max(1, Config.getScanRangeZ());

        ItemStack currentHoe = anchor.hoe == null ? ItemStack.EMPTY : anchor.hoe.copy();
        try {
            // Prefer the physical frame-held hoe at scan start; fall back to replacement from chest only when frame is empty.
            ItemStack frameHoe = readHoeFromFrame(level, center);
            if (frameHoe != null && !frameHoe.isEmpty() && frameHoe.getItem() instanceof HoeItem) {
                currentHoe = frameHoe.copy();
                try { FrameRegistry.updateHoe(dimId, center, currentHoe.copy()); } catch (Throwable ignored) {}
            } else {
                // Frame is empty; delegate replacement logic to FrameHoeReplacement which encapsulates chest/frame transactions.
                if (currentHoe.isEmpty()) {
                    try {
                        HarvestContext tempCtx = new HarvestContext(anchor, level, ItemStack.EMPTY, anchor.chest, null);
                        com.forgetmecrops.util.hoe.FrameHoeReplacement.tryReplaceBrokenHoe(tempCtx);
                        if (!tempCtx.getHoe().isEmpty()) {
                            currentHoe = tempCtx.getHoe().copy();
                        }
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}

        if (currentHoe.isEmpty()) {
            LogUtils.logDebug("[SCAN] No hoe available for anchor {}; aborting scan.", anchor);
            return false;
        }

        HarvestContext ctx = new HarvestContext(anchor, level, currentHoe, anchor.chest, null);

        List<SpiralStep> spiral = generateSpiral(center, rangeX, rangeZ);
        int spiralSteps = spiral.size();
        boolean anyHarvested = false;

        Direction lastDir = null;
        int lastComputedRot = getFrameRotation(level, center) & 7;
        int initialRot = lastComputedRot;
        for (int i = 0; i < spiralSteps; i++) {
            SpiralStep step = spiral.get(i);
            BlockPos pos = step.pos;
            Direction dir = step.dir;
            BlockState state = level.getBlockState(pos);
            blocksScanned++;

            switch (Config.getRotationMode()) {
                case FOLLOW_ROTATION -> {
                    if (lastDir == null || dir != lastDir) {
                        setFrameRotation(level, center, dirToRotation(dir));
                        lastDir = dir;
                    }
                }
                case FULL_ROTATION -> {
                    // Relative mapping: i=0 -> initialRot+1, i=spiralSteps-1 -> initialRot (full cycle).
                    int rot = spiralSteps > 1
                            ? (initialRot + 1 + (int) Math.floor((double) i * 7.0 / (spiralSteps - 1))) & 7
                            : initialRot;
                    if (rot != lastComputedRot) {
                        setFrameRotation(level, center, rot);
                        lastComputedRot = rot;
                    }
                }
                case SINGLE_STEP -> {
                }
            }

            // Re-check anchor presence and chest integrity before attempting to harvest.
            try {
                boolean framePresent = false;
                java.util.List<ItemFrame> frames = level.getEntitiesOfClass(ItemFrame.class, new AABB(center));
                for (ItemFrame f : frames) { if (f.blockPosition().equals(center)) { framePresent = true; break; } }
                BlockEntity be = level.getBlockEntity(center);
                if (!framePresent && (be == null || !FIF.isFastItemFrameBlockEntity(be))) {
                    try { LogUtils.logWarn("[SCAN] Anchor frame removed at {} during scan; unregistering and aborting.", center); } catch (Throwable ignored) {}
                    try { FrameRegistry.unregisterFrame(dimId, center); } catch (Throwable ignored) {}
                    ctx.logSummary();
                    return cropsFound > 0;
                }
                if (anchor.chest instanceof BlockEntity chestBe) {
                    BlockPos chestPos = chestBe.getBlockPos();
                    BlockEntity current = level.getBlockEntity(chestPos);
                    if (current != chestBe || !(current instanceof Container)) {
                        try { LogUtils.logWarn("[SCAN] Anchor chest removed or changed at {} during scan; unregistering and aborting.", chestPos); } catch (Throwable ignored) {}
                        try { FrameRegistry.unregisterFrame(dimId, center); } catch (Throwable ignored) {}
                        ctx.logSummary();
                        return cropsFound > 0;
                    }
                }
            } catch (Throwable t) { LogUtils.logDebug("[SCAN] anchor re-check failed during scan", t); }

            // Before attempting to harvest, ensure a hoe is physically present on the frame.
            try {
                ItemStack liveHoe = readHoeFromFrame(level, center);
                if (liveHoe == null || liveHoe.isEmpty()) {
                    try { LogUtils.logDebug("[SCAN] Hoe removed from frame at {} during scan; aborting.", center); } catch (Throwable ignored) {}
                    ctx.logSummary();
                    return cropsFound > 0;
                }
                // make sure the HarvestContext.hoe matches the live frame hoe
                try { ctx.setHoe(liveHoe); } catch (Throwable ignored) {}
            } catch (Throwable ignored) {}

            boolean harvested = false;
            try {
                Block block = state.getBlock();

                if (state.is(Blocks.MELON) || state.is(Blocks.PUMPKIN)) {
                    HarvestUtils.harvestCrop(ctx, pos, state, s -> true, s -> null);
                    harvested = ctx.getHarvestedCount() > 0;
                    cropsFound = Math.max(cropsFound, ctx.getHarvestedCount());
                } else if (state.is(Blocks.MELON_STEM) || state.is(Blocks.PUMPKIN_STEM)) {
                    for (Direction d : HORIZ_DIRS) {
                        BlockPos npos = pos.relative(d);
                        BlockState ns = level.getBlockState(npos);
                        if ((ns.is(Blocks.MELON) && state.is(Blocks.MELON_STEM)) || (ns.is(Blocks.PUMPKIN) && state.is(Blocks.PUMPKIN_STEM))) {
                            HarvestUtils.harvestCrop(ctx, npos, ns, s -> true, s -> null);
                            harvested = ctx.getHarvestedCount() > 0;
                            cropsFound = Math.max(cropsFound, ctx.getHarvestedCount());
                            break;
                        }
                    }
                } else {
                    boolean isCrop = block instanceof CropBlock || state.is(Blocks.NETHER_WART) || state.is(Blocks.SWEET_BERRY_BUSH);
                    if (isCrop) {
                        int threshold = getMaturityThreshold(state);
                        int age = getAgeSafe(state);
                        boolean mature = age >= threshold;
                        if (mature) {
                            HarvestUtils.harvestCrop(ctx, pos, state,
                                    s -> {
                                        int a = getAgeSafe(s);
                                        if (a < 0) return false;
                                        return a >= getMaturityThreshold(s);
                                    },
                                    s -> {
                                        try {
                                            if (s.is(Blocks.SWEET_BERRY_BUSH)) return setAgeSafe(s, 1);
                                            return setAgeSafe(s, 0);
                                        } catch (Throwable tt) { return null; }
                                    });
                            harvested = ctx.getHarvestedCount() > 0;
                            cropsFound = Math.max(cropsFound, ctx.getHarvestedCount());
                        }
                    }
                }
            } catch (Throwable t) {
                LogUtils.logDebug("[SCAN] Exception while scanning " + pos, t);
            }

            if (harvested) anyHarvested = true;

            if (ctx.isChestFull()) {
                try {
                    FrameRegistry.setCooldown(dimId, center, Config.getChestFullCooldownTicks());
                } catch (Throwable ignored) {}
                ctx.logSummary();
                return cropsFound > 0;
            }

            tryAutoPlantAndTill(anchor, ctx, pos, level);

            if (ctx.isChestFull()) {
                try {
                    FrameRegistry.setCooldown(dimId, center, Config.getChestFullCooldownTicks());
                } catch (Throwable ignored) {}
                ctx.logSummary();
                return cropsFound > 0;
            }
        }

        if (anyHarvested && Config.getRotationMode() == RotationMode.SINGLE_STEP) {
            int newRot = (getFrameRotation(level, center) + 1) & 7;
            setFrameRotation(level, center, newRot);
        }

        LogUtils.logDebug("[SCAN] Scan complete. Blocks scanned: {}, crops found: {}.", blocksScanned, cropsFound);
        return cropsFound > 0;
    }

    /**
     * Generates the flat outward spiral of BlockPos steps from the given center.
     * Walks in ever-expanding rings (N→E→S→W), staying within the configured scan ranges.
     * Out-of-range positions are skipped so the list stays tight and iteration is efficient.
     *
     * @param center the anchor frame position (spiral origin)
     * @param rangeX east-west radius in blocks
     * @param rangeZ north-south radius in blocks
     * @return ordered list of {@link SpiralStep}s from center outward
     */
    static List<SpiralStep> generateSpiral(BlockPos center, int rangeX, int rangeZ) {
        List<SpiralStep> spiral = new ArrayList<>();
        int x = 0, z = 0;
        spiral.add(new SpiralStep(center, Direction.NORTH));
        int stepSize = 1;
        int[] dxs = new int[]{1, 0, -1, 0};
        int[] dzs = new int[]{0, 1, 0, -1};
        int dir = 0;
        int maxCells = (2 * rangeX + 1) * (2 * rangeZ + 1);
        int visitedCells = 1; // center already added
        outer:
        while (visitedCells < maxCells) {
            for (int rep = 0; rep < 2; rep++) {
                for (int i = 0; i < stepSize; i++) {
                    x += dxs[dir];
                    z += dzs[dir];
                    if (Math.abs(x) <= rangeX && Math.abs(z) <= rangeZ) {
                        Direction d = getSpiralDirection(dxs[dir], dzs[dir]);
                        spiral.add(new SpiralStep(new BlockPos(center.getX() + x, center.getY(), center.getZ() + z), d));
                        visitedCells++;
                        if (visitedCells >= maxCells) break outer;
                    }
                }
                dir = (dir + 1) & 3;
            }
            stepSize++;
        }
        return spiral;
    }

    /**
     * Converts a unit-step (dx, dz) into the corresponding cardinal Direction.
     * Used by the spiral generator to tag each step with the direction it was
     * moving in, so FOLLOW_ROTATION mode can align the frame to match.
     */
    static Direction getSpiralDirection(int dx, int dz) {
        if (dx == 1 && dz == 0) return Direction.EAST;
        if (dx == -1 && dz == 0) return Direction.WEST;
        if (dx == 0 && dz == 1) return Direction.SOUTH;
        if (dx == 0 && dz == -1) return Direction.NORTH;
        return Direction.NORTH;
    }

    /**
     * Maps a cardinal Direction to the corresponding item-frame rotation value (0–7 in steps of 2).
     * Frame rotation 0 = north, 2 = east, 4 = south, 6 = west.
     * Used by FOLLOW_ROTATION and FULL_ROTATION modes to align the frame during scanning.
     *
     * @param dir the direction the scanner is currently moving
     * @return the frame rotation value (0, 2, 4, or 6)
     */
    static int dirToRotation(Direction dir) {
        return switch (dir) {
            case NORTH -> 0;
            case EAST -> 2;
            case SOUTH -> 4;
            case WEST -> 6;
            default -> 0;
        };
    }

    /** Returns true if the item-frame entity or a FIF block-entity still exists at pos. */
    static boolean isFrameStillPresent(Level level, BlockPos pos) {
        try {
            java.util.List<ItemFrame> frames = level.getEntitiesOfClass(ItemFrame.class, new AABB(pos));
            for (ItemFrame f : frames) { if (f.blockPosition().equals(pos)) return true; }
            BlockEntity be = level.getBlockEntity(pos);
            return be != null && FIF.isFastItemFrameBlockEntity(be);
        } catch (Throwable t) { return false; }
    }

    /** Returns true if the anchor's chest block-entity is still the same container that was registered. */
    static boolean isChestStillValid(Level level, Anchor anchor) {
        if (!(anchor.chest instanceof BlockEntity chestBe)) return true;
        BlockPos chestPos = chestBe.getBlockPos();
        BlockEntity current = level.getBlockEntity(chestPos);
        return current == chestBe && current instanceof Container;
    }

    /**
     * Picks the dominant crop type from the neighbor-count map and attempts to plant one seed at the position.
     * Falls back to a chest-inventory scan if no neighbor consensus exists.
     * Returns true if a block was successfully placed (seed consumed from chest).
     *
     * @param counts  map of block → neighbor-count vote tallies (may be empty)
     * @param anchor  the farm anchor providing the chest inventory
     * @param level   the level in which to place the block
     * @param pos     the position to plant at
     * @return true if a crop was planted, false if no seed was available or no crop was determined
     */
    static boolean tryPlantConsensus(Map<Block, Integer> counts, Anchor anchor, Level level, BlockPos pos) {
        Block chosen = null;
        if (!counts.isEmpty()) {
            chosen = counts.entrySet().stream().max(Comparator.comparingInt(Map.Entry::getValue)).get().getKey();
        } else {
            chosen = chooseChestFallbackCrop(anchor, level, pos);
        }
        if (chosen == null) return false;
        Item seed = CropRegistry.clutterSeed(chosen);
        if (seed == null || !ChestUtils.removeOne(anchor.chest, seed, false)) return false;
        BlockState plantState = chosen.defaultBlockState();
        try { if (plantState.getBlock() instanceof CropBlock) plantState = setAgeSafe(plantState, 0); } catch (Throwable ignored) {}
        level.setBlock(pos, plantState, 3);
        try { HarvestUtils.playPlantSound(level, pos, plantState); } catch (Throwable ignored) {}
        return true;
    }

    /**
     * When no neighbor-crop consensus can be determined, scans the anchor's chest inventory
     * to find whichever seed type has the most stock, and returns the corresponding crop block.
     * Prefers whichever seed the chest has the most of. For farms that just got established,
     * this avoids the scanner being paralyzed by lack of nearby neighbors.
     *
     * @param anchor the anchor whose chest to check
     * @param level  the level (for reading the soil block below pos)
     * @param pos    the air position to potentially plant at
     * @return the crop block best supported by the chest's current inventory, or null
     */
    private static Block chooseChestFallbackCrop(Anchor anchor, Level level, BlockPos pos) {
        if (anchor == null || anchor.chest == null || level == null || pos == null) return null;

        Block soil = level.getBlockState(pos.below()).getBlock();
        if (soil == Blocks.SOUL_SAND) {
            return ChestUtils.countItem(anchor.chest, net.minecraft.world.item.Items.NETHER_WART) > 0 ? Blocks.NETHER_WART : null;
        }
        if (soil != Blocks.FARMLAND) return null;

        Block bestBlock = null;
        int bestCount = 0;

        int wheatSeeds = ChestUtils.countItem(anchor.chest, net.minecraft.world.item.Items.WHEAT_SEEDS);
        if (wheatSeeds > bestCount) {
            bestBlock = Blocks.WHEAT;
            bestCount = wheatSeeds;
        }

        int carrotCount = ChestUtils.countItem(anchor.chest, net.minecraft.world.item.Items.CARROT);
        if (carrotCount > bestCount) {
            bestBlock = Blocks.CARROTS;
            bestCount = carrotCount;
        }

        int potatoCount = ChestUtils.countItem(anchor.chest, net.minecraft.world.item.Items.POTATO);
        if (potatoCount > bestCount) {
            bestBlock = Blocks.POTATOES;
            bestCount = potatoCount;
        }

        int beetrootSeeds = ChestUtils.countItem(anchor.chest, net.minecraft.world.item.Items.BEETROOT_SEEDS);
        if (beetrootSeeds > bestCount) {
            bestBlock = Blocks.BEETROOTS;
            bestCount = beetrootSeeds;
        }

        return bestBlock;
    }

    /**
     * Checks the given position for empty farmland (auto-plant), empty soul sand (auto-plant Nether Wart),
     * or un-tilled dirt/grass adjacent to farmland (auto-till + plant). For each case, the appropriate
     * seed is consumed from the chest and the block is planted. Hoe durability is applied for tilling.
     *
     * @param anchor the farm anchor providing the chest and hoe
     * @param ctx    the harvest context (for hoe and level state)
     * @param pos    the position to inspect and potentially plant at
     * @param level  the level in which to operate
     */
    static void tryAutoPlantAndTill(Anchor anchor, HarvestContext ctx, BlockPos pos, Level level) {
        BlockState cur = level.getBlockState(pos);
        BlockPos belowPos = pos.below();
        BlockState below = level.getBlockState(belowPos);
        if (below != null && below.getBlock() == Blocks.FARMLAND && cur.isAir()) {
            Map<Block, Integer> counts = new HashMap<>();
            for (Direction d : HORIZ_DIRS) {
                Block rep = CropRegistry.canonicalCropBlock(level.getBlockState(pos.relative(d)).getBlock());
                if (rep != null && CropRegistry.isCropBlock(rep)) counts.put(rep, counts.getOrDefault(rep, 0) + 1);
            }
            tryPlantConsensus(counts, anchor, level, pos);
        }

        if (below != null && below.getBlock() == Blocks.SOUL_SAND && cur.isAir()) {
            Map<Block, Integer> counts = new HashMap<>();
            for (Direction d : HORIZ_DIRS) {
                Block b = level.getBlockState(pos.relative(d)).getBlock();
                if (b == Blocks.NETHER_WART) counts.put(b, counts.getOrDefault(b, 0) + 1);
            }
            tryPlantConsensus(counts, anchor, level, pos);
        }

        if (below != null && (below.getBlock() == Blocks.DIRT || below.getBlock() == Blocks.GRASS_BLOCK) && cur.isAir()) {
            int farmlandNeighbors = 0;
            for (Direction d : HORIZ_DIRS) {
                if (level.getBlockState(belowPos.relative(d)).getBlock() == Blocks.FARMLAND) farmlandNeighbors++;
            }
            if (farmlandNeighbors >= 1) {
                level.setBlock(belowPos, Blocks.FARMLAND.defaultBlockState(), 3);
                try { HarvestUtils.playTillingSound(level, belowPos); } catch (Throwable ignored) {}
                ItemStack before = ctx.getHoe().isEmpty() ? (anchor.hoe == null ? ItemStack.EMPTY : anchor.hoe.copy()) : ctx.getHoe().copy();
                try {
                    if (ctx.isSkipNextDamage()) { ctx.setSkipNextDamage(false); }
                    else { com.forgetmecrops.util.durability.DurabilityLogic.applyDamage(level, ctx.getHoe(), level.getRandom()); }
                } catch (Throwable ignored) {}
                if (ctx.getHoe().isEmpty()) HarvestUtils.handleBrokenHoe(ctx, before);
                Map<Block, Integer> counts = new HashMap<>();
                for (Direction d : HORIZ_DIRS) {
                    Block rep = CropRegistry.canonicalCropBlock(level.getBlockState(pos.relative(d)).getBlock());
                    if (rep != null && CropRegistry.isCropBlock(rep)) counts.put(rep, counts.getOrDefault(rep, 0) + 1);
                }
                tryPlantConsensus(counts, anchor, level, pos);
            }
        }
    }



    /**
     * BFS-discovers all connected farm positions from the center point within the scan range.
     * Seeds from a 3×3 area around the center (to handle frames positioned above non-crop blocks),
     * then expands to all reachable {@link #isFarmPosition} positions within rangeX/rangeZ.
     *
     * @param center the anchor frame position (BFS origin)
     * @param level  the level to check blocks in
     * @param rangeX east-west maximum extent
     * @param rangeZ north-south maximum extent
     * @return all BlockPos positions determined to be part of this farm
     */
    static List<BlockPos> bfsDiscoverFarm(BlockPos center, Level level, int rangeX, int rangeZ) {
        List<BlockPos> result = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        Deque<BlockPos> q = new ArrayDeque<>();

        // Seed the BFS from the frame center and its immediate neighbours.
        // Frames are often positioned above chests/blocks; starting from a 3x3
        // area around the frame increases the chance we begin on an actual
        // farm tile (air above farmland or crop) instead of a non-farm block
        // such as the frame's supporting block. This prevents the BFS from
        // immediately returning empty and falling back to the full-box scan.
        int seedsAdded = 0;
        for (int sx = -1; sx <= 1; sx++) {
            for (int sz = -1; sz <= 1; sz++) {
                BlockPos seed = center.offset(sx, 0, sz);
                long key = seed.asLong();
                if (!visited.contains(key)) {
                    visited.add(key);
                    q.add(seed);
                    seedsAdded++;
                }
            }
        }

        LogUtils.logDebug("[SCAN] BFS seeded from center {} with {} seeds (rangeX={},rangeZ={}).", center, seedsAdded, rangeX, rangeZ);

        while (!q.isEmpty()) {
            BlockPos cur = q.poll();

            int dx = Math.abs(cur.getX() - center.getX());
            int dz = Math.abs(cur.getZ() - center.getZ());
            if (dx > rangeX || dz > rangeZ) continue;

            if (!isFarmPosition(level, cur)) continue;

            result.add(cur);

            for (Direction d : HORIZ_DIRS) {
                BlockPos np = cur.relative(d);
                long key = np.asLong();
                if (visited.contains(key)) continue;
                visited.add(key);
                q.add(np);
            }
        }

        LogUtils.logDebug("[SCAN] BFS discovered {} connected nodes for center {}.", result.size(), center);

        return result;
    }

    /**
     * Decide whether a position should be considered part of the same farm as the given anchor.
     * This implements the "gap" detection from the original project: include empty farmland
     * tiles only when a clear neighboring-crop consensus exists, treat soul-sand/nether-wart
     * and melon/pumpkin clusters specially, and otherwise avoid crossing air/non-farm gaps.
     */
    static boolean isFarmPosition(Level level, BlockPos pos) {
        try {
            BlockState state = level.getBlockState(pos);
            Block b = state.getBlock();
            if (CropRegistry.isCropBlock(b) || state.is(Blocks.MELON) || state.is(Blocks.PUMPKIN) || state.is(Blocks.MELON_STEM) || state.is(Blocks.PUMPKIN_STEM)) return true;
            if (!state.isAir()) return false;

            Block below = level.getBlockState(pos.below()).getBlock();
            if (below == Blocks.FARMLAND) {
                // Empty prepared farmland should remain part of the farm so repair/replant scans can traverse it.
                return true;
            }
            if (below == Blocks.SOUL_SAND) {
                // Empty prepared soul sand should remain part of the farm so Nether Wart scans can traverse it.
                return true;
            }
            if (below == Blocks.DIRT || below == Blocks.GRASS_BLOCK) {
                // Include air-above-dirt/grass positions so they're traversed by BFS. The BFS connectivity
                // naturally limits inclusion to dirt/grass that is reachable from the farm center.
                // This allows repair/tilling of entire dirt patches connected to the farm, not just
                // those directly adjacent to farmland.
                return true;
            }

            int melonPumpkinNeighbors = 0;
            if (CropRegistry.isMelonPumpkinFarmBlock(level.getBlockState(pos.north()).getBlock())) melonPumpkinNeighbors++;
            if (CropRegistry.isMelonPumpkinFarmBlock(level.getBlockState(pos.south()).getBlock())) melonPumpkinNeighbors++;
            if (CropRegistry.isMelonPumpkinFarmBlock(level.getBlockState(pos.east()).getBlock())) melonPumpkinNeighbors++;
            if (CropRegistry.isMelonPumpkinFarmBlock(level.getBlockState(pos.west()).getBlock())) melonPumpkinNeighbors++;
            return melonPumpkinNeighbors >= 2;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Reads the {@code age} property of a crop block state without throwing.
     * Tries CropBlock.AGE first (clean API), then falls back to scanning block-state
     * properties by name. Returns -1 if the state has no age property.
     *
     * @param state the block state to read from
     * @return the age value, or -1 if not a crop or if the read fails
     */
    public static int getAgeSafe(BlockState state) {
        if (state == null) return -1;
        try {
            return state.getValue(CropBlock.AGE);
        } catch (Throwable ignored) {}
        try {
            for (Property<?> prop : state.getProperties()) {
                try {
                    if (prop instanceof IntegerProperty && "age".equalsIgnoreCase(prop.getName())) {
                        IntegerProperty ip = (IntegerProperty) prop;
                        return state.getValue(ip);
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        return -1;
    }

    /**
     * Sets the {@code age} property on a crop block state without throwing.
     * Tries CropBlock.AGE first, then scans by property name. Returns null if the
     * property doesn't exist or the set fails — callers must guard against null returns.
     *
     * @param state the block state to modify
     * @param age   the age value to set
     * @return the new block state with age set, or null if the operation failed
     */
    public static BlockState setAgeSafe(BlockState state, int age) {
        if (state == null) return null;
        try {
            return state.setValue(CropBlock.AGE, age);
        } catch (Throwable ignored) {}
        try {
            for (Property<?> prop : state.getProperties()) {
                try {
                    if (prop instanceof IntegerProperty && "age".equalsIgnoreCase(prop.getName())) {
                        IntegerProperty ip = (IntegerProperty) prop;
                        BlockState ns = state.setValue((Property<Integer>) ip, Integer.valueOf(age));
                        return ns;
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        return null;
    }

    /**
     * Returns the maximum {@code age} value for the given crop block state — i.e., the
     * age at which the crop is fully mature. Determined by inspecting the age property's
     * possible value set. Defaults to 3 for Nether Wart and sweet berries; 7 for everything else.
     *
     * @param state the block state of the crop
     * @return the maturity threshold age value
     */
    static int getMaturityThreshold(BlockState state) {
        if (state == null) return 7;
        try {
            for (Property<?> prop : state.getProperties()) {
                try {
                    if (prop instanceof IntegerProperty && "age".equalsIgnoreCase(prop.getName())) {
                        int max = 0;
                        for (Integer v : ((IntegerProperty) prop).getPossibleValues()) {
                            if (v != null && v.intValue() > max) max = v.intValue();
                        }
                        return max;
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        return (state.is(Blocks.NETHER_WART) || state.is(Blocks.SWEET_BERRY_BUSH)) ? 3 : 7;
    }

    private static final Map<String, List<FarmScanTask>> activeScans = new HashMap<>();

    /**
     * Schedule a farm scan task for the given anchor in the specified dimension.
     * Duplicate scans for the same anchor are ignored.
     *
     * @param dimId dimension identifier
     * @param anchor anchor to scan
     * @param level level where the anchor resides
     */
    public static void submitScan(String dimId, Anchor anchor, Level level) {
        try {
            List<FarmScanTask> list = activeScans.computeIfAbsent(dimId, k -> new ArrayList<>());
            for (FarmScanTask t : list) {
                if (t != null && t.anchor != null && t.anchor.framePos != null && anchor.framePos != null && t.anchor.framePos.equals(anchor.framePos)) {
                    LogUtils.logDebug("[SCAN] Scan already active for {}, skipping schedule.", anchor);
                    return;
                }
            }
            FarmScanTask task = new FarmScanTask(anchor, level, dimId);
            list.add(task);
            LogUtils.logDebug("[SCAN] Scheduled scan task for {} in {} (will span up to {} ticks)", anchor, dimId, Config.getMaxSpiralDurationTicks());
        } catch (Throwable t) {
            LogUtils.logWarn("[SCAN] Failed to schedule scan task for " + anchor, t);
        }
    }

    /**
     * Advance all scheduled scan tasks for a dimension; removes finished tasks.
     *
     * @param dimId dimension identifier
     * @param level level context for scans
     */
    public static void tickScans(String dimId, Level level) {
        List<FarmScanTask> list = activeScans.get(dimId);
        if (list == null || list.isEmpty()) return;

        Iterator<FarmScanTask> it = list.iterator();
        while (it.hasNext()) {
            FarmScanTask task = it.next();
            try {
                boolean finished = task.tick();
                    if (finished) {
                    it.remove();
                    LogUtils.logDebug("[SCAN] Finished scan task for {} in {}", task.anchor, dimId);
                }
            } catch (Throwable t) {
                LogUtils.logWarn("[SCAN] Scan task failed for " + task.anchor, t);
                it.remove();
            }
        }

        if (list.isEmpty()) activeScans.remove(dimId);
    }
    /**
     * Gets the current rotation (0–7) of the item frame at the given position.
     * Tries the entity's accessor method first, falls back to field reflection, then FIF block-entity.
     * Returns 0 if nothing works. Always masked to 3 bits.
     *
     * @param level the level to search for the frame entity
     * @param pos   the frame position
     * @return the frame rotation (0–7), or 0 if unresolvable
     */
    static int getFrameRotation(Level level, BlockPos pos) {
        try {
            List<ItemFrame> frames = level.getEntitiesOfClass(ItemFrame.class, new AABB(pos));
            for (ItemFrame f : frames) {
                if (f.blockPosition().equals(pos)) {
                    for (Method m : f.getClass().getMethods()) {
                        String name = m.getName().toLowerCase(Locale.ROOT);
                        if ((name.contains("get") || name.contains("getitem")) && name.contains("rotation") && m.getParameterCount() == 0) {
                            Object r = m.invoke(f);
                            if (r instanceof Number) return ((Number) r).intValue() & 7;
                        }
                    }
                    try {
                        Field fld = f.getClass().getDeclaredField("rotation");
                        fld.setAccessible(true);
                        Object v = fld.get(f);
                        if (v instanceof Number) return ((Number) v).intValue() & 7;
                    } catch (Throwable ignored) {}
                    return 0;
                }
            }

            BlockEntity be = level.getBlockEntity(pos);
            if (be != null) {
                try { return FIF.getRotation(be); } catch (Throwable ignored) {}
            }
        } catch (Throwable t) {
            LogUtils.logDebug("[ROT] getFrameRotation failed at " + pos, t);
        }
        return 0;
    }

    /**
     * Reads the hoe ItemStack currently held by the item frame (or FIF block-entity) at pos.
     * Tries vanilla ItemFrame entities first; falls back to FIF's extractHeldItem.
     * Returns {@link ItemStack#EMPTY} if no hoe is found or if anything goes wrong.
     *
     * @param level the level to search for the frame
     * @param pos   the frame position
     * @return a copy of the held hoe stack, or EMPTY
     */
    public static ItemStack readHoeFromFrame(Level level, BlockPos pos) {
        try {
            List<ItemFrame> frames = level.getEntitiesOfClass(ItemFrame.class,
                    new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1), e -> true);
            if (!frames.isEmpty()) {
                ItemFrame frame = frames.get(0);
                ItemStack s = frame.getItem();
                if (s != null && !s.isEmpty() && s.getItem() instanceof HoeItem) return s.copy();
            }
        } catch (Throwable ignored) {}

        try {
            BlockEntity be = level.getBlockEntity(pos);
            if (be != null) {
                ItemStack s = FIF.extractHeldItem(be);
                if (s != null && !s.isEmpty() && s.getItem() instanceof HoeItem) return s.copy();
            }
        } catch (Throwable ignored) {}

        return ItemStack.EMPTY;
    }

    /**
     * Sets the frame rotation at pos to newRotation (0–7) via the rotation-scheduling system.
     * Delegates to {@link #setFrameRotation(Level, BlockPos, int, boolean)} with bypassCooldown=false.
     */
    static void setFrameRotation(Level level, BlockPos pos, int newRotation) {
        setFrameRotation(level, pos, newRotation, false);
    }

    /**
     * Sets the frame rotation at pos, optionally bypassing the rotation-rate cooldown.
     * Normal path: checks cooldown via FrameRegistry.tryRotation, schedules via scheduleRotation.
     * Bypass path: directly applies the rotation immediately (used for animation step completions).
     * Both paths no-op if the frame is already at the requested rotation.
     *
     * @param level          the level containing the frame
     * @param pos            the frame position
     * @param newRotation    the target rotation (0–7)
     * @param bypassCooldown if true, skip the cooldown check and apply immediately
     */
    static void setFrameRotation(Level level, BlockPos pos, int newRotation, boolean bypassCooldown) {
        long gameTime = -1L;
        try { gameTime = level != null ? level.getGameTime() : -1L; } catch (Throwable ignored) {}

        String dimId = "";
        try { dimId = level != null ? level.dimension().identifier().toString() : ""; } catch (Throwable ignored) {}

        if (bypassCooldown) {
            try { FrameRegistry.tryRotation(dimId, pos, gameTime); } catch (Throwable ignored) {}
            try {
                try { LogUtils.logDebug("[ROT] Direct apply (bypass) for {} -> {} (gametime={})", pos, newRotation & 7, gameTime); } catch (Throwable ignored) {}
                applyScheduledRotation(level, pos, newRotation);
            } catch (Throwable t) {
                LogUtils.logDebug("[ROT] applyScheduledRotation failed at " + pos, t);
            }
            return;
        }

            try {
            if (!FrameRegistry.tryRotation(dimId, pos, gameTime)) {
                if (Config.isDebugLogging()) try { LogUtils.logDebug("[ROT] Skipped rotation for {} due to cooldown (gametime={})", pos, gameTime); } catch (Throwable ignored) {}
                return;
            }
        } catch (Throwable ignored) {}

            try {
            int cur = getFrameRotation(level, pos) & 7;
            if (cur == (newRotation & 7)) {
                if (Config.isDebugLogging()) try { LogUtils.logDebug("[ROT] No-op rotation for {} (already {})", pos, cur); } catch (Throwable ignored) {}
                return;
            }
        } catch (Throwable ignored) {}

        try {
            try { LogUtils.logDebug("[ROT] Request scheduleRotation for {} -> {} (gametime={})", pos, newRotation & 7, gameTime); } catch (Throwable ignored) {}
            FrameRegistry.scheduleRotation(dimId, pos, newRotation, gameTime);
        } catch (Throwable t) {
            LogUtils.logDebug("[ROT] Failed to schedule rotation for " + pos, t);
        }
    }

    /**
     * Directly applies a pre-scheduled rotation to the item frame entity (or FIF block-entity) at pos.
     * Tries all known rotation setter methods/fields via reflection in a best-effort loop.
     * If the entity is no longer there or the reflection fails, nothing happens and no exception escapes.
     *
     * @param level       the level containing the frame
     * @param pos         the frame position
     * @param newRotation the rotation to apply (0–7, masked to 3 bits internally)
     */
    static void applyScheduledRotation(Level level, BlockPos pos, int newRotation) {
        if (level == null || pos == null) return;
        try {
            long gameTime = -1L;
            try {
                gameTime = level.getGameTime();
            } catch (Exception e) {
                LogUtils.logTrace("[ROT] Could not read game time while applying scheduled rotation", e);
            }
            try { LogUtils.logDebug("[ROT] applyScheduledRotation pos={} newRot={} mode={} gametime={}", pos, newRotation, Config.getRotationMode(), gameTime); } catch (Throwable ignored) {}

            List<ItemFrame> frames = level.getEntitiesOfClass(ItemFrame.class, new AABB(pos));
            for (ItemFrame f : frames) {
                if (f.blockPosition().equals(pos)) {
                    for (Method m : f.getClass().getMethods()) {
                        String name = m.getName().toLowerCase(Locale.ROOT);
                        if (name.contains("set") && name.contains("rotation") && m.getParameterCount() == 1) {
                            Class<?> p = m.getParameterTypes()[0];
                            try {
                                if (p == int.class || p == Integer.class) {
                                    m.invoke(f, newRotation);
                                    if (Config.isDebugLogging()) try { LogUtils.logDebug("[ROT] Applied rotation on ItemFrame entity at {} => {} (method {})", pos, newRotation, m.getName()); } catch (Throwable ignored) {}
                                    int got = -999;
                                    try {
                                        got = f.getRotation();
                                    } catch (Throwable ex) {
                                        try {
                                            for (Method gm : f.getClass().getMethods()) {
                                                String nm = gm.getName().toLowerCase(Locale.ROOT);
                                                if ((nm.contains("get") || nm.contains("getitem")) && nm.contains("rotation") && gm.getParameterCount() == 0) {
                                                    Object r = gm.invoke(f);
                                                    if (r instanceof Number) { got = ((Number) r).intValue() & 7; break; }
                                                }
                                            }
                                        } catch (Throwable ex2) {}
                                    }
                                    if (Config.isDebugLogging()) {
                                        try { LogUtils.logDebug("[ROT] ItemFrame readback at {} => {}", pos, got); } catch (Throwable ex3) {}
                                    }
                                    return;
                                }
                                if (p == byte.class || p == Byte.class) {
                                    m.invoke(f, (byte) newRotation);
                                    if (Config.isDebugLogging()) try { LogUtils.logDebug("[ROT] Applied rotation on ItemFrame entity at {} => {} (method {})", pos, newRotation, m.getName()); } catch (Throwable ignored) {}
                                    int got = -999;
                                    try {
                                        got = f.getRotation();
                                    } catch (Throwable ex) {
                                        try {
                                            for (Method gm : f.getClass().getMethods()) {
                                                String nm = gm.getName().toLowerCase(Locale.ROOT);
                                                if ((nm.contains("get") || nm.contains("getitem")) && nm.contains("rotation") && gm.getParameterCount() == 0) {
                                                    Object r = gm.invoke(f);
                                                    if (r instanceof Number) { got = ((Number) r).intValue() & 7; break; }
                                                }
                                            }
                                        } catch (Throwable ex2) {}
                                    }
                                    if (Config.isDebugLogging()) {
                                        try { LogUtils.logDebug("[ROT] ItemFrame readback at {} => {}", pos, got); } catch (Throwable ex3) {}
                                    }
                                    return;
                                }
                            } catch (Throwable t) {
                                if (Config.isDebugLogging()) try { LogUtils.logDebug("[ROT] Failed to invoke setter " + m.getName() + " on ItemFrame " + pos, t); } catch (Throwable ignored) {}
                            }
                        }
                    }
                    try {
                        Field fld = f.getClass().getDeclaredField("rotation");
                        fld.setAccessible(true);
                        fld.setInt(f, newRotation & 7);
                        if (Config.isDebugLogging()) try { LogUtils.logDebug("[ROT] Applied rotation via field on ItemFrame at {} => {}", pos, newRotation & 7); } catch (Throwable ignored) {}
                        int got = -999;
                        try {
                            got = f.getRotation();
                        } catch (Throwable ex) {
                            try {
                                for (Method gm : f.getClass().getMethods()) {
                                    String nm = gm.getName().toLowerCase(Locale.ROOT);
                                    if ((nm.contains("get") || nm.contains("getitem")) && nm.contains("rotation") && gm.getParameterCount() == 0) {
                                        Object r = gm.invoke(f);
                                        if (r instanceof Number) { got = ((Number) r).intValue() & 7; break; }
                                    }
                                }
                            } catch (Throwable ex2) {}
                        }
                        if (Config.isDebugLogging()) {
                            try { LogUtils.logDebug("[ROT] ItemFrame readback at {} => {}", pos, got); } catch (Throwable ex3) {}
                        }
                        return;
                    } catch (Throwable ignoredField) {}
                }
            }

            BlockEntity be = level.getBlockEntity(pos);
            if (be != null) {
                try {
                    if (Config.isDebugLogging()) {
                        try {
                            BlockState beforeState = level.getBlockState(pos);
                            LogUtils.logDebug("[ROT-DIAG] Pre FIF.setRotation pos={} requested={} beClass={} state={}", pos, newRotation & 7, be.getClass().getName(), beforeState);
                        } catch (Throwable ignored) {}
                    }
                    boolean applied = FIF.setRotation(be, newRotation);
                    if (!applied) {
                        if (Config.isDebugLogging()) {
                            try {
                                BlockState afterState = level.getBlockState(pos);
                                int rb = FIF.getRotation(be);
                                LogUtils.logDebug("[ROT] FastItemFrames rotation write did not verify at {} => {} (readBack={} stateAfter={} beClass={})", pos, newRotation & 7, rb, afterState, be.getClass().getName());
                            } catch (Throwable ignored) {
                                try { LogUtils.logDebug("[ROT] FastItemFrames rotation write did not verify at {} => {}", pos, newRotation & 7); } catch (Throwable ignored2) {}
                            }
                        }
                        return;
                    }
                    try {
                        int rb = FIF.getRotation(be);
                        if (Config.isDebugLogging()) {
                            try {
                                BlockState afterState = level.getBlockState(pos);
                                LogUtils.logDebug("[ROT] Applied rotation on FIF block-entity at {} => {} (readBack={} stateAfter={} beClass={})", pos, newRotation & 7, rb, afterState, be.getClass().getName());
                            } catch (Throwable logEx1) {
                                try { LogUtils.logDebug("[ROT] Applied rotation on FIF block-entity at {} => {} (readBack={})", pos, newRotation & 7, rb); } catch (Throwable ignored) {}
                            }
                        }
                    } catch (Throwable exGet) {
                        if (Config.isDebugLogging()) try { LogUtils.logDebug("[ROT] Applied rotation on FIF block-entity at {} => {} (readBack=?)", pos, newRotation & 7); } catch (Throwable logEx2) {}
                    }
                    return;
                } catch (Throwable t) {
                    if (Config.isDebugLogging()) try { LogUtils.logDebug("[ROT] Failed to apply rotation on FIF block-entity at " + pos, t); } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable t) {
            LogUtils.logDebug("[ROT] applyScheduledRotation failed at " + pos, t);
        }
    }

    /**
     * Clear any in-progress/queued scan tasks. Used when worlds unload to avoid
     * carrying stale FarmScanTask objects across server restarts.
     */
    public static synchronized void clearAllScans() {
        try {
            activeScans.clear();
            LogUtils.logDebug("[SCAN] Cleared all active scan tasks.");
        } catch (Throwable t) {
            LogUtils.logWarn("[SCAN] Failed to clear active scans", t);
        }
    }
}


