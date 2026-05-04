package com.forgetmecrops.fabric.client;

import com.forgetmecrops.enums.DurabilityMode;
import com.forgetmecrops.enums.RotationMode;
import com.forgetmecrops.enums.SeedClutterMode;
import com.forgetmecrops.config.Config;
import com.forgetmecrops.config.ConfigDescriptors;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

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

        server.addEntry(entryBuilder.startIntField(Component.literal(ConfigDescriptors.TICK_INTERVAL_LABEL), Config.getTickInterval())
                .setDefaultValue(ConfigDescriptors.TICK_INTERVAL_DEFAULT)
                .setMin(ConfigDescriptors.TICK_INTERVAL_MIN)
                .setSaveConsumer(v -> Config.setTickInterval(v))
                .setTooltip(Component.literal(ConfigDescriptors.TICK_INTERVAL_TOOLTIP))
                .build());

        server.addEntry(entryBuilder.startIntField(Component.literal(ConfigDescriptors.SCAN_RANGE_X_LABEL), Config.getScanRangeX())
                .setDefaultValue(ConfigDescriptors.SCAN_RANGE_X_DEFAULT)
                .setMin(ConfigDescriptors.SCAN_RANGE_X_MIN)
                .setSaveConsumer(v -> Config.setScanRangeX(v))
                .setTooltip(Component.literal(ConfigDescriptors.SCAN_RANGE_X_TOOLTIP))
                .build());

        server.addEntry(entryBuilder.startIntField(Component.literal(ConfigDescriptors.SCAN_RANGE_Z_LABEL), Config.getScanRangeZ())
                .setDefaultValue(ConfigDescriptors.SCAN_RANGE_Z_DEFAULT)
                .setMin(ConfigDescriptors.SCAN_RANGE_Z_MIN)
                .setSaveConsumer(v -> Config.setScanRangeZ(v))
                .setTooltip(Component.literal(ConfigDescriptors.SCAN_RANGE_Z_TOOLTIP))
                .build());

        server.addEntry(entryBuilder.startEnumSelector(Component.literal(ConfigDescriptors.DURABILITY_MODE_LABEL), DurabilityMode.class, Config.getDurabilityMode())
                .setDefaultValue(ConfigDescriptors.DURABILITY_MODE_DEFAULT)
                .setSaveConsumer(v -> Config.setDurabilityMode(v))
                .setTooltip(Component.literal("")
                        .append(Component.literal("Modes:\n").withStyle(ChatFormatting.YELLOW))
                        .append(Component.literal("  NORMAL").withStyle(ChatFormatting.GREEN))
                        .append(Component.literal(" — Standard durability consumption (default)\n").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal("  IGNORE_UNBREAKING").withStyle(ChatFormatting.GOLD))
                        .append(Component.literal(" — Treat Unbreaking as absent when computing wear\n").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal("  NONE").withStyle(ChatFormatting.RED))
                        .append(Component.literal(" — Disable durability loss entirely\n").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal("\n")).append(Component.literal(ConfigDescriptors.DURABILITY_MODE_TOOLTIP).withStyle(ChatFormatting.GRAY)))
                .build());

        server.addEntry(entryBuilder.startBooleanToggle(Component.literal(ConfigDescriptors.MENDING_NEGATION_LABEL), Config.isMendingNegation())
                .setSaveConsumer(v -> Config.setMendingNegation(v))
                .setTooltip(Component.literal(ConfigDescriptors.MENDING_NEGATION_TOOLTIP))
                .build());

        server.addEntry(entryBuilder.startBooleanToggle(Component.literal(ConfigDescriptors.DEBUG_LOGGING_LABEL), Config.isDebugLogging())
                .setSaveConsumer(v -> Config.setDebugLogging(v))
                .setTooltip(Component.literal(ConfigDescriptors.DEBUG_LOGGING_TOOLTIP))
                .build());

        server.addEntry(entryBuilder.startIntField(Component.literal(ConfigDescriptors.CHEST_FULL_COOLDOWN_LABEL), Config.getChestFullCooldownTicks())
                .setDefaultValue(ConfigDescriptors.CHEST_FULL_COOLDOWN_DEFAULT)
                .setMin(ConfigDescriptors.CHEST_FULL_COOLDOWN_MIN)
                .setSaveConsumer(v -> Config.setChestFullCooldownTicks(v))
                .setTooltip(Component.literal(ConfigDescriptors.CHEST_FULL_COOLDOWN_TOOLTIP))
                .build());

        server.addEntry(entryBuilder.startIntField(Component.literal(ConfigDescriptors.MAX_SPIRAL_DURATION_LABEL), Config.getMaxSpiralDurationTicks())
                .setDefaultValue(ConfigDescriptors.MAX_SPIRAL_DEFAULT)
                .setMin(ConfigDescriptors.MAX_SPIRAL_MIN)
                .setSaveConsumer(v -> Config.setMaxSpiralDurationTicks(v))
                .setTooltip(Component.literal(ConfigDescriptors.MAX_SPIRAL_TOOLTIP))
                .build());

        server.addEntry(entryBuilder.startEnumSelector(Component.literal(ConfigDescriptors.ROTATION_MODE_LABEL), RotationMode.class, Config.getRotationMode())
                .setDefaultValue(ConfigDescriptors.ROTATION_MODE_DEFAULT)
                .setSaveConsumer(v -> Config.setRotationMode(v))
                .setTooltip(Component.literal("")
                        .append(Component.literal("Modes:\n").withStyle(ChatFormatting.YELLOW))
                        .append(Component.literal("  STEP_PER_HARVEST").withStyle(ChatFormatting.AQUA))
                        .append(Component.literal(" — Advance one rotation step per full farm harvest\n").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal("  FULL_ROTATION_PER_HARVEST").withStyle(ChatFormatting.GOLD))
                        .append(Component.literal(" — Perform a full 0..7 rotation cycle per harvest\n").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal("  FOLLOW_HARVEST_SPIRAL").withStyle(ChatFormatting.GREEN))
                        .append(Component.literal(" — Rotate to follow the spiral scan progression (default)\n").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal("\n")).append(Component.literal(ConfigDescriptors.ROTATION_MODE_TOOLTIP).withStyle(ChatFormatting.GRAY)))
                .build());

        server.addEntry(entryBuilder.startEnumSelector(Component.literal(ConfigDescriptors.SEED_CLUTTER_LABEL), SeedClutterMode.class, Config.getSeedClutterMode())
                .setDefaultValue(ConfigDescriptors.SEED_CLUTTER_DEFAULT)
                .setSaveConsumer(v -> Config.setSeedClutterMode(v))
                .setTooltip(Component.literal("")
                        .append(Component.literal("Modes:\n").withStyle(ChatFormatting.YELLOW))
                        .append(Component.literal("  NORMAL").withStyle(ChatFormatting.GREEN))
                        .append(Component.literal(" — Default seed handling\n").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal("  REDUCED").withStyle(ChatFormatting.GOLD))
                        .append(Component.literal(" — Reduce seed clutter by conserving seeds when replanting\n").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal("  NONE").withStyle(ChatFormatting.RED))
                        .append(Component.literal(" — Disable seed-clutter rules; do not automatically manage extra seeds\n").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal("\n")).append(Component.literal(ConfigDescriptors.SEED_CLUTTER_TOOLTIP).withStyle(ChatFormatting.GRAY)))
                .build());

        server.addEntry(entryBuilder.startIntField(Component.literal(ConfigDescriptors.SEED_RESERVE_LABEL), Config.getSeedReservePerType())
                .setDefaultValue(ConfigDescriptors.SEED_RESERVE_DEFAULT)
                .setMin(ConfigDescriptors.SEED_RESERVE_MIN)
                .setSaveConsumer(v -> Config.setSeedReservePerType(v))
                .setTooltip(Component.literal(ConfigDescriptors.SEED_RESERVE_TOOLTIP))
                .build());

        // Client settings category
        ConfigCategory client = builder.getOrCreateCategory(Component.literal(ConfigDescriptors.CATEGORY_CLIENT));
        client.addEntry(entryBuilder.startBooleanToggle(Component.literal(ConfigDescriptors.HARVEST_PARTICLES_LABEL), Config.isHarvestParticles())
                .setSaveConsumer(v -> Config.setHarvestParticles(v))
                .setTooltip(Component.literal(ConfigDescriptors.HARVEST_PARTICLES_TOOLTIP))
                .build());

        // Persist config when the ClothConfig screen is saved
        builder.setSavingRunnable(() -> Config.save());

        return builder.build();
    }
}
