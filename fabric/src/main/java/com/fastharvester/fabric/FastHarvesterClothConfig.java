package com.fastharvester.fabric;

import net.minecraft.client.gui.screens.Screen;

/**
 * Fabric wrapper that delegates ClothConfig screen construction to the shared
 * `com.fastharvester.ConfigScreens` helper to avoid duplicating builder code.
 */
public final class FastHarvesterClothConfig {
    private FastHarvesterClothConfig() {}

    /**
     * Create the Fabric ClothConfig screen by delegating to the shared helper.
     *
     * @param parent parent screen
     * @return created config screen
     */
    public static Screen create(Screen parent) {
        return com.fastharvester.fabric.client.ConfigScreens.create(parent);
    }
}
