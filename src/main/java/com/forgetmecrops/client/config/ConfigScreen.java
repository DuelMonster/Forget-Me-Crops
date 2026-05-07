package com.forgetmecrops.client.config;

import com.forgetmecrops.ModCommon;
import com.forgetmecrops.config.Config;
import com.forgetmecrops.config.ConfigDefaults;
import com.forgetmecrops.enums.DurabilityMode;
import com.forgetmecrops.enums.RotationMode;
import com.forgetmecrops.enums.SeedClutterMode;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.EnumControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * ConfigScreen: The in-game control panel for all of Forget-Me-Crops' settings!
 * <p>
 * Builds the full YACL screen with all server and client settings organized into
 * sensible categories. Called by both the Fabric ModMenu entrypoint and the NeoForge
 * config-screen factory — all loader-specific code does is hand us a parent Screen,
 * and we build the whole thing from there.
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
    @SuppressWarnings("null") // YACL binding setters are never passed null; primitives unbox safely
    public static Screen create(Screen parent) {
        return YetAnotherConfigLib.createBuilder()
                .title(Component.literal(ModCommon.MOD_NAME))
                .save(Config::save)
                .category(ConfigCategory.createBuilder()
                        .name(Component.translatable("forgetmecrops.config.category.server"))
                        .option(Option.<Integer>createBuilder()
                                .name(Component.translatable("forgetmecrops.config.tickInterval"))
                                .description(ConfigTooltipFactory.plain("forgetmecrops.config.tickInterval.tooltip"))
                                .binding(ConfigDefaults.TICK_INTERVAL_DEFAULT, Config::getTickInterval, Config::setTickInterval)
                                .controller(opt -> IntegerSliderControllerBuilder.create(opt).range(ConfigDefaults.TICK_INTERVAL_MIN, ConfigDefaults.TICK_INTERVAL_MAX).step(1))
                                .build())
                        .option(Option.<Integer>createBuilder()
                                .name(Component.translatable("forgetmecrops.config.frameRediscoveryInterval"))
                                .description(ConfigTooltipFactory.plain("forgetmecrops.config.frameRediscoveryInterval.tooltip"))
                                .binding(ConfigDefaults.FRAME_REDISCOVERY_INTERVAL_DEFAULT, Config::getFrameRediscoveryInterval, Config::setFrameRediscoveryInterval)
                                .controller(opt -> IntegerSliderControllerBuilder.create(opt).range(ConfigDefaults.FRAME_REDISCOVERY_INTERVAL_MIN, ConfigDefaults.FRAME_REDISCOVERY_INTERVAL_MAX).step(1))
                                .build())
                        .option(Option.<Integer>createBuilder()
                                .name(Component.translatable("forgetmecrops.config.scanRangeX"))
                                .description(ConfigTooltipFactory.plain("forgetmecrops.config.scanRangeX.tooltip"))
                                .binding(ConfigDefaults.SCAN_RANGE_X_DEFAULT, Config::getScanRangeX, Config::setScanRangeX)
                                .controller(opt -> IntegerSliderControllerBuilder.create(opt).range(ConfigDefaults.SCAN_RANGE_X_MIN, ConfigDefaults.SCAN_RANGE_X_MAX).step(1))
                                .build())
                        .option(Option.<Integer>createBuilder()
                                .name(Component.translatable("forgetmecrops.config.scanRangeZ"))
                                .description(ConfigTooltipFactory.plain("forgetmecrops.config.scanRangeZ.tooltip"))
                                .binding(ConfigDefaults.SCAN_RANGE_Z_DEFAULT, Config::getScanRangeZ, Config::setScanRangeZ)
                                .controller(opt -> IntegerSliderControllerBuilder.create(opt).range(ConfigDefaults.SCAN_RANGE_Z_MIN, ConfigDefaults.SCAN_RANGE_Z_MAX).step(1))
                                .build())
                        .option(Option.<DurabilityMode>createBuilder()
                                .name(Component.translatable("forgetmecrops.config.durabilityMode"))
                                .description(ConfigTooltipFactory.durabilityMode())
                                .binding(ConfigDefaults.DURABILITY_MODE_DEFAULT, Config::getDurabilityMode, Config::setDurabilityMode)
                                .controller(opt -> EnumControllerBuilder.create(opt)
                                        .enumClass(DurabilityMode.class)
                                        .formatValue(v -> localizedEnumName("forgetmecrops.enum.durability_mode.", v)))
                                .build())
                        .option(Option.<Boolean>createBuilder()
                                .name(Component.translatable("forgetmecrops.config.mendingProtection"))
                                .description(ConfigTooltipFactory.plain("forgetmecrops.config.mendingProtection.tooltip"))
                                .binding(ConfigDefaults.MENDING_PROTECTION_DEFAULT, Config::isMendingProtection, Config::setMendingProtection)
                                .controller(TickBoxControllerBuilder::create)
                                .build())
                        .option(Option.<Boolean>createBuilder()
                                .name(Component.translatable("forgetmecrops.config.debugLogging"))
                                .description(ConfigTooltipFactory.plain("forgetmecrops.config.debugLogging.tooltip"))
                                .binding(ConfigDefaults.DEBUG_LOGGING_DEFAULT, Config::isDebugLogging, Config::setDebugLogging)
                                .controller(TickBoxControllerBuilder::create)
                                .build())
                        .option(Option.<Integer>createBuilder()
                                .name(Component.translatable("forgetmecrops.config.chestFullCooldownTicks"))
                                .description(ConfigTooltipFactory.plain("forgetmecrops.config.chestFullCooldownTicks.tooltip"))
                                .binding(ConfigDefaults.CHEST_FULL_COOLDOWN_DEFAULT, Config::getChestFullCooldownTicks, Config::setChestFullCooldownTicks)
                                .controller(opt -> IntegerSliderControllerBuilder.create(opt).range(ConfigDefaults.CHEST_FULL_COOLDOWN_MIN, ConfigDefaults.CHEST_FULL_COOLDOWN_MAX).step(1))
                                .build())
                        .option(Option.<Integer>createBuilder()
                                .name(Component.translatable("forgetmecrops.config.maxSpiralDurationTicks"))
                                .description(ConfigTooltipFactory.plain("forgetmecrops.config.maxSpiralDurationTicks.tooltip"))
                                .binding(ConfigDefaults.MAX_SPIRAL_DEFAULT, Config::getMaxSpiralDurationTicks, Config::setMaxSpiralDurationTicks)
                                .controller(opt -> IntegerSliderControllerBuilder.create(opt).range(ConfigDefaults.MAX_SPIRAL_MIN, ConfigDefaults.MAX_SPIRAL_MAX).step(1))
                                .build())
                        .option(Option.<RotationMode>createBuilder()
                                .name(Component.translatable("forgetmecrops.config.rotationMode"))
                                .description(ConfigTooltipFactory.rotationMode())
                                .binding(ConfigDefaults.ROTATION_MODE_DEFAULT, Config::getRotationMode, Config::setRotationMode)
                                .controller(opt -> EnumControllerBuilder.create(opt)
                                        .enumClass(RotationMode.class)
                                        .formatValue(v -> localizedEnumName("forgetmecrops.enum.rotation_mode.", v)))
                                .build())
                        .option(Option.<SeedClutterMode>createBuilder()
                                .name(Component.translatable("forgetmecrops.config.seedClutterMode"))
                                .description(ConfigTooltipFactory.seedClutterMode())
                                .binding(ConfigDefaults.SEED_CLUTTER_DEFAULT, Config::getSeedClutterMode, Config::setSeedClutterMode)
                                .controller(opt -> EnumControllerBuilder.create(opt)
                                        .enumClass(SeedClutterMode.class)
                                        .formatValue(v -> localizedEnumName("forgetmecrops.enum.seed_clutter_mode.", v)))
                                .build())
                        .option(Option.<Integer>createBuilder()
                                .name(Component.translatable("forgetmecrops.config.seedReservePerType"))
                                .description(ConfigTooltipFactory.plain("forgetmecrops.config.seedReservePerType.tooltip"))
                                .binding(ConfigDefaults.SEED_RESERVE_DEFAULT, Config::getSeedReservePerType, Config::setSeedReservePerType)
                                .controller(opt -> IntegerSliderControllerBuilder.create(opt).range(ConfigDefaults.SEED_RESERVE_MIN, ConfigDefaults.SEED_RESERVE_MAX).step(1))
                                .build())
                        .build())
                .category(ConfigCategory.createBuilder()
                        .name(Component.translatable("forgetmecrops.config.category.client"))
                        .option(Option.<Boolean>createBuilder()
                                .name(Component.translatable("forgetmecrops.config.harvestParticles"))
                                .description(ConfigTooltipFactory.plain("forgetmecrops.config.harvestParticles.tooltip"))
                                .binding(ConfigDefaults.HARVEST_PARTICLES_DEFAULT, Config::isHarvestParticles, Config::setHarvestParticles)
                                .controller(TickBoxControllerBuilder::create)
                                .build())
                        .build())
                .build()
                .generateScreen(parent);
    }
}

