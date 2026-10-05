package com.provipvp.broker;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Erklaert, welche Module sich gegenseitig ausschliessen - und liefert eine Reihenfolge fuer den Fall,
 * dass doch zwei gleichzeitig aktiv sind.
 *
 * <p>Zwei Betriebsarten, und beide werden gebraucht:
 *
 * <ul>
 *   <li><b>Hart:</b> Wenn Modul B aktiviert wird und ein damit konfligierendes Modul laeuft, wird B
 *       gar nicht erst aktiv. Der Spieler bekommt eine Chat-Zeile mit dem Namen des Verdrängten. Das ist
 *       der Normalfall - ein Modul, das man nicht haben kann, laeuft nicht.</li>
 *   <li><b>Weich ({@link Mode#DEFERS}):</b> Beide duerfen laufen, aber das weichere Modul tritt zur
 *       Seite, sobald der Staerkere dieselbe Ressource will. Das ist fuer alles richtig, was nicht
 *       dieselbe Aktion ist sondern dieselbe <i>Ausgangslage</i> liest - etwa ein Angriffs-Modul neben
 *       einem Anzeige-Modul.</li>
 * </ul>
 *
 * <p>Ohne {@code mc.*}: der Registry kennt keine Module, er bekommt nur Klassen und eine Menge
 * aktiver Klassen. Damit ist die Aufloesungsreihenfolge direkt testbar, statt sie im Spiel zu erraten.
 */
public final class ConflictRegistry {

    public enum Mode {
        /** Darf nie gleichzeitig laufen; das neu aktivierte Modul wird zurueckgewiesen. */
        EXCLUSIVE,
        /** Beide duerfen laufen; das konfligierende Modul weicht der Belegung aus. */
        DEFERS
    }

    private record Rule(Mode mode, Set<Class<?>> conflicts, int priority) {
    }

    private final Map<Class<?>, Rule> rules = new HashMap<>();

    public ConflictRegistry declare(Class<?> owner, Mode mode, int priority, Class<?>... conflicts) {
        rules.put(owner, new Rule(mode, new LinkedHashSet<>(java.util.List.of(conflicts)), priority));
        return this;
    }

    /**
     * @return das Modul, das einem frisch aktivierten Modul weichen muss, oder {@code null}
     */
    public Class<?> findConflict(Class<?> activating, Set<Class<?>> active) {
        Rule rule = rules.get(activating);
        if (rule == null) return null;
        for (Class<?> other : rule.conflicts()) {
            if (active.contains(other)) return other;
        }
        return null;
    }

    /**
     * Loest den Fall, dass beide doch aktiv sind, ohne zurueckzuschalten: das hoeher priorisierte Modul
     * gewinnt, das andere darf nur weiterlaufen, wenn es als {@link Mode#DEFERS} deklariert ist.
     *
     * @return {@code true}, wenn der Aufrufer (das aktivierte Modul) zuruecktreten soll
     */
    public boolean shouldYield(Class<?> activating, Set<Class<?>> active) {
        Rule rule = rules.get(activating);
        if (rule == null) return false;

        for (Class<?> other : rule.conflicts()) {
            if (!active.contains(other)) continue;
            Rule otherRule = rules.get(other);

            // Das aktivierte Modul ist selbst EXKLUSIV: es hat gegen den laufenden Gegner keine Chance.
            if (rule.mode() == Mode.EXCLUSIVE) return true;

            // Sonst entscheidet die Prioritaet. Fehlt sie auf einer Seite, gewinnt der Laeufer -
            // ein bereits aktives Modul wird nicht durch eine spaetere Aktivierung verdraengt.
            if (otherRule == null) return true;
            return rule.priority() < otherRule.priority();
        }
        return false;
    }

    public int priorityOf(Class<?> owner) {
        Rule rule = rules.get(owner);
        return rule == null ? 0 : rule.priority();
    }
}
