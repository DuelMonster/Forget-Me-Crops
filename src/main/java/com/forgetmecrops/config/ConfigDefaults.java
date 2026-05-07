package com.forgetmecrops.config;

import com.forgetmecrops.enums.RotationMode;
import com.forgetmecrops.enums.SeedClutterMode;
import com.forgetmecrops.enums.DurabilityMode;

/**
 * ConfigDefaults: The sensible baseline so players don't accidentally break
 * everything on day one!
 * <p>
 * This class holds all the default values and minimum clamps for every single
 * config option.
 * Think of it as the "factory settings" button — safe, sane, and ready to farm
 * immediately without any intervention from the player whatsoever.
 * </p>
 * <p>
 * Why a separate class? Because scattering magic numbers across the codebase is
 * how you end up changing "300" in seventeen different places and missing one.
 * We've been there. It wasn't fun.
 * </p>
 */
public final class ConfigDefaults {
    // Utility class. No instances. Go away.
    private ConfigDefaults() {
    }

    // -----------------------------------------------------------------------------------------
    // Tick & Timing
    // -----------------------------------------------------------------------------------------

    /**
     * How many ticks between farm scans. 300 ticks ≈ 15 seconds — frequent enough
     * to feel alive, slow enough not to reduce the server to a smoking crater.
     */
    public static final int TICK_INTERVAL_DEFAULT = 300;

    /**
     * Minimum allowed tick interval. 1 tick is technically valid, though your
     * server's TPS may have some strongly worded opinions about that choice.
     */
    public static final int TICK_INTERVAL_MIN = 1;

    /**
     * Maximum allowed tick interval. 1200 ticks = 60 seconds — if you're scanning
     * less often than once a minute, you're less automating and more delegating to future-you.
     */
    public static final int TICK_INTERVAL_MAX = 1200;

    /**
     * How often to rescan loaded chunks for frame/chest anchors. 300 ticks keeps
     * registry state fresh without turning discovery into a full-time job.
     */
    public static final int FRAME_REDISCOVERY_INTERVAL_DEFAULT = 300;

    /**
     * Minimum rediscovery interval. 15 ticks (0.75s) is fast enough for responsive
     * anchor refresh without hammering chunk scans every tick.
     */
    public static final int FRAME_REDISCOVERY_INTERVAL_MIN = 15;

    /**
     * Maximum rediscovery interval. 1200 ticks (60s) prevents stale anchors from
     * lingering too long while still allowing low-overhead periodic refreshes.
     */
    public static final int FRAME_REDISCOVERY_INTERVAL_MAX = 1200;

    // -----------------------------------------------------------------------------------------
    // Scan Range
    // -----------------------------------------------------------------------------------------

    /**
     * Default scan radius in blocks (square). 4 blocks is "cozy farm" territory —
     * enough to cover a modest wheat field without scanning half the dimension.
     */
    public static final int SCAN_RANGE_DEFAULT = 4;

    /**
     * Minimum scan range. You need at least 1 block to find literally anything.
     * Zero would scan just the anchor itself, which is philosophically valid but
     * agriculturally useless.
     */
    public static final int SCAN_RANGE_MIN = 1;

    /**
     * Default east-west scan range. Keeps things square by default — most farms are
     * rectangles, not rhombuses. Unless you're feeling adventurous.
     */
    public static final int SCAN_RANGE_X_DEFAULT = 4;

    /**
     * Minimum east-west scan range. 0 would scan nothing, which is valid if you
     * enjoy doing all the harvesting yourself like some kind of caveperson.
     */
    public static final int SCAN_RANGE_X_MIN = 1;

    /**
     * Maximum east-west scan range. 16 blocks covers a full chunk width — beyond
     * that you're farming a biome, not a farm.
     */
    public static final int SCAN_RANGE_X_MAX = 16;

    /**
     * Default north-south scan range. Matches X by default because asymmetric farms
     * are for the truly bold and geometrically adventurous.
     */
    public static final int SCAN_RANGE_Z_DEFAULT = 4;

    /**
     * Minimum north-south scan range. Same reasoning as X_MIN. Even a 1-block farm
     * is still a farm, technically. We don't judge.
     */
    public static final int SCAN_RANGE_Z_MIN = 1;

