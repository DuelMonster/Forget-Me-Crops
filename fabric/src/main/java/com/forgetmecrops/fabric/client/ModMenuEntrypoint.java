package com.forgetmecrops.fabric.client;

import com.forgetmecrops.client.config.ConfigScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * ModMenuEntrypoint: The handshake that earns Forget-Me-Crops a "Config" button in Mod Menu!
 * <p>
 * Implements {@link com.terraformersmc.modmenu.api.ModMenuApi} so that when Mod Menu is
 * installed on Fabric, the mods list shows a dedicated "Config" button that opens the
 * shared Cloth Config screen. Without this, players would have to manually edit TOML files
 * like some kind of pioneer. We can do better. We have a config screen.
 * </p>
 */
public class ModMenuEntrypoint implements ModMenuApi {
    /** Public constructor required by Mod Menu's entrypoint instantiation. */
    public ModMenuEntrypoint() {}

    /**
     * Returns the config screen factory that Mod Menu will invoke when the "Config" button is clicked.
     * Delegates directly to {@link com.forgetmecrops.client.config.ConfigScreen#create}.
     *
     * @return the factory function that builds the Forget-Me-Crops config screen
     */
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return ConfigScreen::create;
    }
}
