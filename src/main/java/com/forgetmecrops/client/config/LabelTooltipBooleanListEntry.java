package com.forgetmecrops.client.config;

import me.shedaniel.clothconfig2.gui.entries.BooleanListEntry;
//? if >=26.1 {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;*/
//?} else {
import net.minecraft.client.gui.GuiGraphics;
//?}
import net.minecraft.network.chat.Component;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class LabelTooltipBooleanListEntry extends BooleanListEntry {
    @FunctionalInterface
    public interface BoolConsumer {
        void accept(boolean value);
    }

    private final LabelHitbox hitbox = new LabelHitbox();

    private static Consumer<Boolean> boxedCallback(BoolConsumer saveCallback) {
        return v -> saveCallback.accept(v != null && v);
    }

    @SuppressWarnings("deprecation")
    public LabelTooltipBooleanListEntry(Component fieldName,
                                        boolean value,
                                        BoolConsumer saveCallback,
                                        Supplier<Optional<Component[]>> tooltipSupplier) {
        super(
                fieldName,
                value,
                Component.translatable("text.cloth-config.reset_value"),
                null,
                boxedCallback(saveCallback),
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
