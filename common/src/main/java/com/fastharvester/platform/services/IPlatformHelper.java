package com.fastharvester.platform.services;

/**
 * IPlatformHelper: The universal translator for mod platforms!
 * <p>
 * This interface defines what every platform helper must do—like a checklist for being a good citizen in the modding world.
 * </p>
 * <p>
 * Why does this matter? Because every loader has its quirks, and this interface smooths out the bumps so your code can glide across Fabric, Forge, NeoForge, and beyond.
 * </p>
 */
public interface IPlatformHelper {

    /**
     * Gets the name of the current platform. (Fabric? Forge? NeoForge? The suspense!)
     * @return The name of the current platform.
     */
    String getPlatformName();

    /**
     * Checks if a mod with the given id is loaded. Because friends don't let friends run without dependencies.
     * @param modId The mod to check if it is loaded.
     * @return True if the mod is loaded, false otherwise.
     */
    boolean isModLoaded(String modId);

    /**
     * Check if the game is currently in a development environment. (Are we safe to break things?)
     * @return True if in a development environment, false otherwise.
     */
    boolean isDevelopmentEnvironment();

    /**
     * Gets the name of the environment type as a string. ("development" or "production"—choose wisely!)
     * @return The name of the environment type.
     */
    default String getEnvironmentName() {
        return isDevelopmentEnvironment() ? "development" : "production";
    }
}
