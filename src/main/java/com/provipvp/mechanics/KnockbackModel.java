package com.provipvp.mechanics;

/**
 * Knockback-Widerstand ist <b>pro Entity</b> zu lesen, nicht zu unterstellen. Wer davon ausgeht, dass
 * "ein Spieler hat 40 % Widerstand", plant zwei Dinge falsch: Pop-Ketten gegen einen Nautilus, der
 * 30 % haelt, und Bedenken gegen Creaking, die mit 100 % gar nicht beweglich sind.
 *
 * <p>Die Quellen addieren sich:
 * <ul>
 *   <li><b>Netherite-Ruestung</b> 10 % pro Stueck, also 40 % im Vollsatz. Das ist der haeufigste Fall
 *       und der Grund, warum ein Crystal-Pop im Vollsatz den Gegner kaum bewegt.</li>
 *   <li><b>Blast Protection</b> 15 % pro Stufe und pro Stueck, additiv — und <b>nur</b> fuer
 *       Explosions-Knockback. Zwei Stuecke auf IV sind 120 %, also gedeckelt: der Widerstand
 *       ueberschreitet die 100 % und der Knockback faellt vollstaendig aus. Genau das ist der Grund, warum
 *       Blast-Protection-PvP ueberhaupt funktioniert: nicht weil es weniger KB gibt, sondern weil es
 *       gar keins mehr gibt.</li>
 *   <li><b>Native Werte</b> neuer Mobs: Creaking 100 % (solange man es ansieht), Nautilus und Zombie
 *       Nautilus 30 %, Agent und NPC 100 % und zusaetzlich unverwundbar.</li>
 * </ul>
 *
 * <p>26.2 hat das Attribut {@code knockback_resistance} auf ein Minimum von <b>-2.0</b> gesenkt. Negative
 * Werte sind also darstellbar und bedeuten <b>verstaerkten</b> Knockback bis zum Doppelten. Die Berechnung
 * darf deshalb nicht einfach auf 0 klemmen — sie muss an beiden Enden klemmen: bei Werten ueber 1.0 darf kein
 * negativer Faktor herauskommen (negativer KB = Anziehung), bei negativen Werten darf der Bonus erhalten
 * bleiben.
 *
 * <p>Reine Funktion auf nackten Zahlen, kein {@code mc.*}: der Aufrufer liest die Ruestung und den
 * Effekt-Stack des Ziels und uebergibt das Ergebnis.
 */
public final class KnockbackModel {

    private KnockbackModel() {}

    /** 10 % Knockback-Widerstand pro Stueck Netherite. */
    public static final double NETHERITE_PER_PIECE = 0.10;

    /** 15 % Widerstand pro Blast-Protection-Stufe und -Stueck, additiv. */
    public static final double BLAST_PROTECTION_PER_LEVEL = 0.15;

    /** Attribut-Minimum seit 26.2 — negativ heisst "doppelter Knockback". */
    public static final double ATTRIBUTE_MIN = -2.0;

    /** Attribut-Maximum: alles darueber ist unerreichbarer Knockback. */
    public static final double ATTRIBUTE_MAX = 1.0;

    /**
     * Native Widerstaende der neuen Mobs. Der eingebaute Wert wird nicht addiert, sondern <b>ersetzt</b>:
     * ein Creaking-Attribut von 100 % ist bereits der Maximalwert, ein spaeter addierter
     * Blast-Protection-Deckel von 120 % aendert daran nichts mehr.
     */
    public enum NativeResistance {
        /** Kein nativer Widerstand — normale Spieler. */
        NONE(0.0, false),
        /** Nautilus und Zombie Nautilus. */
        NAUTILUS(0.30, false),
        /** Creaking — nur solange der Spieler es ansieht. */
        CREAKING(1.00, false),
        /** Agent — zusaetzlich unverwundbar. */
        AGENT(1.00, true),
        /** NPC — zusaetzlich unverwundbar. */
        NPC(1.00, true);

        private final double resistance;
        private final boolean invulnerable;

