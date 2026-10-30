package com.provipvp.crystal;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * R3-Test fuer ExplosionSolver: prueft die beobachtbare Schadens-Entscheidung
 * (Expositions-Anteil -> effektiver Schaden), nicht Getter/Plumbing.
 */
class ExplosionSolverTest {

    @Test
    void volleExponiertGibtVollenSchaden() {
        assertEquals(12.0, ExplosionSolver.effectiveDamage(12.0, 9), 0.01);
    }

    @Test
    void halbExponiertHalbiert() {
        assertEquals(6.0, ExplosionSolver.effectiveDamage(12.0, 4), 0.01);
    }

    @Test
    void nullExponiertGibtNull() {
        assertEquals(0.0, ExplosionSolver.effectiveDamage(12.0, 0), 0.01);
    }

    @Test
    void ueberNeunWirdGekappt() {
        assertEquals(12.0, ExplosionSolver.effectiveDamage(12.0, 99), 0.01);
    }
}
