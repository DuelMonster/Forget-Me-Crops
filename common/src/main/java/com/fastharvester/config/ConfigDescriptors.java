package com.fastharvester.config;

import com.fastharvester.enums.RotationMode;
import com.fastharvester.enums.SeedClutterMode;
import com.fastharvester.enums.DurabilityMode;

/**
 * ConfigDescriptors: shared labels, tooltips, and defaults for config screens.
 */
public final class ConfigDescriptors {
    private ConfigDescriptors() {}

    public static final String TITLE = "FastHarvester";

    public static final String CATEGORY_SERVER = "Server Settings";
    public static final String CATEGORY_CLIENT = "Client Settings";

    public static final String TICK_INTERVAL_LABEL = "Tick Interval";
    public static final int TICK_INTERVAL_DEFAULT = 300;
    public static final int TICK_INTERVAL_MIN = 1;
    public static final String TICK_INTERVAL_TOOLTIP = "Ticks between automatic harvest attempts";

    public static final String SCAN_RANGE_LABEL = "Scan Range";
    public static final int SCAN_RANGE_DEFAULT = 4;
    public static final int SCAN_RANGE_MIN = 1;
    public static final String SCAN_RANGE_TOOLTIP = "Radius (blocks) to search for crops around each frame";

    public static final String SCAN_RANGE_X_LABEL = "Scan Range (X)";
    public static final int SCAN_RANGE_X_DEFAULT = 4;
    public static final int SCAN_RANGE_X_MIN = 1;
    public static final String SCAN_RANGE_X_TOOLTIP = "Horizontal X radius (blocks) to search for crops around each frame";

    public static final String SCAN_RANGE_Z_LABEL = "Scan Range (Z)";
    public static final int SCAN_RANGE_Z_DEFAULT = 4;
    public static final int SCAN_RANGE_Z_MIN = 1;
    public static final String SCAN_RANGE_Z_TOOLTIP = "Horizontal Z radius (blocks) to search for crops around each frame";

    public static final String DURABILITY_MODE_LABEL = "Durability Mode";
    public static final DurabilityMode DURABILITY_MODE_DEFAULT = DurabilityMode.NORMAL;
    public static final String DURABILITY_MODE_TOOLTIP = "How item durability is handled when harvesting";

    public static final String MENDING_NEGATION_LABEL = "Mending Negation";
    public static final String MENDING_NEGATION_TOOLTIP = "Prevent mending from stopping the harvester's tool use";

    public static final String DEBUG_LOGGING_LABEL = "Debug Logging";
    public static final String DEBUG_LOGGING_TOOLTIP = "Enable verbose debug logging for troubleshooting";

    public static final String CHEST_FULL_COOLDOWN_LABEL = "Chest Full Cooldown";
    public static final int CHEST_FULL_COOLDOWN_DEFAULT = 100;
    public static final int CHEST_FULL_COOLDOWN_MIN = 0;
    public static final String CHEST_FULL_COOLDOWN_TOOLTIP = "Ticks to wait before retrying a full chest";

    public static final String MAX_SPIRAL_DURATION_LABEL = "Max Spiral Duration";
    public static final int MAX_SPIRAL_DEFAULT = 100;
    public static final int MAX_SPIRAL_MIN = 1;
    public static final String MAX_SPIRAL_TOOLTIP = "Maximum ticks allowed for a harvesting spiral search";

    public static final String ROTATION_MODE_LABEL = "Rotation Mode";
    public static final RotationMode ROTATION_MODE_DEFAULT = RotationMode.FOLLOW_HARVEST_SPIRAL;
    public static final String ROTATION_MODE_TOOLTIP = "How frames rotate when harvesting";

    public static final String SEED_CLUTTER_LABEL = "Seed Clutter Mode";
    public static final SeedClutterMode SEED_CLUTTER_DEFAULT = SeedClutterMode.REDUCED;
    public static final String SEED_CLUTTER_TOOLTIP = "Behaviour for leaving extra seeds after planting";

    public static final String SEED_RESERVE_LABEL = "Seed Reserve Per Type";
    public static final int SEED_RESERVE_DEFAULT = 80;
    public static final int SEED_RESERVE_MIN = 0;
    public static final String SEED_RESERVE_TOOLTIP = "Number of seeds to keep in reserve per crop type";

    public static final String HARVEST_PARTICLES_LABEL = "Harvest Particles";
    public static final String HARVEST_PARTICLES_TOOLTIP = "Show particle effects when harvesting";
}
