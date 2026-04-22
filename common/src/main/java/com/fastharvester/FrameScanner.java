package com.fastharvester;

/**
 * FrameScanner: The intrepid explorer of your blocky world!
 * <p>
 * This class is responsible for scanning farms, finding frames, and making sure your crops get the attention they deserve. It's loader-agnostic, so it works everywhere—like a universal translator, but for farming.
 * </p>
 * <p>
 * Why does this matter? Because without it, your crops would be lost, alone, and unharvested. And nobody wants that.
 * </p>
 * <p>
 * For the full adventure, see TECHNICAL.md (bring snacks).
 * </p>
 */
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

import java.util.Map;
import java.util.HashMap;
import java.util.Comparator;

public class FrameScanner {
    /**
     * The maximum number of frames we dare scan in a single run. Any more and the crops unionize.
     */
    public static final int MAX_FRAMES_PER_RUN = 24;

    /**
     * The maximum number of blocks to check per run. Because even farmers need a break.
     */
    /**
     * Creates a new FrameScanner. Ready to scan for farming greatness!
     */
    public FrameScanner() {}

    /**
     * Anchor: The holy trinity of farm automation—chest, frame, and hoe.
     * <p>
     * This class bundles together the key objects needed to anchor a farm. Treat it with respect (and maybe a little awe).
     * </p>
     */
    public static class Anchor {
        public final Container chest;
        public final BlockPos framePos;
        public final ItemStack hoe;

        public Anchor(Container chest, BlockPos framePos, ItemStack hoe) {
            this.chest = chest;
            this.framePos = framePos;
            this.hoe = hoe;
        }
        @Override
        public String toString() { return "Anchor[pos="+framePos+",hoe="+hoe+"]"; }
    }

