package com.forgetmecrops.client.config;

import me.shedaniel.clothconfig2.gui.entries.DropdownBoxEntry;
import net.minecraft.client.input.MouseButtonEvent;
//? if >=26.1 {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;*/
//?} else {
import net.minecraft.client.gui.GuiGraphics;
//?}
import net.minecraft.network.chat.Component;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public final class LabelTooltipEnumListEntry<T extends Enum<T>> extends DropdownBoxEntry<T> {
        private static final class CollapsingSelectionCellElement<R>
                extends DropdownBoxEntry.DefaultSelectionCellElement<R> {
            private CollapsingSelectionCellElement(R value, Function<R, Component> textProvider) {
                super(value, textProvider);
            }

            @Override
            public boolean mouseClicked(MouseButtonEvent event, boolean focused) {
                boolean consumed = super.mouseClicked(event, focused);
                if (consumed) {
                    getEntry().getSelectionElement().setFocused(null);
                    getEntry().setFocused(null);
                    getEntry().updateSelected(false);
                    if (getEntry().getParent() != null) {
                        getEntry().getParent().setFocused(null);
                    }
                }
                return consumed;
            }
        }

        private static final class CollapsingSelectionCellCreator<R>
                extends DropdownBoxEntry.DefaultSelectionCellCreator<R> {
            private final Function<R, Component> textProvider;

            private CollapsingSelectionCellCreator(Function<R, Component> textProvider) {
                super(textProvider);
                this.textProvider = textProvider;
            }

            @Override
            public DropdownBoxEntry.SelectionCellElement<R> create(R value) {
                return new CollapsingSelectionCellElement<>(value, textProvider);
            }
        }

    private final LabelHitbox hitbox = new LabelHitbox();
    private T lastObservedValue;

    private void forceCollapseDropdown() {
        getSelectionElement().setFocused(null);
        setFocused(null);
        updateSelected(false);
        if (getParent() != null) {
            getParent().setFocused(null);
        }
    }

    private static String normalizeToken(String input) {
        return input.trim()
                .toLowerCase(Locale.ROOT)
                .replace('_', ' ')
                .replaceAll("\\s+", " ");
    }

    private static <T extends Enum<T>> Function<String, T> enumParser(Class<T> enumClass, Function<T, Component> enumNameProvider) {
        Map<String, T> lookup = new HashMap<>();
        for (T enumConstant : enumClass.getEnumConstants()) {
            lookup.putIfAbsent(normalizeToken(enumConstant.name()), enumConstant);
            lookup.putIfAbsent(normalizeToken(enumConstant.name().replace('_', ' ')), enumConstant);
            lookup.putIfAbsent(normalizeToken(enumNameProvider.apply(enumConstant).getString()), enumConstant);
        }

        return input -> {
            if (input == null || input.isBlank()) {
                return null;
            }

            return lookup.get(normalizeToken(input));
        };
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
                Component.translatable("text.cloth-config.reset_value"),
            tooltipSupplier,
            false,
                () -> defaultValue,
                saveCallback,
            Arrays.asList(enumClass.getEnumConstants()),
            new DropdownBoxEntry.DefaultSelectionTopCellElement<>(
                value,
                enumParser(enumClass, enumNameProvider),
                enumNameProvider
            ),
            new CollapsingSelectionCellCreator<>(enumNameProvider)
        );
        this.lastObservedValue = value;
        setSuggestionMode(false);
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
        T currentValue = getValue();
        if (!Objects.equals(currentValue, lastObservedValue)) {
            forceCollapseDropdown();
            lastObservedValue = currentValue;
        }
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
