package com.forgetmecrops.fabric.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * Mod Menu bridge for opening the Forget-Me-Crops config screen on Fabric.
 */
public class ForgetMeCropsFabricModMenu implements ModMenuApi {
    /** Public constructor required by Mod Menu entrypoint instantiation. */
    public ForgetMeCropsFabricModMenu() {}

    @Override
    /**
     * @return config screen factory used by Mod Menu
     */
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> ForgetMeCropsClothConfig.create(parent);
    }
}
