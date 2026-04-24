package com.fastharvester;

// 🧺 ChestUtils: drawer-of-seeds and gentle-hoarder. Pulls out the right seed at the right time.
// Emotional tone: practical and slightly maternal.

import net.minecraft.world.Container;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.List;

/**
 * ChestUtils: Simple, loader-agnostic helpers for container operations used by FastHarvester.
 */
public class ChestUtils {
    public ChestUtils() {}

    /**
     * Check whether the given chest has any available slot or stack room.
     * Humanized aside: returns true if there's somewhere to tuck the seeds away.
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
     * Insert all provided item stacks into the chest, merging stacks where possible.
     * Emotional aside: this is the gentle tidying routine for loot.
     */
    public static void insertAll(Container chest, List<ItemStack> drops) {
        if (chest == null || drops == null || drops.isEmpty()) return;
        boolean changed = false;
        for (ItemStack drop : drops) {
            if (drop == null || drop.isEmpty()) continue;
            ItemStack remaining = drop.copy();
            // Try to merge into existing stacks
            for (int i = 0; i < chest.getContainerSize(); i++) {
                ItemStack slot = chest.getItem(i);
                if (!slot.isEmpty() && slot.getItem() == remaining.getItem()) {
                    int space = slot.getMaxStackSize() - slot.getCount();
                    if (space > 0) {
                        int move = Math.min(space, remaining.getCount());
                        slot.setCount(slot.getCount() + move);
                        remaining.setCount(remaining.getCount() - move);
                        changed = true;
                        if (remaining.isEmpty()) break;
                    }
                }
            }
            // Put remaining into empty slot
            if (!remaining.isEmpty()) {
                for (int i = 0; i < chest.getContainerSize(); i++) {
                    ItemStack slot = chest.getItem(i);
                    if (slot.isEmpty()) {
                        chest.setItem(i, remaining.copy());
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
     * Remove a single item matching {@code item} from the chest, returning true if removed.
     */
    public static boolean removeOne(Container chest, Item item) {
        if (chest == null || item == null) return false;
        // Respect seed reserve policy when in REDUCED mode
        if (Config.seedClutterMode == com.fastharvester.enums.SeedClutterMode.REDUCED && isSeedItem(item)) {
            int existing = countItem(chest, item);
            if (existing <= Config.seedReservePerType) return false;
        }

        for (int i = 0; i < chest.getContainerSize(); i++) {
            ItemStack slot = chest.getItem(i);
            if (slot != null && !slot.isEmpty() && slot.getItem() == item) {
                if (slot.getCount() > 1) {
                    slot.setCount(slot.getCount() - 1);
                } else {
                    chest.setItem(i, ItemStack.EMPTY);
                }
                if (chest instanceof BlockEntity be) {
                    try { be.setChanged(); } catch (Throwable ignored) {}
                }
                return true;
            }
        }
        return false;
    }

    private static boolean isSeedItem(Item item) {
        if (item == null) return false;
        return item == Items.WHEAT_SEEDS || item == Items.BEETROOT_SEEDS || item == Items.CARROT || item == Items.POTATO
                || item == Items.MELON_SEEDS || item == Items.PUMPKIN_SEEDS || item == Items.NETHER_WART;
    }

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
