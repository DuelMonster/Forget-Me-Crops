package com.forgetmecrops.util;

import net.minecraft.server.level.ServerLevel;

import java.lang.reflect.Method;

/**
 * Compatibility helper for querying per-dimension vertical bounds across mapped versions.
 * Uses reflection to tolerate method-name differences (getMinBuildHeight vs getMinY, etc.).
 */
public final class LevelHeightBounds {
    private static final int FALLBACK_MIN_Y = -64;
    private static final int FALLBACK_MAX_Y_EXCLUSIVE = 320;

    private LevelHeightBounds() {}

    public static int minY(ServerLevel level) {
        Integer minBuildHeight = invokeInt(level, "getMinBuildHeight");
        if (minBuildHeight != null) return minBuildHeight;

        Integer minY = invokeInt(level, "getMinY");
        if (minY != null) return minY;

        return FALLBACK_MIN_Y;
    }

    /**
     * Returns an exclusive upper bound suitable for AABB maxY.
     */
    public static int maxYExclusive(ServerLevel level) {
        int min = minY(level);

        Integer maxBuildHeight = invokeInt(level, "getMaxBuildHeight");
        if (maxBuildHeight != null && maxBuildHeight > min) return maxBuildHeight;

        Integer maxYInclusive = invokeInt(level, "getMaxY");
        if (maxYInclusive != null && maxYInclusive >= min) return maxYInclusive + 1;

        Integer totalHeight = invokeInt(level, "getHeight");
        if (totalHeight != null && totalHeight > 0) return min + totalHeight;

        return Math.max(min + 1, FALLBACK_MAX_Y_EXCLUSIVE);
    }

    private static Integer invokeInt(Object target, String methodName) {
        if (target == null) return null;
        try {
            Method method = target.getClass().getMethod(methodName);
            Object value = method.invoke(target);
            if (value instanceof Number n) {
                return n.intValue();
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
}
