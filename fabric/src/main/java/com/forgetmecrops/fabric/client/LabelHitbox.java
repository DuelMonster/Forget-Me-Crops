package com.forgetmecrops.fabric.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Tracks the label portion of a config list entry row for tooltip hit-testing.
 *
 * The right side of every config row is occupied by a value widget and a reset
 * button.  This helper records label coordinates during render so that
 * {@code getTooltip(mouseX, mouseY)} overrides can suppress the tooltip when
 * the cursor is over those controls.
 */
final class LabelHitbox {
    // 150 px value widget + reset-button text width (~40 px) + 4 px gap ≈ 154 px.
    // This is a conservative ceiling; the exact reset-button width varies with
    // the translation length, but it is always smaller than this constant.
    static final int BUTTON_AREA_WIDTH = 154;

    private int x;
    private int y;
    private int width;
    private int height;

    void update(int entryX, int entryY, int entryWidth, int entryHeight, Component displayedFieldName) {
        this.x = entryX;
        this.y = entryY;
        this.height = entryHeight;
        int maxLabelRight = entryX + Math.max(0, entryWidth - BUTTON_AREA_WIDTH);
        int renderedLabelWidth = Minecraft.getInstance().font.width(displayedFieldName) + 4;
        this.width = Math.max(0, Math.min(renderedLabelWidth, maxLabelRight - entryX));
    }

    boolean isOverLabel(int mouseX, int mouseY) {
        return width > 0 && height > 0
                && mouseX >= x && mouseX < x + width
                && mouseY >= y && mouseY < y + height;
    }
}
