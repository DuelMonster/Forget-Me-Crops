package com.forgetmecrops.harvest;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import com.forgetmecrops.util.log.LogUtils;

public class HarvestContext {
    public final Object anchor;
    public final Level level;
    private ItemStack hoe;
    private boolean skipNextDamage;
    public final Container chest;
    public final Object config;

    private int harvestedCount;
    private int blocksScanned;
    private boolean chestFull;

    public HarvestContext(Object anchor, Level level, ItemStack hoe, Container chest, Object config) {
        this.anchor = anchor;
        this.level = level;
        this.hoe = hoe != null ? hoe.copy() : ItemStack.EMPTY;
        this.chest = chest;
        this.config = config;
        this.harvestedCount = 0;
        this.blocksScanned = 0;
        LogUtils.logDebug("[CTX] Created new HarvestContext: anchor={}, level={}, hoe={}, chest={}, config={}", anchor, level, hoe, chest, config);
    }

    public ItemStack getHoe() { return hoe; }
    public void setHoe(ItemStack hoe) { this.hoe = hoe != null ? hoe.copy() : ItemStack.EMPTY; }
    public boolean isSkipNextDamage() { return skipNextDamage; }
    public void setSkipNextDamage(boolean v) { skipNextDamage = v; }
    public boolean isChestFull() { return chestFull; }
    public void setChestFull(boolean v) { chestFull = v; }
    public int getHarvestedCount() { return harvestedCount; }
    public int getBlocksScanned() { return blocksScanned; }

    public void incrementHarvested() { harvestedCount++; LogUtils.logDebug("[CTX] Crops harvested incremented: {}", harvestedCount); }
    public void incrementBlocksScanned() { blocksScanned++; LogUtils.logDebug("[CTX] Blocks scanned incremented: {}", blocksScanned); }
    public void logSummary() { LogUtils.logDebug("[CTX] Harvest summary: harvestedCount={}, blocksScanned={}", harvestedCount, blocksScanned); }
}
