package com.provipvp.pearl;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Deckt die reine, zustandslose Mathematik der taktischen Wurfentscheidung ab: den Praediktions-
 * versatz des Gap Closers und die Auswahl des Bogen-Kandidaten im Terrain Bypass.
 *
 * <p>Beides ist bewusst ueber {@code Vec3}-Werte und nicht ueber echte Entities formuliert. Die
 * Entity-seitigen Aussagen - "der Bewegungsvektor eines strafenden Ziels liefert einen anderen
 * Vorhalt als der eines vorwaerts weglaufenden", "die Sichtlinie auf die Vorhersage ist blockiert,
 * also greift Szenario 2", "es gibt 1-3 Bloecke voraus eine Standflaeche" - bleiben hier bewusst
 * ungetestet: sie brauchen Block-Registries, Raycasts und einen echten Entity-Zustand und sind
 * ohne laufenden Minecraft-Client nicht isolierbar. Sie leben in {@code ExplosionScanner}
 * (Levelzugriff) bzw. im Aufruf in {@code GodmodePvP}.
 *
 * <p>Ebenso ungetestet bleibt die Ballistik selbst ({@code PvpMath.solvePearlAim}) - sie ist in
 * {@code PvpMathTest} bereits abgedeckt und wird hier nur aufgerufen, nicht nachgebaut.
 */
class PearlAimMathTest {

    // ---------- predictLead: Praediktionsversatz ----------

    @Test
    void predictLeadStaysAtPositionWithoutTicks() {
        Vec3 pos = new Vec3(10, 64, -5);
        Vec3 vel = new Vec3(0.3, 0, 0.1);
        assertEquals(pos, PearlSolver.predictLead(pos, vel, 0, false));
        assertEquals(pos, PearlSolver.predictLead(pos, vel, -5, false));
    }

    @Test
    void predictLeadExtrapolatesLinearlyOnGround() {
        Vec3 pos = new Vec3(0, 64, 0);
        // 0.3 Bloecke/Tick Sprinttempo Nord, 10 Ticks Vorhalt
        Vec3 predicted = PearlSolver.predictLead(pos, new Vec3(0, 0, 0.3), 10, false);
        assertEquals(3.0, predicted.z, 1e-9);
        assertEquals(0.0, predicted.x, 1e-9);
    }

    @Test
    void predictLeadKeepsGroundTargetAtSameHeight() {
        Vec3 predicted = PearlSolver.predictLead(new Vec3(0, 70, 0), new Vec3(0.2, -0.5, 0.2), 8, false);
        assertEquals(70.0, predicted.y, 1e-9, "am Boden darf der Vorhalt die Zielhoehe nicht veraendern");
    }

    @Test
    void predictLeadDropsAirborneTarget() {
        Vec3 pos = new Vec3(0, 80, 0);
        // Ohne Anfangsgeschwindigkeit faellt das Ziel unter Vanillas 0.08/Tick-Gravitation
        Vec3 predicted = PearlSolver.predictLead(pos, new Vec3(0, 0, 0.3), 5, true);
        assertTrue(predicted.y < 80.0, "ein Ziel in der Luft muss beim Vorhalt fallen");
        // faellt: -0.08 -0.16 -0.24 -0.32 -0.40 = -1.2
        assertEquals(78.8, predicted.y, 1e-9);
        assertEquals(1.5, predicted.z, 1e-9);
    }

    @Test
    void predictLeadClampsAbsurdHorizontalSpeed() {
        // 5 Bloecke/Tick horizontal ist unmoeglich - der Vorhalt wird auf 12 Bloecke gedeckelt
        Vec3 predicted = PearlSolver.predictLead(new Vec3(0, 64, 0), new Vec3(0, 0, 5.0), 20, false);
        assertEquals(12.0, predicted.z, 1e-9);
    }

    @Test
    void predictLeadClampsAbsurdFallDistance() {
        // 60 Ticks freier Fall ergaebe -145 Bloecke; gekappt auf 6, sonst zielt der Wurf in den Boden
        Vec3 predicted = PearlSolver.predictLead(new Vec3(0, 100, 0), new Vec3(0, 0, 0.3), 60, true);
        assertEquals(94.0, predicted.y, 1e-9);
    }

    @Test
    void predictLeadScalesDownBeforeClamping() {
        // 0.6 Bloecke/Tick * 30 Ticks = 18 Bloecke, gedeckelt auf 12
        Vec3 predicted = PearlSolver.predictLead(new Vec3(0, 64, 0), new Vec3(0, 0, 0.6), 30, false);
        assertEquals(12.0, predicted.z, 1e-9);
    }

    // ---------- leadFactor / movementYaw ----------

