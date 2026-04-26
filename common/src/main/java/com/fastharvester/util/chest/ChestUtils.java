package com.fastharvester.util.chest;

import com.fastharvester.Constants;
import com.fastharvester.Config;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.List;

/**
 * ChestUtils: Simple, loader-agnostic helpers for container operations used by FastHarvester.
 */
public class ChestUtils {
    private ChestUtils() {}
    /**
     * Check whether the given container has any free space.
     *
     * @param chest container to inspect
     * @return true if there is at least one empty or partially-filled slot
     */
    public static boolean hasSpace(Container chest) {
        if (chest == null) return false;
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
        boolean changed = false;
        for (ItemStack drop : drops) {
            if (drop == null || drop.isEmpty()) continue;
            ItemStack remaining = drop.copy();
            for (int i = 0; i < chest.getContainerSize(); i++) {
                ItemStack slot = chest.getItem(i);
                if (!slot.isEmpty() && slot.getItem() == remaining.getItem()) {
                    int space = slot.getMaxStackSize() - slot.getCount();
                    if (space > 0) {
                        int move = Math.min(space, remaining.getCount());
                        ItemStack newSlot = slot.copy();
                        newSlot.setCount(slot.getCount() + move);
                        chest.setItem(i, newSlot);
                        try { Constants.LOG.info("[FastHarvester][CHEST] insertAll: merged {} x{} into slot {} (slotnow={})", remaining.getItem(), move, i, newSlot.getCount()); } catch (Throwable ignored) {}
                        remaining.setCount(remaining.getCount() - move);
                        changed = true;
                        if (remaining.isEmpty()) break;
                    }
                }
            }
            if (!remaining.isEmpty()) {
                for (int i = 0; i < chest.getContainerSize(); i++) {
                    ItemStack slot = chest.getItem(i);
                    if (slot.isEmpty()) {
                        chest.setItem(i, remaining.copy());
                        try { Constants.LOG.info("[FastHarvester][CHEST] insertAll: placed {} x{} into empty slot {}", remaining.getItem(), remaining.getCount(), i); } catch (Throwable ignored) {}
                        remaining.setCount(0);
                        changed = true;
                        break;
                    }
                }
            }
        }
        if (changed && chest instanceof BlockEntity be) {
            try { be.setChanged(); } catch (Throwable ignored) {}
        }
    }

    /**
     * Remove a single item of the given type from the chest, respecting reserves.
     *
     * @param chest target container
     * @param item item type to remove
     * @return true if an item was removed
     */
    public static boolean removeOne(Container chest, Item item) {
        if (chest == null || item == null) return false;
        if (isSeedItem(item)) {
            int existing = countItem(chest, item);
            if (existing <= Config.seedReservePerType) {
                try { Constants.LOG.info("[FastHarvester][CHEST] removeOne: refusing to remove {} because existing {} <= reserve {}", item, existing, Config.seedReservePerType); } catch (Throwable ignored) {}
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
                    try { be.setChanged(); } catch (Throwable ignored) {}
                }
                try { Constants.LOG.debug("[FastHarvester][CHEST] removeOne: removed one {} from chest (slot {}) - remaining total {}", item, i, countItem(chest, item)); } catch (Throwable ignored) {}
                return true;
            }
        }
        try { Constants.LOG.debug("[FastHarvester][CHEST] removeOne: no {} found in chest", item); } catch (Throwable ignored) {}
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
                if (slot.getCount() > 1) {
                    ItemStack remaining = slot.copy();
                    remaining.setCount(slot.getCount() - 1);
                    chest.setItem(i, remaining);
                } else {
                    chest.setItem(i, ItemStack.EMPTY);
                }
                if (chest instanceof BlockEntity be) {
                    try { be.setChanged(); } catch (Throwable ignored) {}
                }
                return taken;
            }
        }
        return net.minecraft.world.item.ItemStack.EMPTY;
    }

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
}
