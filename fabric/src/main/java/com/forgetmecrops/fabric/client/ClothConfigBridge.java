package com.forgetmecrops.fabric.client;

import net.minecraft.client.gui.screens.Screen;

/**
 * Tiny wrapper that exposes the config screen factory target class.
 */
public final class ClothConfigBridge {
    private ClothConfigBridge() {}

    /**
     * Creates the in-game config screen.
     *
     * @param parent parent screen
     * @return generated config screen
     */
    public static Screen create(Screen parent) {
        return com.forgetmecrops.fabric.client.ConfigScreens.create(parent);
    }
}
