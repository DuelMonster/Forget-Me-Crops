package com.fastharvester;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.registries.Registries;

/**
 * HoeUtils: The unsung hero of tool management!
 * <p>
 * This class is destined to hold all the clever code for working with hoes. Loader-agnostic, so every farmer gets a fair shake.
 * </p>
 * <p>
 * Why does this matter? Because a well-cared-for hoe is a happy hoe, and happy hoes harvest more crops.
 * </p>
 */
// 🌱 Extra friendly aside: treat hoes kindly and they'll last longer. Also, sing to them if you're theatrical.
public class HoeUtils {
    /**
     * Creates a new HoeUtils. For now, it's just a vessel for future wisdom!
     */
    public HoeUtils() {}

    public static boolean hasSilkTouch(ServerLevel level, ItemStack tool) {
        var silkTouch = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SILK_TOUCH);
        return EnchantmentHelper.getItemEnchantmentLevel(silkTouch, tool) > 0;
    }

    public static int getFortuneLevel(ServerLevel level, ItemStack tool) {
        var fortune = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.FORTUNE);
        return EnchantmentHelper.getItemEnchantmentLevel(fortune, tool);
    }
}
