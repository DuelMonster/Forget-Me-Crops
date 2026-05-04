package com.forgetmecrops.frame;

import com.forgetmecrops.config.Config;
import com.forgetmecrops.enums.RotationMode;
import com.forgetmecrops.frame.FrameScanner.Anchor;
import com.forgetmecrops.harvest.CropRegistry;
import com.forgetmecrops.harvest.HarvestContext;
import com.forgetmecrops.harvest.HarvestUtils;
import com.forgetmecrops.util.chest.ChestUtils;
import com.forgetmecrops.util.durability.DurabilityLogic;
import com.forgetmecrops.util.hoe.FrameHoeReplacement;
import com.forgetmecrops.util.log.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Incremental tick-based farm scanner. One instance is created per scan trigger
 * and advanced each server tick until the spiral is exhausted.
 */
class FarmScanTask {
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
            ItemStack frameHoe = FrameScanner.readHoeFromFrame(level, center);
            if (frameHoe != null && !frameHoe.isEmpty() && frameHoe.getItem() instanceof HoeItem) {
                this.ctx.hoe = frameHoe.copy();
                try { FrameRegistry.updateHoe(dimId, center, this.ctx.hoe.copy()); } catch (Throwable ignored) {}
            } else if (this.ctx.hoe == null || this.ctx.hoe.isEmpty()) {
                try {
                    FrameHoeReplacement.tryReplaceBrokenHoe(this.ctx);
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        this.dimId = dimId;

        int rX = Math.max(1, Config.scanRangeX);
        int rZ = Math.max(1, Config.scanRangeZ);
        List<BlockPos> candidates = FrameScanner.bfsDiscoverFarm(center, level, rX, rZ);
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

        for (SpiralStep step : FrameScanner.generateSpiral(center, rX, rZ)) {
            if (step.pos.equals(center) || candidateSet.contains(step.pos)) spiralPositions.add(step);
        }

        // Pre-scan repair pass: repair nearby dirt/grass into farmland across the whole scan rectangle
        try {
            for (int dx = -rX; dx <= rX; dx++) {
                for (int dz = -rZ; dz <= rZ; dz++) {
                    BlockPos pos = center.offset(dx, 0, dz);
                    try {
                        BlockState s2 = level.getBlockState(pos);
                        if (s2 == null || s2.isAir()) {
                            FrameScanner.tryAutoPlantAndTill(anchor, this.ctx, pos, level);
                        }
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}

        // Quick pre-scan: determine whether any spiral position contains a mature crop or harvestable fruit.
        boolean foundMature = false;
        for (SpiralStep step : spiralPositions) {
            BlockPos p = step.pos;
            try {
                BlockState s = level.getBlockState(p);
                    // If the spot is empty, attempt farmland repair (till dirt/grass where nearby farmland exists)
                    if (s == null || s.isAir()) {
                        try { FrameScanner.tryAutoPlantAndTill(anchor, this.ctx, p, level); } catch (Throwable ignored) {}
                        continue;
                    }
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
                        int threshold = FrameScanner.getMaturityThreshold(s);
                        int age = FrameScanner.getAgeSafe(s);
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
            if (!FrameScanner.isFrameStillPresent(level, center)) {
                try { LogUtils.logWarn("[SCAN] Anchor frame missing at {} during scheduled scan; unregistering.", center); } catch (Throwable ignored) {}
                try { FrameRegistry.unregisterFrame(dimId, center); } catch (Throwable ignored) {}
                ctx.logSummary();
                return true;
            }
            if (!FrameScanner.isChestStillValid(level, anchor)) {
                try { LogUtils.logWarn("[SCAN] Anchor chest missing/changed for {} during scheduled scan; unregistering.", center); } catch (Throwable ignored) {}
                try { FrameRegistry.unregisterFrame(dimId, center); } catch (Throwable ignored) {}
                ctx.logSummary();
                return true;
            }

            // Ensure a hoe is present on the frame at tick start; abort the scan if removed.
            try {
                ItemStack liveHoe = FrameScanner.readHoeFromFrame(level, center);
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
                        FrameScanner.setFrameRotation(level, center, next, true);
                        fullAnimationIndex++;
                        animationStepsRemaining--;
                    } else {
                        if (Config.debugLogging) try { LogUtils.logDebug("[ROT] No animation sequence available for {} (idx={} size={})", center, fullAnimationIndex, fullAnimationSequence.size()); } catch (Throwable ignored) {}
                    }
                }
            } catch (Throwable ignored) {}
        }

        if (fullAnimationScheduled && animationStepsRemaining <= 0) {
            try { FrameRegistry.setAnimating(dimId, center, false); } catch (Throwable ignored) {}
            fullAnimationScheduled = false;
            try { fullAnimationSequence.clear(); } catch (Throwable ignored) {}
            fullAnimationIndex = 0;
            try { LogUtils.logDebug("[ROT] Full animation complete for {} — cleared sequence", center); } catch (Throwable ignored) {}
        }

        int endIndex = Math.min(totalPositions - 1, currentIndex + positionsPerTick - 1);

        int beforeHarvest = ctx.harvestedCount;
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
                        int ageDbg = FrameScanner.getAgeSafe(state);
                        int thresholdDbg = FrameScanner.getMaturityThreshold(state);
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
                            int threshold = FrameScanner.getMaturityThreshold(state);
                            int age = FrameScanner.getAgeSafe(state);
                            boolean mature = age >= threshold;
                            if (mature) {
                                HarvestUtils.harvestCrop(ctx, pos, state,
                                        s -> {
                                            int a = FrameScanner.getAgeSafe(s);
                                            if (a < 0) return false;
                                            return a >= FrameScanner.getMaturityThreshold(s);
                                        },
                                        s -> {
                                            try {
                                                if (s.is(Blocks.SWEET_BERRY_BUSH)) return FrameScanner.setAgeSafe(s, 1);
                                                return FrameScanner.setAgeSafe(s, 0);
                                            } catch (Throwable tt) { return null; }
                                        });
                                harvested = ctx.harvestedCount > beforeHarvest;
                            }
                        }
                    }
                    if (harvested) ringHarvested = true;
                    if (harvested) { anyHarvested = true; lastHarvestedRing = ring; }

                    if (Config.rotationMode == RotationMode.FOLLOW_HARVEST_SPIRAL) {
                        Integer posInRing = indexToPosInRing.get(idx);
                        List<Integer> full = ringFullIndices.get(ring);
                        if (posInRing != null && full != null && !full.isEmpty()) {
                            int ringSize = full.size();
                            int rot = (int) Math.floor((double) posInRing * 8.0 / (double) ringSize) & 7;
                            if (rot != lastComputedRotation) {
                                FrameScanner.setFrameRotation(level, center, rot);
                                lastComputedRotation = rot;
                            }
                        } else {
                            if (lastDirection == null || !lastDirection.equals(curDir)) {
                                FrameScanner.setFrameRotation(level, center, FrameScanner.dirToRotation(curDir));
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
                switch (Config.rotationMode) {
                    case STEP_PER_HARVEST -> {
                    }
                    case FULL_ROTATION_PER_HARVEST -> {
                        if (!fullAnimationScheduled) {
                            fullAnimationScheduled = true;
                            tickCounter = 0;
                            // Prepare a deterministic 8-step rotation sequence starting from current rotation
                            try {
                                int start = FrameScanner.getFrameRotation(level, center) & 7;
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
                            try { FrameRegistry.setAnimating(dimId, center, true); } catch (Throwable ignored) {}
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
                                Block rep = CropRegistry.canonicalCropBlock(b);
                                if (rep != null && CropRegistry.isCropBlock(rep)) {
                                    Integer prev = counts.get(rep);
                                    if (prev == null) counts.put(rep, 1);
                                    else counts.put(rep, prev + 1);
                                }
                            }
                            if (!counts.isEmpty()) {
                                Block chosen = counts.entrySet().stream().max(Comparator.comparingInt(Map.Entry::getValue)).get().getKey();
                                Item seed = CropRegistry.clutterSeed(chosen);
                                if (seed != null && ChestUtils.removeOne(anchor.chest, seed, false)) {
                                    BlockState plantState = chosen.defaultBlockState();
                                    try { if (plantState.getBlock() instanceof CropBlock) plantState = FrameScanner.setAgeSafe(plantState, 0); } catch (Throwable t) {}
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
                                if (seed != null && ChestUtils.removeOne(anchor.chest, seed, false)) {
                                    BlockState plantState = chosen.defaultBlockState();
                                    try { if (plantState.getBlock() instanceof CropBlock) plantState = FrameScanner.setAgeSafe(plantState, 0); } catch (Throwable t) {}
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
                            BlockState farmland = Blocks.FARMLAND.defaultBlockState();
                            level.setBlock(belowPos, farmland, 3);
                            ItemStack before = (ctx.hoe == null || ctx.hoe.isEmpty()) ? (anchor.hoe == null ? ItemStack.EMPTY : anchor.hoe.copy()) : ctx.hoe.copy();
                            try {
                                if (ctx.skipNextDamage) {
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
                                Block rep = CropRegistry.canonicalCropBlock(b);
                                if (rep != null && CropRegistry.isCropBlock(rep)) {
                                    Integer prev = counts2.get(rep);
                                    if (prev == null) counts2.put(rep, 1);
                                    else counts2.put(rep, prev + 1);
                                }
                            }
                            if (!counts2.isEmpty()) {
                                Block chosen2 = counts2.entrySet().stream().max(Comparator.comparingInt(Map.Entry::getValue)).get().getKey();
                                Item seed2 = CropRegistry.clutterSeed(chosen2);
                                if (seed2 != null && ChestUtils.removeOne(anchor.chest, seed2, false)) {
                                    BlockState plantState2 = chosen2.defaultBlockState();
                                    try { if (plantState2.getBlock() instanceof CropBlock) plantState2 = FrameScanner.setAgeSafe(plantState2, 0); } catch (Throwable t) {}
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
                        newRotation = (FrameScanner.getFrameRotation(level, center) + 1) & 7;
                        FrameScanner.setFrameRotation(level, center, newRotation);
                    }
                    case FULL_ROTATION_PER_HARVEST -> {
                        if (!fullAnimationScheduled) {
                            int steps = computedMaxRing > 0 ? (int) Math.floor((double)(lastHarvestedRing + 1) * 8.0 / (computedMaxRing + 1)) : 0;
                            newRotation = steps & 7;
                            FrameScanner.setFrameRotation(level, center, newRotation);
                        }
                    }
                    case FOLLOW_HARVEST_SPIRAL -> {
                        List<Integer> full = ringFullIndices.get(lastHarvestedRing);
                        if (full != null && !full.isEmpty()) {
                            int posIdx = Math.max(0, full.size() - 1);
                            int rot = (int) Math.floor((double) posIdx * 8.0 / (double) full.size()) & 7;
                            newRotation = rot;
                            FrameScanner.setFrameRotation(level, center, newRotation);
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
