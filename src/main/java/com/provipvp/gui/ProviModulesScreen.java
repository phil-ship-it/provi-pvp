package com.provipvp.gui;

import com.provipvp.ProviPvPAddon;
import com.provipvp.modules.ProviClickGui;
import com.provipvp.util.SmartSearch;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WidgetScreen;
import meteordevelopment.meteorclient.gui.screens.ModulesScreen;
import meteordevelopment.meteorclient.gui.utils.Cell;
import meteordevelopment.meteorclient.gui.utils.WindowConfig;
import meteordevelopment.meteorclient.gui.widgets.WLabel;
import meteordevelopment.meteorclient.gui.widgets.containers.WContainer;
import meteordevelopment.meteorclient.gui.widgets.containers.WSection;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WWindow;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.Utils;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Kombiniertes ClickGUI: Meteors Kategorie-Spalten (wie im Modules-Screen) plus ein
 * dauerhaftes Einstellungs-Pane auf der rechten Seite. Damit entfaellt der Screen-Wechsel
 * beim Einstellen - die Einstellungen des ausgewaehlten Moduls stehen direkt neben der
 * Modulliste. Rechtsklick auf ein Modul waehlt es aus, "Volle Ansicht" im Pane
 * oeffnet weiterhin Meteors vollstaendigen Modul-Screen.
 *
 * <p>Die Kategorie-Spalten werden links angeordnet und vom Pane ferngehalten; die Positionen
 * liegen in Meteors WindowConfigs und bleiben damit ueber einen Neustart hinaus erhalten.
 */
public class ProviModulesScreen extends ModulesScreen {
    private static final String PANE_ID_PREFIX = "provi-pane-";

    /**
     * Meteors Kategorie-Controller ist privat, sein Typ eine protected verschachtelte Klasse -
     * ein Accessor-Mixin verlangt den exakten Feldtyp und kann ihn von aussen nicht benennen.
     * Darum werden genau diese beiden Felder reflektiert. Fehlen sie (Meteor-Update), laeuft
     * das Layout schlicht ohne Pane weiter.
     */
    private static final Field CONTROLLER_FIELD = lookupField(ModulesScreen.class, "controller");
    private static final Field ROOT_FIELD = lookupField(WidgetScreen.class, "root");

