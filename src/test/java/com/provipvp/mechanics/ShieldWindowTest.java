package com.provipvp.mechanics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShieldWindowTest {

    /** Der Wert, an dem sich die ganze Klasse dreht: Vanilla blockt erst ab dem 6. Tick. */
    private static final int VANILLA_ACTIVATION = 5;

    @Test
    void aktivierungsverzoegerungIstFuenfTicks() {
        assertEquals(VANILLA_ACTIVATION, ShieldWindow.ACTIVATION_TICKS);
    }

    @Test
    void notfallfensterLiefertFuenfzehnTicksEchtenSchutz() {
        assertEquals(15, ShieldWindow.usefulTicks(ShieldWindow.EMERGENCY_HELD_TICKS));
    }

    @Test
    void frischeExplosionLiefertZehnTicksEchtenSchutz() {
        assertEquals(10, ShieldWindow.usefulTicks(ShieldWindow.FRESH_EXPLOSION_HELD_TICKS));
    }

    @Test
    void beideAusgeliefertenFensterHaltenLaengerAlsDasMinimum() {
        assertTrue(ShieldWindow.EMERGENCY_HELD_TICKS >= ShieldWindow.MIN_USEFUL_TICKS);
        assertTrue(ShieldWindow.FRESH_EXPLOSION_HELD_TICKS >= ShieldWindow.MIN_USEFUL_TICKS);
    }

    /**
     * Der eigentliche Bug, den die Klasse verhindert: ein Fenster, das die Aktivierungsverzoegerung
     * nicht mitrechnet, sieht 20Ticks lang aus und bringt 10. Genau daran ist HumanPvP gescheitert,
     * waehrend GodmodePvP es richtig machte.
     */
    @Test
    void jedesFensterLiefertSpuerbarenSchutz() {
        assertTrue(ShieldWindow.usefulTicks(ShieldWindow.EMERGENCY_HELD_TICKS) >= 5);
        assertTrue(ShieldWindow.usefulTicks(ShieldWindow.FRESH_EXPLOSION_HELD_TICKS) >= 5);
        assertTrue(ShieldWindow.usefulTicks(ShieldWindow.MIN_USEFUL_TICKS) >= 5);
    }

    @Test
    void bisWannIstRelativZumJetzt() {
        assertEquals(1000 + ShieldWindow.EMERGENCY_HELD_TICKS,
            ShieldWindow.until(1000, ShieldWindow.EMERGENCY_HELD_TICKS));
        assertEquals(1000 + ShieldWindow.FRESH_EXPLOSION_HELD_TICKS,
            ShieldWindow.until(1000, ShieldWindow.FRESH_EXPLOSION_HELD_TICKS));
    }

    @Test
    void zuKurzeFensterWerdenAufDasMinimumGehoben() {
        // Der Bot soll nie ein Fenster bekommen, in dem er haelt und nicht schuetzt.
        assertEquals(1000 + ShieldWindow.MIN_USEFUL_TICKS, ShieldWindow.until(1000, 2));
        assertEquals(1000 + ShieldWindow.MIN_USEFUL_TICKS, ShieldWindow.until(1000, 0));
        assertEquals(1000 + ShieldWindow.MIN_USEFUL_TICKS, ShieldWindow.until(1000, -50));
    }

    @Test
    void laengereFensterBleibenUnveraendert() {
        assertEquals(1100, ShieldWindow.until(1000, 100));
    }

    @Test
    void nutzloserSchutzKannNichtNegativWerden() {
        assertEquals(0, ShieldWindow.usefulTicks(0));
        assertEquals(0, ShieldWindow.usefulTicks(-10));
    }

    @Test
    void fensterLaeuftImLetztenGehaltenenTickNoch() {
        int until = ShieldWindow.until(1000, ShieldWindow.EMERGENCY_HELD_TICKS);
        assertFalse(ShieldWindow.expired(until - 1, until), "vor dem Ende blockt es noch");
        assertTrue(ShieldWindow.expired(until, until), "am Ende nicht mehr");
        assertTrue(ShieldWindow.expired(until + 1, until), "danach sicher nicht mehr");
    }
}