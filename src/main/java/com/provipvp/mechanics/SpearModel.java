package com.provipvp.mechanics;

/**
 * Der Spear bricht das alte 3-Block-Nahkampfspiel: mit Reichweite 4.5 kann er Gegner von dort treffen,
 * wo das Addon bisher jede Nahkampf-Aktion abgelehnt hat. Der Default von {@code attack-range} (3.6 im
 * GodmodePvP, 3.4 im HumanPvP) ist gegen einen Spear also <b>falsch</b> — er schneidet genau den
 * Distanzstreifen ab, in dem der Spear am gefaehrlichsten ist.
 *
 * <p>Die drei weiteren Speer-Eigenheiten sind Angriffsschichten, die KEIN Code im Modul bisher kannte:
 * <ul>
 *   <li>Der <b>Jab</b> wird auf 100 % Cooldown gezwungen — das normale Cooldown-Gate des Bots greift fuer
 *       ihn also nicht. Er loest die volle Schadensstaerke aus, obwohl der Angriffs-Cooldown gerade erst
 *       zur Haelfte durch ist.</li>
 *   <li>Der Spear kann <b>nicht critten</b> und erzeugt <b>keinen Sprint-Knockback</b>. Der Bot darf also
 *       seinen Crit-Jump und sein W-Tap/Sprint-Reset nicht auf einen Spear-Schlag "herunterrechnen"
 *       wollen — der Sprung kostet Zeit, bringt aber keinen Bonus.</li>
 *   <li>Der <b>Charge-Angriff</b> haengt an der Annaeherungsgeschwindigkeit: unter 4.6 Bloecke/s geht
 *       gar kein Schaden raus, ab 5.1 Bloecke/s erst der Knockback. Er hat drei Ladungszustaende
 *       (engaged / tired / disengaged) und sein Schaden wird — anders als beim Jab — <b>nicht</b> durch
 *       Staerke verstaerkt.</li>
 * </ul>
 *
 * <p>Bewusst frei von {@code mc.*} und Settings: der komplette Zustandsautomat ist eine reine Funktion aus
 * (closingSpeed, chargeTicks, strengthLevel, currentCooldownState). Der Kampfzustand kommt per Parameter
 * herein, die Base-Schadenszahl der gehaltenen Waffe ebenfalls ({@code baseDamage}) — sonst muesste diese
 * Klasse die Schadensformel des Items raten.
 */
public final class SpearModel {

    private SpearModel() {}

    /** Reichweite eines normalen Nahkampf-Schlags. Grim akzeptiert 3.0005 — der Spear ist deutlich weiter. */
    public static final double MAX_REACH = 4.5;

    /** Unterhalb dieser Distanz trifft der Spear nicht mehr (Kampf-Distanz nach oben off, nach unten zu). */
    public static final double MIN_REACH = 2.0;

    /** Ab dieser Annaeherungsgeschwindigkeit richtet der Charge-Angriff ueberhaupt Schaden an. */
    public static final double CHARGE_DAMAGE_SPEED = 4.6;

    /** Ab dieser Annaeherungsgeschwindigkeit stoesst der Charge-Angriff zusaetzlich zurueck. */
    public static final double CHARGE_KNOCKBACK_SPEED = 5.1;

    /**
     * Ab dieser Cooldown-Stufe erlaubt der Bot einen vollstaendigen Angriff. Das ist die Schwelle, die im
     * Modul bisher als {@code getAttackStrengthScale(0.5f) >= 0.9} ueberall hart verdrahtet war.
     */
    public static final double COOLDOWN_GATE = 0.9;

    /** Haltezeit, ab der der Charge-Angriff den Zustand ENGAGED erreicht (2 s). */
    public static final int CHARGE_FULL_TICKS = 40;

    /** Haltezeit, ab der ueberhaupt ein Charge-Angriff moeglich ist; darunter DISENGAGED. */
    public static final int CHARGE_MIN_TICKS = 10;

