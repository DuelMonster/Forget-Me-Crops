package com.forgetmecrops.config;

import com.forgetmecrops.enums.RotationMode;
import com.forgetmecrops.enums.SeedClutterMode;
import com.forgetmecrops.enums.DurabilityMode;

/**
 * ConfigDefaults: The sensible baseline so players don't accidentally break everything on day one!
 * <p>
 * This class holds all the default values and minimum clamps for every single config option.
 * Think of it as the "factory settings" button — safe, sane, and ready to farm immediately
 * without any intervention from the player whatsoever.
 * </p>
 * <p>
 * Why a separate class? Because scattering magic numbers across the codebase is how you end up
 * changing "300" in seventeen different places and missing one. We've been there. It wasn't fun.
 * </p>
 */
public final class ConfigDefaults {
    // Utility class. No instances. Go away.
    private ConfigDefaults() {}

    // -----------------------------------------------------------------------------------------
    // Tick & Timing
    // -----------------------------------------------------------------------------------------

    /** How many ticks between farm scans. 300 ticks ≈ 15 seconds — frequent enough to feel alive,
     *  slow enough not to reduce the server to a smoking crater. */
    public static final int TICK_INTERVAL_DEFAULT = 300;

    /** Minimum allowed tick interval. 1 tick is technically valid, though your server's TPS may
     *  have some strongly worded opinions about that choice. */
    public static final int TICK_INTERVAL_MIN = 1;

    // -----------------------------------------------------------------------------------------
    // Scan Range
    // -----------------------------------------------------------------------------------------

    /** Default scan radius in blocks (square). 4 blocks is "cozy farm" territory — enough to
     *  cover a modest wheat field without scanning half the dimension. */
    public static final int SCAN_RANGE_DEFAULT = 4;

    /** Minimum scan range. You need at least 1 block to find literally anything. Zero would scan
     *  just the anchor itself, which is philosophically valid but agriculturally useless. */
    public static final int SCAN_RANGE_MIN = 1;

    /** Default east-west scan range. Keeps things square by default — most farms are rectangles,
     *  not rhombuses. Unless you're feeling adventurous. */
    public static final int SCAN_RANGE_X_DEFAULT = 4;

    /** Minimum east-west scan range. 0 would scan nothing, which is valid if you enjoy doing
     *  all the harvesting yourself like some kind of caveperson. */
    public static final int SCAN_RANGE_X_MIN = 1;

    /** Default north-south scan range. Matches X by default because asymmetric farms are for
     *  the truly bold and geometrically adventurous. */
    public static final int SCAN_RANGE_Z_DEFAULT = 4;

    /** Minimum north-south scan range. Same reasoning as X_MIN. Even a 1-block farm is still a
     *  farm, technically. We don't judge. */
    public static final int SCAN_RANGE_Z_MIN = 1;

    // -----------------------------------------------------------------------------------------
    // Durability
    // -----------------------------------------------------------------------------------------

    /** Default durability mode. NORMAL means real wear and tear — farming isn't free, after all.
     *  Tools should earn their retirement. */
    public static final DurabilityMode DURABILITY_MODE_DEFAULT = DurabilityMode.NORMAL;

    // -----------------------------------------------------------------------------------------
    // Chest Cooldown
    // -----------------------------------------------------------------------------------------

    /** Default cooldown (ticks) before retrying a full chest. 300 ticks gives the player time to
     *  open the chest, stare in panic, and figure out where to put 64 more wheat. */
    public static final int CHEST_FULL_COOLDOWN_DEFAULT = 300;

    /** Minimum chest cooldown. 0 means "retry immediately every tick" — great for automated
     *  setups, absolutely brutal if you forgot to empty the chest. */
    public static final int CHEST_FULL_COOLDOWN_MIN = 0;

    // -----------------------------------------------------------------------------------------
    // Spiral Duration
    // -----------------------------------------------------------------------------------------

    /** Maximum spiral scan steps per pass. 200 is a generous budget — if you haven't found
     *  crops by then, you should probably verify your farm actually exists. */
    public static final int MAX_SPIRAL_DEFAULT = 200;

    /** Minimum spiral scan budget. Can't spiral with less than 1 step. Basic geometry demands it.
     *  The spiral must spiral, or it is just a dot. */
    public static final int MAX_SPIRAL_MIN = 1;

    // -----------------------------------------------------------------------------------------
    // Rotation
    // -----------------------------------------------------------------------------------------

    /** Default rotation mode. FULL_ROTATION = a satisfying full 0-to-7 spin per harvest.
     *  Maximum drama. Highly recommended for screenshots. */
    public static final RotationMode ROTATION_MODE_DEFAULT = RotationMode.FULL_ROTATION;

    // -----------------------------------------------------------------------------------------
    // Seed Clutter
    // -----------------------------------------------------------------------------------------

    /** Default seed clutter policy. REDUCED keeps chests tidy — because a seed-choked chest is
     *  a silent cry for help from someone who never learned to compost. */
    public static final SeedClutterMode SEED_CLUTTER_DEFAULT = SeedClutterMode.REDUCED;

    /** Default seeds to keep per crop type before discarding extras. 80 is enough to replant an
     *  empire and still have leftovers for a rainy day. */
    public static final int SEED_RESERVE_DEFAULT = 80;

    /** Minimum seed reserve. 0 means "toss every last seed" — valid if you enjoy the thrill of
     *  replanting everything by hand, I suppose. */
    public static final int SEED_RESERVE_MIN = 0;
}
