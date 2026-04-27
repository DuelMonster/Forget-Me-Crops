package com.fastharvester.fabric.client;

import com.fastharvester.enums.DurabilityMode;
import com.fastharvester.enums.RotationMode;
import com.fastharvester.enums.SeedClutterMode;
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
                .setTitle(Component.literal(com.fastharvester.ConfigDescriptors.TITLE));

        ConfigEntryBuilder entryBuilder = builder.entryBuilder();

        // Server settings category
        ConfigCategory server = builder.getOrCreateCategory(Component.literal(com.fastharvester.ConfigDescriptors.CATEGORY_SERVER));

        server.addEntry(entryBuilder.startIntField(Component.literal(com.fastharvester.ConfigDescriptors.TICK_INTERVAL_LABEL), com.fastharvester.Config.tickInterval)
                .setDefaultValue(com.fastharvester.ConfigDescriptors.TICK_INTERVAL_DEFAULT)
                .setMin(com.fastharvester.ConfigDescriptors.TICK_INTERVAL_MIN)
                .setSaveConsumer(v -> com.fastharvester.Config.tickInterval = v)
                .setTooltip(Component.literal(com.fastharvester.ConfigDescriptors.TICK_INTERVAL_TOOLTIP))
                .build());

        server.addEntry(entryBuilder.startIntField(Component.literal(com.fastharvester.ConfigDescriptors.SCAN_RANGE_X_LABEL), com.fastharvester.Config.scanRangeX)
                .setDefaultValue(com.fastharvester.ConfigDescriptors.SCAN_RANGE_X_DEFAULT)
                .setMin(com.fastharvester.ConfigDescriptors.SCAN_RANGE_X_MIN)
                .setSaveConsumer(v -> com.fastharvester.Config.scanRangeX = v)
                .setTooltip(Component.literal(com.fastharvester.ConfigDescriptors.SCAN_RANGE_X_TOOLTIP))
                .build());

        server.addEntry(entryBuilder.startIntField(Component.literal(com.fastharvester.ConfigDescriptors.SCAN_RANGE_Z_LABEL), com.fastharvester.Config.scanRangeZ)
                .setDefaultValue(com.fastharvester.ConfigDescriptors.SCAN_RANGE_Z_DEFAULT)
                .setMin(com.fastharvester.ConfigDescriptors.SCAN_RANGE_Z_MIN)
                .setSaveConsumer(v -> com.fastharvester.Config.scanRangeZ = v)
                .setTooltip(Component.literal(com.fastharvester.ConfigDescriptors.SCAN_RANGE_Z_TOOLTIP))
                .build());

        server.addEntry(entryBuilder.startEnumSelector(Component.literal(com.fastharvester.ConfigDescriptors.DURABILITY_MODE_LABEL), DurabilityMode.class, com.fastharvester.Config.durabilityMode)
                .setDefaultValue(com.fastharvester.ConfigDescriptors.DURABILITY_MODE_DEFAULT)
                .setSaveConsumer(v -> com.fastharvester.Config.durabilityMode = v)
                .setTooltip(Component.literal(com.fastharvester.ConfigDescriptors.DURABILITY_MODE_TOOLTIP))
                .build());

        server.addEntry(entryBuilder.startBooleanToggle(Component.literal(com.fastharvester.ConfigDescriptors.MENDING_NEGATION_LABEL), com.fastharvester.Config.mendingNegation)
                .setSaveConsumer(v -> com.fastharvester.Config.mendingNegation = v)
                .setTooltip(Component.literal(com.fastharvester.ConfigDescriptors.MENDING_NEGATION_TOOLTIP))
                .build());

        server.addEntry(entryBuilder.startBooleanToggle(Component.literal(com.fastharvester.ConfigDescriptors.DEBUG_LOGGING_LABEL), com.fastharvester.Config.debugLogging)
                .setSaveConsumer(v -> com.fastharvester.Config.debugLogging = v)
                .setTooltip(Component.literal(com.fastharvester.ConfigDescriptors.DEBUG_LOGGING_TOOLTIP))
                .build());

        server.addEntry(entryBuilder.startIntField(Component.literal(com.fastharvester.ConfigDescriptors.CHEST_FULL_COOLDOWN_LABEL), com.fastharvester.Config.chestFullCooldownTicks)
                .setDefaultValue(com.fastharvester.ConfigDescriptors.CHEST_FULL_COOLDOWN_DEFAULT)
                .setMin(com.fastharvester.ConfigDescriptors.CHEST_FULL_COOLDOWN_MIN)
                .setSaveConsumer(v -> com.fastharvester.Config.chestFullCooldownTicks = v)
                .setTooltip(Component.literal(com.fastharvester.ConfigDescriptors.CHEST_FULL_COOLDOWN_TOOLTIP))
                .build());

        server.addEntry(entryBuilder.startIntField(Component.literal(com.fastharvester.ConfigDescriptors.MAX_SPIRAL_DURATION_LABEL), com.fastharvester.Config.maxSpiralDurationTicks)
                .setDefaultValue(com.fastharvester.ConfigDescriptors.MAX_SPIRAL_DEFAULT)
                .setMin(com.fastharvester.ConfigDescriptors.MAX_SPIRAL_MIN)
                .setSaveConsumer(v -> com.fastharvester.Config.maxSpiralDurationTicks = v)
                .setTooltip(Component.literal(com.fastharvester.ConfigDescriptors.MAX_SPIRAL_TOOLTIP))
                .build());

        server.addEntry(entryBuilder.startEnumSelector(Component.literal(com.fastharvester.ConfigDescriptors.ROTATION_MODE_LABEL), RotationMode.class, com.fastharvester.Config.rotationMode)
                .setDefaultValue(com.fastharvester.ConfigDescriptors.ROTATION_MODE_DEFAULT)
                .setSaveConsumer(v -> com.fastharvester.Config.rotationMode = v)
                .setTooltip(Component.literal(com.fastharvester.ConfigDescriptors.ROTATION_MODE_TOOLTIP))
                .build());

        server.addEntry(entryBuilder.startEnumSelector(Component.literal(com.fastharvester.ConfigDescriptors.SEED_CLUTTER_LABEL), SeedClutterMode.class, com.fastharvester.Config.seedClutterMode)
                .setDefaultValue(com.fastharvester.ConfigDescriptors.SEED_CLUTTER_DEFAULT)
                .setSaveConsumer(v -> com.fastharvester.Config.seedClutterMode = v)
                .setTooltip(Component.literal(com.fastharvester.ConfigDescriptors.SEED_CLUTTER_TOOLTIP))
                .build());

        server.addEntry(entryBuilder.startIntField(Component.literal(com.fastharvester.ConfigDescriptors.SEED_RESERVE_LABEL), com.fastharvester.Config.seedReservePerType)
                .setDefaultValue(com.fastharvester.ConfigDescriptors.SEED_RESERVE_DEFAULT)
                .setMin(com.fastharvester.ConfigDescriptors.SEED_RESERVE_MIN)
                .setSaveConsumer(v -> com.fastharvester.Config.seedReservePerType = v)
                .setTooltip(Component.literal(com.fastharvester.ConfigDescriptors.SEED_RESERVE_TOOLTIP))
                .build());

        // Client settings category
        ConfigCategory client = builder.getOrCreateCategory(Component.literal(com.fastharvester.ConfigDescriptors.CATEGORY_CLIENT));
        client.addEntry(entryBuilder.startBooleanToggle(Component.literal(com.fastharvester.ConfigDescriptors.HARVEST_PARTICLES_LABEL), com.fastharvester.Config.harvestParticles)
                .setSaveConsumer(v -> com.fastharvester.Config.harvestParticles = v)
                .setTooltip(Component.literal(com.fastharvester.ConfigDescriptors.HARVEST_PARTICLES_TOOLTIP))
                .build());

        // Persist config when the ClothConfig screen is saved
        builder.setSavingRunnable(() -> com.fastharvester.Config.save());

        return builder.build();
    }
}
