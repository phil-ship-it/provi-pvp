package com.provipvp.modules;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.provipvp.ProviPvPAddon;
import meteordevelopment.meteorclient.commands.Commands;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringListSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;

import java.util.List;

/** Chat-Trigger-Makro: fuehrt einen Meteor-Client-Befehl aus, sobald eine eingehende Chat-Nachricht einen
 *  konfigurierten Text enthaelt. Bewusst NUR Chat-Trigger - Paket- oder Inventar-Zustands-Trigger brauchen
 *  eigene Kontext-Infrastruktur (Paket-Typ-Whitelist bzw. Inventar-Diff-Tracking), die es aktuell nirgends
 *  im Projekt gibt und die hier nicht blind mit-erfunden wird. */
public class MacroTriggerModule extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public final Setting<List<String>> triggers = sgGeneral.add(new StringListSetting.Builder()
        .name("triggers")
        .description("Ein Eintrag pro Zeile im Format \"ausloeser=>befehl\", z.B. \"gg=>.pvp off\". Der Ausloeser wird als Teiltext gross/klein-unabhaengig gegen jede eingehende Chat-Nachricht geprueft.")
        .defaultValue()
        .build()
    );

    public final Setting<Boolean> announce = sgGeneral.add(new BoolSetting.Builder()
        .name("announce")
        .description("Meldet im Chat, wenn ein Makro ausgeloest wurde.")
        .defaultValue(true)
        .build()
    );

    public MacroTriggerModule() {
        super(ProviPvPAddon.CATEGORY, "macro-trigger", "Fuehrt einen Meteor-Befehl aus, sobald eine Chat-Nachricht einen konfigurierten Ausloeser-Text enthaelt.");
    }

    @EventHandler
    private void onReceiveMessage(ReceiveMessageEvent event) {
        String text = event.getMessage().getString();

        for (String entry : triggers.get()) {
            int split = entry.indexOf("=>");
            if (split < 0) continue;

            String trigger = entry.substring(0, split).trim();
            String command = entry.substring(split + 2).trim();
            if (trigger.isEmpty() || command.isEmpty()) continue;
            if (!text.toLowerCase().contains(trigger.toLowerCase())) continue;

            String toRun = command.startsWith(".") ? command.substring(1) : command;
            try {
                Commands.dispatch(toRun);
                if (announce.get()) ChatUtils.info("§7[Macro] \"%s\" ausgeloest -> %s", trigger, command);
            } catch (CommandSyntaxException e) {
                ChatUtils.error("Macro-Befehl ungueltig: %s (%s)", command, e.getMessage());
            }
        }
    }
}
