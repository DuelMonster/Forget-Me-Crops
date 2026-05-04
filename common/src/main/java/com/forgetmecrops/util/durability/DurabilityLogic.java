package com.forgetmecrops.util.durability;

import com.forgetmecrops.enums.DurabilityMode;
import com.forgetmecrops.config.Config;
import com.forgetmecrops.util.log.LogUtils;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * DurabilityLogic: helper for applying damage to hoes (moved to util.durability).
 */
public class DurabilityLogic {
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
        if (Config.durabilityMode == DurabilityMode.NONE) return;

        int unbreakingLevel = 0;
        int mendingLevel = 0;
        try {
            Map<String, Integer> ench = com.forgetmecrops.platform.Services.PLATFORM.getEnchantments(hoe);
            if (ench != null) {
                for (Map.Entry<String, Integer> e : ench.entrySet()) {
                    String id = e.getKey();
                    int lvl = (e.getValue() == null) ? 0 : e.getValue();
                    if (id != null && id.toLowerCase().contains("unbreaking")) {
                        unbreakingLevel = Math.max(unbreakingLevel, lvl);
                    }
                    if (id != null && id.toLowerCase().contains("mending")) {
                        mendingLevel = Math.max(mendingLevel, lvl);
                    }
                }
            }
        } catch (Throwable t) {
            LogUtils.logDebug("[DURABILITY] Could not read enchantments", t);
        }

        boolean hasMending = mendingLevel > 0;
        if (Config.mendingNegation && hasMending) return;

        if (!shouldDamageHoe(Config.durabilityMode, unbreakingLevel > 0, hasMending)) return;

        try {
            int max = hoe.getMaxDamage();
            if (max <= 0) return;

            int current = hoe.getDamageValue();
            boolean applyDamage = true;
            if (Config.durabilityMode == DurabilityMode.NORMAL && unbreakingLevel > 0) {
                if (level != null) {
                    if (level.getRandom().nextInt(unbreakingLevel + 1) != 0) {
                        applyDamage = false;
                    }
                } else {
                    if (ThreadLocalRandom.current().nextInt(unbreakingLevel + 1) != 0) {
                        applyDamage = false;
                    }
                }
            }

            try { LogUtils.logDebug("[DURABILITY] applyDamage pre: item={} currentDamage={} max={} unbreaking={} mending={} willApply={}", hoe.getItem(), current, max, unbreakingLevel, mendingLevel, applyDamage); } catch (Throwable ignored) {}

            if (!applyDamage) return;

            int next = current + 1;
            if (next >= max) {
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
