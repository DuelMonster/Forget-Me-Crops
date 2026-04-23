package com.fastharvester;

import com.fastharvester.enums.DurabilityMode;
import com.fastharvester.enums.RotationMode;
import com.fastharvester.enums.SeedClutterMode;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * NeoForge: centralized ClothConfig builder used by the NeoForge module.
 *
 * Kept in the NeoForge module so the common module doesn't need a runtime
 * dependency on the ClothConfig API.
 */
public final class ConfigScreens {
    private ConfigScreens() {}

    public static Screen create(Screen parent) {
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.literal("FastHarvester"));

        ConfigEntryBuilder entryBuilder = builder.entryBuilder();

        // Server settings category
        ConfigCategory server = builder.getOrCreateCategory(Component.literal("Server Settings"));

        server.addEntry(entryBuilder.startIntField(Component.literal("Tick Interval"), Config.tickInterval)
                .setDefaultValue(300)
                .setMin(1)
                .setSaveConsumer(v -> Config.tickInterval = v)
                .setTooltip(Component.literal("Ticks between automatic harvest attempts"))
                .build());

        server.addEntry(entryBuilder.startIntField(Component.literal("Scan Range"), Config.scanRange)
                .setDefaultValue(4)
                .setMin(1)
                .setSaveConsumer(v -> Config.scanRange = v)
                .setTooltip(Component.literal("Radius (blocks) to search for crops around each frame"))
                .build());

        server.addEntry(entryBuilder.startEnumSelector(Component.literal("Durability Mode"), DurabilityMode.class, Config.durabilityMode)
                .setDefaultValue(DurabilityMode.NORMAL)
                .setSaveConsumer(v -> Config.durabilityMode = v)
                .setTooltip(Component.literal("How item durability is handled when harvesting"))
                .build());

        server.addEntry(entryBuilder.startBooleanToggle(Component.literal("Mending Negation"), Config.mendingNegation)
                .setSaveConsumer(v -> Config.mendingNegation = v)
                .setTooltip(Component.literal("Prevent mending from stopping the harvester's tool use"))
                .build());

        server.addEntry(entryBuilder.startBooleanToggle(Component.literal("Debug Logging"), Config.debugLogging)
                .setSaveConsumer(v -> Config.debugLogging = v)
                .setTooltip(Component.literal("Enable verbose debug logging for troubleshooting"))
                .build());

        server.addEntry(entryBuilder.startIntField(Component.literal("Chest Full Cooldown"), Config.chestFullCooldownTicks)
                .setDefaultValue(100)
                .setMin(0)
                .setSaveConsumer(v -> Config.chestFullCooldownTicks = v)
                .setTooltip(Component.literal("Ticks to wait before retrying a full chest"))
                .build());

        server.addEntry(entryBuilder.startIntField(Component.literal("Max Spiral Duration"), Config.maxSpiralDurationTicks)
                .setDefaultValue(100)
                .setMin(1)
                .setSaveConsumer(v -> Config.maxSpiralDurationTicks = v)
                .setTooltip(Component.literal("Maximum ticks allowed for a harvesting spiral search"))
                .build());

        server.addEntry(entryBuilder.startEnumSelector(Component.literal("Rotation Mode"), RotationMode.class, Config.rotationMode)
                .setDefaultValue(RotationMode.FOLLOW_HARVEST_SPIRAL)
                .setSaveConsumer(v -> Config.rotationMode = v)
                .setTooltip(Component.literal("How frames rotate when harvesting"))
                .build());

        server.addEntry(entryBuilder.startEnumSelector(Component.literal("Seed Clutter Mode"), SeedClutterMode.class, Config.seedClutterMode)
                .setDefaultValue(SeedClutterMode.REDUCED)
                .setSaveConsumer(v -> Config.seedClutterMode = v)
                .setTooltip(Component.literal("Behaviour for leaving extra seeds after planting"))
                .build());

        server.addEntry(entryBuilder.startIntField(Component.literal("Seed Reserve Per Type"), Config.seedReservePerType)
                .setDefaultValue(80)
                .setMin(0)
                .setSaveConsumer(v -> Config.seedReservePerType = v)
                .setTooltip(Component.literal("Number of seeds to keep in reserve per crop type"))
                .build());

        // Client settings category
        ConfigCategory client = builder.getOrCreateCategory(Component.literal("Client Settings"));
        client.addEntry(entryBuilder.startBooleanToggle(Component.literal("Harvest Particles"), Config.harvestParticles)
                .setSaveConsumer(v -> Config.harvestParticles = v)
                .setTooltip(Component.literal("Show particle effects when harvesting"))
                .build());

        // Persist config when the ClothConfig screen is saved
        builder.setSavingRunnable(() -> Config.save());

        return builder.build();
    }
}
