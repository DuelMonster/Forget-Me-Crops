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
 * FrameHoeReplacement: The emergency response team when a hoe breaks mid-harvest!
 * <p>
 * Encapsulates the complete "broken hoe" recovery transaction: pull a replacement hoe
 * from the linked chest, write it into the item frame, update the FrameRegistry entry,
 * and verify the replacement actually persisted to the frame. If any step fails, the
 * context is marked as chest-full so the scanner backs off gracefully.
 * </p>
 * <p>
 * Why its own class? Because the replacement logic touches the chest, the frame, the
 * registry, and the platform layer simultaneously. Inlining all that into HarvestUtils
 * would create a method that nobody wants to read and everyone fears to modify.
 * Separation of concerns is a virtue. So is keeping HarvestUtils readable.
 * </p>
 */
public final class FrameHoeReplacement {
    // Utility class. Replacement logic only; no instances required or desired.
    private FrameHoeReplacement() {}

    /**
     * Attempts to replace a broken frame-held hoe using the linked chest inventory.
     * Transaction steps:
     * <ol>
     *   <li>Take the first hoe from the chest (atomic slot removal)</li>
     *   <li>Write it into the harvest context as the active hoe</li>
     *   <li>Update the FrameRegistry entry with the new hoe item</li>
     *   <li>Push the new hoe into the item frame via the platform layer</li>
     *   <li>Read it back to verify the frame actually accepted it</li>
     *   <li>On verification failure, return the hoe to the chest and back off with a cooldown</li>
     * </ol>
     *
     * @param ctx harvest context containing chest, level, and frame anchor
     */
    public static void tryReplaceBrokenHoe(HarvestContext ctx) {
        if (ctx == null || ctx.chest == null) return;
        try {
            // Safely cast the anchor; if it's not our type, proceed without it
            FrameScanner.Anchor anchor = null;
            try { anchor = (FrameScanner.Anchor) ctx.anchor; } catch (Throwable ignored) {}

            // Step 1: grab a replacement hoe from the chest
            ItemStack replacement = ChestUtils.takeFirstHoe(ctx.chest);
            if (replacement != null && !replacement.isEmpty()) {
                try {
                    // Step 2: update the context so the current harvest cycle knows about the new hoe
                    ItemStack newHoe = replacement.copy(); newHoe.setCount(1);
                    ctx.setHoe(newHoe);
                    ctx.setSkipNextDamage(true); // fresh hoe: don't immediately damage it on the first use
                } catch (Throwable ignored) {}

                try {
                    // Step 3: update the registry cache so future ticks know the new hoe info
                    if (anchor != null) {
                        String dimId = ctx.level.dimension().identifier().toString();
                        FrameRegistry.updateHoe(dimId, anchor.framePos, ctx.getHoe().isEmpty() ? replacement.copy() : ctx.getHoe().copy());
                    }
                } catch (Throwable ignored) {}

                // Step 4: push the new hoe into the actual item frame entity via the platform layer
                try { com.forgetmecrops.platform.Services.PLATFORM.updateFrameItem(ctx.level, anchor == null ? null : anchor.framePos, ctx.getHoe().isEmpty() ? ItemStack.EMPTY : ctx.getHoe().copy()); } catch (Throwable ignored) {}

                if (anchor != null && ctx.level != null) {
                    try {
                        // Step 5: read it back and verify the replacement actually stuck to the frame
                        ItemStack verified = FrameScanner.readHoeFromFrame(ctx.level, anchor.framePos);
                        if (verified == null || verified.isEmpty() || !(verified.getItem() instanceof HoeItem)) {
                            // Step 6: frame rejected it — put the hoe back and give up for now
                            try { ChestUtils.insertAll(ctx.chest, java.util.List.of(replacement)); } catch (Throwable ignored) {}
                            try { String dimId = ctx.level.dimension().identifier().toString(); FrameRegistry.updateHoe(dimId, anchor.framePos, ItemStack.EMPTY); FrameRegistry.setCooldown(dimId, anchor.framePos, Config.getChestFullCooldownTicks()); } catch (Throwable ignored) {}
                            try { com.forgetmecrops.platform.Services.PLATFORM.updateFrameItem(ctx.level, anchor.framePos, ItemStack.EMPTY); } catch (Throwable ignored) {}
                            ctx.setChestFull(true); // reuse the chest-full flag to tell the caller to abort
                            LogUtils.logDebug("[HOE] Replacement did not persist to frame; returned to chest and aborting for {}", anchor.framePos);
                            return;
                        }
                    } catch (Throwable ignored) {}
                }

                LogUtils.logDebug("[HOE] Pulled replacement hoe from chest: {}", replacement);
                return;
            } else {
                // No hoe found in chest at all — clear the registry entry and set a cooldown
                LogUtils.logDebug("[HOE] No replacement hoe available in chest for frame at {}", anchor == null ? "unknown" : anchor.framePos);
                try { if (anchor != null) { String dimId = ctx.level.dimension().identifier().toString(); FrameRegistry.updateHoe(dimId, anchor.framePos, ItemStack.EMPTY); FrameRegistry.setCooldown(dimId, anchor.framePos, Config.getChestFullCooldownTicks()); } } catch (Throwable ignored) {}
                try { com.forgetmecrops.platform.Services.PLATFORM.updateFrameItem(ctx.level, anchor == null ? null : anchor.framePos, ItemStack.EMPTY); } catch (Throwable ignored) {}
                ctx.setChestFull(true);
            }
        } catch (Throwable t) { LogUtils.logWarn("[HOE] Error attempting to replace broken hoe", t); }
    }
}
