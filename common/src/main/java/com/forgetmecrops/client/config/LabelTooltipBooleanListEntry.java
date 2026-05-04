package com.forgetmecrops.client.config;

import me.shedaniel.clothconfig2.gui.entries.BooleanListEntry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Restricts boolean toggle tooltips to the field label hitbox so they do not trigger over the toggle or reset button.
 */
public final class LabelTooltipBooleanListEntry extends BooleanListEntry {
    private final LabelHitbox hitbox = new LabelHitbox();

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