    /** Basis-Knockback eines Jabs — Vanilla-Melee-Basiswert, <b>ohne</b> Sprint-Aufschlag. */
    public static final double JAB_KNOCKBACK = 0.4;

    /** Knockback eines voll geladenen Charge-Angriffs. */
    public static final double CHARGE_KNOCKBACK = 1.0;

    /** Die drei Ladungszustaende des Charge-Angriffs. */
    public enum ChargeState {
        /** Voll geladen: Schaden ab 4.6 b/s, Knockback ab 5.1 b/s moeglich. */
        ENGAGED,
        /** Teilweise geladen: Schaden ja, Knockback nein — der Stoss kommt nicht mehr durch. */
        TIRED,
        /** Nicht geladen: ein Charge-Angriff ist gar nicht erst ausfuehrbar. */
        DISENGAGED;

        /** Bildet die gehaltene Haltezeit auf den Zustand ab. */
        public static ChargeState of(int chargeTicks) {
            if (chargeTicks >= CHARGE_FULL_TICKS) return ENGAGED;
            if (chargeTicks >= CHARGE_MIN_TICKS) return TIRED;
            return DISENGAGED;
        }
    }

    /** Welche der beiden Speer-Angriffsformen wird bewertet. */
    public enum Attack {
        /** Der schnelle Stich. Wird serverseitig auf 100 % Cooldown gezwungen. */
        JAB,
        /** Der gehaltene, geschwindigkeitsabhaengige Angriff. */
        CHARGE
    }

    /**
     * Der Angriffs-Cooldown als Spielstand. Gehalten als Zahl statt als Flag, weil der Bot an drei Stellen
     * dieselbe Schwelle prueft und der Speer an genau einer davon vorbeispringt.
     *
     * @param strengthScale Angriffsstaerke 0..1; 0.5 = frisch geschwungen, 1.0 = voll aufgeladen.
     */
    public record CooldownState(double strengthScale) {

        public CooldownState {
            strengthScale = Math.clamp(strengthScale, 0.0, 1.0);
        }

        /** Erlaubt den Bot-Angriff bei vollstaendigem Cooldown? Der Jab umgeht diese Pruefung. */
        public boolean ready() {
            return strengthScale >= COOLDOWN_GATE;
        }
    }

    /**
     * Das Ergebnis einer Speer-Bewertung. Enthaelt bewusst auch die <b>negativen</b> Aussagen
     * (kein Crit, kein Sprint-Knockback) — das sind beim Spear die teuersten Eigenschaften, weil der Bot
     * sonst einen unsichtbaren Schlag einplant.
     *
     * @param cooldownGatePassed hat der Angriff das normale Cooldown-Gate passiert (Jab: immer)
     */
    public record Verdict(
        Attack attack,
        ChargeState chargeState,
        boolean damageDealt,
        double damage,
        boolean crit,
        boolean sprintKnockback,
        boolean knockedBack,
        double knockback,
        boolean strengthApplied,
        boolean cooldownGatePassed,
        double cooldownScaleUsed) {}

