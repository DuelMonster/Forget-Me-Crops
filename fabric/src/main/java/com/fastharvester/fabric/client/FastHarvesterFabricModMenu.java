package com.fastharvester.fabric.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

public class FastHarvesterFabricModMenu implements ModMenuApi {
    public FastHarvesterFabricModMenu() {}

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> FastHarvesterClothConfig.create(parent);
    }
}
