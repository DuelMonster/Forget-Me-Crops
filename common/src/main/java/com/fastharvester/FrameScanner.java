package com.fastharvester;

/**
 * FrameScanner: The intrepid explorer of your blocky world!
 * <p>
 * This class is responsible for scanning farms, finding frames, and making sure your crops get the attention they deserve. It's loader-agnostic, so it works everywhere—like a universal translator, but for farming.
 * </p>
 * <p>
 * Why does this matter? Because without it, your crops would be lost, alone, and unharvested. And nobody wants that.
 * </p>
 * <p>
 * For the full adventure, see TECHNICAL.md (bring snacks).
 * </p>
 */
public class FrameScanner {
    /**
     * The maximum number of frames we dare scan in a single run. Any more and the crops unionize.
     */
    public static final int MAX_FRAMES_PER_RUN = 24;

    /**
     * The maximum number of blocks to check per run. Because even farmers need a break.
     */
    /**
     * Creates a new FrameScanner. Ready to scan for farming greatness!
     */
    public FrameScanner() {}

    /**
     * Anchor: The holy trinity of farm automation—chest, frame, and hoe.
     * <p>
     * This class bundles together the key objects needed to anchor a farm. Treat it with respect (and maybe a little awe).
     * </p>
     */
    public static class Anchor {
        /** The chest at the heart of the operation. */
        public final Object chest;
        /** The item frame that makes it all possible. */
        public final Object frame;
        /** The hoe, because you can't farm without one. */
        public final Object hoe;

        /**
         * Creates a new Anchor. It's like assembling the Avengers, but for farming.
         * @param chest The chest at the heart of the operation.
         * @param frame The item frame that makes it all possible.
         * @param hoe The hoe, because you can't farm without one.
         */
        public Anchor(Object chest, Object frame, Object hoe) {
            this.chest = chest;
            this.frame = frame;
            this.hoe = hoe;
        }
    }

    /**
     * Loader-specific code must provide world/entity access and call these methods. Don't leave FrameScanner hanging!
     */
}
