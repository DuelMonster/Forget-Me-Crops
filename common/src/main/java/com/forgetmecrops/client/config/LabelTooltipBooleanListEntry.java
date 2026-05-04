package com.forgetmecrops.client.config;

import me.shedaniel.clothconfig2.gui.entries.BooleanListEntry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * LabelTooltipBooleanListEntry: A boolean toggle that only shows its tooltip over the label!
 * <p>
 * Extends Cloth Config's {@link BooleanListEntry} and adds a {@link LabelHitbox} to restrict
 * tooltip display to the left-side label area. That way hovering the toggle button itself
 * doesn't accidentally trigger the big descriptive tooltip. Tooltips in the right place:
 * good UX is in the details.
 * </p>
 */
public final class LabelTooltipBooleanListEntry extends BooleanListEntry {
    // The hitbox that tracks the label lane position during each render pass
    private final LabelHitbox hitbox = new LabelHitbox();

    /**
     * Creates a boolean toggle entry with label-only tooltip behavior.
     *
     * @param fieldName       the display label shown on the left side of the row
     * @param value           the current boolean value to initialize the toggle with
     * @param saveCallback    called when the player saves the config screen
     * @param tooltipSupplier provides the tooltip components to show over the label area
     */
    public LabelTooltipBooleanListEntry(Component fieldName,
                                        boolean value,
                                        Consumer<Boolean> saveCallback,
                                        Supplier<Optional<Component[]>> tooltipSupplier) {
        super(
                fieldName,
                value,
                Component.translatable("text.cloth-config.reset_value"),
                null,
                saveCallback,
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
