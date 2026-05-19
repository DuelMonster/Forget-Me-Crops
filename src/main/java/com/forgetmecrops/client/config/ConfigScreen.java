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
 * ConfigScreen: The in-game control panel for all of Forget-Me-Crops' settings!
 * <p>
 * Builds the full Cloth Config screen with all server and client settings organized
 * into sensible categories. Called by both the Fabric ModMenu entrypoint and the
 * NeoForge config-screen factory.
 * </p>
 */
public final class ConfigScreen {
    /** Non-instantiable utility class; use create() static method instead. */
    private ConfigScreen() {}

    /**
     * Converts an enum constant name into a human-readable localized display component.
     * For example: NORMAL → "forgetmecrops.enum.durability_mode.normal" → "Normal".
     *
     * @param prefix    the translation key prefix (e.g., "forgetmecrops.enum.durability_mode.")
     * @param enumValue the enum constant to humanize
     * @return a Component with the localized enum name
     */
    private static Component localizedEnumName(String prefix, Enum<?> enumValue) {
        return Component.translatable(prefix + enumValue.name().toLowerCase(Locale.ROOT));
    }

    /**
     * Builds and returns the config screen for Forget-Me-Crops.
     * Called by both Fabric ModMenu and NeoForge config-screen factory integrations.
     *
     * @param parent the parent screen (to return to when the config screen closes)
     * @return the generated config screen
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
                ConfigDefaults.TICK_INTERVAL_MAX,
                Config::setTickInterval,
                ConfigTooltipFactory.plain("forgetmecrops.config.tickInterval.tooltip")
        ));

        server.addEntry(new LabelTooltipIntegerListEntry(
                Component.translatable("forgetmecrops.config.frameRediscoveryInterval"),
                Config.getFrameRediscoveryInterval(),
                ConfigDefaults.FRAME_REDISCOVERY_INTERVAL_DEFAULT,
                ConfigDefaults.FRAME_REDISCOVERY_INTERVAL_MIN,
                ConfigDefaults.FRAME_REDISCOVERY_INTERVAL_MAX,
                Config::setFrameRediscoveryInterval,
                ConfigTooltipFactory.plain("forgetmecrops.config.frameRediscoveryInterval.tooltip")
        ));

        server.addEntry(new LabelTooltipIntegerListEntry(
                Component.translatable("forgetmecrops.config.scanRangeX"),
                Config.getScanRangeX(),
                ConfigDefaults.SCAN_RANGE_X_DEFAULT,
                ConfigDefaults.SCAN_RANGE_X_MIN,
                ConfigDefaults.SCAN_RANGE_X_MAX,
                Config::setScanRangeX,
                ConfigTooltipFactory.plain("forgetmecrops.config.scanRangeX.tooltip")
        ));

        server.addEntry(new LabelTooltipIntegerListEntry(
                Component.translatable("forgetmecrops.config.scanRangeZ"),
                Config.getScanRangeZ(),
                ConfigDefaults.SCAN_RANGE_Z_DEFAULT,
                ConfigDefaults.SCAN_RANGE_Z_MIN,
                ConfigDefaults.SCAN_RANGE_Z_MAX,
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
                Component.translatable("forgetmecrops.config.mendingProtection"),
                Config.isMendingProtection(),
                Config::setMendingProtection,
                ConfigTooltipFactory.plain("forgetmecrops.config.mendingProtection.tooltip")
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
                ConfigDefaults.CHEST_FULL_COOLDOWN_MAX,
                Config::setChestFullCooldownTicks,
                ConfigTooltipFactory.plain("forgetmecrops.config.chestFullCooldownTicks.tooltip")
        ));

        server.addEntry(new LabelTooltipIntegerListEntry(
                Component.translatable("forgetmecrops.config.maxSpiralDurationTicks"),
                Config.getMaxSpiralDurationTicks(),
                ConfigDefaults.MAX_SPIRAL_DEFAULT,
                ConfigDefaults.MAX_SPIRAL_MIN,
                ConfigDefaults.MAX_SPIRAL_MAX,
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
                ConfigDefaults.SEED_RESERVE_MAX,
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
}

