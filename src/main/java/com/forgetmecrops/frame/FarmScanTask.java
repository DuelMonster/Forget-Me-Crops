package com.forgetmecrops.frame;

import com.forgetmecrops.config.Config;
import com.forgetmecrops.enums.RotationMode;
import com.forgetmecrops.frame.FrameScanner.Anchor;
import com.forgetmecrops.harvest.HarvestContext;
import com.forgetmecrops.harvest.HarvestUtils;
import com.forgetmecrops.util.chest.ChestUtils;
import com.forgetmecrops.util.hoe.FrameHoeReplacement;
import com.forgetmecrops.util.log.LogUtils;
import com.forgetmecrops.util.ExceptionHandler;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ItemStack;

// package-private: shared with FrameScanner which defines the canonical constant
import static com.forgetmecrops.frame.FrameScanner.HORIZ_DIRS;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * FarmScanTask: The hardworking farm-scan robot that politely refuses to do all its work in one tick!
 * <p>
 * One instance is created per scan trigger and then advanced a little each server tick
 * (configurable via maxSpiralDurationTicks) until the full spiral is exhausted or the
 * chest fills up. Spreading the work across many ticks prevents any one scan from
 * causing a TPS spike on large farms. Your server's tick rate will appreciate the thoughtfulness.
 * </p>
 * <p>
 * Lifecycle: create → call {@code tick()} each server tick → dispose when it returns true.
 * Simple. Elegant. Like a Roomba, but for crops. And spirals.
 * </p>
 */
class FarmScanTask {
    // --- Core identity: which anchor we're scanning and in which world ---
    final Anchor anchor;
    final Level level;
    final BlockPos center;   // the frame's position — the spiral's origin point
    final HarvestContext ctx; // shared harvest context: holds hoe, chest, and accumulated result flags
    final String dimId;      // dimension identifier for FrameRegistry lookups

    // --- Spiral precomputation data structures ---
    /** Flat ordered list of (pos, direction) steps in spiral order, pre-generated at construction time. */
    final List<SpiralStep> spiralPositions = new ArrayList<>();
    /** Maximum ring radius computed from scanRangeX and scanRangeZ config values. */
    final int computedMaxRing;
    /** Total number of positions in the spiral — pre-calculated so we know when we're done. */
    final int totalPositions;

    // --- Tick-by-tick progress tracking ---
    /** Index into spiralPositions for the next position to process this tick. */
    int currentIndex = 0;
    /** How many positions to process per tick (from Config.maxSpiralDurationTicks / totalPositions). */
    final int positionsPerTick;
    /** Set to true as soon as any harvest occurs; used to trigger rotation animation. */
    boolean anyHarvested = false;
    /** Ring number of the most recently harvested position — for direction tracking. */
    int lastHarvestedRing = -1;
    /** Remaining animation steps for mid-scan visual feedback. */
    int animationStepsRemaining = 0;
    final Map<Integer, List<Integer>> ringFullIndices = new HashMap<>();
    final Map<Integer, Integer> indexToPosInRing = new HashMap<>();
    int lastComputedRotation = -1;
    /** Frame rotation captured at task-start; every rotation cycle begins at current+1 and ends back here. */
    int startRotation = 0;
    int tickCounter = 0;
    int numberOfTicksNeeded = 0;
    /** True once the full post-scan animation has been queued (prevents double-scheduling). */
    boolean fullAnimationScheduled = false;
    int animationInterval = 1;
    int fullAnimationTickCounter = 0;
    /** Ordered sequence of frame-rotation steps for the full end-of-scan animation. */
    final List<Integer> fullAnimationSequence = new ArrayList<>();
    int fullAnimationIndex = 0;

