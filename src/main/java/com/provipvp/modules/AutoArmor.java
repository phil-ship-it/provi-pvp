package com.provipvp.modules;

import com.provipvp.ProviPvPAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.equipment.Equippable;

/** Ruestet automatisch das staerkste verfuegbare Ruestungsteil pro Slot aus dem gesamten Inventar aus -
 *  bewertet nach echtem Ruestungs-/Zaehigkeits-Attributwert (Attributes.ARMOR/ARMOR_TOUGHNESS), nicht nach
 *  Material-Namen-Raten. Funktioniert deshalb korrekt unabhaengig von Verzauberungen oder Custom-Items:
 *  ein verzaubertes Diamant-Chestplate schlaegt so automatisch ein unverzaubertes Netherite-Chestplate,
 *  falls es tatsaechlich mehr Schutz bietet. */
public class AutoArmor extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public final Setting<Boolean> announce = sgGeneral.add(new BoolSetting.Builder()
        .name("announce")
        .description("Meldet im Chat, wenn ein Ruestungsteil aufgewertet wird.")
        .defaultValue(true)
        .build()
    );

    private static final EquipmentSlot[] ARMOR_SLOTS = {
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private int tickCounter;

    public AutoArmor() {
        super(ProviPvPAddon.CATEGORY, "auto-armor", "Ruestet automatisch das staerkste gefundene Ruestungsteil pro Slot aus dem gesamten Inventar aus (Bewertung nach echtem Ruestungs-/Zaehigkeits-Attributwert, nicht nach Materialname).");
    }

    @Override
    public void onActivate() {
        tickCounter = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null) return;

        tickCounter++;
        if (tickCounter % 10 != 0) return; // alle 0.5s reicht, kein Grund das jeden Tick zu scannen

        for (EquipmentSlot slot : ARMOR_SLOTS) {
            upgradeSlot(slot);
        }
    }

    private void upgradeSlot(EquipmentSlot slot) {
        ItemStack current = mc.player.getItemBySlot(slot);
        double bestScore = current.isEmpty() ? -1 : score(current, slot);

        int bestIndex = -1;
        ItemStack bestStack = null;

        var inv = mc.player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;

            Equippable eq = stack.get(DataComponents.EQUIPPABLE);
            if (eq == null || eq.slot() != slot) continue;

            double s = score(stack, slot);
            if (s > bestScore) {
                bestScore = s;
                bestIndex = i;
                bestStack = stack;
            }
        }

        if (bestIndex < 0) return;

        InvUtils.move().from(bestIndex).toArmor(slot.getIndex());
        if (announce.get()) {
            ChatUtils.info("§7[AutoArmor] %s aufgeruestet: %s", slot.getName(), bestStack.getDisplayName().getString());
        }
    }

    /** Reiner Ruestungspunkt-Wert zaehlt zehnmal mehr als Zaehigkeit - Zaehigkeit ist nur der Tiebreaker
     *  zwischen zwei Teilen mit sonst gleichem Schutz (z.B. Diamant vs. Netherite bei identischer Verzauberung). */
    private double score(ItemStack stack, EquipmentSlot slot) {
        ItemAttributeModifiers mods = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        double armor = mods.compute(Attributes.ARMOR, 0, slot);
        double toughness = mods.compute(Attributes.ARMOR_TOUGHNESS, 0, slot);
        return armor * 10 + toughness;
    }
}
