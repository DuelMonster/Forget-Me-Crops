package com.forgetmecrops.util.chest;

import com.forgetmecrops.util.log.LogUtils;
import com.forgetmecrops.util.ExceptionHandler;
import com.forgetmecrops.config.Config;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import com.forgetmecrops.harvest.CropRegistry;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

/**
 * ChestUtils: The mod's polite chest butler — handles all the tedious inventory bookkeeping!
 * <p>
 * Provides helpers for checking free space, inserting drops with stack merging,
 * removing individual items while respecting seed reserves, finding and extracting
 * hoes for replacement, and counting item totals. Everything the harvest pipeline
 * needs to interact with a Container without repeating the same slot-loop boilerplate
 * in twelve different places.
 * </p>
 * <p>
 * All modification methods call setChanged() on BlockEntity-backed containers so
 * changes actually persist to disk. We are responsible adults. Mostly.
 * </p>
 */
public class ChestUtils {
    // Utility class. The chest butler does not live inside the chest.
    private ChestUtils() {}
    /**
     * Check whether the given container has any free space.
     *
     * @param chest container to inspect
     * @return true if there is at least one empty or partially-filled slot
     */
    public static boolean hasSpace(Container chest) {
        if (chest == null) return false;
        if (chest instanceof BlockEntity be && be.isRemoved()) return false;
        for (int i = 0; i < chest.getContainerSize(); i++) {
            ItemStack s = chest.getItem(i);
            if (s.isEmpty()) return true;
            if (s.getCount() < s.getMaxStackSize()) return true;
        }
        return false;
    }

    /**
     * Insert all drops into the chest, merging stacks when possible.
     *
     * @param chest target container
     * @param drops list of ItemStack drops to insert
     */
    public static void insertAll(Container chest, List<ItemStack> drops) {
        if (chest == null || drops == null || drops.isEmpty()) return;
        for (ItemStack drop : drops) {
            if (drop == null || drop.isEmpty()) continue;
            ItemStack remaining = drop.copy();
            insertIntoContainer(chest, remaining, remaining.getCount());
        }
    }

    public static boolean hasHarvestOutputSpace(Container anchorChest, List<Container> outputContainers) {
        for (Container output : collectUniqueContainers(outputContainers, anchorChest)) {
            if (hasSpace(output)) return true;
        }
        return hasSpace(anchorChest);
    }

    public static boolean insertHarvestOutputs(Container anchorChest, List<Container> outputContainers, List<ItemStack> drops) {
        if (drops == null || drops.isEmpty()) return true;

        List<Container> extraOutputs = collectUniqueContainers(outputContainers, anchorChest);
        int nextOutputIndex = 0;

        for (ItemStack drop : drops) {
            if (drop == null || drop.isEmpty()) continue;
            ItemStack remaining = drop.copy();

            if (!extraOutputs.isEmpty()) {
                int consecutiveMisses = 0;
                while (!remaining.isEmpty() && consecutiveMisses < extraOutputs.size()) {
                    Container target = extraOutputs.get(nextOutputIndex);
                    nextOutputIndex = (nextOutputIndex + 1) % extraOutputs.size();
                    int moved = insertIntoContainer(target, remaining, 1);
                    if (moved > 0) {
                        consecutiveMisses = 0;
                    } else {
                        consecutiveMisses++;
                    }
                }
            }

            if (!remaining.isEmpty()) {
                insertIntoContainer(anchorChest, remaining, remaining.getCount());
            }

            if (!remaining.isEmpty()) {
                try { LogUtils.logDebug("[CHEST] insertHarvestOutputs: ran out of space for {} x{}", remaining.getItem(), remaining.getCount()); } catch (Throwable t) {}
                return false;
            }
        }
        return true;
    }

    /**
     * Remove a single item of the given type from the chest, respecting reserves.
     *
     * @param chest target container
     * @param item item type to remove
     * @return true if an item was removed
     */
    public static boolean removeOne(Container chest, Item item) {
        return removeOne(chest, item, true);
    }