    /**
     * Creates a new FarmScanTask for the given anchor.
     * Pre-generates the full spiral position list and computes per-tick budgets.
     * Also reads the current hoe state directly from the item frame (live snapshot)
     * to ensure we're working with the most up-to-date tool.
     *
     * @param anchor the farm anchor this scan is centered on
     * @param level  the level in which the scan is executing
     * @param dimId  dimension key for FrameRegistry lookups
     */
    FarmScanTask(Anchor anchor, Level level, String dimId) {
        this.anchor = anchor;
        this.level = level;
        this.center = anchor.framePos;
        this.ctx = new HarvestContext(anchor, level, (anchor.hoe == null ? ItemStack.EMPTY : anchor.hoe.copy()), anchor.chest, null);
        try {
            ItemStack frameHoe = FrameScanner.readHoeFromFrame(level, center);
            if (frameHoe != null && !frameHoe.isEmpty() && frameHoe.getItem() instanceof HoeItem) {
                this.ctx.setHoe(frameHoe);
                ExceptionHandler.silentTry(() -> FrameRegistry.updateHoe(dimId, center, this.ctx.getHoe().copy()));
            } else if (this.ctx.getHoe().isEmpty()) {
                try {
                    FrameHoeReplacement.tryReplaceBrokenHoe(this.ctx);
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        this.dimId = dimId;

        int rX = Math.max(1, Config.getScanRangeX());
        int rZ = Math.max(1, Config.getScanRangeZ());
        List<BlockPos> candidates = FrameScanner.bfsDiscoverFarm(center, level, rX, rZ);
        if (candidates.isEmpty()) {
            for (int dx = -rX; dx <= rX; dx++) for (int dz = -rZ; dz <= rZ; dz++) candidates.add(center.offset(dx, 0, dz));
        }

        int maxRing = 0;
        Set<BlockPos> candidateSet = new HashSet<>();
        for (BlockPos p : candidates) {
            int ring = Math.max(Math.abs(p.getX() - center.getX()), Math.abs(p.getZ() - center.getZ()));
            if (ring > Math.max(rX, rZ)) continue;
            maxRing = Math.max(maxRing, ring);
            candidateSet.add(p);
        }
        this.computedMaxRing = maxRing;

        for (SpiralStep step : FrameScanner.generateSpiral(center, rX, rZ)) {
            if (step.pos.equals(center) || candidateSet.contains(step.pos)) spiralPositions.add(step);
        }

        this.totalPositions = spiralPositions.size();

        int ticks = Math.max(1, Config.getMaxSpiralDurationTicks());
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
        this.startRotation = FrameScanner.getFrameRotation(level, center) & 7;
        LogUtils.logDebug("[SCAN] Created FarmScanTask center={} totalPositions={} positionsPerTick={} computedMaxRing={} ticksNeeded={} startRotation={}", center, totalPositions, positionsPerTick, computedMaxRing, numberOfTicksNeeded, startRotation);
    }

    boolean tick() {
        if (ctx.isChestFull()) {
            FrameRegistry.setCooldown(dimId, center, Config.getChestFullCooldownTicks());
            ctx.logSummary();
            return true;
        }

        if (totalPositions == 0) {
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

            // Ensure a hoe is present on the frame at tick start; if removed, try to replace it from chest.
            try {
                ItemStack liveHoe = FrameScanner.readHoeFromFrame(level, center);
                if (liveHoe == null || liveHoe.isEmpty()) {
                    try { LogUtils.logDebug("[SCAN] Hoe removed from frame at {} during scheduled scan; attempting replacement from chest.", center); } catch (Throwable ignLog1) {}
                    try {
                        FrameHoeReplacement.tryReplaceBrokenHoe(ctx);
                        ItemStack replacedHoe = FrameScanner.readHoeFromFrame(level, center);
                        if (replacedHoe == null || replacedHoe.isEmpty()) {
                            try { LogUtils.logDebug("[SCAN] Failed to replace hoe from chest for {} during scheduled scan; aborting.", center); } catch (Throwable ignLog2) {}
                            ctx.logSummary();
                            return true;
                        }
                        try { ctx.setHoe(replacedHoe); } catch (Throwable ignSetHoe) {}
                        try { FrameRegistry.updateHoe(dimId, center, ctx.getHoe().copy()); } catch (Throwable ignUpdateReg) {}
                    } catch (Throwable ignReplace) {
                        try { LogUtils.logDebug("[SCAN] Hoe replacement attempt failed for {} during scheduled scan; aborting.", center); } catch (Throwable ignLog3) {}
                        ctx.logSummary();
                        return true;
                    }
                } else {
                    try { ctx.setHoe(liveHoe); } catch (Throwable ignSet) {}
                    try { FrameRegistry.updateHoe(dimId, center, ctx.getHoe().copy()); } catch (Throwable ignUpd) {}
                }
            } catch (Throwable ignored) {}
        } catch (Throwable t) {
            LogUtils.logDebug("[SCAN] Anchor re-check failed for " + center, t);
        }

        tickCounter++;
        if (animationStepsRemaining > 0) {
            try {
                boolean shouldApply;
                if (!fullAnimationScheduled) {
                    shouldApply = true;
                } else {
                    fullAnimationTickCounter++;
                    shouldApply = (fullAnimationTickCounter % animationInterval == 0);
                }
                if (shouldApply) {
                    if (!fullAnimationSequence.isEmpty() && fullAnimationIndex < fullAnimationSequence.size()) {
                        int next = fullAnimationSequence.get(fullAnimationIndex);
                        try { LogUtils.logDebug("[ROT] Animation step (apply) for {} tick={} next={} idx={} remaining={}", center, tickCounter, next, fullAnimationIndex, animationStepsRemaining); } catch (Throwable ignored) {}
                        FrameScanner.setFrameRotation(level, center, next, true);
                        fullAnimationIndex++;
                        animationStepsRemaining--;
                    } else {
                        if (Config.isDebugLogging()) try { LogUtils.logDebug("[ROT] No animation sequence available for {} (idx={} size={})", center, fullAnimationIndex, fullAnimationSequence.size()); } catch (Throwable ignored) {}
                    }
                }
            } catch (Throwable ignored) {}
        }

        if (fullAnimationScheduled && animationStepsRemaining <= 0) {
            try { FrameRegistry.setAnimating(dimId, center, false); } catch (Throwable ignored) {}
            fullAnimationScheduled = false;
            try { fullAnimationSequence.clear(); } catch (Throwable ignored) {}
            fullAnimationIndex = 0;
            fullAnimationTickCounter = 0;
            try { LogUtils.logDebug("[ROT] Full animation complete for {} — cleared sequence", center); } catch (Throwable ignored) {}
        }

        // Refresh startRotation at the start of each new cycle so a manual player rotation is honoured.
        if (currentIndex == 0) {
            startRotation = FrameScanner.getFrameRotation(level, center) & 7;
            try { LogUtils.logDebug("[ROT] startRotation refreshed for {} => {}", center, startRotation); } catch (Throwable ignored) {}
        }

        // FULL_ROTATION should begin with the scan progression, not only after a mature crop is found.
        if (Config.getRotationMode() == RotationMode.FULL_ROTATION && currentIndex == 0 && !fullAnimationScheduled) {
            startFullRotationAnimation();
        }

        int endIndex = Math.min(totalPositions - 1, currentIndex + positionsPerTick - 1);

        // FOLLOW_ROTATION: rotate once per tick, but map rotation to the current
        // ring position so each outward ring can perform its own 8-step cycle.
        // This preserves the "multiple rotations while spiraling outward" behavior
        // without flooding the per-tick pending-rotation map.
        if (Config.getRotationMode() == RotationMode.FOLLOW_ROTATION) {
            if (endIndex >= 0 && endIndex < spiralPositions.size()) {
                BlockPos p = spiralPositions.get(endIndex).pos;
                int ring = Math.max(Math.abs(p.getX() - center.getX()), Math.abs(p.getZ() - center.getZ()));
                Integer posInRing = indexToPosInRing.get(endIndex);
                List<Integer> full = ringFullIndices.get(ring);
                if (posInRing != null && full != null && !full.isEmpty()) {
                    int ringSize = full.size();
                    int ringOffset = ringSize <= 1
                            ? 0
                            : (int) Math.floor((double) posInRing * 7.0 / (double) (ringSize - 1));
                    int rot = (startRotation + 1 + ringOffset) & 7;
                    if (rot != lastComputedRotation) {
                        FrameScanner.setFrameRotation(level, center, rot, true);
                        lastComputedRotation = rot;
                    }
                }
            }
        }

        int beforeHarvest = ctx.getHarvestedCount();
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
                try {
                    BlockState state = level.getBlockState(pos);
                    ctx.incrementBlocksScanned();
                    if (shouldEmitTrailParticles(level, pos, state)) {
                        try { HarvestUtils.emitSpiralTrailParticles(level, pos); } catch (Throwable ignored) {}
                    }

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
                        harvested = ctx.getHarvestedCount() > beforeHarvest;
                    } else if (state.is(Blocks.MELON_STEM) || state.is(Blocks.PUMPKIN_STEM)) {
                        boolean harvestedFruit = false;
                        for (Direction d : HORIZ_DIRS) {
                            BlockPos npos = pos.relative(d);
                            BlockState ns = level.getBlockState(npos);
                            if ((ns.is(Blocks.MELON) && state.is(Blocks.MELON_STEM)) || (ns.is(Blocks.PUMPKIN) && state.is(Blocks.PUMPKIN_STEM))) {
                                HarvestUtils.harvestCrop(ctx, npos, ns, s -> true, s -> null);
                                harvestedFruit = ctx.getHarvestedCount() > beforeHarvest;
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
                                harvested = ctx.getHarvestedCount() > beforeHarvest;
                            }
                        }
                    }
                    if (harvested) ringHarvested = true;
                    if (harvested) { anyHarvested = true; lastHarvestedRing = ring; }

                    try { FrameScanner.tryAutoPlantAndTill(anchor, ctx, pos, level); } catch (Throwable ignored) {}

                } catch (Throwable t) {
                    LogUtils.logDebug("[SCAN] Exception while scanning " + center, t);
                }

                if (ctx.isChestFull()) {
                    FrameRegistry.setCooldown(dimId, center, Config.getChestFullCooldownTicks());
                    ctx.logSummary();
                    return true;
                }
            }
            if (ringHarvested) {
                switch (Config.getRotationMode()) {
                    case SINGLE_STEP -> {
                    }
                    case FULL_ROTATION -> {
                    }
                    case FOLLOW_ROTATION -> {
                    }
                }
            }
        }

        currentIndex = endIndex + 1;

        if (currentIndex >= totalPositions) {
            if (ctx.isChestFull()) {
                FrameRegistry.setCooldown(dimId, center, Config.getChestFullCooldownTicks());
                ctx.logSummary();
                return true;
            }

            if (fullAnimationScheduled && animationStepsRemaining > 0) {
                return false;
            }

            boolean shouldFinalizeRotation = (anyHarvested && lastHarvestedRing >= 0)
                    || (Config.getRotationMode() == RotationMode.FOLLOW_ROTATION && lastComputedRotation >= 0);
            if (shouldFinalizeRotation) {
                int newRotation = 0;
                switch (Config.getRotationMode()) {
                    case SINGLE_STEP -> {
                        newRotation = (FrameScanner.getFrameRotation(level, center) + 1) & 7;
                        FrameScanner.setFrameRotation(level, center, newRotation);
                    }
                    case FULL_ROTATION -> {
                        if (!fullAnimationScheduled) {
                            // Return to starting position to complete the cycle.
                            newRotation = startRotation;
                            FrameScanner.setFrameRotation(level, center, newRotation);
                        }
                    }
                    case FOLLOW_ROTATION -> {
                        // Always land back at starting position to complete the cycle,
                        // including no-harvest scan passes where FOLLOW still animated.
                        newRotation = startRotation;
                        FrameScanner.setFrameRotation(level, center, newRotation, true);
                    }
                }
            }

            ctx.logSummary();
            return true;
        }

        return false;
    }

