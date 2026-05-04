package com.forgetmecrops.util.loot;

import com.forgetmecrops.util.hoe.HoeUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.registries.Registries;

import java.util.ArrayList;
import java.util.List;

/**
 * LootLogic: The loot goblin of the farm — keeper of drop tables and enchantment respect!
 * <p>
 * Figures out exactly what falls out of a crop block when you break it, accounting for
 * Fortune (more drops!) and Silk Touch (fancy whole-block drops for melons!). Uses
 * Minecraft's native loot context system to get platform-accurate results.
 * </p>
 * <p>
 * Why not just call Block.getDrops() directly? Because Fortune needs a tool with the right
 * enchantment level injected into the loot context, and Silk Touch requires special-casing
 * for things like melons. LootLogic handles all of that so callers don't have to.
 * Your wheat will thank you for the Fortune III treatment.
 * </p>
 */
public class LootLogic {

    // Utility class. The loot goblin works alone and does not accept company.
    private LootLogic() {}

    /**
     * Builds a synthetic iron hoe ItemStack with the given Fortune level applied.
     * Used to inject the correct Fortune enchantment into the loot context so the
     * native block-drop logic applies the right bonuses. Fortune level is clamped to [0, 3]
     * because Fortune IV hoes are not a thing, despite what some players believe.
     *
     * @param level the server level (needed for enchantment registry access)
     * @param fortune the Fortune level to bake in (clamped 0–3)
     * @return an iron hoe with Fortune applied, or a plain iron hoe if fortune is 0
     */
    private static ItemStack fortuneHoe(ServerLevel level, int fortune) {
        int clamped = Math.min(Math.max(fortune, 0), 3);
        ItemStack hoe = new ItemStack(Items.IRON_HOE);
        if (clamped <= 0) return hoe;
        var fortuneHolder = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.FORTUNE);
        hoe.enchant(fortuneHolder, clamped);
        return hoe;
    }

    /**
     * Gets the drops for a block, respecting fortune and silk touch.
     *
     * @param level server level used for loot context and registry lookups
     * @param pos block position being harvested
     * @param state block state of the harvested block
     * @param hoe the tool used (checked for fortune/silk-touch)
     * @return list of ItemStack drops produced by breaking the block
     */
    public static List<ItemStack> getBlockDrops(ServerLevel level, BlockPos pos, BlockState state, ItemStack hoe) {
        if (state.getBlock() == Blocks.MELON && HoeUtils.hasSilkTouch(level, hoe)) {
            List<ItemStack> drops = new ArrayList<>();
            drops.add(new ItemStack(Blocks.MELON.asItem()));
            return drops;
        }

        int fortune = HoeUtils.getFortuneLevel(level, hoe);

        LootParams.Builder params = new LootParams.Builder(level)
                .withParameter(LootContextParams.TOOL, fortuneHoe(level, fortune))
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                .withParameter(LootContextParams.BLOCK_STATE, state);

        return state.getDrops(params);
    }
}
