package com.forgetmecrops.config;

import com.forgetmecrops.enums.RotationMode;
import com.forgetmecrops.enums.SeedClutterMode;
import com.forgetmecrops.enums.DurabilityMode;

/**
 * ConfigDefaults: default values and minimums used by both config screens and saved config.
 */
public final class ConfigDefaults {
    private ConfigDefaults() {}

    public static final int TICK_INTERVAL_DEFAULT = 300;
    public static final int TICK_INTERVAL_MIN = 1;

    public static final int SCAN_RANGE_DEFAULT = 4;
    public static final int SCAN_RANGE_MIN = 1;

    public static final int SCAN_RANGE_X_DEFAULT = 4;
    public static final int SCAN_RANGE_X_MIN = 1;

    public static final int SCAN_RANGE_Z_DEFAULT = 4;
    public static final int SCAN_RANGE_Z_MIN = 1;

    public static final DurabilityMode DURABILITY_MODE_DEFAULT = DurabilityMode.NORMAL;

    public static final int CHEST_FULL_COOLDOWN_DEFAULT = 300;
    public static final int CHEST_FULL_COOLDOWN_MIN = 0;

    public static final int MAX_SPIRAL_DEFAULT = 200;
    public static final int MAX_SPIRAL_MIN = 1;

    public static final RotationMode ROTATION_MODE_DEFAULT = RotationMode.FULL_ROTATION;

    public static final SeedClutterMode SEED_CLUTTER_DEFAULT = SeedClutterMode.REDUCED;

    public static final int SEED_RESERVE_DEFAULT = 80;
    public static final int SEED_RESERVE_MIN = 0;
}
