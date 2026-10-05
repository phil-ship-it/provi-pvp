package com.provipvp.crystal;

import com.provipvp.crystal.CrystalToolPolicy.Tool;
import com.provipvp.crystal.CrystalToolPolicy.Verdict;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static com.provipvp.crystal.CrystalToolPolicy.NO_SLOT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Deckt die Werkzeug-Frage des Crystal-Schlags ab: taugt das Gehaltene, welcher Slot muss
 *  gewechselt werden, und wann gar nicht erst geschwungen werden darf. */
class CrystalToolPolicyTest {

    private final CrystalToolPolicy policy = new CrystalToolPolicy();

    @Test
    void theHeldToolIsUsedWithoutSwitchingSlots() {
        List<Tool> hotbar = List.of(Tool.OTHER, Tool.OTHER, Tool.SWORD, Tool.OTHER);
        Verdict verdict = policy.evaluate(2, hotbar);
        assertTrue(verdict.canAttack());
        assertEquals(NO_SLOT, verdict.switchToSlot());
        assertFalse(verdict.needsSwitch());
    }

    @Test
    void aWrongToolInHandReturnsTheRequiredSlot() {
        // Leere Hand, Schwert in Slot 3: ohne den Wechsel waere der Schlag ein Treffer ins Leere,
        // das den Angriffs-Cooldown des Gegners trotzdem verbraucht.
        List<Tool> hotbar = List.of(Tool.OTHER, Tool.OTHER, Tool.OTHER, Tool.SWORD);
        Verdict verdict = policy.evaluate(-1, hotbar);
        assertTrue(verdict.canAttack());
        assertTrue(verdict.needsSwitch());
        assertEquals(3, verdict.switchToSlot());
        assertEquals(Tool.SWORD, verdict.needed());
        assertEquals(Tool.HAND, verdict.held());
    }

    @Test
    void aNonWeaponInHandAlsoReturnsTheRequiredSlot() {
        // Der haeufigste reale Fall: der Bot haelt Cobblestone/Brot, weil er eben einen Block
        // gesetzt hat. OTHER ist fuer die Regel "kein Werkzeug".
        List<Tool> hotbar = List.of(Tool.OTHER, Tool.OTHER, Tool.OTHER, Tool.SWORD);
        Verdict verdict = policy.evaluate(0, hotbar);
        assertTrue(verdict.canAttack());
        assertEquals(3, verdict.switchToSlot());
        assertEquals(Tool.SWORD, verdict.needed());
    }

    @Test
    void nothingIsThrownWhenNoToolInTheHotbarCanBreakTheCrystal() {
        // Ohne brauchbares Werkzeug wird nicht geschwungen: ein wirkungsloser Schlag kostet den
        // Cooldown und zeigt dem Gegner, dass der Bot zielt und nicht trifft.
        Verdict verdict = policy.evaluate(0, Arrays.asList(Tool.OTHER, Tool.OTHER, null, Tool.HAND));
        assertFalse(verdict.canAttack());
        assertNull(verdict.needed());
    }

    @Test
    void emptySlotsAreSkippedWhileSearching() {
        List<Tool> hotbar = Arrays.asList(null, null, Tool.OTHER, null, Tool.SWORD, null);
        Verdict verdict = policy.evaluate(0, hotbar);
        assertTrue(verdict.canAttack());
        assertEquals(4, verdict.switchToSlot());
    }

    @Test
    void anEmptyHandWithAToolInSlotZeroReturnsSlotZero() {
        Verdict verdict = policy.evaluate(-1, List.of(Tool.SWORD, Tool.OTHER));
        assertTrue(verdict.canAttack());
        assertEquals(0, verdict.switchToSlot());
    }

    @Test
    void theInjectedRuleDecidesWhatCountsAsAWeapon() {
        // Die Werkzeug-Regel ist serverabhaengig (Java vs. Bedrock). Hier gilt nur die Spitzhacke -
        // das naechstliegende Schwert in Slot 1 darf nicht gewaehlt werden.
        CrystalToolPolicy pickaxeOnly = new CrystalToolPolicy(tool -> tool == Tool.PICKAXE);
        List<Tool> hotbar = List.of(Tool.SWORD, Tool.PICKAXE, Tool.SWORD);
        Verdict verdict = pickaxeOnly.evaluate(-1, hotbar);
        assertTrue(verdict.canAttack());
        assertEquals(1, verdict.switchToSlot());
    }

    @Test
    void aRuleThatAllowsTheEmptyHandNeedsNoSwitchAtAll() {
        CrystalToolPolicy anything = new CrystalToolPolicy(tool -> true);
        Verdict verdict = anything.evaluate(-1, List.of(Tool.OTHER, Tool.OTHER));
        assertTrue(verdict.canAttack());
        assertEquals(NO_SLOT, verdict.switchToSlot());
    }
}
