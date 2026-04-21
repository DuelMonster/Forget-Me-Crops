package com.duelmonster.FastHarvester;

/**
 * FastItemFrameAdapter is a loader-agnostic interface for integrating with FastItemFrames or vanilla item frames.
 * Loader-specific code should implement this interface and provide it to the common code.
 *
 * This allows the scanner to use the fastest available item frame lookup path.
 */
public interface FastItemFrameAdapter {
    /**
     * Returns true if the given position contains an item frame with a hoe.
     * Loader-specific code should check for both vanilla and FastItemFrames.
     */
    boolean isItemFrameWithHoe(FrameScanner.BlockPos frame, FrameScanner.BlockPos chest);
}
