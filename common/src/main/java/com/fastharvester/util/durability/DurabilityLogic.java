package com.fastharvester.util.durability;

import com.fastharvester.enums.DurabilityMode;
import com.fastharvester.Config;
import com.fastharvester.Constants;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import java.util.Map;

/**
 * DurabilityLogic: helper for applying damage to hoes (moved to util.durability).
 */
public class DurabilityLogic {
    private DurabilityLogic() {}

    public static boolean shouldDamageHoe(DurabilityMode mode, boolean hasUnbreaking, boolean hasMending) {
        if (mode == DurabilityMode.NONE) return false;
        if (mode == DurabilityMode.IGNORE_UNBREAKING) return true;
        if (mode == DurabilityMode.NORMAL) {
            if (hasMending) return false;
            return true;
        }
        return true;
    }

    public static void applyDamage(Level level, ItemStack hoe, Object random) {
        if (hoe == null || hoe.isEmpty()) return;
        if (Config.durabilityMode == DurabilityMode.NONE) return;

        int unbreakingLevel = 0;
        int mendingLevel = 0;
        try {
            Map<String, Integer> ench = com.fastharvester.platform.Services.PLATFORM.getEnchantments(hoe);
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
            Constants.LOG.debug("[FastHarvester][DURABILITY] Could not read enchantments: {}", t.toString());
        }

        boolean hasMending = mendingLevel > 0;
        if (Config.mendingNegation && hasMending) return;

        if (!shouldDamageHoe(Config.durabilityMode, unbreakingLevel > 0, hasMending)) return;

        try {
            int max = hoe.getMaxDamage();
            if (max <= 0) return;

            boolean applyDamage = true;
            if (Config.durabilityMode == DurabilityMode.NORMAL && unbreakingLevel > 0) {
                if (level != null) {
                    if (level.getRandom().nextInt(unbreakingLevel + 1) != 0) {
                        applyDamage = false;
                    }
                } else {
                    if (new java.util.Random().nextInt(unbreakingLevel + 1) != 0) {
                        applyDamage = false;
                    }
                }
            }

            if (!applyDamage) return;

            int current = hoe.getDamageValue();
            int next = current + 1;
            if (next >= max) {
                hoe.setCount(0);
            } else {
                hoe.setDamageValue(next);
            }
        } catch (Throwable t) {
            Constants.LOG.warn("[FastHarvester][DURABILITY] Failed to apply damage to hoe", t);
        }
    }
}
