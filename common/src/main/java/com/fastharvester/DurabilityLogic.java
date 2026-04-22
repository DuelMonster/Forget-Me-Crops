package com.fastharvester;

import com.fastharvester.enums.DurabilityMode;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * DurabilityLogic: The wise old sage of hoe-wear and tear!
 * <p>
 * This class decides when your hoe should take damage, based on your chosen mode and enchantments. It's like a referee for tool suffering.
 * </p>
 * <p>
 * Why does this matter? Because nobody wants a broken hoe—or a game that's too easy!
 * </p>
 */
public class DurabilityLogic {
    /**
     * Creates a new DurabilityLogic. For now, it's just a wise placeholder!
     */
    public DurabilityLogic() {}
    /**
     * Should we damage the hoe? The eternal question.
     * @param mode The durability mode (choose your destiny).
     * @param hasUnbreaking Does the hoe have Unbreaking? (Lucky!)
     * @param hasMending Does the hoe have Mending? (Cheater!)
     * @return True if the hoe should take damage, false if it gets a free pass.
     */
    public static boolean shouldDamageHoe(DurabilityMode mode, boolean hasUnbreaking, boolean hasMending) {
        if (mode == DurabilityMode.NONE) return false;
        if (mode == DurabilityMode.IGNORE_UNBREAKING) return true;
        if (mode == DurabilityMode.NORMAL) {
            if (hasMending) return false;
            return true;
        }
        return true;
    }

    /**
     * Applies damage to the provided hoe according to the configured durability rules.
     * This implementation is intentionally conservative and will not attempt advanced
     * enchantment math — it's a safe common-side placeholder until loader-specific
     * behavior is implemented.
     */
    public static void applyDamage(Level level, ItemStack hoe, Object random) {
        if (hoe == null || hoe.isEmpty()) return;
        // Conservative defaults: common module avoids relying on specific enchantment registry types here.
        boolean hasUnbreaking = false;
        boolean hasMending = false;
        if (!shouldDamageHoe(Config.durabilityMode, hasUnbreaking, hasMending)) return;

        try {
            int max = hoe.getMaxDamage();
            if (max <= 0) return;
            int current = hoe.getDamageValue();
            int next = current + 1;
            if (next >= max) {
                // Tool would break
                hoe.setCount(0);
            } else {
                hoe.setDamageValue(next);
            }
        } catch (Throwable t) {
            Constants.LOG.warn("[FastHarvester][DURABILITY] Failed to apply damage to hoe", t);
        }
    }
}
