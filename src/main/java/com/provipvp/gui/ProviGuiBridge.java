package com.provipvp.gui;

/**
 * Vermeidungs-Flag fuer den Modul-Screen-Hook: wenn das Pane selbst den vollen
 * Modul-Screen oeffnet, darf der Hook nicht wieder auf Auswahl umschwenken.
 */
public final class ProviGuiBridge {
    public static boolean bypass;

    private ProviGuiBridge() {}
}
