package com.forgetmecrops.harvest;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.BlockPos;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;
import com.forgetmecrops.util.ExceptionHandler;

/**
 * CropRegistry: The agricultural authority on which crops want which seeds!
 * <p>
 * Maintains IdentityHashMap-based mappings from crop blocks to their replant seed items
 * and "clutter" seed items (for the REDUCED/NONE seed management policies). Also provides
 * helpers for canonical crop classification, melon/pumpkin farm detection, and farmland
 * neighbor consensus used to decide whether a specific position is on a known farm type.
 * </p>
 * <p>
 * Also handles Torchflower support via class-name heuristics, because Torchflower crops
 * don't always have a clean static reference we can use. We respect their individuality.
 * </p>
 */
public final class CropRegistry {
    // Utility class. The crop registry does not grow crops; it merely knows about them.
    private CropRegistry() {}

    /**
     * Returns true if the block's class name contains "torchflower" — a heuristic for detecting
     * Torchflower crop blocks that may not be easily referenced by a static Blocks.TORCHFLOWER_CROP field.
     * Best-effort; if the block doesn't self-identify, we shrug and move on.
     */
    private static boolean isTorchflowerBlock(Block block) {
        return block != null && block.getClass().getName().toLowerCase(Locale.ROOT).contains("torchflower");
    }

    /** Block -> seed item used for replanting (one seed consumed per harvest). */
    private static final Map<Block, Item> REPLANT_SEED = new IdentityHashMap<>();
    /** Block -> seed/fruit item shown in the clutter-filter UI. Superset of REPLANT_SEED. */
    private static final Map<Block, Item> CLUTTER_SEED = new IdentityHashMap<>();

    static {
        REPLANT_SEED.put(Blocks.BEETROOTS,  Items.BEETROOT_SEEDS);
        REPLANT_SEED.put(Blocks.WHEAT,      Items.WHEAT_SEEDS);
        REPLANT_SEED.put(Blocks.CARROTS,    Items.CARROT);
        REPLANT_SEED.put(Blocks.POTATOES,   Items.POTATO);
        REPLANT_SEED.put(Blocks.NETHER_WART, Items.NETHER_WART);

        CLUTTER_SEED.putAll(REPLANT_SEED);
        CLUTTER_SEED.put(Blocks.MELON,         Items.MELON_SEEDS);
        CLUTTER_SEED.put(Blocks.PUMPKIN,       Items.PUMPKIN_SEEDS);
        CLUTTER_SEED.put(Blocks.MELON_STEM,    Items.MELON_SEEDS);
        CLUTTER_SEED.put(Blocks.PUMPKIN_STEM,  Items.PUMPKIN_SEEDS);
    }

    /**
     * Returns an ItemStack of the appropriate seed/fruit item for replanting the given crop block.
     * Returns {@link ItemStack#EMPTY} for crops that don't need a manual seed (e.g. melon/pumpkin stems,
     * whose replant is handled by the stem regrowth mechanic, not by us).
     *
     * @param block the harvested crop block
     * @return a single seed/fruit stack for replanting, or empty if none needed
     */
    public static ItemStack replantCost(Block block) {
        Item seed = REPLANT_SEED.get(block);
        if (seed != null) return new ItemStack(seed);
        ItemStack torchflower = ExceptionHandler.silentTry(
            () -> isTorchflowerBlock(block) ? new ItemStack(block.asItem()) : null,
            null
        );
        return torchflower != null ? torchflower : ItemStack.EMPTY;
    }

    /**
     * Returns the "clutter" seed item associated with this block — the item tracked
     * for seed reserve and clutter filtering purposes. Includes melon/pumpkin seeds
     * that aren't replant costs but still accumulate in the chest over time.
     *
     * @param block the crop block to look up
     * @return the seed/fruit item for this block, or null if not recognized
     */
    public static Item clutterSeed(Block block) {
        Item seed = CLUTTER_SEED.get(block);
        if (seed != null) return seed;
        return ExceptionHandler.silentTry(
            () -> isTorchflowerBlock(block) ? block.asItem() : null,
            null
        );
    }

    /**
     * Returns true if the given seed item is also the crop's harvestable fruit (carrot, potato,
     * Nether Wart, Torchflower). These items are both the seed AND the drop, so
     * seed-reserve logic applies differently — we can't freely discard them.
     *
     * @param seedItem the item to check
     * @return true if the item is both seed and fruit for its crop type
     */
    public static boolean isSeedAlsoCropFruit(Item seedItem) {
        if (seedItem == null) return false;
        return seedItem == Items.CARROT || seedItem == Items.POTATO || seedItem == Items.NETHER_WART
            || seedItem instanceof BlockItem && isTorchflowerBlock(((BlockItem)seedItem).getBlock());
    }

