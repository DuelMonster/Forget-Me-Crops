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
     * Injects a log message at the start of the title screen's init method.
     */
    @Inject(at = @At("HEAD"), method = "init()V")
    private void init(CallbackInfo info) {
        com.forgetmecrops.client.TitleScreenLogger.logPlatform();
        LogUtils.logDebug("MC Version: {}", Minecraft.getInstance().getVersionType());
    }
}
