package com.provipvp.mechanics;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SurroundPlannerTest {

    private static final BlockPos FEET = new BlockPos(0, 64, 0);

    /** Minimale Welt zum Testen: Luft, Platzierbarkeit und Stehbarkeit sind drei getrennte Fragen,
     *  genau wie im Spiel. */
    private static final class TestWorld implements SurroundPlanner.World {
        private final Set<BlockPos> solid = new java.util.HashSet<>();
        private final Set<BlockPos> standable = new java.util.HashSet<>();

        TestWorld withSolid(BlockPos pos) {
            solid.add(pos.immutable());
            return this;
        }

        TestWorld withStandable(BlockPos pos) {
            standable.add(pos.immutable());
            return this;
        }

        @Override
        public boolean isAir(BlockPos pos) {
            return !solid.contains(pos);
        }

        @Override
        public boolean canPlaceOn(BlockPos pos) {
            return !solid.contains(pos);
        }

        @Override
        public boolean standable(BlockPos pos) {
            return standable.contains(pos);
        }
    }

    // ---- Genau EIN Block zwischen Bot und Crystal -------------------------------------------------

    @Test
    void crystalWirdAufDerZwischenstelleAbgefangen() {
        TestWorld world = new TestWorld();
        SurroundPlanner.CrystalThreat threat = new SurroundPlanner.CrystalThreat(new BlockPos(2, 64, 0), 11.0);

        BlockPos block = SurroundPlanner.blockAgainstCrystal(FEET, threat, world);

        assertEquals(new BlockPos(1, 64, 0), block, "der Block muss genau zwischen Bot und Crystal");
    }

    @Test
    void abfangenLiefertGenauEinePosition() {
        TestWorld world = new TestWorld();
        BlockPos block = SurroundPlanner.blockAgainstCrystal(
            FEET, new SurroundPlanner.CrystalThreat(new BlockPos(0, 64, -3), 11.0), world);

        // Genau eine BlockPosition, keine Liste, kein Ring: der Aufrufer platziert genau diesen Block.
        assertEquals(new BlockPos(0, 64, -1), block);
    }

    @Test
    void besetzteZwischenstelleWirdNichtUeberschrieben() {
        TestWorld world = new TestWorld().withSolid(new BlockPos(1, 64, 0));

        assertNull(SurroundPlanner.blockAgainstCrystal(
            FEET, new SurroundPlanner.CrystalThreat(new BlockPos(2, 64, 0), 11.0), world),
            "steht dort schon etwas, faellt der Block aus - nicht woanders hin ausweichen");
    }

    @Test
    void crystalOhneSchadenWirdNichtAbgefangen() {
        TestWorld world = new TestWorld();

        assertNull(SurroundPlanner.blockAgainstCrystal(
            FEET, new SurroundPlanner.CrystalThreat(new BlockPos(2, 64, 0), 0.0), world));
    }

    // ---- Der schaedlichste Crystal gewinnt ---------------------------------------------------------

    @Test
    void schaedlichsterCrystalWaehltDenBlock() {
        List<SurroundPlanner.CrystalThreat> threats = List.of(
            new SurroundPlanner.CrystalThreat(new BlockPos(0, 64, 2), 4.0),
            new SurroundPlanner.CrystalThreat(new BlockPos(-2, 64, 0), 9.0));

        SurroundPlanner.CrystalThreat worst = SurroundPlanner.worstThreat(FEET, threats);

        assertNotNull(worst);
        assertEquals(new BlockPos(-2, 64, 0), worst.pos());
    }

    @Test
    void weiterEntfernterCrystalIstKeineDrohung() {
        List<SurroundPlanner.CrystalThreat> threats = List.of(
            new SurroundPlanner.CrystalThreat(new BlockPos(9, 64, 0), 9.0));

        assertNull(SurroundPlanner.worstThreat(FEET, threats),
            "9 Blocker entfernt ist kein Grund, die eigene Ringwand zu unterbrechen");
    }

    @Test
    void leereBedrohungsliefertNichts() {
        assertNull(SurroundPlanner.worstThreat(FEET, List.of()));
    }

    // ---- Immer so tief wie moeglich --------------------------------------------------------------

    @Test
    void tiefsteTauglicheEbeneWirdGewaehlt() {
        TestWorld world = new TestWorld()
            .withStandable(FEET.below(2));

        BlockPos level = SurroundPlanner.lowestRingLevel(FEET, Direction.SOUTH, world, 3);

        assertEquals(FEET.below(2), level, "zwei Bloecke tiefer ist besser als die aktuelle Ebene");
    }

    @Test
    void ringsseiteZuFaelltZurueck() {
        // Alle drei bebaubaren Seiten auf -1 zu - dort gibt es nichts mehr zu bauen, also
        // ist die tiefere Ebene keine Verbesserung und darf nicht gewaehlt werden.
        TestWorld world = new TestWorld()
            .withStandable(FEET.below(1))
            .withSolid(FEET.below(1).relative(Direction.NORTH))
            .withSolid(FEET.below(1).relative(Direction.EAST))
            .withSolid(FEET.below(1).relative(Direction.WEST));

        BlockPos level = SurroundPlanner.lowestRingLevel(FEET, Direction.SOUTH, world, 3);

        assertEquals(FEET, level,
            "eine Ebene, auf der der Ring nichts zu tun hat, ist keine tiefere Verteidigung");
    }

    @Test
    void tiefensucheIstBegrenzt() {
        TestWorld world = new TestWorld().withStandable(FEET.below(5));

        assertEquals(FEET, SurroundPlanner.lowestRingLevel(FEET, Direction.SOUTH, world, 3),
            "mit maxDrop 3 wird nicht 5 Bloecke tief gebaut");
    }


    // ---- Die Seite zum Gegner bleibt offen --------------------------------------------------------

    @Test
    void gegnerseiteBleibtOffen() {
        TestWorld world = new TestWorld();

        BlockPos side = SurroundPlanner.firstOpenSide(FEET, Direction.EAST, world);

        assertNotNull(side);
        assertTrue(!side.equals(FEET.relative(Direction.EAST)),
            "ein geschlossener Ring mauert den Bot selbst ein");
    }

    @Test
    void richtungIstEineAchse() {
        assertEquals(Direction.EAST, SurroundPlanner.horizontalDir(FEET, new BlockPos(5, 64, 1)));
        assertEquals(Direction.SOUTH, SurroundPlanner.horizontalDir(FEET, new BlockPos(1, 64, 5)));
        assertNull(SurroundPlanner.horizontalDir(FEET, FEET));
    }
}