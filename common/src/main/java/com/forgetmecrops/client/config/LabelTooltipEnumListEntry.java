package com.forgetmecrops.client.config;

import me.shedaniel.clothconfig2.gui.entries.EnumListEntry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * LabelTooltipEnumListEntry: An enum picker that only shows its tooltip over the label!
 * <p>
 * Extends Cloth Config's {@link EnumListEntry} with the same label-hitbox tooltip restriction
 * as the boolean and integer variants. Used for RotationMode, DurabilityMode, and SeedClutterMode
 * — all of which have rich mode-description tooltips that you really only want to read when
 * you're hovering the label text, not accidentally when cycling through values.
 * </p>
 */
public final class LabelTooltipEnumListEntry<T extends Enum<?>> extends EnumListEntry<T> {
    // Same label-lane hitbox trick as the other LabelTooltip entry classes
    private final LabelHitbox hitbox = new LabelHitbox();

    /**
     * Creates an enum list entry with label-only tooltip behavior.
     *
     * @param fieldName        the display label on the left side of the row
     * @param enumClass        the enum class to cycle through (e.g. RotationMode.class)
     * @param value            current selected value
     * @param defaultValue     value to reset to when the reset button is clicked
     * @param saveCallback     called when the player saves the config screen
     * @param enumNameProvider translates an enum constant to its display Component
     * @param tooltipSupplier  provides the tooltip shown over the label area
     */
    public LabelTooltipEnumListEntry(Component fieldName,
                                     Class<T> enumClass,
                                     T value,
                                     T defaultValue,
                                     Consumer<T> saveCallback,
                                     Function<Enum, Component> enumNameProvider,
                                     Supplier<Optional<Component[]>> tooltipSupplier) {
        super(
                fieldName,
                enumClass,
                value,
                Component.translatable("text.cloth-config.reset_value"),
                () -> defaultValue,
                saveCallback,
                enumNameProvider,
                tooltipSupplier
        );
    }

    @Override
    public void render(GuiGraphics graphics,
                       int index,
                       int y,
                       int x,
                       int entryWidth,
                       int entryHeight,
                       int mouseX,
                       int mouseY,
                       boolean hovered,
                       float delta) {
        hitbox.update(x, y, entryWidth, entryHeight);
        super.render(graphics, index, y, x, entryWidth, entryHeight, mouseX, mouseY, hovered, delta);
    }

    @Override
    public Optional<Component[]> getTooltip(int mouseX, int mouseY) {
        return hitbox.isOverLabel(mouseX, mouseY) ? super.getTooltip(mouseX, mouseY) : Optional.empty();
    }
}
