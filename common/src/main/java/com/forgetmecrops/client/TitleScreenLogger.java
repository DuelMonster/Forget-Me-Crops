package com.forgetmecrops.client;

import com.forgetmecrops.util.log.LogUtils;

public final class TitleScreenLogger {
    private TitleScreenLogger() {}

    public static void logPlatform() {
        try {
            String pf = "<unknown>";
            try { pf = com.forgetmecrops.platform.Services.PLATFORM.getPlatformName(); } catch (Throwable ignored) {}
            LogUtils.logDebug("Title screen init: platform={}", pf);
        } catch (Throwable ignored) {}
    }
}