    /**
     * Maximum north-south scan range. Mirrors X_MAX — 16 blocks, one full chunk
     * width.
     */
    public static final int SCAN_RANGE_Z_MAX = 16;

    // -----------------------------------------------------------------------------------------
    // Durability
    // -----------------------------------------------------------------------------------------

    /**
     * Default durability mode. NORMAL means real wear and tear — farming isn't
     * free, after all. Tools should earn their retirement.
     */
    public static final DurabilityMode DURABILITY_MODE_DEFAULT = DurabilityMode.NORMAL;

    /**
     * Default mending behavior. When true, hoes with Mending are protected from
     * durability loss caused by this mod's actions.
     */
    public static final boolean MENDING_PROTECTION_DEFAULT = true;

    /**
     * Default debug logging state. False keeps logs quiet during normal play.
     */
    public static final boolean DEBUG_LOGGING_DEFAULT = false;

    // -----------------------------------------------------------------------------------------
    // Chest Cooldown
    // -----------------------------------------------------------------------------------------

    /**
     * Default cooldown (ticks) before retrying a full chest. 300 ticks gives the
     * player time to open the chest, stare in panic, and figure out where to put
     * 64 more wheat.
     */
    public static final int CHEST_FULL_COOLDOWN_DEFAULT = 300;

    /**
     * Minimum chest cooldown. 10 means "retry every 0.5 seconds" — great for
     * automated setups, absolutely brutal if you forgot to empty the chest.
     */
    public static final int CHEST_FULL_COOLDOWN_MIN = 10;

    /**
     * Maximum chest cooldown. 300 ticks — same as the default, because waiting longer
     * than 15 seconds to retry just means your crops are piling up on the ground.
     */
    public static final int CHEST_FULL_COOLDOWN_MAX = 300;

    // -----------------------------------------------------------------------------------------
    // Spiral Duration
    // -----------------------------------------------------------------------------------------

    /**
     * Maximum spiral scan steps per pass. 200 is a generous budget — if you haven't
     * found crops by then, you should probably verify your farm actually exists.
     */
    public static final int MAX_SPIRAL_DEFAULT = 200;

    /**
     * Minimum spiral scan budget. 10 steps is the smallest useful spiral — any less
     * and you're basically just poking around randomly.
     */
    public static final int MAX_SPIRAL_MIN = 10;

    /**
     * Maximum spiral scan budget. 400 steps covers a very large farm — past this,
     * the spiral is more of an expedition and should probably be broken into chunks.
     */
    public static final int MAX_SPIRAL_MAX = 400;

    // -----------------------------------------------------------------------------------------
    // Rotation
    // -----------------------------------------------------------------------------------------

    /**
     * Default rotation mode. FULL_ROTATION = a satisfying full 0-to-7 spin per
     * harvest. Maximum drama. Highly recommended for screenshots.
     */
    public static final RotationMode ROTATION_MODE_DEFAULT = RotationMode.FULL_ROTATION;

    /**
     * Default client visual setting. True enables harvest particle feedback.
     */
    public static final boolean HARVEST_PARTICLES_DEFAULT = true;

    // -----------------------------------------------------------------------------------------
    // Seed Clutter
    // -----------------------------------------------------------------------------------------

    /**
     * Default seed clutter policy. REDUCED keeps chests tidy — because a seed-choked
     * chest is a silent cry for help from someone who never learned to compost.
     */
    public static final SeedClutterMode SEED_CLUTTER_DEFAULT = SeedClutterMode.REDUCED;

    /**
     * Default seeds to keep per crop type before discarding extras. 80 is enough to
     * replant an empire and still have leftovers for a rainy day.
     */
    public static final int SEED_RESERVE_DEFAULT = 80;

    /**
     * Minimum seed reserve. 0 means "toss every last seed" — valid if you enjoy the
     * thrill of replanting everything by hand, I suppose.
     */
    public static final int SEED_RESERVE_MIN = 0;

    /**
     * Maximum seed reserve. 1,152 seeds — exactly 18 stacks, which is enough to
     * replant the entire overworld and still have change.
     */
    public static final int SEED_RESERVE_MAX = 1152;
}
