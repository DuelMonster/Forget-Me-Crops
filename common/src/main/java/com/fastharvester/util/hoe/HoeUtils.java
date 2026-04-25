package com.fastharvester.util.hoe;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.registries.Registries;

/**
 * HoeUtils: Helpers for inspecting hoe enchantments.
 */
public class HoeUtils {
    /** Utility class: do not instantiate. */
    private HoeUtils() {}

    public static boolean hasSilkTouch(ServerLevel level, ItemStack tool) {
        var silkTouch = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SILK_TOUCH);
        return EnchantmentHelper.getItemEnchantmentLevel(silkTouch, tool) > 0;
    }

    public static int getFortuneLevel(ServerLevel level, ItemStack tool) {
        var fortune = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.FORTUNE);
        return EnchantmentHelper.getItemEnchantmentLevel(fortune, tool);
    }
}
