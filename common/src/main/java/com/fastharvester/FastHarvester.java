package com.fastharvester;

/**
 * Main entry for common logic. Called by loader-specific entrypoints.
 */
public class FastHarvester {
    public static Config CONFIG = new Config();
    public static void init() {
        // Called by loader-specific entrypoints
        // Loader should populate CONFIG before calling this
    }
}