    /**
     * Remove one item, optionally enforcing seed reserve protection.
        *
        * @param chest target container
        * @param item item type to remove
        * @param respectSeedReserve whether per-seed reserve protection is enforced
        * @return true if one matching item was removed
     */
    public static boolean removeOne(Container chest, Item item, boolean respectSeedReserve) {
        if (chest == null || item == null) return false;
        if (respectSeedReserve && isSeedItem(item) && !CropRegistry.isSeedAlsoCropFruit(item)) {
            int existing = countItem(chest, item);
            if (existing <= Config.getSeedReservePerType()) {
                try { LogUtils.logDebug("[CHEST] removeOne: refusing to remove {} because existing {} <= reserve {}", item, existing, Config.getSeedReservePerType()); } catch (Throwable t) {}
                return false;
            }
        }

        for (int i = 0; i < chest.getContainerSize(); i++) {
            ItemStack slot = chest.getItem(i);
            if (slot != null && !slot.isEmpty() && slot.getItem() == item) {
                if (slot.getCount() > 1) {
                    ItemStack newSlot = slot.copy();
                    newSlot.setCount(slot.getCount() - 1);
                    chest.setItem(i, newSlot);
                } else {
                    chest.setItem(i, ItemStack.EMPTY);
                }
                if (chest instanceof BlockEntity be) {
                    try { be.setChanged(); } catch (Throwable t) {}
                }
                try { LogUtils.logDebug("[CHEST] removeOne: removed one {} from chest (slot {}) - remaining total {}", item, i, countItem(chest, item)); } catch (Throwable t) {}
                return true;
            }
        }
        try { LogUtils.logDebug("[CHEST] removeOne: no {} found in chest", item); } catch (Throwable t) {}
        return false;
    }

    /**
     * Remove and return the first hoe found in the container.
     *
     * @param chest container to search
     * @return a single ItemStack representing the removed hoe, or ItemStack.EMPTY
     */
    public static net.minecraft.world.item.ItemStack takeFirstHoe(Container chest) {
        if (chest == null) return net.minecraft.world.item.ItemStack.EMPTY;
        for (int i = 0; i < chest.getContainerSize(); i++) {
            ItemStack slot = chest.getItem(i);
            if (slot != null && !slot.isEmpty() && slot.getItem() instanceof HoeItem) {
                ItemStack taken = slot.copy();
                taken.setCount(1);
                try { LogUtils.logDebug("[CHEST] takeFirstHoe: found hoe in slot {} -> item={} damage={} countInSlot={}", i, taken.getItem(), taken.getDamageValue(), slot.getCount()); } catch (Throwable t) {}
                if (slot.getCount() > 1) {
                    ItemStack remaining = slot.copy();
                    remaining.setCount(slot.getCount() - 1);
                    chest.setItem(i, remaining);
                } else {
                    chest.setItem(i, ItemStack.EMPTY);
                }
                if (chest instanceof BlockEntity be) {
                    try { be.setChanged(); } catch (Throwable t) {}
                }
                return taken;
            }
        }
        return net.minecraft.world.item.ItemStack.EMPTY;
    }

    /**
     * Returns a copy of the first hoe found in the container without removing it.
     *
     * @param chest container to search
     * @return a single ItemStack copy of the first hoe found, or ItemStack.EMPTY
     */
    public static net.minecraft.world.item.ItemStack peekFirstHoe(Container chest) {
        if (chest == null) return net.minecraft.world.item.ItemStack.EMPTY;
        for (int i = 0; i < chest.getContainerSize(); i++) {
            ItemStack slot = chest.getItem(i);
            if (slot != null && !slot.isEmpty() && slot.getItem() instanceof HoeItem) {
                ItemStack found = slot.copy();
                found.setCount(1);
                return found;
            }
        }
        return net.minecraft.world.item.ItemStack.EMPTY;
    }

