package com.fastharvester.harvest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.Container;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;
import com.fastharvester.platform.adapter.FastItemFrameAdapterImpl;
import com.fastharvester.frame.FrameRegistry;
import com.fastharvester.frame.FrameScanner;
import com.fastharvester.util.loot.LootLogic;
import com.fastharvester.util.chest.ChestUtils;
import com.fastharvester.util.durability.DurabilityLogic;
import com.fastharvester.Constants;

public class HarvestUtils {
    private HarvestUtils() {}

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

        Item seedItem = clutterSeedItemFor(block);
        boolean seedIsCropFruit = isSeedAlsoCropFruit(seedItem);
        applyPreReplantSeedClutterPolicy(drops, seedItem, seedIsCropFruit);

        ItemStack cost = replantCostItemFor(block);
        boolean tookFromDropsForReplant = false;
        if (cost != null && !cost.isEmpty()) {
            Item replantSeedItem = cost.getItem();
            for (Iterator<ItemStack> it = drops.iterator(); it.hasNext();) {
                ItemStack s = it.next();
                if (s != null && !s.isEmpty() && s.getItem() == replantSeedItem) {
                    if (s.getCount() > 1) { s.setCount(s.getCount() - 1); } else { it.remove(); }
                    tookFromDropsForReplant = true;
                    try { Constants.logDebug("[HARVEST] Took seed from drops for replant: {} at {}", replantSeedItem, pos); } catch (Throwable ignored) {}
                    break;
                }
            }
        }

        applyPostReplantSeedClutterPolicy(drops, seedItem, seedIsCropFruit);
        ChestUtils.insertAll(ctx.chest, drops);

        ItemStack hoeBeforeDamage = ctx.hoe.copy();
        try {
            if (ctx != null && ctx.skipNextDamage) { ctx.skipNextDamage = false; }
            else { DurabilityLogic.applyDamage(ctx.level, ctx.hoe, ctx.level.getRandom()); }
        } catch (Throwable ignored) {}
        if (ctx.hoe.isEmpty()) { handleBrokenHoe(ctx, hoeBeforeDamage); }
        else { syncFrameHoe(ctx); }

