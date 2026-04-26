package com.fastharvester;

import com.fastharvester.Constants;

/**
 * TitleScreenLogger: small common helper to centralize title-screen logging.
 */
public final class TitleScreenLogger {
    private TitleScreenLogger() {}

    public static void logPlatform() {
        try {
            String pf = "<unknown>";
            try { pf = com.fastharvester.platform.Services.PLATFORM.getPlatformName(); } catch (Throwable ignored) {}
            Constants.logDebug("Title screen init: platform={}", pf);
        } catch (Throwable ignored) {}
    }
}
