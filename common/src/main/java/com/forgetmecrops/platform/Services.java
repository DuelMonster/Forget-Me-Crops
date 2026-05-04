package com.forgetmecrops.platform;

import com.forgetmecrops.util.log.LogUtils;
import com.forgetmecrops.platform.services.IPlatformHelper;
import java.util.ServiceLoader;

/**
 * Services: The magical switchboard operator for ForgetMeCrops!
 * <p>
 * This class uses Java's ServiceLoader to find the right platform-specific helpers at runtime. It's like a talent scout, but for code.
 * </p>
 * <p>
 * Why does this matter? Because every loader wants to do things their own way, and Services makes sure everyone gets along (most of the time).
 * </p>
 */
public class Services {
    /** Utility class: do not instantiate. */
    private Services() {}

    /**
     * The platform helper: your guide to the current modding universe (Fabric, NeoForge, or whatever comes next).
     * <p>
     * If you want to know what platform you're on, or if another mod is loaded, ask this friendly helper!
     */
    public static final IPlatformHelper PLATFORM = load(IPlatformHelper.class);

    /**
     * Loads a service for the current environment. It's like summoning a genie, but with fewer wishes and more type safety.
     * <p>
     * Your implementation must be registered in META-INF/services, or this will throw a NullPointerException (and some shade).
     * </p>
     * @param clazz The service interface to load.
     * @return The loaded service implementation.
     * @param <T> The type of service.
     */
    public static <T> T load(Class<T> clazz) {
        final T loadedService = ServiceLoader.load(clazz)
                .findFirst()
                .orElseThrow(() -> new NullPointerException("Failed to load service for " + clazz.getName()));
        LogUtils.logDebug("Loaded {} for service {}", loadedService, clazz);
        return loadedService;
    }
}
