package com.provipvp.broker;

import org.junit.jupiter.api.Test;

import com.provipvp.broker.ActionBroker.ActionKind;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testet das Verhalten, das im Spiel nicht sichtbar waere: zwei Module, dieselbe Handlung, ein Tick.
 * Die Reihenfolge der Aufrufe ist hier die Entscheidung, die spaeter im Kampf zaehlt.
 */
class ActionBrokerTest {

    private final Object a = new Object();
    private final Object b = new Object();
    private final ActionBroker broker = new ActionBroker();

    @Test
    void firstClaimWins() {
        assertTrue(broker.claim(a, ActionKind.MELEE, 10));
        assertEquals(a, broker.holder(ActionKind.MELEE));
    }

    @Test
    void secondModuleIsRefusedOnTheSameKind() {
        assertTrue(broker.claim(a, ActionKind.MELEE, 10));
        assertFalse(broker.claim(b, ActionKind.MELEE, 10));
        assertEquals(a, broker.holder(ActionKind.MELEE));
    }

    @Test
    void differentKindsDoNotBlockEachOther() {
        assertTrue(broker.claim(a, ActionKind.MELEE, 10));
        assertTrue(broker.claim(b, ActionKind.CRYSTAL, 10));
    }

    @Test
    void sameOwnerMayReassertItsOwnClaim() {
        assertTrue(broker.claim(a, ActionKind.MELEE, 10));
        assertTrue(broker.claim(a, ActionKind.MELEE, 10), "dieselbe Handlung zweimal anfragen muss erlaubt sein");
    }

    @Test
    void higherPriorityPreemptsAnUnspentClaim() {
        assertTrue(broker.claim(a, ActionKind.MELEE, 10));
        assertTrue(broker.claim(b, ActionKind.MELEE, 50), "B darf A verdraengen, solange A nicht gefeuert hat");
        assertEquals(b, broker.holder(ActionKind.MELEE));
    }

    @Test
    void aSpentClaimIsNotPreemptible() {
        // Das ist der eigentliche Grund fuer die Regel: das Paket ist unterwegs, ein Verdrängen
        // koennte es nicht mehr zuruecknehmen.
        assertTrue(broker.claim(a, ActionKind.MELEE, 10));
        broker.spend(a, ActionKind.MELEE);
        assertFalse(broker.claim(b, ActionKind.MELEE, 50), "nach dem Feuern darf nicht mehr verdraengt werden");
        assertEquals(a, broker.holder(ActionKind.MELEE));
    }

    @Test
    void lowerPriorityNeverPreempts() {
        assertTrue(broker.claim(a, ActionKind.MELEE, 50));
        assertFalse(broker.claim(b, ActionKind.MELEE, 10));
    }

    @Test
    void oneActionPerOwnerPerTick() {
        assertTrue(broker.claim(a, ActionKind.MELEE, 10));
        broker.spend(a, ActionKind.MELEE);
        assertFalse(broker.claim(a, ActionKind.PEARL, 10), "ein Modul darf je Tick einmal ziehen");
    }

    @Test
    void releaseFreesOnlyTheOwnersClaims() {
        broker.claim(a, ActionKind.MELEE, 10);
        broker.claim(a, ActionKind.CRYSTAL, 10);
        broker.claim(b, ActionKind.ANCHOR, 10);
        broker.release(a);
        assertEquals(null, broker.holder(ActionKind.MELEE));
        assertEquals(null, broker.holder(ActionKind.CRYSTAL));
        assertEquals(b, broker.holder(ActionKind.ANCHOR));
    }

    @Test
    void spendOnlyCountsForTheActualHolder() {
        assertTrue(broker.claim(a, ActionKind.MELEE, 10));
        broker.spend(b, ActionKind.MELEE); // b haelt nichts - darf weder a buchen noch b belasten
        assertEquals(a, broker.holder(ActionKind.MELEE));
        assertFalse(broker.hasSpent(b), "fremdes spend darf das eigene Budget nicht verbrauchen");

        // Anfordern ist nicht Ausfuehren: erst spend() verbraucht das Budget.
        assertTrue(broker.claim(b, ActionKind.CRYSTAL, 10));
        assertTrue(broker.claim(b, ActionKind.ANCHOR, 10));
        broker.spend(b, ActionKind.CRYSTAL);
        assertFalse(broker.claim(b, ActionKind.ANCHOR, 10), "nach eigenem spend ist das Budget weg");
    }

    @Test
    void tickResetsEverything() {
        broker.claim(a, ActionKind.MELEE, 10);
        broker.spend(a, ActionKind.MELEE);
        broker.tick();
        assertEquals(null, broker.holder(ActionKind.MELEE));
        assertFalse(broker.hasSpent(a));
        assertTrue(broker.claim(b, ActionKind.MELEE, 1), "nach dem Reset muss auch ein niederes Modul drankommen");
    }

    @Test
    void nullOwnerIsRefused() {
        assertFalse(broker.claim(null, ActionKind.MELEE, 10));
    }

    @Test
    void identityNotEqualityDecidesOwnership() {
        // Zwei verschiedene Module, die.equals() zufaellig true liefern, duerfen nicht verschmelzen.
        Object x = new String("modul");
        Object y = new String("modul");
        assertTrue(x.equals(y));
        assertTrue(broker.claim(x, ActionKind.MELEE, 10));
        assertFalse(broker.claim(y, ActionKind.MELEE, 10));
    }
}
