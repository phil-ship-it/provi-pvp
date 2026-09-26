package com.provipvp.mixin;

import meteordevelopment.meteorclient.gui.GuiTheme;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/**
 * Kompatibilitaetsschicht fuer Fremd-Themes (z.B. Catppuccin 2.2.0): Die wurde gegen einen
 * aelteren 26.2-Snapshot gebaut und implementiert {@code modulesHelpText()} nicht, obwohl
 * Meteor die Methode inzwischen abstract fuehrt. Beim Aufbau der Modulliste knallt es dann mit
 * {@code AbstractMethodError} - das Addon wirkt schlicht kaputt (das war der Grund, warum es
 * bei uns als {@code .disabled} lag).
 *
 * <p>Der Overwrite liefert die Meteor-Standardantwort ({@code true} = Hilfetext anzeigen).
 * Themes, die die Methode selbst implementieren, gewinnen weiterhin, weil eine
 * Subklassen-Definition die geerbte Basis-Implementierung ueberstimmt. Faellt Meteor die
 * Methode irgendwann weg, schlaegt der Mixin deutlich sichtbar fehl statt still zu brechen.
 */
@Mixin(GuiTheme.class)
public abstract class GuiThemeCompatMixin {
    @Overwrite
    public boolean modulesHelpText() {
        return true;
    }
}
