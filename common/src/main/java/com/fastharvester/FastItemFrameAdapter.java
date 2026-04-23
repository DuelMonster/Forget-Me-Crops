package com.fastharvester;

/*
 * FastItemFrameAdapter — a tiny bridge so FastItemFrames and FastHarvester can hold hands.
 * Notes: this is intentionally simple; implementations should be resilient and forgiving.
 */

// 🧩 FastItemFrameAdapter: a gentle adapter so the mod can play nicely with FastItemFrames.
// Emotional state: diplomatic and slightly nosy.

/**
 * FastItemFrameAdapter: The detective for item frames and hoes!
 * <p>
 * This interface lets you check if a given item frame is holding a hoe and is attached to a chest. It's like a bouncer for your farm automation club.
 * </p>
 * <p>
 * Why does this matter? Because only the right frames get to join the party!
 * </p>
 */
public interface FastItemFrameAdapter {
    /**
     * Checks if the given frame is holding a hoe and is attached to the chest. No posers allowed!
     * @param frame The item frame to check.
     * @param chest The chest to check against.
     * @return True if it's a match made in farming heaven, false otherwise.
     */
    boolean isItemFrameWithHoe(Object frame, Object chest);
}
