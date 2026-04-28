package com.fastharvester;

// 😊 CommonClass: a small, stoic helper. It doesn't gossip much, but it quietly helps other classes behave.
// Why it matters: tidy glue keeps the codebase from tripping over itself.

import com.fastharvester.platform.Services;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Items;
import com.fastharvester.util.log.LogUtils;

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

    /** Utility class: do not instantiate. */
    private CommonClass() {}

    /**
     * Initializes the common logic. Called by loader-specific entrypoints to show off how cool shared code can be.
     * <p>
     * Logs some fun facts about the environment and diamonds, because who doesn't love diamonds?
     */
    public static void init() {
        LogUtils.logDebug("Hello from Common init on {}! We are currently in a {} environment!", Services.PLATFORM.getPlatformName(), Services.PLATFORM.getEnvironmentName());
        LogUtils.logDebug("The ID for diamonds is {}", BuiltInRegistries.ITEM.getKey(Items.DIAMOND));

        // ServiceLoader magic: lets us call platform-specific code from common code. It's like having a secret agent for every loader.
        if (Services.PLATFORM.isModLoaded("FastHarvester")) {
            LogUtils.logDebug("Hello to FastHarvester");
        }
    }
}
