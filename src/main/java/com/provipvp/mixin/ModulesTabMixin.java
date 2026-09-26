package com.provipvp.mixin;

import com.provipvp.gui.ProviModulesScreen;
import com.provipvp.modules.ProviClickGui;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.tabs.TabScreen;
import meteordevelopment.meteorclient.gui.tabs.builtin.ModulesTab;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Leitet den Module-Tab auf unseren kombinierten Screen um. Bewusst am Tab und nicht an
 * {@code GuiTheme.modulesScreen()}: manche Themes (z.B. Catppuccin) ueberschreiben diese
 * Theme-Methode, ein Mixin auf die Basisklasse wuerde dort nie laufen. Der Tab-Hook gilt
 * fuer jedes Theme.
 */
@Mixin(ModulesTab.class)
public abstract class ModulesTabMixin {
    @Inject(method = "createScreen", at = @At("HEAD"), cancellable = true)
    private void provi$combinedScreen(GuiTheme theme, CallbackInfoReturnable<TabScreen> cir) {
        if (ProviClickGui.isOn()) cir.setReturnValue(new ProviModulesScreen(theme));
    }
}
