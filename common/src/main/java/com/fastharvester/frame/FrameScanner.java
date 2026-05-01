package com.fastharvester.frame;
import com.fastharvester.util.log.LogUtils;
import com.fastharvester.config.Config;

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

import com.fastharvester.platform.adapter.FIF;
import com.fastharvester.util.chest.ChestUtils;
import com.fastharvester.harvest.HarvestUtils;
import com.fastharvester.harvest.HarvestContext;
import com.fastharvester.harvest.CropRegistry;
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
            boolean framePresent = false;
            java.util.List<ItemFrame> frames = level.getEntitiesOfClass(ItemFrame.class, new AABB(center));
            for (ItemFrame f : frames) { if (f.blockPosition().equals(center)) { framePresent = true; break; } }
            BlockEntity be = level.getBlockEntity(center);
                // Targeted diagnostic for problematic FIF anchor
                try {
                    if (center.equals(new BlockPos(-10, 56, 20))) {
                        boolean beIsFIF = be != null && FIF.isFastItemFrameBlockEntity(be);
                        LogUtils.logDebug("[DIAG] scanFarm presence check for {}: framesFound={}, beClass={}, isFIF={}", center, frames.size(), be == null ? "null" : be.getClass().getName(), beIsFIF);
                    }
                } catch (Throwable ignored) {}
            if (!framePresent && (be == null || !FIF.isFastItemFrameBlockEntity(be))) {
                try { LogUtils.logWarn("[SCAN] Anchor frame missing at {} in {}; unregistering and aborting scan.", center, dimId); } catch (Throwable ignored) {}
                try { FrameRegistry.unregisterFrame(dimId, center); } catch (Throwable ignored) {}
                return false;
            }
            if (anchor.chest instanceof BlockEntity chestBe) {
                BlockPos chestPos = chestBe.getBlockPos();
                BlockEntity current = level.getBlockEntity(chestPos);
                if (current != chestBe || !(current instanceof Container)) {
                    try { LogUtils.logWarn("[SCAN] Anchor chest missing/changed at {} for {}; unregistering and aborting scan.", chestPos, center); } catch (Throwable ignored) {}
                    try { FrameRegistry.unregisterFrame(dimId, center); } catch (Throwable ignored) {}
                    return false;
                }
            }
        } catch (Throwable t) {
            LogUtils.logDebug("[SCAN] Anchor presence check failed for " + center, t);
        }

        int rangeX = Math.max(1, Config.scanRangeX);
        int rangeZ = Math.max(1, Config.scanRangeZ);

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
                        com.fastharvester.util.hoe.FrameHoeReplacement.tryReplaceBrokenHoe(tempCtx);
                        if (tempCtx.hoe != null && !tempCtx.hoe.isEmpty()) {
                            currentHoe = tempCtx.hoe.copy();
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
                try { ctx.hoe = liveHoe.copy(); } catch (Throwable ignored) {}
            } catch (Throwable ignored) {}

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
                        int age = getAgeSafe(state);
                        boolean mature = age >= threshold;
                        if (mature) {
                            HarvestUtils.harvestCrop(ctx, pos, state,
                                    s -> {
                                        int a = getAgeSafe(s);
                                        if (a < 0) return false;
                                        return (s.is(Blocks.NETHER_WART) || s.is(Blocks.SWEET_BERRY_BUSH)) ? a >= 3 : a >= 7;
                                    },
                                    s -> {
                                        try {
                                            if (s.is(Blocks.SWEET_BERRY_BUSH)) return setAgeSafe(s, 1);
                                            return setAgeSafe(s, 0);
                                        } catch (Throwable tt) { return null; }
                                    });
                            harvested = ctx.harvestedCount > 0;
                            cropsFound = Math.max(cropsFound, ctx.harvestedCount);
                        }
                    }
                }
            } catch (Throwable t) {
                LogUtils.logDebug("[SCAN] Exception while scanning " + pos, t);
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

        LogUtils.logDebug("[SCAN] Scan complete. Blocks scanned: {}, crops found: {}.", blocksScanned, cropsFound);
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

    // private static List<SpiralStep> generateSpiral(BlockPos center, int range) {
    //     return generateSpiral(center, range, range);
    // }

    private static List<SpiralStep> generateSpiral(BlockPos center, int rangeX, int rangeZ) {
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
                if (com.fastharvester.harvest.CropRegistry.isCropBlock(b)) {
                    Integer prev = counts.get(b);
                    if (prev == null) counts.put(b, 1);
                    else counts.put(b, prev + 1);
                }
            }
            if (!counts.isEmpty()) {
                Block chosen = counts.entrySet().stream().max(Comparator.comparingInt(Map.Entry::getValue)).get().getKey();
                Item seed = CropRegistry.clutterSeed(chosen);
                if (seed != null && ChestUtils.removeOne(anchor.chest, seed)) {
                    BlockState plantState = chosen.defaultBlockState();
                    try { if (plantState.getBlock() instanceof CropBlock) plantState = setAgeSafe(plantState, 0); } catch (Throwable t) {}
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
                        Integer prev = counts.get(b);
                        if (prev == null) counts.put(b, 1);
                        else counts.put(b, prev + 1);
                    }
                }
            if (!counts.isEmpty()) {
                Block chosen = counts.entrySet().stream().max(Comparator.comparingInt(Map.Entry::getValue)).get().getKey();
                Item seed = CropRegistry.clutterSeed(chosen);
                if (seed != null && ChestUtils.removeOne(anchor.chest, seed)) {
                    BlockState plantState = chosen.defaultBlockState();
                    try { if (plantState.getBlock() instanceof CropBlock) plantState = setAgeSafe(plantState, 0); } catch (Throwable t) {}
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
                    ItemStack before = (ctx.hoe == null || ctx.hoe.isEmpty()) ? (anchor.hoe == null ? ItemStack.EMPTY : anchor.hoe.copy()) : ctx.hoe.copy();
                    try {
                        if (ctx != null && ctx.skipNextDamage) {
                            ctx.skipNextDamage = false;
                        } else {
                            com.fastharvester.util.durability.DurabilityLogic.applyDamage(level, ctx.hoe, level.getRandom());
                        }
                    } catch (Throwable ignored) {}
                    if (ctx.hoe == null || ctx.hoe.isEmpty()) {
                        HarvestUtils.handleBrokenHoe(ctx, before);
                    }
                    Map<Block, Integer> counts2 = new HashMap<>();
                    for (Direction d : dirs) {
                        BlockPos npos = pos.relative(d);
                        BlockState ns = level.getBlockState(npos);
                        Block b = ns.getBlock();
                        if (com.fastharvester.harvest.CropRegistry.isCropBlock(b)) {
                            Integer prev = counts2.get(b);
                            if (prev == null) counts2.put(b, 1);
                            else counts2.put(b, prev + 1);
                        }
                    }
                    if (!counts2.isEmpty()) {
                        Block chosen2 = counts2.entrySet().stream().max(Comparator.comparingInt(Map.Entry::getValue)).get().getKey();
                        Item seed2 = CropRegistry.clutterSeed(chosen2);
                        if (seed2 != null && ChestUtils.removeOne(anchor.chest, seed2)) {
                            BlockState plantState2 = chosen2.defaultBlockState();
                            try { if (plantState2.getBlock() instanceof CropBlock) plantState2 = setAgeSafe(plantState2, 0); } catch (Throwable t) {}
                            level.setBlock(pos, plantState2, 3);
                        }
                    }
                }
            }
        }
    }



    private static List<BlockPos> bfsDiscoverFarm(BlockPos center, Level level, int rangeX, int rangeZ) {
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
    private static boolean isFarmPosition(Level level, BlockPos pos) {
        try {
            BlockState state = level.getBlockState(pos);
            Block b = state.getBlock();
            if (CropRegistry.isCropBlock(b) || state.is(Blocks.MELON) || state.is(Blocks.PUMPKIN) || state.is(Blocks.MELON_STEM) || state.is(Blocks.PUMPKIN_STEM)) return true;
            if (!state.isAir()) return false;

            Block below = level.getBlockState(pos.below()).getBlock();
            if (below == Blocks.FARMLAND) {
                return com.fastharvester.harvest.CropRegistry.hasClearFarmlandCropConsensus(level, pos);
            }
            if (below == Blocks.SOUL_SAND) {
                if (level.getBlockState(pos.north()).is(Blocks.NETHER_WART) || level.getBlockState(pos.south()).is(Blocks.NETHER_WART)
                        || level.getBlockState(pos.east()).is(Blocks.NETHER_WART) || level.getBlockState(pos.west()).is(Blocks.NETHER_WART)) return true;
            }

            int melonPumpkinNeighbors = 0;
            if (com.fastharvester.harvest.CropRegistry.isMelonPumpkinFarmBlock(level.getBlockState(pos.north()).getBlock())) melonPumpkinNeighbors++;
            if (com.fastharvester.harvest.CropRegistry.isMelonPumpkinFarmBlock(level.getBlockState(pos.south()).getBlock())) melonPumpkinNeighbors++;
            if (com.fastharvester.harvest.CropRegistry.isMelonPumpkinFarmBlock(level.getBlockState(pos.east()).getBlock())) melonPumpkinNeighbors++;
            if (com.fastharvester.harvest.CropRegistry.isMelonPumpkinFarmBlock(level.getBlockState(pos.west()).getBlock())) melonPumpkinNeighbors++;
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
            LogUtils.logDebug("[SCAN] Scheduled scan task for {} in {} (will span up to {} ticks)", anchor, dimId, Config.maxSpiralDurationTicks);
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
        boolean hasMature = false;
        int numberOfTicksNeeded = 0;
        boolean fullAnimationScheduled = false;
        int animationInterval = 1;
        final List<Integer> fullAnimationSequence = new ArrayList<>();
        int fullAnimationIndex = 0;

        FarmScanTask(Anchor anchor, Level level, String dimId) {
            this.anchor = anchor;
            this.level = level;
            this.center = anchor.framePos;
            this.ctx = new HarvestContext(anchor, level, (anchor.hoe == null ? ItemStack.EMPTY : anchor.hoe.copy()), anchor.chest, null);
            try {
                ItemStack frameHoe = readHoeFromFrame(level, center);
                if (frameHoe != null && !frameHoe.isEmpty() && frameHoe.getItem() instanceof HoeItem) {
                    this.ctx.hoe = frameHoe.copy();
                    try { FrameRegistry.updateHoe(dimId, center, this.ctx.hoe.copy()); } catch (Throwable ignored) {}
                } else if (this.ctx.hoe == null || this.ctx.hoe.isEmpty()) {
                    try {
                        com.fastharvester.util.hoe.FrameHoeReplacement.tryReplaceBrokenHoe(this.ctx);
                    } catch (Throwable ignored) {}
                }
            } catch (Throwable ignored) {}
            this.dimId = dimId;

            int rX = Math.max(1, Config.scanRangeX);
            int rZ = Math.max(1, Config.scanRangeZ);
            List<BlockPos> candidates = bfsDiscoverFarm(center, level, rX, rZ);
            if (candidates.isEmpty()) {
                for (int dx = -rX; dx <= rX; dx++) for (int dz = -rZ; dz <= rZ; dz++) candidates.add(center.offset(dx, 0, dz));
            }

            int maxRing = 0;
            Set<BlockPos> candidateSet = new HashSet<>();
            for (BlockPos p : candidates) {
                int ring = Math.max(Math.abs(p.getX() - center.getX()), Math.abs(p.getZ() - center.getZ()));
                if (ring > Math.max(rX, rZ)) continue;
                ringMap.computeIfAbsent(ring, k -> new ArrayList<>()).add(p);
                maxRing = Math.max(maxRing, ring);
                candidateSet.add(p);
            }
            this.computedMaxRing = maxRing;

            // int range = Math.max(rX, rZ);
            spiralPositions.add(new SpiralStep(center, Direction.NORTH));
            int x = 0, z = 0;
            int stepSize = 1;
            int[] dxs = new int[]{1, 0, -1, 0};
            int[] dzs = new int[]{0, 1, 0, -1};
            int dir = 0;
            int maxCells = (2 * rX + 1) * (2 * rZ + 1);
            int visitedCells = 1;
            outer:
            while (visitedCells < maxCells) {
                for (int rep = 0; rep < 2; rep++) {
                    for (int i = 0; i < stepSize; i++) {
                        x += dxs[dir];
                        z += dzs[dir];
                        if (Math.abs(x) <= rX && Math.abs(z) <= rZ) {
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

            // Quick pre-scan: determine whether any spiral position contains a mature crop or harvestable fruit.
            boolean foundMature = false;
            for (SpiralStep step : spiralPositions) {
                BlockPos p = step.pos;
                try {
                    BlockState s = level.getBlockState(p);
                    if (s == null || s.isAir()) continue;
                    if (s.is(Blocks.MELON) || s.is(Blocks.PUMPKIN)) { foundMature = true; break; }
                    if (s.is(Blocks.MELON_STEM) || s.is(Blocks.PUMPKIN_STEM)) {
                        Direction[] dirs = new Direction[]{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
                        for (Direction d : dirs) {
                            BlockPos np = p.relative(d);
                            BlockState ns = level.getBlockState(np);
                            if (ns.is(Blocks.MELON) || ns.is(Blocks.PUMPKIN)) { foundMature = true; break; }
                        }
                        if (foundMature) break;
                    } else {
                        boolean isCrop = s.getBlock() instanceof CropBlock || s.is(Blocks.NETHER_WART) || s.is(Blocks.SWEET_BERRY_BUSH);
                        if (isCrop) {
                            int threshold = (s.is(Blocks.NETHER_WART) || s.is(Blocks.SWEET_BERRY_BUSH)) ? 3 : 7;
                            int age = getAgeSafe(s);
                            if (age >= threshold) { foundMature = true; break; }
                        }
                    }
                } catch (Throwable ignored) {}
            }
            this.hasMature = foundMature;
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
            LogUtils.logDebug("[SCAN] Created FarmScanTask center={} totalPositions={} positionsPerTick={} computedMaxRing={} ticksNeeded={}", center, totalPositions, positionsPerTick, computedMaxRing, numberOfTicksNeeded);
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

            if (!hasMature) {
                try { LogUtils.logDebug("[SCAN] No mature crops found for {} — skipping spiral", center); } catch (Throwable ignored) {}
                ctx.logSummary();
                return true;
            }

            // Validate anchor presence and chest integrity at the start of this tick
            try {
                boolean framePresent = false;
                java.util.List<ItemFrame> frames = level.getEntitiesOfClass(ItemFrame.class, new AABB(center));
                for (ItemFrame f : frames) { if (f.blockPosition().equals(center)) { framePresent = true; break; } }
                BlockEntity be = level.getBlockEntity(center);
                // Targeted diagnostic for problematic FIF anchor during scheduled scan
                try {
                    if (center.equals(new BlockPos(-10, 56, 20))) {
                        boolean beIsFIF = be != null && FIF.isFastItemFrameBlockEntity(be);
                        LogUtils.logDebug("[DIAG] scheduled tick presence check for {}: framesFound={}, beClass={}, isFIF={}", center, frames.size(), be == null ? "null" : be.getClass().getName(), beIsFIF);
                        ItemStack live = readHoeFromFrame(level, center);
                        LogUtils.logDebug("[DIAG] scheduled tick readHoeFromFrame for {}: liveHoeEmpty={}", center, live == null || live.isEmpty());
                    }
                } catch (Throwable ignored) {}
                if (!framePresent && (be == null || !FIF.isFastItemFrameBlockEntity(be))) {
                    try { LogUtils.logWarn("[SCAN] Anchor frame missing at {} during scheduled scan; unregistering.", center); } catch (Throwable ignored) {}
                    try { FrameRegistry.unregisterFrame(dimId, center); } catch (Throwable ignored) {}
                    ctx.logSummary();
                    return true;
                }
                if (anchor.chest instanceof BlockEntity chestBe) {
                    BlockPos chestPos = chestBe.getBlockPos();
                    BlockEntity current = level.getBlockEntity(chestPos);
                    if (current != chestBe || !(current instanceof Container)) {
                        try { LogUtils.logWarn("[SCAN] Anchor chest missing/changed at {} during scheduled scan; unregistering.", chestPos); } catch (Throwable ignored) {}
                        try { FrameRegistry.unregisterFrame(dimId, center); } catch (Throwable ignored) {}
                        ctx.logSummary();
                        return true;
                    }
                }

                // Ensure a hoe is present on the frame at tick start; abort the scan if removed.
                try {
                    ItemStack liveHoe = readHoeFromFrame(level, center);
                    if (liveHoe == null || liveHoe.isEmpty()) {
                        try { LogUtils.logDebug("[SCAN] Hoe removed from frame at {} during scheduled scan; aborting.", center); } catch (Throwable ignored) {}
                        ctx.logSummary();
                        return true;
                    }
                    try { ctx.hoe = liveHoe.copy(); } catch (Throwable ignored) {}
                    try { FrameRegistry.updateHoe(dimId, center, ctx.hoe.copy()); } catch (Throwable ignored) {}
                } catch (Throwable ignored) {}
            } catch (Throwable t) {
                LogUtils.logDebug("[SCAN] Anchor re-check failed for " + center, t);
            }

            tickCounter++;
            if (animationStepsRemaining > 0) {
                try {
                    boolean shouldApply = !fullAnimationScheduled || (fullAnimationScheduled && (tickCounter % animationInterval == 0));
                    if (shouldApply) {
                        if (!fullAnimationSequence.isEmpty() && fullAnimationIndex < fullAnimationSequence.size()) {
                            int next = fullAnimationSequence.get(fullAnimationIndex);
                            try { LogUtils.logDebug("[ROT] Animation step (apply) for {} tick={} next={} idx={} remaining={}", center, tickCounter, next, fullAnimationIndex, animationStepsRemaining); } catch (Throwable ignored) {}
                            setFrameRotation(level, center, next, true);
                            fullAnimationIndex++;
                            animationStepsRemaining--;
                        } else {
                            if (Config.debugLogging) try { LogUtils.logDebug("[ROT] No animation sequence available for {} (idx={} size={})", center, fullAnimationIndex, fullAnimationSequence.size()); } catch (Throwable ignored) {}
                        }
                    }
                } catch (Throwable ignored) {}
            }

            if (fullAnimationScheduled && animationStepsRemaining <= 0) {
                try { com.fastharvester.frame.FrameRegistry.setAnimating(dimId, center, false); } catch (Throwable ignored) {}
                fullAnimationScheduled = false;
                try { fullAnimationSequence.clear(); } catch (Throwable ignored) {}
                fullAnimationIndex = 0;
                try { LogUtils.logDebug("[ROT] Full animation complete for {} — cleared sequence", center); } catch (Throwable ignored) {}
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

                        // Temporary detailed debug: log per-position block/maturity and chest-space checks
                        try {
                            boolean isCropDbg = state.getBlock() instanceof CropBlock || state.is(Blocks.NETHER_WART) || state.is(Blocks.SWEET_BERRY_BUSH);
                            int ageDbg = getAgeSafe(state);
                            int thresholdDbg = (state.is(Blocks.NETHER_WART) || state.is(Blocks.SWEET_BERRY_BUSH)) ? 3 : 7;
                            boolean chestSpaceDbg = false;
                            try { chestSpaceDbg = ChestUtils.hasSpace(ctx.chest); } catch (Throwable ignored) {}
                            try { LogUtils.logDebug("[SCAN-DBG] pos={} block={} isCrop={} age={} threshold={} chestHasSpace={} beforeHarvest={}", pos, state.getBlock().getClass().getName(), isCropDbg, ageDbg, thresholdDbg, chestSpaceDbg, beforeHarvest); } catch (Throwable ignored) {}
                        } catch (Throwable ignored) {}

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
                                int age = getAgeSafe(state);
                                boolean mature = age >= threshold;
                                if (mature) {
                                    HarvestUtils.harvestCrop(ctx, pos, state,
                                            s -> {
                                                int a = getAgeSafe(s);
                                                if (a < 0) return false;
                                                return (s.is(Blocks.NETHER_WART) || s.is(Blocks.SWEET_BERRY_BUSH)) ? a >= 3 : a >= 7;
                                            },
                                            s -> {
                                                try {
                                                    if (s.is(Blocks.SWEET_BERRY_BUSH)) return setAgeSafe(s, 1);
                                                    return setAgeSafe(s, 0);
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
                        LogUtils.logDebug("[SCAN] Exception while scanning " + center, t);
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
                                tickCounter = 0;
                                // Prepare a deterministic 8-step rotation sequence starting from current rotation
                                try {
                                    int start = getFrameRotation(level, center) & 7;
                                    fullAnimationSequence.clear();
                                    for (int s = 1; s <= 8; s++) fullAnimationSequence.add((start + s) & 7);
                                    fullAnimationIndex = 0;
                                    animationStepsRemaining = fullAnimationSequence.size();
                                    animationInterval = Math.max(1, (int) Math.ceil((double) numberOfTicksNeeded / (double) fullAnimationSequence.size()));
                                    try { LogUtils.logDebug("[ROT] Prepared full animation sequence for {} start={} seq={}", center, start, fullAnimationSequence); } catch (Throwable ignored) {}
                                } catch (Throwable ignored) {
                                    animationStepsRemaining = 8;
                                    animationInterval = Math.max(1, (int) Math.ceil((double) numberOfTicksNeeded / 8.0));
                                }
                                try { com.fastharvester.frame.FrameRegistry.setAnimating(dimId, center, true); } catch (Throwable ignored) {}
                            }
                        }
                        case FOLLOW_HARVEST_SPIRAL -> {
                        }
                    }
                }
            }

            currentIndex = endIndex + 1;

            if (currentIndex >= totalPositions) {
                int rX = Math.max(1, Config.scanRangeX);
                int rZ = Math.max(1, Config.scanRangeZ);

                if (!neighborPassDone) {
                    for (int dx = -rX; dx <= rX; dx++) {
                        for (int dz = -rZ; dz <= rZ; dz++) {
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
                                    if (com.fastharvester.harvest.CropRegistry.isCropBlock(b)) {
                                                Integer prev = counts.get(b);
                                                if (prev == null) counts.put(b, 1);
                                                else counts.put(b, prev + 1);
                                            }
                                }
                                if (!counts.isEmpty()) {
                                    Block chosen = counts.entrySet().stream().max(Comparator.comparingInt(Map.Entry::getValue)).get().getKey();
                                    Item seed = CropRegistry.clutterSeed(chosen);
                                    if (seed != null && ChestUtils.removeOne(anchor.chest, seed)) {
                                        BlockState plantState = chosen.defaultBlockState();
                                        try { if (plantState.getBlock() instanceof CropBlock) plantState = setAgeSafe(plantState, 0); } catch (Throwable t) {}
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
                                    if (b == Blocks.NETHER_WART) {
                                        Integer prev = counts.get(b);
                                        if (prev == null) counts.put(b, 1);
                                        else counts.put(b, prev + 1);
                                    }
                                }
                                if (!counts.isEmpty()) {
                                    Block chosen = counts.entrySet().stream().max(Comparator.comparingInt(Map.Entry::getValue)).get().getKey();
                                    Item seed = CropRegistry.clutterSeed(chosen);
                                    if (seed != null && ChestUtils.removeOne(anchor.chest, seed)) {
                                        BlockState plantState = chosen.defaultBlockState();
                                            try { if (plantState.getBlock() instanceof CropBlock) plantState = setAgeSafe(plantState, 0); } catch (Throwable t) {}
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
                                ItemStack before = (ctx.hoe == null || ctx.hoe.isEmpty()) ? (anchor.hoe == null ? ItemStack.EMPTY : anchor.hoe.copy()) : ctx.hoe.copy();
                                try {
                                    if (ctx != null && ctx.skipNextDamage) {
                                        ctx.skipNextDamage = false;
                                    } else {
                                        DurabilityLogic.applyDamage(level, ctx.hoe, level.getRandom());
                                    }
                                } catch (Throwable ignored) {}
                                if (ctx.hoe == null || ctx.hoe.isEmpty()) HarvestUtils.handleBrokenHoe(ctx, before);

                                Map<Block, Integer> counts2 = new HashMap<>();
                                for (Direction d : dirs) {
                                    BlockPos npos = pos.relative(d);
                                    BlockState ns = level.getBlockState(npos);
                                    Block b = ns.getBlock();
                                    if (b == Blocks.WHEAT || b == Blocks.BEETROOTS || b == Blocks.CARROTS || b == Blocks.POTATOES || b == Blocks.MELON_STEM || b == Blocks.PUMPKIN_STEM) {
                                        Integer prev = counts2.get(b);
                                        if (prev == null) counts2.put(b, 1);
                                        else counts2.put(b, prev + 1);
                                    }
                                }
                                if (!counts2.isEmpty()) {
                                    Block chosen2 = counts2.entrySet().stream().max(Comparator.comparingInt(Map.Entry::getValue)).get().getKey();
                                    Item seed2 = CropRegistry.clutterSeed(chosen2);
                                    if (seed2 != null && ChestUtils.removeOne(anchor.chest, seed2)) {
                                        BlockState plantState2 = chosen2.defaultBlockState();
                                        try { if (plantState2.getBlock() instanceof CropBlock) plantState2 = setAgeSafe(plantState2, 0); } catch (Throwable t) {}
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
                try { LogUtils.logDebug("[ROT] Direct apply (bypass) for {} -> {} (gametime={})", pos, newRotation & 7, gameTime); } catch (Throwable ignored) {}
                applyScheduledRotation(level, pos, newRotation);
            } catch (Throwable t) {
                LogUtils.logDebug("[ROT] applyScheduledRotation failed at " + pos, t);
            }
            return;
        }

            try {
            if (!FrameRegistry.tryRotation(dimId, pos, gameTime)) {
                if (Config.debugLogging) try { LogUtils.logDebug("[ROT] Skipped rotation for {} due to cooldown (gametime={})", pos, gameTime); } catch (Throwable ignored) {}
                return;
            }
        } catch (Throwable ignored) {}

            try {
            int cur = getFrameRotation(level, pos) & 7;
            if (cur == (newRotation & 7)) {
                if (Config.debugLogging) try { LogUtils.logDebug("[ROT] No-op rotation for {} (already {})", pos, cur); } catch (Throwable ignored) {}
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
        try {
            long gameTime = -1L;
            try { gameTime = level != null ? level.getGameTime() : -1L; } catch (Throwable ignored) {}
            try { LogUtils.logDebug("[ROT] applyScheduledRotation pos={} newRot={} mode={} gametime={}", pos, newRotation, Config.rotationMode, gameTime); } catch (Throwable ignored) {}

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
                                    if (Config.debugLogging) try { LogUtils.logDebug("[ROT] Applied rotation on ItemFrame entity at {} => {} (method {})", pos, newRotation, m.getName()); } catch (Throwable ignored) {}
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
                                    if (Config.debugLogging) {
                                        try { LogUtils.logDebug("[ROT] ItemFrame readback at {} => {}", pos, got); } catch (Throwable ex3) {}
                                    }
                                    return;
                                }
                                if (p == byte.class || p == Byte.class) {
                                    m.invoke(f, (byte) newRotation);
                                    if (Config.debugLogging) try { LogUtils.logDebug("[ROT] Applied rotation on ItemFrame entity at {} => {} (method {})", pos, newRotation, m.getName()); } catch (Throwable ignored) {}
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
                                    if (Config.debugLogging) {
                                        try { LogUtils.logDebug("[ROT] ItemFrame readback at {} => {}", pos, got); } catch (Throwable ex3) {}
                                    }
                                    return;
                                }
                            } catch (Throwable t) {
                                if (Config.debugLogging) try { LogUtils.logDebug("[ROT] Failed to invoke setter " + m.getName() + " on ItemFrame " + pos, t); } catch (Throwable ignored) {}
                            }
                        }
                    }
                    try {
                        Field fld = f.getClass().getDeclaredField("rotation");
                        fld.setAccessible(true);
                        fld.setInt(f, newRotation & 7);
                        if (Config.debugLogging) try { LogUtils.logDebug("[ROT] Applied rotation via field on ItemFrame at {} => {}", pos, newRotation & 7); } catch (Throwable ignored) {}
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
                        if (Config.debugLogging) {
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
                        if (Config.debugLogging) try { LogUtils.logDebug("[ROT] Applied rotation on FIF block-entity at {} => {} (readBack={})", pos, newRotation & 7, rb); } catch (Throwable logEx1) {}
                    } catch (Throwable exGet) {
                        if (Config.debugLogging) try { LogUtils.logDebug("[ROT] Applied rotation on FIF block-entity at {} => {} (readBack=?)", pos, newRotation & 7); } catch (Throwable logEx2) {}
                    }
                    return;
                } catch (Throwable t) {
                    if (Config.debugLogging) try { LogUtils.logDebug("[ROT] Failed to apply rotation on FIF block-entity at " + pos, t); } catch (Throwable ignored) {}
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
