package com.fastharvester.fabric;

import com.fastharvester.Config;
import com.fastharvester.FastHarvester;
import net.fabricmc.api.ModInitializer;

public class FastHarvesterFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        Config.load();
        FastHarvester.init();
    }
}
