package com.forgetmecrops.client.config;

/**
 * LabelHitbox tracks the left-side label lane so tooltips only show over labels.
 */
public final class LabelHitbox {
    // 150px widget area plus a small gap for reset control text.
    private static final int BUTTON_AREA_WIDTH = 154;

    private int x;
    private int y;
    private int width;
    private int height;

    public void update(int entryX, int entryY, int entryWidth, int entryHeight) {
        this.x = entryX;
        this.y = entryY;
        this.height = entryHeight;
        this.width = Math.max(0, entryWidth - BUTTON_AREA_WIDTH);
    }

    public boolean isOverLabel(int mouseX, int mouseY) {
        return width > 0 && height > 0
                && mouseX >= x && mouseX < x + width
                && mouseY >= y && mouseY < y + height;
    }
}
