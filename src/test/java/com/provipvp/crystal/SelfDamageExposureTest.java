package com.provipvp.crystal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SelfDamageExposureTest {

    private static final double CAP = 12.0;

    /**
     * Der eigentliche Fehler, den die Klasse verhindert: die alte Regel verlangte, dass ALLE neun
     * Stichproben verdeckt sind, und hat damit jede Platzierung auf offenem Feld verworfen. Im
     * Nahkampf ist genau das der Normalfall — der Bot tat nichts und starb.
     */
    @Test
    void volleExpositionErlaubtDiePlatzierungWennDerDeckelPasst() {
        assertTrue(SelfDamageExposure.withinCap(5.0, 9, CAP));
        assertTrue(SelfDamageExposure.withinCap(12.0, 9, CAP));
    }

    @Test
    void volleExpositionVerweigertNurUeberDemDeckel() {
        assertFalse(SelfDamageExposure.withinCap(12.5, 9, CAP));
    }

    @Test
    void volleDeckungKostetNichts() {
        assertEquals(0.0, SelfDamageExposure.effective(9.0, 0));
        assertTrue(SelfDamageExposure.withinCap(9.0, 0, CAP));
    }

    @Test
    void anteilSkaliertDenSchadenLinear() {
        assertEquals(5.0, SelfDamageExposure.effective(9.0, 5), 1e-9);
        assertEquals(4.0, SelfDamageExposure.effective(9.0, 4), 1e-9);
        assertEquals(1.0, SelfDamageExposure.effective(9.0, 1), 1e-9);
    }

    @Test
    void anteilErlaubtMehrAlsDieBinaereRegel() {
        // Dieselbe Lage, die frueher jede Platzierung gekillt hat: 9 von 9 exponiert bei einem
        // Schaden von 5. Der Deckel steht auf 12, also ist das erlaubt.
        double base = 5.0;
        assertTrue(SelfDamageExposure.fraction(9) > 0.9);
        assertTrue(SelfDamageExposure.withinCap(base, 9, CAP));
    }

    @Test
    void anteilWirdGeklemmt() {
        assertEquals(0.0, SelfDamageExposure.fraction(-5));
        assertEquals(1.0, SelfDamageExposure.fraction(50));
        // Die zweite effective()-Ueberladung nimmt den ANTEIL, nicht die Stichprobenzahl:
        // 3.7 wird auf 1.0 geklemmt, der Schaden bleibt also voll.
        assertEquals(5.0, SelfDamageExposure.effective(5.0, 3.7));
        assertEquals(0.0, SelfDamageExposure.effective(5.0, -1.0));
    }

    @Test
    void keinSchadenBleibtKeinSchaden() {
        assertEquals(0.0, SelfDamageExposure.effective(0.0, 9));
        assertEquals(0.0, SelfDamageExposure.effective(-3.0, 9));
    }

    @Test
    void neunStichproben() {
        assertEquals(9, SelfDamageExposure.SAMPLE_COUNT);
        assertEquals(4.0 / 9.0, SelfDamageExposure.fraction(4), 1e-9);
    }

    /** Der Deckel muss weiterhin greifen - die Proportion ersetzt ihn nicht. */
    @Test
    void deckelWirktWeiterhin() {
        assertFalse(SelfDamageExposure.withinCap(30.0, 9, CAP));
        assertFalse(SelfDamageExposure.withinCap(20.0, 8, CAP));
    }
}