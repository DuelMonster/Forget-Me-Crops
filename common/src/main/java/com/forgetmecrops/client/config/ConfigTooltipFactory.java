package com.forgetmecrops.client.config;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * ConfigTooltipFactory: The tooltip scriptwriter for the config UI!
 * <p>
 * Provides tooltip content suppliers for each config option — everything from a plain
 * single-line translatable tooltip to the rich multi-mode enum descriptions that tell
 * players exactly what each mode does and why they might care. The goal: no player
 * should need to read the README just to understand their config screen options.
 * </p>
 * <p>
 * Why a factory? Because tooltip content is complex enough to deserve its own class,
 * and ConfigScreen is already busy enough instantiating widgets. Delegation is healthy.
 * </p>
 */
public final class ConfigTooltipFactory {
    // Utility class. The tooltip factory does not have tooltips about itself.
    private ConfigTooltipFactory() {}

    private static Component modeLabel(String key, ChatFormatting color) {
        return Component.literal("  ")
                .append(Component.translatable(key).withStyle(color));
    }

    /**
     * Creates a simple single-line tooltip from a translatable lang key.
     * The "plain" option — for when the option is self-explanatory and doesn't need a drama.
     *
     * @param key the translation key for the tooltip text
     * @return a supplier that yields an Optional of a one-element Component array
     */
    public static Supplier<Optional<Component[]>> plain(String key) {
        Component c = Component.translatable(key);
        return () -> Optional.of(new Component[]{c});
    }

    /**
     * Generates a rich multi-line tooltip explaining all durability modes.
     * Lists each mode with color-coded descriptions so players know exactly
     * what "IGNORE_UNBREAKING" or "NONE" actually does to their farming experience.
     *
     * @return a supplier yielding an Optional of a Component array (the tooltip lines)
     */
    public static Supplier<Optional<Component[]>> durabilityMode() {
        Component c = Component.literal("")
                .append(Component.literal("Modes:\n").withStyle(ChatFormatting.YELLOW))
                .append(modeLabel("forgetmecrops.enum.durability_mode.normal", ChatFormatting.GREEN))
                .append(Component.literal(" - Standard durability consumption (default)\n").withStyle(ChatFormatting.GRAY))
                .append(modeLabel("forgetmecrops.enum.durability_mode.ignore_unbreaking", ChatFormatting.GOLD))
                .append(Component.literal(" - Treat Unbreaking as absent when computing wear\n").withStyle(ChatFormatting.GRAY))
                .append(modeLabel("forgetmecrops.enum.durability_mode.none", ChatFormatting.RED))
                .append(Component.literal(" - Disable durability loss entirely\n").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("\n"))
                .append(Component.translatable("forgetmecrops.config.durabilityMode.tooltip").withStyle(ChatFormatting.GRAY));
        return () -> Optional.of(new Component[]{c});
    }

    /**
     * Generates a rich multi-line tooltip explaining all rotation modes.
     * Color-coded descriptions of SINGLE_STEP, FULL_ROTATION, and FOLLOW_ROTATION
     * so players understand the frame-spinning behavior they're configuring.
     *
     * @return a supplier yielding an Optional of a Component array (the tooltip lines)
     */
    public static Supplier<Optional<Component[]>> rotationMode() {
        Component c = Component.literal("")
                .append(Component.literal("Modes:\n").withStyle(ChatFormatting.YELLOW))
                .append(modeLabel("forgetmecrops.enum.rotation_mode.single_step", ChatFormatting.AQUA))
                .append(Component.literal(" - Advance one rotation step per full farm harvest\n").withStyle(ChatFormatting.GRAY))
                .append(modeLabel("forgetmecrops.enum.rotation_mode.full_rotation", ChatFormatting.GOLD))
                .append(Component.literal(" - Perform a full 0..7 rotation cycle per harvest\n").withStyle(ChatFormatting.GRAY))
                .append(modeLabel("forgetmecrops.enum.rotation_mode.follow_rotation", ChatFormatting.GREEN))
                .append(Component.literal(" - Rotate to follow the spiral scan progression (default)\n").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("\n"))
                .append(Component.translatable("forgetmecrops.config.rotationMode.tooltip").withStyle(ChatFormatting.GRAY));
        return () -> Optional.of(new Component[]{c});
    }

    /**
     * Generates a rich multi-line tooltip explaining all seed-clutter modes.
     * Describes NORMAL, REDUCED, and NONE with color-coding so players know whether
     * their extra seeds will be auto-cleaned or left alone.
     *
     * @return a supplier yielding an Optional of a Component array (the tooltip lines)
     */
    public static Supplier<Optional<Component[]>> seedClutterMode() {
        Component c = Component.literal("")
                .append(Component.literal("Modes:\n").withStyle(ChatFormatting.YELLOW))
                .append(modeLabel("forgetmecrops.enum.seed_clutter_mode.normal", ChatFormatting.GREEN))
                .append(Component.literal(" - Default seed handling\n").withStyle(ChatFormatting.GRAY))
                .append(modeLabel("forgetmecrops.enum.seed_clutter_mode.reduced", ChatFormatting.GOLD))
                .append(Component.literal(" - Reduce seed clutter by conserving seeds when replanting\n").withStyle(ChatFormatting.GRAY))
                .append(modeLabel("forgetmecrops.enum.seed_clutter_mode.none", ChatFormatting.RED))
                .append(Component.literal(" - Disable seed-clutter rules; do not automatically manage extra seeds\n").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("\n"))
                .append(Component.translatable("forgetmecrops.config.seedClutterMode.tooltip").withStyle(ChatFormatting.GRAY));
        return () -> Optional.of(new Component[]{c});
    }
}