package com.forgetmecrops.client;

// 🔧 ConfigScreenFactoryBridge: earns Forget-Me-Crops a "Config" button on NeoForge.
// This entire file is gated by a Stonecutter condition — it only compiles on NeoForge.
// Fabric uses ModMenuEntrypoint instead.
// Registered via META-INF/services/net.neoforged.neoforge.client.gui.IConfigScreenFactory.

//? if neoforge {
/*import com.forgetmecrops.client.config.ConfigScreen;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.fml.ModContainer;

/**
 * ConfigScreenFactoryBridge: The SPI adapter that earns Forget-Me-Crops a "Config" button on NeoForge!
 * <p>
 * Implements NeoForge's {@code IConfigScreenFactory} service interface so the mods list
 * shows a "Config" button for Forget-Me-Crops. NeoForge discovers this implementation via
 * the Java {@link java.util.ServiceLoader} mechanism registered in META-INF/services.
 * Small class. One method. Pure delegation. Exactly how it should be.
 * </p>
 * /
public final class ConfigScreenFactoryBridge implements IConfigScreenFactory {
    // Public constructor required by the ServiceLoader; NeoForge instantiates this reflectively.
    public ConfigScreenFactoryBridge() {}

    /**
     * Creates the Forget-Me-Crops config screen. Called by NeoForge when the player clicks
     * the "Config" button in the mods list. Hands off to the shared config builder.
     *
     * @param container the NeoForge mod container (not used; we just need the parent screen)
     * @param parent    the currently open screen to return to when config closes
     * @return the fully built config screen, ready to display
     * /
    @Override
    public Screen createScreen(ModContainer container, Screen parent) {
        return ConfigScreen.create(parent);
    }
}*///?} else {
// This file intentionally empty on Fabric — see ModMenuEntrypoint.java instead.
//?}
