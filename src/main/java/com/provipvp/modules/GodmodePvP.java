package com.provipvp.modules;

import com.provipvp.util.InvHelper;
import com.provipvp.util.PvpMath;
import com.provipvp.crystal.AttackGate;
import com.provipvp.crystal.CrystalOwnership;
import com.provipvp.crystal.CrystalScorer;
import com.provipvp.crystal.CrystalToolPolicy;
import com.provipvp.crystal.SelfDamageExposure;
import com.provipvp.crystal.SelfDamageGuard;
import com.provipvp.mechanics.KnockbackModel;
import com.provipvp.mechanics.ShieldWindow;
import com.provipvp.mechanics.SlowFallingArrow;
import com.provipvp.mechanics.SpearModel;
import com.provipvp.mechanics.WindChargeModel;
import com.provipvp.net.ActionCadence;
import com.provipvp.net.AttackDispatcher;
import com.provipvp.net.TickRateGate;
import com.provipvp.net.TotemEventReader;
import com.provipvp.ray.ActionRayValidator;
import com.provipvp.ray.PlaceCursorSolver;
import com.provipvp.ray.ReachPolicy;
import com.provipvp.rotation.GcdRotator;
import com.provipvp.util.RandomBetween;

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
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
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
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.BedItem;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.TridentItem;
import java.util.Set;
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

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.IdentityHashMap;
import java.util.Random;
import java.util.UUID;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class GodmodePvP extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgCombat = settings.createGroup("1 · Angriff & Auras");
    private final SettingGroup sgDefense = settings.createGroup("2 · Schutz & Recovery");
    private final SettingGroup sgCity = settings.createGroup("3 · Stadt & Traps");
    private final SettingGroup sgNavigation = settings.createGroup("4 · Navigation");
    private final SettingGroup sgInv = settings.createGroup("5 · Inventar");
    private final SettingGroup sgTurtle = settings.createGroup("6 · Turtle-Master");
    private final SettingGroup sgPearl = settings.createGroup("7 · Perlen & Flucht");
    private final SettingGroup sgHeal = settings.createGroup("8 · Heilung");
    private final SettingGroup sgQA = settings.createGroup("9 · QA & Erweitert");
    private final SettingGroup sgMobs = settings.createGroup("10 · Mobs");

    // General
    public final Setting<Boolean> follow = sgNavigation.add(new BoolSetting.Builder()
        .name("follow")
        .description("Verfolgt das Ziel automatisch mit Baritone, sobald es in Engage-Distanz ist oder dort stillsteht.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Integer> followRange = sgNavigation.add(new IntSetting.Builder()
        .name("follow-range")
        .description("Maximale Distanz, ab der ein Spieler ueberhaupt als Ziel erkannt/beobachtet wird.")
        .defaultValue(48)
        .range(8, 64)
        .sliderRange(8, 48)
        .build()
    );

    public final Setting<Integer> engageDistance = sgNavigation.add(new IntSetting.Builder()
        .name("engage-distance")
        .description("Erst ab dieser Distanz laeuft/perlt der Bot aktiv auf das Ziel zu. Darueber hinaus (bis follow-range) wird nur beobachtet/anvisiert, ohne loszurennen - verhindert, dass der Bot beim Aktivieren quer ueber die Karte auf jeden Spieler zusprintet.")
        .defaultValue(40)
        .range(4, 64)
        .sliderRange(4, 40)
        .build()
    );

    public final Setting<Boolean> pursueStationaryTargets = sgNavigation.add(new BoolSetting.Builder()
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
        .description("Bevorzugt bei der Zielwahl einen isolierten Gegner (ohne Mitspieler in Rueckendeckungs-Reichweite) vor reiner Distanz - ein alleine stehender Spieler ist ein sichereres, schnelleres Ziel als einer mit Unterstuetzung. Waehrend eines Engagements wird die Auswahl zusaetzlich alle 40 Ticks neu bewertet, damit ein neu hinzugekommener oder Deckung verlierender Gegner beruecksichtigt wird.")
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

    public final Setting<Boolean> ignoreFire = sgNavigation.add(new BoolSetting.Builder()
        .name("ignore-fire")
        .description("Ignoriert Feuer am Boden im Nahbereich - laeuft geradewegs hindurch statt drumherum zu pathen. Baritone haelt Feuer sonst hart fuer unpassierbar (macht Umweg oder bleibt stehen).")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> freeLook = sgQA.add(new BoolSetting.Builder()
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
        .defaultValue(true)
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

    public final Setting<Double> bedSelfDamageMultiplier = sgCombat.add(new DoubleSetting.Builder()
        .name("bed-self-damage-multiplier")
        .description("Multiplikator fuer den berechneten Eigenschaden bei Bett-Explosionen. Paper/Spigot-Server haben oft geaenderte Explosionsmechanik (2-3x hoeherer Self-Damage als Vanilla). Erhoehen, wenn Bed Aura kaum platziert, obwohl rechnerisch self-safe. 1.0 = Vanilla, 2.0-3.0 typisch fuer Paper/Spigot.")
        .defaultValue(1.0)
        .range(0.5, 5.0)
        .sliderRange(0.5, 5.0)
        .visible(useBeds::get)
        .build()
    );

    public final Setting<Double> balanceResources = sgCombat.add(new DoubleSetting.Builder()
        .name("balance-resources")
        .description("Gleicht den Verbrauch der drei Explosiv-Ressourcen (Crystal/Anchor/Bett) aus: liegen zwei Optionen im Schaden dicht beieinander, gewinnt die, die bisher WENIGER verbraucht wurde (gezaehlt ueber Inventar-Deltas). Der Wert ist der maximale Schadens-Bonus (in HP), den diese Bevorzugung vergeben darf - ein echter Schadensvorsprung schlaegt den Ausgleich immer. 0 = aus. Gemessener Effekt (je drei 90s-Fenster): Bett-Anteil im Nether 14% -> 23-26%, Anchor-Anteil in der Oberwelt 15% -> 26%. Das ist ein bewusster Tausch, kein Gratis-Gewinn: eine Bett-/Anchor-Zuendung dauert laenger als ein Crystal-Zyklus, die reine Explosionszahl pro Sekunde sinkt dabei also leicht. Hoeher stellen, wenn die Betten/Anchors trotzdem liegen bleiben - bei min-support-delay 1 ist der Crystal-Zyklus so schnell, dass 1.5 HP Bonus im Nether kaum noch durchschlaegt.")
        .defaultValue(2.0)
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

    public final Setting<Boolean> throughWalls = sgQA.add(new BoolSetting.Builder()
        .name("through-walls")
        .description("Greift auch durch Waende/Deckung an: die eigenen Sichtlinien-Sperren fuer Crystal-, Anchor- und Bett-Plaetze entfallen, ebenso die Sichtpruefung vor dem Nahkampf. Vanilla prueft serverseitig NUR die Distanz (ca. 3 Bloecke fuer Nahkampf, 4.5 fuer Platzieren), keine Sichtlinie - ein Schlag oder eine Platzierung hinter einer duennen Wand geht also wirklich durch. Zusaetzlich werden Meteors CrystalAura und KillAura auf volle Reichweite-durch-Waende gestellt (walls-range = range), sonst bremst deren eigene, niedrigere Wanddistanz alles wieder aus. Wovon das bewusst NICHT gilt: Enderperlen - die fliegen physikalisch gegen die Wand, dort bleibt die Sichtpruefung aktiv.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> preHit = sgCombat.add(new BoolSetting.Builder()
        .name("pre-hit")
        .description("Schlaegt den Gegner vor der Explosion fuer mehr Schaden. Off by default - Vanillas Angriffs-Cooldown (~0.5-0.6s je nach Waffe) ist gegen ein Anchor/Crystal-Sperrfeuer reine Zeitverschwendung; ohne diesen Extra-Hit koennen Anchor und Crystal so schnell hintereinander gezuendet werden, wie der Server sie verarbeitet.")
        .defaultValue(true)
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

    public final Setting<Boolean> zeroDelay = sgQA.add(new BoolSetting.Builder()
        .name("zero-delay")
        .description("Setzt CrystalAuras Platzierungs- und Break-Delays auf 0 und aktiviert Fast-Break. Der Angriff erfolgt, sobald der Server die reale Entity-ID des neu gespawnten Crystals liefert; eine vorab geratene Server-ID waere nicht adressierbar.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> ghostBlockMitigation = sgQA.add(new BoolSetting.Builder()
        .name("ghost-block-mitigation")
        .description("Verfolgt eigene Blockplatzierungen bis zum Server-Blockupdate. Bleibt die lokale Vorhersage nach zweimaligem Roundtrip sichtbar, wird nur die falsche Client-Hitbox entfernt und Baritones Weltcache neu geladen; es wird kein erfundenes Desync-Paket gesendet.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Integer> minSupportDelay = sgQA.add(new IntSetting.Builder()
        .name("min-support-delay")
        .description("Mindest-Tickabstand zwischen Obsidian-Unterbau und dem folgenden Crystal-Platzieren (CrystalAuras 'support-delay'). Beide Aktionen nutzen Minecrafts eigenes sequenznummer-basiertes Block-Vorhersage-System (seit 1.19) - schickt man beide zu dicht hintereinander raus, bevor die erste Sequenz vom Server bestaetigt ist, kann die Vorhersage durcheinanderkommen ('Crystal-Hitbox erscheint, aber kein Crystal kommt'). Steht auf Meteors eigenem Standard (1), weil dieser Wert der schaerfste Aggressions-Hebel ueberhaupt ist: live A/B ueber je drei 90s-Fenster hat 4 -> 1 die Explosionen pro Sekunde im Nether von 3.3-4.3 auf 5.3 und in der Oberwelt von 3.2 auf 4.3 gehoben (+33 bis +60%), bei ~+31% ausgeteiltem Schaden. Auf Servern mit spuerbarer Latenz oder Versions-Uebersetzung (z.B. ViaVersion) bei Fehlplatzierungen wieder hochdrehen - der Wert wird nur angehoben, nie unter diesen Mindestwert gesenkt.")
        .defaultValue(1)
        .range(0, 10)
        .sliderRange(0, 10)
        .build()
    );

    public final Setting<Boolean> instantMode = sgQA.add(new BoolSetting.Builder()
        .name("no-delay")
        .description("Sofort-Modus: hebelt alle verbleibenden kuenstlichen Wartezeiten aus (Anchor/Bett-Platzierungs- und Wartungspausen, D-Tap-Cooldown, alle Perlwurf-Cooldowns). Reine Geschwindigkeit statt Vorsicht - kann Perlen/Anchors/Betten verschwenden, wenn Aktionen schneller abgefeuert werden als der Server sie verarbeitet. Zwei Ausnahmen BLEIBEN aktiv, weil sie keine Vorsicht sondern technische Notwendigkeit sind: die Aura-Umschalt-Traegheit (ohne sie faengt sich CrystalAura nie ein stabiles Fenster zum tatsaechlichen Platzieren, wurde beim Testen zu 0 Schaden in JEDER Form) und min-support-delay (ohne die Untergrenze schlaegt die Server-Sequenznummer-Vorhersage fehl, Crystal kommt nie an - selbes Symptom).")
        .defaultValue(false)
        .build()
    );

    public final Setting<Boolean> killAuraOn = sgQA.add(new BoolSetting.Builder()
        .name("kill-aura")
        .description("Zusaetzlich KillAura fuer Nahkampf. Mob-Filter wird automatisch aus 'Mobs' uebernommen. Standard aus - eigener Axt-Nahkampf aktiv.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> escapePearl = sgPearl.add(new BoolSetting.Builder()
        .name("escape-pearl")
        .description("Perlen-Flucht bei niedrigem HP und nahem Gegner.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> knockbackPearl = sgPearl.add(new BoolSetting.Builder()
        .name("knockback-pearl")
        .description("Wenn der Bot durch Knockback (Schlag/Explosion) in die Luft geschleudert wird ODER generell gerade in einem gefaehrlichen Fall steckt (z.B. von einer Kante), sofort senkrecht nach unten perlen - kommt kontrolliert runter statt Fallschaden zu nehmen oder als Ziel in der Luft zu haengen.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> antiAnchorDisengage = sgPearl.add(new BoolSetting.Builder()
        .name("anti-anchor-disengage")
        .description("Erkennt mindestens drei schadenswirksame Anchor-Explosionen innerhalb einer Sekunde. Steckt der Bot dabei in einem offenen 1x1-Loch, wirft er eine fast senkrechte Perle nach oben, um aus dem Blast-Radius zu kommen.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> explosionFloorSnap = sgPearl.add(new BoolSetting.Builder()
        .name("explosion-floor-snap")
        .description("Bei einer Explosion mit mehr als 0.45 vertikalem Delta wirft die Perle mit 88.5 Grad fast senkrecht vor die eigenen Fuesse, um schnell wieder Boden fuer Platzierungen zu erreichen.")
        .defaultValue(true)
        .build()
    );

    /**
     * +/- Spanne in Grad, um die der senkrechte Rettungswurf gestreut wird. Ohne diese Streuung
     * geht der Wurf reproduzierbar bei exakt 80.0 Grad raus - ein Anti-Cheat, das nur auf den
     * Wurfwinkel schaut, erkennt daran eine Maschine. Siehe {@link com.provipvp.exec.PitchVariance}.
     */
    public final Setting<Double> pearlPitchVariance = sgPearl.add(new DoubleSetting.Builder()
        .name("pearl-pitch-variance")
        .description("Zufallsstreuung des senkrechten Rettungswurfs in Grad. 0.75 ist ein guter Start; 0 schaltet die Streuung ab (dann exakt 80 Grad, nicht empfohlen).")
        .defaultValue(0.75)
        .min(0)
        .max(10)
        .build()
    );

    // Defense
    public final Setting<Boolean> fastTotem = sgDefense.add(new BoolSetting.Builder()
        .name("fast-totem")
        .description("Sofort-Totem-Manager: prueft die Offhand jeden Tick.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> turtleMasterDefense = sgTurtle.add(new BoolSetting.Builder()
        .name("turtle-master-defense")
        .description("Unter der Health-Schwelle eine bereits mit Turtle-Master-Pfeilen geladene Armbrust in die Offhand nehmen und die Pfeile im selben Tick direkt vor die eigenen Fuesse schiessen. Die Offhand bleibt whilend Turtle-Master aktiv; Totem-Manager und Combat-Slot werden fuer diesen Schuss exklusiv pausiert.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Double> turtleMasterHealth = sgTurtle.add(new DoubleSetting.Builder()
        .name("turtle-master-health")
        .description("Anteil der maximalen Health, unter dem Turtle-Master aktiv wird.")
        .defaultValue(0.4)
        .range(0.1, 0.8)
        .sliderRange(0.1, 0.8)
        .visible(turtleMasterDefense::get)
        .build()
    );

    public final Setting<Integer> turtleMasterCooldown = sgTurtle.add(new IntSetting.Builder()
        .name("turtle-master-cooldown")
        .description("Ticks zwischen zwei Turtle-Master-Fussschuessen. Ein Schuss gewaehrt normalerweise 5 Sekunden Resistenz.")
        .defaultValue(10)
        .range(1, 40)
        .sliderRange(1, 20)
        .visible(turtleMasterDefense::get)
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

    public final Setting<Boolean> instaCity = sgCity.add(new BoolSetting.Builder()
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
        .defaultValue(false)
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

    public final Setting<Boolean> antiEscapeTrap = sgCity.add(new BoolSetting.Builder()
        .name("anti-escape-trap")
        .description("Erkennt ein 1x1-Loch bis drei Bloecke in der aktuellen Laufrichtung des Gegners und fuellt es bevorzugt mit Cobweb, sonst Obsidian. Cobweb stoppt die Bewegung, laesst Crystal-/Anchorsplash aber durch.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Double> maxSelfDamage = sgDefense.add(new DoubleSetting.Builder()
        .name("max-self-damage")
        .description("Maximaler Eigenschaden pro Angriffsplatz.")
        .defaultValue(14.0)
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
        .defaultValue(12.0)
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

    // ---------- D1/D2/D3/D5/D6-D10/D11-D13/D14/D15-D19 ----------

    /** GcdRotator: quantisiert jede gesendete Rotation auf das Mausraster. Grim zaehlt jedes
     *  Winkel-Delta, das KEIN Vielfaches von 0.0086 ist, als "AimModulo360"-Verstoss. Ohne das
     *  sendet das Modul rohe Float-Winkel (Meteor quantisiert nicht) und faellt bei jedem Dreh auf. */
    public final Setting<Boolean> gcdRotation = sgCombat.add(new BoolSetting.Builder()
        .name("gcd-rotation")
        .description("Quantisiert Yaw/Pitch vor dem Senden auf das Mausraster (Divisor 0.0086). Ohne das meldet Grim jedes Winkel-Delta, das kein Vielfaches des Rasters ist - das Modul sendet bisher rohe Float-Winkel.")
        .defaultValue(true)
        .build()
    );

    /** Zusaetzliches Jitter in Gitter-Schritten. 0 = exakt deterministisch (Fingerabdruck), 1-2 = ein
     *  bis zwei Schritte Streuung. Zu viel kostet praezise Aim-Zustaende ohne echten Gewinn. */
    public final Setting<Integer> gcdJitterSteps = sgCombat.add(new IntSetting.Builder()
        .name("gcd-jitter-steps")
        .description("Zusaetzliche Jitter-Streuung in Gitter-Schritten (Divisor 0.0086 pro Schritt). 0 = exakt; groessere Werte kosten praezise Aim-Zustaende, ohne die Grid-Form zu verlassen.")
        .defaultValue(1)
        .range(0, 5)
        .sliderRange(0, 5)
        .build()
    );

    /** ActionRayValidator/ReachPolicy: alle clientSide-Aktionen gegen die GESENDETE Rotation pruefen. */
    public final Setting<Boolean> rayValidateActions = sgCombat.add(new BoolSetting.Builder()
        .name("ray-validate-actions")
        .description("Prueft jede ausgerichtete Aktion gegen die wirklich gesendete Rotation statt gegen die Kamera: Crystal-Zuendung, Nahkampf, Schildbrechen. Ersetzt die geratene BlockHitResult-Flaeche durch einen echten Cursor.")
        .defaultValue(true)
        .build()
    );

    /** ReachPolicy: harte Distanzpruefung. Melee/Crystal = 3.0, Platzierung = 4.5 - zwei Attribute. */
    public final Setting<Boolean> enforceReach = sgCombat.add(new BoolSetting.Builder()
        .name("enforce-reach")
        .description("Bildet die echten Vanilla-Reichweiten ab: Nahkampf und Crystal-Zuendung 3.0 (Grim flaggt ab 3.0005), Blockplatzierung 4.5. Die beiden werden nie vermischt.")
        .defaultValue(true)
        .build()
    );

    /** PlaceCursorSolver: aufgeloeste Flaeche/Cursor statt geratenem BlockHitResult. */
    public final Setting<Boolean> solvePlaceCursor = sgCombat.add(new BoolSetting.Builder()
        .name("solve-place-cursor")
        .description("Loest die echte Klickflaeche und den Cursor fuer Anker-/Bett-/Crystal-Platzierung aus der gesendeten Rotation auf. Ein geratener BlockHitResult zeigt bei schraegen Waenden in die Nachbarflaeche.")
        .defaultValue(true)
        .build()
    );

    /** CrystalScorer: bewerteter Crystal-Pick statt 'der naechste'. */
    public final Setting<Boolean> scoreCrystals = sgCombat.add(new BoolSetting.Builder()
        .name("score-crystals")
        .description("Waehlt den Crystal nach projiziertem Schaden statt nach Position in der Entity-Liste. Beruecksichtigt nur selbstgesetzte Crystals, deren Alter und projizierten Schaden auf dem Ziel.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Double> crystalMinPickDamage = sgCombat.add(new DoubleSetting.Builder()
        .name("crystal-min-pick-damage")
        .description("Mindest-projizierter-Schaden, den ein eigener Crystal fuer die Auswahl erreichen muss. Filtert Crystals, deren Pop am Ziel nichts oder kaum etwas bringt.")
        .defaultValue(3.0)
        .range(0.0, 30.0)
        .sliderRange(0.0, 20.0)
        .build()
    );

    public final Setting<Integer> crystalMinTickAge = sgCombat.add(new IntSetting.Builder()
        .name("crystal-min-tick-age")
        .description("Mindestalter (Ticks) eines eigenen Crystals, bevor er angegriffen wird. Zu junge Crystals koennen vom Server noch verworfen werden - der Angriff waere dann umsonst und sieht wie Zufall aus.")
        .defaultValue(0)
        .range(0, 20)
        .sliderRange(0, 10)
        .build()
    );

    /** AttackGate: hurtTime-Fenster, Angriffsstaerke, lethaler Sprung-Crit. */
    public final Setting<Boolean> gateAttacks = sgCombat.add(new BoolSetting.Builder()
        .name("gate-attacks")
        .description("Gated Nahkampf und D-Tap durch die echten Angriffsbedingungen: Ziel nicht in der Unverletzlichkeits-Phase (hurtTime), voller Angriffs-Cooldown, und Sprung-Crit nur wenn er das Ziel toeten wuerde.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Double> minAttackStrength = sgCombat.add(new DoubleSetting.Builder()
        .name("min-attack-strength")
        .description("Mindest-Angriffsstaerke (0..1) fuer einen Nahkampf oder D-Tap-Schlag. 0.9 = nahezu vollstaendig geladen; niedriger feuert frueher, trifft aber entsprechend schwaecher.")
        .defaultValue(0.9)
        .range(0.0, 1.0)
        .sliderRange(0.5, 1.0)
        .build()
    );

    /** SelfDamageGuard: ablehnen, was den Spieler TOETET - nicht nur was ueber dem Deckel liegt. */
    public final Setting<Boolean> lethalSelfDamageGuard = sgDefense.add(new BoolSetting.Builder()
        .name("lethal-self-damage-guard")
        .description("Verwirft jede Platzierung, deren projizierter Eigenschaden die eigenen aktuellen HP erreichen wuerde - unabhaengig von max-self-damage. Der Deckel ist einstellbar, der eigene Tod nicht.")
        .defaultValue(true)
        .build()
    );

    /** CrystalToolPolicy: nicht mit einem Werkzeug schwingen, das dem Crystal 0 Schaden macht. */
    public final Setting<Boolean> crystalToolCheck = sgCombat.add(new BoolSetting.Builder()
        .name("crystal-tool-check")
        .description("Schwingt nicht mit einem Werkzeug, das einem End Crystal keinen Schaden zufuegt (Schaufel, Spitzhacke ohne Effekt). Weicht vorher auf einen tauglichen Hotbar-Slot aus.")
        .defaultValue(true)
        .build()
    );

    /** SpearModel: der Default 3.6 erreicht einen Spear (4.5) nicht. */
    public final Setting<Boolean> spearAware = sgCombat.add(new BoolSetting.Builder()
        .name("spear-aware")
        .description("Rechnet mit dem 26er-Spear: Reichweite 4.5 statt 3.0, kein Crit, kein Sprint-Knockback, Charge-Angriff nur ab 4.6 b/s. Ohne das meldet attack-range 3.6 den Spear paeglich als 'ausser Reichweite' und der Bot schlaegt nie zu.")
        .defaultValue(true)
        .build()
    );

    /** KnockbackModel: Knockback-Widerstand pro Entity statt eines festen Faktors. */
    public final Setting<Boolean> perEntityKnockback = sgCombat.add(new BoolSetting.Builder()
        .name("per-entity-knockback")
        .description("Berechnet den Knockback pro Ziel aus Netherite, Blast-Protection und nativer Widerstandsfaehigkeit. Bisher galt ein fester Faktor fuer alle - dabei bewegt ein Netherite-Gegner gar nichts und ein Creaking gar nichts mehr.")
        .defaultValue(true)
        .build()
    );

    /** SlowFallingArrow: Zielzustands-abhaengige Angriffsplanung. */
    public final Setting<Boolean> slowFallingPlan = sgCombat.add(new BoolSetting.Builder()
        .name("slow-falling-plan")
        .description("Plant Angriffe gegen ein Ziel, das gerade unter Slow Falling steht: der Mace-Smash-Bonus ist dann unerreichbar, und die Zahl der Pop-Zyklen im i-Frame-Fenster ist bekannt. Verhindert Smash-Versuche, die garantiert nichts bringen.")
        .defaultValue(true)
        .build()
    );

    /** WindChargeModel: eigene Wind Charge als Schadens- und Knockback-Quelle mitrechnen. */
    public final Setting<Boolean> windChargeAwareness = sgDefense.add(new BoolSetting.Builder()
        .name("wind-charge-awareness")
        .description("Plant die eigene Wind Charge als Quelle ein: 1 Schaden, Radius 2.4, Knockback x1.22, 10 Ticks Abklingzeit. Wind Charges sprengen seit 1.20.5 keine Crystals - sie zaehlen nur als Schaden/Knockback.")
        .defaultValue(false)
        .build()
    );

    /** AttackDispatcher: Angriffe als INTERACT-dann-ANIMATION statt vanilla-Doppelpaket. */
    public final Setting<Boolean> dispatchAttacks = sgCombat.add(new BoolSetting.Builder()
        .name("dispatch-attacks")
        .description("Sendet Crystal-Zuendung, Nahkampf und Schildbrechen als Interact-Paket plus separate Animation. Der Vanilla-Weg wuerde das Swing-Paket ein zweites Mal mitschicken - Grim sieht das Paket doppelt.")
        .defaultValue(true)
        .build()
    );

    /** TotemEventReader: Self-Pop aus Event-Paket 35 statt Health-Drop-Heuristik. */
    public final Setting<Boolean> totemEventDetection = sgDefense.add(new BoolSetting.Builder()
        .name("totem-event-detection")
        .description("Erkennt den eigenen Totem-Pop am Entity-Event-Paket 35 statt ueber den Health-Drop. Das Ereignis faellt Health-Drop UND Offhand-Verschwinden in EINEN Pop zusammen; die Heuristik zaehlte beides zweimal.")
        .defaultValue(true)
        .build()
    );

    /** TickRateGate: bei Lag drosseln/pausieren statt weiterzufuettern. */
    public final Setting<Boolean> lagThrottle = sgDefense.add(new BoolSetting.Builder()
        .name("lag-throttle")
        .description("Pausiert oder drosselt die Aktionsrate, wenn der Server laenger als 1.2 s pro Tick braucht. Bei Lag trifft der Bot seine eigenen Aktionen nicht mehr - sie verpuffen im Server-Backlog und sehen dort nur wie Makrospamming aus.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Integer> lagThrottleEvery = sgDefense.add(new IntSetting.Builder()
        .name("lag-throttle-every")
        .description("Bei geaempftem Server laeuft nur jede n-te Aktion. 2 = jede zweite. Wirkt nur, wenn lag-throttle aktiv ist.")
        .defaultValue(2)
        .range(1, 5)
        .sliderRange(1, 5)
        .build()
    );

    /** ActionCadence: eine Aktion pro Movement-Paket, kein Slot-Swap nach der Aktion. */
    public final Setting<Boolean> actionCadence = sgCombat.add(new BoolSetting.Builder()
        .name("action-cadence")
        .description("Pro Movement-Paket hoechstens eine Platzierung, ein Angriff, ein Rechtsklick, ein Schwung und eine Blickrichtung. Angreifen oder setzen waehrend ein Item benutzt wird, wird abgelehnt. Der Slot-Wechsel passiert VOR der Aktion, damit das nachlaufende releaseCombatSlot legal bleibt.")
        .defaultValue(true)
        .build()
    );

    /** D14: RandomBetween fuer verzoegungsartige Einstellungen. */
    public final Setting<Integer> pearlDelayMin = sgPearl.add(new IntSetting.Builder()
        .name("pearl-delay-min")
        .description("Untere Grenze des Perlen-Cooldown-Fensters in Ticks (D14, RandomBetween). Zusammen mit pearl-delay-max ergibt das ein echtes Intervall statt eines starren Werts - drei Perlenwuerfe im exakt gleichen Abstand sind ein Muster.")
        .defaultValue(45)
        .range(0, 200)
        .sliderRange(20, 120)
        .build()
    );

    public final Setting<Integer> pearlDelayMax = sgPearl.add(new IntSetting.Builder()
        .name("pearl-delay-max")
        .description("Obere Grenze des Perlen-Cooldown-Fensters in Ticks (D14, RandomBetween). Ist sie kleiner als pearl-delay-min, werden beide still vertauscht.")
        .defaultValue(60)
        .range(0, 200)
        .sliderRange(20, 120)
        .build()
    );

    public final Setting<Integer> dtapDelayMin = sgCombat.add(new IntSetting.Builder()
        .name("dtap-delay-min")
        .description("Untere Grenze des D-Tap-Nachladen-Fensters in Ticks (D14, RandomBetween).")
        .defaultValue(28)
        .range(0, 120)
        .sliderRange(10, 80)
        .build()
    );

    public final Setting<Integer> dtapDelayMax = sgCombat.add(new IntSetting.Builder()
        .name("dtap-delay-max")
        .description("Obere Grenze des D-Tap-Nachladen-Fensters in Ticks (D14, RandomBetween).")
        .defaultValue(44)
        .range(0, 120)
        .sliderRange(10, 80)
        .build()
    );

    public final Setting<Integer> anchorDelayMin = sgCombat.add(new IntSetting.Builder()
        .name("anchor-delay-min")
        .description("Untere Grenze des Anker-Platzierungsfensters in Ticks (D14, RandomBetween).")
        .defaultValue(5)
        .range(0, 60)
        .sliderRange(2, 30)
        .build()
    );

    public final Setting<Integer> anchorDelayMax = sgCombat.add(new IntSetting.Builder()
        .name("anchor-delay-max")
        .description("Obere Grenze des Anker-Platzierungsfensters in Ticks (D14, RandomBetween).")
        .defaultValue(9)
        .range(0, 60)
        .sliderRange(2, 30)
        .build()
    );

    /** C8: Perlen und Splash-Traenke sprengen eigene Crystals. */
    public final Setting<Boolean> protectOwnCrystals = sgDefense.add(new BoolSetting.Builder()
        .name("protect-own-crystals")
        .description("Bevor eine Perle oder ein Heiltraenk geworfen wird, wird die Bahn gegen die eigenen Crystals geprueft (Entity-Box um 0.3 aufgeweitet, weil Perlen und Splash-Traenke mit ihnen kollidieren). Ohne das sprengt der eigene Rettungswurf die eigene Crystal-Kette.")
        .defaultValue(true)
        .build()
    );

    // State
    private final Random rng = new Random();
    private final Map<UUID, Float> lastHealth = new HashMap<>();
    private final Map<UUID, Integer> pops = new HashMap<>();
    private final Map<UUID, Vec3> lastPositions = new HashMap<>();
    private final Map<UUID, Vec3> velocities = new HashMap<>();

    /** Welt, zu der {@link #lastPositions} und {@link #velocities} gehoeren. Ein Wechsel leert beide
     *  Maps — sonst rechnet der erste Tick in der neuen Welt die Differenz zur Position aus der alten
     *  und haelt das fuer einen Teleport. Im Arena-Log stand dort einmal "35351738 m". */
    private net.minecraft.client.multiplayer.ClientLevel trackedWorld;

    /** Kleinster Positionssprung eines Ziels, der als echter Teleport gilt. Bewusst weit ueber dem,
     *  was ein harter Crystal-Pop oder Vollnahkampf-Knockback in einem Tick erreicht (siehe Kommentar
     *  am Geschwindigkeits-Cap: 3-5 Bloecke/Tick), und weit unter einer echten Perle (16-20 m). Der
     *  alte Wert 6.0 lag direkt neben dem Pop-Bereich - jeder harte Treffer loeste dadurch den
     *  Teleport-Zweig aus. */
    private static final double TELEPORT_JUMP_BLOCKS = 10.0;

    /** Mindestabstand zweier Pfad-Invalidierungen fuer dasselbe Ziel. Eine echte Perle teleportiert
     *  einmal; serverseitige Positionskorrekturen ("moved wrongly") dagegen mehrere Male pro Sekunde.
     *  Ohne diese Bremse hat der Bot im Arena-Test seinen eigenen Follow-Pfad abwaerts gerissen,
     *  kam nie auf Angriffsdistanz und verbrauchte ueberhaupt keine Ressource. */
    private static final int PATH_INVALIDATION_COOLDOWN_TICKS = 20;

    private UUID lastPathInvalidationTarget;
    private int lastPathInvalidationTick = Integer.MIN_VALUE;

    /** Zuletzt in {@link #traceCombatState()} protokollierte Aktion - verhindert Spam bei Dauerkampf. */
    private String lastTracedAction = "";

    /** Ziel des aktuellen Ticks - nur fuer {@link #traceCombatState()}, weil handleTargeting() es lokal zurueckgibt. */
    private LivingEntity tracedTarget;

    /**
     * Schreibt jeden Zustandswechsel der Kampf-KI ins Log. Standard aus, weil es pro Tick
     * allokiert (String.format) — im Gefecht will man das nicht nebenbei bezahlen.
     */
    public final Setting<Boolean> debugTrace = sgQA.add(new BoolSetting.Builder()
        .name("debug-trace")
        .description("Schreibt den Kampf-Zustand (Aktion, Ziel, Distanz, engaged, Aura-Modus) ins Log — bei Wechsel und alle 100 Ticks. Zum Finden haengender Zweige, im normalen Betrieb aus.")
        .defaultValue(false)
        .build()
    );
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
    private int lastPearlScanTick = -999;
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
    private Double savedCrystalMinDamage;
    private Double savedCrystalMaxDamage;
    private final Map<Module, java.util.Set<EntityType<?>>> savedEntityFilters = new IdentityHashMap<>();
    /** Gemerkte Original-Werte der walls-range-Einstellungen (0 = CrystalAura place, 1 = CrystalAura
     *  break, 2 = KillAura); -1 = nichts veraendert. Siehe syncWallsRange(). */
    private final double[] savedWallsRange = { -1, -1, -1 };
    private boolean supportSyncFailed;
    private boolean crystalAuraOwned;
    private boolean autoMendOwned;
    private boolean autoEatOwned;
    private boolean noFallOwned;
    private boolean killAuraOwned;
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
    private boolean holeEscapeActive;
    private int holeEscapeStartedTick = -1;
    private Vec3 holeEscapeLastPosition;
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

    // ---------- D1-D19: Helfer-Instanzen und ihr Zustand ----------

    /** D1: die Gitterbasis fuer gesendete Rotationen. Einmal pro Kampf auf die Kamera geseedet und
     *  bei Zielwechsel verworfen - sonst kontaminiert die alte Rotation das erste Delta des neuen
     *  Ziels, und dieses Delta waere kein Vielfaches des Rasters. */
    private final GcdRotator.AngleDeltaAccumulator gcd = new GcdRotator.AngleDeltaAccumulator();
    private UUID gcdSeededFor;

    /** D6: eigene Crystals als Entity-ID-Menge. Ohne dieses Buch ist pick() immer leer, weil der
     *  Scorer per Definition nur Crystals bewertet, denen der Bot selbst vertraut. */
    private final CrystalOwnership crystalOwnership = new CrystalOwnership();

    /** D13: der letzte eigene Wind-Charge-Wurf; die Abklingzeit betraegt 10 Ticks. */
    private int lastWindChargeTick = -999;

    /** C8: so weit wird die Strecke vor einem Perlenwurf auf eigene Crystals geprueft. Der Wert
     *  deckt die uebliche Gapclose-Distanz ab — eine Perle, die weiter fliegt, verlaesst den
     *  Kampfbereich ohnehin. */
    private static final double PEARL_C8_CHECK_DISTANCE = 20.0;
    /** D6: Bodenzellen, auf denen der Bot in diesem Tick einen Crystal gesetzt hat. Der Server
     *  bestaetigt die Entity erst danach per Entity-Added-Event; ohne diese Bruecke wuerde die
     *  Ownership-Menge beim ersten Tick nach der Platzierung noch leer sein. */
    private final Set<BlockPos> pendingCrystalCells = new HashSet<>();
    /** D6/D18: die zuletzt gesehene Dimension. Entity-IDs und Offhand-Zustaende sind weltgebunden. */
    private ResourceKey<Level> lastDimension;

    /** D7/D9/D10: gemeinsame Gate-Auswertung fuer Nahkampf und D-Tap. */
    private static final CrystalToolPolicy TOOL_POLICY = new CrystalToolPolicy();

    /** D19: das Aktions-Ledger des Ticks. Hier gezaehlt, damit jede Aktion nur ECHT einmal feuert. */
    private final ActionCadence cadence = new ActionCadence();
    private int cadenceActionIndex;
    /** D17: eigener, tickuebergreifender Zaehler fuer das Lag-Gate. Getrennt von
     *  {@link #cadenceActionIndex}, das pro Tick zurueckgesetzt wird und den Paket-Takt zaehlt. */
    private int throttleIndex;

    /** D15: lagt das Ledger an einer Entity-ID-Leiste; AttackDispatcher schickt daraus die Pakete. */
    private AttackDispatcher dispatcher;

    /** D17: pausiert/drosselt die Aktionsrate bei geaempftem Server. */
    private TickRateGate tickGate;
    private double lastTickRate = TickRateGate.NOMINAL;

    /** D18: der eine Pop pro Tick, aus dem Entity-Event 35 und dem Offhand-Verschwinden. */
    private final TotemEventReader totemReader = new TotemEventReader();
    private boolean selfPopThisTick;

    /** D12: D-Tap, Restlaufzeit des eigenen Slow-Falling-Effekts fuer die Zielplanung. */
    private int slowFallingTicksRest;

    /** D11: Hatte der Bot im aktuellen Kampf schon einen Spear in der Hand gesehen? Nur dann
     *  greift die 4.5-Reichweite - sonst wuerde jeder normale Kampf auf 4.5 aufgeweicht. */
    private boolean spearSeen;
    private int lastSpearTick = -999;

    /** D14: die drei RandomBetween-Fenster werden pro Nutzung neu gezogen, nicht pro Tick gecacht. */
    private int pearlDelayWindow;
    private int dtapDelayWindow;
    private int anchorDelayWindow;
    private int delayWindowTick = -999;

    /** C8: Entity-Box eines Crystals, um PICK_INFLATION aufgeweitet - Perlen und Splash-Traenke
     *  kollidieren mit dieser Box, nicht mit der nackten Entity-Box. */
    private boolean throwHitsOwnCrystal(Vec3 from, Vec3 to) {
        if (mc.level == null) return false;
        AABB path = new AABB(from, to).inflate(ActionRayValidator.PICK_INFLATION);
        for (EndCrystal ec : mc.level.getEntitiesOfClass(EndCrystal.class, path)) {
            if (!crystalOwnership.owns(ec.getId())) continue;
            if (path.contains(ec.getBoundingBox().getCenter())) return true;
        }
        return false;
    }

    /**
     * Terrain-/Sichtbarkeitsschicht mit Raycast-Cache. Wird pro Tick ueber {@link #markScannerTick()}
     * weitergezogen und bei jeder Blockaenderung invalidiert, sonst liefert {@code trajectoryClear}
     * veraltete Treffer. Die Perlen-Szenarien ({@code com.provipvp.pearl.PearlSolver}) bauen darauf auf.
     */
    private final com.provipvp.terrain.ExplosionScanner scanner = new com.provipvp.terrain.ExplosionScanner();

    /** Drosselt die teuren Block-Volumen-Sweeps. Ohne das kostete allein die Auto-Shield-Deltaerkennung
     *  2x1331 Zellen je Tick, der Anker-Wartung weitere 567 und die Explosionzaehlung 405 - zusammen ueber
     *  3000 {@code getBlockState}-Aufrufe pro Tick, also rund 60 000 pro Sekunde, nur um nach einem neu
     *  aufgetauchten Block zu sehen. Zwischen zwei erlaubten Laeufen wird das VORHERIGE Ergebnis
     *  weiterverwendet, die Erkennung verliert also hoechstens ein paar Ticks Reaktionszeit. */
    private final com.provipvp.perf.ScanBudget scanBudget = new com.provipvp.perf.ScanBudget();

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
    /** true, sobald onActivate() vollstaendig durchlief; siehe onDeactivate(). */
    private boolean lifecycleStarted;
    private int turtleSwitchReadyTick;
    private int turtleShotReadyTick = -999;
    /** Ob in diesem Tick bereits eine "echte" Aktion (Priority > PRIORITY_LOOK) in die Rotations-Warteschlange
     *  eingegeben wurde. Wird verwendet, um den free-look Tail-Flush (PRIORITY_LOOK) nur dann auszufuehren,
     *  wenn KEINE echte Aktion (Crystal/Anchor/Bed/Pearl/Misc) die Rotation beansprucht hat. rotationsThisTick
     *  allein reicht nicht, weil der Callback asynchron in Rotations.onSendMovementPacketsPost laeuft und
     *  rotationsThisTick > 0 sein kann, bevor der erste Callback ueberhaupt ausgefuehrt wurde. */
    private boolean realActionThisTick;

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
    private int dtapStage; // 0 idle, 1 1.Crystal platzieren, 2 1.Crystal zuenden (warten auf Server-Bestätigung), 3 Immunitaet abwarten, 4 2.Crystal platzieren+zuenden
    private int dtapStageTick;
    private int dtapCooldown;
    /** Entity-ID des ersten D-Tap Crystals, gesetzt sobald der Server das EntityAdded-Paket sendet.
     *  Verhindert Race Condition: wir warten auf diese ID, bevor wir den Crystal angreifen. */
    private int dtapFirstCrystalId = -1;

    public GodmodePvP() {
        super(com.provipvp.ProviPvPAddon.CATEGORY, "godmode-pvp", "ProviPvP v4: Kampf-KI mit eigenem Blitz-Anchor (1 Glowstone), Verfolgung ohne Limit. Befehl: .pvp");
    }

    /**
     * Erzwingt die Profil-Exklusivitaet schon im Toggle-Pfad. Der Waechter in {@link #onActivate()} greift
     * nicht, weil Meteor {@code onActivate()} <em>und</em> das Event-Bus-Subscribe ueberspringt, sobald
     * {@code Utils.canUpdate()} false ist (Hauptmenue, Welt laedt). Dann waeren beide Profile aktiv
     * und der {@code onTick}-Waechter koennte ebenfalls nicht laufen, weil er die Subscription
     * braucht, die ebenfalls uebersprungen wurde.
     */
    @Override
    public void toggle() {
        if (!isActive()) com.provipvp.ProviPvPAddon.enforceExclusiveProfile(GodmodePvP.class);
        super.toggle();
    }

    @Override
    public void onActivate() {
        // GodmodePvP und HumanPvP teilen sich globale Baritone-/CrystalAura-Zustaende und duerfen
        // daher niemals gleichzeitig aktiv sein. Sonst wuerde das spaeter aktivierte Profil die
        // Rotationen, Slot-Lease und Cooldowns des anderen ueberschreiben.
        HumanPvP human = Modules.get().get(HumanPvP.class);
        if (human != null && human.isActive()) human.disable();
        tickCounter = 0;
        supportSyncFailed = false;
        auraMode = -1;
        java.util.Arrays.fill(auraUsage, 0);
        java.util.Arrays.fill(lastResourceCount, -1);
        lastErrorWarnTick = -999;
        savedCrystalMinDamage = null;
        savedCrystalMaxDamage = null;
        crystalAuraOwned = false;
        autoMendOwned = false;
        autoEatOwned = false;
        noFallOwned = false;
        killAuraOwned = false;
        savedEntityFilters.clear();
        popBurstUntil = 0;
        lastPearlTick = -999;
        lastPearlScanTick = -999;
        anchorPlaceCooldown = 0;
        crystalForcedUntil = 0;
        anchorUnreachableTicks = 0;
        anchorCalcOrigin = null;
        bedPlaceCooldown = 0;
        bedMaintCooldown = 0;
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
        dtapFirstCrystalId = -1;
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
        holeEscapeActive = false;
        holeEscapeStartedTick = -1;
        holeEscapeLastPosition = null;
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
            syncCrystalDamageSettings(ca);
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
            autoMendOwned = safeEnable(m, AutoMend.class);
            tuneAutoMend();
        }
        if (autoEatOn.get()) autoEatOwned = safeEnable(m, AutoEat.class);
        if (noFallOn.get()) noFallOwned = safeEnable(m, NoFall.class);
        if (killAuraOn.get()) killAuraOwned = safeEnable(m, KillAura.class);
        syncMobFilter();
        syncWallsRange();

        // KEIN EVENT_BUS.subscribe(this): Meteor abonniert in Module.toggle() bereits selbst
        // (autoSubscribe, Default true). Ein zweites Abonnieren setzt jeden @EventHandler zweimal in
        // die Orbit-Liste — Orbit dedupliziert nicht — und doTick() laeuft dann zweimal pro
        // TickEvent.Pre. Beim zweiten Durchlauf werden rotationsThisTick und realActionThisTick
        // zurueckgesetzt und die Warteschlange des ersten verworfen.

        lifecycleStarted = true;

        info("ProviPvP v4 aktiv. Rechtsklick auf das Modul im Meteor-Menue zum Keybind. Befehl: .pvp");
    }

    /**
     * Meteor kann {@code onDeactivate()} aufrufen, ohne dass {@code onActivate()} je lief: werden im
     * Hauptmenue beide Profile eingeschaltet, ueberspringt {@code toggle()} das Aktivieren, und beim
     * Weltbeitritt feuert die Aufraeumroutine an einem Lauf, den es nie gab. Der globale Abriss-Cleanup
     * (Baritone-Follow abbrechen, Tasten loslassen) wuerde dann den Zustand eines ganz anderen
     * Addons zerstoeren. Deshalb genau dieser Lauf nachgefuehrt.
     */
    @Override
    public void onDeactivate() {
        if (!lifecycleStarted) return;
        lifecycleStarted = false;

        MeteorClient.EVENT_BUS.unsubscribe(this);
        releaseCombatSlot();
        releaseInstaCityTool();
        turtleModeActive = false;


        Modules m = Modules.get();
        if (crystalAuraOwned) safeDisable(m, CrystalAura.class);
        if (autoMendOwned) safeDisable(m, AutoMend.class);
        if (autoEatOwned) safeDisable(m, AutoEat.class);
        if (noFallOwned) safeDisable(m, NoFall.class);
        if (killAuraOwned) safeDisable(m, KillAura.class);
        crystalAuraOwned = false;
        autoMendOwned = false;
        autoEatOwned = false;
        noFallOwned = false;
        killAuraOwned = false;
        CrystalAura ca = m.get(CrystalAura.class);
        if (ca != null) {
            restoreZeroDelaySettings(ca);
            restoreSupport(ca);
            restoreCrystalDamageSettings(ca);
        }
        pendingBlockConfirmations.clear();
        restoreWallsRange();
        restoreEntityFilters();
        dtapStage = 0;
        dtapFirstCrystalId = -1;
        cancelFollow();

        if (blocking) {
            releaseCombatSlot();
            blocking = false;
            blockingSwapBack = false;
        }

        Input.setKeyState(mc.options.keyLeft, false);
        Input.setKeyState(mc.options.keyRight, false);
        Input.setKeyState(mc.options.keyJump, false);
        Input.setKeyState(mc.options.keySprint, false);
        Input.setKeyState(mc.options.keyUp, false);
        // mc.player ist null, sobald die Welt schon weg ist. onDeactivate laeuft ueber onGameLeft und
        // ueber den Toggle, also genau dann, wenn das passiert - ein unguardeter Zugriff wirft hier eine
        // NPE mitten im Aufraeumen und laesst den Rest des Zustands halb zurueck.
        if (mc.player != null) {
            mc.player.setShiftKeyDown(false);
            mc.player.setSprinting(false);
        }

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
        HumanPvP human = Modules.get().get(HumanPvP.class);
        if (human != null && human.isActive()) human.disable();
        // Der Budget-Zustand haengt am Ort: Chunk- oder Weltwechsel machen jede gecachte Zaehlung ungueltig.
        if (mc.player != null && mc.level != null) {
            scanBudget.markContext(new com.provipvp.perf.ScanBudget.ChunkHint(
                mc.player.blockPosition().getX() >> 4, mc.player.blockPosition().getZ() >> 4, mc.level));
        }

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
        if (tickCounter % 20 == 0) {
            CrystalAura ca = Modules.get().get(CrystalAura.class);
            if (ca != null) {
                syncZeroDelaySettings(ca);
                syncSupport(ca);
                syncCrystalDamageSettings(ca);
            }
        }

        try {
            doTick();
            traceCombatState();
        } catch (Exception e) {
            // Zeitbasiert statt fuer-immer-still: ein dauerhafter Fehler bleibt sichtbar, spammt aber nicht.
            if (tickCounter - lastErrorWarnTick > 100) {
                lastErrorWarnTick = tickCounter;
                warning("Interner Fehler: %s", e.toString());
            }
        }
    }

    /**
     * Schreibt den Kampf-Zustand in das Log, damit man sieht, an welchem Zweig der Bot haengenbleibt.
     * Bisher war {@code currentAction} nur im ClickGui-Text sichtbar und damit im Server-/Bot-Log
     * nirgends auffindbar — im Arena-Test blieb so die Ursage komplett unsichtbar.
     *
     * <p>Nur bei Wechsel und alle 100 Ticks, damit ein 5-Minuten-Lauf lesbar bleibt. Wirft bewusst
     * nichts ab: Diagnose darf keinen Kampf verhindern.
     */
    private void traceCombatState() {
        if (!debugTrace.get()) return;
        boolean changed = !currentAction.equals(lastTracedAction);
        if (!changed && tickCounter % 100 != 0) return;

        LivingEntity foe = tracedTarget;
        double dist = foe != null && mc.player != null ? Math.sqrt(mc.player.distanceToSqr(foe)) : -1.0;
        MeteorClient.LOG.info("[ProviPvP] tick={} action={} target={} dist={} engaged={} follow={} auraMode={} dtap={} held={}",
            tickCounter, currentAction, foe == null ? "-" : foe.getName().getString(),
            String.format(java.util.Locale.ROOT, "%.1f", dist), engaged, followActive, auraMode, dtapStage,
            mc.player == null || mc.player.getMainHandItem().isEmpty() ? "-"
                : mc.player.getMainHandItem().getHoverName().getString());

        lastTracedAction = currentAction;
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (event.packet instanceof ClientboundBlockUpdatePacket packet) {
            pendingBlockConfirmations.remove(packet.getPos());
            scanner.onBlockUpdate(packet.getPos());
            if (instaCityBlock != null && packet.getPos().equals(instaCityBlock)) {
                instaCityBreakConfirmed = packet.getBlockState().isAir();
            }
            return;
        }

        // D18: Der eigene Totem-Pop kommt als Entity-Event 35. Das ist die einzige Stelle, an der
        // der Server den Pop ECHT bestaetigt — die Health-Drop-Heuristik in trackPop sah ihn nur
        // indirekt und zaehlte Health-Drop UND Offhand-Verschwinden als zwei Ereignisse.
        if (totemEventDetection.get() && event.packet instanceof ClientboundEntityEventPacket packet) {
            if (mc.player == null) return;
            int eventId = packet.getEventId();
            Entity subject = packet.getEntity(mc.level);
            TotemEventReader.Detection detection = totemReader.onEntityEvent(
                eventId, subject == null ? Integer.MIN_VALUE : subject.getId(), subject == mc.player);
            if (detection == TotemEventReader.Detection.POP) selfPopThisTick = true;
        }
    }

    @EventHandler
    private void onEntityAdded(EntityAddedEvent event) {
        if (!(event.entity instanceof EndCrystal crystal) || mc.player == null) return;
        Vec3 position = crystal.position();

        // D-Tap: erste Crystal-Entity-ID merken, damit runDtapTick weiss, wann der Server sie bestaetigt hat
        if (dtapStage == 2 && dtapSpot != null && position.distanceTo(Vec3.atCenterOf(dtapSpot.above())) < 1.0) {
            dtapFirstCrystalId = crystal.getId();
        }

        // D6: Das Entity-Added-Event ist die ERSTE Stelle, an der der Server einen von uns gesetzten
        // Crystal bestaetigt. Ohne dieses notePlaced bleibt die Ownership-Menge leer und der bewertete
        // Pick liefert nie einen Kandidaten.
        for (BlockPos cell : pendingCrystalCells) {
            if (position.distanceTo(Vec3.atCenterOf(cell.above())) < 1.0) {
                crystalOwnership.notePlaced(crystal.getId());
                break;
            }
        }
        pendingCrystalCells.clear();

        // Verteidigungs-Abbau: nur wenn der Crystal uns WIRKLICH ueber den Deckel hinaus trifft.
        // Die alte Regel hat hier jeden Crystal auf offenem Feld abgebrochen, was den Bot zum
        // reflexhaften Crystal-Schlager gemacht hat.
        if (effectiveSelfDamage(position, DamageUtils.crystalDamage(mc.player, position)) > maxSelfDamage.get()) {
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
        rotationsThisTick = 0;
        realActionThisTick = false;
        // D6: der Tick-Zaehler des Ownership-Buchs. Ohne dieses advance() altern die Eintraege nie
        // aus, und alte Entity-IDs blieben unbegrenzt im Buch.
        crystalOwnership.advance(1);
        // D19: das Ledger wird pro Tick geleert, sonst blockiert die erste Aktion den ganzen Kampf.
        if (actionCadence.get()) cadence.onTick();
        cadenceActionIndex = 0;
        // D18: EIN Pop pro Tick, egal ob er ueber das Event-Paket oder das Offhand-Verschwinden kam.
        totemReader.onTick();
        selfPopThisTick = false;
        lastTickRate = measureTickRate();
        scanner.markTick(tickCounter);
        pendingFreeLook = false;
        sampleCombatMotion(self);
        updateSlowFallingRest();
        detectDimensionChange();
        reconcilePendingBlockConfirmations();
        if (handleExplosionEscape(self)) return;
        // Verbrauchs-Delta nur alle 5 Ticks messen (12x/s statt 20x/s) - InvUtils.find() scanned gesamten Inventar
        if (tickCounter % 5 == 0) trackResourceUsage();
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
        tracedTarget = target;
        updateSpearAwareness(target);
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
        // Rotation schon beansprucht hat. rotationsThisTick wird in rotateAndRun() inkrementiert,
        // ABER der Callback wird asynchron ausgefuehrt (in Rotations.onSendMovementPacketsPost).
        // Deshalb reicht rotationsThisTick == 0 NICHT: eine echte Aktion koennte bereits gequeued sein
        // (rotationsThisTick > 0), aber der Callback noch nicht gelaufen sein. Wir tracken deshalb
        // explizit, ob eine "echte" Aktion (Priority > PRIORITY_LOOK) diesen Tick gerendert wurde.
        // Siehe rotateAndRun() fuer die Erhoehung von realActionThisTick.

        if (pendingFreeLook && !realActionThisTick) {
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

    // ---------- Lifecycle der Helfer (ein Tick = ein advance, ein Weltwechsel = ein reset) ----------

    private long lastTickNanos;

    /**
     * D17: die tatsaechlich gemessene Zeit zwischen zwei Ticks, normiert auf 50 ms.
     *
     * <p>1.0 ist ein gesunder Server. Ueber 1.2 gilt er als geaempft, und das Gate pausiert oder
     * drosselt dann, statt weiter exakt dieselbe Rate zu feuern.
     */
    private double measureTickRate() {
        long now = System.nanoTime();
        if (lastTickNanos == 0) {
            lastTickNanos = now;
            return TickRateGate.NOMINAL;
        }
        double elapsedMs = (now - lastTickNanos) / 1_000_000.0;
        lastTickNanos = now;
        if (elapsedMs <= 0 || elapsedMs > 10_000) return TickRateGate.NOMINAL;
        return elapsedMs / 50.0;
    }

    /**
     * D6: Ein Dimensionswechsel macht jede Entity-ID ungueltig. Die alte Menge wuerde sonst
     * Entity-IDs der naechsten Welt als "eigene Crystals" fuehren — und der Scorer wuerde auf
     * fremde Crystals zielen.
     */
    private void detectDimensionChange() {
        if (mc.level == null) return;
        var dimension = mc.level.dimension();
        if (lastDimension == null) {
            lastDimension = dimension;
            return;
        }
        if (lastDimension.equals(dimension)) return;
        lastDimension = dimension;
        crystalOwnership.reset();
        pendingCrystalCells.clear();
        totemReader.reset();
        gcd.reset();
        gcdSeededFor = null;
    }

    /**
     * D12: Restlaufzeit des eigenen Slow-Falling-Effekts. Nur waehrenddessen ist der Mace-Smash
     * unerreichbar — der Plan wird dann daraufhin auf einen normalen Pop umgestellt.
     */
    private void updateSlowFallingRest() {
        if (mc.player == null) {
            slowFallingTicksRest = 0;
            return;
        }
        var effect = mc.player.getEffect(MobEffects.SLOW_FALLING);
        slowFallingTicksRest = effect == null ? 0 : Math.max(0, effect.getDuration());
    }

    /**
     * D11: hat der Gegner einen Spear in der Hand?
     *
     * <p>Der Spear erreicht 4.5 statt 3.0. Ohne diese Beobachtung wuerde der Bot dauerhaft gegen
     * 3.6 (attack-range) pruefen, den Spear paeglich als ausser Reichweite einstufen und nie
     * zuschlagen — das Fehlerbild, das den Spear unbrauchbar machte. Die Beobachtung ist bewusst
     * zeitbasiert: sieht der Gegner den Spear nur kurz, bleibt die Standardreichweite erhalten.
     */
    private void updateSpearAwareness(LivingEntity target) {
        if (target == null) return;
        // Der Spear ist ein Item; er wird an der gehaltenen Waffe des Gegners erkannt. Ohne ein
        // zusaetzliches Item-Interface bleibt die Erkennung am Namen des Hauptgegenstands.
        String held = target.getMainHandItem().getHoverName().getString().toLowerCase(java.util.Locale.ROOT);
        if (held.contains("spear") || held.contains("spieß") || held.contains("spiess")) {
            spearSeen = true;
            lastSpearTick = tickCounter;
        } else if (tickCounter - lastSpearTick > 200) {
            spearSeen = false; // 10 s ohne Spear -> wieder Standardreichweite
        }
    }

    /** Zielwahl/-verfolgung: findet das Ziel (oder raeumt bei Zielverlust auf), wertet periodisch
     *  ein besseres Ziel neu, pflegt engaged/engagedTargetId und die kontinuierliche Blickrichtung.
     *  @return das (ggf. neu gewaehlte) Ziel, oder null wenn keins gefunden wurde. */
    private LivingEntity handleTargeting(Player self) {
        LivingEntity target = findTarget(self);

        // Ziel weg -> Baritone stoppen
        if (target == null) {
            stopOwnedCrystalAura();
            engaged = false;
            dtapStage = 0;
            dtapFirstCrystalId = -1;
            cancelFollow();
            cancelInstaCity();
            resetFireWalkState();
            activeHole = null;
            holeEscapeActive = false;
            holeEscapeStartedTick = -1;
            holeEscapeLastPosition = null;
            heightCalcOrigin = null;
            Input.setKeyState(mc.options.keyLeft, false);
            Input.setKeyState(mc.options.keyRight, false);
            mc.player.setShiftKeyDown(false);
            Input.setKeyState(mc.options.keyJump, false);
            currentAction = "beobachten";
            auraMode = -1;
            bestCrystalDmgCache = 0;
            bestAnchorDmgCache = 0;
            bestBedDmgCache = 0;
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
        // Weltwechsel (Join, Dimensionswechsel, Teleport-Server): Positions- und
        // Geschwindigkeits-Caches gehoeren zur alten Welt. Ohne Leerung meldet der erste Tick in der
        // neuen Welt einen Sprung ueber Millionen Bloecke und loest den Teleport-Zweig aus — der
        // riss in der alten Fassung Baritone samt Welt-Cache mitten im Gefecht ab.
        if (trackedWorld != mc.level) {
            trackedWorld = mc.level;
            lastPositions.clear();
            velocities.clear();
        }

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
        if (!zeroDelay.get() && !instantMode.get()) {
            restoreZeroDelaySettings(ca);
            return;
        }

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

    /** CrystalAura's real damage gates must match this module's imminent/self-damage policy. */
    private void syncCrystalDamageSettings(CrystalAura ca) {
        Setting<Double> minDamage = crystalSetting(ca, "minDamage");
        if (minDamage != null) {
            if (savedCrystalMinDamage == null) savedCrystalMinDamage = minDamage.get();
            minDamage.set(MIN_MEANINGFUL_DAMAGE);
        }

        Setting<Double> maxDamage = crystalSetting(ca, "maxDamage");
        if (maxDamage != null) {
            if (savedCrystalMaxDamage == null) savedCrystalMaxDamage = maxDamage.get();
            maxDamage.set(maxSelfDamage.get());
        }
    }

    private void restoreCrystalDamageSettings(CrystalAura ca) {
        if (savedCrystalMinDamage != null) {
            Setting<Double> minDamage = crystalSetting(ca, "minDamage");
            if (minDamage != null) minDamage.set(savedCrystalMinDamage);
        }
        savedCrystalMinDamage = null;

        if (savedCrystalMaxDamage != null) {
            Setting<Double> maxDamage = crystalSetting(ca, "maxDamage");
            if (maxDamage != null) maxDamage.set(savedCrystalMaxDamage);
        }
        savedCrystalMaxDamage = null;
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
        savedPlaceDelay = -1;
        savedBreakDelay = -1;
        savedTicksExisted = -1;
        savedFastBreak = null;
    }

    /** Zentraler Blockplatzierungs-Einstieg. Nur erfolgreich lokalisierte Platzierungen werden
     *  bestaetigungspflichtig; End-Crystal-Entitaeten laufen weiterhin ueber CrystalAura. */
    private boolean placeTrackedBlock(BlockPos pos, FindItemResult item, boolean rotate, int priority) {
        BlockPos trackedPos = pos.immutable();

        // D3: fuer die Blockplatzierung gilt 4.5 (BLOCK_INTERACTION_RANGE), nicht die 3.0 des
        // Entity-Zugriffs. Die beiden duerfen nie vermischt werden — sonst wuerde der Bot Blocks
        // jenseits seiner Reichweite setzen wollen oder sich selbst zu frueh zurueckhalten.
        if (enforceReach.get() && mc.player != null
            && !PlaceCursorSolver.withinPlacementReach(eye(), trackedPos)) {
            return false;
        }
        if (!tickGateAllows() || !cadenceAllows(ActionCadence.Action.PLACE)) return false;

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
        holeEscapeActive = false;
        holeEscapeStartedTick = -1;
        holeEscapeLastPosition = null;
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
            && throwPearlAtCurrentYaw(rescuePitch())) {
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
            && InvHelper.has(Items.ENDER_PEARL) && throwPearlAtTarget(target)) {
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
                && InvHelper.has(Items.ENDER_PEARL) && throwPearlAtTarget(target)) {
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
            dtapFirstCrystalId = -1;
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
                shieldUntil = ShieldWindow.until(tickCounter, ShieldWindow.EMERGENCY_HELD_TICKS);
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

    /**
     * D11: Wie weit bewegt sich dieser Gegner ueberhaupt noch?
     *
     * <p>Der Knockback wird paeglich ueberall gleich angenommen. Tatsaechlich addiert sich der
     * Widerstand aus Netherite (10 % pro Stueck), Blast-Protection (15 % pro Stufe) und einem
     * nativen Mob-Widerstand. Ein vollstaendig gepanzerter Gegner bewegt sich praktisch nicht —
     * eine auf seinen Knockback geplante Perle landet dann an der falschen Stelle, und der
     * Crystal-Chase bringt keinen furtheren Pop zustande.
     */
    private boolean worthChasingKnockback(LivingEntity target) {
        if (!perEntityKnockback.get() || target == null) return true;
        KnockbackModel.NativeResistance nativeResistance = nativeResistanceOf(target);
        // "angesieht" ist die Bedingung fuer den Creaking-Widerstand — ohne Sichtkontakt faellt er weg.
        boolean lookedAt = mc.player != null && mc.player.hasLineOfSight(target);
        return KnockbackModel.worthChasing(nativeResistance, lookedAt);
    }

    /** Liest den nativen Widerstand aus dem Entity-Typ. Nur die neuen Mobs haben einen; normale
     *  Spieler fallen auf {@link KnockbackModel.NativeResistance#NONE} zurueck. */
    private static KnockbackModel.NativeResistance nativeResistanceOf(LivingEntity target) {
        var type = target.getType();
        String name = type.toString().toLowerCase(java.util.Locale.ROOT);
        if (name.contains("nautilus")) return KnockbackModel.NativeResistance.NAUTILUS;
        if (name.contains("creaking")) return KnockbackModel.NativeResistance.CREAKING;
        if (name.contains("agent")) return KnockbackModel.NativeResistance.AGENT;
        if (name.contains("npc")) return KnockbackModel.NativeResistance.NPC;
        return KnockbackModel.NativeResistance.NONE;
    }

    /**
     * D13: Bringt die eigene Wind Charge ueberhaupt noch etwas?
     *
     * <p>Wind Charges sprengen seit 1.20.5 keine Crystals mehr. Ihr Wert liegt ausschliesslich in
     * Schaden und Knockback — 1 Schaden, Radius 2.4, Knockback x1.22, 10 Ticks Abklingzeit. Als
     * Crystal-Ersatz geplant zu werden waere pure Verschwendung; als Schadensquelle ist sie eine
     * Option, wenn das Ziel nah genug fuer den Radius ist.
     */
    private boolean windChargeUsable(LivingEntity target, double dist) {
        if (!windChargeAwareness.get() || target == null) return false;
        if (!InvHelper.has(Items.WIND_CHARGE)) return false;
        WindChargeModel.WindCharge charge = WindChargeModel.playerCharge();
        return charge.hits(dist) && WindChargeModel.playerChargeReady(tickCounter - lastWindChargeTick);
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
            if (ka != null && !ka.isActive()) {
                ka.toggle();
                killAuraOwned = ka.isActive();
            }
        }

        // Feindlicher Crystal frisch platziert (in 5 m)? -> kurzes Schild-Block-Fenster
        java.util.List<EndCrystal> nearCrystals = mc.level.getEntitiesOfClass(EndCrystal.class, self.getBoundingBox().inflate(5));
        boolean freshCrystal = lastCrystalCount >= 0 && nearCrystals.size() > lastCrystalCount && !nearCrystals.isEmpty();

        // Anchor/Bett sind Bloecke, keine Entities - dieselbe Delta-Erkennung wie oben fuer Crystals,
        // nur per Block-Scan im gleichen 5-Block-Radius statt per Entity-Liste. Ein frisch erscheinender
        // Anchor/Bett in der Naehe ist ein ebenso starkes Vorzeichen einer unmittelbar bevorstehenden
        // Explosion wie ein neuer Crystal - vorher gab es dafuer ueberhaupt keine reaktive Verteidigung.
        // Zwei Zaehlungen im selben Wuerfel -> sie teilen sich ein Budget. Faellt es aus, zaehlen wir
        // nicht neu, sondern nutzen die Werte des letzten erlaubten Laufs weiter. Die Delta-Erkennung
        // verliert dadurch hoechstens ein paar Ticks, kostet aber nicht mehr 2662 Blockzustands-
        // Abfragen in JEDEM Tick.
        int anchorBlocks = lastAnchorBlockCount;
        int bedBlocks = lastBedBlockCount;
        if (scanBudget.allows(com.provipvp.perf.ScanBudget.ScanKind.NEARBY_BLOCK_COUNT, tickCounter)) {
            BlockPos feetNow = self.blockPosition();
            anchorBlocks = countNearbyBlocks(feetNow, 5, st -> st.is(Blocks.RESPAWN_ANCHOR));
            bedBlocks = countNearbyBlocks(feetNow, 5, st -> st.getBlock() instanceof BedBlock);
        }
        boolean freshAnchor = lastAnchorBlockCount >= 0 && anchorBlocks > lastAnchorBlockCount;
        boolean freshBed = lastBedBlockCount >= 0 && bedBlocks > lastBedBlockCount;

        if (freshCrystal || freshAnchor || freshBed) {
            // Kurzes Rueckzugs-Fenster fuer updateCombatMovement() (siehe dort) - unabhaengig vom
            // Schild-Setting, das ist eine reine Bewegungsentscheidung: physisch Abstand zur frisch
            // erschienenen Explosionsquelle gewinnen, nicht nur wegblocken.
            explosionRetreatUntil = tickCounter + 12;
            if (autoShield.get() && !blocking) {
                // Das Fenster wird erst ab der Aktivierung gezahlt - vorher steht nur ein Schild
                // in der Hand, der nichts blockt. 15+5 ergibt 10 Ticks echten Schutz.
                shieldUntil = ShieldWindow.until(tickCounter, ShieldWindow.FRESH_EXPLOSION_HELD_TICKS);
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
        // ist an attack-range gekoppelt (nie kleiner als Nahkampf-Reichweite+0.5).
        // Die Perlenbahn wird gegen die aktuelle Umgebung und mehrere Zielpunkte geprueft. Ein
        // straight-line LOS-Gate wuerde Lochrand und kurze Kanten faelschlich als "nicht werfbar"
        // behandeln; der Scanner verwirft dagegen nur Kandidaten, deren Bahn wirklich kollidiert.
        // D11: Gegen ein Ziel, das sich kaum wegschieben laesst, bringt die Gapclose-Perle nichts —
        // sie landet neben ihm, statt ihn in unsere Reichweite zu tragen. Die Perle wird dann nicht
        // verschwendet; der Bot naehert sich stattdessen auf eigene Faust.
        double pearlReachThreshold = Math.max(pearlMinDist.get(), attackRange.get() + 0.5);
        long pearlCooldown = pearlDelay();
        if (pearlThrow.get() && dist > pearlReachThreshold && engaged
            && worthChasingKnockback(target)
            && tickCounter - lastPearlTick > pearlCooldown && !guiOpen && throwPearlAtTarget(target)) {
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
                if (ca != null && !ca.isActive()) {
                    ca.toggle();
                    crystalAuraOwned = ca.isActive();
                }
            }
        }

        // Anchor-/Bett-Wartung: laeuft IMMER, unabhaengig vom aktuellen Aura-Modus - ein waehrend
        // Anchor-Modus platzierter Anchor wird auch fertig geladen/gezuendet, wenn zwischenzeitlich auf
        // Crystal umgeschaltet wird. Das war die Hauptursache dafuer, dass nicht alle Anchors gezuendet
        // wurden.
        //
        // Bewusst NACH der Aura-Platzierung und nicht davor: Wartung und Platzierung brauchen beide
        // eine Blickrichtung, und pro Movementspaket ist genau eine erlaubt (ActionCadence.LOOK).
        // Stand die Wartung davor, nahm sie den Slot, sobald irgendein Anker in Reichweite lag - und
        // ein frisch platzierter, noch ungeladener Anker ist genau das. Die Wartung nahm sich damit
        // jeden zweiten Tick selbst die Drehung, die die naechste Platzierung brauchte.
        maintainNearbyAnchors();
        maintainNearbyBeds();

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

        if (!InvHelper.isHotbarOrOffhand(shield) || !reserveCombatSlot(shield.slot())) return;
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
        if (!useAnchors.get() || anchorMode.get() == 2) {
            anchorCandidates.clear();
            anchorCandidateIndex = 0;
            anchorPlaceCooldown = 0;
            anchorsChargedByUs.clear();
            return;
        }
        if (anchorPlaceCooldown > 0) {
            anchorPlaceCooldown--;
            return;
        }

        Player self = mc.player;
        BlockPos spot = nextAnchorCandidate();
        if (spot == null) {
            anchorUnreachableTicks = 0;
            // KEIN Kandidat ist etwas voellig anderes als eine gescheiterte Platzierung. Es kann
            // sein, dass der Gegner im Mauerwerk steht, dass die Kandidatenliste noch nicht berechnet
            // ist oder dass jede Zelle am Spieler scheitert. "Crystal erzwingen" ist darauf keine
            // Antwort - es hat hier 2 Sekunden lang genau das Gegenteil bewirkt: der Bot wechselte
            // zu Crystal, obwohl es gar keinen Crystal gab, und die Verweigerung beim naechsten
            // Versuch wiederholte sich. Ein Platzierungsfehler zaehlt weiterhin, der fehlende
            // Kandidat nicht.
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
        // rotate=true. Der Erfolg-Callback laesst sich dadurch direkt im Rotations-Callback abgeben,
        // ohne einen zweiten Tick zu warten.
        //
        // Zur Erstladung: der Versuch unten gelingt nur, wenn action-cadence AUS ist. Ist es an
        // (Standard), hat der aeussere rotateAndRun() den einzigen LOOK-Slot des Movementpakets
        // bereits verbraucht, und der verschachtelte Aufruf in interactAnchorAt() wird abgelehnt -
        // zusaetzlich fehlt dem Slot: der Combat-Slot ist noch auf den Anker geparkt. Der Anker
        // bleibt also liegen und wird von maintainNearbyAnchors() im Folgetick geladen. Deshalb steht
        // die Wartung jetzt HINTER der Aura-Platzierung: sonst nimmt sie sich im Folgetick sofort
        // wieder den LOOK-Slot, den die naechste Platzierung braucht.
        Vec3 center = Vec3.atCenterOf(spot);
        rotateAndRun(Rotations.getYaw(center), Rotations.getPitch(center), PRIORITY_ANCHOR, () -> {
            if (!useAnchors.get() || anchorMode.get() == 2) return;
            if (placeTrackedBlock(spot, anchor, false, 50)) {
                anchorPlaceCooldown = anchorDelay();
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
        // Ohne Aktion gearbeitet zu haben, lief der Scan ueber "delay(1)" in JEDEM Tick weiter - 567
        // Zellen, 20 Mal pro Sekunde, meistens ohne Ergebnis. Das Budget laesst nur jeden vierten Lauf
        // wirklich scannen. Die Reaktionszeit kostet das nicht: ein Anchor, den der Gegner gerade
        // platziert hat, ist nach vier Ticks immer noch da.
        if (!scanBudget.allows(com.provipvp.perf.ScanBudget.ScanKind.ANCHOR_MAINTENANCE, tickCounter)) {
            anchorMaintCooldown = 1;
            return;
        }

        Player self = mc.player;
        if (self == null || mc.level == null) return;
        BlockPos origin = self.blockPosition();

        // 9*7*9 = 567 Zellen. origin.offset() allozierte dabei 567 BlockPos pro Aufruf, und der Aufruf
        // passierte in jedem einzelnen Tick. Ein wiederverwendeter Cursor plus das Budget nehmen beides weg.
        final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int dx = -4; dx <= 4; dx++) {
            for (int dy = -3; dy <= 3; dy++) {
                for (int dz = -4; dz <= 4; dz++) {
                    cursor.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    BlockState st = mc.level.getBlockState(cursor);
                    if (!st.is(Blocks.RESPAWN_ANCHOR)) continue;
                    // Nur im Trefferfall eine unveraenderliche Position: sie landet in
                    // anchorsChargedByUs und in interactAnchorAt. Der Cursor selbst darf dort nicht
                    // landen - er wandert weiter, und die Merkliste wuerde dieselbe Instanz mehrfach
                    // unter verschiedenen Koordinaten fuehren.
                    BlockPos pos = cursor.immutable();

                    Vec3 center = Vec3.atCenterOf(pos);
                    if (Math.sqrt(self.distanceToSqr(center)) > 4.2) continue;

                    double selfDmg = effectiveSelfDamage(center, DamageUtils.anchorDamage(mc.player, center));
                    if (selfDmg > maxSelfDamage.get()) continue;

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

        Vec3 gapCenter = Vec3.atCenterOf(gap);
        if (effectiveSelfDamage(gapCenter, DamageUtils.anchorDamage(mc.player, gapCenter)) > maxSelfDamage.get()) return false;

        if (placeTrackedBlock(gap, anchor, true, 50)) {
            currentAction = "box-luecke";
            return true;
        }
        return false;
    }

    private boolean interactAnchorAt(BlockPos pos, FindItemResult item) {
        if (!useAnchors.get() || anchorMode.get() == 2) return false;
        if (drinkingFireRes) return false;
        // D3/D5: 4.5 fuer den Blockzugriff, und die Flaeche kommt aus der gesendeten Rotation.
        if (enforceReach.get() && mc.player != null && !PlaceCursorSolver.withinPlacementReach(eye(), pos)) return false;
        if (!tickGateAllows() || !cadenceAllows(ActionCadence.Action.RIGHT_CLICK)) return false;
        Vec3 center = Vec3.atCenterOf(pos);
        InteractionHand hand = item.isOffhand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        return queueWithCombatSlot(item, Rotations.getYaw(center), Rotations.getPitch(center), PRIORITY_ANCHOR,
            () -> BlockUtils.interact(solveHitResult(pos), hand, true));
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
        boolean hasAnchorItem = useAnchors.get() && anchorsExplodeHere() && hasActionableItem(Items.RESPAWN_ANCHOR)
            && hasActionableItem(Items.GLOWSTONE);
        boolean hasBedItem = useBeds.get() && bedsExplodeHere() && hasActionableItem(GodmodePvP::isBed);
        Module ca = Modules.get().get(CrystalAura.class);
        if (!hasCrystals && !hasAnchorItem && !hasBedItem) {
            stopOwnedCrystalAura();
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
            setCrystalAuraActive(ca, false);
            auraMode = 1;
            lastAnchorProgressTick = tickCounter;
            lastAuraSwitch = tickCounter;
        } else if (wantBed && auraMode != 2) {
            setCrystalAuraActive(ca, false);
            auraMode = 2;
            lastBedProgressTick = tickCounter;
            lastAuraSwitch = tickCounter;
        } else if (!wantAnchor && !wantBed && auraMode != 0) {
            setCrystalAuraActive(ca, true);
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

                    // Wirksamer Schaden statt binarer Deckung: die alte Regel hat auf offenem Feld
                    // jede Zelle verworfen, damit blieb best == 0 und die Aura-Wahl sah ueberhaupt
                    // keine brauchbare Crystal-Plaetze mehr.
                    double rawSelfDmg = crystal
                        ? DamageUtils.crystalDamage(mc.player, pos)
                        : DamageUtils.anchorDamage(mc.player, pos);
                    if (effectiveSelfDamage(pos, rawSelfDmg) > maxSelfDamage.get()) continue;

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

                    // Deckung ist ein Anteil, keine Verweigerung: effectiveSelfDamage() skaliert den
                    // Wert, der Deckel entscheidet. Die alte binare Regel hat hier jede Zelle im
                    // Nahkampf verworfen und die Kandidatenliste leer gemacht.
                    double selfDmg = effectiveSelfDamage(pos, DamageUtils.anchorDamage(mc.player, pos));
                    if (selfDmg > maxSelfDamage.get() || wouldBeLethal(selfDmg)) continue;

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
        // gleichwertigen Optionen hin und herwechseln, statt konsequent in der einen zu bleiben.
        java.util.List<Integer> order = new java.util.ArrayList<>();
        for (int i = 0; i < found.size(); i++) order.add(i);
        order.sort((a, b) -> {
            int cmp = Double.compare(dmgs.get(b), dmgs.get(a));
            return cmp != 0 ? cmp : Integer.compare(found.get(a).hashCode(), found.get(b).hashCode());
        });
        for (int i : order) anchorCandidates.add(found.get(i));
    }
    private boolean validExplosionSpot(BlockPos cell, boolean crystal) {
        if (mc.level == null) return false;
        if (!mc.level.getBlockState(cell).isAir()) return false;
        if (avoidLava.get() && isNearLava(cell)) return false;
        if (!hasRaycastLineOfSight(Vec3.atCenterOf(cell))) return false;

        BlockState below = mc.level.getBlockState(cell.below());
        if (crystal) {
            if (below.is(Blocks.OBSIDIAN) || below.is(Blocks.BEDROCK)) return true;
            // Freie Luft als Unterlage ist nur gueltig, wenn Obsidian direkt in Hotbar/Offhand liegt;
            // ein Main-Inventar-Stack kann CrystalAuras Support-Platzierung nicht ausfuehren.
            return below.isAir() && hasActionableItem(Items.OBSIDIAN);
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
     *  Idee wie die bestehende EndCrystal-Entity-Zaehlung direkt darueber).
     *
     *  <p>Delegiert an den Scanner, der mit EINEM wiederverwendeten {@code MutableBlockPos} durch den
     *  Wuerfel laeuft. Die eigene Variante hier rief {@code center.offset(dx,dy,dz)} auf und allozierte
     *  damit bei Radius 5 genau 1331 unveraenderliche {@code BlockPos} pro Aufruf - zweimal je Tick. */
    private int countNearbyBlocks(BlockPos center, int radius, java.util.function.Predicate<BlockState> matcher) {
        return scanner.countNearbyBlocks(center, radius, matcher);
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

                    double selfDmg = effectiveSelfDamage(pos, DamageUtils.bedDamage(mc.player, pos));
                    if (!bedSelfDamageAcceptable(selfDmg)) continue;

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

    /** Wie viele der neun AABB-Stichproben liegen im freien Explosionsstrahl. 0..9. */
    private int countExposedPoints(Vec3 explosionPos) {
        AABB box = mc.player.getBoundingBox();
        int exposed = 0;
        if (blastRayClear(explosionPos, box.getCenter())) exposed++;
        if (blastRayClear(explosionPos, new Vec3(box.minX, box.minY, box.minZ))) exposed++;
        if (blastRayClear(explosionPos, new Vec3(box.maxX, box.minY, box.minZ))) exposed++;
        if (blastRayClear(explosionPos, new Vec3(box.minX, box.maxY, box.minZ))) exposed++;
        if (blastRayClear(explosionPos, new Vec3(box.maxX, box.maxY, box.minZ))) exposed++;
        if (blastRayClear(explosionPos, new Vec3(box.minX, box.minY, box.maxZ))) exposed++;
        if (blastRayClear(explosionPos, new Vec3(box.maxX, box.minY, box.maxZ))) exposed++;
        if (blastRayClear(explosionPos, new Vec3(box.minX, box.maxY, box.maxZ))) exposed++;
        if (blastRayClear(explosionPos, new Vec3(box.maxX, box.maxY, box.maxZ))) exposed++;
        return exposed;
    }

    /**
     * Der Schaden, mit dem die Deckel tatsaechlich rechnen.
     *
     * <p>Frueher stand hier eine Verweigerung: "ist irgendein Punkt der Box exponiert, ist die
     * Position selbstschaedlich". Auf offenem Feld — also im Nahkampf, wo ein Anchor, ein Crystal
     * oder ein Bett ueberhaupt erst Sinn ergibt — sind ausnahmslos alle neun Stichproben exponiert.
     * Damit war die Kandidatenliste permanent leer, der Bot placing nichts und starb. Der Deckel in
     * {@code max-self-damage} wurde nie erreicht und war damit wirkungslos.
     *
     * <p>Jetzt ist die Deckung ein Anteil, kein Ausschluss. Siehe {@link SelfDamageExposure}.
     */
    private double effectiveSelfDamage(Vec3 explosionPos, double baseDamage) {
        if (baseDamage <= 0 || mc.level == null || mc.player == null) return 0;
        return SelfDamageExposure.effective(baseDamage, countExposedPoints(explosionPos));
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
        Vec3 spot = Vec3.atCenterOf(cell);
        // Deckel und Todesgrenze sehen denselben wirksamen Schaden: Deckung ist jetzt ein Anteil
        // und keine Verweigerung mehr, also muss sie in beiden Pruefungen stecken.
        double selfDamage = effectiveSelfDamage(spot, DamageUtils.crystalDamage(self, spot));
        if (selfDamage > maxSelfDamage.get() * selfDamageScale) return false;
        // D8: Der Deckel ist einstellbar, der eigene Tod nicht. Eine Platzierung, deren Schaden die
        // AKTUELLEN HP erreicht, wird unabhaengig von max-self-damage verworfen — sonst kann ein
        // hochgesetzter Deckel (etwa 12) einen Spieler mit 8 HP toeten.
        return !wouldBeLethal(selfDamage);
    }

    /**
     * D8: die Todesgrenze. Geprueft wird die projizierte Schadenshoehe gegen die GESAMTE aktuelle
     * Lebensenergie inklusive Absorption — die Absorption zaehlt, weil sie den Schaden zuerst
     * auffaengt und der Bot dann am Leben bleibt.
     */
    private boolean wouldBeLethal(double projectedSelfDamage) {
        if (!lethalSelfDamageGuard.get() || mc.player == null) return false;
        double totalHealth = mc.player.getHealth() + mc.player.getAbsorptionAmount();
        return !SelfDamageGuard.allows(projectedSelfDamage, maxSelfDamage.get(), totalHealth);
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
            bedUnreachableTicks = 0;
            // Wie beim Anker: kein Kandidat ist keine gescheiterte Platzierung. "Crystal erzwingen"
            // als Antwort darauf schaltet im Nether genau die falsche Waffe zu - Anker koennen dort
            // gar nicht explodieren, also gibt es auch keinen Ausweichweg.
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

                    double selfDmg = effectiveSelfDamage(posCenter, DamageUtils.bedDamage(mc.player, posCenter));
                    if (!bedSelfDamageAcceptable(selfDmg)) continue;

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
        // D3/D5: dieselbe Blockreichweite und dieselbe aufgeloeste Flaeche wie beim Anker — ein Bett
        // ist ein Block, kein Entity, die 3.0 des Nahkampfs gelten hier nicht.
        if (enforceReach.get() && mc.player != null && !PlaceCursorSolver.withinPlacementReach(eye(), pos)) return false;
        if (!tickGateAllows() || !cadenceAllows(ActionCadence.Action.RIGHT_CLICK)) return false;
        Vec3 center = Vec3.atCenterOf(pos);
        return rotateAndRun(Rotations.getYaw(center), Rotations.getPitch(center), PRIORITY_BED, () ->
            BlockUtils.interact(solveHitResult(pos), InteractionHand.MAIN_HAND, true)
        );
    }

    /** Eigenschaden-Freigabe fuer Bett-Explosionen: eigener (hoeherer) Deckel als Crystal/Anchor, plus
     *  harter Selbstmord-Schutz. Bett-PvP ist bewusst ein Trade - der Bot nimmt Schaden in Kauf und
     *  faengt ihn mit Totem/Heiltrank ab - aber eine Zuendung, die die eigenen AKTUELLEN HP (inkl.
     *  Absorption) toeten wuerde, ist nie ein guter Trade und wird unabhaengig vom eingestellten
     *  Deckel verworfen (entspricht Meteors BedAura 'anti-suicide').
     *  Wendet bedSelfDamageMultiplier an (Paper/Spigot haben oft 2-3x hoeheren Self-Damage). */
    private boolean bedSelfDamageAcceptable(double selfDmg) {
        double adjustedDmg = selfDmg * bedSelfDamageMultiplier.get();
        if (adjustedDmg > bedMaxSelfDamage.get()) return false;
        double effectiveHp = mc.player.getHealth() + mc.player.getAbsorptionAmount();
        // D8: derselbe Todes-Schutz wie beim Crystal — die 1.0-Marge hier ist ein Notfallpuffer,
        // aber kein Ersatz fuer die echte Grenze. BedPvP nimmt den Schaden bewusst in Kauf; toeten
        // darf es nicht.
        if (lethalSelfDamageGuard.get()
            && !SelfDamageGuard.allows(adjustedDmg, bedMaxSelfDamage.get(), effectiveHp)) {
            return false;
        }
        return adjustedDmg < effectiveHp - 1.0;
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

    // ---------- D14: verzoegerungsartige Einstellungen als echte Intervalle ----------

    /**
     * D14: der Perlen-Cooldown als Intervall statt als fester Wert.
     *
     * <p>Drei Perlenwuerfe im exakt gleichen Abstand sind ein Muster, kein Zufall. Das Fenster wird
     * einmal pro Zyklus gezogen und fuer dessen Laenge eingefroren — sonst wuerde der Bot die
     * Wartezeit JEDEN Tick neu ziehen und damit faktisch nie warten.
     */
    private int pearlDelay() {
        if (instantMode.get()) return 0;
        refreshDelayWindows();
        return pearlDelayWindow;
    }

    private int dtapDelay() {
        if (instantMode.get()) return 0;
        refreshDelayWindows();
        return dtapDelayWindow;
    }

    private int anchorDelay() {
        if (instantMode.get()) return 0;
        refreshDelayWindows();
        return anchorDelayWindow;
    }

    /**
     * Zieht die drei D14-Fenster gemeinsam, aber hoechstens alle 20 Ticks. Das Intervall bleibt
     * dadurch ueber den ganzen Zyklus stabil, ohne pro Tick eine Zufallszahl zu erzeugen.
     */
    private void refreshDelayWindows() {
        if (tickCounter - delayWindowTick < 20) return;
        delayWindowTick = tickCounter;
        pearlDelayWindow = new RandomBetween.RandomBetweenInt(pearlDelayMin.get(), pearlDelayMax.get()).sample(rng);
        dtapDelayWindow = new RandomBetween.RandomBetweenInt(dtapDelayMin.get(), dtapDelayMax.get()).sample(rng);
        anchorDelayWindow = new RandomBetween.RandomBetweenInt(anchorDelayMin.get(), anchorDelayMax.get()).sample(rng);
    }

    /** Reiht eine Rotation+Aktion ein. Anders als vorher (ein starrer 1-Aktion-pro-Tick-Mutex, der
     *  jeden weiteren rotateAndRun()-Aufruf im selben Tick komplett auf den naechsten Tick verschob)
     *  koennen jetzt mehrere UNABHAENGIGE Aktionen pro Tick korrekt ausgerichtet feuern - Meteors
     *  Rotations-Klasse unterstuetzt das nativ: der ERSTE Eintrag eines Ticks laeuft ueber den
     *  normalen Bewegungspaket-Pfad (echte Rotation wird gesetzt), JEDER WEITERE bekommt mit
     *  clientSide=true ebenfalls kurzzeitig die echte Rotation gesetzt - exakt fuer die Dauer seines
     *  eigenen Callbacks (siehe Rotations.onSendMovementPacketsPost), danach zurueckgesetzt. Gibt
     *  jetzt immer true zurueck - bestehende Aufrufer, die frueher auf false pruefen mussten,
     *  funktionieren unveraendert weiter (der Erfolgsfall greift jetzt einfach immer).
     *  @param priority Priorität der Aktion. Nur Aktionen mit Priority > PRIORITY_LOOK zählen als
     *  "echte" Aktionen für realActionThisTick (verhindert free-look Tail-Flush). */
    private boolean rotateAndRun(double yaw, double pitch, int priority, Runnable callback) {
        // D19: hoechstens eine Blickrichtung pro Movement-Paket. Faellt sie aus, laeuft die Aktion
        // mit der vorherigen Rotation — das ist schlechter als keine Aktion, aber besser als ein
        // Movement-Paket mit zwei verschiedenen Yaw-Werten, das kein echter Client produziert.
        if (actionCadence.get() && !cadenceAllows(ActionCadence.Action.LOOK)) return false;

        // Einmal quantisieren, beide Winkel aus DERSELBEN Rotation: der Akkumulator merkt sich die
        // zuletzt gesendete Lage, ein zweiter Aufruf wuerde also gegen einen Zwischenstand rechnen.
        GcdRotator.Rotation sent = quantizeForSend(yaw, pitch);
        Rotations.rotate(sent.yaw(), sent.pitch(), priority, rotationsThisTick > 0, callback);
        rotationsThisTick++;
        if (priority > PRIORITY_LOOK) realActionThisTick = true;
        return true;
    }

    /** Der Divisor, auf den die Maus abtastet. Meteor quantisiert selbst NICHT — es speichert rohe
     *  Float-Winkel in serverYaw/serverPitch, und Grim verlangt, dass jedes gesendete Delta ein
     *  Vielfaches dieses Rasters ist. */
    private double sensitivityDivisor() {
        double sensitivity = mc.options == null ? 0.5 : mc.options.sensitivity().get();
        return GcdRotator.sensitivityDivisor(sensitivity);
    }

    /**
     * Quantisiert die abgeschickte Rotation auf das Mausraster — der eine Schritt, den Grim akzeptiert.
     *
     * <p>Der Akkumulator wird einmal pro Kampf geseedet und bei Zielwechsel verworfen: die Gitterbasis
     * ist ein Vielfaches des Divisors relativ zur Kamera, und eine Rotation aus dem vorigen Kampf
     * liegt im Allgemeinen nicht mehr auf diesem Raster. Ohne den Reset wäre das erste Delta nach dem
     * Zielwechsel ein Sprung zwischen zwei fremden Gittern.
     */
    private GcdRotator.Rotation quantizeForSend(double yaw, double pitch) {
        if (!gcdRotation.get() || mc.player == null) return new GcdRotator.Rotation(yaw, pitch);

        LivingEntity target = tracedTarget;
        UUID targetId = target == null ? null : target.getUUID();
        if (targetId == null || !targetId.equals(gcdSeededFor)) {
            gcd.reset();
            gcdSeededFor = targetId;
        }
        if (!gcd.isSeeded()) gcd.seed(mc.player.getYRot(), mc.player.getXRot(), sensitivityDivisor());

        double jitter = gcdJitterSteps.get() * sensitivityDivisor();
        return gcd.quantize(yaw, pitch, sensitivityDivisor(), jitter, rng);
    }

    /** Die zuletzt GESENDETE Rotation — die einzige, die eine Aktion rechtfertigt. Die Kamera
     *  waere die falsche Groesse: Grim sieht ausschliesslich die Floats im Movement-Paket. */
    private GcdRotator.Rotation sentRotation() {
        if (mc.player == null) return new GcdRotator.Rotation(0, 0);
        if (!gcd.isSeeded()) return new GcdRotator.Rotation(mc.player.getYRot(), mc.player.getXRot());
        return gcd.last();
    }

    /**
     * D19: das Aktions-Ledger. Eine Platzierung, ein Angriff, ein Rechtsklick, ein Schwung und eine
     * Blickrichtung pro Movement-Paket — mehr als eins pro Paket sieht serverseitig wie ein
     * abgeschnittener Batch aus, den kein echter Client produziert.
     *
     * @return true, wenn die Aktion in das Ledger passt
     */
    private boolean cadenceAllows(ActionCadence.Action action) {
        if (!actionCadence.get()) return true;
        if (mc.player != null) cadence.setUsingItem(mc.player.isUsingItem());
        ActionCadence.Decision decision = cadence.request(action);
        if (decision.allowed()) cadenceActionIndex++;
        return decision.allowed();
    }

    /** D17: Unter Last pausiert oder drosselt das Gate die Aktionsrate. Ein Bot, der bei 100 ms pro
     *  Tick weiter exakt dieselbe-rate feuert, verpufft seine Aktionen im Backlog des Servers — sie
     *  kommen verspaetet, in Bloecken, und als Makrospamming durch. */
    private boolean tickGateAllows() {
        if (!lagThrottle.get() || mc.level == null) return true;
        if (tickGate == null) {
            tickGate = new TickRateGate(TickRateGate.DEFAULT_LAG_THRESHOLD,
                TickRateGate.DEFAULT_SPIKE_THRESHOLD, TickRateGate.DEFAULT_LOW_HEALTH_FRACTION,
                Math.max(1, lagThrottleEvery.get()));
        }
        double healthFraction = mc.player == null || mc.player.getMaxHealth() <= 0
            ? 1.0
            : (mc.player.getHealth() + mc.player.getAbsorptionAmount()) / mc.player.getMaxHealth();
        boolean allowed = tickGate.allows(tickGate.evaluate(lastTickRate, healthFraction), throttleIndex);
        // JEDER Versuch zaehlt, nicht nur der erlaubte. Das Gate entscheidet ueber
        // `index % n == 0`: ein Zaehler, der bei einem abgelehnten Versuch stehen bleibt, klemmt
        // bei einem ungeraden Wert fest und blockiert dann ALLES — oder, wenn er pro Tick
        // zurueckgesetzt wird, laesst er unabhaengig von n immer nur die erste Aktion zu. Beides
        // macht lag-throttle-every wirkungslos. Deshalb ein eigener, tickuebergreifender Zaehler.
        throttleIndex++;
        return allowed;
    }

    // ---------- D2/D3/D5: Aktionen gegen die GESENDETE Rotation pruefen ----------

    /** D2/D3: Weltzugriff fuer die Strahlpruefung, bewusst ohne mc.-Abhaengigkeit im Helfer. */
    private final ActionRayValidator.World rayWorld =
        pos -> mc.level != null && mc.level.getBlockState(pos).isSolidRender();

    /** D5: derselbe Zugriff fuer die Platzierungs-Geometrie. */
    private final PlaceCursorSolver.World placeWorld =
        new PlaceCursorSolver.World() {
            @Override
            public boolean isSolid(BlockPos pos) {
                return mc.level != null && mc.level.getBlockState(pos).isSolidRender();
            }

            @Override
            public boolean isReplaceable(BlockPos pos) {
                return mc.level != null && mc.level.getBlockState(pos).canBeReplaced();
            }
        };

    private Vec3 eye() {
        return mc.player == null ? Vec3.ZERO : mc.player.getEyePosition();
    }

    /**
     * D2/D3: Darf der Entity-Angriff mit der GESENDETEN Rotation ueberhaupt raus?
     *
     * <p>Geprueft wird die Rotation, die das Movement-Paket traegt, nicht die Kamera — die beiden
     * unterscheiden sich um die Quantisierung, und genau diese Differenz ist der Winkel, den Grim
     * nachrechnet. Die Reichweite kommt aus {@link ReachPolicy} und ist fuer {@code BREAK_ENTITY} und
     * {@code MELEE} getrennt von der Blockplatzierung.
     *
     * @return true, wenn die Aktion gesendet werden darf
     */
    private boolean rayAllowsEntityAttack(Entity target, ReachPolicy.Action action) {
        if (!rayValidateActions.get() || mc.player == null || target == null) return true;

        AABB box = target.getBoundingBox();
        ActionRayValidator.Box target3d = new ActionRayValidator.Box(
            box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
        GcdRotator.Rotation sent = sentRotation();
        GcdRotator.Rotation camera = new GcdRotator.Rotation(mc.player.getYRot(), mc.player.getXRot());
        return ActionRayValidator.validateAttack(sent, camera, eye(), target3d, action, rayWorld).valid();
    }

    /**
     * D3/D5: Die reichweitengebundene Kurzform. Wird zusaetzlich zu {@link #rayAllowsEntityAttack}
     * benutzt, damit die reine Distanzpruefung auch dann greift, wenn die Strahlvalidierung aus ist —
     * die beiden sind unterschiedliche Fehlerklassen: ein Crystal auf 4.1 Bloecken ist ein reiner
     * Reichweitenfehler, keine schlechte Rotation.
     */
    private boolean withinActionReach(Entity target, ReachPolicy.Action action) {
        if (mc.player == null || target == null) return false;
        if (!enforceReach.get()) return true;
        double distance = eye().distanceTo(target.getBoundingBox().getCenter());
        return ReachPolicy.allows(action, distance);
    }

    // ---------- D15: Angriffe als Paketpaar ----------

    /**
     * D15: der Dispatcher fuer crystal/melee/shield-break.
     * <p>{@code SwingMode.BOTH} schickt erst das ANIMATION-Paket und dann eine NUR-lokale Animation.
     * Der Vanilla-Weg {@code attack()} + {@code swing()} wuerde das Paket ein zweites Mal senden — der
     * Server sieht fuer EINEN Schlag zwei Schwingen, was wie ein doppelter Client aussieht.
     */
    private AttackDispatcher dispatcher() {
        if (dispatcher == null) {
            dispatcher = new AttackDispatcher((packet, argument) -> {
                if (mc.getConnection() == null) return;
                switch (packet) {
                    case INTERACT -> mc.getConnection().send(new ServerboundInteractPacket(
                        argument, InteractionHand.MAIN_HAND, Vec3.ZERO, mc.player != null && mc.player.isShiftKeyDown()));
                    case ANIMATION -> mc.getConnection().send(
                        new ServerboundSwingPacket(argument == AttackDispatcher.OFF_HAND
                            ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND));
                }
            }, hand -> {
                // Nur die lokale Animation: das Paket ist schon raus. resetAttackStrengthTicker
                // erneuert den Cooldown-Zaehler, ohne ein zweites Swing-Paket zu erzeugen.
                if (mc.player != null) mc.player.resetAttackStrengthTicker();
            });
        }
        return dispatcher;
    }

    /** D15: der Angriff als Paketpaar. Ersetzt {@code attack()} + {@code swing()}. */
    private void dispatchAttack(Entity target) {
        if (dispatchAttacks.get()) {
            dispatcher().attack(target.getId(), AttackDispatcher.MAIN_HAND, AttackDispatcher.SwingMode.BOTH);
            return;
        }
        mc.gameMode.attack(mc.player, target);
        mc.player.swing(InteractionHand.MAIN_HAND);
    }

    // ---------- D7/D9/D10: Angriffsgates ----------

    /**
     * D7/D9/D10: die drei Angriffsbedingungen in einer Auswertung.
     *
     * <p>Die Todesgrenze wird als Verletzlichkeits-Fenster (hurtTime) uebergeben, nicht als
     * Health-Vergleich: waehrend der i-Frames nimmt ein Schlag serverseitig ueberhaupt keinen Schaden
     * an, egal wie viel Cooldown dahintersteckt.
     */
    private AttackGate.Verdict gateAttack(LivingEntity target) {
        if (!gateAttacks.get() || mc.player == null || target == null) return new AttackGate.Verdict(true,
            AttackGate.Reason.NONE, false);

        float strength = mc.player.getAttackStrengthScale(0.5f);
        return AttackGate.evaluate(new AttackGate.AttackState(
            target.hurtTime, strength, target.getHealth(), playerAttackDamage()), minAttackStrength.get());
    }

    /** Der Schaden, mit dem der Bot zuschlaegt — der Basiswert der gehaltenen Waffe, soweit sich
     *  der aus dem Attribut lesen laesst. Nur der Vergleich mit der Ziel-HP zaehlt fuer den
     *  lethalen Sprung-Crit. */
    private double playerAttackDamage() {
        if (mc.player == null) return 0.0;
        double base = mc.player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        return Double.isFinite(base) ? base : 1.0;
    }

    /** Harter Mainhand-Mutex fuer Combat-Aktionen. Baritone/Follow/CustomGoal werden waehrend der
     *  Reservierung angehalten, damit kein Inventory-Select zwischen UseItem/Interact und dem
     *  nachfolgenden Slot-Restore laeuft. Meteor InvUtils.previousSlot wird hier nicht benutzt. */
    private boolean reserveCombatSlot(int targetSlot) {
        if (targetSlot < 0 || targetSlot > 8 || combatSlotReserved || mc.player == null) return false;
        int selected = mc.player.getInventory().getSelectedSlot();
        if (selected != targetSlot && !InvUtils.swap(targetSlot, false)) return false;
        // D19: Der Slot-Wechsel muss VOR der Aktion liegen. Die Regel feuert deshalb auf den
        // FOLGENDEN Request — reserveCombatSlot() laeuft immer vor der Aktion, das nachlaufende
        // releaseCombatSlot() danach. Ohne diese Reihenfolge wuerde das Ledger einen Slot-Wechsel
        // NACH der Aktion verbuchen und die naechste Aktion faelschlich ablehnen, waehrend der
        // Restore selbst legal ist.
        if (actionCadence.get() && cadence.hasActionThisTick()) cadence.onSlotChange();
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

        // C8: Splash-Traenke kollidieren mit End Crystals. Ein Heiltrank, der neben der eigenen
        // Crystal-Kette einschlaegt, zerstoert sie im Moment, in dem der Bot sie am dringendsten
        // braucht. Lieber einen Tick spaeter heilen als die eigene Schadensquelle wegraeumen.
        if (protectOwnCrystals.get() && splashTargetHitsOwnCrystal(splashTarget)) return;

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

    /**
     * C8: Liegt der Einschlagpunkt des Heiltraenkes im Splash-Quader eines eigenen Crystals?
     *
     * <p>Der Splash-Wirkungsbereich ist ein Quader von 8.25 x 8.25 x 4.25 Bloecken. Geprueft wird
     * deshalb der Quader, nicht nur der Punkt — ein Crystal knapp neben dem Einschlagpunkt wuerde
     * sonst als ungefaehrlich durchgehen und trotzdem weggesprengt.
     */
    private boolean splashTargetHitsOwnCrystal(Vec3 splashTarget) {
        if (mc.level == null) return false;
        AABB splash = new AABB(
            splashTarget.x - 4.125, splashTarget.y - 2.125, splashTarget.z - 4.125,
            splashTarget.x + 4.125, splashTarget.y + 2.125, splashTarget.z + 4.125);
        for (EndCrystal ec : mc.level.getEntitiesOfClass(EndCrystal.class, splash)) {
            if (crystalOwnership.owns(ec.getId())) return true;
        }
        return false;
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
        return InvHelper.find(GodmodePvP::isFireResPotion);
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

                    double selfDmg = effectiveSelfDamage(Vec3.atCenterOf(cell),
                        DamageUtils.crystalDamage(mc.player, Vec3.atCenterOf(cell)));
                    if (selfDmg > maxSelfDamage.get() * 0.6) continue;

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
                if (tickCounter - dtapStageTick > 15) { dtapStage = 0; dtapFirstCrystalId = -1; return; } // Fenster verpasst
                if (!mc.level.getBlockState(dtapSpot.above()).isAir()) { dtapStage = 0; dtapFirstCrystalId = -1; return; } // besetzt
                if (!crystalPlacementSafe(mc.player, dtapSpot.above(), 0.6)) { dtapStage = 0; dtapFirstCrystalId = -1; return; }

                FindItemResult crystal = InvHelper.find(Items.END_CRYSTAL);
                if (!crystal.found()) { dtapStage = 0; dtapFirstCrystalId = -1; return; }

                if (placeCrystal(dtapSpot, crystal)) {
                    dtapStage = 2;
                    dtapStageTick = tickCounter;
                    dtapFirstCrystalId = -1; // wird in onEntityAdded gesetzt
                } // sonst Rotations-Slot belegt - naechster Tick erneut versuchen, Fenster laeuft noch
            }
            case 2 -> { // 1. Crystal platziert - auf Server-Bestaetigung (EntityAdded) warten, dann zuenden
                if (dtapFirstCrystalId == -1) {
                    if (tickCounter - dtapStageTick > 20) { dtapStage = 0; dtapCooldown = dtapDelay(); } // Timeout: Entity nie angekommen
                    return;
                }
                var entity = mc.level.getEntity(dtapFirstCrystalId);
                if (!(entity instanceof EndCrystal ec)) { dtapStage = 0; dtapFirstCrystalId = -1; dtapCooldown = dtapDelay(); return; } // Entity schon wieder weg
                if (attackCrystal(ec)) {
                    dtapStage = 3;
                    dtapStageTick = tickCounter;
                    dtapFirstCrystalId = -1;
                }
            }
            case 3 -> { // Trefferimmunitaet abwarten (~10 Ticks = 0.5s), dann 2. Crystal
                if (tickCounter - dtapStageTick < 10) return;
                if (tickCounter - dtapStageTick > 30
                    || !mc.level.getBlockState(dtapSpot.above()).isAir()
                    || !crystalPlacementSafe(mc.player, dtapSpot.above(), 0.6)) {
                    dtapStage = 0;
                    dtapFirstCrystalId = -1;
                    dtapCooldown = dtapDelay();
                    return;
                }

                FindItemResult crystal = InvHelper.find(Items.END_CRYSTAL);
                if (!crystal.found()) { dtapStage = 0; dtapFirstCrystalId = -1; return; }

                if (placeCrystal(dtapSpot, crystal)) {
                    dtapStage = 4;
                    dtapStageTick = tickCounter;
                }
            }
            case 4 -> { // 2. Crystal steht - zuenden, fertig
                // D6: bewerteter Pick statt "der erste Crystal im Kasten" — im D-Tap stehen
                // haeufig zwei eigene Crystals uebereinander, und nur einer davon bringt Schaden.
                EndCrystal ec = pickCrystalToBreak(dtapSpot, target);
                if (ec == null) {
                    if (tickCounter - dtapStageTick > 4) { dtapStage = 0; dtapFirstCrystalId = -1; dtapCooldown = dtapDelay(); }
                    return;
                }
                if (attackCrystal(ec)) {
                    dtapStage = 0;
                    dtapFirstCrystalId = -1;
                    dtapCooldown = dtapDelay();
                }
            }
            default -> { dtapStage = 0; dtapFirstCrystalId = -1; }
        }
    }

    /** @return true, wenn die Rotation+Platzierung tatsaechlich eingereiht wurde (Rotations-Slot frei war). */
    private boolean placeCrystal(BlockPos floor, FindItemResult item) {
        if (drinkingFireRes) return false;
        if (!tickGateAllows() || !cadenceAllows(ActionCadence.Action.PLACE)) return false;
        Vec3 center = Vec3.atCenterOf(floor);
        InteractionHand hand = item.isOffhand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;

        // D5: Die Klickflaeche und der Cursor kommen aus der gesendeten Rotation, nicht aus einem
        // geratenen BlockHitResult. Ein geratener Treffer zeigt bei schraegen Wanden in die
        // Nachbarflaeche — der Server setzt dann den Block neben dem beabsichtigten.
        Runnable place = () -> BlockUtils.interact(solveHitResult(floor), hand, true);
        boolean queued = queueWithCombatSlot(item, Rotations.getYaw(center), Rotations.getPitch(center),
            PRIORITY_CRYSTAL, place);
        if (queued) notePlacedCrystalOn(floor);
        return queued;
    }

    /**
     * D5: loest Block+Fläche+Cursor aus der gesendeten Rotation auf.
     *
     * <p>Findet der Strahl keinen zulaessigen Treffer, wird auf die Richtung zum Spieler
     * zurueckgefallen — sonst wuerde die Aktion bei jeder Kante ins Leere zeigen.
     */
    private BlockHitResult solveHitResult(BlockPos clicked) {
        if (!solvePlaceCursor.get() || mc.player == null) {
            Vec3 center = Vec3.atCenterOf(clicked);
            return new BlockHitResult(center, BlockUtils.getDirection(clicked), clicked, true);
        }
        GcdRotator.Rotation sent = sentRotation();
        PlaceCursorSolver.Placement placement = PlaceCursorSolver.trace(
            eye(), PlaceCursorSolver.lookVector(sent.yaw(), sent.pitch()), placeWorld,
            ReachPolicy.BLOCK_INTERACTION_REACH);
        if (placement != null && placement.clicked().equals(clicked)) {
            return new BlockHitResult(placement.hit(), placement.face(), placement.clicked(), true);
        }
        Vec3 center = Vec3.atCenterOf(clicked);
        return new BlockHitResult(center, PlaceCursorSolver.faceTowardPlayer(clicked, eye()), clicked, true);
    }

    /** D6: merkt sich, welcher Boden zu einem selbstgesetzten Crystal gehoert. Der Server bestaetigt
     *  die Entity erst mit dem Entity-Added-Event — dort wird die ID notiert (siehe onEntityAdded). */
    private void notePlacedCrystalOn(BlockPos floor) {
        if (dtapSpot != null && dtapSpot.equals(floor)) return; // D-Tap notiert selbst, siehe Stage 1
        pendingCrystalCells.add(floor.immutable());
    }

    private EndCrystal findCrystalAbove(BlockPos floor) {
        AABB box = new AABB(floor.above()).inflate(0.6, 1.0, 0.6);
        for (EndCrystal ec : mc.level.getEntitiesOfClass(EndCrystal.class, box)) return ec;
        return null;
    }

    /**
     * D6: bewerteter Crystal-Pick.
     *
     * <p>Ersetzt "den ersten/nächsten Crystal nehmen": bewertet wird der projizierte Schaden auf dem
     * aktuellen Ziel, und nur Crystals, denen der Bot selbst vertraut. Faellt der Scorer aus, bleibt
     * der alte Naechstes-Fallback — ein fehlender eigener Crystal darf den D-Tap nicht tot machen.
     */
    private EndCrystal pickCrystalToBreak(BlockPos floor, LivingEntity target) {
        AABB box = new AABB(floor.above()).inflate(0.6, 1.0, 0.6);
        List<EndCrystal> found = mc.level.getEntitiesOfClass(EndCrystal.class, box);
        if (!scoreCrystals.get() || target == null || found.isEmpty()) {
            for (EndCrystal ec : found) return ec;
            return null;
        }

        List<CrystalScorer.Candidate> candidates = new ArrayList<>(found.size());
        for (EndCrystal ec : found) {
            candidates.add(new CrystalScorer.Candidate(ec.getId(), ec.tickCount,
                ec.getX(), ec.getY(), ec.getZ()));
        }
        CrystalScorer.ScoreConfig config = new CrystalScorer.ScoreConfig(
            candidate -> DamageUtils.crystalDamage(target, new Vec3(candidate.x(), candidate.y(), candidate.z())),
            crystalOwnership.snapshot(), crystalMinTickAge.get(), crystalMinPickDamage.get());

        return CrystalScorer.pick(candidates, config)
            .flatMap(choice -> mc.level.getEntity(choice.entityId()) instanceof EndCrystal ec ? java.util.Optional.of(ec)
                : java.util.Optional.<EndCrystal>empty())
            .orElseGet(() -> found.get(0));
    }

    /**
     * @return true, wenn die Rotation+Attacke tatsaechlich eingereiht wurde (Rotations-Slot frei war).
     */
    private boolean attackCrystal(EndCrystal ec) {
        if (ec == null || mc.player == null) return false;
        // D7: Ein End Crystal ist kein LivingEntity und hat kein hurtTime. Seine Verletzlichkeit
        // steckt in der Beacon-/Strahlphase — waehrend sie laeuft, nimmt er keinen Schaden an.
        if (gateAttacks.get() && ec.getBeamTarget() != null) return false;
        // D3: 3.0 fuer den Entity-Zugriff — nicht die 4.5 der Blockplatzierung.
        if (!withinActionReach(ec, ReachPolicy.Action.BREAK_ENTITY)) return false;
        if (!rayAllowsEntityAttack(ec, ReachPolicy.Action.BREAK_ENTITY)) return false;
        if (!tickGateAllows() || !cadenceAllows(ActionCadence.Action.ATTACK)) return false;

        // D9: nicht mit einem Werkzeug schwingen, das dem Crystal 0 Schaden macht.
        if (!crystalToolAcceptable()) return false;

        Vec3 center = ec.getBoundingBox().getCenter();
        return rotateAndRun(Rotations.getYaw(center), Rotations.getPitch(center), PRIORITY_CRYSTAL,
            () -> dispatchAttack(ec));
    }

    /** D9: Werkzeug-Kompatibilitaet gegen die Hotbar. Der Wechsel passiert VOR dem Schlag, damit
     *  das nachlaufende releaseCombatSlot legal bleibt. */
    private boolean crystalToolAcceptable() {
        if (!crystalToolCheck.get() || mc.player == null) return true;
        List<CrystalToolPolicy.Tool> hotbar = new ArrayList<>(9);
        for (int slot = 0; slot < 9; slot++) {
            hotbar.add(toCrystalTool(mc.player.getInventory().getItem(slot)));
        }
        int held = mc.player.getInventory().getSelectedSlot();
        CrystalToolPolicy.Verdict verdict = TOOL_POLICY.evaluate(held, hotbar);
        if (verdict.canAttack()) return true;
        if (verdict.needsSwitch() && !combatSlotReserved) {
            return reserveCombatSlot(verdict.switchToSlot());
        }
        return false;
    }

    private static CrystalToolPolicy.Tool toCrystalTool(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return CrystalToolPolicy.Tool.HAND;
        // 26.2 fuehrt keine SwordItem-/PickaxeItem-Klassen mehr - Schwerte und Spitzhacken sind
        // normale Items, die nur ueber die Item-Tags erkennbar sind. Deshalb Tags statt instanceof;
        // AxeItem und ShovelItem existieren zwar, die Tags decken aber auch Trank- und Mod-Varianten ab.
        if (stack.is(ItemTags.SWORDS)) return CrystalToolPolicy.Tool.SWORD;
        if (stack.is(ItemTags.AXES)) return CrystalToolPolicy.Tool.AXE;
        if (stack.is(ItemTags.PICKAXES)) return CrystalToolPolicy.Tool.PICKAXE;
        if (stack.is(ItemTags.SHOVELS)) return CrystalToolPolicy.Tool.SHOVEL;
        // Mace, Bogen, Food und alles Unbekannte: OTHER, nicht HAND. Sonst wuerde die Regel "leere
        // Hand bricht keinen Crystal" faelschlich als "unbekannt bricht keinen Crystal" gelesen.
        return CrystalToolPolicy.Tool.OTHER;
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
        if (!target.getUUID().equals(followedId)) followedId = target.getUUID();
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
        holeEscapeActive = false;
        holeEscapeStartedTick = -1;
        holeEscapeLastPosition = null;
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
                    // Kuerzestes Fenster im Code. ShieldWindow.until() haelt es auf MIN_USEFUL_TICKS,
                    // denn ohne die 5 Aktivierungs-Ticks waeren die 10 Ticks zur Haelfte nutzlos.
                    shieldUntil = ShieldWindow.until(tickCounter, 10);
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

        if (activeHole != null && heightCalcOrigin != null && heightCalcOrigin.distSqr(targetPos) <= 1.0
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
        Vec3 spot = Vec3.atCenterOf(gap);
        if (!isCurrentCitySurround(gap, target) || !isStandable(gap)
            || self.distanceToSqr(spot) > 20.25
            || effectiveSelfDamage(spot, DamageUtils.crystalDamage(self, spot)) > maxSelfDamage.get()
            || DamageUtils.crystalDamage(target, spot) <= 0
            || hitsFriend(spot, true)) return;

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
                        && bedSelfDamageAcceptable(effectiveSelfDamage(Vec3.atCenterOf(pos),
                            DamageUtils.bedDamage(mc.player, Vec3.atCenterOf(pos))));

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
     *  und im schlimmsten Fall ein SPAETER dort platziertes gegnerisches Bett als "unseres" durchwinken.
     *
     *  <p>{@code anchorsChargedByUs} stand hier lange nicht drin und lief dadurch fuer die ganze Sitzung
     *  mit. Das war nicht nur ein Speicherleck: {@code maintainNearbyAnchors} fragt {@code contains(pos)}
     *  ab, um ein zweites Glowstone zu verhindern - ein veralteter Eintrag markiert die Zelle also fuer
     *  immer als "geladen von uns". Geraet wurde die Liste nur an zwei Stellen, ein zerstoerter Anchor
     *  blieb also fuer immer markiert. Ein Anchor verschwindet beim Zuenden, und genau dann ist der
     *  Eintrag ebenfalls ueberfluessig - der Blockzustand ist damit das richtige Pruefkriterium. */
    private void pruneOwnedBlocks() {
        if (mc.player == null || mc.level == null) return;
        BlockPos feet = mc.player.blockPosition();
        bedsPlacedByUs.removeIf(p -> p.distSqr(feet) > 256
            || !(mc.level.getBlockState(p).getBlock() instanceof BedBlock));
        pistonPartsByUs.removeIf(p -> {
            if (p.distSqr(feet) > 256) return true;
            BlockState st = mc.level.getBlockState(p);
            return !(st.is(Blocks.PISTON) || st.is(Blocks.STICKY_PISTON) || st.is(Blocks.REDSTONE_BLOCK)
                || st.is(Blocks.OBSIDIAN) || st.is(Blocks.MOVING_PISTON) || st.is(Blocks.PISTON_HEAD));
        });
        anchorsChargedByUs.removeIf(p -> p.distSqr(feet) > 256
            || !mc.level.getBlockState(p).is(Blocks.RESPAWN_ANCHOR));
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
                if (tickCounter - pistonStageTick < 2) return false;
                FindItemResult crystal = InvHelper.find(Items.END_CRYSTAL);
                if (!crystal.found()) {
                    resetPistonAura();
                    return false;
                }
                if (!crystalPlacementSafe(self, pistonCrystalCell, 1.0)) {
                    resetPistonAura();
                    return false;
                }
                Vec3 base = Vec3.atCenterOf(pistonCrystalCell.below());
                rotateAndRun(Rotations.getYaw(base), Rotations.getPitch(base), PRIORITY_CRYSTAL,
                    () -> {
                        if (withCombatSlot(crystal, () -> BlockUtils.interact(
                            new BlockHitResult(base, Direction.UP, pistonCrystalCell.below(), false),
                            InteractionHand.MAIN_HAND, true))) {
                            pistonStageTick = tickCounter;
                        }
                    });
            }
            case 3 -> {
                FindItemResult pistonItem = InvHelper.find(Items.PISTON);
                if (!pistonItem.found()) pistonItem = InvHelper.find(Items.STICKY_PISTON);
                if (!pistonItem.found() || !mc.level.getBlockState(pistonBodyCell).isAir()) {
                    resetPistonAura();
                    return false;
                }
                // Vom Ziel WEG schauen, damit der Kolbenkopf zum Ziel zeigt (siehe Javadoc oben).
                float awayYaw = (float) (Rotations.getYaw(target.position()) + 180.0);
                final FindItemResult piston = pistonItem;
                final BlockPos bodyCell = pistonBodyCell;
                rotateAndRun(awayYaw, 0, PRIORITY_CRYSTAL, () -> {
                    if (placeTrackedBlock(bodyCell, piston, false, PRIORITY_CRYSTAL)) {
                        pistonPartsByUs.add(bodyCell);
                        pistonStage = 4;
                        pistonStageTick = tickCounter;
                    }
                });
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
                    pistonCooldown = delay(60);
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
        if (!hasActionableItem(Items.END_CRYSTAL) || !hasActionableItem(Items.REDSTONE_BLOCK)) return false;
        if (!hasActionableItem(Items.PISTON) && !hasActionableItem(Items.STICKY_PISTON)) return false;
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

    /** Beendet einen alten Kampf-/Escape-Pfad, bevor der direkte FollowProcess wieder uebernimmt. */
    private void clearHoleGoal() {
        var customGoal = BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess();
        if (customGoal.isActive()) customGoal.onLostControl();
        activeHole = null;
        heightCalcOrigin = null;
        holeEscapeActive = false;
        holeEscapeStartedTick = -1;
        holeEscapeLastPosition = null;
    }

    /** Sucht aus einer eingeschlossenen Zelle eine offene Nachbarzelle als Ausstieg.
     *  Die bisherige Hole-Suche bevorzugte genau das Gegenteil (maximal viele Waende); wenn der
     *  Gegner sein Loch verlassen hat, blieb der Bot dadurch in der alten Kampfposition stehen. */
    private BlockPos findEscapePosition(Player self, LivingEntity target) {
        if (mc.level == null || target == null) return null;
        BlockPos origin = self.blockPosition();
        int currentSides = countBoxedSides(self);
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;

        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = -1; dy <= 2; dy++) {
                    BlockPos candidate = origin.offset(dx, dy, dz);
                    if (!isStandable(candidate) || self.getBoundingBox().intersects(new AABB(candidate))) continue;
                    int sides = countBoxedSidesAt(candidate);
                    if (sides >= currentSides) continue;

                    double targetDistance = Math.sqrt(candidate.distSqr(target.blockPosition()));
                    // Offene Seiten zuerst, dann naeher am Gegner; leichtes Hoehengewicht verhindert,
                    // dass der Bot in ein weiter entferntes, aber ebenfalls offenes Feld springt.
                    double score = sides * 10.0 + targetDistance + Math.abs(dy) * 0.35;
                    if (score < bestScore) {
                        bestScore = score;
                        best = candidate;
                    }
                }
            }
        }
        return best;
    }

    /** Sucht und nutzt eine nahe Kampfposition, bevor der direkte FollowProcess laeuft.
     *  Bei einer eingeschlossenen Zelle wird zuerst ein Ausstieg gesucht; ein verlassenes
     *  gegnerisches Loch loest den alten Pfad sofort, damit der Bot wieder verfolgt. */
    private boolean updateHolePositioning(LivingEntity target, double dist) {
        if (buildCoverCooldown > 0) buildCoverCooldown--;

        BlockPos targetFeet = target.blockPosition();
        Player self = mc.player;

        // Ein Escape-Ziel hat Vorrang vor der normalen Hole-Suche. Wenn der Gegner sein Loch
        // verlassen hat, darf der Bot nicht weiter auf die alte Kampfzelle pathen.
        if (holeEscapeActive) {
            if (activeHole == null || targetFeet.distSqr(activeHole) > 16.0
                || self.blockPosition().distSqr(activeHole) <= 1) {
                clearHoleGoal();
                return false;
            }

            if (holeEscapeStartedTick < 0) holeEscapeStartedTick = tickCounter;
            if (holeEscapeLastPosition == null || self.position().distanceTo(holeEscapeLastPosition) > 0.12) {
                holeEscapeLastPosition = self.position();
            } else if (tickCounter - holeEscapeStartedTick > 30) {
                // Baritone kommt nicht aus der Zelle heraus: Pfad aufgeben, damit der direkte
                // FollowProcess bzw. eine ballistische Perle den Ausweg uebernehmen kann.
                clearHoleGoal();
                return false;
            }
            currentAction = "hole-escape";
            return true;
        }

        if (activeHole != null && targetFeet.distSqr(activeHole) > 9.0) {
            clearHoleGoal();
            return false;
        }

        if ((!holeAwareness.get() && !heightAdvantage.get()) || dist > 8.0) {
            clearHoleGoal();
            return false;
        }

        // Bot selbst in einer 1x1-/Eingeschlossenen-Zelle und Gegner mehr als drei Bloecke weg:
        // zuerst eine offene Nachbarzelle suchen, statt weiterhin eine zweite Deckungszelle zu waehlen.
        if (countBoxedSides(self) >= 3 && dist > 3.0) {
            BlockPos escape = findEscapePosition(self, target);
            if (escape != null) {
                activeHole = escape;
                holeEscapeActive = true;
                holeEscapeStartedTick = tickCounter;
                holeEscapeLastPosition = self.position();
                BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess()
                    .setGoalAndPath(new baritone.api.pathing.goals.GoalBlock(escape));
                currentAction = "hole-escape";
                return true;
            }
            clearHoleGoal();
            return false;
        }

        BlockPos best = findBestPosition(self, target);

        if (best == null) {
            clearHoleGoal();
            // Cooldown, statt jeden Tick neu zu versuchen: ohne ihn rief updateHolePositioning() das
            // hier JEDEN Tick auf, solange kein natuerliches Loch gefunden wurde - der Bot hat sich damit
            // Seite fuer Seite komplett selbst eingemauert.
            boolean outOfExplosives = totalItem(Items.END_CRYSTAL) <= 0
                && !(anchorsExplodeHere() && totalItem(Items.RESPAWN_ANCHOR) > 0 && totalItem(Items.GLOWSTONE) > 0)
                && !(useBeds.get() && bedsExplodeHere() && totalItem(GodmodePvP::isBed) > 0);
            if (buildCover.get() && dist <= 4.5 && buildCoverCooldown <= 0 && outOfExplosives) {
                buildOwnCover(self, target);
                buildCoverCooldown = 30;
                currentAction = "deckung-bauen";
            }
            return false;
        }

        if (self.blockPosition().distSqr(best) <= 1) {
            clearHoleGoal(); // angekommen - normale Verfolgung/Kampf uebernimmt wieder
            return false;
        }

        if (!best.equals(activeHole)) {
            activeHole = best;
            holeEscapeActive = false;
            holeEscapeStartedTick = -1;
            holeEscapeLastPosition = null;
            BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess()
                .setGoalAndPath(new baritone.api.pathing.goals.GoalBlock(best));
        }
        currentAction = "hole";
        return true;
    }

    /** UUID-Positionswechsel invalidieren nicht nur die Vorhersage, sondern auch jeden Baritone-Pfad.
     *  Sonst bleibt ein CustomGoal auf der alten Zelle aktiv, waehrend pursue-stationary-targets den
     *  FollowProcess bereits auf die neue teleportierte Position gesetzt hat.
     *
     *  <p><b>Bewusst ohne {@code reloadAllFromDisk()}:</b> Baritones Welt-Cache enthaelt Bloecke, keine
     *  Entities. Dass ein verfolgtes Ziel springt, aendert daran nichts - der Cache veraltet nur, wenn
     *  sich Bloecke aendern, und darauf reagiert das Modul bereits ueber
     *  {@code ClientboundBlockUpdatePacket}. Das Neuladen wurde im Arena-Test mehrfach pro Sekunde
     *  ausgeloest und hat den Client bei jedem Durchlauf die Welt neu von der Platte gelesen. */
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
    }

    private void updateTracking(LivingEntity target) {
        UUID id = target.getUUID();
        Vec3 cur = target.position();
        Vec3 prev = lastPositions.put(id, cur);

        if (prev != null) {
            double jump = cur.distanceTo(prev);
            if (jump > TELEPORT_JUMP_BLOCKS) {
                velocities.put(id, Vec3.ZERO);
                popBurstUntil = Math.max(popBurstUntil, tickCounter + 6);

                // Nur der erste Sprung in kurzer Folge reisst den Pfad ab. Die Vorhersage wird oben in
                // jedem Fall zurueckgesetzt - das ist der eigentliche Zweck dieses Zweigs.
                boolean suppressed = id.equals(lastPathInvalidationTarget)
                    && tickCounter - lastPathInvalidationTick < PATH_INVALIDATION_COOLDOWN_TICKS;
                if (!suppressed) {
                    lastPathInvalidationTarget = id;
                    lastPathInvalidationTick = tickCounter;
                    invalidateTargetPath();
                    ChatUtils.info("Pearl-Teleport erkannt (%.0f m) - verfolge neue Position.", jump);
                }
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
        if (drinkingFireRes || target == null) return;

        // D7/D9/D10: Zielverletzlichkeit, Angriffsstaerke und lethaler Sprung-Crit in einer Pruefung.
        AttackGate.Verdict verdict = gateAttack(target);
        if (!verdict.allowed()) return;

        // D3: die echte Nahkampfreichweite. Gegen einen Spear gilt 4.5 statt 3.0 — mit 3.6 als
        // attack-range_default haette das Modul einen Spear paeglich als "ausser Reichweite"
        // eingestuft und nie zugeschlagen.
        double reach = meleeReach();
        double dist = Math.sqrt(mc.player.distanceToSqr(target));
        if (dist > reach) return;
        if (!withinActionReach(target, ReachPolicy.Action.MELEE)
            && !(spearAware.get() && SpearModel.withinReach(dist) && !ReachPolicy.allows(ReachPolicy.Action.MELEE, dist))) {
            return;
        }
        if (!rayAllowsEntityAttack(target, ReachPolicy.Action.MELEE)) return;
        if (!tickGateAllows() || !cadenceAllows(ActionCadence.Action.ATTACK)) return;

        FindItemResult weapon = null;
        if (useMace.get() && maceSmashAvailable()) {
            weapon = InvUtils.findInHotbar(itemStack -> itemStack.getItem() instanceof MaceItem);
        }
        if ((weapon == null || !weapon.found()) && preferAxeMelee.get()) {
            weapon = InvUtils.findInHotbar(itemStack -> itemStack.getItem() instanceof AxeItem);
        }
        if (combatSlotReserved && (weapon == null || !weapon.found())) return;

        // D10: Das Gate hat die Todesgrenze bereits ausgewertet — verdict.jumpCrit() gilt direkt,
        // es wird hier NICHT neu berechnet. Ein Sprung-Crit, der das Ziel nicht toeten wuerde,
        // kostet nur den Angriffs-Cooldown: also gar nicht erst springen, sondern normal zuschlagen.
        if (critJump.get() && verdict.jumpCrit() && !prepareCritAndCheck(dist)) return;

        boolean wasSprinting = mc.player.isSprinting();
        Runnable hit = () -> dispatchAttack(target);
        if (weapon != null && weapon.found()) {
            if (!withCombatSlot(weapon, hit)) return;
        } else {
            hit.run();
        }

        if (sprintReset.get() && wasSprinting) sprintResetCooldown = 2;
    }

    /**
     * D11/D3: die Reichweite, gegen die der Nahkampf geprueft wird.
     *
     * <p>Der Default von {@code attack-range} ist 3.6. Das ist weder die Vanilla-Reichweite (3.0)
     * noch die eines Spears (4.5) — es ist ein historischer Kompromiss. Gegen einen Spear muss der
     * Default 4.5 erreichen, sonst faellt der Bot paeglich aus dem Fenster und schlaegt nie zu.
     */
    private double meleeReach() {
        double configured = attackRange.get();
        if (spearSeen && spearAware.get()) return Math.max(configured, SpearModel.requiredAttackRange());
        return configured;
    }

    /** D12: Der Mace-Smash-Bonus braucht >= 1.5 Bloecke Fallhoehe. Unter Slow Falling ist er
     *  vollstaendig unerreichbar — der Versuch waere eine umsonste Aktion. */
    private boolean maceSmashAvailable() {
        if (!slowFallingPlan.get()) return mc.player.fallDistance >= SlowFallingArrow.SMASH_FALL_BLOCKS;
        return SlowFallingArrow.evaluate(mc.player.fallDistance, slowFallingTicksRest).smashAvailable();
    }

    private void breakShield(Player target) {
        if (drinkingFireRes || target == null) return;
        // D3: Der Schildbrecher ist ein Nahkampf — 3.0, nicht die 4.5 der Blockplatzierung.
        if (!withinActionReach(target, ReachPolicy.Action.MELEE)
            || !rayAllowsEntityAttack(target, ReachPolicy.Action.MELEE)) return;
        if (!tickGateAllows() || !cadenceAllows(ActionCadence.Action.ATTACK)) return;
        FindItemResult axe = InvUtils.findInHotbar(itemStack -> itemStack.getItem() instanceof AxeItem);
        if (!axe.found()) return;
        // D15: der Ax-Stun dauert 5 s unabhaengig vom Cooldown-Anteil, deshalb wird die Staerke hier
        // bewusst NICHT gegatet — ein halb geladener Ax deaktiviert das Schild genauso.
        withCombatSlot(axe, () -> dispatchAttack(target));
    }

    /** Prueft die komplette berechnete Perlenbahn gegen die aktuelle Blockumgebung. */
    private boolean pearlTrajectoryClear(Vec3 origin, double yaw, double pitch, Vec3 extraVel, double ticks) {
        if (mc.level == null || mc.player == null) return false;
        int maxTicks = Math.max(4, Math.min(80, (int) Math.ceil(ticks) + 2));
        return PvpMath.trajectoryClear(origin, yaw, pitch, extraVel, (from, to) -> {
            if (from.distanceTo(to) < 1e-6) return true;
            ClipContext context = new ClipContext(from, to, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, mc.player);
            return mc.level.clip(context).getType() == HitResult.Type.MISS;
        }, maxTicks);
    }

    /** Erzeugt Zielpunkte aus Gegner-Vorhersage, Hitbox-Hoehen und tatsaechlich begehbaren Zellen
     *  in der Umgebung. Dadurch kann eine Perle nicht nur geometrisch, sondern auch entlang der
     *  realen Blockumgebung auf einen offenen Rand neben dem Gegner zielen. */
    private List<Vec3> pearlCandidatePoints(LivingEntity target, Vec3 from) {
        Vec3 centerOffset = target.getBoundingBox().getCenter().subtract(target.position());
        Vec3 current = target.getBoundingBox().getCenter();
        Vec3 predicted = predictOverTicks(target, leadTicks.get() + pingTicks()).add(centerOffset);
        List<Vec3> points = new ArrayList<>();
        points.add(predicted);
        points.add(current);

        for (Vec3 base : List.of(predicted, current)) {
            points.add(base.add(0, -0.65, 0));
            points.add(base.add(0, 0.65, 0));
            for (int i = 0; i < 8; i++) {
                double angle = i * Math.PI / 4.0;
                points.add(base.add(Math.cos(angle) * 0.7, 0, Math.sin(angle) * 0.7));
            }
        }

        BlockPos targetFeet = target.blockPosition();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = -1; dy <= 2; dy++) {
                    BlockPos cell = targetFeet.offset(dx, dy, dz);
                    if (!isStandable(cell)) continue;
                    Vec3 landing = Vec3.atBottomCenterOf(cell).add(0, 0.9, 0);
                    if (landing.distanceTo(from) <= 28.0) points.add(landing);
                }
            }
        }
        return points;
    }

    /** Loest Zielbewegung, eigene Bewegung und Blockumgebung gemeinsam auf. Ein Wurf wird nur
     *  zurueckgegeben, wenn die berechnete Bahn im aktuellen Level tatsaechlich frei ist. */
    private double[] solvePearlAtTarget(LivingEntity target) {
        if (target == null || mc.player == null || mc.level == null) return null;
        Vec3 from = mc.player.getEyePosition().subtract(0, 0.1, 0);
        Vec3 own = mc.player.getKnownMovement();
        Vec3 extraVel = new Vec3(own.x, mc.player.onGround() ? 0 : own.y, own.z);
        Vec3 targetCenter = target.getBoundingBox().getCenter();
        Vec3 predicted = predictOverTicks(target, leadTicks.get() + pingTicks())
            .add(target.getBoundingBox().getCenter().subtract(target.position()));

        double bestScore = Double.MAX_VALUE;
        double[] best = null;
        for (Vec3 candidate : pearlCandidatePoints(target, from)) {
            Vec3 aimPoint = candidate;
            for (int round = 0; round < 3; round++) {
                double[] aim = PvpMath.solvePearlAim(from, aimPoint, extraVel);
                if (aim == null) break;
                if (pearlTrajectoryClear(from, aim[0], aim[1], extraVel, aim[2])) {
                    double score = candidate.distanceTo(predicted);
                    if (score < bestScore) {
                        bestScore = score;
                        best = aim;
                    }
                    break;
                }
                // Nur echte Gegnerpunkte mit der Flugzeit nachfuehren; eine begehbare
                // Umgebungszelle bleibt als feste Ausweichzelle erhalten.
                if (candidate.distanceTo(targetCenter) < 1.5) {
                    aimPoint = predictOverTicks(target, (int) Math.round(aim[2]))
                        .add(target.getBoundingBox().getCenter().subtract(target.position()));
                } else {
                    break;
                }
            }
        }
        return best;
    }

    private boolean throwPearlAtTarget(LivingEntity target) {
        if (tickCounter - lastPearlScanTick < 8) return false;
        lastPearlScanTick = tickCounter;
        double[] aim = solvePearlAtTarget(target);
        return aim != null && throwPearlAt(aim[0], aim[1]);
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
            // Zielbewegung, eigene Bewegung und die tatsaechliche Blockumgebung werden gemeinsam
            // geloest; ein Kandidat wird nur gewaehlt, wenn seine Bahn im Level frei ist.
            double[] aim = solvePearlAtTarget(aimAt);
            if (aim == null) return; // ausserhalb der physischen Perlenreichweite - Perle sparen
            yaw = aim[0];
            pitch = aim[1];
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

    /**
     * Ziel-Pitch des Rettungswurfs, um {@link com.provipvp.exec.PitchVariance} gestreut.
     *
     * <p>Ohne diese Streuung ging jeder Rettungswurf reproduzierbar bei exakt 80 Grad raus. Das ist
     * das perfekte Anti-Cheat-Fingerzeig: Menschen treffen eine Senkrechte nicht dreimal hintereinander
     * auf zwei Nachkommastellen. {@code pearl-pitch-variance} auf 0 stellt das alte, exakte Verhalten
     * wieder her.
     */
    private double rescuePitch() {
        return com.provipvp.exec.PitchVariance.apply(80.0, pearlPitchVariance.get(), rng);
    }

    private boolean throwPearlAt(double yaw, double pitch) {
        if (drinkingFireRes) return false;
        FindItemResult pearl = InvHelper.find(Items.ENDER_PEARL);
        if (!pearl.found() || (!pearl.isOffhand() && !pearl.isHotbar())) return false;

        // C8: Perlen kollidieren mit End Crystals. Ein Rettungswurf, der durch die eigene
        // Crystal-Kette geht, sprengt sie — der Bot verliert also im schlimmsten Moment seine
        // eigene Schadensquelle. Geprueft wird die Strecke vom Auge bis zu dem Punkt, an dem die
        // Perle den eigenen Crystals begegnen koennte, also ueber die volle Wurfdistanz.
        if (protectOwnCrystals.get() && mc.player != null) {
            Vec3 from = mc.player.getEyePosition();
            Vec3 to = from.add(PlaceCursorSolver.lookVector(yaw, pitch).scale(PEARL_C8_CHECK_DISTANCE));
            if (throwHitsOwnCrystal(from, to)) return false;
        }

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

        // D18: Fuer den eigenen Pop zaehlt das Entity-Event 35, nicht der Health-Drop. Der
        // Health-Drop sieht denselben Pop als zwei Signale — einmal als HP-Sprung und einmal als
        // Offhand-Verschwinden — und wuerde den Pop-Zaehler verdoppeln. Der Event-Reader liefert
        // genau EINEN Pop pro Tick, unabhaengig davon, wie viele Signale anfallen.
        boolean popped = entity == mc.player && totemEventDetection.get()
            ? selfPopThisTick
            : drop >= popThreshold.get();
        if (popped && entity.isAlive()) {
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
        if (src < 0) return;

        int dst = hotbarTargetSlot(item);
        // Kein Platz und nichts, was man raeumen darf: aufgeben. Das ist der stille 95-Sekunden-
        // Blackout, nur eben fuer eine andere Ressource.
        if (dst < 0) {
            evictHotbarBallast();
            dst = hotbarTargetSlot(item);
            if (dst < 0) return;
        }

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
        if (src < 0) return;

        int dst = hotbarTargetSlot(pred);
        if (dst < 0) {
            evictHotbarBallast();
            dst = hotbarTargetSlot(pred);
            if (dst < 0) return;
        }

        InvUtils.move().from(src).to(dst);
    }

    /** Raeumt einen Hotbar-Slot frei, indem ein dort liegender Ballast-Stack ins Hauptinventar
     *  zurueckgeschoben wird. NOETIG, weil InvUtils.swap() ausschliesslich mit HOTBAR-Slots
     *  arbeitet: liegt eine Kampfressource nur im Hauptinventar, ist sie fuer Platzierung/Wurf
     *  faktisch nicht vorhanden. Live passiert - nach einem Nether-Abschnitt blockierten
     *  uebrig gebliebene Betten und leere Glasflaschen (Reste der Fire-Res-Traenke) die komplette
     *  Hotbar, waehrend Crystals/Obsidian/Anchor/Glowstone auf den Haupt-Slots 12-15 lagen: der Bot
     *  hatte volle Vorraete und platzierte trotzdem 95 Sekunden lang NULL Explosive (nur Nahkampf +
     *  Heiltraenke, live gemessen).
     *
     *  <p>Die alte Ballast-Liste kannte nur drei Faelle: leere Flaschen, Betten in der Oberwelt und
     *  Anker im Nether. Damit blieb die haeufigste Blockade unerreicht - die Hotbar fuellt sich im
     *  Laufe eines Kampfes mit Kies, Erde, Pfeilen, Faeulnisfleisch und Baubloecken, und <b>keines</b>
     *  davon war als Ballast erkennbar. evictHotbarBallast() lieferte dann false,
     *  hotbarTargetSlot() -1, und saemtliche refill() liefen ins Leere - bei vollen Vorraeten im
     *  Hauptinventar. Das trifft jede Ressource, nicht nur Anker und Glowstone.
     *
     *  <p>Geschuetzt wird alles, womit der Bot kaeuft, kaeuft oder sich verteidigt: die verwalteten
     *  Ressourcen, Totem, Schild, Nahrung, Werkzeug, der Offhand-Inhalt und der gerade belegte
     *  Combat-Slot. Geraeumt wird nur, was davon nichts ist.
     *
     *  @return true, wenn ein Slot freigeraeumt wurde. */
    private boolean evictHotbarBallast() {
        for (int i = 0; i <= 8; i++) {
            ItemStack s = mc.player.getInventory().getItem(i);
            if (s.isEmpty()) return false; // schon Platz - nichts zu raeumen
            if (!isHotbarBallast(i, s)) continue;

            int dst = findFreeMainSlot();
            if (dst < 0) dst = findMainMergeSlot(s);
            if (dst < 0) return false;
            InvUtils.move().from(i).to(dst);
            return true;
        }
        return false;
    }

    /**
     * Darf dieser Hotbar-Slot fuer eine Kampfressource geopfert werden?
     *
     *  <p>Die Regel ist bewusst ein Schutz-Verzeichnis statt einer Ballast-Liste: alles, was der Bot
     *  braucht, wird <b>aufgezählt</b> und geschuetzt, alles andere ist raeumbar. Eine Ballast-Liste
     *  muss dagegen jede unnoetige Sache kennen - Kies, Erde, Pfeile, Fäulnisfleisch, Netherrack,
     *  gebrauchte Truenke, und was der Server dem Bot in die Hand gibt, wovon heute niemand weiss.
     *  Genau daran ist die alte Liste gescheitert.
     *
     *  <p>Geschuetzt werden die verwalteten Ressourcen, Totem, Schild, Nahrung, Werkzeug, der
     *  Offhand-Inhalt sowie der gerade belegte Combat-Slot. Der Combat-Slot ist der heikelste Fall:
     *  waere er Ballast, wuerde ein refill() ihn mitten im Kampf ungefragt verschieben und den
     *  Swap-Merkposten des Moduls verwaeisen.
     *
     *  @param slot Index 0-8, damit der belegte Combat-Slot mitgeprueft werden kann
     */
    private boolean isHotbarBallast(int slot, ItemStack s) {
        if (s.isEmpty()) return false;

        // Der belegte Combat-Slot gehoert dem gerade laufenden Angriff, nicht der Haushaltsfuehrung.
        if (slot == combatSlotTargetSlot) return false;

        // Werkzeug ueber die Tags, die es da gibt (SWORDS/AXES/PICKAXES/SHOVELS/HOES/SPEARS).
        // ItemTags kennt in 26.2 KEIN BOWS/CROSSBOWS/MACES/TRIDENTS - dafuer die Item-Klassen,
        // die das Modul an anderer Stelle (crystalToolAt, Waffenwahl) ohnehin benutzt.
        if (s.is(ItemTags.SWORDS) || s.is(ItemTags.AXES) || s.is(ItemTags.PICKAXES)
            || s.is(ItemTags.SHOVELS) || s.is(ItemTags.HOES) || s.is(ItemTags.SPEARS)) return false;
        if (s.getItem() instanceof BowItem || s.getItem() instanceof CrossbowItem
            || s.getItem() instanceof TridentItem || s.getItem() instanceof MaceItem) return false;

        // Nahrung: die Komponente ist der verlaessliche Test. FoodData hat keine getFoodItem()-
        // Methode mehr, und die Minecraft-eigene "bevorzugte Nahrung" ist servergesteuert.
        if (s.has(DataComponents.FOOD)) return false;

        // Verwaltete Ressourcen - die, fuer die refill() ueberhaupt zustaendig ist.
        if (s.is(Items.END_CRYSTAL) || s.is(Items.ENDER_PEARL) || s.is(Items.OBSIDIAN)
            || s.is(Items.COBWEB) || s.is(Items.PISTON) || s.is(Items.STICKY_PISTON)
            || s.is(Items.REDSTONE_BLOCK)) return false;
        if (isHealingSplash(s) || isFireResPotion(s) || s.is(Items.WATER_BUCKET)
            || s.is(Items.LAVA_BUCKET) || s.is(Items.ENDER_EYE)) return false;

        // Nur was in DIESER Dimension auch explodiert, wird geschuetzt. Sonst schiebt refill() genau
        // den Stack zurueck, den evictHotbarBallast() eine Zeile vorher herausgeraeumt hat - endloses
        // Hin-und-Her alle 20 Ticks, das dauerhaft einen Hotbar-Slot der echten Waffen belegt.
        if (isBed(s) && !bedsExplodeHere()) return true;
        if (s.is(Items.RESPAWN_ANCHOR) && !anchorsExplodeHere()) return true;
        if (s.is(Items.GLOWSTONE) && !anchorsExplodeHere()) return false;
        if (isBed(s) || s.is(Items.RESPAWN_ANCHOR) || s.is(Items.GLOWSTONE)) return false;

        return true;
    }

    /**
     * Feuerresistenz-Traenke, trinkbar oder als Splash. {@code Items.FIRE_RESISTANCE_POTION} gibt es
     * seit 1.20.4 nicht mehr - Traenke sind ein Item mit Wirkungs-Komponente, der Wirkungsname
     * entscheidet. Dieselbe Pruefung steht bereits an zwei weiteren Stellen (Trinken im Nether,
     * Bestandsaufnahme); hier ist sie die dritte Nutzung derselben Wahrheit.
     */
    private static boolean isFireResPotion(ItemStack stack) {
        if (!stack.is(Items.POTION) && !stack.is(Items.SPLASH_POTION)) return false;
        PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
        return contents != null
            && (contents.is(Potions.FIRE_RESISTANCE) || contents.is(Potions.LONG_FIRE_RESISTANCE));
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
            if (s != null) {
                java.util.Set<EntityType<?>> original = s.get();
                if (original != null && !savedEntityFilters.containsKey(mod)) {
                    savedEntityFilters.put(mod, new HashSet<>(original));
                }
                java.util.Set<EntityType<?>> filter = new HashSet<>();
                filter.add(EntityTypes.PLAYER);
                if (attackMobs.get()) filter.addAll(mobTypes.get());
                s.set(filter);
            }
        } catch (NoSuchFieldException ignored) {
        } catch (Throwable ignored) {
        }
    }

    @SuppressWarnings("unchecked")
    private void restoreEntityFilters() {
        for (var entry : savedEntityFilters.entrySet()) {
            try {
                java.lang.reflect.Field f = entry.getKey().getClass().getDeclaredField("entities");
                f.setAccessible(true);
                Setting<java.util.Set<EntityType<?>>> setting =
                    (Setting<java.util.Set<EntityType<?>>>) f.get(entry.getKey());
                if (setting != null) setting.set(new HashSet<>(entry.getValue()));
            } catch (Throwable ignored) {
            }
        }
        savedEntityFilters.clear();
    }


    private void syncSupport(CrystalAura ca) {
        try {
            java.lang.reflect.Field f = CrystalAura.class.getDeclaredField("support");
            f.setAccessible(true);
            @SuppressWarnings("unchecked")
            Setting<CrystalAura.SupportMode> s = (Setting<CrystalAura.SupportMode>) f.get(ca);
            if (s != null) {
                if (savedSupport == null) savedSupport = s.get();
                if (s.get() == CrystalAura.SupportMode.Disabled) s.set(CrystalAura.SupportMode.Fast);
                supportSyncFailed = false;
            } else {
                // Feld existiert, aber die Meteor-Version liefert keinen Setting-Wert zurueck - genauso
                // unzuverlaessig wie eine geworfene Exception, also denselben Fallback-Pfad nehmen.
                supportSyncFailed = true;
            }
        } catch (Throwable t) {
            // savedSupport bewusst NICHT nullen: es ist der zuletzt GELESENE Wert von CrystalAura und
            // damit der einzige Weg zurueck zum Original. Wird hier verwischt, sichert der naechste
            // erfolgreiche Aufruf den selbst geschriebenen Wert als "Original" - der Nutzerwert
            // waere dann endgueltig verloren. Ist noch nie gesichert worden, ist es ohnehin null.
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
                if (savedSupportDelay < 0) savedSupportDelay = s.get();
                if (s.get() < minSupportDelay.get()) s.set(minSupportDelay.get());
            }
        } catch (Throwable t) {
            // dito: siehe oben, savedSupportDelay nicht verwischen.
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

    private boolean safeEnable(Modules m, Class<? extends Module> clazz) {
        Module mod = m.get(clazz);
        if (mod == null || mod.isActive()) return false;
        mod.toggle();
        return mod.isActive();
    }

    private void safeDisable(Modules m, Class<? extends Module> clazz) {
        Module mod = m.get(clazz);
        if (mod != null && mod.isActive()) mod.toggle();
    }

    private void setCrystalAuraActive(Module ca, boolean active) {
        if (ca == null) return;
        if (active) {
            if (!ca.isActive()) {
                ca.toggle();
                crystalAuraOwned = ca.isActive();
            }
        } else if (crystalAuraOwned && ca.isActive()) {
            ca.toggle();
            crystalAuraOwned = false;
        }
    }

    private void stopOwnedCrystalAura() {
        setCrystalAuraActive(Modules.get().get(CrystalAura.class), false);
    }
}
