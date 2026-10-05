package com.provipvp.broker;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Referenzgezaehlte Baritone-Sperre. Das ist die Korrektur fuer C3.
 *
 * <p>Bis hierher hat jeder Combat-Pfad sein eigenes {@code cancelFollow()} und danach NICHHT
 * zurueckgesetzt - Baritone kam also nur wieder in Gang, wenn der Aufrufer im selben Tick erneut
 * ein Ziel stellte. Praktisch bedeutete das: nach jeder belegten Combat-Slot-Aktionpfad war der Bot
 * fuer den Rest des Ticks ohne Verfolgung. Ein Modul, das jetzt ebenfalls anhalten will, weiss
 * nicht, dass schon jemand anders angehalten hat, und stellt es nie wieder her - oder stellt es
 * twice her und laesst Baritone waehrend eines Kampfes weiterlaufen.
 *
 * <p>Hier ist der Zustand ein Zaehler mit Besitzern: Anhalten und Freigeben sind symmetrisch, und
 * Baritone laeuft genau dann weiter, wenn niemand mehr haelt. Derselbe Besitzer, der zweimal anhaelt,
 * zaehlt einmal - sonst wuerde ein Modul mit zwei Aktionen den Weg dauerhaft blockieren.
 */
public final class PathLease {

    private final Set<Object> holders = new LinkedHashSet<>();

    /** @return true, wenn dieser Aufruf den Zustand von "unterwegs" auf "angehalten" umgestellt hat */
    public boolean pause(Object owner) {
        if (owner == null) return false;
        return holders.add(owner);
    }

    /** @return true, wenn dieser Aufruf den letzten Besitzer freigab und Baritone wieder laeuft */
    public boolean resume(Object owner) {
        if (owner == null) return false;
        return holders.remove(owner);
    }

    public boolean isPaused() {
        return !holders.isEmpty();
    }

    public int holders() {
        return holders.size();
    }

    public boolean heldBy(Object owner) {
        return owner != null && holders.contains(owner);
    }

    /**
     * Loest alles fuer einen Besitzer - noetig beim Deaktivieren und beim Weltwechsel, weil dort kein
     * einzelner Pfad mehr zurueckgerufen wird. Ohne das bliebe Baritone nach einem Toggle aus.
     */
    public void releaseAll(Object owner) {
        if (owner != null) holders.remove(owner);
    }

    public void clear() {
        holders.clear();
    }
}
