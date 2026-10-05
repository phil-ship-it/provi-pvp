package com.provipvp.mechanics;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DtapPlannerTest {

    private static final BlockPos CENTER = new BlockPos(0, 64, 0);
    private static final double MIN_FRACTION = 0.6;

    /** Kandidatenfeld: Schaden je Stelle, erreichbare Stellen, Friend-Konflikte. */
    private static final class Field implements DtapPlanner.SpotProbe {
        private final Map<BlockPos, Double> damage = new HashMap<>();
        private final Set<BlockPos> reachable = new HashSet<>();
        private final Set<BlockPos> friendBlocked = new HashSet<>();

        Field damage(BlockPos pos, double value) {
            damage.put(pos, value);
            return this;
        }

        Field canBuildAt(BlockPos... spots) {
            for (BlockPos s : spots) reachable.add(s);
            return this;
        }

        Field blockedForFriend(BlockPos... spots) {
            for (BlockPos s : spots) friendBlocked.add(s);
            return this;
        }

        @Override
        public double damageToTarget(BlockPos spot) {
            return damage.getOrDefault(spot, 0.0);
        }

        @Override
        public boolean reachable(BlockPos spot) {
            return reachable.contains(spot);
        }

        @Override
        public boolean conflictsWithFriend(BlockPos spot) {
            return friendBlocked.contains(spot);
        }
    }

    @Test
    void besteErreichbareStelleGewinnt() {
        Field f = new Field()
            .damage(new BlockPos(1, 64, 0), 6.0)
            .damage(new BlockPos(-1, 64, 0), 9.0)
            .canBuildAt(new BlockPos(1, 64, 0), new BlockPos(-1, 64, 0));

        DtapPlanner.Choice choice = DtapPlanner.pick(CENTER, f, MIN_FRACTION);

        assertNotNull(choice);
        assertEquals(new BlockPos(-1, 64, 0), choice.spot());
        assertEquals(9.0, choice.damage(), 1e-9);
    }

    /** Der eigentliche Zweck: die beste Stelle liegt zu weit weg, eine naeher gelegene reicht trotzdem. */
    @Test
    void erreichbareStelleWaehltStattAufzugeben() {
        Field f = new Field()
            .damage(new BlockPos(3, 64, 0), 10.0)   // weit weg -> nicht erreichbar
            .damage(new BlockPos(1, 64, 0), 7.5)    // erreichbar
            .canBuildAt(new BlockPos(1, 64, 0));

        DtapPlanner.Choice choice = DtapPlanner.pick(CENTER, f, MIN_FRACTION);

        assertNotNull(choice, "eine erreichbare Stelle ist besser als gar keine");
        assertEquals(new BlockPos(1, 64, 0), choice.spot());
    }

    @Test
    void zuSchwacheNaeherloesungWirdVerworfen() {
        Field f = new Field()
            .damage(new BlockPos(3, 64, 0), 10.0)
            .damage(new BlockPos(1, 64, 0), 2.0)    // nur 20 % des Bestmoeglichen
            .canBuildAt(new BlockPos(1, 64, 0));

        assertNull(DtapPlanner.pick(CENTER, f, MIN_FRACTION),
            "lieber kein D-Tap als einer, der das Ziel nicht mehr erreicht");
    }

    @Test
    void ohneErreichbareStelleKeinDtap() {
        Field f = new Field().damage(new BlockPos(1, 64, 0), 8.0);   // erreichbar: nichts markiert

        assertNull(DtapPlanner.pick(CENTER, f, MIN_FRACTION));
    }

    @Test
    void freundKonfliktSchiebtDieWahl() {
        Field f = new Field()
            .damage(new BlockPos(1, 64, 0), 9.0)
            .damage(new BlockPos(0, 64, -1), 7.0)
            .canBuildAt(new BlockPos(1, 64, 0), new BlockPos(0, 64, -1))
            .blockedForFriend(new BlockPos(1, 64, 0));

        DtapPlanner.Choice choice = DtapPlanner.pick(CENTER, f, MIN_FRACTION);

        assertNotNull(choice);
        assertEquals(new BlockPos(0, 64, -1), choice.spot(), "neben einem Freund wird nie gebaut");
    }

    @Test
    void nurMitPositivemSchadenIstUeberhauptEineStelle() {
        Field f = new Field()
            .damage(new BlockPos(1, 64, 0), 0.0)
            .canBuildAt(new BlockPos(1, 64, 0));

        assertNull(DtapPlanner.pick(CENTER, f, MIN_FRACTION));
    }

    @Test
    void suchradiusDecktMehrAlsDieFuenfBlockeDesAltenCodesAb() {
        // Der alte Code suchte nur +/-2; die Reichweite von 4.5 liess dann bei weiter Landung nichts
        // mehr zu. Der Radius muss weit genug sein, damit ueberhaupt eine erreichbare Stelle existiert.
        assertTrue(DtapPlanner.DEFAULT_RADIUS_XZ >= 3);
    }
}