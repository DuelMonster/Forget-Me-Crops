package com.fastharvester.crops;

/**
 * CropRouter: The GPS for your crops!
 * <p>
 * This class will someday decide which crop goes where, and when. Loader-agnostic, so every plant gets a fair route.
 * </p>
 * <p>
 * Why does this matter? Because even crops need a sense of direction.
 * </p>
 */
public class CropRouter {
    /**
     * Routes a crop to the correct handler. Emits verbose debug logs.
     * @param cropType The type of crop (String or enum, loader-agnostic).
     * @param cropPos The position of the crop.
     * @param world The world object.
     * @return true if routed and handled, false otherwise.
     */
    public static boolean routeCrop(Object cropType, Object cropPos, Object world) {
        com.fastharvester.Constants.LOG.info("[FastHarvester][ROUTER] Routing crop type '{}' at {}...", cropType, cropPos);
        if ("nether_wart".equals(cropType)) {
            return NetherWartHandler.handle(cropPos, world);
        } else if ("sweet_berry".equals(cropType)) {
            return SweetBerryHandler.handle(cropPos, world);
        } else {
            com.fastharvester.Constants.LOG.info("[FastHarvester][ROUTER] No handler for crop type '{}'. Skipping.", cropType);
            return false;
        }
    }
}