    @Test
    void leadFactorIsFullWhenRunningAlongFacing() {
        assertEquals(1.0, PearlSolver.leadFactor(90, 90), 1e-6, "vorwaerts = stabile Fluchtlinie");
        assertEquals(1.0, PearlSolver.leadFactor(-90, 90), 1e-6, "rueckwaerts = ebenfalls stabile Fluchtlinie");
    }

    @Test
    void leadFactorIsHalvedWhenStrafing() {
        // Ziel schaut nach Norden (90), laeuft aber nach Osten (0) - quer zur Blickrichtung
        assertEquals(0.5, PearlSolver.leadFactor(0, 90), 1e-6);
    }

    @Test
    void leadFactorHandlesWrappedAngles() {
        // 359 vs -1 sind dieselbe Richtung, trotzdem darf der Faktor nicht einbrechen
        assertEquals(1.0, PearlSolver.leadFactor(359, -1), 1e-4);
    }

    @Test
    void leadFactorStaysInHalfRange() {
        for (double velocityYaw = -360; velocityYaw <= 360; velocityYaw += 17) {
            for (double facingYaw = -360; facingYaw <= 360; facingYaw += 23) {
                double factor = PearlSolver.leadFactor(velocityYaw, facingYaw);
                assertTrue(factor >= 0.5 - 1e-9 && factor <= 1.0 + 1e-9,
                    "Faktor muss zwischen 0.5 und 1.0 liegen, war " + factor);
            }
        }
    }

    @Test
    void movementYawFollowsMinecraftConvention() {
        assertEquals(0, PearlSolver.movementYaw(new Vec3(0, 0, 1)), 1e-6, "+Z ist Yaw 0 (Sueden)");
        assertEquals(90, PearlSolver.movementYaw(new Vec3(-1, 0, 0)), 1e-6, "-X ist Yaw 90 (Westen)");
        assertEquals(-90, PearlSolver.movementYaw(new Vec3(1, 0, 0)), 1e-6, "+X ist Yaw -90 (Osten)");
        assertEquals(180, PearlSolver.movementYaw(new Vec3(0, 0, -1)), 1e-6);
    }

    // ---------- predictedIntercept ----------

    @Test
    void predictedInterceptIgnoresStationaryTarget() {
        Vec3 center = new Vec3(0, 65, 10);
        Vec3 predicted = PearlSolver.predictedIntercept(center, new Vec3(0, 64, 0),
            new Vec3(0, 0, 0), 90, false, 15);
        assertEquals(center, predicted, "ohne Bewegung gibt es nichts zu praedizieren");
    }

    @Test
    void predictedInterceptLeadsStraightAwayTarget() {
        // Ziel schaut und laeuft nach Norden (Yaw 90), Sprint 0.3/Tick, 10 Ticks Flugzeit
        Vec3 center = new Vec3(0, 65, 0);
        Vec3 position = new Vec3(0, 64, 0);
        Vec3 velocity = new Vec3(-0.3, 0, 0);
        Vec3 predicted = PearlSolver.predictedIntercept(center, position, velocity, 90, false, 10);
        // voller Vorhalt: 10 Ticks * 0.3 = 3 Bloecke in Fluchtrichtung
        assertEquals(-3.0, predicted.x, 1e-6);
        assertEquals(0.0, predicted.z, 1e-6);
        assertEquals(65.0, predicted.y, 1e-9, "Vorhalt aendert die Zielhoehe am Boden nicht");
    }

    @Test
    void predictedInterceptHalvesLeadForStrafingTarget() {
        // Blickrichtung Sueden (Yaw 0), Bewegung nach Westen (Yaw 90) - quer, also halber Vorhalt
        Vec3 center = new Vec3(0, 65, 0);
        Vec3 position = new Vec3(0, 64, 0);
        Vec3 velocity = new Vec3(-0.3, 0, 0);
        Vec3 predicted = PearlSolver.predictedIntercept(center, position, velocity, 0, false, 10);
        assertEquals(-1.5, predicted.x, 1e-6, "strafendes Ziel bekommt nur den halben Vorhalt");
    }

    @Test
    void predictedInterceptShiftsDownForAirborneTarget() {
        Vec3 center = new Vec3(0, 80, 0);
        Vec3 position = new Vec3(0, 80, 0);
        Vec3 velocity = new Vec3(0, 0, 0.3);
        Vec3 ground = PearlSolver.predictedIntercept(center, position, velocity, 0, false, 5);
        Vec3 air = PearlSolver.predictedIntercept(center, position, velocity, 0, true, 5);
        assertTrue(air.y < ground.y, "ein fallendes Ziel muss tiefer vorhergesagt werden");
        assertEquals(1.5, air.z, 1e-6, "der Vorhalt verschiebt auch horizontal (5 Ticks * 0.3)");
        assertEquals(1.5, ground.z, 1e-6, "beide Varianten nutzen denselben Vorhalt nach Norden");
    }

