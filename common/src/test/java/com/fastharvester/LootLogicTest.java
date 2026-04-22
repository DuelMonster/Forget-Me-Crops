package com.fastharvester;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class LootLogicTest {

    @Test
    public void calculateLootWithinBounds_NoFortune() {
        for (int i = 0; i < 200; i++) {
            int v = TestUtils.calculateLootSimple("wheat", 0);
            assertTrue(v >= 1 && v <= 2, "value " + v + " not in [1,2]");
        }
    }

    @Test
    public void calculateLootWithinBounds_WithFortune() {
        int fortune = 3;
        for (int i = 0; i < 200; i++) {
            int v = TestUtils.calculateLootSimple("wheat", fortune);
            assertTrue(v >= 1 && v <= 2 + fortune, "value " + v + " not in [1," + (2 + fortune) + "]");
        }
    }
}
