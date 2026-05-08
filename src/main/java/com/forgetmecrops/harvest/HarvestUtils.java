package com.forgetmecrops.harvest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.server.level.ServerLevel;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;
import java.util.Locale;
import com.forgetmecrops.platform.adapter.FIF;
import com.forgetmecrops.frame.FrameRegistry;
import com.forgetmecrops.frame.FrameScanner;
import com.forgetmecrops.util.hoe.FrameHoeReplacement;
import com.forgetmecrops.util.loot.LootLogic;
import com.forgetmecrops.util.chest.ChestUtils;
import com.forgetmecrops.util.durability.DurabilityLogic;
import com.forgetmecrops.util.log.LogUtils;
import com.forgetmecrops.util.ExceptionHandler;
import com.forgetmecrops.util.ValidationUtils;
import com.forgetmecrops.config.Config;
import com.forgetmecrops.enums.SeedClutterMode;

/**
 * HarvestUtils: The actual farming code — where the crops meet their fate!
 * <p>
 * Contains all the logic for breaking mature crops, collecting and filtering loot,
 * applying seed clutter policies, inserting drops into the chest, triggering replants,
 * applying hoe durability, playing harvest sounds and particles, and gracefully recovering
 * from broken hoes. If FrameScanner is the explorer and FrameRegistry is the ledger,
 * HarvestUtils is the one actually getting its hands dirty.
 * </p>
 * <p>
 * Also contains particle and sound helpers for that satisfying audiovisual feedback
 * that makes automated farming feel alive instead of robotic. Because even robots
 * deserve nice harvest animations.
 * </p>
 */
public class HarvestUtils {
    // Utility class. HarvestUtils doesn't harvest itself.
    private HarvestUtils() {}

