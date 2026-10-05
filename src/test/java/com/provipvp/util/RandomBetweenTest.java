package com.provipvp.util;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Deckt die Min/Max-Intervalle ab, die aus jedem festen Timing-Wert eine einstellbare Spanne machen.
 *  Geprueft wird jeweils eine beobachtbare Eigenschaft einer Stichprobe — die Schranken selbst,
 *  nicht irgendein Getter. */
class RandomBetweenTest {

    /** Fest get seedet: ein Fehler muss sich in jedem Lauf gleich zeigen. */
    private static final Random RNG = new Random(20260930L);

    @Test
    void intSampleStaysInsideTheInterval() {
        RandomBetween.RandomBetweenInt range = new RandomBetween.RandomBetweenInt(3, 12);
        for (int i = 0; i < 20_000; i++) {
            int value = range.sample(RNG);
            assertTrue(value >= 3 && value <= 12, "Wert ausserhalb [3,12]: " + value);
        }
    }

    @Test
    void intSampleReachesBothEnds() {
        RandomBetween.RandomBetweenInt range = new RandomBetween.RandomBetweenInt(3, 12);
        boolean sawMin = false, sawMax = false;
        for (int i = 0; i < 20_000; i++) {
            int value = range.sample(RNG);
            sawMin |= value == 3;
            sawMax |= value == 12;
        }
        // Beide Grenzen sind inklusiv - ein Intervall, das eine Seite nur selten erreicht, waere ein
        // Zufallsfehler, kein gewuenschtes "fast nie".
        assertTrue(sawMin, "Untergrenze 3 wurde nie gezogen");
        assertTrue(sawMax, "Obergrenze 12 wurde nie gezogen");
    }

    @Test
    void intSampleIsConstantForADegenerateInterval() {
        RandomBetween.RandomBetweenInt range = new RandomBetween.RandomBetweenInt(7, 7);
        for (int i = 0; i < 1000; i++) {
            assertEquals(7, range.sample(RNG));
        }
    }

    @Test
    void intIntervalNormalisesInvertedBounds() {
        RandomBetween.RandomBetweenInt range = new RandomBetween.RandomBetweenInt(12, 3);
        assertEquals(3, range.min());
        assertEquals(12, range.max());
        for (int i = 0; i < 5000; i++) {
            int value = range.sample(RNG);
            assertTrue(value >= 3 && value <= 12, "Wert ausserhalb [3,12]: " + value);
        }
    }

    @Test
    void intSampleSurvivesTheFullIntegerRange() {
        // Ein naives min + nextInt(max - min + 1) laeuft hier ueber und wirft - genau der Grund,
        // warum die Spannweite als long gerechnet wird.
        RandomBetween.RandomBetweenInt range =
            new RandomBetween.RandomBetweenInt(Integer.MIN_VALUE, Integer.MAX_VALUE);
        for (int i = 0; i < 1000; i++) {
            int value = range.sample(RNG);
            assertTrue(value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE);
        }
    }

    @Test
    void doubleSampleStaysInsideTheInterval() {
        RandomBetween.RandomBetweenDouble range = new RandomBetween.RandomBetweenDouble(0.28, 0.42);
        for (int i = 0; i < 20_000; i++) {
            double value = range.sample(RNG);
            assertTrue(value >= 0.28 && value <= 0.42, "Wert ausserhalb [0.28,0.42]: " + value);
        }
    }

    @Test
    void doubleSampleIsConstantForADegenerateInterval() {
        RandomBetween.RandomBetweenDouble range = new RandomBetween.RandomBetweenDouble(0.5, 0.5);
        for (int i = 0; i < 1000; i++) {
            assertEquals(0.5, range.sample(RNG));
        }
    }

    @Test
    void doubleIntervalNormalisesInvertedBounds() {
        RandomBetween.RandomBetweenDouble range = new RandomBetween.RandomBetweenDouble(0.42, 0.28);
        assertEquals(0.28, range.min());
        assertEquals(0.42, range.max());
        for (int i = 0; i < 5000; i++) {
            double value = range.sample(RNG);
            assertTrue(value >= 0.28 && value <= 0.42, "Wert ausserhalb [0.28,0.42]: " + value);
        }
    }

    @Test
    void doubleSampleSpansTheWholeWidth() {
        RandomBetween.RandomBetweenDouble range = new RandomBetween.RandomBetweenDouble(10.0, 20.0);
        double min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
        for (int i = 0; i < 20_000; i++) {
            double value = range.sample(RNG);
            min = Math.min(min, value);
            max = Math.max(max, value);
        }
        // Nur Schranken zu pruefen wuerde auch bei einem winzigen Intervall gruen sein - die Breite
        // ist das, was den Unterschied zu einer Konstante ausmacht.
        assertTrue(min < 10.5, "Untergrenze wird nicht erreicht: " + min);
        assertTrue(max > 19.5, "Obergrenze wird nicht erreicht: " + max);
    }
}
