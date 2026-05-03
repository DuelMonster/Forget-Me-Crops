package com.fastharvester.frame;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/** Immutable (position, approach-direction) pair used by the outward spiral scanner. */
class SpiralStep {
    public final BlockPos pos;
    public final Direction dir;

    SpiralStep(BlockPos pos, Direction dir) {
        this.pos = pos;
        this.dir = dir;
    }
}
