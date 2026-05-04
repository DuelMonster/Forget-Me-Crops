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

/**
 * Small registry for mapping vanilla crop blocks to their seed/replant items.
 * Keeps crop-specific mappings centralized so harvest logic can be consistent.
 */
public final class CropRegistry {
    private CropRegistry() {}

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

    public static ItemStack replantCost(Block block) {
        Item seed = REPLANT_SEED.get(block);
        if (seed != null) return new ItemStack(seed);
        try { if (block != null && block.getClass().getName().toLowerCase(Locale.ROOT).contains("torchflower")) return new ItemStack(block.asItem()); } catch (Throwable ignored) {}
        return ItemStack.EMPTY;
    }

    public static Item clutterSeed(Block block) {
        Item seed = CLUTTER_SEED.get(block);
        if (seed != null) return seed;
        try { if (block != null && block.getClass().getName().toLowerCase(Locale.ROOT).contains("torchflower")) return block.asItem(); } catch (Throwable ignored) {}
        return null;
    }

    public static boolean isSeedAlsoCropFruit(Item seedItem) {
        if (seedItem == null) return false;
        return seedItem == Items.CARROT || seedItem == Items.POTATO || seedItem == Items.NETHER_WART
            || seedItem instanceof BlockItem && ((BlockItem)seedItem).getBlock().getClass().getName().toLowerCase(Locale.ROOT).contains("torchflower");
    }

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
    public static boolean isMelonPumpkinFarmBlock(Block block) {
        if (block == null) return false;
        return block == Blocks.MELON || block == Blocks.PUMPKIN
                || block == Blocks.MELON_STEM || block == Blocks.PUMPKIN_STEM
                || block == Blocks.ATTACHED_MELON_STEM || block == Blocks.ATTACHED_PUMPKIN_STEM;
    }

    public static boolean hasClearFarmlandCropConsensus(Level level, BlockPos pos) {
        return findDominantFarmlandCropIndex(level, pos) >= 0;
    }

    private static int findDominantFarmlandCropIndex(Level level, BlockPos pos) {
        int[] scores = new int[5];
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                try {
                    Block neighbor = level.getBlockState(pos.offset(dx, 0, dz)).getBlock();
                    int idx = indexForFarmlandCrop(neighbor);
                    if (idx >= 0) {
                        int weight = (Math.abs(dx) + Math.abs(dz) == 1) ? 3 : 1;
                        scores[idx] += weight;
                    }
                } catch (Throwable ignored) {}
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

    private static final Map<Block, Integer> FARMLAND_CROP_INDEX = new IdentityHashMap<>();
    static {
        FARMLAND_CROP_INDEX.put(Blocks.WHEAT,     0);
        FARMLAND_CROP_INDEX.put(Blocks.CARROTS,   1);
        FARMLAND_CROP_INDEX.put(Blocks.POTATOES,  2);
        FARMLAND_CROP_INDEX.put(Blocks.BEETROOTS, 3);
    }

    private static int indexForFarmlandCrop(Block block) {
        Integer idx = FARMLAND_CROP_INDEX.get(block);
        if (idx != null) return idx;
        try { if (block != null && block.getClass().getName().toLowerCase(Locale.ROOT).contains("torchflower")) return 4; } catch (Throwable ignored) {}
        return -1;
    }
}
