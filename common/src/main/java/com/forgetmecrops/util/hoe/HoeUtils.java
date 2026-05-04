package com.forgetmecrops.util.hoe;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.registries.Registries;

/**
 * HoeUtils: Quick enchantment checks for the hoe that powers your farm!
 * <p>
 * Provides dead-simple helpers to ask "does this hoe have Silk Touch?" and
 * "how much Fortune does this hoe have?" — without making callers wade through
 * Minecraft's increasingly elaborate registry and EnchantmentHelper APIs.
 * </p>
 * <p>
 * Why do we need this? Because enchantment lookups in modern Minecraft require registry
 * access, a ServerLevel reference, a minor prayer, and about three more lines than you'd
 * expect. This class hides all that behind two clean method names.
 * </p>
 */
public class HoeUtils {
    /** Utility class. No instances. The hoe doesn't need company. */
    private HoeUtils() {}

    /**
     * Returns true if the supplied tool has the Silk Touch enchantment.
     * Silk Touch means drops come back as blocks instead of crops — relevant for
     * deciding whether to replant or just collect the whole plant.
     *
     * @param level the ServerLevel; needed to resolve the enchantment from the registry
     * @param tool  the tool ItemStack to inspect
     * @return true if the tool gleams with Silk Touch, false if it's just a regular dirty hoe
     */
    public static boolean hasSilkTouch(ServerLevel level, ItemStack tool) {
        // Look up the Silk Touch enchantment holder through the registry — because Mojang said so
        var silkTouch = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SILK_TOUCH);
        return EnchantmentHelper.getItemEnchantmentLevel(silkTouch, tool) > 0;
    }

    /**
     * Returns the Fortune enchantment level on the provided tool (0 if none).
     * Higher Fortune = more drops per harvest = more wheat = more bread = happiness.
     * Fortune III on a hoe is basically the mod's best friend.
     *
     * @param level the ServerLevel; needed to resolve the enchantment from the registry
     * @param tool  the tool ItemStack to inspect
     * @return the Fortune level (0 = none, 1-3 = you're eating well tonight)
     */
    public static int getFortuneLevel(ServerLevel level, ItemStack tool) {
        // Same registry dance as Silk Touch — Minecraft enchantment lookups are a whole thing
        var fortune = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.FORTUNE);
        return EnchantmentHelper.getItemEnchantmentLevel(fortune, tool);
    }
}