    /**
     * Returns true if the block is a standard tillable-soil crop block.
     * Excludes melon/pumpkin fruit blocks (which get harvested but not replanted directly)
     * and Nether Wart (which is special-cased elsewhere). Just the main gang.
     *
     * @param b the block to classify
     * @return true if it's wheat, beetroot, carrot, potato, melon stem, or pumpkin stem
     */
    public static boolean isCropBlock(Block b) {
        if (b == null) return false;
        return b == Blocks.WHEAT || b == Blocks.BEETROOTS || b == Blocks.CARROTS || b == Blocks.POTATOES || b == Blocks.MELON_STEM || b == Blocks.PUMPKIN_STEM;
    }

    /**
     * Return a canonical crop-block representative for counting/consensus.
     * Maps fruit blocks (melon/pumpkin) to their stem form so neighbour
     * consensus and seed lookup behave consistently.
     */
    public static Block canonicalCropBlock(Block b) {
        if (b == null) return null;
        if (b == Blocks.MELON) return Blocks.MELON_STEM;
        if (b == Blocks.PUMPKIN) return Blocks.PUMPKIN_STEM;
        if (b == Blocks.ATTACHED_MELON_STEM) return Blocks.MELON_STEM;
        if (b == Blocks.ATTACHED_PUMPKIN_STEM) return Blocks.PUMPKIN_STEM;
        return b;
    }

    // --- Farm-consensus helpers (migrated from original CropRouter) ---

    /**
     * Returns true if the block is any melon or pumpkin farm block variant — including
     * detached/attached stems and fruit blocks. Used for detecting melon/pumpkin farm areas
     * in FrameDiscovery's nearby-crop scan.
     */
    public static boolean isMelonPumpkinFarmBlock(Block block) {
        if (block == null) return false;
        return block == Blocks.MELON || block == Blocks.PUMPKIN
                || block == Blocks.MELON_STEM || block == Blocks.PUMPKIN_STEM
                || block == Blocks.ATTACHED_MELON_STEM || block == Blocks.ATTACHED_PUMPKIN_STEM;
    }

    /**
     * Returns true if the given farmland position has a clear dominant crop type among its neighbors.
     * Used in FrameDiscovery to confirm the anchor is above an actual single-crop farm, not
     * a chaotic mixed-crop disaster or empty dirt.
     *
     * @param level the server level (for block lookups)
     * @param pos   the farmland position to check
     * @return true if one crop type dominates the neighborhood with sufficient confidence
     */
    public static boolean hasClearFarmlandCropConsensus(Level level, BlockPos pos) {
        return findDominantFarmlandCropIndex(level, pos) >= 0;
    }

    /**
     * Scores each recognized crop type by weighted neighbor-count and returns the index
     * of the dominant one. Cardinal neighbors score 3x diagonal neighbors (they're more significant).
     * Returns -1 if no clear winner, score is below threshold, or there's a tie —
     * all of which indicate an ambiguous or empty farm area.
     *
     * @param level the level for neighbor lookups
     * @param pos   the center farmland position
     * @return 0–4 for a recognized crop type, -1 if ambiguous
     */
    private static int findDominantFarmlandCropIndex(Level level, BlockPos pos) {
        int[] scores = new int[5];
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                ExceptionHandler.silentTry(() -> {
                    Block neighbor = level.getBlockState(pos.offset(dx, 0, dz)).getBlock();
                    int idx = indexForFarmlandCrop(neighbor);
                    if (idx >= 0) {
                        int weight = (Math.abs(dx) + Math.abs(dz) == 1) ? 3 : 1;
                        scores[idx] += weight;
                    }
                });
            }
        }
        int bestIdx = -1;
        int bestScore = 0;
        boolean tie = false;
        for (int i = 0; i < scores.length; i++) {
            if (scores[i] > bestScore) {
                bestScore = scores[i];
                bestIdx = i;
                tie = false;
            } else if (scores[i] > 0 && scores[i] == bestScore) {
                tie = true;
            }
        }
        if (bestScore < 3 || tie) return -1;
        return bestIdx;
    }

    // Lookup table: block -> consensus index (0=wheat, 1=carrots, 2=potatoes, 3=beetroots, 4=torchflower)
    private static final Map<Block, Integer> FARMLAND_CROP_INDEX = new IdentityHashMap<>();
    static {
        FARMLAND_CROP_INDEX.put(Blocks.WHEAT,     0);
        FARMLAND_CROP_INDEX.put(Blocks.CARROTS,   1);
        FARMLAND_CROP_INDEX.put(Blocks.POTATOES,  2);
        FARMLAND_CROP_INDEX.put(Blocks.BEETROOTS, 3);
    }

    /** Returns the crop index for the given block, or -1 if not a recognized farmland crop. */
    private static int indexForFarmlandCrop(Block block) {
        Integer idx = FARMLAND_CROP_INDEX.get(block);
        if (idx != null) return idx;
        return ExceptionHandler.silentTry(
            () -> isTorchflowerBlock(block) ? 4 : -1,
            -1
        );
    }
}
