package com.forgetmecrops.harvest;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import com.forgetmecrops.util.log.LogUtils;

/**
 * HarvestContext: The clipboard that remembers everything about a single harvest pass!
 * <p>
 * A mutable state bag that travels with the scanner during one full harvest sweep.
 * It knows which anchor triggered the scan, which level we're in, which hoe we're using,
 * and whether the chest is full (spoiler: it usually is at the worst possible moment).
 * </p>
 * <p>
 * Why a context object instead of just passing everything as method arguments? Because
 * "passing 8 parameters to every method" is a horror story nobody asked to live through.
 * </p>
 */
public class HarvestContext {
    /** The anchor (frame/FIF) that kicked off this harvest pass. The whole reason we're here. */
    public final Object anchor;

    /** The level (dimension) this harvest is happening in. Don't accidentally harvest in the Nether. */
    public final Level level;

    /** The hoe currently doing the hard work. Handle with care; replacements are expensive. */
    private ItemStack hoe;

    /** If true, skip the next durability tick — a courtesy flag so replant ops don't double-damage. */
    private boolean skipNextDamage;

    /** The chest this anchor is associated with. Where crops go to live out their post-harvest life. */
    public final Container chest;

    /** Opaque config reference. Carried along for convenience; don't ask too many questions. */
    public final Object config;

    /** Running total of crops harvested this pass. Every crop counts. */
    private int harvestedCount;

    /** Running total of blocks scanned this pass. Even the empty ones. Especially the empty ones. */
    private int blocksScanned;

    /** True if the chest ran out of space during this pass. The scan will bail early when this is set. */
    private boolean chestFull;

    /**
     * Constructs a brand-new HarvestContext for one harvest pass. Get your clipboard ready!
     *
     * @param anchor the frame/FIF anchor that triggered the scan
     * @param level  the server level this scan is running in
     * @param hoe    the hoe stack to use — copied immediately so we own our own reference
     * @param chest  the container attached to this anchor
     * @param config opaque config reference (loader-specific; carried along for callers)
     */
    public HarvestContext(Object anchor, Level level, ItemStack hoe, Container chest, Object config) {
        this.anchor = anchor;
        this.level = level;
        // Copy the hoe stack so external modifications can't silently corrupt our state
        this.hoe = hoe != null ? hoe.copy() : ItemStack.EMPTY;
        this.chest = chest;
        this.config = config;
        this.harvestedCount = 0; // nothing harvested yet — the journey is just beginning
        this.blocksScanned = 0;  // likewise; the farm awaits
        LogUtils.logDebug("[CTX] Created new HarvestContext: anchor={}, level={}, hoe={}, chest={}, config={}", anchor, level, hoe, chest, config);
    }

    // --- Hoe accessors --- //

    /** Returns the current hoe stack. Handle with care; this hoe is doing real work. */
    public ItemStack getHoe() { return hoe; }

    /** Replaces the hoe stack — used when the hoe breaks or gets swapped out of the chest. */
    public void setHoe(ItemStack hoe) { this.hoe = hoe != null ? hoe.copy() : ItemStack.EMPTY; }

    // --- Damage skip flag --- //

    /** Returns whether the next durability application should be skipped. One free pass. */
    public boolean isSkipNextDamage() { return skipNextDamage; }

    /** Sets the damage-skip flag — set this before a replant so the hoe doesn't get double-tapped. */
    public void setSkipNextDamage(boolean v) { skipNextDamage = v; }

    // --- Chest state --- //

    /** Returns true if the chest declared itself full during this pass. Scan will stop soon. */
    public boolean isChestFull() { return chestFull; }

    /** Marks the chest as full (or un-marks it, though that would be optimistic). */
    public void setChestFull(boolean v) { chestFull = v; }

    // --- Counters --- //

    /** Returns the number of crops successfully harvested this pass. The good number. */
    public int getHarvestedCount() { return harvestedCount; }

    /** Returns the total blocks scanned this pass — includes all the empty dirt we visited. */
    public int getBlocksScanned() { return blocksScanned; }

    /** Increments the harvested-crop counter. One more crop bites the dust (into the chest). */
    public void incrementHarvested() { harvestedCount++; LogUtils.logDebug("[CTX] Crops harvested incremented: {}", harvestedCount); }

    /** Increments the blocks-scanned counter. Another block accounted for, empty or otherwise. */
    public void incrementBlocksScanned() { blocksScanned++; LogUtils.logDebug("[CTX] Blocks scanned incremented: {}", blocksScanned); }

    /** Logs a summary of this harvest pass. The post-game stats screen for farming. */
    public void logSummary() { LogUtils.logDebug("[CTX] Harvest summary: harvestedCount={}, blocksScanned={}", harvestedCount, blocksScanned); }
}
