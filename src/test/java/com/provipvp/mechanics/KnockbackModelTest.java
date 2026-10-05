package com.provipvp.mechanics;

import com.provipvp.mechanics.KnockbackModel.NativeResistance;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Knockback-Widerstand ist pro Entity zu lesen. Der teuerste Fehler ist die Annahme "40 % bei Netherite,
 *  also kann man den Gegner wegschieben" — bei Blast Protection stimmt das nicht, und bei Creaking schon
 *  gar nicht. */
class KnockbackModelTest {

    // ---------- Netherite: 10 % pro Stueck ----------

    @Test
    void netheriteImVollsatzHaeltVierzigProzentZurueck() {
        assertEquals(0.6, KnockbackModel.effectiveKnockbackFactor(NativeResistance.NONE, false, 4, 0), 1e-9);
    }

    @Test
    void jedeWeitereNetheriteStueckWirktLinear() {
        assertEquals(1.0, KnockbackModel.effectiveKnockbackFactor(NativeResistance.NONE, false, 0, 0), 1e-9);
        assertEquals(0.9, KnockbackModel.effectiveKnockbackFactor(NativeResistance.NONE, false, 1, 0), 1e-9);
        assertEquals(0.8, KnockbackModel.effectiveKnockbackFactor(NativeResistance.NONE, false, 2, 0), 1e-9);
        assertEquals(0.7, KnockbackModel.effectiveKnockbackFactor(NativeResistance.NONE, false, 3, 0), 1e-9);
    }

    // ---------- Blast Protection: additiv pro Stueck, nur bei Explosionen ----------

    @Test
    void zweiBlastProtectionStueckeAufIVSchaltenDenKnockbackAus() {
        // 2 x 10 % Netherite + 2 x 4 x 15 % Blast Protection = 140 %, gedeckelt auf 100 % -> gar kein KB.
        assertEquals(0.0, KnockbackModel.effectiveKnockbackFactor(NativeResistance.NONE, true, 2, 4), 1e-9);
    }

    @Test
    void eineBlastProtectionStueckeAufIVLaesstNochDreissigProzentUebrig() {
        // 10 % Netherite + 4 x 15 % Blast Protection = 70 % Widerstand.
        assertEquals(0.3, KnockbackModel.effectiveKnockbackFactor(NativeResistance.NONE, true, 1, 4), 1e-9);
    }

    @Test
    void blastProtectionZaehltBeiNichtExplosiverQuelleNicht() {
        double explosiv = KnockbackModel.effectiveKnockbackFactor(NativeResistance.NONE, true, 4, 4);
        double melee = KnockbackModel.effectiveKnockbackFactor(NativeResistance.NONE, false, 4, 4);

        assertEquals(0.0, explosiv, 1e-9);
        // Sonst wuerde der Bot aus Blast Protection schliessen, Nahkampf sei sinnlos — er ist es nicht.
        assertEquals(0.6, melee, 1e-9);
    }

    // ---------- Native Werte der neuen Mobs ----------

    @Test
    void einCreakingIstBeiBlickkontaktVollstaendigFest() {
        assertEquals(0.0, KnockbackModel.effectiveKnockbackFactor(NativeResistance.CREAKING, false, 4, 0), 1e-9);
    }

    @Test
    void dieBlickrichtungEntscheidetUeberDasCreaking() {
        assertEquals(NativeResistance.NONE, NativeResistance.lookingAt(NativeResistance.CREAKING, false));
        assertEquals(NativeResistance.CREAKING, NativeResistance.lookingAt(NativeResistance.CREAKING, true));

        assertTrue(KnockbackModel.canBeKnockedBack(NativeResistance.CREAKING, false));
        assertFalse(KnockbackModel.canBeKnockedBack(NativeResistance.CREAKING, true));
    }

    @Test
    void einNautilusHaeltDreissigProzent() {
        assertEquals(0.7, KnockbackModel.effectiveKnockbackFactor(NativeResistance.NAUTILUS, true, 0, 0), 1e-9);
    }

    @Test
    void agentUndNpcSindZusaetzlichUnverwundbar() {
        assertTrue(NativeResistance.AGENT.invulnerable());
        assertTrue(NativeResistance.NPC.invulnerable());
        assertFalse(NativeResistance.CREAKING.invulnerable());
    }

    @Test
    void nativeWiderstaendeSindEinDeckelKeinSummand() {
        // Ein Creaking hat bereits 100 % — Blast Protection darueber kann nichts mehr ausrichten.
        assertEquals(0.0, KnockbackModel.effectiveKnockbackFactor(NativeResistance.CREAKING, true, 4, 4), 1e-9);
    }

    // ---------- Negative Attributwerte (26.2 senkt das Minimum auf -2.0) ----------

    @Test
    void einUeberHundertProzentWiderstandErzeugtKeinenNegativenFaktor() {
        // Negativer Anteil hiesse Anziehung — das waere ein Bewegungsfehler, kein Bonus.
        assertEquals(0.0, KnockbackModel.effectiveKnockbackFactor(1.4, false, 4, 4), 1e-9);
        assertEquals(0.0, KnockbackModel.effectiveKnockbackFactor(1.0, false, 0, 0), 1e-9);
    }

    @Test
    void dasAttributMinimumErhaeltDenVerstaerktenKnockback() {
        assertEquals(3.0, KnockbackModel.effectiveKnockbackFactor(-2.0, false, 0, 0), 1e-9);
        assertEquals(1.2, KnockbackModel.effectiveKnockbackFactor(-0.2, false, 0, 0), 1e-9);
    }

    @Test
    void netheriteFrisstDenBonusNichtAufNull() {
        // -2.0 plus 40 % Netherite bleibt deutlich ueber 1.0: das Minimum darf nicht weggeklemmt werden.
        double factor = KnockbackModel.effectiveKnockbackFactor(-2.0, false, 4, 0);

        assertTrue(factor > 1.0);
        assertEquals(2.6, factor, 1e-9);
    }

    // ---------- Lohnt sich die naechste Pop-Kette? ----------

    @Test
    void gegenEinCreakingLohntSichKeinePopKette() {
        assertFalse(KnockbackModel.worthChasing(NativeResistance.CREAKING, true));
        assertTrue(KnockbackModel.worthChasing(NativeResistance.NAUTILUS, true));
    }
}