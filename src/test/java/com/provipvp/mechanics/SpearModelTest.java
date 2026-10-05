package com.provipvp.mechanics;

import com.provipvp.mechanics.SpearModel.Attack;
import com.provipvp.mechanics.SpearModel.ChargeState;
import com.provipvp.mechanics.SpearModel.CooldownState;
import com.provipvp.mechanics.SpearModel.Verdict;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Der Spear ist die Waffe, die dem Addon die ganze 3-Block-Nahkampfannahme kippt: Reichweite bis 4.5,
 *  erzwungener 100-%-Cooldown beim Jab, kein Crit und kein Sprint-Knockback, und ein Charge-Angriff, dessen
 *  Schaden an der Annaeherungsgeschwindigkeit haengt statt am Cooldown. Jeder Test hier prueft eine dieser
 *  Entscheidungen, nicht den Zustand eines Feldes. */
class SpearModelTest {

    private static final double BASE_DAMAGE = 6.0;

    /** Voll geladen und voller Cooldown — der Referenzfall, gegen den alles andere gemessen wird. */
    private static CooldownState full() {
        return new CooldownState(1.0);
    }

    // ---------- Jab: 100 % Cooldown, egal was der Bot gerade hat ----------

    @Test
    void jabBeiHalbemCooldownRichtetVollenSchadenAn() {
        Verdict halb = SpearModel.resolve(Attack.JAB, 0.0, 0, 0, new CooldownState(0.5), BASE_DAMAGE);
        Verdict voll = SpearModel.resolve(Attack.JAB, 0.0, 0, 0, full(), BASE_DAMAGE);

        assertEquals(voll.damage(), halb.damage(), 1e-9,
            "Der Jab wird serverseitig auf 100 % Cooldown gezwungen — 50 % duerfen nicht weniger Schaden bringen.");
        assertEquals(BASE_DAMAGE, halb.damage(), 1e-9);
    }

    @Test
    void jabUmgehtDasCooldownGate() {
        // 0.5 liegt unter dem 0.9-Gate, das der Bot sonst ueberall hart verdrahtet hat.
        Verdict jab = SpearModel.resolve(Attack.JAB, 0.0, 0, 0, new CooldownState(0.5), BASE_DAMAGE);
        Verdict charge = SpearModel.resolve(Attack.CHARGE, 0.0, 60, 0, new CooldownState(0.5), BASE_DAMAGE);

        assertTrue(jab.cooldownGatePassed());
        assertTrue(jab.damageDealt());
        assertFalse(charge.cooldownGatePassed(), "Der Charge-Angriff haengt weiterhin am normalen Gate.");
        assertFalse(charge.damageDealt());
    }

    // ---------- Kein Crit, kein Sprint-Knockback ----------

    @Test
    void jabLiefertWederCritNochSprintKnockback() {
        Verdict jab = SpearModel.resolve(Attack.JAB, 5.0, 0, 5, full(), BASE_DAMAGE);

        assertFalse(jab.crit());
        assertFalse(jab.sprintKnockback());
        // Knockback an sich gibt es sehr wohl — nur nicht den Sprint-Aufschlag. Genau diese Unterscheidung
        // entscheidet, ob der Bot sein W-Tap/Sprint-Reset ueberhaupt braucht.
        assertTrue(jab.knockedBack());
        assertEquals(SpearModel.JAB_KNOCKBACK, jab.knockback(), 1e-9);
    }

    @Test
    void auchDerChargeAngriffKannNichtCritten() {
        Verdict charge = SpearModel.resolve(Attack.CHARGE, 6.0, 60, 0, full(), BASE_DAMAGE);

        assertFalse(charge.crit());
        assertFalse(charge.sprintKnockback());
    }

    // ---------- Charge: Geschwindigkeit ist das Gate ----------

    @Test
    void oberhalbDerKnockbackSchwelleStoesstDerChargeZurueck() {
        Verdict knockback = SpearModel.resolve(Attack.CHARGE, 5.2, 60, 0, full(), BASE_DAMAGE);

        assertTrue(knockback.damageDealt());
        assertTrue(knockback.knockedBack());
        assertEquals(SpearModel.CHARGE_KNOCKBACK, knockback.knockback(), 1e-9);
    }

    @Test
    void zwischenDenSchwellenTrifftDerChargeOhneKnockback() {
        // 4.7 b/s: ueber der Schadenschwelle 4.6, unter der Knockback-Schwelle 5.1. Genau dieses Band
        // ist der Grund, warum ein Bot nie "Charge heisst automatisch Wegschieben" annehmen darf.
        Verdict nurSchaden = SpearModel.resolve(Attack.CHARGE, 4.7, 60, 0, full(), BASE_DAMAGE);

        assertTrue(nurSchaden.damageDealt(), "4.7 b/s liegt ueber der Schadenschwelle von 4.6.");
        assertFalse(nurSchaden.knockedBack(), "4.7 b/s liegt unter der Knockback-Schwelle von 5.1.");
        assertEquals(0.0, nurSchaden.knockback(), 1e-9);
    }

