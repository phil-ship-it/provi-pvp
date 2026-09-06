package com.provipvp.modules;

import com.provipvp.ProviPvPAddon;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.BrandPayload;

/** Mod-Erkennungs-Schutz, ehrlich im Umfang: faelscht nur den Client-Brand-String (server-seitig sichtbar
 *  z.B. via /brand-Plugins oder F3-Anzeige beim Server), den Fabric normalerweise als "fabric" meldet -
 *  die simpelste, verbreitetste automatische Mod-Erkennung. Schuetzt NICHT vor Verhaltens-Analyse
 *  (Bewegungsmuster, Timing, Rotation) - dafuer siehe die Silent-Rotation-Einstellungen in GodmodePvP/HumanPvP. */
public class BrandSpoof extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public final Setting<String> spoofedBrand = sgGeneral.add(new StringSetting.Builder()
        .name("spoofed-brand")
        .description("Client-Brand, der dem Server gemeldet wird, statt \"fabric\".")
        .defaultValue("vanilla")
        .build()
    );

    public BrandSpoof() {
        super(ProviPvPAddon.CATEGORY, "brand-spoof", "Meldet dem Server einen falschen Client-Brand statt \"fabric\" - verhindert die simpelste automatische Erkennung eines modifizierten Clients.");
    }

    @EventHandler
    private void onSend(PacketEvent.Send event) {
        if (!(event.packet instanceof ServerboundCustomPayloadPacket packet)) return;
        if (!(packet.payload() instanceof BrandPayload brand)) return;
        if (brand.brand().equals(spoofedBrand.get())) return;

        event.setCancelled(true);
        event.sendSilently(new ServerboundCustomPayloadPacket(new BrandPayload(spoofedBrand.get())));
    }
}