    private static boolean shouldEmitTrailParticles(Level level, BlockPos pos, BlockState state) {
        if (level == null || pos == null || state == null) return false;

        if (state.getBlock() instanceof CropBlock
                || state.is(Blocks.NETHER_WART)
                || state.is(Blocks.SWEET_BERRY_BUSH)
                || state.is(Blocks.MELON)
                || state.is(Blocks.PUMPKIN)
                || state.is(Blocks.MELON_STEM)
                || state.is(Blocks.PUMPKIN_STEM)) {
            return true;
        }

        // Show trail on prepared-but-empty farm substrate, but not on dirt/grass traversal links.
        if (state.isAir()) {
            Block below = level.getBlockState(pos.below()).getBlock();
            return below == Blocks.FARMLAND || below == Blocks.SOUL_SAND;
        }

        return state.is(Blocks.FARMLAND) || state.is(Blocks.SOUL_SAND);
    }

    private void startFullRotationAnimation() {
        fullAnimationScheduled = true;
        fullAnimationTickCounter = 0;
        // Prepare a deterministic 8-step rotation sequence starting from current rotation.
        try {
            int start = startRotation;
            fullAnimationSequence.clear();
            for (int s = 1; s <= 8; s++) fullAnimationSequence.add((start + s) & 7);
            fullAnimationIndex = 0;
            animationStepsRemaining = fullAnimationSequence.size();
            int remainingScanTicks = Math.max(1, numberOfTicksNeeded - tickCounter + 1);
            animationInterval = Math.max(1, (int) Math.ceil((double) remainingScanTicks / (double) fullAnimationSequence.size()));

            // Apply the first step immediately so animation starts with the active spiral.
            if (animationStepsRemaining > 0 && !fullAnimationSequence.isEmpty()) {
                int first = fullAnimationSequence.get(fullAnimationIndex);
                FrameScanner.setFrameRotation(level, center, first, true);
                fullAnimationIndex++;
                animationStepsRemaining--;
            }
            try { LogUtils.logDebug("[ROT] Prepared full animation sequence for {} start={} seq={}", center, start, fullAnimationSequence); } catch (Throwable ignored) {}
        } catch (Throwable ignored) {
            animationStepsRemaining = 8;
            int remainingScanTicks = Math.max(1, numberOfTicksNeeded - tickCounter + 1);
            animationInterval = Math.max(1, (int) Math.ceil((double) remainingScanTicks / 8.0));
        }
        try { FrameRegistry.setAnimating(dimId, center, true); } catch (Throwable ignored) {}
    }
}

