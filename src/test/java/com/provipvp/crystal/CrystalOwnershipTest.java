package com.provipvp.crystal;

import com.provipvp.crystal.CrystalScorer.Candidate;
import com.provipvp.crystal.CrystalScorer.ScoreConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Deckt die Schranken ab, ohne die sich die "eigene Crystals"-Menge nicht auswachsen laesst:
 * Zugehoerigkeit, Kapazitaetsverdraengung in der richtigen Reihenfolge, Altersgrenze, Snapshot-
 * Isolation und Reset. Dazu eine Ende-zu-Ende-Probe, dass ein Crystal, den die Schranke hat
 * verschwinden lassen, vom Scorer auch nicht mehr gewaehlt wird.
 */
class CrystalOwnershipTest {

    /** Besitz bis zum Vergessen, danach wieder fremd. */
    @Test
    void aSelfPlacedCrystalIsOursUntilWeForgetIt() {
        CrystalOwnership ownership = new CrystalOwnership();

        ownership.notePlaced(42);

        assertTrue(ownership.owns(42), "selbst gesetzter Crystal muss als eigener gelten");
        assertFalse(ownership.owns(7), "eine nie notierte ID ist nicht unser Crystal");

        ownership.forget(42);

        assertFalse(ownership.owns(42), "zerstoerter Crystal darf nicht als eigener weiterleben");
    }

    /** Die Kopie im Scorer darf sich nicht mit dem Ledger des Moduls verschranken. */
    @Test
    void aSnapshotIsUnaffectedByLaterMutations() {
        CrystalOwnership ownership = new CrystalOwnership();
        ownership.notePlaced(1);
        ownership.notePlaced(2);

        Set<Integer> givenToScorer = ownership.snapshot();

        ownership.forget(1);
        ownership.advance(CrystalOwnership.DEFAULT_MAX_AGE_TICKS + 1);   // die 2 faellt aus dem Fenster
        ownership.notePlaced(3);                                          // erst danach gesetzt

        assertEquals(Set.of(3), ownership.snapshot());
        // Lebend: nur die 3. In der Kopie: die 1 und die 2. Der Zustand ist auseinandergelaufen,
        // die Kopie nicht — genau das macht sie fuer den laufenden Scorer-Aufruf unbrauchbar, wenn
        // sie nicht mitwaechst.
        assertEquals(Set.of(1, 2), givenToScorer,
            "die uebergebene Kopie muss den Stand zeigen, nicht den spaeteren");
        assertThrows(UnsupportedOperationException.class, () -> givenToScorer.add(9),
            "die Kopie muss die Schranke des Scorers wirklich tragen");
    }

    /**
     * Ende zu Ende: der Scorer zundet eigene Crystals und niemals die des Gegners. Genau das macht
     * die Verwaltung der Zugehoerigkeit wertvoll — das Verhalten haengt an ihr, nicht nur an ihr
     * Buchhaltungszustand.
     */
    @Test
    void theScorerOnlyBurnsCrystalsWeStillClaim() {
        // Cap 2: die 100 wird als aeltere verdraengt, die 999 war nie unsere.
        CrystalOwnership ownership = new CrystalOwnership(2, 10_000);
        ownership.notePlaced(100);
        ownership.notePlaced(200);
        ownership.notePlaced(300);

        ScoreConfig config = new ScoreConfig(c -> 6.0, ownership.snapshot(), 0, 0.0);
        List<Candidate> candidates = List.of(
            new Candidate(100, 10, 1.0, 64.0, 0.0),
            new Candidate(200, 10, 2.0, 64.0, 0.0),
            new Candidate(999, 10, 0.0, 64.0, 0.0));

        assertEquals(200, CrystalScorer.pick(candidates, config).orElseThrow().entityId(),
            "die verdraengte und die fremde ID duerfen nicht gewaehlt werden");
    }

    /** Ueberlauf verdraengt den aelteren Eintrag, nicht einen beliebigen. */
    @Test
    void exceedingTheCapDropsTheOldestEntry() {
        CrystalOwnership ownership = new CrystalOwnership(3, 10_000);

        ownership.notePlaced(1);
        ownership.notePlaced(2);
        ownership.notePlaced(3);
        ownership.advance(1);
        ownership.notePlaced(4);      // Cap ueberschritten: die 1 ist dran
        ownership.notePlaced(5);      // Cap erneut ueberschritten: die 2 ist dran

        assertEquals(3, ownership.snapshot().size(), "der Cap darf nicht ueberschritten werden");
        assertFalse(ownership.owns(1));
        assertFalse(ownership.owns(2));
        assertTrue(ownership.owns(3));
        assertTrue(ownership.owns(4));
        assertTrue(ownership.owns(5));
    }

