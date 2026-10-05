package com.provipvp.crystal;

/** Drei voneinander unabhaengige Angriffs-Gates, jeweils eine reine Praedikatsfunktion ueber
 *  einfache Zahlen (D7, D9, D10).
 *
 *  <p>Die drei Bedingungen gehoeren inhaltlich nicht zusammen — sie messen drei verschiedene Dinge
 *  (Zielzustand, eigene Waffe, eigene Absicht) —, werden aber an denselben drei Stellen im Modul
 *  ausgewertet. Deshalb drei einzeln anwendbare Gates plus eine kombinierte Auswertung, und
 *  ausdruecklich KEIN {@code boolean}: wenn ein Angriff ausbleibt, muss das Log sagen warum. Sonst
 *  sieht ein stillstehender Bot nach perfektem Timing aus, und man sucht den Fehler in der Rotation
 *  statt im Cooldown.
 *
 *  <p>Die Reihenfolge der kombinierten Auswertung ist Teil der API: der erste verletzte Gate gewinnt
 *  und benennt damit den Grund. Wer das umdreht, laesst den interessanteren Grund verschlucken.
 *
 *  <p>Alle Werte kommen als Parameter herein (kein {@code mc.*}, keine Settings — R1); das Modul liest
 *  {@code target.hurtTime}, {@code self.getAttackStrengthScale(0.5f)}, {@code target.getHealth()} und
 *  den errechneten Angriffs-Schaden und reicht sie durch.
 */
public final class AttackGate {
    private AttackGate() {}

    /**
     * Angriffs-Cooldown vollstaendig aufgeladen, {@code getAttackStrengthScale(0.5f)}.
     *
     * <p>Der bisherige Code in beiden Modulen lockert auf 0.9 (GodmodePvP) bzw. 0.95 (HumanPvP), um
     * den Schlag nicht ganz zu verlieren, wenn der Cooldown knapp fehlt. Genau dieses "knapp fehlt"
     * ist der interessante Fall: der zweite Pop muss im 10-Tick-Fenster nach dem ersten liegen, und
     * in dieses Fenster faellt der eigene Angriffs-Cooldown zwangslaeufig mit ab. Ein Schlag auf
     * halb geladenem Cooldown ist aber kein "etwas schoenerer Treffer", sondern einer mit rund der
     * Haelfte des Schadens — der Cooldown des Gegners ist danach trotzdem verbraucht und der
     * Crystal laeuft weiter auf. Lieber gar nicht schwingen und den Cooldown fuer den richtigen
     * Moment aufheben.
     */
    public static final double FULL_STRENGTH = 1.0;

    /** Warum ein Gate nicht passiert hat. {@link #NONE} heisst: das Gate hat durchgelassen. */
    public enum Reason {
        /** Kein Gate hat blockiert. */
        NONE,
        /** D7: das Ziel ist in seiner Unverletzlichkeits-/Pop-Phase ({@code hurtTime > 0}). */
        TARGET_INVULNERABLE,
        /** D9: der eigene Angriffs-Cooldown ist noch nicht voll aufgeladen. */
        STRENGTH_NOT_READY,
        /** D10: ein Sprung-Crit waere nicht einmal der Tod des Ziels — also nicht springen. */
        CRIT_NOT_LETHAL
    }

    /**
     * @param allowed  das Gate hat durchgelassen
     * @param reason   Grund des Nicht-Durchlassens, {@link Reason#NONE} bei {@code allowed}
     * @param jumpCrit D10-Ergebnis: {@code true} heisst "sprung-Crit ist lohnend". Bei blockiertem
     *                 Angriff immer {@code false} — die Absicht wird nicht ausgefuehrt
     */
    public record Verdict(boolean allowed, Reason reason, boolean jumpCrit) {
        static Verdict pass() {
            return new Verdict(true, Reason.NONE, false);
        }

        static Verdict passWithCrit(boolean jumpCrit) {
            return new Verdict(true, Reason.NONE, jumpCrit);
        }

        static Verdict fail(Reason reason) {
            return new Verdict(false, reason, false);
        }
    }

    /**
     * Der komplette Zustand eines geplanten Schlags.
     *
     * @param targetHurtTime        {@code target.hurtTime} — Vanilla setzt 10 (0.5 s) nach Schaden
     * @param attackStrengthScale   {@code self.getAttackStrengthScale(0.5f)}
     * @param targetHealth          {@code target.getHealth()} fuer D10
     * @param attackDamage          geplanter Schaden dieses Schlags, unveraendert (ohne Crit-Bonus)
     */
    public record AttackState(int targetHurtTime, double attackStrengthScale,
                              double targetHealth, double attackDamage) {}

