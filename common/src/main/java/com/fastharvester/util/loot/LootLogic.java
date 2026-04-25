package com.fastharvester.util.loot;

import com.fastharvester.util.hoe.HoeUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * LootLogic: The loot goblin of the farm. Figures out what drops when you break a block, and makes sure fortune and silk touch are respected.
 */
public class LootLogic {

    /** Utility class: do not instantiate. */
    private LootLogic() {}

    // Cached fake hoes for fortune 0–3, initialised on first harvest.
    private static ItemStack[] fortuneHoes;

    private static ItemStack fortuneHoe(ServerLevel level, int fortune) {
        if (fortuneHoes == null) {
            fortuneHoes = new ItemStack[4];
            fortuneHoes[0] = new ItemStack(Items.IRON_HOE);
            var fortuneHolder = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.FORTUNE);
            for (int i = 1; i <= 3; i++) {
                ItemStack hoe = new ItemStack(Items.IRON_HOE);
                hoe.enchant(fortuneHolder, i);
                fortuneHoes[i] = hoe;
            }
        }
        return fortuneHoes[Math.min(Math.max(fortune, 0), 3)];
    }

    /**
     * Gets the drops for a block, respecting fortune and silk touch.
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
