package com.provipvp.exec;

import java.util.Random;

/** Zufallsstreuung des Anti-Fall-Rettungswurfs.
 *
 *  <p>Der Rettungswurf nach einer Explosion geht praktisch immer mit einem exakt berechneten,
 *  senkrechten Pitch (z.B. 88.5 Grad als Fallback-Ziel) raus. Ein Anti-Cheat, das nur auf
 *  Wurfwinkel schaut, erkennt daran eine Maschine: Menschen treffen keine Senkrechte auf
 *  zwei Nachkommastellen genau, schon gar nicht dreimal hintereinander. Deshalb wird der
 *  Basiswert vor dem Wurf um eine kleine, gleichverteilte Abweichung verschoben.
 *
 *  <p>Bewusst zustandslos und {@code static}, mit uebergebenem {@link Random}: so ist die
 *  Funktion ohne laufenden Minecraft-Client unit-testbar (gleiches Muster wie
 *  {@link com.provipvp.util.PvpMath}) und der Aufrufer bestimmt die Streuung ueber seine
 *  eigenen Einstellungen.
 *
 *  <p>Zwei Eigenschaften sind hart garantiert und gelten fuer jeden Eingabewert:
 *  <ul>
 *      <li>das Ergebnis ist <b>nie</b> exakt der Basiswert (sonst waere die ganze Streuung
 *          fuer die Nichtsnutzlose) und
 *      <li>das Ergebnis liegt <b>nie</b> auf bzw. jenseits der exakten Senkrechten
 *          (+/-90 Grad). Das ist nicht nur Anti-Cheat-Haeuflichkeit: ueberschritte
 *          Minecraft seinen eigenen Pitch-Limit nicht von selbst, klemmt der Client beim
 *          Senden auf 90 Grad - und damit waere die Senkrechte doch wieder exakt getroffen.
 *  </ul>
 *  Innerhalb von +/- {@code range} um den Basiswert liegt das Ergebnis in jedem Fall. */
public final class PitchVariance {
    private PitchVariance() {}

    /** Senkrechter Wurfwinkel nach unten - der maschinenperfekte Anti-Cheat-Fingerzeig. */
    public static final double VERTICAL_DOWN = 90.0;

    /** Senkrechter Wurfwinkel nach oben, siehe {@link #VERTICAL_DOWN}. */
    public static final double VERTICAL_UP = -90.0;

    /** Spanne, die greift, wenn {@code range <= 0} ist - der Rettungswurf wird nie
     *  ungestreut gefeuert, ein 0-Settingswert darf nicht zum exakten Winkel fuehren. */
    public static final double DEFAULT_RANGE = 0.75;

    /** Zaehlt, wie oft neu gewuerfelt wird, bevor der deterministische Ausweg greift. */
    private static final int MAX_ATTEMPTS = 8;

    /**
     * @param basePitch  berechneter Wurfwinkel in Grad
     * @param range      erlaubte Spanne +/- um den Basiswert; {@code <= 0} (oder nicht
     *                   endlich) faellt auf {@link #DEFAULT_RANGE} zurueck
     * @param rng        Quelle der Gleichverteilung, vom Aufrufer eingereicht
     * @return gestreuter Pitch: immer ungleich {@code basePitch}, immer in
     *         {@code (VERTICAL_UP, VERTICAL_DOWN)}, immer maximal {@code range} entfernt
     */
    public static double apply(double basePitch, double range, Random rng) {
        double r = (Double.isFinite(range) && range > 0.0) ? range : DEFAULT_RANGE;

        // u -> 2u-1 bildet [0,1) verlustfrei auf [-1,1) ab, die Skalierung mit r ergibt also
        // eine GLEICHVERTEILTE Abweichung in [-r, r) (kein Normalverteilungs-Sigma, das
        // wuerde die Haeufigkeit auf den Bereich um den Basisballen). u == 0.5 liefert exakt
        // 0.0 und damit den unveraenderten Basiswert - dieser Einzelfall wird auf +r
        // umgebogen. Weicht das Ergebnis trotzdem von der Senkrechten ab oder verlaesst es
        // den sendbaren Bereich, wird neu gewuerfelt (praktisch unmoeglich, aber die
        // Garantie soll nicht vom Zufallsgenerator abhaengen).
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            double offset = (2.0 * rng.nextDouble() - 1.0) * r;
            if (offset == 0.0) offset = r;
            double result = basePitch + offset;
            if (result != basePitch && result > VERTICAL_UP && result < VERTICAL_DOWN) return result;
        }

        // Alle Ziehungen fielen in die Sackgasse (praktisch nur bei nicht-endlichem
        // basePitch moeglich). Der Ausweg ist weiterhin gestreut und bleibt in +/- r.
        double fallback = basePitch + r;
        if (!(fallback > VERTICAL_UP && fallback < VERTICAL_DOWN)) fallback = basePitch - r;
        return fallback;
    }
}
