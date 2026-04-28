package com.fastharvester.client;

import com.fastharvester.util.log.LogUtils;

public final class TitleScreenLogger {
    private TitleScreenLogger() {}

    public static void logPlatform() {
        try {
            String pf = "<unknown>";
            try { pf = com.fastharvester.platform.Services.PLATFORM.getPlatformName(); } catch (Throwable ignored) {}
            LogUtils.logDebug("Title screen init: platform={}", pf);
        } catch (Throwable ignored) {}
    }
}
