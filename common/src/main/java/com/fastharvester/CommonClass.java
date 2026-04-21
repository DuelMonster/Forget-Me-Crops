package com.fastharvester;

import com.fastharvester.platform.Services;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Items;

/**
 * CommonClass: The friendly neighborhood example for shared logic!
 * <p>
 * This class lives in the common project, meaning it can be used by all supported loaders. It's like the Switzerland of your codebase—neutral, helpful, and always available.
 * </p>
 * <p>
 * Why does this matter? Because writing code once and using it everywhere is the dream. Loader-specific projects can import and use anything from here, making your life easier and your code DRY (not the boring kind).
 * </p>
 */
public class CommonClass {

    /**
     * Creates a new CommonClass. Not much to do here—this class is all about static methods!
     */
    public CommonClass() {}

    /**
     * Initializes the common logic. Called by loader-specific entrypoints to show off how cool shared code can be.
     * <p>
     * Logs some fun facts about the environment and diamonds, because who doesn't love diamonds?
     */
    public static void init() {
        Constants.LOG.info("Hello from Common init on {}! We are currently in a {} environment!", Services.PLATFORM.getPlatformName(), Services.PLATFORM.getEnvironmentName());
        Constants.LOG.info("The ID for diamonds is {}", BuiltInRegistries.ITEM.getKey(Items.DIAMOND));

        // ServiceLoader magic: lets us call platform-specific code from common code. It's like having a secret agent for every loader.
        if (Services.PLATFORM.isModLoaded("FastHarvester")) {
            Constants.LOG.info("Hello to FastHarvester");
        }
    }
}
