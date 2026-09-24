package com.provipvp.modules;

import com.provipvp.util.InvHelper;
import com.provipvp.util.PvpMath;

import baritone.api.BaritoneAPI;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.entity.EntityAddedEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EntityTypeListSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.combat.CrystalAura;
import meteordevelopment.meteorclient.systems.modules.combat.KillAura;
import meteordevelopment.meteorclient.systems.modules.player.AutoMend;
import meteordevelopment.meteorclient.systems.modules.player.AutoEat;
import meteordevelopment.meteorclient.systems.modules.movement.NoFall;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.entity.DamageUtils;
import meteordevelopment.meteorclient.utils.entity.fakeplayer.FakePlayerEntity;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.meteorclient.utils.misc.input.Input;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.BedItem;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class GodmodePvP extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgCombat = settings.createGroup("Kampf");
    private final SettingGroup sgDefense = settings.createGroup("Schutz");
    private final SettingGroup sgInv = settings.createGroup("Inventar");
    private final SettingGroup sgMobs = settings.createGroup("Mobs");
    private final SettingGroup sgPearl = settings.createGroup("Enderperlen");
    private final SettingGroup sgHeal = settings.createGroup("Heilung");

    // General
    public final Setting<Boolean> follow = sgGeneral.add(new BoolSetting.Builder()
        .name("follow")
        .description("Verfolgt das Ziel automatisch mit Baritone, sobald es in Engage-Distanz ist oder dort stillsteht.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Integer> followRange = sgGeneral.add(new IntSetting.Builder()
        .name("follow-range")
        .description("Maximale Distanz, ab der ein Spieler ueberhaupt als Ziel erkannt/beobachtet wird.")
        .defaultValue(40)
        .range(8, 64)
        .sliderRange(8, 48)
        .build()
    );

    public final Setting<Integer> engageDistance = sgGeneral.add(new IntSetting.Builder()
        .name("engage-distance")
        .description("Erst ab dieser Distanz laeuft/perlt der Bot aktiv auf das Ziel zu. Darueber hinaus (bis follow-range) wird nur beobachtet/anvisiert, ohne loszurennen - verhindert, dass der Bot beim Aktivieren quer ueber die Karte auf jeden Spieler zusprintet.")
        .defaultValue(16)
        .range(4, 64)
        .sliderRange(4, 40)
        .build()
    );

    public final Setting<Boolean> pursueStationaryTargets = sgGeneral.add(new BoolSetting.Builder()
        .name("pursue-stationary-targets")
        .description("Schliesst die Distanz zu einem stillstehenden Ziel auch ausserhalb der Engage-Distanz. Bewegte Ziele behalten die Kaltstart-Bremse, damit der Bot beim Aktivieren nicht quer ueber die Karte sprintet.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Double> attackRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("attack-range")
        .description("Maximale Distanz fuer Nahkampf-Schlaege (pre-hit, melee-fallback, Pop-Burst). Manche Server/Anti-Cheats tolerieren mehr oder weniger als das Standard-3.6.")
        .defaultValue(3.6)
        .range(2.5, 4.5)
        .sliderRange(2.5, 4.0)
        .build()
    );

    public final Setting<Boolean> smartTargeting = sgGeneral.add(new BoolSetting.Builder()
        .name("smart-targeting")
        .description("Bevorzugt bei der Zielwahl einen isolierten Gegner (ohne Mitspieler in Rueckendeckungs-Reichweite) vor reiner Distanz - ein alleine stehender Spieler ist ein sichereres, schnelleres Ziel als einer mit Unterstuetzung. Wirkt nur auf die ANFANGS-Zielwahl, ein bereits engagiertes Ziel wird nicht mehr gewechselt.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Double> backupRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("backup-range")
        .description("Ab welcher Naehe zu einem anderen Spieler ein Ziel als 'hat Rueckendeckung' gilt (fuer smart-targeting).")
        .defaultValue(10.0)
        .range(4.0, 24.0)
        .sliderRange(4.0, 20.0)
        .visible(smartTargeting::get)
        .build()
    );

    public final Setting<Double> popThreshold = sgGeneral.add(new DoubleSetting.Builder()
        .name("pop-threshold")
        .description("Health-Drop, der als Totem-Pop gewertet wird.")
        .defaultValue(8.0)
        .range(4.0, 18.0)
        .sliderRange(4.0, 16.0)
        .build()
    );

    public final Setting<Integer> leadTicks = sgGeneral.add(new IntSetting.Builder()
        .name("prediction-ticks")
        .description("Wie weit die Gegner-Bewegung fuer Angriffe vorhergesagt wird.")
        .defaultValue(5)
        .range(0, 8)
        .sliderRange(0, 6)
        .build()
    );

    public final Setting<Boolean> ignoreFire = sgGeneral.add(new BoolSetting.Builder()
        .name("ignore-fire")
        .description("Ignoriert Feuer am Boden im Nahbereich - laeuft geradewegs hindurch statt drumherum zu pathen. Baritone haelt Feuer sonst hart fuer unpassierbar (macht Umweg oder bleibt stehen).")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> freeLook = sgGeneral.add(new BoolSetting.Builder()
        .name("free-look")
        .description("Silent-Rotations: der Bot zielt/dreht sich fuer Angriffe, Platzierungen und Ziel-Verfolgung weiterhin korrekt (das Server-Paket bekommt die richtige Blickrichtung), aber deine eigene Kamera bleibt frei drehbar - du kannst dich umsehen, waehrend der Bot kaempft. ACHTUNG: separate Rotations-Pakete ohne dazu passende Kamerabewegung sind eines der klassischsten Anti-Cheat-Erkennungsmuster ueberhaupt (Vulcan/Grim/Matrix/NCP haben alle explizite Rotation-Checks dafuer) - auf Servern mit aktivem Anti-Cheat kann das zu Bewegungs-Korrekturen/Rubberbanding fuehren. Deshalb standardmaessig aus.")
        .defaultValue(false)
        .build()
    );

    // Combat
    public final Setting<Boolean> smartAuras = sgCombat.add(new BoolSetting.Builder()
        .name("smart-auras")
        .description("Waehlt Crystal oder Anchor nach Schadensrechnung.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Integer> anchorMode = sgCombat.add(new IntSetting.Builder()
        .name("anchor-mode")
        .description("Anchor: 0 = automatisch (immer max Schaden), 1 = Anchor schon bei Gleichstand, 2 = aus.")
        .defaultValue(1)
        .range(0, 2)
        .sliderRange(0, 2)
        .build()
    );

    public final Setting<Boolean> useAnchors = sgCombat.add(new BoolSetting.Builder()
        .name("use-anchors")
        .description("Anchor ueberhaupt erlauben (braucht 1 Glowstone pro Anchor).")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> useBeds = sgCombat.add(new BoolSetting.Builder()
        .name("use-beds")
        .description("Bed Aura: platziert und zuendet Betten als Explosion (Schadenswert 5.0, wie Anchor). Wirkt nur ausserhalb der Overworld (Nether/End, z.B. Portal-Camping auf 5b5t) - der Client kann das nicht vorab pruefen, das entscheidet allein der Server. Standardmaessig aus, damit in der Overworld nicht sinnlos Betten verbraucht werden (dort wird nur geschlafen/der Spawnpunkt gesetzt statt zu explodieren).")
        .defaultValue(false)
        .build()
    );

    public final Setting<Double> bedMinDamage = sgCombat.add(new DoubleSetting.Builder()
        .name("bed-min-damage")
        .description("Mindestschaden, den eine Bett-Explosion beim Ziel anrichten muss, damit Bett-Modus ueberhaupt in Frage kommt - unabhaengig vom Vergleich zu Crystal/Anchor. Verhindert ein Bett fuer eine fast wirkungslose Explosion (z.B. Ziel steht nur am Rand des Blast-Radius) zu verbrauchen. Realer, verifizierter Mechanik-Abgleich (Meteor BedAura 'min-damage', CandyCat BedAura 'minDmg').")
        .defaultValue(3.0)
        .range(0.5, 10.0)
        .sliderRange(0.5, 10.0)
        .build()
    );

    public final Setting<Double> bedMaxSelfDamage = sgCombat.add(new DoubleSetting.Builder()
        .name("bed-max-self-damage")
        .description("Eigener Eigenschaden-Deckel NUR fuer Bett-Explosionen. Muss getrennt vom allgemeinen max-self-damage einstellbar sein (Meteors eigenes BedAura-Modul hat aus demselben Grund einen eigenen Wert): eine Bett-Explosion macht im Nahbereich ein Vielfaches eines Crystals (live gemessen ~50 Rohschaden), also verwirft ein auf Crystal getrimmter 12er-Deckel restlos JEDEN erreichbaren Bett-Platz - Bed Aura feuert dann faktisch nie. Bett-PvP ist bewusst ein Trade: man nimmt Schaden in Kauf und faengt ihn mit Totem + Heiltrank ab. Zusaetzlich greift immer ein harter Selbstmord-Schutz (nie ein Platz, der die eigenen aktuellen HP toeten wuerde).")
        .defaultValue(16.0)
        .range(2.0, 36.0)
        .sliderRange(2.0, 36.0)
        .build()
    );

    public final Setting<Double> balanceResources = sgCombat.add(new DoubleSetting.Builder()
        .name("balance-resources")
        .description("Gleicht den Verbrauch der drei Explosiv-Ressourcen (Crystal/Anchor/Bett) aus: liegen zwei Optionen im Schaden dicht beieinander, gewinnt die, die bisher WENIGER verbraucht wurde (gezaehlt ueber Inventar-Deltas). Der Wert ist der maximale Schadens-Bonus (in HP), den diese Bevorzugung vergeben darf - ein echter Schadensvorsprung schlaegt den Ausgleich immer. 0 = aus. Gemessener Effekt (je drei 90s-Fenster): Bett-Anteil im Nether 14% -> 23-26%, Anchor-Anteil in der Oberwelt 15% -> 26%. Das ist ein bewusster Tausch, kein Gratis-Gewinn: eine Bett-/Anchor-Zuendung dauert laenger als ein Crystal-Zyklus, die reine Explosionszahl pro Sekunde sinkt dabei also leicht. Hoeher stellen, wenn die Betten/Anchors trotzdem liegen bleiben - bei min-support-delay 1 ist der Crystal-Zyklus so schnell, dass 1.5 HP Bonus im Nether kaum noch durchschlaegt.")
        .defaultValue(1.5)
        .range(0.0, 5.0)
        .sliderRange(0.0, 5.0)
        .build()
    );

    public final Setting<Boolean> aggressive = sgCombat.add(new BoolSetting.Builder()
        .name("aggressive")
        .description("Aggressiv-Profil: halbiert alle kuenstlichen Wartezeiten (Anchor-/Bett-Platzierung und -Wartung, D-Tap, Perlwuerfe) statt sie wie no-delay komplett auf 0 zu setzen - jede Platzierung behaelt so mindestens einen Tick fuer die Server-Bestaetigung. Wirkt vor allem auf die verzoegerungs-gebremsten Ressourcen: live A/B in der Oberwelt hob es den Anchor-Anteil von 14.8% auf 26.4%, bei leicht niedrigerer Gesamt-Explosionszahl (4.74 -> 4.28 pro Sekunde), weil ein Anchor-Zyklus laenger dauert als ein Crystal-Zyklus. Fuer maximale reine Schlagzahl stattdessen min-support-delay senken - das ist der Rate-Hebel, dieser hier ist der Mix-Hebel. Bewusst OHNE Eingriff in die Aura-Umschalt-Traegheit: kuerzere Traegheit wurde getestet und senkte die Explosionen pro 90s von 438 auf 377, weil jeder Moduswechsel CrystalAura neu startet und ein angefangenes Platzierungsfenster wegwirft.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> pistonAura = sgCombat.add(new BoolSetting.Builder()
        .name("piston-aura")
        .description("Piston-PvP als ANGRIFF: sitzt der Gegner so in einem Loch/einer Obsidian-Deckung, dass ueber mehrere Sekunden KEIN Crystal-, Anchor- oder Bett-Platz mehr Schaden bringt, wird ein Crystal daneben gesetzt und mit Kolben + Redstone-Block in sein Loch geschoben. Genau die Technik, die auf Anarchy-Servern 'Piston Aura' heisst - sie bringt eine Explosion an eine Stelle, an der man selbst nichts platzieren darf. Braucht Kolben UND Redstone-Bloecke in der Hotbar (zwei zusaetzliche Slots) - ohne diese Items passiert schlicht nichts.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> throughWalls = sgCombat.add(new BoolSetting.Builder()
        .name("through-walls")
        .description("Greift auch durch Waende/Deckung an: die eigenen Sichtlinien-Sperren fuer Crystal-, Anchor- und Bett-Plaetze entfallen, ebenso die Sichtpruefung vor dem Nahkampf. Vanilla prueft serverseitig NUR die Distanz (ca. 3 Bloecke fuer Nahkampf, 4.5 fuer Platzieren), keine Sichtlinie - ein Schlag oder eine Platzierung hinter einer duennen Wand geht also wirklich durch. Zusaetzlich werden Meteors CrystalAura und KillAura auf volle Reichweite-durch-Waende gestellt (walls-range = range), sonst bremst deren eigene, niedrigere Wanddistanz alles wieder aus. Wovon das bewusst NICHT gilt: Enderperlen - die fliegen physikalisch gegen die Wand, dort bleibt die Sichtpruefung aktiv.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> preHit = sgCombat.add(new BoolSetting.Builder()
        .name("pre-hit")
        .description("Schlaegt den Gegner vor der Explosion fuer mehr Schaden. Off by default - Vanillas Angriffs-Cooldown (~0.5-0.6s je nach Waffe) ist gegen ein Anchor/Crystal-Sperrfeuer reine Zeitverschwendung; ohne diesen Extra-Hit koennen Anchor und Crystal so schnell hintereinander gezuendet werden, wie der Server sie verarbeitet.")
        .defaultValue(false)
        .build()
    );

    public final Setting<Boolean> meleeFallback = sgCombat.add(new BoolSetting.Builder()
        .name("melee-fallback")
        .description("Schlaegt normal im Nahkampf, wenn gerade keine Explosion bevorsteht (z.B. kein Obsidian mehr fuer Crystal-Unterbau, oder gerade kein gueltiger Anchor-/Crystal-Kandidat in Reichweite). Bleibt an - ohne das steht der Bot direkt neben einem treffbaren Ziel einfach nur da, sobald Anchor/Crystal kurzzeitig nichts zustande bringen (z.B. durch Krater/unebenes Gelaende von vorherigen Explosionen). Nur `pre-hit` (redundanter Schlag WAEHREND eine Explosion schon so gut wie sicher kommt) ist aus - das kostet wirklich nur Zeit.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> preferAxeMelee = sgCombat.add(new BoolSetting.Builder()
        .name("prefer-axe-melee")
        .description("Schlaegt automatisch mit der Axt (Axt-Swap-Meta) statt Schwert.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> shieldBreaker = sgCombat.add(new BoolSetting.Builder()
        .name("shield-breaker")
        .description("Wechselt zur Axt gegen blockende Gegner.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> meleeStrafe = sgCombat.add(new BoolSetting.Builder()
        .name("melee-strafe")
        .description("Dreht sich im Nahkampf zum Ziel und kreis-strafet - schwerer zu treffen, variiert den Explosionswinkel.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> sprintReset = sgCombat.add(new BoolSetting.Builder()
        .name("sprint-reset")
        .description("W-Tap: setzt den Sprint vor jedem Nahkampf-Treffer kurz zurueck (aus-ein), damit jeder Schlag den Sprint-Knockback-Bonus (mehr Aufwaertsschub) bekommt, statt nur der erste einer Sprint-Sequenz.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> trackTarget = sgCombat.add(new BoolSetting.Builder()
        .name("track-target")
        .description("Schaut das Ziel ausserhalb der Nahkampf-Strafe-Distanz kontinuierlich an (vorhergesagte Position), statt nur waehrend einer einzelnen Anziel-Aktion.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> critJump = sgCombat.add(new BoolSetting.Builder()
        .name("crit-jump")
        .description("Springt kurz vor dem Schlag hoch, damit beim Treffen gefallen wird (+50% Schaden) - wie es echte Top-Spieler tun.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> dtap = sgCombat.add(new BoolSetting.Builder()
        .name("d-tap")
        .description("D-Tap: nach einem spuerbaren Knockback-Treffer Obsidian in die vorhergesagte Flugbahn setzen, zwei Crystals im Abstand der Trefferimmunitaet (~0.5s) zuenden - fuer einen schnellen Doppel-Totem-Pop.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> useMace = sgCombat.add(new BoolSetting.Builder()
        .name("use-mace")
        .description("Nutzt die Mace statt Axt/Schwert fuer Finishing-Hits, wenn gerade gefallen wird (Smash-Attack-Bonus).")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> elytraCombat = sgCombat.add(new BoolSetting.Builder()
        .name("elytra-combat")
        .description("Aktiviert Flugkampf-Logik (Feuerwerk-Boost bei zu geringer Geschwindigkeit), wenn eine Elytra genutzt wird.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> zeroDelay = sgCombat.add(new BoolSetting.Builder()
        .name("zero-delay")
        .description("Setzt CrystalAuras Platzierungs- und Break-Delays auf 0 und aktiviert Fast-Break. Der Angriff erfolgt, sobald der Server die reale Entity-ID des neu gespawnten Crystals liefert; eine vorab geratene Server-ID waere nicht adressierbar.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> ghostBlockMitigation = sgCombat.add(new BoolSetting.Builder()
        .name("ghost-block-mitigation")
        .description("Verfolgt eigene Blockplatzierungen bis zum Server-Blockupdate. Bleibt die lokale Vorhersage nach zweimaligem Roundtrip sichtbar, wird nur die falsche Client-Hitbox entfernt und Baritones Weltcache neu geladen; es wird kein erfundenes Desync-Paket gesendet.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Integer> minSupportDelay = sgCombat.add(new IntSetting.Builder()
        .name("min-support-delay")
        .description("Mindest-Tickabstand zwischen Obsidian-Unterbau und dem folgenden Crystal-Platzieren (CrystalAuras 'support-delay'). Beide Aktionen nutzen Minecrafts eigenes sequenznummer-basiertes Block-Vorhersage-System (seit 1.19) - schickt man beide zu dicht hintereinander raus, bevor die erste Sequenz vom Server bestaetigt ist, kann die Vorhersage durcheinanderkommen ('Crystal-Hitbox erscheint, aber kein Crystal kommt'). Steht auf Meteors eigenem Standard (1), weil dieser Wert der schaerfste Aggressions-Hebel ueberhaupt ist: live A/B ueber je drei 90s-Fenster hat 4 -> 1 die Explosionen pro Sekunde im Nether von 3.3-4.3 auf 5.3 und in der Oberwelt von 3.2 auf 4.3 gehoben (+33 bis +60%), bei ~+31% ausgeteiltem Schaden. Auf Servern mit spuerbarer Latenz oder Versions-Uebersetzung (z.B. ViaVersion) bei Fehlplatzierungen wieder hochdrehen - der Wert wird nur angehoben, nie unter diesen Mindestwert gesenkt.")
        .defaultValue(1)
        .range(0, 10)
        .sliderRange(0, 10)
        .build()
    );

    public final Setting<Boolean> instantMode = sgCombat.add(new BoolSetting.Builder()
        .name("no-delay")
        .description("Sofort-Modus: hebelt alle verbleibenden kuenstlichen Wartezeiten aus (Anchor/Bett-Platzierungs- und Wartungspausen, D-Tap-Cooldown, alle Perlwurf-Cooldowns). Reine Geschwindigkeit statt Vorsicht - kann Perlen/Anchors/Betten verschwenden, wenn Aktionen schneller abgefeuert werden als der Server sie verarbeitet. Zwei Ausnahmen BLEIBEN aktiv, weil sie keine Vorsicht sondern technische Notwendigkeit sind: die Aura-Umschalt-Traegheit (ohne sie faengt sich CrystalAura nie ein stabiles Fenster zum tatsaechlichen Platzieren, wurde beim Testen zu 0 Schaden in JEDER Form) und min-support-delay (ohne die Untergrenze schlaegt die Server-Sequenznummer-Vorhersage fehl, Crystal kommt nie an - selbes Symptom).")
        .defaultValue(false)
        .build()
    );

    public final Setting<Boolean> killAuraOn = sgCombat.add(new BoolSetting.Builder()
        .name("kill-aura")
        .description("Zusaetzlich KillAura fuer Nahkampf. Mob-Filter wird automatisch aus 'Mobs' uebernommen. Standard aus - eigener Axt-Nahkampf aktiv.")
        .defaultValue(false)
        .build()
    );

    public final Setting<Boolean> escapePearl = sgCombat.add(new BoolSetting.Builder()
        .name("escape-pearl")
        .description("Perlen-Flucht bei niedrigem HP und nahem Gegner.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> knockbackPearl = sgCombat.add(new BoolSetting.Builder()
        .name("knockback-pearl")
        .description("Wenn der Bot durch Knockback (Schlag/Explosion) in die Luft geschleudert wird ODER generell gerade in einem gefaehrlichen Fall steckt (z.B. von einer Kante), sofort senkrecht nach unten perlen - kommt kontrolliert runter statt Fallschaden zu nehmen oder als Ziel in der Luft zu haengen.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> antiAnchorDisengage = sgCombat.add(new BoolSetting.Builder()
        .name("anti-anchor-disengage")
        .description("Erkennt mindestens drei schadenswirksame Anchor-Explosionen innerhalb einer Sekunde. Steckt der Bot dabei in einem offenen 1x1-Loch, wirft er eine fast senkrechte Perle nach oben, um aus dem Blast-Radius zu kommen.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> explosionFloorSnap = sgCombat.add(new BoolSetting.Builder()
        .name("explosion-floor-snap")
        .description("Bei einer Explosion mit mehr als 0.45 vertikalem Delta wirft die Perle mit 88.5 Grad fast senkrecht vor die eigenen Fuesse, um schnell wieder Boden fuer Platzierungen zu erreichen.")
        .defaultValue(true)
        .build()
    );

    // Defense
    public final Setting<Boolean> fastTotem = sgDefense.add(new BoolSetting.Builder()
        .name("fast-totem")
        .description("Sofort-Totem-Manager: prueft die Offhand jeden Tick.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> turtleMasterDefense = sgDefense.add(new BoolSetting.Builder()
        .name("turtle-master-defense")
        .description("Unter der Health-Schwelle eine bereits mit Turtle-Master-Pfeilen geladene Armbrust in die Offhand nehmen und die Pfeile im selben Tick direkt vor die eigenen Fuesse schiessen. Die Offhand bleibt whilend Turtle-Master aktiv; Totem-Manager und Combat-Slot werden fuer diesen Schuss exklusiv pausiert.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Double> turtleMasterHealth = sgDefense.add(new DoubleSetting.Builder()
        .name("turtle-master-health")
        .description("Anteil der maximalen Health, unter dem Turtle-Master aktiv wird.")
        .defaultValue(0.4)
        .range(0.1, 0.8)
        .sliderRange(0.1, 0.8)
        .build()
    );

    public final Setting<Integer> turtleMasterCooldown = sgDefense.add(new IntSetting.Builder()
        .name("turtle-master-cooldown")
        .description("Ticks zwischen zwei Turtle-Master-Fussschuessen. Ein Schuss gewaehrt normalerweise 5 Sekunden Resistenz.")
        .defaultValue(10)
        .range(1, 40)
        .sliderRange(1, 20)
        .build()
    );

    public final Setting<Boolean> autoMendOn = sgDefense.add(new BoolSetting.Builder()
        .name("auto-mend")
        .description("Ruestung mit XP heilen.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> autoEatOn = sgDefense.add(new BoolSetting.Builder()
        .name("auto-eat")
        .description("Isst automatisch (Meteors AutoEat), wenn der Hunger niedrig ist - ohne genug Saettigung setzt Minecraft selbst das Sprinten aus, das bricht Sprint-Reset-Knockback und Baritones Lauftempo.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> noFallOn = sgDefense.add(new BoolSetting.Builder()
        .name("no-fall")
        .description("Verhindert Fallschaden (Meteors NoFall) - noetig, weil Baritone hier bewusst auf aggressive Sprung-/Klippen-Verfolgung getrimmt ist (bis zu 20 Bloecke Fallhoehe ohne Wasser).")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> autoShield = sgDefense.add(new BoolSetting.Builder()
        .name("auto-shield")
        .description("Blockt kurz mit dem Schild, wenn frisch ein feindlicher Crystal in der Naehe erscheint - reduziert den Explosionsschaden.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> antiRubberband = sgDefense.add(new BoolSetting.Builder()
        .name("anti-rubberband")
        .description("Erkennt Server-Positionskorrekturen (Rubberband) und verwirft den alten Pfad, statt dagegen anzukaempfen.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> respectFriends = sgDefense.add(new BoolSetting.Builder()
        .name("respect-friends")
        .description("Vermeidet Explosionen, die einen befreundeten Spieler (Friends-Liste) mittreffen wuerden.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> holeAwareness = sgDefense.add(new BoolSetting.Builder()
        .name("hole-awareness")
        .description("Sucht im Nahgefecht ein nahes 1-tiefes, oben offenes Loch und nutzt es als Kampfposition statt frei zu stehen.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> heightAdvantage = sgDefense.add(new BoolSetting.Builder()
        .name("height-advantage")
        .description("Bevorzugt eine Position niedriger als der Gegner - eigene Explosionen treffen dadurch mehr, gegnerische weniger.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> avoidLava = sgDefense.add(new BoolSetting.Builder()
        .name("avoid-lava")
        .description("Platziert keine Crystals/Anchors direkt neben Lava (Nether-Seen, Bedrock-Pools) - verhindert Selbstentzuendung und riesige Lava-Fluten nach der Explosion.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> autoFireRes = sgDefense.add(new BoolSetting.Builder()
        .name("auto-fire-res")
        .description("Trinkt automatisch einen Fire-Resistance-Trank, sobald man sich im Nether befindet und keiner aktiv ist - macht Lava/Feuer/brennenden Explosionsschaden irrelevant.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> buildCover = sgDefense.add(new BoolSetting.Builder()
        .name("build-cover")
        .description("Baut eigene Obsidian-Deckung (offene Seiten schliessen), wenn kein natuerliches Loch in der Naehe ist.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> surroundOn = sgDefense.add(new BoolSetting.Builder()
        .name("surround")
        .description("Setzt im Nahkampf Obsidian um die eigenen Fuesse (Surround, Standard-Verteidigung im Crystal-PvP): der Gegner kann dann keinen Crystal mehr direkt an die Fuesse setzen, wo er den meisten Schaden macht. BEWUSST bleibt die Seite ZUM Gegner offen - ein vollstaendiger Ring mauert den Bot ein, Baritones Verfolgung laeuft dann gegen die eigene Wand (genau dieser Selbsteinmauerungs-Bug ist bei build-cover schon einmal aufgetreten). Braucht Obsidian im Inventar.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> instaCity = sgDefense.add(new BoolSetting.Builder()
        .name("insta-city")
        .description("Bricht erreichbares gegnerisches Obsidian-Surround mit der Hotbar-Spitzhacke und setzt in die bestaetigte Luecke einen Crystal. In Survival berechnet Vanilla den Abbaufortschritt mit dem jeweils gehaltenen Werkzeug; ein legaler Client-Trick,_obsidian erst ohne Werkzeug anzufangen und exakt im letzten Tick mit der Pickaxe fertigzustellen, existiert nicht.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> antiBed = sgDefense.add(new BoolSetting.Builder()
        .name("anti-bed")
        .description("Baut ein FREMDES Bett in Explosionsreichweite ab, statt zu warten bis der Gegner es zuendet. Ein Bett explodiert im Nether/End mit Staerke 5 (staerker als TNT) und toetet laut 2b2t-Wiki auch in voller Prot-4-Ruestung - das ist die Standard-Todesursache im Nether-PvP. Ein Bett hat Haerte 0.2, geht also praktisch sofort kaputt. Nur Betten, die der Bot NICHT selbst gelegt hat, und nur wenn die Explosion an dieser Stelle mehr Eigenschaden machen wuerde als bed-max-self-damage erlaubt (sonst wird es lieber selbst gezuendet).")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> antiPiston = sgDefense.add(new BoolSetting.Builder()
        .name("anti-piston")
        .description("Bricht Piston-PvP: Gegner setzen einen Kolben plus Redstone-Block neben ein Loch/eine Deckung und schieben damit einen bereits platzierten Crystal auf dich zu - so kommt eine Explosion an eine Stelle, an der man selbst gar nicht platzieren koennte. Erkennt fremde Kolben (auch Sticky) im Nahbereich und bricht sie ab (Haerte 0.5), bevor sie ausfahren; ein danebenliegender Redstone-Block wird ebenfalls entfernt, weil er die Stromquelle ist.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> secureFooting = sgDefense.add(new BoolSetting.Builder()
        .name("secure-footing")
        .description("Setzt Obsidian unter die eigenen Fuesse, sobald dort Luft, Lava oder Feuer ist. Im Nether ist das die haeufigste Todesursache neben Betten: Netherrack hat Sprengwiderstand 0.4, nach zwei Explosionen ist der Boden weg, darunter oft Lava - und ein Wassereimer verdampft dort, es gibt also keinen MLG-Ausweg.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> peekTactic = sgDefense.add(new BoolSetting.Builder()
        .name("peek-tactic")
        .description("Duckt sich in Deckung, wenn gerade nichts aktiv passiert - steht nur kurz zum Angriff auf.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Integer> watchdogTicks = sgDefense.add(new IntSetting.Builder()
        .name("watchdog-ticks")
        .description("Nach so vielen Ticks ohne Eigenbewegung UND ohne Ziel-Schaden gilt der Bot als haengengeblieben (kompletter Reset). War fix auf 100 (5s) - gegen einen mobilen Gegner, der laengst wieder weg ist, viel zu lang.")
        .defaultValue(30)
        .range(10, 100)
        .sliderRange(10, 60)
        .build()
    );

    public final Setting<Boolean> retreatThreshold = sgDefense.add(new BoolSetting.Builder()
        .name("retreat-threshold")
        .description("Bricht das Gefecht ab (Rueckzug), wenn Totems <2 UND keine Crystal/Anchor-Ressourcen mehr da sind.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> retreatOnLosingTrade = sgDefense.add(new BoolSetting.Builder()
        .name("retreat-on-losing-trade")
        .description("Perlt weg, wenn man selbst gerade hart getroffen (gepoppt) wurde, die eigenen Crystal/Anchor-Explosionen den Gegner dabei aber nicht treffen - erkennt einen verlorenen Trade statt sinnlos weiterzumachen.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> multiTargetAlarm = sgDefense.add(new BoolSetting.Builder()
        .name("multi-target-alarm")
        .description("Warnt und wird kurz vorsichtiger, wenn waehrend des Kampfes ein zweiter Spieler in der Naehe auftaucht.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Integer> trapMode = sgDefense.add(new IntSetting.Builder()
        .name("trap-mode")
        .description("Cobweb an den Fuessen des Gegners, bremst ihn: 0 = aus, 1 = wenn Gegner nah (<=6 Bloecke), 2 = immer.")
        .defaultValue(1)
        .range(0, 2)
        .sliderRange(0, 2)
        .build()
    );

    public final Setting<Boolean> antiEscapeTrap = sgDefense.add(new BoolSetting.Builder()
        .name("anti-escape-trap")
        .description("Erkennt ein 1x1-Loch bis drei Bloecke in der aktuellen Laufrichtung des Gegners und fuellt es bevorzugt mit Cobweb, sonst Obsidian. Cobweb stoppt die Bewegung, laesst Crystal-/Anchorsplash aber durch.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Double> maxSelfDamage = sgDefense.add(new DoubleSetting.Builder()
        .name("max-self-damage")
        .description("Maximaler Eigenschaden pro Angriffsplatz.")
        .defaultValue(12.0)
        .range(2.0, 14.0)
        .sliderRange(2.0, 12.0)
        .build()
    );

    // Inventar
    public final Setting<Boolean> invManager = sgInv.add(new BoolSetting.Builder()
        .name("inv-manager")
        .description("Legt knapp gewordene Combat-Items (Crystals, Anchors, Glowstone, Perlen, Obsidian, Web) aus dem Hauptinventar in die Hotbar nach.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Integer> minCrystals = sgInv.add(new IntSetting.Builder()
        .name("min-crystals")
        .description("Nachschub-Schwelle Crystals.")
        .defaultValue(32)
        .range(8, 64)
        .sliderRange(8, 64)
        .build()
    );

    public final Setting<Integer> minAnchors = sgInv.add(new IntSetting.Builder()
        .name("min-anchors")
        .description("Nachschub-Schwelle Respawn Anchors.")
        .defaultValue(4)
        .range(1, 16)
        .sliderRange(1, 16)
        .build()
    );

    public final Setting<Integer> minGlowstone = sgInv.add(new IntSetting.Builder()
        .name("min-glowstone")
        .description("Nachschub-Schwelle Glowstone (Anchor-Ladung).")
        .defaultValue(8)
        .range(1, 32)
        .sliderRange(1, 32)
        .build()
    );

    public final Setting<Integer> minPearls = sgInv.add(new IntSetting.Builder()
        .name("min-pearls")
        .description("Nachschub-Schwelle Enderperlen.")
        .defaultValue(8)
        .range(1, 32)
        .sliderRange(1, 32)
        .build()
    );

    public final Setting<Integer> minObsidian = sgInv.add(new IntSetting.Builder()
        .name("min-obsidian")
        .description("Nachschub-Schwelle Obsidian (D-Tap, Notdeckung).")
        .defaultValue(16)
        .range(4, 64)
        .sliderRange(4, 64)
        .build()
    );

    public final Setting<Integer> minWeb = sgInv.add(new IntSetting.Builder()
        .name("min-web")
        .description("Nachschub-Schwelle Cobweb (Falle).")
        .defaultValue(4)
        .range(1, 16)
        .sliderRange(1, 16)
        .build()
    );

    public final Setting<Integer> minBeds = sgInv.add(new IntSetting.Builder()
        .name("min-beds")
        .description("Nachschub-Schwelle Betten (Bed Aura). Funktioniert unabhaengig von der Stack-Groesse - auch bei serverseitig erweiterten 64er-Staples (z.B. 5b5t), da die Nachschub-Logik die tatsaechliche Maximal-Stapelgroesse des Items abfragt statt sie fest anzunehmen.")
        .defaultValue(4)
        .range(1, 16)
        .sliderRange(1, 16)
        .build()
    );

    public final Setting<Integer> minHealPotionsStock = sgInv.add(new IntSetting.Builder()
        .name("min-heal-potions")
        .description("Nachschub-Schwelle Splash-Heiltraenke. Funktioniert unabhaengig von der Stack-Groesse - auch bei serverseitig erweiterten 64er-Staples (z.B. 5b5t).")
        .defaultValue(8)
        .range(1, 32)
        .sliderRange(1, 32)
        .build()
    );

    // Mobs
    public final Setting<Boolean> attackMobs = sgMobs.add(new BoolSetting.Builder()
        .name("attack-mobs")
        .description("Greift Mobs an, wenn kein Spieler in Reichweite ist.")
        .defaultValue(false)
        .build()
    );

    public final Setting<java.util.Set<EntityType<?>>> mobTypes = sgMobs.add(new EntityTypeListSetting.Builder()
        .name("mob-types")
        .description("Diese Mob-Typen werden angegriffen (wird automatisch an KillAura und CrystalAura uebergeben).")
        .onlyAttackable()
        .build()
    );

    public final Setting<Integer> mobRange = sgMobs.add(new IntSetting.Builder()
        .name("mob-range")
        .description("Reichweite fuer Mob-Angriffe.")
        .defaultValue(10)
        .range(4, 32)
        .sliderRange(4, 24)
        .build()
    );

    // Enderperlen
    public final Setting<Boolean> pearlThrow = sgPearl.add(new BoolSetting.Builder()
        .name("pearl-gapclose")
        .description("Perlt zum Gegner, wenn er zu weit weg ist (mit Rotation aufs Ziel).")
        .defaultValue(true)
        .build()
    );

    public final Setting<Double> pearlMinDist = sgPearl.add(new DoubleSetting.Builder()
        .name("pearl-min-dist")
        .description("Ab dieser Distanz wird eine Perle geworfen - auf 4 gestellt heisst: sobald Nahkampf (3.6 Bloecke) nicht mehr reicht.")
        .defaultValue(4.0)
        .range(4.0, 40.0)
        .sliderRange(4.0, 30.0)
        .build()
    );

    // Heilung
    public final Setting<Boolean> healPotions = sgHeal.add(new BoolSetting.Builder()
        .name("heal-potions")
        .description("Wirft bei frischem Schaden sofort eine Splash-Heiltraenke (Instant Health) zu den eigenen Fuessen - explodiert direkt am Boden und heilt augenblicklich. Braucht Splash Potion of Healing/Strong Healing im Inventar (auch als 64er-Stack auf Servern mit erweiterten Stack-Groessen).")
        .defaultValue(true)
        .build()
    );

    public final Setting<Double> healMinDamage = sgHeal.add(new DoubleSetting.Builder()
        .name("heal-min-damage")
        .description("Mindestens so viel HP muessen seit dem letzten Tick verloren gegangen sein, damit ueberhaupt ein Trank geworfen wird - verhindert Trankverschwendung bei jedem winzigen Kratzer (z.B. Fallschaden, Dornen).")
        .defaultValue(3.0)
        .range(0.5, 10.0)
        .sliderRange(0.5, 10.0)
        .build()
    );

    public final Setting<Integer> healCooldown = sgHeal.add(new IntSetting.Builder()
        .name("heal-cooldown")
        .description("Mindestabstand (Ticks) zwischen zwei geworfenen Heiltraenken - verhindert, dass ein einzelner Mehrfach-Treffer-Combo sofort mehrere Traenke auf einmal verbraucht, ohne bei anhaltendem Druck (mehrere Pops kurz hintereinander) spuerbar zu blockieren.")
        .defaultValue(12)
        .range(0, 100)
        .sliderRange(0, 100)
        .build()
    );

    // State
    private final Random rng = new Random();
    private final Map<UUID, Float> lastHealth = new HashMap<>();
    private final Map<UUID, Integer> pops = new HashMap<>();
    private final Map<UUID, Vec3> lastPositions = new HashMap<>();
    private final Map<UUID, Vec3> velocities = new HashMap<>();
    private int tickCounter;
    private int auraMode = -1;
    /** Wie viele Einheiten jeder Ressource seit dem Einschalten tatsaechlich VERBRAUCHT wurden
     *  (Inventar-Delta, siehe trackResourceUsage) - Grundlage fuer balance-resources.
     *  Index 0 = Crystal, 1 = Anchor, 2 = Bett. */
    private final int[] auraUsage = new int[3];
    /** Letzter beobachteter Inventarstand der drei Ressourcen (-1 = noch nie gemessen) - Referenz fuer
     *  die Delta-Messung in trackResourceUsage(). */
    private final int[] lastResourceCount = { -1, -1, -1 };
    private int lastErrorWarnTick = -999;
    private boolean pendingFreeLook;
    private double pendingFreeLookYaw, pendingFreeLookPitch;
    private boolean warnedLowTotems;
    private boolean warnedOutOfCrystals;
    private boolean warnedOutOfAnchorSupply;
    private final Map<net.minecraft.world.item.Item, Boolean> warnedOutOfMisc = new HashMap<>();
    private int savedPlaceDelay = -1;
    private int savedBreakDelay = -1;
    private int savedTicksExisted = -1;
    private Boolean savedFastBreak;
    private record PendingBlockConfirmation(int tick, BlockState expected) {}
    private final Map<BlockPos, PendingBlockConfirmation> pendingBlockConfirmations = new HashMap<>();
    private int popBurstUntil;
    private int lastPearlTick = -999;
    private int lastSelfPopTick = -999;
    private int lastTargetDamageTick = -999;
    private final Map<UUID, Boolean> hadTotemEffects = new HashMap<>();
    private int sprintResetCooldown;
    private String currentAction = "-";
    private boolean blocking;
    private boolean blockingSwapBack;
    private int shieldUntil;
    private CrystalAura.SupportMode savedSupport;
    private int savedSupportDelay = -1;
    /** Gemerkte Original-Werte der walls-range-Einstellungen (0 = CrystalAura place, 1 = CrystalAura
     *  break, 2 = KillAura); -1 = nichts veraendert. Siehe syncWallsRange(). */
    private final double[] savedWallsRange = { -1, -1, -1 };
    private boolean supportSyncFailed;
    private int lastCrystalCount = -1;
    private int lastAnchorBlockCount = -1;
    private int lastBedBlockCount = -1;
    private int explosionRetreatUntil;
    private boolean followActive;
    private boolean engaged; // sticky: einmal in Engage-Distanz gekommen, bleibt es auch nach Explosions-Knockback ueber diese Distanz hinaus (bis follow-range/Zielverlust) - sonst reisst eine Crystal-Explosion die Verfolgung mitten im Kampf ab.
    private UUID engagedTargetId;
    private int lastRetargetCheck;
    private UUID followedId;
    private Vec3 lastSelfPos;
    private int obstacleStuckTicks;
    private float lastTargetHpForStuck = -1;
    private int watchdogStuckTicks;
    private int lastAnchorProgressTick;
    private Vec3 oscillationAnchorPos;
    private int oscillationAnchorTick;
    private int rubberbandCooldown;
    private boolean strafeLeft = true;
    private int nextStrafeSwitchTick = -1;
    private float lastSelfHpForRubberband = -1;
    private double lastSelfVelocityY = Double.NaN;
    private boolean selfLostHealthThisTick;
    private boolean selfTookRealDamageThisTick;
    private double selfVerticalDeltaThisTick;
    private Map<BlockPos, Integer> trackedAnchorCharges = new HashMap<>();
    private Map<BlockPos, Integer> scannedAnchorCharges = new HashMap<>();
    private int anchorExplosionWindowStart = -1;
    private int anchorExplosionWindowCount;
    private BlockPos activeHole;
    private BlockPos heightCalcOrigin;
    private int buildCoverCooldown;
    // Zaehlt, wie viele Rotation+Aktion-Paare (rotateAndRun) diesen Tick schon eingereiht wurden.
    // Meteors Rotations-Klasse unterstuetzt nativ MEHRERE ausgerichtete Aktionen pro Tick (siehe
    // Rotations.onSendMovementPacketsPost): der ERSTE Eintrag laeuft ueber den normalen Bewegungspaket-
    // Pfad (echte Rotation wird gesetzt), JEDER WEITERE bekommt bei clientSide=true ebenfalls kurzzeitig
    // die echte Rotation gesetzt - genau fuer die Dauer seines eigenen Callbacks, danach zurueckgesetzt.
    // Frueher war das komplett gesperrt (ein starres 1-Aktion-Mutex) - das verhinderte z.B., dass ein
    // Perlwurf UND eine Anchor-/Bett-Interaktion im selben Tick feuern konnten, obwohl beide fachlich
    // unabhaengig sind und das Framework das technisch bereits unterstuetzt (siehe rotateAndRun()).
    private int rotationsThisTick;
    private boolean combatSlotReserved;
    private int combatSlotPreviousSlot = -1;
    private int combatSlotTargetSlot = -1;

    // Prioritaeten fuer rotateAndRun(): bei einer echten Kollision (mehrere Aktionen wollen im selben
    // Tick den primaeren Bewegungspaket-Rotationspfad, Index 0) gewinnt die hoehere Prioritaet - siehe
    // Rotations.rotate(), das die Warteschlange danach sortiert. PRIORITY_LOOK ist bewusst die
    // niedrigste: die rein kosmetische Ziel-Verfolgung am Tick-Ende soll NIE eine echte Aktion
    // verdraengen, landet aber als letzter Eintrag zuverlaessig in Rotations' lastRotation-Haltefeld.
    private static final int PRIORITY_CRYSTAL = 100;
    private static final int PRIORITY_ANCHOR = 90;
    private static final int PRIORITY_BED = 80;
    private static final int PRIORITY_PEARL = 70;
    private static final int PRIORITY_MISC = 60;
    private static final int PRIORITY_LOOK = 0;
    private int lastFireworkTick = -999;
    private int secondEnemyWarnCooldown;
    private boolean lowOnTotems;
    private boolean turtleModeActive;
    private int turtleSwitchReadyTick;
    private int turtleShotReadyTick = -999;

    // Surround / Anti-Bett / Anti-Piston / Bodensicherung / Piston-Aura
    private int surroundCooldown;
    private int hostileBlockCooldown;
    private int footingCooldown;
    private BlockPos instaCityBlock;
    private UUID instaCityTargetId;
    private int instaCityCooldown;
    private boolean instaCityOwnsCombatSlot;
    private int instaCityBreakQueuedTick = -1;
    private boolean instaCityBreakConfirmed;
    private int antiEscapeCooldown;
    /** Betten, die der Bot SELBST gelegt hat - alles andere in Reichweite ist ein feindliches Bett und
     *  wird von anti-bed abgebaut statt abgewartet. Analog zu anchorsChargedByUs. */
    private final java.util.Set<BlockPos> bedsPlacedByUs = new java.util.HashSet<>();

    // Manuelles Feuer-Durchlaufen (siehe fireWalkAllowed/walkThroughFire): nur im Nahbereich, nur
    // solange es Boden gutmacht - sonst gehoert die Bewegung Baritone.
    /** Naeher als das darf der Bot dem Ziel nicht bleiben: ab hier ueberlappen die Hitboxen so weit,
     *  dass jede Explosivplatzierung den eigenen Eigenschaden-Deckel reisst (live: bester Crystal-Platz
     *  nur noch 0.1 HP Zielschaden). Er tritt dann aktiv zurueck, statt im Gegner stehenzubleiben. */
    private static final double MIN_ATTACK_DIST = 1.4;

    /** Ab diesem Zielschaden gilt eine Explosion als "kommt gleich und lohnt sich" (explosionImminent).
     *  Vorher genuegte JEDER Wert > 0 - ein 0.1-HP-Platz hat damit dauerhaft den Nahkampf-Fallback
     *  blockiert, obwohl faktisch nichts passierte. */
    private static final double MIN_MEANINGFUL_DAMAGE = 1.0;

    private static final double FIRE_WALK_MAX_DIST = 6.0;
    private static final int FIRE_WALK_PATIENCE = 20;      // 1s ohne Annaeherung reicht als Urteil
    private static final int FIRE_WALK_BLOCK_TICKS = 40;  // nach Stillstand 2s Baritone-Fallback
    private int fireWalkStartTick = -1;
    private int fireWalkBlockedUntil;
    private double fireWalkStartDist;
    /** Eigene Piston-Aura-Bauteile - damit anti-piston nicht die eigene Maschine wieder abreisst. */
    private final java.util.Set<BlockPos> pistonPartsByUs = new java.util.HashSet<>();
    /** Wie lange in Folge KEINE Explosivoption mehr Schaden am Ziel bringt (Gegner eingegraben/in
     *  Deckung) - Ausloeser fuer die Piston-Aura. */
    private int explosiveStarvedTicks;
    private int pistonStage;
    private int pistonStageTick;
    private BlockPos pistonCrystalCell, pistonBodyCell;
    private Direction pistonPushDir;
    private int pistonCooldown;

    // Anchor-Platzierung (neue Anchors an guten Stellen) + Anchor-Wartung (JEDER Anchor im Nahbereich,
    // egal von wem/wann platziert, wird geladen und gezuendet - laeuft unabhaengig vom Aura-Modus,
    // damit ein waehrend Anchor-Modus platzierter Anchor auch nach einem Wechsel zu Crystal fertig wird).
    private int anchorPlaceCooldown;
    private int anchorMaintCooldown;
    private final java.util.List<BlockPos> anchorCandidates = new java.util.ArrayList<>();
    private int anchorCandidateIndex;
    private double bestAnchorDmgCache;
    private double bestCrystalDmgCache;
    private boolean outOfGlowstone;
    private final java.util.Set<BlockPos> anchorsChargedByUs = new java.util.HashSet<>();
    private int anchorPlaceFails;
    private int crystalForcedUntil;
    private int anchorUnreachableTicks;
    private boolean drinkingFireRes;
    private int fireResStartTick = -999;
    private BlockPos anchorCalcOrigin;
    private int lastAuraSwitch;

    // Bed-Platzierung: analog zur Anchor-Platzierung, aber ohne Ladeschritt - eine Explosion pro Bett,
    // ausgeloest durch simples Rechtsklicken. Wirkt nur ausserhalb der Overworld (Nether/End).
    private int bedPlaceCooldown;
    private int bedMaintCooldown;
    private final java.util.List<BedSpot> bedCandidates = new java.util.ArrayList<>();
    private int bedCandidateIndex;
    private double bestBedDmgCache;
    /** Roher (NICHT totem-abgeschlagener) Bett-Schaden des aktuell besten Kandidaten. Die absolute
     *  bed-min-damage-Schwelle muss gegen diesen Wert pruefen, nicht gegen den totem-gewichteten:
     *  totemAdjustedDamage() ist eine reine VERGLEICHS-Gewichtung zwischen Optionen (siehe dort), kein
     *  Mass fuer "lohnt sich diese Explosion ueberhaupt". Gegen den 0.3-Abschlag geprueft war die
     *  Schwelle gegen JEDEN totem-tragenden Gegner (also praktisch jeden ernsthaften PvP-Gegner)
     *  faktisch unerreichbar und legte Bed Aura fast komplett still (live gemessen: 2-3 Betten/Minute
     *  statt durchgehender Platzierung, waehrend Crystal im selben Setup 192/Minute schaffte). */
    private double bestBedRawDmgCache;
    private int bedPlaceFails;
    private int bedUnreachableTicks;
    private BlockPos bedCalcOrigin;
    private int lastBedProgressTick;

    // Heiltraenke: Schaden-Delta pro Tick verfolgen, um frischen Treffern sofort einen Splash-Heiltrank
    // entgegenzusetzen.
    private float hpAtHealWindowStart = -999;
    private int healWindowStartTick = -999;
    private int healPotionCooldown;
    private boolean healingUntilFull;

    private record BedSpot(BlockPos pos, Direction dir) {}

    // Eigener D-Tap-Executor (Knockback -> Obsidian in Flugbahn -> 2 Crystals im Immunitaets-Abstand)
    private BlockPos dtapSpot;
    private int dtapStage; // 0 idle, 1 1.Crystal platzieren, 2 1.Crystal zuenden, 3 Immunitaet abwarten, 4 2.Crystal platzieren+zuenden
    private int dtapStageTick;
    private int dtapCooldown;

    public GodmodePvP() {
        super(com.provipvp.ProviPvPAddon.CATEGORY, "godmode-pvp", "ProviPvP v4: Kampf-KI mit eigenem Blitz-Anchor (1 Glowstone), Verfolgung ohne Limit. Befehl: .pvp");
    }

    @Override
    public void onActivate() {
        tickCounter = 0;
        supportSyncFailed = false;
        auraMode = -1;
        java.util.Arrays.fill(auraUsage, 0);
        java.util.Arrays.fill(lastResourceCount, -1);
        lastErrorWarnTick = -999;
        savedPlaceDelay = -1;
        savedBreakDelay = -1;
        savedTicksExisted = -1;
        savedFastBreak = null;
        popBurstUntil = 0;
        lastPearlTick = -999;
        anchorPlaceCooldown = 0;
        anchorMaintCooldown = 0;
        anchorPlaceFails = 0;
        crystalForcedUntil = 0;
        anchorUnreachableTicks = 0;
        anchorCalcOrigin = null;
        bedPlaceCooldown = 0;
        bedMaintCooldown = 0;
        bedPlaceFails = 0;
        bedUnreachableTicks = 0;
        bedCalcOrigin = null;
        bedCandidates.clear();
        bedCandidateIndex = 0;
        lastBedProgressTick = 0;
        hpAtHealWindowStart = -999;
        healWindowStartTick = -999;
        healPotionCooldown = 0;
        fireWalkStartTick = -1;
        fireWalkBlockedUntil = 0;
        fireWalkStartDist = 0;
        healingUntilFull = false;
        drinkingFireRes = false;
        fireResStartTick = -999;
        dtapStage = 0;
        dtapCooldown = 0;
        surroundCooldown = 0;
        hostileBlockCooldown = 0;
        footingCooldown = 0;
        instaCityBlock = null;
        instaCityTargetId = null;
        instaCityCooldown = 0;
        instaCityOwnsCombatSlot = false;
        instaCityBreakQueuedTick = -1;
        instaCityBreakConfirmed = false;
        antiEscapeCooldown = 0;
        bedsPlacedByUs.clear();
        pistonPartsByUs.clear();
        explosiveStarvedTicks = 0;
        pistonStage = 0;
        pistonStageTick = 0;
        pistonCrystalCell = null;
        pistonBodyCell = null;
        pistonPushDir = null;
        pistonCooldown = 0;
        dtapSpot = null;
        lastAuraSwitch = -999;
        lastHealth.clear();
        pops.clear();
        lastPositions.clear();
        velocities.clear();
        blocking = false;
        blockingSwapBack = false;
        combatSlotReserved = false;
        combatSlotPreviousSlot = -1;
        combatSlotTargetSlot = -1;
        shieldUntil = 0;
        lastCrystalCount = -1;
        lastAnchorBlockCount = -1;
        lastBedBlockCount = -1;
        explosionRetreatUntil = 0;
        followActive = false;
        followedId = null;
        engaged = false;
        engagedTargetId = null;
        lastRetargetCheck = 0;
        lastSelfPos = null;
        obstacleStuckTicks = 0;
        lastTargetHpForStuck = -1;
        watchdogStuckTicks = 0;
        lastAnchorProgressTick = 0;
        oscillationAnchorPos = null;
        oscillationAnchorTick = 0;
        rubberbandCooldown = 0;
        lastSelfHpForRubberband = -1;
        lastSelfVelocityY = Double.NaN;
        trackedAnchorCharges.clear();
        scannedAnchorCharges.clear();
        anchorExplosionWindowStart = -1;
        anchorExplosionWindowCount = 0;
        selfLostHealthThisTick = false;
        selfTookRealDamageThisTick = false;
        selfVerticalDeltaThisTick = 0.0;
        activeHole = null;
        heightCalcOrigin = null;
        buildCoverCooldown = 0;
        lastFireworkTick = -999;
        secondEnemyWarnCooldown = 0;
        lowOnTotems = false;
        turtleModeActive = false;
        turtleSwitchReadyTick = 0;
        turtleShotReadyTick = -999;
        lastSelfPopTick = -999;
        lastTargetDamageTick = -999;
        hadTotemEffects.clear();
        sprintResetCooldown = 0;
        anchorsChargedByUs.clear();
        pendingBlockConfirmations.clear();

        Modules m = Modules.get();

        CrystalAura ca = m.get(CrystalAura.class);
        if (ca != null) {
            syncZeroDelaySettings(ca);
            syncSupport(ca);
        } else {
            // CrystalAura nicht geladen/verfuegbar - denselben Fallback-Pfad wie ein fehlgeschlagener
            // Reflection-Zugriff in syncSupport() nehmen: Obsidian-Unterbau ueber freier Luft kann so
            // nicht garantiert werden, also Anchor aktiv bevorzugen statt sich stillschweigend auf einen
            // Crystal-Modus zu verlassen, der in dieser Session gar nicht existiert.
            supportSyncFailed = true;
        }

        // Baritone: aggressive Verfolgung - Klippen runter, Luecken/Gaps ueberspringen, notfalls mit Bruecken-Sprung
        var bs = BaritoneAPI.getSettings();
        bs.allowDownward.value = true;
        bs.allowParkour.value = true;
        bs.allowParkourAscend.value = true;
        bs.allowParkourPlace.value = true;
        // Baritones eigene Bruecken-/Pillar-Logik (parkour-place) darf zwar bauen, greift aber ins
        // Leere: die Default-Liste "erlaubter Wegwerf-Bloecke" ist [Dirt, Cobblestone, Netherrack,
        // Stone] - nichts davon fuehren wir mit. Obsidian ist der einzige echte Ueberschuss-Block im
        // Inventar (D-Tap/Deckung/Anchor-Unterbau) - ohne diesen Eintrag war allowParkourPlace die ganze
        // Zeit ein reines No-Op, weil Baritone schlicht nichts hatte, das es platzieren durfte.
        bs.acceptableThrowawayItems.value = new java.util.ArrayList<>(java.util.List.of(Items.OBSIDIAN));
        bs.sprintAscends.value = true;
        bs.allowSprint.value = true;
        // Diagonal ab-/aufsteigen: kuerzere, direktere Pfade (kein Umweg ueber zwei Kardinalschritte) -
        // konsequent im selben "Risiko in Kauf nehmen"-Profil wie Parkour/Parkour-Place oben.
        bs.allowDiagonalDescend.value = true;
        bs.allowDiagonalAscend.value = true;
        bs.jumpPenalty.value = 0.6;
        bs.maxFallHeightNoWater.value = 20; // genug fuer normales Gelaende ohne Baritone bei jedem Abhang blockieren zu lassen; entstehender Fallschaden wird ueber escape-pearl statt eines eigenen Fallschaden-Hacks abgefangen
        bs.followRadius.value = 3; // Baritone haelt/regelt selbst diesen Abstand - kontinuierlich statt hart cancel+neu
        // Zuegig, aber nicht so aggressiv, dass ein laufender Pfad (z.B. Sprung ueber einen Block)
        // vor Fertigstellung ständig verworfen und neu berechnet wird - das war die Ursache fuer
        // das Hin-und-her-Pendeln und haengenbleibende Bewegung an einfachen Hindernissen.
        bs.primaryTimeoutMS.value = 450L;
        bs.failureTimeoutMS.value = 1200L;
        bs.planAheadPrimaryTimeoutMS.value = 1200L;
        bs.planAheadFailureTimeoutMS.value = 2500L;
        // Ein Kampf-Bot soll NIE mitten im Gefecht in ein zufaellig auf dem Pfad liegendes Nether-Portal
        // spazieren, nur weil Baritones Pfadsuche es als kuerzeste Route ansieht - das wuerde die
        // Verfolgung/den Kampf komplett abbrechen und den Bot in eine andere Dimension verfrachten, weit
        // ausserhalb jeder engage-distance/follow-range-Kontrolle dieses Moduls. enterPortal=false verhindert
        // nur das ABSICHTLICHE Ziel "geh in dieses Portal" - ein Portal, das zufaellig auf dem Weg zu einem
        // ANDEREN Ziel liegt, wuerde trotzdem einfach durchquert. blocksToAvoid zwingt Baritone, aktiv
        // drumherum zu routen statt nur nicht direkt hineinzulaufen.
        bs.enterPortal.value = false;
        bs.blocksToAvoid.value = new java.util.ArrayList<>(java.util.List.of(net.minecraft.world.level.block.Blocks.NETHER_PORTAL));

        if (autoMendOn.get()) {
            safeEnable(m, AutoMend.class);
            tuneAutoMend();
        }
        if (autoEatOn.get()) safeEnable(m, AutoEat.class);
        if (noFallOn.get()) safeEnable(m, NoFall.class);
        if (killAuraOn.get()) safeEnable(m, KillAura.class);
        syncMobFilter();
        syncWallsRange();

        MeteorClient.EVENT_BUS.subscribe(this);

        info("ProviPvP v4 aktiv. Rechtsklick auf das Modul im Meteor-Menue zum Keybind. Befehl: .pvp");
    }

    @Override
    public void onDeactivate() {
        MeteorClient.EVENT_BUS.unsubscribe(this);
        releaseCombatSlot();
        releaseInstaCityTool();
        turtleModeActive = false;

        Modules m = Modules.get();
        safeDisable(m, CrystalAura.class);
        safeDisable(m, AutoMend.class);
        safeDisable(m, AutoEat.class);
        safeDisable(m, NoFall.class);
        safeDisable(m, KillAura.class);

        CrystalAura ca = m.get(CrystalAura.class);
        if (ca != null) {
            restoreZeroDelaySettings(ca);
            restoreSupport(ca);
        }
        pendingBlockConfirmations.clear();
        restoreWallsRange();
        dtapStage = 0;
        cancelFollow();

        if (blocking) {
            releaseCombatSlot();
            blocking = false;
            blockingSwapBack = false;
        }

        Input.setKeyState(mc.options.keyLeft, false);
        Input.setKeyState(mc.options.keyRight, false);
        mc.player.setShiftKeyDown(false);
        Input.setKeyState(mc.options.keyJump, false);
        Input.setKeyState(mc.options.keySprint, false);
        Input.setKeyState(mc.options.keyUp, false);
        mc.player.setSprinting(false);

        lastHealth.clear();
        pops.clear();
        warnedLowTotems = false;
        warnedOutOfCrystals = false;
        warnedOutOfAnchorSupply = false;
        warnedOutOfMisc.clear();
        anchorsChargedByUs.clear();

        info("ProviPvP aus.");
    }

    @EventHandler
    public void onTick(TickEvent.Pre event) {
        if (!Utils.canUpdate()) return;

        tickCounter++;

        // Glowstone-Check jede Tick (sofortige Reaktivierung)
        if (outOfGlowstone) {
            FindItemResult gs = InvUtils.find(Items.GLOWSTONE);
            if (gs.found()) {
                outOfGlowstone = false;
                anchorCandidates.clear();
                anchorCandidateIndex = 0;
            }
        }
        syncMobFilter(); // rein clientseitiger Reflection-Sync (keine Serverpakete) - so schnell wie moeglich statt gedrosselt

        try {
            doTick();
        } catch (Exception e) {
            // Zeitbasiert statt fuer-immer-still: ein dauerhafter Fehler bleibt sichtbar, spammt aber nicht.
            if (tickCounter - lastErrorWarnTick > 100) {
                lastErrorWarnTick = tickCounter;
                warning("Interner Fehler: %s", e.toString());
            }
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (event.packet instanceof ClientboundBlockUpdatePacket packet) {
            pendingBlockConfirmations.remove(packet.getPos());
            if (instaCityBlock != null && packet.getPos().equals(instaCityBlock)) {
                instaCityBreakConfirmed = packet.getBlockState().isAir();
            }
        }
    }

    @EventHandler
    private void onEntityAdded(EntityAddedEvent event) {
        if (!(event.entity instanceof EndCrystal crystal) || mc.player == null) return;
        Vec3 position = crystal.position();
        double selfDamage = DamageUtils.crystalDamage(mc.player, position);
        if (selfDamage > maxSelfDamage.get() || !selfDamageAllowed(position, selfDamage)) {
            attackCrystal(crystal);
        }
    }

    /** Server-Wechsel/Disconnect: nichts (CrystalAura, KillAura, ...) darf ueber die Weltgrenze hinaus
     *  aktiv bleiben - sonst laufen fremde Meteor-Module beim naechsten Join in einem undefinierten
     *  Zustand (z.B. mc.player kurzzeitig null) mit und koennen den Client abstuerzen lassen. */
    @EventHandler
    public void onGameLeft(GameLeftEvent event) {
        if (isActive()) toggle();
    }

    private void doTick() {
        Player self = mc.player;
        currentAction = "-";
        // walkThroughFire() setzt keyUp=true, ruft sich aber nicht mehr auf sobald kein Feuer mehr
        // blockiert - ohne diesen Reset bleibt "W" clientseitig fuer immer gedrueckt (auch Freecam
        // sieht diesen rohen Tastenzustand und laeuft dann von selbst vorwaerts).
        Input.setKeyState(mc.options.keyUp, false);
        Input.setKeyState(mc.options.keySprint, false);
        Input.setKeyState(mc.options.keyJump, false);
        rotationsThisTick = 0;
        pendingFreeLook = false;
        sampleCombatMotion(self);
        reconcilePendingBlockConfirmations();
        if (handleExplosionEscape(self)) return;
        // Verbrauchs-Delta jeden Tick messen (nicht nur im Kampf) - eine Explosion kann das Item auch
        // dann aus dem Inventar nehmen, wenn dieser Tick spaeter fruehzeitig abbricht (Schild-Block,
        // Rueckzug, kein Ziel).
        trackResourceUsage();
        boolean turtleAction = handleTurtleDefense(self);

        boolean guiOpen = mc.gui.screen() != null;
        if (!turtleAction) handleInventory(self, guiOpen);
        if (tickCounter % 40 == 0) pruneOwnedBlocks();
        if (tickCounter % 20 == 0) syncWallsRange();
        if (turtleAction) {
            LivingEntity turtleTarget = handleTargeting(self);
            if (turtleTarget == null) return;
            double turtleDist = Math.sqrt(self.distanceToSqr(turtleTarget));
            handleDefense(self, turtleTarget, turtleDist);
            return;
        }

        if (blocking) {
            if (tickCounter < shieldUntil) {
                mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                currentAction = "schild-block";
                return;
            }
            stopBlock();
        }

        LivingEntity target = handleTargeting(self);
        if (target == null) return; // currentAction wurde bereits auf "beobachten" gesetzt

        double dist = Math.sqrt(self.distanceToSqr(target));
        boolean flying = mc.player.isFallFlying();

        if (handleDefense(self, target, dist)) return;

        handleOffense(self, target, dist, flying, guiOpen);

        if (currentAction.equals("-")) {
            currentAction = auraMode == 0 ? "crystal" : "zielen";
        }
        updatePeekStance();

        trackPop(target);
        trackPop(self);
        trackTotemEffect(target);

        // Cosmetic Ziel-Verfolgung (free-look): laeuft am Tick-Ende, mit der niedrigsten Prioritaet -
        // ABER NUR, wenn diesen Tick noch KEINE echte Aktion (Perle, Nahkampf, Crystal/Anchor/Bett) die
        // Rotation schon beansprucht hat (rotationsThisTick > 0). Vorher lief dieser Tail-Flush IMMER,
        // unabhaengig davon - Rotations.rotate() haengt ihn dann als ZWEITE, separate
        // ServerboundMovePlayerPacket.Rot-Nachricht direkt HINTER die der echten Aktion (siehe
        // Rotations.onSendMovementPacketsPost: die Callback-Aktion feuert mit der ERSTEN/hoechst-
        // priorisierten Rotation, aber danach schickt die for-Schleife trotzdem noch eine zweite
        // Rotation fuer jeden weiteren Eintrag raus). Das war die Ursache der gemeldeten "Perlen landen
        // immer am Kopf des Gegners statt in der berechneten Flugbahn": das kontinuierliche Ziel-Tracking
        // (trackTarget, aktiv sobald dist > 3.6 - also in praktisch jedem Perlen-Gapclose-Fall) setzte
        // pendingFreeLook auf die direkte Blickrichtung zum Ziel, und dieser Tail-Flush schickte sie als
        // zusaetzliches Rotations-Paket direkt nach dem Perlwurf-Paket raus. Wenn diesen Tick schon eine
        // echte Aktion lief, ist der Tail-Flush ohnehin ueberfluessig (naechster Tick berechnet
        // pendingFreeLook frisch neu) - jetzt wird er dann komplett uebersprungen statt nur mit
        // niedrigerer Prioritaet trotzdem ein zweites Rotations-Paket zu senden.

        if (pendingFreeLook && rotationsThisTick == 0) {
            rotateAndRun(pendingFreeLookYaw, pendingFreeLookPitch, PRIORITY_LOOK, null);
        }
    }

    /** Totem/Heiltraenke/Feuerresistenz-Pflege und periodischer Inventar-Nachschub - unabhaengig
     *  vom aktuellen Ziel, laeuft jeden Tick zuerst. */
    private void handleInventory(Player self, boolean guiOpen) {
        // Nur eine ECHTE Fremd-Container-GUI (Kiste, Ambos, Shulker, ...) hat ein anderes containerMenu
        // als das Standard-Spieler-Inventar - Slot-Indizes waeren dann falsch gemappt und koennten
        // Items in der falschen GUI verschieben. Das Meteor-ClickGUI und das eigene Inventar (E) teilen
        // sich weiterhin das normale inventoryMenu, also darf Totem-Nachlegen dabei NICHT pausieren.
        boolean foreignContainerOpen = mc.player.containerMenu != mc.player.inventoryMenu;
        if (fastTotem.get() && !foreignContainerOpen && !turtleModeActive) ensureOffhandTotem();
        if (!foreignContainerOpen) maintainHealPotions(self);
        maintainFireResistance();
        if (!guiOpen) {
            if (invManager.get() && tickCounter % 20 == 0) inventoryTick(self);
        }
    }

    /** Zielwahl/-verfolgung: findet das Ziel (oder raeumt bei Zielverlust auf), wertet periodisch
     *  ein besseres Ziel neu, pflegt engaged/engagedTargetId und die kontinuierliche Blickrichtung.
     *  @return das (ggf. neu gewaehlte) Ziel, oder null wenn keins gefunden wurde. */
    private LivingEntity handleTargeting(Player self) {
        LivingEntity target = findTarget(self);

        // Ziel weg -> Baritone stoppen
        if (target == null) {
            engaged = false;
            dtapStage = 0;
            cancelFollow();
            cancelInstaCity();
            resetFireWalkState();
            activeHole = null;
            heightCalcOrigin = null;
            Input.setKeyState(mc.options.keyLeft, false);
            Input.setKeyState(mc.options.keyRight, false);
            mc.player.setShiftKeyDown(false);
            Input.setKeyState(mc.options.keyJump, false);
            currentAction = "beobachten";
            return null;
        }

        updateTracking(target);
        checkSecondEnemy(self, target);
        updateElytraFlight();

        // Periodische Neubewertung statt stur am einmal gewaehlten Ziel festzuhalten - smartTargeting
        // wirkte bisher nur bei der ERSTEN Zielwahl. Wenn inzwischen z.B. ein zweiter Spieler beim
        // aktuellen Ziel aufgetaucht ist (macht einen anderen Kandidaten zum sichereren Wechsel) oder
        // das Ziel selbst Rueckendeckung verloren hat, kann pickSmartTarget() das erst hier erkennen.
        // 40-Tick-Gate verhindert Zielflackern bei knappen Score-Unterschieden. MUSS vor der dist-
        // Berechnung unten laufen, sonst wuerde dist noch gegen das alte Ziel gemessen.
        if (smartTargeting.get() && target instanceof Player && tickCounter - lastRetargetCheck > 40) {
            lastRetargetCheck = tickCounter;
            Player alt = findPlayerTarget(self);
            if (alt != null && alt.isAlive() && !alt.getUUID().equals(target.getUUID())) {
                target = alt;
            }
        }

        double dist = Math.sqrt(self.distanceToSqr(target));
        boolean flying = mc.player.isFallFlying();
        if (!target.getUUID().equals(engagedTargetId)) {
            invalidateTargetPath();
            engagedTargetId = target.getUUID();
            engaged = false; // neues Ziel -> Kaltstart-Schwelle (engage-distance) gilt wieder von vorn
        }
        Vec3 targetVelocity = target.getDeltaMovement();
        boolean targetStandingStill = target.onGround()
            && targetVelocity.x * targetVelocity.x + targetVelocity.z * targetVelocity.z <= 0.0025;
        if (dist <= engageDistance.get() || pursueStationaryTargets.get() && targetStandingStill) engaged = true;

        // Kontinuierliches Ziel-Tracking: der Bot schaut das Ziel (vorhergesagte Position) die meiste
        // Zeit direkt an, nicht nur kurz waehrend einer einzelnen Anzielen-Aktion. Ausserhalb der
        // Nahkampf-Strafe-Distanz (die ihre eigene Rotation setzt) und nicht waehrend Elytra-Flug.
        if (trackTarget.get() && !flying && dist > 3.6) {
            Vec3 lookAt = predict(target);
            if (freeLook.get()) {
                pendingFreeLook = true;
                pendingFreeLookYaw = Rotations.getYaw(lookAt);
                pendingFreeLookPitch = Rotations.getPitch(lookAt);
            } else {
                mc.player.setYRot((float) Rotations.getYaw(lookAt));
                mc.player.setXRot((float) Rotations.getPitch(lookAt));
            }
        }

        return target;
    }

    /** Samplet Health, Y-Impuls und Anchor-Explosionen VOR jedem Blocking-/Ziel-Early-Return. Dadurch
     *  koennen Explosionsflucht und Floor-Snap weder durch Schildblock noch durch Zielverlust einen
     *  Tick lang veralteten Bewegungs- oder Anchor-Snapshot auswerten. */
    private void sampleCombatMotion(Player self) {
        float health = self.getHealth();
        selfLostHealthThisTick = lastSelfHpForRubberband >= 0 && health < lastSelfHpForRubberband;
        selfTookRealDamageThisTick = lastSelfHpForRubberband >= 0 && health < lastSelfHpForRubberband - 1.0f;
        lastSelfHpForRubberband = health;

        double verticalVelocity = self.getDeltaMovement().y;
        selfVerticalDeltaThisTick = Double.isNaN(lastSelfVelocityY) ? 0.0 : verticalVelocity - lastSelfVelocityY;
        lastSelfVelocityY = verticalVelocity;

        if (anchorExplosionWindowStart >= 0 && tickCounter - anchorExplosionWindowStart > 20) {
            anchorExplosionWindowStart = -1;
            anchorExplosionWindowCount = 0;
        }

        if (!antiAnchorDisengage.get() || !isOpenOneByOneHole(self)) {
            clearAnchorExplosionTracking();
            return;
        }

        int anchorExplosions = countDamagingAnchorExplosions(self);
        if (anchorExplosions > 0) {
            if (anchorExplosionWindowStart < 0) anchorExplosionWindowStart = tickCounter;
            anchorExplosionWindowCount = Math.min(4, anchorExplosionWindowCount + anchorExplosions);
        }
    }

    /** Explosionen haben Vorrang vor Blocken, Zielwahl und Offensiv-Aktionen. Anti-Anchor und
     *  Floor-Snap sind unabhaengige Schalter; nur der alte Knockback-/Fallschutz bleibt an
     *  {@link #knockbackPearl} gebunden. */
    private boolean handleExplosionEscape(Player self) {
        if (mc.gui.screen() != null || mc.gameMode == null) return false;

        if (antiAnchorDisengage.get() && isOpenOneByOneHole(self) && anchorExplosionWindowCount > 2
            && tickCounter - lastPearlTick > delay(20) && InvHelper.has(Items.ENDER_PEARL)) {
            double[] aim = solveDisengagePearlAim(self);
            if (aim != null && throwPearlAt(aim[0], aim[1])) {
                currentAction = "pearl-anchor-disengage";
                return true;
            }
        }

        if (explosionFloorSnap.get() && selfLostHealthThisTick && selfVerticalDeltaThisTick > 0.45
            && tickCounter - lastPearlTick > delay(15) && InvHelper.has(Items.ENDER_PEARL)) {
            double[] aim = solveFloorSnapPearlAim(self);
            if (aim != null && throwPearlAt(aim[0], aim[1])) {
                currentAction = "pearl-floor-snap";
                return true;
            }
        }

        return false;
    }

    private boolean isOpenOneByOneHole(Player self) {
        return countBoxedSides(self) == 4
            && isStandable(self.blockPosition())
            && !mc.level.getBlockState(self.blockPosition().above(2)).blocksMotion();
    }

    private void clearAnchorExplosionTracking() {
        trackedAnchorCharges.clear();
        scannedAnchorCharges.clear();
        anchorExplosionWindowStart = -1;
        anchorExplosionWindowCount = 0;
    }

    private Vec3 pearlThrowVelocity(Player self) {
        Vec3 own = self.getKnownMovement();
        return new Vec3(own.x, self.onGround() ? 0 : own.y, own.z);
    }

    /** Zielpunkt 2.5 Blöcke über den Füßen und 0.75 Blöcke vor dem Loch. Der kleine horizontale
     *  Versatz sorgt dafür, dass PvpMath auch bei starkem Explosions-Y-Impuls die geerbte
     *  Geschwindigkeit in die Lösung einrechnen kann, statt einen reinen 90-Grad-Wurf zu verwenden. */
    private double[] solveDisengagePearlAim(Player self) {
        double yaw = Math.toRadians(self.getYRot());
        Vec3 forward = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        Vec3 from = self.getEyePosition().subtract(0, 0.1, 0);
        Vec3 landing = self.position().add(0, 2.5, 0).add(forward.scale(0.75));
        return PvpMath.solvePearlAim(from, landing, pearlThrowVelocity(self));
    }

    /** Floor-Snap bevorzugt eine echte Standflaeche 1-3 Bloecke vor dem Spieler. Nur wenn direkt unter
     *  den Fuessen noch Boden liegt, wird als kontrollierter Fallback exakt 88.5 Grad verwendet. */
    private double[] solveFloorSnapPearlAim(Player self) {
        Direction forward = Direction.fromYRot(self.getYRot());
        BlockPos feet = self.blockPosition();
        BlockPos landing = null;
        for (int distance = 1; distance <= 3; distance++) {
            BlockPos candidate = feet.relative(forward, distance);
            if (isStandable(candidate)) {
                landing = candidate;
                break;
            }
        }

        if (landing == null && isStandable(feet)) {
            return new double[] { self.getYRot(), 88.5, 0 };
        }
        if (landing == null) return null;

        Vec3 from = self.getEyePosition().subtract(0, 0.1, 0);
        Vec3 target = Vec3.atBottomCenterOf(landing).add(0, 0.1, 0);
        return PvpMath.solvePearlAim(from, target, pearlThrowVelocity(self));
    }

    /** Serverseitige entityIds koennen nicht vorhersagebar berechnet werden: der Client erhaelt die
     *  AddEntity-ID erst nach Verarbeitung des Use-Item-Pakets. CrystalAuras eigener EntityAdded-Handler
     *  greift deshalb ausschliesslich die reale, vom Server vergebene Entity-ID ab. */
    private void syncZeroDelaySettings(CrystalAura ca) {
        if (!zeroDelay.get() && !instantMode.get()) return;

        if (savedPlaceDelay < 0) savedPlaceDelay = ca.placeDelay.get();
        ca.placeDelay.set(0);

        Setting<Integer> breakDelay = crystalSetting(ca, "breakDelay");
        if (breakDelay != null) {
            if (savedBreakDelay < 0) savedBreakDelay = breakDelay.get();
            breakDelay.set(0);
        }

        Setting<Integer> ticksExisted = crystalSetting(ca, "ticksExisted");
        if (ticksExisted != null) {
            if (savedTicksExisted < 0) savedTicksExisted = ticksExisted.get();
            ticksExisted.set(0);
        }

        Setting<Boolean> fastBreak = crystalSetting(ca, "fastBreak");
        if (fastBreak != null) {
            if (savedFastBreak == null) savedFastBreak = fastBreak.get();
            fastBreak.set(true);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> Setting<T> crystalSetting(CrystalAura ca, String name) {
        try {
            var field = CrystalAura.class.getDeclaredField(name);
            field.setAccessible(true);
            return (Setting<T>) field.get(ca);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void restoreZeroDelaySettings(CrystalAura ca) {
        if (savedPlaceDelay >= 0) ca.placeDelay.set(savedPlaceDelay);
        Setting<Integer> breakDelay = crystalSetting(ca, "breakDelay");
        if (breakDelay != null && savedBreakDelay >= 0) breakDelay.set(savedBreakDelay);
        Setting<Integer> ticksExisted = crystalSetting(ca, "ticksExisted");
        if (ticksExisted != null && savedTicksExisted >= 0) ticksExisted.set(savedTicksExisted);
        Setting<Boolean> fastBreak = crystalSetting(ca, "fastBreak");
        if (fastBreak != null && savedFastBreak != null) fastBreak.set(savedFastBreak);
    }

    /** Zentraler Blockplatzierungs-Einstieg. Nur erfolgreich lokalisierte Platzierungen werden
     *  bestaetigungspflichtig; End-Crystal-Entitaeten laufen weiterhin ueber CrystalAura. */
    private boolean placeTrackedBlock(BlockPos pos, FindItemResult item, boolean rotate, int priority) {
        BlockPos trackedPos = pos.immutable();
        Runnable place = () -> placeBlockNow(trackedPos, item, priority);
        if (!rotate) return placeBlockNow(trackedPos, item, priority);

        Vec3 center = Vec3.atCenterOf(trackedPos);
        return queueWithCombatSlot(item, Rotations.getYaw(center), Rotations.getPitch(center), priority, place);
    }

    private boolean placeBlockNow(BlockPos pos, FindItemResult item, int priority) {
        boolean ownsSlot = false;
        if (!item.isOffhand()) {
            if (combatSlotReserved) {
                if (combatSlotTargetSlot != item.slot()) return false;
            } else {
                if (!item.isHotbar() || !reserveCombatSlot(item.slot())) return false;
                ownsSlot = true;
            }
        }

        boolean placed;
        try {
            placed = BlockUtils.place(pos, item, false, priority);
        } finally {
            if (ownsSlot) releaseCombatSlot();
        }
        if (!placed || !ghostBlockMitigation.get() || mc.level == null) return placed;

        BlockState expected = mc.level.getBlockState(pos);
        if (!expected.isAir()) {
            pendingBlockConfirmations.put(pos.immutable(), new PendingBlockConfirmation(tickCounter, expected));
        }
        return true;
    }

    /** 2 x RTT = 4 x gemessener Einweg-Ping. Ein explizites Server-Update entfernt den Eintrag
     *  vorzeitig. Bleibt die lokale Vorhersage danach bestehen, ist sie ein Ghost; dann werden nur
     *  lokaler Client-State und Baritone-Cache bereinigt - es gibt kein Standardprotokoll, mit dem
     *  ein Client den Server korrekt um eine Blockresynchronisierung bitten kann. */
    private void reconcilePendingBlockConfirmations() {
        if (!ghostBlockMitigation.get()) {
            pendingBlockConfirmations.clear();
            return;
        }

        int timeout = Math.max(8, pingTicks() * 4);
        var iterator = pendingBlockConfirmations.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (tickCounter - entry.getValue().tick() < timeout) continue;

            BlockPos pos = entry.getKey();
            BlockState expected = entry.getValue().expected();
            if (mc.level != null && mc.level.getBlockState(pos).equals(expected)) {
                mc.level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                invalidateBaritoneAfterGhostBlock();
            }
            iterator.remove();
        }
    }

    private void invalidateBaritoneAfterGhostBlock() {
        followActive = false;
        followedId = null;
        activeHole = null;
        heightCalcOrigin = null;

        var baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
        baritone.getFollowProcess().cancel();
        baritone.getPathingBehavior().cancelEverything();
        var customGoal = baritone.getCustomGoalProcess();
        if (customGoal.isActive()) customGoal.onLostControl();

        var worldData = baritone.getWorldProvider().getCurrentWorld();
        if (worldData != null) worldData.getCachedWorld().reloadAllFromDisk();
    }

    /** Reaktive Verteidigung: Stuck-/Rubberband-Erkennung, Knockback-/Notfall-/Flucht-Perlen und
     *  Rueckzugs-Schwellen. Laeuft NACH der Zielwahl (braucht target/dist), vor jeder Offensiv-Aktion.
     *  @return true, wenn diesen Tick bereits eine Verteidigungsreaktion den gemeinsamen
     *  Aktions-/Rotations-Slot belegt hat und der restliche Tick (Angriff/Platzierung) ausfallen muss. */
    private boolean handleDefense(Player self, LivingEntity target, double dist) {
        // Stuck-Erkennung: eigene Position + Ziel-HP beobachten
        double selfMoved = lastSelfPos == null ? 999 : self.position().distanceTo(lastSelfPos);
        lastSelfPos = self.position();

        // Anti-Rubberband: ploetzlicher, nicht selbst verursachter Sprung -> Server hat uns korrigiert.
        // Baritones Pfad verwerfen und kurz bremsen statt gegen die Korrektur anzukaempfen.
        // Explosions-Knockback (spuerbarer HP-Verlust im selben Tick) zaehlt NICHT als Rubberband -
        // sonst wird genau der Moment ignoriert, in dem der Bot eigentlich fliehen muesste. ABER: ein
        // WIRKLICH extremer Sprung (deutlich mehr als selbst starker Explosions-Knockback in einem Tick
        // ueberbruecken kann) greift auch waehrend des Kampfes - sonst wird das Rubberbanding ausgerechnet
        // in den Crystal/Anchor-lastigen Momenten ignoriert, in denen es laut Beobachtung am haeufigsten ist.
        if (rubberbandCooldown > 0) {
            rubberbandCooldown--;
        } else if (antiRubberband.get() && tickCounter - lastPearlTick > delay(10)
            && (selfMoved > 6.0 || (selfMoved > 2.5 && !selfTookRealDamageThisTick))) {
            BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().cancelEverything();
            followActive = false;
            rubberbandCooldown = delay(10);
            currentAction = "rubberband";
        }

        // Durch Knockback (Schlag/Explosion) in die Luft geschleudert ODER generell gerade in einem
        // gefaehrlichen Fall (z.B. von einer Kante gelaufen): sofort perlen, um kontrolliert runterzukommen
        // statt hilflos zu fallen oder als leichtes Ziel in der Luft zu haengen.
        double verticalVelocity = self.getDeltaMovement().y;
        boolean launchedByHit = selfTookRealDamageThisTick && verticalVelocity > 0.35;
        boolean fallingDanger = !self.onGround() && self.fallDistance > 3.0f && verticalVelocity < 0.05;
        if (knockbackPearl.get() && (launchedByHit || fallingDanger)
            && tickCounter - lastPearlTick > delay(15)
            && InvHelper.has(Items.ENDER_PEARL)
            && throwPearlAtCurrentYaw(80.0)) {
            currentAction = launchedByHit ? "pearl-knockback" : "pearl-fallschutz";
            return true;
        }

        boolean blockedByObstacle = dist <= 6.0 && !self.hasLineOfSight(target);
        obstacleStuckTicks = blockedByObstacle ? obstacleStuckTicks + 1 : 0;

        boolean targetHpChanged = lastTargetHpForStuck < 0 || Math.abs(target.getHealth() - lastTargetHpForStuck) > 0.01f;
        lastTargetHpForStuck = target.getHealth();
        watchdogStuckTicks = (selfMoved < 0.05 && !targetHpChanged) ? watchdogStuckTicks + 1 : 0;

        // Kein Sichtkontakt trotz Naehe (Hindernis im Weg): so gut wie sofort reagieren,
        // klappt das nicht, zum Gegner perlen statt festzustehen.
        if (obstacleStuckTicks > 8 && pearlThrow.get() && tickCounter - lastPearlTick > delay(20)
            && InvHelper.has(Items.ENDER_PEARL)) {
            throwPearl(target, false);
            currentAction = "pearl-obstacle";
            obstacleStuckTicks = 0;
        }

        // Oszillations-Erkennung: an einer Krater-/Kanten-Kante kann Baritone zwischen zwei fast
        // gleich guten Routen hin- und herspringen (z.B. hochklettern vs. drumherum) - das ist AKTIVE
        // Bewegung (selfMoved bleibt hoch, targetHpChanged aendert nichts daran), also greift weder der
        // LOS-basierte Obstacle-Check oben (Ziel bleibt sichtbar von der Kraterkante aus) noch der
        // Total-Stillstand-Watchdog unten (selfMoved liegt weit ueber der 0.05-Schwelle). Stattdessen den
        // NETTO-Fortschritt ueber ein laengeres Zeitfenster (1.5s) pruefen statt nur Tick-zu-Tick -
        // bleibt die Position dabei praktisch gleich trotz staendiger Bewegung, zum Ziel perlen statt
        // endlos weiter hin- und herzulaufen.
        if (oscillationAnchorPos == null || tickCounter - oscillationAnchorTick > 30) {
            if (oscillationAnchorPos != null && self.position().distanceTo(oscillationAnchorPos) < 1.5
                && pearlThrow.get() && tickCounter - lastPearlTick > delay(20)
                && InvHelper.has(Items.ENDER_PEARL)) {
                throwPearl(target, false);
                currentAction = "pearl-oszillation";
            }
            oscillationAnchorPos = self.position();
            oscillationAnchorTick = tickCounter;
        }

        // Generischer Watchdog: konfigurierbare Anzahl Ticks ohne Eigenbewegung UND ohne Ziel-Schaden ->
        // kompletter Reset, damit der Bot nicht haengen bleibt bis man selbst zuschlaegt. War fix auf
        // 100 Ticks (5s) - gegen einen mobilen Gegner laengst wieder weg, viel zu lang.
        if (watchdogStuckTicks > watchdogTicks.get()) {
            cancelFollow();
            resetPositioningState();
            dtapStage = 0;
            anchorCandidates.clear();
            anchorCandidateIndex = 0;
            lastAuraSwitch = -999;
            crystalForcedUntil = 0;
            watchdogStuckTicks = 0;
        }

        // Eingekesselt (3+ Seiten blockiert UND Decke zu, kein Rausspringen moeglich) -> Fluchtperle
        // bevor die letzte Wand zugeht. Deckung mit offenem Himmel (z.B. unser eigenes Loch, Feature 8)
        // zaehlt bewusst NICHT als Kaefig - da kann man jederzeit selbst wieder raus.
        if (countBoxedSides(self) >= 3 && mc.level.getBlockState(self.blockPosition().above(2)).blocksMotion()
            && escapePearl.get() && tickCounter - lastPearlTick > delay(15)
            && InvHelper.has(Items.ENDER_PEARL)) {
            throwPearl(target, true);
            currentAction = "escape-cage";
            return true;
        }

        // Notfall-Flucht: kritisches HP -> Perle weg statt sinnlos weiterzukaempfen, egal wie weit der Gegner ist
        boolean pearlReady = tickCounter - lastPearlTick > delay(20) && InvHelper.has(Items.ENDER_PEARL);
        if (escapePearl.get() && self.getHealth() <= 6.0f) {
            if (pearlReady) {
                throwPearl(target, true);
                currentAction = "escape-pearl";
                return true;
            }
            // Gestufter Fallback statt schutzlos weiterzukaempfen: keine Perle da/auf Cooldown -> Schild
            // hoch und physisch zurueckweichen (dieselbe explosionRetreatUntil-Bewegung wie beim
            // Explosions-Rueckzug), bis wieder eine Perle bereit ist oder sich die Lage entspannt.
            if (autoShield.get() && !blocking) {
                shieldUntil = tickCounter + 20;
                startBlock();
            }
            explosionRetreatUntil = tickCounter + 20;
            currentAction = "notfall-schild-rueckzug";
            return true;
        }

        // Rueckzugs-Schwelle: Totems knapp UND keine Explosiv-Ressourcen mehr (Crystal, Anchor UND Bett)
        // -> Gefecht abbrechen statt aussichtslos im reinen Nahkampf weiterzumachen. Bett muss hier
        // eigens gegengeprueft werden - sonst haelt sich der Bot faelschlich fuer "ohne jede Explosiv-
        // Option", obwohl mit aktiviertem use-beds (z.B. im Nether ohne Anchor-Support) noch ein
        // voll funktionsfaehiger dritter Explosionsweg zur Verfuegung steht, und bricht das Gefecht
        // dauerhaft ab statt ihn zu nutzen.
        boolean hasBedSupply = useBeds.get() && bedsExplodeHere() && totalItem(GodmodePvP::isBed) > 0;
        if (retreatThreshold.get() && lowOnTotems && warnedOutOfCrystals && warnedOutOfAnchorSupply && !hasBedSupply) {
            cancelFollow();
            if (dist <= 10.0 && tickCounter - lastPearlTick > delay(30)
                && InvHelper.has(Items.ENDER_PEARL)) {
                throwPearl(target, true);
            }
            currentAction = "rueckzug";
            return true;
        }

        // Verlorener Trade: wir selbst wurden gerade hart getroffen (typischerweise Crystal/Anchor-Pop),
        // aber unsere eigenen Explosionen richten seit einer Weile keinen Schaden beim Gegner an
        // (Platzierung blockiert/unerreichbar, Schild, o.ae.) - abhauen statt einen Verlust-Trade fortzusetzen.
        if (retreatOnLosingTrade.get() && tickCounter - lastSelfPopTick < 60
            && tickCounter - lastTargetDamageTick > 60 && tickCounter - lastPearlTick > delay(30)
            && InvHelper.has(Items.ENDER_PEARL)) {
            throwPearl(target, true);
            currentAction = "pearl-verlorener-trade";
            lastSelfPopTick = -999;
            return true;
        }

        return false;
    }

    /** Offensive Kampf-Logik: Nahkampf-Bewegung/-Schlaege, Perlen-Gapclose, Verfolgung, D-Tap,
     *  Anchor/Bett-Wartung und Aura-Platzierung (Crystal/Anchor/Bett). Laeuft nur, wenn
     *  {@link #handleDefense} diesen Tick nicht bereits selbst abgeschlossen hat. */
    private void handleOffense(Player self, LivingEntity target, double dist, boolean flying, boolean guiOpen) {
        if (handleInstaCity(self, target, dist)) return;
        if (handleAntiEscapeTrap(target)) return;
        handleTrap(target);
        if (meleeStrafe.get() && !flying) updateCombatMovement(target, dist);
        manageSprintForKnockback(dist);

        if (killAuraOn.get()) {
            Module ka = Modules.get().get(KillAura.class);
            if (ka != null && !ka.isActive()) ka.toggle();
        }

        // Feindlicher Crystal frisch platziert (in 5 m)? -> kurzes Schild-Block-Fenster
        java.util.List<EndCrystal> nearCrystals = mc.level.getEntitiesOfClass(EndCrystal.class, self.getBoundingBox().inflate(5));
        boolean freshCrystal = lastCrystalCount >= 0 && nearCrystals.size() > lastCrystalCount && !nearCrystals.isEmpty();

        // Anchor/Bett sind Bloecke, keine Entities - dieselbe Delta-Erkennung wie oben fuer Crystals,
        // nur per Block-Scan im gleichen 5-Block-Radius statt per Entity-Liste. Ein frisch erscheinender
        // Anchor/Bett in der Naehe ist ein ebenso starkes Vorzeichen einer unmittelbar bevorstehenden
        // Explosion wie ein neuer Crystal - vorher gab es dafuer ueberhaupt keine reaktive Verteidigung.
        int anchorBlocks = countNearbyBlocks(self.blockPosition(), 5, st -> st.is(Blocks.RESPAWN_ANCHOR));
        int bedBlocks = countNearbyBlocks(self.blockPosition(), 5, st -> st.getBlock() instanceof BedBlock);
        boolean freshAnchor = lastAnchorBlockCount >= 0 && anchorBlocks > lastAnchorBlockCount;
        boolean freshBed = lastBedBlockCount >= 0 && bedBlocks > lastBedBlockCount;

        if (freshCrystal || freshAnchor || freshBed) {
            // Kurzes Rueckzugs-Fenster fuer updateCombatMovement() (siehe dort) - unabhaengig vom
            // Schild-Setting, das ist eine reine Bewegungsentscheidung: physisch Abstand zur frisch
            // erschienenen Explosionsquelle gewinnen, nicht nur wegblocken.
            explosionRetreatUntil = tickCounter + 12;
            if (autoShield.get() && !blocking) {
                shieldUntil = tickCounter + 15; // zuendet praktisch sofort - kurzes, hartes Block-Fenster
                startBlock();
            }
        }
        lastCrystalCount = nearCrystals.size();
        lastAnchorBlockCount = anchorBlocks;
        lastBedBlockCount = bedBlocks;

        // Pop-Fenster: volle Aggression
        if (tickCounter < popBurstUntil) {
            if (preHit.get() && dist <= attackRange.get() && self.getAttackStrengthScale(0.5f) >= 0.9f && canReachThrough(target) && prepareCritAndCheck(dist)) attackMelee(target);
            selectAura(target);
            currentAction = "burst";
        }

        // Perlen-Gapclose (bei grosser Distanz schnellerer Cooldown) - nur wenn schon engaged (siehe unten),
        // sonst wuerde auch ein 35 Blocke entfernter Spieler beim Kaltstart sofort angeperlt. Die Schwelle
        // ist an attack-range gekoppelt (nie kleiner als Nahkampf-Reichweite+0.5) - sonst wuerde ein zu
        // niedrig gestelltes pearl-min-dist eine Perle verschwenden, obwohl der Gegner noch schlagbar waere.
        // Sichtlinie ist Pflicht: ohne sie fliegt die Perle nur gegen die Wand/den Huegel dazwischen statt
        // zum Gegner (anders als die gezielte Hindernis-Perle oben, die genau auf so ein Durchclippen zielt).
        double pearlReachThreshold = Math.max(pearlMinDist.get(), attackRange.get() + 0.5);
        // Fixer, konservativer Cooldown statt distanzabhaengig gestaffelt: in einer grossen Arena ist
        // "weit weg" der Normalfall, nicht die Ausnahme - die alte Staffelung (schneller ab >15 Bloecke)
        // machte den "schnellen" Ast faktisch zum Standardfall und hielt den Perlen-Spam am Leben.
        long pearlCooldown = delay(50);
        if (pearlThrow.get() && dist > pearlReachThreshold && engaged && self.hasLineOfSight(target)
            && tickCounter - lastPearlTick > pearlCooldown && !guiOpen) {
            throwPearl(target, false);
            currentAction = "pearl-gapclose";
        }

        // Verfolgen: Baritones FollowProcess folgt dem Ziel von selbst (fluessiges Re-Pathing) - ausser
        // wir sind gerade unterwegs zu einem gefundenen Loch, dann laesst Baritones CustomGoalProcess laufen.
        // Erst ab Engage-Distanz wird ueberhaupt losgelaufen/-geflogen (Kaltstart-Bremse) - ist der Bot
        // aber schon "engaged" (siehe oben, sticky bis follow-range/Zielverlust), wird auch nach einem
        // Explosions-Knockback ueber die Engage-Distanz hinaus weiterverfolgt statt die Verfolgung
        // abzubrechen - genau das war sonst der Bug: Crystal wirft den Gegner raus, Bot bleibt einfach stehen.
        // Waehrend eines aktiven Rueckzugsschritts (explosionRetreatUntil, siehe updateCombatMovement)
        // Baritones Verfolgung pausieren statt weiterlaufen zu lassen: Baritones FollowProcess haelt
        // staendig den konfigurierten followRadius (3 Bloecke) zum Ziel und pathet sofort wieder NAEHER,
        // sobald die manuelle Rueckwaerts-Taste den Abstand vergroessert - dieses Tauziehen zwischen
        // Baritones eigener Bewegung und dem WASD-Rueckzug erzeugte spuerbar mehr serverseitige
        // "moved wrongly"-Flags als die reine Seitwaerts-Strafe (die den Abstand kaum aendert).
        if (!engaged) {
            cancelFollow();
            activeHole = null;
            heightCalcOrigin = null;
            currentAction = "beobachten-fern";
        } else if (tickCounter < explosionRetreatUntil || dist < MIN_ATTACK_DIST) {
            cancelFollow();
            currentAction = "rueckzugsschritt";
        } else if (flying) {
            updateFollow(target);
        } else if (fireWalkAllowed(dist) && fireBlocksPath(self, target)) {
            walkThroughFire(self, target, dist);
            currentAction = "feuer-durchqueren";
        } else {
            if (fireWalkStartTick >= 0) resetFireWalkState();
            if (!updateHolePositioning(target, dist)) updateFollow(target);
        }

        // D-Tap: nach einem spuerbaren Knockback-Treffer (unser Schlag, Anchor- oder Crystal-Explosion)
        // Obsidian in die vorhergesagte Flugbahn setzen, zwei Crystals im Abstand der Trefferimmunitaet
        // (~0.5s) zuenden - klassische Pro-Technik fuer einen schnellen Doppel-Totem-Pop.
        if (dtapCooldown > 0) dtapCooldown--;
        if (dtap.get() && dtapStage == 0 && dtapCooldown <= 0 && dist <= 6.0) {
            Vec3 tvel = velocities.getOrDefault(target.getUUID(), Vec3.ZERO);
            // Volle 3D-Geschwindigkeit statt nur horizontal - ein senkrechter Anchor-Launch (fast reine
            // Y-Komponente) soll den D-Tap genauso ausloesen wie seitlicher Explosions-Knockback.
            if (tvel.lengthSqr() > 0.09) startDtap(target);
        }

        // Anchor-Wartung: laeuft IMMER, unabhaengig vom aktuellen Aura-Modus - ein waehrend Anchor-Modus
        // platzierter Anchor wird auch fertig geladen/gezuendet, wenn zwischenzeitlich auf Crystal
        // umgeschaltet wird. Das war die Hauptursache dafuer, dass nicht alle Anchors gezuendet wurden.
        maintainNearbyAnchors();
        maintainNearbyBeds();

        // Verteidigung, die keinen Aura-Modus braucht und deshalb IMMER laeuft: fremde Betten/Kolben
        // wegraeumen, bevor sie zuenden bzw. ausfahren, den eigenen Stand sichern und im Nahbereich
        // die Fuesse mit Obsidian umstellen. Reihenfolge ist Absicht - eine gegnerische Bett-Explosion
        // toetet sofort, ein weggesprengter Boden erst beim naechsten Schritt, Surround ist reine Vorsorge.
        removeHostileBlocks(self);
        secureFootingTick(self, dist);
        updateSurround(self, target, dist);

        // Piston-Aura: nur wenn ueber Sekunden keine Explosivoption mehr Schaden bringt (Gegner
        // eingegraben). Laeuft VOR der normalen Aura-Auswahl, weil sie genau deren Ausfall behandelt.
        if (runPistonAura(self, target, dist)) return;

        if (dtapStage != 0) {
            runDtapTick(target);
            currentAction = "d-tap";
        } else if (interceptEnemyBoxing(target)) {
            // currentAction wurde bereits in interceptEnemyBoxing() gesetzt - der Anchor liegt jetzt in
            // der Luecke, maintainNearbyAnchors() (laeuft oben unabhaengig vom Aura-Modus) laedt/zuendet
            // ihn in den naechsten Ticks ganz normal weiter.
        } else {
            if (smartAuras.get()) {
                selectAura(target);
            } else if (auraMode != 0) {
                // Smart-Auswahl aus: immer einfacher Crystal-Modus statt fuer immer beim Initialwert (-1)
                // haengen zu bleiben - sonst wuerde das Ausschalten dieser reinen Auswahl-Einstellung
                // versehentlich die GESAMTE Explosions-Kampflogik (Crystal/Anchor/Bett) stilllegen.
                auraMode = 0;
            }

            // Bett-Abstandhalten: eine Bett-Explosion macht im Nahbereich ein Vielfaches eines Crystals
            // (live ~50 Rohschaden). Klebt der Bot im Nahkampf am Gegner (1-3 Bloecke), liegt JEDE
            // erreichbare Platzierung auch im eigenen Explosionsradius - calcBestBed findet dann
            // garantiert null Kandidaten (live gemessen: cands=0 bei dist<=4.3, Treffer nur bei
            // dist 4.6-5.4). Sind Betten die EINZIGE Explosiv-Option, wird deshalb BARITONES
            // Verfolgungs-Radius aufgemacht statt gegen ihn anzukaempfen: ein reiner Rueckwaerts-
            // Schritt (explosionRetreatUntil) hat den Bot stattdessen komplett eingefroren - er
            // cancelt Baritones Follow, der eigene Rueckwaerts-Tastendruck feuert aber nur INNERHALB
            // attack-range, also stand der Bot zwischen attack-range und Bett-Reichweite bewegungslos
            // (live reproduziert: dist blieb sekundenlang exakt auf 3.66 stehen).
            boolean bedIsOnlyExplosive = useBeds.get() && bedsExplodeHere() && totalItem(GodmodePvP::isBed) > 0
                && totalItem(Items.END_CRYSTAL) == 0
                && !(anchorsExplodeHere() && totalItem(Items.RESPAWN_ANCHOR) > 0 && totalItem(Items.GLOWSTONE) > 0);
            // BEWUSST nur im Bett-only-Fall: den groesseren Radius versuchsweise auch dann zu halten,
            // wenn Betten lediglich UNTERdurchschnittlich genutzt sind, wurde live gemessen und war ein
            // klarer Rueckschritt (Crystals 288 -> 260 UND Betten 89 -> 71 pro 90s) - auf 5 Bloecken
            // Abstand verliert CrystalAura Platzierungsziele, ohne dass dadurch mehr self-safe
            // Bett-Zellen entstehen. Die Bett-Quote haengt an der Eigenschaden-Grenze, nicht am Abstand.
            int wantFollowRadius = bedIsOnlyExplosive ? BED_FOLLOW_RADIUS : DEFAULT_FOLLOW_RADIUS;
            var baritoneSettings = BaritoneAPI.getSettings();
            if (baritoneSettings.followRadius.value != wantFollowRadius) {
                baritoneSettings.followRadius.value = wantFollowRadius;
            }
            if (bedIsOnlyExplosive && dist < BED_STANDOFF_DIST) currentAction = "bett-abstand";

            if (auraMode == 1) {
                // 3s ohne neue Platzierung -> nicht ewig auf unerreichbaren/erschoepften Kandidaten
                // haengen bleiben, Crystal uebernimmt
                if (tickCounter - lastAnchorProgressTick > 60) {
                    anchorCandidates.clear();
                    anchorCandidateIndex = 0;
                    crystalForcedUntil = tickCounter + 60;
                    lastAnchorProgressTick = tickCounter;
                }
                tryPlaceAnchor();
                currentAction = "anchor";
            } else if (auraMode == 2) {
                // Gleiche Anti-Stuck-Logik wie Anchor: 3s ohne Fortschritt -> Crystal erzwingen statt
                // ewig auf unerreichbaren/erschoepften Bett-Kandidaten haengen zu bleiben.
                if (tickCounter - lastBedProgressTick > 60) {
                    bedCandidates.clear();
                    bedCandidateIndex = 0;
                    crystalForcedUntil = tickCounter + 60;
                    lastBedProgressTick = tickCounter;
                }
                tryPlaceBed();
                currentAction = "bed";
            } else if (auraMode == 0) {
                // Selbstheilung: falls CrystalAura extern/durch einen Fehler ausgegangen ist, wieder anschalten
                Module ca = Modules.get().get(CrystalAura.class);
                if (ca != null && !ca.isActive()) ca.toggle();
            }
        }

        if (shieldBreaker.get() && target instanceof Player p && p.isBlocking()) {
            breakShield(p);
            currentAction = "schild-brechen";
        } else if (preHit.get() && dist <= attackRange.get() && self.getAttackStrengthScale(0.5f) >= 0.9f
            && canReachThrough(target) && explosionImminent(target) && prepareCritAndCheck(dist)) {
            attackMelee(target);
            currentAction = "pre-hit";
        } else if (meleeFallback.get() && dist <= attackRange.get() && self.getAttackStrengthScale(0.5f) >= 0.9f
            && canReachThrough(target) && !explosionImminent(target)) {
            attackMelee(target);
            currentAction = "nahkampf-fallback";
        }
    }

    /** Sichtlinie zum Ziel fuer NAHKAMPF-Aktionen. Bei through-walls entfaellt sie: Vanilla prueft
     *  serverseitig nur die Angriffsdistanz, keine Sichtlinie - ein Schlag durch eine duenne Wand oder
     *  in ein Loch hinein kommt also wirklich an. Gilt bewusst NICHT fuer Enderperlen (die fliegen
     *  physikalisch gegen die Wand) - dort bleibt hasLineOfSight() direkt im Aufruf stehen. */
    private boolean canReachThrough(LivingEntity target) {
        return throughWalls.get() || mc.player.hasLineOfSight(target);
    }

    @Override
    public String getInfoString() {
        // Persistenter Warnzustand statt nur einer einzelnen Chatzeile beim Fehlschlag - bleibt
        // sichtbar, solange syncSupport() nicht wieder erfolgreich lief (siehe dort).
        return supportSyncFailed ? currentAction + " §c[kein Obsidian-Support!]" : currentAction;
    }

    /** Schild in die Haupthand (Offhand bleibt frei fuer den Totem) und blocken - reduziert Explosionsschaden. */
    private void startBlock() {
        if (drinkingFireRes) return; // Feuerresistenz-Trank haelt gerade den gemeinsamen Swap-Merkposten - nicht ueberschreiben
        FindItemResult shield = InvHelper.find(Items.SHIELD);
        if (!shield.found()) return;

        if (!shield.isHotbar() || !reserveCombatSlot(shield.slot())) return;
        blockingSwapBack = combatSlotPreviousSlot >= 0;
        mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
        blocking = true;
    }

    private void stopBlock() {
        releaseCombatSlot();
        blockingSwapBack = false;
        blocking = false;
    }

    // ---------- Anchor-Executor (1 Glowstone, verifizierend) ----------

    /** Platziert einen NEUEN Anchor am aktuell besten berechneten Kandidaten (Schadensoptimiert).
     *  Reine Platzierungs-Entscheidung - laden/zuenden uebernimmt maintainNearbyAnchors() separat und
     *  unabhaengig vom Aura-Modus, damit ein platzierter Anchor nie unfertig liegen bleibt. */
    private void tryPlaceAnchor() {
        if (anchorPlaceCooldown > 0) {
            anchorPlaceCooldown--;
            return;
        }

        Player self = mc.player;
        BlockPos spot = nextAnchorCandidate();
        if (spot == null) {
            anchorPlaceFails++;
            if (anchorPlaceFails >= 2) crystalForcedUntil = tickCounter + 40;
            anchorUnreachableTicks = 0;
            return;
        }

        double d = Math.sqrt(self.distanceToSqr(Vec3.atCenterOf(spot)));
        if (d > 4.2) {
            // Kandidat gerade unerreichbar (z.B. Baritone stoppt im Nahkampf) - nicht endlos auf denselben Platz warten
            anchorUnreachableTicks++;
            if (anchorUnreachableTicks > 8) {
                anchorCandidateIndex++;
                anchorUnreachableTicks = 0;
                if (anchorCandidateIndex >= anchorCandidates.size()) crystalForcedUntil = tickCounter + 40;
            }
            return; // naechster Tick neuer Versuch
        }
        anchorUnreachableTicks = 0;

        FindItemResult anchor = InvHelper.find(Items.RESPAWN_ANCHOR);
        if (!anchor.found()) return;

        // Ueber unsere eigene rotateAndRun()-Warteschlange statt BlockUtils.place()'s eigenem
        // rotate=true - so kann die Erstladung (Glowstone) direkt im Erfolgsfall-Callback nachgeschoben
        // werden und feuert dank Mehrfachaktionen-pro-Tick (siehe rotateAndRun-Dokumentation) noch im
        // SELBEN Tick, statt bis zum naechsten Tick zu warten: maintainNearbyAnchors() scannt VOR
        // diesem Aufruf im selben Tick und sieht den frisch platzierten Anchor daher noch nicht -
        // ohne dieses Nachschieben blieb ein frisch platzierter Anchor bis zum naechsten Tick ungeladen.
        Vec3 center = Vec3.atCenterOf(spot);
        rotateAndRun(Rotations.getYaw(center), Rotations.getPitch(center), PRIORITY_ANCHOR, () -> {
            if (placeTrackedBlock(spot, anchor, false, 50)) {
                anchorPlaceFails = 0;
                anchorPlaceCooldown = delay(6); // kurze Pause, damit maintainNearbyAnchors weitere Ladungen/Zuendung uebernimmt
                lastAnchorProgressTick = tickCounter;

                FindItemResult gs = InvHelper.find(Items.GLOWSTONE);
                if (gs.found() && interactAnchorAt(spot, gs)) {
                    anchorsChargedByUs.add(spot);
                }
            } else if (!combatSlotBusyFor(anchor)) {
                anchorCandidateIndex++;
            }
        });
    }

    private BlockPos nextAnchorCandidate() {
        while (anchorCandidateIndex < anchorCandidates.size()) {
            BlockPos p = anchorCandidates.get(anchorCandidateIndex);
            if (mc.level.getBlockState(p).isAir()) return p;
            anchorCandidateIndex++;
        }
        return null;
    }

    /** Scannt kontinuierlich (unabhaengig vom Aura-Modus) den Nahbereich nach JEDEM Respawn Anchor -
     *  egal von wem/wann platziert. Geladene werden sofort gezuendet, ungeladene mit Glowstone geladen -
     *  sofern der Eigenschaden vertretbar bleibt. Behebt liegen gebliebene, nie gezuendete Anchors
     *  (Hauptursache der Inkonsistenz) und nutzt nebenbei auch fremde/liegen gebliebene Anchors mit. */
    private void maintainNearbyAnchors() {
        if (!useAnchors.get() || anchorMode.get() == 2 || !anchorsExplodeHere()) return;
        if (anchorMaintCooldown > 0) {
            anchorMaintCooldown--;
            return;
        }

        Player self = mc.player;
        BlockPos origin = self.blockPosition();

        for (int dx = -4; dx <= 4; dx++) {
            for (int dy = -3; dy <= 3; dy++) {
                for (int dz = -4; dz <= 4; dz++) {
                    BlockPos pos = origin.offset(dx, dy, dz);
                    BlockState st = mc.level.getBlockState(pos);
                    if (!st.is(Blocks.RESPAWN_ANCHOR)) continue;

                    Vec3 center = Vec3.atCenterOf(pos);
                    if (Math.sqrt(self.distanceToSqr(center)) > 4.2) continue;

                    double selfDmg = DamageUtils.anchorDamage(mc.player, center);
                    if (selfDmg > maxSelfDamage.get() || !selfDamageAllowed(center, selfDmg)) continue;

                    int charges = st.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.RESPAWN_ANCHOR_CHARGES);
                    if (charges > 0) {
                        FindItemResult fir = InvHelper.find(itemStack -> !itemStack.isEmpty() && !itemStack.is(Items.GLOWSTONE));
                        if (!fir.found()) continue;
                        if (interactAnchorAt(pos, fir)) {
                            anchorMaintCooldown = delay(3);
                            anchorsChargedByUs.remove(pos); // gezuendet (bzw. versucht) - Position wieder frei fuer einen kuenftigen neuen Anchor
                        }
                        return; // Rotations-Slot ist so oder so belegt (versucht oder schon anderweitig vergeben) - naechster Tick
                    } else {
                        // Blitz-Anchor braucht nur 1 Glowstone - aber der Blockstate hier kommt erst nach
                        // einem Server-Rundlauf zurueck (auch Singleplayer laeuft ueber denselben Paket-Weg).
                        // Ohne diese eigene Merkliste liest charges hier fuer 1+ weitere Ticks noch 0, obwohl
                        // wir schon geladen haben, und der Bot steckt ungewollt ein zweites/drittes/viertes
                        // Glowstone rein - aus dem gewollten 1-Glowstone-Blitz-Anchor wurde eine volle 4er-Ladung.
                        if (anchorsChargedByUs.contains(pos)) continue;

                        FindItemResult gs = InvHelper.find(Items.GLOWSTONE);
                        if (!gs.found()) {
                            outOfGlowstone = true;
                            continue;
                        }
                        if (interactAnchorAt(pos, gs)) {
                            anchorMaintCooldown = delay(3);
                            anchorsChargedByUs.add(pos);
                        }
                        return;
                    }
                }
            }
        }
        anchorMaintCooldown = delay(1); // nichts gefunden - naechster voller Scan erst naechsten Tick statt jeden Tick doppelt
    }

    /** Erkennt, wenn der Gegner sich gerade aktiv selbst einmauert (nur noch eine von vier Seiten offen)
     *  und wirft sofort einen Anchor in genau diese Luecke, bevor der naechste Block sie schliesst - das
     *  Zeitfenster dafuer ist nur 1-2 Platzierungen lang, dafuer lohnt sich ein eigener Sofort-Check statt
     *  auf die normale, langsamere Anchor-Kandidatenwertung in selectAura() zu warten. Laden/Zuenden
     *  uebernimmt danach ganz normal maintainNearbyAnchors() (laeuft oben unabhaengig vom Aura-Modus).
     *  @return true, wenn diesen Tick ein Anchor in eine erkannte Luecke geworfen wurde. */
    private boolean interceptEnemyBoxing(LivingEntity target) {
        if (!useAnchors.get() || anchorMode.get() == 2) return false;

        BlockPos center = target.blockPosition();
        net.minecraft.core.Direction openSide = null;
        int openCount = 0;
        for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            if (mc.level.getBlockState(center.relative(dir)).isAir()) {
                openCount++;
                openSide = dir;
            }
        }
        // Genau eine offene Seite = der Gegner baut sich gerade aktiv ein und hat nur noch eine Wand
        // uebrig. Alle vier offen ist einfach freies Feld (keine Box im Bau), null offene ist schon
        // fertig versiegelt - dafuer gibt es hier nichts mehr zu retten (die normale Anchor-/Crystal-
        // Kandidatenwertung findet noch verbliebene Lecks, z.B. oben/unten, von selbst ueber die echte
        // Schadensberechnung).
        if (openCount != 1) return false;

        BlockPos gap = center.relative(openSide);
        double d = Math.sqrt(mc.player.distanceToSqr(Vec3.atCenterOf(gap)));
        if (d > 4.2) return false;

        FindItemResult anchor = InvHelper.find(Items.RESPAWN_ANCHOR);
        if (!anchor.found()) return false;
        if (totalItem(Items.GLOWSTONE) <= 0) return false;

        double selfDmg = DamageUtils.anchorDamage(mc.player, Vec3.atCenterOf(gap));
        if (selfDmg > maxSelfDamage.get() || !selfDamageAllowed(Vec3.atCenterOf(gap), selfDmg)) return false;

        if (placeTrackedBlock(gap, anchor, true, 50)) {
            currentAction = "box-luecke";
            return true;
        }
        return false;
    }

    /** @return true, wenn die Rotation+Interaktion tatsaechlich eingereiht wurde (Rotations-Slot frei war). */
    private boolean interactAnchorAt(BlockPos pos, FindItemResult item) {
        if (drinkingFireRes) return false;
        Vec3 center = Vec3.atCenterOf(pos);
        InteractionHand hand = item.isOffhand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        return queueWithCombatSlot(item, Rotations.getYaw(center), Rotations.getPitch(center), PRIORITY_ANCHOR,
            () -> BlockUtils.interact(new BlockHitResult(center, BlockUtils.getDirection(pos), pos, true), hand, true));
    }
    // ---------- Aura-Steuerung ----------

    /** Ein Respawn Anchor explodiert NUR ausserhalb des Nethers - dort ist er ein voll funktionsfaehiger
     *  Spawnpunkt-Block, das Aufladen und Benutzen setzt lediglich den Spawn und macht NULL Schaden
     *  (Minecraft Wiki, Respawn Anchor: "if the player attempts to set their spawn ... in the Overworld,
     *  the End, or custom dimensions in which they are disabled, the block explodes"). Ohne diese Pruefung
     *  waehlt die Aura im Nether einen Anchor-Kandidaten, platziert, laedt mit Glowstone, interagiert -
     *  und wundert sich, dass nie etwas passiert: explosionImminent() bleibt dabei die ganze Zeit true
     *  (bestAnchorDmgCache > 0, denn die Schadensformel kennt die Dimension nicht), was zusaetzlich den
     *  Nahkampf-Fallback blockiert. Der Bot steht also im Nether neben dem Gegner und fuehrt eine
     *  Waffe vor, die dort gar nicht existiert. */
    private boolean anchorsExplodeHere() {
        return mc.level != null && mc.level.dimension() != Level.NETHER;
    }

    /** Spiegelbild dazu: ein Bett explodiert nur AUSSERHALB der Oberwelt (Nether/End). In der Oberwelt
     *  ist es ein normales Moebelstueck - Platzieren und Benutzen setzt nur den Spawn. */
    private boolean bedsExplodeHere() {
        return mc.level != null && mc.level.dimension() != Level.OVERWORLD;
    }

    /** Zaehlt den TATSAECHLICHEN Ressourcenverbrauch ueber Inventar-Deltas (Crystal/Anchor/Bett), statt
     *  eigene Zuendungen zu zaehlen: Crystals werden von Meteors CrystalAura platziert und gezuendet, in
     *  diesem Modul gibt es dafuer gar keinen zentralen Aufrufpunkt - ein Zaehler auf unserer Seite waere
     *  systematisch zu niedrig und der Ausgleich damit dauerhaft schief. Nur Abnahmen zaehlen; ein
     *  Anstieg (Nachschub aus einer Kiste, Kit-Refill) setzt lediglich den Referenzwert neu. */
    private void trackResourceUsage() {
        int[] now = { totalItem(Items.END_CRYSTAL), totalItem(Items.RESPAWN_ANCHOR), totalItem(GodmodePvP::isBed) };
        for (int i = 0; i < 3; i++) {
            if (lastResourceCount[i] >= 0 && now[i] < lastResourceCount[i]) auraUsage[i] += lastResourceCount[i] - now[i];
            lastResourceCount[i] = now[i];
        }
    }

    /** Ausgleichs-Bonus (in HP-Schaden) fuer eine Explosiv-Option: je weiter ihr bisheriger Anteil am
     *  Gesamtverbrauch unter dem Gleichanteil liegt, desto groesser der Bonus - maximal
     *  balance-resources. Nur zwischen den gerade VERFUEGBAREN Optionen verteilt: fehlt z.B. das Bett
     *  (Overworld), waere ein Gleichanteil von 1/3 unerreichbar und Crystal/Anchor bekaemen beide
     *  dauerhaft Bonus - also ohne Wirkung, aber mit verzerrtem Bezugswert.
     *  Vor 20 verbrauchten Einheiten passiert nichts (zu kleine Stichprobe: die ersten paar Explosionen
     *  wuerden den Anteil sonst auf 100% treiben und die Wahl gegen die real bessere Option kippen). */
    private double balanceBonus(int mode, boolean hasCrystals, boolean hasAnchorItem, boolean hasBedItem) {
        double max = balanceResources.get();
        if (max <= 0) return 0;

        boolean[] avail = { hasCrystals, hasAnchorItem, hasBedItem };
        if (!avail[mode]) return 0;

        int options = 0, total = 0;
        for (int i = 0; i < 3; i++) {
            if (!avail[i]) continue;
            options++;
            total += auraUsage[i];
        }
        if (options < 2 || total < 20) return 0;

        double fairShare = 1.0 / options;
        double share = (double) auraUsage[mode] / total;
        if (share >= fairShare) return 0;
        return max * (fairShare - share) / fairShare;
    }

    private void selectAura(LivingEntity target) {
        // Ohne ein einziges Crystal UND ohne vollstaendige Anchor-Ausruestung (Anchor + Glowstone) UND ohne
        // Bett (falls aktiviert) gibt es schlicht nichts zu platzieren - die teure Damage-/Positions-
        // Simulation unten (Anchor-/Bett-Kandidaten-Scan, Crystal-Schadens-Suche) UND das wiederholte
        // An-/Ausschalten von Meteors CrystalAura liefen bisher trotzdem jeden Tick weiter, obwohl nie etwas
        // dabei rauskam - genau das erzeugte spuerbares Ruckeln/Stottern im Movement, waehrend "Platzierung"
        // nach aussen einfach nichts tat.
        boolean hasCrystals = hasActionableItem(Items.END_CRYSTAL);
        boolean hasAnchorItem = anchorsExplodeHere() && hasActionableItem(Items.RESPAWN_ANCHOR)
            && hasActionableItem(Items.GLOWSTONE);
        boolean hasBedItem = useBeds.get() && bedsExplodeHere() && hasActionableItem(GodmodePvP::isBed);
        Module ca = Modules.get().get(CrystalAura.class);
        if (!hasCrystals && !hasAnchorItem && !hasBedItem) {
            if (ca != null && ca.isActive()) ca.toggle();
            auraMode = -1;
            bestCrystalDmgCache = 0;
            bestAnchorDmgCache = 0;
            bestBedDmgCache = 0;
            bestBedRawDmgCache = 0;
            return;
        }

        Vec3 predicted = predict(target);
        double crystalDmg = hasCrystals ? bestDamageAround(target, predicted, true) : -1;
        bestCrystalDmgCache = crystalDmg;

        // Anchor-/Bett-Kandidaten anhand der AKTUELLEN Position berechnen (nicht der Vorhersage - der Bot
        // muss erst noch hinlaufen, eine extrapolierte Position waere bei schnellen/fliegenden Zielen
        // komplett daneben). Neu berechnen, wenn die alte Liste durch ist ODER sich das Ziel > 2 Bloecke
        // bewegt hat.
        if (hasAnchorItem && useAnchors.get() && anchorMode.get() != 2) {
            BlockPos targetBlock = target.blockPosition();
            boolean stale = anchorCandidateIndex >= anchorCandidates.size()
                || anchorCalcOrigin == null
                || anchorCalcOrigin.distSqr(targetBlock) > 4;
            if (stale) {
                calcBestAnchor(target, target.position());
                anchorCalcOrigin = targetBlock;
            }
        }

        bestAnchorDmgCache = 0;
        if (hasAnchorItem && anchorCandidateIndex < anchorCandidates.size()) {
            BlockPos best = anchorCandidates.get(anchorCandidateIndex);
            bestAnchorDmgCache = totemAdjustedDamage(target, DamageUtils.anchorDamage(target, Vec3.atCenterOf(best)));
        }

        if (hasBedItem) {
            BlockPos targetBlock = target.blockPosition();
            boolean staleBed = bedCandidateIndex >= bedCandidates.size()
                || bedCalcOrigin == null
                || bedCalcOrigin.distSqr(targetBlock) > 4;
            if (staleBed) {
                calcBestBed(target, target.position());
                bedCalcOrigin = targetBlock;
            }
        }

        bestBedDmgCache = 0;
        bestBedRawDmgCache = 0;
        if (hasBedItem && bedCandidateIndex < bedCandidates.size()) {
            BedSpot best = bedCandidates.get(bedCandidateIndex);
            bestBedRawDmgCache = DamageUtils.bedDamage(target, Vec3.atCenterOf(best.pos()));
            bestBedDmgCache = totemAdjustedDamage(target, bestBedRawDmgCache);
        }

        if (ca == null) return;

        // Anchor/Bett nur im Nahbereich - sonst Crystal, damit es nie totlaeuft
        boolean inRange = mc.player.distanceToSqr(target) < 5.5 * 5.5;

        // Anchor-Plaetze unerreichbar? -> 2 s Crystal erzwingen (Anti-Stuck)
        boolean anchorForced = tickCounter < crystalForcedUntil;

        // Ressourcen-Ausgleich: liegen zwei Optionen im Schaden dicht beieinander, bekommt die bisher
        // WENIGER genutzte einen kleinen Bonus (siehe balance-resources). Ohne das gewann Crystal
        // praktisch jeden knappen Vergleich und die Betten blieben faktisch ungenutzt (live 339:99).
        // Bewusst NUR als Summand in denselben Vergleichen wie bisher - alle harten Gates (use-anchors,
        // anchor-mode 2, Glowstone, Reichweite, bed-min-damage, Eigenschaden) bleiben unberuehrt.
        double bonusCrystal = balanceBonus(0, hasCrystals, hasAnchorItem, hasBedItem);
        double bonusAnchor = balanceBonus(1, hasCrystals, hasAnchorItem, hasBedItem);
        double bonusBed = balanceBonus(2, hasCrystals, hasAnchorItem, hasBedItem);

        // Hysterese statt scharfer Schwelle: zum Wechsel IN den Anchor-/Bett-Modus braucht es einen klaren
        // Vorsprung, zum Bleiben reicht Gleichstand. Ohne das kippt der Modus bei jedem winzigen
        // Schadens-Unterschied (z.B. durch die Ziel-Vorhersage) mehrfach pro Sekunde hin und her -
        // jedes Mal ein voller CrystalAura-Neustart, der wie ein Ruckeln/Haken wirkt. Die Marge NICHT
        // aggressiv abzusenken ist live belegt: mit halbierter Umschalt-Traegheit und weggefallener
        // Eintritts-Marge fielen die Gesamt-Explosionen pro 90s von 438 auf 377 - haeufigeres Umschalten
        // heisst mehr CrystalAura-Neustarts, also weniger tatsaechliche Zuendungen statt mehr.
        double margin = anchorMode.get() == 1 ? 0.0 : 0.15;
        double enterMargin = auraMode == 1 ? -0.3 : margin;

        boolean wantAnchor;
        if (!hasAnchorItem) {
            wantAnchor = false;
        } else if (anchorMode.get() == 1) {
            // Anchor schon bei Gleichstand (bricht Schilde)
            wantAnchor = !outOfGlowstone && !anchorForced && inRange
                && anchorCandidateIndex < anchorCandidates.size()
                && bestAnchorDmgCache + bonusAnchor >= crystalDmg + bonusCrystal + enterMargin;
        } else {
            wantAnchor = !outOfGlowstone && !anchorForced && inRange
                && bestAnchorDmgCache + bonusAnchor > crystalDmg + bonusCrystal + 0.15 + enterMargin;
        }

        // syncSupport() konnte CrystalAuras Obsidian-Auto-Unterbau nicht erzwingen -> Crystal-Modus ist
        // ueber freier Luft (validExplosionSpot) faktisch nicht mehr voll funktionsfaehig. Anchor braucht
        // dafuer keinen Support-Mechanismus (eigene Platzierungslogik), also aktiv bevorzugen statt sich
        // weiter auf einen angeschlagenen Crystal-Modus zu verlassen - solange ueberhaupt ein gueltiger
        // Anchor-Kandidat mit positivem Schaden gefunden wurde.
        if (supportSyncFailed && hasAnchorItem && !outOfGlowstone && !anchorForced && inRange
            && anchorCandidateIndex < anchorCandidates.size() && bestAnchorDmgCache > 0) {
            wantAnchor = true;
        }

        // Bett: gleiche Hysterese-Logik wie Anchor-Automatik (kein eigener Tie-Break-Modus - use-beds ist
        // ein simpler On/Off-Schalter, siehe Beschreibung).
        double bedEnterMargin = auraMode == 2 ? -0.3 : 0.15;
        boolean wantBed = hasBedItem && !anchorForced && inRange && bedCandidateIndex < bedCandidates.size()
            && bestBedRawDmgCache >= bedMinDamage.get()
            && bestBedDmgCache + bonusBed > crystalDmg + bonusCrystal + bedEnterMargin;

        // Wenn beide verfuegbar waeren, gewinnt die schadenstaerkere Option - inklusive Ausgleichs-Bonus,
        // sonst wuerde der Ausgleich hier direkt wieder wegsortiert.
        if (wantAnchor && wantBed) {
            if (bestBedDmgCache + bonusBed > bestAnchorDmgCache + bonusAnchor) wantAnchor = false;
            else wantBed = false;
        }

        // Deutlich seltener umschalten als der Rohwert - genug Zeit, damit eine begonnene Platzierung/
        // Ladung auch tatsaechlich fertig wird, statt staendig unterbrochen zu werden. Skaliert mit der
        // Zielgeschwindigkeit statt eines fixen Werts: ein schnell bewegtes Ziel veraltet die Anchor-/
        // Bett-/Crystal-Bewertung viel schneller als ein stehendes, verdient also eine kuerzere Sperre.
        // Bewusst NICHT vom Aggressiv-Profil verkuerzt - live gemessen war genau das ein Rueckschritt
        // (siehe Kommentar bei 'margin' oben): weniger Traegheit heisst mehr CrystalAura-Neustarts und
        // damit WENIGER Explosionen pro Sekunde, nicht mehr.
        // Waehrend eines aktiven Kombo-Fensters (popBurstUntil, siehe trackPop) komplett umgangen - beim
        // Nachschlag auf einen frisch erkannten Totem-Pop zaehlt jeder Tick, nicht erst der naechste
        // freie Umschalt-Slot.
        if (tickCounter >= popBurstUntil) {
            double targetSpeed = velocities.getOrDefault(target.getUUID(), Vec3.ZERO).length();
            int hysteresis = (int) Math.max(3, 10 - targetSpeed * 4);
            if (tickCounter - lastAuraSwitch < hysteresis) return;
        }

        if (wantAnchor && auraMode != 1) {
            if (ca.isActive()) ca.toggle();
            auraMode = 1;
            lastAnchorProgressTick = tickCounter;
            lastAuraSwitch = tickCounter;
        } else if (wantBed && auraMode != 2) {
            if (ca.isActive()) ca.toggle();
            auraMode = 2;
            lastBedProgressTick = tickCounter;
            lastAuraSwitch = tickCounter;
        } else if (!wantAnchor && !wantBed && auraMode != 0) {
            if (!ca.isActive()) ca.toggle();
            auraMode = 0;
            lastAuraSwitch = tickCounter;
        }
    }

    /** Nur schlagen, wenn wir aktiv im Anchor-, Bett- oder Crystal-Angriff auf dieses Ziel stecken - kein
     *  Hieb ins Blaue. Absichtlich NICHT an den genauen Anchor-/Bett-Stage (Platzierung/Ladung/Zuendung im
     *  Detail) gekoppelt - das waere zu zerbrechlich. Aber "gerade in diesem Aura-Modus" ALLEIN reicht
     *  ebenfalls nicht: Anchor/Bett blieb dann auch OHNE JEDEN gueltigen Kandidaten (z.B. Ziel gerade
     *  ueber Wasser/Void gewebt, alle Kandidaten durch Entities/Lava/fehlende Sicht ausgeschlossen)
     *  fuer explosionImminent()==true haengen, was melee-fallback (erfordert !explosionImminent)
     *  komplett und dauerhaft blockierte, obwohl der Gegner voll treffbar direkt daneben stand - genau
     *  der Bug, den der bestCrystalDmgCache-Check unten fuer den Crystal-Fall schon damals loeste, hier
     *  aber nie mit ausgerollt wurde. bestAnchorDmgCache/bestBedDmgCache > 0 heisst: der letzte Scan
     *  (selectAura(), laeuft davor im selben Tick) hat wirklich einen machbaren Kandidaten gefunden. */
    private boolean explosionImminent(LivingEntity target) {
        if (auraMode == 1) return bestAnchorDmgCache > MIN_MEANINGFUL_DAMAGE;
        if (auraMode == 2) return bestBedDmgCache > MIN_MEANINGFUL_DAMAGE;
        if (auraMode == 0) {
            // ca.isActive() allein reicht nicht - das Modul kann eingeschaltet sein, aber ohne Obsidian fuer den
            // Support-Unterbau (oder ohne jeden gueltigen Platzierungs-Kandidaten) faktisch nie explodieren.
            // bestCrystalDmgCache > 0 heisst: der letzte Scan hat wirklich eine machbare Stelle gefunden.
            Module ca = Modules.get().get(CrystalAura.class);
            return ca != null && ca.isActive() && bestCrystalDmgCache > MIN_MEANINGFUL_DAMAGE;
        }
        return false;
    }

    /** Prueft, ob das Ziel gerade einen Totem in der Offhand bereit haelt. */
    private boolean targetHasTotemReady(LivingEntity target) {
        return target.getOffhandItem().is(Items.TOTEM_OF_UNDYING);
    }

    /** Bewertet eine nicht-toedliche Explosion gegen einen Totem-bereiten Gegner niedriger, statt sie
     *  wie jede andere Explosion voll zu werten - sonst "lohnt" sich jede Zuendung gegen einen Dauer-
     *  Nachschub-Gegner gleich viel wie ein echter Treffer, was Crystals/Anchors/Betten sinnlos
     *  verballert, ohne den Kampf fortzubewegen (der Gegner poppt einfach den naechsten Totem). Kein
     *  hartes Gate (0) - eine Explosion gegen einen Totem-Traeger ist immer noch besser als gar keine:
     *  sie zehrt seinen Totem-Vorrat auf UND ist ueberhaupt erst die Voraussetzung fuer das Kombo-Fenster
     *  (popBurstUntil, siehe trackPop) - waehrend eines laufenden Kombo-Fensters zaehlt der volle Wert.
     */
    private double totemAdjustedDamage(LivingEntity target, double dmg) {
        if (dmg <= 0) return dmg;
        if (dmg >= target.getHealth()) return dmg; // toedlich -> Totem-Frage irrelevant
        if (tickCounter < popBurstUntil) return dmg; // schon im Kombo-Fenster -> das IST der Nachschlag
        if (!targetHasTotemReady(target)) return dmg;
        return dmg * 0.3;
    }

    private double bestDamageAround(LivingEntity target, Vec3 center, boolean crystal) {
        int bx = (int) Math.floor(center.x);
        int by = (int) Math.floor(center.y);
        int bz = (int) Math.floor(center.z);

        double best = 0;

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    Vec3 pos = new Vec3(bx + dx + 0.5, by + dy, bz + dz + 0.5);
                    BlockPos cell = new BlockPos(bx + dx, by + dy, bz + dz);

                    if (!validExplosionSpot(cell, crystal)) continue;
                    if (hitsFriend(pos, crystal)) continue;

                    double selfDmg = crystal
                        ? DamageUtils.crystalDamage(mc.player, pos)
                        : DamageUtils.anchorDamage(mc.player, pos);
                    if (selfDmg > maxSelfDamage.get() || !selfDamageAllowed(pos, selfDmg)) continue;

                    double dmg = crystal
                        ? DamageUtils.crystalDamage(target, pos)
                        : DamageUtils.anchorDamage(target, pos);
                    dmg = totemAdjustedDamage(target, dmg);
                    if (dmg > best) best = dmg;
                }
            }
        }
        return best;
    }

    /** Alle gueltigen Anchor-Plaetze um das Ziel, sortiert nach Schaden (absteigend). */
    private void calcBestAnchor(LivingEntity target, Vec3 center) {
        anchorCandidates.clear();
        anchorCandidateIndex = 0;

        int bx = (int) Math.floor(center.x);
        int by = (int) Math.floor(center.y);
        int bz = (int) Math.floor(center.z);

        java.util.List<BlockPos> found = new java.util.ArrayList<>();
        java.util.List<Double> dmgs = new java.util.ArrayList<>();

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    Vec3 pos = new Vec3(bx + dx + 0.5, by + dy, bz + dz + 0.5);
                    BlockPos cell = new BlockPos(bx + dx, by + dy, bz + dz);

                    if (!validExplosionSpot(cell, false)) continue;
                    if (hitsFriend(pos, false)) continue;

                    AABB cellBox = new AABB(cell);
                    if (target.getBoundingBox().intersects(cellBox)) continue;
                    if (mc.player.getBoundingBox().intersects(cellBox)) continue;

                    double selfDmg = DamageUtils.anchorDamage(mc.player, pos);
                    if (selfDmg > maxSelfDamage.get() || !selfDamageAllowed(pos, selfDmg)) continue;

                    double dmg = DamageUtils.anchorDamage(target, pos);
                    if (dmg <= 0) continue;

                    found.add(cell);
                    dmgs.add(dmg);
                }
            }
        }

        // Stabile Sortierung mit deterministischem Tie-Break (BlockPos-Hash) statt manuellem Bubble-Sort
        // ohne Tie-Break - bei exakt gleichem Schaden konnten zwei Kandidaten sonst bei aufeinander-
        // folgenden Neuberechnungen ihre Position tauschen und so unentschlossen zwischen zwei
        // gleichwertigen Optionen hin- und herwechseln, statt konsequent bei einer zu bleiben.
        java.util.List<Integer> order = new java.util.ArrayList<>();
        for (int i = 0; i < found.size(); i++) order.add(i);
        order.sort((a, b) -> {
            int cmp = Double.compare(dmgs.get(b), dmgs.get(a));
            return cmp != 0 ? cmp : Integer.compare(found.get(a).hashCode(), found.get(b).hashCode());
        });
        for (int i : order) anchorCandidates.add(found.get(i));
    }

    /** Crystal: Obsidian/Bedrock-Basis ODER offene Luft-Tasche (CrystalAura setzt dort im Support-Modus
     *  selbst Obsidian als Unterlage). Anchor: fester Boden, Luft darueber. */
    private boolean validExplosionSpot(BlockPos cell, boolean crystal) {
        if (mc.level == null) return false;
        if (!mc.level.getBlockState(cell).isAir()) return false;
        if (avoidLava.get() && isNearLava(cell)) return false;
        if (!hasRaycastLineOfSight(Vec3.atCenterOf(cell))) return false;

        BlockState below = mc.level.getBlockState(cell.below());
        if (crystal) {
            if (below.is(Blocks.OBSIDIAN) || below.is(Blocks.BEDROCK)) return true;
            // Freie Luft als Unterlage ist nur gueltig, wenn tatsaechlich noch Obsidian zum
            // Unterbauen da ist - sonst waehlt bestDamageAround() eine Stelle, die CrystalAura nie
            // wirklich bespielen kann. Das liess bestCrystalDmgCache faelschlich > 0 stehen, was
            // explosionImminent() "true" zurueckgeben und melee-fallback komplett blockieren liess,
            // obwohl der Bot regungslos neben einem voll treffbaren Gegner stand.
            return below.isAir() && (InvHelper.has(Items.OBSIDIAN));
        }
        return below.blocksMotion() && mc.level.getBlockState(cell.above()).isAir();
    }

    /** Zelle selbst oder einer der 6 Nachbarn ist Lava - typisch fuer Nether-Seen/Bedrock-Pools. Verhindert,
     *  dass eine Explosion dort eine Feuerflut auslaesst oder der Bot direkt neben kochender Lava landet. */
    private boolean isNearLava(BlockPos cell) {
        if (mc.level.getBlockState(cell).is(Blocks.LAVA)) return true;
        for (Direction dir : Direction.values()) {
            if (mc.level.getBlockState(cell.relative(dir)).is(Blocks.LAVA)) return true;
        }
        return false;
    }

    /** Zaehlt Bloecke in einem Wuerfel um 'center' (Kantenlaenge 2*radius+1), die 'matcher' erfuellen -
     *  fuer die Anchor-/Bett-Delta-Erkennung von auto-shield (Blocks statt Entities, sonst dieselbe
     *  Idee wie die bestehende EndCrystal-Entity-Zaehlung direkt darueber). */
    private int countNearbyBlocks(BlockPos center, int radius, java.util.function.Predicate<BlockState> matcher) {
        int count = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (matcher.test(mc.level.getBlockState(center.offset(dx, dy, dz)))) count++;
                }
            }
        }
        return count;
    }

    private static final Direction[] BED_DIRECTIONS = { Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST };

    /** Zieldistanz, ab der eine Bett-Platzierung ueberhaupt self-safe moeglich ist (live ermittelt:
     *  unter ~4.5 Bloecken liegt jede erreichbare Zelle im eigenen Explosionsradius). */
    private static final double BED_STANDOFF_DIST = 4.6;

    /** Baritone-Verfolgungsradius im Normalfall (Crystal/Anchor/Nahkampf) bzw. wenn Betten die einzige
     *  Explosiv-Option sind - im Bett-Fall bewusst groesser, damit der Bot ueberhaupt auf einer Distanz
     *  stehen bleibt, auf der eine self-safe Bett-Platzierung existiert (siehe handleOffense). */
    private static final int DEFAULT_FOLLOW_RADIUS = 3;
    private static final int BED_FOLLOW_RADIUS = 5;

    /** Toleranz fuer den LoS-Block-Raycast (hasRaycastLineOfSight): ein Treffer NAEHER als dieser Abstand
     *  zum eigentlichen Zielpunkt zaehlt noch als "erreicht", nicht als blockiert. Kein exakter MISS
     *  ist der Normalfall an einer Kandidaten-Zelle, die direkt an einer Wand/Kante liegt (Slab-Stufe,
     *  Zaun, angewinkelte Treppe) - der Raycast trifft dann die Nachbarflaeche statt exakt durchzugehen.
     *  0.6 Bloecke (bisher als Magic Number inline) deckt diesen "am Rand liegt"-Fall ab, ohne echte
     *  Deckung (eine ganze Wand dazwischen, deutlich groesserer Rest-Abstand) durchzulassen - Wert bewusst
     *  UNVERAENDERT gelassen (kein Live-Tuning-Beleg fuer einen anderen Wert), nur benannt/dokumentiert.
     */
    private static final double LOS_RAYCAST_TOLERANCE = 0.6;

    /** Reichweite, in der eine Bett-Platzierung real ausgefuehrt werden kann (identisch zur Pruefung in
     *  tryPlaceBed) - Kandidaten ausserhalb davon gehoeren gar nicht erst in die Liste, sonst blockieren
     *  sie den Index und laufen erst nach 8 Warte-Ticks pro Stueck durch. */
    private static final double BED_PLACE_REACH = 4.2;

    /** Alle gueltigen Bett-Plaetze um das Ziel, sortiert nach NETTO-Vorteil (Zielschaden minus
     *  Eigenschaden, absteigend). Ein Bett braucht anders als Crystal/Anchor KEINE feste Unterlage
     *  (nur zwei freie, ersetzbare Bloecke: Fuss- + Kopfteil in eine der vier Himmelsrichtungen) -
     *  wirkt aber nur ausserhalb der Overworld (Nether/End); das kann der Client nicht vorab pruefen.
     *
     *  Suchradius horizontal +-2 statt +-1 (Live-Messung): eine Bett-Explosion macht im Nahbereich
     *  ~50 Schaden, also liegt bei einem +-1-Wuerfel um ein Ziel, das im Nahkampf 2-4 Bloecke vor
     *  einem steht, JEDE Kandidatenzelle auch fuer einen selbst im vollen Explosionsradius - der
     *  Eigenschaden-Deckel (max-self-damage) hat dann restlos alle Kandidaten verworfen (live
     *  gemessen: cands=0 bei dist 2.6-4.3, Bed Aura feuerte nur in den seltenen Momenten mit
     *  dist > 4.6). Mit +-2 existieren Zellen auf der ABGEWANDTEN Seite des Ziels: volles Schadens-
     *  potential am Gegner, deutlich weniger am eigenen Koerper. Die Sortierung nach Netto-Vorteil
     *  waehlt genau diese Zellen zuerst, statt nur nach rohem Zielschaden zu gehen (der ist am
     *  gefaehrlichsten Platz - direkt zwischen beiden - naturgemaess am hoechsten). */
    private void calcBestBed(LivingEntity target, Vec3 center) {
        bedCandidates.clear();
        bedCandidateIndex = 0;

        int bx = (int) Math.floor(center.x);
        int by = (int) Math.floor(center.y);
        int bz = (int) Math.floor(center.z);

        java.util.List<BedSpot> found = new java.util.ArrayList<>();
        java.util.List<Double> scores = new java.util.ArrayList<>();

        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    BlockPos foot = new BlockPos(bx + dx, by + dy, bz + dz);
                    if (!validBedCell(foot)) continue;

                    Vec3 pos = Vec3.atCenterOf(foot);
                    // Nur Zellen, die wir real anklicken koennen - siehe BED_PLACE_REACH.
                    if (mc.player.distanceToSqr(pos) > BED_PLACE_REACH * BED_PLACE_REACH) continue;

                    Direction dir = findFreeBedDirection(foot);
                    if (dir == null) continue;
                    BlockPos head = foot.relative(dir);

                    if (hitsFriendBed(pos)) continue;

                    AABB footBox = new AABB(foot);
                    AABB headBox = new AABB(head);
                    // Beide Zellen pruefen (nicht nur das Fussteil) - das Kopfteil liegt oft direkt dort,
                    // wo Ziel oder wir selbst gerade stehen (Nahkampf-Distanz), und ein durch eine Entity
                    // blockiertes Kopfteil laesst den Server die Platzierung ablehnen, waehrend der Client
                    // das Bett trotzdem schon als verbraucht behandelt hat (Referenz: BlackOut BedAura+
                    // prueft explizit `pos.offset(dir)` zusaetzlich zum Fussteil - Ursache des gemeldeten
                    // "Betten droppen dauernd").
                    if (target.getBoundingBox().intersects(footBox) || target.getBoundingBox().intersects(headBox)) continue;
                    if (mc.player.getBoundingBox().intersects(footBox) || mc.player.getBoundingBox().intersects(headBox)) continue;

                    double selfDmg = DamageUtils.bedDamage(mc.player, pos);
                    if (!bedSelfDamageAcceptable(selfDmg)) continue;
                    if (!selfDamageAllowed(pos, selfDmg)) continue;

                    double dmg = DamageUtils.bedDamage(target, pos);
                    if (dmg <= 0) continue;

                    found.add(new BedSpot(foot, dir));
                    scores.add(dmg - selfDmg);
                }
            }
        }

        // Stabile Sortierung mit deterministischem Tie-Break (BedSpot-Position-Hash) - siehe
        // calcBestAnchor() fuer dieselbe Begruendung (kein Hin- und Herwechseln bei Gleichstand).
        java.util.List<Integer> order = new java.util.ArrayList<>();
        for (int i = 0; i < found.size(); i++) order.add(i);
        order.sort((a, b) -> {
            int cmp = Double.compare(scores.get(b), scores.get(a));
            return cmp != 0 ? cmp : Integer.compare(found.get(a).pos().hashCode(), found.get(b).pos().hashCode());
        });
        for (int i : order) bedCandidates.add(found.get(i));
    }

    /** Bett braucht keine feste Unterlage (Java Edition erlaubt frei schwebende Betten) - aber die
     *  Platzierung selbst laeuft ueber einen rechtsklickbaren Nachbarblock (BlockUtils.place() faellt
     *  sonst auf einen "klickt sich selbst an"-Nottrick zurueck, der bei Betten am Server unzuverlaessig
     *  angenommen/abgelehnt wird - genau das liess das Item ohne sichtbaren Blockaufbau aus dem Inventar
     *  verschwinden bzw. droppen). Deshalb: mindestens EIN echter, klickbarer Nachbarblock muss existieren.
     */
    private boolean validBedCell(BlockPos cell) {
        if (mc.level == null) return false;
        if (!mc.level.getBlockState(cell).isAir()) return false;
        if (BlockUtils.getPlaceSide(cell) == null) return false;
        if (!hasRaycastLineOfSight(Vec3.atCenterOf(cell))) return false;
        return !(avoidLava.get() && isNearLava(cell));
    }

    /** Echter Block-Raycast (nicht nur Distanz/Blockstate) zwischen Augenposition und einem Kandidaten-
     *  Mittelpunkt - ohne das wurden auch Zellen "gueltig", die real durch eine Wand/Deckung hindurch
     *  gar nicht treffbar sind (reine Distanz-/Blockstate-Pruefung sieht Mauern dazwischen nicht). MISS
     *  = freie Sicht; ein Treffer nahe genug am Zielpunkt selbst (z.B. die Zielzelle grenzt direkt an
     *  eine Wand) zaehlt noch als "erreicht", nicht als blockiert.
     */
    private boolean hasRaycastLineOfSight(Vec3 point) {
        if (mc.level == null || mc.player == null) return true;
        // through-walls: die Sichtlinie ist eine reine Selbstbeschraenkung des Clients - der Server
        // prueft beim Platzieren nur die Distanz. Faellt sie weg, sind auch Plaetze hinter Deckung
        // (andere Seite einer Wand, im Loch des Gegners) wieder gueltige Kandidaten.
        if (throughWalls.get()) return true;
        Vec3 eye = mc.player.getEyePosition();
        ClipContext ctx = new ClipContext(eye, point, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player);
        BlockHitResult result = mc.level.clip(ctx);
        return result.getType() == HitResult.Type.MISS || result.getLocation().distanceTo(point) < LOS_RAYCAST_TOLERANCE;
    }

    /** Explosions-Splash wird bei aktivem through-walls nicht nur ueber die Hitbox-Mitte bewertet.
     *  Mehrere Punkte der AABB werden geprueft; ist irgendein Punkt der Box fuer den Explosionsstrahl
     *  exponiert, gilt die Position als selbstschadengefaehrlich. */
    private boolean selfDamageAllowed(Vec3 explosionPos, double selfDamage) {
        if (selfDamage <= 0 || mc.level == null || mc.player == null) return true;
        AABB box = mc.player.getBoundingBox();
        return blastRayClear(explosionPos, box.getCenter())
            || blastRayClear(explosionPos, new Vec3(box.minX, box.minY, box.minZ))
            || blastRayClear(explosionPos, new Vec3(box.maxX, box.minY, box.minZ))
            || blastRayClear(explosionPos, new Vec3(box.minX, box.maxY, box.minZ))
            || blastRayClear(explosionPos, new Vec3(box.maxX, box.maxY, box.minZ))
            || blastRayClear(explosionPos, new Vec3(box.minX, box.minY, box.maxZ))
            || blastRayClear(explosionPos, new Vec3(box.maxX, box.minY, box.maxZ))
            || blastRayClear(explosionPos, new Vec3(box.minX, box.maxY, box.maxZ))
            || blastRayClear(explosionPos, new Vec3(box.maxX, box.maxY, box.maxZ));
    }

    private boolean blastRayClear(Vec3 explosionPos, Vec3 hitPoint) {
        ClipContext context = new ClipContext(explosionPos, hitPoint,
            ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player);
        BlockHitResult result = mc.level.clip(context);
        return result.getType() == HitResult.Type.MISS
            || result.getLocation().distanceTo(hitPoint) < 0.15;
    }

    private boolean crystalPlacementSafe(Player self, BlockPos cell, double selfDamageScale) {
        if (!validExplosionSpot(cell, true) || hitsFriend(Vec3.atCenterOf(cell), true)) return false;
        double selfDamage = DamageUtils.crystalDamage(self, Vec3.atCenterOf(cell));
        return selfDamage <= maxSelfDamage.get() * selfDamageScale
            && selfDamageAllowed(Vec3.atCenterOf(cell), selfDamage);
    }

    /** Erste Himmelsrichtung, in der neben dem Fussteil noch eine zweite freie Zelle fuer das Kopfteil
     *  liegt - das Bett wird spaeter exakt in diese Richtung ausgerichtet platziert. */
    private Direction findFreeBedDirection(BlockPos foot) {
        for (Direction dir : BED_DIRECTIONS) {
            if (validBedCell(foot.relative(dir))) return dir;
        }
        return null;
    }

    /** Vermeidet Bett-Explosionen, die einen befreundeten Spieler (Meteor-Friends-Liste) mittreffen wuerden. */
    private boolean hitsFriendBed(Vec3 pos) {
        if (!respectFriends.get() || Friends.get().isEmpty()) return false;
        for (Player p : mc.level.players()) {
            if (p == mc.player) continue;
            if (!Friends.get().isFriend(p)) continue;
            if (DamageUtils.bedDamage(p, pos) > 2.0) return true;
        }
        return false;
    }

    /** Platziert ein NEUES Bett am aktuell besten berechneten Kandidaten (Schadensoptimiert). Reine
     *  Platzierungs-Entscheidung - zuenden uebernimmt maintainNearbyBeds() separat, damit ein platziertes
     *  Bett nie unfertig liegen bleibt. Anders als beim Anchor gibt es keinen Ladeschritt: ein Bett
     *  explodiert (falls die Position/Dimension es zulaesst) sofort beim ersten Interagieren. */
    private void tryPlaceBed() {
        if (bedPlaceCooldown > 0) {
            bedPlaceCooldown--;
            return;
        }

        Player self = mc.player;
        BedSpot spot = nextBedCandidate();
        if (spot == null) {
            bedPlaceFails++;
            if (bedPlaceFails >= 2) crystalForcedUntil = tickCounter + 40;
            bedUnreachableTicks = 0;
            return;
        }

        double d = Math.sqrt(self.distanceToSqr(Vec3.atCenterOf(spot.pos())));
        if (d > 4.2) {
            // Kandidat gerade unerreichbar (z.B. Baritone stoppt im Nahkampf) - nicht endlos auf denselben Platz warten
            bedUnreachableTicks++;
            if (bedUnreachableTicks > 8) {
                bedCandidateIndex++;
                bedUnreachableTicks = 0;
                if (bedCandidateIndex >= bedCandidates.size()) crystalForcedUntil = tickCounter + 40;
            }
            return; // naechster Tick neuer Versuch
        }
        bedUnreachableTicks = 0;


        FindItemResult foundBed = InvHelper.find(GodmodePvP::isBed);
        if (!foundBed.found()) return;
        FindItemResult bed = foundBed;

        // Eigene Rotation VOR der Platzierung setzen (statt BlockUtils' rotate=true) - die Ausrichtung des
        // Kopfteils richtet sich nach der horizontalen Blickrichtung zum Platzierungszeitpunkt, nicht nach
        // der angeklickten Blockseite. dir.toYRot() ist exakt die Umkehrung von Direction.fromYRot().
        double yaw = spot.dir().toYRot();
        rotateAndRun(yaw, 55, PRIORITY_BED, () -> {
            if (placeTrackedBlock(spot.pos(), bed, false, 50)) {
                bedPlaceFails = 0;
                bedPlaceCooldown = delay(4); // kurze Pause, damit maintainNearbyBeds Zeit zum Zuenden hat
                lastBedProgressTick = tickCounter;
                // Besitz merken, sonst haelt anti-bed das eigene Bett fuer einen gegnerischen Bett-Bomber
                // und reisst es wieder ab, bevor es gezuendet werden kann.
                bedsPlacedByUs.add(spot.pos());
                bedsPlacedByUs.add(spot.pos().relative(spot.dir()));
            } else if (!combatSlotBusyFor(bed)) {
                bedCandidateIndex++;
            }
        });
    }

    private BedSpot nextBedCandidate() {
        while (bedCandidateIndex < bedCandidates.size()) {
            BedSpot s = bedCandidates.get(bedCandidateIndex);
            if (mc.level.getBlockState(s.pos()).isAir()) return s;
            bedCandidateIndex++;
        }
        return null;
    }

    /** Scannt kontinuierlich (unabhaengig vom Aura-Modus) den Nahbereich nach JEDEM platzierten Bett -
     *  egal von wem/wann platziert - und zuendet es sofort per Rechtsklick. Anders als beim Anchor gibt es
     *  keinen Ladezustand zu pruefen: die Explosion (falls Position/Dimension sie ueberhaupt zulassen)
     *  loest beim allerersten Interagieren aus. */
    private void maintainNearbyBeds() {
        if (!useBeds.get() || !bedsExplodeHere()) return; // in der Oberwelt setzt der Rechtsklick nur den Spawn
        if (bedMaintCooldown > 0) {
            bedMaintCooldown--;
            return;
        }

        Player self = mc.player;
        BlockPos center = self.blockPosition();
        for (int dx = -3; dx <= 3; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -3; dz <= 3; dz++) {
                    BlockPos pos = center.offset(dx, dy, dz);
                    if (!(mc.level.getBlockState(pos).getBlock() instanceof BedBlock)) continue;

                    Vec3 posCenter = Vec3.atCenterOf(pos);
                    if (Math.sqrt(self.distanceToSqr(posCenter)) > 4.2) continue;

                    double selfDmg = DamageUtils.bedDamage(mc.player, posCenter);
                    if (!bedSelfDamageAcceptable(selfDmg) || !selfDamageAllowed(posCenter, selfDmg)) continue;

                    if (interactBedAt(pos)) bedMaintCooldown = delay(3);
                    return;
                }
            }
        }
        bedMaintCooldown = delay(1); // nichts gefunden - naechster voller Scan erst naechsten Tick statt jeden Tick doppelt
    }

    /** @return true, wenn die Rotation+Interaktion tatsaechlich eingereiht wurde (Rotations-Slot frei war). */
    private boolean interactBedAt(BlockPos pos) {
        if (drinkingFireRes) return false; // s.o. - Swap-Merkposten waehrend des Trinkens nicht anfassen
        Vec3 center = Vec3.atCenterOf(pos);
        return rotateAndRun(Rotations.getYaw(center), Rotations.getPitch(center), PRIORITY_BED, () ->
            BlockUtils.interact(new BlockHitResult(center, BlockUtils.getDirection(pos), pos, true), InteractionHand.MAIN_HAND, true)
        );
    }

    /** Eigenschaden-Freigabe fuer Bett-Explosionen: eigener (hoeherer) Deckel als Crystal/Anchor, plus
     *  harter Selbstmord-Schutz. Bett-PvP ist bewusst ein Trade - der Bot nimmt Schaden in Kauf und
     *  faengt ihn mit Totem/Heiltrank ab - aber eine Zuendung, die die eigenen AKTUELLEN HP (inkl.
     *  Absorption) toeten wuerde, ist nie ein guter Trade und wird unabhaengig vom eingestellten
     *  Deckel verworfen (entspricht Meteors BedAura 'anti-suicide'). */
    private boolean bedSelfDamageAcceptable(double selfDmg) {
        if (selfDmg > bedMaxSelfDamage.get()) return false;
        double effectiveHp = mc.player.getHealth() + mc.player.getAbsorptionAmount();
        return selfDmg < effectiveHp - 1.0;
    }

    private static boolean isBed(ItemStack stack) {
        return stack.getItem() instanceof BedItem;
    }

    /** Zentrale Stelle fuer alle kuenstlichen Wartezeiten: liefert `ticks` normal, oder 0 wenn der
     *  Sofort-Modus (no-delay) aktiv ist. So bleibt jede einzelne Cooldown-Stelle im Code weiterhin
     *  lesbar (die "normale" Wartezeit steht direkt daneben), aber no-delay hebelt sie zentral aus. */
    private int delay(int ticks) {
        if (instantMode.get()) return 0;
        // Aggressiv-Profil: halbe Wartezeit statt gar keiner. no-delay auf 0 zu setzen hat im Test
        // Aktionen schneller abgefeuert, als der Server sie bestaetigt (verlorene Betten/Perlen);
        // die Haelfte verdoppelt die Aktionsrate, laesst aber jeder Platzierung noch eine
        // Server-Bestaetigung Zeit.
        return aggressive.get() ? Math.max(1, ticks / 2) : ticks;
    }

    /** Reiht eine Rotation+Aktion ein. Anders als vorher (ein starrer 1-Aktion-pro-Tick-Mutex, der
     *  jeden weiteren rotateAndRun()-Aufruf im selben Tick komplett auf den naechsten Tick verschob)
     *  koennen jetzt mehrere UNABHAENGIGE Aktionen pro Tick korrekt ausgerichtet feuern - Meteors
     *  Rotations-Klasse unterstuetzt das nativ: der ERSTE Eintrag eines Ticks laeuft ueber den
     *  normalen Bewegungspaket-Pfad (echte Rotation wird gesetzt), JEDER WEITERE bekommt mit
     *  clientSide=true ebenfalls kurzzeitig die echte Rotation gesetzt - exakt fuer die Dauer seines
     *  eigenen Callbacks (siehe Rotations.onSendMovementPacketsPost), danach zurueckgesetzt. Gibt
     *  jetzt immer true zurueck - bestehende Aufrufer, die frueher auf false pruefen mussten,
     *  funktionieren unveraendert weiter (der Erfolgsfall greift jetzt einfach immer). */
    private boolean rotateAndRun(double yaw, double pitch, int priority, Runnable callback) {
        Rotations.rotate(yaw, pitch, priority, rotationsThisTick > 0, callback);
        rotationsThisTick++;
        return true;
    }

    /** Harter Mainhand-Mutex fuer Combat-Aktionen. Baritone/Follow/CustomGoal werden waehrend der
     *  Reservierung angehalten, damit kein Inventory-Select zwischen UseItem/Interact und dem
     *  nachfolgenden Slot-Restore laeuft. Meteor InvUtils.previousSlot wird hier nicht benutzt. */
    private boolean reserveCombatSlot(int targetSlot) {
        if (targetSlot < 0 || targetSlot > 8 || combatSlotReserved || mc.player == null) return false;
        int selected = mc.player.getInventory().getSelectedSlot();
        if (selected != targetSlot && !InvUtils.swap(targetSlot, false)) return false;
        combatSlotReserved = true;
        combatSlotTargetSlot = targetSlot;
        combatSlotPreviousSlot = selected == targetSlot ? -1 : selected;

        var baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
        baritone.getFollowProcess().cancel();
        baritone.getFollowProcess().onLostControl();
        baritone.getPathingBehavior().cancelEverything();
        var customGoal = baritone.getCustomGoalProcess();
        if (customGoal.isActive()) customGoal.onLostControl();
        resetPositioningState();
        followActive = false;
        followedId = null;
        return true;
    }

    private void releaseCombatSlot() {
        if (combatSlotReserved && combatSlotPreviousSlot >= 0 && mc.player != null
            && mc.player.getInventory().getSelectedSlot() == combatSlotTargetSlot) {
            InvUtils.swap(combatSlotPreviousSlot, false);
        }
        combatSlotReserved = false;
        combatSlotPreviousSlot = -1;
        combatSlotTargetSlot = -1;
    }

    private boolean withCombatSlot(FindItemResult item, Runnable action) {
        if (item.isOffhand()) {
            action.run();
            return true;
        }
        if (!item.isHotbar() || !reserveCombatSlot(item.slot())) return false;
        try {
            action.run();
            return true;
        } finally {
            releaseCombatSlot();
        }
    }

    private boolean combatSlotBusyFor(FindItemResult item) {
        return !item.isOffhand() && combatSlotReserved && combatSlotTargetSlot != item.slot();
    }

    private boolean queueWithCombatSlot(FindItemResult item, double yaw, double pitch, int priority, Runnable action) {
        boolean offhand = item.isOffhand();
        if (!offhand && !reserveCombatSlot(item.slot())) return false;

        boolean queued = rotateAndRun(yaw, pitch, priority, () -> {
            try {
                action.run();
            } finally {
                if (!offhand) releaseCombatSlot();
            }
        });
        if (!queued && !offhand) releaseCombatSlot();
        return queued;
    }

    private boolean rotateAndRun(double yaw, double pitch, Runnable callback) {
        return rotateAndRun(yaw, pitch, PRIORITY_MISC, callback);
    }

    private static boolean isHealingSplash(ItemStack stack) {
        if (!stack.is(Items.SPLASH_POTION)) return false;
        PotionContents pc = stack.get(DataComponents.POTION_CONTENTS);
        return pc != null && (pc.is(Potions.HEALING) || pc.is(Potions.STRONG_HEALING));
    }

    /** Wirft sofort eine Splash-Heiltraenke (Instant Health) zu den eigenen Fuessen, sobald frischer
     *  Schaden erkannt wird - der Trank zerschellt direkt am Boden und heilt augenblicklich. Laeuft
     *  unabhaengig vom Kampf-/Engage-Zustand, damit auch Fall-/Feuer-/Umweltschaden abgefedert wird.
     *
     *  Zwei Korrekturen gegenueber der ersten Version:
     *  1) Schaden wird ueber ein kurzes Zeitfenster (8 Ticks/0.4s) aufsummiert statt nur Tick-zu-Tick
     *     verglichen - ein Crystal-/Anchor-Treffer verteilt sich oft ueber 2+ Ticks (Knockback- und
     *     Schadens-Tick getrennt), wodurch jeder einzelne Tick fuer sich unter heal-min-damage bleiben
     *     kann, obwohl der Gesamtschaden klar ueber der Schwelle liegt - genau das liess Wuerfe bisher
     *     "zu spaet" wirken.
     *  2) Wird komplett uebersprungen, waehrend `blocking` (aktives Schild) oder `drinkingFireRes` laeuft:
     *     beide nutzen denselben globalen InvUtils.previousSlot-Merkposten fuer ihren eigenen, mehrere
     *     Ticks andauernden Hotbar-Swap. Ein dazwischengefunkter Heiltrank-Swap+swapBack() ueberschreibt
     *     diesen Merkposten und liefert beim Zurueckwechseln den FALSCHEN Slot - das Modul "haengt" dann
     *     im Schild/Trank-Zustand fest bzw. wechselt auf die falsche Waffe (die gemeldeten Aussetzer). */
    private void maintainHealPotions(Player self) {
        if (healPotionCooldown > 0) healPotionCooldown--;

        float hp = self.getHealth();
        float dmg = hpAtHealWindowStart - hp;

        // Fenster erst verschieben, wenn entweder geheilt wurde ODER es veraltet ist UND gerade kein
        // nennenswerter Schaden ansteht - ein Reset auf den AKTUELLEN (gerade erst gefallenen) Wert im
        // selben Tick wie ein Treffer wuerde den Schaden loeschen, bevor er ueberhaupt geprueft wird.
        if (hp > hpAtHealWindowStart || (tickCounter - healWindowStartTick > 8 && dmg < healMinDamage.get())) {
            hpAtHealWindowStart = hp;
            healWindowStartTick = tickCounter;
            dmg = hpAtHealWindowStart - hp;
        }

        // Sobald genug Schaden erkannt wurde, bleibt Heilen aktiv - nicht nur EIN Trank pro Treffer,
        // sondern jeden Cooldown-Zyklus erneut, bis die HP wieder (fast) voll sind. Erst dann endet
        // der Heil-Modus wieder, statt bei jedem folgenden Tick neu ueber die Schadens-Schwelle zu
        // pruefen (die nach dem ersten Trank sowieso meist wieder unter der Schwelle laege).
        if (dmg >= healMinDamage.get()) healingUntilFull = true;
        if (hp >= self.getMaxHealth() - 0.5f) healingUntilFull = false;

        if (!healPotions.get() || blocking || drinkingFireRes || healPotionCooldown > 0) return;
        if (!healingUntilFull) return;

        // Blind steil nach unten werfen (fixer Pitch 80) nahm an, dass immer fester Boden in Wurfnaehe
        // ist - stimmt nicht mehr, sobald der Bot gerade per Explosion/Knockback in der Luft haengt,
        // an einer Grubenkante steht oder ueber offenem Wasser/Void schwebt: der Trank faellt dann
        // weit, bevor er ueberhaupt etwas trifft, und die Selbstheilung (Splash-Radius ~4 Bloecke um
        // den Einschlagpunkt) verpufft komplett. Zielt jetzt stattdessen auf die naechste feste
        // Blockflaeche in Wurfreichweite (Boden bevorzugt, sonst Wand/Decke) - garantiert einen nahen
        // Einschlag unabhaengig von der Ausrichtung. Kein Ziel in Reichweite -> lieber gar nicht
        // werfen (naechster Tick versucht es erneut) als den Trank zu verschwenden.
        Vec3 splashTarget = findNearbySplashTarget(self);
        if (splashTarget == null) return;

        FindItemResult potion = InvHelper.find(GodmodePvP::isHealingSplash);
        if (!potion.found()) return;

        double throwYaw = Rotations.getYaw(splashTarget);
        double throwPitch = Rotations.getPitch(splashTarget);

        InteractionHand hand = potion.isOffhand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        boolean thrown = queueWithCombatSlot(potion, throwYaw, throwPitch, PRIORITY_MISC,
            () -> mc.gameMode.useItem(mc.player, hand));

        if (!thrown) return;
        healPotionCooldown = healCooldown.get();

        // hpAtHealWindowStart bewusst NICHT hier zuruecksetzen - healingUntilFull haelt den Heil-Modus
        // ueber mehrere Traenke hinweg aktiv, bis maxHealth-0.5 erreicht ist (siehe oben).
    }

    /** Sucht die naechste feste Blockflaeche in Splash-Wurfreichweite (4 Bloecke - der wirksame Radius
     *  einer Splash-Explosion) fuer die Selbstheilung: Boden zuerst (Normalfall), dann die 4
     *  Himmelsrichtungen, zuletzt die Decke - deckt auch Sonderfaelle ab (an einer Wand haengend,
     *  gerade per Explosion seitlich geschleudert, unter einem Ueberhang). Liefert den echten
     *  Block-Raycast-Treffer der naechstgelegenen Richtung, oder null, wenn in KEINER Richtung
     *  innerhalb der Reichweite etwas Festes liegt (z.B. mitten ueber offenem Wasser/Void/hoch in der
     *  Luft) - dann lieber gar nicht werfen, als den Trank fuer eine Explosion zu verschwenden, deren
     *  Radius den Werfer gar nicht mehr erreicht. */
    private Vec3 findNearbySplashTarget(Player self) {
        Vec3 eye = self.getEyePosition();
        Direction[] dirs = { Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP };

        Vec3 best = null;
        double bestDist = Double.MAX_VALUE;
        for (Direction dir : dirs) {
            Vec3 probe = eye.add(dir.getStepX() * 4.0, dir.getStepY() * 4.0, dir.getStepZ() * 4.0);
            ClipContext ctx = new ClipContext(eye, probe, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, self);
            BlockHitResult result = mc.level.clip(ctx);
            if (result.getType() == HitResult.Type.MISS) continue;

            double d = eye.distanceTo(result.getLocation());
            if (d < bestDist) {
                bestDist = d;
                best = result.getLocation();
            }
        }
        return best;
    }

    /** Haelt Fire Resistance permanent aktiv, solange man sich im Nether befindet - macht Lava-Kontakt,
     *  Explosions-Feuer und brennende Nachbarblöcke irrelevant. Laeuft unabhaengig vom Kampf-Zustand. */
    private void maintainFireResistance() {
        if (drinkingFireRes) {
            if (mc.player.hasEffect(MobEffects.FIRE_RESISTANCE) || tickCounter - fireResStartTick > 40) {
                releaseCombatSlot();
                drinkingFireRes = false;
            } else {
                mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
            }
            return;
        }

        if (blocking) return; // Schild-Swap laeuft gerade - nicht mit einem eigenen Trank-Swap ueberschreiben
        if (!autoFireRes.get()) return;
        if (mc.level == null || mc.level.dimension() != Level.NETHER) return;
        if (mc.player.hasEffect(MobEffects.FIRE_RESISTANCE)) return;

        FindItemResult potion = findFireResistancePotion();
        if (!potion.found()) return;

        InteractionHand hand = potion.isOffhand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        if (potion.isOffhand() || reserveCombatSlot(potion.slot())) {
            mc.gameMode.useItem(mc.player, hand);
            drinkingFireRes = true;
            fireResStartTick = tickCounter;
        }
    }

    private FindItemResult findFireResistancePotion() {
        java.util.function.Predicate<ItemStack> isFireRes = stack -> {
            if (!stack.is(Items.POTION)) return false;
            PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
            return contents != null && (contents.is(Potions.FIRE_RESISTANCE) || contents.is(Potions.LONG_FIRE_RESISTANCE));
        };
        FindItemResult found = InvHelper.find(isFireRes);
        return found;
    }

    // ---------- D-Tap-Executor (Obsidian in Flugbahn, 2 Crystals im Immunitaets-Abstand) ----------

    private void startDtap(LivingEntity target) {
        Vec3 predicted = predict(target);
        BlockPos floor = findDtapSpot(target, predicted);
        if (floor == null) return;

        if (mc.level.getBlockState(floor).isAir()) {
            FindItemResult obsidian = InvHelper.find(Items.OBSIDIAN);
            if (!obsidian.found()) return;
            if (!placeTrackedBlock(floor, obsidian, true, 50)) return;
        }

        dtapSpot = floor;
        dtapStage = 1;
        dtapStageTick = tickCounter;
    }

    /** Sucht rund um die vorhergesagte Landeposition eine gueltige Crystal-Basis (bestehendes Obsidian/
     *  Bedrock oder freie Luft zum selbst Obsidian setzen) mit maximalem Schaden am Ziel. Der Eigenschaden-
     *  Deckel wird verschaerft (60%), weil bei D-Tap zwei Explosionen in ca. 0.5s Abstand zusammenkommen.
     *
     *  Kandidaten muessen zusaetzlich in Reichweite des BOTS liegen (nicht nur nahe der Ziel-Vorhersage):
     *  bei starkem Knockback (Ziel fliegt hoch/weit) kann die extrapolierte Landeposition deutlich vom
     *  Bot abweichen - ohne diese Pruefung landete der Obsidian-Unterbau gelegentlich freischwebend
     *  ausser Reichweite, der nachfolgende Crystal-Platzierungsversuch schlug dann lautlos fehl und
     *  der Bot blieb wirkungslos vor dem selbstgebauten Turm stehen. */
    private BlockPos findDtapSpot(LivingEntity target, Vec3 predicted) {
        int bx = (int) Math.floor(predicted.x);
        int by = (int) Math.floor(predicted.y);
        int bz = (int) Math.floor(predicted.z);

        BlockPos best = null;
        double bestDmg = 0;

        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    BlockPos floor = new BlockPos(bx + dx, by + dy, bz + dz);
                    BlockPos cell = floor.above();
                    if (Math.sqrt(mc.player.distanceToSqr(Vec3.atCenterOf(cell))) > 4.5) continue;
                    if (!validExplosionSpot(cell, true)) continue;
                    if (hitsFriend(Vec3.atCenterOf(cell), true)) continue;

                    double selfDmg = DamageUtils.crystalDamage(mc.player, Vec3.atCenterOf(cell));
                    if (selfDmg > maxSelfDamage.get() * 0.6 || !selfDamageAllowed(Vec3.atCenterOf(cell), selfDmg)) continue;

                    double dmg = DamageUtils.crystalDamage(target, Vec3.atCenterOf(cell));
                    if (dmg <= 0) continue;

                    if (dmg > bestDmg) {
                        bestDmg = dmg;
                        best = floor;
                    }
                }
            }
        }
        return best;
    }

    private void runDtapTick(LivingEntity target) {
        switch (dtapStage) {
            case 1 -> { // Obsidian steht (oder gerade platziert) - 1. Crystal setzen
                if (tickCounter - dtapStageTick > 15) { dtapStage = 0; return; } // Fenster verpasst
                if (!mc.level.getBlockState(dtapSpot.above()).isAir()) { dtapStage = 0; return; } // besetzt
                if (!crystalPlacementSafe(mc.player, dtapSpot.above(), 0.6)) { dtapStage = 0; return; }

                FindItemResult crystal = InvHelper.find(Items.END_CRYSTAL);
                if (!crystal.found()) { dtapStage = 0; return; }

                if (placeCrystal(dtapSpot, crystal)) {
                    dtapStage = 2;
                    dtapStageTick = tickCounter;
                } // sonst Rotations-Slot belegt - naechster Tick erneut versuchen, Fenster laeuft noch
            }
            case 2 -> { // 1. Crystal steht - sofort zuenden
                EndCrystal ec = findCrystalAbove(dtapSpot);
                if (ec == null) {
                    if (tickCounter - dtapStageTick > 4) dtapStage = 0; // nie angekommen
                    return;
                }
                if (attackCrystal(ec)) {
                    dtapStage = 3;
                    dtapStageTick = tickCounter;
                }
            }
            case 3 -> { // Trefferimmunitaet abwarten (~10 Ticks = 0.5s), dann 2. Crystal
                if (tickCounter - dtapStageTick < 10) return;
                if (tickCounter - dtapStageTick > 30
                    || !mc.level.getBlockState(dtapSpot.above()).isAir()
                    || !crystalPlacementSafe(mc.player, dtapSpot.above(), 0.6)) {
                    dtapStage = 0;
                    dtapCooldown = delay(30);
                    return;
                }

                FindItemResult crystal = InvHelper.find(Items.END_CRYSTAL);
                if (!crystal.found()) { dtapStage = 0; return; }

                if (placeCrystal(dtapSpot, crystal)) {
                    dtapStage = 4;
                    dtapStageTick = tickCounter;
                }
            }
            case 4 -> { // 2. Crystal steht - zuenden, fertig
                EndCrystal ec = findCrystalAbove(dtapSpot);
                if (ec == null) {
                    if (tickCounter - dtapStageTick > 4) { dtapStage = 0; dtapCooldown = delay(30); }
                    return;
                }
                if (attackCrystal(ec)) {
                    dtapStage = 0;
                    dtapCooldown = delay(40);
                }
            }
            default -> dtapStage = 0;
        }
    }

    /** @return true, wenn die Rotation+Platzierung tatsaechlich eingereiht wurde (Rotations-Slot frei war). */
    private boolean placeCrystal(BlockPos floor, FindItemResult item) {
        if (drinkingFireRes) return false;
        Vec3 center = Vec3.atCenterOf(floor);
        InteractionHand hand = item.isOffhand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        return queueWithCombatSlot(item, Rotations.getYaw(center), Rotations.getPitch(center), PRIORITY_CRYSTAL,
            () -> BlockUtils.interact(new BlockHitResult(center, BlockUtils.getDirection(floor), floor, true), hand, true));
    }

    private EndCrystal findCrystalAbove(BlockPos floor) {
        AABB box = new AABB(floor.above()).inflate(0.6, 1.0, 0.6);
        for (EndCrystal ec : mc.level.getEntitiesOfClass(EndCrystal.class, box)) return ec;
        return null;
    }

    /** @return true, wenn die Rotation+Attacke tatsaechlich eingereiht wurde (Rotations-Slot frei war). */
    private boolean attackCrystal(EndCrystal ec) {
        Vec3 center = ec.getBoundingBox().getCenter();
        return rotateAndRun(Rotations.getYaw(center), Rotations.getPitch(center), PRIORITY_CRYSTAL, () -> {
            mc.gameMode.attack(mc.player, ec);
            mc.player.swing(InteractionHand.MAIN_HAND);
        });
    }

    // ---------- Tracking / Prediction ----------

    /** Prueft, ob der Bot GERADE IN Feuer steht oder auf dem direkten Weg zum Ziel (bis zu 6 Bloecke
     *  voraus, Fuss- und Kopfhoehe) Feuer liegt - Baritone haelt Feuer hart fuer unpassierbar (Umweg
     *  oder Stillstand). Feuer soll dagegen NIE ein Hindernis sein: einfach durchlaufen. */
    private boolean fireBlocksPath(Player self, LivingEntity target) {
        BlockPos here = self.blockPosition();
        if (isFireBlock(here) || isFireBlock(here.above())) return true;

        Vec3 diff = target.position().subtract(self.position());
        double len = diff.length();
        if (len < 0.001) return false;
        double dx = diff.x / len;
        double dz = diff.z / len;
        // Senkrechte Richtung zur Korridor-Verbreiterung - eine reine 1-Block-Linie direkt aufs Ziel
        // verfehlte Feuer, das nur leicht daneben lag oder eine wegen eines Kraters/Hindernisses
        // gekruemmte Baritone-Route blockierte, obwohl der Bot sichtbar direkt daneben durchlief.
        double px = -dz, pz = dx;

        int steps = (int) Math.min(len, 6);
        for (int step = 1; step <= steps; step++) {
            double cx = here.getX() + dx * step;
            double cz = here.getZ() + dz * step;
            for (int side = -1; side <= 1; side++) {
                BlockPos p = BlockPos.containing(cx + px * side + 0.5, here.getY(), cz + pz * side + 0.5);
                if (isFireBlock(p) || isFireBlock(p.above())) return true;
            }
        }
        return false;
    }

    private boolean isFireBlock(BlockPos pos) {
        var block = mc.level.getBlockState(pos).getBlock();
        return block == Blocks.FIRE || block == Blocks.SOUL_FIRE;
    }

    /** Darf das manuelle Feuer-Durchlaufen diesen Tick uebernehmen? Zwei harte Schranken, beide aus einem
     *  live reproduzierten Totalausfall heraus: der Bot stand mit einem passiven Gegner 16.5 Bloecke
     *  entfernt ueber 13 Sekunden bewegungslos in "feuer-durchqueren" (Eigengeschwindigkeit exakt 0.00).
     *  Ursache: nach den ersten Anchor-/Crystal-Explosionen brennt die halbe Umgebung, fireBlocksPath()
     *  meldet also praktisch dauerhaft Feuer, dieser Zweig kappt Baritone (cancelFollow) und drueckt
     *  stattdessen blind "W" Richtung Ziel - gegen eine Wand/Kraterkante laeuft der Bot damit fuer immer
     *  ins Leere, waehrend Baritone laengst drumherum gefunden haette.
     *  1. Nur im Nahbereich: weiter weg ist Pfadsuche IMMER besser als blind geradeaus.
     *  2. Nur solange es etwas bringt: kommt der Bot nicht naeher, uebernimmt wieder Baritone. */
    private boolean fireWalkAllowed(double dist) {
        if (!ignoreFire.get() || dist > FIRE_WALK_MAX_DIST) {
            resetFireWalkState();
            return false;
        }
        return tickCounter >= fireWalkBlockedUntil;
    }

    private void resetFireWalkState() {
        fireWalkStartTick = -1;
        fireWalkBlockedUntil = 0;
        fireWalkStartDist = 0;
    }

    /** Baritone weigert sich hart, durch Feuer zu pathen - dafuer kurz manuell geradeaus durchlaufen,
     *  der Schaden ist minimal verglichen mit dem, was der Bot sonst schon wegsteckt. Bricht selbst ab,
     *  sobald das Durchlaufen ueber FIRE_WALK_PATIENCE Ticks keinen Boden gutgemacht hat. */
    private void walkThroughFire(Player self, LivingEntity target, double dist) {
        if (fireWalkStartTick < 0 || tickCounter - fireWalkStartTick > FIRE_WALK_PATIENCE) {
            // Neues Fenster beginnen bzw. das abgelaufene auswerten.
            if (fireWalkStartTick >= 0 && dist > fireWalkStartDist - 0.5) {
                // Kein Fortschritt (oder sogar weiter weg) - Zweig fuer eine Weile sperren, Baritone
                // bekommt die Bewegung zurueck und routet um das Feuer herum.
                fireWalkBlockedUntil = tickCounter + FIRE_WALK_BLOCK_TICKS;
                fireWalkStartTick = -1;
                return;
            }
            fireWalkStartTick = tickCounter;
            fireWalkStartDist = dist;
        }

        cancelFollow();
        Vec3 center = target.getBoundingBox().getCenter();
        self.setYRot((float) Rotations.getYaw(center));
        Input.setKeyState(mc.options.keyUp, true);
        Input.setKeyState(mc.options.keySprint, true);
        // Auto-Sprung ueber Stufen/Kanten im Weg - sonst bleibt die manuelle Geradeaus-Bewegung
        // (ohne Baritones Pfadberechnung) an jedem kleinen Hoehenunterschied haengen.
        Input.setKeyState(mc.options.keyJump, self.horizontalCollision && self.onGround());
    }

    private void updateFollow(LivingEntity target) {
        if (!follow.get()) {
            cancelFollow();
            return;
        }

        // Jeden Tick neu setzen und NIE hart canceln, solange ein Ziel da ist - Baritones eigener
        // followRadius (siehe onActivate) haelt/loest den Nahkampf-Abstand von selbst, kontinuierlich
        // statt mit hartem cancelEverything()+Neuberechnung bei jedem Rein/Raus aus 3 Bloecken.
        var fp = BaritoneAPI.getProvider().getPrimaryBaritone().getFollowProcess();
        fp.follow(e -> e.getUUID().equals(target.getUUID()));
        followActive = true;
        if (!target.getUUID().equals(followedId)) {
            followedId = target.getUUID();
            anchorPlaceFails = 0; // Zielwechsel -> Fail-Counter zuruecksetzen
        }
    }

    private void cancelFollow() {
        var baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
        baritone.getFollowProcess().cancel();
        baritone.getFollowProcess().onLostControl();
        baritone.getPathingBehavior().cancelEverything();
        var customGoal = baritone.getCustomGoalProcess();
        if (customGoal.isActive()) customGoal.onLostControl();
        followActive = false;
        followedId = null;
        resetPositioningState();
    }

    /** Buendelt alle Felder, die eine als "veraltet" erkannte Positionierung/Navigation zuruecksetzen
     *  muessen - bisher setzte der Watchdog nur Anchor-/Follow-Zustand zurueck, liess aber activeHole/
     *  heightCalcOrigin (updateHolePositioning navigierte dann weiter zu einer laengst veralteten
     *  Loch-Position) und die Oszillations-Anker (oscillationAnchorPos/-Tick) unangetastet stehen -
     *  Ping-Pong-Bewegung statt eines sauberen Neustarts. */
    private void resetPositioningState() {
        activeHole = null;
        heightCalcOrigin = null;
        oscillationAnchorPos = null;
        oscillationAnchorTick = 0;
    }

    /** Vermeidet Explosionen, die einen befreundeten Spieler (Meteor-Friends-Liste) mittreffen wuerden. */
    private boolean hitsFriend(Vec3 pos, boolean crystal) {
        if (!respectFriends.get() || Friends.get().isEmpty()) return false;
        for (Player p : mc.level.players()) {
            if (p == mc.player) continue;
            if (!Friends.get().isFriend(p)) continue;
            double dmg = crystal ? DamageUtils.crystalDamage(p, pos) : DamageUtils.anchorDamage(p, pos);
            if (dmg > 2.0) return true;
        }
        return false;
    }

    /** Menschen tun das: kurz vor dem Schlag hochspringen, damit man beim Treffen faellt (Critical Hit,
     *  +50% Schaden). Physisch springen (sichtbar), nicht per Paket faken - genau das machen echte Spieler. */
    private boolean prepareCritAndCheck(double dist) {
        if (!critJump.get()) return true;

        // Im Wasser/Lava behaelt Baritone die volle Kontrolle ueber die Sprungtaste (zum Auftauchen/
        // Manoevrieren) - ein erzwungenes "false" hier wuerde Baritones eigenes Schwimmen sabotieren.
        if (mc.player.isInWater() || mc.player.isInLava()) return !mc.player.onGround();

        boolean shouldJump = mc.player.onGround() && dist <= 4.0 && mc.player.getAttackStrengthScale(0.5f) >= 0.4f;
        Input.setKeyState(mc.options.keyJump, shouldJump);

        return !mc.player.onGround() && mc.player.getDeltaMovement().y < 0;
    }

    /** Dreht sich im Nahkampf direkt zum Ziel (silent, falls free-look aktiv) und kreis-strafet -
     *  schwerer zu treffen, variiert den Explosionswinkel automatisch. Die Strafe-Richtung wird relativ
     *  zur TATSAECHLICHEN aktuellen Blickrichtung berechnet (nicht der evtl. nur serverseitigen Ziel-
     *  Rotation), damit das Kreisen auch bei freier Kamera (free-look) geometrisch korrekt bleibt.
     *  Ausserhalb der Nahkampfreichweite werden die Strafe-Tasten geloest, damit Baritones eigene
     *  Bewegung nicht gestoert wird. */
    private void updateCombatMovement(LivingEntity target, double dist) {
        if (dist > 3.6) {
            Input.setKeyState(mc.options.keyLeft, false);
            Input.setKeyState(mc.options.keyRight, false);
            Input.setKeyState(mc.options.keyDown, false);
            nextStrafeSwitchTick = -1; // Sentinel: naechster Nahkampf-Eintritt bekommt frischen Zufalls-Versatz
            return;
        }

        Vec3 center = target.getBoundingBox().getCenter();
        if (freeLook.get()) {
            pendingFreeLook = true;
            pendingFreeLookYaw = Rotations.getYaw(center);
            pendingFreeLookPitch = Rotations.getPitch(center);
        } else {
            mc.player.setYRot((float) Rotations.getYaw(center));
            mc.player.setXRot((float) Rotations.getPitch(center));
        }

        // Zufaellig getaktete Richtungswechsel (10-24 Ticks, 0.5-1.2s) statt eines starren 20-Tick-Rhythmus -
        // ein exakt periodisches Strafing ist leicht zu lesen (fuer Gegner UND Anti-Cheat-Heuristiken),
        // echte Spieler wechseln unregelmaessig. Bei frischem Nahkampf-Eintritt (Sentinel -1) NICHT sofort
        // auf Tick 0 flippen (waere ein exakt vorhersagbares Timing-Signal) - nur einen zufaelligen
        // Zeitpunkt fuer den ERSTEN Wechsel vormerken und die aktuelle Strafe-Richtung vorerst behalten.
        if (nextStrafeSwitchTick < 0) {
            nextStrafeSwitchTick = tickCounter + rng.nextInt(15);
        } else if (tickCounter >= nextStrafeSwitchTick) {
            strafeLeft = !strafeLeft;
            nextStrafeSwitchTick = tickCounter + 10 + rng.nextInt(15);
        }

        // Gewuenschte Weltraum-Tangentialrichtung um das Ziel auf die ECHTE aktuelle Blickrichtung
        // projizieren, statt blind keyLeft/keyRight zu druecken - bei free-look kann die Kamera woanders
        // hinschauen als das Server-Ziel, WASD-Bewegung richtet sich aber immer nach der echten Rotation.
        double dx = center.x - mc.player.getX();
        double dz = center.z - mc.player.getZ();
        double horiz = Math.sqrt(dx * dx + dz * dz);
        if (horiz < 1e-4) return;
        dx /= horiz;
        dz /= horiz;

        double tangX = strafeLeft ? -dz : dz;
        double tangZ = strafeLeft ? dx : -dx;

        double yawRad = Math.toRadians(mc.player.getYRot());
        double rightX = -Math.cos(yawRad), rightZ = -Math.sin(yawRad);
        double rightDot = tangX * rightX + tangZ * rightZ;

        Input.setKeyState(mc.options.keyRight, rightDot >= 0);
        Input.setKeyState(mc.options.keyLeft, rightDot < 0);

        // Physischer Rueckzug statt nur seitlichem Kreisen, wenn gerade ein neuer Crystal/Anchor/Bett
        // in der Naehe aufgetaucht ist (siehe explosionRetreatUntil, gesetzt direkt neben auto-shield).
        // Rotation zeigt hier bereits exakt auf 'center' (siehe oben) - "rueckwaerts" IST also "vom Ziel
        // weg", keine eigene Richtungsberechnung noetig.
        // Rueckwaerts auch, wenn das Ziel praktisch IM eigenen Koerper steht: auf Distanz 0 liegt jede
        // Explosivplatzierung genauso im eigenen Radius wie beim Gegner, der Eigenschaden-Deckel verwirft
        // damit JEDEN Kandidaten - live gemessen blieb der beste Crystal-Platz bei 0.1 HP Zielschaden
        // haengen, der Bot stand handlungsunfaehig im Gegner drin. Gegen echte Spieler faellt das kaum
        // auf, weil Spieler sich gegenseitig wegschieben; gegen einen Trainings-Dummy (clientseitig, ohne
        // Kollision) oder einen stehenden Gegner laeuft der Bot dagegen direkt hinein und bleibt stecken.
        boolean tooClose = dist < MIN_ATTACK_DIST;
        Input.setKeyState(mc.options.keyDown, tickCounter < explosionRetreatUntil || tooClose);
        if (tooClose) currentAction = "abstand-gewinnen";
    }

    /** Warnt und macht kurz vorsichtiger, wenn waehrend des Kampfes ein zweiter Spieler in der Naehe auftaucht. */
    private void checkSecondEnemy(Player self, LivingEntity currentTarget) {
        if (!multiTargetAlarm.get()) return;
        if (secondEnemyWarnCooldown > 0) {
            secondEnemyWarnCooldown--;
            return;
        }

        for (Player p : mc.level.players()) {
            if (p == self || p == currentTarget || !p.isAlive() || p.isSpectator()) continue;
            if (p.isCreative() && !(p instanceof FakePlayerEntity)) continue;

            double d = Math.sqrt(self.distanceToSqr(p));
            if (d <= 12.0) {
                ChatUtils.info("Zweiter Spieler in der Naehe: %s (%.0fm) - Vorsicht!", p.getName().getString(), d);
                secondEnemyWarnCooldown = 100;
                // Echte 2v1-Situation UND nicht auf voller Gesundheit: mehr als nur ein kurzes
                // Schild-Fenster - aktiv Distanz gewinnen (Escape-Pearl wenn bereit, sonst Schild+
                // Rueckzug), statt einfach normal weiterzukaempfen und auf den zweiten Treffer zu warten.
                if (self.getHealth() <= 14.0f) {
                    boolean pearlReady = tickCounter - lastPearlTick > delay(20)
                        && (InvHelper.has(Items.ENDER_PEARL));
                    if (escapePearl.get() && pearlReady) {
                        throwPearl(currentTarget, true);
                        currentAction = "2v1-flucht";
                        return;
                    }
                    explosionRetreatUntil = tickCounter + 15;
                }
                if (autoShield.get() && !blocking) {
                    shieldUntil = tickCounter + 10;
                    startBlock();
                }
                return;
            }
        }
    }

    /** Feuerwerk-Boost, wenn die Fluggeschwindigkeit beim Gleiten zu niedrig wird (Elytra-Flugkampf). */
    private void updateElytraFlight() {
        if (!elytraCombat.get() || !mc.player.isFallFlying()) return;
        if (drinkingFireRes) return; // s.o. - Swap-Merkposten waehrend des Trinkens nicht anfassen

        Vec3 vel = mc.player.getDeltaMovement();
        double speed = Math.sqrt(vel.x * vel.x + vel.z * vel.z);
        if (speed >= 0.6 || tickCounter - lastFireworkTick <= 20) return;

        FindItemResult firework = InvHelper.find(Items.FIREWORK_ROCKET);
        if (!firework.found()) return;

        InteractionHand hand = firework.isOffhand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        if (!withCombatSlot(firework, () -> mc.gameMode.useItem(mc.player, hand))) return;
        lastFireworkTick = tickCounter;
        currentAction = "elytra-boost";
    }

    /** Duckt sich in Deckung, wenn gerade nicht aktiv gekaempft wird - steht nur kurz zum Angriff auf. */
    private void updatePeekStance() {
        if (!peekTactic.get()) return;

        boolean inCover = countBoxedSides(mc.player) >= 3;
        boolean attacking = currentAction.equals("burst") || currentAction.equals("pre-hit")
            || currentAction.equals("anchor") || currentAction.equals("bed") || currentAction.equals("schild-brechen");
        mc.player.setShiftKeyDown(inCover && !attacking);
    }

    /** Zaehlt, wie viele der 4 horizontalen Nachbarn auf Fuesshoehe fest sind - erkennt Einkesselung/Deckung. */
    private int countBoxedSides(Player self) {
        return countBoxedSidesAt(self.blockPosition());
    }

    private int countBoxedSidesAt(BlockPos feet) {
        int blocked = 0;
        for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            if (mc.level.getBlockState(feet.relative(dir)).blocksMotion()) blocked++;
        }
        return blocked;
    }

    private boolean isStandable(BlockPos feet) {
        return mc.level.getBlockState(feet).isAir()
            && mc.level.getBlockState(feet.above()).isAir()
            && mc.level.getBlockState(feet.below()).blocksMotion();
    }

    /** Deckung (Waende) bewerten - Hoehen-Vorteil wird separat ueber findLowestAroundTarget() gesucht. */
    private double positionScore(BlockPos pos) {
        return holeAwareness.get() ? countBoxedSidesAt(pos) : 0;
    }

    /** Scannt einen 7x7-Bereich (dx/dz -3..3) UM DAS ZIEL herum, spaltenweise von 3 ueber bis 4 unter
     *  Ziel-Hoehe, und liefert die niedrigste erreichbare stehbare Stelle - eigene Explosionen treffen
     *  von dort mehr, gegnerische treffen weniger. Nur Kandidaten, die tatsaechlich niedriger als die
     *  aktuelle eigene Position und in vertretbarer Laufdistanz liegen, zaehlen.
     *
     *  Einmal gewaehlte Stelle bleibt bestehen, bis das Ziel sich meaningful (>2 Bloecke, gleiche
     *  Schwelle wie bei Anchor-/Bett-Kandidaten) bewegt hat oder ungueltig wird - sonst kippt die
     *  Entscheidung "tiefer gehen vs. direkt naeher laufen" bei jeder kleinen Bewegungsschwankung
     *  (Slope-Halbschritt beim Laufen, Ziel-Zittern beim Strafen) mehrmals pro Sekunde hin und her:
     *  ohne diese Stabilisierung flippt findBestPosition() zwischen "Kandidat gefunden" (-> Loch-Pfad)
     *  und "nichts gefunden" (-> direkte Verfolgung) und Baritone bekommt jeden Tick ein neues Ziel. */
    private BlockPos findLowestAroundTarget(Player self, LivingEntity target) {
        BlockPos targetPos = target.blockPosition();

        if (activeHole != null && heightCalcOrigin != null && heightCalcOrigin.distSqr(targetPos) <= 4
            && isStandable(activeHole)
            && Math.sqrt(self.distanceToSqr(Vec3.atCenterOf(activeHole))) <= 8.0) {
            return activeHole;
        }

        heightCalcOrigin = targetPos;
        BlockPos best = null;
        int bestY = self.blockPosition().getY();
        double selfX = self.getX(), selfZ = self.getZ();

        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                int cx = targetPos.getX() + dx;
                int cz = targetPos.getZ() + dz;
                // Horizontale Vorab-Distanz: ist schon die reine XZ-Distanz > 8, ist es die volle 3D-Distanz
                // (inkl. Y) erst recht - spart die kompletten 8 isStandable()-Checks dieser Spalte.
                double hdx = (cx + 0.5) - selfX, hdz = (cz + 0.5) - selfZ;
                if (hdx * hdx + hdz * hdz > 64.0) continue;
                for (int dy = 3; dy >= -4; dy--) {
                    BlockPos cand = new BlockPos(cx, targetPos.getY() + dy, cz);
                    if (cand.getY() >= bestY || !isStandable(cand)) continue;
                    if (Math.sqrt(self.distanceToSqr(Vec3.atCenterOf(cand))) > 8.0) continue; // nicht zu weit weglaufen
                    bestY = cand.getY();
                    best = cand;
                }
            }
        }
        return best;
    }

    /** Sucht die beste Position: zuerst Hoehen-Vorteil (7x7 um das Ziel, niedrigste Stelle), sonst Deckung
     *  im 3-Block-Radius um sich selbst. Muss spuerbar besser sein als einfach stehenzubleiben. */
    private BlockPos findBestPosition(Player self, LivingEntity target) {
        if (heightAdvantage.get()) {
            BlockPos lowest = findLowestAroundTarget(self, target);
            if (lowest != null) return lowest;
        }

        if (!holeAwareness.get()) return null;

        BlockPos origin = self.blockPosition();
        BlockPos targetPos = target.blockPosition();
        double originDistSqr = origin.distSqr(targetPos);
        BlockPos best = null;
        double bestScore = 1.5;

        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                for (int dy = -2; dy <= 1; dy++) {
                    BlockPos cand = origin.offset(dx, dy, dz);
                    if (!isStandable(cand)) continue;
                    // Nie weiter vom Ziel weg als jetzt - sonst laeuft der Bot fuer einen Deckungsvorteil
                    // aus dem Gefecht raus, und sobald er zurueckverfolgt wird dieselbe Stelle erneut
                    // "gefunden" -> endloses Hin-und-her-Pendeln.
                    if (cand.distSqr(targetPos) > originDistSqr + 2) continue;

                    double score = positionScore(cand);
                    if (score > bestScore) {
                        bestScore = score;
                        best = cand;
                    }
                }
            }
        }
        return best;
    }

    /** Horizontale Richtung vom Bot zum Ziel - gemeinsame Basis fuer Surround und Notdeckung, damit
     *  beide dieselbe Seite offen lassen. */
    private Direction horizontalDirTo(Player self, LivingEntity target) {
        Vec3 to = target.position().subtract(self.position());
        Direction best = null;
        double bestDot = 0;
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            double dot = dir.getStepX() * to.x + dir.getStepZ() * to.z;
            if (dot > bestDot) {
                bestDot = dot;
                best = dir;
            }
        }
        return best;
    }

    /** Surround: Obsidian an die eigenen Fuss-Nachbarn, damit dort kein gegnerischer Crystal mehr
     *  hinpasst (Standard-Verteidigung im Crystal-PvP). Die Seite ZUM Gegner bleibt offen - ein
     *  geschlossener Ring nimmt Baritone jeden Weg und der Bot mauert sich selbst ein. Ein Block pro
     *  Durchlauf, damit ein einzelner Tick nicht drei Rotationen und den halben Obsidianvorrat frisst. */
    private void updateSurround(Player self, LivingEntity target, double dist) {
        if (surroundCooldown > 0) surroundCooldown--;
        if (!surroundOn.get() || dist > 5.0 || !self.onGround() || surroundCooldown > 0) return;

        FindItemResult obsidian = InvHelper.find(Items.OBSIDIAN);
        if (!obsidian.found()) return;

        Direction toward = horizontalDirTo(self, target);
        BlockPos feet = self.blockPosition();
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            if (dir == toward) continue;
            BlockPos side = feet.relative(dir);
            if (!mc.level.getBlockState(side).isAir()) continue;
            if (!BlockUtils.canPlace(side, true)) continue;
            if (placeTrackedBlock(side, obsidian, true, PRIORITY_MISC)) {
                surroundCooldown = delay(4);
                currentAction = "surround";
            }
            return;
        }
    }

    /** Bricht nur Obsidian, das den Zielspieler auf Fusshoehe direkt umschliesst. Der Crystal wird
     *  erst nach beobachtetem Air-/Server-State-Wechsel im entstandenen Feld gesetzt, nicht blind in einen
     *  noch existierenden Block. */
    private boolean handleInstaCity(Player self, LivingEntity target, double dist) {
        if (instaCityCooldown > 0) instaCityCooldown--;
        if (combatSlotReserved && !instaCityOwnsCombatSlot) return false;
        if (!instaCity.get() || mc.gui.screen() != null || dist > 5.5) {
            cancelInstaCity();
            return false;
        }

        if (instaCityBlock != null && (!target.getUUID().equals(instaCityTargetId)
            || !isCurrentCitySurround(instaCityBlock, target)
            || self.distanceToSqr(Vec3.atCenterOf(instaCityBlock)) > 20.25
            || !BlockUtils.canBreak(instaCityBlock))) {
            cancelInstaCity();
        }

        if (instaCityBlock == null) {
            instaCityBlock = findInstaCityBlock(self, target);
            if (instaCityBlock == null) return false;
            instaCityTargetId = target.getUUID();
            instaCityBreakQueuedTick = -1;
            instaCityBreakConfirmed = false;
        }

        BlockPos pos = instaCityBlock;
        int breakTimeout = Math.max(400, pingTicks() * 4);
        if (instaCityBreakQueuedTick >= 0 && !instaCityBreakConfirmed
            && tickCounter - instaCityBreakQueuedTick > breakTimeout) {
            cancelInstaCity();
            return false;
        }

        if (!mc.level.getBlockState(pos).is(Blocks.OBSIDIAN)) {
            if (!instaCityBreakConfirmed) return true;
            cancelInstaCity();
            instaCityCooldown = 20;
            placeInstaCityCrystal(self, target, pos);
            return true;
        }

        if (instaCityCooldown > 0) return true;
        FindItemResult pickaxe = InvUtils.findInHotbar(stack -> stack.is(ItemTags.PICKAXES));
        if (!pickaxe.found() || !pickaxe.isHotbar()) return false;

        int selected = mc.player.getInventory().getSelectedSlot();
        if (instaCityOwnsCombatSlot) {
            if (selected != combatSlotTargetSlot) {
                cancelInstaCity();
                return false;
            }
        } else {
            if (!reserveCombatSlot(pickaxe.slot())) return false;
            instaCityOwnsCombatSlot = true;
        }

        Vec3 center = Vec3.atCenterOf(pos);
        boolean queued = rotateAndRun(Rotations.getYaw(center), Rotations.getPitch(center), PRIORITY_CRYSTAL,
            () -> BlockUtils.breakBlock(pos, false));
        if (instaCityBreakQueuedTick < 0) instaCityBreakQueuedTick = tickCounter;
        if (queued) {
            instaCityCooldown = 1;
            currentAction = "insta-city";
        }
        return queued;
    }

    private void cancelInstaCity() {
        releaseInstaCityTool();
        instaCityBlock = null;
        instaCityTargetId = null;
        instaCityBreakQueuedTick = -1;
        instaCityBreakConfirmed = false;
    }

    private void releaseInstaCityTool() {
        if (instaCityOwnsCombatSlot) releaseCombatSlot();
        instaCityOwnsCombatSlot = false;
    }

    private BlockPos findInstaCityBlock(Player self, LivingEntity target) {
        BlockPos targetFeet = target.blockPosition();
        BlockPos best = null;
        double bestDistance = 20.25;
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos pos = targetFeet.relative(dir);
            if (!mc.level.getBlockState(pos).is(Blocks.OBSIDIAN) || !BlockUtils.canBreak(pos)) continue;
            double distance = self.distanceToSqr(Vec3.atCenterOf(pos));
            if (distance < bestDistance) {
                bestDistance = distance;
                best = pos;
            }
        }
        return best;
    }

    private boolean isCurrentCitySurround(BlockPos pos, LivingEntity target) {
        BlockPos feet = target.blockPosition();
        if (countBoxedSidesAt(feet) < 2) return false;
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            if (pos.equals(feet.relative(dir))) return true;
        }
        return false;
    }

    private void placeInstaCityCrystal(Player self, LivingEntity target, BlockPos gap) {
        if (!isCurrentCitySurround(gap, target) || !isStandable(gap)
            || self.distanceToSqr(Vec3.atCenterOf(gap)) > 20.25
            || DamageUtils.crystalDamage(self, Vec3.atCenterOf(gap)) > maxSelfDamage.get()
            || !selfDamageAllowed(Vec3.atCenterOf(gap), DamageUtils.crystalDamage(self, Vec3.atCenterOf(gap)))
            || DamageUtils.crystalDamage(target, Vec3.atCenterOf(gap)) <= 0
            || hitsFriend(Vec3.atCenterOf(gap), true)) return;

        FindItemResult crystal = InvUtils.find(Items.END_CRYSTAL);
        if (!crystal.found() || (!crystal.isHotbar() && !crystal.isOffhand())) return;
        currentAction = "insta-city";
        placeCrystal(gap.below(), crystal);
    }

    private boolean tryAntiEscapeTrap(LivingEntity target, Vec3 targetVelocity) {
        if (!antiEscapeTrap.get() || targetVelocity.horizontalDistance() < 0.08) return false;

        Direction direction = null;
        double bestDot = 0.55;
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            double dot = dir.getStepX() * targetVelocity.x + dir.getStepZ() * targetVelocity.z;
            if (dot > bestDot) {
                bestDot = dot;
                direction = dir;
            }
        }
        if (direction == null) return false;

        BlockPos origin = target.onGround() ? target.blockPosition() : BlockPos.containing(predict(target));
        for (int distance = 1; distance <= 3; distance++) {
            BlockPos hole = origin.relative(direction, distance);
            if (!isStandable(hole) || countBoxedSidesAt(hole) != 4) continue;
            if (mc.player.getBoundingBox().intersects(new AABB(hole))) continue;
            if (mc.player.distanceToSqr(Vec3.atBottomCenterOf(hole)) > 20.25) continue;

            FindItemResult web = InvUtils.find(Items.COBWEB);
            if (web.found() && (web.isHotbar() || web.isOffhand()) && placeTrackedBlock(hole, web, true, PRIORITY_MISC)) {
                currentAction = "anti-escape-web";
                antiEscapeCooldown = 15;
                return true;
            }

            FindItemResult obsidian = InvUtils.find(Items.OBSIDIAN);
            if (obsidian.found() && (obsidian.isHotbar() || obsidian.isOffhand())
                && placeTrackedBlock(hole, obsidian, true, PRIORITY_MISC)) {
                currentAction = "anti-escape-obsidian";
                antiEscapeCooldown = 15;
                return true;
            }
        }
        return false;
    }

    private boolean handleAntiEscapeTrap(LivingEntity target) {
        if (antiEscapeCooldown > 0) antiEscapeCooldown--;
        if (!antiEscapeTrap.get() || mc.gui.screen() != null || tickCounter % 3 != 0) return false;
        Vec3 targetVelocity = velocities.getOrDefault(target.getUUID(), Vec3.ZERO);
        return tryAntiEscapeTrap(target, targetVelocity);
    }

    /** Raeumt gegnerische Angriffsbauten im Nahbereich weg, bevor sie zuenden bzw. ausfahren:
     *  fremde Betten (Nether/End: Explosionsstaerke 5, toetet auch durch Prot 4) und fremde Kolben
     *  samt Redstone-Block (Piston-PvP schiebt damit einen Crystal in die eigene Deckung).
     *  Eigene Bauteile werden nie angefasst - sonst reisst der Bot seine eigene Piston-Aura ab. */
    private void removeHostileBlocks(Player self) {
        if (hostileBlockCooldown > 0) {
            hostileBlockCooldown--;
            return;
        }
        if (!antiBed.get() && !antiPiston.get()) return;

        BlockPos feet = self.blockPosition();
        for (int dx = -3; dx <= 3; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -3; dz <= 3; dz++) {
                    BlockPos pos = feet.offset(dx, dy, dz);
                    BlockState st = mc.level.getBlockState(pos);

                    boolean hostileBed = antiBed.get() && bedsExplodeHere()
                        && st.getBlock() instanceof BedBlock
                        && !bedsPlacedByUs.contains(pos)
                        && !bedsPlacedByUs.contains(pos.relative(st.getValue(BedBlock.FACING)))
                        && !bedsPlacedByUs.contains(pos.relative(st.getValue(BedBlock.FACING).getOpposite()))
                        // Nur abbauen, was wir NICHT selbst gefahrlos zuenden koennten - sonst nimmt
                        // anti-bed der eigenen Bed Aura die fertige Explosion weg.
                        && !bedSelfDamageAcceptable(DamageUtils.bedDamage(mc.player, Vec3.atCenterOf(pos)))
                        && selfDamageAllowed(Vec3.atCenterOf(pos), DamageUtils.bedDamage(mc.player, Vec3.atCenterOf(pos)));

                    boolean hostilePiston = antiPiston.get() && !pistonPartsByUs.contains(pos)
                        && (st.is(Blocks.PISTON) || st.is(Blocks.STICKY_PISTON) || st.is(Blocks.REDSTONE_BLOCK));

                    if (!hostileBed && !hostilePiston) continue;
                    if (mc.player.distanceToSqr(Vec3.atCenterOf(pos)) > 4.5 * 4.5) continue;
                    if (!BlockUtils.canBreak(pos)) continue;

                    Vec3 center = Vec3.atCenterOf(pos);
                    boolean queued = rotateAndRun(Rotations.getYaw(center), Rotations.getPitch(center), PRIORITY_MISC,
                        () -> BlockUtils.breakBlock(pos, true));
                    if (queued) {
                        hostileBlockCooldown = delay(2);
                        currentAction = hostileBed ? "fremd-bett-abbauen" : "anti-piston";
                    }
                    return;
                }
            }
        }
    }

    /** Bodensicherung: steht der Bot ueber Luft/Lava/Feuer, kommt Obsidian darunter. Im Nether ist das
     *  der haeufigste Tod neben Betten - Netherrack (Sprengwiderstand 0.4) ist nach zwei Explosionen
     *  weg, darunter liegt oft Lava, und Wasser verdampft dort (kein MLG moeglich). */
    private void secureFootingTick(Player self, double dist) {
        if (footingCooldown > 0) footingCooldown--;
        if (!secureFooting.get() || dist > 8 || footingCooldown > 0) return;

        BlockPos below = self.blockPosition().below();
        BlockState st = mc.level.getBlockState(below);
        if (!st.isAir() && !st.is(Blocks.LAVA) && !st.is(Blocks.FIRE) && !st.is(Blocks.SOUL_FIRE)) return;

        // Nicht jede Bodenluecke ist gefaehrlich: liegt innerhalb von drei Bloecken wieder fester,
        // nicht brennender Boden, faellt der Bot dort hoechstens eine Stufe herunter - dafuer Obsidian
        // zu verbrennen war live der Normalfall (83 Platzierungen in 70s, vor allem waehrend
        // Explosions-Knockback). Gebaut wird nur ueber Lava/Feuer oder ueber einem echten Abgrund.
        boolean lethal = false;
        BlockPos scan = below;
        for (int i = 0; i < 3; i++) {
            BlockState s = mc.level.getBlockState(scan);
            if (s.is(Blocks.LAVA) || s.is(Blocks.FIRE) || s.is(Blocks.SOUL_FIRE)) {
                lethal = true;
                break;
            }
            if (s.blocksMotion()) return; // sicherer Boden in Reichweite - nichts zu tun
            scan = scan.below();
        }
        if (!lethal && !mc.level.getBlockState(scan).isAir()) return;

        FindItemResult obsidian = InvHelper.find(Items.OBSIDIAN);
        if (!obsidian.found()) return;
        if (!BlockUtils.canPlace(below, true)) return;
        if (placeTrackedBlock(below, obsidian, true, PRIORITY_MISC)) {
            footingCooldown = delay(4);
            currentAction = "boden-sichern";
        }
    }

    /** Haelt die Besitz-Merklisten klein und aktuell: ein Eintrag, dessen Block nicht mehr existiert
     *  (gezuendet, weggesprengt, abgebaut) oder der ausser Reichweite liegt, wuerde sonst ewig mitlaufen -
     *  und im schlimmsten Fall ein SPAETER dort platziertes gegnerisches Bett als "unseres" durchwinken. */
    private void pruneOwnedBlocks() {
        BlockPos feet = mc.player.blockPosition();
        bedsPlacedByUs.removeIf(p -> p.distSqr(feet) > 256
            || !(mc.level.getBlockState(p).getBlock() instanceof BedBlock));
        pistonPartsByUs.removeIf(p -> {
            if (p.distSqr(feet) > 256) return true;
            BlockState st = mc.level.getBlockState(p);
            return !(st.is(Blocks.PISTON) || st.is(Blocks.STICKY_PISTON) || st.is(Blocks.REDSTONE_BLOCK)
                || st.is(Blocks.OBSIDIAN) || st.is(Blocks.MOVING_PISTON) || st.is(Blocks.PISTON_HEAD));
        });
    }

    /** Schadensgrenze, unter der eine Explosivoption als "der Gegner sitzt zu gut" gilt - Ausloeser fuer
     *  die Piston-Aura. 4 HP entspricht in etwa dem, was ein Crystal vom Lochrand aus noch durch volle
     *  Netherit-Ruestung bringt; alles darunter lohnt den normalen Platz nicht mehr. */
    private static final double PISTON_WEAK_DAMAGE = 4.0;

    /** Piston-Aura (Angriff) - die Antwort auf einen Gegner, der in einem Loch bzw. unter Deckung sitzt.
     *  Ein Kolben schiebt ENTITIES, und ein End Crystal ist eine Entity: man setzt den Crystal also auf
     *  den Lochrand (dort gibt es eine Unterlage) und schiebt ihn anschliessend in die LUFTSAEULE UEBER
     *  DEM KOPF des Gegners - genau die Zelle, in der die Explosion am meisten weh tut und in die man
     *  normal nichts setzen kann, weil unter ihr kein Block, sondern der Spieler selbst steht.
     *  Obsidian schiebt ein Kolben NICHT (Push-Reaktion BLOCK), die Deckung selbst bleibt also stehen.
     *
     *  Ausloeser ist bewusst NICHT "gar kein Schaden mehr moeglich": ist der Gegner komplett zugebaut,
     *  laesst sich auch nichts mehr hineinschieben. Der reale Fall ist SCHWACHER Schaden - der Gegner
     *  im Loch, erreichbar sind nur noch Plaetze zwei Bloecke entfernt am Rand.
     *
     *  Ablauf als Zustandsautomat, eine Aktion pro Tick (jede braucht ihre eigene Rotation):
     *  1 = Obsidian-Unterlage am Rand, 2 = Crystal darauf, 3 = Kolben dahinter (zum Ziel ausgerichtet),
     *  4 = Redstone-Block daneben -> Kolben faehrt aus, schiebt den Crystal ueber den Gegner,
     *  CrystalAura zuendet ihn dort ganz normal.
     *
     *  Die Blickrichtung beim Kolbensetzen ist NICHT kosmetisch: Vanilla richtet einen Kolben nach
     *  {@code getNearestLookingDirection().getOpposite()} aus, der Kopf zeigt also zum Spieler. Damit er
     *  zum GEGNER zeigt, muss der Bot beim Setzen bewusst VOM Ziel weg schauen.
     *  @return true, wenn diese Tick-Aktion die Piston-Aura war. */
    private boolean runPistonAura(Player self, LivingEntity target, double dist) {
        if (pistonCooldown > 0) pistonCooldown--;

        // Ausloeser: seit >= 1.5s bringt KEINE Explosivoption mehr als PISTON_WEAK_DAMAGE, obwohl der
        // Gegner in Reichweite ist - das ist die Signatur eines Gegners im Loch/unter Deckung.
        double bestAvailable = Math.max(bestCrystalDmgCache, Math.max(bestAnchorDmgCache, bestBedDmgCache));
        if (bestAvailable > PISTON_WEAK_DAMAGE || dist > 6.0) explosiveStarvedTicks = 0;
        else explosiveStarvedTicks++;

        if (pistonStage == 0) {
            if (!pistonAura.get() || pistonCooldown > 0 || explosiveStarvedTicks < 40) return false;
            if (!startPistonAura(self, target)) return false;
        }

        // Haengengeblieben (Block weggesprengt, Ziel weg, Reichweite verloren) -> abbrechen statt ewig warten
        if (tickCounter - pistonStageTick > 40 || dist > 7.0) {
            resetPistonAura();
            return false;
        }

        switch (pistonStage) {
            case 1 -> {
                BlockPos base = pistonCrystalCell.below();
                BlockState st = mc.level.getBlockState(base);
                if (st.is(Blocks.OBSIDIAN) || st.is(Blocks.BEDROCK)) {
                    pistonStage = 2;
                    pistonStageTick = tickCounter;
                    return false;
                }
                FindItemResult obsidian = InvHelper.find(Items.OBSIDIAN);
                if (!obsidian.found()) {
                    resetPistonAura();
                    return false;
                }
                if (placeTrackedBlock(base, obsidian, true, PRIORITY_CRYSTAL)) {
                    pistonPartsByUs.add(base);
                    pistonStage = 2;
                    pistonStageTick = tickCounter;
                }
            }
            case 2 -> {
                if (!mc.level.getEntitiesOfClass(EndCrystal.class, new AABB(pistonCrystalCell)).isEmpty()) {
                    pistonStage = 3;
                    pistonStageTick = tickCounter;
                    return false;
                }
                FindItemResult crystal = InvHelper.find(Items.END_CRYSTAL);
                if (!crystal.found()) {
                    resetPistonAura();
                    return false;
                }
                if (!crystalPlacementSafe(self, pistonCrystalCell, 1.0)) { resetPistonAura(); return false; }
                Vec3 base = Vec3.atCenterOf(pistonCrystalCell.below());
                rotateAndRun(Rotations.getYaw(base), Rotations.getPitch(base), PRIORITY_CRYSTAL,
                    () -> withCombatSlot(crystal, () -> BlockUtils.interact(
                        new BlockHitResult(base, Direction.UP, pistonCrystalCell.below(), false),
                        InteractionHand.MAIN_HAND, true)));
                pistonStage = 3;
                pistonStageTick = tickCounter;
            }
            case 3 -> {
                FindItemResult piston = InvHelper.find(Items.PISTON);
                if (!piston.found()) piston = InvHelper.find(Items.STICKY_PISTON);
                if (!piston.found() || !mc.level.getBlockState(pistonBodyCell).isAir()) {
                    resetPistonAura();
                    return false;
                }
                // Vom Ziel WEG schauen, damit der Kolbenkopf zum Ziel zeigt (siehe Javadoc oben).
                float awayYaw = (float) (Rotations.getYaw(target.position()) + 180.0);
                boolean queued = rotateAndRun(awayYaw, 0, PRIORITY_CRYSTAL, () -> {
                    FindItemResult p = InvHelper.find(Items.PISTON).found()
                        ? InvHelper.find(Items.PISTON) : InvHelper.find(Items.STICKY_PISTON);
                    placeTrackedBlock(pistonBodyCell, p, false, PRIORITY_CRYSTAL);
                });
                if (queued) {
                    pistonPartsByUs.add(pistonBodyCell);
                    pistonStage = 4;
                    pistonStageTick = tickCounter;
                }
            }
            case 4 -> {
                FindItemResult redstone = InvHelper.find(Items.REDSTONE_BLOCK);
                if (!redstone.found()) {
                    resetPistonAura();
                    return false;
                }
                BlockPos power = findPowerCell(pistonBodyCell);
                if (power == null) {
                    resetPistonAura();
                    return false;
                }
                if (placeTrackedBlock(power, redstone, true, PRIORITY_CRYSTAL)) {
                    pistonPartsByUs.add(power);
                    pistonCooldown = delay(60); // ausgefahren - CrystalAura uebernimmt das Zuenden
                    resetPistonAura();
                }
            }
        }
        currentAction = "piston-aura";
        return true;
    }

    /** Sucht die Geometrie fuer die Piston-Aura. Zielzelle des Schubs ist die Luftsaeule UEBER dem Kopf
     *  des Gegners: dort steht unter der Zelle kein Block, sondern der Spieler selbst - genau deshalb
     *  kann CrystalAura da nichts platzieren, obwohl es die wirksamste Stelle waere. Von dort aus wird
     *  seitlich zurueckgerechnet: Crystal-Zelle am Rand (mit fester Unterlage), dahinter der Kolben. */
    private boolean startPistonAura(Player self, LivingEntity target) {
        // ALLE Bauteile vorab pruefen, nicht erst in der jeweiligen Stufe: sonst setzt der Automat
        // Obsidian und einen Crystal und bricht danach mangels Kolben ab - ein verschenkter Crystal
        // pro Versuch (live so beobachtet, bevor diese Pruefung da war).
        if (!InvHelper.has(Items.END_CRYSTAL) || !InvHelper.has(Items.REDSTONE_BLOCK)) return false;
        if (!InvHelper.has(Items.PISTON) && !InvHelper.has(Items.STICKY_PISTON)) return false;

        // Schubziel von unten nach oben suchen: bei einem 1 Block tiefen Loch ist die Kopfzelle selbst
        // seitlich offen, bei einem 2 Bloecke tiefen liegt die erste seitlich erreichbare Zelle eine
        // Ebene HOEHER (auf Hoehe des umgebenden Bodens). Tiefer ist immer besser - naeher am Kopf.
        for (int level = 1; level <= 2; level++) {
            BlockPos pushTarget = target.blockPosition().above(level);
            if (!mc.level.getBlockState(pushTarget).isAir()) continue;

            for (Direction dir : Direction.Plane.HORIZONTAL) {
                BlockPos crystalCell = pushTarget.relative(dir);
                BlockPos bodyCell = crystalCell.relative(dir);
                if (!mc.level.getBlockState(crystalCell).isAir()) continue;
                if (!mc.level.getBlockState(crystalCell.above()).isAir()) continue; // End Crystal ist 2 Bloecke hoch
                if (!mc.level.getBlockState(bodyCell).isAir()) continue;
                // Unterlage fuer den Crystal: vorhandenes Obsidian/Bedrock oder eine freie Zelle, in die
                // wir selbst Obsidian setzen koennen. Netherrack/Stein traegt keinen Crystal.
                BlockState base = mc.level.getBlockState(crystalCell.below());
                if (!base.is(Blocks.OBSIDIAN) && !base.is(Blocks.BEDROCK) && !base.isAir()) continue;
                if (self.distanceToSqr(Vec3.atCenterOf(bodyCell)) > 4.5 * 4.5) continue;
                if (self.distanceToSqr(Vec3.atCenterOf(crystalCell)) > 4.5 * 4.5) continue;
                if (!crystalPlacementSafe(self, crystalCell, 1.0)) continue;

                pistonCrystalCell = crystalCell;
                pistonBodyCell = bodyCell;
                pistonPushDir = dir.getOpposite(); // Schubrichtung: vom Kolben ueber den Gegner
                if (findPowerCell(bodyCell) == null) continue;

                pistonStage = 1;
                pistonStageTick = tickCounter;
                return true;
            }
        }
        pistonCrystalCell = null;
        pistonBodyCell = null;
        pistonPushDir = null;
        return false;
    }

    /** Freie, erreichbare Zelle direkt am Kolben, in die der Redstone-Block passt (nicht die
     *  Schubrichtung selbst - dort steht der Crystal). */
    private BlockPos findPowerCell(BlockPos pistonCell) {
        for (Direction dir : Direction.values()) {
            if (dir == pistonPushDir) continue;
            BlockPos cell = pistonCell.relative(dir);
            if (!mc.level.getBlockState(cell).isAir()) continue;
            if (mc.player.distanceToSqr(Vec3.atCenterOf(cell)) > 4.5 * 4.5) continue;
            if (!BlockUtils.canPlace(cell, true)) continue;
            return cell;
        }
        return null;
    }

    private void resetPistonAura() {
        pistonStage = 0;
        pistonCrystalCell = null;
        pistonBodyCell = null;
        pistonPushDir = null;
        explosiveStarvedTicks = 0;
    }

    /** Notdeckung: platziert einen Obsidian-Block an einer offenen Seite, wenn kein natuerliches Loch da ist.
     *  Baut NIE in die Richtung des Ziels - sonst mauert sich der Bot die eigene Sichtlinie zu und kann
     *  weder Nahkampf noch Explosionen mehr landen (genau das erzeugte den "steht nur noch da"-Bug: der
     *  einzige offene Nachbarblock lag zufaellig zwischen Bot und Gegner). */
    private void buildOwnCover(Player self, LivingEntity target) {
        FindItemResult obsidian = InvHelper.find(Items.OBSIDIAN);
        if (!obsidian.found()) return;

        Direction towardTarget = horizontalDirTo(self, target);

        BlockPos feet = self.blockPosition();
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            if (dir == towardTarget) continue;
            BlockPos side = feet.relative(dir);
            if (mc.level.getBlockState(side).isAir()) {
                placeTrackedBlock(side, obsidian, true, 50);
                return; // ein Block pro Versuch reicht - nicht den ganzen Vorrat auf einmal verbrauchen
            }
        }
    }

    /** Sucht/nutzt aktiv die beste nahe Position (Deckung und/oder Hoehen-Vorteil) als Kampfposition,
     *  statt frei/hoehengleich zu stehen. Berechnet die Ziel-Position JEDEN TICK frisch neu (folgt damit
     *  einem sich bewegenden Gegner kontinuierlich), setzt Baritones Pfad aber nur bei tatsaechlicher
     *  Aenderung neu - sonst wuerde ein unveraendertes Ziel den laufenden Pfad jeden Tick sinnlos
     *  verwerfen. Findet sich nichts Natuerliches, wird notfalls selbst Deckung gebaut. Liefert true,
     *  solange Baritones CustomGoalProcess unterwegs ist - dann soll updateFollow() diesen Tick pausieren. */
    private boolean updateHolePositioning(LivingEntity target, double dist) {
        if (buildCoverCooldown > 0) buildCoverCooldown--;

        if ((!holeAwareness.get() && !heightAdvantage.get()) || dist > 8) {
            activeHole = null;
            return false;
        }

        BlockPos best = findBestPosition(mc.player, target);

        if (best == null) {
            activeHole = null;
            // Cooldown, statt jeden Tick neu zu versuchen: ohne ihn rief updateHolePositioning() das
            // hier JEDEN Tick auf, solange kein natuerliches Loch gefunden wurde - der Bot hat sich damit
            // Seite fuer Seite komplett selbst eingemauert (jede Platzierung eine eigene Rotation +
            // Block-Paket), was sich als Lag bemerkbar machte UND ihn am Ende blind/bewegungsunfaehig
            // in seiner eigenen Kiste stehen liess.
            boolean outOfExplosives = totalItem(Items.END_CRYSTAL) <= 0
                && !(anchorsExplodeHere() && totalItem(Items.RESPAWN_ANCHOR) > 0 && totalItem(Items.GLOWSTONE) > 0)
                && !(useBeds.get() && bedsExplodeHere() && totalItem(GodmodePvP::isBed) > 0);
            // Echte Notdeckung heisst: NICHTS Explosives mehr verfuegbar - solange noch Crystals/Anchor+
            // Glowstone/Betten da sind, soll der Bot damit kaempfen (Block neben dem GEGNER fuer die
            // Crystal-Unterlage, schlagen, crystaln, verfolgen), statt sich staendig ohne echten Grund
            // selbst einzumauern, nur weil kein natuerliches Loch in der Naehe lag (auf offenem Feld quasi
            // immer der Fall - das liess den Bot bislang WAEHREND aktiver Gefechte pausenlos Deckung um
            // sich selbst bauen statt zu kaempfen).
            if (buildCover.get() && dist <= 4.5 && buildCoverCooldown <= 0 && outOfExplosives) {
                buildOwnCover(mc.player, target);
                buildCoverCooldown = 30;
                currentAction = "deckung-bauen";
            }
            return false;
        }

        if (mc.player.blockPosition().distSqr(best) <= 1) {
            activeHole = null; // angekommen - normale Verfolgung/Kampf uebernimmt wieder
            return false;
        }

        if (!best.equals(activeHole)) {
            activeHole = best;
            BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess()
                .setGoalAndPath(new baritone.api.pathing.goals.GoalBlock(best));
        }
        currentAction = "hole";
        return true;
    }

    /** UUID-Positionswechsel invalidieren nicht nur die Vorhersage, sondern auch jeden Baritone-Pfad.
     *  Sonst bleibt ein CustomGoal auf der alten Zelle aktiv, waehrend pursue-stationary-targets den
     *  FollowProcess bereits auf die neue teleportierte Position gesetzt hat. */
    private void invalidateTargetPath() {
        followActive = false;
        followedId = null;
        obstacleStuckTicks = 0;
        lastTargetHpForStuck = -1;
        watchdogStuckTicks = 0;
        oscillationAnchorPos = null;
        oscillationAnchorTick = 0;
        resetFireWalkState();
        cancelInstaCity();
        resetPositioningState();

        var baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
        baritone.getFollowProcess().cancel();
        baritone.getFollowProcess().onLostControl();
        baritone.getPathingBehavior().cancelEverything();
        var customGoal = baritone.getCustomGoalProcess();
        if (customGoal.isActive()) customGoal.onLostControl();
        var worldData = baritone.getWorldProvider().getCurrentWorld();
        if (worldData != null) worldData.getCachedWorld().reloadAllFromDisk();
    }

    private void updateTracking(LivingEntity target) {
        UUID id = target.getUUID();
        Vec3 cur = target.position();
        Vec3 prev = lastPositions.put(id, cur);

        if (prev != null) {
            double jump = cur.distanceTo(prev);
            if (jump > 6.0) {
                velocities.put(id, Vec3.ZERO);
                invalidateTargetPath();
                popBurstUntil = Math.max(popBurstUntil, tickCounter + 6);
                ChatUtils.info("Pearl-Teleport erkannt (%.0f m) - verfolge neue Position.", jump);
                // FollowProcess verfolgt automatisch zur neuen Position
            } else {
                // Cap knapp unter der Teleport-Schwelle statt bei 2.0: ein hartes Crystal-/Anchor-Pop
                // (oder ein frisch getroffener Vollnahkampf-Knockback) launcht durchaus mit 3-5 Bloecken/
                // Tick - der alte 2.0-Cap hat genau diese Faelle systematisch unterschaetzt und damit die
                // Pearl-/Anchor-/Crystal-Vorhersage bei einem gerade weggeschleuderten Ziel voellig
                // daneben zielen lassen (das Ziel "sah" fuer die Vorhersage viel langsamer aus als es war).
                Vec3 rawVel = cur.subtract(prev);
                if (rawVel.length() > 5.5) rawVel = rawVel.normalize().scale(5.5);
                // Geglaettet (EMA) statt roh uebernommen: reines Tick-zu-Tick-Delta zappelt bei jedem
                // Strafe-Richtungswechsel oder Sprung-Scheitelpunkt sofort komplett um, was die
                // Vorhersage staendig kurz in die falsche Richtung schiessen liess. alpha=0.5 haelt echte
                // Richtungswechsel innerhalb von 2-3 Ticks sichtbar, daempft aber Einzeltick-Rauschen.
                Vec3 prevVel = velocities.getOrDefault(id, rawVel);
                velocities.put(id, prevVel.scale(0.5).add(rawVel.scale(0.5)));
            }
        }
    }

    /** Am Boden: einfache lineare Extrapolation reicht (kaum Vertikalbewegung). In der Luft (gesprungen,
     *  von Anchor/Crystal hochgeschleudert, am Fallen, Elytra): simuliert Minecrafts Schwerkraft+Luftwiderstand
     *  pro Tick, damit die Vorhersage der echten Wurfparabel folgt statt geradeaus davonzulaufen - sonst zielt
     *  Crystal/Anchor-Platzierung bei einem in der Luft befindlichen Gegner systematisch daneben. */
    private Vec3 predict(LivingEntity target) {
        // lead-ticks (Setting) ist die Basis-Vorlaufzeit fuer eine latenzfreie Verbindung; die
        // tatsaechliche eigene Round-Trip-Zeit zum Server kommt real oben drauf. Vorher war der Wert
        // fix, egal ob 5ms LAN oder 150+ms uebers Internet/durch einen Latenz-Proxy - auf Servern mit
        // spuerbarer Latenz griff die Vorhersage dadurch systematisch zu kurz (das Ziel hatte sich
        // bis zur tatsaechlichen Aktion laengst weiterbewegt, als die feste Tick-Zahl einkalkulierte).
        return predictOverTicks(target, leadTicks.get() + pingTicks());
    }

    /** Eigene Client-Server-Latenz in Ticks (1 Tick = 50ms), aus Meteors Tab-Listen-Latenz-Cache -
     *  kein Live-Wert (der Server aktualisiert ihn nur alle paar Sekunden), aber eine echte gemessene
     *  Groesse statt eine geratene Konstante. Kein Eintrag (z.B. Sekundenbruchteile nach dem Connect)
     *  -> 0 Bonus-Ticks, lieber ohne Ping-Zuschlag als mit einem erfundenen Wert.
     */
    private int pingTicks() {
        if (mc.getConnection() == null || mc.player == null) return 0;
        var info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
        if (info == null) return 0;
        return Math.max(0, info.getLatency() / 50);
    }

    /** Wie predict(), aber mit frei waehlbarer Tick-Anzahl statt der festen 'lead-ticks'-Einstellung - fuer
     *  Faelle mit eigener, datengetriebener Vorlaufzeit statt eines fixen kurzen Vorhersage-Fensters (z.B.
     *  die tatsaechliche Flugzeit eines Perlwurfs, die je nach Distanz stark variiert). */
    private Vec3 predictOverTicks(LivingEntity target, int ticks) {
        if (ticks <= 0) return target.position();
        // Kein Tracking-Datenpunkt fuer dieses Ziel (allererster Tick nach Zielwahl/Zielwechsel,
        // updateTracking() hat noch keine Geschwindigkeit gemessen) - eine angenommene Nullgeschwindigkeit
        // waere fuer ein Ziel in der Luft (gerade explosionsgeschleudert, mitten im Sprung) komplett falsch
        // (die Fallsimulation unten wuerde bei "ruht gerade" statt der echten Ausgangsgeschwindigkeit
        // starten). Lieber ehrlich keine Vorhersage liefern (aktuelle Position), bis der naechste Tick
        // eine echte gemessene Geschwindigkeit beisteuert.
        Vec3 vel = velocities.get(target.getUUID());
        if (vel == null) return target.position();

        if (target.onGround()) {
            return target.position().add(vel.scale(ticks));
        }

        Vec3 pos = target.position();
        Vec3 v = vel;
        for (int i = 0; i < ticks; i++) {
            Vec3 next = pos.add(v);
            // Landung erkennen: sobald der simulierte Fall in einen soliden Block hineinlaeuft, dort
            // einrasten statt endlos weiterzufallen. Ohne das faellt die Vorhersage bei einer Perlflugzeit,
            // die laenger ist als die Fallzeit bis zur tatsaechlichen Landung, klaglos durch den Boden -
            // genau der gemeldete Fall "Perle trifft in der Luft befindliche Gegner nicht, weil sie dahin
            // wirft, wo sie waeren, wenn sie ewig weiterfallen wuerden" statt an die echte Landestelle.
            if (v.y < 0) {
                BlockPos landPos = BlockPos.containing(next.x, next.y, next.z);
                BlockState landState = mc.level.getBlockState(landPos);
                if (landState.blocksMotion()) {
                    // Echte Oberflaechenhoehe der Kollisionsform statt pauschal "ganzer Block" (Y+1) -
                    // eine Slab-Kante (Top bei Y+0.5) oder ein Zaun (Top bei Y+1.5) wurde sonst um bis zu
                    // einen halben Block falsch platziert, die Landestelle des Ziels also verfehlt.
                    double topOffset = 1.0;
                    var shape = landState.getCollisionShape(mc.level, landPos);
                    if (!shape.isEmpty()) topOffset = shape.max(net.minecraft.core.Direction.Axis.Y);
                    return new Vec3(next.x, landPos.getY() + topOffset, next.z);
                }
            }
            pos = next;
            v = new Vec3(v.x * 0.91, (v.y - 0.08) * 0.98, v.z * 0.91);
        }
        return pos;
    }

    // ---------- Zielauswahl ----------

    private Player findPlayerTarget(Player self) {
        // Echte Spieler haben immer Vorrang vor einem Trainings-Dummy (FakePlayerEntity) - der zaehlt nur
        // als Ziel, wenn wirklich kein echter Gegner in Reichweite ist. Sonst wuerde ein liegen gelassener
        // Dummy (z.B. nach einem Server-/Welt-Wechsel) die Zielwahl von einem echten Angreifer kapern.
        double followRangeSq = followRange.get() * (double) followRange.get();
        java.util.List<Player> realCandidates = new java.util.ArrayList<>();
        Player bestFake = null;
        double bestFakeDist = followRangeSq;

        for (Player p : mc.level.players()) {
            if (p == self || !p.isAlive() || p.isSpectator()) continue;
            boolean isFake = p instanceof FakePlayerEntity;
            if (p.isCreative() && !isFake) continue;

            double d = self.distanceToSqr(p);
            if (d >= followRangeSq) continue;

            if (isFake) {
                if (d < bestFakeDist) {
                    bestFakeDist = d;
                    bestFake = p;
                }
            } else {
                realCandidates.add(p);
            }
        }

        Player bestReal = smartTargeting.get() ? pickSmartTarget(self, realCandidates) : pickClosest(self, realCandidates);
        return bestReal != null ? bestReal : bestFake;
    }

    private Player pickClosest(Player self, java.util.List<Player> candidates) {
        Player best = null;
        double bestDist = Double.MAX_VALUE;
        for (Player p : candidates) {
            double d = self.distanceToSqr(p);
            if (d < bestDist) {
                bestDist = d;
                best = p;
            }
        }
        return best;
    }

    /** Bevorzugt ein isoliertes Ziel (kein zweiter Spieler in Rueckendeckungs-Reichweite) vor reiner
     *  Distanz - ein alleine stehender Gegner ist ein sichereres, schneller erledigtes Ziel als einer mit
     *  Unterstuetzung, selbst wenn er etwas weiter weg steht. Faellt bei Gleichstand (alle isoliert oder
     *  alle mit Begleitung) auf die naechste Distanz zurueck. */
    private Player pickSmartTarget(Player self, java.util.List<Player> candidates) {
        Player best = null;
        double bestScore = Double.MAX_VALUE;
        double backupRangeSq = backupRange.get() * backupRange.get();

        for (Player p : candidates) {
            double dist = Math.sqrt(self.distanceToSqr(p));
            boolean hasBackup = false;
            for (Player other : candidates) {
                if (other == p) continue;
                if (p.distanceToSqr(other) <= backupRangeSq) {
                    hasBackup = true;
                    break;
                }
            }

            double score = hasBackup ? dist + backupRange.get() : dist;
            if (score < bestScore) {
                bestScore = score;
                best = p;
            }
        }
        return best;
    }

    private LivingEntity findMobTarget(Player self) {
        double r = mobRange.get();
        AABB box = self.getBoundingBox().inflate(r);
        LivingEntity best = null;
        double bestDist = r * r;

        for (LivingEntity e : mc.level.getEntitiesOfClass(LivingEntity.class, box)) {
            if (e == self || !e.isAlive()) continue;
            if (!mobTypes.get().contains(e.getType())) continue;
            double d = self.distanceToSqr(e);
            if (d < bestDist) {
                bestDist = d;
                best = e;
            }
        }
        return best;
    }

    private LivingEntity findTarget(Player self) {
        if (mc.level == null) return null;

        // Bereits engagiertes Ziel bevorzugt behalten, solange es lebt und in Reichweite bleibt - sonst
        // kann waehrend eines laufenden Kampfes ein dritter, kurzzeitig naeherer/isolierterer Spieler
        // das Ziel mitten im Gefecht kapern (Ziel-Flackern statt einen Kampf durchzuziehen).
        if (engaged && engagedTargetId != null) {
            for (Player p : mc.level.players()) {
                if (!p.getUUID().equals(engagedTargetId)) continue;
                if (p.isAlive() && !p.isSpectator() && self.distanceToSqr(p) <= followRange.get() * (double) followRange.get()) {
                    return p;
                }
                break;
            }
        }

        LivingEntity best = findPlayerTarget(self);
        if (best == null && attackMobs.get()) best = findMobTarget(self);
        return best;
    }

    // ---------- Aktionen ----------

    /** W-Tap/Sprint-Reset: der erste Treffer nach (erneutem) Sprint-Start bekommt automatisch mehr
     *  Aufwaerts-Knockback. Sprint kurz aus-ein schalten, statt durchgehend zu sprinten, damit dieser
     *  Bonus bei JEDEM Treffer greift statt nur beim ersten einer laufenden Sprint-Sequenz. */
    private void manageSprintForKnockback(double dist) {
        if (!sprintReset.get() || dist > 4.5) return;

        if (sprintResetCooldown > 0) {
            sprintResetCooldown--;
            Input.setKeyState(mc.options.keySprint, false);
            mc.player.setSprinting(false);
            return;
        }
        Input.setKeyState(mc.options.keySprint, true);
        mc.player.setSprinting(true);
    }

    private void attackMelee(LivingEntity target) {
        if (drinkingFireRes) return;
        FindItemResult weapon = null;
        if (useMace.get() && mc.player.fallDistance > 1.5f) {
            weapon = InvUtils.findInHotbar(itemStack -> itemStack.getItem() instanceof MaceItem);
        }
        if ((weapon == null || !weapon.found()) && preferAxeMelee.get()) {
            weapon = InvUtils.findInHotbar(itemStack -> itemStack.getItem() instanceof AxeItem);
        }
        if (combatSlotReserved && (weapon == null || !weapon.found())) return;

        boolean wasSprinting = mc.player.isSprinting();
        Runnable hit = () -> {
            mc.gameMode.attack(mc.player, target);
            mc.player.swing(InteractionHand.MAIN_HAND);
        };
        if (weapon != null && weapon.found()) {
            if (!withCombatSlot(weapon, hit)) return;
        } else {
            hit.run();
        }

        if (sprintReset.get() && wasSprinting) sprintResetCooldown = 2;
    }

    private void breakShield(Player target) {
        if (drinkingFireRes) return;
        FindItemResult axe = InvUtils.findInHotbar(itemStack -> itemStack.getItem() instanceof AxeItem);
        if (!axe.found()) return;
        withCombatSlot(axe, () -> {
            mc.gameMode.attack(mc.player, target);
            mc.player.swing(InteractionHand.MAIN_HAND);
        });
    }

    private void throwPearl(LivingEntity aimAt, boolean away) {
        if (drinkingFireRes) return; // s.o. - Swap-Merkposten waehrend des Trinkens nicht anfassen
        FindItemResult pearl = InvHelper.find(Items.ENDER_PEARL);
        if (!pearl.found()) return;

        double yaw, pitch;
        if (away && aimAt != null) {
            yaw = Rotations.getYaw(aimAt) + 180.0;
            pitch = -35; // steilerer Bogen als vorher (-20 war zu flach - Perle blieb oft am Boden/Hindernis haengen)
        } else if (aimAt != null) {
            // Zwei Fehlerquellen, die beide eingerechnet werden muessen:
            // 1. Zielbewegung: die Perle braucht je nach Distanz 0.5-2s Flugzeit. Deshalb wird abwechselnd
            //    der Wurf fuer den aktuellen Zielpunkt geloest und der Zielpunkt mit der dabei berechneten
            //    Flugzeit neu vorhergesagt (konvergiert nach 2-3 Runden).
            // 2. EIGENbewegung: Minecraft addiert die Geschwindigkeit des Werfers auf die Perle
            //    (getKnownMovement(), Y nur wenn nicht am Boden). Der Bot wirft praktisch immer im Sprint,
            //    das sind bis zu 0.3 Bloecke/Tick gegen eine Wurfgeschwindigkeit von 1.5 - nachgerechnet
            //    im Mittel 2.68 Bloecke Zielfehler, 82% der Wuerfe mehr als einen Block daneben. Genau das
            //    war die Ursache der "Perlen liegen nicht akkurat"-Meldung; solvePearlAim korrigiert
            //    dafuer sowohl Pitch als auch Yaw.
            Vec3 from = mc.player.getEyePosition().subtract(0, 0.1, 0);
            Vec3 own = mc.player.getKnownMovement();
            Vec3 extraVel = new Vec3(own.x, mc.player.onGround() ? 0 : own.y, own.z);
            // predictOverTicks() arbeitet mit der rohen Entity-Position (Fuesse) - dieselbe Referenz wie
            // fuer D-Tap/Crystal-Bodensuche anderswo im File. Ohne diesen Offset wuerde jede verfeinerte
            // Iteration nach der ersten leise auf Fusshoehe statt Koerpermitte zielen (~0.9 Bloecke zu
            // niedrig) - genau das machte auch schon den ERSTEN Wurf (keine Zielbewegung noetig, aber die
            // Iteration laeuft trotzdem) systematisch zu flach/kurz.
            Vec3 centerOffset = aimAt.getBoundingBox().getCenter().subtract(aimAt.position());
            Vec3 aimPoint = aimAt.getBoundingBox().getCenter();
            double[] aim = PvpMath.solvePearlAim(from, aimPoint, extraVel);
            for (int i = 0; i < 2 && aim != null; i++) {
                aimPoint = predictOverTicks(aimAt, (int) Math.round(aim[2])).add(centerOffset);
                aim = PvpMath.solvePearlAim(from, aimPoint, extraVel);
            }
            if (aim == null) return; // ausserhalb der physischen Perlenreichweite - Perle sparen
            yaw = aim[0];
            pitch = aim[1];
            // Restliche Streuung ist Vanilla, kein Bug hier: Minecraft wirft EnderPearlItem serverseitig
            // mit shootFromRotation(..., inaccuracy=1.0F) - jeder Wurf bekommt eine kleine Zufallsstreuung
            // vom Server, unabhaengig vom Client-Aim.
        } else {
            return;
        }

        throwPearlAt(yaw, pitch);
    }

    /** Wirft eine Perle mit der aktuellen Blick-Yaw und dem angegebenen Pitch. Positive Werte zeigen
     *  nach unten, negative nach oben. */
    private boolean throwPearlAtCurrentYaw(double pitch) {
        return throwPearlAt(mc.player.getYRot(), pitch);
    }

    private boolean throwPearlAt(double yaw, double pitch) {
        if (drinkingFireRes) return false;
        FindItemResult pearl = InvHelper.find(Items.ENDER_PEARL);
        if (!pearl.found()) return false;

        InteractionHand hand = pearl.isOffhand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        boolean queued = queueWithCombatSlot(pearl, yaw, pitch, PRIORITY_PEARL,
            () -> mc.gameMode.useItem(mc.player, hand));
        if (queued) lastPearlTick = tickCounter;
        return queued;
    }

    /** Zaehlt in diesem Tick verschwundene oder entladene Anker, deren Explosion die eigene Hitbox
     *  tatsaechlich erreichen wuerde. Die beiden Karten werden bei jedem Scan getauscht, damit der
     *  Per-Tick-Check keine Map alloziert. */
    private int countDamagingAnchorExplosions(Player self) {
        scannedAnchorCharges.clear();
        BlockPos center = self.blockPosition();
        for (int dx = -4; dx <= 4; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -4; dz <= 4; dz++) {
                    BlockPos pos = center.offset(dx, dy, dz);
                    var state = mc.level.getBlockState(pos);
                    if (state.is(Blocks.RESPAWN_ANCHOR)) {
                        scannedAnchorCharges.put(pos, state.getValue(BlockStateProperties.RESPAWN_ANCHOR_CHARGES));
                    }
                }
            }
        }

        int explosions = 0;
        for (Map.Entry<BlockPos, Integer> entry : trackedAnchorCharges.entrySet()) {
            Integer current = scannedAnchorCharges.get(entry.getKey());
            if (entry.getValue() > 0 && (current == null || current < entry.getValue())
                && DamageUtils.anchorDamage(self, Vec3.atCenterOf(entry.getKey())) > 0) {
                explosions++;
            }
        }

        Map<BlockPos, Integer> previous = trackedAnchorCharges;
        trackedAnchorCharges = scannedAnchorCharges;
        scannedAnchorCharges = previous;
        return explosions;
    }

    /** Turtle-Master ist eine Item-Sequenz, kein serverbarer Buff-Schalter: Eine geladene
     *  Crossbow wird in die Offhand geparkt, fuer den Schuss kurz in die Mainhand verschoben und
     *  im selben Callback per useItem()+releaseUsingItem() senkrecht vor den Spieler abgefeuert. */
    private boolean handleTurtleDefense(Player self) {
        if (!turtleMasterDefense.get() || mc.gui.screen() != null
            || self.getHealth() > self.getMaxHealth() * turtleMasterHealth.get()
            || blocking || drinkingFireRes || combatSlotReserved || instaCityBlock != null) {
            turtleModeActive = false;
            return false;
        }

        FindItemResult loaded = InvUtils.find(this::isLoadedTurtleCrossbow);
        if (!loaded.found()) {
            turtleModeActive = false;
            return false;
        }
        turtleModeActive = true;

        ItemStack offhand = self.getItemInHand(InteractionHand.OFF_HAND);
        if (!isLoadedTurtleCrossbow(offhand)) {
            if (!loaded.isOffhand()) InvUtils.move().from(loaded.slot()).toOffhand();
            turtleSwitchReadyTick = tickCounter + 2;
            currentAction = "turtle-master-offhand";
            return false;
        }

        if (tickCounter < turtleSwitchReadyTick || tickCounter < turtleShotReadyTick) return false;
        int mainSlot = self.getInventory().getSelectedSlot();
        InvUtils.move().fromOffhand().toHotbar(mainSlot);
        if (!reserveCombatSlot(mainSlot)) {
            InvUtils.move().fromHotbar(mainSlot).toOffhand();
            return false;
        }

        boolean queued = rotateAndRun(self.getYRot(), 88.5, PRIORITY_MISC, () -> {
            try {
                if (isLoadedTurtleCrossbow(self.getMainHandItem())) {
                    mc.gameMode.useItem(self, InteractionHand.MAIN_HAND);
                    mc.gameMode.releaseUsingItem(self);
                    self.swing(InteractionHand.MAIN_HAND);
                }
            } finally {
                releaseCombatSlot();
                InvUtils.move().fromHotbar(mainSlot).toOffhand();
            }
        });
        if (queued) {
            turtleShotReadyTick = tickCounter + turtleMasterCooldown.get();
            currentAction = "turtle-master-bubbling";
        }
        return queued;
    }

    private boolean isLoadedTurtleCrossbow(ItemStack stack) {
        if (!stack.is(Items.CROSSBOW) || !CrossbowItem.isCharged(stack)) return false;
        var charged = stack.get(DataComponents.CHARGED_PROJECTILES);
        if (charged == null) return false;
        for (ItemStack projectile : charged.itemCopies()) {
            if (isTurtleMasterArrow(projectile)) return true;
        }
        return false;
    }

    private boolean isTurtleMasterArrow(ItemStack stack) {
        if (!stack.is(Items.TIPPED_ARROW)) return false;
        PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
        return contents != null && (contents.is(Potions.TURTLE_MASTER)
            || contents.is(Potions.LONG_TURTLE_MASTER) || contents.is(Potions.STRONG_TURTLE_MASTER));
    }

    private void ensureOffhandTotem() {
        ItemStack off = mc.player.getItemInHand(InteractionHand.OFF_HAND);
        if (off.is(Items.TOTEM_OF_UNDYING)) return;

        FindItemResult totem = InvUtils.find(Items.TOTEM_OF_UNDYING);
        if (totem.found()) {
            InvUtils.move().from(totem.slot()).toOffhand();
        } else if (!lowOnTotems) {
            // Sofort erkennen statt bis zu 20 Ticks (1s) auf den naechsten periodischen Inventar-Scan zu
            // warten - im Kampf ist "komplett ohne Totem" die kritischste Ressourcenluecke ueberhaupt.
            lowOnTotems = true;
            ChatUtils.info("§cKein Totem mehr verfuegbar - Offhand bleibt leer!");
        }
    }

    // ---------- Schutz / Falle ----------

    /** Cobweb an die Fuesse des Gegners - bremst ihn, bleibt aber voll treffbar (kein Kaefig). */
    private void handleTrap(LivingEntity target) {
        int mode = trapMode.get();
        if (mode == 0 || target == null) return;

        double dist = Math.sqrt(mc.player.distanceToSqr(target));
        if (mode == 1 && dist > 6) return;
        // Bereits in Nahkampf-Reichweite: Trap bringt nichts mehr (das Ziel muss ja nicht mehr
        // anlaufen) und der dafuer verbrauchte Rotations-Slot geht sonst zulasten des naechsten
        // Nahkampf-Schlags - genau das liess den Bot direkt neben einem regungslosen Ziel (z.B. Dummy)
        // ein Web setzen und dann sichtbar eine Weile untaetig wirken, statt sofort zuzuschlagen.
        if (dist <= attackRange.get()) return;
        if (mc.gui.screen() != null || tickCounter % 3 != 0) return;

        Vec3 targetVel = velocities.getOrDefault(target.getUUID(), Vec3.ZERO);

        // In der Luft (Sprung, Knockback, Anchor-/Crystal-Wurf) hat die AKTUELLE Position kein tragendes
        // Blockdarunter zum draufkleben - ohne Vorhersage versagte das Web hier komplett, bis der Gegner
        // wieder gelandet war. Dieselbe Kurzzeit-Vorhersage wie fuer D-Tap/Crystal-Platzierung nutzen,
        // damit das Web dort wartet, wo der Gegner voraussichtlich als naechstes ist.
        BlockPos feet = target.onGround() ? target.blockPosition() : BlockPos.containing(predict(target));
        if (feet.equals(mc.player.blockPosition())) return; // sonst web(t) sich der Bot bei Ueberlappung selbst ein
        if (!mc.level.getBlockState(feet).isAir()) return;
        if (!mc.level.getBlockState(feet.below()).blocksMotion()) return;

        // Nur ein Ziel bremsen, das sich ueberhaupt bewegt. Ein stehender Gegner (Dummy, campender
        // Spieler, jemand der gar nicht angreift) gewinnt durch das Web nichts fuer uns - es liegt dann
        // aber mitten in unserem eigenen Anmarschweg und bremst ausgerechnet den Bot aus, der da noch
        // hinlaufen will. Genau die Beobachtung aus dem Feld: "er perlt hin, setzt ein Web, danach
        // passiert nichts".
        if (targetVel.horizontalDistance() < 0.05) return;

        FindItemResult web = InvHelper.find(Items.COBWEB);
        if (web.found()) placeTrackedBlock(feet, web, true, 50);
    }

    // ---------- Totem-Pops / Inventar ----------

    private void trackPop(LivingEntity entity) {
        Float prev = lastHealth.put(entity.getUUID(), entity.getHealth());
        if (prev == null) return;

        float drop = prev - entity.getHealth();

        // Jeder spuerbare Treffer beim Gegner zaehlt als "Trade gelandet" - haelt fest, dass unsere
        // eigenen Explosionen/Schlaege tatsaechlich ankommen (fuer die Verlorener-Trade-Erkennung).
        if (entity != mc.player && drop >= 0.5f && entity.isAlive()) {
            lastTargetDamageTick = tickCounter;
        }

        if (drop >= popThreshold.get() && entity.isAlive()) {
            int count = pops.merge(entity.getUUID(), 1, Integer::sum);
            String name = entity == mc.player ? "Du" : entity.getName().getString();
            ChatUtils.info("Totem-Pop #%d bei %s (%.1f HP)", count, name, entity.getHealth());

            if (entity != mc.player) popBurstUntil = tickCounter + 16;
            else lastSelfPopTick = tickCounter; // wir selbst wurden hart getroffen (typischerweise Crystal/Anchor)
        }

        // Combo-Fenster: nach jedem spuerbaren Treffer sofort auf JEDE andere Explosions-Art pruefen
        // (nicht nur Anchor), statt die normale Umschalt-Sperre abzuwarten - Anchor+Crystal+Bett
        // Doppel-Schaden ausnutzen. Beide Kandidaten-Caches invalidieren, sonst bliebe z.B. nach einem
        // Anchor-Treffer die Bett-Bewertung auf einem veralteten Stand haengen, obwohl der Kommentar
        // "jeweils andere Aura-Art" beide Alternativen meint, nicht nur Anchor.
        if (entity != mc.player && drop >= 3.0f && entity.isAlive()) {
            triggerComboRecheck();
        }
    }

    /** Zwingt selectAura() beim naechsten Aufruf zu einer sofortigen Neubewertung ALLER
     *  Explosions-Alternativen (Anchor- und Bett-Kandidatenliste), statt auf die normale
     *  Umschalt-Hysterese/Cache-Gueltigkeit zu warten - siehe trackPop() fuer den Ausloeser. */
    private void triggerComboRecheck() {
        lastAuraSwitch = -999;
        anchorCandidateIndex = anchorCandidates.size();
        bedCandidateIndex = bedCandidates.size();
    }

    /** Praezise Totem-Erkennung ueber die drei Effekte, die ein Totem-Pop garantiert vergibt
     *  (Regeneration + Absorption + Fire Resistance gleichzeitig neu) - zuverlaessiger als ein reiner
     *  HP-Sprung-Heuristik-Wert. Meldet nur fremde Spieler im Chat, nicht den eigenen Pop. */
    private void trackTotemEffect(LivingEntity entity) {
        boolean has = entity.hasEffect(MobEffects.REGENERATION)
            && entity.hasEffect(MobEffects.ABSORPTION)
            && entity.hasEffect(MobEffects.FIRE_RESISTANCE);
        Boolean had = hadTotemEffects.put(entity.getUUID(), has);

        if (has && (had == null || !had) && entity != mc.player) {
            ChatUtils.info("§c⚠ %s hat gerade ein Totem gepoppt!", entity.getName().getString());
        }
    }

    private int countHotbar(net.minecraft.world.item.Item item) {
        int n = 0;
        for (int i = 0; i <= 8; i++) {
            ItemStack s = mc.player.getInventory().getItem(i);
            if (s.is(item)) n += s.getCount();
        }
        return n;
    }

    private int countHotbar(java.util.function.Predicate<ItemStack> pred) {
        int n = 0;
        for (int i = 0; i <= 8; i++) {
            ItemStack s = mc.player.getInventory().getItem(i);
            if (pred.test(s)) n += s.getCount();
        }
        return n;
    }

    private int totalItem(net.minecraft.world.item.Item item) {
        FindItemResult r = InvUtils.find(item);
        return r.found() ? r.count() : 0;
    }

    private int totalItem(java.util.function.Predicate<ItemStack> pred) {
        FindItemResult r = InvUtils.find(pred);
        return r.found() ? r.count() : 0;
    }

    private boolean hasActionableItem(net.minecraft.world.item.Item item) {
        FindItemResult result = InvUtils.find(item);
        return result.found() && (result.isHotbar() || result.isOffhand());
    }

    private boolean hasActionableItem(java.util.function.Predicate<ItemStack> predicate) {
        FindItemResult result = InvUtils.find(predicate);
        return result.found() && (result.isHotbar() || result.isOffhand());
    }

    private int findMainSlotWith(net.minecraft.world.item.Item item) {
        for (int i = 9; i <= 35; i++) {
            if (mc.player.getInventory().getItem(i).is(item)) return i;
        }
        return -1;
    }

    private int findMainSlotWith(java.util.function.Predicate<ItemStack> pred) {
        for (int i = 9; i <= 35; i++) {
            if (pred.test(mc.player.getInventory().getItem(i))) return i;
        }
        return -1;
    }

    private int hotbarTargetSlot(net.minecraft.world.item.Item item) {
        for (int i = 0; i <= 8; i++) {
            ItemStack s = mc.player.getInventory().getItem(i);
            if (s.isEmpty()) return i;
            if (s.is(item) && s.getCount() < s.getMaxStackSize()) return i;
        }
        return -1;
    }

    private int hotbarTargetSlot(java.util.function.Predicate<ItemStack> pred) {
        for (int i = 0; i <= 8; i++) {
            ItemStack s = mc.player.getInventory().getItem(i);
            if (s.isEmpty()) return i;
            if (pred.test(s) && s.getCount() < s.getMaxStackSize()) return i;
        }
        return -1;
    }

    private void refill(net.minecraft.world.item.Item item, int min) {
        if (countHotbar(item) >= min) return;
        if (totalItem(item) < min) return;

        int src = findMainSlotWith(item);
        int dst = hotbarTargetSlot(item);
        if (src < 0 || dst < 0) return;

        InvUtils.move().from(src).to(dst);
    }

    /** Predicate-Variante fuer Ressourcen, die nicht ueber einen einzelnen Item-Typ erfassbar sind (Betten
     *  gibt es in 16 Farben, Heiltraenke sind ueber ihren Effekt statt ihren Item-Typ definiert). Nutzt
     *  wie die Item-Variante die tatsaechliche Stapelgroesse jedes Slots (ItemStack.getMaxStackSize()) -
     *  funktioniert daher unveraendert bei serverseitig erweiterten Staples (z.B. 5b5ts 64er-Betten/
     *  -Traenke statt Vanillas 1), ohne dass hier irgendwo "max 1" angenommen wird. */
    private void refill(java.util.function.Predicate<ItemStack> pred, int min) {
        if (countHotbar(pred) >= min) return;
        if (totalItem(pred) < min) return;

        int src = findMainSlotWith(pred);
        int dst = hotbarTargetSlot(pred);
        if (src < 0 || dst < 0) return;

        InvUtils.move().from(src).to(dst);
    }

    /** Raeumt einen Hotbar-Slot frei, indem ein dort liegender Ballast-Stack ins Hauptinventar
     *  zurueckgeschoben wird. NOETIG, weil InvUtils.swap() ausschliesslich mit HOTBAR-Slots
     *  arbeitet: liegt eine Kampfressource nur im Hauptinventar, ist sie fuer Platzierung/Wurf
     *  faktisch nicht vorhanden. Genau das ist live passiert - nach einem Nether-Abschnitt
     *  blockierten uebrig gebliebene Betten und leere Glasflaschen (Reste der Fire-Res-Traenke)
     *  die komplette Hotbar, waehrend Crystals/Obsidian/Anchor/Glowstone auf den Haupt-Slots
     *  12-15 lagen: der Bot hatte volle Vorraete und platzierte trotzdem 95 Sekunden lang NULL
     *  Explosive (nur Nahkampf + Heiltraenke, live gemessen).
     *
     *  Ballast = leere Glasflaschen (reines Trank-Abfallprodukt) plus die Sprengmittel, die in der
     *  AKTUELLEN Dimension gar nicht explodieren koennen: Betten in der Oberwelt, Respawn Anchors im
     *  Nether (dort sind sie ein funktionierender Spawn-Block, siehe anchorsExplodeHere). Beides wird
     *  nur verschoben, nie geworfen - ein Dimensionswechsel macht sie sofort wieder zur Hauptwaffe.
     *  @return true, wenn ein Slot freigeraeumt wurde. */
    private boolean evictHotbarBallast() {
        boolean bedsUseless = !bedsExplodeHere();
        boolean anchorsUseless = !anchorsExplodeHere();
        for (int i = 0; i <= 8; i++) {
            ItemStack s = mc.player.getInventory().getItem(i);
            if (s.isEmpty()) return false; // schon Platz - nichts zu raeumen
            boolean ballast = s.is(Items.GLASS_BOTTLE)
                || (bedsUseless && isBed(s))
                || (anchorsUseless && s.is(Items.RESPAWN_ANCHOR));
            if (!ballast) continue;

            int dst = findFreeMainSlot();
            if (dst < 0) dst = findMainMergeSlot(s);
            if (dst < 0) return false;
            InvUtils.move().from(i).to(dst);
            return true;
        }
        return false;
    }

    /** Erster freier Slot im Hauptinventar (9-35) fuer evictHotbarBallast(). */
    private int findFreeMainSlot() {
        for (int i = 9; i <= 35; i++) {
            if (mc.player.getInventory().getItem(i).isEmpty()) return i;
        }
        return -1;
    }

    private int findMainMergeSlot(ItemStack stack) {
        for (int i = 9; i <= 35; i++) {
            ItemStack destination = mc.player.getInventory().getItem(i);
            if (ItemStack.isSameItemSameComponents(destination, stack) && destination.getCount() < destination.getMaxStackSize()) {
                return i;
            }
        }
        return -1;
    }

    private void inventoryTick(Player self) {
        // Erst Platz schaffen, dann nachfuellen - sonst findet hotbarTargetSlot() nie einen Slot
        // und jeder refill() unten laeuft wirkungslos ins Leere (siehe evictHotbarBallast).
        evictHotbarBallast();

        refill(Items.END_CRYSTAL, minCrystals.get());
        // Nur nachfuellen, was hier auch explodiert - sonst schiebt refill() genau den Stack zurueck in
        // die Hotbar, den evictHotbarBallast() eine Zeile vorher als Ballast herausgeraeumt hat
        // (endloses Hin-und-Her alle 20 Ticks, das dauerhaft einen Hotbar-Slot der echten Waffen belegt).
        if (anchorsExplodeHere()) {
            refill(Items.RESPAWN_ANCHOR, minAnchors.get());
            refill(Items.GLOWSTONE, minGlowstone.get());
        }
        refill(Items.ENDER_PEARL, minPearls.get());
        refill(Items.OBSIDIAN, minObsidian.get());
        refill(Items.COBWEB, minWeb.get());
        if (bedsExplodeHere()) refill(GodmodePvP::isBed, minBeds.get());
        refill(GodmodePvP::isHealingSplash, minHealPotionsStock.get());
        if (turtleMasterDefense.get()) refill(stack -> isTurtleMasterArrow(stack), 8);
        // Piston-Aura-Bauteile in die Hotbar nachziehen. refill() tut nichts, wenn gar keine da
        // sind - wer ohne Kolben/Redstone spielt, verliert dadurch also keinen Hotbar-Slot.
        refill(Items.PISTON, 1);
        refill(Items.REDSTONE_BLOCK, 1);

        int totems = totalItem(Items.TOTEM_OF_UNDYING);
        if (totems < 6 && !warnedLowTotems) {
            warnedLowTotems = true;
            ChatUtils.info("Nur noch %d Totems im Inventar!", totems);
        }
        if (totems >= 10) warnedLowTotems = false;
        lowOnTotems = totems < 2;

        // Klar erkennbar machen, WARUM keine Explosionen mehr kommen - statt dass es wie ein Bug aussieht
        boolean noCrystals = totalItem(Items.END_CRYSTAL) == 0;
        if (noCrystals && !warnedOutOfCrystals) {
            warnedOutOfCrystals = true;
            ChatUtils.info("Keine End Crystals mehr im Inventar - nur noch Nahkampf/Web moeglich!");
        }
        if (!noCrystals) warnedOutOfCrystals = false;

        // "Anchor nicht verfuegbar" heisst hier bewusst auch "explodiert in dieser Dimension nicht" -
        // dieses Flag ist zugleich die Rueckzugs-Bedingung (siehe handleTargeting), und im Nether ist ein
        // voller Anchor-Vorrat kampftechnisch exakt so viel wert wie gar keiner. Die Chat-Warnung kommt
        // trotzdem nur im echten Mangelfall, sonst wuerde sie bei jedem Nether-Besuch faelschlich melden.
        boolean anchorUnusable = !anchorsExplodeHere()
            || totalItem(Items.RESPAWN_ANCHOR) == 0 || totalItem(Items.GLOWSTONE) == 0;
        if (anchorUnusable && !warnedOutOfAnchorSupply) {
            warnedOutOfAnchorSupply = true;
            if (anchorsExplodeHere()) ChatUtils.info("Kein Respawn Anchor oder Glowstone mehr im Inventar - Anchor-Modus pausiert!");
        }
        if (!anchorUnusable) warnedOutOfAnchorSupply = false;

        warnIfEmpty(Items.ENDER_PEARL, "Enderperlen");
        warnIfEmpty(Items.OBSIDIAN, "Obsidian");
    }

    /** Generische Einmal-Warnung (pro Item), wenn eine wichtige Ressource komplett aufgebraucht ist -
     *  wird automatisch wieder scharf, sobald wieder welche im Inventar sind. */
    private void warnIfEmpty(net.minecraft.world.item.Item item, String label) {
        boolean empty = totalItem(item) == 0;
        Boolean was = warnedOutOfMisc.get(item);
        if (empty && (was == null || !was)) {
            warnedOutOfMisc.put(item, true);
            ChatUtils.info("Kein(e) %s mehr im Inventar!", label);
        } else if (!empty) {
            warnedOutOfMisc.put(item, false);
        }
    }

    // ---------- Kopplung mit Meteor-Modulen ----------

    private void syncMobFilter() {
        if (mobTypes.get().isEmpty()) return;
        syncEntityFilter(Modules.get().get(KillAura.class));
        syncEntityFilter(Modules.get().get(CrystalAura.class));
    }

    @SuppressWarnings("unchecked")
    private void syncEntityFilter(Module mod) {
        if (mod == null) return;

        try {
            java.lang.reflect.Field f = mod.getClass().getDeclaredField("entities");
            f.setAccessible(true);
            Setting<java.util.Set<EntityType<?>>> s = (Setting<java.util.Set<EntityType<?>>>) f.get(mod);
            if (s != null) s.set(new HashSet<>(mobTypes.get()));
        } catch (NoSuchFieldException ignored) {
        } catch (Throwable ignored) {
        }
    }


    private void syncSupport(CrystalAura ca) {
        try {
            java.lang.reflect.Field f = CrystalAura.class.getDeclaredField("support");
            f.setAccessible(true);
            @SuppressWarnings("unchecked")
            Setting<CrystalAura.SupportMode> s = (Setting<CrystalAura.SupportMode>) f.get(ca);
            if (s != null) {
                savedSupport = s.get();
                if (s.get() == CrystalAura.SupportMode.Disabled) s.set(CrystalAura.SupportMode.Fast);
                supportSyncFailed = false;
            } else {
                // Feld existiert, aber die Meteor-Version liefert keinen Setting-Wert zurueck - genauso
                // unzuverlaessig wie eine geworfene Exception, also denselben Fallback-Pfad nehmen.
                supportSyncFailed = true;
            }
        } catch (Throwable t) {
            savedSupport = null;
            // Nicht nur einmalig chatten: solange die Sync fehlschlaegt, kann sich CrystalAura im
            // Crystal-Modus nicht selbst mit Obsidian unterbauen - freie-Luft-Zellen (siehe
            // validExplosionSpot) sind dann faktisch nie bespielbar. supportSyncFailed bleibt aktiv
            // gesetzt (sichtbar im HUD via getInfoString(), siehe unten) und laesst wantAnchor()
            // Anchor aktiv bevorzugen, solange verfuegbar - sicherer Fallback statt sich auf einen
            // Crystal-Modus zu verlassen, der real nicht mehr voll funktioniert.
            supportSyncFailed = true;
            error("CrystalAura-Support-Mode konnte nicht gesetzt werden (Meteor-Version geaendert?) - Obsidian-Unterbau bei freier Luft laeuft evtl. nicht automatisch. Weiche auf Anchor-Vorzug aus, solange das so bleibt.");
        }

        // support-delay: der Tickabstand zwischen Obsidian-Platzierung und dem folgenden Crystal-Versuch.
        // Bei 0 schickt CrystalAura beide Pakete im selben Tick - auf Servern mit spuerbarer Latenz kann
        // der Crystal-Versuch dann ankommen, bevor der Server das Obsidian ueberhaupt registriert hat, und
        // wird lautlos abgelehnt (Hitbox/Vorschau erscheint, aber kein Crystal). Nur anheben, nie senken.
        try {
            java.lang.reflect.Field f = CrystalAura.class.getDeclaredField("supportDelay");
            f.setAccessible(true);
            @SuppressWarnings("unchecked")
            Setting<Integer> s = (Setting<Integer>) f.get(ca);
            if (s != null) {
                savedSupportDelay = s.get();
                if (s.get() < minSupportDelay.get()) s.set(minSupportDelay.get());
            }
        } catch (Throwable t) {
            savedSupportDelay = -1;
            error("CrystalAura-Support-Delay konnte nicht gesetzt werden (Meteor-Version geaendert?) - Crystal-Platzierung nach Obsidian-Unterbau kann dadurch auf langsameren Servern manchmal fehlschlagen.");
        }
    }

    private void restoreSupport(CrystalAura ca) {
        if (savedSupport != null) {
            try {
                java.lang.reflect.Field f = CrystalAura.class.getDeclaredField("support");
                f.setAccessible(true);
                @SuppressWarnings("unchecked")
                Setting<CrystalAura.SupportMode> s = (Setting<CrystalAura.SupportMode>) f.get(ca);
                if (s != null) s.set(savedSupport);
            } catch (Throwable ignored) {
            }
            savedSupport = null;
        }

        if (savedSupportDelay >= 0) {
            try {
                java.lang.reflect.Field f = CrystalAura.class.getDeclaredField("supportDelay");
                f.setAccessible(true);
                @SuppressWarnings("unchecked")
                Setting<Integer> s = (Setting<Integer>) f.get(ca);
                if (s != null) s.set(savedSupportDelay);
            } catch (Throwable ignored) {
            }
            savedSupportDelay = -1;
        }
    }

    /** Zieht Meteors eigene "durch Waende"-Reichweiten auf die volle Reichweite hoch, wenn through-walls
     *  an ist. CrystalAura und KillAura fuehren jeweils ein eigenes walls-range, das UNABHAENGIG von
     *  unseren Sichtpruefungen greift (CrystalAura: Kandidat hinter einer Wand wird nur akzeptiert, wenn
     *  er innerhalb walls-range liegt; KillAura-Standard ist mit 3.5 sogar kleiner als die normale
     *  Reichweite). Ohne diesen Abgleich bleibt "durch Waende" auf halber Strecke stehen: unsere Logik
     *  liefert Ziele hinter Deckung, Meteor verwirft sie danach wieder. Alte Werte werden gemerkt und
     *  beim Ausschalten des Moduls zurueckgesetzt. */
    private void syncWallsRange() {
        if (!throughWalls.get()) {
            restoreWallsRange();
            return;
        }
        syncWallsField(CrystalAura.class, Modules.get().get(CrystalAura.class), "placeWallsRange", "placeRange", 0);
        syncWallsField(CrystalAura.class, Modules.get().get(CrystalAura.class), "breakWallsRange", "breakRange", 1);
        syncWallsField(KillAura.class, Modules.get().get(KillAura.class), "wallsRange", "range", 2);
    }

    /** Setzt ein walls-range-Feld auf den Wert des zugehoerigen normalen Reichweiten-Feldes. */
    private void syncWallsField(Class<?> owner, Module module, String wallsField, String rangeField, int saveSlot) {
        if (module == null) return;
        try {
            java.lang.reflect.Field wf = owner.getDeclaredField(wallsField);
            java.lang.reflect.Field rf = owner.getDeclaredField(rangeField);
            wf.setAccessible(true);
            rf.setAccessible(true);
            @SuppressWarnings("unchecked")
            Setting<Double> walls = (Setting<Double>) wf.get(module);
            @SuppressWarnings("unchecked")
            Setting<Double> range = (Setting<Double>) rf.get(module);
            if (walls == null || range == null) return;
            if (savedWallsRange[saveSlot] < 0) savedWallsRange[saveSlot] = walls.get();
            if (walls.get() < range.get()) walls.set(range.get());
        } catch (Throwable t) {
            // Meteor-Version hat die Felder umbenannt: kein Grund abzubrechen - unsere eigenen
            // Sichtpruefungen sind trotzdem aus, nur Meteors Zusatzbremse bleibt dann bestehen.
            savedWallsRange[saveSlot] = -1;
        }
    }

    private void restoreWallsRange() {
        restoreWallsField(CrystalAura.class, Modules.get().get(CrystalAura.class), "placeWallsRange", 0);
        restoreWallsField(CrystalAura.class, Modules.get().get(CrystalAura.class), "breakWallsRange", 1);
        restoreWallsField(KillAura.class, Modules.get().get(KillAura.class), "wallsRange", 2);
    }

    private void restoreWallsField(Class<?> owner, Module module, String wallsField, int saveSlot) {
        if (module == null || savedWallsRange[saveSlot] < 0) return;
        try {
            java.lang.reflect.Field wf = owner.getDeclaredField(wallsField);
            wf.setAccessible(true);
            @SuppressWarnings("unchecked")
            Setting<Double> walls = (Setting<Double>) wf.get(module);
            if (walls != null) walls.set(savedWallsRange[saveSlot]);
        } catch (Throwable ignored) {
        }
        savedWallsRange[saveSlot] = -1;
    }

    private void tuneAutoMend() {
        Module am = Modules.get().get(AutoMend.class);
        if (am == null) return;

        try {
            java.lang.reflect.Field ad = AutoMend.class.getDeclaredField("autoDisable");
            ad.setAccessible(true);
            @SuppressWarnings("unchecked")
            Setting<Boolean> s = (Setting<Boolean>) ad.get(am);
            if (s != null) s.set(false);
        } catch (Throwable ignored) {
        }

        try {
            java.lang.reflect.Field cf = Module.class.getField("chatFeedback");
            cf.setAccessible(true);
            cf.set(am, false);
        } catch (Throwable ignored) {
        }
    }

    private void safeEnable(Modules m, Class<? extends Module> clazz) {
        Module mod = m.get(clazz);
        if (mod != null && !mod.isActive()) mod.toggle();
    }

    private void safeDisable(Modules m, Class<? extends Module> clazz) {
        Module mod = m.get(clazz);
        if (mod != null && mod.isActive()) mod.toggle();
    }
}
