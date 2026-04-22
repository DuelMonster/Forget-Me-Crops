package com.fastharvester;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantment;
import java.util.List;
import java.util.Collections;
import java.util.Map;
import java.util.ArrayList;
import java.util.Random;

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

        // Let the platform implementation provide accurate drops when possible
        try {
            java.util.List<ItemStack> platformDrops = com.fastharvester.platform.Services.PLATFORM.getBlockDrops(level, pos, state, tool);
            if (platformDrops != null && !platformDrops.isEmpty()) return platformDrops;
        } catch (Throwable t) {
            Constants.LOG.debug("[FastHarvester][LOOT] Platform getBlockDrops failed: {}", t.toString());
        }

        int fortune = 0;
        boolean silk = false;
        try {
            java.util.Map<String, Integer> ench = com.fastharvester.platform.Services.PLATFORM.getEnchantments(tool);
            if (ench != null) {
                for (java.util.Map.Entry<String, Integer> e : ench.entrySet()) {
                    String id = e.getKey();
                    int lvl = (e.getValue() == null) ? 0 : e.getValue();
                    String idl = (id == null) ? "" : id.toLowerCase();
                    if (idl.contains("fortune")) {
                        fortune = Math.max(fortune, lvl);
                    }
                    if (idl.contains("silk")) {
                        silk = true;
                    }
                }
            }
        } catch (Throwable t) {
            Constants.LOG.debug("[FastHarvester][LOOT] Could not read platform enchantments: {}", t.toString());
        }

        // Fallback conservative behavior
        if (block instanceof CropBlock) {
            if (block == Blocks.WHEAT) {
                int wheatCount = 1 + ((level != null) ? level.getRandom().nextInt(fortune + 1) : new java.util.Random().nextInt(fortune + 1));
                int seedsCount = 1 + ((level != null) ? level.getRandom().nextInt(fortune + 1) : new java.util.Random().nextInt(fortune + 1));
                java.util.List<ItemStack> out = new ArrayList<>();
                out.add(new ItemStack(Items.WHEAT, wheatCount));
                out.add(new ItemStack(Items.WHEAT_SEEDS, seedsCount));
                return out;
            }
            if (block == Blocks.BEETROOTS) {
                int beets = 1 + ((level != null) ? level.getRandom().nextInt(fortune + 1) : new java.util.Random().nextInt(fortune + 1));
                java.util.List<ItemStack> out = new ArrayList<>();
                out.add(new ItemStack(Items.BEETROOT, Math.max(1, beets)));
                out.add(new ItemStack(Items.BEETROOT_SEEDS, 1 + ((level != null) ? level.getRandom().nextInt(fortune + 1) : new java.util.Random().nextInt(fortune + 1))));
                return out;
            }
            if (block == Blocks.CARROTS) {
                int carrots = 1 + ((level != null) ? level.getRandom().nextInt(fortune + 1) : new java.util.Random().nextInt(fortune + 1));
                return List.of(new ItemStack(Items.CARROT, carrots));
            }
            if (block == Blocks.POTATOES) {
                int potatoes = 1 + ((level != null) ? level.getRandom().nextInt(fortune + 1) : new java.util.Random().nextInt(fortune + 1));
                return List.of(new ItemStack(Items.POTATO, potatoes));
            }
        }

        if (block == Blocks.NETHER_WART) {
            int count = 1 + ((level != null) ? level.getRandom().nextInt(fortune + 1) : new java.util.Random().nextInt(fortune + 1));
            return List.of(new ItemStack(Items.NETHER_WART, count));
        }

        // Fall back to the block's item (if any)
        ItemStack asItem = new ItemStack(block.asItem());
        if (!asItem.isEmpty()) return List.of(asItem);
        return Collections.emptyList();
    }
}
