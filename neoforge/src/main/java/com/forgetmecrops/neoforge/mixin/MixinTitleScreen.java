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

@Mixin(TitleScreen.class)
public class MixinTitleScreen {
    @Inject(at = @At("HEAD"), method = "init()V")
    private void init(CallbackInfo info) {
        com.forgetmecrops.client.TitleScreenLogger.logPlatform();
        LogUtils.logDebug("MC Version: {}", Minecraft.getInstance().getVersionType());
    }
}
