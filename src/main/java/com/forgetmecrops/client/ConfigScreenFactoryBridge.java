package com.forgetmecrops.client;

// 🔧 ConfigScreenFactoryBridge: earns Forget-Me-Crops a "Config" button on NeoForge.
// This entire file is gated by a Stonecutter condition — it only compiles on NeoForge.
// Fabric uses ModMenuEntrypoint instead.
// Registered from ModEntry via ModContainer#registerExtensionPoint(IConfigScreenFactory, ...).

//? if neoforge {
/*import com.forgetmecrops.client.config.ConfigScreen;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.fml.ModContainer;

public final class ConfigScreenFactoryBridge implements IConfigScreenFactory {
    public ConfigScreenFactoryBridge() {}

    @Override
    public Screen createScreen(ModContainer container, Screen parent) {
        return ConfigScreen.create(parent);
    }
}*///?} else {
// This file intentionally empty on Fabric — see ModMenuEntrypoint.java instead.
//?}
