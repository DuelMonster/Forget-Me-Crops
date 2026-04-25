package com.fastharvester.platform.services;

// 🧾 IPlatformHelper: the contract that platform helpers lovingly implement.
// Emotional aside: it's an interface, but it still believes in teamwork.

/**
 * IPlatformHelper: The universal translator for mod platforms!
 * <p>
 * This interface defines what every platform helper must do—like a checklist for being a good citizen in the modding world.
 * </p>
 * <p>
 * Why does this matter? Because every loader has its quirks, and this interface smooths out the bumps so your code can glide across Fabric, NeoForge, and beyond.
 * </p>
 */
public interface IPlatformHelper {

    /**
    * Gets the name of the current platform. (Fabric? NeoForge? The suspense!)
     * @return The name of the current platform.
     */
    String getPlatformName();

    /**
     * Checks if a mod with the given id is loaded. Because friends don't let friends run without dependencies.
     * @param modId The mod to check if it is loaded.
     * @return True if the mod is loaded, false otherwise.
     */
    boolean isModLoaded(String modId);

    /**
     * Check if the game is currently in a development environment. (Are we safe to break things?)
     * @return True if in a development environment, false otherwise.
     */
    boolean isDevelopmentEnvironment();

    /**
     * Gets the name of the environment type as a string. ("development" or "production"—choose wisely!)
     * @return The name of the environment type.
     */
    default String getEnvironmentName() {
        return isDevelopmentEnvironment() ? "development" : "production";
    }

    /**
     * Extract enchantments from an ItemStack as a simple map from enchantment id to level.
     * Platform implementations should return accurate enchantment ids (e.g. "minecraft:unbreaking").
     * Default implementation returns an empty map.
     * @param stack the ItemStack to extract enchantments from
     * @return a map of enchantment id -> level
     */
    default java.util.Map<String, Integer> getEnchantments(net.minecraft.world.item.ItemStack stack) {
        return java.util.Collections.emptyMap();
    }

    /**
     * Platform-accurate block drops. Implementations should use the native loot/context APIs
     * to return exact drops for the provided `state` at `pos` in `level` using the provided `tool`.
     * The default common implementation returns an empty list indicating no platform-specific result.
     * @param level the world level where the block resides
     * @param pos block position to query
     * @param state the block state at the position
     * @param tool the tool ItemStack used for the query (may affect drops)
     * @return a list of ItemStacks representing drops
     */
    default java.util.List<net.minecraft.world.item.ItemStack> getBlockDrops(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state, net.minecraft.world.item.ItemStack tool) {
        return java.util.Collections.emptyList();
    }

    /**
     * Optional platform hook: update the held item on an item-frame/block-frame in the world.
     * Default implementation is a no-op; platform implementations may override to persist changes.
     * @param level the world level containing the frame
     * @param pos the position of the frame to update
     * @param stack the ItemStack to set as the held item
     */
    default void updateFrameItem(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos, net.minecraft.world.item.ItemStack stack) {
        // no-op in common
    }
}
