package com.fastharvester.fabric.client;

import net.minecraft.client.gui.screens.Screen;

public final class FastHarvesterClothConfig {
    private FastHarvesterClothConfig() {}

    public static Screen create(Screen parent) {
        return com.fastharvester.fabric.client.ConfigScreens.create(parent);
    }
}
