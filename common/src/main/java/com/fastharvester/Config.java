package com.fastharvester;

import com.fastharvester.enums.DurabilityMode;
import com.fastharvester.enums.RotationMode;
import com.fastharvester.enums.SeedClutterMode;

/**
 * Config: The magical spellbook of FastHarvester!

package com.fastharvester;

import com.fastharvester.enums.DurabilityMode;
import com.fastharvester.enums.RotationMode;
import com.fastharvester.enums.SeedClutterMode;
// No external config dependencies

/**
 * Config: The magical spellbook of FastHarvester!
 * <p>
 * This class holds all the settings that make your farm go vroom. Loader-specific code fills this out, and then the fun begins.
 * </p>
 * <p>
 * Why does this matter? Because every great farm needs a plan, and this is where you write it down (preferably in pencil, but code will do).
 * </p>
 */
public class Config {
    /** How often (in ticks) should we try to harvest? Lower = faster, higher = lazier. */
    public int tickInterval = 300;

    /** How often (in ticks) should we rediscover frames? Because sometimes frames like to play hide and seek. */
    public int frameRediscoveryInterval = 100;

    /** How far should we scan for crops? The bigger the number, the more ambitious the farmer. */
    public int scanRange = 4;

    /** How tough should our hoes be? Choose your durability mode wisely! */
    public DurabilityMode durabilityMode = DurabilityMode.NORMAL;

    /** Should mending be ignored? True means 'no cheating with mending!' */
    public boolean mendingNegation = true;

    /** Should we print debug logs? True if you like living on the edge (of your log file). */
    public boolean debugLogging = false;

    /** How long to wait (in ticks) when a chest is full. Because patience is a virtue. */
    public int chestFullCooldownTicks = 100;

    /** Maximum spiral duration (in ticks). For those who like their farming with a twist. */
    public int maxSpiralDurationTicks = 100;

    /** How should we rotate? Choose your favorite dance move. */
    public RotationMode rotationMode = RotationMode.FOLLOW_HARVEST_SPIRAL;

    /** How should we handle seed clutter? Options: reduced, off, or extreme chaos. */
    public SeedClutterMode seedClutterMode = SeedClutterMode.REDUCED;

    /** How many seeds should we keep per type? Because running out is embarrassing. */
    public int seedReservePerType = 80;
}
}
