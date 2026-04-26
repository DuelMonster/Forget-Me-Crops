package com.fastharvester.mixin;

// 😄 Tiny mixin with big dreams: sprinkles mod info onto the title screen when it feels social.
// Why it matters: first impressions count. Make them charming.

import com.fastharvester.Constants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * MixinTitleScreen: The party crasher for the Minecraft title screen!
 * <p>
 * This mixin injects a little FastHarvester flavor into the title screen, just to let you know we're here and ready to farm.
 * </p>
 * <p>
 * Why does this matter? Because every mod deserves a dramatic entrance.
 * </p>
 */
@Mixin(TitleScreen.class)
public class MixinTitleScreen {
    /** Prevent instantiation of this mixin helper class. */
    private MixinTitleScreen() {}
    /**
     * Injects a log message at the start of the title screen's init method. Because subtlety is overrated.
     */
    @Inject(at = @At("HEAD"), method = "init()V")
    private void init(CallbackInfo info) {
        Constants.LOG.info("This line is printed by an example mod mixin from NeoForge!");
        Constants.LOG.info("MC Version: {}", Minecraft.getInstance().getVersionType());
    }
}
