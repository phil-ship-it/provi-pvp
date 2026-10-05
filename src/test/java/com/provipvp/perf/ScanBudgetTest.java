package com.provipvp.perf;

import com.provipvp.perf.ScanBudget.ChunkHint;
import com.provipvp.perf.ScanBudget.ScanKind;
import com.provipvp.perf.ScanBudget.Stats;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Deckt ab, wann ein teurer Blockvolumen-Sweep laufen darf und was der Aufrufer in der
 * Zwischenzeit als Antwort benutzt - die beiden Entscheidungen, an denen sich zeigt, ob die
 * Drosselung wirklich ~3500 Blockabfragen pro Tick einspart, ohne je eine falsche Antwort zu
 * liefern.
 */
class ScanBudgetTest {

    private final ScanBudget budget = new ScanBudget();

    @Test
    void aRateLimitedKindRunsOnTheFirstCall() {
        // Sonst haette der Bot in den ersten Sekunden eines Kampfes gar keine Antwort - und
        // "0 Anker in der Naehe" faellt fuer ihn nach "Blockupdate" aus.
        assertTrue(budget.allows(ScanKind.NEARBY_BLOCK_COUNT, 100));
    }

    @Test
    void aKindWithIntervalFourIsSkippedForTheNextThreeTicks() {
        assertTrue(budget.allows(ScanKind.NEARBY_BLOCK_COUNT, 100), "der erste Lauf darf nie ausfallen");

        assertFalse(budget.allows(ScanKind.NEARBY_BLOCK_COUNT, 101), "Tick 101 ist innerhalb des Fensters");
        assertFalse(budget.allows(ScanKind.NEARBY_BLOCK_COUNT, 102), "Tick 102 ebenfalls");
        assertFalse(budget.allows(ScanKind.NEARBY_BLOCK_COUNT, 103), "Tick 103 ist der letzte gesperrte");
        assertTrue(budget.allows(ScanKind.NEARBY_BLOCK_COUNT, 104), "bei Tick 104 ist das Fenster abgelaufen");
    }

    @Test
    void aSkippedScanStillDeliversThePreviousResult() {
        budget.markContext(new ChunkHint(4, -2, "overworld"));
        assertTrue(budget.allows(ScanKind.NEARBY_BLOCK_COUNT, 100));
        budget.store(ScanKind.NEARBY_BLOCK_COUNT, 7);

        // Die drei gesperrten Ticks: kein Sweep, aber die Antwort aus dem Lauf von Tick 100.
        for (int tick = 101; tick <= 103; tick++) {
            assertFalse(budget.allows(ScanKind.NEARBY_BLOCK_COUNT, tick));
            assertEquals(Optional.of(7), budget.cached(ScanKind.NEARBY_BLOCK_COUNT),
                "in Tick " + tick + " muss der Wert aus dem letzten Lauf weiterleben");
        }
    }

    @Test
    void aChangedChunkInvalidatesTheCachedResult() {
        budget.markContext(new ChunkHint(4, -2, "overworld"));
        budget.store(ScanKind.NEARBY_BLOCK_COUNT, 7);

        // Gleicher Chunk, gleiche Welt: derselbe Ortsbezug darf nichts verwerfen.
        budget.markContext(new ChunkHint(4, -2, "overworld"));
        assertEquals(Optional.of(7), budget.cached(ScanKind.NEARBY_BLOCK_COUNT));

        // Ein Block weiter: dieselben Blockkoordinaten sind jetzt anderes Terrain, "7 Anker in
        // der Naehe" waere eine erfundene Antwort.
        budget.markContext(new ChunkHint(5, -2, "overworld"));
        assertEquals(Optional.empty(), budget.cached(ScanKind.NEARBY_BLOCK_COUNT),
            "nach dem Chunkwechsel darf kein Ergebnis der alten Nachbarschaft mehr kommen");
    }

    @Test
    void aChangedWorldInvalidatesEvenAtTheSameChunk() {
        budget.markContext(new ChunkHint(0, 0, "overworld"));
        budget.store(ScanKind.NEARBY_BLOCK_COUNT, 7);

        // Gleiche Chunk-Koordinaten, andere Welt: ein Bett explodiert im Nether ueberall und in
        // der Overworld nirgends - die alte Antwort ist in der neuen Welt keine mehr.
        budget.markContext(new ChunkHint(0, 0, "nether"));
        assertEquals(Optional.empty(), budget.cached(ScanKind.NEARBY_BLOCK_COUNT));
    }

