package com.forgetmecrops.util.durability;

import com.forgetmecrops.enums.DurabilityMode;
import com.forgetmecrops.config.Config;
import com.forgetmecrops.util.log.LogUtils;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * DurabilityLogic: The accountant who decides exactly how much wear your hoe takes each harvest!
 * <p>
 * Implements the configured durability consumption rules: NORMAL (realistic wear with
 * Unbreaking probability roll and Mending respect), IGNORE_UNBREAKING (pessimistic wear
 * every single time regardless of enchantments), and NONE (your hoe is immortal — enjoy).
 * Also gracefully destroys the hoe stack when the damage counter reaches maximum,
 * which triggers the broken-hoe replacement flow upstream.
 * </p>
 * <p>
 * Mending negation is checked upstream in the applyDamage method itself (via Config.isMendingNegation()),
 * and Unbreaking probability is calculated here using the level's random source when available,
 * or ThreadLocalRandom as a fallback. We try to be correct. The hoe tries to survive.
 * </p>
 */
public class DurabilityLogic {
    // Utility class. The durability accountant does not take damage personally.
    private DurabilityLogic() {}

    /**
     * Determine whether the hoe should be damaged based on mode and enchantments.
     *
     * @param mode configured durability mode
     * @param hasUnbreaking whether the tool has Unbreaking
     * @param hasMending whether the tool has Mending
     * @return true if the hoe should take durability damage
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
     * Apply one point of damage to the hoe according to configuration and enchantments.
     *
     * @param level the level used for randomness and logging (may be null)
     * @param hoe the hoe ItemStack to damage
     * @param random optional random provider (kept for compatibility)
     */
    public static void applyDamage(Level level, ItemStack hoe, Object random) {
        if (hoe == null || hoe.isEmpty()) return;
        if (Config.getDurabilityMode() == DurabilityMode.NONE) return;

        int unbreakingLevel = 0;
        int mendingLevel = 0;
        try {
            Map<String, Integer> ench = com.forgetmecrops.platform.Services.PLATFORM.getEnchantments(hoe);
            if (ench != null) {
                for (Map.Entry<String, Integer> e : ench.entrySet()) {
                    String id = e.getKey();
                    int lvl = (e.getValue() == null) ? 0 : e.getValue();
                    if (id != null && id.toLowerCase(java.util.Locale.ROOT).contains("unbreaking")) {
                        unbreakingLevel = Math.max(unbreakingLevel, lvl);
                    }
                    if (id != null && id.toLowerCase(java.util.Locale.ROOT).contains("mending")) {
                        mendingLevel = Math.max(mendingLevel, lvl);
                    }
                }
            }
        } catch (Throwable t) {
            LogUtils.logDebug("[DURABILITY] Could not read enchantments", t);
        }

        // Mending negation check: if the hoe has Mending AND we're configured to negate it,
        // we still skip damage (the negation already cancels; the upstream caller handles it).
        boolean hasMending = mendingLevel > 0;
        if (Config.isMendingNegation() && hasMending) return;

        // Ask shouldDamageHoe whether damage applies given the mode and enchantment combo
        if (!shouldDamageHoe(Config.getDurabilityMode(), unbreakingLevel > 0, hasMending)) return;

        try {
            int max = hoe.getMaxDamage();
            if (max <= 0) return; // unbreakable item (Gold Sword behavior etc.)

            int current = hoe.getDamageValue();
            boolean applyDamage = true;
            // Unbreaking probability roll: 1/(level+1) chance of taking damage per use,
            // same formula vanilla uses for tools. NORMAL mode only.
            if (Config.getDurabilityMode() == DurabilityMode.NORMAL && unbreakingLevel > 0) {
                if (level != null) {
                    if (level.getRandom().nextInt(unbreakingLevel + 1) != 0) {
                        applyDamage = false; // luck out this tick — hoe lives to harvest another day
                    }
                } else {
                    if (ThreadLocalRandom.current().nextInt(unbreakingLevel + 1) != 0) {
                        applyDamage = false; // fallback random when level is null (edge case but possible)
                    }
                }
            }

            try { LogUtils.logDebug("[DURABILITY] applyDamage pre: item={} currentDamage={} max={} unbreaking={} mending={} willApply={}", hoe.getItem(), current, max, unbreakingLevel, mendingLevel, applyDamage); } catch (Throwable ignored) {}

            if (!applyDamage) return;

            int next = current + 1;
            if (next >= max) {
                // Hoe has reached its final tick — destroy the stack to trigger replacement flow
                try { LogUtils.logDebug("[DURABILITY] applyDamage: next >= max -> destroying stack"); } catch (Throwable ignored) {}
                hoe.setCount(0);
            } else {
                hoe.setDamageValue(next);
                try { LogUtils.logDebug("[DURABILITY] applyDamage post: newDamage={} (was={})", next, current); } catch (Throwable ignored) {}
            }
        } catch (Throwable t) {
            LogUtils.logWarn("[DURABILITY] Failed to apply damage to hoe", t);
        }
    }
}