    private static Field lookupField(Class<?> owner, String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException | RuntimeException e) {
            ProviPvPAddon.LOG.warn("ClickGUI-Eigenbau inaktiv: Feld '" + name + "' nicht zugaenglich", e);
            return null;
        }
    }

    private Module selected;
    private WWindow pane;
    private Cell<WWindow> paneCell;
    private WContainer controller;

    public ProviModulesScreen(GuiTheme theme) {
        super(theme);
    }

    private WContainer controller() {
        if (CONTROLLER_FIELD == null) return null;
        try {
            return (WContainer) CONTROLLER_FIELD.get(this);
        } catch (IllegalAccessException e) {
            return null;
        }
    }

    public Module selected() {
        return selected;
    }

    @Override
    public void initWidgets() {
        super.initWidgets();

        if (!ProviClickGui.layoutOn()) return;
        controller = controller();
        if (controller == null) return;

        // Auswahl ueberlebt Screen-Neuaufbauten (Theme-Wechsel, Reload)
        if (selected != null) buildPane(selected);

        // Eigene Bedienhinweise; Meteors Block wird ausgeblendet
        WVerticalList hints = theme.verticalList();
        add(hints).pad(theme.scale(4)).bottom();
        hints.add(theme.label("Linksklick - Modul an/aus").color(theme.textSecondaryColor()));
        hints.add(theme.label("Rechtsklick - Modul im Pane rechts auswaehlen").color(theme.textSecondaryColor()));
        hideStockHelp();

        // Der Controller legt seine Fenster erst beim ersten Layout an - erst danach sind
        // Breiten bekannt und das Pane kann in die letzte Zeichenebene geholt werden.
        MeteorClient.mc.execute(() -> {
            controller = controller();
            layoutColumns();
            raisePane();
        });
    }

    /**
     * Meteors Hilfetext am unteren Rand beschreibt das alte Verhalten ("Right click - Open
     * module settings"). {@code GuiTheme.modulesHelpText()} ist abstrakt und damit kein
     * Mixin-Ziel, deshalb wird der Block anhand seines Textes ausgeblendet - wirkt in jedem
     * Theme, auch bei Drittanbieter-Themes.
     */
    private void hideStockHelp() {
        if (ROOT_FIELD == null) return;
        Object root;
        try {
            root = ROOT_FIELD.get(this);
        } catch (IllegalAccessException e) {
            return;
        }
        if (!(root instanceof WContainer container)) return;

        for (Cell<?> cell : container.cells) {
            if (!(cell.widget() instanceof WVerticalList list)) continue;
            for (Cell<?> inner : list.cells) {
                if (inner.widget() instanceof WLabel label && label.get().startsWith("Left click")) {
                    cell.widget().visible = false;
                    return;
                }
            }
        }
    }

    /**Rechtsklick auf ein Modul: im neuen Layout Auswahl statt Screen-Wechsel.*/
    public void select(Module module) {
        this.selected = module;
        if (!ProviClickGui.layoutOn() || controller == null) return;

        buildPane(module);
        MeteorClient.mc.execute(() -> {
            layoutColumns();
            raisePane();
        });
    }

    /**Pane in die letzte Zeichenebene holen, sonst zeichnen die Kategorie-Fenster darueber.*/
    private void raisePane() {
        if (paneCell == null || controller == null) return;
        if (controller.cells.remove(paneCell)) controller.cells.add(paneCell);
        invalidate();
    }

    /**
     * Spalten links vom Pane anordnen: noch nie platzierte Fenster bekommen eine saubere
     * Reihe, vom Nutzer verschobene Fenster bleiben, werden aber in den freien Bereich
     * geklemmt. Positionen liegen in Meteors WindowConfigs und ueberleben einen Neustart.
     */
    private void layoutColumns() {
        if (controller == null) return;

        double pad = theme.scale(6);
        double paneSpace = pane != null ? pane.width + theme.scale(12) : 0;
        double maxX = Utils.getWindowWidth() - paneSpace - pad;
        double x = pad;
        double y = pad;

        for (Cell<?> cell : controller.cells) {
            if (cell == paneCell) continue;
            if (!(cell.widget() instanceof WWindow window)) continue;
            if (window.id == null || window.id.equals("search") || window.id.startsWith(PANE_ID_PREFIX)) continue;

            WindowConfig config = theme.getWindowConfig(window.id);
            double width = Math.max(cell.width, window.minWidth);

            if (config.x == 0 && config.y == 0) {
                if (x + width > maxX && x > pad) {
                    x = pad;
                    y += cell.height + pad;
                }
                config.x = x;
                config.y = y;
                x += width + pad;
            } else {
                config.x = Math.min(Math.max(config.x, 0), Math.max(0, maxX - width));
                config.y = Math.max(config.y, 0);
            }
        }
    }

    private void buildPane(Module module) {
        if (paneCell != null) {
            controller.remove(paneCell);
            paneCell = null;
        }

        pane = theme.window(module.title);
        pane.id = PANE_ID_PREFIX + module.name;
        pane.minWidth = ProviClickGui.paneWidth();

        // Position vor dem Hinzufuegen setzen, damit WWindow.init() sie uebernimmt
        WindowConfig config = theme.getWindowConfig(pane.id);
        config.expanded = true;
        config.x = Math.max(0, Utils.getWindowWidth() - ProviClickGui.paneWidth() - theme.scale(6));
        config.y = theme.scale(6);

        // Wichtig: erst in den Container einhaengen - WContainer.add ruft init() auf und
        // erzeugt Header und View. Erst danach darf Inhalt hinzugefuegt werden.
        paneCell = controller.add(pane).top();
        paneCell.minWidth(ProviClickGui.paneWidth());

        // Kopfzeile: Modul, Addon, Beschreibung
        pane.add(theme.label(module.title)).expandX();
        if (module.addon != null) {
            pane.add(theme.label("Aus " + module.addon.name).color(theme.textSecondaryColor())).expandX();
        }
        if (!module.description.isBlank()) {
            pane.add(theme.label(module.description).color(theme.textSecondaryColor())).expandX();
        }

        WButton active = theme.button(module.isActive() ? "Deaktivieren" : "Aktivieren");
        active.action = () -> {
            module.toggle();
            active.set(module.isActive() ? "Deaktivieren" : "Aktivieren");
        };
        pane.add(active).padTop(theme.scale(2)).expandX();
        pane.add(theme.keybind(module.keybind)).expandX();
        pane.add(theme.horizontalSeparator());

        if (module.settings.sizeGroups() > 0) {
            pane.add(theme.settings(module.settings)).expandX();
        }

        WButton full = theme.button("Volle Ansicht");
        full.action = () -> openFullModuleScreen(this, module);
        pane.add(full).padTop(theme.scale(4)).expandX();
    }

    @Override
    protected void createSearchW(WContainer container, String query) {
        if (!ProviClickGui.searchOn() || query == null || query.isBlank()) {
            super.createSearchW(container, query);
            return;
        }

        SmartSearch.Options options = new SmartSearch.Options();
        options.typoTolerance = ProviClickGui.typoTolerance();
        options.searchDescriptions = ProviClickGui.descriptionsOn();
        options.limit = ProviClickGui.maxResults();

        List<ModuleEntry> entries = new ArrayList<>();
        for (Module module : Modules.get().getAll()) entries.add(new ModuleEntry(module));

        List<SmartSearch.Hit<ModuleEntry>> hits = SmartSearch.search(query, entries, options);
        if (hits.isEmpty()) {
            container.add(theme.label("Keine Treffer fuer \"" + query + "\"")
                .color(theme.textSecondaryColor())).pad(theme.scale(2));
            return;
        }

        WSection section = theme.section(hits.size() + " Treffer");
        section.spacing = 0;
        container.add(section).expandX();

        for (SmartSearch.Hit<ModuleEntry> hit : hits) {
            section.add(theme.module(hit.entry().module())).expandX();
            if (hit.reason() != null) {
                section.add(theme.label("  " + hit.reason()).color(theme.textSecondaryColor())).expandX();
            }
        }
    }

    /**Öffnet Meteors vollen Modul-Screen, ohne dass der Auswahl-Hook ihn abfaengt.*/
    static void openFullModuleScreen(ProviModulesScreen screen, Module module) {
        ProviGuiBridge.bypass = true;
        try {
            MeteorClient.mc.gui.setScreen(screen.theme.moduleScreen(module));
        } finally {
            ProviGuiBridge.bypass = false;
        }
    }
}
