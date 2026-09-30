package com.provipvp.exec;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Deckt die Prioritaeten- und Starvation-Semantik der Rotations-Warteschlange ab. Der Bug aus Woche 1
 * lebte genau hier: der kosmetische Free-Look-Tail verdraengte echte Aktionen, weil nur die Anzahl der
 * gequeue-ten Rotationen gezaehlt wurde und der Callback asynchron laeuft.
 */
class RotationQueueTest {

    @Test
    void aRealActionMarksTheTick() {
        RotationQueue q = new RotationQueue();
        assertFalse(q.hasRealActionThisTick(), "vor der ersten Aktion darf nichts markiert sein");

        q.execute(0.0, 0.0, RotationQueue.PRIORITY_CRYSTAL, () -> { });

        assertTrue(q.hasRealActionThisTick(), "eine echte Aktion muss den Slot beanspruchen");
    }

    @Test
    void freeLookAloneNeverClaimsTheSlot() {
        RotationQueue q = new RotationQueue();

        q.execute(0.0, 0.0, RotationQueue.PRIORITY_LOOK, () -> { });

        assertFalse(q.hasRealActionThisTick(),
            "Free-Look darf sich nicht selbst unterdruecken - sonst wuerde der Tail-Flush nie laufen");
    }

    @Test
    void queuedThisTickCountsEveryEntry() {
        RotationQueue q = new RotationQueue();
        assertEquals(0, q.queuedThisTick());

        q.execute(0.0, 0.0, RotationQueue.PRIORITY_CRYSTAL, () -> { });
        q.execute(0.0, 0.0, RotationQueue.PRIORITY_PEARL, () -> { });

        assertEquals(2, q.queuedThisTick(),
            "jeder Eintrag zaehlt - davon haengt clientSide ab (erster Eintrag normaler Pfad)");
    }

    @Test
    void onTickResetsBothCounters() {
        RotationQueue q = new RotationQueue();
        q.execute(0.0, 0.0, RotationQueue.PRIORITY_CRYSTAL, () -> { });
        q.execute(0.0, 0.0, RotationQueue.PRIORITY_PEARL, () -> { });

        q.onTick();

        assertEquals(0, q.queuedThisTick(), "neuer Tick beginnt ohne gequeue-te Rotation");
        assertFalse(q.hasRealActionThisTick(), "neuer Tick beginnt ohne beanspruchte Aktion");
    }

    @Test
    void freeLookAfterARealActionIsSuppressed() {
        RotationQueue q = new RotationQueue();
        q.execute(0.0, 0.0, RotationQueue.PRIORITY_CRYSTAL, () -> { });

        q.execute(0.0, 0.0, RotationQueue.PRIORITY_LOOK, () -> { });

        assertTrue(q.hasRealActionThisTick(),
            "nach einer echten Aktion bleibt der Slot beansprucht - der Free-Look-Tail wird uebersprungen");
    }

    @Test
    void prioritiesAreOrderedSoRealActionsWin() {
        assertTrue(RotationQueue.PRIORITY_CRYSTAL > RotationQueue.PRIORITY_LOOK,
            "Crystal muss den Free-Look verdraengen");
        assertTrue(RotationQueue.PRIORITY_PEARL > RotationQueue.PRIORITY_LOOK,
            "Perle muss den Free-Look verdraengen");
        assertTrue(RotationQueue.PRIORITY_MISC > RotationQueue.PRIORITY_LOOK,
            "MISC muss den Free-Look verdraengen");
    }
}