    @Test
    void invalidateAllClearsEveryKindAndKeepsTheCounters() {
        budget.markContext(new ChunkHint(0, 0, "overworld"));
        for (ScanKind kind : ScanKind.values()) {
            budget.store(kind, 1);
            budget.allows(kind, 10);
        }
        Stats before = budget.stats(ScanKind.ANCHOR_MAINTENANCE);

        budget.invalidateAll();

        for (ScanKind kind : ScanKind.values()) {
            assertEquals(Optional.empty(), budget.cached(kind), kind + " muss nach invalidateAll leer sein");
        }
        assertEquals(before, budget.stats(ScanKind.ANCHOR_MAINTENANCE),
            "die Zaehler dokumentieren die Sitzung und duerfen vom Leeren nicht verschwinden");
    }

    @Test
    void invalidateAllRestoresTheFullBudgetImmediately() {
        assertTrue(budget.allows(ScanKind.ANCHOR_EXPLOSION, 50));
        assertFalse(budget.allows(ScanKind.ANCHOR_EXPLOSION, 51), "ohne Invalidierung laeuft die Frist weiter");

        budget.invalidateAll();

        // Wer nichts mehr im Cache hat, darf nicht noch zwei Ticks auf eine Erlaubnis warten -
        // sonst haette der Aufrufer in diesem Fenster gar keine Antwort.
        assertTrue(budget.allows(ScanKind.ANCHOR_EXPLOSION, 51),
            "nach dem Verwerfen muss der Ersatzlauf sofort erlaubt sein");
    }

    @Test
    void invalidateClearsOnlyTheNamedKind() {
        budget.markContext(new ChunkHint(0, 0, "overworld"));
        budget.store(ScanKind.ANCHOR_MAINTENANCE, 11);
        budget.store(ScanKind.ANCHOR_EXPLOSION, 3);

        budget.invalidate(ScanKind.ANCHOR_MAINTENANCE);

        assertEquals(Optional.empty(), budget.cached(ScanKind.ANCHOR_MAINTENANCE),
            "der Blockupdate trifft den gepufferten Ladestand, also genau diese Art");
        assertEquals(Optional.of(3), budget.cached(ScanKind.ANCHOR_EXPLOSION),
            "die Explosionszaehlung haengt an anderen Zellen und bleibt gueltig");
    }

    @Test
    void invalidateLeavesTheOtherKindsRateLimited() {
        assertTrue(budget.allows(ScanKind.ANCHOR_MAINTENANCE, 10));
        assertTrue(budget.allows(ScanKind.ANCHOR_EXPLOSION, 10));

        budget.invalidate(ScanKind.ANCHOR_MAINTENANCE);

        // Wuerde invalidate auch die Fristen der anderen Arten zuruecksetzen, wuerde ein einzelner
        // Blockupdate den halben Cache verwerfen - und die Drosselung waie genau wieder aufgehoben.
        assertFalse(budget.allows(ScanKind.ANCHOR_EXPLOSION, 11),
            "eine fremde Invalidierung darf die Frist einer anderen Art nicht zuruecksetzen");
        assertTrue(budget.allows(ScanKind.ANCHOR_MAINTENANCE, 11),
            "die invalidated Art darf sofort neu scannen");
    }

    @Test
    void allowedAndSkippedCountersAccumulatePerKind() {
        // 9 Aufrufe im Abstand 1 ab Tick 100 mit Intervall 4: erlaubt bei 100, 104 und 108.
        for (int tick = 100; tick <= 108; tick++) budget.allows(ScanKind.NEARBY_BLOCK_COUNT, tick);

        assertEquals(new Stats(3, 6), budget.stats(ScanKind.NEARBY_BLOCK_COUNT));
    }

