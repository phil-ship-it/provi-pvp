package com.provipvp.broker;

import java.util.Set;

import com.provipvp.broker.ConflictRegistry.Mode;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Deckt die Fehler ab, die vorher nicht auffielen: Baritone blieb nach einer Combat-Aktion stehen,
 * und die Aufloesung zweier gleichzeitig aktiver Module war nicht entscheidbar.
 */
class BrokerCoordinationTest {

    private final Object a = new Object();
    private final Object b = new Object();
    private final Object c = new Object();

    // --- PathLease -------------------------------------------------------------------------

    @Test
    void pathIsFreeUntilSomeoneHoldsIt() {
        PathLease lease = new PathLease();
        assertFalse(lease.isPaused());
        assertTrue(lease.pause(a));
        assertTrue(lease.isPaused());
    }

    @Test
    void resumingTheLastHolderReleasesThePath() {
        PathLease lease = new PathLease();
        lease.pause(a);
        lease.resume(a);
        assertFalse(lease.isPaused());
    }

    @Test
    void oneHolderDoesNotCutOffAnother() {
        PathLease lease = new PathLease();
        lease.pause(a);
        lease.pause(b);
        assertEquals(2, lease.holders());
        lease.resume(a);
        assertTrue(lease.isPaused(), "b haelt noch - Baritone muss stehen bleiben");
        lease.resume(b);
        assertFalse(lease.isPaused());
    }

    @Test
    void theSameOwnerPausingTwiceCountsOnce() {
        // Sonst wuerde ein Modul mit zwei Aktionen den Weg dauerhaft blockieren, weil sein zweiter
        // Pfad zurueckgibt, der erste aber nie.
        PathLease lease = new PathLease();
        lease.pause(a);
        lease.pause(a);
        assertEquals(1, lease.holders());
        lease.resume(a);
        assertFalse(lease.isPaused());
    }

    @Test
    void releaseAllClearsOnlyTheOwner() {
        PathLease lease = new PathLease();
        lease.pause(a);
        lease.pause(b);
        lease.releaseAll(a);
        assertFalse(lease.heldBy(a));
        assertTrue(lease.heldBy(b));
    }

    @Test
    void resumingAnUnheldPathReportsNoChange() {
        PathLease lease = new PathLease();
        assertFalse(lease.resume(a));
    }

    // --- ConflictRegistry -------------------------------------------------------------------

    private static final class Attack { }
    private static final class Spear { }
    private static final class Overlay { }

    @Test
    void exclusiveConflictIsFound() {
        ConflictRegistry reg = new ConflictRegistry()
            .declare(Spear.class, Mode.EXCLUSIVE, 0, Attack.class);
        assertEquals(Attack.class, reg.findConflict(Spear.class, Set.of(Attack.class)));
    }

    @Test
    void noConflictWhenNothingOverlaps() {
        ConflictRegistry reg = new ConflictRegistry()
            .declare(Spear.class, Mode.EXCLUSIVE, 0, Attack.class);
        assertEquals(null, reg.findConflict(Spear.class, Set.of(Overlay.class)));
    }

    @Test
    void anExclusiveModuleAlwaysYieldsToAnActiveOne() {
        ConflictRegistry reg = new ConflictRegistry()
            .declare(Spear.class, Mode.EXCLUSIVE, 99, Attack.class);
        assertTrue(reg.shouldYield(Spear.class, Set.of(Attack.class)),
            "EXKLUSIV heisst: auch eine noch hoehere eigene Prioritaet rettet nicht");
    }

    @Test
    void aDeferringModuleYieldsToAHigherPriorityOne() {
        ConflictRegistry reg = new ConflictRegistry()
            .declare(Spear.class, Mode.DEFERS, 10, Attack.class)
            .declare(Attack.class, Mode.DEFERS, 90, Spear.class);
        assertTrue(reg.shouldYield(Spear.class, Set.of(Attack.class)));
        assertFalse(reg.shouldYield(Attack.class, Set.of(Spear.class)));
    }

    @Test
    void aDeferringModuleWithEqualPriorityWinsAgainstTheActiveOne() {
        // Ein bereits aktives Modul soll nicht durch eine spaetere Aktivierung verdraengt werden,
        // solange beide gleich stark dastehen.
        ConflictRegistry reg = new ConflictRegistry()
            .declare(Spear.class, Mode.DEFERS, 50, Attack.class)
            .declare(Attack.class, Mode.DEFERS, 50, Spear.class);
        assertFalse(reg.shouldYield(Spear.class, Set.of(Attack.class)));
    }

    @Test
    void aDeferringModuleYieldsToAnUnregisteredActiveOne() {
        // Kennt der Registry die Gegenseite nicht, gewinnt der Laeufer - sonst wuerde ein spaeter
        // aktiviertes Modul ein bereits arbeitendes stilllegen koennen.
        ConflictRegistry reg = new ConflictRegistry()
            .declare(Spear.class, Mode.DEFERS, 99, Attack.class);
        assertTrue(reg.shouldYield(Spear.class, Set.of(Attack.class)));
    }

    @Test
    void anUndeclaredModuleNeverYields() {
        ConflictRegistry reg = new ConflictRegistry();
        assertFalse(reg.shouldYield(Overlay.class, Set.of(Attack.class)));
    }

    @Test
    void priorityDefaultsToZeroWhenUndeclared() {
        assertEquals(0, new ConflictRegistry().priorityOf(Overlay.class));
    }
}
