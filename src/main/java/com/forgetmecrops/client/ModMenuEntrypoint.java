package com.forgetmecrops.client;

// 🌿 ModMenuEntrypoint: the Fabric-side handshake that earns Forget-Me-Crops a "Config" button.
// This entire file is gated by a Stonecutter condition — it only compiles on Fabric.
// NeoForge uses ConfigScreenFactoryBridge (via SPI) instead.

//? if fabric {
import com.forgetmecrops.client.config.ConfigScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * ModMenuEntrypoint: The handshake that earns Forget-Me-Crops a "Config" button in Mod Menu!
 * <p>
 * Implements {@link ModMenuApi} so that when Mod Menu is installed on Fabric, the mods
 * list shows a dedicated "Config" button that opens the shared config screen.
 * Without this, players would have to edit TOML files manually like pioneers.
 * We can do better.
 * </p>
 */
public class ModMenuEntrypoint implements ModMenuApi {
    /** Public constructor required by Mod Menu's entrypoint instantiation. */
    public ModMenuEntrypoint() {}

    /**
     * Returns the config screen factory that Mod Menu will invoke when "Config" is clicked.
     * Delegates directly to {@link ConfigScreen#create}.
     *
     * @return the factory function that builds the Forget-Me-Crops config screen
     */
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return ConfigScreen::create;
    }
}
//?} else {
/*
// This file intentionally empty on NeoForge — see ConfigScreenFactoryBridge.java instead.
*///?}
