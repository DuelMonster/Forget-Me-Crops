package com.forgetmecrops.neoforge.client;

import com.forgetmecrops.enums.DurabilityMode;
import com.forgetmecrops.enums.RotationMode;
import com.forgetmecrops.enums.SeedClutterMode;
import com.forgetmecrops.config.Config;
import com.forgetmecrops.config.ConfigDescriptors;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * NeoForge: centralized ClothConfig builder used by the NeoForge module.
 *
 * Kept in the NeoForge module so the common module doesn't need a runtime
 * dependency on the ClothConfig API.
 */
public final class ConfigScreens {
        private ConfigScreens() {}

        /**
         * Create the NeoForge config screen using the shared builder.
         *
         * @param parent parent screen passed to the ClothConfig builder
         * @return constructed config screen
         */
        public static Screen create(Screen parent) {
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.literal(ConfigDescriptors.TITLE));

        // Server settings category
        ConfigCategory server = builder.getOrCreateCategory(Component.literal(ConfigDescriptors.CATEGORY_SERVER));

        server.addEntry(new LabelTooltipIntegerListEntry(
                Component.literal(ConfigDescriptors.TICK_INTERVAL_LABEL),
                Config.getTickInterval(),
                ConfigDescriptors.TICK_INTERVAL_DEFAULT,
                ConfigDescriptors.TICK_INTERVAL_MIN,
                Config::setTickInterval,
                tooltip(ConfigDescriptors.TICK_INTERVAL_TOOLTIP)
        ));

        server.addEntry(new LabelTooltipIntegerListEntry(
                Component.literal(ConfigDescriptors.SCAN_RANGE_X_LABEL),
                Config.getScanRangeX(),
                ConfigDescriptors.SCAN_RANGE_X_DEFAULT,
                ConfigDescriptors.SCAN_RANGE_X_MIN,
                Config::setScanRangeX,
                tooltip(ConfigDescriptors.SCAN_RANGE_X_TOOLTIP)
        ));

        server.addEntry(new LabelTooltipIntegerListEntry(
                Component.literal(ConfigDescriptors.SCAN_RANGE_Z_LABEL),
                Config.getScanRangeZ(),
                ConfigDescriptors.SCAN_RANGE_Z_DEFAULT,
                ConfigDescriptors.SCAN_RANGE_Z_MIN,
                Config::setScanRangeZ,
                tooltip(ConfigDescriptors.SCAN_RANGE_Z_TOOLTIP)
        ));

        server.addEntry(new LabelTooltipEnumListEntry<>(
                Component.literal(ConfigDescriptors.DURABILITY_MODE_LABEL),
                DurabilityMode.class,
                Config.getDurabilityMode(),
                ConfigDescriptors.DURABILITY_MODE_DEFAULT,
                Config::setDurabilityMode,
                () -> Optional.of(new Component[] {durabilityModeTooltip()})
        ));

        server.addEntry(new LabelTooltipBooleanListEntry(
                Component.literal(ConfigDescriptors.MENDING_NEGATION_LABEL),
                Config.isMendingNegation(),
                Config::setMendingNegation,
                tooltip(ConfigDescriptors.MENDING_NEGATION_TOOLTIP)
        ));

        server.addEntry(new LabelTooltipBooleanListEntry(
                Component.literal(ConfigDescriptors.DEBUG_LOGGING_LABEL),
                Config.isDebugLogging(),
                Config::setDebugLogging,
                tooltip(ConfigDescriptors.DEBUG_LOGGING_TOOLTIP)
        ));

        server.addEntry(new LabelTooltipIntegerListEntry(
                Component.literal(ConfigDescriptors.CHEST_FULL_COOLDOWN_LABEL),
                Config.getChestFullCooldownTicks(),
                ConfigDescriptors.CHEST_FULL_COOLDOWN_DEFAULT,
                ConfigDescriptors.CHEST_FULL_COOLDOWN_MIN,
                Config::setChestFullCooldownTicks,
                tooltip(ConfigDescriptors.CHEST_FULL_COOLDOWN_TOOLTIP)
        ));

        server.addEntry(new LabelTooltipIntegerListEntry(
                Component.literal(ConfigDescriptors.MAX_SPIRAL_DURATION_LABEL),
                Config.getMaxSpiralDurationTicks(),
                ConfigDescriptors.MAX_SPIRAL_DEFAULT,
                ConfigDescriptors.MAX_SPIRAL_MIN,
                Config::setMaxSpiralDurationTicks,
                tooltip(ConfigDescriptors.MAX_SPIRAL_TOOLTIP)
        ));

        server.addEntry(new LabelTooltipEnumListEntry<>(
                Component.literal(ConfigDescriptors.ROTATION_MODE_LABEL),
                RotationMode.class,
                Config.getRotationMode(),
                ConfigDescriptors.ROTATION_MODE_DEFAULT,
                Config::setRotationMode,
                () -> Optional.of(new Component[] {rotationModeTooltip()})
        ));

