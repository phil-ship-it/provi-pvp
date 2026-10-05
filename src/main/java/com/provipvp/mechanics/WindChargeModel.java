package com.provipvp.mechanics;

/**
 * Die beiden Wind-Quellen, die im Kampf unterschiedliche Arbeit machen — und die haeufig verwechselt
 * werden, weil beide "Wind" heissen und sich voellig ungleich benehmen:
 *
 * <ul>
 *   <li>Die <b>von Hand geworfene Wind Charge</b> ist ein Item: 1 HP Schaden, eine 2.4-Block-Kugel und ein
 *       Knockback-Multiplikator von 1.22 — also <b>mehr</b> Schub als ein Windschlag. Dafuer kostet jede
 *       Nutzung 10 Ticks (0.5 s), in denen der Bot keinen zweiten werfen kann. Das ist einburstweiliger,
 *       sehr gezielter Einzelschaden.</li>
 *   <li>Der <b>Breeze Shot</b> ist nicht als Item erhaeltlich: eine 6-Block-Kugel, aber nur ein
 *       Knockback-Multiplikator von 0.6 und ueberhaupt kein Schaden fuer einen Spieler. Man sieht ihn
 *       nur, wenn man gegen einen Breeze kaempft.</li>
 * </ul>
 *
 * <p><b>Wind Burst</b> ist davon voellig getrennt: eine Mace-Verzauberung, die den Spieler beim Smash
 * selbst in die Luft schiesst — 8 / 16 / 24 Bloecke auf I / II / III. Sie <b>negiert den Fallschaden
 * nicht</b>; wer sich damit in die Luft schiesst, nimmt beim Landen vollen Schaden. Das ist der Grund,
 * warum der Kick hier nicht als Fluchtmittel eingeplant werden darf, sondern nur als Abstandsmanagement.
 *
 * <p>Reine Berechnungen ohne {@code mc.*} und ohne Settings: Positionierung und Escape-Vorhersage brauchen
 * nur diese Zahlen.
 */
public final class WindChargeModel {

    private WindChargeModel() {}

    /** Schaden einer handgeworfenen Wind Charge. */
    public static final float PLAYER_CHARGE_DAMAGE = 1.0f;

    /** Wirkungsradius der handgeworfenen Wind Charge in Bloecken. */
    public static final double PLAYER_CHARGE_RADIUS = 2.4;

    /** Knockback-Multiplikator der handgeworfenen Wind Charge — staerker als ein Windschlag. */
    public static final double PLAYER_CHARGE_KNOCKBACK = 1.22;

    /** Abklingzeit nach einem Wurf: 10 Ticks = 0.5 s. */
    public static final int PLAYER_CHARGE_COOLDOWN_TICKS = 10;

    /** Wirkungsradius des Breeze Shots in Bloecken. */
    public static final double BREEZE_SHOT_RADIUS = 6.0;

    /** Knockback-Multiplikator des Breeze Shots — schwacher als ein normaler Windschlag. */
    public static final double BREEZE_SHOT_KNOCKBACK = 0.6;

    /** Anzahl der Wind-Burst-Stufen. */
    public static final int WIND_BURST_LEVELS = 3;

    /** Selbstschuss-Distanz in Bloecken, Index 0 = Stufe I. */
    public static final double[] WIND_BURST_BLOCKS = {8.0, 16.0, 24.0};

    /** Multiplikator auf diese Distanz, Index 0 = Stufe I. */
    public static final double[] WIND_BURST_MULTIPLIERS = {1.2, 1.75, 2.2};

    /**
     * Eine Wind-Quelle mit allen entscheidenden Eigenschaften.
     *
     * @param obtainable         als Item erreichbar? Beim Breeze Shot nein — er wird nie berechnet
     * @param damage             Schaden an Spielern (beim Breeze Shot 0)
     * @param radius             Wirkungsradius in Bloecken
     * @param knockbackMultiplier Multiplikator auf den Basis-Knockback
     * @param cooldownTicks      Abklingzeit bis zum naechsten Wurf
     */
    public record WindCharge(boolean obtainable, float damage, double radius,
                              double knockbackMultiplier, int cooldownTicks) {

        /** Trifft diese Quelle ein Ziel im Abstand {@code distance}? Rand included. */
        public boolean hits(double distance) {
            return distance <= radius;
        }

        /** Wie weit wird ein Ziel in dieser Distanz tatsaechlich geschoben? */
        public double knockbackAt(double distance) {
            return hits(distance) ? knockbackMultiplier : 0.0;
        }
    }

    /** Die handgeworfene Wind Charge — die einzige, die der Bot selbst einsetzen kann. */
    public static WindCharge playerCharge() {
        return new WindCharge(true, PLAYER_CHARGE_DAMAGE, PLAYER_CHARGE_RADIUS,
            PLAYER_CHARGE_KNOCKBACK, PLAYER_CHARGE_COOLDOWN_TICKS);
    }

    /** Der Breeze Shot — breit, schwach, und als Item nicht erhaeltlich. */
    public static WindCharge breezeShot() {
        return new WindCharge(false, 0.0f, BREEZE_SHOT_RADIUS, BREEZE_SHOT_KNOCKBACK, 0);
    }

    /**
     * Wind Burst auf der Mace. Wirkt <b>nur</b> auf Smash-Angriffe und <b>negiert den Fallschaden
     * nicht</b> — beides bewusst als Felder, damit kein Aufrufer die Verzauberung als sichere Flucht
     * missversteht.
     *
     * @param level              1..3, 0 = keine Verzauberung
     * @param launchBlocks       Selbstschuss in Bloecken
     * @param multiplier         Multiplikator auf {@link #launchBlocks}
     * @param smashOnly          wirkt ausschliesslich beim Smash-Angriff
     * @param negatesFallDamage  negiert den Fallschaden nach dem Selbstschuss
     */
    public record WindBurst(int level, double launchBlocks, double multiplier,
                            boolean smashOnly, boolean negatesFallDamage) {

        /** Wie weit wird der Spieler insgesamt in die Luft geschossen? */
        public double totalLaunchBlocks() {
            return launchBlocks * multiplier;
        }
    }

    /** Wind Burst auf der angegebenen Stufe; Stufe 0 (keine Verzauberung) ergibt {@code null}. */
    public static WindBurst windBurst(int level) {
        if (level < 1 || level > WIND_BURST_LEVELS) return null;
        int i = level - 1;
        return new WindBurst(level, WIND_BURST_BLOCKS[i], WIND_BURST_MULTIPLIERS[i], true, false);
    }

    /** Ist die Abklingzeit der handgeworfenen Wind Charge abgelaufen? */
    public static boolean playerChargeReady(int ticksSinceThrow) {
        return ticksSinceThrow >= PLAYER_CHARGE_COOLDOWN_TICKS;
    }
}