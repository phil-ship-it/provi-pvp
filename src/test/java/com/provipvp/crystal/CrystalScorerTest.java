package com.provipvp.crystal;

import com.provipvp.crystal.CrystalScorer.Candidate;
import com.provipvp.crystal.CrystalScorer.CrystalChoice;
import com.provipvp.crystal.CrystalScorer.ScoreConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.ToDoubleFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Deckt die Crystal-Auswahl ab: Schaden statt Naehe, eigene Crystals statt aller, Mindestalter
 *  statt "sofort zuetzen", und ein deterministischer Gleichstand. */
class CrystalScorerTest {

    /** Simuliert die Schadensfunktion des Moduls: hier abhaengig von der Position, wie es
     *  {@code DamageUtils.crystalDamage} in der Realitaet auch ist. */
    private static ToDoubleFunction<Candidate> damageByDistance() {
        return c -> Math.max(0, 8.0 - c.x());
    }

    private static Candidate at(int id, int ticks, double x) {
        return new Candidate(id, ticks, x, 64.0, 0.0);
    }

    private static ScoreConfig config(Set<Integer> owned, ToDoubleFunction<Candidate> damage) {
        return new ScoreConfig(damage, owned, 0, 0.0);
    }

    @Test
    void theHighestDamageCrystalWinsNotTheNearestOne() {
        // Entity 7 steht dichter dran, bringt aber weniger Schaden. Genau diese Umkehrung ist der
        // Grund fuer den Scorer - "naechster Treffer" kostet den Cooldown fuer 1.5 statt 6.0.
        List<Candidate> candidates = List.of(at(7, 12, 6.0), at(9, 12, 2.0));
        ScoreConfig config = config(Set.of(7, 9), damageByDistance());

        CrystalChoice choice = CrystalScorer.pick(candidates, config).orElseThrow();
        assertEquals(9, choice.entityId());
        assertEquals(6.0, choice.damage());
    }

    @Test
    void aLowerDamageCrystalScoresLower() {
        // Zwei Kandidaten, beide zulaessig, unterschiedlicher Schaden: die Reihenfolge der Liste
        // darf das Ergebnis nicht bestimmen, der Schaden schon.
        List<Candidate> candidates = List.of(at(1, 10, 7.0), at(2, 10, 3.0));
        ScoreConfig config = config(Set.of(1, 2), damageByDistance());

        CrystalChoice choice = CrystalScorer.pick(candidates, config).orElseThrow();
        assertEquals(2, choice.entityId());
        assertEquals(5.0, choice.damage());
        // Gegenprobe mit vertauschter Listenreihenfolge: unveraendertes Ergebnis.
        assertEquals(2, CrystalScorer.pick(List.of(at(2, 10, 3.0), at(1, 10, 7.0)), config)
            .orElseThrow().entityId());
    }

    @Test
    void anEnemyOwnedCrystalIsNeverSelected() {
        // Entity 7 liegt naeher und ist juenger, hat aber eine fremde ID: ihn zu zerstoeren gibt
        // dem Gegner den Pop, den wir eigentlich gegen ihn stellen wollten.
        List<Candidate> candidates = List.of(at(7, 20, 1.0), at(8, 20, 5.0));
        ScoreConfig config = config(Set.of(8), damageByDistance());

        CrystalChoice choice = CrystalScorer.pick(candidates, config).orElseThrow();
        assertEquals(8, choice.entityId());
    }

    @Test
    void aCrystalOwnedByNobodyIsNeverSelected() {
        List<Candidate> candidates = List.of(at(7, 20, 1.0));
        Optional<CrystalChoice> choice = CrystalScorer.pick(candidates, config(Set.of(), damageByDistance()));
        assertTrue(choice.isEmpty());
    }

    @Test
    void aCrystalYoungerThanTheMinimumIsNeverSelected() {
        // 2 Ticks alt: gesetzt, aber noch nicht serverseitig bestaetigt. Mit Mindestalter 3 fliegt er
        // raus, obwohl er der staerkste waere.
        List<Candidate> candidates = List.of(at(4, 2, 0.0), at(5, 9, 6.0));
        ScoreConfig config = new ScoreConfig(damageByDistance(), Set.of(4, 5), 3, 0.0);

        CrystalChoice choice = CrystalScorer.pick(candidates, config).orElseThrow();
        assertEquals(5, choice.entityId());
    }

