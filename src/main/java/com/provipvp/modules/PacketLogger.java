package com.provipvp.modules;

import com.provipvp.ProviPvPAddon;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.Packet;

/** Reines Debug-/Beobachtungswerkzeug - liest nur mit, veraendert und blockiert nichts. Nuetzlich um
 *  nachzuvollziehen, welche Pakete bei bestimmtem Server-Verhalten (Rubberband, Anti-Cheat-Reaktionen,
 *  Kick-Gruende) tatsaechlich ankommen/rausgehen, ohne den Client-Log-Umweg. */
public class PacketLogger extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public final Setting<Boolean> logReceive = sgGeneral.add(new BoolSetting.Builder()
        .name("log-receive")
        .description("Loggt eingehende Pakete in den Chat.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> logSend = sgGeneral.add(new BoolSetting.Builder()
        .name("log-send")
        .description("Loggt ausgehende Pakete in den Chat.")
        .defaultValue(false)
        .build()
    );

    public final Setting<String> filter = sgGeneral.add(new StringSetting.Builder()
        .name("filter")
        .description("Nur Paket-Klassennamen, die diesen Text enthalten (Gross/Klein egal), werden geloggt. Leer = alle.")
        .defaultValue("")
        .build()
    );

    public PacketLogger() {
        super(ProviPvPAddon.CATEGORY, "packet-logger", "Loggt ein-/ausgehende Netzwerkpakete zum Debuggen - reines Beobachtungswerkzeug, veraendert nichts am Verhalten.");
    }

    @EventHandler
    private void onReceive(PacketEvent.Receive event) {
        if (logReceive.get()) log("<-", event.packet);
    }

    @EventHandler
    private void onSend(PacketEvent.Send event) {
        if (logSend.get()) log("->", event.packet);
    }

    private void log(String arrow, Packet<?> packet) {
        String name = packet.getClass().getSimpleName();
        String f = filter.get();
        if (!f.isEmpty() && !name.toLowerCase().contains(f.toLowerCase())) return;
        ChatUtils.info("§7[Packet %s] %s", arrow, name);
    }
}
