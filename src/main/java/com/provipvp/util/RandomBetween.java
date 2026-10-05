package com.provipvp.util;

import java.util.Random;

/** Min/Max-Werteintervalle als unveraenderliche Wertobjekte — der Baustein, mit dem aus jedem festen
 *  Timing-Wert (Delay, Cooldown, CPS, Reaktionszeit) ein vom Nutzer einstellbarer Bereich wird.
 *
 *  <p>Warum ueberhaupt: bislang stand ueberall {@code 15 + rng.nextInt(15)} fest im Code. Die Spanne
 *  war damit weder vom Nutzer aenderbar noch einheitlich — und genau die Gleichmaessigkeit des
 *  Rhythmus ist das, was einen Bot von einem Menschen unterscheidt. Mit diesen Objekten wird aus
 *  jedem solchen Ausdruck eine Einstellung, ohne dass die Aufrufstelle ihre Zufallsquelle verlieren
 *  muss ({@code sample(rng)} bekommt das {@link Random} uebergeben, es wird keins erzeugt — sonst
 *  waere das Ergebnis nicht reproduzierbar).
 *
 *  <p>Beide Varianten normalisieren vertauschte Grenzen ({@code (10, 3)} wird zu {@code (3, 10)}) und
 *  liefern bei {@code min == max} konstant diesen einen Wert. Damit kann ein Aufrufer ein
 *  Null-Intervall nicht von einem Fehler unterscheiden und braucht keine Sonderbehandlung.
 *
 *  <p>Ohne jeden {@code mc.*}-Zugriff (siehe {@code PvpMathTest}), damit direkt testbar.
 *
 *  <p>{@link RandomBetweenInt} und {@link RandomBetweenDouble} liegen als verschachtelte Records in
 *  dieser Huelle-Utility, analog {@code PvpMath.PearlArrival}.
 */
public final class RandomBetween {
    private RandomBetween() {}

    /** Ganzzahliges Intervall, beide Grenzen inklusiv. */
    public record RandomBetweenInt(int min, int max) {
        /** Vertauschte Grenzen werden still normalisiert: eine Einstellung "3-10" darf nicht
         *  stillschweigend zu einer leeren Menge fuehren, nur weil der Nutzer die Werte vertauscht hat. */
        public RandomBetweenInt {
            if (min > max) {
                int swap = min;
                min = max;
                max = swap;
            }
        }

        /** @param random die Aufrufer-Zufallsquelle; {@code null} ist ein Programmierfehler
         *  @return eine Zahl in {@code [min, max]} — bei {@code min == max} immer exakt dieser Wert */
        public int sample(Random random) {
            // Die Spannweite als long, damit das +1 bei MIN/MAX-Werten nicht ueberlaeuft und
            // nextInt() eine negative Spanne bekommt. nextDouble()*spanne ist fuer jede Spanne bis
            // 2^32 exakt gleichverteilt (53-Bit-Mantisse) — der Integer-Zweig waere hier nicht
            // genauer, nur komplizierter.
            long span = (long) max - min + 1L;
            return (int) (min + (long) (random.nextDouble() * span));
        }
    }

    /** Reales Interkmal, {@code [min, max]} mit Untergrenze inklusiv, Obergrenze exklusiv im
     *  Sinne von {@link Random#nextDouble()} — reicht fuer normalisierte Anteile (Smoothing-Faktor,
     *  Jitter-Anteil, Wahrscheinlichkeiten). */
    public record RandomBetweenDouble(double min, double max) {
        /** Vertauschte Grenzen werden still normalisiert, siehe {@link RandomBetweenInt}. */
        public RandomBetweenDouble {
            if (min > max) {
                double swap = min;
                min = max;
                max = swap;
            }
        }

        /** @return ein Wert in {@code [min, max]} — bei {@code min == max} immer exakt dieser Wert */
        public double sample(Random random) {
            // Ohne Sonderfall fuer min == max: (max - min) ist dann 0.0, damit ist das Ergebnis
            // exakt min statt "fast" min.
            return min + random.nextDouble() * (max - min);
        }
    }
}
