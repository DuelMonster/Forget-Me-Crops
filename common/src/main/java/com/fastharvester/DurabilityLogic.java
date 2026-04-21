package com.fastharvester;

import com.fastharvester.enums.DurabilityMode;

/**
 * Loader-agnostic durability logic for hoes.
 */
public class DurabilityLogic {
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