    @Test
    void countersStaySeparatePerKind() {
        for (int tick = 100; tick <= 108; tick++) {
            budget.allows(ScanKind.NEARBY_BLOCK_COUNT, tick);
            budget.allows(ScanKind.ANCHOR_EXPLOSION, tick);
        }
        // Beide Arten bekommen dieselben 9 Ticks vorgelegt, aber unterschiedliche Fenster:
        // Intervall 2 laeuft fuenfmal, Intervall 4 dreimal. Die Counter muessen das getrennt
        // ausweisen - addiert man sie, sieht man nur "14 Aufrufe" und damit nichts.
        assertEquals(new Stats(5, 4), budget.stats(ScanKind.ANCHOR_EXPLOSION));
        assertEquals(new Stats(3, 6), budget.stats(ScanKind.NEARBY_BLOCK_COUNT));
    }

    @Test
    void perTickIsNeverRateLimited() {
        for (int tick = 0; tick < 500; tick++) {
            assertTrue(budget.allows(ScanKind.PER_TICK, tick), "PER_TICK ist per Definition billig genug fuer jeden Tick");
        }
        assertEquals(new Stats(500, 0), budget.stats(ScanKind.PER_TICK),
            "ein nie gedrosselter Scan darf keine einzige Skip-Buchung erzeugen");
    }

    @Test
    void perTickIgnoresEvenAnAbsurdlyShortInterval() {
        // Das Intervall von PER_TICK ist 0; wer es per setIntervalTicks auf 0 oder 1 setzt, darf
        // daraus keine Drosselung bauen.
        budget.setIntervalTicks(ScanKind.PER_TICK, 1);
        assertTrue(budget.allows(ScanKind.PER_TICK, 1));
        assertTrue(budget.allows(ScanKind.PER_TICK, 1), "derselbe Tick zweimal ist bei PER_TICK erlaubt");
    }

    @Test
    void aKindIntervallIsConfigurable() {
        ScanBudget tuned = new ScanBudget(Map.of(ScanKind.ANCHOR_EXPLOSION, 7));

        assertTrue(tuned.allows(ScanKind.ANCHOR_EXPLOSION, 200));
        for (int tick = 201; tick <= 206; tick++) {
            assertFalse(tuned.allows(ScanKind.ANCHOR_EXPLOSION, tick), "Tick " + tick + " liegt im 7er-Fenster");
        }
        assertTrue(tuned.allows(ScanKind.ANCHOR_EXPLOSION, 207));
        // Nicht genannte Arten behalten ihre Defaults - sonst waere ein Setting fuer einen Scan
        // stillschweigend auch ein Setting fuer alle anderen.
        assertEquals(4, tuned.intervalTicks(ScanKind.NEARBY_BLOCK_COUNT));
    }

    @Test
    void aNonPositiveIntervallIsClampedToOne() {
        ScanBudget broken = new ScanBudget();
        broken.setIntervalTicks(ScanKind.NEARBY_BLOCK_COUNT, 0);

        // Intervall 0 wuerde nicht drosseln, sondern jeden Lauf erlauben - das Gegenteil dessen,
        // was der Aufrufer mit "alle 0 Ticks" gemeint haben kann.
        assertTrue(broken.allows(ScanKind.NEARBY_BLOCK_COUNT, 5));
        assertFalse(broken.allows(ScanKind.NEARBY_BLOCK_COUNT, 5), "auch beim kleinsten Intervall gilt: nicht zweimal im selben Tick");
        assertTrue(broken.allows(ScanKind.NEARBY_BLOCK_COUNT, 6));
    }

    @Test
    void aRestartedTickCounterThrowsStaleResultsAway() {
        budget.markContext(new ChunkHint(0, 0, "overworld"));
        assertTrue(budget.allows(ScanKind.ANCHOR_EXPLOSION, 40_000));
        budget.store(ScanKind.ANCHOR_EXPLOSION, 5);

        // Disconnect und neuer Server: der Zaehler faellt auf 0. Ohne Reset wuerde der Aufrufer
        // 40 000 Ticks lang gesperrt bleiben und die alte Welt weiter beobachten.
        assertTrue(budget.allows(ScanKind.ANCHOR_EXPLOSION, 0), "nach dem Neustart muss sofort gescannt werden");
        assertEquals(Optional.empty(), budget.cached(ScanKind.ANCHOR_EXPLOSION),
            "ein Ergebnis aus der alten Sitzung darf nicht in die neue hineinragen");
    }

