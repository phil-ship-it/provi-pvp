package com.provipvp.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

/** Deckt die reine Auswahlentscheidung aus {@link TargetSelector} ab - ohne laufenden
 *  Minecraft-Client. Geprueft wird der entity-neutrale Kern {@link TargetSelector.Sticky} (den
 *  {@code TargetSelector} 1:1 fuer seine LivingEntity-Ziele benutzt) sowie die Filter-/Naehe-Regel
 *  aus {@code pickBest}. */
class TargetSelectorTest {

    /** Zeichnet jeden Zielwechsel auf, den die Sticky-Semantik meldet - entspricht dem, was
     *  {@code TargetSelector} als {@code TargetChangeEvent} postet. */
    private static final class Recorder {
        final List<String> changes = new ArrayList<>();

        TargetSelector.Sticky<String> sticky() {
            return new TargetSelector.Sticky<>((from, to) -> changes.add(from + "->" + to));
        }
    }

    // ---------- Wechsel-Meldung ----------

    @Test
    void wechselLoestGenauEinEventAus() {
        Recorder rec = new Recorder();
        TargetSelector.Sticky<String> sticky = rec.sticky();

        sticky.update("a");
        rec.changes.clear(); // Ersterwerb ist ein Wechsel von null, hier uninteressant

        sticky.update("b");
        sticky.update("b");

        assertEquals(List.of("a->b"), rec.changes,
            "genau ein Wechsel gemeldet, wiederholtes Setzen desselben Ziels bleibt still");
        assertEquals("b", sticky.current());
    }

    @Test
    void erstesZielWirdAlsWechselVonNichtsGemeldet() {
        Recorder rec = new Recorder();
        rec.sticky().update("a");

        // Ziel erwerben ist ein Wechsel - sonst wuesste kein Abonnent, dass es jetzt ueberhaupt eins gibt.
        assertEquals(List.of("null->a"), rec.changes);
    }

    @Test
    void gleichesZielLoestKeinEventAus() {
        Recorder rec = new Recorder();
        TargetSelector.Sticky<String> sticky = rec.sticky();
        sticky.update("same");
        rec.changes.clear();

        for (int i = 0; i < 5; i++) sticky.update("same");

        assertTrue(rec.changes.isEmpty(), "dieselbe Instanz ist kein Zielwechsel");
        assertEquals("same", sticky.current());
    }

    @Test
    void instanzenMitGleichemInhaltSindVerschiedeneZiele() {
        // new String erzeugt bewusst eine andere Instanz mit gleichem Inhalt - ein Vergleich ueber
        // Inhalt/Gleichheit wuerde hier faelschlich "kein Wechsel" melden.
        Recorder rec = new Recorder();
        TargetSelector.Sticky<String> sticky = rec.sticky();
        sticky.update(new String("x"));
        rec.changes.clear();

        sticky.update(new String("x"));

        assertEquals(List.of("x->x"), rec.changes, "Vergleich ist Instanz-, nicht Inhaltsgleichheit");
    }

    @Test
    void resetSetztCurrentAufNull() {
        Recorder rec = new Recorder();
        TargetSelector.Sticky<String> sticky = rec.sticky();
        sticky.update("a");
        rec.changes.clear();

        sticky.reset();

        assertNull(sticky.current(), "reset() leert den Zustand");
        assertEquals(List.of("a->null"), rec.changes);
    }

    @Test
    void resetOhneVorzielMeldetNichts() {
        Recorder rec = new Recorder();
        rec.sticky().reset();

        assertTrue(rec.changes.isEmpty());
    }

    // ---------- Filter- und Naehe-Regel ----------

    private record Candidate(String name, double distSq) {}

    @Test
    void naechsterZulaessigerKandidatWirdGewaehlt() {
        List<Candidate> all = List.of(
            new Candidate("fern", 25.0),
            new Candidate("tot", 1.0),
            new Candidate("sichtbar", 4.0));

        Predicate<Candidate> filter = c -> !c.name().equals("tot");

        Candidate best = TargetSelector.pickBest(all, filter, Candidate::distSq);

        assertNotNull(best);
        assertEquals("sichtbar", best.name(), "gefilterte Kandidaten duerfen den Filter nicht umgehen");
    }

    @Test
    void ohneZulaessigenKandidatenBleibtEsBeiNull() {
        Candidate best = TargetSelector.pickBest(
            List.of(new Candidate("a", 1.0)), c -> false, Candidate::distSq);

        assertNull(best);
    }
}
