package com.provipvp.mixin;

import com.provipvp.gui.ProviGuiBridge;
import com.provipvp.gui.ProviModulesScreen;
import com.provipvp.modules.ProviClickGui;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WidgetScreen;
import meteordevelopment.meteorclient.systems.modules.Module;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Theme-agnostischer Auswahl-Hook: beide Themes oeffnen den Modul-Screen ueber
 * {@code theme.moduleScreen(module)} (Rechtsklick auf ein Modul). Liegt gerade unser
 * Screen mit Pane offen, wird daraus stattdessen die Auswahl im Pane - und der
 * aufrufende Code bekommt den aktuellen Screen zurueck, damit das GUI offen bleibt.
 */
@Mixin(GuiTheme.class)
public abstract class GuiThemeMixin {
    @Inject(method = "moduleScreen", at = @At("HEAD"), cancellable = true)
    private void provi$selectInsteadOfScreen(Module module, CallbackInfoReturnable<WidgetScreen> cir) {
        if (ProviGuiBridge.bypass || !ProviClickGui.layoutOn()) return;
        if (!(MeteorClient.mc.gui.screen() instanceof ProviModulesScreen screen)) return;

        screen.select(module);
        cir.setReturnValue((WidgetScreen) MeteorClient.mc.gui.screen());
    }
}
