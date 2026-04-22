/**
 * FastHarvesterNeoForgeConfig: The config wizard for NeoForge!
 * <p>
 * This class bridges the gap between NeoForge's config system and FastHarvester's loader-agnostic logic.
 * It defines all the magical numbers and toggles that make your farm run smoother than a slime block on ice.
 * </p>
 * <p>
 * Why does this matter? Because every great farm needs a great config—and every config needs a little love (and maybe a few jokes).
 * </p>
 * <p>
 * Loader: NeoForge. Mood: Efficient. Attitude: "Let me handle the details, you handle the crops!"
 * </p>
 */
package com.fastharvester.neoforge;

// 🛠️ NeoForge config: holds settings and quietly judges your choices.
// Why it matters: your harvest personality lives here.

import com.fastharvester.Config;
import com.fastharvester.enums.DurabilityMode;
import com.fastharvester.enums.RotationMode;
import com.fastharvester.enums.SeedClutterMode;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

public class FastHarvesterNeoForgeConfig {
    public static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec SPEC;

    public static ModConfigSpec.IntValue TICK_INTERVAL;
    public static ModConfigSpec.IntValue FRAME_REDISCOVERY_INTERVAL;
    public static ModConfigSpec.IntValue SCAN_RANGE;
    public static ModConfigSpec.EnumValue<DurabilityMode> DURABILITY_MODE;
    public static ModConfigSpec.BooleanValue MENDING_NEGATION;
    public static ModConfigSpec.BooleanValue DEBUG_LOGGING;
    public static ModConfigSpec.IntValue CHEST_FULL_COOLDOWN_TICKS;
    public static ModConfigSpec.IntValue MAX_SPIRAL_DURATION_TICKS;
    public static ModConfigSpec.EnumValue<RotationMode> ROTATION_MODE;
    public static ModConfigSpec.EnumValue<SeedClutterMode> SEED_CLUTTER_MODE;
    public static ModConfigSpec.IntValue SEED_RESERVE_PER_TYPE;

    static {
        BUILDER.push("FastHarvester");
        TICK_INTERVAL = BUILDER.defineInRange("tickInterval", 300, 1, Integer.MAX_VALUE);
        FRAME_REDISCOVERY_INTERVAL = BUILDER.defineInRange("frameRediscoveryInterval", 100, 1, Integer.MAX_VALUE);
        SCAN_RANGE = BUILDER.defineInRange("scanRange", 4, 1, 64);
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

    public static void update() {
        Config.tickInterval = TICK_INTERVAL.get();
        Config.frameRediscoveryInterval = FRAME_REDISCOVERY_INTERVAL.get();
        Config.scanRange = SCAN_RANGE.get();
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