    /**
     * Der komplette Speer-Zustandsautomat als reine Funktion.
     *
     * @param closingSpeed Annaeherungsgeschwindigkeit des Bots zum Ziel in Bloecke pro Sekunde; der
     *                     Charge-Angriff bewertet ausschliesslich diesen Wert
     * @param chargeTicks  gehaltene Haltezeit der Waffe in Ticks
     * @param strengthLevel Staerke-Stufe 0..5; wirkt nur auf den Jab
     * @param cooldown     aktueller Angriffs-Cooldown
     * @param baseDamage   Basis-Schaden der gehaltenen Speer-Waffe (Item-spezifisch, wird durchgereicht)
     */
    public static Verdict resolve(Attack attack, double closingSpeed, int chargeTicks,
                                  int strengthLevel, CooldownState cooldown, double baseDamage) {

        ChargeState state = ChargeState.of(chargeTicks);

        // Der Spear kann grundsaetzlich nicht critten und kennt keinen Sprint-Knockback — fuer BEIDE
        // Angriffsformen. Das Modul darf daraus keinen "halben" Bonus ableiten.
        boolean crit = false;
        boolean sprintKnockback = false;

        if (attack == Attack.JAB) {
            // Zwang auf 100 %: der tatsaechliche Cooldown-Faktor ist 1.0, egal was der Bot gerade hat.
            // Ohne das wuerde ein Jab bei 50 % Cooldown nur halben Schaden bringen und der Bot muesste den
            // 0.9-Gate trotzdem pruefen — beides ist bei einem Spear falsch.
            double scale = 1.0;
            double damage = baseDamage + strengthLevel;
            return new Verdict(attack, state, true, damage, crit, sprintKnockback,
                true, JAB_KNOCKBACK, true, true, scale);
        }

        // Charge: das normale Cooldown-Gate gilt hier unveraendert.
        if (!cooldown.ready()) {
            return new Verdict(attack, state, false, 0.0, crit, sprintKnockback, false, 0.0,
                false, false, cooldown.strengthScale());
        }

        // Nicht geladen -> es gibt keinen Charge-Angriff, nur den Jab.
        if (state == ChargeState.DISENGAGED) {
            return new Verdict(attack, state, false, 0.0, crit, sprintKnockback, false, 0.0,
                false, true, cooldown.strengthScale());
        }

        // Ladung wirkt ueber dieselbe 0.2/0.8-Kurve wie der Angriffs-Cooldown: halb geladen heisst
        // nicht halber Schaden, sondern ein Viertel. Bewusst als Fortschritt modelliert, damit
        // chargeTicks wirklich etwas entscheidet und nicht nur ein Ja/Nein ist.
        double progress = Math.clamp(chargeTicks / (double) CHARGE_FULL_TICKS, 0.0, 1.0);
        double chargeFactor = 0.2 + 0.8 * progress * progress;

        double scale = cooldown.strengthScale();
        double cooldownFactor = 0.2 + scale * scale * 0.8;

        // Die Annaeherungsgeschwindigkeit ist das eigentliche Gate: zu langsam = gar kein Schaden.
        if (closingSpeed < CHARGE_DAMAGE_SPEED) {
            return new Verdict(attack, state, false, 0.0, crit, sprintKnockback, false, 0.0,
                false, true, scale);
        }

        // Staerke wirkt auf den Charge-Angriff ausdruecklich NICHT — sonst wuerde der Bot Staerke als
        // Schadenshebel gegen einen Spear einplanen, den es dort nicht gibt.
        double damage = baseDamage * chargeFactor * cooldownFactor;

        // Knockback nur bei voller Ladung UND ab 5.1 b/s. Ein tired Spear trifft zwar, stoesst aber nicht.
        boolean knockedBack = state == ChargeState.ENGAGED && closingSpeed >= CHARGE_KNOCKBACK_SPEED;

        return new Verdict(attack, state, true, damage, crit, sprintKnockback, knockedBack,
            knockedBack ? CHARGE_KNOCKBACK : 0.0, false, true, scale);
    }

    /** Liegt die Distanz im Trefferfenster des Spears (oben weit, unten eng)? */
    public static boolean withinReach(double distance) {
        return distance >= MIN_REACH && distance <= MAX_REACH;
    }

    /** Die Distanz, die {@code attack-range} gegen einen Spear mindestens haben muss. */
    public static double requiredAttackRange() {
        return MAX_REACH;
    }

    /**
     * Passt die konfigurierte Nahkampfreichweite zum Spear? Der aktuelle Default (3.6 / 3.4) verneint das
     * und schneidet damit den Bereich von 3.6 bis 4.5 ab — genau den, in dem ein Spear ohne Zeitverlust
     * zuschlaegt.
     */
    public static boolean attackRangeCoversSpear(double configuredRange) {
        return configuredRange >= MAX_REACH;
    }
}