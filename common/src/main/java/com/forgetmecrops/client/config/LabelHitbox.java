package com.forgetmecrops.client.config;

/**
 * Tracks the left-side label lane of a config row for tooltip hit-testing.
 */
public final class LabelHitbox {
    // 150 px value widget + reset-button text width (~40 px) + 4 px gap.
    private static final int BUTTON_AREA_WIDTH = 154;

    private int x;
    private int y;
    private int width;
    private int height;

    public void update(int entryX, int entryY, int entryWidth, int entryHeight) {
        this.x = entryX;
        this.y = entryY;
        this.height = entryHeight;
        // Cover the full label lane up to where right-side controls begin.
        this.width = Math.max(0, entryWidth - BUTTON_AREA_WIDTH);
    }

    public boolean isOverLabel(int mouseX, int mouseY) {
        return width > 0 && height > 0
                && mouseX >= x && mouseX < x + width
                && mouseY >= y && mouseY < y + height;
    }
}