package com.forgetmecrops.platform.adapter;

/*
 * FastItemFrameAdapter — a tiny bridge so FastItemFrames and Forget-Me-Crops can hold hands.
 * Notes: this is intentionally simple; implementations should be resilient and forgiving.
 */

/**
 * FastItemFrameAdapter: The detection contract for FastItemFrames integration!
 * <p>
 * Defines the minimal interface that any FIF adapter implementation must satisfy:
 * given a frame object (vanilla ItemFrame or FIF block-entity) and a chest object,
 * determine whether they form a valid farm anchor. Implementations should be resilient
 * and forgiving — if anything is uncertain or throws, the answer is false and life goes on.
 * </p>
 */
public interface FastItemFrameAdapter {
    /**
     * Returns true if the given frame object holds a hoe and is associated with the given chest.
     * This is the core "is this an anchor?" question asked during discovery.
     *
     * @param frame the item frame or FIF block-entity to inspect (we'll figure out which type it is)
     * @param chest the expected container context for validation
     * @return true if this is a hoe-holding anchor-worthy frame, false if not or if anything explodes
     */
    boolean isItemFrameWithHoe(Object frame, Object chest);
}
