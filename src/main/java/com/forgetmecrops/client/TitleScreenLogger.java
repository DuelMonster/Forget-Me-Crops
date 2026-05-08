package com.forgetmecrops.client;

import com.forgetmecrops.util.log.LogUtils;
import com.forgetmecrops.util.ExceptionHandler;

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
        ExceptionHandler.silentTry(() -> {
            // Start with a humble "I dunno" placeholder in case the platform helper is feeling shy
            String pf = ExceptionHandler.silentTry(
                () -> com.forgetmecrops.platform.Services.PLATFORM.getPlatformName(),
                "<unknown>"
            );
            // Emit at DEBUG level — quiet enough not to alarm production logs
            LogUtils.logDebug("Title screen init: platform={}", pf);
        });
    }
}
