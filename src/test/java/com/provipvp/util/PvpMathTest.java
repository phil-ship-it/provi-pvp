package com.provipvp.util;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Deckt die reinen, zustandslosen Berechnungen in {@link PvpMath} ab - die einzige Logik in beiden
 *  PvP-Modulen, die ohne einen laufenden Minecraft-Client testbar ist (kein {@code mc.*}-Zugriff,
 *  keine Registries/Bootstrap noetig). Kampf-Entscheidungslogik wie
 *  {@code GodmodePvP#bestDamageAround}, {@code #validExplosionSpot} oder {@code #trackTotemEffect}
 *  bleibt bewusst ungetestet: sie liest direkt {@code mc.level}/{@code mc.player}, Block-Registries
 *  und den Entity-Effekt-Zustand und laesst sich ohne einen echten (oder aufwendig gemockten)
 *  Minecraft-Client nicht sinnvoll isolieren. */
class PvpMathTest {

    // ---------- wrapDelta ----------

    @Test
    void wrapDeltaLeavesSmallAnglesUnchanged() {
        assertEquals(0f, PvpMath.wrapDelta(0f), 1e-6);
        assertEquals(90f, PvpMath.wrapDelta(90f), 1e-6);
        assertEquals(-90f, PvpMath.wrapDelta(-90f), 1e-6);
    }

    @Test
    void wrapDeltaWrapsAboveHalfCircleNegative() {
        // 270 Grad "vorwaerts" ist derselbe Winkel wie 90 Grad "rueckwaerts"
        assertEquals(-90f, PvpMath.wrapDelta(270f), 1e-4);
        assertEquals(-1f, PvpMath.wrapDelta(359f), 1e-4);
    }

    @Test
    void wrapDeltaWrapsBelowNegativeHalfCirclePositive() {
        assertEquals(90f, PvpMath.wrapDelta(-270f), 1e-4);
        assertEquals(1f, PvpMath.wrapDelta(-359f), 1e-4);
    }

    @Test
    void wrapDeltaHandlesExactBoundary() {
        // [-180, 180) - +180 wird auf -180 gewrapt, -180 bleibt unveraendert
        assertEquals(-180f, PvpMath.wrapDelta(180f), 1e-6);
        assertEquals(-180f, PvpMath.wrapDelta(-180f), 1e-6);
    }

    @Test
    void wrapDeltaHandlesMultipleFullRotations() {
        assertEquals(30f, PvpMath.wrapDelta(750f), 1e-4); // 750 = 2*360 + 30
        assertEquals(-30f, PvpMath.wrapDelta(-750f), 1e-4);
    }

    // ---------- clampAbs ----------

    @Test
    void clampAbsPassesThroughWithinRange() {
        assertEquals(5f, PvpMath.clampAbs(5f, 10f), 1e-6);
        assertEquals(-5f, PvpMath.clampAbs(-5f, 10f), 1e-6);
    }

    @Test
    void clampAbsClampsPositiveOverflow() {
        assertEquals(10f, PvpMath.clampAbs(15f, 10f), 1e-6);
    }

    @Test
    void clampAbsClampsNegativeOverflow() {
        assertEquals(-10f, PvpMath.clampAbs(-15f, 10f), 1e-6);
    }

    @Test
    void clampAbsHandlesZeroMax() {
        assertEquals(0f, PvpMath.clampAbs(5f, 0f), 1e-6);
        assertEquals(0f, PvpMath.clampAbs(-5f, 0f), 1e-6);
    }

    // ---------- simulatePearl ----------

    @Test
    void simulatePearlLevelThrowFallsBelowStart() {
        // Waagerecht geworfen (pitch=0) faellt eine Perle unter Wurfhoehe, sobald sie ueberhaupt eine
        // spuerbare Distanz erreicht - reine Schwerkraftwirkung ohne Aufwaertskomponente.
        PvpMath.PearlArrival hit = PvpMath.simulatePearl(0, 0, Vec3.ZERO, 10, 0, 1);
        assertNotNull(hit);
        assertTrue(hit.height() < 0, "Waagerechter Wurf sollte unter Starthoehe landen, war " + hit.height());
    }

    @Test
    void simulatePearlSteeperPitchLandsHigher() {
        // Ein steilerer (weiter nach oben gerichteter, also negativerer Pitch in Minecrafts Konvention)
        // Wurf muss bei GLEICHER horizontaler Zieldistanz hoeher landen als ein flacherer.
        PvpMath.PearlArrival flat = PvpMath.simulatePearl(0, -10, Vec3.ZERO, 15, 0, 1);
        PvpMath.PearlArrival steep = PvpMath.simulatePearl(0, -40, Vec3.ZERO, 15, 0, 1);
        assertNotNull(flat);
        assertNotNull(steep);
        assertTrue(steep.height() > flat.height(),
            "steep=" + steep.height() + " sollte > flat=" + flat.height() + " sein");
    }

    @Test
    void simulatePearlAddsThrowerVelocity() {
        // Minecraft addiert die Eigenbewegung des Werfers auf die Perle - mit Rueckenwind ist sie an
        // derselben Zieldistanz frueher da als aus dem Stand.
        PvpMath.PearlArrival still = PvpMath.simulatePearl(0, 0, Vec3.ZERO, 15, 0, 1);
        PvpMath.PearlArrival running = PvpMath.simulatePearl(0, 0, new Vec3(0, 0, 0.28), 15, 0, 1);
        assertNotNull(still);
        assertNotNull(running);
        assertTrue(running.ticks() < still.ticks(),
            "mit Eigenbewegung sollte die Perle frueher ankommen: " + running.ticks() + " vs " + still.ticks());
    }

    @Test
    void simulatePearlReachesTargetDistanceAndReportsFlightTime() {
        PvpMath.PearlArrival hit = PvpMath.simulatePearl(0, 0, Vec3.ZERO, 5, 0, 1);
        assertNotNull(hit);
        assertTrue(hit.ticks() > 0, "Ankunfts-Tick sollte positiv sein, war " + hit.ticks());
    }

    @Test
    void simulatePearlReturnsNullWhenDistanceNeverReached() {
        // Senkrecht nach oben geworfen: die horizontale Zieldistanz wird nie erreicht.
        assertNull(PvpMath.simulatePearl(0, -90, Vec3.ZERO, 20, 0, 1));
    }

    // ---------- solvePearlAim ----------

    @Test
    void solvePearlAimAtOwnFeetNeedsNoBallistics() {
        double[] aim = PvpMath.solvePearlAim(new Vec3(0, 64, 0), new Vec3(0.1, 64, 0.1), Vec3.ZERO);
        assertNotNull(aim);
    }

    @Test
    void solvePearlAimOnSameLevelAimsUpwards() {
        // Ziel auf gleicher Hoehe, normale Wurfdistanz: der geloeste Pitch muss leicht nach oben zeigen
        // (negativ in Minecrafts Konvention), um den Hoehenverlust durch die Schwerkraft auszugleichen.
        double[] aim = PvpMath.solvePearlAim(new Vec3(0, 64, 0), new Vec3(12, 64, 0), Vec3.ZERO);
        assertNotNull(aim);
        assertTrue(aim[1] < 0, "Pitch sollte negativ (nach oben) sein, war " + aim[1]);
    }

    @Test
    void solvePearlAimLandsAtTargetWhileStandingStill() {
        Vec3 from = new Vec3(0, 70, 0);
        Vec3 to = new Vec3(0, 65, 18); // 5 Bloecke tiefer, 18 Bloecke entfernt
        double[] aim = PvpMath.solvePearlAim(from, to, Vec3.ZERO);
        assertNotNull(aim);

        PvpMath.PearlArrival hit = PvpMath.simulatePearl(aim[0], aim[1], Vec3.ZERO, 18, 0, 1);
        assertNotNull(hit);
        assertEquals(to.y - from.y, hit.height(), 0.1, "geloester Wurf landet nicht auf Zielhoehe");
        assertEquals(0, hit.lateral(), 0.1, "geloester Wurf hat Seitenversatz");
    }

    @Test
    void solvePearlAimCompensatesOwnMovement() {
        // Kern des behobenen Fehlers: Minecraft addiert die Eigenbewegung des Werfers auf die Perle.
        // Ein im Sprint quer zur Wurfrichtung geworfener Ball muss trotzdem ins Ziel gehen - deshalb
        // korrigiert der Loeser auch den Yaw, nicht nur den Pitch.
        Vec3 from = new Vec3(0, 64, 0);
        Vec3 to = new Vec3(0, 64, 20);
        Vec3 sprintSideways = new Vec3(0.28, 0, 0);

        double[] aim = PvpMath.solvePearlAim(from, to, sprintSideways);
        assertNotNull(aim);
        PvpMath.PearlArrival hit = PvpMath.simulatePearl(aim[0], aim[1], sprintSideways, 20, 0, 1);
        assertNotNull(hit);
        assertEquals(0, hit.height(), 0.1);
        assertEquals(0, hit.lateral(), 0.1, "Seitenversatz durch Eigenbewegung nicht auskorrigiert");

        // Gegenprobe: ohne Kompensation (direkt aufs Ziel gezielt) landet derselbe Wurf klar daneben.
        PvpMath.PearlArrival naive = PvpMath.simulatePearl(0, aim[1], sprintSideways, 20, 0, 1);
        assertNotNull(naive);
        assertTrue(Math.abs(naive.lateral()) > 1.0,
            "unkompensierter Wurf sollte deutlich daneben liegen, war " + naive.lateral());
    }

    @Test
    void solvePearlAimReturnsNullWhenTargetPhysicallyUnreachable() {
        // Extrem hoch UND extrem nah: selbst der steilste erlaubte Wurf erreicht die Zielhoehe nicht.
        assertNull(PvpMath.solvePearlAim(new Vec3(0, 64, 0), new Vec3(1, 200, 1), Vec3.ZERO));
    }
}
