package com.forgetmecrops.client.config;

import dev.isxander.yacl3.api.OptionDescription;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * ConfigTooltipFactory: The tooltip scriptwriter for the config UI!
 * <p>
 * Provides {@link OptionDescription} instances for each config option — from a plain
 * single-line translatable tooltip to rich multi-mode enum descriptions that tell
 * players exactly what each mode does and why they might care. The goal: no player
 * should need to read the README just to understand their config screen options.
 * </p>
 * <p>
 * Why a factory? Because tooltip content is complex enough to deserve its own class,
 * and ConfigScreen is already busy enough building the option tree. Delegation is healthy.
 * </p>
 */
public final class ConfigTooltipFactory {
    // Utility class. The tooltip factory does not have tooltips about itself.
    private ConfigTooltipFactory() {}

    private static MutableComponent modeLabel(String key, ChatFormatting color) {
        return Component.literal("  ")
                .append(Component.translatable(key).withStyle(color));
    }

    /**
     * Creates a simple single-line tooltip from a translatable lang key.
     *
     * @param key the translation key for the tooltip text
     * @return an {@link OptionDescription} wrapping the translatable component
     */
    public static OptionDescription plain(String key) {
        return OptionDescription.of(Component.translatable(key));
    }

    /**
     * Generates a rich multi-line tooltip explaining all durability modes.
     * Each mode is color-coded so players know exactly what "IGNORE_UNBREAKING"
     * or "NONE" actually does to their farming experience.
     *
     * @return an {@link OptionDescription} with one component per tooltip line
     */
    public static OptionDescription durabilityMode() {
        return OptionDescription.of(
                Component.literal("Modes:").withStyle(ChatFormatting.YELLOW),
                modeLabel("forgetmecrops.enum.durability_mode.normal", ChatFormatting.GREEN)
                        .append(Component.literal(" - Standard durability consumption (default)").withStyle(ChatFormatting.GRAY)),
                modeLabel("forgetmecrops.enum.durability_mode.ignore_unbreaking", ChatFormatting.GOLD)
                        .append(Component.literal(" - Treat Unbreaking as absent when computing wear").withStyle(ChatFormatting.GRAY)),
                modeLabel("forgetmecrops.enum.durability_mode.none", ChatFormatting.RED)
                        .append(Component.literal(" - Disable durability loss entirely").withStyle(ChatFormatting.GRAY)),
                Component.translatable("forgetmecrops.config.durabilityMode.tooltip").withStyle(ChatFormatting.GRAY)
        );
    }

    /**
     * Generates a rich multi-line tooltip explaining all rotation modes.
     * Color-coded descriptions of SINGLE_STEP, FULL_ROTATION, and FOLLOW_ROTATION
     * so players understand the frame-spinning behavior they're configuring.
     *
     * @return an {@link OptionDescription} with one component per tooltip line
     */
    public static OptionDescription rotationMode() {
        return OptionDescription.of(
                Component.literal("Modes:").withStyle(ChatFormatting.YELLOW),
                modeLabel("forgetmecrops.enum.rotation_mode.single_step", ChatFormatting.AQUA)
                        .append(Component.literal(" - Advance one rotation step per full farm harvest").withStyle(ChatFormatting.GRAY)),
                modeLabel("forgetmecrops.enum.rotation_mode.full_rotation", ChatFormatting.GOLD)
                        .append(Component.literal(" - Perform a full 0..7 rotation cycle per harvest (default)").withStyle(ChatFormatting.GRAY)),
                modeLabel("forgetmecrops.enum.rotation_mode.follow_rotation", ChatFormatting.GREEN)
                        .append(Component.literal(" - Rotate to follow the spiral scan progression").withStyle(ChatFormatting.GRAY)),
                Component.translatable("forgetmecrops.config.rotationMode.tooltip").withStyle(ChatFormatting.GRAY)
        );
    }

    /**
     * Generates a rich multi-line tooltip explaining all seed-clutter modes.
     * Describes NORMAL, REDUCED, and NONE with color-coding so players know whether
     * their extra seeds will be auto-cleaned or left alone.
     *
     * @return an {@link OptionDescription} with one component per tooltip line
     */
    public static OptionDescription seedClutterMode() {
        return OptionDescription.of(
                Component.literal("Modes:").withStyle(ChatFormatting.YELLOW),
                modeLabel("forgetmecrops.enum.seed_clutter_mode.normal", ChatFormatting.GREEN)
                        .append(Component.literal(" - Default seed handling").withStyle(ChatFormatting.GRAY)),
                modeLabel("forgetmecrops.enum.seed_clutter_mode.reduced", ChatFormatting.GOLD)
                        .append(Component.literal(" - Reduce seed clutter by conserving seeds when replanting (default)").withStyle(ChatFormatting.GRAY)),
                modeLabel("forgetmecrops.enum.seed_clutter_mode.none", ChatFormatting.RED)
                        .append(Component.literal(" - Disable seed-clutter rules; do not automatically manage extra seeds").withStyle(ChatFormatting.GRAY)),
                Component.translatable("forgetmecrops.config.seedClutterMode.tooltip").withStyle(ChatFormatting.GRAY)
        );
    }
}