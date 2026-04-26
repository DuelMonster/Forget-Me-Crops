package com.fastharvester.mixin;

// ✨ MixinMinecraft: sneaks tiny integrations into Minecraft classes. Quietly mischievous.
// Why it matters: sometimes you need a gentle nudge into game behavior.

import com.fastharvester.Constants;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * MixinMinecraft: The backstage pass to Minecraft's main class!
 * <p>
 * This mixin sneaks in at the end of Minecraft's constructor to let you know FastHarvester is here and ready to party.
 * </p>
 * <p>
 * Why does this matter? Because sometimes you just want to say hi (and check the version).
 * </p>
 */
@Mixin(Minecraft.class)
public class MixinMinecraft {
    /**
     * Creates a new MixinMinecraft. For now, it's just a mixin with big dreams!
     */
    public MixinMinecraft() {}
    /**
     * Injects a log message at the end of Minecraft's constructor. Because every mod wants to make an entrance.
     */
    @Inject(at = @At("TAIL"), method = "<init>")
    private void init(CallbackInfo info) {
        Constants.logDebug("This line is printed by an example mod common mixin!");
        Constants.logDebug("MC Version: {}", Minecraft.getInstance().getVersionType());
    }
}
