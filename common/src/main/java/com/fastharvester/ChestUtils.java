package com.fastharvester;

import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import java.util.List;

/**
 * ChestUtils: Simple, loader-agnostic helpers for container operations used by FastHarvester.
 */
public class ChestUtils {
    public ChestUtils() {}

    public static boolean hasSpace(Container chest) {
        if (chest == null) return false;
        for (int i = 0; i < chest.getContainerSize(); i++) {
            ItemStack s = chest.getItem(i);
            if (s.isEmpty()) return true;
            if (s.getCount() < s.getMaxStackSize()) return true;
        }
        return false;
    }

    public static void insertAll(Container chest, List<ItemStack> drops) {
        if (chest == null || drops == null || drops.isEmpty()) return;
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
                        break;
                    }
                }
            }
        }
    }

    /**
     * Remove a single item matching {@code item} from the chest, returning true if removed.
     */
    public static boolean removeOne(Container chest, Item item) {
        if (chest == null || item == null) return false;
        for (int i = 0; i < chest.getContainerSize(); i++) {
            ItemStack slot = chest.getItem(i);
            if (slot != null && !slot.isEmpty() && slot.getItem() == item) {
                if (slot.getCount() > 1) {
                    slot.setCount(slot.getCount() - 1);
                } else {
                    chest.setItem(i, ItemStack.EMPTY);
                }
                return true;
            }
        }
        return false;
    }
}
