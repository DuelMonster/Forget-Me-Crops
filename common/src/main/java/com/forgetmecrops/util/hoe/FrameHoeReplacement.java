package com.forgetmecrops.util.hoe;

import com.forgetmecrops.harvest.HarvestContext;
import com.forgetmecrops.frame.FrameRegistry;
import com.forgetmecrops.frame.FrameScanner;
import com.forgetmecrops.util.chest.ChestUtils;
import com.forgetmecrops.util.log.LogUtils;
import com.forgetmecrops.config.Config;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.HoeItem;

/**
 * Helper to encapsulate the chest -> registry -> frame hoe replacement transaction.
 */
public final class FrameHoeReplacement {
    private FrameHoeReplacement() {}

    /**
     * Attempts to replace a broken frame-held hoe using the linked chest inventory.
     *
     * @param ctx harvest context containing chest, level, and frame anchor
     */
    public static void tryReplaceBrokenHoe(HarvestContext ctx) {
        if (ctx == null || ctx.chest == null) return;
        try {
            FrameScanner.Anchor anchor = null;
            try { anchor = (FrameScanner.Anchor) ctx.anchor; } catch (Throwable ignored) {}

            ItemStack replacement = ChestUtils.takeFirstHoe(ctx.chest);
            if (replacement != null && !replacement.isEmpty()) {
                try {
                    ItemStack newHoe = replacement.copy(); newHoe.setCount(1);
                    ctx.hoe = newHoe;
                    ctx.skipNextDamage = true;
                } catch (Throwable ignored) {}

                try {
                    if (anchor != null) {
                        String dimId = ctx.level.dimension().identifier().toString();
                        FrameRegistry.updateHoe(dimId, anchor.framePos, ctx.hoe == null ? replacement.copy() : ctx.hoe.copy());
                    }
                } catch (Throwable ignored) {}

                try { com.forgetmecrops.platform.Services.PLATFORM.updateFrameItem(ctx.level, anchor == null ? null : anchor.framePos, ctx.hoe == null ? ItemStack.EMPTY : ctx.hoe.copy()); } catch (Throwable ignored) {}

                if (anchor != null && ctx.level != null) {
                    try {
                        ItemStack verified = FrameScanner.readHoeFromFrame(ctx.level, anchor.framePos);
                        if (verified == null || verified.isEmpty() || !(verified.getItem() instanceof HoeItem)) {
                            try { ChestUtils.insertAll(ctx.chest, java.util.List.of(replacement)); } catch (Throwable ignored) {}
                            try { String dimId = ctx.level.dimension().identifier().toString(); FrameRegistry.updateHoe(dimId, anchor.framePos, ItemStack.EMPTY); FrameRegistry.setCooldown(dimId, anchor.framePos, Config.chestFullCooldownTicks); } catch (Throwable ignored) {}
                            try { com.forgetmecrops.platform.Services.PLATFORM.updateFrameItem(ctx.level, anchor.framePos, ItemStack.EMPTY); } catch (Throwable ignored) {}
                            ctx.chestFull = true;
                            LogUtils.logDebug("[HOE] Replacement did not persist to frame; returned to chest and aborting for {}", anchor.framePos);
                            return;
                        }
                    } catch (Throwable ignored) {}
                }

                LogUtils.logDebug("[HOE] Pulled replacement hoe from chest: {}", replacement);
                return;
            } else {
                LogUtils.logDebug("[HOE] No replacement hoe available in chest for frame at {}", anchor == null ? "unknown" : anchor.framePos);
                try { if (anchor != null) { String dimId = ctx.level.dimension().identifier().toString(); FrameRegistry.updateHoe(dimId, anchor.framePos, ItemStack.EMPTY); FrameRegistry.setCooldown(dimId, anchor.framePos, Config.chestFullCooldownTicks); } } catch (Throwable ignored) {}
                try { com.forgetmecrops.platform.Services.PLATFORM.updateFrameItem(ctx.level, anchor == null ? null : anchor.framePos, ItemStack.EMPTY); } catch (Throwable ignored) {}
                ctx.chestFull = true;
            }
        } catch (Throwable t) { LogUtils.logWarn("[HOE] Error attempting to replace broken hoe", t); }
    }
}
