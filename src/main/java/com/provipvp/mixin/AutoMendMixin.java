/*
 * Fixes an upstream Meteor Client crash bug (not our own code): AutoMend.onTick()
 * calls shouldWait() -> mc.player.getOffhandItem() without ever checking whether
 * mc.player is null. Any disconnect (server restart, kick, network drop, timeout)
 * while AutoMend is active crashes the whole client with a NullPointerException.
 *
 * This matters to us specifically because GodmodePvP.onActivate() enables AutoMend
 * by default (auto-mend defaultValue = true) - on a real anarchy server, random
 * disconnects are routine, so this crash would hit real users during normal play,
 * not just our bot test harness that happened to trigger it via a server restart.
 */
package com.provipvp.mixin;

import meteordevelopment.meteorclient.systems.modules.player.AutoMend;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AutoMend.class)
public class AutoMendMixin {

    @Inject(method = "onTick", at = @At("HEAD"), cancellable = true)
    private void provipvp$skipWhenNoPlayer(CallbackInfo ci) {
        if (Minecraft.getInstance().player == null) {
            ci.cancel();
        }
    }
}
