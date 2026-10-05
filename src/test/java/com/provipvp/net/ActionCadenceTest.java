package com.provipvp.net;

import com.provipvp.net.ActionCadence.Action;
import com.provipvp.net.ActionCadence.Decision;
import com.provipvp.net.ActionCadence.Refusal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Deckt die beiden Regeln ab, die Grim pro Bewegungspaket auszaehlt: eine Aktion pro Art (D18) und
 * die Reihenfolge von Slot-Wechsel und Aktion (D19).
 */
class ActionCadenceTest {

    @Test
    void aSecondPlacementInTheSameTickIsRefusedWithTheCadenceRule() {
        ActionCadence cadence = new ActionCadence();

        assertTrue(cadence.request(Action.PLACE).allowed(), "die erste Platzierung eines Ticks ist erlaubt");

        Decision second = cadence.request(Action.PLACE);
        assertFalse(second.allowed(), "Grims MultiPlace feuert ab der zweiten Platzierung pro Tick");
        assertEquals(Refusal.MULTI_PLACE, second.refusal(),
            "die Ablehnung muss die Taktungsregel benennen, nicht nur false liefern");
    }

    @Test
    void anAttackWhileUsingAnItemIsRefusedWithTheUseItemRule() {
        ActionCadence cadence = new ActionCadence();
        cadence.setUsingItem(true);

        Decision attack = cadence.request(Action.ATTACK);

        assertFalse(attack.allowed(), "waehrend isUsingItem geht kein Angriff durch");
        assertEquals(Refusal.USING_ITEM, attack.refusal(),
            "das muss als Item-Gebrauch erkennbar sein, nicht als Taktung — der Item-Slot bleibt belegt");
    }

    @Test
    void aPlacementWhileUsingAnItemIsRefusedToo() {
        ActionCadence cadence = new ActionCadence();
        cadence.setUsingItem(true);

        assertEquals(Refusal.USING_ITEM, cadence.request(Action.PLACE).refusal());
    }

    @Test
    void aSlotSwapBeforeTheAttackIsTheCorrectOrder() {
        ActionCadence cadence = new ActionCadence();

        cadence.onSlotChange(); // Combat-Slot wird VOR dem Schlag umgelegt
        Decision attack = cadence.request(Action.ATTACK);

        assertTrue(attack.allowed(),
            "Slot-Wechsel vor der Aktion ist genau die Reihenfolge, die Grim sehen will");
    }

    @Test
    void aSlotSwapBetweenTwoAttacksIsRefusedAsInterleaving() {
        ActionCadence cadence = new ActionCadence();
        assertTrue(cadence.request(Action.ATTACK).allowed());

        cadence.onSlotChange(); // Slot-Wechsel NACH dem ersten Schlag
        Decision second = cadence.request(Action.ATTACK);

        assertFalse(second.allowed(),
            "ein Slot-Wechsel zwischen zwei Aktionen ist die Verschachtelung aus PacketOrderE/F");
        assertEquals(Refusal.SLOT_ORDER, second.refusal(),
            "das muss als Reihenfolgefehler erkennbar sein, nicht als Taktung");
    }

    @Test
    void aTrailingSlotRestoreAtTheEndOfTheTickStaysAllowed() {
        // Der Combat-Slot wird nach dem Schlag zurueckgesetzt. Das ist kein Verschachteln, weil
        // keine Aktion mehr folgt — blockiert man das, bleibt der Bot dauerhaft mit der Waffe in
        // der Hand und der Place-Zweig stirbt.
        ActionCadence cadence = new ActionCadence();
        cadence.onSlotChange();
        assertTrue(cadence.request(Action.ATTACK).allowed());

        cadence.onSlotChange(); // Rueckbau des Combat-Slots
        assertFalse(cadence.hasActionThisTick() && cadence.request(Action.LOOK).refusal() == Refusal.SLOT_ORDER,
            "nach dem Rueckbau darf eine reine Blickrichtung kommen");
    }

    @Test
    void eachActionKindHasItsOwnSingleSlot() {
        ActionCadence cadence = new ActionCadence();

        // Angriff, Platzierung und Blick duerfen in einem Tick jeweils einmal vorkommen — das ist
        // ein Crystal setzen und im selben Tick zuschlagen, ein ganz normaler, notwendiger Ablauf.
        assertTrue(cadence.request(Action.ATTACK).allowed());
        assertTrue(cadence.request(Action.PLACE).allowed());
        assertTrue(cadence.request(Action.LOOK).allowed());

        assertEquals(Refusal.DUPLICATE_ATTACK, cadence.request(Action.ATTACK).refusal());
        assertEquals(Refusal.MULTI_PLACE, cadence.request(Action.PLACE).refusal());
        assertEquals(Refusal.DUPLICATE_ROT_LOOK, cadence.request(Action.LOOK).refusal(),
            "DuplicateRotLook feuert ab der zweiten Blickrichtung im selben Bewegungspaket");
    }

    @Test
    void swingHasItsOwnBudgetAndIsNotBoundByUsingItem() {
        ActionCadence cadence = new ActionCadence();

        assertTrue(cadence.request(Action.SWING).allowed());
        assertEquals(Refusal.DUPLICATE_SWING, cadence.request(Action.SWING).refusal());

        // Ein Armschwung bricht keinen Item-Gebrauch ab und ist kein Verschachtelungskandidat.
        ActionCadence fresh = new ActionCadence();
        fresh.setUsingItem(true);
        assertTrue(fresh.request(Action.SWING).allowed(),
            "der Item-Gebrauch darf nur Angriff und Platzierung blockieren");
    }

    @Test
    void aRefusedActionIsNotBooked() {
        // Wird ein Angriff waehrend isUsingItem abgelehnt, muss er im naechsten Tick wieder moeglich
        // sein — sonst verliert der Bot den Kampf, weil er sich selbst blockiert hat.
        ActionCadence cadence = new ActionCadence();
        cadence.setUsingItem(true);
        cadence.request(Action.ATTACK);

        cadence.onTick();
        assertTrue(cadence.request(Action.ATTACK).allowed(),
            "nach der Ablehnung darf kein Phantom-Platzhalter im Ledger stehen");
    }

    @Test
    void onTickClearsEveryBudget() {
        ActionCadence cadence = new ActionCadence();
        cadence.request(Action.PLACE);
        cadence.request(Action.ATTACK);
        cadence.request(Action.SWING);
        cadence.request(Action.LOOK);

        cadence.onTick();

        assertFalse(cadence.hasActionThisTick(), "der neue Tick beginnt ohne gebuchte Aktion");
        assertTrue(cadence.request(Action.PLACE).allowed(), "die Platzierungsbilanz ist zurueckgesetzt");
        assertTrue(cadence.request(Action.ATTACK).allowed(), "die Angriffsbilanz ist zurueckgesetzt");
    }

    @Test
    void onTickAlsoClearsTheUsingItemFlag() {
        // Der Aufrufer muss isUsingItem nicht selbst zuruecksetzen — das waere eine zweite
        // Fehlerquelle, weil ein vergessenes clear() den Bot fuer den Rest der Sitzung blind macht.
        ActionCadence cadence = new ActionCadence();
        cadence.setUsingItem(true);
        cadence.request(Action.ATTACK);

        cadence.onTick();

        assertTrue(cadence.request(Action.ATTACK).allowed());
    }

    @Test
    void permitsMirrorsTheDecision() {
        ActionCadence cadence = new ActionCadence();

        assertTrue(cadence.permits(Action.PLACE));
        assertFalse(cadence.permits(Action.PLACE), "permits() darf das Ledger nicht umgehen");
    }
}