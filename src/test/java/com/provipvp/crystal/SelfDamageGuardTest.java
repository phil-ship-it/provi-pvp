package com.provipvp.crystal;

import org.junit.jupiter.api.Test;

import static com.provipvp.crystal.SelfDamageGuard.Reason.LETHAL;
import static com.provipvp.crystal.SelfDamageGuard.Reason.OK;
import static com.provipvp.crystal.SelfDamageGuard.Reason.OVER_CAP;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Deckt die Eigenschaden-Sperre ab. Der entscheidende Fall ist der, den der alte Code durchlaesst:
 *  ein Schaden UNTER dem Deckel, der trotzdem die eigene Gesamtlebensenergie erreicht. */
class SelfDamageGuardTest {

    private static final double CAP = 12.0;

    @Test
    void harmlessPlacementIsAllowed() {
        SelfDamageGuard.Verdict verdict = SelfDamageGuard.check(3.0, CAP, 20.0);
        assertTrue(verdict.allowed());
        assertEquals(OK, verdict.reason());
    }

    @Test
    void damageOverTheCapIsRejected() {
        SelfDamageGuard.Verdict verdict = SelfDamageGuard.check(14.0, CAP, 20.0);
        assertFalse(verdict.allowed());
        assertEquals(OVER_CAP, verdict.reason());
    }

    @Test
    void damageExactlyOnTheCapStaysAllowed() {
        // Der Deckel ist eine Obergrenze, keine Schranke: "bis 12 erlaubt" heisst 12 erlaubt.
        // Sonst verliert man die erste nach oben offene Stelle der eigenen Settings.
        assertTrue(SelfDamageGuard.check(CAP, CAP, 20.0).allowed());
    }

    @Test
    void lethalPlacementIsRejectedEvenBelowTheCap() {
        // Der Kern von D8: 12 Schaden liegen unter dem Deckel von 20, treffen aber exakt die
        // verbleibenden 12 HP nach einem Totem-Pop. Der alte Vergleich gegen max-self-damage wuerde
        // das durchlassen - der Bot tauscht 1.2 Crystals gegen seinen Tod.
        SelfDamageGuard.Verdict verdict = SelfDamageGuard.check(12.0, 20.0, 12.0);
        assertFalse(verdict.allowed());
        assertEquals(LETHAL, verdict.reason());
    }

    @Test
    void damageEqualToTotalHealthIsAlreadyLethal() {
        // Vanilla setzt den Tod, sobald der Schaden die Lebensenergie erreicht - ">=" und nicht
        // "> ". Bei exakt gleichem Wert darf die Platzierung nicht durchfallen.
        SelfDamageGuard.Verdict verdict = SelfDamageGuard.check(20.0, 20.0, 20.0);
        assertFalse(verdict.allowed());
        assertEquals(LETHAL, verdict.reason());
    }

    @Test
    void absorptionCountsTowardsTotalHealth() {
        // getTotalHealth() statt getHealth(): dieselbe rohe Lebensenergie, aber eine Absorptionshaube
        // macht daraus weniger EHP. Ohne sie waere diese Platzierung freigegeben worden.
        assertFalse(SelfDamageGuard.allows(20.0, 30.0, 6.0 + 14.0));
    }

    @Test
    void lethalReasonWinsOverTheCap() {
        // Trifft beides zu, ist "wuerde mich toeten" die Diagnose, die etwas aendert - der Deckel ist
        // einstellbar, der Tote nicht.
        SelfDamageGuard.Verdict verdict = SelfDamageGuard.check(40.0, 12.0, 20.0);
        assertEquals(LETHAL, verdict.reason());
    }

    @Test
    void allowsAgreesWithTheVerdict() {
        assertTrue(SelfDamageGuard.allows(2.0, CAP, 20.0));
        assertFalse(SelfDamageGuard.allows(13.0, CAP, 20.0));
    }
}
