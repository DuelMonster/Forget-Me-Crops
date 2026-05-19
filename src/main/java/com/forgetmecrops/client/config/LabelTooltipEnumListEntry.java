package com.forgetmecrops.client.config;

import me.shedaniel.clothconfig2.gui.entries.EnumListEntry;
//? if >=26.1 {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;*/
//?} else {
import net.minecraft.client.gui.GuiGraphics;
//?}
import net.minecraft.network.chat.Component;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public final class LabelTooltipEnumListEntry<T extends Enum<?>> extends EnumListEntry<T> {
    private final LabelHitbox hitbox = new LabelHitbox();

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static <T extends Enum<?>> Function<Enum, Component> legacyEnumNameProvider(Function<T, Component> enumNameProvider) {
        return e -> enumNameProvider.apply((T) e);
    }

    @SuppressWarnings("deprecation")
    public LabelTooltipEnumListEntry(Component fieldName,
                                     Class<T> enumClass,
                                     T value,
                                     T defaultValue,
                                     Consumer<T> saveCallback,
                                     Function<T, Component> enumNameProvider,
                                     Supplier<Optional<Component[]>> tooltipSupplier) {
        super(
                fieldName,
                enumClass,
                value,
                Component.translatable("text.cloth-config.reset_value"),
                () -> defaultValue,
                saveCallback,
                legacyEnumNameProvider(enumNameProvider),
                tooltipSupplier
        );
    }

    //? if >=26.1 {
    /*@Override*/
    /*public void extractRenderState(GuiGraphicsExtractor graphics,*/
    //?} else {
    @Override
    public void render(GuiGraphics graphics,
    //?}
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
        //? if >=26.1 {
        /*super.extractRenderState(graphics, index, y, x, entryWidth, entryHeight, mouseX, mouseY, hovered, delta);*/
        //?} else {
        super.render(graphics, index, y, x, entryWidth, entryHeight, mouseX, mouseY, hovered, delta);
        //?}
    }

    @Override
    public Optional<Component[]> getTooltip(int mouseX, int mouseY) {
        return hitbox.isOverLabel(mouseX, mouseY) ? super.getTooltip(mouseX, mouseY) : Optional.empty();
    }
}
