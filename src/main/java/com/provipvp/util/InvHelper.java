package com.provipvp.util;

import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.function.Predicate;

/** Buendelt das Zwei-Stufen-Suchmuster "erst Hotbar (kein Slot-Wechsel noetig), dann Rest des
 *  Inventars" - tauchte vorher als identisches
 *  {@code FindItemResult r = InvUtils.findInHotbar(x); if (!r.found()) r = InvUtils.find(x);}
 *  rund 25x sowohl in GodmodePvP als auch in HumanPvP auf. */
public final class InvHelper {
    private InvHelper() {}

    public static FindItemResult find(Item item) {
        FindItemResult r = InvUtils.findInHotbar(item);
        return r.found() ? r : InvUtils.find(item);
    }

    public static FindItemResult find(Predicate<ItemStack> predicate) {
        FindItemResult r = InvUtils.findInHotbar(predicate);
        return r.found() ? r : InvUtils.find(predicate);
    }

    /** Reine Verfuegbarkeits-Abfrage, ohne das Ergebnis (Slot etc.) zu benoetigen. */
    public static boolean has(Item item) {
        return find(item).found();
    }
}
