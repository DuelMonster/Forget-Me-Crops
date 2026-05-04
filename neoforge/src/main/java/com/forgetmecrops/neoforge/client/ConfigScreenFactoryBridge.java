package com.forgetmecrops.neoforge.client;

import com.forgetmecrops.client.config.ConfigScreen;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.fml.ModContainer;

/**
 * ConfigScreenFactoryBridge: The SPI adapter that earns Forget-Me-Crops a "Config" button on NeoForge!
 * <p>
 * Implements NeoForge's {@code IConfigScreenFactory} service interface so the mods list
 * shows a "Config" button for Forget-Me-Crops. NeoForge discovers this implementation via
 * the Java {@link java.util.ServiceLoader} mechanism registered in META-INF/services.
 * When invoked, it delegates directly to {@link com.forgetmecrops.client.config.ConfigScreen#create}
 * and lets the shared Cloth Config UI take over from there.
 * </p>
 * <p>
 * Small class. One method. No drama. Pure delegation. Exactly how it should be.
 * </p>
 */
public final class ConfigScreenFactoryBridge implements IConfigScreenFactory {
    // Public constructor required by the ServiceLoader; NeoForge instantiates this reflectively.
    public ConfigScreenFactoryBridge() {}

    /**
     * Creates the Forget-Me-Crops config screen. Called by NeoForge when the player clicks
     * the "Config" button in the mods list. Hands off to the shared Cloth Config builder.
     *
     * @param container the NeoForge mod container (not used here; we just need the parent screen)
     * @param parent    the currently open screen to return to when the config screen is closed
     * @return the fully built config screen, ready to display
     */
    @Override
    public Screen createScreen(ModContainer container, Screen parent) {
        return ConfigScreen.create(parent);
    }
}
