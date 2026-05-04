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
import com.forgetmecrops.util.durability.DurabilityLogic;
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
 * FrameScanner: The intrepid explorer of your blocky world!
 */
public class FrameScanner {
    /** Maximum frames processed per run. */
    public static final int MAX_FRAMES_PER_RUN = 24;

    /** Default constructor. */
    public FrameScanner() {}

    /**
     * Anchor: represents a registered frame anchor with its chest and stored hoe.
     */
    public static class Anchor {
        /** The associated chest/container (may be null). */
        public final Container chest;
        /** The position of the item frame anchor. */
        public final BlockPos framePos;
        /** The stored hoe ItemStack for this anchor. */
        public final ItemStack hoe;

        /**
         * Create a new Anchor.
         *
         * @param chest associated container (may be null)
         * @param framePos position of the item frame
         * @param hoe stored hoe ItemStack
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
                if (currentHoe == null || currentHoe.isEmpty()) {
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

        if (currentHoe == null || currentHoe.isEmpty()) {
            LogUtils.logDebug("[SCAN] No hoe available for anchor {}; aborting scan.", anchor);
            return false;
        }

        HarvestContext ctx = new HarvestContext(anchor, level, currentHoe, anchor.chest, null);

        List<SpiralStep> spiral = generateSpiral(center, rangeX, rangeZ);
        int spiralSteps = spiral.size();
        boolean anyHarvested = false;

        Direction lastDir = null;
        int lastComputedRot = getFrameRotation(level, center) & 7;
        for (int i = 0; i < spiralSteps; i++) {
            SpiralStep step = spiral.get(i);
            BlockPos pos = step.pos;
            Direction dir = step.dir;
            BlockState state = level.getBlockState(pos);
            blocksScanned++;

            switch (Config.getRotationMode()) {
                case FOLLOW_HARVEST_SPIRAL -> {
                    if (lastDir == null || dir != lastDir) {
                        setFrameRotation(level, center, dirToRotation(dir));
                        lastDir = dir;
                    }
                }
                case FULL_ROTATION_PER_HARVEST -> {
                    int rot = (int) Math.floor((double) i * 8.0 / spiralSteps) & 7;
                    if (rot != lastComputedRot) {
                        setFrameRotation(level, center, rot);
                        lastComputedRot = rot;
                    }
                }
                case STEP_PER_HARVEST -> {
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
                    Direction[] dirs = new Direction[]{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
                    for (Direction d : dirs) {
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

        if (anyHarvested && Config.getRotationMode() == RotationMode.STEP_PER_HARVEST) {
            int newRot = (getFrameRotation(level, center) + 1) & 7;
            setFrameRotation(level, center, newRot);
        }

        LogUtils.logDebug("[SCAN] Scan complete. Blocks scanned: {}, crops found: {}.", blocksScanned, cropsFound);
        return cropsFound > 0;
    }

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

    static Direction getSpiralDirection(int dx, int dz) {
        if (dx == 1 && dz == 0) return Direction.EAST;
        if (dx == -1 && dz == 0) return Direction.WEST;
        if (dx == 0 && dz == 1) return Direction.SOUTH;
        if (dx == 0 && dz == -1) return Direction.NORTH;
        return Direction.NORTH;
    }

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
     * Pick the dominant block from a neighbor-count map, consume one seed from the chest, and place it at pos.
     * Returns true if a block was placed.
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

    static void tryAutoPlantAndTill(Anchor anchor, HarvestContext ctx, BlockPos pos, Level level) {
        BlockState cur = level.getBlockState(pos);
        BlockPos belowPos = pos.below();
        BlockState below = level.getBlockState(belowPos);
        Direction[] dirs = new Direction[]{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

        if (below != null && below.getBlock() == Blocks.FARMLAND && cur.isAir()) {
            Map<Block, Integer> counts = new HashMap<>();
            for (Direction d : dirs) {
                Block rep = CropRegistry.canonicalCropBlock(level.getBlockState(pos.relative(d)).getBlock());
                if (rep != null && CropRegistry.isCropBlock(rep)) counts.merge(rep, 1, Integer::sum);
            }
            tryPlantConsensus(counts, anchor, level, pos);
        }

        if (below != null && below.getBlock() == Blocks.SOUL_SAND && cur.isAir()) {
            Map<Block, Integer> counts = new HashMap<>();
            for (Direction d : dirs) {
                Block b = level.getBlockState(pos.relative(d)).getBlock();
                if (b == Blocks.NETHER_WART) counts.merge(b, 1, Integer::sum);
            }
            tryPlantConsensus(counts, anchor, level, pos);
        }

        if (below != null && (below.getBlock() == Blocks.DIRT || below.getBlock() == Blocks.GRASS_BLOCK) && cur.isAir()) {
            int farmlandNeighbors = 0;
            for (Direction d : dirs) {
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
                for (Direction d : dirs) {
                    Block rep = CropRegistry.canonicalCropBlock(level.getBlockState(pos.relative(d)).getBlock());
                    if (rep != null && CropRegistry.isCropBlock(rep)) counts.merge(rep, 1, Integer::sum);
                }
                tryPlantConsensus(counts, anchor, level, pos);
            }
        }
    }



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

            for (Direction d : new Direction[]{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}) {
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

    static void setFrameRotation(Level level, BlockPos pos, int newRotation) {
        setFrameRotation(level, pos, newRotation, false);
    }

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

    @SuppressWarnings("null")
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
                    FIF.setRotation(be, newRotation);
                    try { level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), 3); } catch (Throwable ignored) {}
                    try {
                        int rb = FIF.getRotation(be);
                        if (Config.isDebugLogging()) try { LogUtils.logDebug("[ROT] Applied rotation on FIF block-entity at {} => {} (readBack={})", pos, newRotation & 7, rb); } catch (Throwable logEx1) {}
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


