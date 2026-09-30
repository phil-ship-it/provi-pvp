package com.provipvp.pearl;

/** Ergebnis eines Perlwurfs: {@code {yaw, pitch, Flugzeit}}, das ausloesende Szenario und wie
 *  belastbar die Loesung ist.
 *
 *  <p>Bedeutung der Felder:
 *  <ul>
 *    <li>{@code yaw}/{@code pitch} - Grad-Werte im Minecraft-Konventionen (Pitch positiv = nach
 *        unten), direkt als Zielrotation verwendbar.</li>
 *    <li>{@code flightTicks} - interpolierte Flugzeit der Perle in Ticks, aus
 *        {@link com.provipvp.util.PvpMath}. Wird von der Ausfuehrungsschicht fuer Ping-Korrektur
 *        und Nachwurf-Planung gebraucht.</li>
 *    <li>{@code confident} - {@code true} heisst "gegen die Welt geprueft" (Sichtlinie bzw. Bahn
 *        frei), {@code false} heisst "bestmoegliche Naeherung ohne Verifikation" (z.B. der
 *        Fallback in {@link PearlSolver#terrainBypass}). Die Ausfuehrungsschult enthaelt KEINEN
 *        {@code pearlPitchVariance}-Streuwert: die Varianz gehoert in die Schicht, die den Wurf
 *        wirklich ausfuehrt, {@link PearlSolver} liefert ausschliesslich diesen reinen Zielwert.</li>
 *  </ul> */
public record PearlAim(double yaw, double pitch, int flightTicks,
                       PearlScenario scenario, boolean confident) {
}