    /**
     * D7: waehrend der Unverletzlichkeitsphase des Ziels nicht angreifen.
     *
     * <p>Die 0.5 s (10 Ticks) sind genau das Fenster, in dem der Doppelpop-Cliff haengt: der erste
     * Crystal hat das Ziel gepoppt, jeder weitere Treffer darin wird vom Server verworfen. Der
     * zweite Pop muss also im Moment landen, in dem der Flash endet, nicht irgendwann darin. Mit
     * "hurtTime > 0 wird gewartet" faellt der Schlag von selbst auf den ersten Tick, an dem die
     * Phase vorbei ist, und geht nicht verloren, wenn man zu frueh geschossen haette.
     */
    public static Verdict gateInvulnerability(int targetHurtTime) {
        return targetHurtTime > 0 ? Verdict.fail(Reason.TARGET_INVULNERABLE) : Verdict.pass();
    }

    /**
     * D9: erst schwingen, wenn der Angriffs-Cooldown voll aufgeladen ist.
     *
     * <p>Ein Schlag auf halb geladenem Cooldown ist kein "etwas schoenerer Treffer", sondern ein
     * Treffer mit rund der Haelfte des Schadens — der Angriffs-Cooldown des Gegners ist danach
     * trotzdem verbraucht und der Crystal laeuft weiter auf. Der lostige Schlag kostet also einen
     * Pop und liefert den halben.
     */
    public static Verdict gateStrength(double attackStrengthScale, double minStrengthScale) {
        return attackStrengthScale >= minStrengthScale
            ? Verdict.pass()
            : Verdict.fail(Reason.STRENGTH_NOT_READY);
    }

    /**
     * D10: nur springen, wenn der Crit das Ziel auch wirklich toetet.
     *
     * <p>Der Sprung-Crit kostet Sichtbarkeit (der Bot verliert fuer einen Tick die Deckung und
     * macht eine sichtbare Bewegung) und bringt +50 % Schaden. Das ist genau dann ein Gewinn, wenn
     * es ohnehin das Ende ist: ein Crystal-Pop setzt den Gegner auf volle Lebensenergie zurueck,
     * Schaden sammelt sich also nicht. Trifft der Crit nicht, hat man denselben Pop, denselben
     * Cooldown-Verbrauch und zusaetzlich einen sichtbaren Luftsprung — der Sprung ist dann reine
     * Informationsgabe an den Gegner.
     *
     * <p><b>Zur Formulierung:</b> die Plan-Notiz lautet {@code targetHealth >= attackDamage}. Das ist
     * dieselbe Grenze von der <b>anderen</b> Seite und wuerde genau dann springen, wenn der Crit das
     * Ziel <b>nicht</b> toetet — die Regel waere damit ihr eigener Gegensinn. Entscheidend ist der
     * Satz davor ("nur wenn der Crit wirklich toetet"), und der Gleichheitsfall gehoert dazu:
     * Vanilla setzt den Tod, sobald der Schaden die verbleibende Lebensenergie <b>erreicht</b>, also
     * {@code attackDamage >= targetHealth}. {@code attackDamage} ist dabei der Schaden DIESES
     * Sprung-Crits (also Basis + 50 %), nicht der Basis-Schlag — sonst waere die 1.5-Faktor-Regel
     * doppelt und die Bedingung waere nie erfuellbar.
     */
    public static Verdict gateLethalCrit(double targetHealth, double attackDamage) {
        return attackDamage >= targetHealth
            ? Verdict.passWithCrit(true)
            : Verdict.fail(Reason.CRIT_NOT_LETHAL);
    }

    /** Kurzform des D10-Gates fuer die Aufrufstelle, die nur die Sprung-Entscheidung braucht.
     *  {@code attackDamage} ist der Schaden des Sprung-Crits, siehe {@link #gateLethalCrit}. */
    public static boolean shouldJumpCrit(double targetHealth, double attackDamage) {
        return attackDamage >= targetHealth;
    }

    /** Kombinierte Auswertung mit vollem Angriffs-Cooldown als Anforderung. */
    public static Verdict evaluate(AttackState state) {
        return evaluate(state, FULL_STRENGTH);
    }

    /**
     * Kombinierte Auswertung aller drei Gates in fester Reihenfolge: D7, D9, dann D10 als
     * Sprung-Entscheidung. D10 blockiert den Angriff nicht — es entscheidet nur, ob gesprungen wird —
     * und steht deshalb hinter den beiden echten Sperren.
     *
     * @param minStrengthScale Mindestanforderung an {@code getAttackStrengthScale} (D9)
     * @return {@link Verdict}; {@code reason} benennt die erste verletzte Sperre, {@code jumpCrit}
     *         traegt D10's Antwort
     */
    public static Verdict evaluate(AttackState state, double minStrengthScale) {
        Verdict invulnerable = gateInvulnerability(state.targetHurtTime());
        if (!invulnerable.allowed()) return invulnerable;

        Verdict strength = gateStrength(state.attackStrengthScale(), minStrengthScale);
        if (!strength.allowed()) return strength;

        boolean jumpCrit = gateLethalCrit(state.targetHealth(), state.attackDamage()).allowed();
        return Verdict.passWithCrit(jumpCrit);
    }
}