    @Test
    void aStoredNullIsNotOfferedAsACachedResult() {
        budget.markContext(new ChunkHint(0, 0, "overworld"));
        budget.store(ScanKind.ANCHOR_EXPLOSION, null);

        assertEquals(Optional.empty(), budget.cached(ScanKind.ANCHOR_EXPLOSION),
            "false und null sind verschiedene Antworten - null darf nicht wie ein Treffer aussehen");
    }

    @Test
    void theCacheKeepsResultsOfDifferentKindsApart() {
        budget.markContext(new ChunkHint(0, 0, "overworld"));
        budget.store(ScanKind.NEARBY_BLOCK_COUNT, 3);
        budget.store(ScanKind.ANCHOR_MAINTENANCE, 8);

        assertEquals(Optional.of(3), budget.cached(ScanKind.NEARBY_BLOCK_COUNT));
        assertEquals(Optional.of(8), budget.cached(ScanKind.ANCHOR_MAINTENANCE));

        budget.store(ScanKind.NEARBY_BLOCK_COUNT, 4);
        assertEquals(Optional.of(4), budget.cached(ScanKind.NEARBY_BLOCK_COUNT),
            "der neuere Lauf ersetzt den aelteren derselben Art");
        assertEquals(Optional.of(8), budget.cached(ScanKind.ANCHOR_MAINTENANCE),
            "das Schreiben der einen Art darf die andere nicht ueberschreiben");
    }

    @Test
    void aFullCacheDropsTheLeastRecentlyUsedResult() {
        // Kapazitaet 2 macht die LRU-Politik sichtbar; im Normalbetrieb ist sie so hoch wie die
        // Zahl der Kinds und kann gar nicht greifen.
        ScanBudget small = new ScanBudget(Map.of(), 2);
        small.markContext(new ChunkHint(0, 0, "overworld"));
        small.store(ScanKind.NEARBY_BLOCK_COUNT, 1);
        small.store(ScanKind.ANCHOR_MAINTENANCE, 2);
        small.store(ScanKind.ANCHOR_EXPLOSION, 3);

        assertEquals(Optional.empty(), small.cached(ScanKind.NEARBY_BLOCK_COUNT),
            "NEARBY_BLOCK_COUNT wurde seit dem Einlagern nicht mehr benutzt und ist damit der aelteste");
        assertEquals(Optional.of(2), small.cached(ScanKind.ANCHOR_MAINTENANCE));
        assertEquals(Optional.of(3), small.cached(ScanKind.ANCHOR_EXPLOSION));
    }

    @Test
    void aFreshlyReadResultSurvivesTheNextEviction() {
        ScanBudget small = new ScanBudget(Map.of(), 2);
        small.markContext(new ChunkHint(0, 0, "overworld"));
        small.store(ScanKind.NEARBY_BLOCK_COUNT, 1);
        small.store(ScanKind.ANCHOR_MAINTENANCE, 2);
        small.cached(ScanKind.NEARBY_BLOCK_COUNT);

        small.store(ScanKind.ANCHOR_EXPLOSION, 3);

        assertEquals(Optional.of(1), small.cached(ScanKind.NEARBY_BLOCK_COUNT),
            "gelesen heisst benutzt - der Eintrag darf nicht als der aelteste fliegen");
        assertEquals(Optional.empty(), small.cached(ScanKind.ANCHOR_MAINTENANCE));
    }

    @Test
    void aCachedResultIsOnlyOfferedToTheContextItWasMeasuredIn() {
        // Der Aufrufer kann den Sweep rechnen, ohne vorher markContext zu kennen - das Ergebnis
        // gehoert dann aber zu keinem Ortsbezug und wird nicht ausgegeben.
        ScanBudget fresh = new ScanBudget();
        fresh.store(ScanKind.ANCHOR_EXPLOSION, 9);
        assertEquals(Optional.empty(), fresh.cached(ScanKind.ANCHOR_EXPLOSION),
            "ohne bekannten Ortsbezug ist eine Blockanzahl nicht zuordenbar");

        fresh.markContext(new ChunkHint(0, 0, "overworld"));
        fresh.store(ScanKind.ANCHOR_EXPLOSION, 9);
        assertEquals(Optional.of(9), fresh.cached(ScanKind.ANCHOR_EXPLOSION),
            "gemessen unter demselben Ortsbezug, den der Aufrufer gerade meldet");
    }
}
