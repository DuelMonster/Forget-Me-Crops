package com.fastharvester.neoforge;

import com.fastharvester.FastHarvester;
import net.neoforged.fml.common.Mod;

/**
 * NeoForge mod initializer. Wires up FastHarvester for NeoForge.
 */
@Mod("fastharvester")
public class FastHarvesterNeoForge {
    public FastHarvesterNeoForge() {
        // Loader-specific config loading would go here
        FastHarvester.init();
    }
}
