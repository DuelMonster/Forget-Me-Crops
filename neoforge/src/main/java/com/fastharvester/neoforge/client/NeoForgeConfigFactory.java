package com.fastharvester.neoforge.client;

import net.minecraft.client.gui.screens.Screen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.fml.ModContainer;

/**
 * Service implementation for NeoForge's config-screen SPI.
 * NeoForge discovers implementations via the standard Java ServiceLoader
 * when building the mods list; providing this class enables the
 * "Config" button for FastHarvester.
 */
public final class NeoForgeConfigFactory implements IConfigScreenFactory {
    public NeoForgeConfigFactory() {}

    @Override
    public Screen createScreen(ModContainer container, Screen parent) {
        return NeoForgeClothConfig.create(parent);
    }
}