    @Test
    void aCrystalExactlyAtTheMinimumAgeIsStillAllowed() {
        // "Mindestens N Ticks" heisst N, nicht N+1 - sonst waere die Einstellung um eins verschoben.
        List<Candidate> candidates = List.of(at(4, 3, 0.0));
        ScoreConfig config = new ScoreConfig(damageByDistance(), Set.of(4), 3, 0.0);

        assertEquals(4, CrystalScorer.pick(candidates, config).orElseThrow().entityId());
    }

    @Test
    void thePickIsEmptyWhenEverythingIsFilteredOut() {
        // Drei Kandidaten, drei verschiedene Ablehnungsgruende: fremde ID, zu jung, kein Schaden.
        List<Candidate> candidates = List.of(at(1, 20, 0.0), at(2, 1, 0.0), at(3, 20, 0.0));
        ToDoubleFunction<Candidate> onlyZero = c -> c.entityId() == 3 ? 0.0 : 6.0;
        ScoreConfig config = new ScoreConfig(onlyZero, Set.of(3), 3, 1.0);

        assertTrue(CrystalScorer.pick(candidates, config).isEmpty());
    }

    @Test
    void thePickIsEmptyForAnEmptyCandidateList() {
        assertTrue(CrystalScorer.pick(List.of(), config(Set.of(1), damageByDistance())).isEmpty());
    }

    @Test
    void aCrystalWithoutDamageIsNotWorthTheCooldown() {
        List<Candidate> candidates = List.of(at(1, 10, 0.0), at(2, 10, 4.0));
        ScoreConfig config = new ScoreConfig(c -> 0.0, Set.of(1, 2), 0, 0.0);

        assertTrue(CrystalScorer.pick(candidates, config).isEmpty());
    }

    @Test
    void damageExactlyAtTheThresholdIsAccepted() {
        // Die Schwelle filtert "kein Schaden" heraus, nicht den kleinsten noch sinnvollen Pop.
        List<Candidate> candidates = List.of(at(1, 10, 0.0));
        ScoreConfig config = new ScoreConfig(c -> 1.0, Set.of(1), 0, 1.0);

        assertEquals(1, CrystalScorer.pick(candidates, config).orElseThrow().entityId());
    }

    @Test
    void equalDamageIsBrokenDeterministicallyTowardsTheOlderCrystal() {
        List<Candidate> candidates = List.of(at(1, 5, 0.0), at(2, 9, 0.0));
        ScoreConfig config = config(Set.of(1, 2), c -> 4.0);

        // Ohne Regel waere das ein Tick-festes Signal (der Bot wechselt zwischen zwei gleichwertigen
        // Crystals) und zufaellig von der Reihenfolge der Entity-Liste abhaengig.
        assertEquals(2, CrystalScorer.pick(candidates, config).orElseThrow().entityId());
        assertEquals(2, CrystalScorer.pick(List.of(at(2, 9, 0.0), at(1, 5, 0.0)), config)
            .orElseThrow().entityId());
    }

    @Test
    void equalDamageAndAgeIsBrokenTowardsTheLowerEntityId() {
        List<Candidate> candidates = List.of(at(42, 9, 0.0), at(17, 9, 1.0));
        ScoreConfig config = config(Set.of(42, 17), c -> 4.0);

        assertEquals(17, CrystalScorer.pick(candidates, config).orElseThrow().entityId());
    }

    @Test
    void aBrokenDamageCalculationCannotWinTheRanking() {
        // NaN > anything ist false. Ohne die NaN-Pruefung wuerde der Kandidat durchwandern und
        // jede spaetere Vergleichsentscheidung verdrehen.
        List<Candidate> candidates = List.of(at(1, 10, 0.0), at(2, 10, 0.0));
        ToDoubleFunction<Candidate> nan = c -> c.entityId() == 1 ? Double.NaN : 3.0;

        assertEquals(2, CrystalScorer.pick(candidates, config(Set.of(1, 2), nan)).orElseThrow().entityId());
    }
}
