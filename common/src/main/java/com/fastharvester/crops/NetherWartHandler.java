package com.fastharvester.crops;

// 🧪 NetherWartHandler: specialized for gloomy soul-sand gardens. Quiet, serious, and surprisingly tender.
// Emotional aside: it knows Nether plants need special care and a comforting pat.

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
    /** Utility class: do not instantiate. */
    private NetherWartHandler() {}

    /**
     * Handles Nether Wart harvesting with verbose debug logging.
     * @param cropPos the position of the Nether Wart crop
     * @param world the world/context object where the crop resides
     * @return true if the handler performed a successful harvest simulation
     */
    public static boolean handle(Object cropPos, Object world) {
        com.fastharvester.Constants.logInfo("[NETHER_WART] Handling Nether Wart at {}...", cropPos);
        // Simulate harvest
        boolean success = Math.random() > 0.1;
        com.fastharvester.Constants.logDebug("[NETHER_WART] Harvest {} at {}.", success ? "SUCCESS" : "FAILURE", cropPos);
        return success;
    }
}