    /** Ohne {@link CrystalOwnership#advance(int)} darf die Menge trotzdem nicht unbegrenzt waachsen. */
    @Test
    void theCapAloneBoundsTheSetWhenTimeNeverAdvances() {
        CrystalOwnership ownership = new CrystalOwnership(2, 10_000);

        for (int id = 1; id <= 500; id++) ownership.notePlaced(id);

        assertEquals(2, ownership.snapshot().size());
        assertEquals(List.of(499, 500), List.copyOf(ownership.snapshot()),
            "die beiden juengsten muessen uebrig sein");
    }

    /**
     * Eine zweite Notiz derselben ID ist kein zweiter Crystal — und darf den Eintrag auch nicht
     * unsterblich machen, sonst waere die Altersgrenze genau dort nutzlos, wo das Paket-Echo laeuft.
     */
    @Test
    void notingTheSameIdTwiceKeepsASingleEntryThatStillExpires() {
        CrystalOwnership ownership = new CrystalOwnership();

        ownership.notePlaced(5);
        ownership.advance(150);
        ownership.notePlaced(5);      // Echo, kein zweiter Crystal
        assertEquals(1, ownership.snapshot().size());

        ownership.advance(50);
        assertTrue(ownership.owns(5), "nach insgesamt 200 Ticks lebt der Eintrag noch");
        ownership.advance(1);
        assertFalse(ownership.owns(5), "mit dem Alter des ersten Vermerks, nicht des zweiten, faellig");
    }

    /** Altersgrenze: genau die Grenze lebt noch, ein Tick mehr nicht. */
    @Test
    void anEntryDisappearsOneTickPastTheMaximumAge() {
        CrystalOwnership ownership = new CrystalOwnership();

        ownership.notePlaced(1);
        ownership.advance(200);
        assertTrue(ownership.owns(1), "bei exakt maxAgeTicks ist der Eintrag noch nicht faellig");

        ownership.advance(1);
        assertFalse(ownership.owns(1));
    }

    /** Nur die aelteren Eintraege fallen; die juengeren derselben Runde bleiben. */
    @Test
    void expiryOnlyRemovesTheEntriesThatOutlivedTheWindow() {
        CrystalOwnership ownership = new CrystalOwnership();
        ownership.notePlaced(1);

        ownership.advance(120);
        ownership.notePlaced(2);
        ownership.notePlaced(3);

        ownership.advance(100);       // die 1 ist jetzt 220 Ticks alt, die 2 und 3 je 100

        assertFalse(ownership.owns(1));
        assertTrue(ownership.owns(2));
        assertTrue(ownership.owns(3));
    }

    /** Das Modul muss die gesetzte Grenze nennen koennen, ohne sie zu duplizieren. */
    @Test
    void theConfiguredMaximumAgeIsReadableAndTakesEffect() {
        assertEquals(200, new CrystalOwnership().maxAgeTicks(), "Standard sind 200 Ticks");

        CrystalOwnership ownership = new CrystalOwnership(8, 60);
        assertEquals(60, ownership.maxAgeTicks());

        ownership.notePlaced(1);
        ownership.advance(60);
        assertTrue(ownership.owns(1));

        ownership.setMaxAgeTicks(10); // Setting im Kampf verschaerft
        assertEquals(10, ownership.maxAgeTicks());
        ownership.advance(11);
        assertFalse(ownership.owns(1), "die verschraefte Grenze muss sofort greifen");
    }

    /** Dimensionswechsel: nichts aus der alten Welt darf in der neuen noch gelten. */
    @Test
    void resetEmptiesTheStateAndRestartsTheAgeClock() {
        CrystalOwnership ownership = new CrystalOwnership();
        ownership.notePlaced(1);
        ownership.advance(150);

        ownership.reset();

        assertFalse(ownership.owns(1));
        assertTrue(ownership.snapshot().isEmpty());

        // Neuer Eintrag nach dem Reset: waere der Tickzaehler nicht zurueckgesetzt, waere dieser
        // Crystal sofort 150 Ticks alt und damit an der Grenze.
        ownership.notePlaced(2);
        ownership.advance(200);
        assertTrue(ownership.owns(2));
    }

    /** Ein zuruecklaufender Takt wuerde Eintraege wieder verjuengen — das muss scheitern. */
    @Test
    void aNegativeTimeStepIsRejected() {
        CrystalOwnership ownership = new CrystalOwnership();

        assertThrows(IllegalArgumentException.class, () -> ownership.advance(-1));
    }

    /** Eine Obergrenze unter 1 wuerde jeden Crystal sofort wieder verdraengen. */
    @Test
    void anUnusableCapIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new CrystalOwnership(0, 200));
        assertThrows(IllegalArgumentException.class, () -> new CrystalOwnership(32, -1));
    }
}