package com.provipvp.pearl;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Funktionaler Adapter fuer Weltzugriffe, damit die Perlen-Logik niemals
 * direkt auf {@code mc.*} oder {@code Minecraft.getInstance()} verweisen muss.
 *
 * <p>Warum: R1 verlangt, dass neue Klassen keinerlei harte Minecraft-Bezuege
 * haben. Stattdessen wird der Zustand der Welt hier als reine Funktion
 * uebergeben – testbar, austauschbar und frei von Client-Abhaengigkeit.</p>
 */
@FunctionalInterface
public interface BlockAccessPort {

    /**
     * Liefert den Block-Zustand fuer die gegebene Position.
     * @return {@code null}, wenn die Position nicht erreichbar ist.
     */
    BlockState getState(BlockPos pos);

    /**
     * Prueft, ob ein Block an der Position standfaehig ist
     * (Luft ueber und unter, Block unten bewegt sich).
     */
    default boolean isStandable(BlockPos feet, int aboveOffset, int belowOffset) {
        BlockState above = getState(feet.above(aboveOffset));
        BlockState at = getState(feet);
        BlockState below = getState(feet.below(belowOffset));
        return at != null && at.isAir()
            && (above == null || above.isAir())
            && (below != null && below.blocksMotion());
    }
}
