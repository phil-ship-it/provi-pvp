package com.provipvp.ray;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Deckt die zwei Entscheidungen ab, an denen eine von Hand gebaute Platzierung scheitert: der Cursor
 * muss im Fenster liegen, und die Flaeche muss die dem Spieler zugewandte sein.
 */
class PlaceCursorSolverTest {

    /** Welt aus einer Menge solider Zellen — sonst nichts, keine Block-Registries. */
    private static PlaceCursorSolver.World worldOf(BlockPos... solid) {
        Set<BlockPos> cells = new HashSet<>(List.of(solid));
        return cells::contains;
    }

    @Test
    void cursorStaysInsideTheUnitRangeForEveryAngleOnTheFace() {
        PlaceCursorSolver.World world = worldOf(new BlockPos(0, 0, 0));

        // Augen ueber der Blockmitte, Blick in den unteren Block, einmal senkrecht und zweimal
        // schraeg - schraeg ist der Fall, bei dem ein Cursor tatsaechlich aus dem Fenster laeuft.
        Vec3[] eyes = {new Vec3(0.5, 2.5, 0.5), new Vec3(0.9, 2.5, 0.9), new Vec3(0.15, 2.2, 0.85)};
        Vec3[] aims = {new Vec3(0.5, 0.5, 0.5), new Vec3(0.4, 0.5, 0.2), new Vec3(0.2, 0.5, 0.1)};

        for (int i = 0; i < eyes.length; i++) {
            PlaceCursorSolver.Placement hit = PlaceCursorSolver.trace(eyes[i], aims[i].subtract(eyes[i]), world, 4.5);
            assertNotNull(hit, "Strahl " + i + " hat den Block verfehlt");
            assertEquals(new BlockPos(0, 0, 0), hit.clicked());
            assertEquals(Direction.UP, hit.face(), "von oben muss immer die Deckflaeche getroffen werden");

            assertTrue(hit.cursorLegal(false),
                "Cursor " + hit.cursorX() + "/" + hit.cursorY() + " liegt ausserhalb [0,1]");
            assertTrue(hit.cursorX() >= 0.0 && hit.cursorX() <= 1.0);
            assertTrue(hit.cursorY() >= 0.0 && hit.cursorY() <= 1.0);
        }
    }

    @Test
    void tracedPlacementLandsOnTheNeighbourCellOfTheHitFace() {
        PlaceCursorSolver.Placement hit = PlaceCursorSolver.trace(new Vec3(0.5, 2.5, 0.5), new Vec3(0, -1, 0),
            worldOf(new BlockPos(0, 0, 0)), 4.5);

        assertNotNull(hit);
        // Vanilla rekonstruiert die Zielzelle als clickedPos.relative(clickedDirection) - genau das
        // muss hier herauskommen, sonst zuegt der Block eine Zelle daneben.
        assertEquals(hit.clicked().relative(hit.face()), hit.placed());
        assertEquals(new BlockPos(0, 1, 0), hit.placed());
    }

    @Test
    void chosenFaceIsTheOneActuallyFacingThePlayer() {
        PlaceCursorSolver.World world = worldOf(new BlockPos(0, 0, 0));
        Vec3 eye = new Vec3(2.5, 0.7, 0.5);
        BlockPos placed = new BlockPos(1, 0, 0);

        assertEquals(Direction.EAST, PlaceCursorSolver.faceTowardPlayer(new BlockPos(0, 0, 0), eye));

        List<PlaceCursorSolver.Placement> faces = PlaceCursorSolver.candidates(placed, eye, world);
        assertEquals(1, faces.size(), "nur die eine freie, zugewandte Seite darf uebrig bleiben");

        PlaceCursorSolver.Placement best = faces.get(0);
        assertEquals(Direction.EAST, best.face());
        assertEquals(new BlockPos(0, 0, 0), best.clicked());
        assertEquals(placed, best.placed());
        // Flaechenmitte ergibt Cursor (0.5, 0.5) - die unauffaelligste Platzierung ueberhaupt.
        assertEquals(0.5, best.cursorX(), 1e-9);
        assertEquals(0.5, best.cursorY(), 1e-9);
    }

    @Test
    void aSolidNeighbourFacingAwayFromThePlayerIsNoUsablePlacementFace() {
        // Mauer aus zwei Bloecken, davor eine freie Zelle. Steht der Spieler davor, ist die
        // Suedseite des Mauerblocks die einzige moegliche Angriffsflaeche; steht er dahinter, ist
        // derselbe Nachbarblock als Angriffsflaeche tot - obwohl er solide ist und die Zelle grenzt.
        PlaceCursorSolver.World world = worldOf(new BlockPos(0, 0, 0), new BlockPos(1, 0, 0));
        BlockPos placed = new BlockPos(0, 0, 1);

        List<PlaceCursorSolver.Placement> inFront = PlaceCursorSolver.candidates(placed, new Vec3(0.5, 0.7, 2.5), world);
        assertEquals(1, inFront.size());
        assertEquals(Direction.SOUTH, inFront.get(0).face());
        assertEquals(new BlockPos(0, 0, 0), inFront.get(0).clicked());

        assertTrue(PlaceCursorSolver.candidates(placed, new Vec3(0.5, 0.7, -2.5), world).isEmpty(),
            "eine dem Spieler abgewandte Flaeche ist keine Angriffsflaeche");
    }

    @Test
    void cursorAboveOneIsOnlyAllowedForLecternAndScaffolding() {
        // Genau diese Unterscheidung verhindert Grim's FabricatedPlace: 1.2 ist fuer einen
        // normalen Block eine erfundene Position, fuer ein Lectern ein gueltiger Rand.
        assertFalse(PlaceCursorSolver.cursorLegal(1.2, 0.5, false));
        assertTrue(PlaceCursorSolver.cursorLegal(1.2, 0.5, true));
        assertFalse(PlaceCursorSolver.cursorLegal(-0.01, 0.5, true));
        assertTrue(PlaceCursorSolver.cursorLegal(1.5, 1.5, true));
        assertFalse(PlaceCursorSolver.cursorLegal(1.51, 0.5, true));
    }

    @Test
    void placementReachIsTheBlockInteractionRangeNotTheMeleeRange() {
        Vec3 eye = new Vec3(4.5, 0.5, 0.5);
        BlockPos target = new BlockPos(0, 0, 0);
        double distance = eye.distanceTo(Vec3.atCenterOf(target));

        // 4.0 Bloecke: Obsidian-Deckung geht, ein Schlag nicht.
        assertEquals(4.0, distance, 1e-9);
        assertTrue(PlaceCursorSolver.withinPlacementReach(eye, target));
        assertFalse(ReachPolicy.allows(ReachPolicy.Action.MELEE, distance));

        // Und derselbe Abstand als Crystal-Zuendung ist ebenfalls zu weit.
        assertFalse(ReachPolicy.allows(ReachPolicy.Action.BREAK_ENTITY, distance));
    }

    @Test
    void traceReportsNothingWhenTheBeamPassesTheBlock() {
        // Blick nach unten, der Block liegt aber neben dem Strahl - genau das liefert ein von Hand
        // gebautes BlockHitResult, wenn die Rotation nicht wirklich auf den Block zeigt.
        assertNull(PlaceCursorSolver.trace(new Vec3(0.5, 2.5, 4.5), new Vec3(0, -1, 0),
            worldOf(new BlockPos(0, 0, 0)), 4.5));
    }
}