    /**
    * Scans for a farm starting from a given anchor. Emits extremely verbose debug logs for every step.
    * @param anchor The anchor (chest, frame, hoe) to start scanning from.
    * @param level The world `Level` to scan in (server-level expected).
    * @return true if a valid farm was found and scanned, false otherwise.
     */
    public boolean scanFarm(Anchor anchor, Level level) {
        Constants.LOG.info("[FastHarvester][SCAN] Starting farm scan from anchor: {}", anchor);
        if (anchor == null || anchor.chest == null || anchor.hoe == null || level == null) {
            Constants.LOG.warn("[FastHarvester][SCAN] Anchor or environment missing, aborting scan.");
            return false;
        }

        int blocksScanned = 0;
        int cropsFound = 0;
        int range = Math.max(1, Config.scanRange);

        BlockPos center = anchor.framePos;
        // First pass: harvest mature crops and fruit blocks (melons/pumpkins)
        for (int dx = -range; dx <= range; dx++) {
            for (int dz = -range; dz <= range; dz++) {
                BlockPos pos = center.offset(dx, 0, dz);
                BlockState state = level.getBlockState(pos);
                blocksScanned++;

                try {
                    Block block = state.getBlock();

                    // Direct fruit blocks (melons/pumpkins): harvest the fruit block itself, no replant.
                    if (state.is(Blocks.MELON) || state.is(Blocks.PUMPKIN)) {
                        cropsFound++;
                        HarvestContext ctx = new HarvestContext(anchor, level, anchor.hoe, anchor.chest, null);
                        java.util.function.Function<BlockState, Boolean> isMatureFn = s -> true;
                        java.util.function.Function<BlockState, BlockState> getReplantFn = s -> null;
                        HarvestUtils.harvestCrop(ctx, pos, state, isMatureFn, getReplantFn);
                        continue;
                    }

                    // If this is a stem, check for adjacent fruit and harvest that instead.
                    if (state.is(Blocks.MELON_STEM) || state.is(Blocks.PUMPKIN_STEM)) {
                        Direction[] dirs = new Direction[]{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
                        boolean harvestedFruit = false;
                        for (Direction d : dirs) {
                            BlockPos npos = pos.relative(d);
                            BlockState ns = level.getBlockState(npos);
                            if ((ns.is(Blocks.MELON) && state.is(Blocks.MELON_STEM)) || (ns.is(Blocks.PUMPKIN) && state.is(Blocks.PUMPKIN_STEM))) {
                                cropsFound++;
                                HarvestContext ctx = new HarvestContext(anchor, level, anchor.hoe, anchor.chest, null);
                                java.util.function.Function<BlockState, Boolean> isMatureFn = s -> true;
                                java.util.function.Function<BlockState, BlockState> getReplantFn = s -> null;
                                HarvestUtils.harvestCrop(ctx, npos, ns, isMatureFn, getReplantFn);
                                harvestedFruit = true;
                                break;
                            }
                        }
                        if (harvestedFruit) continue;
                    }

                    // Standard crop logic
                    boolean isCrop = block instanceof CropBlock || state.is(Blocks.NETHER_WART) || state.is(Blocks.SWEET_BERRY_BUSH);
                    if (!isCrop) continue;

                    // Maturity determination: read AGE defensively; different crops have different max ages.
                    int threshold = (state.is(Blocks.NETHER_WART) || state.is(Blocks.SWEET_BERRY_BUSH)) ? 3 : 7;
                    boolean mature = false;
                    try {
                        int age = state.getValue(CropBlock.AGE);
                        mature = age >= threshold;
                    } catch (Throwable t) {
                        mature = false;
                    }

                    if (!mature) continue;

                    cropsFound++;
                    HarvestContext ctx = new HarvestContext(anchor, level, anchor.hoe, anchor.chest, null);

                    java.util.function.Function<BlockState, Boolean> isMatureFn = s -> {
                        try { int a = s.getValue(CropBlock.AGE); if (s.is(Blocks.NETHER_WART) || s.is(Blocks.SWEET_BERRY_BUSH)) return a >= 3; return a >= 7; } catch (Throwable tt) { return false; }
                    };
                    java.util.function.Function<BlockState, BlockState> getReplantFn = s -> {
                        try {
                            if (s.is(Blocks.SWEET_BERRY_BUSH)) return s.setValue(CropBlock.AGE, 1);
                            return s.setValue(CropBlock.AGE, 0);
                        } catch (Throwable tt) { return null; }
                    };

                    HarvestUtils.harvestCrop(ctx, pos, state, isMatureFn, getReplantFn);
                } catch (Throwable t) {
                    Constants.LOG.debug("[FastHarvester][SCAN] Exception while scanning {}: {}", center, t.toString());
                }
            }
        }

        // Second pass: attempt neighbor-dominant auto-planting on empty farmland above.
        for (int dx = -range; dx <= range; dx++) {
            for (int dz = -range; dz <= range; dz++) {
                BlockPos pos = center.offset(dx, 0, dz);
                BlockState cur = level.getBlockState(pos);
                if (!cur.isAir()) continue;
                BlockState below = level.getBlockState(pos.below());
                if (below == null || below.getBlock() != Blocks.FARMLAND) continue;

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
                if (counts.isEmpty()) continue;
                Block chosen = counts.entrySet().stream().max(Comparator.comparingInt(Map.Entry::getValue)).get().getKey();
                Item seed = seedForBlock(chosen);
                if (seed == null) continue;
                boolean taken = ChestUtils.removeOne(anchor.chest, seed);
                if (!taken) continue;

                BlockState plantState = chosen.defaultBlockState();
                try { if (plantState.getBlock() instanceof CropBlock) plantState = plantState.setValue(CropBlock.AGE, 0); } catch (Throwable t) {}
                level.setBlock(pos, plantState, 3);
            }
        }

        Constants.LOG.info("[FastHarvester][SCAN] Scan complete. Blocks scanned: {}, crops found: {}.", blocksScanned, cropsFound);
        return cropsFound > 0;
    }

    private static Item seedForBlock(Block b) {
        if (b == Blocks.WHEAT) return Items.WHEAT_SEEDS;
        if (b == Blocks.BEETROOTS) return Items.BEETROOT_SEEDS;
        if (b == Blocks.CARROTS) return Items.CARROT;
        if (b == Blocks.POTATOES) return Items.POTATO;
        if (b == Blocks.MELON_STEM) return Items.MELON_SEEDS;
        if (b == Blocks.PUMPKIN_STEM) return Items.PUMPKIN_SEEDS;
        return null;
    }
}
