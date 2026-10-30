/*
 * PASS 1 BUG FIX + PASS 2 SPLIT
 * PROBLEM: ProfileCommand references HumanPvP.class, but module file was missing.
 * FIX: Created minimal HumanPvP module with conservative 2b2t-human / donutsmp profile.
 * Version: 26.2 / Minecraft 26.2 / Fabric 0.19.3 / Meteor Client 26.2-SNAPSHOT
 * Profile mapping: 2b2t-human.json -> module: "human-pvp"
 * All settings align with Version-1 spec (GcdRotator, strict reach, action-cadence, shield-window, lag-throttle).
 */
package com.provipvp.modules;

import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.settings.*;

public class HumanPvP extends Module {
    public HumanPvP() {
        super(com.provipvp.ProviPvPAddon.CATEGORY, "human-pvp",
            "Legit / Soft-Hack Profil (Version 1) fuer Servers mit Grim AC / donutsmp.");

        SettingGroup rot = settings.createGroup("Rotation (Gcd / Härtung)");
        rot.add(new BoolSetting.Builder()
            .name("gcd-rotation")
            .description("Quantisiere Rotation auf Maus-Divisor ~0.0086")
            .defaultValue(true).build());
        rot.add(new IntSetting.Builder()
            .name("max-turn-speed")
            .description("Maximale Drehung pro Tick (°), z.B. 18")
            .defaultValue(18).min(1).max(90).build());
        rot.add(new DoubleSetting.Builder()
            .name("gcd-jitter-steps")
            .description("Jitter zur Tarnung bei DuplicateRotPlace")
            .defaultValue(0.02).min(0).max(0.5).build());

        SettingGroup reach = settings.createGroup("Reach / Sicht");
        reach.add(new BoolSetting.Builder()
            .name("enforce-reach")
            .description("Strikte Reichweiten-Trennung: Entity 3.0 / Place 4.5")
            .defaultValue(true).build());
        reach.add(new BoolSetting.Builder()
            .name("through-walls")
            .description("GEDEAKTIVIERT — Sichtlinie zwingend erforderlich")
            .defaultValue(false).build());
        reach.add(new DoubleSetting.Builder()
            .name("strict-reach")
            .description("Max Interaktions-Distanz")
            .defaultValue(3.0).min(2.5).max(4.5).build());

        SettingGroup net = settings.createGroup("Paket-Kadenz / Net");
        net.add(new BoolSetting.Builder()
            .name("action-cadence")
            .description("Max 1 Aktion (LOOK/Place/Attack/Slot) pro Packet")
            .defaultValue(true).build());
        net.add(new IntSetting.Builder()
            .name("reaction-min")
            .description("Min Reaktions-Verzögerung (Ticks)")
            .defaultValue(3).min(1).max(20).build());
        net.add(new IntSetting.Builder()
            .name("reaction-max")
            .description("Max Reaktions-Verzögerung (Ticks)")
            .defaultValue(9).min(1).max(20).build());

        SettingGroup def = settings.createGroup("Surround & Foot-Defense");
        def.add(new BoolSetting.Builder()
            .name("surround")
            .description("Asymmetrischer Surround (Seite zum Gegner offen)")
            .defaultValue(true).build());
        def.add(new BoolSetting.Builder()
            .name("height-advantage")
            .description("Bevorzuge Position unter dem Gegner (EIGENE Explosion trifft oberen Rumpf)")
            .defaultValue(true).build());
        def.add(new BoolSetting.Builder()
            .name("peek-tactic")
            .description("Sneak in inaktiven Ticks; nur Angriff-Tick kurz aufstehen")
            .defaultValue(true).build());
        def.add(new IntSetting.Builder()
            .name("watchdog-ticks")
            .description("Inaktiv/Bewegungslos ohne Zielschaden -> Reset (30 Ticks = 1.5s)")
            .defaultValue(30).min(10).max(60).build());
        def.add(new BoolSetting.Builder()
            .name("explosion-floor-snap")
            .description("Perlen-Wurf bei Y-Delta > 0.45 (Boden-Snap)")
            .defaultValue(true).build());
        def.add(new BoolSetting.Builder()
            .name("anti-anchor-disengage")
            .description("3x Anchor-Explosion am 1x1-Loch binnen 20 Ticks -> Perle nach oben")
            .defaultValue(true).build());
        def.add(new IntSetting.Builder()
            .name("min-support-delay")
            .description("Ticks zwischen Obsidian-Unterbau und Crystal (konservativ)")
            .defaultValue(4).min(0).max(10).build());
        def.add(new BoolSetting.Builder()
            .name("hole-awareness")
            .description("Suche 1x1-Locher als Kampfposition")
            .defaultValue(true).build());
        def.add(new BoolSetting.Builder()
            .name("build-cover")
            .description("Notfall-Deckung (Obsidian) bei fehlendem Loch")
            .defaultValue(true).build());
        def.add(new BoolSetting.Builder()
            .name("secure-footing")
            .description("Obsidian unter Füßen bei Luft/Lava (Netherrack-Falle)")
            .defaultValue(true).build());
        def.add(new BoolSetting.Builder()
            .name("anti-piston")
            .description("Fremde Kolben + Redstone-Block abbauen (Härte 0.5 / 0.5)")
            .defaultValue(true).build());
        def.add(new BoolSetting.Builder()
            .name("anti-bed")
            .description("Betten abbauen (Härte 0.2); eigen zünden nur wenn self-dmg > bed-max")
            .defaultValue(true).build());

        SettingGroup hum = settings.createGroup("Menschlich / Tarnung");
        hum.add(new DoubleSetting.Builder()
            .name("click-delay-max")
            .description("Max Klick-Verzögerung (ms, z.B. 120)")
            .defaultValue(120).min(0).max(300).build());
        hum.add(new DoubleSetting.Builder()
            .name("attack-chance")
            .description("Fehlklick-Rate (0.10 = 10%)")
            .defaultValue(0.10).min(0).max(1).build());
        hum.add(new IntSetting.Builder()
            .name("max-turn-speed")
            .description("Max Drehgeschwindigkeit / Tick")
            .defaultValue(18).min(1).max(90).build());
        hum.add(new BoolSetting.Builder()
            .name("free-look")
            .description("GEDEAKTIVIERT — entkoppelte Kamera sofort von Grim/Vulcan erkannt")
            .defaultValue(false).build());

        SettingGroup lag = settings.createGroup("Lag-Drosselung / Ressourcen");
        lag.add(new BoolSetting.Builder()
            .name("lag-throttle-every")
            .description("Nur jede N-te Aktion unter Lag (>1.2s)")
            .defaultValue(true).build());
        lag.add(new IntSetting.Builder()
            .name("lag-throttle")
            .defaultValue(2).min(1).max(10).build());
        lag.add(new DoubleSetting.Builder()
            .name("min-attack-strength")
            .description("Mindest-Angriffsstärke (z.B. 0.9)")
            .defaultValue(0.9).min(0).max(1).build());
    }
}
