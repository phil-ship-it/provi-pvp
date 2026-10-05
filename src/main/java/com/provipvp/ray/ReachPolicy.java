package com.provipvp.ray;

/**
 * Die drei voeinander unabhaengigen Reichweiten, die Vanilla im Kampf unterscheidet — als benannter
 * Typ statt als drei Zahlen an den Aufrufstellen.
 *
 * <p>Der Grund fuer den Aufwand: {@code EntityInteractionRange} und {@code BlockInteractionRange}
 * sind zwei verschiedene Attribute mit zwei verschiedenen Werten, und der Unterschied entscheidet
 * darueber, ob eine Aktion ueberhaupt ankommt. Ein Crystal 4.5 Bloecke entfernt mit der Hand
 * anzufassen geht nicht (Reichweite 3.0), Obsidian bei 4.0 Bloecken sehr wohl (Reichweite 4.5).
 * Wer beides als eine Konstante {@code 4.5} fuehrt, verliert still die Crystal-Aktionen; wer beides
 * als 3.0 fuehrt, verliert die Platzierungen.
 *
 * <p>Deshalb gibt es hier keine freistehende {@code double} mehr, sondern ausschliesslich
 * {@link Action}-Konstanten: an der Aufrufstelle steht {@code ReachPolicy.Action.BREAK_ENTITY}, nicht
 * eine Zahl, die man verwechseln kann. {@link Action#limit()} liefert dann die Zahl.
 *
 * <p>Grim ist bei der Nahkampf- und der Entity-Reichweite strenger als Vanilla: {@link
 * #GRIM_REACH_TOLERANCE} ist die Schwelle, ab der {@code Reach} anschlaegt. Platzierung misst Grim
 * mit derselben 4.5er-Grenze wie Vanilla.
 */
public final class ReachPolicy {

    private ReachPolicy() {}

    /** Vanilla {@code Attributes.ENTITY_INTERACTION_RANGE} — End-Crystal abbauen. */
    public static final double ENTITY_INTERACTION_REACH = 3.0;

    /** Vanilla {@code Attributes.BLOCK_INTERACTION_RANGE} — Bloecke setzen und benutzen. */
    public static final double BLOCK_INTERACTION_REACH = 4.5;

    /** Grim {@code Reach} flaggt jeden Nahkampf- oder Entity-Zugriff jenseits dieser Distanz. */
    public static final double GRIM_REACH_TOLERANCE = 3.0005;

    /**
     * Der zu verrichtende Zugriff auf ein Ziel.
     *
     * <p>{@link #MELEE} und {@link #BREAK_ENTITY} teilen sich bewusst denselben Zahlenwert, haben
     * aber verschiedene Namen und verschiedene Herleitungen im Spiel: ein Schlag gegen einen Spieler
     * geht ueber den Nahkampfschaden, das Zuendung eines End Crystals ueber das Entity-Interaktions-
     * Attribut. Die Serverbehandlung ist unterschiedlich, die Distanz nicht — genau deshalb sind es
     * zwei Namen und nicht eine Konstante.
     */
    public enum Action {

        /** Schlag gegen einen Spieler. Vanilla 3.0, Grim 3.0005. */
        MELEE("Nahkampf", ENTITY_INTERACTION_REACH),

        /** End Crystal abbauen, um die Explosion freizugeben. Vanilla 3.0, Grim 3.0005. */
        BREAK_ENTITY("Entity abbauen", ENTITY_INTERACTION_REACH),

        /** Block setzen (Crystal, Anker, Bett, Obsidian-Deckung). Vanilla 4.5. */
        PLACE_BLOCK("Block setzen", BLOCK_INTERACTION_REACH);

        private final String label;
        private final double limit;

        Action(String label, double limit) {
            this.label = label;
            this.limit = limit;
        }

        /** Kurzbezeichnung fuer Debug-Ausgaben. */
        public String label() {
            return label;
        }

        /** Maximale Distanz Augen-Standort zu Block-/Entity-Mitte in Bloecken. */
        public double limit() {
            return limit;
        }

        /** Greift {@link #GRIM_REACH_TOLERANCE} fuer diese Aktion ueberhaupt? */
        public boolean hasGrimReachCheck() {
            return this != PLACE_BLOCK;
        }
    }

    /** Reichweite der benannten Aktion. */
    public static double limit(Action action) {
        return action.limit();
    }

    /** Liegt die Distanz innerhalb der Vanilla-Reichweite der Aktion? */
    public static boolean allows(Action action, double distance) {
        return Double.isFinite(distance) && distance <= action.limit();
    }

    /**
     * Liegt die Distanz auch innerhalb der Grim-Schwelle?
     *
     * <p>Die Marge zwischen 3.0 und 3.0005 ist Absicht: Vanilla rundet die Distanzpruefung auf
     * volle Bloecke ab, Grim nicht. Wer exakt auf 3.0 geht, kann mit Rundungsrauschen trotzdem
     * knapp darüber landen — die Toleranz gehoert deshalb zu jeder Gate-Entscheidung dazu.
     */
    public static boolean withinGrimReach(Action action, double distance) {
        if (!Double.isFinite(distance)) return false;
        return action.hasGrimReachCheck()
            ? distance <= GRIM_REACH_TOLERANCE
            : distance <= action.limit();
    }

    /** Wirkt diese Aktion auf Bloecke statt auf Entities? Nur dann gilt 4.5. */
    public static boolean isBlockInteraction(Action action) {
        return action == Action.PLACE_BLOCK;
    }
}