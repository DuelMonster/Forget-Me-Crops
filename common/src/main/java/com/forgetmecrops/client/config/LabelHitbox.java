package com.forgetmecrops.client.config;

/**
 * LabelHitbox: The invisible hit zone that makes label-side tooltips work!
 * <p>
 * Cloth Config's default behavior only fires tooltip events over the right-side widget
 * area. LabelHitbox tracks the left-side label lane coordinates so our custom config
 * entries can also show descriptive tooltips when the mouse hovers over the text label.
 * Small class. Big quality-of-life improvement for anyone who reads their tooltips.
 * </p>
 */
public final class LabelHitbox {
    // The right-side widget area that belongs to the value widget + reset button; we cover everything else
    // 150 px value widget + reset-button text width (~40 px) + 4 px gap.
    // Width in pixels of the right-side control area (value widget + reset button).
    // Everything to the left of this is the label hitbox.
    private static final int BUTTON_AREA_WIDTH = 154;

    private int x;
    private int y;
    private int width;
    private int height;

    /**
     * Updates the hitbox boundaries from the entry's current layout position and dimensions.
     * Called each frame to keep the hitbox synchronized with where the entry is actually drawn.
     * The label lane is everything except the right-side button area.
     *
     * @param entryX      the X coordinate of the entry's top-left
     * @param entryY      the Y coordinate of the entry's top-left
     * @param entryWidth  the total width of the entry
     * @param entryHeight the height of the entry
     */
    public void update(int entryX, int entryY, int entryWidth, int entryHeight) {
        this.x = entryX;
        this.y = entryY;
        this.height = entryHeight;
        // Cover the full label lane up to where right-side controls begin.
        this.width = Math.max(0, entryWidth - BUTTON_AREA_WIDTH);
    }

    /**
     * Checks if the given mouse coordinates are over the label hitbox (left-side text area).
     * Returns false if the hitbox has zero width or height (no valid label area).
     *
     * @param mouseX the mouse X coordinate
     * @param mouseY the mouse Y coordinate
     * @return true if the coordinates are inside the label hitbox bounds
     */
    public boolean isOverLabel(int mouseX, int mouseY) {
        return width > 0 && height > 0
                && mouseX >= x && mouseX < x + width
                && mouseY >= y && mouseY < y + height;
    }
}