package com.fastharvester.crops;

// 🍓 SweetBerryHandler: handles prickly decisions with warm resolve. Berries are loved here.
// Why it matters: different crops have different social needs.

/**
 * SweetBerryHandler: The berry best at what it does!
 * <p>
 * This class will handle all things Sweet Berry, no matter the loader. It's loader-agnostic, so every berry gets a chance to shine.
 * </p>
 * <p>
 * Why does this matter? Because life is sweeter with berries (and good code).
 * </p>
 */
public class SweetBerryHandler {
    /** Utility class: do not instantiate. */
    private SweetBerryHandler() {}

    /**
     * Handles Sweet Berry harvesting with verbose debug logging.
     * @param cropPos the position of the Sweet Berry crop
     * @param world the world/context object where the crop resides
     * @return true if the handler performed a successful harvest simulation
     */
    public static boolean handle(Object cropPos, Object world) {
        com.fastharvester.Constants.logInfo("[SWEET_BERRY] Handling Sweet Berry at {}...", cropPos);
        // Simulate harvest
        boolean success = Math.random() > 0.1;
        com.fastharvester.Constants.logDebug("[SWEET_BERRY] Harvest {} at {}.", success ? "SUCCESS" : "FAILURE", cropPos);
        return success;
    }
}