    @Test
    void genauAnDerKnockbackSchwelleStoesstDerChargeNochZurueck() {
        assertTrue(SpearModel.resolve(Attack.CHARGE, SpearModel.CHARGE_KNOCKBACK_SPEED, 60, 0, full(), BASE_DAMAGE).knockedBack());
    }

    @Test
    void chargeUnterDerSchadenschwelleRichtetGarNichtsAn() {
        Verdict zuLangsam = SpearModel.resolve(Attack.CHARGE, 4.5, 60, 0, full(), BASE_DAMAGE);
        assertFalse(zuLangsam.damageDealt());
        assertEquals(0.0, zuLangsam.damage(), 1e-9);
        assertFalse(zuLangsam.knockedBack());
    }

    @Test
    void staerkeVeraendertDenChargeSchadenNicht() {
        Verdict ohneStaerke = SpearModel.resolve(Attack.CHARGE, 6.0, 60, 0, full(), BASE_DAMAGE);
        Verdict mitStaerke = SpearModel.resolve(Attack.CHARGE, 6.0, 60, 5, full(), BASE_DAMAGE);

        assertEquals(ohneStaerke.damage(), mitStaerke.damage(), 1e-9);
        assertFalse(mitStaerke.strengthApplied());
    }

    @Test
    void staerkeVeraendertDenJabSchadenSehrWohl() {
        Verdict ohneStaerke = SpearModel.resolve(Attack.JAB, 0.0, 0, 0, full(), BASE_DAMAGE);
        Verdict mitStaerke = SpearModel.resolve(Attack.JAB, 0.0, 0, 5, full(), BASE_DAMAGE);

        assertTrue(mitStaerke.strengthApplied());
        assertEquals(5.0, mitStaerke.damage() - ohneStaerke.damage(), 1e-9);
    }

    // ---------- Die drei Ladungszustaende ----------

    @Test
    void dieDreiZustaendeSindUeberDieHaltezeitErreichbar() {
        assertEquals(ChargeState.DISENGAGED, ChargeState.of(0));
        assertEquals(ChargeState.TIRED, ChargeState.of(20));
        assertEquals(ChargeState.ENGAGED, ChargeState.of(SpearModel.CHARGE_FULL_TICKS));
    }

    @Test
    void entladenerSpeerKannDenChargeNichtAusfuehren() {
        Verdict entladen = SpearModel.resolve(Attack.CHARGE, 9.0, 0, 0, full(), BASE_DAMAGE);

        assertEquals(ChargeState.DISENGAGED, entladen.chargeState());
        assertFalse(entladen.damageDealt());
    }

    @Test
    void tiredTrifftAberStoesstNicht() {
        Verdict tired = SpearModel.resolve(Attack.CHARGE, 9.0, 25, 0, full(), BASE_DAMAGE);

        assertEquals(ChargeState.TIRED, tired.chargeState());
        assertTrue(tired.damageDealt(), "Auch ein tired Spear schlaegt zu.");
        assertFalse(tired.knockedBack(), "Aber er stoesst nicht zurueck — nur ENGAGED kann das.");
    }

    @Test
    void halbeLadungRichtetWenigerSchadenAlsVolleLadungAn() {
        Verdict tired = SpearModel.resolve(Attack.CHARGE, 6.0, 25, 0, full(), BASE_DAMAGE);
        Verdict engaged = SpearModel.resolve(Attack.CHARGE, 6.0, 60, 0, full(), BASE_DAMAGE);

        assertTrue(tired.damage() < engaged.damage(),
            "Die Haltezeit muss den Schaden wirklich veraendern, sonst waere chargeTicks ein totes Argument.");
    }

    // ---------- Reichweite: hier kippt die 3.6-Default ----------

    @Test
    void derSpearTrifftJenseitsDerAddonDefaultReichweite() {
        assertTrue(SpearModel.withinReach(4.0), "4.0 liegt ueber attack-range 3.6 — der Bot wuerde nie schlagen.");
        assertEquals(4.5, SpearModel.requiredAttackRange(), 1e-9);
    }

    @Test
    void dieAktuelleDefaultReichweiteDecktDenSpearNicht() {
        assertFalse(SpearModel.attackRangeCoversSpear(3.6));
        assertFalse(SpearModel.attackRangeCoversSpear(3.4), "Auch der HumanPvP-Default ist zu kurz.");
        assertTrue(SpearModel.attackRangeCoversSpear(4.5));
    }

    @Test
    void unterhalbDerMindestreichweiteTrifftDerSpearNicht() {
        assertFalse(SpearModel.withinReach(1.5));
        assertFalse(SpearModel.withinReach(4.6), "Ueber 4.5 ist auch der Spear zu weit.");
        assertTrue(SpearModel.withinReach(SpearModel.MIN_REACH));
    }
}