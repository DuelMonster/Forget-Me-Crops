package com.fastharvester.fabric.client;

import com.fastharvester.enums.DurabilityMode;
import com.fastharvester.enums.RotationMode;
import com.fastharvester.enums.SeedClutterMode;
import com.fastharvester.config.Config;
import com.fastharvester.config.ConfigDescriptors;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Fabric: centralized ClothConfig builder used by the Fabric module.
 *
 * Kept in the Fabric module so the common module doesn't need a runtime
 * dependency on the ClothConfig API.
 */
public final class ConfigScreens {
        private ConfigScreens() {}

        /**
         * Create the Fabric config screen using the shared builder.
         *
         * @param parent parent screen passed to the ClothConfig builder
         * @return constructed config screen
         */
        public static Screen create(Screen parent) {
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.literal(ConfigDescriptors.TITLE));

        ConfigEntryBuilder entryBuilder = builder.entryBuilder();

        // Server settings category
        ConfigCategory server = builder.getOrCreateCategory(Component.literal(ConfigDescriptors.CATEGORY_SERVER));

        server.addEntry(entryBuilder.startIntField(Component.literal(ConfigDescriptors.TICK_INTERVAL_LABEL), Config.tickInterval)
                .setDefaultValue(ConfigDescriptors.TICK_INTERVAL_DEFAULT)
                .setMin(ConfigDescriptors.TICK_INTERVAL_MIN)
                .setSaveConsumer(v -> Config.tickInterval = v)
                .setTooltip(Component.literal(ConfigDescriptors.TICK_INTERVAL_TOOLTIP))
                .build());

        server.addEntry(entryBuilder.startIntField(Component.literal(ConfigDescriptors.SCAN_RANGE_X_LABEL), Config.scanRangeX)
                .setDefaultValue(ConfigDescriptors.SCAN_RANGE_X_DEFAULT)
                .setMin(ConfigDescriptors.SCAN_RANGE_X_MIN)
                .setSaveConsumer(v -> Config.scanRangeX = v)
                .setTooltip(Component.literal(ConfigDescriptors.SCAN_RANGE_X_TOOLTIP))
                .build());

        server.addEntry(entryBuilder.startIntField(Component.literal(ConfigDescriptors.SCAN_RANGE_Z_LABEL), Config.scanRangeZ)
                .setDefaultValue(ConfigDescriptors.SCAN_RANGE_Z_DEFAULT)
                .setMin(ConfigDescriptors.SCAN_RANGE_Z_MIN)
                .setSaveConsumer(v -> Config.scanRangeZ = v)
                .setTooltip(Component.literal(ConfigDescriptors.SCAN_RANGE_Z_TOOLTIP))
                .build());

        server.addEntry(entryBuilder.startEnumSelector(Component.literal(ConfigDescriptors.DURABILITY_MODE_LABEL), DurabilityMode.class, Config.durabilityMode)
                .setDefaultValue(ConfigDescriptors.DURABILITY_MODE_DEFAULT)
                .setSaveConsumer(v -> Config.durabilityMode = v)
                .setTooltip(Component.literal(ConfigDescriptors.DURABILITY_MODE_TOOLTIP))
                .build());

        server.addEntry(entryBuilder.startBooleanToggle(Component.literal(ConfigDescriptors.MENDING_NEGATION_LABEL), Config.mendingNegation)
                .setSaveConsumer(v -> Config.mendingNegation = v)
                .setTooltip(Component.literal(ConfigDescriptors.MENDING_NEGATION_TOOLTIP))
                .build());

        server.addEntry(entryBuilder.startBooleanToggle(Component.literal(ConfigDescriptors.DEBUG_LOGGING_LABEL), Config.debugLogging)
                .setSaveConsumer(v -> Config.debugLogging = v)
                .setTooltip(Component.literal(ConfigDescriptors.DEBUG_LOGGING_TOOLTIP))
                .build());

        server.addEntry(entryBuilder.startIntField(Component.literal(ConfigDescriptors.CHEST_FULL_COOLDOWN_LABEL), Config.chestFullCooldownTicks)
                .setDefaultValue(ConfigDescriptors.CHEST_FULL_COOLDOWN_DEFAULT)
                .setMin(ConfigDescriptors.CHEST_FULL_COOLDOWN_MIN)
                .setSaveConsumer(v -> Config.chestFullCooldownTicks = v)
                .setTooltip(Component.literal(ConfigDescriptors.CHEST_FULL_COOLDOWN_TOOLTIP))
                .build());

        server.addEntry(entryBuilder.startIntField(Component.literal(ConfigDescriptors.MAX_SPIRAL_DURATION_LABEL), Config.maxSpiralDurationTicks)
                .setDefaultValue(ConfigDescriptors.MAX_SPIRAL_DEFAULT)
                .setMin(ConfigDescriptors.MAX_SPIRAL_MIN)
                .setSaveConsumer(v -> Config.maxSpiralDurationTicks = v)
                .setTooltip(Component.literal(ConfigDescriptors.MAX_SPIRAL_TOOLTIP))
                .build());

        server.addEntry(entryBuilder.startEnumSelector(Component.literal(ConfigDescriptors.ROTATION_MODE_LABEL), RotationMode.class, Config.rotationMode)
                .setDefaultValue(ConfigDescriptors.ROTATION_MODE_DEFAULT)
                .setSaveConsumer(v -> Config.rotationMode = v)
                .setTooltip(Component.literal(ConfigDescriptors.ROTATION_MODE_TOOLTIP))
                .build());

        server.addEntry(entryBuilder.startEnumSelector(Component.literal(ConfigDescriptors.SEED_CLUTTER_LABEL), SeedClutterMode.class, Config.seedClutterMode)
                .setDefaultValue(ConfigDescriptors.SEED_CLUTTER_DEFAULT)
                .setSaveConsumer(v -> Config.seedClutterMode = v)
                .setTooltip(Component.literal(ConfigDescriptors.SEED_CLUTTER_TOOLTIP))
                .build());

        server.addEntry(entryBuilder.startIntField(Component.literal(ConfigDescriptors.SEED_RESERVE_LABEL), Config.seedReservePerType)
                .setDefaultValue(ConfigDescriptors.SEED_RESERVE_DEFAULT)
                .setMin(ConfigDescriptors.SEED_RESERVE_MIN)
                .setSaveConsumer(v -> Config.seedReservePerType = v)
                .setTooltip(Component.literal(ConfigDescriptors.SEED_RESERVE_TOOLTIP))
                .build());

        // Client settings category
        ConfigCategory client = builder.getOrCreateCategory(Component.literal(ConfigDescriptors.CATEGORY_CLIENT));
        client.addEntry(entryBuilder.startBooleanToggle(Component.literal(ConfigDescriptors.HARVEST_PARTICLES_LABEL), Config.harvestParticles)
                .setSaveConsumer(v -> Config.harvestParticles = v)
                .setTooltip(Component.literal(ConfigDescriptors.HARVEST_PARTICLES_TOOLTIP))
                .build());

        // Persist config when the ClothConfig screen is saved
        builder.setSavingRunnable(() -> Config.save());

        return builder.build();
    }
}
