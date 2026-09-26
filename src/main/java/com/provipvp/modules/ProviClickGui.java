package com.provipvp.modules;

import com.provipvp.ProviPvPAddon;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;

/**
 * ClickGUI-Overhaul: kombiniertes Layout (Kategorie-Spalten wie Meteors Modules-Screen plus
 * einem Einstellungs-Pane rechts im Stil klassischer Client-GUIs) und eine echte
 * Ranking-Suche statt einer reinen Levenshtein-Sortierung.
 *
 * <p>Wichtig: Das Modul muss NICHT aktiviert sein - es traegt nur die Einstellungen. Die
 * Mixins lesen die Werte direkt, damit die GUI nach einem Update sofort passt und nicht
 * erst nach einem Aktivieren im neuen Layout erscheint. Abschalten ueber
 * {@link #enabled} jederzeit moeglich.
 */
public class ProviClickGui extends Module {
    private final SettingGroup sgLayout = settings.createGroup("1 · Layout");
    private final SettingGroup sgSearch = settings.createGroup("2 · Suche");

    public final Setting<Boolean> enabled = sgLayout.add(new BoolSetting.Builder()
        .name("enabled")
        .description("Master-Schalter. Aus = Meteors originales ClickGUI, inklusive Suche, unveraendert.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> settingsPane = sgLayout.add(new BoolSetting.Builder()
        .name("settings-pane")
        .description("Rechtes Pane mit den Einstellungen des ausgewaehlten Moduls, direkt in der Modulliste - ohne Screen-Wechsel. Rechtsklick auf ein Modul waehlt es aus, der Knopf 'Volle Ansicht' oeffnet weiterhin Meteors Modul-Screen.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Double> paneWidth = sgLayout.add(new DoubleSetting.Builder()
        .name("pane-width")
        .description("Breite des Einstellungs-Pane in GUI-Pixeln. Die Kategorie-Spalten werden automatisch in den Restbereich geschoben.")
        .defaultValue(240)
        .range(160, 460)
        .sliderRange(160, 460)
        .build()
    );

    public final Setting<Boolean> smartSearch = sgSearch.add(new BoolSetting.Builder()
        .name("smart-search")
        .description("Suche filtern und ranken statt nur nach Levenshtein-Distanz zu sortieren. Meteor sortiert aktuell alle Module ohne Filter, wodurch inhaltlich passende Module hinter sachfremden verschwinden.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> searchDescriptions = sgSearch.add(new BoolSetting.Builder()
        .name("search-descriptions")
        .description("Auch Setting-Beschreibungen durchsuchen, nicht nur Setting-Namen. Treffer werden als 'Beschreibung: xyz' begruendet.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Integer> typoTolerance = sgSearch.add(new IntSetting.Builder()
        .name("typo-tolerance")
        .description("Erlaubte Tippfehler fuer Tippfehler-Treffer. 0 = nur exakte/teilweise Uebereinstimmung. Tippfehler-Treffer stehen immer hinten, sie verdraengen nie einen echten Treffer.")
        .defaultValue(2)
        .range(0, 4)
        .sliderRange(0, 4)
        .build()
    );

    public final Setting<Integer> maxResults = sgSearch.add(new IntSetting.Builder()
        .name("max-results")
        .description("Hoechstzahl angezeigter Treffer.")
        .defaultValue(40)
        .range(5, 200)
        .sliderRange(5, 200)
        .build()
    );

    public ProviClickGui() {
        super(ProviPvPAddon.CATEGORY, "click-gui", "Kombi-Layout (Kategorien + Einstellungs-Pane) und Ranking-Suche fuer die Modulliste.");
    }

    private static ProviClickGui get() {
        return Modules.get().get(ProviClickGui.class);
    }

    public static boolean isOn() {
        ProviClickGui module = get();
        return module != null && module.enabled.get();
    }

    public static boolean layoutOn() {
        ProviClickGui module = get();
        return module != null && module.enabled.get() && module.settingsPane.get();
    }

    public static boolean searchOn() {
        ProviClickGui module = get();
        return module != null && module.enabled.get() && module.smartSearch.get();
    }

    public static boolean descriptionsOn() {
        ProviClickGui module = get();
        return module != null && module.enabled.get() && module.smartSearch.get() && module.searchDescriptions.get();
    }

    public static int typoTolerance() {
        ProviClickGui module = get();
        return module == null ? 2 : module.typoTolerance.get();
    }

    public static int maxResults() {
        ProviClickGui module = get();
        return module == null ? 40 : module.maxResults.get();
    }

    public static double paneWidth() {
        ProviClickGui module = get();
        return module == null ? 240 : module.paneWidth.get();
    }
}
