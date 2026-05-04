package com.forgetmecrops.neoforge.client;

import net.minecraft.client.gui.screens.Screen;

/**
 * NeoForge wrapper that delegates ClothConfig screen construction to the shared
 * `com.forgetmecrops.ConfigScreens` helper to avoid duplicating builder code.
 */
public final class ClothConfigBridge {
    private ClothConfigBridge() {}

    /**
     * Create the NeoForge ClothConfig screen by delegating to the shared helper.
     *
     * @param parent parent screen
     * @return created config screen
     */
    public static Screen create(Screen parent) {
        return com.forgetmecrops.neoforge.client.ConfigScreens.create(parent);
    }
}
