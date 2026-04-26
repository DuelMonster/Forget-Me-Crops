package com.fastharvester.frame;
import com.fastharvester.Constants;
import com.fastharvester.Config;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

import com.fastharvester.platform.adapter.FastItemFrameAdapterImpl;
import com.fastharvester.util.chest.ChestUtils;
import com.fastharvester.HarvestUtils;
import com.fastharvester.HarvestContext;
import com.fastharvester.util.durability.DurabilityLogic;
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
        Constants.logInfo("[SCAN] Starting farm scan from anchor: {}", anchor);
        if (anchor == null || anchor.chest == null || level == null) {
            Constants.logWarn("[SCAN] Anchor or environment missing, aborting scan.");
            return false;
        }

        int blocksScanned = 0;
        int cropsFound = 0;
        int range = Math.max(1, Config.scanRange);
        BlockPos center = anchor.framePos;
        String dimId = level.dimension().identifier().toString();

        ItemStack currentHoe = anchor.hoe == null ? ItemStack.EMPTY : anchor.hoe.copy();
        try {
            ItemStack frameHoe = readHoeFromFrame(level, center);
            if (frameHoe != null && !frameHoe.isEmpty() && frameHoe.getItem() instanceof HoeItem) {
                currentHoe = frameHoe.copy();
                try { FrameRegistry.updateHoe(dimId, center, currentHoe.copy()); } catch (Throwable ignored) {}
            } else if (currentHoe == null || currentHoe.isEmpty()) {
                ItemStack replacement = ChestUtils.takeFirstHoe(anchor.chest);
                if (replacement != null && !replacement.isEmpty()) {
                    try { com.fastharvester.platform.Services.PLATFORM.updateFrameItem(level, center, replacement.copy()); } catch (Throwable ignored) {}
                    currentHoe = replacement.copy();
                    try { FrameRegistry.updateHoe(dimId, center, currentHoe.copy()); } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}

        if (currentHoe == null || currentHoe.isEmpty()) {
            Constants.logInfo("[SCAN] No hoe available for anchor {}; aborting scan.", anchor);
            return false;
        }

        HarvestContext ctx = new HarvestContext(anchor, level, currentHoe, anchor.chest, null);

        List<SpiralStep> spiral = generateSpiral(center, range);
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

            switch (Config.rotationMode) {
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

            boolean harvested = false;
            try {
                Block block = state.getBlock();

                if (state.is(Blocks.MELON) || state.is(Blocks.PUMPKIN)) {
                    HarvestUtils.harvestCrop(ctx, pos, state, s -> true, s -> null);
                    harvested = ctx.harvestedCount > 0;
                    cropsFound = Math.max(cropsFound, ctx.harvestedCount);
                } else if (state.is(Blocks.MELON_STEM) || state.is(Blocks.PUMPKIN_STEM)) {
                    Direction[] dirs = new Direction[]{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
                    for (Direction d : dirs) {
                        BlockPos npos = pos.relative(d);
                        BlockState ns = level.getBlockState(npos);
                        if ((ns.is(Blocks.MELON) && state.is(Blocks.MELON_STEM)) || (ns.is(Blocks.PUMPKIN) && state.is(Blocks.PUMPKIN_STEM))) {
                            HarvestUtils.harvestCrop(ctx, npos, ns, s -> true, s -> null);
                            harvested = ctx.harvestedCount > 0;
                            cropsFound = Math.max(cropsFound, ctx.harvestedCount);
                            break;
                        }
                    }
                } else {
                    boolean isCrop = block instanceof CropBlock || state.is(Blocks.NETHER_WART) || state.is(Blocks.SWEET_BERRY_BUSH);
                    if (isCrop) {
                        int threshold = (state.is(Blocks.NETHER_WART) || state.is(Blocks.SWEET_BERRY_BUSH)) ? 3 : 7;
                        boolean mature = false;
                        try {
                            int age = state.getValue(CropBlock.AGE);
                            mature = age >= threshold;
                        } catch (Throwable t) {
                            mature = false;
                        }
                        if (mature) {
                            HarvestUtils.harvestCrop(ctx, pos, state,
                                    s -> {
                                        try { int a = s.getValue(CropBlock.AGE); if (s.is(Blocks.NETHER_WART) || s.is(Blocks.SWEET_BERRY_BUSH)) return a >= 3; return a >= 7; } catch (Throwable tt) { return false; }
                                    },
                                    s -> {
                                        try {
                                            if (s.is(Blocks.SWEET_BERRY_BUSH)) return s.setValue(CropBlock.AGE, 1);
                                            return s.setValue(CropBlock.AGE, 0);
                                        } catch (Throwable tt) { return null; }
                                    });
                            harvested = ctx.harvestedCount > 0;
                            cropsFound = Math.max(cropsFound, ctx.harvestedCount);
                        }
                    }
                }
            } catch (Throwable t) {
                Constants.logDebug("[SCAN] Exception while scanning " + pos, t);
            }

            if (harvested) anyHarvested = true;

            if (ctx.chestFull) {
                try {
                    FrameRegistry.setCooldown(dimId, center, Config.chestFullCooldownTicks);
                } catch (Throwable ignored) {}
                ctx.logSummary();
                return cropsFound > 0;
            }

            tryAutoPlantAndTill(anchor, ctx, pos, level);

            if (ctx.chestFull) {
                try {
                    FrameRegistry.setCooldown(dimId, center, Config.chestFullCooldownTicks);
                } catch (Throwable ignored) {}
                ctx.logSummary();
                return cropsFound > 0;
            }
        }

        if (anyHarvested && Config.rotationMode == com.fastharvester.enums.RotationMode.STEP_PER_HARVEST) {
            int newRot = (getFrameRotation(level, center) + 1) & 7;
            setFrameRotation(level, center, newRot);
        }

        Constants.logInfo("[SCAN] Scan complete. Blocks scanned: {}, crops found: {}.", blocksScanned, cropsFound);
        return cropsFound > 0;
    }

    private static class SpiralStep {
        public final BlockPos pos;
        public final Direction dir;
        public SpiralStep(BlockPos pos, Direction dir) {
            this.pos = pos;
            this.dir = dir;
        }
    }

    private static List<SpiralStep> generateSpiral(BlockPos center, int range) {
        List<SpiralStep> spiral = new ArrayList<>();
        int x = 0, z = 0, dx = 0, dz = -1;
        int max = (range * 2 + 1) * (range * 2 + 1);
        for (int i = 0; i < max; i++) {
            int px = center.getX() + x;
            int pz = center.getZ() + z;
            int dist = Math.max(Math.abs(x), Math.abs(z));
            if (dist <= range) {
                Direction dir = getSpiralDirection(dx, dz);
                spiral.add(new SpiralStep(new BlockPos(px, center.getY(), pz), dir));
            }
            if (x == z || (x < 0 && x == -z) || (x > 0 && x == 1 - z)) {
                int temp = dx;
                dx = -dz;
                dz = temp;
            }
            x += dx;
            z += dz;
        }
        return spiral;
    }

    private static Direction getSpiralDirection(int dx, int dz) {
        if (dx == 1 && dz == 0) return Direction.EAST;
        if (dx == -1 && dz == 0) return Direction.WEST;
        if (dx == 0 && dz == 1) return Direction.SOUTH;
        if (dx == 0 && dz == -1) return Direction.NORTH;
        return Direction.NORTH;
    }

    private static int dirToRotation(Direction dir) {
        return switch (dir) {
            case NORTH -> 0;
            case EAST -> 2;
            case SOUTH -> 4;
            case WEST -> 6;
            default -> 0;
        };
    }

    private static void tryAutoPlantAndTill(Anchor anchor, HarvestContext ctx, BlockPos pos, Level level) {
        BlockState cur = level.getBlockState(pos);
        BlockPos belowPos = pos.below();
        BlockState below = level.getBlockState(belowPos);
        Direction[] dirs = new Direction[]{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

        if (below != null && below.getBlock() == Blocks.FARMLAND && cur.isAir()) {
            Map<Block, Integer> counts = new HashMap<>();
            for (Direction d : dirs) {
                BlockPos npos = pos.relative(d);
                BlockState ns = level.getBlockState(npos);
                Block b = ns.getBlock();
                if (b == Blocks.WHEAT || b == Blocks.BEETROOTS || b == Blocks.CARROTS || b == Blocks.POTATOES || b == Blocks.MELON_STEM || b == Blocks.PUMPKIN_STEM) {
                    counts.merge(b, 1, Integer::sum);
                }
            }
            if (!counts.isEmpty()) {
                Block chosen = counts.entrySet().stream().max(Comparator.comparingInt(Map.Entry::getValue)).get().getKey();
                Item seed = seedForBlock(chosen);
                if (seed != null && ChestUtils.removeOne(anchor.chest, seed)) {
                    BlockState plantState = chosen.defaultBlockState();
                    try { if (plantState.getBlock() instanceof CropBlock) plantState = plantState.setValue(CropBlock.AGE, 0); } catch (Throwable t) {}
                    level.setBlock(pos, plantState, 3);
                }
            }
        }

        if (below != null && below.getBlock() == Blocks.SOUL_SAND && cur.isAir()) {
            Map<Block, Integer> counts = new HashMap<>();
            for (Direction d : dirs) {
                BlockPos npos = pos.relative(d);
                BlockState ns = level.getBlockState(npos);
                Block b = ns.getBlock();
                if (b == Blocks.NETHER_WART) {
                    counts.merge(b, 1, Integer::sum);
                }
            }
            if (!counts.isEmpty()) {
                Block chosen = counts.entrySet().stream().max(Comparator.comparingInt(Map.Entry::getValue)).get().getKey();
                Item seed = seedForBlock(chosen);
                if (seed != null && ChestUtils.removeOne(anchor.chest, seed)) {
                    BlockState plantState = chosen.defaultBlockState();
                    try { if (plantState.getBlock() instanceof CropBlock) plantState = plantState.setValue(CropBlock.AGE, 0); } catch (Throwable t) {}
                    level.setBlock(pos, plantState, 3);
                }
            }
        }

        if (below != null && (below.getBlock() == Blocks.DIRT || below.getBlock() == Blocks.GRASS_BLOCK) && cur.isAir()) {
            int farmlandNeighbors = 0;
            for (Direction d : dirs) {
                BlockState ns = level.getBlockState(belowPos.relative(d));
                if (ns != null && ns.getBlock() == Blocks.FARMLAND) farmlandNeighbors++;
            }
            if (farmlandNeighbors >= 1) {
                boolean nearMelonPumpkin = false;
                for (Direction d : dirs) {
                    BlockState ns = level.getBlockState(pos.relative(d));
                    if (ns.is(Blocks.MELON) || ns.is(Blocks.PUMPKIN) || ns.is(Blocks.MELON_STEM) || ns.is(Blocks.PUMPKIN_STEM)) { nearMelonPumpkin = true; break; }
                }
                if (!nearMelonPumpkin) {
                    BlockState farmland = Blocks.FARMLAND.defaultBlockState();
                    level.setBlock(belowPos, farmland, 3);
                    ItemStack before = anchor.hoe.copy();
                    com.fastharvester.util.durability.DurabilityLogic.applyDamage(level, anchor.hoe, level.getRandom());
                    if (anchor.hoe.isEmpty()) {
                        HarvestUtils.handleBrokenHoe(ctx, before);
                    }
                    Map<Block, Integer> counts2 = new HashMap<>();
                    for (Direction d : dirs) {
                        BlockPos npos = pos.relative(d);
                        BlockState ns = level.getBlockState(npos);
                        Block b = ns.getBlock();
                        if (b == Blocks.WHEAT || b == Blocks.BEETROOTS || b == Blocks.CARROTS || b == Blocks.POTATOES || b == Blocks.MELON_STEM || b == Blocks.PUMPKIN_STEM) {
                            counts2.merge(b, 1, Integer::sum);
                        }
                    }
                    if (!counts2.isEmpty()) {
                        Block chosen2 = counts2.entrySet().stream().max(Comparator.comparingInt(Map.Entry::getValue)).get().getKey();
                        Item seed2 = seedForBlock(chosen2);
                        if (seed2 != null && ChestUtils.removeOne(anchor.chest, seed2)) {
                            BlockState plantState2 = chosen2.defaultBlockState();
                            try { if (plantState2.getBlock() instanceof CropBlock) plantState2 = plantState2.setValue(CropBlock.AGE, 0); } catch (Throwable t) {}
                            level.setBlock(pos, plantState2, 3);
                        }
                    }
                }
            }
        }
    }

    private static Item seedForBlock(Block b) {
        if (b == Blocks.WHEAT) return Items.WHEAT_SEEDS;
        if (b == Blocks.BEETROOTS) return Items.BEETROOT_SEEDS;
        if (b == Blocks.CARROTS) return Items.CARROT;
        if (b == Blocks.POTATOES) return Items.POTATO;
        if (b == Blocks.MELON_STEM) return Items.MELON_SEEDS;
        if (b == Blocks.PUMPKIN_STEM) return Items.PUMPKIN_SEEDS;
        try {
            String cls = b.getClass().getName().toLowerCase();
            if (cls.contains("torchflower")) return b.asItem();
        } catch (Throwable ignored) {}
        if (b == Blocks.NETHER_WART) return Items.NETHER_WART;
        return null;
    }

    private static List<BlockPos> bfsDiscoverFarm(BlockPos center, Level level, int range) {
        List<BlockPos> result = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();
        Deque<BlockPos> q = new ArrayDeque<>();

        for (int dx = -range; dx <= range; dx++) {
            for (int dz = -range; dz <= range; dz++) {
                BlockPos p = center.offset(dx, 0, dz);
                BlockState s = level.getBlockState(p);
                Block b = s.getBlock();
                boolean isSeed = s.is(Blocks.MELON) || s.is(Blocks.PUMPKIN) || s.is(Blocks.MELON_STEM) || s.is(Blocks.PUMPKIN_STEM)
                        || b instanceof CropBlock || s.is(Blocks.NETHER_WART) || s.is(Blocks.SWEET_BERRY_BUSH);
                if (isSeed) {
                    visited.add(p);
                    q.add(p);
                }
            }
        }

        Constants.logDebug("[SCAN] BFS seeded {} nodes around {} (range {}).", visited.size(), center, range);

        while (!q.isEmpty()) {
            BlockPos cur = q.poll();
            result.add(cur);

            for (Direction d : new Direction[]{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}) {
                BlockPos np = cur.relative(d);
                if (visited.contains(np)) continue;
                int ring = Math.max(Math.abs(np.getX() - center.getX()), Math.abs(np.getZ() - center.getZ()));
                if (ring > range) continue;
                BlockState ns = level.getBlockState(np);
                Block nb = ns.getBlock();
                boolean traverse = nb instanceof CropBlock || ns.is(Blocks.FARMLAND) || ns.is(Blocks.DIRT) || ns.is(Blocks.GRASS_BLOCK)
                        || ns.is(Blocks.MELON) || ns.is(Blocks.PUMPKIN) || ns.is(Blocks.MELON_STEM) || ns.is(Blocks.PUMPKIN_STEM)
                        || ns.is(Blocks.SWEET_BERRY_BUSH) || ns.is(Blocks.NETHER_WART) || ns.is(Blocks.SOUL_SAND);
                if (traverse) {
                    visited.add(np);
                    q.add(np);
                }
            }
        }

        Constants.logDebug("[SCAN] BFS discovered {} connected nodes for center {}.", result.size(), center);

        return result;
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
                    Constants.logDebug("[SCAN] Scan already active for {}, skipping schedule.", anchor);
                    return;
                }
            }
            FarmScanTask task = new FarmScanTask(anchor, level, dimId);
            list.add(task);
            Constants.logInfo("[SCAN] Scheduled scan task for {} in {} (will span up to {} ticks)", anchor, dimId, Config.maxSpiralDurationTicks);
        } catch (Throwable t) {
            Constants.logWarn("[SCAN] Failed to schedule scan task for " + anchor, t);
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
                    Constants.logInfo("[SCAN] Finished scan task for {} in {}", task.anchor, dimId);
                }
            } catch (Throwable t) {
                Constants.logWarn("[SCAN] Scan task failed for " + task.anchor, t);
                it.remove();
            }
        }

        if (list.isEmpty()) activeScans.remove(dimId);
    }

    private static class FarmScanTask {
        final Anchor anchor;
        final Level level;
        final BlockPos center;
        final HarvestContext ctx;
        final String dimId;

        final Map<Integer, List<BlockPos>> ringMap = new HashMap<>();
        final List<SpiralStep> spiralPositions = new ArrayList<>();
        final int computedMaxRing;
        final int totalPositions;
        int currentIndex = 0;
        final int positionsPerTick;
        boolean anyHarvested = false;
        int lastHarvestedRing = -1;
        Direction lastDirection = null;
        int animationStepsRemaining = 0;
        final Map<Integer, List<Integer>> ringFullIndices = new HashMap<>();
        final Map<Integer, Integer> indexToPosInRing = new HashMap<>();
        int lastComputedRotation = -1;
        int tickCounter = 0;
        boolean neighborPassDone = false;
        int numberOfTicksNeeded = 0;
        boolean fullAnimationScheduled = false;
        int animationInterval = 1;

        FarmScanTask(Anchor anchor, Level level, String dimId) {
            this.anchor = anchor;
            this.level = level;
            this.center = anchor.framePos;
            this.ctx = new HarvestContext(anchor, level, anchor.hoe, anchor.chest, null);
            try {
                ItemStack frameHoe = readHoeFromFrame(level, center);
                if (frameHoe != null && !frameHoe.isEmpty() && frameHoe.getItem() instanceof HoeItem) {
                    this.ctx.hoe = frameHoe.copy();
                    try { FrameRegistry.updateHoe(dimId, center, this.ctx.hoe.copy()); } catch (Throwable ignored) {}
                } else if (this.ctx.hoe == null || this.ctx.hoe.isEmpty()) {
                    ItemStack replacement = ChestUtils.takeFirstHoe(anchor.chest);
                    if (replacement != null && !replacement.isEmpty()) {
                        try { com.fastharvester.platform.Services.PLATFORM.updateFrameItem(level, center, replacement.copy()); } catch (Throwable ignored) {}
                        this.ctx.hoe = replacement.copy();
                        try { FrameRegistry.updateHoe(dimId, center, this.ctx.hoe.copy()); } catch (Throwable ignored) {}
                    }
                }
            } catch (Throwable ignored) {}
            this.dimId = dimId;

            List<BlockPos> candidates = bfsDiscoverFarm(center, level, Math.max(1, Config.scanRange));
            if (candidates.isEmpty()) {
                for (int dx = -Config.scanRange; dx <= Config.scanRange; dx++) for (int dz = -Config.scanRange; dz <= Config.scanRange; dz++) candidates.add(center.offset(dx, 0, dz));
            }

            int maxRing = 0;
            Set<BlockPos> candidateSet = new HashSet<>();
            for (BlockPos p : candidates) {
                int ring = Math.max(Math.abs(p.getX() - center.getX()), Math.abs(p.getZ() - center.getZ()));
                if (ring > Config.scanRange) continue;
                ringMap.computeIfAbsent(ring, k -> new ArrayList<>()).add(p);
                maxRing = Math.max(maxRing, ring);
                candidateSet.add(p);
            }
            this.computedMaxRing = maxRing;

            int range = Math.max(1, Config.scanRange);
            spiralPositions.add(new SpiralStep(center, Direction.NORTH));
            int x = 0, z = 0;
            int stepSize = 1;
            int[] dxs = new int[]{1, 0, -1, 0};
            int[] dzs = new int[]{0, 1, 0, -1};
            int dir = 0;
            int maxCells = (2 * range + 1) * (2 * range + 1);
            int visitedCells = 1;
            outer:
            while (visitedCells < maxCells) {
                for (int rep = 0; rep < 2; rep++) {
                    for (int i = 0; i < stepSize; i++) {
                        x += dxs[dir];
                        z += dzs[dir];
                        if (Math.abs(x) <= range && Math.abs(z) <= range) {
                            visitedCells++;
                            BlockPos p = center.offset(x, 0, z);
                            if (candidateSet.contains(p)) spiralPositions.add(new SpiralStep(p, getSpiralDirection(dxs[dir], dzs[dir])));
                            if (visitedCells >= maxCells) break outer;
                        }
                    }
                    dir = (dir + 1) & 3;
                }
                stepSize++;
            }

            this.totalPositions = spiralPositions.size();

            int ticks = Math.max(1, Config.maxSpiralDurationTicks);
            this.positionsPerTick = Math.max(1, (int) Math.ceil((double) totalPositions / (double) ticks));
            for (int i = 0; i < spiralPositions.size(); i++) {
                BlockPos p = spiralPositions.get(i).pos;
                int ring = Math.max(Math.abs(p.getX() - center.getX()), Math.abs(p.getZ() - center.getZ()));
                ringFullIndices.computeIfAbsent(ring, k -> new ArrayList<>()).add(i);
            }
            for (Map.Entry<Integer, List<Integer>> e : ringFullIndices.entrySet()) {
                List<Integer> list = e.getValue();
                for (int j = 0; j < list.size(); j++) indexToPosInRing.put(list.get(j), j);
            }
            this.numberOfTicksNeeded = (int) Math.ceil((double) this.totalPositions / (double) this.positionsPerTick);
            Constants.logDebug("[SCAN] Created FarmScanTask center={} totalPositions={} positionsPerTick={} computedMaxRing={} ticksNeeded={}", center, totalPositions, positionsPerTick, computedMaxRing, numberOfTicksNeeded);
        }

        boolean tick() {
            if (ctx.chestFull) {
                FrameRegistry.setCooldown(dimId, center, Config.chestFullCooldownTicks);
                ctx.logSummary();
                return true;
            }

            if (totalPositions == 0) {
                ctx.logSummary();
                return true;
            }

            tickCounter++;
            if (animationStepsRemaining > 0) {
                try {
                    boolean shouldApply = !fullAnimationScheduled || (fullAnimationScheduled && (tickCounter % animationInterval == 0));
                    if (shouldApply) {
                        int before = getFrameRotation(level, center) & 7;
                        int next = (before + 1) & 7;
                        setFrameRotation(level, center, next, fullAnimationScheduled);
                        int after = getFrameRotation(level, center) & 7;
                        if (after != before) animationStepsRemaining--;
                    }
                } catch (Throwable ignored) {}
            }

            int endIndex = Math.min(totalPositions - 1, currentIndex + positionsPerTick - 1);

            int beforeHarvest = ctx.harvestedCount;
            // int baseRotation = getFrameRotation(level, center);
            int computedMaxRingLocal = computedMaxRing;
            int maxRing = computedMaxRingLocal;
            Map<Integer, List<Integer>> ringToIndices = new HashMap<>();
            for (int idx = currentIndex; idx <= endIndex; idx++) {
                BlockPos pos = spiralPositions.get(idx).pos;
                int ring = Math.max(Math.abs(pos.getX() - center.getX()), Math.abs(pos.getZ() - center.getZ()));
                ringToIndices.computeIfAbsent(ring, k -> new ArrayList<>()).add(idx);
            }

            for (int ring = 0; ring <= maxRing; ring++) {
                List<Integer> indices = ringToIndices.get(ring);
                if (indices == null) continue;
                boolean ringHarvested = false;
                for (int idx : indices) {
                    BlockPos pos = spiralPositions.get(idx).pos;
                    Direction curDir = spiralPositions.get(idx).dir;
                    try {
                        BlockState state = level.getBlockState(pos);
                        ctx.incrementBlocksScanned();

                        Block block = state.getBlock();
                        boolean harvested = false;
                        if (state.is(Blocks.MELON) || state.is(Blocks.PUMPKIN)) {
                            HarvestUtils.harvestCrop(ctx, pos, state, s -> true, s -> null);
                            harvested = ctx.harvestedCount > beforeHarvest;
                        } else if (state.is(Blocks.MELON_STEM) || state.is(Blocks.PUMPKIN_STEM)) {
                            Direction[] dirs = new Direction[]{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
                            boolean harvestedFruit = false;
                            for (Direction d : dirs) {
                                BlockPos npos = pos.relative(d);
                                BlockState ns = level.getBlockState(npos);
                                if ((ns.is(Blocks.MELON) && state.is(Blocks.MELON_STEM)) || (ns.is(Blocks.PUMPKIN) && state.is(Blocks.PUMPKIN_STEM))) {
                                    HarvestUtils.harvestCrop(ctx, npos, ns, s -> true, s -> null);
                                    harvestedFruit = ctx.harvestedCount > beforeHarvest;
                                    if (harvestedFruit) break;
                                }
                            }
                            harvested = harvestedFruit;
                        } else {
                            boolean isCrop = block instanceof CropBlock || state.is(Blocks.NETHER_WART) || state.is(Blocks.SWEET_BERRY_BUSH);
                            if (isCrop) {
                                int threshold = (state.is(Blocks.NETHER_WART) || state.is(Blocks.SWEET_BERRY_BUSH)) ? 3 : 7;
                                boolean mature = false;
                                try { int age = state.getValue(CropBlock.AGE); mature = age >= threshold; } catch (Throwable ignored) { mature = false; }
                                if (mature) {
                                    HarvestUtils.harvestCrop(ctx, pos, state,
                                            s -> {
                                                try { int a = s.getValue(CropBlock.AGE); if (s.is(Blocks.NETHER_WART) || s.is(Blocks.SWEET_BERRY_BUSH)) return a >= 3; return a >= 7; } catch (Throwable tt) { return false; }
                                            },
                                            s -> {
                                                try {
                                                    if (s.is(Blocks.SWEET_BERRY_BUSH)) return s.setValue(CropBlock.AGE, 1);
                                                    return s.setValue(CropBlock.AGE, 0);
                                                } catch (Throwable tt) { return null; }
                                            });
                                    harvested = ctx.harvestedCount > beforeHarvest;
                                }
                            }
                        }
                        if (harvested) ringHarvested = true;
                        if (harvested) { anyHarvested = true; lastHarvestedRing = ring; }

                        if (Config.rotationMode == com.fastharvester.enums.RotationMode.FOLLOW_HARVEST_SPIRAL) {
                            Integer posInRing = indexToPosInRing.get(idx);
                            List<Integer> full = ringFullIndices.get(ring);
                            if (posInRing != null && full != null && !full.isEmpty()) {
                                int ringSize = full.size();
                                int rot = (int) Math.floor((double) posInRing * 8.0 / (double) ringSize) & 7;
                                if (rot != lastComputedRotation) {
                                    setFrameRotation(level, center, rot);
                                    lastComputedRotation = rot;
                                }
                            } else {
                                if (lastDirection == null || !lastDirection.equals(curDir)) {
                                    setFrameRotation(level, center, dirToRotation(curDir));
                                    lastDirection = curDir;
                                }
                            }
                        }
                    } catch (Throwable t) {
                        Constants.logDebug("[SCAN] Exception while scanning " + center, t);
                    }

                    if (ctx.chestFull) {
                        FrameRegistry.setCooldown(dimId, center, Config.chestFullCooldownTicks);
                        ctx.logSummary();
                        return true;
                    }
                }
                if (ringHarvested) {
                    // int newRotation = baseRotation;
                    switch (Config.rotationMode) {
                        case STEP_PER_HARVEST -> {
                        }
                        case FULL_ROTATION_PER_HARVEST -> {
                            if (!fullAnimationScheduled) {
                                fullAnimationScheduled = true;
                                animationStepsRemaining = 8;
                                animationInterval = Math.max(1, (int) Math.ceil((double) numberOfTicksNeeded / 8.0));
                                tickCounter = 0;
                            }
                        }
                        case FOLLOW_HARVEST_SPIRAL -> {
                        }
                    }
                }
            }

            currentIndex = endIndex + 1;

            if (currentIndex >= totalPositions) {
                int range = Math.max(1, Config.scanRange);

                if (!neighborPassDone) {
                    for (int dx = -range; dx <= range; dx++) {
                        for (int dz = -range; dz <= range; dz++) {
                            BlockPos pos = center.offset(dx, 0, dz);
                            BlockState cur = level.getBlockState(pos);
                            if (!cur.isAir()) continue;
                            BlockPos belowPos = pos.below();
                            BlockState below = level.getBlockState(belowPos);

                            if (below != null && below.getBlock() == Blocks.FARMLAND) {
                                Map<Block, Integer> counts = new HashMap<>();
                                Direction[] dirs = new Direction[]{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
                                for (Direction d : dirs) {
                                    BlockPos npos = pos.relative(d);
                                    BlockState ns = level.getBlockState(npos);
                                    Block b = ns.getBlock();
                                    if (b == Blocks.WHEAT || b == Blocks.BEETROOTS || b == Blocks.CARROTS || b == Blocks.POTATOES || b == Blocks.MELON_STEM || b == Blocks.PUMPKIN_STEM) {
                                        counts.merge(b, 1, Integer::sum);
                                    }
                                }
                                if (!counts.isEmpty()) {
                                    Block chosen = counts.entrySet().stream().max(Comparator.comparingInt(Map.Entry::getValue)).get().getKey();
                                    Item seed = seedForBlock(chosen);
                                    if (seed != null && ChestUtils.removeOne(anchor.chest, seed)) {
                                        BlockState plantState = chosen.defaultBlockState();
                                        try { if (plantState.getBlock() instanceof CropBlock) plantState = plantState.setValue(CropBlock.AGE, 0); } catch (Throwable t) {}
                                        level.setBlock(pos, plantState, 3);
                                    }
                                }
                            }

                            if (below != null && below.getBlock() == Blocks.SOUL_SAND) {
                                Map<Block, Integer> counts = new HashMap<>();
                                Direction[] dirs = new Direction[]{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
                                for (Direction d : dirs) {
                                    BlockPos npos = pos.relative(d);
                                    BlockState ns = level.getBlockState(npos);
                                    Block b = ns.getBlock();
                                    if (b == Blocks.NETHER_WART) counts.merge(b, 1, Integer::sum);
                                }
                                if (!counts.isEmpty()) {
                                    Block chosen = counts.entrySet().stream().max(Comparator.comparingInt(Map.Entry::getValue)).get().getKey();
                                    Item seed = seedForBlock(chosen);
                                    if (seed != null && ChestUtils.removeOne(anchor.chest, seed)) {
                                        BlockState plantState = chosen.defaultBlockState();
                                        try { if (plantState.getBlock() instanceof CropBlock) plantState = plantState.setValue(CropBlock.AGE, 0); } catch (Throwable t) {}
                                        level.setBlock(pos, plantState, 3);
                                    }
                                }
                            }

                            if (below != null && (below.getBlock() == Blocks.DIRT || below.getBlock() == Blocks.GRASS_BLOCK)) {
                                Direction[] dirs = new Direction[]{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
                                int farmlandNeighbors = 0;
                                for (Direction d : dirs) {
                                    BlockState ns = level.getBlockState(belowPos.relative(d));
                                    if (ns != null && ns.getBlock() == Blocks.FARMLAND) farmlandNeighbors++;
                                }
                                if (farmlandNeighbors < 1) continue;
                                boolean nearMelonPumpkin = false;
                                for (Direction d : dirs) {
                                    BlockState ns = level.getBlockState(pos.relative(d));
                                    if (ns.is(Blocks.MELON) || ns.is(Blocks.PUMPKIN) || ns.is(Blocks.MELON_STEM) || ns.is(Blocks.PUMPKIN_STEM)) { nearMelonPumpkin = true; break; }
                                }
                                if (nearMelonPumpkin) continue;
                                BlockState farmland = Blocks.FARMLAND.defaultBlockState();
                                level.setBlock(belowPos, farmland, 3);
                                ItemStack before = anchor.hoe.copy();
                                DurabilityLogic.applyDamage(level, anchor.hoe, level.getRandom());
                                if (anchor.hoe.isEmpty()) HarvestUtils.handleBrokenHoe(ctx, before);

                                Map<Block, Integer> counts2 = new HashMap<>();
                                for (Direction d : dirs) {
                                    BlockPos npos = pos.relative(d);
                                    BlockState ns = level.getBlockState(npos);
                                    Block b = ns.getBlock();
                                    if (b == Blocks.WHEAT || b == Blocks.BEETROOTS || b == Blocks.CARROTS || b == Blocks.POTATOES || b == Blocks.MELON_STEM || b == Blocks.PUMPKIN_STEM) {
                                        counts2.merge(b, 1, Integer::sum);
                                    }
                                }
                                if (!counts2.isEmpty()) {
                                    Block chosen2 = counts2.entrySet().stream().max(Comparator.comparingInt(Map.Entry::getValue)).get().getKey();
                                    Item seed2 = seedForBlock(chosen2);
                                    if (seed2 != null && ChestUtils.removeOne(anchor.chest, seed2)) {
                                        BlockState plantState2 = chosen2.defaultBlockState();
                                        try { if (plantState2.getBlock() instanceof CropBlock) plantState2 = plantState2.setValue(CropBlock.AGE, 0); } catch (Throwable t) {}
                                        level.setBlock(pos, plantState2, 3);
                                    }
                                }
                            }
                        }
                    }
                    neighborPassDone = true;
                }

                if (ctx.chestFull) {
                    FrameRegistry.setCooldown(dimId, center, Config.chestFullCooldownTicks);
                    ctx.logSummary();
                    return true;
                }

                if (fullAnimationScheduled && animationStepsRemaining > 0) {
                    return false;
                }

                if (anyHarvested && lastHarvestedRing >= 0) {
                    int newRotation = 0;
                    switch (Config.rotationMode) {
                        case STEP_PER_HARVEST -> {
                            newRotation = (getFrameRotation(level, center) + 1) & 7;
                            setFrameRotation(level, center, newRotation);
                        }
                        case FULL_ROTATION_PER_HARVEST -> {
                            if (!fullAnimationScheduled) {
                                int steps = computedMaxRing > 0 ? (int) Math.floor((double)(lastHarvestedRing + 1) * 8.0 / (computedMaxRing + 1)) : 0;
                                newRotation = steps & 7;
                                setFrameRotation(level, center, newRotation);
                            }
                        }
                        case FOLLOW_HARVEST_SPIRAL -> {
                            List<Integer> full = ringFullIndices.get(lastHarvestedRing);
                            if (full != null && !full.isEmpty()) {
                                int posIdx = Math.max(0, full.size() - 1);
                                int rot = (int) Math.floor((double) posIdx * 8.0 / (double) full.size()) & 7;
                                newRotation = rot;
                                setFrameRotation(level, center, newRotation);
                            }
                        }
                    }
                }

                ctx.logSummary();
                return true;
            }

            return false;
        }
    }

    private static int getFrameRotation(Level level, BlockPos pos) {
        try {
            List<ItemFrame> frames = level.getEntitiesOfClass(ItemFrame.class, new AABB(pos));
            for (ItemFrame f : frames) {
                if (f.blockPosition().equals(pos)) {
                    for (Method m : f.getClass().getMethods()) {
                        String name = m.getName().toLowerCase();
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
                try { return FastItemFrameAdapterImpl.getRotation(be); } catch (Throwable ignored) {}
            }
        } catch (Throwable t) {
            Constants.logDebug("[ROT] getFrameRotation failed at " + pos, t);
        }
        return 0;
    }

    private static ItemStack readHoeFromFrame(Level level, BlockPos pos) {
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
                ItemStack s = FastItemFrameAdapterImpl.extractHeldItem(be);
                if (s != null && !s.isEmpty() && s.getItem() instanceof HoeItem) return s.copy();
            }
        } catch (Throwable ignored) {}

        return ItemStack.EMPTY;
    }

    private static void setFrameRotation(Level level, BlockPos pos, int newRotation) {
        setFrameRotation(level, pos, newRotation, false);
    }

    private static void setFrameRotation(Level level, BlockPos pos, int newRotation, boolean bypassCooldown) {
        long gameTime = -1L;
        try { gameTime = level != null ? level.getGameTime() : -1L; } catch (Throwable ignored) {}

        String dimId = "";
        try { dimId = level != null ? level.dimension().identifier().toString() : ""; } catch (Throwable ignored) {}

        if (bypassCooldown) {
            try { FrameRegistry.tryRotation(dimId, pos, gameTime); } catch (Throwable ignored) {}
            try {
                applyScheduledRotation(level, pos, newRotation);
            } catch (Throwable t) {
                Constants.logDebug("[ROT] applyScheduledRotation failed at " + pos, t);
            }
            return;
        }

            try {
            if (!FrameRegistry.tryRotation(dimId, pos, gameTime)) {
                if (Config.debugLogging) try { Constants.logDebug("[ROT] Skipped rotation for {} due to cooldown (gametime={})", pos, gameTime); } catch (Throwable ignored) {}
                return;
            }
        } catch (Throwable ignored) {}

            try {
            int cur = getFrameRotation(level, pos) & 7;
            if (cur == (newRotation & 7)) {
                if (Config.debugLogging) try { Constants.logDebug("[ROT] No-op rotation for {} (already {})", pos, cur); } catch (Throwable ignored) {}
                return;
            }
        } catch (Throwable ignored) {}

        try {
            FrameRegistry.scheduleRotation(dimId, pos, newRotation, gameTime);
        } catch (Throwable t) {
            Constants.logDebug("[ROT] Failed to schedule rotation for " + pos, t);
        }
    }

    @SuppressWarnings("null")
    static void applyScheduledRotation(Level level, BlockPos pos, int newRotation) {
        try {
            long gameTime = -1L;
            try { gameTime = level != null ? level.getGameTime() : -1L; } catch (Throwable ignored) {}
            try { Constants.logInfo("[ROT] applyScheduledRotation pos={} newRot={} mode={} gametime={}", pos, newRotation, Config.rotationMode, gameTime); } catch (Throwable ignored) {}

            List<ItemFrame> frames = level.getEntitiesOfClass(ItemFrame.class, new AABB(pos));
            for (ItemFrame f : frames) {
                if (f.blockPosition().equals(pos)) {
                    for (Method m : f.getClass().getMethods()) {
                        String name = m.getName().toLowerCase();
                        if (name.contains("set") && name.contains("rotation") && m.getParameterCount() == 1) {
                            Class<?> p = m.getParameterTypes()[0];
                            try {
                                if (p == int.class || p == Integer.class) {
                                    m.invoke(f, newRotation);
                                    if (Config.debugLogging) try { Constants.logDebug("[ROT] Applied rotation on ItemFrame entity at {} => {} (method {})", pos, newRotation, m.getName()); } catch (Throwable ignored) {}
                                    return;
                                }
                                if (p == byte.class || p == Byte.class) {
                                    m.invoke(f, (byte) newRotation);
                                    if (Config.debugLogging) try { Constants.logDebug("[ROT] Applied rotation on ItemFrame entity at {} => {} (method {})", pos, newRotation, m.getName()); } catch (Throwable ignored) {}
                                    return;
                                }
                            } catch (Throwable t) {
                                if (Config.debugLogging) try { Constants.logDebug("[ROT] Failed to invoke setter " + m.getName() + " on ItemFrame " + pos, t); } catch (Throwable ignored) {}
                            }
                        }
                    }
                    try {
                        Field fld = f.getClass().getDeclaredField("rotation");
                        fld.setAccessible(true);
                        fld.setInt(f, newRotation & 7);
                        if (Config.debugLogging) try { Constants.logDebug("[ROT] Applied rotation via field on ItemFrame at {} => {}", pos, newRotation & 7); } catch (Throwable ignored) {}
                        return;
                    } catch (Throwable ignored) {}
                }
            }

            BlockEntity be = level.getBlockEntity(pos);
            if (be != null) {
                try {
                    FastItemFrameAdapterImpl.setRotation(be, newRotation);
                    try { level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), 3); } catch (Throwable ignored) {}
                    if (Config.debugLogging) try { Constants.logDebug("[ROT] Applied rotation on FIF block-entity at {} => {}", pos, newRotation); } catch (Throwable ignored) {}
                    return;
                } catch (Throwable t) {
                    if (Config.debugLogging) try { Constants.logDebug("[ROT] Failed to apply rotation on FIF block-entity at " + pos, t); } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable t) {
            Constants.logDebug("[ROT] applyScheduledRotation failed at " + pos, t);
        }
    }
}
