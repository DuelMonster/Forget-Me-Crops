package com.fastharvester.crops;

/**
 * NetherWartHandler: The spicy specialist for Nether Wart!
 * <p>
 * This class will handle all things Nether Wart, no matter the loader. It's like a chef, but for fungus.
 * </p>
 * <p>
 * Why does this matter? Because Nether Wart deserves love too (and maybe a little respect).
 * </p>
 */
public class NetherWartHandler {
    public NetherWartHandler() {}
    /**
     * Handles Nether Wart harvesting with verbose debug logging.
     */
    public static boolean handle(Object cropPos, Object world) {
        com.fastharvester.Constants.LOG.info("[FastHarvester][NETHER_WART] Handling Nether Wart at {}...", cropPos);
        // Simulate harvest
        boolean success = Math.random() > 0.1;
        com.fastharvester.Constants.LOG.debug("[FastHarvester][NETHER_WART] Harvest {} at {}.", success ? "SUCCESS" : "FAILURE", cropPos);
        return success;
    }
}
