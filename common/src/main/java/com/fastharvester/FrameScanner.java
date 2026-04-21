package com.fastharvester;

import java.util.*;

/**
 * Loader-agnostic farm scanning and automation logic.
 * See TECHNICAL.md for full rules and traversal details.
 */
public class FrameScanner {
    public static final int MAX_FRAMES_PER_RUN = 24;
    public static final int MAX_BLOCKS_PER_RUN = 3072;

    public static class Anchor {
        public final Object chest;
        public final Object frame;
        public final Object hoe;
        public Anchor(Object chest, Object frame, Object hoe) {
            this.chest = chest;
            this.frame = frame;
            this.hoe = hoe;
        }
    }

    /**
     * Loader-specific code must provide world/entity access and call these methods.
     */
}
