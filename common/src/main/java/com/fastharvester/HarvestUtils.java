

package com.fastharvester;

// 🌾 HarvestUtils: hands-on harvesting logic. It plucks, sifts, and occasionally judges wasted seeds.
// Why it matters: this is where the magic (and occasional mayhem) of replanting happens.

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
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

        List<ItemStack> drops = LootLogic.getBlockDrops((net.minecraft.server.level.ServerLevel)ctx.level, pos, state, ctx.hoe);
        if (drops.isEmpty()) return;
        try {
            StringBuilder sbBefore = new StringBuilder();
            for (ItemStack s : drops) {
                if (s == null || s.isEmpty()) continue;
                sbBefore.append(s.getCount()).append('x').append(s.getItem()).append(',');
            }
            Constants.LOG.info("[FastHarvester][HARVEST] Drops before clutterPolicy: {}", sbBefore.length() > 0 ? sbBefore.toString() : "(none)");
        } catch (Throwable ignored) {}

        // Don't let seeds take over your chest like rabbits.
        applySeedClutterPolicy(drops, clutterSeedItemFor(block), ctx.chest);
        try {
            StringBuilder sbAfter = new StringBuilder();
            for (ItemStack s : drops) {
                if (s == null || s.isEmpty()) continue;
                sbAfter.append(s.getCount()).append('x').append(s.getItem()).append(',');
            }
            Constants.LOG.info("[FastHarvester][HARVEST] Drops after clutterPolicy: {}", sbAfter.length() > 0 ? sbAfter.toString() : "(none)");
        } catch (Throwable ignored) {}

        // Prefer to consume a seed from the freshly-harvested drops for replanting so we don't drain the chest reserve.
        ItemStack cost = replantCostItemFor(block);
        boolean tookFromDropsForReplant = false;
        if (cost != null && !cost.isEmpty()) {
            Item seedItem = cost.getItem();
            for (Iterator<ItemStack> it = drops.iterator(); it.hasNext();) {
                ItemStack s = it.next();
                if (s != null && !s.isEmpty() && s.getItem() == seedItem) {
                    if (s.getCount() > 1) {
                        s.setCount(s.getCount() - 1);
                    } else {
                        it.remove();
                    }
                    tookFromDropsForReplant = true;
                    try { Constants.LOG.info("[FastHarvester][HARVEST] Took seed from drops for replant: {} at {}", seedItem, pos); } catch (Throwable ignored) {}
                    break;
                }
            }
        }

        // Insert remaining drops into the chest
        try {
            if (cost != null && !cost.isEmpty()) {
                Item sItem = cost.getItem();
                int before = ChestUtils.countItem(ctx.chest, sItem);
                try { Constants.LOG.info("[FastHarvester][HARVEST] Chest before insertAll has {} of {}", before, sItem); } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}

        ChestUtils.insertAll(ctx.chest, drops);

        try {
            if (cost != null && !cost.isEmpty()) {
                Item sItem = cost.getItem();
                int after = ChestUtils.countItem(ctx.chest, sItem);
                try { Constants.LOG.info("[FastHarvester][HARVEST] After insertAll: chest has {} of {}", after, sItem); } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}

        ItemStack hoeBeforeDamage = ctx.hoe.copy();
        DurabilityLogic.applyDamage(ctx.level, ctx.hoe, ctx.level.getRandom());
        if (ctx.hoe.isEmpty()) {
            // RIP, brave hoe. You served us well.
            handleBrokenHoe(ctx, hoeBeforeDamage);
        } else {
            syncFrameHoe(ctx);
        }

        // If we already reserved a seed from the drops, use that for replant; otherwise attempt to remove from chest (respecting reserve).
        if (cost != null && !cost.isEmpty()) {
            if (tookFromDropsForReplant) {
                BlockState replanted = null;
                try {
                    replanted = getReplantState.apply(state);
                } catch (Throwable ignore) {}
                if (replanted != null) {
                    try { if (replanted.getBlock() instanceof CropBlock) replanted = replanted.setValue(CropBlock.AGE, 1); } catch (Throwable ignored) {}
                    ctx.level.setBlock(pos, replanted, 3);
                }
            } else {
                boolean taken = ChestUtils.removeOne(ctx.chest, cost.getItem());
                if (taken) {
                    BlockState replanted = null;
                    try {
                        replanted = getReplantState.apply(state);
                    } catch (Throwable ignore) {}
                    if (replanted != null) {
                        try { if (replanted.getBlock() instanceof CropBlock) replanted = replanted.setValue(CropBlock.AGE, 1); } catch (Throwable ignored) {}
                        ctx.level.setBlock(pos, replanted, 3);
                    }
                } else {
                    Constants.LOG.debug("[FastHarvester][HARVEST] Could not take seed for replant: {} at {}", cost.getItem(), pos);
                    try {
                        ctx.level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                    } catch (Throwable ignored) {}
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
            // In REDUCED mode we do not trim seed drops prior to insertion.
            // Seed reserve enforcement happens when removing seeds for replanting (ChestUtils.removeOne).
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
        // Play visual/sound effects for the broken hoe
        try { playHoeBreakEffects(ctx, oldHoe); } catch (Throwable ignored) {}
        if (ctx.chest == null) return;
        try {
            FrameScanner.Anchor anchor = null;
            try { anchor = (FrameScanner.Anchor) ctx.anchor; } catch (Throwable ignored) {}

            ItemStack replacement = ChestUtils.takeFirstHoe(ctx.chest);
            if (replacement != null && !replacement.isEmpty()) {
                // Assign the replacement into the active context so further harvests use it
                try {
                    ItemStack newHoe = replacement.copy();
                    newHoe.setCount(1);
                    ctx.hoe = newHoe;
                } catch (Throwable ignored) {}

                // Update registry entry so future scans see the replacement
                try {
                    if (anchor != null) {
                        String dimId = ctx.level.dimension().identifier().toString();
                        FrameRegistry.updateHoe(dimId, anchor.framePos, ctx.hoe == null ? replacement.copy() : ctx.hoe.copy());
                    }
                } catch (Throwable ignored) {}

                // Ask platform to persist the frame-held item if possible
                try { syncFrameHoe(ctx); } catch (Throwable ignored) {}

                Constants.LOG.info("[FastHarvester][HOE] Pulled replacement hoe from chest: {}", replacement);
                return;
            } else {
                Constants.LOG.debug("[FastHarvester][HOE] No replacement hoe available in chest for frame at {}", anchor == null ? "unknown" : anchor.framePos);
                try {
                    if (anchor != null) {
                        String dimId = ctx.level.dimension().identifier().toString();
                        FrameRegistry.updateHoe(dimId, anchor.framePos, net.minecraft.world.item.ItemStack.EMPTY);
                        FrameRegistry.setCooldown(dimId, anchor.framePos, Config.chestFullCooldownTicks);
                    }
                } catch (Throwable ignored) {}
                try { syncFrameHoe(ctx); } catch (Throwable ignored) {}
                // Signal scanner to stop this pass for this anchor
                ctx.chestFull = true;
            }
        } catch (Throwable t) {
            Constants.LOG.warn("[FastHarvester][HOE] Error attempting to replace broken hoe: {}", t.toString());
        }
    }

    private static void playHoeBreakEffects(HarvestContext ctx, ItemStack brokenHoe) {
        try {
            if (ctx.level == null || ctx.anchor == null) return;
        } catch (Throwable ignored) {}
        try {
            try {
                ctx.level.playSound(
                        null,
                        ((FrameScanner.Anchor)ctx.anchor).framePos,
                        (net.minecraft.sounds.SoundEvent)(Object)SoundEvents.ITEM_BREAK,
                        SoundSource.BLOCKS,
                        0.8F,
                        0.8F + ctx.level.getRandom().nextFloat() * 0.4F);
            } catch (NoSuchMethodError | ClassCastException e) {
                // Fallback: try calling playSound with coordinates and a looser cast
                try {
                    ctx.level.playSound(null,
                            ((FrameScanner.Anchor)ctx.anchor).framePos.getX() + 0.5,
                            ((FrameScanner.Anchor)ctx.anchor).framePos.getY() + 0.5,
                            ((FrameScanner.Anchor)ctx.anchor).framePos.getZ() + 0.5,
                            (net.minecraft.sounds.SoundEvent)(Object)SoundEvents.ITEM_BREAK,
                            SoundSource.BLOCKS,
                            0.8F,
                            0.8F + ctx.level.getRandom().nextFloat() * 0.4F);
                } catch (Throwable ignored) {}
            }

            ItemStack particlesFrom = (brokenHoe == null || brokenHoe.isEmpty()) ? new ItemStack(Items.WOODEN_HOE) : brokenHoe.copy();
            try {
                if (ctx.level instanceof net.minecraft.server.level.ServerLevel server) {
                    server.sendParticles(
                            new ItemParticleOption(ParticleTypes.ITEM, particlesFrom),
                            ((FrameScanner.Anchor)ctx.anchor).framePos.getX() + 0.5,
                            ((FrameScanner.Anchor)ctx.anchor).framePos.getY() + 0.5,
                            ((FrameScanner.Anchor)ctx.anchor).framePos.getZ() + 0.5,
                            10,
                            0.18,
                            0.18,
                            0.18,
                            0.03);
                }
            } catch (Throwable ignored) {}
        } catch (Throwable ignored) {}
    }

    /**
     * Rotate the item frame or FastItemFrames block-entity by one step.
     * <p>
     * This is used to nudge the visible frame rotation when a harvest action
     * requests a small, local rotation (e.g. for visual feedback).
     * </p>
     * Emotional aside: gently nudges the frame so it looks like it's doing a tiny happy dance.
     * @param ctx Harvest context containing anchor and level info
     */
    public static void spinFrame(HarvestContext ctx) {
        try {
            FrameScanner.Anchor anchor = null;
            try { anchor = (FrameScanner.Anchor) ctx.anchor; } catch (Throwable ignored) {}
            if (anchor == null || ctx.level == null) return;
            BlockPos pos = anchor.framePos;
            try {
                java.util.List<net.minecraft.world.entity.decoration.ItemFrame> frames = ctx.level.getEntitiesOfClass(net.minecraft.world.entity.decoration.ItemFrame.class,
                        new net.minecraft.world.phys.AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX()+1, pos.getY()+1, pos.getZ()+1), e -> true);
                if (!frames.isEmpty()) {
                    net.minecraft.world.entity.decoration.ItemFrame frame = frames.get(0);
                    frame.setRotation((frame.getRotation() + 1) & 7);
                    return;
                }
            } catch (Throwable ignored) {}

            try {
                BlockEntity be = ctx.level.getBlockEntity(pos);
                if (be != null) {
                    int current = FastItemFrameAdapterImpl.getRotation(be);
                    FastItemFrameAdapterImpl.setRotation(be, (current + 1) & 7);
                }
            } catch (Throwable ignored) {}
        } catch (Throwable ignored) {}
    }

    /**
     * Sync the frame-held hoe back to the world if loader-specific code requires it.
     * Currently a no-op in common; present for platform implementations to hook.
     */
    private static void syncFrameHoe(HarvestContext ctx) {
        // Loader-specific: update the frame/block-held item if possible.
        Constants.LOG.debug("[FastHarvester][HOE] syncFrameHoe called. Current hoe: {}", ctx.hoe);
        try {
            FrameScanner.Anchor anchor = null;
            try { anchor = (FrameScanner.Anchor) ctx.anchor; } catch (Throwable ignored) {}
            if (anchor != null && ctx.level != null) {
                com.fastharvester.platform.Services.PLATFORM.updateFrameItem(ctx.level, anchor.framePos, ctx.hoe == null ? net.minecraft.world.item.ItemStack.EMPTY : ctx.hoe);
            }
        } catch (Throwable ignored) {}
    }
}
