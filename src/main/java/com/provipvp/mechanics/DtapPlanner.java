package com.provipvp.mechanics;

import net.minecraft.core.BlockPos;

/**
 * Waehlt die D-Tap-Stelle - kopflos, ohne Minecraft-Handle.
 *
 * <p>Der Engpass war die Kombination aus zwei gegenlaeufigen Filtern: die Kandidaten lagen rund um
 * die <em>vorhergesagte Landestelle</em> des Gegners, mussten aber gleichzeitig in Bot-Reichweite
 * liegen. Je weiter der Gegner weggeschleudert wurde - also genau in dem Fall, fuer den ein D-Tap
 * gedacht ist -, desto weiter ausserhalb der Reichweite lag die Landestelle und desto eher fiel
 * jeder einzelne Kandidat durch. Der Pfad blieb dadurch fast tot.
 *
 * <p>Die Regel hier: unter den <em>erreichbaren</em> Kandidaten die beste nehmen, aber nur, wenn sie
 * einen Mindestanteil des theoretisch moeglichen Schadens haelt. Sonst lieber kein D-Tap als einer
 * auf einer Stelle, die das Ziel gar nicht mehr trifft.
 */
public final class DtapPlanner {

    /** Wie viele Bloecke der Kandidat vom Suchzentrum abweichen darf. */
    public static final int DEFAULT_RADIUS_XZ = 3;
    public static final int DEFAULT_RADIUS_Y = 1;

    private DtapPlanner() {
    }

    /** Was der Aufrufer ueber eine Kandidatenstelle wissen muss. */
    public interface SpotProbe {
        /**
         * Schaden einer Explosion auf dieser Stelle am Ziel.
         *
         * @return Schaden, oder &lt;= 0 wenn die Stelle das Ziel nicht erreicht
         */
        double damageToTarget(BlockPos spot);

        /** @return true, wenn der Bot den Block von hier aus setzen kann (Reichweite, Slot, Sicht). */
        boolean reachable(BlockPos spot);

        /** @return true, wenn die Stelle neben einer befreundeten Entity liegt oder von ihr belegt ist. */
        boolean conflictsWithFriend(BlockPos spot);
    }

    /**
     * @param minDamageFraction Mindestanteil am besten theoretisch erreichbaren Schaden. Bei 0.6 wird
     *                          eine Stelle verworfen, die weniger als 60 % des Bestmöglichen bringt.
     */
    public record Choice(BlockPos spot, double damage) {
    }

    /**
     * Sucht die beste erreichbare Stelle um {@code center}.
     *
     * @return die gewaehlte Stelle, oder null wenn keine erreichbare Stelle existiert oder die beste
     *         erreichbare zu schwach ist
     */
    public static Choice pick(BlockPos center, SpotProbe probe, double minDamageFraction) {
        if (center == null || probe == null) return null;

        double bestPossible = 0.0;
        BlockPos bestSpot = null;
        double bestReachable = 0.0;
        BlockPos bestReachableSpot = null;

        for (int dx = -DEFAULT_RADIUS_XZ; dx <= DEFAULT_RADIUS_XZ; dx++) {
            for (int dz = -DEFAULT_RADIUS_XZ; dz <= DEFAULT_RADIUS_XZ; dz++) {
                for (int dy = -DEFAULT_RADIUS_Y; dy <= DEFAULT_RADIUS_Y; dy++) {
                    BlockPos spot = new BlockPos(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    double dmg = probe.damageToTarget(spot);
                    if (dmg <= 0) continue;
                    if (dmg > bestPossible) bestPossible = dmg;

                    if (!probe.reachable(spot)) continue;
                    if (probe.conflictsWithFriend(spot)) continue;
                    if (dmg > bestReachable) {
                        bestReachable = dmg;
                        bestReachableSpot = spot;
                    }
                }
            }
        }

        if (bestReachableSpot == null) return null;
        if (bestReachable < bestPossible * minDamageFraction) return null;
        return new Choice(bestReachableSpot, bestReachable);
    }
}