        if (cost != null && !cost.isEmpty()) {
            if (tookFromDropsForReplant) {
                BlockState replanted = null;
                try { replanted = getReplantState.apply(state); } catch (Throwable ignore) {}
                if (replanted != null) { try { if (replanted.getBlock() instanceof CropBlock) replanted = replanted.setValue(CropBlock.AGE, 1); } catch (Throwable ignored) {} ctx.level.setBlock(pos, replanted, 3); }
            } else {
                boolean taken = ChestUtils.removeOne(ctx.chest, cost.getItem());
                if (taken) { BlockState replanted = null; try { replanted = getReplantState.apply(state); } catch (Throwable ignore) {} if (replanted != null) { try { if (replanted.getBlock() instanceof CropBlock) replanted = replanted.setValue(CropBlock.AGE, 1); } catch (Throwable ignored) {} ctx.level.setBlock(pos, replanted, 3); } }
                else { try { ctx.level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3); } catch (Throwable ignored) {} }
            }
        }

        if (com.fastharvester.Config.harvestParticles) ctx.level.levelEvent(2001, pos, Block.getId(state));
        ctx.harvestedCount++;
    }

    private static ItemStack replantCostItemFor(Block block) {
        if (block == Blocks.BEETROOTS) return new ItemStack(Items.BEETROOT_SEEDS);
        if (block == Blocks.WHEAT) return new ItemStack(Items.WHEAT_SEEDS);
        if (block == Blocks.CARROTS) return new ItemStack(Items.CARROT);
        if (block == Blocks.POTATOES) return new ItemStack(Items.POTATO);
        if (block == Blocks.NETHER_WART) return new ItemStack(Items.NETHER_WART);
        try { if (block.getClass().getName().toLowerCase().contains("torchflower")) return new ItemStack(block.asItem()); } catch (Throwable ignored) {}
        return ItemStack.EMPTY;
    }

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

    private static void applyPreReplantSeedClutterPolicy(List<ItemStack> drops, Item seedItem, boolean seedIsCropFruit) {
        if (seedItem == null) return;
        if (com.fastharvester.Config.seedClutterMode == com.fastharvester.enums.SeedClutterMode.NONE) {
            if (seedIsCropFruit) return;
            drops.removeIf(s -> s.getItem() == seedItem);
        }
    }

    private static void applyPostReplantSeedClutterPolicy(List<ItemStack> drops, Item seedItem, boolean seedIsCropFruit) {
        if (seedItem == null) return;
        if (com.fastharvester.Config.seedClutterMode == com.fastharvester.enums.SeedClutterMode.REDUCED) {
            if (seedIsCropFruit) return;
            for (Iterator<ItemStack> it = drops.iterator(); it.hasNext();) {
                ItemStack s = it.next();
                if (s == null || s.isEmpty()) continue;
                if (s.getItem() == seedItem) {
                    int newCount = s.getCount() / 2;
                    if (newCount <= 0) { it.remove(); } else { s.setCount(newCount); }
                }
            }
        }
    }

    private static boolean isSeedAlsoCropFruit(Item seedItem) {
        if (seedItem == null) return false;
        return seedItem == Items.CARROT || seedItem == Items.POTATO || seedItem == Items.NETHER_WART
                || seedItem instanceof BlockItem && ((BlockItem)seedItem).getBlock().getClass().getName().toLowerCase().contains("torchflower");
    }

    public static void handleBrokenHoe(HarvestContext ctx, ItemStack oldHoe) {
        Constants.logDebug("[HOE] Hoe broke during harvest. Previous: {}", oldHoe);
        try { playHoeBreakEffects(ctx, oldHoe); } catch (Throwable ignored) {}
        if (ctx.chest == null) return;
        try {
            FrameScanner.Anchor anchor = null;
            try { anchor = (FrameScanner.Anchor) ctx.anchor; } catch (Throwable ignored) {}

            ItemStack replacement = ChestUtils.takeFirstHoe(ctx.chest);
            if (replacement != null && !replacement.isEmpty()) {
                try { ItemStack newHoe = replacement.copy(); newHoe.setCount(1); ctx.hoe = newHoe; try { ctx.skipNextDamage = true; } catch (Throwable ignored) {} } catch (Throwable ignored) {}
                try { if (anchor != null) { String dimId = ctx.level.dimension().identifier().toString(); FrameRegistry.updateHoe(dimId, anchor.framePos, ctx.hoe == null ? replacement.copy() : ctx.hoe.copy()); } } catch (Throwable ignored) {}
                try { syncFrameHoe(ctx); } catch (Throwable ignored) {}
                try {
                    ItemStack verified = FrameScanner.readHoeFromFrame(ctx.level, anchor == null ? null : anchor.framePos);
                    if (verified == null || verified.isEmpty() || !(verified.getItem() instanceof HoeItem)) {
                        try { ChestUtils.insertAll(ctx.chest, java.util.List.of(replacement)); } catch (Throwable ignored) {}
                        try { if (anchor != null) { String dimId = ctx.level.dimension().identifier().toString(); FrameRegistry.updateHoe(dimId, anchor.framePos, net.minecraft.world.item.ItemStack.EMPTY); FrameRegistry.setCooldown(dimId, anchor.framePos, com.fastharvester.Config.chestFullCooldownTicks); } } catch (Throwable ignored) {}
                        try { syncFrameHoe(ctx); } catch (Throwable ignored) {}
                        ctx.chestFull = true;
                        Constants.logDebug("[HOE] Replacement did not persist to frame; returned to chest and aborting for {}", anchor == null ? "unknown" : anchor.framePos);
                        return;
                    }
                } catch (Throwable ignored) {}

                Constants.logDebug("[HOE] Pulled replacement hoe from chest: {}", replacement);
                return;
            } else {
                Constants.logDebug("[HOE] No replacement hoe available in chest for frame at {}", anchor == null ? "unknown" : anchor.framePos);
                try { if (anchor != null) { String dimId = ctx.level.dimension().identifier().toString(); FrameRegistry.updateHoe(dimId, anchor.framePos, net.minecraft.world.item.ItemStack.EMPTY); FrameRegistry.setCooldown(dimId, anchor.framePos, com.fastharvester.Config.chestFullCooldownTicks); } } catch (Throwable ignored) {}
                try { syncFrameHoe(ctx); } catch (Throwable ignored) {}
                ctx.chestFull = true;
            }
        } catch (Throwable t) { Constants.logWarn("[HOE] Error attempting to replace broken hoe", t); }
    }

    private static void playHoeBreakEffects(HarvestContext ctx, ItemStack brokenHoe) {
        try { if (ctx.level == null || ctx.anchor == null) return; } catch (Throwable ignored) {}
        try {
            try {
                ctx.level.playSound(null, ((FrameScanner.Anchor)ctx.anchor).framePos, (net.minecraft.sounds.SoundEvent)(Object)SoundEvents.ITEM_BREAK, SoundSource.BLOCKS, 0.8F, 0.8F + ctx.level.getRandom().nextFloat() * 0.4F);
            } catch (NoSuchMethodError | ClassCastException e) {
                try { ctx.level.playSound(null, ((FrameScanner.Anchor)ctx.anchor).framePos.getX() + 0.5, ((FrameScanner.Anchor)ctx.anchor).framePos.getY() + 0.5, ((FrameScanner.Anchor)ctx.anchor).framePos.getZ() + 0.5, (net.minecraft.sounds.SoundEvent)(Object)SoundEvents.ITEM_BREAK, SoundSource.BLOCKS, 0.8F, 0.8F + ctx.level.getRandom().nextFloat() * 0.4F); } catch (Throwable ignored) {}
            }

            ItemStack particlesFrom = (brokenHoe == null || brokenHoe.isEmpty()) ? new ItemStack(Items.WOODEN_HOE) : brokenHoe.copy();
            try { if (ctx.level instanceof net.minecraft.server.level.ServerLevel server) { server.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, particlesFrom), ((FrameScanner.Anchor)ctx.anchor).framePos.getX() + 0.5, ((FrameScanner.Anchor)ctx.anchor).framePos.getY() + 0.5, ((FrameScanner.Anchor)ctx.anchor).framePos.getZ() + 0.5, 10, 0.18, 0.18, 0.18, 0.03); } } catch (Throwable ignored) {}
        } catch (Throwable ignored) {}
    }

    public static void spinFrame(HarvestContext ctx) {
        try {
            FrameScanner.Anchor anchor = null;
            try { anchor = (FrameScanner.Anchor) ctx.anchor; } catch (Throwable ignored) {}
            if (anchor == null || ctx.level == null) return;
            BlockPos pos = anchor.framePos;
            try {
                java.util.List<net.minecraft.world.entity.decoration.ItemFrame> frames = ctx.level.getEntitiesOfClass(net.minecraft.world.entity.decoration.ItemFrame.class, new net.minecraft.world.phys.AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX()+1, pos.getY()+1, pos.getZ()+1), e -> true);
                if (!frames.isEmpty()) { net.minecraft.world.entity.decoration.ItemFrame frame = frames.get(0); frame.setRotation((frame.getRotation() + 1) & 7); return; }
            } catch (Throwable ignored) {}

            try { BlockEntity be = ctx.level.getBlockEntity(pos); if (be != null) { int current = FastItemFrameAdapterImpl.getRotation(be); FastItemFrameAdapterImpl.setRotation(be, (current + 1) & 7); } } catch (Throwable ignored) {}
        } catch (Throwable ignored) {}
    }

    private static void syncFrameHoe(HarvestContext ctx) {
        Constants.logDebug("[HOE] syncFrameHoe called. Current hoe: {}", ctx.hoe);
        try { FrameScanner.Anchor anchor = null; try { anchor = (FrameScanner.Anchor) ctx.anchor; } catch (Throwable ignored) {} if (anchor != null && ctx.level != null) { com.fastharvester.platform.Services.PLATFORM.updateFrameItem(ctx.level, anchor.framePos, ctx.hoe == null ? net.minecraft.world.item.ItemStack.EMPTY : ctx.hoe.copy()); } } catch (Throwable ignored) {}
    }
}