    /**
     * Returns true if the item is a seed or crop fruit that we track reserves for.
     * This is the gatekeeper list for seed-reserve protection — only items in this list
     * can trigger the "don't remove below reserve count" logic in removeOne().
     * If we ever add mod-compat crop seeds, this list needs expanding.
     */
    private static boolean isSeedItem(Item item) {
        if (item == null) return false;
        return item == Items.WHEAT_SEEDS || item == Items.BEETROOT_SEEDS || item == Items.CARROT || item == Items.POTATO
                || item == Items.MELON_SEEDS || item == Items.PUMPKIN_SEEDS || item == Items.NETHER_WART;
    }

    /**
     * Count total number of the given item in the container.
     *
     * @param chest container to inspect
     * @param item item type to count
     * @return total count of the item in the chest
     */
    public static int countItem(Container chest, Item item) {
        if (chest == null || item == null) return 0;
        int cnt = 0;
        for (int i = 0; i < chest.getContainerSize(); i++) {
            ItemStack s = chest.getItem(i);
            if (s != null && !s.isEmpty() && s.getItem() == item) cnt += s.getCount();
        }
        return cnt;
    }

    private static List<Container> collectUniqueContainers(List<Container> containers, Container excluded) {
        List<Container> unique = new ArrayList<>();
        IdentityHashMap<Container, Boolean> seen = new IdentityHashMap<>();
        if (containers == null) return unique;
        for (Container container : containers) {
            if (container == null || container == excluded) continue;
            if (container instanceof BlockEntity be && be.isRemoved()) continue;
            if (seen.put(container, Boolean.TRUE) == null) {
                unique.add(container);
            }
        }
        return unique;
    }

    private static int insertIntoContainer(Container chest, ItemStack remaining, int maxToMove) {
        if (chest == null || remaining == null || remaining.isEmpty() || maxToMove <= 0) return 0;
        if (chest instanceof BlockEntity be && be.isRemoved()) return 0;

        int moved = 0;
        moved += mergeIntoMatchingStacks(chest, remaining, maxToMove - moved);
        if (moved < maxToMove && !remaining.isEmpty()) {
            moved += placeIntoEmptySlots(chest, remaining, maxToMove - moved);
        }
        if (moved > 0 && chest instanceof BlockEntity be) {
            ExceptionHandler.silentTry(() -> be.setChanged());
        }
        return moved;
    }

    private static int mergeIntoMatchingStacks(Container chest, ItemStack remaining, int maxToMove) {
        int moved = 0;
        for (int i = 0; i < chest.getContainerSize() && moved < maxToMove && !remaining.isEmpty(); i++) {
            ItemStack slot = chest.getItem(i);
            if (!slot.isEmpty() && slot.getItem() == remaining.getItem()) {
                int allowed = Math.min(maxToMove - moved, remaining.getCount());
                int space = slot.getMaxStackSize() - slot.getCount();
                if (space > 0) {
                    int move = Math.min(space, allowed);
                    ItemStack newSlot = slot.copy();
                    newSlot.setCount(slot.getCount() + move);
                    chest.setItem(i, newSlot);
                    try { LogUtils.logDebug("[CHEST] insert: merged {} x{} into slot {} (slotnow={})", remaining.getItem(), move, i, newSlot.getCount()); } catch (Throwable t) {}
                    remaining.setCount(remaining.getCount() - move);
                    moved += move;
                }
            }
        }
        return moved;
    }

    private static int placeIntoEmptySlots(Container chest, ItemStack remaining, int maxToMove) {
        int moved = 0;
        for (int i = 0; i < chest.getContainerSize() && moved < maxToMove && !remaining.isEmpty(); i++) {
            ItemStack slot = chest.getItem(i);
            if (slot.isEmpty()) {
                int move = Math.min(Math.min(remaining.getMaxStackSize(), maxToMove - moved), remaining.getCount());
                ItemStack placed = remaining.copy();
                placed.setCount(move);
                chest.setItem(i, placed);
                try { LogUtils.logDebug("[CHEST] insert: placed {} x{} into empty slot {}", remaining.getItem(), move, i); } catch (Throwable t) {}
                remaining.setCount(remaining.getCount() - move);
                moved += move;
            }
        }
        return moved;
    }
}
