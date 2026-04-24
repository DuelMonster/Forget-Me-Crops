

package com.fastharvester;

// 🌾 HarvestUtils: hands-on harvesting logic. It plucks, sifts, and occasionally judges wasted seeds.
// Why it matters: this is where the magic (and occasional mayhem) of replanting happens.

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.Container;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import java.util.List;

/**
 * HarvestUtils: The Swiss Army knife for all things harvest!
 * <p>
 * This class is meant to be filled with handy methods for harvesting crops, no matter the loader. If you need a trick up your sleeve, look here.
 * </p>
 * <p>
 * Why does this matter? Because every great farmer has a few secrets, and this is where you keep them.
 * </p>
 */
public class HarvestUtils {
    /**
     * Harvests a crop at the given position. Emits extremely verbose debug logs for every step.
     * @param cropPos The position of the crop (platform-specific, passed as Object for loader-agnostic code).
     * @param world The world object (platform-specific, passed as Object).
     * @param player The player or automation entity (platform-specific, passed as Object).
     * @return true if the crop was harvested, false otherwise.
     */
    /**
     * Harvests a crop at the given position, puts all drops into the chest, and replants if possible.
     * This is the real, chest-based, server-side automation logic per the original design.
     * @param ctx The harvest context (must contain level, hoe, chest, etc.)
     * @param pos The block position
     * @param state The block state
     * @param isMature Function to check if the crop is ready for harvest
     * @param getReplantState Function to get the state for replanting
     */
    public static void harvestCrop(HarvestContext ctx, BlockPos pos, BlockState state, Function<BlockState, Boolean> isMature, Function<BlockState, BlockState> getReplantState) {
        if (ctx == null || ctx.level == null || ctx.hoe == null || ctx.hoe.isEmpty() || ctx.chest == null) return;

        Block block = state.getBlock();
        if (!isMature.apply(state)) return;

        if (!ChestUtils.hasSpace(ctx.chest)) {
            ctx.chestFull = true;
            return;
        }

        List<ItemStack> drops = LootLogic.getBlockDrops(ctx.level, pos, state, ctx.hoe);
        if (drops.isEmpty()) return;

        // Don't let seeds take over your chest like rabbits.
        applySeedClutterPolicy(drops, clutterSeedItemFor(block), ctx.chest);

        // Insert all drops into the chest first so we can respect reserve policy
        ChestUtils.insertAll(ctx.chest, drops);

        ItemStack hoeBeforeDamage = ctx.hoe.copy();
        DurabilityLogic.applyDamage(ctx.level, ctx.hoe, ctx.level.getRandom());
        if (ctx.hoe.isEmpty()) {
            // RIP, brave hoe. You served us well.
            handleBrokenHoe(ctx, hoeBeforeDamage);
        } else {
            syncFrameHoe(ctx);
        }

        // Attempt to remove a seed from the chest for replanting (this respects seedReservePerType)
        ItemStack cost = replantCostItemFor(block);
        if (cost != null && !cost.isEmpty()) {
            boolean taken = ChestUtils.removeOne(ctx.chest, cost.getItem());
            if (taken) {
                BlockState replanted = getReplantState.apply(state);
                if (replanted != null) {
                    ctx.level.setBlock(pos, replanted, 3);
                }
            }
        }

        // Mirror vanilla block break event so loader-specific clients see default break particles.
        if (Config.harvestParticles) {
            ctx.level.levelEvent(2001, pos, Block.getId(state));
        }

        // Play sound and particles if enabled (client-controlled via Config.harvestParticles)

        ctx.harvestedCount++;
    }

    

    /**
     * Determine the item cost required to replant the given block type.
     * @return the ItemStack representing one unit of the replant cost, or ItemStack.EMPTY if unknown.
     */
    private static ItemStack replantCostItemFor(Block block) {
        if (block == Blocks.BEETROOTS) return new ItemStack(Items.BEETROOT_SEEDS);
        if (block == Blocks.WHEAT) return new ItemStack(Items.WHEAT_SEEDS);
        if (block == Blocks.CARROTS) return new ItemStack(Items.CARROT);
        if (block == Blocks.POTATOES) return new ItemStack(Items.POTATO);
        if (block == Blocks.NETHER_WART) return new ItemStack(Items.NETHER_WART);
        try { if (block.getClass().getName().toLowerCase().contains("torchflower")) return new ItemStack(block.asItem()); } catch (Throwable ignored) {}
        return ItemStack.EMPTY;
    }

    /**
     * Return the seed Item corresponding to the block, used for seed-clutter policies.
     */
    private static Item clutterSeedItemFor(Block block) {
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

    /**
     * Apply the configured seed clutter policy to the list of drops before insertion into chest.
     * Emotional aside: this prevents your chests from becoming overrun by tiny seed armies.
     */
    private static void applySeedClutterPolicy(List<ItemStack> drops, Item seedItem, Container chest) {
        if (seedItem == null) return;
        if (Config.seedClutterMode == com.fastharvester.enums.SeedClutterMode.NONE) {
            drops.removeIf(s -> s.getItem() == seedItem);
            return;
        }
        if (Config.seedClutterMode == com.fastharvester.enums.SeedClutterMode.REDUCED) {
            int reserve = Math.max(0, Config.seedReservePerType);
            // Count existing seeds in chest
            int existing = 0;
            if (chest != null) {
                for (int i = 0; i < chest.getContainerSize(); i++) {
                    ItemStack s = chest.getItem(i);
                    if (!s.isEmpty() && s.getItem() == seedItem) existing += s.getCount();
                }
            }

            // Trim drops so that after insertion the chest will have at most `reserve` seeds
            int allowed = Math.max(0, reserve - existing);
            for (Iterator<ItemStack> it = drops.iterator(); it.hasNext();) {
                ItemStack s = it.next();
                if (s.getItem() == seedItem) {
                    if (allowed <= 0) {
                        it.remove();
                        continue;
                    }
                    if (s.getCount() > allowed) {
                        s.setCount(allowed);
                        allowed = 0;
                    } else {
                        allowed -= s.getCount();
                    }
                }
            }
            return;
        }

        // Normal mode: keep all supported seed drops (no-op)
    }

    /**
     * Attempt to replace a broken hoe from the chest inventory if possible.
     * Humanized note: replace the fallen warrior so harvesting can continue.
     */
    public static void handleBrokenHoe(HarvestContext ctx, ItemStack oldHoe) {
        Constants.LOG.info("[FastHarvester][HOE] Hoe broke during harvest. Previous: {}", oldHoe);
        // Attempt to find a replacement in the chest (naive)
        if (ctx.chest == null) return;
        for (int i = 0; i < ctx.chest.getContainerSize(); i++) {
            ItemStack slot = ctx.chest.getItem(i);
            if (!slot.isEmpty() && slot.getItem() == oldHoe.getItem()) {
                // Move one into the frame's hoe slot
                ItemStack one = slot.copy();
                one.setCount(1);
                slot.setCount(slot.getCount() - 1);
                ctx.hoe.setCount(1);
                Constants.LOG.info("[FastHarvester][HOE] Loaded spare hoe from chest slot {}.", i);
                return;
            }
        }
    }

    /**
     * Sync the frame-held hoe back to the world if loader-specific code requires it.
     * Currently a no-op in common; present for platform implementations to hook.
     */
    private static void syncFrameHoe(HarvestContext ctx) {
        // Loader-specific: update the frame block entity if necessary. For now just log.
        Constants.LOG.debug("[FastHarvester][HOE] syncFrameHoe called (no-op in common). Current hoe: {}", ctx.hoe);
    }
}
