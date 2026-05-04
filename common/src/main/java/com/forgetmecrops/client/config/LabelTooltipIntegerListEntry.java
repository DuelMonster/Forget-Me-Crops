package com.forgetmecrops.client.config;

import me.shedaniel.clothconfig2.gui.entries.IntegerListEntry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * LabelTooltipIntegerListEntry: An integer field that only shows its tooltip over the label!
 * <p>
 * Extends Cloth Config's {@link IntegerListEntry} with label-hitbox tooltip restriction.
 * Used for numeric options like tick interval, scan range, and chest cooldown — all of
 * which have tooltips explaining valid ranges that you want to read before changing,
 * not accidentally while typing a value into the field.
 * </p>
 */
public final class LabelTooltipIntegerListEntry extends IntegerListEntry {
    // Tracks the label lane per-render so tooltip hit-testing is always accurate
    private final LabelHitbox hitbox = new LabelHitbox();

    /**
     * Creates an integer field entry with label-only tooltip behavior and a configured minimum.
     *
     * @param fieldName       the display label on the left side of the row
     * @param value           the current integer value to initialize the field with
     * @param defaultValue    value to reset to when the reset button is clicked
     * @param minimum         the minimum allowed value (enforced by setMinimum)
     * @param saveCallback    called when the player saves the config screen
     * @param tooltipSupplier provides the tooltip components to show over the label area
     */
    public LabelTooltipIntegerListEntry(Component fieldName,
                                        int value,
                                        int defaultValue,
                                        int minimum,
                                        Consumer<Integer> saveCallback,
                                        Supplier<Optional<Component[]>> tooltipSupplier) {
        super(
                fieldName,
                value,
                Component.translatable("text.cloth-config.reset_value"),
                () -> defaultValue,
                saveCallback,
                tooltipSupplier
        );
        setMinimum(minimum);
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
