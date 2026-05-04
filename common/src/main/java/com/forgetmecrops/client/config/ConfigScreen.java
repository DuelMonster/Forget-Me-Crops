package com.forgetmecrops.client.config;

import com.forgetmecrops.ModCommon;
import com.forgetmecrops.config.Config;
import com.forgetmecrops.config.ConfigDefaults;
import com.forgetmecrops.enums.DurabilityMode;
import com.forgetmecrops.enums.RotationMode;
import com.forgetmecrops.enums.SeedClutterMode;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Locale;

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
                .setTitle(Component.literal(ModCommon.MOD_NAME));

        ConfigCategory server = builder.getOrCreateCategory(Component.translatable("forgetmecrops.config.category.server"));

        server.addEntry(new LabelTooltipIntegerListEntry(
                Component.translatable("forgetmecrops.config.tickInterval"),
                Config.getTickInterval(),
                ConfigDefaults.TICK_INTERVAL_DEFAULT,
                ConfigDefaults.TICK_INTERVAL_MIN,
                Config::setTickInterval,
                ConfigTooltipFactory.plain("forgetmecrops.config.tickInterval.tooltip")
        ));

        server.addEntry(new LabelTooltipIntegerListEntry(
                Component.translatable("forgetmecrops.config.scanRangeX"),
                Config.getScanRangeX(),
                ConfigDefaults.SCAN_RANGE_X_DEFAULT,
                ConfigDefaults.SCAN_RANGE_X_MIN,
                Config::setScanRangeX,
                ConfigTooltipFactory.plain("forgetmecrops.config.scanRangeX.tooltip")
        ));

        server.addEntry(new LabelTooltipIntegerListEntry(
                Component.translatable("forgetmecrops.config.scanRangeZ"),
                Config.getScanRangeZ(),
                ConfigDefaults.SCAN_RANGE_Z_DEFAULT,
                ConfigDefaults.SCAN_RANGE_Z_MIN,
                Config::setScanRangeZ,
                ConfigTooltipFactory.plain("forgetmecrops.config.scanRangeZ.tooltip")
        ));

        server.addEntry(new LabelTooltipEnumListEntry<>(
                Component.translatable("forgetmecrops.config.durabilityMode"),
                DurabilityMode.class,
                Config.getDurabilityMode(),
                ConfigDefaults.DURABILITY_MODE_DEFAULT,
                Config::setDurabilityMode,
                enumValue -> localizedEnumName("forgetmecrops.enum.durability_mode.", enumValue),
                ConfigTooltipFactory.durabilityMode()
        ));

        server.addEntry(new LabelTooltipBooleanListEntry(
                Component.translatable("forgetmecrops.config.mendingNegation"),
                Config.isMendingNegation(),
                Config::setMendingNegation,
                ConfigTooltipFactory.plain("forgetmecrops.config.mendingNegation.tooltip")
        ));

        server.addEntry(new LabelTooltipBooleanListEntry(
                Component.translatable("forgetmecrops.config.debugLogging"),
                Config.isDebugLogging(),
                Config::setDebugLogging,
                ConfigTooltipFactory.plain("forgetmecrops.config.debugLogging.tooltip")
        ));

        server.addEntry(new LabelTooltipIntegerListEntry(
                Component.translatable("forgetmecrops.config.chestFullCooldownTicks"),
                Config.getChestFullCooldownTicks(),
                ConfigDefaults.CHEST_FULL_COOLDOWN_DEFAULT,
                ConfigDefaults.CHEST_FULL_COOLDOWN_MIN,
                Config::setChestFullCooldownTicks,
                ConfigTooltipFactory.plain("forgetmecrops.config.chestFullCooldownTicks.tooltip")
        ));

        server.addEntry(new LabelTooltipIntegerListEntry(
                Component.translatable("forgetmecrops.config.maxSpiralDurationTicks"),
                Config.getMaxSpiralDurationTicks(),
                ConfigDefaults.MAX_SPIRAL_DEFAULT,
                ConfigDefaults.MAX_SPIRAL_MIN,
                Config::setMaxSpiralDurationTicks,
                ConfigTooltipFactory.plain("forgetmecrops.config.maxSpiralDurationTicks.tooltip")
        ));

        server.addEntry(new LabelTooltipEnumListEntry<>(
                Component.translatable("forgetmecrops.config.rotationMode"),
                RotationMode.class,
                Config.getRotationMode(),
                ConfigDefaults.ROTATION_MODE_DEFAULT,
                Config::setRotationMode,
                enumValue -> localizedEnumName("forgetmecrops.enum.rotation_mode.", enumValue),
                ConfigTooltipFactory.rotationMode()
        ));

        server.addEntry(new LabelTooltipEnumListEntry<>(
                Component.translatable("forgetmecrops.config.seedClutterMode"),
                SeedClutterMode.class,
                Config.getSeedClutterMode(),
                ConfigDefaults.SEED_CLUTTER_DEFAULT,
                Config::setSeedClutterMode,
                enumValue -> localizedEnumName("forgetmecrops.enum.seed_clutter_mode.", enumValue),
                ConfigTooltipFactory.seedClutterMode()
        ));

        server.addEntry(new LabelTooltipIntegerListEntry(
                Component.translatable("forgetmecrops.config.seedReservePerType"),
                Config.getSeedReservePerType(),
                ConfigDefaults.SEED_RESERVE_DEFAULT,
                ConfigDefaults.SEED_RESERVE_MIN,
                Config::setSeedReservePerType,
                ConfigTooltipFactory.plain("forgetmecrops.config.seedReservePerType.tooltip")
        ));

        ConfigCategory client = builder.getOrCreateCategory(Component.translatable("forgetmecrops.config.category.client"));
        client.addEntry(new LabelTooltipBooleanListEntry(
                Component.translatable("forgetmecrops.config.harvestParticles"),
                Config.isHarvestParticles(),
                Config::setHarvestParticles,
                ConfigTooltipFactory.plain("forgetmecrops.config.harvestParticles.tooltip")
        ));

        builder.setSavingRunnable(Config::save);
        return builder.build();
    }

        private static Component localizedEnumName(String keyPrefix, Enum<?> enumValue) {
                return Component.translatable(keyPrefix + enumValue.name().toLowerCase(Locale.ROOT));
        }
}
