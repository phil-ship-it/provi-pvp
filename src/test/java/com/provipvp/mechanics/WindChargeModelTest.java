package com.provipvp.mechanics;

import com.provipvp.mechanics.WindChargeModel.WindBurst;
import com.provipvp.mechanics.WindChargeModel.WindCharge;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Zwei voellig verschiedene Wind-Quellen und eine dritte, die gar keine ist: Wind Burst ist eine
 *  Mace-Verzauberung, kein Wind-Item. Die Fallschaden-Regel ist hier die wichtigste Zahl der ganzen Datei —
 *  wer sie uebersieht, plant mit dem Kick eine Flucht, die beim Landen tödlich endet. */
class WindChargeModelTest {

    // ---------- Wind Burst: die drei dokumentierten Stufen ----------

    @Test
    void windBurstLiefertDieDreiDokumentiertenStufen() {
        WindBurst i = WindChargeModel.windBurst(1);
        WindBurst ii = WindChargeModel.windBurst(2);
        WindBurst iii = WindChargeModel.windBurst(3);

        assertEquals(8.0, i.launchBlocks(), 1e-9);
        assertEquals(16.0, ii.launchBlocks(), 1e-9);
        assertEquals(24.0, iii.launchBlocks(), 1e-9);

        assertEquals(1.2, i.multiplier(), 1e-9);
        assertEquals(1.75, ii.multiplier(), 1e-9);
        assertEquals(2.2, iii.multiplier(), 1e-9);
    }

    @Test
    void windBurstSchiesstDenSpielerSelbstInDieLuft() {
        assertEquals(9.6, WindChargeModel.windBurst(1).totalLaunchBlocks(), 1e-9);
        assertEquals(28.0, WindChargeModel.windBurst(2).totalLaunchBlocks(), 1e-9);
        assertEquals(52.8, WindChargeModel.windBurst(3).totalLaunchBlocks(), 1e-9);
    }

    @Test
    void windBurstNegiertDenFallschadenNicht() {
        for (int level = 1; level <= WindChargeModel.WIND_BURST_LEVELS; level++) {
            assertFalse(WindChargeModel.windBurst(level).negatesFallDamage(),
                "Stufe " + level + " nimmt beim Landen vollen Fallschaden.");
        }
    }

    @Test
    void windBurstWirktNurAufDenSmash() {
        for (int level = 1; level <= WindChargeModel.WIND_BURST_LEVELS; level++) {
            assertTrue(WindChargeModel.windBurst(level).smashOnly());
        }
    }

    @Test
    void ohneVerzauberungGibtEsKeinenWindBurst() {
        assertNull(WindChargeModel.windBurst(0));
        assertNull(WindChargeModel.windBurst(4));
    }

    // ---------- Handgeworfene Wind Charge ----------

    @Test
    void dieHandgeworfeneChargeIstKleinAberStark() {
        WindCharge charge = WindChargeModel.playerCharge();

        assertTrue(charge.obtainable());
        assertEquals(1.0f, charge.damage(), 1e-9);
        assertEquals(2.4, charge.radius(), 1e-9);
        assertEquals(1.22, charge.knockbackMultiplier(), 1e-9);
    }

    @Test
    void dieHandgeworfeneChargeTrifftInnerhalbVonZweieVierBloecken() {
        WindCharge charge = WindChargeModel.playerCharge();

        assertTrue(charge.hits(2.3));
        assertTrue(charge.hits(2.4), "Der Rand gehoert noch zur Kugel.");
        assertFalse(charge.hits(2.5));
    }

    @Test
    void nachEinemWurfIstHalbeSekundePause() {
        assertFalse(WindChargeModel.playerChargeReady(9));
        assertTrue(WindChargeModel.playerChargeReady(10));
        assertEquals(0.5, WindChargeModel.PLAYER_CHARGE_COOLDOWN_TICKS / 20.0, 1e-9);
    }

    // ---------- Breeze Shot ----------

    @Test
    void derBreezeShotIstBreitSchwachUndNichtErhaeltlich() {
        WindCharge shot = WindChargeModel.breezeShot();

        assertFalse(shot.obtainable(), "Ein Breeze Shot ist kein Item — der Bot kann ihn nie werfen.");
        assertEquals(6.0, shot.radius(), 1e-9);
        assertEquals(0.6, shot.knockbackMultiplier(), 1e-9);
        assertEquals(0.0f, shot.damage(), 1e-9);
    }

    @Test
    void derBreezeShotWirftWeiterAberSchwaecherAlsDieHandCharge() {
        WindCharge shot = WindChargeModel.breezeShot();
        WindCharge charge = WindChargeModel.playerCharge();

        assertTrue(shot.radius() > charge.radius());
        assertTrue(shot.knockbackMultiplier() < charge.knockbackMultiplier());
    }

    @Test
    void ausserhalbDerKugelGibtEsKeinenKnockback() {
        assertEquals(0.0, WindChargeModel.breezeShot().knockbackAt(9.0), 1e-9);
        assertEquals(0.6, WindChargeModel.breezeShot().knockbackAt(3.0), 1e-9);
    }
}