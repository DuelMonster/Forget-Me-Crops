package com.fastharvester.harvest;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.BlockItem;
import java.util.Locale;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Small registry for mapping vanilla crop blocks to their seed/replant items.
 * Keeps crop-specific mappings centralized so harvest logic can be consistent.
 */
public final class CropRegistry {
    private CropRegistry() {}

    public static ItemStack replantCost(Block block) {
        if (block == Blocks.BEETROOTS) return new ItemStack(Items.BEETROOT_SEEDS);
        if (block == Blocks.WHEAT) return new ItemStack(Items.WHEAT_SEEDS);
        if (block == Blocks.CARROTS) return new ItemStack(Items.CARROT);
        if (block == Blocks.POTATOES) return new ItemStack(Items.POTATO);
        if (block == Blocks.NETHER_WART) return new ItemStack(Items.NETHER_WART);
        try { if (block.getClass().getName().toLowerCase(Locale.ROOT).contains("torchflower")) return new ItemStack(block.asItem()); } catch (Throwable ignored) {}
        return ItemStack.EMPTY;
    }

    public static Item clutterSeed(Block block) {
        if (block == Blocks.BEETROOTS) return Items.BEETROOT_SEEDS;
        if (block == Blocks.WHEAT) return Items.WHEAT_SEEDS;
        if (block == Blocks.CARROTS) return Items.CARROT;
        if (block == Blocks.POTATOES) return Items.POTATO;
        if (block == Blocks.MELON_STEM) return Items.MELON_SEEDS;
        if (block == Blocks.PUMPKIN_STEM) return Items.PUMPKIN_SEEDS;
        if (block == Blocks.NETHER_WART) return Items.NETHER_WART;
        try { if (block.getClass().getName().toLowerCase(Locale.ROOT).contains("torchflower")) return block.asItem(); } catch (Throwable ignored) {}
        return null;
    }

    public static boolean isSeedAlsoCropFruit(Item seedItem) {
        if (seedItem == null) return false;
        return seedItem == Items.CARROT || seedItem == Items.POTATO || seedItem == Items.NETHER_WART
            || seedItem instanceof BlockItem && ((BlockItem)seedItem).getBlock().getClass().getName().toLowerCase(Locale.ROOT).contains("torchflower");
    }

    public static boolean isCropBlock(net.minecraft.world.level.block.Block b) {
        if (b == null) return false;
        return b == Blocks.WHEAT || b == Blocks.BEETROOTS || b == Blocks.CARROTS || b == Blocks.POTATOES || b == Blocks.MELON_STEM || b == Blocks.PUMPKIN_STEM;
    }

    // --- Farm-consensus helpers (migrated from original CropRouter) ---
    public static boolean isMelonPumpkinFarmBlock(net.minecraft.world.level.block.Block block) {
        if (block == null) return false;
        return block == Blocks.MELON || block == Blocks.PUMPKIN
                || block == Blocks.MELON_STEM || block == Blocks.PUMPKIN_STEM
                || block == Blocks.ATTACHED_MELON_STEM || block == Blocks.ATTACHED_PUMPKIN_STEM;
    }

    public static boolean hasClearFarmlandCropConsensus(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos) {
        return findDominantFarmlandCropIndex(level, pos) >= 0;
    }

    private static int findDominantFarmlandCropIndex(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos) {
        int[] scores = new int[5];
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                try {
                    net.minecraft.world.level.block.Block neighbor = level.getBlockState(pos.offset(dx, 0, dz)).getBlock();
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

    private static int indexForFarmlandCrop(net.minecraft.world.level.block.Block block) {
        if (block == Blocks.WHEAT) return 0;
        if (block == Blocks.CARROTS) return 1;
        if (block == Blocks.POTATOES) return 2;
        if (block == Blocks.BEETROOTS) return 3;
        try {
            if (block != null && block.getClass().getName().toLowerCase(Locale.ROOT).contains("torchflower")) return 4;
        } catch (Throwable ignored) {}
        return -1;
    }
}
