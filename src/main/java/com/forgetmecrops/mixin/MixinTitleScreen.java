package com.forgetmecrops.mixin;

// 🎨 MixinTitleScreen: the tiny spy that waves hello from the title screen.
// Both loaders use the exact same injection target and method, so one class
// covers Fabric and NeoForge without any Stonecutter conditions needed.
// Small, focused, proud of itself.

import com.forgetmecrops.util.log.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * MixinTitleScreen: The party crasher for the Minecraft title screen!
 * <p>
 * Injects at the start of {@link TitleScreen#init()} to log the detected platform
 * and MC version. A small but satisfying diagnostic on every title-screen load:
 * proves the mod loaded and tells the player which loader they're running.
 * </p>
 * <p>
 * Shared by Fabric and NeoForge — both platforms use the same mixin machinery
 * for the client screen class, so no platform guards are needed here.
 * </p>
 */
@Mixin(TitleScreen.class)
public class MixinTitleScreen {
    /**
     * Fires at the start of TitleScreen.init() — the mod's chance to wave hello.
     * Logs the platform name and Minecraft version type for diagnostic purposes.
     *
     * @param info the Mixin callback info (unused here, but required by the contract)
     */
    @Inject(at = @At("HEAD"), method = "init()V")
    private void init(CallbackInfo info) {
        com.forgetmecrops.client.TitleScreenLogger.logPlatform();
        LogUtils.logDebug("MC Version: {}", Minecraft.getInstance().getVersionType());
    }
}
