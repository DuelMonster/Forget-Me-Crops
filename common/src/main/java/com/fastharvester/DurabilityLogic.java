package com.fastharvester;

import com.fastharvester.enums.DurabilityMode;

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
}