    @Test
    void predictedInterceptIgnoresNonPositiveFlightTime() {
        Vec3 center = new Vec3(0, 65, 0);
        Vec3 predicted = PearlSolver.predictedIntercept(center, new Vec3(0, 64, 0),
            new Vec3(-0.3, 0, 0), 90, false, 0);
        assertEquals(center, predicted, "ohne Flugzeit gibt es kein Vorhaltfenster");
    }

    // ---------- Bogen-Kandidatenauswahl ----------

    @Test
    void selectLowestClearPicksFirstFreeCandidate() {
        List<Integer> tested = new ArrayList<>();
        int chosen = PearlSolver.selectLowestClear(6, index -> {
            tested.add(index);
            return index >= 2;
        });
        assertEquals(2, chosen);
        assertEquals(List.of(0, 1, 2), tested, "die Pruefung muss beim ersten Treffer aufhoeren");
    }

    @Test
    void selectLowestClearPrefersFlatArcOverSteepOne() {
        // 0 und 1 sind frei, 4 und 5 ebenfalls: gewaehlt wird der NIEDRIGSTE Bogen, nicht der steilste
        int chosen = PearlSolver.selectLowestClear(6, index -> index == 0 || index == 1 || index >= 4);
        assertEquals(0, chosen, "flachster freier Bogen schlaegt steilsten freien Bogen");
    }

    @Test
    void selectLowestClearReturnsMinusOneWhenNothingIsClear() {
        assertEquals(-1, PearlSolver.selectLowestClear(6, index -> false));
    }

    @Test
    void selectLowestClearHandlesEmptyCandidateList() {
        assertEquals(-1, PearlSolver.selectLowestClear(0, index -> true));
    }

    @Test
    void selectLowestClearStopsAtCandidateCount() {
        int[] probed = { 0 };
        int chosen = PearlSolver.selectLowestClear(2, index -> {
            probed[0]++;
            return true;
        });
        assertEquals(0, chosen, "der erste freie Kandidat gewinnt");
        assertEquals(1, probed[0], "kein Kandidat ueber die uebergebene Grenze hinaus pruefen");
    }

    // ---------- Bogen-Aufsetzpunkte ----------

    @Test
    void candidateLandingFirstIndexIsTheDirectThrow() {
        Vec3 center = new Vec3(0, 64, 10);
        assertEquals(center, PearlSolver.candidateLanding(center, 0, 1, 0));
    }

    @Test
    void candidateLandingMovesBackwardsAlongThrowDirection() {
        Vec3 center = new Vec3(0, 64, 0);
        // Wirfrichtung nach Norden (+Z): der Aufsetzpunkt rueckt bei groesserem Index weiter nach Norden
        Vec3 first = PearlSolver.candidateLanding(center, 0, 1, 1);
        Vec3 last = PearlSolver.candidateLanding(center, 0, 1, PearlSolver.bypassCandidateCount() - 1);
        assertTrue(first.z > center.z, "Index 1 liegt bereits hinter dem Ziel");
        assertTrue(last.z > first.z, "Aufsetzpunkte muessen aufsteigend nach hinten versetzt sein");
        assertEquals(64.0, last.y, 1e-9, "der Aufsetzpunkt bleibt auf Zielhoehe");
    }

    @Test
    void candidateLandingFollowsArbitraryThrowDirection() {
        Vec3 center = new Vec3(0, 64, 0);
        Vec3 west = PearlSolver.candidateLanding(center, -1, 0, 2);
        assertTrue(west.x < center.x, "-X ist Westen, der Aufsetzpunkt muss dort liegen");
    }

    @Test
    void candidateLandingRejectsIndexOutsideCandidateList() {
        Vec3 center = new Vec3(0, 64, 0);
        assertThrows(IndexOutOfBoundsException.class,
            () -> PearlSolver.candidateLanding(center, 0, 1, PearlSolver.bypassCandidateCount()));
        assertThrows(IndexOutOfBoundsException.class,
            () -> PearlSolver.candidateLanding(center, 0, 1, -1));
    }

    // ---------- Datentypen ----------

    @Test
    void pearlAimCarriesScenarioAndConfidence() {
        PearlAim aim = new PearlAim(45.0, 12.0, 17, PearlScenario.GAP_CLOSER, true);
        assertEquals(45.0, aim.yaw());
        assertEquals(12.0, aim.pitch());
        assertEquals(17, aim.flightTicks());
        assertEquals(PearlScenario.GAP_CLOSER, aim.scenario());
        assertTrue(aim.confident());
    }

    @Test
    void pearlScenarioCoversAllThreeTacticalCases() {
        assertEquals(3, PearlScenario.values().length);
        assertEquals(PearlScenario.TERRAIN_BYPASS,
            PearlScenario.valueOf("TERRAIN_BYPASS"), "Szenario 2 muss benennbar bleiben (Logging)");
    }
}
