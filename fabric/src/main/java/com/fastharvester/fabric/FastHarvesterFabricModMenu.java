package com.fastharvester.fabric;

import com.fastharvester.Config;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class FastHarvesterFabricModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> createConfigScreen(parent);
    }

    private Screen createConfigScreen(Screen parent) {
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.literal("FastHarvester Config"));
        ConfigCategory general = builder.getOrCreateCategory(Component.literal("General"));
        ConfigEntryBuilder entryBuilder = builder.entryBuilder();

        general.addEntry(entryBuilder.startIntField(Component.literal("Tick Interval"), Config.tickInterval)
                .setDefaultValue(300)
                .setTooltip(Component.literal("How often (in ticks) should we try to harvest?"))
                .setSaveConsumer(val -> Config.tickInterval = val)
                .build());
        general.addEntry(entryBuilder.startIntField(Component.literal("Frame Rediscovery Interval"), Config.frameRediscoveryInterval)
                .setDefaultValue(100)
                .setTooltip(Component.literal("How often (in ticks) should we rediscover frames?"))
                .setSaveConsumer(val -> Config.frameRediscoveryInterval = val)
                .build());
        general.addEntry(entryBuilder.startIntField(Component.literal("Scan Range"), Config.scanRange)
                .setDefaultValue(4)
                .setTooltip(Component.literal("How far should we scan for crops?"))
                .setSaveConsumer(val -> Config.scanRange = val)
                .build());
        general.addEntry(entryBuilder.startEnumSelector(Component.literal("Durability Mode"), com.fastharvester.enums.DurabilityMode.class, Config.durabilityMode)
                .setDefaultValue(com.fastharvester.enums.DurabilityMode.NORMAL)
                .setTooltip(Component.literal("How tough should our hoes be?"))
                .setSaveConsumer(val -> Config.durabilityMode = val)
                .build());
        general.addEntry(entryBuilder.startBooleanToggle(Component.literal("Mending Negation"), Config.mendingNegation)
                .setDefaultValue(true)
                .setTooltip(Component.literal("Should mending be ignored?"))
                .setSaveConsumer(val -> Config.mendingNegation = val)
                .build());
        general.addEntry(entryBuilder.startBooleanToggle(Component.literal("Debug Logging"), Config.debugLogging)
                .setDefaultValue(false)
                .setTooltip(Component.literal("Should we print debug logs?"))
                .setSaveConsumer(val -> Config.debugLogging = val)
                .build());
        general.addEntry(entryBuilder.startIntField(Component.literal("Chest Full Cooldown Ticks"), Config.chestFullCooldownTicks)
                .setDefaultValue(100)
                .setTooltip(Component.literal("How long to wait (in ticks) when a chest is full."))
                .setSaveConsumer(val -> Config.chestFullCooldownTicks = val)
                .build());
        general.addEntry(entryBuilder.startIntField(Component.literal("Max Spiral Duration Ticks"), Config.maxSpiralDurationTicks)
                .setDefaultValue(100)
                .setTooltip(Component.literal("Maximum spiral duration (in ticks)."))
                .setSaveConsumer(val -> Config.maxSpiralDurationTicks = val)
                .build());
        general.addEntry(entryBuilder.startEnumSelector(Component.literal("Rotation Mode"), com.fastharvester.enums.RotationMode.class, Config.rotationMode)
                .setDefaultValue(com.fastharvester.enums.RotationMode.FOLLOW_HARVEST_SPIRAL)
                .setTooltip(Component.literal("How should we rotate?"))
                .setSaveConsumer(val -> Config.rotationMode = val)
                .build());
        general.addEntry(entryBuilder.startEnumSelector(Component.literal("Seed Clutter Mode"), com.fastharvester.enums.SeedClutterMode.class, Config.seedClutterMode)
                .setDefaultValue(com.fastharvester.enums.SeedClutterMode.REDUCED)
                .setTooltip(Component.literal("How should we handle seed clutter?"))
                .setSaveConsumer(val -> Config.seedClutterMode = val)
                .build());
        general.addEntry(entryBuilder.startIntField(Component.literal("Seed Reserve Per Type"), Config.seedReservePerType)
                .setDefaultValue(80)
                .setTooltip(Component.literal("How many seeds should we keep per type?"))
                .setSaveConsumer(val -> Config.seedReservePerType = val)
                .build());
        general.addEntry(entryBuilder.startBooleanToggle(Component.literal("Harvest Particles"), Config.harvestParticles)
                .setDefaultValue(true)
                .setTooltip(Component.literal("Show harvest particles?"))
                .setSaveConsumer(val -> Config.harvestParticles = val)
                .build());

        builder.setSavingRunnable(Config::save);
        return builder.build();
    }
}
