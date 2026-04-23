package com.fastharvester.neoforge;

import net.minecraft.client.gui.screens.Screen;

/**
 * NeoForge wrapper that delegates ClothConfig screen construction to the shared
 * `com.fastharvester.ConfigScreens` helper to avoid duplicating builder code.
 */
public final class NeoForgeClothConfig {
    private NeoForgeClothConfig() {}

    public static Screen create(Screen parent) {
        return com.fastharvester.ConfigScreens.create(parent);
    }
}
