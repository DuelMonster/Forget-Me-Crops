package com.forgetmecrops.client;

import com.forgetmecrops.util.log.LogUtils;

/**
 * TitleScreenLogger: The mod's version of announcing itself at a party!
 * <p>
 * Sneaks a tiny platform identification log into the title screen init so you always know
 * where you're running. Basically the mod waving "I'M HERE!" into the void of game startup.
 * </p>
 * <p>
 * Why does this matter? Because if something goes wrong before the world even loads, you'll want
 * to know which loader is currently at fault. Spoiler: it's usually the one you least expected.
 * </p>
 */
public final class TitleScreenLogger {
    // Static utility class. No instances. If you try to make one, the farm gods will judge you.
    private TitleScreenLogger() {}

    /**
     * Announces the current platform to the debug log like a tiny herald at the title screen gate.
     * <p>
     * Grabs the platform name from the Services layer (Fabric? NeoForge? Something exotic?), then
     * whispers it into the debug log. Swallows all exceptions because the title screen is NOT the
     * place to throw a dramatic stack trace tantrum.
     * </p>
     */
    public static void logPlatform() {
        try {
            // Start with a humble "I dunno" placeholder in case the platform helper is feeling shy
            String pf = "<unknown>";
            try {
                // Ask the Services layer: "hey, what loader are we running under right now?"
                pf = com.forgetmecrops.platform.Services.PLATFORM.getPlatformName();
            } catch (Throwable ignored) {
                // Platform helper threw a tantrum. We'll shrug and carry on with our life.
            }
            // Emit at DEBUG level — quiet enough not to alarm production logs
            LogUtils.logDebug("Title screen init: platform={}", pf);
        } catch (Throwable ignored) {
            // Something went cosmically wrong. We refuse to let title screen chaos ruin anyone's day.
        }
    }
}
