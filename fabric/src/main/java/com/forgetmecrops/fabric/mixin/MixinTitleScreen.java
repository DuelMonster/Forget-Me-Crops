package com.forgetmecrops.fabric.mixin;

// 🎨 Fabric title mixin: adds a little mod flair to the title screen, like a digital sticker.
// Emotional note: proud and slightly show-offy.
import com.forgetmecrops.util.log.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * MixinTitleScreen: The party crasher for the Minecraft title screen!
 */
@Mixin(TitleScreen.class)
public class MixinTitleScreen {
    /**
     * Injects at the start of TitleScreen.init() to log the detected platform and MC version.
     * A small but helpful diagnostic on every title-screen load: lets players know what mod
     * loader and Minecraft version they're running. Emotional bonus: proves the mod loaded.
     */
    @Inject(at = @At("HEAD"), method = "init()V")
    private void init(CallbackInfo info) {
        com.forgetmecrops.client.TitleScreenLogger.logPlatform();
        LogUtils.logDebug("MC Version: {}", Minecraft.getInstance().getVersionType());
    }
}
