package com.fastharvester;

import com.fastharvester.enums.DurabilityMode;
import com.fastharvester.enums.RotationMode;
import com.fastharvester.enums.SeedClutterMode;

/**
 * Loader-agnostic config holder for FastHarvester.
 * Populated by loader-specific config adapters.
 */
public class Config {
    public int tickInterval = 300;
    public int frameRediscoveryInterval = 100;
    public int scanRange = 4;
    public DurabilityMode durabilityMode = DurabilityMode.NORMAL;
    public boolean mendingNegation = true;
    public boolean debugLogging = false;
    public int chestFullCooldownTicks = 100;
    public int maxSpiralDurationTicks = 100;
    public RotationMode rotationMode = RotationMode.FOLLOW_HARVEST_SPIRAL;
    public SeedClutterMode seedClutterMode = SeedClutterMode.REDUCED;
    public int seedReservePerType = 80;
    public boolean harvestParticles = true;
}
