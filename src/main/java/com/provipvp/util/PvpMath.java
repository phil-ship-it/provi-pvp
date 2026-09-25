package com.provipvp.util;

import net.minecraft.world.phys.Vec3;
import java.util.function.BiPredicate;

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

    /** Ankunft einer simulierten Perle an der Zieldistanz, alles relativ zum Wurfpunkt:
     *  @param height   Hoehe ueber dem Wurfpunkt
     *  @param lateral  Seitenversatz quer zur Ziellinie (positiv = links davon, siehe Kreuzprodukt unten)
     *  @param ticks    interpolierter Ankunfts-Tick (Flugzeit) */
    public record PearlArrival(double height, double lateral, double ticks) {}

    /** Simuliert einen Perlenwurf nach Minecrafts eigener Projektil-Physik und liefert, wo die Perle
     *  die Ziel-Horizontaldistanz kreuzt. Pro Tick exakt in Vanilla-Reihenfolge fuer ThrowableProjectile:
     *  Schwerkraft (vy -= 0.03), dann Luftwiderstand (v *= 0.99), dann Bewegung (pos += v) - bestaetigt
     *  an Meteors eigenem ProjectileEntitySimulator ("gravity -> drag -> position").
     *
     *  extraVel ist die EIGENBEWEGUNG des Werfers, die Minecraft beim Abwurf auf die Startgeschwindigkeit
     *  addiert (Projectile#shootFromRotation, danach {@code add(shooter.getKnownMovement())}, Y nur wenn
     *  der Werfer nicht am Boden steht). Genau dieser Term fehlte hier bisher: der Bot wirft praktisch
     *  immer im Sprint (~0.15-0.30 Bloecke/Tick) gegen eine Perlengeschwindigkeit von 1.5 - das sind bis
     *  zu 20% Zusatzgeschwindigkeit QUER zur Zielrichtung. Numerisch nachgerechnet ueber 300 Zufallsfaelle
     *  mit Sprinttempo: mittlerer Zielfehler 2.68 Bloecke, 82% der Wuerfe mehr als einen Block daneben.
     *
     *  Der Fortschritt wird auf die Ziellinie projiziert (Skalarprodukt), der Querversatz ueber das
     *  Kreuzprodukt bestimmt - so liefert eine einzige Simulation beide Fehlerkomponenten, die
     *  {@link #solvePearlAim} dann getrennt auf Pitch und Yaw zurueckrechnet.
     *  @return null, wenn die Perle die Distanz in 300 Ticks nie erreicht (viel zu steil geworfen). */
    public static PearlArrival simulatePearl(double yaw, double pitch, Vec3 extraVel,
                                             double targetDistXZ, double dirX, double dirZ) {
        if (!Double.isFinite(targetDistXZ) || targetDistXZ <= 0) return null;
        double dirLength = Math.hypot(dirX, dirZ);
        if (dirLength < 1e-9) return null;
        dirX /= dirLength;
        dirZ /= dirLength;

        double yawRad = Math.toRadians(yaw), pitchRad = Math.toRadians(pitch);
        double vx = -Math.sin(yawRad) * Math.cos(pitchRad);
        double vy = -Math.sin(pitchRad);
        double vz = Math.cos(yawRad) * Math.cos(pitchRad);
        double len = Math.sqrt(vx * vx + vy * vy + vz * vz);
        vx = vx / len * 1.5 + extraVel.x;
        vy = vy / len * 1.5 + extraVel.y;
        vz = vz / len * 1.5 + extraVel.z;

        double x = 0, y = 0, z = 0;
        for (int tick = 0; tick < 300; tick++) {
            double prevX = x, prevY = y, prevZ = z;
            double prevProgress = prevX * dirX + prevZ * dirZ;

            vy -= 0.03;
            vx *= 0.99;
            vy *= 0.99;
            vz *= 0.99;
            x += vx;
            y += vy;
            z += vz;

            double progress = x * dirX + z * dirZ;
            if (progress >= targetDistXZ) {
                double frac = progress > prevProgress ? (targetDistXZ - prevProgress) / (progress - prevProgress) : 1.0;
                double hx = prevX + (x - prevX) * frac;
                double hy = prevY + (y - prevY) * frac;
                double hz = prevZ + (z - prevZ) * frac;
                return new PearlArrival(hy, hx * dirZ - hz * dirX, tick + frac);
            }
        }
        return null;
    }

    /** Prueft die berechnete Flugbahn abschnittsweise gegen ein frei uebergebenes Kollisions- oder
     *  Sichtbarkeitspruefverfahren. Dadurch bleibt die Ballistik rein, waehrend das Modul pro Tick
     *  Bloecke, Wasser und die tatsaechliche Umgebung des Werfers einbeziehen kann. */
    public static boolean trajectoryClear(Vec3 origin, double yaw, double pitch, Vec3 extraVel,
                                          BiPredicate<Vec3, Vec3> segmentClear, int maxTicks) {
        if (origin == null || extraVel == null || segmentClear == null || maxTicks <= 0) return false;

        double yawRad = Math.toRadians(yaw), pitchRad = Math.toRadians(pitch);
        double vx = -Math.sin(yawRad) * Math.cos(pitchRad);
        double vy = -Math.sin(pitchRad);
        double vz = Math.cos(yawRad) * Math.cos(pitchRad);
        double len = Math.sqrt(vx * vx + vy * vy + vz * vz);
        if (len < 1e-9) return false;
        vx = vx / len * 1.5 + extraVel.x;
        vy = vy / len * 1.5 + extraVel.y;
        vz = vz / len * 1.5 + extraVel.z;

        double x = 0, y = 0, z = 0;
        Vec3 previous = origin;
        for (int tick = 0; tick < maxTicks; tick++) {
            vy -= 0.03;
            vx *= 0.99;
            vy *= 0.99;
            vz *= 0.99;
            x += vx;
            y += vy;
            z += vz;
            Vec3 current = origin.add(x, y, z);
            if (!segmentClear.test(previous, current)) return false;
            previous = current;
        }
        return true;
    }

    /** Loest Yaw UND Pitch fuer einen Perlenwurf auf einen Punkt, inklusive der Eigenbewegung des
     *  Werfers. Pitch kommt aus einer Bisektion ueber die Ankunftshoehe (die faellt monoton mit
     *  steigendem Pitch), der Yaw aus dem verbleibenden Querversatz - beides abwechselnd, bis der
     *  Restfehler verschwindet (konvergiert numerisch in 2-3 Runden; ueber 300 Zufallsfaelle mit
     *  Sprinttempo blieb der groesste Restfehler bei 0.02 Bloecken).
     *
     *  Nur den Pitch zu loesen reicht NICHT, sobald der Werfer sich bewegt: die Eigenbewegung kippt die
     *  Flugbahn auch seitlich weg, dagegen hilft ausschliesslich eine Yaw-Korrektur.
     *  @return {yaw, pitch, Flugzeit in Ticks} oder null, wenn das Ziel physisch nicht erreichbar ist. */
    public static double[] solvePearlAim(Vec3 from, Vec3 to, Vec3 extraVel) {
        if (from == null || to == null || extraVel == null) return null;
        double dx = to.x - from.x, dz = to.z - from.z, dy = to.y - from.y;
        double distXZ = Math.hypot(dx, dz);
        if (!Double.isFinite(distXZ) || distXZ < 1e-6) return null;

        double dirX = dx / distXZ, dirZ = dz / distXZ;
        double yaw = Math.toDegrees(Math.atan2(-dx, dz));
        double pitch = Math.toDegrees(-Math.atan2(dy, distXZ));

        for (int round = 0; round < 8; round++) {
            // Pitch ist auf [-90,90] begrenzt; die Ankunftshoehe faellt monoton mit steigendem Pitch,
            // deshalb reicht eine simple Bisektion ueber den gesamten erlaubten Bereich.
            double lo = -89, hi = 89;
            for (int i = 0; i < 40; i++) {
                double mid = (lo + hi) / 2;
                PearlArrival probe = simulatePearl(yaw, mid, extraVel, distXZ, dirX, dirZ);
                // "Distanz nie erreicht" heisst zu steil nach oben geworfen - zaehlt wie zu hoch.
                if (probe == null || probe.height() > dy) lo = mid; else hi = mid;
            }
            pitch = (lo + hi) / 2;
            PearlArrival hit = simulatePearl(yaw, pitch, extraVel, distXZ, dirX, dirZ);
            if (hit == null) return null;
            // Selbst der steilste erlaubte Wurf erreicht die Zielhoehe nicht (nahe und sehr hoch) -
            // lieber die Perle sparen als sicher danebenwerfen.
            if (Math.abs(hit.height() - dy) > 1.0) return null;
            if (Math.abs(hit.lateral()) < 0.02) break;
            yaw += Math.toDegrees(Math.atan2(hit.lateral(), Math.max(distXZ, 1e-6)));
        }
        PearlArrival finalHit = simulatePearl(yaw, pitch, extraVel, distXZ, dirX, dirZ);
        return finalHit == null ? null : new double[] { yaw, pitch, finalHit.ticks() };
    }
}
