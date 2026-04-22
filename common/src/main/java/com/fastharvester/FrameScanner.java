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
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.ItemStack;

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
        for (int dx = -range; dx <= range; dx++) {
            for (int dz = -range; dz <= range; dz++) {
                BlockPos pos = center.offset(dx, 0, dz);
                BlockState state = level.getBlockState(pos);
                blocksScanned++;

                try {
                    boolean isCrop = state.getBlock() instanceof CropBlock || state.is(Blocks.NETHER_WART) || state.is(Blocks.SWEET_BERRY_BUSH);
                    if (!isCrop) continue;

                    // Simple maturity test: crops use an AGE property; many crops reach age 7, nether wart and berries reach 3.
                    boolean mature = false;
                    try {
                        int age = state.getValue(CropBlock.AGE);
                        int threshold = 7;
                        if (state.is(Blocks.NETHER_WART) || state.is(Blocks.SWEET_BERRY_BUSH)) threshold = 3;
                        mature = age >= threshold;
                    } catch (Throwable t) {
                        // best-effort fallback: if reflection failed assume mature=false
                        mature = false;
                    }

                    if (!mature) continue;

                    cropsFound++;
                    HarvestContext ctx = new HarvestContext(anchor, level, anchor.hoe, anchor.chest, null);

                    // Provide a simple isMature and getReplantState functions for HarvestUtils
                    java.util.function.Function<BlockState, Boolean> isMatureFn = s -> {
                        try { int a = s.getValue(CropBlock.AGE); if (s.is(Blocks.NETHER_WART) || s.is(Blocks.SWEET_BERRY_BUSH)) return a >= 3; return a >= 7; } catch (Throwable tt) { return true; }
                    };
                    java.util.function.Function<BlockState, BlockState> getReplantFn = s -> {
                        try { return s.setValue(CropBlock.AGE, 0); } catch (Throwable tt) { return null; }
                    };

                    HarvestUtils.harvestCrop(ctx, pos, state, isMatureFn, getReplantFn);
                } catch (Throwable t) {
                    Constants.LOG.debug("[FastHarvester][SCAN] Exception while scanning {}: {}", center, t.toString());
                }
            }
        }

        Constants.LOG.info("[FastHarvester][SCAN] Scan complete. Blocks scanned: {}, crops found: {}.", blocksScanned, cropsFound);
        return cropsFound > 0;
    }
}
