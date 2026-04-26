package com.fastharvester.neoforge;

import com.fastharvester.Config;
import com.fastharvester.enums.DurabilityMode;
import com.fastharvester.enums.RotationMode;
import com.fastharvester.enums.SeedClutterMode;
import me.shedaniel.autoconfig.ConfigData;

/**
 * Autoconfig data class used by ClothConfig/AutoConfig integration on NeoForge.
 * This mirrors the runtime values in `Config` so the ClothConfig screen can
 * be presented via the native mods list.
 */
@me.shedaniel.autoconfig.annotation.Config(name = "fastharvester")
public class FastHarvesterAutoConfig implements ConfigData {
    /** Public no-arg constructor for AutoConfig. */
    public FastHarvesterAutoConfig() {}
    /** Ticks between automatic harvest attempts. */
    public int tickInterval = Config.tickInterval;
    /** Ticks between automatic rediscovery passes for loaded frames. */
    public int frameRediscoveryInterval = Config.frameRediscoveryInterval;
    /** Radius (blocks) to search for crops around each frame. */
    public int scanRange = Config.scanRange;
    /** How durability is handled for tools used by the harvester. */
    public DurabilityMode durabilityMode = Config.durabilityMode;
    /** Whether mending negation is enabled (ignore mending behavior). */
    public boolean mendingNegation = Config.mendingNegation;
    /** Enable verbose debug logging. */
    public boolean debugLogging = Config.debugLogging;
    /** Cooldown ticks before retrying a full chest. */
    public int chestFullCooldownTicks = Config.chestFullCooldownTicks;
    /** Maximum ticks allowed for a harvesting spiral search. */
    public int maxSpiralDurationTicks = Config.maxSpiralDurationTicks;
    /** Rotation mode for item frames when harvesting. */
    public RotationMode rotationMode = Config.rotationMode;
    /** Behaviour for leaving extra seeds after planting. */
    public SeedClutterMode seedClutterMode = Config.seedClutterMode;
    /** Number of seeds to keep in reserve per crop type. */
    public int seedReservePerType = Config.seedReservePerType;
    /** Enable visual harvest particles on the client. */
    public boolean harvestParticles = Config.harvestParticles;
}
