package com.forgetmecrops.neoforge.mixin;

// 😄 Tiny mixin with big dreams: sprinkles mod info onto the title screen when it feels social.
// Why it matters: first impressions count. Make them charming.

import com.forgetmecrops.util.log.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * MixinTitleScreen (NeoForge): The party crasher who logs the platform at title screen init!
 * <p>
 * Injects a tiny log call at the beginning of TitleScreen.init() to confirm the mod
 * is alive and well when the player sees the main menu. Same behavior as the Fabric version,
 * just using NeoForge's mixin infrastructure instead. Small but satisfying.
 * </p>
 */
@Mixin(TitleScreen.class)
public class MixinTitleScreen {
    /**
     * Fires at the start of TitleScreen.init() — the mod's chance to wave hello from the title screen.
     * Logs the platform name and Minecraft version type for diagnostic purposes.
     */
    @Inject(at = @At("HEAD"), method = "init()V")
    private void init(CallbackInfo info) {
        com.forgetmecrops.client.TitleScreenLogger.logPlatform();
        LogUtils.logDebug("MC Version: {}", Minecraft.getInstance().getVersionType());
    }
}
