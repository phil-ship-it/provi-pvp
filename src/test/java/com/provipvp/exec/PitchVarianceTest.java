package com.provipvp.exec;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Deckt die Wurfwinkel-Streuung des Anti-Fall-Rettungswurfs ab. Die beiden Garantien, an denen
 *  ein Anti-Cheat haengt - der Wurf trifft nie den unveraenderten Basiswert und nie die exakte
 *  Senkrechte - sind hier ueber viele Ziehungen abgesichert, dazu die Spanne (inklusive
 *  Rueckfall bei {@code range <= 0}) und die Gleichverteilung. Alles ueber einen geseedeten
 *  {@link Random}, also deterministisch und ohne laufenden Client lauffaehig. */
class PitchVarianceTest {

    private static final int SAMPLES = 20_000;

    // ---------- nie der unveraenderte Basiswert ----------

    @Test
    void weichtVomBasiswertAb() {
        Random rng = new Random(1234L);
        for (int i = 0; i < SAMPLES; i++) {
            double pitch = PitchVariance.apply(88.5, 0.75, rng);
            assertNotEquals(88.5, pitch, "der Rettungswurf landete exakt auf dem Basiswert");
        }
    }

    @Test
    void weichtBeiAllenTiefenUndVorzeichenAb() {
        Random rng = new Random(99L);
        for (double base : new double[] {0.0, 12.5, -12.5, 45.0, -45.0, 88.5, -88.5, 90.0, -90.0}) {
            for (int i = 0; i < 200; i++) {
                assertNotEquals(base, PitchVariance.apply(base, 0.75, rng), "Basiswert " + base + " unveraendert");
            }
        }
    }

    // ---------- Spanne einhalten ----------

    @Test
    void bleibtInnerhalbDerSpanne() {
        Random rng = new Random(7L);
        for (double base : new double[] {0.0, 30.0, -30.0, 60.0, 88.5, -88.5, 90.0, -90.0}) {
            for (double range : new double[] {0.1, 0.75, 5.0}) {
                for (int i = 0; i < 500; i++) {
                    double pitch = PitchVariance.apply(base, range, rng);
                    assertTrue(Math.abs(pitch - base) <= range,
                        "Basis " + base + " Spanne " + range + " ergab " + pitch);
                }
            }
        }
    }

    @Test
    void faelltBeiKleinerSpanneAufDefaultZurueck() {
        Random rng = new Random(555L);
        for (double range : new double[] {0.0, -1.0, -99.0, Double.NaN, Double.POSITIVE_INFINITY}) {
            for (int i = 0; i < 1000; i++) {
                double pitch = PitchVariance.apply(88.5, range, rng);
                assertNotEquals(88.5, pitch, "range " + range + " lieferte den Basiswert");
                assertTrue(Math.abs(pitch - 88.5) <= PitchVariance.DEFAULT_RANGE,
                    "range " + range + " verliess die Default-Spanne: " + pitch);
            }
        }
    }

    // ---------- nie die exakte Senkrechte ----------

    @Test
    void trifftDieSenkrechteNieExakt() {
        Random rng = new Random(2024L);
        for (double base : new double[] {90.0, -90.0, 88.5, 89.0, 89.9, 0.0}) {
            for (int i = 0; i < SAMPLES; i++) {
                double pitch = PitchVariance.apply(base, 0.75, rng);
                assertNotEquals(90.0, pitch, "Basis " + base + " ergab exakt 90 Grad");
                assertNotEquals(-90.0, pitch, "Basis " + base + " ergab exakt -90 Grad");
            }
        }
    }

    @Test
    void verlaesstDenSendbarenPitchBereichNicht() {
        // Ueber +/- 90 hinaus wuerde der Client beim Senden selbst auf die Senkrechte
        // klemmen - die Streuung waere dann wieder exakt und damit sinnlos.
        Random rng = new Random(31L);
        for (int i = 0; i < SAMPLES; i++) {
            double up = PitchVariance.apply(90.0, 2.0, rng);
            assertTrue(up > PitchVariance.VERTICAL_UP && up < PitchVariance.VERTICAL_DOWN, "oben: " + up);
            double down = PitchVariance.apply(-90.0, 2.0, rng);
            assertTrue(down > PitchVariance.VERTICAL_UP && down < PitchVariance.VERTICAL_DOWN, "unten: " + down);
        }
    }

    @Test
    void bleibtBeiBasisAmLimitUnterhalbDerGrenze() {
        // Ausgehend exakt von 90 Grad muss der Wurf zwingend darunter landen.
        Random rng = new Random(4242L);
        for (int i = 0; i < 1000; i++) {
            assertTrue(PitchVariance.apply(90.0, 0.75, rng) < 90.0, "Wurf auf oder ueber 90 Grad");
        }
    }

    // ---------- gleichverteilt, nicht normalverteilt ----------

    @Test
    void istGleichverteiltUmDenBasiswert() {
        Random rng = new Random(8L);
        double sum = 0;
        int below = 0;
        for (int i = 0; i < SAMPLES; i++) {
            double pitch = PitchVariance.apply(20.0, 1.0, rng);
            sum += pitch - 20.0;
            if (pitch < 20.0) below++;
        }
        // Erwartungswert und Vorzeichenhaeufigkeit der Gleichverteilung. Die
        // Abweichungsverteilung selbst prueft der Test darunter - eine Normalverteilung
        // haette zwar dasselbe Mittel, wuerde die Randbereiche aber nicht besetzen.
        assertTrue(Math.abs(sum / SAMPLES) < 0.03, "Mittel " + (sum / SAMPLES));
        assertTrue(below > SAMPLES / 2 - SAMPLES / 20 && below < SAMPLES / 2 + SAMPLES / 20,
            "Vorzeichen unausgewogen: " + below + "/" + SAMPLES);
    }

    @Test
    void nutztDieGanzeSpanne() {
        // Gegenprobe zur Gleichverteilung: die Randbereiche muessen vorkommen.
        Random rng = new Random(11L);
        int outer = 0;
        for (int i = 0; i < SAMPLES; i++) {
            if (Math.abs(PitchVariance.apply(0.0, 1.0, rng)) > 0.8) outer++;
        }
        assertTrue(outer > SAMPLES / 10, "Randbereiche kaum besetzt: " + outer + "/" + SAMPLES);
    }

    // ---------- Determinismus ----------

    @Test
    void haengtNurAmUebergebenenRandom() {
        Random a = new Random(4711L);
        Random b = new Random(4711L);
        for (int i = 0; i < 500; i++) {
            assertEquals(PitchVariance.apply(88.5, 0.75, a), PitchVariance.apply(88.5, 0.75, b),
                "gleicher Seed muss gleiche Wurfwinkel liefern");
        }
    }
}
