package com.fastharvester;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.List;
import java.util.Collections;

/**
 * LootLogic: Simple, conservative loot helpers used by the common harvesting code.
 */
public class LootLogic {
    public LootLogic() {}

    /**
     * Legacy helper retained for tests.
     */
    public static int calculateLoot(Object cropType, int fortuneLevel) {
        Constants.LOG.info("[FastHarvester][LOOT] Calculating loot for crop type '{}' with fortune level {}...", cropType, fortuneLevel);
        int baseDrops = 1 + (int)(Math.random() * 2);
        Constants.LOG.debug("[FastHarvester][LOOT] Base drops: {}", baseDrops);
        int bonus = (int)(Math.random() * (fortuneLevel + 1));
        Constants.LOG.debug("[FastHarvester][LOOT] Fortune bonus drops: {}", bonus);
        int total = baseDrops + bonus;
        Constants.LOG.info("[FastHarvester][LOOT] Total drops for crop '{}': {}", cropType, total);
        return total;
    }

    /**
     * Naive block drop resolver used by the common harvest logic. This is intentionally conservative —
     * it returns reasonable defaults for common crop blocks and falls back to the block's item form.
     */
    public static List<ItemStack> getBlockDrops(Level level, BlockPos pos, BlockState state, ItemStack tool) {
        if (state == null) return Collections.emptyList();
        Block block = state.getBlock();
        if (block instanceof CropBlock) {
            // Default to wheat seeds for generic crops; more specific logic can be added later.
            return List.of(new ItemStack(Items.WHEAT_SEEDS));
        }
        if (block == Blocks.NETHER_WART) {
            return List.of(new ItemStack(Items.NETHER_WART));
        }
        // Fall back to the block's item (if any)
        ItemStack asItem = new ItemStack(block.asItem());
        if (!asItem.isEmpty()) return List.of(asItem);
        return Collections.emptyList();
    }
}
