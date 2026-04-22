package com.fastharvester;

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
    public final Object anchor;
    public final Level level;
    public final ItemStack hoe;
    public final Container chest;
    public final Object config;

    // Counters used during a harvest pass
    public int harvestedCount;
    public int blocksScanned;

    /**
     * Creates a new HarvestContext and logs all initial state.
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

    public void incrementHarvested() {
        harvestedCount++;
        Constants.LOG.debug("[FastHarvester][CTX] Crops harvested incremented: {}", harvestedCount);
    }

    public void incrementBlocksScanned() {
        blocksScanned++;
        Constants.LOG.debug("[FastHarvester][CTX] Blocks scanned incremented: {}", blocksScanned);
    }

    public void logSummary() {
        Constants.LOG.info("[FastHarvester][CTX] Harvest summary: harvestedCount={}, blocksScanned={}", harvestedCount, blocksScanned);
    }
}
