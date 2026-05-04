package com.forgetmecrops.platform.adapter;

/*
 * FastItemFrameAdapter — a tiny bridge so FastItemFrames and ForgetMeCrops can hold hands.
 * Notes: this is intentionally simple; implementations should be resilient and forgiving.
 */

/**
 * FastItemFrameAdapter: The detective for item frames and hoes!
 */
public interface FastItemFrameAdapter {
    /**
     * Checks if the given frame is holding a hoe and is attached to the chest.
     * @param frame The item frame to check.
     * @param chest The chest to check against.
     * @return True if it's a match made in farming heaven, false otherwise.
     */
    boolean isItemFrameWithHoe(Object frame, Object chest);
}
