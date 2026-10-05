package com.provipvp.mechanics;

import com.provipvp.mechanics.SlowFallingArrow.Plan;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Slow Falling ist die vom Wiki empfohlene Gegenmassnahme gegen Crystal-PvP: der Getroffene kann nicht
 *  critten und nicht smashen, haengt aber ~31x laenger in der Luft — das ist der Grund, warum sich die
 *  selbe Pop-Kette mehrfach an ihn setzen laesst. */
class SlowFallingArrowTest {

    private static final double FALL_HEIGHT = 6.0;

    private static Plan slowed(int restTicks) {
        return SlowFallingArrow.evaluate(FALL_HEIGHT, restTicks);
    }

    private static Plan normal() {
        return SlowFallingArrow.evaluate(FALL_HEIGHT, 0);
    }

    // ---------- Was der Effekt wegnimmt ----------

    @Test
    void mitEffektSindCritUndSmashWeg() {
        Plan plan = slowed(30);

        assertFalse(plan.critAvailable());
        assertFalse(plan.smashAvailable());
    }

    @Test
    void ohneEffektSindBeideVerfuegbar() {
        Plan plan = normal();

        assertTrue(plan.critAvailable());
        assertTrue(plan.smashAvailable());
    }

    @Test
    void dieSmashSchwelleWirdBeiAktivemEffektUnendlich() {
        assertEquals(Double.POSITIVE_INFINITY, slowed(30).smashFallBlocks());
        assertEquals(SlowFallingArrow.SMASH_FALL_BLOCKS, normal().smashFallBlocks(), 1e-9);
    }

    // ---------- Wann der Crit-Plan wieder greift ----------

    @Test
    void derCritPlanKommtMitDemEndeDesEffektsZurueck() {
        assertEquals(30, slowed(30).critDelayTicks());
        assertEquals(3, slowed(3).critDelayTicks());
        assertEquals(0, normal().critDelayTicks());
    }

    // ---------- Der eigentliche Gewinn: das Pop-Fenster ----------

    @Test
    void slowFallingVerlaengertDasPopFensterAufDasVielfache() {
        Plan kurz = normal();
        Plan lang = slowed(30);

        assertTrue(lang.popWindowTicks() > kurz.popWindowTicks() * 20,
            "0.125 b/s statt 3.92 b/s sind rund 31x so lange in der Luft — das ist die eigentliche Waffe.");
    }

    @Test
    void ohneEffektPasstKaumEinPopInDasFenster() {
        // 6 Bloecke bei 3.92 b/s sind 2 Ticks — das reicht nicht einmal fuer die i-Frames nach einem Pop.
        assertEquals(0, normal().maxRepeatedPops());
    }

    @Test
    void mitEffektPassenMehrerePopZyklenInDasselbeFenster() {
        Plan plan = slowed(30);

        assertTrue(plan.maxRepeatedPops() >= 4,
            "6 Bloecke bei 0.125 b/s sind 48 Ticks, also 4 volle i-Frame-Zyklen.");
    }

    // ---------- Randfaelle ----------

    @Test
    void amBodenGibtEsKeinFenster() {
        Plan plan = SlowFallingArrow.evaluate(0.0, 30);

        assertEquals(0, plan.popWindowTicks());
        assertEquals(0, plan.maxRepeatedPops());
    }

    @Test
    void ohneFallhoeheIstDerSmashGrundsatzlichNichtErreichbar() {
        assertFalse(SlowFallingArrow.hasFallToExploit(1.0));
        assertTrue(SlowFallingArrow.hasFallToExploit(1.5));
    }
}