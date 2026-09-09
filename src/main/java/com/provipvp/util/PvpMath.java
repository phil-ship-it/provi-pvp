package com.provipvp.util;

import net.minecraft.world.phys.Vec3;

/** Reine, zustandslose Berechnungen ohne jeden Zugriff auf den laufenden Client (kein {@code mc.*}) -
 *  bewusst hier statt als private Methode in den Modulen, aus zwei Gruenden: (1) die Pfeil-/Pearl-
 *  Ballistik ({@link #simulatePearlHeightAt} / {@link #solvePearlPitch}) war zuvor identisch in
 *  GodmodePvP und HumanPvP dupliziert, (2) reine Funktionen ohne Spielzustand lassen sich ohne
 *  laufenden Minecraft-Client unit-testen. */
public final class PvpMath {
    private PvpMath() {}

    /** Normalisiert eine Winkeldifferenz auf [-180, 180). */
    public static float wrapDelta(float delta) {
        delta %= 360f;
        if (delta >= 180f) delta -= 360f;
        if (delta < -180f) delta += 360f;
        return delta;
    }

    public static float clampAbs(float v, float max) {
        return Math.max(-max, Math.min(max, v));
    }

    /** Simuliert einen Perlenwurf mit gegebenem Yaw/Pitch nach Minecrafts eigener Projektil-Physik
     *  (Richtungsvektor wie ThrowableProjectile#shootFromRotation, dann pro Tick: vy -= 0.03, v *= 0.99,
     *  pos += v) und liefert die Hoehe relativ zum Startpunkt, sobald die Perle horizontal targetDistXZ
     *  erreicht hat (zwischen den beiden umgebenden Ticks linear interpoliert). NaN, wenn sie die Distanz
     *  innerhalb von 300 Ticks (15s, weit jenseits jeder echten Wurfdistanz) nie erreicht.
     *  @param arrivalTicksOut optionaler 1-Element-Output fuer den (interpolierten) Ankunfts-Tick, oder null. */
    public static double simulatePearlHeightAt(double yaw, double pitch, double targetDistXZ, double[] arrivalTicksOut) {
        double yawRad = Math.toRadians(yaw), pitchRad = Math.toRadians(pitch);
        double vx = -Math.sin(yawRad) * Math.cos(pitchRad);
        double vy = -Math.sin(pitchRad);
        double vz = Math.cos(yawRad) * Math.cos(pitchRad);
        double len = Math.sqrt(vx * vx + vy * vy + vz * vz);
        vx = vx / len * 1.5;
        vy = vy / len * 1.5;
        vz = vz / len * 1.5;

        double x = 0, y = 0, z = 0;
        for (int tick = 0; tick < 300; tick++) {
            double prevDistXZ = Math.sqrt(x * x + z * z);
            double prevY = y;

            vy -= 0.03;
            vx *= 0.99;
            vy *= 0.99;
            vz *= 0.99;
            x += vx;
            y += vy;
            z += vz;

            double distXZ = Math.sqrt(x * x + z * z);
            if (distXZ >= targetDistXZ) {
                double frac = distXZ > prevDistXZ ? (targetDistXZ - prevDistXZ) / (distXZ - prevDistXZ) : 1.0;
                if (arrivalTicksOut != null) arrivalTicksOut[0] = tick + frac;
                return prevY + (y - prevY) * frac;
            }
        }
        if (arrivalTicksOut != null) arrivalTicksOut[0] = 300;
        return Double.NaN;
    }

    /** Simple "look directly at the target" pitch works fine up close, but a thrown Ender Pearl is a real
     *  projectile (power 1.5, gravity 0.03/tick, 0.99 air drag - see Meteor's own ProjectileEntitySimulator/
     *  Minecraft's ThrowableItemProjectile) - aimed dead-on at longer range it visibly falls short since
     *  gravity has more time to pull it down over the longer flight. Solves for the pitch that actually
     *  lands at the target's height by simulating Minecraft's own pearl physics and bisecting on it,
     *  instead of guessing a fixed arc offset. Falls back to the direct look-pitch if nothing in the
     *  bounded search range lands close (never happens in practice within pearl-gapclose's own range caps,
     *  purely a safety net).
     *  @param arrivalTicksOut optionaler 1-Element-Output fuer den Ankunfts-Tick bei finalem Pitch, oder null. */
    public static double solvePearlPitch(Vec3 from, double yaw, Vec3 to, double[] arrivalTicksOut) {
        double dx = to.x - from.x, dz = to.z - from.z;
        double distXZ = Math.sqrt(dx * dx + dz * dz);
        double dy = to.y - from.y;
        double directPitch = Math.toDegrees(-Math.atan2(dy, distXZ));
        if (distXZ < 0.5) { // praktisch am eigenen Fuss - keine Ballistik noetig
            if (arrivalTicksOut != null) arrivalTicksOut[0] = 0;
            return directPitch;
        }

        // Pitch ist auf [-90,90] begrenzt - ohne diese Klammer suchte die Bisektion bei sehr steilen
        // Wuerfen (Ziel hoch UND nah, z.B. gerade explosionsgeschleudert) in physisch unmoeglichem
        // Terrain jenseits von -90 Grad und lieferte einen voellig sinnlosen, viel zu flachen Pitch -
        // die Perle landete dann weit vor dem Ziel statt in dessen Naehe.
        double lo = Math.max(-89, directPitch - 40);
        double hi = directPitch;

        for (int i = 0; i < 40; i++) {
            double mid = (lo + hi) / 2;
            double heightAtDist = simulatePearlHeightAt(yaw, mid, distXZ, null);
            // NaN (Distanz nie erreicht, zu steil nach oben verschossen) zaehlt wie "deutlich zu hoch" -
            // also wie beim Ueberschiessen weniger Korrektur nach oben nehmen.
            boolean overshootsHeight = Double.isNaN(heightAtDist) || heightAtDist > dy;
            if (overshootsHeight) lo = mid; else hi = mid;
        }
        double finalPitch = (lo + hi) / 2;
        double landedHeight = simulatePearlHeightAt(yaw, finalPitch, distXZ, arrivalTicksOut);
        // Selbst der steilst erlaubte Wurf (Pitch nahe -90) erreicht die Zielhoehe nicht - das Ziel ist
        // bei dieser Distanz schlicht ausserhalb der physischen Reichweite einer Perle (z.B. gerade sehr
        // hoch explosionsgeschleudert, aber noch zu nah, um genug Anlauf fuer die Hoehe zu nehmen).
        if (Double.isNaN(landedHeight) || landedHeight < dy - 0.5) return Double.NaN;
        return finalPitch;
    }
}
