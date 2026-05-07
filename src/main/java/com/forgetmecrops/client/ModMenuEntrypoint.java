package com.forgetmecrops.client;

// 🌿 ModMenuEntrypoint: the Fabric-side handshake that earns Forget-Me-Crops a "Config" button.
// This entire file is gated by a Stonecutter condition — it only compiles on Fabric.
// NeoForge uses ConfigScreenFactoryBridge (via SPI) instead.

//? if fabric {
import com.forgetmecrops.client.config.ConfigScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

// ModMenuEntrypoint: Fabric-side handshake for the Mod Menu "Config" button.
// Implements ModMenuApi so the mods list shows a "Config" button on Fabric.
public class ModMenuEntrypoint implements ModMenuApi {
    public ModMenuEntrypoint() {}

    // Returns the factory that Mod Menu invokes when "Config" is clicked.
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return ConfigScreen::create;
    }
}
//?} else {
/*
// This file intentionally empty on NeoForge — see ConfigScreenFactoryBridge.java instead.
*/ //?}
