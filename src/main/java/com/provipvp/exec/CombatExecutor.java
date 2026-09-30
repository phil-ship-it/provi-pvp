package com.provipvp.exec;

import com.provipvp.core.InventoryManager;
import com.provipvp.pearl.PearlAim;
import com.provipvp.util.InvHelper;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.Rotations;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;

import java.util.Random;

/** Fuehrt einen bereits berechneten Wurf bzw. eine bereits berechnete Combat-Aktion aus.
 *
 *  <p>Aus {@code GodmodePvP.rotateAndRun} (:3072) und {@code queueWithCombatSlot} (:3130)
 *  herausgelöst, damit die Rotations-Warteschlange an genau einer Stelle liegt: der Erste
 *  Eintrag eines Ticks laeuft ueber Meteors normalen Bewegungspaket-Pfad (echte Rotation wird
 *  gesetzt), jeder weitere wird mit {@code clientSide=true} ebenfalls kurzzeitig auf die
 *  echte Rotation gesetzt - exakt fuer die Dauer seines eigenen Callbacks
 *  (siehe {@code Rotations.onSendMovementPacketsPost}).
 *
 *  <p>Keine Modul-Settings: Spanne, Prioritaet und Zufallsquelle kommen per Konstruktor,
 *  damit der Executor ohne Meteor-Modul und ohne Setting-Group testbar bleibt. */
public final class CombatExecutor {
    /** Prioritaet eines Perlenwurfs, portiert aus {@code GodmodePvP.PRIORITY_PEARL}. */
    public static final int PRIORITY_PEARL = 70;

    /** Prioritaet fuer sonstige Item-Aktionen (Trank, Blockplatzierung, ...). */
    public static final int PRIORITY_MISC = 60;

    /** Niedrigste Prioritaet: rein kosmetisches Free-Look, darf echte Aktionen nie verdraengen. */
    public static final int PRIORITY_LOOK = 0;

    private final InventoryManager inventory;
    /** +/- Spanne, um die der Rettungswurf-Pitch gestreut wird. */
    private final double rescueVariance;
    /** Prioritaet, mit der der Perlenwurf in die Rotations-Queue eingereiht wird. */
    private final int reservePriority;
    private final Random rng;

    /** Anzahl der diesen Tick bereits eingereihten Rotationen - steuert {@code clientSide}. */
    private int rotationsThisTick;

    /** Ob diesen Tick bereits eine "echte" Aktion gerendert wurde, siehe {@link #execute}. */
    private boolean realActionThisTick;

    public CombatExecutor(InventoryManager inventory, double rescueVariance, int reservePriority, Random rng) {
        this.inventory = inventory;
        this.rescueVariance = rescueVariance;
        this.reservePriority = reservePriority;
        this.rng = rng;
    }

    /**
     * Reiht Rotation + Aktion in Meteors Rotations-Queue ein und gibt immer {@code true}
     * zurueck (der Erfolgsfall greift immer - frueheren Aufrufern, die auf {@code false}
     * pruefen mussten, bleibt das Verhalten unveraendert).
     *
     * <p><b>Rotations-Queue-Starvation:</b> {@code rotationsThisTick} allein reicht als
     * "diesen Tick ist die Rotation schon vergeben"-Merkmal nicht, weil der Callback
     * asynchron in {@code Rotations.onSendMovementPacketsPost} laeuft: eine echte Aktion
     * kann bereits gequeued sein ({@code rotationsThisTick > 0}), ihr Callback aber noch
     * gar nicht gelaufen sein. Genau dann wuerde der kosmetische Free-Look-Tail-Flush
     * (Prioritaet {@link #PRIORITY_LOOK}, siehe {@code pendingFreeLook} in GodmodePvP :1389)
     * als letzter Eintrag doch noch die Rotation beanspruchen und die echte Aktion
     * verdraengen bzw. hinter sich schieben. Deshalb wird {@link #realActionThisTick}
     * gesetzt, <b>wenn und nur wenn</b> {@code priority > PRIORITY_LOOK} - nur diese
     * Prioritaeten sind Aktionen, die den Slot wirklich verdienen; ein Free-Look selbst
     * setzt das Flag nicht und kann sich damit nicht selbst unterdruecken.
     *
     * @param callback darf {@code null} sein (reine Rotation ohne Aktion, z.B. Free-Look)
     */
    public boolean execute(double yaw, double pitch, int priority, Runnable callback) {
        Rotations.rotate(yaw, pitch, priority, rotationsThisTick > 0, callback);
        rotationsThisTick++;
        if (priority > PRIORITY_LOOK) realActionThisTick = true;
        return true;
    }

    /**
     * Wirft die Perle auf eine fertige {@link PearlAim}-Loesung: Perle suchen, ggf. streuen,
     * ausrichten und im Rotations-Callback benutzen.
     *
     * @param rescueThrow {@code true} fuer den Anti-Fall-Rettungswurf nach einer Explosion -
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

    /** Setzt die Tick-Zaehlung zurueck - einmal pro Tick, am Anfang. */
    public void onTick() {
        rotationsThisTick = 0;
        realActionThisTick = false;
    }

    /** Free-Look-Tail darf diesen Tick nur laufen, wenn das {@code false} ist. */
    public boolean hasRealActionThisTick() {
        return realActionThisTick;
    }
}
