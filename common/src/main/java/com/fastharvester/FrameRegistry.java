package com.fastharvester;

import java.util.*;

/**
 * FrameRegistry: The diligent librarian of all your farm frames!
 * <p>
 * This class keeps track of every active and recorded frame in every dimension. If frames had a yearbook, this would be it.
 * </p>
 * <p>
 * Why does this matter? Because losing track of your frames is like losing your keys—frustrating and embarrassing.
 * </p>
 */
public class FrameRegistry {
    /**
     * Maps dimension IDs to their active frames. Like a VIP list for currently loaded frames.
     */
    private static final Map<String, Set<Object>> activeFrames = new HashMap<>();

    /**
     * Maps dimension IDs to all frames we've ever seen. Nostalgic, isn't it?
     */
    private static final Map<String, Set<Object>> recordedFrames = new HashMap<>();

    /**
     * Counts how many frames are currently active in a given dimension.
     * @param dimensionId The dimension to check.
     * @return The number of active frames. More frames, more fun!
     */
    public static int countActiveFrames(String dimensionId) {
        return activeFrames.getOrDefault(dimensionId, Collections.emptySet()).size();
    }

    /**
     * Counts how many frames have ever been recorded in a given dimension.
     * @param dimensionId The dimension to check.
     * @return The number of recorded frames. Memories!
     */
    public static int countRecordedFrames(String dimensionId) {
        return recordedFrames.getOrDefault(dimensionId, Collections.emptySet()).size();
    }

    /**
     * Loader-specific code should update these sets as frames are loaded/unloaded. Don't leave your frames feeling neglected!
     */
}
