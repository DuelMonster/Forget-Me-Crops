package com.forgetmecrops.client.config;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Shared tooltip builders for loader-specific Cloth config screens.
 */
public final class ConfigTooltipFactory {
    private ConfigTooltipFactory() {}

    public static Supplier<Optional<Component[]>> plain(String key) {
        Component c = Component.translatable(key);
        return () -> Optional.of(new Component[]{c});
    }

    public static Supplier<Optional<Component[]>> durabilityMode() {
        Component c = Component.literal("")
                .append(Component.literal("Modes:\n").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal("  NORMAL").withStyle(ChatFormatting.GREEN))
                .append(Component.literal(" - Standard durability consumption (default)\n").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("  IGNORE_UNBREAKING").withStyle(ChatFormatting.GOLD))
                .append(Component.literal(" - Treat Unbreaking as absent when computing wear\n").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("  NONE").withStyle(ChatFormatting.RED))
                .append(Component.literal(" - Disable durability loss entirely\n").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("\n"))
                .append(Component.translatable("forgetmecrops.config.durabilityMode.tooltip").withStyle(ChatFormatting.GRAY));
        return () -> Optional.of(new Component[]{c});
    }

    public static Supplier<Optional<Component[]>> rotationMode() {
        Component c = Component.literal("")
                .append(Component.literal("Modes:\n").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal("  SINGLE_STEP").withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" - Advance one rotation step per full farm harvest\n").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("  FULL_ROTATION").withStyle(ChatFormatting.GOLD))
                .append(Component.literal(" - Perform a full 0..7 rotation cycle per harvest\n").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("  FOLLOW_ROTATION").withStyle(ChatFormatting.GREEN))
                .append(Component.literal(" - Rotate to follow the spiral scan progression (default)\n").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("\n"))
                .append(Component.translatable("forgetmecrops.config.rotationMode.tooltip").withStyle(ChatFormatting.GRAY));
        return () -> Optional.of(new Component[]{c});
    }

    public static Supplier<Optional<Component[]>> seedClutterMode() {
        Component c = Component.literal("")
                .append(Component.literal("Modes:\n").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal("  NORMAL").withStyle(ChatFormatting.GREEN))
                .append(Component.literal(" - Default seed handling\n").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("  REDUCED").withStyle(ChatFormatting.GOLD))
                .append(Component.literal(" - Reduce seed clutter by conserving seeds when replanting\n").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("  NONE").withStyle(ChatFormatting.RED))
                .append(Component.literal(" - Disable seed-clutter rules; do not automatically manage extra seeds\n").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("\n"))
                .append(Component.translatable("forgetmecrops.config.seedClutterMode.tooltip").withStyle(ChatFormatting.GRAY));
        return () -> Optional.of(new Component[]{c});
    }
}