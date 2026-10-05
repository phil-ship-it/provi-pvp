package com.provipvp.crystal;

/** Prueft eine geplante Platzierung gegen den EIGENEN Schaden, den die daraus folgende Explosion
 *  verursachen wuerde (D8).
 *
 *  <p>Der bisherige Code in beiden Modulen prueft ausschliesslich {@code projected > max-self-damage}.
 *  Das erlaubt genau die Platzierung, die das Feature verhindern soll: nahe am Gegner ist jede
 *  erreichbare Crystal-Zelle weit ausserhalb des (konservativ gewaehlten) Deckels, aber der
 *  projizierte Schaden kann trotzdem noch die eigene Gesamtlebensenergie erreichen — der Bot tauscht
 *  seinen Trade dann mit dem Tod. Ein Crystal-Pop macht im Nahbereich ein Vielfaches des Deckels
 *  (der Deckel steht absichtlich tief, weil er Crystal- UND Bett-Platzierungen gemeinsam begrenzt),
 *  "unter dem Deckel" ist damit keine Garantie fuer "nicht fatal".
 *
 *  <p>Deshalb die zweite, entscheidende Bedingung: {@code projectedSelfDamage >= getTotalHealth()}.
 *  {@code getTotalHealth()} und nicht {@code getHealth()}, weil Absorptionshauben mitzaehlen — wer
 *  gerade eine Haube auf dem Kopf hat, ist mit weniger EHP deutlich naeher am Tod, als die rote
 *  Lebensleiste suggeriert.
 *
 *  <p>Reine Zahleneingaben, kein {@code mc.*} und keine Settings (R1): Aufbau- und Deaktivieren der
 *  Hauben laufen im Modul, die Entscheidung hier.
 */
public final class SelfDamageGuard {
    private SelfDamageGuard() {}

    /** Warum eine Platzierung abgelehnt wurde — ein nacktes {@code false} laesst sich im Log nicht
     *  von "Deckel gerissen" und "wuerde mich toeten" unterscheiden, und genau diese Unterscheidung
     *  entscheidet, ob man den Deckel oder die Position aendern muss. */
    public enum Reason {
        /** Platzierung ist vertretbar. */
        OK,
        /** Ueber dem konfigurierten Deckel, aber noch nicht fatal. */
        OVER_CAP,
        /** Erreicht die eigene Gesamtlebensenergie — waere ein Selbstmord-Trade. */
        LETHAL
    }

    /**
     * @param allowed        darf die Platzierung ausgefuehrt werden
     * @param reason         die Abbruchursache zum Logging
     * @param projectedSelfDamage der gepruefte Schadenswert, im Log mitgefuehrt, damit man sieht
     *                          woran der Wert lag (nicht nur, dass er durchgefallen ist)
     */
    public record Verdict(boolean allowed, Reason reason, double projectedSelfDamage) {
        /** Kurzform fuer Aufrufer, die den Grund nicht loggen. */
        public boolean allowed() {
            return allowed;
        }
    }

    /**
     * @param projectedSelfDamage projizierter Schaden der Platzierung beim Spieler
     * @param maxSelfDamage        konfigurierter Deckel
     * @param playerTotalHealth    {@code getTotalHealth()} des Spielers (Leben + Absorption)
     * @return {@link Verdict} mit {@link Reason#OVER_CAP}, {@link Reason#LETHAL} oder {@link Reason#OK}
     */
    public static Verdict check(double projectedSelfDamage, double maxSelfDamage, double playerTotalHealth) {
        // LETHAL wird VOR dem Deckel geprueft: wenn beides greift, ist "wuerde mich toeten" die
        // Diagnose, die etwas aendert - der Deckel ist einstellbar, der Tote nicht.
        if (projectedSelfDamage >= playerTotalHealth) {
            return new Verdict(false, Reason.LETHAL, projectedSelfDamage);
        }
        if (projectedSelfDamage > maxSelfDamage) {
            return new Verdict(false, Reason.OVER_CAP, projectedSelfDamage);
        }
        return new Verdict(true, Reason.OK, projectedSelfDamage);
    }

    /** Kurzform von {@link #check} fuer Aufrufstellen, die nur ein Ja/Nein brauchen. */
    public static boolean allows(double projectedSelfDamage, double maxSelfDamage, double playerTotalHealth) {
        return check(projectedSelfDamage, maxSelfDamage, playerTotalHealth).allowed();
    }
}