        NativeResistance(double resistance, boolean invulnerable) {
            this.resistance = resistance;
            this.invulnerable = invulnerable;
        }

        /** Der native Widerstand als Anteil 0..1. */
        public double resistance() {
            return resistance;
        }

        /** Unverwundbar: selbst Volldaemon-Ladung macht keinen Schaden. */
        public boolean invulnerable() {
            return invulnerable;
        }

        /**
         * Effektiver nativer Widerstand unter Beruecksichtigung der Blickrichtung — beim Creaking ist das
         * der Unterschied zwischen beweglich und absolut unbeweglich, also keine Kosmetik.
         */
        public static NativeResistance lookingAt(NativeResistance mob, boolean lookedAt) {
            if (mob != CREAKING) return mob;
            return lookedAt ? CREAKING : NONE;
        }
    }

    /**
     * Der verbleibende Knockback-Anteil: 1.0 = voller Schub, 0.0 = gar keiner, groesser 1.0 = verstaerkter
     * Schub (durch negativen Attributwert).
     *
     * @param nativeResistance     nativer Widerstand des Ziels (ersetzt, nicht addiert)
     * @param explosionSource      Explosion? Nur dann zaehlt Blast Protection
     * @param armourPieces         Anzahl getragener Stuecke (die Blast-Protection-Traeger einschliesslich)
     * @param blastProtectionLevel Blast-Protection-Stufe dieser Stuecke
     */
    public static double effectiveKnockbackFactor(NativeResistance nativeResistance, boolean explosionSource,
                                                  int armourPieces, int blastProtectionLevel) {
        return effectiveKnockbackFactor(nativeResistance.resistance(), explosionSource, armourPieces,
            blastProtectionLevel);
    }

    /** Wie oben, mit dem Widerstand als nackter Zahl (z.B. aus dem Attribut eines Spielers gelesen). */
    public static double effectiveKnockbackFactor(double targetResistance, boolean explosionSource,
                                                  int armourPieces, int blastProtectionLevel) {
        // Native Werte sind ein Deckel, kein Summand: ein Creaking ist mit 100 % bereits an der
        // Deckelgrenze, Blast Protection darueber kann nichts mehr ausrichten.
        if (targetResistance >= ATTRIBUTE_MAX) return 0.0;

        double total = targetResistance;

        int pieces = Math.max(0, armourPieces);
        total += pieces * NETHERITE_PER_PIECE;

        // Blast Protection wirkt ausschliesslich auf Explosions-Knockback — bei einem normalen Schlag
        // waere das Zaehlen ein reiner Fehler, weil der Bot sonst Nahkampf als "nutzlos" einplant.
        if (explosionSource) total += pieces * Math.max(0, blastProtectionLevel) * BLAST_PROTECTION_PER_LEVEL;

        // Oben: negativer Widerstand erzeugt negativen Anteil = Anziehung. Unten: das Attribut-Minimum
        // begrenzt den Bonus auf das Doppelte. In die Mitte zu klemmen wuerde den -2.0-Wert aus 26.2 wegwerfen.
        return Math.clamp(1.0 - total, 0.0, 1.0 - ATTRIBUTE_MIN);
    }

    /** Ist dieses Ziel ueberhaupt wegschiebbar? */
    public static boolean canBeKnockedBack(NativeResistance nativeResistance, boolean lookedAt) {
        return effectiveKnockbackFactor(NativeResistance.lookingAt(nativeResistance, lookedAt),
            false, 0, 0) > 0.0;
    }

    /** Ist eine weitere Crystal-Pop-Kette bei diesem Widerstand noch sinnvoll? */
    public static boolean worthChasing(NativeResistance nativeResistance, boolean lookedAt) {
        // Alles unter 20 % Rest-KB bewegt das Ziel noch weit genug, um den naechsten Pop zu setzen.
        return effectiveKnockbackFactor(NativeResistance.lookingAt(nativeResistance, lookedAt),
            true, 0, 0) >= 0.20;
    }
}