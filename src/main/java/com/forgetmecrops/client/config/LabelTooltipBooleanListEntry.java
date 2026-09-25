package com.forgetmecrops.client.config;

import me.shedaniel.clothconfig2.gui.entries.BooleanListEntry;
//? if mc1 {
/*import net.minecraft.client.gui.GuiGraphics;*/
//?} else {
import net.minecraft.client.gui.GuiGraphicsExtractor;
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

    //? if mc1 {
    /*@Override*/
    /*public void render(GuiGraphics graphics,*/
    //?} else {
    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics,
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
        //? if mc1 {
        /*super.render(graphics, index, y, x, entryWidth, entryHeight, mouseX, mouseY, hovered, delta);*/
        //?} else {
        super.extractRenderState(graphics, index, y, x, entryWidth, entryHeight, mouseX, mouseY, hovered, delta);
        //?}
    }

    @Override
    public Optional<Component[]> getTooltip(int mouseX, int mouseY) {
        return hitbox.isOverLabel(mouseX, mouseY) ? super.getTooltip(mouseX, mouseY) : Optional.empty();
    }
}
