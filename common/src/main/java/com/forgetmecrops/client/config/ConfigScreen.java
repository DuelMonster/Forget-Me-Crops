package com.forgetmecrops.client.config;

import com.forgetmecrops.config.Config;
import com.forgetmecrops.config.ConfigDescriptors;
import com.forgetmecrops.enums.DurabilityMode;
import com.forgetmecrops.enums.RotationMode;
import com.forgetmecrops.enums.SeedClutterMode;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Shared Cloth Config screen builder used by both Fabric and NeoForge loaders.
 */
public final class ConfigScreen {
        private ConfigScreen() {}

    /**
     * Build the config screen.
     *
     * @param parent parent screen passed to the Cloth Config builder
     * @return generated config screen
     */
    public static Screen create(Screen parent) {
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.literal(ConfigDescriptors.TITLE));

        ConfigCategory server = builder.getOrCreateCategory(Component.literal(ConfigDescriptors.CATEGORY_SERVER));

        server.addEntry(new LabelTooltipIntegerListEntry(
                Component.literal(ConfigDescriptors.TICK_INTERVAL_LABEL),
                Config.getTickInterval(),
                ConfigDescriptors.TICK_INTERVAL_DEFAULT,
                ConfigDescriptors.TICK_INTERVAL_MIN,
                Config::setTickInterval,
                ConfigTooltipFactory.plain(ConfigDescriptors.TICK_INTERVAL_TOOLTIP)
        ));

        server.addEntry(new LabelTooltipIntegerListEntry(
                Component.literal(ConfigDescriptors.SCAN_RANGE_X_LABEL),
                Config.getScanRangeX(),
                ConfigDescriptors.SCAN_RANGE_X_DEFAULT,
                ConfigDescriptors.SCAN_RANGE_X_MIN,
                Config::setScanRangeX,
                ConfigTooltipFactory.plain(ConfigDescriptors.SCAN_RANGE_X_TOOLTIP)
        ));

        server.addEntry(new LabelTooltipIntegerListEntry(
                Component.literal(ConfigDescriptors.SCAN_RANGE_Z_LABEL),
                Config.getScanRangeZ(),
                ConfigDescriptors.SCAN_RANGE_Z_DEFAULT,
                ConfigDescriptors.SCAN_RANGE_Z_MIN,
                Config::setScanRangeZ,
                ConfigTooltipFactory.plain(ConfigDescriptors.SCAN_RANGE_Z_TOOLTIP)
        ));

        server.addEntry(new LabelTooltipEnumListEntry<>(
                Component.literal(ConfigDescriptors.DURABILITY_MODE_LABEL),
                DurabilityMode.class,
                Config.getDurabilityMode(),
                ConfigDescriptors.DURABILITY_MODE_DEFAULT,
                Config::setDurabilityMode,
                ConfigTooltipFactory.durabilityMode()
        ));

        server.addEntry(new LabelTooltipBooleanListEntry(
                Component.literal(ConfigDescriptors.MENDING_NEGATION_LABEL),
                Config.isMendingNegation(),
                Config::setMendingNegation,
                ConfigTooltipFactory.plain(ConfigDescriptors.MENDING_NEGATION_TOOLTIP)
        ));

        server.addEntry(new LabelTooltipBooleanListEntry(
                Component.literal(ConfigDescriptors.DEBUG_LOGGING_LABEL),
                Config.isDebugLogging(),
                Config::setDebugLogging,
                ConfigTooltipFactory.plain(ConfigDescriptors.DEBUG_LOGGING_TOOLTIP)
        ));

        server.addEntry(new LabelTooltipIntegerListEntry(
                Component.literal(ConfigDescriptors.CHEST_FULL_COOLDOWN_LABEL),
                Config.getChestFullCooldownTicks(),
                ConfigDescriptors.CHEST_FULL_COOLDOWN_DEFAULT,
                ConfigDescriptors.CHEST_FULL_COOLDOWN_MIN,
                Config::setChestFullCooldownTicks,
                ConfigTooltipFactory.plain(ConfigDescriptors.CHEST_FULL_COOLDOWN_TOOLTIP)
        ));

        server.addEntry(new LabelTooltipIntegerListEntry(
                Component.literal(ConfigDescriptors.MAX_SPIRAL_DURATION_LABEL),
                Config.getMaxSpiralDurationTicks(),
                ConfigDescriptors.MAX_SPIRAL_DEFAULT,
                ConfigDescriptors.MAX_SPIRAL_MIN,
                Config::setMaxSpiralDurationTicks,
                ConfigTooltipFactory.plain(ConfigDescriptors.MAX_SPIRAL_TOOLTIP)
        ));

        server.addEntry(new LabelTooltipEnumListEntry<>(
                Component.literal(ConfigDescriptors.ROTATION_MODE_LABEL),
                RotationMode.class,
                Config.getRotationMode(),
                ConfigDescriptors.ROTATION_MODE_DEFAULT,
                Config::setRotationMode,
                ConfigTooltipFactory.rotationMode()
        ));

        server.addEntry(new LabelTooltipEnumListEntry<>(
                Component.literal(ConfigDescriptors.SEED_CLUTTER_LABEL),
                SeedClutterMode.class,
                Config.getSeedClutterMode(),
                ConfigDescriptors.SEED_CLUTTER_DEFAULT,
                Config::setSeedClutterMode,
                ConfigTooltipFactory.seedClutterMode()
        ));

        server.addEntry(new LabelTooltipIntegerListEntry(
                Component.literal(ConfigDescriptors.SEED_RESERVE_LABEL),
                Config.getSeedReservePerType(),
                ConfigDescriptors.SEED_RESERVE_DEFAULT,
                ConfigDescriptors.SEED_RESERVE_MIN,
                Config::setSeedReservePerType,
                ConfigTooltipFactory.plain(ConfigDescriptors.SEED_RESERVE_TOOLTIP)
        ));

        ConfigCategory client = builder.getOrCreateCategory(Component.literal(ConfigDescriptors.CATEGORY_CLIENT));
        client.addEntry(new LabelTooltipBooleanListEntry(
                Component.literal(ConfigDescriptors.HARVEST_PARTICLES_LABEL),
                Config.isHarvestParticles(),
                Config::setHarvestParticles,
                ConfigTooltipFactory.plain(ConfigDescriptors.HARVEST_PARTICLES_TOOLTIP)
        ));

        builder.setSavingRunnable(Config::save);
        return builder.build();
    }
}
