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
    /** Utility class: do not instantiate. */
    private HoeUtils() {}
    /**
     * Returns whether the provided `tool` has Silk Touch applied in the given server registry context.
     * @param level the `ServerLevel` used to resolve enchantment registry
     * @param tool the tool ItemStack to inspect
     * @return true when the `tool` has Silk Touch enchantment
     */
    public static boolean hasSilkTouch(ServerLevel level, ItemStack tool) {
        var silkTouch = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SILK_TOUCH);
        return EnchantmentHelper.getItemEnchantmentLevel(silkTouch, tool) > 0;
    }

    /**
     * Get the Fortune enchantment level present on the provided `tool`.
     * @param level the `ServerLevel` used to resolve enchantment registry
     * @param tool the tool ItemStack to inspect
     * @return the integer Fortune level (0 when not present)
     */
    public static int getFortuneLevel(ServerLevel level, ItemStack tool) {
        var fortune = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.FORTUNE);
        return EnchantmentHelper.getItemEnchantmentLevel(fortune, tool);
    }
}