        server.addEntry(new LabelTooltipEnumListEntry<>(
                Component.literal(ConfigDescriptors.SEED_CLUTTER_LABEL),
                SeedClutterMode.class,
                Config.getSeedClutterMode(),
                ConfigDescriptors.SEED_CLUTTER_DEFAULT,
                Config::setSeedClutterMode,
                () -> Optional.of(new Component[] {seedClutterModeTooltip()})
        ));

        server.addEntry(new LabelTooltipIntegerListEntry(
                Component.literal(ConfigDescriptors.SEED_RESERVE_LABEL),
                Config.getSeedReservePerType(),
                ConfigDescriptors.SEED_RESERVE_DEFAULT,
                ConfigDescriptors.SEED_RESERVE_MIN,
                Config::setSeedReservePerType,
                tooltip(ConfigDescriptors.SEED_RESERVE_TOOLTIP)
        ));

        // Client settings category
        ConfigCategory client = builder.getOrCreateCategory(Component.literal(ConfigDescriptors.CATEGORY_CLIENT));
        client.addEntry(new LabelTooltipBooleanListEntry(
                Component.literal(ConfigDescriptors.HARVEST_PARTICLES_LABEL),
                Config.isHarvestParticles(),
                Config::setHarvestParticles,
                tooltip(ConfigDescriptors.HARVEST_PARTICLES_TOOLTIP)
        ));

        // Persist config when the ClothConfig screen is saved
        builder.setSavingRunnable(() -> Config.save());

        return builder.build();
    }

    private static Supplier<Optional<Component[]>> tooltip(String text) {
        Component c = Component.literal(text);
        return () -> Optional.of(new Component[]{c});
    }

        private static Component durabilityModeTooltip() {
                return Component.literal("")
                                .append(Component.literal("Modes:\n").withStyle(ChatFormatting.YELLOW))
                                .append(Component.literal("  NORMAL").withStyle(ChatFormatting.GREEN))
                                .append(Component.literal(" - Standard durability consumption (default)\n").withStyle(ChatFormatting.GRAY))
                                .append(Component.literal("  IGNORE_UNBREAKING").withStyle(ChatFormatting.GOLD))
                                .append(Component.literal(" - Treat Unbreaking as absent when computing wear\n").withStyle(ChatFormatting.GRAY))
                                .append(Component.literal("  NONE").withStyle(ChatFormatting.RED))
                                .append(Component.literal(" - Disable durability loss entirely\n").withStyle(ChatFormatting.GRAY))
                                .append(Component.literal("\n"))
                                .append(Component.literal(ConfigDescriptors.DURABILITY_MODE_TOOLTIP).withStyle(ChatFormatting.GRAY));
        }

        private static Component rotationModeTooltip() {
                return Component.literal("")
                                .append(Component.literal("Modes:\n").withStyle(ChatFormatting.YELLOW))
                                .append(Component.literal("  STEP_PER_HARVEST").withStyle(ChatFormatting.AQUA))
                                .append(Component.literal(" - Advance one rotation step per full farm harvest\n").withStyle(ChatFormatting.GRAY))
                                .append(Component.literal("  FULL_ROTATION_PER_HARVEST").withStyle(ChatFormatting.GOLD))
                                .append(Component.literal(" - Perform a full 0..7 rotation cycle per harvest\n").withStyle(ChatFormatting.GRAY))
                                .append(Component.literal("  FOLLOW_HARVEST_SPIRAL").withStyle(ChatFormatting.GREEN))
                                .append(Component.literal(" - Rotate to follow the spiral scan progression (default)\n").withStyle(ChatFormatting.GRAY))
                                .append(Component.literal("\n"))
                                .append(Component.literal(ConfigDescriptors.ROTATION_MODE_TOOLTIP).withStyle(ChatFormatting.GRAY));
        }

        private static Component seedClutterModeTooltip() {
                return Component.literal("")
                                .append(Component.literal("Modes:\n").withStyle(ChatFormatting.YELLOW))
                                .append(Component.literal("  NORMAL").withStyle(ChatFormatting.GREEN))
                                .append(Component.literal(" - Default seed handling\n").withStyle(ChatFormatting.GRAY))
                                .append(Component.literal("  REDUCED").withStyle(ChatFormatting.GOLD))
                                .append(Component.literal(" - Reduce seed clutter by conserving seeds when replanting\n").withStyle(ChatFormatting.GRAY))
                                .append(Component.literal("  NONE").withStyle(ChatFormatting.RED))
                                .append(Component.literal(" - Disable seed-clutter rules; do not automatically manage extra seeds\n").withStyle(ChatFormatting.GRAY))
                                .append(Component.literal("\n"))
                                .append(Component.literal(ConfigDescriptors.SEED_CLUTTER_TOOLTIP).withStyle(ChatFormatting.GRAY));
        }
}
