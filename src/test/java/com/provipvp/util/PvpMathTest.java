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

    // ---------- simulatePearlHeightAt ----------

    @Test
    void simulatePearlHeightAtLevelThrowFallsBelowStart() {
        // Waagerecht geworfen (pitch=0) faellt eine Perle unter Wurfhoehe, sobald sie ueberhaupt eine
        // spuerbare Distanz erreicht - reine Schwerkraftwirkung ohne Aufwaertskomponente.
        double height = PvpMath.simulatePearlHeightAt(0, 0, 10, null);
        assertFalse(Double.isNaN(height));
        assertTrue(height < 0, "Waagerechter Wurf sollte unter Starthoehe landen, war " + height);
    }

    @Test
    void simulatePearlHeightAtSteeperPitchLandsHigher() {
        // Ein steilerer (weiter nach oben gerichteter, also negativerer Pitch in Minecrafts Konvention)
        // Wurf muss bei GLEICHER horizontaler Zieldistanz hoeher landen als ein flacherer.
        double flat = PvpMath.simulatePearlHeightAt(0, -10, 15, null);
        double steep = PvpMath.simulatePearlHeightAt(0, -40, 15, null);
        assertFalse(Double.isNaN(flat));
        assertFalse(Double.isNaN(steep));
        assertTrue(steep > flat, "steep=" + steep + " sollte > flat=" + flat + " sein");
    }

    @Test
    void simulatePearlHeightAtReturnsNaNWhenDistanceUnreachable() {
        // Senkrecht nach oben (pitch=-90) hat keinerlei horizontale Geschwindigkeitskomponente -
        // jede horizontale Zieldistanz > 0 ist unerreichbar.
        double height = PvpMath.simulatePearlHeightAt(0, -90, 20, null);
        assertTrue(Double.isNaN(height));
    }

    @Test
    void simulatePearlHeightAtWritesArrivalTicksWhenReached() {
        double[] arrival = {-1};
        double height = PvpMath.simulatePearlHeightAt(0, 0, 5, arrival);
        assertFalse(Double.isNaN(height));
        assertTrue(arrival[0] > 0, "Ankunfts-Tick sollte positiv sein, war " + arrival[0]);
    }

    @Test
    void simulatePearlHeightAtWritesSaturatedArrivalTicksWhenUnreached() {
        double[] arrival = {-1};
        double height = PvpMath.simulatePearlHeightAt(0, -90, 20, arrival);
        assertTrue(Double.isNaN(height));
        assertEquals(300, arrival[0], 1e-9);
    }

    // ---------- solvePearlPitch ----------

    @Test
    void solvePearlPitchAtOwnFeetNeedsNoBallistics() {
        Vec3 from = new Vec3(0, 64, 0);
        Vec3 to = new Vec3(0.1, 64, 0.1); // < 0.5 Bloecke horizontale Distanz
        double pitch = PvpMath.solvePearlPitch(from, 0, to, null);
        assertFalse(Double.isNaN(pitch));
    }

    @Test
    void solvePearlPitchOnSameLevelIsShallowNegative() {
        // Ziel auf gleicher Hoehe, normale Wurfdistanz: die geloeste Ballistik-Pitch muss leicht nach
        // oben zeigen (negativ in Minecrafts Konvention), um den Hoehenverlust durch Schwerkraft
        // waehrend des Flugs auszugleichen - nicht einfach der direkte 0-Grad-Blick.
        Vec3 from = new Vec3(0, 64, 0);
        Vec3 to = new Vec3(12, 64, 0);
        double pitch = PvpMath.solvePearlPitch(from, 0, to, null);
        assertFalse(Double.isNaN(pitch));
        assertTrue(pitch < 0, "Pitch sollte negativ (nach oben) sein, war " + pitch);
    }

    @Test
    void solvePearlPitchResultActuallyLandsAtTarget() {
        // End-zu-Ende-Check: die geloeste Pitch, erneut durch simulatePearlHeightAt gejagt, muss
        // tatsaechlich nahe der Ziel-Hoehendifferenz landen (bisektionsbedingte Toleranz).
        Vec3 from = new Vec3(0, 70, 0);
        Vec3 to = new Vec3(0, 65, 18); // 5 Bloecke tiefer, 18 Bloecke entfernt
        double yaw = 0;
        double pitch = PvpMath.solvePearlPitch(from, yaw, to, null);
        assertFalse(Double.isNaN(pitch));

        double landedHeight = PvpMath.simulatePearlHeightAt(yaw, pitch, 18, null);
        double expectedDy = to.y - from.y;
        assertEquals(expectedDy, landedHeight, 0.5, "geloeste Pitch landet nicht nahe der Zielhoehe");
    }

    @Test
    void solvePearlPitchReturnsNaNWhenTargetPhysicallyUnreachable() {
        // Extrem hoch UND extrem nah: selbst der steilste erlaubte Wurf (Pitch nahe -90) kann die
        // Zielhoehe bei dieser Distanz nicht erreichen.
        Vec3 from = new Vec3(0, 64, 0);
        Vec3 to = new Vec3(1, 200, 1);
        double pitch = PvpMath.solvePearlPitch(from, 0, to, null);
        assertTrue(Double.isNaN(pitch));
    }
}
