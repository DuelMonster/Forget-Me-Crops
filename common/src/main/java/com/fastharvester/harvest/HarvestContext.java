package com.fastharvester.harvest;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import com.fastharvester.util.log.LogUtils;

public class HarvestContext {
    public final Object anchor;
    public final Level level;
    public ItemStack hoe;
    public boolean skipNextDamage = false;
    public final Container chest;
    public final Object config;

    public int harvestedCount;
    public int blocksScanned;
    public boolean chestFull = false;

    public HarvestContext(Object anchor, Level level, ItemStack hoe, Container chest, Object config) {
        this.anchor = anchor;
        this.level = level;
        this.hoe = hoe;
        this.chest = chest;
        this.config = config;
        this.harvestedCount = 0;
        this.blocksScanned = 0;
        LogUtils.logDebug("[CTX] Created new HarvestContext: anchor={}, level={}, hoe={}, chest={}, config={}", anchor, level, hoe, chest, config);
    }

    public void incrementHarvested() { harvestedCount++; LogUtils.logDebug("[CTX] Crops harvested incremented: {}", harvestedCount); }
    public void incrementBlocksScanned() { blocksScanned++; LogUtils.logDebug("[CTX] Blocks scanned incremented: {}", blocksScanned); }
    public void logSummary() { LogUtils.logDebug("[CTX] Harvest summary: harvestedCount={}, blocksScanned={}", harvestedCount, blocksScanned); }
}
