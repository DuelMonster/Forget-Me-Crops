package com.fastharvester.neoforge;

import com.fastharvester.Config;
import com.fastharvester.enums.DurabilityMode;
import com.fastharvester.enums.RotationMode;
import com.fastharvester.enums.SeedClutterMode;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.config.ConfigSpec;

public class FastHarvesterNeoForgeConfig {
    public static final ConfigSpec.Builder BUILDER = new ConfigSpec.Builder();
    public static final ConfigSpec SPEC;

    static {
        BUILDER.push("FastHarvester");
        BUILDER.defineInRange("tickInterval", 300, 1, Integer.MAX_VALUE);
        BUILDER.defineInRange("frameRediscoveryInterval", 100, 1, Integer.MAX_VALUE);
        BUILDER.defineInRange("scanRange", 4, 1, 64);
        BUILDER.defineEnum("durabilityMode", DurabilityMode.NORMAL);
        BUILDER.define("mendingNegation", true);
        BUILDER.define("debugLogging", false);
        BUILDER.defineInRange("chestFullCooldownTicks", 100, 0, Integer.MAX_VALUE);
        BUILDER.defineInRange("maxSpiralDurationTicks", 100, 1, Integer.MAX_VALUE);
        BUILDER.defineEnum("rotationMode", RotationMode.FOLLOW_HARVEST_SPIRAL);
        BUILDER.defineEnum("seedClutterMode", SeedClutterMode.REDUCED);
        BUILDER.defineInRange("seedReservePerType", 80, 0, 9999);
        BUILDER.pop();
        SPEC = BUILDER.build();
    }

    public static void register() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SPEC);
    }

    public static void onConfigReload(ModConfigEvent.Reloading event) {
        ModConfig config = event.getConfig();
        if (config.getSpec() != SPEC) return;
        var conf = config.getConfigData();
        Config.tickInterval = conf.getInt("FastHarvester.tickInterval");
        Config.frameRediscoveryInterval = conf.getInt("FastHarvester.frameRediscoveryInterval");
        Config.scanRange = conf.getInt("FastHarvester.scanRange");
        Config.durabilityMode = DurabilityMode.valueOf(conf.getEnum("FastHarvester.durabilityMode", DurabilityMode.NORMAL.name()));
        Config.mendingNegation = conf.getBoolean("FastHarvester.mendingNegation");
        Config.debugLogging = conf.getBoolean("FastHarvester.debugLogging");
        Config.chestFullCooldownTicks = conf.getInt("FastHarvester.chestFullCooldownTicks");
        Config.maxSpiralDurationTicks = conf.getInt("FastHarvester.maxSpiralDurationTicks");
        Config.rotationMode = RotationMode.valueOf(conf.getEnum("FastHarvester.rotationMode", RotationMode.FOLLOW_HARVEST_SPIRAL.name()));
        Config.seedClutterMode = SeedClutterMode.valueOf(conf.getEnum("FastHarvester.seedClutterMode", SeedClutterMode.REDUCED.name()));
        Config.seedReservePerType = conf.getInt("FastHarvester.seedReservePerType");
    }
}
