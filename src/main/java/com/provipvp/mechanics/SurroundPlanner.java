package com.provipvp.mechanics;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.List;

/**
 * Entscheidet, welcher EIN Obsidian-Block als naechstes gesetzt wird - ohne Minecraft-Handle,
 * ohne Setting, ohne Seiteneffekt. Der Weltzugriff kommt ausschliesslich ueber {@link World}.
 *
 * <p>Zwei Fragen, die GodmodePvP vorher im Modul selbst beantwortet hat und hier beantwortet bekommt:
 * <ol>
 *   <li>Steht ein feindlicher Crystal drohend ueber uns, wird GENAU EIN Block auf die Seite zwischen
 *       Bot und Crystal gesetzt - nicht einer pro Ringposition und nicht erst im naechsten Tick.</li>
 *   <li>Sonst wird der Ring auf der niedrigsten Fuss-Level gesetzt, auf der wir stehen koennen.</li>
 * </ol>
 *
 * <p>Die Ringseite zum Gegner bleibt offen. Ein geschlossener Ring nimmt Baritone jeden Weg und
 * mauert den Bot selbst ein - das ist kein Stilwunsch, sondern ein beobachteter Selbstmauerungs-Bug.
 */
public final class SurroundPlanner {

    /** Minimaler Blockabstand, ab dem ein Crystal als "droht" gilt. Wer weiter weg steht, schadet
     *  nicht mehr genug, um dafuer die eigene Ringwand zu unterbrechen. */
    public static final double THREAT_MAX_DISTANCE = 4.0;

    private SurroundPlanner() {
    }

    /** Die Welt, die der Planer lesen darf. Lambdas statt mc.level, damit das hier testbar bleibt. */
    public interface World {
        boolean isAir(BlockPos pos);
        boolean canPlaceOn(BlockPos pos);
        boolean standable(BlockPos pos);
    }

    /** Ein bedrohlicher Crystal: wo er steht und wie viel Schaden er uns ausrichten wuerde. */
    public record CrystalThreat(BlockPos pos, double damageToSelf) {
    }

    /**
     * Der Block, der einen Crystal abfaengt - oder null.
     *
     * <p>Gesucht wird die Ringzelle zwischen Fussposition und Crystal. Sie muss Luft sein und
     * beschraenkbar sein; steht dort schon etwas, ist der Crystal nicht abfangbar und die Antwort
     * ist null (kein Umfallen des Rings, um doch noch irgendwo hinzustellen).
     */
    public static BlockPos blockAgainstCrystal(BlockPos feet, CrystalThreat threat, World world) {
        if (feet == null || threat == null || threat.pos() == null || world == null) return null;
        if (threat.damageToSelf() <= 0.0) return null;

        Direction dir = horizontalDir(feet, threat.pos());
        if (dir == null) return null;

        BlockPos between = feet.relative(dir);
        if (!world.isAir(between)) return null;
        if (!world.canPlaceOn(between)) return null;
        return between;
    }

    /**
     * Der schaedlichste Crystal in {@code threats} - nach Damage, bei Gleichstand nach Distanz.
     * Damit wird genau EIN Block gesetzt und zwar vor dem, der den Bot am meisten wehtut.
     */
    public static CrystalThreat worstThreat(BlockPos feet, List<CrystalThreat> threats) {
        if (feet == null || threats == null || threats.isEmpty()) return null;

        CrystalThreat best = null;
        double bestDamage = 0.0;
        double bestDistSq = 0.0;

        for (CrystalThreat threat : threats) {
            if (threat == null || threat.pos() == null) continue;
            if (threat.damageToSelf() <= 0.0) continue;

            double dx = threat.pos().getX() - feet.getX();
            double dy = threat.pos().getY() - feet.getY();
            double dz = threat.pos().getZ() - feet.getZ();
            double distSq = dx * dx + dy * dy + dz * dz;
            if (distSq > THREAT_MAX_DISTANCE * THREAT_MAX_DISTANCE) continue;

            boolean closer = distSq < bestDistSq;
            if (best == null || threat.damageToSelf() > bestDamage || (threat.damageToSelf() == bestDamage && closer)) {
                best = threat;
                bestDamage = threat.damageToSelf();
                bestDistSq = distSq;
            }
        }
        return best;
    }

    /**
     * Die unterste Fuss-Level, auf der wir den Ring setzen. Gesucht wird ab {@code feet} abwaerts bis
     * {@code maxDrop} Bloecke tief; die erste standbare und bebaubare Ebene gewinnt.
     *
     * <p>Nur Ebenen, auf denen mindestens eine der drei zu bauenden Seiten wirklich offen ist, zaehlen -
     * eine Ebene, auf der der Ring nichts zu tun hat, ist keine Verbesserung, sondern eine
     * sinnlose Platzierung eines Blocks weiter unten, wo er nichts schuetzt.
     *
     * @return die gewaehlte Fuss-Level, oder {@code feet} selbst, wenn keine tiefere taugt
     */
    public static BlockPos lowestRingLevel(BlockPos feet, Direction openSide, World world, int maxDrop) {
        if (feet == null || world == null) return feet;
        if (maxDrop <= 0) return feet;

        for (int drop = 1; drop <= maxDrop; drop++) {
            BlockPos candidate = feet.below(drop);
            if (!world.standable(candidate)) continue;
            if (!anyRingCellBuildable(candidate, openSide, world)) continue;
            return candidate;
        }
        return feet;
    }

    /** Die naechste noch offene Ringsseite, damit wir den Gegner nicht einschliessen - oder null,
     *  wenn der Ring vollstaendig steht und nichts gebaut werden muss. */
    public static BlockPos firstOpenSide(BlockPos feet, Direction openSide, World world) {
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            if (dir == openSide) continue;
            BlockPos side = feet.relative(dir);
            if (world.isAir(side) && world.canPlaceOn(side)) return side;
        }
        return null;
    }

    private static boolean anyRingCellBuildable(BlockPos feet, Direction openSide, World world) {
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            if (dir == openSide) continue;
            BlockPos side = feet.relative(dir);
            if (world.isAir(side) && world.canPlaceOn(side)) return true;
        }
        return false;
    }

    /** Die horizontale Richtung von {@code feet} zu {@code point}. Genau eine Achse, wie die
     *  Ringsseite, die den Crystal abfaengt. Liegt der Crystal auf derselben Position, null. */
    public static Direction horizontalDir(BlockPos feet, BlockPos point) {
        int dx = point.getX() - feet.getX();
        int dz = point.getZ() - feet.getZ();
        if (dx == 0 && dz == 0) return null;
        if (Math.abs(dx) >= Math.abs(dz)) return dx >= 0 ? Direction.EAST : Direction.WEST;
        return dz >= 0 ? Direction.SOUTH : Direction.NORTH;
    }
}