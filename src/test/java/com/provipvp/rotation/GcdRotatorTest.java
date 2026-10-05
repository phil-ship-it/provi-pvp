package com.provipvp.rotation;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Deckt die drei Entscheidungen ab, die Grims Rotationspruefungen erzwingen: liegt die gesendete
 * Rotation auf dem Maus-Gitter, wiederholt sich nie ein Delta, und wird nie gesprungen.
 */
class GcdRotatorTest {

    /** sensitivity 0.5 -> Divisor 0.0751, ein realistischer Mittelwert. */
    private static final double DIVISOR = GcdRotator.sensitivityDivisor(0.5);

    @Test
    void quantizedAngleIsALegalMultipleOfTheDivisor() {
        GcdRotator.AngleDeltaAccumulator acc = new GcdRotator.AngleDeltaAccumulator();
        acc.seed(12.0, 0.0, DIVISOR);

        double[] desiredYaws = {0.0, 45.0, 90.0, 137.3, -179.9, 179.9, 270.0, -300.0};
        double[] desiredPitches = {0.0, -12.5, 30.0, -88.2, 89.7, 4.0, -45.0, 61.0};

        for (int i = 0; i < desiredYaws.length; i++) {
            GcdRotator.Rotation sent = acc.quantize(desiredYaws[i], desiredPitches[i],
                DIVISOR, 0.4, new Random(i));

            assertEquals(Math.rint(sent.yaw() / DIVISOR), sent.yaw() / DIVISOR, 1e-6,
                "Yaw " + sent.yaw() + " liegt nicht auf dem Gitter");
            assertEquals(Math.rint(sent.pitch() / DIVISOR), sent.pitch() / DIVISOR, 1e-6,
                "Pitch " + sent.pitch() + " liegt nicht auf dem Gitter");

            // Der Delta ist die Groesse, die Grim sieht - er muss es genauso sein.
            assertEquals(Math.rint(acc.lastYawDelta() / DIVISOR), acc.lastYawDelta() / DIVISOR, 1e-6,
                "Yaw-Delta liegt nicht auf dem Gitter");
        }
    }

    @Test
    void consecutivePlacementsNeverShareAnIdenticalDelta() {
        for (double jitter : new double[]{0.0, 0.35}) {
            GcdRotator.AngleDeltaAccumulator acc = new GcdRotator.AngleDeltaAccumulator();
            Random rng = new Random(20260930L);
            acc.seed(0.0, 0.0, DIVISOR);

            double previous = Math.abs(acc.lastYawDelta());
            for (int placement = 0; placement < 40; placement++) {
                acc.quantize(84.0, 12.0, DIVISOR, jitter, rng);

                double current = Math.abs(acc.lastYawDelta());
                assertTrue(Math.abs(current - previous) > GcdRotator.DUPLICATE_EPSILON,
                    "Delta wiederholt sich bei Platzierung " + placement
                        + " (jitter " + jitter + "): vorher " + previous + ", jetzt " + current);
                previous = current;
            }
        }
    }

    @Test
    void snapLimiterNeverEmitsADeltaOverTheThreshold() {
        double[] froms = {0.0, 90.0, 179.0, -179.0, 250.0, 359.0, -0.4};
        double[] tos = {180.0, -180.0, 359.0, 1.0, -45.0, 270.0, 179.5, 0.0};

        for (double from : froms) {
            for (double to : tos) {
                double eased = GcdRotator.easeYaw(from, to, GcdRotator.MAX_YAW_STEP);
                assertTrue(Math.abs(eased - from) <= GcdRotator.MAX_YAW_STEP + 1e-9,
                    "Sprung von " + from + " nach " + to + " ergab " + Math.abs(eased - from));

                // Auch ein engeres Limit muss greifen - genau das ist der testbare Unterschied
                // zwischen "nimmt den kurzen Weg" und "nimmt irgendeinen Weg".
                double capped = GcdRotator.easeYaw(from, to, 25.0);
                assertTrue(Math.abs(capped - from) <= 25.0 + 1e-9,
                    "Kappung auf 25 Grad wurde ueberschritten: " + (capped - from));
            }
        }
    }

    @Test
    void snapLimiterTakesTheShortWayAroundTheBack() {
        // 179 -> -179 sind fuer einen Menschen zwei Grad, fuer ein naives Abs(subtrahieren) 358.
        double eased = GcdRotator.easeYaw(179.0, -179.0, GcdRotator.MAX_YAW_STEP);
        assertEquals(2.0, eased - 179.0, 1e-9);
    }

    @Test
    void accumulatorNeverSnapsMoreThanTheThresholdEvenForTargetsBehind() {
        GcdRotator.AngleDeltaAccumulator acc = new GcdRotator.AngleDeltaAccumulator();
        acc.seed(0.0, 0.0, DIVISOR);

        double[] targets = {180.0, -170.0, 175.0, -178.0, 179.0};
        for (double target : targets) {
            acc.quantize(target, 0.0, DIVISOR, 0.0, new Random(11));
            assertTrue(Math.abs(acc.lastYawDelta()) <= GcdRotator.MAX_YAW_STEP,
                "Ziel " + target + " ergab einen Sprung von " + acc.lastYawDelta());
        }
    }

    @Test
    void pitchIsClampedToTheVanillaLimitWithoutLeavingTheLattice() {
        GcdRotator.AngleDeltaAccumulator acc = new GcdRotator.AngleDeltaAccumulator();
        acc.seed(0.0, 0.0, DIVISOR);

        // Der Bot zielt auf ein Ziel am Boden; 120 Grad als Wunsch ist unmoeglich.
        GcdRotator.Rotation sent = acc.quantize(0.0, 120.0, DIVISOR, 0.0, new Random(5));
        assertTrue(sent.pitch() <= 90.0, "Pitch " + sent.pitch() + " ueberschreitet das Limit");
        assertEquals(Math.rint(sent.pitch() / DIVISOR), sent.pitch() / DIVISOR, 1e-6);
    }
}