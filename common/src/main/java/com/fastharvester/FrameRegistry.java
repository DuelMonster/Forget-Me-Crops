package com.fastharvester;

import java.util.*;

/**
 * Tracks active and recorded frames per dimension.
 */
public class FrameRegistry {
    private static final Map<String, Set<Object>> activeFrames = new HashMap<>();
    private static final Map<String, Set<Object>> recordedFrames = new HashMap<>();

    public static int countActiveFrames(String dimensionId) {
        return activeFrames.getOrDefault(dimensionId, Collections.emptySet()).size();
    }
    public static int countRecordedFrames(String dimensionId) {
        return recordedFrames.getOrDefault(dimensionId, Collections.emptySet()).size();
    }
    // Loader-specific code should update these sets as frames are loaded/unloaded.
}
