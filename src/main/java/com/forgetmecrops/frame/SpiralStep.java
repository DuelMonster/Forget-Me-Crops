package com.forgetmecrops.frame;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * SpiralStep: A single breadcrumb on the outward spiral trail!
 * <p>
 * Represents one (position, approach-direction) pair in the farm scan spiral. Think of it as
 * a GPS waypoint, except instead of "turn left in 200 meters" it says "harvest here, and you
 * came from THAT direction over there."
 * </p>
 * <p>
 * Why the direction? Because the scanner needs to know which face of each block it approached
 * from — like how you wouldn't walk into your own house through the chimney (hopefully).
 * </p>
 * <p>
 * Immutable by design. Once created, a SpiralStep stays exactly as it is — a tiny, trustworthy
 * pair of coordinates and a direction, locked forever in their humble purpose.
 * </p>
 */
class SpiralStep {
    /** The block position to visit on this step of the spiral. X marks the crops. */
    public final BlockPos pos;

    /**
     * The direction from which this step was approached during spiral generation.
     * The scanner uses this to correctly orient harvest operations and frame interactions.
     */
    public final Direction dir;

    /**
     * Creates a new SpiralStep — stamping a single waypoint into the spiral's collective memory.
     *
     * @param pos the block position for this step; where the magic (or boring dirt) happens
     * @param dir the approach direction; from whence we came on this leg of the journey
     */
    SpiralStep(BlockPos pos, Direction dir) {
        this.pos = pos;
        this.dir = dir;
    }
}
