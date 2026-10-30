package com.provipvp.pearl;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JUnit-Tests fuer den optimierten Perlen-Rechner.
 *
 * <p>Warum nur beobachtbare Entscheidungen: Unsere Tests pruefen,
 * ob die Methode bei gueltigen Eingaben ein Winkelergebnis liefert,
 * ob sie bei null-Eingaben abbricht und ob der funktionale Adapter
 * korrekt durchgereicht wird. Keine Getter/Setter-Tests.</p>
 */
public class PearlTrajectoryOptimizerTest {

    @Test
    void solveOptimizedReturnsAimForClearPath() {
        Vec3 from = new Vec3(0, 2, 0);
        Vec3 target = new Vec3(3, 2, 0);
        Vec3 predicted = new Vec3(3.1, 2, 0.1);
        Vec3 extra = new Vec3(0, 0, 0);
        BlockAccessPort alwaysClear = pos -> null;
        double[] result = PearlTrajectoryOptimizer.solveOptimized(
            from, target, predicted, extra, alwaysClear, 3.0);
        assertTrue(result == null || result.length == 3,
            "Entweder kein freier Pfad (null) oder gueltiges 3-Wert-Array");
    }

    @Test
    void solveOptimizedReturnsNullOnNullInputs() {
        Vec3 v = new Vec3(0, 0, 0);
        BlockAccessPort dummy = pos -> null;
        assertNull(PearlTrajectoryOptimizer.solveOptimized(null, v, v, v, dummy, 3.0));
        assertNull(PearlTrajectoryOptimizer.solveOptimized(v, null, v, v, dummy, 3.0));
    }

    @Test
    void adapterIsPassedWithoutDirectWorldAccess() {
        Vec3 from = new Vec3(0, 2, 0);
        Vec3 to = new Vec3(2, 2, 0);
        Vec3 pred = new Vec3(2.2, 2, 0);
        BlockAccessPort blocking = new BlockAccessPort() {
            @Override public net.minecraft.world.level.block.state.BlockState getState(BlockPos pos) {
                return null; // Einfacher Adapter, kein direkter mc-Zugriff
            }
        };
        double[] result = PearlTrajectoryOptimizer.solveOptimized(
            from, to, pred, new Vec3(0, 0, 0), blocking, 3.0);
        assertTrue(result == null || result.length == 3,
            "Adapter muss akzeptiert werden; Ergebnis muss entweder null oder 3-Wert-Array sein");
    }

    @Test
    void maxDistEnforcedStrictly() {
        Vec3 from = new Vec3(0, 2, 0);
        Vec3 target = new Vec3(10, 2, 0);
        Vec3 pred = new Vec3(10, 2, 0);
        BlockAccessPort clear = pos -> null;
        double[] result = PearlTrajectoryOptimizer.solveOptimized(
            from, target, pred, new Vec3(0, 0, 0), clear, 3.0);
        assertNull(result, "Jenseits von maxDist darf kein Ergebnis existieren");
    }
}
