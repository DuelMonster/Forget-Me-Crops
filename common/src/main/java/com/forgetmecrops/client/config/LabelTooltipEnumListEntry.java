package com.forgetmecrops.client.config;

import me.shedaniel.clothconfig2.gui.entries.EnumListEntry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Restricts enum entry tooltips to the field label hitbox so they do not trigger over action buttons.
 */
public final class LabelTooltipEnumListEntry<T extends Enum<?>> extends EnumListEntry<T> {
    private final LabelHitbox hitbox = new LabelHitbox();

    @SuppressWarnings("unchecked")
    public LabelTooltipEnumListEntry(Component fieldName,
                                     Class<T> enumClass,
                                     T value,
                                     T defaultValue,
                                     Consumer<T> saveCallback,
                                     Supplier<Optional<Component[]>> tooltipSupplier) {
        super(
                fieldName,
                enumClass,
                value,
                Component.translatable("text.cloth-config.reset_value"),
                () -> defaultValue,
                saveCallback,
                (Function<Enum, Component>) EnumListEntry.DEFAULT_NAME_PROVIDER,
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
