package com.provipvp.modules;

import com.provipvp.ProviPvPAddon;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringListSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;

import java.util.List;

/** Kontrolle darueber, was tatsaechlich an den Server rausgeht: jeder Eintrag in der Liste ist ein Text,
 *  der im Klassennamen eines ausgehenden Pakets vorkommen darf, um es zu blockieren (z.B. "ServerboundChat"
 *  blockiert jegliche Chat-Pakete, unabhaengig davon was reingetippt wird). Case-insensitiv, mehrere Eintraege
 *  moeglich. Greift VOR dem Senden - der Server sieht das Paket nie. */
public class PacketFilter extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public final Setting<List<String>> blockedOutgoing = sgGeneral.add(new StringListSetting.Builder()
        .name("blocked-outgoing")
        .description("Ausgehende Pakete, deren Klassenname einen dieser Texte enthaelt (Gross/Klein egal), werden nie an den Server gesendet.")
        .defaultValue()
        .build()
    );

    public final Setting<Boolean> announce = sgGeneral.add(new BoolSetting.Builder()
        .name("announce")
        .description("Meldet im Chat, wenn ein Paket blockiert wurde.")
        .defaultValue(true)
        .build()
    );

    public PacketFilter() {
        super(ProviPvPAddon.CATEGORY, "packet-filter", "Blockiert gezielt ausgehende Pakete anhand des Klassennamens, bevor sie den Server ueberhaupt erreichen.");
    }

    @EventHandler
    private void onSend(PacketEvent.Send event) {
        String name = event.packet.getClass().getSimpleName();

        for (String blocked : blockedOutgoing.get()) {
            if (blocked.isBlank()) continue;
            if (!name.toLowerCase().contains(blocked.toLowerCase())) continue;

            event.setCancelled(true);
            if (announce.get()) ChatUtils.info("§c[PacketFilter] Blockiert: %s", name);
            return;
        }
    }
}
