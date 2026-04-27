package com.fastharvester.harvest;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.BlockItem;
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
        try { if (block.getClass().getName().toLowerCase().contains("torchflower")) return new ItemStack(block.asItem()); } catch (Throwable ignored) {}
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
        try { if (block.getClass().getName().toLowerCase().contains("torchflower")) return block.asItem(); } catch (Throwable ignored) {}
        return null;
    }

    public static boolean isSeedAlsoCropFruit(Item seedItem) {
        if (seedItem == null) return false;
        return seedItem == Items.CARROT || seedItem == Items.POTATO || seedItem == Items.NETHER_WART
                || seedItem instanceof BlockItem && ((BlockItem)seedItem).getBlock().getClass().getName().toLowerCase().contains("torchflower");
    }

    public static boolean isCropBlock(net.minecraft.world.level.block.Block b) {
        if (b == null) return false;
        return b == Blocks.WHEAT || b == Blocks.BEETROOTS || b == Blocks.CARROTS || b == Blocks.POTATOES || b == Blocks.MELON_STEM || b == Blocks.PUMPKIN_STEM;
    }
}
