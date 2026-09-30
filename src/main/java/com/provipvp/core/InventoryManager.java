package com.provipvp.core;

import com.provipvp.util.InvHelper;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.function.Predicate;

/** Kapselt {@link InvHelper#find} und den harten Mainhand-Mutex aus
 *  {@code GodmodePvP.reserveCombatSlot} / {@code releaseCombatSlot} / {@code withCombatSlot} /
 *  {@code queueWithCombatSlot}.
 *
 *  Der Mutex sichert einen Slot-Wechsel fuer die Dauer genau einer Aktion ab: waehrend der
 *  Reservierung darf kein Inventory-Select (Baritone, Follow, eigenes Quick-Swap) dazwischenfunken,
 *  weil dann der Slot-Restore auf dem falschen Slot landet. Meteor {@code InvUtils.previousSlot}
 *  wird bewusst NICHT benutzt - das ist ein globaler Merkposten, den sich Module mit
 *  laenger anhaltenden Swaps (Schild, Trank) sonst gegenseitig ueberschreiben.
 *
 *  <p>Kein {@code mc.*}-Zugriff: der Spieler kommt ueber den Konstruktor, geswappt wird ueber
 *  {@link InvUtils}, die Callback-Ausfuehrung ueber {@link Rotations}. */
public final class InventoryManager {

    /** Wird nach erfolgreicher Reservierung aufgerufen, um autonome Navigation (Baritone/
     *  Follow/CustomGoal) fuer die Dauer der Aktion anzuhalten. */
    private final Runnable pauseAutonomy;
    private final Player self;

    private boolean reserved;
    private int reservedSlot = -1;
    private int previousSlot = -1;

    /** @param pauseAutonomy autonome Navigation anhalten; {@code null} = nichts zu tun */
    public InventoryManager(Player self, Runnable pauseAutonomy) {
        this.self = self;
        this.pauseAutonomy = pauseAutonomy;
    }

    public InventoryManager(Player self) {
        this(self, null);
    }

    // ---------- Itemsuche ----------

    /** Zweistufige Suche: erst Hotbar, sonst der Rest inkl. Offhand. */
    public FindItemResult find(Item item) {
        return InvHelper.find(item);
    }

    /** Zweistufige Suche: erst Hotbar, sonst der Rest inkl. Offhand. */
    public FindItemResult find(Predicate<ItemStack> predicate) {
        return InvHelper.find(predicate);
    }

    public boolean has(Item item) {
        return InvHelper.has(item);
    }

    // ---------- Mainhand-Mutex ----------

    public boolean reserved() {
        return reserved;
    }

    /** Der aktuell reservierte Hotbar-Slot, oder -1. */
    public int reservedSlot() {
        return reservedSlot;
    }

    /** Reserviert {@code slot} (0..8) als Mainhand. Ein Offhand- oder Rucksack-Slot wird hier
     *  bewusst abgelehnt - dafuer gibt es {@link #withItem}. */
    public boolean reserve(int slot) {
        if (self == null || slot < 0 || slot > 8 || reserved) return false;
        int selected = self.getInventory().getSelectedSlot();
        if (selected != slot && !InvUtils.swap(slot, false)) return false;
        reserved = true;
        reservedSlot = slot;
        previousSlot = selected == slot ? -1 : selected;
        if (pauseAutonomy != null) pauseAutonomy.run();
        return true;
    }

    /** Gibt die Reserve zurueck und stellt den vorherigen Slot wieder her - aber nur, wenn
     *  wirklich noch der reservierte Slot in der Mainhand liegt (sonst hat jemand dazwischen
     *  gewechselt und wir wuerden fremd zuruecktauschen). */
    public void release() {
        if (reserved && previousSlot >= 0 && self != null
            && self.getInventory().getSelectedSlot() == reservedSlot) {
            InvUtils.swap(previousSlot, false);
        }
        reserved = false;
        previousSlot = -1;
        reservedSlot = -1;
    }

    /** Blockiert die Aktion, solange ein ANDERER Slot reserviert ist. Ein Offhand-Treffer braucht
     *  keinen Mainhand-Slot und blockiert deshalb nie. */
    public boolean busyFor(FindItemResult item) {
        return !item.isOffhand() && reserved && reservedSlot != item.slot();
    }

    /** Fuehrt {@code action} mit passendem Item in der Mainhand aus. Offhand: direkt ausfuehren,
     *  NIE swappen (das wuerde das Item aus der Offhand in die Mainhand verschieben). Rest des
     *  Inventars: abgelehnt, dafuer gibt es den Quick-Swap-Pfad des Moduls. */
    public boolean withItem(FindItemResult item, Runnable action) {
        if (!InvHelper.isHotbarOrOffhand(item)) return false;
        if (item.isOffhand()) {
            action.run();
            return true;
        }
        if (!reserve(item.slot())) return false;
        try {
            action.run();
            return true;
        } finally {
            release();
        }
    }

    /** Wie {@link #withItem}, aber die Ausfuehrung wird in die Rotations-Queue gehaengt, damit die
     *  Aktion im passenden Tick mit passender Blickrichtung laeuft. Die Reserve wird sowohl beim
     *  Scheitern der Einreihung als auch im Callback per {@code finally} wieder freigegeben. */
    public boolean queueWithItem(FindItemResult item, double yaw, double pitch, int priority, Runnable action) {
        if (!InvHelper.isHotbarOrOffhand(item)) return false;
        boolean offhand = item.isOffhand();
        if (!offhand && !reserve(item.slot())) return false;

        Rotations.rotate(yaw, pitch, priority, false, () -> {
            try {
                action.run();
            } finally {
                if (!offhand) release();
            }
        });
        return true;
    }
}
