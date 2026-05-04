package com.forgetmecrops.fabric.client;

import com.forgetmecrops.client.config.ConfigScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * Mod Menu bridge for opening the Forget-Me-Crops config screen on Fabric.
 */
public class ModMenuEntrypoint implements ModMenuApi {
    /** Public constructor required by Mod Menu entrypoint instantiation. */
    public ModMenuEntrypoint() {}

    @Override
    /**
     * @return config screen factory used by Mod Menu
     */
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return ConfigScreen::create;
    }
}
