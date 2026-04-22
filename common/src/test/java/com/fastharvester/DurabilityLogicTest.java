package com.fastharvester;

import com.fastharvester.enums.DurabilityMode;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class DurabilityLogicTest {

    @Test
    public void testShouldDamageHoe_None() {
        assertFalse(DurabilityLogic.shouldDamageHoe(DurabilityMode.NONE, false, false));
    }

    @Test
    public void testShouldDamageHoe_IgnoreUnbreaking() {
        assertTrue(DurabilityLogic.shouldDamageHoe(DurabilityMode.IGNORE_UNBREAKING, true, false));
    }

    @Test
    public void testShouldDamageHoe_NormalWithMending() {
        assertFalse(DurabilityLogic.shouldDamageHoe(DurabilityMode.NORMAL, false, true));
    }

    @Test
    public void testShouldDamageHoe_NormalWithUnbreakingNoMending() {
        assertTrue(DurabilityLogic.shouldDamageHoe(DurabilityMode.NORMAL, true, false));
    }
}
