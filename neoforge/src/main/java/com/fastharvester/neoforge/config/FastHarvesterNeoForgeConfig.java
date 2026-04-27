package com.fastharvester.neoforge.config;

// 🛠️ NeoForge config: holds settings and quietly judges your choices.
// Why it matters: your harvest personality lives here.

import com.fastharvester.Config;
import com.fastharvester.enums.DurabilityMode;
import com.fastharvester.enums.RotationMode;
import com.fastharvester.enums.SeedClutterMode;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * FastHarvesterNeoForgeConfig: The config wizard for NeoForge!
 *
 * This class bridges the gap between NeoForge's config system and FastHarvester's loader-agnostic logic.
 * It defines all the magical numbers and toggles that make your farm run smoother than a slime block on ice.
 */
public class FastHarvesterNeoForgeConfig {
    /** Prevent instantiation; static-only config helper. */
    private FastHarvesterNeoForgeConfig() {}
    /** Builder used to assemble the NeoForge config spec. */
    public static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    /** Built NeoForge config spec instance. */
    public static final ModConfigSpec SPEC;

    /** Config spec value for tick interval. */
    public static ModConfigSpec.IntValue TICK_INTERVAL;
    /** Config spec value for frame rediscovery interval. */
    public static ModConfigSpec.IntValue FRAME_REDISCOVERY_INTERVAL;
    /** Config spec value for scan range. */
    public static ModConfigSpec.IntValue SCAN_RANGE_X;
    public static ModConfigSpec.IntValue SCAN_RANGE_Z;
    /** Config spec value for durability mode. */
    public static ModConfigSpec.EnumValue<DurabilityMode> DURABILITY_MODE;
    /** Config spec flag for mending negation. */
    public static ModConfigSpec.BooleanValue MENDING_NEGATION;
    /** Config spec flag for debug logging. */
    public static ModConfigSpec.BooleanValue DEBUG_LOGGING;
    /** Config spec value for chest full cooldown ticks. */
    public static ModConfigSpec.IntValue CHEST_FULL_COOLDOWN_TICKS;
    /** Config spec value for maximum spiral duration ticks. */
    public static ModConfigSpec.IntValue MAX_SPIRAL_DURATION_TICKS;
    /** Config spec value for rotation mode. */
    public static ModConfigSpec.EnumValue<RotationMode> ROTATION_MODE;
    /** Config spec value for seed clutter mode. */
    public static ModConfigSpec.EnumValue<SeedClutterMode> SEED_CLUTTER_MODE;
    /** Config spec value for seed reserve per type. */
    public static ModConfigSpec.IntValue SEED_RESERVE_PER_TYPE;

    static {
        BUILDER.push("FastHarvester");
        TICK_INTERVAL = BUILDER.defineInRange("tickInterval", 300, 1, Integer.MAX_VALUE);
        FRAME_REDISCOVERY_INTERVAL = BUILDER.defineInRange("frameRediscoveryInterval", 100, 1, Integer.MAX_VALUE);
        SCAN_RANGE_X = BUILDER.defineInRange("scanRangeX", 4, 1, 64);
        SCAN_RANGE_Z = BUILDER.defineInRange("scanRangeZ", 4, 1, 64);
        DURABILITY_MODE = BUILDER.defineEnum("durabilityMode", DurabilityMode.NORMAL);
        MENDING_NEGATION = BUILDER.define("mendingNegation", true);
        DEBUG_LOGGING = BUILDER.define("debugLogging", false);
        CHEST_FULL_COOLDOWN_TICKS = BUILDER.defineInRange("chestFullCooldownTicks", 100, 0, Integer.MAX_VALUE);
        MAX_SPIRAL_DURATION_TICKS = BUILDER.defineInRange("maxSpiralDurationTicks", 100, 1, Integer.MAX_VALUE);
        ROTATION_MODE = BUILDER.defineEnum("rotationMode", RotationMode.FOLLOW_HARVEST_SPIRAL);
        SEED_CLUTTER_MODE = BUILDER.defineEnum("seedClutterMode", SeedClutterMode.REDUCED);
        SEED_RESERVE_PER_TYPE = BUILDER.defineInRange("seedReservePerType", 80, 0, 9999);
        BUILDER.pop();
        SPEC = BUILDER.build();
    }
    /**
     * Synchronize values from the NeoForge config spec into the shared {@code Config} class.
     * <p>
     * Called when the mod configuration changes so runtime values reflect the spec.
     * </p>
     */
    public static void update() {
        Config.tickInterval = TICK_INTERVAL.get();
        Config.frameRediscoveryInterval = FRAME_REDISCOVERY_INTERVAL.get();
        Config.scanRangeX = SCAN_RANGE_X.get();
        Config.scanRangeZ = SCAN_RANGE_Z.get();
        Config.durabilityMode = DURABILITY_MODE.get();
        Config.mendingNegation = MENDING_NEGATION.get();
        Config.debugLogging = DEBUG_LOGGING.get();
        Config.chestFullCooldownTicks = CHEST_FULL_COOLDOWN_TICKS.get();
        Config.maxSpiralDurationTicks = MAX_SPIRAL_DURATION_TICKS.get();
        Config.rotationMode = ROTATION_MODE.get();
        Config.seedClutterMode = SEED_CLUTTER_MODE.get();
        Config.seedReservePerType = SEED_RESERVE_PER_TYPE.get();
    }
}
