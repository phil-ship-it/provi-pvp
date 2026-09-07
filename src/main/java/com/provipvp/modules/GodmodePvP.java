package com.provipvp.modules;

import baritone.api.BaritoneAPI;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
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
        .description("Verfolgt das Ziel automatisch mit Baritone, sobald es in Engage-Distanz ist.")
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

    public final Setting<Boolean> preHit = sgCombat.add(new BoolSetting.Builder()
        .name("pre-hit")
        .description("Schlaegt den Gegner vor der Explosion fuer mehr Schaden.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> meleeFallback = sgCombat.add(new BoolSetting.Builder()
        .name("melee-fallback")
        .description("Schlaegt normal im Nahkampf, wenn gerade keine Explosion bevorsteht (z.B. kein Obsidian mehr fuer Crystal-Unterbau) - sonst steht der Bot nur da, sobald Crystal/Anchor tatsaechlich nichts mehr zustande bringen.")
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
        .description("CrystalAura-Delay auf 0 (Sofort-Reaktion).")
        .defaultValue(true)
        .build()
    );

    public final Setting<Integer> minSupportDelay = sgCombat.add(new IntSetting.Builder()
        .name("min-support-delay")
        .description("Mindest-Tickabstand zwischen Obsidian-Unterbau und dem folgenden Crystal-Platzieren (CrystalAuras 'support-delay'). Beide Aktionen nutzen Minecrafts eigenes sequenznummer-basiertes Block-Vorhersage-System (seit 1.19) - schickt man beide zu dicht hintereinander raus, bevor die erste Sequenz vom Server bestaetigt ist, kann die Vorhersage durcheinanderkommen ('Crystal-Hitbox erscheint, aber kein Crystal kommt'). Auf Servern mit spuerbarer Latenz oder Versions-Uebersetzung (z.B. ViaVersion) braucht es mehr Puffer als den Meteor-Standard. Wird nur angehoben, nie gesenkt - bei anhaltenden Fehlplatzierungen hochdrehen.")
        .defaultValue(4)
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

    // Defense
    public final Setting<Boolean> fastTotem = sgDefense.add(new BoolSetting.Builder()
        .name("fast-totem")
        .description("Sofort-Totem-Manager: prueft die Offhand jeden Tick.")
        .defaultValue(true)
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

    public final Setting<Boolean> peekTactic = sgDefense.add(new BoolSetting.Builder()
        .name("peek-tactic")
        .description("Duckt sich in Deckung, wenn gerade nichts aktiv passiert - steht nur kurz zum Angriff auf.")
        .defaultValue(true)
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
    private int lastErrorWarnTick = -999;
    private boolean warnedLowTotems;
    private boolean warnedOutOfCrystals;
    private boolean warnedOutOfAnchorSupply;
    private final Map<net.minecraft.world.item.Item, Boolean> warnedOutOfMisc = new HashMap<>();
    private int savedPlaceDelay = -1;
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
    private int lastCrystalCount = -1;
    private boolean followActive;
    private boolean engaged; // sticky: einmal in Engage-Distanz gekommen, bleibt es auch nach Explosions-Knockback ueber diese Distanz hinaus (bis follow-range/Zielverlust) - sonst reisst eine Crystal-Explosion die Verfolgung mitten im Kampf ab.
    private UUID engagedTargetId;
    private UUID followedId;
    private Vec3 lastSelfPos;
    private int obstacleStuckTicks;
    private float lastTargetHpForStuck = -1;
    private int watchdogStuckTicks;
    private int lastAnchorProgressTick;
    private int rubberbandCooldown;
    private boolean strafeLeft = true;
    private int nextStrafeSwitchTick = -1;
    private float lastSelfHpForRubberband = -1;
    private BlockPos activeHole;
    private BlockPos heightCalcOrigin;
    private int buildCoverCooldown;
    // Meteors Rotations-Queue fuehrt bei MEHREREN im selben Tick angemeldeten Rotationen nur die ERSTE mit
    // der tatsaechlich gesetzten Blickrichtung aus - jede weitere bekommt beim Ausfuehren ihres Callbacks
    // schon wieder die alte, zurueckgesetzte Rotation (siehe Rotations.onSendMovementPacketsPost: nur
    // Index 0 durchlaeuft setClientRotation() VOR seinem Callback, alle weiteren senden zwar ihr Paket,
    // aber mit der Spielerrotation von VOR der Aktion). Ohne diese Sperre feuerte z.B. ein Perlwurf
    // gleichzeitig mit einer Anchor-/Bett-Interaktion im selben Tick - eine der beiden landete dann mit
    // komplett falscher Blickrichtung (wild verirrte Perlen, "haengende" nie gezuendete Anchors/Betten).
    private boolean rotationQueuedThisTick;
    private int lastFireworkTick = -999;
    private int secondEnemyWarnCooldown;
    private boolean lowOnTotems;

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
    private int bedPlaceFails;
    private int bedUnreachableTicks;
    private BlockPos bedCalcOrigin;
    private int lastBedProgressTick;

    // Heiltraenke: Schaden-Delta pro Tick verfolgen, um frischen Treffern sofort einen Splash-Heiltrank
    // entgegenzusetzen.
    private float hpAtHealWindowStart = -999;
    private int healWindowStartTick = -999;
    private int healPotionCooldown;

    private record BedSpot(BlockPos pos, Direction dir) {}

    // Eigener D-Tap-Executor (Knockback -> Obsidian in Flugbahn -> 2 Crystals im Immunitaets-Abstand)
    private BlockPos dtapSpot;
    private int dtapStage; // 0 idle, 1 1.Crystal platzieren, 2 1.Crystal zuenden, 3 Immunitaet abwarten, 4 2.Crystal platzieren+zuenden
    private int dtapStageTick;
    private int dtapCooldown;

    public GodmodePvP() {
        super(Categories.Combat, "godmode-pvp", "ProviPvP v4: Kampf-KI mit eigenem Blitz-Anchor (1 Glowstone), Verfolgung ohne Limit. Befehl: .pvp");
    }

    @Override
    public void onActivate() {
        tickCounter = 0;
        auraMode = -1;
        savedPlaceDelay = -1;
        popBurstUntil = 0;
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
        drinkingFireRes = false;
        fireResStartTick = -999;
        dtapStage = 0;
        dtapCooldown = 0;
        dtapSpot = null;
        lastAuraSwitch = -999;
        lastHealth.clear();
        pops.clear();
        lastPositions.clear();
        velocities.clear();
        blocking = false;
        blockingSwapBack = false;
        shieldUntil = 0;
        lastCrystalCount = -1;
        followActive = false;
        followedId = null;
        engaged = false;
        engagedTargetId = null;
        lastSelfPos = null;
        obstacleStuckTicks = 0;
        lastTargetHpForStuck = -1;
        watchdogStuckTicks = 0;
        lastAnchorProgressTick = 0;
        rubberbandCooldown = 0;
        lastSelfHpForRubberband = -1;
        activeHole = null;
        heightCalcOrigin = null;
        buildCoverCooldown = 0;
        lastFireworkTick = -999;
        secondEnemyWarnCooldown = 0;
        lowOnTotems = false;
        lastSelfPopTick = -999;
        lastTargetDamageTick = -999;
        hadTotemEffects.clear();
        sprintResetCooldown = 0;

        Modules m = Modules.get();

        CrystalAura ca = m.get(CrystalAura.class);
        if (ca != null) {
            if (zeroDelay.get() || instantMode.get()) {
                savedPlaceDelay = ca.placeDelay.get();
                ca.placeDelay.set(0);
            }
            syncSupport(ca);
        }

        // Baritone: aggressive Verfolgung - Klippen runter, Luecken/Gaps ueberspringen, notfalls mit Bruecken-Sprung
        var bs = BaritoneAPI.getSettings();
        bs.allowDownward.value = true;
        bs.allowParkour.value = true;
        bs.allowParkourAscend.value = true;
        bs.allowParkourPlace.value = true;
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

        if (autoMendOn.get()) {
            safeEnable(m, AutoMend.class);
            tuneAutoMend();
        }
        if (autoEatOn.get()) safeEnable(m, AutoEat.class);
        if (noFallOn.get()) safeEnable(m, NoFall.class);
        if (killAuraOn.get()) safeEnable(m, KillAura.class);
        syncMobFilter();

        MeteorClient.EVENT_BUS.subscribe(this);

        info("ProviPvP v4 aktiv. Rechtsklick auf das Modul im Meteor-Menue zum Keybind. Befehl: .pvp");
    }

    @Override
    public void onDeactivate() {
        MeteorClient.EVENT_BUS.unsubscribe(this);

        Modules m = Modules.get();
        safeDisable(m, CrystalAura.class);
        safeDisable(m, AutoMend.class);
        safeDisable(m, AutoEat.class);
        safeDisable(m, NoFall.class);
        safeDisable(m, KillAura.class);

        CrystalAura ca = m.get(CrystalAura.class);
        if (ca != null) {
            if (savedPlaceDelay >= 0) ca.placeDelay.set(savedPlaceDelay);
            restoreSupport(ca);
        }
        savedPlaceDelay = -1;
        dtapStage = 0;
        cancelFollow();

        if (blocking) {
            if (blockingSwapBack) InvUtils.swapBack();
            blocking = false;
            blockingSwapBack = false;
        }

        Input.setKeyState(mc.options.keyLeft, false);
        Input.setKeyState(mc.options.keyRight, false);
        mc.player.setShiftKeyDown(false);
        Input.setKeyState(mc.options.keyJump, false);
        Input.setKeyState(mc.options.keySprint, false);
        mc.player.setSprinting(false);

        lastHealth.clear();
        pops.clear();
        warnedLowTotems = false;
        warnedOutOfCrystals = false;
        warnedOutOfAnchorSupply = false;
        warnedOutOfMisc.clear();

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
        rotationQueuedThisTick = false;

        boolean guiOpen = mc.gui.screen() != null;
        // Nur eine ECHTE Fremd-Container-GUI (Kiste, Ambos, Shulker, ...) hat ein anderes containerMenu
        // als das Standard-Spieler-Inventar - Slot-Indizes waeren dann falsch gemappt und koennten
        // Items in der falschen GUI verschieben. Das Meteor-ClickGUI und das eigene Inventar (E) teilen
        // sich weiterhin das normale inventoryMenu, also darf Totem-Nachlegen dabei NICHT pausieren.
        boolean foreignContainerOpen = mc.player.containerMenu != mc.player.inventoryMenu;
        if (fastTotem.get() && !foreignContainerOpen) ensureOffhandTotem();
        if (!foreignContainerOpen) maintainHealPotions(self);
        maintainFireResistance();
        if (!guiOpen) {
            if (invManager.get() && tickCounter % 20 == 0) inventoryTick(self);
        }

        if (blocking) {
            if (tickCounter < shieldUntil) {
                mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                currentAction = "schild-block";
                return;
            }
            stopBlock();
        }

        LivingEntity target = findTarget(self);

        // Ziel weg -> Baritone stoppen
        if (target == null) {
            engaged = false;
            dtapStage = 0;
            cancelFollow();
            activeHole = null;
            heightCalcOrigin = null;
            Input.setKeyState(mc.options.keyLeft, false);
            Input.setKeyState(mc.options.keyRight, false);
            mc.player.setShiftKeyDown(false);
            Input.setKeyState(mc.options.keyJump, false);
            currentAction = "beobachten";
            return;
        }

        updateTracking(target);
        checkSecondEnemy(self, target);
        updateElytraFlight();

        double dist = Math.sqrt(self.distanceToSqr(target));
        boolean flying = mc.player.isFallFlying();
        if (!target.getUUID().equals(engagedTargetId)) {
            engagedTargetId = target.getUUID();
            engaged = false; // neues Ziel -> Kaltstart-Schwelle (engage-distance) gilt wieder von vorn
        }
        if (dist <= engageDistance.get()) engaged = true;

        // Kontinuierliches Ziel-Tracking: der Bot schaut das Ziel (vorhergesagte Position) die meiste
        // Zeit direkt an, nicht nur kurz waehrend einer einzelnen Anzielen-Aktion. Ausserhalb der
        // Nahkampf-Strafe-Distanz (die ihre eigene Rotation setzt) und nicht waehrend Elytra-Flug.
        if (trackTarget.get() && !flying && dist > 3.6) {
            Vec3 lookAt = predict(target);
            if (freeLook.get()) {
                Rotations.rotate(Rotations.getYaw(lookAt), Rotations.getPitch(lookAt));
            } else {
                mc.player.setYRot((float) Rotations.getYaw(lookAt));
                mc.player.setXRot((float) Rotations.getPitch(lookAt));
            }
        }

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
        boolean tookRealDamage = lastSelfHpForRubberband >= 0 && self.getHealth() < lastSelfHpForRubberband - 1.0f;
        lastSelfHpForRubberband = self.getHealth();
        if (rubberbandCooldown > 0) {
            rubberbandCooldown--;
        } else if (antiRubberband.get() && tickCounter - lastPearlTick > delay(10)
            && (selfMoved > 6.0 || (selfMoved > 2.5 && !tookRealDamage))) {
            BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().cancelEverything();
            followActive = false;
            rubberbandCooldown = delay(10);
            currentAction = "rubberband";
        }

        // Durch Knockback (Schlag/Explosion) in die Luft geschleudert ODER generell gerade in einem
        // gefaehrlichen Fall (z.B. von einer Kante gelaufen, durch fremden Knockback/Explosion, die wir
        // nicht selbst als "gerade getroffen" erkennen): sofort senkrecht nach unten perlen, um kontrolliert
        // runterzukommen statt hilflos zu fallen, Fallschaden zu nehmen oder als leichtes Ziel in der Luft
        // zu haengen. fallDistance > 3 ist die gleiche Schwelle, ab der Minecraft selbst Fallschaden zaehlt.
        boolean launchedByHit = tookRealDamage && self.getDeltaMovement().y > 0.35;
        boolean fallingDanger = !self.onGround() && self.fallDistance > 3.0f && self.getDeltaMovement().y < 0.05;
        if (knockbackPearl.get() && (launchedByHit || fallingDanger)
            && tickCounter - lastPearlTick > delay(15)
            && (InvUtils.findInHotbar(Items.ENDER_PEARL).found() || InvUtils.find(Items.ENDER_PEARL).found())) {
            throwPearlDown();
            currentAction = launchedByHit ? "pearl-knockback" : "pearl-fallschutz";
            return;
        }

        boolean blockedByObstacle = dist <= 6.0 && !self.hasLineOfSight(target);
        obstacleStuckTicks = blockedByObstacle ? obstacleStuckTicks + 1 : 0;

        boolean targetHpChanged = lastTargetHpForStuck < 0 || Math.abs(target.getHealth() - lastTargetHpForStuck) > 0.01f;
        lastTargetHpForStuck = target.getHealth();
        watchdogStuckTicks = (selfMoved < 0.05 && !targetHpChanged) ? watchdogStuckTicks + 1 : 0;

        // Kein Sichtkontakt trotz Naehe (Hindernis im Weg): so gut wie sofort reagieren,
        // klappt das nicht, zum Gegner perlen statt festzustehen.
        if (obstacleStuckTicks > 1 && pearlThrow.get() && tickCounter - lastPearlTick > delay(20)
            && (InvUtils.findInHotbar(Items.ENDER_PEARL).found() || InvUtils.find(Items.ENDER_PEARL).found())) {
            throwPearl(target, false);
            currentAction = "pearl-obstacle";
            obstacleStuckTicks = 0;
        }

        // Generischer Watchdog: 5s weder Eigenbewegung noch Schaden am Ziel -> kompletter Reset,
        // damit der Bot nicht haengen bleibt bis man selbst zuschlaegt.
        if (watchdogStuckTicks > 100) {
            cancelFollow();
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
            && (InvUtils.findInHotbar(Items.ENDER_PEARL).found() || InvUtils.find(Items.ENDER_PEARL).found())) {
            throwPearl(target, true);
            currentAction = "escape-cage";
            return;
        }

        // Notfall-Flucht: kritisches HP -> Perle weg statt sinnlos weiterzukaempfen, egal wie weit der Gegner ist
        if (escapePearl.get() && self.getHealth() <= 6.0f
            && tickCounter - lastPearlTick > delay(20)
            && (InvUtils.findInHotbar(Items.ENDER_PEARL).found() || InvUtils.find(Items.ENDER_PEARL).found())) {
            throwPearl(target, true);
            currentAction = "escape-pearl";
            return;
        }

        // Rueckzugs-Schwelle: Totems knapp UND keine Explosiv-Ressourcen mehr -> Gefecht abbrechen statt
        // aussichtslos im reinen Nahkampf weiterzumachen.
        if (retreatThreshold.get() && lowOnTotems && warnedOutOfCrystals && warnedOutOfAnchorSupply) {
            cancelFollow();
            if (dist <= 10.0 && tickCounter - lastPearlTick > delay(30)
                && (InvUtils.findInHotbar(Items.ENDER_PEARL).found() || InvUtils.find(Items.ENDER_PEARL).found())) {
                throwPearl(target, true);
            }
            currentAction = "rueckzug";
            return;
        }

        // Verlorener Trade: wir selbst wurden gerade hart getroffen (typischerweise Crystal/Anchor-Pop),
        // aber unsere eigenen Explosionen richten seit einer Weile keinen Schaden beim Gegner an
        // (Platzierung blockiert/unerreichbar, Schild, o.ae.) - abhauen statt einen Verlust-Trade fortzusetzen.
        if (retreatOnLosingTrade.get() && tickCounter - lastSelfPopTick < 60
            && tickCounter - lastTargetDamageTick > 60 && tickCounter - lastPearlTick > delay(30)
            && (InvUtils.findInHotbar(Items.ENDER_PEARL).found() || InvUtils.find(Items.ENDER_PEARL).found())) {
            throwPearl(target, true);
            currentAction = "pearl-verlorener-trade";
            lastSelfPopTick = -999;
            return;
        }

        handleTrap(target);
        if (meleeStrafe.get() && !flying) updateCombatMovement(target, dist);
        manageSprintForKnockback(dist);

        if (killAuraOn.get()) {
            Module ka = Modules.get().get(KillAura.class);
            if (ka != null && !ka.isActive()) ka.toggle();
        }

        // Feindlicher Crystal frisch platziert (in 5 m)? -> kurzes Schild-Block-Fenster
        java.util.List<EndCrystal> nearCrystals = mc.level.getEntitiesOfClass(EndCrystal.class, self.getBoundingBox().inflate(5));
        if (lastCrystalCount >= 0 && nearCrystals.size() > lastCrystalCount && !nearCrystals.isEmpty()
            && autoShield.get() && !blocking) {
            shieldUntil = tickCounter + 15; // Crystal zuendet praktisch sofort - kurzes, hartes Block-Fenster
            startBlock();
        }
        lastCrystalCount = nearCrystals.size();

        // Pop-Fenster: volle Aggression
        if (tickCounter < popBurstUntil) {
            if (dist <= attackRange.get() && self.getAttackStrengthScale(0.5f) >= 0.9f && self.hasLineOfSight(target) && prepareCritAndCheck(dist)) attackMelee(target);
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
        long pearlCooldown = delay(dist > 15 ? 8 : 10);
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
        if (!engaged) {
            cancelFollow();
            activeHole = null;
            heightCalcOrigin = null;
            currentAction = "beobachten-fern";
        } else if (flying) {
            updateFollow(target);
        } else if (ignoreFire.get() && fireBlocksPath(self, target)) {
            walkThroughFire(target);
            currentAction = "feuer-durchqueren";
        } else if (!updateHolePositioning(target, dist)) {
            updateFollow(target);
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

        if (dtapStage != 0) {
            runDtapTick(target);
            currentAction = "d-tap";
        } else {
            if (smartAuras.get()) selectAura(target);

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
            && self.hasLineOfSight(target) && explosionImminent(target) && prepareCritAndCheck(dist)) {
            attackMelee(target);
            currentAction = "pre-hit";
        } else if (meleeFallback.get() && dist <= attackRange.get() && self.getAttackStrengthScale(0.5f) >= 0.9f
            && self.hasLineOfSight(target) && !explosionImminent(target)) {
            attackMelee(target);
            currentAction = "nahkampf-fallback";
        }

        if (currentAction.equals("-")) {
            currentAction = auraMode == 0 ? "crystal" : "zielen";
        }
        updatePeekStance();

        trackPop(target);
        trackPop(self);
        trackTotemEffect(target);
    }

    @Override
    public String getInfoString() {
        return currentAction;
    }

    /** Schild in die Haupthand (Offhand bleibt frei fuer den Totem) und blocken - reduziert Explosionsschaden. */
    private void startBlock() {
        FindItemResult shield = InvUtils.findInHotbar(Items.SHIELD);
        if (!shield.found()) shield = InvUtils.find(Items.SHIELD);
        if (!shield.found()) return;

        blockingSwapBack = InvUtils.swap(shield.slot(), true);
        mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
        blocking = true;
    }

    private void stopBlock() {
        if (blockingSwapBack) InvUtils.swapBack();
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

        FindItemResult anchor = InvUtils.findInHotbar(Items.RESPAWN_ANCHOR);
        if (!anchor.found()) anchor = InvUtils.find(Items.RESPAWN_ANCHOR);
        if (!anchor.found()) return;

        if (BlockUtils.place(spot, anchor, true, 50)) {
            anchorPlaceFails = 0;
            anchorPlaceCooldown = delay(6); // kurze Pause, damit maintainNearbyAnchors Zeit zum Laden hat
            lastAnchorProgressTick = tickCounter;
        } else {
            anchorCandidateIndex++;
        }
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
        if (!useAnchors.get() || anchorMode.get() == 2) return;
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
                    if (selfDmg > maxSelfDamage.get()) continue;

                    int charges = st.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.RESPAWN_ANCHOR_CHARGES);
                    if (charges > 0) {
                        FindItemResult fir = InvUtils.findInHotbar(itemStack -> !itemStack.isEmpty() && !itemStack.is(Items.GLOWSTONE));
                        if (!fir.found()) fir = InvUtils.find(itemStack -> !itemStack.isEmpty() && !itemStack.is(Items.GLOWSTONE));
                        if (!fir.found()) continue;
                        if (interactAnchorAt(pos, fir)) anchorMaintCooldown = delay(3);
                        return; // Rotations-Slot ist so oder so belegt (versucht oder schon anderweitig vergeben) - naechster Tick
                    } else {
                        FindItemResult gs = InvUtils.findInHotbar(Items.GLOWSTONE);
                        if (!gs.found()) gs = InvUtils.find(Items.GLOWSTONE);
                        if (!gs.found()) {
                            outOfGlowstone = true;
                            continue;
                        }
                        if (interactAnchorAt(pos, gs)) anchorMaintCooldown = delay(3);
                        return;
                    }
                }
            }
        }
        anchorMaintCooldown = delay(1); // nichts gefunden - naechster voller Scan erst naechsten Tick statt jeden Tick doppelt
    }

    /** @return true, wenn die Rotation+Interaktion tatsaechlich eingereiht wurde (Rotations-Slot frei war). */
    private boolean interactAnchorAt(BlockPos pos, FindItemResult item) {
        Vec3 center = Vec3.atCenterOf(pos);
        return rotateAndRun(Rotations.getYaw(center), Rotations.getPitch(center), () -> {
            boolean swapped = InvUtils.swap(item.slot(), true);
            BlockUtils.interact(new BlockHitResult(center, BlockUtils.getDirection(pos), pos, true), InteractionHand.MAIN_HAND, true);
            if (swapped) InvUtils.swapBack();
        });
    }
    // ---------- Aura-Steuerung ----------

    private void selectAura(LivingEntity target) {
        // Ohne ein einziges Crystal UND ohne vollstaendige Anchor-Ausruestung (Anchor + Glowstone) UND ohne
        // Bett (falls aktiviert) gibt es schlicht nichts zu platzieren - die teure Damage-/Positions-
        // Simulation unten (Anchor-/Bett-Kandidaten-Scan, Crystal-Schadens-Suche) UND das wiederholte
        // An-/Ausschalten von Meteors CrystalAura liefen bisher trotzdem jeden Tick weiter, obwohl nie etwas
        // dabei rauskam - genau das erzeugte spuerbares Ruckeln/Stottern im Movement, waehrend "Platzierung"
        // nach aussen einfach nichts tat.
        boolean hasCrystals = totalItem(Items.END_CRYSTAL) > 0;
        boolean hasAnchorItem = totalItem(Items.RESPAWN_ANCHOR) > 0 && totalItem(Items.GLOWSTONE) > 0;
        boolean hasBedItem = useBeds.get() && totalItem(GodmodePvP::isBed) > 0;
        Module ca = Modules.get().get(CrystalAura.class);
        if (!hasCrystals && !hasAnchorItem && !hasBedItem) {
            if (ca != null && ca.isActive()) ca.toggle();
            auraMode = -1;
            bestCrystalDmgCache = 0;
            bestAnchorDmgCache = 0;
            bestBedDmgCache = 0;
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
            bestAnchorDmgCache = DamageUtils.anchorDamage(target, Vec3.atCenterOf(best));
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
        if (hasBedItem && bedCandidateIndex < bedCandidates.size()) {
            BedSpot best = bedCandidates.get(bedCandidateIndex);
            bestBedDmgCache = DamageUtils.bedDamage(target, Vec3.atCenterOf(best.pos()));
        }

        if (ca == null) return;

        // Anchor/Bett nur im Nahbereich - sonst Crystal, damit es nie totlaeuft
        boolean inRange = mc.player.distanceToSqr(target) < 5.5 * 5.5;

        // Anchor-Plaetze unerreichbar? -> 2 s Crystal erzwingen (Anti-Stuck)
        boolean anchorForced = tickCounter < crystalForcedUntil;

        // Hysterese statt scharfer Schwelle: zum Wechsel IN den Anchor-/Bett-Modus braucht es einen klaren
        // Vorsprung, zum Bleiben reicht Gleichstand. Ohne das kippt der Modus bei jedem winzigen
        // Schadens-Unterschied (z.B. durch die Ziel-Vorhersage) mehrfach pro Sekunde hin und her -
        // jedes Mal ein voller CrystalAura-Neustart, der wie ein Ruckeln/Haken wirkt.
        double margin = anchorMode.get() == 1 ? 0.0 : 0.15;
        double enterMargin = auraMode == 1 ? -0.3 : margin;

        boolean wantAnchor;
        if (!hasAnchorItem) {
            wantAnchor = false;
        } else if (anchorMode.get() == 1) {
            // Anchor schon bei Gleichstand (bricht Schilde)
            wantAnchor = !outOfGlowstone && !anchorForced && inRange
                && anchorCandidateIndex < anchorCandidates.size()
                && bestAnchorDmgCache >= crystalDmg + enterMargin;
        } else {
            wantAnchor = !outOfGlowstone && !anchorForced && inRange && bestAnchorDmgCache > crystalDmg + 0.15 + enterMargin;
        }

        // Bett: gleiche Hysterese-Logik wie Anchor-Automatik (kein eigener Tie-Break-Modus - use-beds ist
        // ein simpler On/Off-Schalter, siehe Beschreibung).
        double bedEnterMargin = auraMode == 2 ? -0.3 : 0.15;
        boolean wantBed = hasBedItem && inRange && bedCandidateIndex < bedCandidates.size()
            && bestBedDmgCache > crystalDmg + 0.15 + bedEnterMargin;

        // Wenn beide verfuegbar waeren, gewinnt die schadenstaerkere Option - Anchor braucht nur 1
        // Glowstone und ist meist die effizientere Standardwahl bei echtem Gleichstand.
        if (wantAnchor && wantBed) {
            if (bestBedDmgCache > bestAnchorDmgCache) wantAnchor = false; else wantBed = false;
        }

        // Deutlich seltener umschalten (0.5s statt 0.15s) - genug Zeit, damit eine begonnene
        // Platzierung/Ladung auch tatsaechlich fertig wird, statt staendig unterbrochen zu werden.
        if (tickCounter - lastAuraSwitch < 10) return;

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

    /** Nur schlagen, wenn wir aktiv im Anchor- oder Crystal-Angriff auf dieses Ziel stecken - kein Hieb ins Blaue.
     *  Absichtlich NICHT an den genauen Anchor-Stage/Schadens-Zeitpunkt gekoppelt: sonst blockiert eine haengende
     *  Anchor-Platzierung (z.B. Ziel gewebt + gleiche Hoehe + Kandidaten unerreichbar) jeden Nahkampf komplett,
     *  obwohl der Gegner voll treffbar daeme. */
    private boolean explosionImminent(LivingEntity target) {
        if (auraMode == 1 || auraMode == 2) return true;
        if (auraMode == 0) {
            // ca.isActive() allein reicht nicht - das Modul kann eingeschaltet sein, aber ohne Obsidian fuer den
            // Support-Unterbau (oder ohne jeden gueltigen Platzierungs-Kandidaten) faktisch nie explodieren.
            // bestCrystalDmgCache > 0 heisst: der letzte Scan hat wirklich eine machbare Stelle gefunden.
            Module ca = Modules.get().get(CrystalAura.class);
            return ca != null && ca.isActive() && bestCrystalDmgCache > 0;
        }
        return false;
    }

    private double bestDamageAround(LivingEntity target, Vec3 center, boolean crystal) {
        int bx = (int) Math.floor(center.x);
        int by = (int) Math.floor(center.y);
        int bz = (int) Math.floor(center.z);

        double best = 0;

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = 0; dy <= 1; dy++) {
                    Vec3 pos = new Vec3(bx + dx + 0.5, by + dy, bz + dz + 0.5);
                    BlockPos cell = new BlockPos(bx + dx, by + dy, bz + dz);

                    if (!validExplosionSpot(cell, crystal)) continue;
                    if (hitsFriend(pos, crystal)) continue;

                    double selfDmg = crystal
                        ? DamageUtils.crystalDamage(mc.player, pos)
                        : DamageUtils.anchorDamage(mc.player, pos);
                    if (selfDmg > maxSelfDamage.get()) continue;

                    double dmg = crystal
                        ? DamageUtils.crystalDamage(target, pos)
                        : DamageUtils.anchorDamage(target, pos);
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
                for (int dy = 0; dy <= 1; dy++) {
                    Vec3 pos = new Vec3(bx + dx + 0.5, by + dy, bz + dz + 0.5);
                    BlockPos cell = new BlockPos(bx + dx, by + dy, bz + dz);

                    if (!validExplosionSpot(cell, false)) continue;
                    if (hitsFriend(pos, false)) continue;

                    AABB cellBox = new AABB(cell);
                    if (target.getBoundingBox().intersects(cellBox)) continue;
                    if (mc.player.getBoundingBox().intersects(cellBox)) continue;

                    double selfDmg = DamageUtils.anchorDamage(mc.player, pos);
                    if (selfDmg > maxSelfDamage.get()) continue;

                    double dmg = DamageUtils.anchorDamage(target, pos);
                    if (dmg <= 0) continue;

                    found.add(cell);
                    dmgs.add(dmg);
                }
            }
        }

        for (int i = 0; i < found.size(); i++) {
            for (int j = i + 1; j < found.size(); j++) {
                if (dmgs.get(j) > dmgs.get(i)) {
                    BlockPos tp = found.get(i); found.set(i, found.get(j)); found.set(j, tp);
                    Double td = dmgs.get(i); dmgs.set(i, dmgs.get(j)); dmgs.set(j, td);
                }
            }
        }
        anchorCandidates.addAll(found);
    }

    /** Crystal: Obsidian/Bedrock-Basis ODER offene Luft-Tasche (CrystalAura setzt dort im Support-Modus
     *  selbst Obsidian als Unterlage). Anchor: fester Boden, Luft darueber. */
    private boolean validExplosionSpot(BlockPos cell, boolean crystal) {
        if (mc.level == null) return false;
        if (!mc.level.getBlockState(cell).isAir()) return false;
        if (avoidLava.get() && isNearLava(cell)) return false;

        BlockState below = mc.level.getBlockState(cell.below());
        if (crystal) return below.is(Blocks.OBSIDIAN) || below.is(Blocks.BEDROCK) || below.isAir();
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

    private static final Direction[] BED_DIRECTIONS = { Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST };

    /** Alle gueltigen Bett-Plaetze um das Ziel, sortiert nach Schaden (absteigend). Ein Bett braucht anders
     *  als Crystal/Anchor KEINE feste Unterlage (nur zwei freie, ersetzbare Bloecke: Fuss- + Kopfteil in
     *  eine der vier Himmelsrichtungen) - wirkt aber nur ausserhalb der Overworld (Nether/End); das kann der
     *  Client nicht vorab pruefen, das entscheidet allein der Server. */
    private void calcBestBed(LivingEntity target, Vec3 center) {
        bedCandidates.clear();
        bedCandidateIndex = 0;

        int bx = (int) Math.floor(center.x);
        int by = (int) Math.floor(center.y);
        int bz = (int) Math.floor(center.z);

        java.util.List<BedSpot> found = new java.util.ArrayList<>();
        java.util.List<Double> dmgs = new java.util.ArrayList<>();

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = 0; dy <= 1; dy++) {
                    BlockPos foot = new BlockPos(bx + dx, by + dy, bz + dz);
                    if (!validBedCell(foot)) continue;

                    Direction dir = findFreeBedDirection(foot);
                    if (dir == null) continue;

                    Vec3 pos = Vec3.atCenterOf(foot);
                    if (hitsFriendBed(pos)) continue;

                    AABB cellBox = new AABB(foot);
                    if (target.getBoundingBox().intersects(cellBox)) continue;
                    if (mc.player.getBoundingBox().intersects(cellBox)) continue;

                    double selfDmg = DamageUtils.bedDamage(mc.player, pos);
                    if (selfDmg > maxSelfDamage.get()) continue;

                    double dmg = DamageUtils.bedDamage(target, pos);
                    if (dmg <= 0) continue;

                    found.add(new BedSpot(foot, dir));
                    dmgs.add(dmg);
                }
            }
        }

        for (int i = 0; i < found.size(); i++) {
            for (int j = i + 1; j < found.size(); j++) {
                if (dmgs.get(j) > dmgs.get(i)) {
                    BedSpot tp = found.get(i); found.set(i, found.get(j)); found.set(j, tp);
                    Double td = dmgs.get(i); dmgs.set(i, dmgs.get(j)); dmgs.set(j, td);
                }
            }
        }
        bedCandidates.addAll(found);
    }

    /** Bett braucht keine feste Unterlage - nur eine ersetzbare (Luft-)Zelle, optional abseits von Lava. */
    private boolean validBedCell(BlockPos cell) {
        if (mc.level == null) return false;
        if (!mc.level.getBlockState(cell).isAir()) return false;
        return !(avoidLava.get() && isNearLava(cell));
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

        if (rotationQueuedThisTick) return; // Rotations-Slot diesen Tick schon belegt - naechster Tick

        FindItemResult foundBed = InvUtils.findInHotbar(GodmodePvP::isBed);
        if (!foundBed.found()) foundBed = InvUtils.find(GodmodePvP::isBed);
        if (!foundBed.found()) return;
        FindItemResult bed = foundBed;

        // Eigene Rotation VOR der Platzierung setzen (statt BlockUtils' rotate=true) - die Ausrichtung des
        // Kopfteils richtet sich nach der horizontalen Blickrichtung zum Platzierungszeitpunkt, nicht nach
        // der angeklickten Blockseite. dir.toYRot() ist exakt die Umkehrung von Direction.fromYRot().
        double yaw = spot.dir().toYRot();
        rotateAndRun(yaw, 55, () -> {
            if (BlockUtils.place(spot.pos(), bed, false, 50)) {
                bedPlaceFails = 0;
                bedPlaceCooldown = delay(4); // kurze Pause, damit maintainNearbyBeds Zeit zum Zuenden hat
                lastBedProgressTick = tickCounter;
            } else {
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
        if (!useBeds.get()) return;
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
                    if (selfDmg > maxSelfDamage.get()) continue;

                    if (interactBedAt(pos)) bedMaintCooldown = delay(3);
                    return;
                }
            }
        }
        bedMaintCooldown = delay(1); // nichts gefunden - naechster voller Scan erst naechsten Tick statt jeden Tick doppelt
    }

    /** @return true, wenn die Rotation+Interaktion tatsaechlich eingereiht wurde (Rotations-Slot frei war). */
    private boolean interactBedAt(BlockPos pos) {
        Vec3 center = Vec3.atCenterOf(pos);
        return rotateAndRun(Rotations.getYaw(center), Rotations.getPitch(center), () ->
            BlockUtils.interact(new BlockHitResult(center, BlockUtils.getDirection(pos), pos, true), InteractionHand.MAIN_HAND, true)
        );
    }

    private static boolean isBed(ItemStack stack) {
        return stack.getItem() instanceof BedItem;
    }

    /** Zentrale Stelle fuer alle kuenstlichen Wartezeiten: liefert `ticks` normal, oder 0 wenn der
     *  Sofort-Modus (no-delay) aktiv ist. So bleibt jede einzelne Cooldown-Stelle im Code weiterhin
     *  lesbar (die "normale" Wartezeit steht direkt daneben), aber no-delay hebelt sie zentral aus. */
    private int delay(int ticks) {
        return instantMode.get() ? 0 : ticks;
    }

    /** Reiht eine Rotation+Aktion nur ein, wenn dieser Tick noch kein Rotations-Slot vergeben ist (siehe
     *  Feld-Kommentar zu rotationQueuedThisTick). Gibt false zurueck, wenn abgelehnt - der Aufrufer MUSS
     *  in dem Fall alle eigenen Zustandsaenderungen (Cooldowns, Verbrauchszaehler) unterlassen, damit der
     *  naechste Tick sauber erneut versuchen kann, statt die Aktion als "erledigt" zu verbuchen, obwohl
     *  sie nie mit korrekter Blickrichtung ausgefuehrt wurde. */
    private boolean rotateAndRun(double yaw, double pitch, Runnable callback) {
        if (rotationQueuedThisTick) return false;
        rotationQueuedThisTick = true;
        Rotations.rotate(yaw, pitch, callback);
        return true;
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
        if (tickCounter - healWindowStartTick > 8 || hp > hpAtHealWindowStart) {
            hpAtHealWindowStart = hp;
            healWindowStartTick = tickCounter;
        }

        if (!healPotions.get() || blocking || drinkingFireRes || healPotionCooldown > 0) return;

        float dmg = hpAtHealWindowStart - hp;
        if (dmg < healMinDamage.get()) return;

        FindItemResult potion = InvUtils.findInHotbar(GodmodePvP::isHealingSplash);
        if (!potion.found()) potion = InvUtils.find(GodmodePvP::isHealingSplash);
        if (!potion.found()) return;

        boolean thrown;
        if (potion.isOffhand()) {
            thrown = rotateAndRun(self.getYRot(), 80, () -> mc.gameMode.useItem(mc.player, InteractionHand.OFF_HAND));
        } else {
            boolean swapped = InvUtils.swap(potion.slot(), true);
            thrown = rotateAndRun(self.getYRot(), 80, () -> {
                mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                if (swapped) InvUtils.swapBack();
            });
            if (!thrown && swapped) InvUtils.swapBack(); // Rotations-Slot belegt - Swap sofort rueckgaengig, kein Trank geworfen
        }
        if (!thrown) return; // naechster Tick erneut versuchen - Cooldown/Fenster bleiben unveraendert

        healPotionCooldown = healCooldown.get();
        hpAtHealWindowStart = hp;
        healWindowStartTick = tickCounter;
    }

    /** Haelt Fire Resistance permanent aktiv, solange man sich im Nether befindet - macht Lava-Kontakt,
     *  Explosions-Feuer und brennende Nachbarblöcke irrelevant. Laeuft unabhaengig vom Kampf-Zustand. */
    private void maintainFireResistance() {
        if (drinkingFireRes) {
            if (mc.player.hasEffect(MobEffects.FIRE_RESISTANCE) || tickCounter - fireResStartTick > 40) {
                InvUtils.swapBack();
                drinkingFireRes = false;
            } else {
                mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
            }
            return;
        }

        if (!autoFireRes.get()) return;
        if (mc.level == null || mc.level.dimension() != Level.NETHER) return;
        if (mc.player.hasEffect(MobEffects.FIRE_RESISTANCE)) return;

        FindItemResult potion = findFireResistancePotion();
        if (!potion.found()) return;

        if (InvUtils.swap(potion.slot(), true)) {
            mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
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
        FindItemResult found = InvUtils.findInHotbar(isFireRes);
        if (!found.found()) found = InvUtils.find(isFireRes);
        return found;
    }

    // ---------- D-Tap-Executor (Obsidian in Flugbahn, 2 Crystals im Immunitaets-Abstand) ----------

    private void startDtap(LivingEntity target) {
        Vec3 predicted = predict(target);
        BlockPos floor = findDtapSpot(target, predicted);
        if (floor == null) return;

        if (mc.level.getBlockState(floor).isAir()) {
            FindItemResult obsidian = InvUtils.findInHotbar(Items.OBSIDIAN);
            if (!obsidian.found()) obsidian = InvUtils.find(Items.OBSIDIAN);
            if (!obsidian.found()) return;
            if (!BlockUtils.place(floor, obsidian, true, 50)) return;
        }

        dtapSpot = floor;
        dtapStage = 1;
        dtapStageTick = tickCounter;
    }

    /** Sucht rund um die vorhergesagte Landeposition eine gueltige Crystal-Basis (bestehendes Obsidian/
     *  Bedrock oder freie Luft zum selbst Obsidian setzen) mit maximalem Schaden am Ziel. Der Eigenschaden-
     *  Deckel wird verschaerft (60%), weil bei D-Tap zwei Explosionen in ca. 0.5s Abstand zusammenkommen. */
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
                    if (!validExplosionSpot(cell, true)) continue;
                    if (hitsFriend(Vec3.atCenterOf(cell), true)) continue;

                    double selfDmg = DamageUtils.crystalDamage(mc.player, Vec3.atCenterOf(cell));
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
                if (tickCounter - dtapStageTick > 15) { dtapStage = 0; return; } // Fenster verpasst
                if (!mc.level.getBlockState(dtapSpot.above()).isAir()) { dtapStage = 0; return; } // besetzt

                FindItemResult crystal = InvUtils.findInHotbar(Items.END_CRYSTAL);
                if (!crystal.found()) crystal = InvUtils.find(Items.END_CRYSTAL);
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
                if (tickCounter - dtapStageTick > 30 || !mc.level.getBlockState(dtapSpot.above()).isAir()) {
                    dtapStage = 0;
                    dtapCooldown = delay(30);
                    return;
                }

                FindItemResult crystal = InvUtils.findInHotbar(Items.END_CRYSTAL);
                if (!crystal.found()) crystal = InvUtils.find(Items.END_CRYSTAL);
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
        Vec3 center = Vec3.atCenterOf(floor);
        return rotateAndRun(Rotations.getYaw(center), Rotations.getPitch(center), () -> {
            boolean swapped = InvUtils.swap(item.slot(), true);
            BlockUtils.interact(new BlockHitResult(center, BlockUtils.getDirection(floor), floor, true), InteractionHand.MAIN_HAND, true);
            if (swapped) InvUtils.swapBack();
        });
    }

    private EndCrystal findCrystalAbove(BlockPos floor) {
        AABB box = new AABB(floor.above()).inflate(0.6, 1.0, 0.6);
        for (EndCrystal ec : mc.level.getEntitiesOfClass(EndCrystal.class, box)) return ec;
        return null;
    }

    /** @return true, wenn die Rotation+Attacke tatsaechlich eingereiht wurde (Rotations-Slot frei war). */
    private boolean attackCrystal(EndCrystal ec) {
        Vec3 center = ec.getBoundingBox().getCenter();
        return rotateAndRun(Rotations.getYaw(center), Rotations.getPitch(center), () -> {
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

        int steps = (int) Math.min(len, 6);
        for (int step = 1; step <= steps; step++) {
            BlockPos p = here.offset((int) Math.round(dx * step), 0, (int) Math.round(dz * step));
            if (isFireBlock(p) || isFireBlock(p.above())) return true;
        }
        return false;
    }

    private boolean isFireBlock(BlockPos pos) {
        var block = mc.level.getBlockState(pos).getBlock();
        return block == Blocks.FIRE || block == Blocks.SOUL_FIRE;
    }

    /** Baritone weigert sich hart, durch Feuer zu pathen - dafuer kurz manuell geradeaus durchlaufen,
     *  der Schaden ist minimal verglichen mit dem, was der Bot sonst schon wegsteckt. */
    private void walkThroughFire(LivingEntity target) {
        cancelFollow();
        Vec3 center = target.getBoundingBox().getCenter();
        mc.player.setYRot((float) Rotations.getYaw(center));
        Input.setKeyState(mc.options.keyUp, true);
        Input.setKeyState(mc.options.keySprint, true);
        // Auto-Sprung ueber Stufen/Kanten im Weg - sonst bleibt die manuelle Geradeaus-Bewegung
        // (ohne Baritones Pfadberechnung) an jedem kleinen Hoehenunterschied haengen.
        Input.setKeyState(mc.options.keyJump, mc.player.horizontalCollision && mc.player.onGround());
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
        fp.follow(e -> e == target);
        followActive = true;
        if (!target.getUUID().equals(followedId)) {
            followedId = target.getUUID();
            anchorPlaceFails = 0; // Zielwechsel -> Fail-Counter zuruecksetzen
        }
    }

    private void cancelFollow() {
        if (followActive) {
            BaritoneAPI.getProvider().getPrimaryBaritone().getFollowProcess().cancel();
            BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().cancelEverything();
            followActive = false;
            followedId = null;
        }
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
            nextStrafeSwitchTick = -1; // frisches, zufaelliges Intervall beim naechsten Nahkampf-Eintritt
            return;
        }

        Vec3 center = target.getBoundingBox().getCenter();
        if (freeLook.get()) {
            Rotations.rotate(Rotations.getYaw(center), Rotations.getPitch(center));
        } else {
            mc.player.setYRot((float) Rotations.getYaw(center));
            mc.player.setXRot((float) Rotations.getPitch(center));
        }

        // Zufaellig getaktete Richtungswechsel (10-24 Ticks, 0.5-1.2s) statt eines starren 20-Tick-Rhythmus -
        // ein exakt periodisches Strafing ist leicht zu lesen (fuer Gegner UND Anti-Cheat-Heuristiken),
        // echte Spieler wechseln unregelmaessig.
        if (tickCounter >= nextStrafeSwitchTick) {
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

        Vec3 vel = mc.player.getDeltaMovement();
        double speed = Math.sqrt(vel.x * vel.x + vel.z * vel.z);
        if (speed >= 0.6 || tickCounter - lastFireworkTick <= 20) return;

        FindItemResult firework = InvUtils.findInHotbar(Items.FIREWORK_ROCKET);
        if (!firework.found()) firework = InvUtils.find(Items.FIREWORK_ROCKET);
        if (!firework.found()) return;

        boolean swapped = InvUtils.swap(firework.slot(), true);
        mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
        if (swapped) InvUtils.swapBack();
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

    /** Notdeckung: platziert einen Obsidian-Block an einer offenen Seite, wenn kein natuerliches Loch da ist.
     *  Baut NIE in die Richtung des Ziels - sonst mauert sich der Bot die eigene Sichtlinie zu und kann
     *  weder Nahkampf noch Explosionen mehr landen (genau das erzeugte den "steht nur noch da"-Bug: der
     *  einzige offene Nachbarblock lag zufaellig zwischen Bot und Gegner). */
    private void buildOwnCover(Player self, LivingEntity target) {
        FindItemResult obsidian = InvUtils.findInHotbar(Items.OBSIDIAN);
        if (!obsidian.found()) obsidian = InvUtils.find(Items.OBSIDIAN);
        if (!obsidian.found()) return;

        Vec3 toTarget = target.position().subtract(self.position());
        net.minecraft.core.Direction towardTarget = null;
        double bestDot = 0;
        for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            double dot = dir.getStepX() * toTarget.x + dir.getStepZ() * toTarget.z;
            if (dot > bestDot) {
                bestDot = dot;
                towardTarget = dir;
            }
        }

        BlockPos feet = self.blockPosition();
        for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            if (dir == towardTarget) continue;
            BlockPos side = feet.relative(dir);
            if (mc.level.getBlockState(side).isAir()) {
                BlockUtils.place(side, obsidian, true, 50);
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
            if (buildCover.get() && dist <= 4.5 && buildCoverCooldown <= 0) {
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

    private void updateTracking(LivingEntity target) {
        UUID id = target.getUUID();
        Vec3 cur = target.position();
        Vec3 prev = lastPositions.put(id, cur);

        if (prev != null) {
            double jump = cur.distanceTo(prev);
            if (jump > 6.0) {
                velocities.put(id, Vec3.ZERO);
                popBurstUntil = Math.max(popBurstUntil, tickCounter + 6);
                ChatUtils.info("Pearl-Teleport erkannt (%.0f m) - verfolge neue Position.", jump);
                // FollowProcess verfolgt automatisch zur neuen Position
            } else {
                Vec3 vel = cur.subtract(prev);
                if (vel.length() > 2.0) vel = vel.normalize().scale(2.0);
                velocities.put(id, vel);
            }
        }
    }

    /** Am Boden: einfache lineare Extrapolation reicht (kaum Vertikalbewegung). In der Luft (gesprungen,
     *  von Anchor/Crystal hochgeschleudert, am Fallen, Elytra): simuliert Minecrafts Schwerkraft+Luftwiderstand
     *  pro Tick, damit die Vorhersage der echten Wurfparabel folgt statt geradeaus davonzulaufen - sonst zielt
     *  Crystal/Anchor-Platzierung bei einem in der Luft befindlichen Gegner systematisch daneben. */
    private Vec3 predict(LivingEntity target) {
        Vec3 vel = velocities.getOrDefault(target.getUUID(), Vec3.ZERO);
        int ticks = leadTicks.get();

        if (target.onGround()) {
            return target.position().add(vel.scale(ticks));
        }

        Vec3 pos = target.position();
        Vec3 v = vel;
        for (int i = 0; i < ticks; i++) {
            pos = pos.add(v);
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
        boolean swapped = false;
        if (useMace.get() && mc.player.fallDistance > 1.5f) {
            FindItemResult mace = InvUtils.findInHotbar(itemStack -> itemStack.getItem() instanceof MaceItem);
            if (mace.found() && !mace.isMainHand()) swapped = InvUtils.swap(mace.slot(), true);
        }
        if (!swapped && preferAxeMelee.get()) {
            FindItemResult axe = InvUtils.findInHotbar(itemStack -> itemStack.getItem() instanceof AxeItem);
            if (axe.found() && !axe.isMainHand()) swapped = InvUtils.swap(axe.slot(), true);
        }
        boolean wasSprinting = mc.player.isSprinting();
        mc.gameMode.attack(mc.player, target);
        mc.player.swing(InteractionHand.MAIN_HAND);
        if (swapped) InvUtils.swapBack();

        // Nach einem Sprint-Treffer den Sprint kurz zuruecksetzen, damit der naechste Schlag erneut
        // als "frischer" Sprint-Treffer zaehlt (mehr Knockback) statt nur ein gewoehnlicher Folgehit.
        if (sprintReset.get() && wasSprinting) sprintResetCooldown = 2;
    }

    private void breakShield(Player target) {
        FindItemResult axe = InvUtils.findInHotbar(itemStack -> itemStack.getItem() instanceof AxeItem);
        if (!axe.found()) return;

        boolean swapped = InvUtils.swap(axe.slot(), true);
        mc.gameMode.attack(mc.player, target);
        mc.player.swing(InteractionHand.MAIN_HAND);
        if (swapped) InvUtils.swapBack();
    }

    private void throwPearl(LivingEntity aimAt, boolean away) {
        FindItemResult pearl = InvUtils.findInHotbar(Items.ENDER_PEARL);
        if (!pearl.found()) pearl = InvUtils.find(Items.ENDER_PEARL);
        if (!pearl.found()) return;

        double yaw, pitch;
        if (away && aimAt != null) {
            yaw = Rotations.getYaw(aimAt) + 180.0;
            pitch = -20;
        } else if (aimAt != null) {
            yaw = Rotations.getYaw(aimAt);
            pitch = Rotations.getPitch(aimAt.getBoundingBox().getCenter());
        } else {
            return;
        }

        if (pearl.isOffhand()) {
            if (rotateAndRun(yaw, pitch, () -> mc.gameMode.useItem(mc.player, InteractionHand.OFF_HAND))) {
                lastPearlTick = tickCounter;
            }
        } else {
            boolean swapped = InvUtils.swap(pearl.slot(), true);
            if (rotateAndRun(yaw, pitch, () -> {
                mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                if (swapped) InvUtils.swapBack();
            })) {
                lastPearlTick = tickCounter;
            } else if (swapped) {
                InvUtils.swapBack(); // Rotations-Slot belegt - Swap sofort rueckgaengig, kein Wurf, kein Cooldown verbraucht
            }
        }
    }

    /** Perle senkrecht nach unten - teleportiert bei Landung, kein unkontrolliertes Fallen nach Knockback. */
    private void throwPearlDown() {
        FindItemResult pearl = InvUtils.findInHotbar(Items.ENDER_PEARL);
        if (!pearl.found()) pearl = InvUtils.find(Items.ENDER_PEARL);
        if (!pearl.found()) return;

        if (pearl.isOffhand()) {
            if (rotateAndRun(mc.player.getYRot(), 80, () -> mc.gameMode.useItem(mc.player, InteractionHand.OFF_HAND))) {
                lastPearlTick = tickCounter;
            }
        } else {
            boolean swapped = InvUtils.swap(pearl.slot(), true);
            if (rotateAndRun(mc.player.getYRot(), 80, () -> {
                mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                if (swapped) InvUtils.swapBack();
            })) {
                lastPearlTick = tickCounter;
            } else if (swapped) {
                InvUtils.swapBack();
            }
        }
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
        if (mc.gui.screen() != null || tickCounter % 3 != 0) return;

        BlockPos feet = target.blockPosition();
        if (feet.equals(mc.player.blockPosition())) return; // sonst web(t) sich der Bot bei Ueberlappung selbst ein
        if (!mc.level.getBlockState(feet).isAir()) return;
        if (!mc.level.getBlockState(feet.below()).blocksMotion()) return;

        FindItemResult web = InvUtils.findInHotbar(Items.COBWEB);
        if (!web.found()) web = InvUtils.find(Items.COBWEB);
        if (web.found()) BlockUtils.place(feet, web, true, 50);
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

        // Combo-Fenster: nach jedem spuerbaren Treffer sofort auf die jeweils andere Aura-Art pruefen,
        // statt die normale Umschalt-Sperre abzuwarten - Anchor+Crystal Doppel-Schaden ausnutzen.
        if (entity != mc.player && drop >= 3.0f && entity.isAlive()) {
            lastAuraSwitch = -999;
            anchorCandidateIndex = anchorCandidates.size();
        }
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
        if (totalItem(item) <= min) return;

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
        if (totalItem(pred) <= min) return;

        int src = findMainSlotWith(pred);
        int dst = hotbarTargetSlot(pred);
        if (src < 0 || dst < 0) return;

        InvUtils.move().from(src).to(dst);
    }

    private void inventoryTick(Player self) {
        refill(Items.END_CRYSTAL, minCrystals.get());
        refill(Items.RESPAWN_ANCHOR, minAnchors.get());
        refill(Items.GLOWSTONE, minGlowstone.get());
        refill(Items.ENDER_PEARL, minPearls.get());
        refill(Items.OBSIDIAN, minObsidian.get());
        refill(Items.COBWEB, minWeb.get());
        refill(GodmodePvP::isBed, minBeds.get());
        refill(GodmodePvP::isHealingSplash, minHealPotionsStock.get());

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

        boolean noAnchorSupply = totalItem(Items.RESPAWN_ANCHOR) == 0 || totalItem(Items.GLOWSTONE) == 0;
        if (noAnchorSupply && !warnedOutOfAnchorSupply) {
            warnedOutOfAnchorSupply = true;
            ChatUtils.info("Kein Respawn Anchor oder Glowstone mehr im Inventar - Anchor-Modus pausiert!");
        }
        if (!noAnchorSupply) warnedOutOfAnchorSupply = false;

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
            }
        } catch (Throwable t) {
            savedSupport = null;
            error("CrystalAura-Support-Mode konnte nicht gesetzt werden (Meteor-Version geaendert?) - Obsidian-Unterbau bei freier Luft laeuft evtl. nicht automatisch.");
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
