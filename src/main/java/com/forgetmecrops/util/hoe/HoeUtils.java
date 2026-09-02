package com.forgetmecrops.util.hoe;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

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
        return getEnchantmentLevel(level, tool, Enchantments.SILK_TOUCH) > 0;
    }

    /**
     * Returns the Unbreaking enchantment level on the provided tool (0 if none).
     *
     * @param level the ServerLevel; needed to resolve the enchantment from the registry
     * @param tool  the tool ItemStack to inspect
     * @return the Unbreaking level (0 = none)
     */
    public static int getUnbreakingLevel(ServerLevel level, ItemStack tool) {
        return getEnchantmentLevel(level, tool, Enchantments.UNBREAKING);
    }

    /**
     * Returns the Mending enchantment level on the provided tool (0 if none).
     *
     * @param level the ServerLevel; needed to resolve the enchantment from the registry
     * @param tool  the tool ItemStack to inspect
     * @return the Mending level (0 = none)
     */
    public static int getMendingLevel(ServerLevel level, ItemStack tool) {
        return getEnchantmentLevel(level, tool, Enchantments.MENDING);
    }

    /**
     * Resolves an enchantment holder from the level registry and reads its level on the tool.
     *
     * @param level the ServerLevel used for registry access
     * @param tool  the tool ItemStack to inspect
     * @param key   the enchantment resource key to look up
     * @return the enchantment level, or 0 if the tool/level is unusable or the lookup fails
     */
    private static int getEnchantmentLevel(ServerLevel level, ItemStack tool, ResourceKey<Enchantment> key) {
        if (level == null || tool == null || tool.isEmpty()) return 0;
        // Enchantment lookups in modern Minecraft require registry access via the level
        var holder = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
        return EnchantmentHelper.getItemEnchantmentLevel(holder, tool);
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
        return getEnchantmentLevel(level, tool, Enchantments.FORTUNE);
    }
}
