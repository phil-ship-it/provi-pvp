/** Schritt 4 — R3: JUnit-5-Test fuer reine, zustandslose PvpMath (kein mc.*, kein Modul). */
package com.provipvp.util;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PvpMathTest {
    @Test
    void wrapDeltaWrapsPositiveOver180() {
        assertEquals(-10f, PvpMath.wrapDelta(350f), 0.001f);
    }

    @Test
    void wrapDeltaWrapsNegativeUnderMinus180() {
        assertEquals(10f, PvpMath.wrapDelta(-350f), 0.001f);
    }

    @Test
    void clampAbsRespectsMax() {
        assertEquals(3.0, PvpMath.clampAbs(5.0, 3.0), 0.001);
    }

    @Test
    void simulatePearlReturnsNonNullForReasonablePitch() {
        // Reiner Funktions-Test ohne laufenden Client.
        var arrival = PvpMath.simulatePearl(0.0, -45.0, Vec3.ZERO, 20.0, 1.0, 0.0);
        assertNotNull(arrival);
        assertTrue(arrival.ticks() > 0);
    }

    @Test
    void solvePearlAtTargetCoarseToFineReturnsNotNull() {
        Vec3 from = new Vec3(0, 2, 0);
        Vec3 to = new Vec3(15, 3, 5);
        // R1: reine Funktion, kein mc.*; BiPredicate nur als Platzhalter (nicht vom Client zu laden)
        double[] result = PvpMath.solvePearlAtTarget(from, to, Vec3.ZERO,
            (a, b) -> true); // Kopf-Stub; echtes Segment-Check vom Modul bereitgestellt
        assertNotNull(result);
        assertEquals(2, result.length);
    }
    @Test
    void solvePearlAimReturnsNotNullForCloseTarget() {
        Vec3 from = new Vec3(0, 0, 0);
        Vec3 to = new Vec3(10, 2, 0);
        double[] result = PvpMath.solvePearlAim(from, to, Vec3.ZERO);
        assertNotNull(result);
        assertEquals(2, result.length); // yaw, pitch
    }
}
