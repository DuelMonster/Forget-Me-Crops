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
    public int tickInterval = Config.tickInterval;
    public int frameRediscoveryInterval = Config.frameRediscoveryInterval;
    public int scanRange = Config.scanRange;
    public DurabilityMode durabilityMode = Config.durabilityMode;
    public boolean mendingNegation = Config.mendingNegation;
    public boolean debugLogging = Config.debugLogging;
    public int chestFullCooldownTicks = Config.chestFullCooldownTicks;
    public int maxSpiralDurationTicks = Config.maxSpiralDurationTicks;
    public RotationMode rotationMode = Config.rotationMode;
    public SeedClutterMode seedClutterMode = Config.seedClutterMode;
    public int seedReservePerType = Config.seedReservePerType;
    public boolean harvestParticles = Config.harvestParticles;
}
