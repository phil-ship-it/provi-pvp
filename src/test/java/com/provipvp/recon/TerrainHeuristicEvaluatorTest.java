package com.provipvp.recon;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.function.Function;
import static org.junit.jupiter.api.Assertions.*;

/**
 * R3: Beobachtbare Heuristik-Entscheidung — unnaturliche Licht-Grenzen.
 * WHY: Jeder Test prüft eine Ja/Nein-Entscheidung der Kopfheüristik.
 */
class TerrainHeuristicEvaluatorTest {

    @Test
    void lightAnomalyDetected() {
        Function<BlockPos, BlockState> dummy = p -> null;
        Function<BlockPos, Integer> light = p -> p.getY() == 1 ? 15 : 0; // Sprung bei Y-Wechsel
        assertTrue(TerrainHeuristicEvaluator.checkLightAnomaly(BlockPos.containing(0, 1, 0), dummy, light));
    }

    @Test
    void noLightAnomalyForSmoothField() {
        Function<BlockPos, BlockState> dummy = p -> null;
        Function<BlockPos, Integer> light = p -> 10;
        assertFalse(TerrainHeuristicEvaluator.checkLightAnomaly(BlockPos.containing(0, 1, 0), dummy, light));
    }

    @Test
    void chunkBorderLightAnomalyDetected() {
        Function<BlockPos, Integer> light = p -> {
            if (p.getX() == 8 && p.getZ() == 8) return 5;
            return 12;
        };
        assertTrue(TerrainHeuristicEvaluator.checkChunkBorderLight(light, 0, 0));
    }
}
