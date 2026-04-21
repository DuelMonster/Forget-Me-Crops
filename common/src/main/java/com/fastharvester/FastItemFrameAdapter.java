package com.fastharvester;

/**
 * Loader-agnostic interface for item frame/hoe detection.
 */
public interface FastItemFrameAdapter {
    boolean isItemFrameWithHoe(Object frame, Object chest);
}
