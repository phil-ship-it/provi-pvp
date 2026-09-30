package com.provipvp.exec;

import com.provipvp.core.InventoryManager;
import com.provipvp.pearl.PearlAim;
import com.provipvp.util.InvHelper;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;

import java.util.Random;

/**
 * Fuehrt eine bereits berechnete Combat-Aktion aus: Item suchen, Combat-Slot halten, in die
 * {@link RotationQueue} einreihen und im Callback benutzen.
 *
 * <p>Aus {@code GodmodePvP.rotateAndRun} (:3072) und {@code queueWithCombatSlot} (:3130)
 * herausgeloest. Die Rotations-Warteschlange selbst liegt seit der WEEK-2-Extraktion in
 * {@link RotationQueue} — hier laeuft nur noch das Drumherum, damit die Warteschlange nicht an
 * zwei Orten gepflegt wird.
 *
 * <p>Keine Modul-Settings: Spanne, Prioritaet und Zufallsquelle kommen per Konstruktor,
 * damit der Executor ohne Meteor-Modul und ohne Setting-Group testbar bleibt.
 */
public final class CombatExecutor {

    /** Prioritaet eines Perlenwurfs, portiert aus {@code GodmodePvP.PRIORITY_PEARL}. */
    public static final int PRIORITY_PEARL = RotationQueue.PRIORITY_PEARL;

    /** Prioritaet fuer sonstige Item-Aktionen (Trank, Blockplatzierung, ...). */
    public static final int PRIORITY_MISC = RotationQueue.PRIORITY_MISC;

    /** Niedrigste Prioritaet: rein kosmetisches Free-Look, darf echte Aktionen nie verdraengen. */
    public static final int PRIORITY_LOOK = RotationQueue.PRIORITY_LOOK;

    private final InventoryManager inventory;
    private final RotationQueue rotations;
    /** +/- Spanne, um die der Rettungswurf-Pitch gestreut wird. */
    private final double rescueVariance;
    /** Prioritaet, mit der der Perlenwurf in die Rotations-Queue eingereiht wird. */
    private final int reservePriority;
    private final Random rng;

    public CombatExecutor(InventoryManager inventory, RotationQueue rotations,
                           double rescueVariance, int reservePriority, Random rng) {
        this.inventory = inventory;
        this.rotations = rotations;
        this.rescueVariance = rescueVariance;
        this.reservePriority = reservePriority;
        this.rng = rng;
    }

    /**
     * Reiht Rotation + Aktion ein. Die Prioritaeten- und Starvation-Semantik liegt in
     * {@link RotationQueue#execute}.
     *
     * @param callback darf {@code null} sein (reine Rotation ohne Aktion, z.B. Free-Look)
     */
    public boolean execute(double yaw, double pitch, int priority, Runnable callback) {
        return rotations.execute(yaw, pitch, priority, callback);
    }

    /**
     * Wirft die Perle auf eine fertige {@link PearlAim}-Loesung: Perle suchen, ggf. streuen,
     * ausrichten und im Rotations-Callback benutzen.
     *
     * @param rescueThrow {@code true} fuer den Anti-Fall-Rettungswurf nach einer Explosion —
     *                    nur der bekommt die {@link PitchVariance}-Streuung. Normale
     *                    Kampf-/Gap-Close-Wuerfe gehen unveraendert raus, weil ihr Winkel
     *                    vom Ballistik-Solver exakt bestimmt wird und eine Zufallsabweichung
     *                    hier den Wurf regelmaessig ins Leere traegt.
     * @return {@code true}, wenn der Wurf in die Rotations-Queue eingereiht wurde
     */
    public boolean executePearl(PearlAim aim, boolean rescueThrow) {
        if (aim == null) return false;

        FindItemResult pearl = InvHelper.find(Items.ENDER_PEARL);
        if (!pearl.found() || !InvHelper.isHotbarOrOffhand(pearl)) return false;

        double pitch = rescueThrow ? PitchVariance.apply(aim.pitch(), rescueVariance, rng) : aim.pitch();
        InteractionHand hand = pearl.isOffhand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        Minecraft mc = Minecraft.getInstance();
        return queueWithItem(pearl, aim.yaw(), pitch, reservePriority,
            () -> mc.gameMode.useItem(mc.player, hand));
    }

    /**
     * Item-Aktion, die fuer die Dauer des Rotations-Callbacks eine Combat-Slot-Reserve
     * haelt: der Slot wird vor dem Einreihen getauscht, im {@code finally} des Callbacks
     * (und noch einmal im {@code finally} des Einreihens, falls es gar nicht erst zur
     * Ausfuehrung kommt) wieder freigegeben - Baritone/Follow/Pathing bleiben so waehrend
     * Slot-Wechsel und Use-Item ausgeschlossen.
     * Die Reserve selbst haelt und gibt {@link InventoryManager} frei.
     */
    public boolean queueWithItem(FindItemResult item, double yaw, double pitch, int priority, Runnable action) {
        return inventory.queueWithItem(item, yaw, pitch, priority, action);
    }

    /** Setzt die Tick-Zaehlung der Rotations-Queue zurueck — einmal pro Tick, am Anfang. */
    public void onTick() {
        rotations.onTick();
    }

    /** Free-Look-Tail darf diesen Tick nur laufen, wenn das {@code false} ist. */
    public boolean hasRealActionThisTick() {
        return rotations.hasRealActionThisTick();
    }
}
