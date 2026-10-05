package com.provipvp.ray;

import org.junit.jupiter.api.Test;

import static com.provipvp.ray.ReachPolicy.Action.BREAK_ENTITY;
import static com.provipvp.ray.ReachPolicy.Action.MELEE;
import static com.provipvp.ray.ReachPolicy.Action.PLACE_BLOCK;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Deckt genau die Verwechslung ab, die es bei freistehenden Zahlen gab: 4.5 gilt fuer die
 * Platzierung und ausdruecklich NICHT fuer das Zuendung eines End Crystals.
 */
class ReachPolicyTest {

    @Test
    void crystalBreakAtFourPointFiveIsRejectedWhileThreeIsAccepted() {
        // Der Fehler, der den Bot ein Crystal nach dem anderen "nicht abbauen" laesst: die 4.5 der
        // Blockplatzierung auf den Entity-Zugriff angewendet.
        assertFalse(ReachPolicy.allows(BREAK_ENTITY, 4.5));
        assertTrue(ReachPolicy.allows(BREAK_ENTITY, 3.0));
        assertFalse(ReachPolicy.allows(BREAK_ENTITY, 3.4));
    }

    @Test
    void placementAtFourIsAcceptedWhereMeleeIsNot() {
        // Und die Umkehrung desselben Fehlers: 3.0 auf die Platzierung angewendet verliert jede
        // Deckung und jeden Anker jenseits von 3 Bloecken.
        assertTrue(ReachPolicy.allows(PLACE_BLOCK, 4.0));
        assertFalse(ReachPolicy.allows(MELEE, 4.0));
        assertFalse(ReachPolicy.allows(PLACE_BLOCK, 4.6));
    }

    @Test
    void bothThreeBlockActionsShareOneLimitAndKeepTheirOwnName() {
        // Nahkampf und Crystal-Zuendung haben denselben Zahlenwert, aber verschiedene Herleitungen
        // im Spiel. Wer einen von beiden gegen den anderen austauscht, darf am Ergebnis nichts
        // aendern - genau das macht die Unterscheidung an der Aufrufstelle gefahrlos.
        assertTrue(ReachPolicy.allows(MELEE, 3.4) == ReachPolicy.allows(BREAK_ENTITY, 3.4));
        assertFalse(ReachPolicy.allows(MELEE, 3.4));
        assertTrue(ReachPolicy.allows(PLACE_BLOCK, 3.4));
    }

    @Test
    void grimReachToleranceIsTighterThanTheVanillaLimit() {
        // Die Marge zwischen 3.0 und 3.0005 ist der Unterschied zwischen "server erlaubt es" und
        // "Grim flaggt es" - beide Pruefungen gehoeren an jedes Gate.
        assertTrue(ReachPolicy.allows(MELEE, 3.0));
        assertFalse(ReachPolicy.allows(MELEE, ReachPolicy.GRIM_REACH_TOLERANCE),
            "3.0005 ueberschreitet die Vanilla-Grenze von 3.0 bereits");
        assertTrue(ReachPolicy.withinGrimReach(MELEE, ReachPolicy.GRIM_REACH_TOLERANCE));
        assertFalse(ReachPolicy.withinGrimReach(MELEE, ReachPolicy.GRIM_REACH_TOLERANCE + 1e-4));

        assertTrue(ReachPolicy.withinGrimReach(BREAK_ENTITY, 3.0004));
        assertFalse(ReachPolicy.withinGrimReach(BREAK_ENTITY, 3.0006));
    }

    @Test
    void placementCarriesNoThreeBlockReachCheck() {
        assertTrue(ReachPolicy.withinGrimReach(PLACE_BLOCK, 4.5));
        assertFalse(ReachPolicy.withinGrimReach(PLACE_BLOCK, 4.51));
    }

    @Test
    void blockInteractionIsTheOnlyActionWithTheFourBlockFiveLimit() {
        assertTrue(ReachPolicy.isBlockInteraction(PLACE_BLOCK));
        assertFalse(ReachPolicy.isBlockInteraction(BREAK_ENTITY));
        assertFalse(ReachPolicy.isBlockInteraction(MELEE));
    }

    @Test
    void nonsensicalDistancesAreRejectedRatherThanCompared() {
        // NaN <= 3.0 ist in Java false, aber ein unendlicher Wert waere es nicht - beides darf
        // niemals als "erlaubt" durchrutschen.
        assertFalse(ReachPolicy.allows(MELEE, Double.NaN));
        assertFalse(ReachPolicy.allows(MELEE, Double.POSITIVE_INFINITY));
        assertFalse(ReachPolicy.withinGrimReach(PLACE_BLOCK, Double.NaN));
    }
}