    /**
     * Harvests a single mature crop block: gets the drops, filters seeds, inserts loot into the
     * chest, applies hoe durability, handles replanting, and plays the satisfying harvest effects.
     * The single most important method in the mod. Everything else is in service of this.
     *
     * @param ctx           harvest context (anchor, level, hoe, chest, state bags)
     * @param pos           position of the block to harvest
     * @param state         current block state at pos
     * @param isMature      predicate that returns true if a given state is mature enough to harvest
     * @param getReplantState function that returns the replanted block state, or null if non-replantable
     */
    public static void harvestCrop(HarvestContext ctx, BlockPos pos, BlockState state, Function<BlockState, Boolean> isMature, Function<BlockState, BlockState> getReplantState) {
        if (ValidationUtils.isAnyNull(ctx.level, ctx.chest) || ctx.getHoe().isEmpty()) return;

        Block block = state.getBlock();
        if (!isMature.apply(state)) return;

        if (!ChestUtils.hasSpace(ctx.chest)) {
            ctx.setChestFull(true);
            return;
        }

        List<ItemStack> drops = LootLogic.getBlockDrops((net.minecraft.server.level.ServerLevel)ctx.level, pos, state, ctx.getHoe());
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
                    ExceptionHandler.silentTry(() -> LogUtils.logDebug("[HARVEST] Took seed from drops for replant: {} at {}", replantSeedItem, pos));
                    break;
                }
            }
        }

        applyPostReplantSeedClutterPolicy(drops, seedItem, seedIsCropFruit);
        ChestUtils.insertAll(ctx.chest, drops);

        try {
            if (ctx.isSkipNextDamage()) { ctx.setSkipNextDamage(false); }
            else { DurabilityLogic.applyDamage(ctx.level, ctx.getHoe(), ctx.level.getRandom()); }
        } catch (Throwable ignored) {}
        if (ctx.getHoe().isEmpty()) { FrameHoeReplacement.tryReplaceBrokenHoe(ctx); }
        else { syncFrameHoe(ctx); }

        if (cost != null && !cost.isEmpty()) {
            if (tookFromDropsForReplant) {
                applyReplantState(ctx, pos, state, getReplantState);
            } else {
                boolean taken = ChestUtils.removeOne(ctx.chest, cost.getItem());
                if (taken) { applyReplantState(ctx, pos, state, getReplantState); }
                else { try { ctx.level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3); } catch (Throwable ignored) {} }
            }
        } else {
            // Non-replantable harvest targets (e.g. melon/pumpkin fruit) should be removed.
            try { ctx.level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3); } catch (Throwable ignored) {}
        }

        playHarvestBreakSound(ctx, pos, state);
        if (Config.isHarvestParticles()) {
            ctx.level.levelEvent(2001, pos, Block.getId(state));
            emitHarvestBurstParticles(ctx.level, pos, state);
        }
        ctx.incrementHarvested();
    }

    /**
     * Places the replanted block state at the given position, resetting it to age=1.
     * Age 1 (not 0) because starting the regrowth at a very young but non-zero age
     * lets the crop start growing immediately without the awkward freshly-planted look.
     *
     * @param ctx            the harvest context (for level access)
     * @param pos            position to replant at
     * @param state          the original harvested state (passed to getReplantState)
     * @param getReplantState function that produces the replanted block state
     */
    private static void applyReplantState(HarvestContext ctx, BlockPos pos, BlockState state,
                                          Function<BlockState, BlockState> getReplantState) {
        BlockState replanted = null;
        try { replanted = getReplantState.apply(state); } catch (Throwable ignored) {}
        if (replanted == null) return;
        try {
            if (replanted.getBlock() instanceof CropBlock) {
                replanted = FrameScanner.setAgeSafe(replanted, 1);
            }
        } catch (Throwable ignored) {}
        try { ctx.level.setBlock(pos, replanted, 3); } catch (Throwable ignored) {}
    }

    /**
     * Pre-replant seed clutter filter: for NONE mode, strips all seeds from the drop list
     * before replanting occurs. Seeds that are also crop fruits (carrot, potato, Nether Wart)
     * are exempt because removing them would mean losing the harvest entirely. That would be bad.
     *
     * @param drops          the drop list to filter (modified in-place)
     * @param seedItem       the seed item for this crop type
     * @param seedIsCropFruit true if the seed is also the harvestable fruit
     */
    private static void applyPreReplantSeedClutterPolicy(List<ItemStack> drops, Item seedItem, boolean seedIsCropFruit) {
        if (seedItem == null) return;
        if (Config.getSeedClutterMode() == SeedClutterMode.NONE) {
            if (seedIsCropFruit) return;
            drops.removeIf(s -> s.getItem() == seedItem);
        }
    }

    /**
     * Post-replant seed clutter filter: for REDUCED mode, halves all seed stacks in the drop list.
     * Applied after the replant seed is taken so we only reduce the excess going into the chest.
     * Fruit-seeds are exempt. Seeds that halve to zero are removed entirely.
     *
     * @param drops          the drop list to filter (modified in-place)
     * @param seedItem       the seed item for this crop type
     * @param seedIsCropFruit true if the seed is also the harvestable fruit
     */
    private static void applyPostReplantSeedClutterPolicy(List<ItemStack> drops, Item seedItem, boolean seedIsCropFruit) {
        if (seedItem == null) return;
        if (Config.getSeedClutterMode() == SeedClutterMode.REDUCED) {
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

    /**
     * Plays the hoe-break sound and emits item-particle debris at the frame position.
     * Called from the main harvest loop when a hoe's stack count drops to zero, before
     * the replacement attempt. Because a hoe dying silently would be disrespectful.
     *
     * @param ctx     the harvest context (for level and anchor)
     * @param oldHoe  the broken hoe stack (used for particle item type)
     */
    public static void handleBrokenHoe(HarvestContext ctx, ItemStack oldHoe) {
        LogUtils.logDebug("[HOE] Hoe broke during harvest. Previous: {}", oldHoe);
        try { playHoeBreakEffects(ctx, oldHoe); } catch (Throwable ignored) {}
        FrameHoeReplacement.tryReplaceBrokenHoe(ctx);
    }

    /**
     * Plays the hoe-break sound effect and a burst of item particles at the frame.
     * The particles use the actual broken hoe's item texture for dramatic effect.
     * If the level or anchor is unavailable, it bails out quietly — no crash for missing drama.
     */
    private static void playHoeBreakEffects(HarvestContext ctx, ItemStack brokenHoe) {
        try { if (ctx.level == null || ctx.anchor == null) return; } catch (Throwable ignored) {}
        FrameScanner.Anchor anchor = resolveAnchor(ctx);
        if (anchor == null) return;
        SoundEvent breakSound = resolveItemBreakSound();
        if (breakSound == null) return;
        try {
            try {
                ctx.level.playSound(null, anchor.framePos, breakSound, SoundSource.BLOCKS, 0.8F, 0.8F + ctx.level.getRandom().nextFloat() * 0.4F);
            } catch (NoSuchMethodError | ClassCastException e) {
                try { ctx.level.playSound(null, anchor.framePos.getX() + 0.5, anchor.framePos.getY() + 0.5, anchor.framePos.getZ() + 0.5, breakSound, SoundSource.BLOCKS, 0.8F, 0.8F + ctx.level.getRandom().nextFloat() * 0.4F); } catch (Throwable ignored) {}
            }

            ItemStack particlesFrom = (brokenHoe == null || brokenHoe.isEmpty()) ? new ItemStack(Items.WOODEN_HOE) : brokenHoe;
            try { if (ctx.level instanceof net.minecraft.server.level.ServerLevel server) { server.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, particlesFrom), anchor.framePos.getX() + 0.5, anchor.framePos.getY() + 0.5, anchor.framePos.getZ() + 0.5, 10, 0.18, 0.18, 0.18, 0.03); } } catch (Throwable ignored) {}
        } catch (Throwable ignored) {}
    }

    /**
     * Plays the block-placement sound for the replanted crop.
     * Called after a crop block is set so the farm sounds alive and not suspiciously quiet.
     *
     * @param level        the level in which to play the sound
     * @param pos          the position the crop was planted at
     * @param plantedState the planted block state (used to get the correct sound type)
     */
    public static void playPlantSound(net.minecraft.world.level.Level level, BlockPos pos, BlockState plantedState) {
        if (level == null || pos == null || plantedState == null) return;
        try {
            SoundEvent sound = plantedState.getSoundType().getPlaceSound();
            level.playSound(null, pos, sound, SoundSource.BLOCKS, 0.7F, 0.92F + level.getRandom().nextFloat() * 0.16F);
        } catch (Throwable ignored) {}
    }

    /**
     * Plays the tilling sound effect at the given position.
     * Uses HOE_TILL sound via reflective resolution (handles both wrapped and unwrapped SoundEvent
     * across API versions), with a fallback to gravel-hit if the proper sound can't be found.
     *
     * @param level the level in which to play the sound
     * @param pos   the tilled position
     */
    public static void playTillingSound(net.minecraft.world.level.Level level, BlockPos pos) {
        if (level == null || pos == null) return;
        try {
            SoundEvent sound = resolveHoeTillSound();
            level.playSound(null, pos, sound, SoundSource.BLOCKS, 0.9F, 0.95F + level.getRandom().nextFloat() * 0.12F);
        } catch (Throwable ignored) {}
    }

    /**
     * Emits a subtle trailing dust particle at a crop position during spiral scanning.
     * Gives visual feedback that the scanner is walking through the farm.
     * Only fires if harvest particles are enabled in config. Gracefully skips if level is wrong type.
     *
     * @param level   the level (must be a ServerLevel to send particles)
     * @param cropPos the position to emit the particle at
     */
    public static void emitSpiralTrailParticles(net.minecraft.world.level.Level level, BlockPos cropPos) {
        if (!Config.isHarvestParticles() || level == null || cropPos == null) return;
        if (!(level instanceof ServerLevel server)) return;
        try {
            float brightness = Mth.clamp(0.82F + (server.getRandom().nextFloat() - 0.5F) * 0.14F, 0.70F, 0.95F);
            int c = toColor(brightness, brightness, brightness);
            DustParticleOptions dust = new DustParticleOptions(c, 0.75F);
            server.sendParticles(dust,
                    cropPos.getX() + 0.5,
                    cropPos.getY() + 0.10,
                    cropPos.getZ() + 0.5,
                    6,
                    0.16,
                    0.34,
                    0.16,
                    0.018);
        } catch (Throwable ignored) {}
    }

    /**
     * Emits a burst of colored dust particles when a crop is harvested.
     * Color is chosen based on crop type (wheat = golden, beetroot = deep red, carrot = orange, etc.)
     * for satisfying type-specific harvest feedback. Two separate bursts for a fuller effect.
     *
     * @param level          the level (must be ServerLevel)
     * @param cropPos        the harvested position
     * @param harvestedState the block state of the harvested crop (for color selection)
     */
    private static void emitHarvestBurstParticles(net.minecraft.world.level.Level level, BlockPos cropPos, BlockState harvestedState) {
        if (!(level instanceof ServerLevel server) || cropPos == null || harvestedState == null) return;
        try {
            float[] base = dominantDustColor(harvestedState.getBlock());
            for (int i = 0; i < 2; i++) {
                float variance = 0.12F;
                float r = Mth.clamp(base[0] + (server.getRandom().nextFloat() - 0.5F) * variance, 0.0F, 1.0F);
                float g = Mth.clamp(base[1] + (server.getRandom().nextFloat() - 0.5F) * variance, 0.0F, 1.0F);
                float b = Mth.clamp(base[2] + (server.getRandom().nextFloat() - 0.5F) * variance, 0.0F, 1.0F);
                DustParticleOptions dust = new DustParticleOptions(toColor(r, g, b), 0.90F);
                server.sendParticles(dust,
                        cropPos.getX() + 0.5,
                        cropPos.getY() + 0.12,
                        cropPos.getZ() + 0.5,
                        14,
                        0.16,
                        1.35,
                        0.16,
                        0.038);
            }
        } catch (Throwable ignored) {}
    }

    /**
     * Plays the break sound for the harvested crop block at the given position.
     * Volume and pitch are slightly randomized for natural variation.
     */
    private static void playHarvestBreakSound(HarvestContext ctx, BlockPos pos, BlockState harvestedState) {
        try {
            SoundEvent sound = harvestedState.getSoundType().getBreakSound();
            ctx.level.playSound(null, pos, sound, SoundSource.BLOCKS, 0.85F, 0.92F + ctx.level.getRandom().nextFloat() * 0.18F);
        } catch (Throwable ignored) {}
    }

    /**
     * Resolves the HOE_TILL SoundEvent with fallback compatibility for different MC API wrapping styles.
     * Some versions wrap SoundEvent in a Holder; others expose it directly.
     * We probe both .value() and .get() before giving up and returning GRAVEL_HIT.
     */
    private static SoundEvent resolveHoeTillSound() {
        try {
            Object value = SoundEvents.HOE_TILL;
            try {
                java.lang.reflect.Method method = value.getClass().getMethod("value");
                Object wrapped = method.invoke(value);
                if (wrapped instanceof SoundEvent event) return event;
            } catch (Throwable ignored) {}
            try {
                java.lang.reflect.Method method = value.getClass().getMethod("get");
                Object wrapped = method.invoke(value);
                if (wrapped instanceof SoundEvent event) return event;
            } catch (Throwable ignored) {}
        } catch (Throwable ignored) {}
        return SoundEvents.GRAVEL_HIT;
    }

    /**
     * Returns an [r, g, b] color array representing the dominant visual color of the given crop.
     * Used to choose the harvest particle color. Each crop has a bespoke color chosen
     * to match its real-world look. The default (gray) is for anything we don't recognize.
     *
     * @param block the harvested crop block
     * @return float[3] with r, g, b values in [0.0, 1.0]
     */
    private static float[] dominantDustColor(Block block) {
        if (block == null) return new float[]{0.78F, 0.78F, 0.78F};
        if (block == Blocks.WHEAT) return new float[]{0.86F, 0.78F, 0.28F};
        if (block == Blocks.CARROTS) return new float[]{0.93F, 0.55F, 0.20F};
        if (block == Blocks.POTATOES) return new float[]{0.75F, 0.63F, 0.38F};
        if (block == Blocks.BEETROOTS) return new float[]{0.72F, 0.20F, 0.24F};
        if (block == Blocks.NETHER_WART) return new float[]{0.62F, 0.12F, 0.18F};
        if (block == Blocks.SWEET_BERRY_BUSH) return new float[]{0.76F, 0.18F, 0.34F};
        if (block == Blocks.MELON || block == Blocks.MELON_STEM || block == Blocks.ATTACHED_MELON_STEM) return new float[]{0.45F, 0.76F, 0.30F};
        if (block == Blocks.PUMPKIN || block == Blocks.PUMPKIN_STEM || block == Blocks.ATTACHED_PUMPKIN_STEM) return new float[]{0.92F, 0.52F, 0.18F};
        return new float[]{0.78F, 0.78F, 0.78F};
    }

    /**
     * Packs r/g/b float components (0.0–1.0) into a packed ARGB int with alpha=255.
     * Used to create the {@link DustParticleOptions} packed color argument.
     */
    private static int toColor(float r, float g, float b) {
        int ri = Mth.clamp((int)(r * 255.0F), 0, 255);
        int gi = Mth.clamp((int)(g * 255.0F), 0, 255);
        int bi = Mth.clamp((int)(b * 255.0F), 0, 255);
        return (255 << 24) | (ri << 16) | (gi << 8) | bi;
    }

    /**
     * Resolves the ITEM_BREAK SoundEvent with the same reflective fallback strategy as resolveHoeTillSound.
     * Returns null (not a default) if not found, because the hoe-break visual can survive without sound.
     */
    private static SoundEvent resolveItemBreakSound() {
        try {
            Object value = SoundEvents.ITEM_BREAK;
            try {
                java.lang.reflect.Method method = value.getClass().getMethod("value");
                Object wrapped = method.invoke(value);
                if (wrapped instanceof SoundEvent event) return event;
            } catch (Throwable ignored) {}
            try {
                java.lang.reflect.Method method = value.getClass().getMethod("get");
                Object wrapped = method.invoke(value);
                if (wrapped instanceof SoundEvent event) return event;
            } catch (Throwable ignored) {}
        } catch (Throwable ignored) {}
        return null;
    }

    /**
     * Advances the frame's rotation by one step for visual feedback during harvest.
     * Reads the current rotation from the item frame entity (or FIF block-entity),
     * increments by 1 modulo 8, and schedules the rotation update via FrameRegistry
     * so it doesn't immediately conflict with the tick cycle.
     *
     * @param ctx the harvest context (for anchor and level)
     */
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

    /**
     * Pushes the current hoe ItemStack state from the context back into the item frame.
     * Called after every harvest (not just on hoe replacement) so the frame's displayed
     * item stays in sync with damage value changes. The frame is the ground truth.
     *
     * @param ctx the harvest context containing the current hoe state
     */
    private static void syncFrameHoe(HarvestContext ctx) {
        LogUtils.logDebug("[HOE] syncFrameHoe called. Current hoe: {}", ctx.getHoe());
        try {
            FrameScanner.Anchor anchor = resolveAnchor(ctx);
            if (anchor != null && ctx.level != null) {
                com.forgetmecrops.platform.Services.PLATFORM.updateFrameItem(
                        ctx.level,
                        anchor.framePos,
                        ctx.getHoe().isEmpty() ? net.minecraft.world.item.ItemStack.EMPTY : ctx.getHoe().copy());
            }
        } catch (Throwable ignored) {}
    }

    private static FrameScanner.Anchor resolveAnchor(HarvestContext ctx) {
        if (ctx == null || ctx.anchor == null) return null;
        try {
            return (FrameScanner.Anchor) ctx.anchor;
        } catch (Throwable ignored) {
            return null;
        }
    }
}
