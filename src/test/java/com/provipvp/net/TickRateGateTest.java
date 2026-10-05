package com.provipvp.net;

import com.provipvp.net.TickRateGate.Verdict;
import org.junit.jupiter.api.Test;

import static com.provipvp.net.TickRateGate.NOMINAL;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Deckt die Lag-Sperre ab: bei einem Spike frisst der Bot sonst Gratis-Treffer, weil die i-Frames
 * des Servers laenger dauern als die sichtbare Animation des Bots.
 */
class TickRateGateTest {

    private final TickRateGate gate = new TickRateGate();

    @Test
    void aLagSpikeBlocksActions() {
        assertTrue(gate.evaluate(4.0, 1.0) == Verdict.BLOCK,
            "ein Spike von 4x Normal muss alles blockieren - unabhaengig vom HP");
        assertFalse(gate.allowsAnyAction(gate.evaluate(4.0, 1.0)));
        assertFalse(gate.allows(Verdict.BLOCK, 0));
    }

    @Test
    void nominalRateWithHealthyHealthDoesNotBlock() {
        assertTrue(gate.evaluate(NOMINAL, 1.0) == Verdict.RUN,
            "1.0 bei vollen HP ist Normalbetrieb - es darf nichts gesperrt werden");
        assertTrue(gate.allows(gate.evaluate(NOMINAL, 1.0), 7),
            "im Normalbetrieb ist jede Aktion erlaubt, auch bei einem ungeraden Aktionszaehler");
    }

    @Test
    void lowHealthAloneDoesNotBlockAtNominalRate() {
        // Die HP-Schwelle ist eine Lag-Sicherung, kein Generalschalter: bei 3 HP auf sauberem
        // Server muss der Bot weiter kaempfen, sonst verliert er den Kampf im Stehen.
        assertTrue(gate.evaluate(NOMINAL, 0.15) == Verdict.RUN,
            "niedriges HP bei sauberem Server darf den Bot nicht stilllegen");
    }

    @Test
    void mildLagThrottlesInsteadOfBlocking() {
        Verdict verdict = gate.evaluate(1.5, 1.0);

        assertTrue(verdict == Verdict.THROTTLE, "milde Verzoegerung bei vollen HP wird gedrosselt, nicht blockiert");
        assertTrue(gate.allows(verdict, 0), "jede zweite Aktion muss durchgelassen werden");
        assertFalse(gate.allows(verdict, 1), "die dazwischen liegende Aktion wird geschluckt");
        assertTrue(gate.allows(verdict, 2), "die Drosselung wiederholt sich");
    }

    @Test
    void mildLagWithLowHealthBlocks() {
        // Der Kern der Regel: dieselbe Verzoegerung ist bei 20 HP unkritisch und bei 3 HP ein
        // kostenloser Treffer fuer den Angreifer.
        assertTrue(gate.evaluate(1.5, 0.10) == Verdict.BLOCK,
            "bei knappen HP wird im Lag nicht mehr gedaempft, sondern ganz gestoppt");
    }

    @Test
    void theLagThresholdIsStrictlyBelowTheSpikeThreshold() {
        // Sonst wuerde der konstruierte Drosselbereich leer und die zweite Schwelle toter Code sein.
        assertTrue(TickRateGate.DEFAULT_SPIKE_THRESHOLD > TickRateGate.DEFAULT_LAG_THRESHOLD);
        assertTrue(TickRateGate.DEFAULT_LAG_THRESHOLD > NOMINAL,
            "die Lagschwelle muss ueber dem Normalwert liegen, sonst waere der Server immer gehaemmt");
        assertTrue(TickRateGate.DEFAULT_LOW_HEALTH_FRACTION > 0.0
                && TickRateGate.DEFAULT_LOW_HEALTH_FRACTION < 1.0,
            "die HP-Schwelle muss eine echte Teilmenge des Lebensbereichs sein");
    }

    @Test
    void customThresholdsAreHonoured() {
        // Der Bot soll die Schwelle an die Serverlage anpassen koennen (2b2t laeuft anders als donutsmp).
        TickRateGate strict = new TickRateGate(1.05, 1.5, 0.8, 4);

        assertTrue(strict.evaluate(1.1, 1.0) == Verdict.THROTTLE, "1.1 ist bei 1.05 bereits Lag");
        assertTrue(strict.evaluate(1.1, 0.5) == Verdict.BLOCK, "0.5 HP-Fraktion liegt unter der 0.8-Schwelle");
        assertTrue(strict.evaluate(1.5, 1.0) == Verdict.BLOCK, "1.5 erreicht die harte Schwelle");

        Verdict throttled = strict.evaluate(1.1, 1.0);
        assertFalse(strict.allows(throttled, 1));
        assertFalse(strict.allows(throttled, 2));
        assertFalse(strict.allows(throttled, 3));
        assertTrue(strict.allows(throttled, 4), "bei throttleEvery=4 kommt nur jede vierte Aktion durch");
    }
}