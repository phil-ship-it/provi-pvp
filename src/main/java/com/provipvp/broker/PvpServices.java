package com.provipvp.broker;

import java.util.LinkedHashSet;
import java.util.Set;

import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;

/**
 * Der einzige Ort, an dem {@link ActionBroker}, {@link PathLease} und {@link ConflictRegistry} fuer
 * das Addon existieren.
 *
 * <p>Ohne diesen Singleton haette jedes Modul eine eigene Instanz - und zwei Instanzen koennen sich
 * nicht sehen. Genau das waere der naechste Fehler nach dem bisherigen Muster: {@code SlowFallingAura}
 * haelt die {@code PROJECTILE}-Belegung, {@code GodmodePvP} weiss nichts davon und wirft im selben
 * Tick trotzdem seinen Crystal. Der Broker waere dann korrekt und wirkungslos.
 *
 * <p>{@link #tick()} wird einmal pro Client-Tick aufgerufen. Wer sich die Broker-Semantik ansehen will,
 * findet sie hier dokumentiert statt in jedem Modul neu.
 */
public final class PvpServices {

    private static final ActionBroker BROKER = new ActionBroker();
    private static final ConflictRegistry CONFLICTS = new ConflictRegistry();

    /**
     * Prioritaeten fuer {@link ActionBroker#claim}. Sie liegen absichtlich auf derselben Skala wie die
     * Rotationsprioren in den Combat-Modulen (Perle 70, Misc, Look), damit die Broker-Aufteilung und die
     * Rotations-Warteschlange nicht in zwei verschiedenen Welten rechnen.
     */
    public static final int P_CRITICAL = 100;  // Rettungsaktion, die ein laufendes Podest verhindert
    public static final int P_COMBAT = 70;     // regulaerer Angriff
    public static final int P_SUPPORT = 40;    // Heilung, Schild, Mobility
    public static final int P_OPPORTUNIST = 20;// nutzt eine Lage, die gerade ohnehin besteht

    private PvpServices() {
    }

    public static ActionBroker broker() {
        return BROKER;
    }

    public static ConflictRegistry conflicts() {
        return CONFLICTS;
    }

    /** Einmal pro Client-Tick. Setzt die Belegungen zurueck; die Pfad-Sperre bleibt bewusst bestehen,
     *  weil sie ueber Ticks hinweg gehalten wird, bis der letzte Besitzer freigibt. */
    public static void tick() {
        BROKER.tick();
    }

    /**
     * Meldet, ob dieses Modul in diesem Tick bereits gehandelt hat. Module pruefen das VOR teuren
     * Berechnungen - ein bereits aktives Modul soll gar nicht erst scannen, wenn es ohnehin nichts
     * mehr zustande bekommt.
     */
    public static boolean spentThisTick(Module module) {
        return BROKER.hasSpent(module);
    }

    /**
     * Kurzform fuer den haeufigsten Fall: Belegung anfordern, ausfuehren, Belegung verbrauchen.
     *
     * @return true, wenn die Aktion gehoert - dann ausfuehren und {@code broker.spend} aufrufen
     */
    public static boolean claim(Module module, ActionBroker.ActionKind kind, int priority) {
        return BROKER.claim(module, kind, priority);
    }

    /** Loest Belegungen und Pfad-Sperre fuer ein Modul. Muss aus {@code onDeactivate()} aufgerufen
     *  werden - sonst haelt ein ausgeschaltetes Modul den Baritone-Pfad weiter fest. */
    public static void release(Module module) {
        BROKER.release(module);
        BROKER.path().releaseAll(module);
    }

    /** Alle gerade aktiven Moduleklassen - Eingang fuer {@link ConflictRegistry}.
     *  {@code getActive()} statt einer Schleife ueber {@code Modules}: die Systems-Klasse selbst ist
     *  in Meteor 26.2 nicht {@code Iterable}. */
    public static Set<Class<?>> activeModules() {
        Set<Class<?>> out = new LinkedHashSet<>();
        for (Module m : Modules.get().getActive()) {
            out.add(m.getClass());
        }
        return out;
    }
}
