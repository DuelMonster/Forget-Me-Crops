package com.fastharvester;

// 🧭 HarvestContext: carries the state and tiny hopes for a single scan. Think of it as the scanner's backpack.
// Why it matters: neat state makes repeatable, understandable scanning decisions.

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * HarvestContext: The diary for every harvest adventure!
 * <p>
 * Holds the details for a single harvest operation: anchor, level, hoe, chest, and counters.
 * </p>
 */
public class HarvestContext {
    /** The anchor object for this harvest (typically a FrameScanner.Anchor). */
    public final Object anchor;
    /** The world level in which harvesting occurs. */
    public final Level level;
    /** The hoe ItemStack currently in use for this harvest pass. */
    public ItemStack hoe;
    /** Linked chest container used for drops and replacements. */
    public final Container chest;
    /** Loader-specific configuration object (if any). */
    public final Object config;

    // Counters used during a harvest pass
    /** Number of crops harvested during this scan. */
    public int harvestedCount;
    /** Number of blocks scanned during this scan. */
    public int blocksScanned;
    /** Whether the linked chest became full during the scan. */
    public boolean chestFull = false;

    /**
     * Creates a new HarvestContext and logs all initial state.
     */
    /**
     * Creates a new HarvestContext and logs all initial state.
     * @param anchor The anchor for this harvest (FrameScanner.Anchor or similar).
     * @param level The level in which harvesting will occur.
     * @param hoe The hoe ItemStack used for harvesting.
     * @param chest The linked chest container for drops and replacements.
     * @param config Loader-specific config object if any.
     */
    public HarvestContext(Object anchor, Level level, ItemStack hoe, Container chest, Object config) {
        this.anchor = anchor;
        this.level = level;
        this.hoe = hoe;
        this.chest = chest;
        this.config = config;
        this.harvestedCount = 0;
        this.blocksScanned = 0;
        Constants.LOG.info("[FastHarvester][CTX] Created new HarvestContext: anchor={}, level={}, hoe={}, chest={}, config={}", anchor, level, hoe, chest, config);
    }
    /**
     * Increment the harvested counter and emit a debug message.
     */
    public void incrementHarvested() {
        harvestedCount++;
        Constants.LOG.debug("[FastHarvester][CTX] Crops harvested incremented: {}", harvestedCount);
    }

    /**
     * Increment the scanned blocks counter and emit a debug message.
     */
    public void incrementBlocksScanned() {
        blocksScanned++;
        Constants.LOG.debug("[FastHarvester][CTX] Blocks scanned incremented: {}", blocksScanned);
    }

    /**
     * Log a summary of the harvest context, useful at the end of a scan.
     */
    public void logSummary() {
        Constants.LOG.info("[FastHarvester][CTX] Harvest summary: harvestedCount={}, blocksScanned={}", harvestedCount, blocksScanned);
    }
}
