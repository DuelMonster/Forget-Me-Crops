package com.forgetmecrops;

/*
 * DurabilityLogicTest — unit tests for `DurabilityLogic`.
 * Humanized note: these tests guard hoe feelings so your tools don't throw tantrums.
 */

import com.forgetmecrops.enums.DurabilityMode;
import com.forgetmecrops.util.durability.DurabilityLogic;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class DurabilityLogicTest {

    /**
     * Verify that no damage is applied when DurabilityMode.NONE is used.
     */
    @Test
    public void testShouldDamageHoe_None() {
        assertFalse(DurabilityLogic.shouldDamageHoe(DurabilityMode.NONE, false, false));
    }

    /**
     * Verify that damage is applied when IGNORE_UNBREAKING is set, regardless of Unbreaking enchantment.
     */
    @Test
    public void testShouldDamageHoe_IgnoreUnbreaking() {
        assertTrue(DurabilityLogic.shouldDamageHoe(DurabilityMode.IGNORE_UNBREAKING, true, false));
    }

    /**
     * Verify that active Mending protection prevents damage under NORMAL mode.
     */
    @Test
    public void testShouldDamageHoe_NormalWithMendingProtection() {
        assertFalse(DurabilityLogic.shouldDamageHoe(DurabilityMode.NORMAL, false, true));
    }

    /**
     * Verify that active Mending protection also prevents damage in IGNORE_UNBREAKING mode.
     */
    @Test
    public void testShouldDamageHoe_IgnoreUnbreakingWithMendingProtection() {
        assertFalse(DurabilityLogic.shouldDamageHoe(DurabilityMode.IGNORE_UNBREAKING, true, true));
    }

    /**
     * Verify that Unbreaking does not prevent damage under NORMAL mode if no Mending.
     */
    @Test
    public void testShouldDamageHoe_NormalWithUnbreakingNoMending() {
        assertTrue(DurabilityLogic.shouldDamageHoe(DurabilityMode.NORMAL, true, false));
    }
}
