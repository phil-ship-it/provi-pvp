package com.provipvp.crystal;

import com.provipvp.crystal.AttackGate.AttackState;
import com.provipvp.crystal.AttackGate.Reason;
import com.provipvp.crystal.AttackGate.Verdict;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Deckt die drei Angriffs-Gates ab: jedes einzeln gegen seine Bedingung, und die kombinierte
 *  Auswertung darauf, WELCHEN Grund sie nennt, wenn mehrere Gates gleichzeitig sperren. */
class AttackGateTest {

    /** Ein unauffaelliger Angriff: Ziel nicht gepoppt, Cooldown voll, Ziel weit vom Tod weg.
     *  Der geplante Crit-Schaden (9.0 Basis + 50 %) laesst 20 HP deutlich stehen. */
    private static AttackState normal() {
        return new AttackState(0, 1.0, 20.0, 13.5);
    }

    @Test
    void attackIsBlockedWhileTheTargetIsInItsPopWindow() {
        Verdict verdict = AttackGate.gateInvulnerability(10);
        assertFalse(verdict.allowed());
        assertEquals(Reason.TARGET_INVULNERABLE, verdict.reason());
    }

    @Test
    void theLastTickOfThePopWindowStillBlocks() {
        // 10 Ticks sind 0.5 s. Ein "hurtTime >= 2"-Zweig wuerde hier bereits freigeben und den
        // Schlag einen Tick zu frueh in das Fenster schieben, in dem der Server ihn verwirft.
        assertFalse(AttackGate.gateInvulnerability(1).allowed());
    }

    @Test
    void attackPassesOnceThePopWindowIsOver() {
        assertTrue(AttackGate.gateInvulnerability(0).allowed());
    }

    // ---------- D9: Angriffs-Cooldown ----------

    @Test
    void attackIsBlockedWhileTheCooldownIsStillCharging() {
        Verdict verdict = AttackGate.gateStrength(0.62, AttackGate.FULL_STRENGTH);
        assertFalse(verdict.allowed());
        assertEquals(Reason.STRENGTH_NOT_READY, verdict.reason());
    }

    @Test
    void attackPassesOnAFullyChargedCooldown() {
        assertTrue(AttackGate.gateStrength(1.0, AttackGate.FULL_STRENGTH).allowed());
    }

    @Test
    void theOldNinetyFivePercentThresholdWouldHavePassed() {
        // Beide Module schwingen heute ab 0.9/0.95 - das ist der Loss, den D9 zurueckholt.
        assertTrue(AttackGate.gateStrength(0.95, 0.95).allowed());
        assertFalse(AttackGate.gateStrength(0.95, AttackGate.FULL_STRENGTH).allowed());
    }

    // ---------- D10: Sprung-Crit nur wenn toedlich ----------

    @Test
    void critIsSkippedWhenItWouldNotKill() {
        // 18 HP stehen, der Sprung-Crit bringt 13.5 - derselbe Pop, aber mit sichtbarem Luftsprung.
        Verdict verdict = AttackGate.gateLethalCrit(18.0, 13.5);
        assertFalse(verdict.allowed());
        assertEquals(Reason.CRIT_NOT_LETHAL, verdict.reason());
    }

    @Test
    void critIsKeptWhenItWouldKill() {
        // Vanilla setzt den Tod, sobald der Schaden die Lebensenergie ERREICHT - der Gleichheits-
        // fall gehoert also zu "toetet", nicht zu "toetet knapp nicht".
        assertTrue(AttackGate.gateLethalCrit(13.5, 13.5).allowed());
        assertTrue(AttackGate.shouldJumpCrit(13.5, 13.5));
        assertFalse(AttackGate.shouldJumpCrit(14.0, 13.5));
    }

    // ---------- kombinierte Auswertung ----------

    @Test
    void aNormalAttackPassesAndDoesNotJump() {
        Verdict verdict = AttackGate.evaluate(normal());
        assertTrue(verdict.allowed());
        assertEquals(Reason.NONE, verdict.reason());
        assertFalse(verdict.jumpCrit());
    }

    @Test
    void aLethalTargetMakesTheJumpWorthIt() {
        Verdict verdict = AttackGate.evaluate(new AttackState(0, 1.0, 8.0, 13.5));
        assertTrue(verdict.allowed());
        assertTrue(verdict.jumpCrit());
    }

    @Test
    void thePopWindowIsNamedWhenItAndTheCooldownBothBlock() {
        // Reihenfolge ist Teil der API: der erste verletzte Gate gewinnt und benennt den Grund.
        Verdict verdict = AttackGate.evaluate(new AttackState(4, 0.4, 20.0, 9.0));
        assertFalse(verdict.allowed());
        assertEquals(Reason.TARGET_INVULNERABLE, verdict.reason());
    }

    @Test
    void theCooldownIsNamedWhenOnlyItAndTheCritBlock() {
        Verdict verdict = AttackGate.evaluate(new AttackState(0, 0.8, 20.0, 9.0));
        assertFalse(verdict.allowed());
        assertEquals(Reason.STRENGTH_NOT_READY, verdict.reason());
    }

    @Test
    void aBlockedAttackNeverCarriesAJumpIntention() {
        Verdict verdict = AttackGate.evaluate(new AttackState(3, 1.0, 2.0, 13.5));
        assertFalse(verdict.allowed());
        // Das Ziel waere durch den Sprung-Crit zu toeten - er wird trotzdem nicht ausgefuehrt.
        assertFalse(verdict.jumpCrit());
    }

    @Test
    void aNonLethalCritDoesNotBlockTheAttackItself() {
        Verdict verdict = AttackGate.evaluate(new AttackState(0, 1.0, 20.0, 13.5));
        assertTrue(verdict.allowed());
        assertEquals(Reason.NONE, verdict.reason());
        assertFalse(verdict.jumpCrit());
    }
}
