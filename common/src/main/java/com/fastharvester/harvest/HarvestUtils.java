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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;
import java.util.Locale;
import com.fastharvester.platform.adapter.FIF;
import com.fastharvester.frame.FrameRegistry;
import com.fastharvester.frame.FrameScanner;
import com.fastharvester.util.hoe.FrameHoeReplacement;
import com.fastharvester.util.loot.LootLogic;
import com.fastharvester.util.chest.ChestUtils;
import com.fastharvester.util.durability.DurabilityLogic;
import com.fastharvester.util.log.LogUtils;
import com.fastharvester.config.Config;

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

        Item seedItem = CropRegistry.clutterSeed(block);
        boolean seedIsCropFruit = CropRegistry.isSeedAlsoCropFruit(seedItem);
        applyPreReplantSeedClutterPolicy(drops, seedItem, seedIsCropFruit);

        ItemStack cost = CropRegistry.replantCost(block);
        boolean tookFromDropsForReplant = false;
        if (cost != null && !cost.isEmpty()) {
            Item replantSeedItem = cost.getItem();
            for (Iterator<ItemStack> it = drops.iterator(); it.hasNext();) {
                ItemStack s = it.next();
                if (s != null && !s.isEmpty() && s.getItem() == replantSeedItem) {
                    if (s.getCount() > 1) { s.setCount(s.getCount() - 1); } else { it.remove(); }
                    tookFromDropsForReplant = true;
                    try { LogUtils.logDebug("[HARVEST] Took seed from drops for replant: {} at {}", replantSeedItem, pos); } catch (Throwable ignored) {}
                    break;
                }
            }
        }

        applyPostReplantSeedClutterPolicy(drops, seedItem, seedIsCropFruit);
        ChestUtils.insertAll(ctx.chest, drops);

        try {
            if (ctx != null && ctx.skipNextDamage) { ctx.skipNextDamage = false; }
            else { DurabilityLogic.applyDamage(ctx.level, ctx.hoe, ctx.level.getRandom()); }
        } catch (Throwable ignored) {}
        if (ctx.hoe.isEmpty()) { FrameHoeReplacement.tryReplaceBrokenHoe(ctx); }
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

        if (Config.harvestParticles) ctx.level.levelEvent(2001, pos, Block.getId(state));
        ctx.harvestedCount++;
    }

    

    private static void applyPreReplantSeedClutterPolicy(List<ItemStack> drops, Item seedItem, boolean seedIsCropFruit) {
        if (seedItem == null) return;
        if (Config.seedClutterMode == com.fastharvester.enums.SeedClutterMode.NONE) {
            if (seedIsCropFruit) return;
            drops.removeIf(s -> s.getItem() == seedItem);
        }
    }

    private static void applyPostReplantSeedClutterPolicy(List<ItemStack> drops, Item seedItem, boolean seedIsCropFruit) {
        if (seedItem == null) return;
        if (Config.seedClutterMode == com.fastharvester.enums.SeedClutterMode.REDUCED) {
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

    public static void handleBrokenHoe(HarvestContext ctx, ItemStack oldHoe) {
        LogUtils.logDebug("[HOE] Hoe broke during harvest. Previous: {}", oldHoe);
        try { playHoeBreakEffects(ctx, oldHoe); } catch (Throwable ignored) {}
        FrameHoeReplacement.tryReplaceBrokenHoe(ctx);
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
                int cur = 0;
                try {
                    java.util.List<net.minecraft.world.entity.decoration.ItemFrame> frames = ctx.level.getEntitiesOfClass(net.minecraft.world.entity.decoration.ItemFrame.class, new net.minecraft.world.phys.AABB(pos));
                    for (net.minecraft.world.entity.decoration.ItemFrame f : frames) {
                        if (f.blockPosition().equals(pos)) {
                                for (java.lang.reflect.Method m : f.getClass().getMethods()) {
                                String name = m.getName().toLowerCase(Locale.ROOT);
                                if ((name.contains("get") || name.contains("getitem")) && name.contains("rotation") && m.getParameterCount() == 0) {
                                    try { Object r = m.invoke(f); if (r instanceof Number) { cur = ((Number) r).intValue() & 7; } } catch (Throwable ignored) {}
                                    break;
                                }
                            }
                            try { java.lang.reflect.Field fld = f.getClass().getDeclaredField("rotation"); fld.setAccessible(true); Object v = fld.get(f); if (v instanceof Number) cur = ((Number) v).intValue() & 7; } catch (Throwable ignored) {}
                            break;
                        }
                    }
                } catch (Throwable ignored) {}

                    try { BlockEntity be = ctx.level.getBlockEntity(pos); if (be != null) { try { cur = FIF.getRotation(be); } catch (Throwable ignored) {} } } catch (Throwable ignored) {}

                int next = (cur + 1) & 7;
                long gameTime = -1L; try { gameTime = ctx.level.getGameTime(); } catch (Throwable ignored) {}
                try { FrameRegistry.scheduleRotation(ctx.level.dimension().identifier().toString(), pos, next, gameTime); } catch (Throwable ignored) {}
            } catch (Throwable ignored) {}
        } catch (Throwable ignored) {}
    }

    private static void syncFrameHoe(HarvestContext ctx) {
        LogUtils.logDebug("[HOE] syncFrameHoe called. Current hoe: {}", ctx.hoe);
        try { FrameScanner.Anchor anchor = null; try { anchor = (FrameScanner.Anchor) ctx.anchor; } catch (Throwable ignored) {} if (anchor != null && ctx.level != null) { com.fastharvester.platform.Services.PLATFORM.updateFrameItem(ctx.level, anchor.framePos, ctx.hoe == null ? net.minecraft.world.item.ItemStack.EMPTY : ctx.hoe.copy()); } } catch (Throwable ignored) {}
    }
}
