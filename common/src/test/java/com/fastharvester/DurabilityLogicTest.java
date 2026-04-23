package com.fastharvester;

/*
 * DurabilityLogicTest — unit tests for `DurabilityLogic`.
 * Humanized note: these tests guard hoe feelings so your tools don't throw tantrums.
 */

import com.fastharvester.enums.DurabilityMode;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class DurabilityLogicTest {

    @Test
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
     * Verify that Mending prevents damage under NORMAL mode when mendingNegation is not configured.
     */
    @Test
    public void testShouldDamageHoe_NormalWithMending() {
        assertFalse(DurabilityLogic.shouldDamageHoe(DurabilityMode.NORMAL, false, true));
    }

    /**
     * Verify that Unbreaking does not prevent damage under NORMAL mode if no Mending.
     */
    @Test
    public void testShouldDamageHoe_NormalWithUnbreakingNoMending() {
        assertTrue(DurabilityLogic.shouldDamageHoe(DurabilityMode.NORMAL, true, false));
    }
}
