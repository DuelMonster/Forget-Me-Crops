package com.fastharvester;

// 🛡️ DurabilityLogic: keeps track of hoe feelings and when they finally give up.
// Emotional note: handle with care; hoes have feelings too.

import com.fastharvester.enums.DurabilityMode;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import java.util.Map;

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
     * @param level the world level in which damage is applied (may be null)
     * @param hoe the ItemStack representing the hoe to damage
     * @param random a source of randomness (platform-specific), may be null
     */
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
            // Fallback to no enchantments if something unexpected happens
            Constants.LOG.debug("[FastHarvester][DURABILITY] Could not read enchantments: {}", t.toString());
        }

        boolean hasMending = mendingLevel > 0;
        if (Config.mendingNegation && hasMending) return;

        if (!shouldDamageHoe(Config.durabilityMode, unbreakingLevel > 0, hasMending)) return;

        try {
            int max = hoe.getMaxDamage();
            if (max <= 0) return;

            // Compute whether unbreaking prevents this damage event
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
