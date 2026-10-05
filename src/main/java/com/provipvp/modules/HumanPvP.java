package com.provipvp.modules;

import com.provipvp.crystal.AttackGate;
import com.provipvp.crystal.CrystalOwnership;
import com.provipvp.crystal.SelfDamageGuard;
import com.provipvp.exec.PitchVariance;
import com.provipvp.mechanics.ShieldWindow;
import com.provipvp.net.ActionCadence;
import com.provipvp.net.AttackDispatcher;
import com.provipvp.net.TickRateGate;
import com.provipvp.ray.ActionRayValidator;
import com.provipvp.ray.ReachPolicy;
import com.provipvp.rotation.GcdRotator;
import com.provipvp.terrain.ExplosionScanner;
import com.provipvp.util.InvHelper;
import com.provipvp.util.PvpMath;
import com.provipvp.util.RandomBetween;

import baritone.api.BaritoneAPI;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.entity.EntityAddedEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.combat.CrystalAura;
import meteordevelopment.meteorclient.systems.modules.player.AutoMend;
import meteordevelopment.meteorclient.systems.modules.player.AutoEat;
import meteordevelopment.meteorclient.systems.modules.movement.NoFall;
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
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BedItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * ProviPvP V2 - "menschlicher" Kampf-Bot.
 *
 * Anders als GodmodePvP (V1, maximale Aggression/Praezision) versucht dieses Modul bewusst
 * MENSCHLICHE Unzulaenglichkeit nachzubilden: sichtbare, tempolimitierte Kamera-Drehung statt
 * Sofort-Snap, zufaellige Reaktionszeit auf neue Ziele, Klick-Jitter/gelegentliches Verklicken,
 * groessere Umschalt-Hysterese zwischen Crystal/Anchor und vorsichtigere Baritone-Pfade.
 *
 * Wichtig: das ist KEIN Unerkennbarkeits-Versprechen. Es reduziert nur die offensichtlichsten
 * statistischen Signale (perfekte Rotation, 0-Tick-Reaktion, 100%-Kadenz), die Rotation-/Timing-
 * Analyse (Grim, Vulcan, NCP, ...) typischerweise pruefen. Befehl: .hpvp
 */
public class HumanPvP extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgCombat = settings.createGroup("1 · Angriff & Auras");
    private final SettingGroup sgDefense = settings.createGroup("2 · Schutz & Recovery");
    private final SettingGroup sgNavigation = settings.createGroup("3 · Navigation");
    private final SettingGroup sgInv = settings.createGroup("4 · Inventar");
    private final SettingGroup sgHuman = settings.createGroup("5 · Human-Profil");
    private final SettingGroup sgPearl = settings.createGroup("6 · Perlen & Flucht");
    private final SettingGroup sgHeal = settings.createGroup("7 · Heilung");
    private final SettingGroup sgQA = settings.createGroup("8 · QA & Erweitert");

    // General
    public final Setting<Boolean> follow = sgNavigation.add(new BoolSetting.Builder()
        .name("follow")
        .description("Verfolgt das Ziel mit Baritone - vorsichtige, menschentaugliche Pfade.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Integer> followRange = sgNavigation.add(new IntSetting.Builder()
        .name("follow-range")
        .description("Maximale Distanz, ab der ein Spieler ueberhaupt als Ziel erkannt/beobachtet wird.")
        .defaultValue(20)
        .range(6, 48)
        .sliderRange(6, 32)
        .build()
    );

    public final Setting<Integer> engageDistance = sgNavigation.add(new IntSetting.Builder()
        .name("engage-distance")
        .description("Erst ab dieser Distanz laeuft/perlt der Bot aktiv auf das Ziel zu. Darueber hinaus (bis follow-range) wird nur beobachtet, ohne loszurennen.")
        .defaultValue(14)
        .range(4, 48)
        .sliderRange(4, 32)
        .build()
    );

    public final Setting<Double> attackRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("attack-range")
        .description("Maximale Distanz fuer Nahkampf-Schlaege. Manche Server/Anti-Cheats tolerieren mehr oder weniger als das Standard-3.4.")
        .defaultValue(3.4)
        .range(2.5, 4.5)
        .sliderRange(2.5, 4.0)
        .build()
    );

    public final Setting<Boolean> smartTargeting = sgGeneral.add(new BoolSetting.Builder()
        .name("smart-targeting")
        .description("Bevorzugt bei der Zielwahl einen isolierten Gegner (ohne Mitspieler in Rueckendeckungs-Reichweite) vor reiner Distanz. Die Zielwahl wird regelmaessig neu bewertet; ein laufender Kampf bleibt bis zum naechsten sauberen Wechsel gebunden.")
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

    public final Setting<Integer> reactionMinTicks = sgHuman.add(new IntSetting.Builder()
        .name("reaction-min")
        .description("Minimale Reaktionszeit (Ticks) auf ein neues Ziel, bevor angegriffen wird.")
        .defaultValue(3)
        .range(0, 15)
        .sliderRange(0, 12)
        .build()
    );

    public final Setting<Integer> reactionMaxTicks = sgHuman.add(new IntSetting.Builder()
        .name("reaction-max")
        .description("Maximale Reaktionszeit (Ticks) auf ein neues Ziel.")
        .defaultValue(9)
        .range(1, 25)
        .sliderRange(1, 20)
        .build()
    );

    public final Setting<Boolean> freeLook = sgQA.add(new BoolSetting.Builder()
        .name("free-look")
        .description("Silent-Rotations: der Bot zielt weiterhin korrekt (das Server-Paket bekommt die richtige Blickrichtung), aber deine eigene Kamera bleibt frei drehbar. ACHTUNG: separate Rotations-Pakete ohne dazu passende Kamerabewegung sind eines der klassischsten Anti-Cheat-Erkennungsmuster ueberhaupt - auf Servern mit aktivem Anti-Cheat kann das zu Bewegungs-Korrekturen/Rubberbanding fuehren. Deshalb standardmaessig aus.")
        .defaultValue(false)
        .build()
    );

    // Combat
    public final Setting<Boolean> useAnchors = sgCombat.add(new BoolSetting.Builder()
        .name("use-anchors")
        .description("Anchor ueberhaupt erlauben (braucht 1 Glowstone pro Anchor).")
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

    public final Setting<Boolean> useBeds = sgCombat.add(new BoolSetting.Builder()
        .name("use-beds")
        .description("Bed Aura: platziert und zuendet Betten als Explosion (Schadenswert 5.0, wie Anchor). Wirkt nur ausserhalb der Overworld (Nether/End, z.B. Portal-Camping auf 5b5t) - der Client kann das nicht vorab pruefen, das entscheidet allein der Server. Standardmaessig aus, damit in der Overworld nicht sinnlos Betten verbraucht werden.")
        .defaultValue(false)
        .build()
    );

    public final Setting<Double> bedMinDamage = sgCombat.add(new DoubleSetting.Builder()
        .name("bed-min-damage")
        .description("Mindestschaden, den eine Bett-Explosion beim Ziel anrichten muss, damit Bett-Modus ueberhaupt in Frage kommt - siehe GodmodePvP fuer dieselbe Begruendung (verifizierter Mechanik-Abgleich: Meteor BedAura 'min-damage', CandyCat BedAura 'minDmg').")
        .defaultValue(4.0)
        .range(0.5, 10.0)
        .sliderRange(0.5, 10.0)
        .build()
    );

    public final Setting<Double> bedMaxSelfDamage = sgCombat.add(new DoubleSetting.Builder()
        .name("bed-max-self-damage")
        .description("Eigener Eigenschaden-Deckel NUR fuer Bett-Explosionen - siehe GodmodePvP fuer die volle Begruendung (eine Bett-Explosion macht im Nahbereich ein Vielfaches eines Crystals, ein auf Crystal getrimmter Deckel legt Bed Aura sonst komplett stumm). Selbstmord-Schutz greift zusaetzlich immer.")
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

    public final Setting<Integer> minSupportDelay = sgQA.add(new IntSetting.Builder()
        .name("min-support-delay")
        .description("Mindest-Tickabstand zwischen Obsidian-Unterbau und dem folgenden Crystal-Platzieren (CrystalAuras 'support-delay'). Beide Aktionen nutzen Minecrafts eigenes sequenznummer-basiertes Block-Vorhersage-System (seit 1.19) - schickt man beide zu dicht hintereinander raus, bevor die erste Sequenz vom Server bestaetigt ist, kann die Vorhersage durcheinanderkommen. Auf Servern mit spuerbarer Latenz oder Versions-Uebersetzung (z.B. ViaVersion) braucht es mehr Puffer als den Meteor-Standard.")
        .defaultValue(4)
        .range(0, 10)
        .sliderRange(0, 10)
        .build()
    );

    public final Setting<Boolean> preferAxeMelee = sgCombat.add(new BoolSetting.Builder()
        .name("prefer-axe-melee")
        .description("Schlaegt automatisch mit der Axt statt Schwert.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> sprintReset = sgCombat.add(new BoolSetting.Builder()
        .name("sprint-reset")
        .description("W-Tap: setzt den Sprint vor jedem Nahkampf-Treffer kurz zurueck (aus-ein), damit jeder Schlag den Sprint-Knockback-Bonus bekommt.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> knockbackPearl = sgPearl.add(new BoolSetting.Builder()
        .name("knockback-pearl")
        .description("Wenn der Bot durch Knockback in die Luft geschleudert wird ODER generell gerade in einem gefaehrlichen Fall steckt (z.B. von einer Kante), sofort senkrecht nach unten perlen.")
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
        .description("Kreis-strafet im Nahkampf (variiert den Explosionswinkel, schwerer zu treffen) und weicht kurz zurueck, wenn gerade eine neue Explosionsquelle (Crystal/Anchor/Bett) in der Naehe auftaucht - siehe GodmodePvP fuer dieselbe Mechanik.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Double> maxSelfDamage = sgCombat.add(new DoubleSetting.Builder()
        .name("max-self-damage")
        .description("Maximaler Eigenschaden pro Angriffsplatz (konservativer als V1).")
        .defaultValue(6.0)
        .range(2.0, 12.0)
        .sliderRange(2.0, 10.0)
        .build()
    );

    public final Setting<Double> attackChance = sgHuman.add(new DoubleSetting.Builder()
        .name("attack-chance")
        .description("Wahrscheinlichkeit, dass ein bereiter Schlag wirklich ausgefuehrt wird (menschliches Verklicken).")
        .defaultValue(0.9)
        .range(0.5, 1.0)
        .sliderRange(0.5, 1.0)
        .build()
    );

    public final Setting<Double> aimTolerance = sgHuman.add(new DoubleSetting.Builder()
        .name("aim-tolerance")
        .description("Ziel-Toleranz in Grad, bevor geschlagen oder platziert wird.")
        .defaultValue(4.0)
        .range(1.0, 10.0)
        .sliderRange(1.0, 8.0)
        .build()
    );

    public final Setting<Double> maxTurnPerTick = sgHuman.add(new DoubleSetting.Builder()
        .name("max-turn-speed")
        .description("Maximale Kamera-Drehung pro Tick in Grad (menschliches Tempo statt Sofort-Snap).")
        .defaultValue(18.0)
        .range(5.0, 45.0)
        .sliderRange(5.0, 40.0)
        .build()
    );

    /** Obergrenze des "menschlichen Klickversatzes" — der untere Rand ist immer 1 Tick, ein Bereich
     *  aus einem einzelnen Wert waere genau das maschinenidentische Timing, das dieses Profil
     *  vermeiden soll (siehe RandomBetween). */
    public final Setting<Integer> clickDelayMax = sgHuman.add(new IntSetting.Builder()
        .name("click-delay-max")
        .description("Obergrenze des zufaelligen Klick-Versatzes in Ticks (1 = nie verzoegert). Der Bot wartet vor jedem bereiten Schlag 1..N Ticks - das ist die zweite Haelfte des Verklickens neben attack-chance und der Grund, warum der Schlagtakt nie exakt im Cooldown-Takt faellt.")
        .defaultValue(4)
        .range(1, 20)
        .sliderRange(1, 10)
        .build()
    );

    /** Symmetrische Streuung um die gesendete Rotation. Bewusst winzig: die Rampe aus smoothLookAt()
     *  ist die eigentliche Humanisierung dieses Profils, der Jitter existiert nur, damit nicht drei
     *  aufeinanderfolgende Rotationen exakt dasselbe Gitter-Delta haben (Grim DuplicateRotPlace). */
    public final Setting<Double> rotationJitter = sgHuman.add(new DoubleSetting.Builder()
        .name("rotation-jitter")
        .description("Zufaellige Streuung der gesendeten Rotation um den Zielwinkel, symmetrisch. Wertet als reines Gitter-Rauschen, damit der Winkel auf dem Maus-Empfindlichkeits-Gitter landet statt auf einem glatten Float. 0 = aus.")
        .defaultValue(0.2)
        .range(0.0, 2.0)
        .sliderRange(0.0, 1.0)
        .build()
    );

    /** Rettungswurf-Streuung — siehe PitchVariance.VERTICAL_DOWN. */
    public final Setting<Double> pearlDownVariance = sgPearl.add(new DoubleSetting.Builder()
        .name("pearl-down-variance")
        .description("Streuung in Grad um den festen 80-Grad-Winkel des senkrechten Rettungswurfs. Ohne das wirft der Bot bei jedem Fall exakt gleich - das ist das auffaelligste Maschinensignal in diesem ganzen Profil. 0 nutzt die Sicherheitsspanne von 0.75 Grad.")
        .defaultValue(0.75)
        .range(0.0, 10.0)
        .sliderRange(0.0, 5.0)
        .build()
    );

    public final Setting<Boolean> strictReach = sgQA.add(new BoolSetting.Builder()
        .name("strict-reach")
        .description("Prueft Nahkampf und Schildbrechen gegen die wirklich gesendete Rotation: Vanilla-Reichweite 3.0 (Grim flaggt ab 3.0005) statt des attack-range-Werts, plus Sichtlinie und Zieltreffer. Der Standardwert attack-range=3.4 lag real ueber der Grim-Schwelle - jeder Schlag zwischen 3.0 und 3.4 war ein Reach-Flag. Aus schalten, wenn der Server nachweislich mehr Reichweite erlaubt.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> packetCadence = sgQA.add(new BoolSetting.Builder()
        .name("packet-cadence")
        .description("Eine Aktion pro Bewegungspaket: kein zweiter Angriff, keine zweite Platzierung und kein Armschwung im selben Paket (Grim MultiActionsA/DuplicateSwing). Zaehlt auch den Slot-Wechsel zwischen zwei Aktionen - genau die Verschachtelung, die als PacketOrderE/F auffaellt.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> lagThrottle = sgQA.add(new BoolSetting.Builder()
        .name("lag-throttle")
        .description("Unter Lag (langsamer Tick als 1.2 s nominal) nur noch jede zweite Aktion; bei einem harten Spike oder unter 30% HP gar keine. Die Zuordnung von Bewegung, Rotation und Aktion ist bei gehaemmter Verbindung genau das, was als Rubberband zurueckkommt.")
        .defaultValue(true)
        .build()
    );

    /** Selbst-Platzierung als eingebaute Crystal-Buchhaltung: eigene Crystals loesen keinen
     *  Auto-Schield aus und werden nicht als "frischer Gegner-Crystal" gezaehlt. */
    public final Setting<Boolean> crystalOwnership = sgQA.add(new BoolSetting.Builder()
        .name("crystal-ownership")
        .description("Merkt sich, welche End Crystals das eigene Aura gesetzt hat. Eigene Crystals loesen dann keinen Auto-Schield aus - sonst blockt der Bot im Crystal-Kampf reflexhaft gegen seinen eigenen Crystal und schuetzt sich nie gegen den des Gegners.")
        .defaultValue(true)
        .build()
    );

    // Defense
    public final Setting<Integer> trapMode = sgDefense.add(new IntSetting.Builder()
        .name("trap-mode")
        .description("Cobweb an den Fuessen des Gegners: 0 = aus, 1 = wenn Gegner nah (<=6 Bloecke), 2 = immer.")
        .defaultValue(1)
        .range(0, 2)
        .sliderRange(0, 2)
        .build()
    );

    public final Setting<Boolean> escapePearl = sgPearl.add(new BoolSetting.Builder()
        .name("escape-pearl")
        .description("Perlen-Flucht bei kritischem HP und nahem Gegner.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> antiRubberband = sgDefense.add(new BoolSetting.Builder()
        .name("anti-rubberband")
        .description("Erkennt Server-Positionskorrekturen (Rubberband) und verwirft den alten Pfad, statt dagegen anzukaempfen. Ein wirklich extremer Sprung greift auch waehrend des Kampfes, ein moderater nur ausserhalb (sonst wuerde normaler Explosions-Knockback faelschlich als Rubberband gewertet).")
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
        .description("Isst automatisch (Meteors AutoEat), wenn der Hunger niedrig ist - ohne genug Saettigung setzt Minecraft selbst das Sprinten aus.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> noFallOn = sgDefense.add(new BoolSetting.Builder()
        .name("no-fall")
        .description("Verhindert Fallschaden (Meteors NoFall) waehrend Baritone aggressiv verfolgt (Parkour/Klippen-Sprung).")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> autoShield = sgDefense.add(new BoolSetting.Builder()
        .name("auto-shield")
        .description("Blockt kurz mit dem Schild, wenn frisch ein feindlicher Crystal in der Naehe erscheint - reduziert den Explosionsschaden.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> fastTotem = sgDefense.add(new BoolSetting.Builder()
        .name("fast-totem")
        .description("Legt einen Totem in die Offhand nach, sobald sie leer ist.")
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

    // Inventar
    public final Setting<Boolean> invManager = sgInv.add(new BoolSetting.Builder()
        .name("inv-manager")
        .description("Legt knapp gewordene Combat-Items (Crystals, Anker, Glowstone, Perlen, Obsidian, Web) aus dem Hauptinventar in die Hotbar nach und raeumt unbrauchbare Ballast-Slots frei.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Integer> minCrystals = sgInv.add(new IntSetting.Builder()
        .name("min-crystals")
        .description("Nachschub-Schwelle Crystals.")
        .defaultValue(24)
        .range(4, 64)
        .sliderRange(4, 64)
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
        .description("Nachschub-Schwelle Obsidian.")
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
        .description("Nachschub-Schwelle Betten (Bed Aura). Funktioniert unabhaengig von der Stack-Groesse - auch bei serverseitig erweiterten 64er-Staples (z.B. 5b5t).")
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

    // Enderperlen
    public final Setting<Boolean> pearlThrow = sgPearl.add(new BoolSetting.Builder()
        .name("pearl-gapclose")
        .description("Perlt zum Gegner, wenn er zu weit weg ist.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Double> pearlMinDist = sgPearl.add(new DoubleSetting.Builder()
        .name("pearl-min-dist")
        .description("Distanzschwelle fuer den Gap-Close-Wurf; der effektive Wert ist mindestens attack-range + 0.5 und benoetigt freie Sicht.")
        .defaultValue(10.0)
        .range(6.0, 40.0)
        .sliderRange(6.0, 30.0)
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
        .description("Mindestens so viel HP müssen im rollierenden Fenster (bis zu 8 Ticks) verloren gehen, damit ein Heiltrank geworfen wird; nach dem ersten qualifizierten Treffer wird bis zur vollen Health weiter geheilt.")
        .defaultValue(3.0)
        .range(0.5, 10.0)
        .sliderRange(0.5, 10.0)
        .build()
    );

    public final Setting<Integer> healCooldown = sgHeal.add(new IntSetting.Builder()
        .name("heal-cooldown")
        .description("Mindestabstand (Ticks) zwischen zwei geworfenen Heiltraenken - verhindert, dass ein einzelner Mehrfach-Treffer-Combo sofort mehrere Traenke auf einmal verbraucht, ohne bei anhaltendem Druck spuerbar zu blockieren.")
        .defaultValue(12)
        .range(0, 100)
        .sliderRange(0, 100)
        .build()
    );

    // ---------- State ----------
    private final Random rng = new Random();
    private final Map<UUID, Vec3> lastPositions = new HashMap<>();
    private double targetSpeed;
    private int lastPearlScanTick = -999;
    private int tickCounter;
    private int lastErrorWarnTick = -999;
    private int lastPearlTick = -999;
    // Zaehlt, wie viele Rotation+Aktion-Paare (rotateAndRun) diesen Tick schon eingereiht wurden -
    // siehe GodmodePvP fuer die volle Erklaerung (Meteors Rotations-Klasse unterstuetzt nativ mehrere
    // ausgerichtete Aktionen pro Tick ueber clientSide=true fuer jede Nicht-Erst-Aktion; das fruehere
    // starre 1-Aktion-Mutex verhinderte unnoetig, dass z.B. ein Perlwurf und ein Heiltrank-Wurf im
    // selben Tick feuern konnten).
    private int rotationsThisTick;

    // Prioritaeten fuer rotateAndRun() - siehe GodmodePvP fuer dieselbe Begruendung. HumanPvPs Anchor-/
    // Bett-Ausfuehrung nutzt bewusst KEINE rotateAndRun()-Warteschlange (smoothLookAt() rampt die
    // Rotation stattdessen ueber mehrere Ticks menschlich hoch), daher gibt es hier keine eigene
    // Anchor-/Bett-/Crystal-Prioritaet.
    private static final int PRIORITY_PEARL = 70;
    private static final int PRIORITY_MISC = 60;
    private static final int PRIORITY_LOOK = 0;
    /** Ob in diesem Tick bereits eine "echte" Aktion (Priority > PRIORITY_LOOK) in die Rotations-Warteschlange
     *  eingegeben wurde. Siehe GodmodePvP.realActionThisTick fuer die volle Begruendung. */
    private boolean realActionThisTick;
    private boolean pendingFreeLook;
    private double pendingFreeLookYaw, pendingFreeLookPitch;
    private int sprintResetCooldown;
    private float lastSelfHpForKnockback = -1;
    private Vec3 lastSelfPos;
    private int rubberbandCooldown;
    private boolean followSuppressedUntilDecision;
    private boolean autoMendEnabledByHuman;
    private boolean autoEatEnabledByHuman;
    private boolean noFallEnabledByHuman;
    private boolean crystalAuraEnabledByHuman;
    private final Map<net.minecraft.world.item.Item, Boolean> warnedOutOfMisc = new HashMap<>();
    private String currentAction = "-";
    private boolean blocking;
    private boolean blockingSwapBack;
    private int shieldUntil;
    private int lastCrystalCount = -1;
    private int lastAnchorBlockCount = -1;
    private int lastBedBlockCount = -1;
    private int explosionRetreatUntil;
    private boolean strafeLeft;
    private int nextStrafeSwitchTick = -1;
    private int savedPlaceDelay = -1;
    private CrystalAura.SupportMode savedSupport;
    private int savedSupportDelay = -1;
    private boolean supportSyncFailed;
    private InteractionHand blockingHand = InteractionHand.MAIN_HAND;
    private InteractionHand fireResHand = InteractionHand.MAIN_HAND;
    private boolean fireResSwapBack;
    private boolean followActive;
    private UUID followedId;

    private UUID engagedId;
    private int lastRetargetCheck;
    private int secondEnemyCooldown;
    private int engageAtTick;
    private int nextClickTick = -1;
    private boolean pursuing; // sticky: einmal in Engage-Distanz gekommen, bleibt es auch nach Explosions-Knockback ueber diese Distanz hinaus (bis follow-range/Zielverlust) - sonst reisst eine Crystal-Explosion die Verfolgung mitten im Kampf ab.
    private float aimYaw, aimPitch; // virtuelle Ziel-Blickrichtung fuer Silent-Rotations (free-look) - unabhaengig von der echten Kamera
    private boolean aimInitialized;

    private int auraMode = -1;
    private int lastAuraSwitch = -999;

    private BlockPos anchorPos;
    private int anchorStage; // 0 auswaehlen/hinlaufen, 1 platziert-wartet, 2 geladen-wartet, 3 gezuendet-wartet
    private int stageDeadline;
    private int anchorCooldown;
    private final List<BlockPos> anchorCandidates = new ArrayList<>();
    private int anchorCandidateIndex;
    private BlockPos anchorCalcOrigin;
    private double bestAnchorDmgCache;

    private static final Direction[] BED_DIRECTIONS = { Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST };

    private BlockPos bedPos;
    private int bedStage; // 0 auswaehlen/hinlaufen/ausrichten/platzieren, 1 platziert-wartet, dann zuenden
    private int bedStageDeadline;
    private int bedCooldown;
    private final List<BedSpot> bedCandidates = new ArrayList<>();
    private int bedCandidateIndex;
    private BlockPos bedCalcOrigin;
    private double bestBedDmgCache;
    /** Roher (NICHT totem-abgeschlagener) Bett-Schaden - siehe GodmodePvP fuer die volle Begruendung:
     *  die absolute bed-min-damage-Schwelle muss gegen den Rohwert pruefen, sonst legt der
     *  0.3-Totem-Abschlag Bed Aura gegen jeden totem-tragenden Gegner praktisch komplett still. */
    private double bestBedRawDmgCache;

    private float hpAtHealWindowStart = -999;
    private int healWindowStartTick = -999;
    private int healPotionCooldown;
    private boolean healingUntilFull;

    private record BedSpot(BlockPos pos, Direction dir) {}

    private int lastTrapTick = -999;
    private boolean drinkingFireRes;
    /** true, sobald onActivate() vollstaendig durchlief; siehe onDeactivate(). */
    private boolean lifecycleStarted;
    private int fireResStartTick = -999;

    // ---------- Verdrahtung der getesteten Helfer ----------

    /** Gitter-Basis der gesendeten Rotationen. Meteor quantisiert nicht, Grim rechnet den ggT der
     *  gesendeten Deltas - also muss der Addon das selbst tun. Geseedet wird aus dem Wert, den
     *  smoothLookAt() ERSTELLT hat, nicht aus der Kamera: das Profil rampiert, der Akkumulator muss
     *  also die Rampe als Vorwerts sehen, sonst quantisiert er jeden Tick gegen eine falsche Basis. */
    private final GcdRotator.AngleDeltaAccumulator rotationAccumulator = new GcdRotator.AngleDeltaAccumulator();

    /** Zuletzt tatsaechlich an Rotations.rotate() uebergebene Winkel — die Validierung am Nahkampf
     *  prueft gegen DAS und nicht gegen die Kamera (siehe ActionRayValidator, "Warum die Kamera
     *  nicht zaehlt"). null, sobald dieser Tick noch nichts eingereiht wurde. */
    private GcdRotator.Rotation lastSentRotation;

    /** Eine Aktion pro Bewegungspaket (Grim MultiActionsA / DuplicateSwing / PacketOrderE). */
    private final ActionCadence cadence = new ActionCadence();

    /** Drosselt die Aktionsrate, sobald der Client-Tick laenger als nominell dauert. */
    private final TickRateGate tickGate = new TickRateGate();
    private TickRateGate.Verdict tickVerdict = TickRateGate.Verdict.RUN;
    private int actionIndex;
    private long lastTickNanos;

    /** Welche End Crystals dieses Modul gesetzt hat — filtert sie aus der Auto-Schield-Reaktion
     *  heraus (Setting {@link #crystalOwnership}). Endet mit 32 Eintraegen / 200 Ticks, damit die
     *  Liste nicht ueber eine lange Sitzung hinweg leer laeuft. */
    private final CrystalOwnership ownCrystals = new CrystalOwnership();

    /** Geteilter Block-Raycast fuer Perlenbahn, Sichtlinien und Explosions-Vorpruefung. Enthaelt den
     *  Cache, weshalb derselbe Strahl im Kampf nicht zweimal durch die Welt geht. */
    private final ExplosionScanner scanner = new ExplosionScanner();

    /**
     * Sichtpruefung fuer ActionRayValidator — die Regel selbst ist kopflos, nur die Welt kommt herein.
     *
     *  <p>{@code isCollisionShapeFullBlock} statt des veralteten {@code blocksMotion()}: es ist genau
     *  die Eigenschaft, an der Vanillas COLLIDER-ClipContext haengt. Damit kann der Validator nicht
     *  "verdeckt" melden, wo der echte Raycast durchkommt (Truhen, Tueren, Gelander zaehlen nicht als
     *  voll — ein Schlag durch einen offenen Rahmen ist kein Occlusion-Fehler).
     */
    private final ActionRayValidator.World rayWorld = pos -> mc.level != null
        && mc.level.getBlockState(pos).isCollisionShapeFullBlock(mc.level, pos);

    /** Der Dispatcher bestellt nur den Animationsteil — das Schadenspaket kommt weiter aus
     *  {@code gameMode.attack()}, weil nur das den Angriffs-Cooldown zuruecksetzt und die
     *  Sweep-Reichweitenpruefung aufloest. {@code SwingMode.CLIENT} entspricht dem vorherigen
     *  {@code player.swing()}; ein zusaetzliches Swing-Paket waere eine Aenderung des Protokollbilds,
     *  kein Fix. Als Feld, damit nicht pro Schlag ein neuer Dispatcher entsteht. */
    private final AttackDispatcher attacker = new AttackDispatcher((packet, argument) -> { }, hand -> {
        if (mc.player != null) mc.player.swing(hand == AttackDispatcher.OFF_HAND
            ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
    });

    /** Wiederverwendete Menge fuer den Crystal-Abgleich — pro Tick neu zu allozieren waere die
     *  einzige Daueralokation, die dieser Tick ansonsten nicht braucht. */
    private final Set<Integer> visibleCrystalIds = new HashSet<>();

    public HumanPvP() {
        super(com.provipvp.ProviPvPAddon.CATEGORY, "human-pvp", "ProviPvP V2: menschlich wirkender Kampf-Bot (Reaktionszeit, sichtbare Rotation, Klick-Jitter). Kein Unerkennbarkeits-Versprechen. Befehl: .hpvp");
    }

    /**
     * Erzwingt die Profil-Exklusivitaet schon im Toggle-Pfad. Meteor ueberspringt bei
     * {@code Utils.canUpdate()==false} (Hauptmenue, Welt laedt) sowohl das Event-Bus-Subscribe als
     * auch {@link #onActivate()} — dann greift auch der {@code onTick}-Waechter nicht, weil er genau
     * die uebersprungene Subscription braucht. Der Toggle-Pfad laeuft immer.
     */
    @Override
    public void toggle() {
        if (!isActive()) com.provipvp.ProviPvPAddon.enforceExclusiveProfile(HumanPvP.class);
        super.toggle();
    }

    /**
     * Der gemeinsame Zustands-Reset fuer onActivate() und onDeactivate().
     *
     * <p><b>Warum ueberhaupt extrahiert:</b> das Modul ist ein Singleton, sein Zustand lebt also
     * ueber Aktivierungssessions hinweg weiter. Zwei getrennt geschriebene Reset-Listen driften
     * unausweichlich auseinander — onDeactivate() raeumte nur einen Teil dessen ab, was
     * onActivate() gesetzt hatte, also blieb nach jedem Deaktivieren Engagement-, Aura- oder
     * Heilverlauf-Zustand des letzten Kampfes stehen und wurde beim naechsten Aktivieren
     * uebernommen. Eine Liste, zwei Aufrufer, kann nicht driften.
     */
    private void resetCombatState() {
        lastPearlScanTick = -999;
        tickCounter = 0;
        lastPearlTick = -999;
        supportSyncFailed = false;
        lastErrorWarnTick = -999;
        drinkingFireRes = false;
        fireResStartTick = -999;
        sprintResetCooldown = 0;
        lastSelfHpForKnockback = -1;
        lastSelfPos = null;
        rubberbandCooldown = 0;
        followSuppressedUntilDecision = false;
        blockingSwapBack = false;
        blocking = false;
        shieldUntil = 0;
        lastCrystalCount = -1;
        lastAnchorBlockCount = -1;
        lastBedBlockCount = -1;
        explosionRetreatUntil = -1;
        nextStrafeSwitchTick = -1;
        strafeLeft = false;
        followActive = false;
        followedId = null;
        autoMendEnabledByHuman = false;
        autoEatEnabledByHuman = false;
        noFallEnabledByHuman = false;
        crystalAuraEnabledByHuman = false;
        blockingHand = InteractionHand.MAIN_HAND;
        fireResHand = InteractionHand.MAIN_HAND;
        fireResSwapBack = false;
        lastRetargetCheck = 0;
        secondEnemyCooldown = 0;
        engagedId = null;
        engageAtTick = 0;
        pursuing = false;
        targetSpeed = 0;
        nextClickTick = -1;
        auraMode = -1;
        lastAuraSwitch = -999;
        anchorPos = null;
        anchorStage = 0;
        anchorCooldown = 0;
        anchorCandidates.clear();
        anchorCandidateIndex = 0;
        anchorCalcOrigin = null;
        bestAnchorDmgCache = 0;
        bedPos = null;
        bedStage = 0;
        bedStageDeadline = 0;
        bedCooldown = 0;
        bedCandidates.clear();
        bedCandidateIndex = 0;
        bedCalcOrigin = null;
        bestBedDmgCache = 0;
        bestBedRawDmgCache = 0;
        hpAtHealWindowStart = -999;
        healWindowStartTick = -999;
        healPotionCooldown = 0;
        healingUntilFull = false;
        lastTrapTick = -999;
        lastPositions.clear();
        currentAction = "-";

        // Die stillen Helfer: Gitter-Basis, Ledger und Raycast-Cache sind Welt- und Session-bezogen,
        // ein Restart ohne Reset hiesse "letzte Rotation der letzten Sitzung" als GCD-Vorwert.
        rotationAccumulator.reset();
        lastSentRotation = null;
        cadence.onTick();
        actionIndex = 0;
        lastTickNanos = 0L;
        tickVerdict = TickRateGate.Verdict.RUN;
        ownCrystals.reset();
        scanner.invalidate();
    }

    @Override
    public void onActivate() {
        // Beide PvP-Profile verwalten dieselben globalen Meteor-/Baritone-Ressourcen. Beim
        // Aktivieren wird das andere Profil daher sofort und rueckstandsfrei deaktiviert.
        GodmodePvP godmode = Modules.get().get(GodmodePvP.class);
        resetCombatState();

        Modules m = Modules.get();

        CrystalAura ca = m.get(CrystalAura.class);
        if (ca != null) {
            savedPlaceDelay = ca.placeDelay.get();
            ca.placeDelay.set(3 + rng.nextInt(4)); // 3-6 Ticks - menschliche Klickgeschwindigkeit statt Sofort-Reaktion
            syncSupport(ca); // ohne das kann CrystalAura nur auf bereits vorhandenem Obsidian platzieren -> quasi nie
        }

        // Baritone bewusst konservativ: keine Bruecken-Sprung-Perfektion, kein 250-Block-Sturz-Kalkuel.
        // Unabhaengig davon, was V1 (GodmodePvP) global gesetzt hat.
        var bs = BaritoneAPI.getSettings();
        bs.allowDownward.value = true;
        bs.allowParkour.value = true;
        bs.allowParkourAscend.value = true;
        bs.allowParkourPlace.value = false;
        bs.sprintAscends.value = true;
        bs.allowSprint.value = true;
        bs.jumpPenalty.value = 2.0;
        bs.maxFallHeightNoWater.value = 15; // genug fuer normales Gelaende; Fallschaden wird ueber escape-pearl abgefangen, kein eigener Fallschaden-Hack
        bs.followRadius.value = 3; // Baritone haelt/regelt selbst diesen Abstand - kontinuierlich statt hart cancel+neu
        // Ein Kampf-Bot soll NIE mitten im Gefecht in ein zufaellig auf dem Pfad liegendes Nether-Portal
        // spazieren, nur weil Baritones Pfadsuche es als kuerzeste Route ansieht. enterPortal=false
        // verhindert nur das ABSICHTLICHE Ziel "geh in dieses Portal" - blocksToAvoid zwingt Baritone,
        // aktiv drumherum zu routen statt nur nicht direkt hineinzulaufen.
        bs.enterPortal.value = false;
        bs.blocksToAvoid.value = new java.util.ArrayList<>(java.util.List.of(net.minecraft.world.level.block.Blocks.NETHER_PORTAL));

        if (autoMendOn.get()) autoMendEnabledByHuman = safeEnable(m, AutoMend.class);
        if (autoEatOn.get()) autoEatEnabledByHuman = safeEnable(m, AutoEat.class);
        if (noFallOn.get()) noFallEnabledByHuman = safeEnable(m, NoFall.class);

        // KEIN EVENT_BUS.subscribe(this): Meteor abonniert in Module.toggle() bereits selbst.
        // Ein zweites Abonnieren laesst jeden @EventHandler zweimal pro Event laufen.

        info("ProviPvP V2 (human) aktiv. Rechtsklick auf das Modul im Meteor-Menue zum Keybind. Befehl: .hpvp");
        lifecycleStarted = true;
    }

    /**
     * Meteor kann {@code onDeactivate()} ohne vorheriges {@code onActivate()} aufrufen (wurden beide
     * Profile im Hauptmenue eingeschaltet, ueberspringt toggle() das Aktivieren). Der Aufraeumlauf
     * wuerde dann fremde Zustaende zerstoeren — siehe GodmodePvP fuer die ausfuehrliche Begruendung.
     */
    @Override
    public void onDeactivate() {
        if (!lifecycleStarted) return;
        lifecycleStarted = false;
        MeteorClient.EVENT_BUS.unsubscribe(this);

        Modules m = Modules.get();
        if (crystalAuraEnabledByHuman) safeDisable(m, CrystalAura.class);
        if (autoMendEnabledByHuman) safeDisable(m, AutoMend.class);
        if (autoEatEnabledByHuman) safeDisable(m, AutoEat.class);
        if (noFallEnabledByHuman) safeDisable(m, NoFall.class);
        CrystalAura ca = m.get(CrystalAura.class);
        if (ca != null && savedPlaceDelay >= 0) ca.placeDelay.set(savedPlaceDelay);
        if (ca != null) restoreSupport(ca);
        savedPlaceDelay = -1;

        cancelFollow();

        // Die Swap-Ruecksetzung braucht mc.player — beim Weltwechsel ist es schon null, und genau
        // dort wird am haeufigsten deaktiviert (onGameLeft).
        if (mc.player != null) {
            if (blocking && blockingSwapBack) InvUtils.swapBack();
            if (drinkingFireRes && fireResSwapBack) InvUtils.swapBack();
        }

        warnedOutOfMisc.clear();
        Input.setKeyState(mc.options.keySprint, false);
        // Auch die Bewegungstasten: die Retreat-Logik setzt keyLeft/keyRight/keyDown direkt. Ohne
        // diesen Reset bleiben sie nach dem Deaktivieren clientseitig gedrueckt und der Spieler
        // laeuft dauerhaft seitwaerts bzw. rueckwaerts weiter.
        Input.setKeyState(mc.options.keyLeft, false);
        Input.setKeyState(mc.options.keyRight, false);
        Input.setKeyState(mc.options.keyDown, false);
        Input.setKeyState(mc.options.keyUp, false);
        if (mc.player != null) mc.player.setSprinting(false);

        // Symmetrisch zu onActivate(): alles, was der Kampf zurueckgelassen hat (Engagement, Aura-
        // Modus, Anker-/Bett-Stufe, Heilverlauf, Gitter-Basis, Ledger) geht hier runter — sonst
        // startet die naechste Sitzung mit dem Zustand des letzten Kampfes.
        resetCombatState();

        info("ProviPvP V2 aus.");
    }

    @EventHandler
    public void onTick(TickEvent.Pre event) {
        if (!Utils.canUpdate()) return;

        tickCounter++;
        // Ledger-Tick-Grenze VOR doTick(): eine Aktion, die dieser Tick bucht, muss im selben Tick
        // auch wieder abgerechnet werden, sonst zaehlt sie doppelt.
        cadence.onTick();
        ownCrystals.advance(1);
        // Eigene Rotationen duerfen das Delta des Vortages nicht erben — der Akkumulator seedet neu.
        lastSentRotation = null;
        sampleTickRate();

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

    /** Server-Wechsel/Disconnect: nichts (CrystalAura, ...) darf ueber die Weltgrenze hinaus aktiv
     *  bleiben - sonst laufen fremde Meteor-Module beim naechsten Join in einem undefinierten
     *  Zustand (z.B. mc.player kurzzeitig null) mit und koennen den Client abstuerzen lassen. */
    @EventHandler
    public void onGameLeft(GameLeftEvent event) {
        if (isActive()) toggle();
    }

    /**
     * Merkt sich End Crystals, die das von uns aktivierte Aura gesetzt hat.
     *
     * <p><b>Warum ueberhaupt:</b> HumanPvP setzt keine Crystals selbst — Meteors CrystalAura tut das.
     * Der Client sieht die Platzierung aber vorhergesagt als Entity-Appear, und solange das Aura von
     * uns stammt und in Reichweite steht, ist genau das die Erklaerung fuer einen neu auftauchenden
     * Crystal. Ohne diese Buchhaltung zaehlt der Bot seinen EIGENEN Crystal bei jedem Platzieren als
     * "frischen Gegner-Crystal" und blockt reflexhaft dagegen.
     *
     * <p>Grenze der Heuristik, bewusst so belassen: ein Gegner, der im selben Tick durch denselben
     * Client-Pfad einen Crystal in Reichweite setzt, wird als eigener gewertet. Die Fehlentscheidung
     * faellt gegen den Bot aus (ein ausgelassener Auto-Schild), nicht gegen uns.
     */
    @EventHandler
    public void onEntityAdded(EntityAddedEvent event) {
        if (!crystalOwnership.get() || mc.player == null) return;
        if (!(event.entity instanceof EndCrystal crystal)) return;
        // Nur was unser Aura auch erreichen wuerde — weiter entfernte Crystals sind Fremdbestand.
        if (crystal.distanceToSqr(mc.player) > 64.0) return;
        ownCrystals.notePlaced(crystal.getId());
    }

    private void doTick() {
        Player self = mc.player;
        // C10: containerMenu wurde unguarded gelesen. Das faellt beim Disconnect auf — dort ist
        // mc.player bereits null, aber onGameLeft tickt nicht mehr. Der Wirt entscheidet heute ueber
        // Utils.canUpdate(), aber eine eigene Zustaendigkeit im Body waere eine stille Pflicht.
        if (self == null || mc.level == null) return;
        currentAction = "-";
        rotationsThisTick = 0;
        realActionThisTick = false;
        pendingFreeLook = false;
        boolean guiOpen = mc.gui.screen() != null;
        // Nur eine echte Fremd-Container-GUI hat ein anderes containerMenu als das normale Inventar -
        // Meteor-ClickGUI/eigenes Inventar teilen sich inventoryMenu, Totem-Nachlegen darf da weiterlaufen.
        boolean foreignContainerOpen = self.containerMenu != null && self.containerMenu != self.inventoryMenu;
        // Der Raycast-Cache haengt an Welt und Chunk-Kontext: ohne dieses markTick liefert er nach
        // einem Chunkwechsel oder einem Weltwechsel noch "frei" fuer Bloecke, die es nicht mehr gibt.
        scanner.markTick(tickCounter);
        // Kein Verzoegerungs-Gate mehr: nach einem Totem-Pop ist die Offhand fuer die naechste(n)
        // Angriffswelle sofort ungeschuetzt - bei zwei schnellen Treffern hintereinander (2v1, oder ein
        // Gegner der einfach schnell genug klickt) reicht selbst 1-3 Tick Verzoegerung, um den Bot
        // trotz Totem-Vorrat echt sterben zu lassen (im Live-Test reproduziert). GodmodePvP prueft schon
        // jeden Tick ohne Gate - hier genauso, "menschlicher wirken" ist es nicht wert, dafuer wirklich
        // zu sterben.
        if (fastTotem.get() && !foreignContainerOpen) ensureOffhandTotem();
        if (!foreignContainerOpen) maintainHealPotions(self);
        maintainFireResistance();

        if (!guiOpen) {
            if (invManager.get() && tickCounter % 20 == 0) inventoryTick();
            if (tickCounter % 20 == 0) {
                CrystalAura ca = Modules.get().get(CrystalAura.class);
                if (ca != null) syncSupportDelay(ca);
            }
        }

        if (blocking) {
            if (tickCounter < shieldUntil) {
                mc.gameMode.useItem(mc.player, blockingHand);
                currentAction = "schild-block";
                return;
            }
            stopBlock();
        }

        LivingEntity target = findTarget(self);
        if (target == null) {
            resetEngagement();
            cancelFollow();
            currentAction = "beobachten";
            return;
        }

        // Periodische Neubewertung statt stur am einmal gewaehlten Ziel festzuhalten - smartTargeting
        // wirkte bisher nur bei der ERSTEN Zielwahl (siehe GodmodePvP fuer dieselbe Ergaenzung/Begruendung).
        if (smartTargeting.get() && target instanceof Player && tickCounter - lastRetargetCheck > 40) {
            lastRetargetCheck = tickCounter;
            Player alt = findPlayerTarget(self);
            if (alt != null && alt.isAlive() && !alt.getUUID().equals(target.getUUID())) {
                target = alt;
            }
        }

        // Echte 2v1-Erkennung: ein zweiter, nicht-anvisierter Spieler nah dran UND nicht auf voller
        // Gesundheit - dieselbe gestufte Reaktion wie oben (Escape-Pearl wenn bereit, sonst kurzer
        // Rueckzugsimpuls), statt einfach normal weiterzukaempfen und auf den zweiten Treffer zu warten.
        if (secondEnemyCooldown > 0) {
            secondEnemyCooldown--;
        } else {
            for (Player p : mc.level.players()) {
                if (p == self || p == target || !p.isAlive() || p.isSpectator()) continue;
                if (p.isCreative() && !(p instanceof FakePlayerEntity)) continue;
                if (self.distanceToSqr(p) > 12.0 * 12.0) continue;

                secondEnemyCooldown = 100;
                if (self.getHealth() <= 14.0f) {
                    explosionRetreatUntil = Math.max(explosionRetreatUntil, tickCounter + 15);
                }
                break;
            }
        }
        if (!target.getUUID().equals(engagedId)) {
            engagedId = target.getUUID();
            // Reaktionszeit als echtes Min/Max-Intervall: reactionMin/reactionMax sind bereits
            // einstellbar, aber die Spannweite wurde mit einem selbst gebauten nextInt gebildet.
            // RandomBetween uebernimmt die Normalisierung (vertauschte Grenzen) mit, also kann der
            // Nutzer die beiden Werte in beliebiger Reihenfolge eintragen, ohne den Kampf stillzulegen.
            engageAtTick = tickCounter + reactionRange().sample(rng);
            nextClickTick = -1;
            pursuing = false; // neues Ziel -> Kaltstart-Schwelle (engage-distance) gilt wieder von vorn
        }

        updateTracking(target);
        double dist = Math.sqrt(self.distanceToSqr(target));
        if (dist <= engageDistance.get()) pursuing = true;

        boolean tookHit = lastSelfHpForKnockback >= 0 && self.getHealth() < lastSelfHpForKnockback - 1.0f;
        lastSelfHpForKnockback = self.getHealth();

        // Anti-Rubberband: ploetzlicher, nicht selbst verursachter Sprung -> Server hat uns korrigiert.
        // Ein moderater Sprung zaehlt nur ausserhalb des Kampfes (sonst wird normaler Explosions-Knockback
        // faelschlich als Rubberband gewertet); ein wirklich extremer Sprung greift immer, auch waehrend
        // des Kampfes - genau dort tritt Rubberbanding laut Beobachtung am haeufigsten auf.
        double selfMoved = lastSelfPos == null ? 999 : self.position().distanceTo(lastSelfPos);
        lastSelfPos = self.position();
        if (rubberbandCooldown > 0) {
            rubberbandCooldown--;
        } else if (antiRubberband.get() && tickCounter - lastPearlTick > 10
            && (selfMoved > 6.0 || (selfMoved > 2.5 && !tookHit))) {
            cancelFollow();
            followSuppressedUntilDecision = true;
            rubberbandCooldown = 10;
            currentAction = "rubberband";
        }
        boolean launchedByHit = tookHit && self.getDeltaMovement().y > 0.35;
        boolean fallingDanger = !self.onGround() && self.fallDistance > 3.0f && self.getDeltaMovement().y < 0.05;
        if (knockbackPearl.get() && (launchedByHit || fallingDanger)
            && tickCounter - lastPearlTick > 15 && InvHelper.has(Items.ENDER_PEARL)) {
            if (throwPearlDown()) {
                currentAction = launchedByHit ? "pearl-knockback" : "pearl-fallschutz";
                return;
            }
        }
        manageSprintForKnockback(dist);

        // Gestufte Notfall-Eskalation statt eines Einzel-Triggers:
        // (a) mittlere Gefahr (HP <=14, Gegner nah) - physisch Abstand halten statt weiter reinzulaufen.
        // (b) kritisches HP + Perle bereit - Escape-Pearl (bestehend).
        // (c) kritisches HP, aber KEINE Perle verfuegbar/auf Cooldown - Schild+Rueckzug als Fallback,
        //     statt schutzlos weiterzukaempfen bis irgendwann doch eine Perle da ist.
        boolean pearlReady = tickCounter - lastPearlTick > 30
            && (InvHelper.has(Items.ENDER_PEARL));
        if (escapePearl.get() && self.getHealth() <= 8.0f && dist <= 6.0) {
            if (pearlReady) {
                if (throwPearlAwayFrom(target)) {
                    currentAction = "escape-pearl";
                    return;
                }
            }
            if (autoShield.get() && !blocking) {
                // +5, weil der Schild die ersten 5 Ticks des Item-Use noch gar nicht blockt.
                shieldUntil = ShieldWindow.until(tickCounter, ShieldWindow.EMERGENCY_HELD_TICKS);
                startBlock();
            }
            explosionRetreatUntil = tickCounter + 20;
            currentAction = "notfall-schild-rueckzug";
            return;
        }
        if (self.getHealth() <= 14.0f && dist <= 4.0) {
            // Stufe (a): noch nicht kritisch, aber genug Gefahr, um lieber kurz Abstand zu halten statt
            // den naechsten Treffer aktiv zu suchen - updateCombatMovement() (unten) nutzt dasselbe
            // explosionRetreatUntil-Fenster fuer den physischen Rueckzugsschritt.
            explosionRetreatUntil = Math.max(explosionRetreatUntil, tickCounter + 6);
        }

        // Feindlicher Crystal frisch platziert (in 5 m)? -> kurz blocken. Anchor/Bett sind Bloecke statt
        // Entities, gleiche Delta-Idee per Block-Scan (siehe GodmodePvP fuer dieselbe Erweiterung).
        // Gezählt wird nur, was NICHT uns gehört: das eigene Aura setzt permanent Crystals, und ohne
        // diesen Filter löst jede eigene Platzierung den Auto-Schield aus — der Bot blockt dann im
        // Crystal-Kampf reflexhaft gegen seinen eigenen Crystal und verpasst den des Gegners.
        List<EndCrystal> nearCrystals =
            mc.level.getEntitiesOfClass(EndCrystal.class, self.getBoundingBox().inflate(5));
        forgetVanishedCrystals(nearCrystals);
        int enemyCrystals = 0;
        for (EndCrystal crystal : nearCrystals) {
            if (!crystalOwnership.get() || !ownCrystals.owns(crystal.getId())) enemyCrystals++;
        }
        boolean freshCrystal = lastCrystalCount >= 0 && enemyCrystals > lastCrystalCount;
        int anchorBlocks = countNearbyBlocks(self.blockPosition(), 5, st -> st.is(Blocks.RESPAWN_ANCHOR));
        int bedBlocks = countNearbyBlocks(self.blockPosition(), 5, st -> st.getBlock() instanceof BedBlock);
        boolean freshAnchor = lastAnchorBlockCount >= 0 && anchorBlocks > lastAnchorBlockCount;
        boolean freshBed = lastBedBlockCount >= 0 && bedBlocks > lastBedBlockCount;

        if (freshCrystal || freshAnchor || freshBed) {
            explosionRetreatUntil = tickCounter + 12; // fuer updateCombatMovement() unten - physischer Rueckzug
            if (autoShield.get() && !blocking) {
                // 15+5 ergibt 10 Ticks echten Schutz statt 15 Ticks "ich halte ein Schild".
                shieldUntil = ShieldWindow.until(tickCounter, ShieldWindow.FRESH_EXPLOSION_HELD_TICKS);
                startBlock();
            }
        }
        lastCrystalCount = enemyCrystals;
        lastAnchorBlockCount = anchorBlocks;
        lastBedBlockCount = bedBlocks;

        Vec3 aim = aimPoint(target);
        smoothLookAt(aim);
        if (meleeStrafe.get()) updateCombatMovement(target, dist);

        // Verfolgen: siehe GodmodePvP fuer dieselbe Logik/Begruendung. Waehrend eines aktiven
        // Rueckzugsschritts pausiert, damit Baritones FollowProcess (haelt staendig followRadius zum
        // Ziel) nicht sofort gegen die manuelle Rueckwaerts-Taste anpathet.
        if (tickCounter < explosionRetreatUntil) {
            cancelFollow();
            currentAction = "rueckzugsschritt";
        } else if (followSuppressedUntilDecision) {
            cancelFollow();
            if (rubberbandCooldown <= 0) followSuppressedUntilDecision = false;
        } else if (follow.get() && pursuing) {
            updateFollow(target);
        } else {
            cancelFollow();
        }

        if (tickCounter < engageAtTick) {
            currentAction = "reagieren";
            return;
        }

        handleTrap(target);

        // Die Perlenbahn wird gegen die aktuelle Umgebung geprueft; ein straight-line LOS-Gate
        // wuerde Lochrand und kurze Kanten faelschlich als "nicht werfbar" behandeln.
        double pearlReachThreshold = Math.max(pearlMinDist.get(), attackRange.get() + 0.5);
        if (pearlThrow.get() && dist > pearlReachThreshold && pursuing
            && tickCounter - lastPearlTick > pearlCooldown(dist) && !guiOpen
            && throwPearlAtTarget(target)) {
            currentAction = "pearl-gapclose";
        }

        selectAura(target);

        if (auraMode == 1) {
            runAnchorTick(target);
            currentAction = "anchor";
        } else if (auraMode == 2) {
            runBedTick(target);
            currentAction = "bed";
        }

        if (shieldBreaker.get() && target instanceof Player p && p.isBlocking()) {
            if (breakShield(p)) currentAction = "schild-brechen";
        } else if (dist <= attackRange.get() && currentAimError(aim) <= aimTolerance.get() && self.hasLineOfSight(target)
            // Frisch NACH selectAura()/runAnchorTick()/runBedTick() neu berechnet statt eines am
            // Tick-Anfang zwischengespeicherten Werts: die drei rufen ihrerseits smoothLookAt() auf
            // einen ANDEREN Punkt (Anchor-/Bett-Platzierungsstelle) auf und ueberschreiben damit die
            // tatsaechliche Rotation dieses Ticks. Der alte, gecachte aimError haette hier weiter "ja,
            // aufs Ziel ausgerichtet" gesagt, obwohl der Bot gerade real zu einem Block daneben schaut -
            // ein Melee-Schlag mit nicht passender Blickrichtung ist ein klassisches Rotation-Anti-Cheat-
            // Flag-Muster (Grim/Vulcan).
            // AttackGate D9 ersetzt den festen >=0.95-Vergleich durch das benannte Cooldown-Gate; die
            // 0.95 bleiben als bewusste Humanisierung dieses Profils — AttackGate.FULL_STRENGTH (1.0)
            // waere hier die maschinelle Variante. D7/D10 bleiben bewusst aussen, siehe attackGateAllows.
            && attackGateAllows()
            // Die Reihenfolge zählt: Reichweite/Sicht zuerst, weil sie nichts verbrauchen. readyToClick()
            // wuerfelt den Klickversatz und verbraucht attack-chance — faellt es danach, ist die
            // Chance fuer diesen bereiten Schlag verbraucht, ohne dass geschlagen wurde.
            && meleeRayAllows(target)
            && tickGate.allows(tickVerdict, actionIndex++)
            && readyToClick()) {
            if (attackMelee(target)) currentAction = "schlagen";
        }

        if (currentAction.equals("-")) currentAction = auraMode == 0 ? "crystal" : "zielen";

        // Cosmetic Ziel-Verfolgung: laeuft am Tick-Ende mit der niedrigsten Prioritaet - ABER NUR, wenn
        // diesen Tick noch keine echte Aktion (Perle, Nahkampf, Anchor/Bett) die Rotation schon
        // beansprucht hat. rotationsThisTick allein reicht nicht, weil der Callback asynchron laeuft.
        // Wir nutzen realActionThisTick (siehe rotateAndRun), das nur bei Priority > PRIORITY_LOOK gesetzt wird.
        if (pendingFreeLook && !realActionThisTick) {
            rotateAndRun(pendingFreeLookYaw, pendingFreeLookPitch, PRIORITY_LOOK, null);
        }
    }

    @Override
    public String getInfoString() {
        return supportSyncFailed ? currentAction + " §c[kein Obsidian-Support!]" : currentAction;
    }

    private long pearlCooldown(double dist) {
        return dist > 15 ? 25 : 40; // spuerbar zurueckhaltender als V1
    }

    private void resetEngagement() {
        engagedId = null;
        engageAtTick = 0;
        nextClickTick = -1;
        // anchorStage wird hier BEWUSST NICHT zurueckgesetzt (anders als in einer frueheren Version):
        // ein kurzer Ziel-Verlust/-Wechsel wuerde sonst einen bereits platzierten und teilweise
        // geladenen Anchor komplett verwaisen lassen (Investition futsch, nie gezuendet) - exakt das
        // gleiche "begonnene Aktion zu Ende bringen"-Verhalten wie beim Bett (bedStage bleibt hier
        // ebenfalls unangetastet). runAnchorTick() pausiert einfach ohne Ziel und macht beim naechsten
        // Ziel an der gleichen Stelle weiter.
        pursuing = false;
        aimInitialized = false;
    }

    /** Die Reaktionsspanne als Wertobjekt — RandomBetween normalisiert selbst, ein vertauschtes
     *  Paar aus den beiden Settings darf den Kampf nicht stilllegen. */
    private RandomBetween.RandomBetweenInt reactionRange() {
        return new RandomBetween.RandomBetweenInt(reactionMinTicks.get(), reactionMaxTicks.get());
    }

    /** Der Klickversatz als Wertobjekt; die Untergrenze ist immer 1 Tick, damit ein bereiter Schlag
     *  nie im selben Tick wie die letzte Aktion herausfaellt. */
    private RandomBetween.RandomBetweenInt clickDelayRange() {
        return new RandomBetween.RandomBetweenInt(1, clickDelayMax.get());
    }

    private boolean readyToClick() {
        if (nextClickTick < 0) {
            // Vor dem festen nextInt(3): der Bereich war nicht einstellbar, und genau dieser feste
            // Versatz ist die regelmaessigste Spur im ganzen Profil — der Schlagtakt lag exakt im
            // Cooldown-Raster. Jetzt 1..click-delay-max.
            nextClickTick = tickCounter + clickDelayRange().sample(rng);
            return false;
        }
        if (tickCounter < nextClickTick) return false;
        nextClickTick = -1;
        return rng.nextDouble() < attackChance.get();
    }

    // ---------- Rotation (sichtbar, tempolimitiert - kein Silent-Snap) ----------

    private Vec3 aimPoint(LivingEntity target) {
        Vec3 base = target.getBoundingBox().getCenter();
        double jitter = 0.06;
        return base.add((rng.nextDouble() - 0.5) * jitter, (rng.nextDouble() - 0.5) * jitter, (rng.nextDouble() - 0.5) * jitter);
    }

    private static float wrapDelta(float delta) {
        return PvpMath.wrapDelta(delta);
    }

    /** Ease-out statt linearer Marschgeschwindigkeit + hartem Stopp bei Erreichen: die Drehung wird
     *  langsamer, je naeher sie am Ziel ist - wie eine echte Mausbewegung. maxTurn deckelt nur
     *  grosse Korrekturen (z.B. frischer Zielwechsel), sonst bestimmt der Rest-Winkel das Tempo. */
    private void smoothLookAt(Vec3 point) {
        float targetYaw = (float) Rotations.getYaw(point);
        float targetPitch = (float) Rotations.getPitch(point);

        float curYaw = effectiveAimYaw();
        float curPitch = effectiveAimPitch();

        float yawDelta = wrapDelta(targetYaw - curYaw);
        float pitchDelta = targetPitch - curPitch;

        float maxTurn = (float) (double) maxTurnPerTick.get();
        float smoothing = (float) (0.28 + rng.nextDouble() * 0.14); // 0.28-0.42 Anteil des Restwinkels pro Tick

        float appliedYaw = clampAbs(yawDelta * smoothing, maxTurn);
        float appliedPitch = clampAbs(pitchDelta * smoothing, maxTurn);

        float newYaw = curYaw + appliedYaw;
        float newPitch = Math.max(-90f, Math.min(90f, curPitch + appliedPitch));

        if (freeLook.get()) {
            // Silent: nur das Server-Paket bekommt die Blickrichtung, die eigene Kamera bleibt frei.
            aimYaw = newYaw;
            aimPitch = newPitch;
            aimInitialized = true;
            pendingFreeLook = true;
            pendingFreeLookYaw = aimYaw;
            pendingFreeLookPitch = aimPitch;
        } else {
            aimInitialized = false;
            mc.player.setYRot(newYaw);
            mc.player.setXRot(newPitch);
        }
    }

    /** Aktuell wirksame Ziel-Blickrichtung: die virtuelle Silent-Aim-Richtung bei free-look, sonst die
     *  echte Kamera-Rotation. Muss synchron mit smoothLookAt() sein, sonst denkt currentAimError()
     *  bei free-look permanent, es sei nicht ausgerichtet (Kamera zeigt ja absichtlich woanders hin). */
    private float effectiveAimYaw() {
        return freeLook.get() && aimInitialized ? aimYaw : mc.player.getYRot();
    }

    private float effectiveAimPitch() {
        return freeLook.get() && aimInitialized ? aimPitch : mc.player.getXRot();
    }

    private static float clampAbs(float v, float max) {
        return PvpMath.clampAbs(v, max);
    }

    private double currentAimError(Vec3 point) {
        float targetYaw = (float) Rotations.getYaw(point);
        float targetPitch = (float) Rotations.getPitch(point);
        double yawErr = Math.abs(wrapDelta(targetYaw - effectiveAimYaw()));
        double pitchErr = Math.abs(targetPitch - effectiveAimPitch());
        return Math.max(yawErr, pitchErr);
    }


    /** Schild in die Haupthand (Offhand bleibt frei fuer den Totem) und blocken - reduziert Explosionsschaden. */
    private void startBlock() {
        if (drinkingFireRes) return; // Feuerresistenz-Trank haelt gerade den gemeinsamen Swap-Merkposten - nicht ueberschreiben
        FindItemResult shield = InvHelper.find(Items.SHIELD);
        if (!shield.found() || (!shield.isHotbar() && !shield.isOffhand())) return;

        boolean swapped = false;
        if (!shield.isOffhand() && !shield.isMainHand()) {
            swapped = InvUtils.swap(shield.slot(), true);
            if (!swapped) return;
        }
        blockingHand = shield.isOffhand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        mc.gameMode.useItem(mc.player, blockingHand);
        blocking = true;
        blockingSwapBack = swapped;
    }

    private void stopBlock() {
        if (blockingSwapBack) InvUtils.swapBack();
        blockingSwapBack = false;
        blocking = false;
    }

    private void ensureOffhandTotem() {
        ItemStack off = mc.player.getItemInHand(InteractionHand.OFF_HAND);
        if (off.is(Items.TOTEM_OF_UNDYING)) return;

        FindItemResult totem = InvUtils.find(Items.TOTEM_OF_UNDYING);
        if (totem.found()) {
            InvUtils.move().from(totem.slot()).toOffhand();
        }
    }

    // ---------- Anchor-Executor (humanisiert: sichtbare Rotation + zufaellige Wartezeiten) ----------

    private void runAnchorTick(LivingEntity target) {
        if (drinkingFireRes) return; // s.o. - Swap-Merkposten waehrend des Trinkens nicht anfassen
        if (anchorCooldown > 0) {
            anchorCooldown--;
            return;
        }

        switch (anchorStage) {
            case 0 -> {
                BlockPos spot = nextAnchorCandidate();
                if (spot == null) return;

                // Vanilla BLOCK_INTERACTION_REACH ist 4.5, ENTITY_INTERACTION_RANGE waere 3.0 — fuer
                // eine Blockplatzierung gilt die 4.5. ReachPolicy benennt das, statt hier eine Zahl
                // zu stehen, die man mit der Nahkampfreichweite verwechseln kann (Grim misst Platzierung
                // mit derselben 4.5er-Grenze).
                double d = mc.player.getEyePosition().distanceTo(Vec3.atCenterOf(spot));
                if (!ReachPolicy.allows(ReachPolicy.Action.PLACE_BLOCK, d)) return; // naechster Tick neuer Versuch

                smoothLookAt(Vec3.atCenterOf(spot));
                if (currentAimError(Vec3.atCenterOf(spot)) > aimTolerance.get()) return; // erst ausrichten

                FindItemResult anchor = InvHelper.find(Items.RESPAWN_ANCHOR);
                if (!anchor.found()) return;

                // Eine Platzierung pro Bewegungspaket (Grim MultiPlace) — faellt sie aus, wandert der
                // Kandidat NICHT weiter, sonst verliert der Bot diese Stelle fuer immer.
                if (!allowAction(ActionCadence.Action.PLACE)) return;

                boolean swapped = InvUtils.swap(anchor.slot(), true);
                if (swapped) cadence.onSlotChange();
                boolean placed = BlockUtils.place(spot, anchor, false, 50);
                if (swapped) InvUtils.swapBack();

                if (placed) {
                    anchorPos = spot;
                    anchorStage = 1;
                    stageDeadline = tickCounter + 3 + rng.nextInt(5); // 150-400ms "Maus zum Anchor bewegen"
                } else {
                    anchorCandidateIndex++;
                }
            }
            case 1 -> {
                BlockState st = mc.level.getBlockState(anchorPos);
                if (!st.is(Blocks.RESPAWN_ANCHOR)) {
                    anchorStage = 0;
                    return;
                }
                if (tickCounter < stageDeadline) return;

                FindItemResult gs = InvHelper.find(Items.GLOWSTONE);
                if (!gs.found()) {
                    anchorStage = 0;
                    anchorCandidates.clear();
                    anchorCandidateIndex = 0;
                    return;
                }

                if (interactAnchor(gs)) {
                    anchorStage = 2;
                    stageDeadline = tickCounter + 3 + rng.nextInt(5);
                }
            }
            case 2 -> {
                BlockState st = mc.level.getBlockState(anchorPos);
                if (!st.is(Blocks.RESPAWN_ANCHOR)) {
                    anchorStage = 0;
                    anchorCooldown = 8;
                    return;
                }
                if (tickCounter < stageDeadline) return;

                int charges = st.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.RESPAWN_ANCHOR_CHARGES);
                if (charges == 0) {
                    anchorStage = 1;
                    stageDeadline = tickCounter + 3;
                    return;
                }

                FindItemResult fir = InvHelper.find(itemStack -> !itemStack.isEmpty() && !itemStack.is(Items.GLOWSTONE));
                if (!fir.found()) return;

                if (interactAnchor(fir)) {
                    anchorStage = 3;
                    stageDeadline = tickCounter + 20;
                }
            }
            default -> {
                if (!mc.level.getBlockState(anchorPos).is(Blocks.RESPAWN_ANCHOR)) {
                    anchorStage = 0;
                    anchorCooldown = 15 + rng.nextInt(15); // Verschnaufpause statt Dauerfeuer
                } else if (tickCounter >= stageDeadline) {
                    anchorStage = 2;
                }
            }
        }
    }

    /** Interagiert nur, wenn die (sichtbare, tempolimitierte) Rotation schon nah am Ziel ist
     *  UND die GESENDETE Rotation den Block wirklich trifft. Die zweite Bedingung fehlte vorher:
     *  der Anchor kann seitlich versetzt liegen, und die reine currentAimError()-Pruefung sieht das
     *  nicht — der Bot zeigte in die Naehe des Blocks und schickte trotzdem einen useItemOn auf eine
     *  voellig andere Flaeche (Grim InvalidInteractCursor). */
    private boolean interactAnchor(FindItemResult item) {
        if (drinkingFireRes || !item.found() || (!item.isOffhand() && !item.isHotbar())) return false;
        Vec3 center = Vec3.atCenterOf(anchorPos);
        smoothLookAt(center);
        if (currentAimError(center) > aimTolerance.get()) return false;
        if (strictReach.get() && !blockRayAllows(anchorPos)) return false;
        if (!allowAction(ActionCadence.Action.RIGHT_CLICK)) return false;
        boolean swapped = false;
        if (!item.isOffhand() && !item.isMainHand()) {
            swapped = InvUtils.swap(item.slot(), true);
            if (!swapped) return false;
            cadence.onSlotChange();
        }
        InteractionHand hand = item.isOffhand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        boolean success = mc.gameMode.useItemOn(mc.player, hand,
            new BlockHitResult(center, BlockUtils.getDirection(anchorPos), anchorPos, true)).consumesAction();
        if (success) {
            attacker.swing(hand == InteractionHand.OFF_HAND ? AttackDispatcher.OFF_HAND : AttackDispatcher.MAIN_HAND,
                AttackDispatcher.SwingMode.CLIENT);
        }
        if (swapped) InvUtils.swapBack();
        return success;
    }

    private BlockPos nextAnchorCandidate() {
        while (anchorCandidateIndex < anchorCandidates.size()) {
            BlockPos p = anchorCandidates.get(anchorCandidateIndex);
            if (mc.level.getBlockState(p).isAir()) return p;
            anchorCandidateIndex++;
        }
        return null;
    }

    // ---------- Bett-Executor (humanisiert: sichtbare Rotation + zufaellige Wartezeiten, kein Ladeschritt) ----------

    private void runBedTick(LivingEntity target) {
        if (bedCooldown > 0) {
            bedCooldown--;
            return;
        }

        Player self = mc.player;

        switch (bedStage) {
            case 0 -> {
                BedSpot spot = nextBedCandidate();
                if (spot == null) return;

                // Platzierung ist eine Blockinteraktion (4.5), kein Entity-Zugriff (3.0) — siehe die
                // gleiche Begruendung beim Anchor.
                double d = self.getEyePosition().distanceTo(Vec3.atCenterOf(spot.pos()));
                if (!ReachPolicy.allows(ReachPolicy.Action.PLACE_BLOCK, d)) return; // naechster Tick neuer Versuch

                // Grob in Richtung der gewuenschten Kopfteil-Ausrichtung drehen (menschliches Tempo via
                // smoothLookAt) - der virtuelle Zielpunkt liegt 10 Bloecke in "spot.dir()".
                Vec3 facePoint = self.getEyePosition().add(spot.dir().getStepX() * 10.0, -2.0, spot.dir().getStepZ() * 10.0);
                smoothLookAt(facePoint);
                if (currentAimError(facePoint) > aimTolerance.get()) return; // erst ausrichten

                FindItemResult bed = InvHelper.find(HumanPvP::isBed);
                if (!bed.found()) return;
                FindItemResult foundBed = bed;

                // Die vorangehende, tempolimitierte smoothLookAt(facePoint)+Toleranz-Gate oben hat den
                // Bot schon nah genug an spot.dir()'s exakte Kardinalrichtung herangedreht (facePoint
                // liegt selbst exakt in dieser Richtung, keine Zwischenwinkel) - ein zusaetzlicher
                // synchroner Praezisions-Snap direkt vor der Platzierung war unnoetig und widersprach
                // dem eigenen Modul-Ziel (tempolimitierte Drehung statt Snap ueberall). Platzierung
                // nutzt jetzt einfach die bereits erreichte, tolerierte Rotation.
                if (!allowAction(ActionCadence.Action.PLACE)) return;
                if (BlockUtils.place(spot.pos(), foundBed, false, 50)) {
                    bedPos = spot.pos();
                    bedStage = 1;
                    bedStageDeadline = tickCounter + 3 + rng.nextInt(5);
                } else {
                    bedCandidateIndex++;
                }
            }
            default -> {
                if (!(mc.level.getBlockState(bedPos).getBlock() instanceof BedBlock)) {
                    bedStage = 0;
                    bedCooldown = 8;
                    return;
                }
                if (tickCounter < bedStageDeadline) return;

                Vec3 center = Vec3.atCenterOf(bedPos);
                smoothLookAt(center);
                if (currentAimError(center) > aimTolerance.get()) return;
                // Auch das Zünden ist eine Interaktion auf einer bestimmten Flaeche — der Strahl muss
                // dort ankommen, sonst schickt der Bot einen Klick ins Leere (InvalidInteractCursor).
                if (strictReach.get() && !blockRayAllows(bedPos)) return;
                if (!allowAction(ActionCadence.Action.RIGHT_CLICK)) return;

                BlockUtils.interact(new BlockHitResult(center, BlockUtils.getDirection(bedPos), bedPos, true), InteractionHand.MAIN_HAND, true);
                attacker.swing(AttackDispatcher.MAIN_HAND, AttackDispatcher.SwingMode.CLIENT);
                bedStage = 0;
                bedCooldown = 15 + rng.nextInt(15); // Verschnaufpause statt Dauerfeuer
            }
        }
    }

    private BedSpot nextBedCandidate() {
        while (bedCandidateIndex < bedCandidates.size()) {
            BedSpot s = bedCandidates.get(bedCandidateIndex);
            if (mc.level.getBlockState(s.pos()).isAir()) return s;
            bedCandidateIndex++;
        }
        return null;
    }

    // ---------- Aura-Steuerung (groessere, verrauschte Hysterese) ----------

    private void selectAura(LivingEntity target) {
        // Wie in GodmodePvP: ohne Crystal UND ohne vollstaendige Anchor-Ausruestung UND ohne Bett (falls
        // aktiviert) gibt es nichts zu platzieren - Simulation und CrystalAura-Toggle komplett
        // ueberspringen statt sinnlos weiterzurechnen.
        boolean hasCrystals = hasActionableItem(Items.END_CRYSTAL);
        boolean hasAnchorItem = mc.level != null && mc.level.dimension() != Level.OVERWORLD
            && hasActionableItem(Items.RESPAWN_ANCHOR) && hasActionableItem(Items.GLOWSTONE);
        boolean hasBedItem = useBeds.get() && mc.level != null && mc.level.dimension() != Level.OVERWORLD
            && hasActionableItem(HumanPvP::isBed);
        Module ca = Modules.get().get(CrystalAura.class);
        if (!hasCrystals && !hasAnchorItem && !hasBedItem) {
            if (ca != null && crystalAuraEnabledByHuman && ca.isActive()) ca.toggle();
            crystalAuraEnabledByHuman = false;
            auraMode = -1;
            return;
        }

        Vec3 center = target.position();
        double crystalDmg = hasCrystals ? bestDamageAround(target, center, true) : -1;

        if (hasAnchorItem && useAnchors.get() && anchorMode.get() != 2) {
            BlockPos targetBlock = target.blockPosition();
            boolean stale = anchorCandidateIndex >= anchorCandidates.size()
                || anchorCalcOrigin == null
                || anchorCalcOrigin.distSqr(targetBlock) > 4;
            if (stale) {
                calcBestAnchor(target, center);
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
                calcBestBed(target, center);
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

        // Basisdistanz + Puffer statt fixer Konstante, skaliert mit Nahkampf-Reichweite und tatsaechlicher
        // Zielgeschwindigkeit (targetSpeed, siehe updateTracking) - ein schnell bewegtes Ziel ist bei
        // gleicher Distanz "weiter weg" bis zur naechsten Neubewertung als ein stehendes.
        double inRangeDist = attackRange.get() + 1.9 + targetSpeed * 4;
        boolean inRange = mc.player.distanceToSqr(target) < inRangeDist * inRangeDist;

        // Kleines Rauschen auf der Vergleichsschwelle - vermeidet ein starres, immer-gleiches
        // Umschalt-Verhalten bei exakt derselben Schadensdifferenz (wirkt sonst wie ein Taschenrechner).
        double noise = (rng.nextDouble() - 0.5) * 0.3;

        // Hysterese: zum Wechsel IN den Anchor-/Bett-Modus braucht es einen klaren Vorsprung, zum
        // BLEIBEN reicht ein kleinerer Abstand - sonst kippt der Modus bei jedem kleinen Schadens-
        // Unterschied hin und her (siehe GodmodePvP fuer dieselbe, dort schon vorhandene Traegheit -
        // HumanPvP nutzte bisher fuer beide Richtungen dieselbe feste Schwelle).
        double anchorStickyBonus = auraMode == 1 ? 0.3 : 0.0;
        double bedStickyBonus = auraMode == 2 ? 0.3 : 0.0;

        boolean wantAnchor;
        if (anchorMode.get() == 1) {
            wantAnchor = hasAnchorItem && inRange && anchorCandidateIndex < anchorCandidates.size()
                && bestAnchorDmgCache + anchorStickyBonus >= crystalDmg + noise;
        } else {
            wantAnchor = hasAnchorItem && inRange && bestAnchorDmgCache + anchorStickyBonus > crystalDmg + 0.3 + noise;
        }

        // syncSupport() konnte CrystalAuras Obsidian-Auto-Unterbau nicht erzwingen -> Crystal-Modus ist
        // ueber freier Luft (validExplosionSpot) faktisch nicht mehr voll funktionsfaehig. Anchor braucht
        // dafuer keinen Support-Mechanismus, also aktiv bevorzugen statt sich weiter auf einen
        // angeschlagenen Crystal-Modus zu verlassen (siehe GodmodePvP fuer denselben Fix).
        if (supportSyncFailed && hasAnchorItem && inRange
            && anchorCandidateIndex < anchorCandidates.size() && bestAnchorDmgCache > 0) {
            wantAnchor = true;
        }

        boolean wantBed = hasBedItem && inRange && bedCandidateIndex < bedCandidates.size()
            && bestBedRawDmgCache >= bedMinDamage.get() && bestBedDmgCache + bedStickyBonus > crystalDmg + 0.3 + noise;

        // Bei beiden verfuegbar gewinnt die schadenstaerkere Option.
        if (wantAnchor && wantBed) {
            if (bestBedDmgCache > bestAnchorDmgCache) wantAnchor = false; else wantBed = false;
        }

        // Deutlich groessere Umschalt-Traegheit als V1 (Basiswert), aber wie in GodmodePvP verkuerzt,
        // wenn sich das Ziel gerade tatsaechlich schnell bewegt - sonst bleibt bei schneller
        // Distanzaenderung 1+ Sekunde lang der objektiv schlechtere Modus aktiv.
        int hysteresis = (int) Math.max(10, 25 - targetSpeed * 8);
        if (tickCounter - lastAuraSwitch < hysteresis) return;

        if (wantAnchor && auraMode != 1) {
            if (ca.isActive() && crystalAuraEnabledByHuman) ca.toggle();
            auraMode = 1;
            lastAuraSwitch = tickCounter;
        } else if (wantBed && auraMode != 2) {
            if (ca.isActive() && crystalAuraEnabledByHuman) ca.toggle();
            auraMode = 2;
            lastAuraSwitch = tickCounter;
        } else if (!wantAnchor && !wantBed && auraMode != 0) {
            if (!ca.isActive()) {
                ca.toggle();
                crystalAuraEnabledByHuman = true;
            }
            auraMode = 0;
            lastAuraSwitch = tickCounter;
        }
    }

    /** Prueft, ob das Ziel gerade einen Totem in der Offhand bereit haelt - siehe GodmodePvP fuer die
     *  volle Begruendung des Abschlags unten. */
    private boolean targetHasTotemReady(LivingEntity target) {
        return target.getOffhandItem().is(Items.TOTEM_OF_UNDYING);
    }

    /** Kein hartes Gate (0) - eine Explosion gegen einen Totem-Traeger ist immer noch besser als gar
     *  keine (zehrt seinen Totem-Vorrat auf), aber sie "lohnt" sich nicht wie ein echter, unverteidigter
     *  Treffer, solange sie nicht toedlich ist. */
    private double totemAdjustedDamage(LivingEntity target, double dmg) {
        if (dmg <= 0) return dmg;
        if (dmg >= target.getHealth()) return dmg;
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
                for (int dy = 0; dy <= 1; dy++) {
                    Vec3 pos = new Vec3(bx + dx + 0.5, by + dy, bz + dz + 0.5);
                    BlockPos cell = new BlockPos(bx + dx, by + dy, bz + dz);

                    if (!validExplosionSpot(cell, crystal)) continue;

                    double selfDmg = crystal
                        ? DamageUtils.crystalDamage(mc.player, pos)
                        : DamageUtils.anchorDamage(mc.player, pos);
                    // SelfDamageGuard: Deckel 6.0 bleibt (bewusst strenger als GodmodePvP), aber der
                    // Deckel allein liess den Selbstmord zu — bei 4 HP und 6.0 Schadensdeckel ist
                    // jede Platzierung unter dem Deckel und trotzdem toedlich. LETHAL wird zuerst
                    // geprueft, unabhaengig davon, wie weit der Nutzer den Deckel stellt.
                    if (!SelfDamageGuard.allows(selfDmg, maxSelfDamage.get(), totalHealth())) continue;

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

    private void calcBestAnchor(LivingEntity target, Vec3 center) {
        anchorCandidates.clear();
        anchorCandidateIndex = 0;

        int bx = (int) Math.floor(center.x);
        int by = (int) Math.floor(center.y);
        int bz = (int) Math.floor(center.z);

        List<BlockPos> found = new ArrayList<>();
        List<Double> dmgs = new ArrayList<>();

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = 0; dy <= 1; dy++) {
                    Vec3 pos = new Vec3(bx + dx + 0.5, by + dy, bz + dz + 0.5);
                    BlockPos cell = new BlockPos(bx + dx, by + dy, bz + dz);

                    if (!validExplosionSpot(cell, false)) continue;
                    if (target.getBoundingBox().intersects(new net.minecraft.world.phys.AABB(cell))) continue;
                    if (mc.player.getBoundingBox().intersects(new net.minecraft.world.phys.AABB(cell))) continue;

                    double selfDmg = DamageUtils.anchorDamage(mc.player, pos);
                    if (!SelfDamageGuard.allows(selfDmg, maxSelfDamage.get(), totalHealth())) continue;

                    double dmg = DamageUtils.anchorDamage(target, pos);
                    if (dmg <= 0) continue;

                    found.add(cell);
                    dmgs.add(dmg);
                }
            }
        }

        // Stabile Sortierung mit deterministischem Tie-Break (BlockPos-Hash) statt manuellem Bubble-Sort
        // ohne Tie-Break - siehe GodmodePvP fuer dieselbe Begruendung (kein Hin- und Herwechseln bei
        // exakt gleichem Schaden).
        List<Integer> order = new ArrayList<>();
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
        if (crystal) return below.is(Blocks.OBSIDIAN) || below.is(Blocks.BEDROCK) || below.isAir();
        return below.blocksMotion() && mc.level.getBlockState(cell.above()).isAir();
    }

    /** Toleranz fuer den LoS-Block-Raycast - siehe GodmodePvP fuer dieselbe Begruendung. Wert bewusst
     *  UNVERAENDERT gelassen (kein Live-Tuning-Beleg fuer einen anderen Wert), nur benannt/dokumentiert. */
    private static final double LOS_RAYCAST_TOLERANCE = 0.6;

    /** Echter Block-Raycast zwischen Augenposition und Kandidaten-Mittelpunkt - siehe GodmodePvP fuer
     *  denselben Fix/dieselbe Begruendung (reine Distanz-/Blockstate-Pruefung sieht Mauern dazwischen
     *  nicht, waehlte also auch real unerreichbare Kandidaten hinter Deckung). */
    private boolean hasRaycastLineOfSight(Vec3 point) {
        if (mc.level == null || mc.player == null) return true;
        Vec3 eye = mc.player.getEyePosition();
        ClipContext ctx = new ClipContext(eye, point, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player);
        BlockHitResult result = mc.level.clip(ctx);
        return result.getType() == HitResult.Type.MISS || result.getLocation().distanceTo(point) < LOS_RAYCAST_TOLERANCE;
    }

    private boolean isNearLava(BlockPos cell) {
        if (mc.level.getBlockState(cell).is(Blocks.LAVA)) return true;
        for (Direction dir : Direction.values()) {
            if (mc.level.getBlockState(cell.relative(dir)).is(Blocks.LAVA)) return true;
        }
        return false;
    }

    /** Zaehlt Bloecke in einem Wuerfel um 'center', die 'matcher' erfuellen - fuer die Anchor-/Bett-
     *  Delta-Erkennung von auto-shield/explosionRetreatUntil (Blocks statt Entities, siehe GodmodePvP
     *  fuer dieselbe Methode/Begruendung). */
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

    /** Reichweite, in der eine Bett-Platzierung real ausgefuehrt werden kann - siehe GodmodePvP. */
    private static final double BED_PLACE_REACH = 4.2;

    /** Alle gueltigen Bett-Plaetze um das Ziel, sortiert nach NETTO-Vorteil (Zielschaden minus
     *  Eigenschaden). Suchradius horizontal +-2 und Reichweiten-Filter - siehe GodmodePvP.calcBestBed
     *  fuer die volle, live gemessene Begruendung (mit +-1 lagen im Nahkampf ALLE Kandidatenzellen auch
     *  im eigenen Explosionsradius, der Eigenschaden-Deckel verwarf dadurch restlos alle). */
    private void calcBestBed(LivingEntity target, Vec3 center) {
        bedCandidates.clear();
        bedCandidateIndex = 0;

        int bx = (int) Math.floor(center.x);
        int by = (int) Math.floor(center.y);
        int bz = (int) Math.floor(center.z);

        List<BedSpot> found = new ArrayList<>();
        List<Double> scores = new ArrayList<>();

        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    BlockPos foot = new BlockPos(bx + dx, by + dy, bz + dz);
                    if (!validBedCell(foot)) continue;

                    Vec3 pos = Vec3.atCenterOf(foot);
                    if (mc.player.distanceToSqr(pos) > BED_PLACE_REACH * BED_PLACE_REACH) continue;

                    Direction dir = findFreeBedDirection(foot);
                    if (dir == null) continue;
                    BlockPos head = foot.relative(dir);

                    net.minecraft.world.phys.AABB footBox = new net.minecraft.world.phys.AABB(foot);
                    net.minecraft.world.phys.AABB headBox = new net.minecraft.world.phys.AABB(head);
                    // Beide Zellen pruefen (nicht nur das Fussteil) - siehe GodmodePvP.calcBestBed fuer
                    // die volle Begruendung (Referenz: BlackOut BedAura+ prueft `pos.offset(dir)` mit).
                    if (target.getBoundingBox().intersects(footBox) || target.getBoundingBox().intersects(headBox)) continue;
                    if (mc.player.getBoundingBox().intersects(footBox) || mc.player.getBoundingBox().intersects(headBox)) continue;

                    double selfDmg = DamageUtils.bedDamage(mc.player, pos);
                    if (!bedSelfDamageAcceptable(selfDmg)) continue;

                    double dmg = DamageUtils.bedDamage(target, pos);
                    if (dmg <= 0) continue;

                    found.add(new BedSpot(foot, dir));
                    scores.add(dmg - selfDmg);
                }
            }
        }

        // Stabile Sortierung mit deterministischem Tie-Break - siehe GodmodePvP/calcBestAnchor fuer
        // dieselbe Begruendung (kein Hin- und Herwechseln bei exakt gleichem Schaden).
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < found.size(); i++) order.add(i);
        order.sort((a, b) -> {
            int cmp = Double.compare(scores.get(b), scores.get(a));
            return cmp != 0 ? cmp : Integer.compare(found.get(a).pos().hashCode(), found.get(b).pos().hashCode());
        });
        for (int i : order) bedCandidates.add(found.get(i));
    }

    /** Bett braucht keine feste Unterlage - aber die Platzierung selbst laeuft ueber einen
     *  rechtsklickbaren Nachbarblock (siehe GodmodePvP.validBedCell fuer die volle Begruendung -
     *  ohne echten Nachbarn faellt BlockUtils.place() auf einen unzuverlaessigen Fallback zurueck,
     *  der Betten aus dem Inventar verschwinden/droppen liess statt sie sichtbar zu platzieren). */
    private boolean validBedCell(BlockPos cell) {
        if (mc.level == null) return false;
        if (!mc.level.getBlockState(cell).isAir()) return false;
        if (BlockUtils.getPlaceSide(cell) == null) return false;
        if (!hasRaycastLineOfSight(Vec3.atCenterOf(cell))) return false;
        return !(avoidLava.get() && isNearLava(cell));
    }

    /** Erste Himmelsrichtung, in der neben dem Fussteil noch eine zweite freie Zelle fuer das Kopfteil liegt. */
    private Direction findFreeBedDirection(BlockPos foot) {
        for (Direction dir : BED_DIRECTIONS) {
            if (validBedCell(foot.relative(dir))) return dir;
        }
        return null;
    }

    /** Eigenschaden-Freigabe fuer Bett-Explosionen inkl. Selbstmord-Schutz - siehe GodmodePvP.
     *  Wendet bedSelfDamageMultiplier an (Paper/Spigot haben oft 2-3x hoeheren Self-Damage).
     *  SelfDamageGuard prueft den Selbstmord VOR dem Deckel — der Deckel ist einstellbar, der Tote
     *  nicht; ein Nutzer, der bed-max-self-damage hochdreht, soll daran nicht sterben. */
    private boolean bedSelfDamageAcceptable(double selfDmg) {
        double adjustedDmg = selfDmg * bedSelfDamageMultiplier.get();
        return SelfDamageGuard.allows(adjustedDmg, bedMaxSelfDamage.get(), totalHealth());
    }

    /** Gesamtlebensenergie inkl. Absorption — die Groesse, gegen die ein Selbstmord gerechnet wird.
     *  Absorption zaehlt mit, sonst haelt der Bot einen Absorptions-Totem fuer "Leben" und geht bei
     *  4 HP plus 6 Absorption in einen Schaden, der ihn nicht toetet. */
    private double totalHealth() {
        Player self = mc.player;
        if (self == null) return 0.0;
        return self.getHealth() + self.getAbsorptionAmount();
    }

    private static boolean isBed(ItemStack stack) {
        return stack.getItem() instanceof BedItem;
    }

    /** Reiht eine Rotation+Aktion ein - siehe GodmodePvP fuer die volle Erklaerung (Mehrfachaktionen
     *  pro Tick statt des frueheren starren 1-Aktion-Mutex). Gibt immer true zurueck.
     *  @param priority Priorität der Aktion. Nur Aktionen mit Priority > PRIORITY_LOOK zählen als
     *  "echte" Aktionen für realActionThisTick (verhindert free-look Tail-Flush). */
    private boolean rotateAndRun(double yaw, double pitch, int priority, Runnable callback) {
        // Meteor schreibt rohe Floats nach serverYaw/serverPitch; Grim rechnet den ggT der
        // gesendeten Deltas. Deshalb quantisiert der Addon selbst — auf dem Gitter der aktuellen
        // Maus-Empfindlichkeit, mit einem winzigen Jitter, damit nicht drei Rotationen hintereinander
        // exakt dasselbe Delta haben.
        GcdRotator.Rotation sent = quantizeForWire(yaw, pitch);
        lastSentRotation = sent;
        Rotations.rotate(sent.yaw(), sent.pitch(), priority, rotationsThisTick > 0, callback);
        rotationsThisTick++;
        if (priority > PRIORITY_LOOK) realActionThisTick = true;
        return true;
    }

    /**
     * Quantisiert die Zielwinkel auf das Maus-Gitter.
     *
     *  <p><b>Warum der Seed aus dem Rampe-Wert kommt und nicht aus der Kamera:</b> dieses Profil
     *  snappt nicht, sondern dreht pro Tick höchstens {@code max-turn-speed} Grad. Der Akkumulator
     *  braucht aber die zuletzt GESENDETE Rotation als Vorwert — und das ist in diesem Profil gerade
     *  nicht die Kamera, sondern das Ergebnis des letzten Aufrufs. Ohne Seed quantisiert Grim-blind
     *  gegen 0, mit Kamera-Seed gegen einen Winkel, den wir nie gesendet haben; beides erzeugt Deltas,
     *  die nicht auf einem Gitter liegen. Deshalb wird einmalig aus effectiveAimYaw/Pitch geseedet,
     *  dem Wert, den smoothLookAt() gerade ERSTELLT hat, und danach zustandsbehaltend weitergefuihrt.
     */
    private GcdRotator.Rotation quantizeForWire(double yaw, double pitch) {
        double divisor = GcdRotator.sensitivityDivisor(mc.options.sensitivity().get());
        if (!rotationAccumulator.isSeeded()) {
            rotationAccumulator.seed(effectiveAimYaw(), effectiveAimPitch(), divisor);
        }
        return rotationAccumulator.quantize(yaw, pitch, divisor, rotationJitter.get(), rng);
    }

    private boolean rotateAndRun(double yaw, double pitch, Runnable callback) {
        return rotateAndRun(yaw, pitch, PRIORITY_MISC, callback);
    }

    /** Haelt Fire Resistance permanent aktiv, solange man sich im Nether befindet - macht Lava-Kontakt,
     *  Explosions-Feuer und brennende Nachbarblöcke irrelevant. Laeuft unabhaengig vom Kampf-Zustand. */
    private void maintainFireResistance() {
        if (drinkingFireRes) {
            if (mc.player.hasEffect(MobEffects.FIRE_RESISTANCE) || tickCounter - fireResStartTick > 40) {
                if (fireResSwapBack) InvUtils.swapBack();
                drinkingFireRes = false;
                fireResSwapBack = false;
                fireResHand = InteractionHand.MAIN_HAND;
            } else {
                mc.gameMode.useItem(mc.player, fireResHand);
            }
            return;
        }

        if (blocking || !autoFireRes.get() || mc.level == null || mc.level.dimension() != Level.NETHER
            || mc.player.hasEffect(MobEffects.FIRE_RESISTANCE)) return;
        FindItemResult potion = findFireResistancePotion();
        if (!potion.found() || (!potion.isOffhand() && !potion.isHotbar())) return;

        InteractionHand hand = potion.isOffhand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        boolean swapped = false;
        if (!potion.isOffhand() && !potion.isMainHand()) {
            swapped = InvUtils.swap(potion.slot(), true);
            if (!swapped) return;
        }
        fireResHand = hand;
        fireResSwapBack = swapped;
        if (mc.gameMode.useItem(mc.player, hand).consumesAction()) {
            drinkingFireRes = true;
            fireResStartTick = tickCounter;
        } else if (swapped) {
            InvUtils.swapBack();
            fireResSwapBack = false;
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

    // ---------- Tracking ----------

    private void updateTracking(LivingEntity target) {
        UUID id = target.getUUID();
        Vec3 cur = target.position();
        Vec3 prev = lastPositions.put(id, cur);
        // Nur fuer die adaptive inRange-/Umschalt-Traegheit unten (selectAura) - keine volle Vorhersage
        // wie GodmodePvP, hier reicht ein grober Tempo-Skalar.
        if (prev != null) targetSpeed = cur.distanceTo(prev);
    }

    // ---------- Zielauswahl ----------

    private LivingEntity findTarget(Player self) {
        if (mc.level == null) return null;

        // Bereits verfolgtes Ziel bevorzugt behalten, solange es lebt und in Reichweite bleibt - sonst
        // kann ein dritter, kurzzeitig naeherer/isolierterer Spieler das Ziel mitten im Kampf kapern.
        if (pursuing && engagedId != null) {
            for (Player p : mc.level.players()) {
                if (!p.getUUID().equals(engagedId)) continue;
                if (p.isAlive() && !p.isSpectator() && self.distanceToSqr(p) <= followRange.get() * (double) followRange.get()) {
                    return p;
                }
                break;
            }
        }

        Player alt = findPlayerTarget(self);
        return alt;
    }

    /** Reine Neubewertung ohne Sticky-Bias zum aktuellen Ziel - im Gegensatz zu findTarget() (das ein
     *  bereits verfolgtes Ziel bevorzugt behaelt) fuer die periodische Re-Targeting-Pruefung in doTick(),
     *  die genau diese Bevorzugung NICHT haben darf, um ueberhaupt einen Wechsel erkennen zu koennen. */
    private Player findPlayerTarget(Player self) {
        // Echte Spieler haben immer Vorrang vor einem Trainings-Dummy (FakePlayerEntity) - der zaehlt nur
        // als Ziel, wenn wirklich kein echter Gegner in Reichweite ist.
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

    // ---------- Aktionen ----------

    /** W-Tap/Sprint-Reset: Sprint kurz aus-ein schalten statt durchgehend zu sprinten, damit jeder
     *  Treffer den Sprint-Knockback-Bonus bekommt statt nur der erste einer Sprint-Sequenz. */
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

    /** Kreis-strafet im Nahkampf (variiert den Explosionswinkel, schwerer zu treffen) und weicht kurz
     *  zurueck, wenn gerade eine neue Explosionsquelle aufgetaucht ist - siehe GodmodePvP fuer dieselbe
     *  Mechanik/Begruendung. Nutzt die VIRTUELLE Ziel-Rotation (effectiveAimYaw()) statt der echten
     *  Kamera-Rotation - bei aktivem Silent-Aim (free-look) zeigt mc.player.getYRot() sonst auf die
     *  alte/stehengebliebene echte Kamera-Rotation, waehrend smoothLookAt() nur noch die virtuelle
     *  aimYaw kontinuierlich Richtung Ziel dreht. Ohne diesen Fix strafte der Bot relativ zur falschen
     *  Richtung, sobald free-look aktiv war - Bewegungs- und Blickrichtung liefen auseinander. */
    private void updateCombatMovement(LivingEntity target, double dist) {
        if (dist > attackRange.get()) {
            Input.setKeyState(mc.options.keyLeft, false);
            Input.setKeyState(mc.options.keyRight, false);
            Input.setKeyState(mc.options.keyDown, false);
            nextStrafeSwitchTick = -1; // Sentinel: naechster Nahkampf-Eintritt bekommt frischen Zufalls-Versatz
            return;
        }

        // Bei frischem Nahkampf-Eintritt (Sentinel -1) NICHT sofort auf Tick 0 flippen (waere ein exakt
        // vorhersagbares Timing-Signal) - nur einen zufaelligen Zeitpunkt fuer den ERSTEN Wechsel
        // vormerken und die aktuelle Strafe-Richtung vorerst behalten.
        if (nextStrafeSwitchTick < 0) {
            nextStrafeSwitchTick = tickCounter + rng.nextInt(15);
        } else if (tickCounter >= nextStrafeSwitchTick) {
            strafeLeft = !strafeLeft;
            nextStrafeSwitchTick = tickCounter + 10 + rng.nextInt(15);
        }

        Vec3 center = target.getBoundingBox().getCenter();
        double dx = center.x - mc.player.getX();
        double dz = center.z - mc.player.getZ();
        double horiz = Math.sqrt(dx * dx + dz * dz);
        if (horiz < 1e-4) return;
        dx /= horiz;
        dz /= horiz;

        double tangX = strafeLeft ? -dz : dz;
        double tangZ = strafeLeft ? dx : -dx;

        double yawRad = Math.toRadians(effectiveAimYaw());
        double rightX = -Math.cos(yawRad), rightZ = -Math.sin(yawRad);
        double rightDot = tangX * rightX + tangZ * rightZ;

        Input.setKeyState(mc.options.keyRight, rightDot >= 0);
        Input.setKeyState(mc.options.keyLeft, rightDot < 0);
        Input.setKeyState(mc.options.keyDown, tickCounter < explosionRetreatUntil);
    }

    /**
     * Der Nahkampfschlag. Gibt zurueck, ob wirklich geschlagen wurde — der Aufrufer setzt
     * {@code currentAction} nur noch auf tatsaechliche Aktionen.
     */
    private boolean attackMelee(LivingEntity target) {
        if (drinkingFireRes) return false; // s.o. - Swap-Merkposten waehrend des Trinkens nicht anfassen
        // Eine Aktion pro Bewegungspaket: der Slot-Wechsel zur Axt und der Schlag duerfen nicht
        // zwischen zwei anderen Aktionen liegen (PacketOrderE/F). tickGate hat der Aufrufer schon
        // geprueft — dort VOR readyToClick(), damit der Klickversatz nicht verbraucht wird, wenn die
        // Drosselung ohnehin sperrt. Deshalb hier nur das Ledger, kein zweiter Drossel-Versuch.
        cadence.setUsingItem(mc.player.isUsingItem());
        if (!cadence.request(ActionCadence.Action.ATTACK).allowed()) return false;

        boolean swapped = false;
        if (preferAxeMelee.get()) {
            FindItemResult axe = InvUtils.findInHotbar(itemStack -> itemStack.getItem() instanceof AxeItem);
            if (axe.found() && !axe.isMainHand()) {
                swapped = InvUtils.swap(axe.slot(), true);
                cadence.onSlotChange();
            }
        }
        boolean wasSprinting = mc.player.isSprinting();
        // Das Schadenspaket bleibt gameMode.attack(): nur das setzt den Angriffs-Cooldown zurueck
        // und loest die Sweep-Reichweitenpruefung auf. AttackDispatcher liefert den Animationsteil,
        // der vorher ein nacktes player.swing() war.
        mc.gameMode.attack(mc.player, target);
        attacker.attack(target.getId(), AttackDispatcher.MAIN_HAND, AttackDispatcher.SwingMode.CLIENT);
        // Das swapBack() hier ist die legitime Ruecksetzung und bleibt bewusst stehen.
        if (swapped) InvUtils.swapBack();

        if (sprintReset.get() && wasSprinting) sprintResetCooldown = 2;
        return true;
    }

    /** Schildbrechen mit der Axt — dieselben Takt- und Reichweitenregeln wie der normale Schlag. */
    private boolean breakShield(Player target) {
        if (drinkingFireRes) return false; // s.o. - Swap-Merkposten waehrend des Trinkens nicht anfassen
        FindItemResult axe = InvUtils.findInHotbar(itemStack -> itemStack.getItem() instanceof AxeItem);
        if (!axe.found()) return false;

        if (!allowAction(ActionCadence.Action.ATTACK)) return false;
        // Der Schildbrecher schiesst ohne eigene Toleranz-Pruefung — genau hier war bisher die
        // Rotation am wenigsten garantiert, weil runAnchorTick()/runBedTick() sie zuletzt gesetzt
        // haben. Die Validierung gegen die gesendete Rotation schliesst das Loch.
        if (!meleeRayAllows(target)) return false;

        boolean swapped = InvUtils.swap(axe.slot(), true);
        cadence.onSlotChange();
        mc.gameMode.attack(mc.player, target);
        attacker.attack(target.getId(), AttackDispatcher.MAIN_HAND, AttackDispatcher.SwingMode.CLIENT);
        // Legitime Ruecksetzung, bleibt.
        if (swapped) InvUtils.swapBack();
        return true;
    }


    /**
     * D9 — die einzige der drei AttackGate-Sperren, die am Spielerschlag wirklich greift.
     *
     *  <p>Mindestanforderung an den Angriffs-Cooldown ist bewusst 0.95 und nicht
     *  {@link AttackGate#FULL_STRENGTH}: dieses Profil ist der langsame, fehlertolerante Bot — ein
     *  Schlag, der am Cooldown scheitert, ist Teil der Rolle, kein Fehler.
     *
     *  <p><b>Warum nicht {@code evaluate()} mit allen drei Gates:</b>
     *  D7 ({@code hurtTime > 0}) ist eine Kristall-Pop-Sperre. Sie entscheidet, ob eine
     *  Explosion im 0.5-Sekunden-Flash des Ziels ankommt — Spielerschaden blockiert sie nicht,
     *  in diesem Zeitfenster wird nur der Knockback ignoriert. Als Melee-Sperre wuerde sie den Bot
     *  lahmlegen: Kaempft man gegen jemanden, der selbst zuschlaegt, ist {@code hurtTime} fast
     *  dauerhaft groesser 0, und der Bot schlaegt dann nie mehr zurueck.
     *  D10 (Sprung-Crit) hat in diesem Profil keine Entsprechung — der Bot springt nie, um zu
     *  critten; eine toedliche-Crit-Frage zu stellen, die nie gestellt wird, waere toter Code.
     */
    private static final double HUMAN_MIN_ATTACK_STRENGTH = 0.95;

    private boolean attackGateAllows() {
        return AttackGate.gateStrength(
            mc.player.getAttackStrengthScale(0.5f), HUMAN_MIN_ATTACK_STRENGTH).allowed();
    }

    /**
     * Prueft den Schlag gegen die WIRKLICH gesendete Rotation — nicht gegen die Kamera und nicht
     * gegen {@code attack-range} (3.4, ueber der Grim-Schwelle 3.0005). Faellt eine von vier
     * Pruefungen durch, wird nicht geschlagen.
     */
    private boolean meleeRayAllows(LivingEntity target) {
        if (!strictReach.get()) return true;
        GcdRotator.Rotation sent = sentRotation();
        Vec3 eye = mc.player.getEyePosition();
        AABB box = target.getBoundingBox();
        ActionRayValidator.Verdict verdict = ActionRayValidator.validateAttack(
            sent, sent, eye, ActionRayValidator.Box.of(box.getMinPosition(), box.getMaxPosition()),
            ReachPolicy.Action.MELEE, rayWorld);
        return verdict.valid();
    }

    /**
     * Dasselbe fuer eine Blockinteraktion: trifft der Strahl der gesendeten Rotation den beabsichtigten
     * Block ueberhaupt, und liegt der Cursor auf der Flaeche?
     *
     *  <p>Bewusst OHNE Faces-Vergleich. {@code ActionRayValidator} akzeptiert {@code null} als
     *  "irgendeine Flaeche" — und genau das ist hier richtig: dieses Modul zielt auf die Blockmitte,
     *  nicht auf eine bewusst gewaehlte Seite. Ein erzwungener Flaechenvergleich wuerde bei jedem
     *  schraegen Blick auf den Anchor FACE_MISS werfen und die komplette Anker-Stufe (platzieren →
     *  laden → zuenden) dauerhaft blockieren, weil der Anker nie fertig wuerde. Blockidentitaet und
     *  Cursor-Lage sind die beiden Pruefungen, die Grim hier wirklich interessieren.
     */
    private boolean blockRayAllows(BlockPos target) {
        if (target == null) return false;
        GcdRotator.Rotation sent = sentRotation();
        return ActionRayValidator.validateBlockInteraction(
            sent, sent, mc.player.getEyePosition(), target, null, rayWorld).valid();
    }

    /**
     * Die Rotation, die der Server in diesem Tick sieht.
     *
     *  <p>Hat rotateAndRun() schon etwas eingereiht, ist das deren Ergebnis — Meteors Callback laeuft
     *  asynchron, der Winkel im Movement-Paket ist aber der zuletzt uebergebene. Sonst (keine
     *  eingereihte Aktion) traegt der Client die Kamerarotation, und genau die zaehlt dann.
     */
    private GcdRotator.Rotation sentRotation() {
        if (lastSentRotation != null) return lastSentRotation;
        return new GcdRotator.Rotation(effectiveAimYaw(), effectiveAimPitch());
    }

    /**
     * Misst die tatsaechliche Client-Tickdauer und uebersetzt sie in ein Urteil des Gates.
     *
     *  <p>Warum selbst gemessen statt aus dem Server gelesen: die Zeit zwischen zwei eigenen
     *  TickEvent.Pre ist genau die Groesse, gegen die sich Bewegung, Rotation und Aktion verschieben
     *  muessen — eine Ping-Anzeige waere etwas anderes.
     */
    private void sampleTickRate() {
        if (!lagThrottle.get()) {
            tickVerdict = TickRateGate.Verdict.RUN;
            return;
        }
        long now = System.nanoTime();
        double secondsSinceLastTick = lastTickNanos == 0L
            ? TickRateGate.NOMINAL : (now - lastTickNanos) / 1.0E9;
        lastTickNanos = now;
        double maxHealth = mc.player != null ? mc.player.getMaxHealth() : 20.0;
        double healthFraction = maxHealth > 0 && mc.player != null
            ? mc.player.getHealth() / maxHealth : 1.0;
        tickVerdict = tickGate.evaluate(secondsSinceLastTick, healthFraction);
    }

    /** Vergisst Crystals, die aus der Naehe verschwunden sind (gesprengt oder vom Chunk verschluckt).
     *  Ohne das waechst die eigene Liste bis zur Kapazitaetsgrenze und verdraengt sich selbst. */
    private void forgetVanishedCrystals(List<EndCrystal> visible) {
        if (ownCrystals.snapshot().isEmpty()) return;
        visibleCrystalIds.clear();
        for (EndCrystal crystal : visible) visibleCrystalIds.add(crystal.getId());
        for (Integer id : ownCrystals.snapshot()) {
            if (!visibleCrystalIds.contains(id)) ownCrystals.forget(id);
        }
    }

    /**
     * Die eine Tuer fuer jede weltwirksame Aktion: Lag-Drosselung, dann Ledger.
     *
     *  <p><b>Warum der Zaehler bei jedem Versuch hochlaeuft und nicht nur beim Erfolg:</b>
     *  {@code TickRateGate} entscheidet im THROTTLE-Fall ueber {@code index % n == 0}. Wuerde der
     *  Zaehler nur bei ausgefuehrten Aktionen steigen, bliebe er bei einem ungeraden Stand fuer
     *  immer stehen — die Drosselung wuerde dann nie wieder durchlassen und der Bot faellt unter
     *  Lag dauerhaft still. Genau das ist der Fehler, den diese Reihenfolge verhindert.
     *
     *  <p>Die Reihenfolge ist Absicht: erst die Drosselung (sie ist billig und kann alles blockieren),
     *  dann das Ledger. Ein abgelehnter Angriff darf die Aktionsmarke des Ticks nicht verbrauchen —
     *  sonst waere der naechste, gültige Versuch schon der zweite im selben Bewegungspaket.
     */
    private boolean allowAction(ActionCadence.Action action) {
        cadence.setUsingItem(mc.player != null && mc.player.isUsingItem());
        if (!tickGate.allows(tickVerdict, actionIndex++)) return false;
        return cadence.request(action).allowed();
    }

    private boolean pearlTrajectoryClear(Vec3 origin, double yaw, double pitch, Vec3 extraVel, double ticks) {
        // Bewusst KONSERVATIV und damit bewusst gegen ExplosionScanner.segmentClear, das bei fehlender
        // Welt "frei" liefert. Die beiden beantworten verschiedene Fragen: segmentClear sagt "steht
        // auf dieser Strecke ein Block?", trajectoryClear sagt "darf ich damit einen Wurf ausloesen?".
        // Eine nicht pruefbare Bahn ist keine freie Bahn — also kein Wurf ohne Welt.
        if (mc.level == null || mc.player == null) return false;
        int maxTicks = Math.max(4, Math.min(80, (int) Math.ceil(ticks) + 2));
        return scanner.trajectoryClear(origin, yaw, pitch, extraVel, maxTicks);
    }

    private boolean isPearlLanding(BlockPos cell) {
        return mc.level != null && mc.level.getBlockState(cell).isAir()
            && mc.level.getBlockState(cell.above()).isAir()
            && mc.level.getBlockState(cell.below()).blocksMotion();
    }

    /** Hoechster Vorhalt in Bloecken. Der Vorhalt ist eine Korrektur, kein Zielanflug: dieser Bot
     *  soll danebenschiessen koennen. Ohne diese Klampe wuerde eine sprintende Zielsprint bei
     *  25 Ticks Flugzeit auf ueber fuenf Bloecke Vorhalt fuehren — das waere der praezise Wurf eines
     *  Rechners und nicht der eines Menschen. */
    private static final double LEAD_MAX_BLOCKS = 1.2;

    /**
     * Vorhalt auf die gemessene Zielbewegung.
     *
     *  <p>C6: der Wurf zielte auf {@code getBoundingBox().getCenter()} — also dorthin, wo das Ziel
     *  JETZT steht. Bei jedem laufenden Gegner kommt die Perle einen bis zwei Bloecke zu spaet an.
     *  Richtig ist der Schnittpunkt aus Flugzeit und Zielgeschwindigkeit.
     */
    private Vec3 leadPoint(Vec3 center, Vec3 velocity, double ticks) {
        if (!(ticks > 0) || Double.isInfinite(ticks)) return center;
        double aheadX = velocity.x * ticks;
        double aheadZ = velocity.z * ticks;
        double ahead = Math.hypot(aheadX, aheadZ);
        if (ahead > LEAD_MAX_BLOCKS && ahead > 1.0E-6) {
            double scale = LEAD_MAX_BLOCKS / ahead;
            aheadX *= scale;
            aheadZ *= scale;
        }
        return center.add(aheadX, 0.0, aheadZ);
    }

    private double[] solvePearlAtTarget(LivingEntity target) {
        if (target == null || mc.player == null || mc.level == null) return null;
        Vec3 from = mc.player.getEyePosition().subtract(0, 0.1, 0);
        Vec3 own = mc.player.getKnownMovement();
        Vec3 extraVel = new Vec3(own.x, mc.player.onGround() ? 0 : own.y, own.z);
        Vec3 center = target.getBoundingBox().getCenter();

        // Die Flugzeit zum aktuellen Mittelpunkt, um den Vorhalt zu bestimmen. Das ist EINE
        // Ballistikrechnung ohne Kandidatensuche und ohne Raycasts — bewusst nicht der alte Weg,
        // bei dem der komplette Scan (Kandidatenmenge + Bahnpruefung) pro angenommenem Wurf zweimal
        // durchlief (B11).
        double[] nominal = PvpMath.solvePearlAim(from, center, extraVel);
        double horizon = nominal != null && nominal.length > 2 ? nominal[2] : 0.0;
        Vec3 aimCenter = leadPoint(center, target.getDeltaMovement(), horizon);

        List<Vec3> points = new ArrayList<>();
        points.add(aimCenter);
        points.add(aimCenter.add(0, -0.65, 0));
        points.add(aimCenter.add(0, 0.65, 0));
        for (int i = 0; i < 8; i++) {
            double angle = i * Math.PI / 4.0;
            points.add(aimCenter.add(Math.cos(angle) * 0.7, 0, Math.sin(angle) * 0.7));
        }
        BlockPos feet = target.blockPosition();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    BlockPos cell = feet.offset(dx, dy, dz);
                    if (isPearlLanding(cell)) points.add(Vec3.atBottomCenterOf(cell).add(0, 0.9, 0));
                }
            }
        }

        double[] best = null;
        double bestScore = Double.MAX_VALUE;
        for (Vec3 point : points) {
            double[] aim = PvpMath.solvePearlAim(from, point, extraVel);
            if (aim == null || !pearlTrajectoryClear(from, aim[0], aim[1], extraVel, aim[2])) continue;
            // Bewertung gegen den Vorhaltepunkt, nicht gegen die aktuelle Position: sonst gewinnt
            // konsequent der Kandidat direkt am jetzigen Mittelpunkt und der Vorhalt ist wieder weg.
            double score = point.distanceTo(aimCenter);
            if (score < bestScore) {
                bestScore = score;
                best = aim;
            }
        }
        return best;
    }

    /**
     * Gap-Close-Wurf. Loest die Bahn EINMAL auf und reicht das Ergebnis an den Wurf weiter —
     * vorher lief throwPearl() intern noch einmal solvePearlAtTarget() und damit die komplette
     * Kandidatensuche ein zweites Mal fuer jeden Wurf, der dann tatsaechlich abging (B11).
     */
    private boolean throwPearlAtTarget(LivingEntity target) {
        if (tickCounter - lastPearlScanTick < 8) return false;
        lastPearlScanTick = tickCounter;
        double[] aim = solvePearlAtTarget(target);
        if (aim == null) return false;
        return throwPearlWithAim(aim[0], aim[1]);
    }

    /** Fluchtwurf: nach hinten ueber den Kopf, ohne Ballistiksuche — dafuer gibt es nichts zu loesen. */
    private boolean throwPearlAwayFrom(LivingEntity aimAt) {
        return throwPearlWithAim(Rotations.getYaw(aimAt) + 180.0, -35);
    }

    /** Der gemeinsame Wurf: Swap, Rotation, Benutzung. Der Slot-Wechsel wird dem Ledger gemeldet,
     *  damit eine zwischen zwei Aktionen liegende Umschaltung als SLOT_ORDER erkannt wird. */
    private boolean throwPearlWithAim(double yaw, double pitch) {
        if (drinkingFireRes) return false;
        FindItemResult pearl = InvHelper.find(Items.ENDER_PEARL);
        if (!pearl.found() || (!pearl.isOffhand() && !pearl.isHotbar())) return false;
        InteractionHand hand = pearl.isOffhand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        boolean swapped = false;
        if (!pearl.isOffhand() && !pearl.isMainHand()) {
            swapped = InvUtils.swap(pearl.slot(), true);
            if (!swapped) return false;
            cadence.onSlotChange();
        }
        final boolean didSwap = swapped;
        // hand ist effectively final, der Lambda-Kontext braucht keinen Alias.
        rotateAndRun(yaw, pitch, PRIORITY_PEARL, () -> {
            if (mc.gameMode.useItem(mc.player, hand).consumesAction()) lastPearlTick = tickCounter;
            if (didSwap) InvUtils.swapBack();
        });
        return true;
    }

    /** Rettungswurf senkrecht nach unten — mit Streuung (C9).
     *
     *  <p>Ein exakt fester 80-Grad-Winkel ist das auffaelligste Muster im ganzen Modul: der Bot
     *  wuerde bei jedem Sturz pixelgleich werfen. PitchVariance liefert den gestreuten Winkel und
     * faellt bei einem 0-Setting auf eine Sicherheitsspanne zurueck, damit der Rettungswurf nie
     * ungestreut feuert — ein 0-Wert darf nicht dazu fuehren, dass das Feature ganz ausfaellt.
     */
    private boolean throwPearlDown() {
        if (drinkingFireRes) return false;
        FindItemResult pearl = InvHelper.find(Items.ENDER_PEARL);
        if (!pearl.found() || (!pearl.isOffhand() && !pearl.isHotbar())) return false;
        InteractionHand hand = pearl.isOffhand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        boolean swapped = false;
        if (!pearl.isOffhand() && !pearl.isMainHand()) {
            swapped = InvUtils.swap(pearl.slot(), true);
            if (!swapped) return false;
            cadence.onSlotChange();
        }
        final boolean didSwap = swapped;
        // s.o.: hand ist effectively final.
        double pitch = PitchVariance.apply(PEARL_DOWN_PITCH, pearlDownVariance.get(), rng);
        rotateAndRun(mc.player.getYRot(), pitch, PRIORITY_PEARL, () -> {
            if (mc.gameMode.useItem(mc.player, hand).consumesAction()) lastPearlTick = tickCounter;
            if (didSwap) InvUtils.swapBack();
        });
        return true;
    }

    /** Basiswinkel des senkrechten Rettungswurfs. Bewusst 80 statt 90 Grad: exakt 90 waere exakt der
     *  Winkel, den PitchVariance selbst als "maschinenperfekter Fingerzeig" benennt. */
    private static final double PEARL_DOWN_PITCH = 80.0;

    // ---------- Verfolgung ----------

    private void updateFollow(LivingEntity target) {
        if (!follow.get()) {
            cancelFollow();
            return;
        }

        // Jeden Tick neu setzen und NIE hart canceln, solange ein Ziel da ist - Baritones eigener
        // followRadius (siehe onActivate) haelt/loest den Nahkampf-Abstand von selbst, kontinuierlich
        // statt mit hartem cancelEverything()+Neuberechnung bei jedem Rein/Raus aus 3.5 Bloecken.
        var fp = BaritoneAPI.getProvider().getPrimaryBaritone().getFollowProcess();
        fp.follow(e -> e == target);
        followActive = true;
        followedId = target.getUUID();
    }

    private void cancelFollow() {
        if (followActive) {
            BaritoneAPI.getProvider().getPrimaryBaritone().getFollowProcess().cancel();
            BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().cancelEverything();
            followActive = false;
            followedId = null;
        }
    }

    // ---------- Falle ----------

    private void handleTrap(LivingEntity target) {
        int mode = trapMode.get();
        if (mode == 0 || target == null) return;

        double dist = Math.sqrt(mc.player.distanceToSqr(target));
        if (mode == 1 && dist > 6) return;
        // Bereits in Nahkampf-Reichweite: Trap bringt nichts mehr und der Rotations-Slot geht sonst
        // zulasten des naechsten Nahkampf-Schlags (siehe GodmodePvP fuer den vollen Kontext).
        if (dist <= attackRange.get()) return;
        if (mc.gui.screen() != null) return;
        if (tickCounter - lastTrapTick < 8 + rng.nextInt(8)) return; // menschlich unregelmaessiger Rhythmus

        BlockPos feet = target.blockPosition();
        if (feet.equals(mc.player.blockPosition())) return; // sonst web(t) sich der Bot bei Ueberlappung selbst ein
        if (!mc.level.getBlockState(feet).isAir()) return;
        if (!mc.level.getBlockState(feet.below()).blocksMotion()) return;

        // Platzierung, also Blockinteraktion 4.5 — der Cobweb muss wirklich erreichbar sein, sonst
        // sendet BlockUtils einen useItemOn, den der Server als Out-of-Range verwirft.
        if (!ReachPolicy.allows(ReachPolicy.Action.PLACE_BLOCK,
            mc.player.getEyePosition().distanceTo(Vec3.atBottomCenterOf(feet)))) return;

        FindItemResult web = InvHelper.find(Items.COBWEB);
        if (!web.found()) return;
        if (!allowAction(ActionCadence.Action.PLACE)) return;
        if (BlockUtils.place(feet, web, true, 50)) lastTrapTick = tickCounter;
    }

    // ---------- Inventar ----------

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
        FindItemResult r = InvUtils.find(item);
        return r.found() && (r.isHotbar() || r.isOffhand());
    }

    private boolean hasActionableItem(java.util.function.Predicate<ItemStack> pred) {
        FindItemResult r = InvUtils.find(pred);
        return r.found() && (r.isHotbar() || r.isOffhand());
    }

    private static boolean isHealingSplash(ItemStack stack) {
        if (!stack.is(Items.SPLASH_POTION)) return false;
        PotionContents pc = stack.get(DataComponents.POTION_CONTENTS);
        return pc != null && (pc.is(Potions.HEALING) || pc.is(Potions.STRONG_HEALING));
    }

    /** Wirft sofort eine Splash-Heiltraenke (Instant Health) zu den eigenen Fuessen, sobald frischer
     *  Schaden erkannt wird - der Trank zerschellt direkt am Boden und heilt augenblicklich. Laeuft
     *  unabhaengig vom Kampf-/Engage-Zustand.
     *
     *  Zwei Korrekturen gegenueber der ersten Version (siehe GodmodePvP fuer Details): Schaden wird ueber
     *  ein kurzes Zeitfenster (8 Ticks/0.4s) aufsummiert statt nur Tick-zu-Tick verglichen (ein Treffer
     *  verteilt sich oft ueber mehrere Ticks), und der Wurf wird waehrend `blocking`/`drinkingFireRes`
     *  komplett uebersprungen - beide teilen sich mit diesem Wurf denselben globalen
     *  InvUtils.previousSlot-Merkposten fuer ihren eigenen, laenger andauernden Hotbar-Swap; ein
     *  dazwischengefunkter swapBack() ueberschrieb den Merkposten und liess das Modul im Schild-/
     *  Trank-Zustand haengenbleiben bzw. auf die falsche Waffe wechseln. */
    private void maintainHealPotions(Player self) {
        if (healPotionCooldown > 0) healPotionCooldown--;

        float hp = self.getHealth();
        float dmg = hpAtHealWindowStart - hp;

        // Fenster erst verschieben, wenn entweder geheilt wurde ODER es veraltet ist UND gerade kein
        // nennenswerter Schaden ansteht - ein Reset auf den AKTUELLEN (gerade erst gefallenen) Wert im
        // selben Tick wie ein Treffer wuerde den Schaden loeschen, bevor er ueberhaupt geprueft wird.
        // Genau das liess einen Totem-Pop nach laengerer stabiler Gesundheit (>0.4s ohne HP-Aenderung -
        // der Normalfall zwischen zwei Treffern) komplett ungeheilt durch, weil die Alt-Logik das
        // "veraltete Fenster" IMMER zuerst zuruecksetzte, egal ob der aktuelle Tick selbst der Treffer war.
        if (hp > hpAtHealWindowStart || (tickCounter - healWindowStartTick > 8 && dmg < healMinDamage.get())) {
            hpAtHealWindowStart = hp;
            healWindowStartTick = tickCounter;
            dmg = hpAtHealWindowStart - hp;
        }

        if (dmg >= healMinDamage.get()) healingUntilFull = true;
        if (hp >= self.getMaxHealth() - 0.5f) healingUntilFull = false;

        if (!healPotions.get() || blocking || drinkingFireRes || healPotionCooldown > 0) return;
        if (!healingUntilFull) return;

        // Blind steil nach unten werfen (fixer Pitch 80) nahm an, dass immer fester Boden in Wurfnaehe
        // ist - siehe GodmodePvP.findNearbySplashTarget fuer die volle Begruendung. Zielt jetzt auf die
        // naechste feste Blockflaeche in Wurfreichweite; kein Ziel gefunden -> lieber gar nicht werfen.
        Vec3 splashTarget = findNearbySplashTarget(self);
        if (splashTarget == null) return;

        FindItemResult potion = InvHelper.find(HumanPvP::isHealingSplash);
        if (!potion.found()) return;

        double throwYaw = Rotations.getYaw(splashTarget);
        double throwPitch = Rotations.getPitch(splashTarget);

        if (potion.isOffhand()) {
            rotateAndRun(throwYaw, throwPitch, () -> {
                if (mc.gameMode.useItem(mc.player, InteractionHand.OFF_HAND).consumesAction()) healPotionCooldown = healCooldown.get();
            });
        } else if (potion.isHotbar()) {
            boolean swapped = !potion.isMainHand() && InvUtils.swap(potion.slot(), true);
            if (!potion.isMainHand() && !swapped) return;
            rotateAndRun(throwYaw, throwPitch, () -> {
                if (mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND).consumesAction()) healPotionCooldown = healCooldown.get();
                if (swapped) InvUtils.swapBack();
            });
        } else {
            return;
        }
        // hpAtHealWindowStart bewusst NICHT hier zuruecksetzen - healingUntilFull haelt den Heil-Modus
        // ueber mehrere Traenke hinweg aktiv, bis maxHealth-0.5 erreicht ist (siehe oben).
    }

    /** Siehe GodmodePvP.findNearbySplashTarget fuer die volle Begruendung: naechste feste Blockflaeche
     *  in Splash-Wurfreichweite (4 Bloecke), Boden bevorzugt, sonst Wand/Decke - null, wenn nirgends
     *  etwas in Reichweite ist. */
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
        if (countHotbar(item) >= min || totalItem(item) < min) return;
        int src = findMainSlotWith(item);
        int dst = hotbarTargetSlot(item);
        if (src < 0 || dst < 0) return;
        InvUtils.move().from(src).to(dst);
    }

    private void refill(java.util.function.Predicate<ItemStack> pred, int min) {
        if (countHotbar(pred) >= min || totalItem(pred) < min) return;
        int src = findMainSlotWith(pred);
        int dst = hotbarTargetSlot(pred);
        if (src < 0 || dst < 0) return;
        InvUtils.move().from(src).to(dst);
    }

    private void inventoryTick() {
        refill(Items.END_CRYSTAL, minCrystals.get());
        refill(Items.RESPAWN_ANCHOR, minAnchors.get());
        refill(Items.GLOWSTONE, minGlowstone.get());
        refill(Items.ENDER_PEARL, minPearls.get());
        refill(Items.OBSIDIAN, minObsidian.get());
        refill(Items.COBWEB, minWeb.get());
        refill(HumanPvP::isBed, minBeds.get());
        refill(HumanPvP::isHealingSplash, minHealPotionsStock.get());

        warnIfEmpty(Items.END_CRYSTAL, "End Crystals");
        warnIfEmpty(Items.ENDER_PEARL, "Enderperlen");
        warnIfEmpty(Items.OBSIDIAN, "Obsidian");
        warnIfEmpty(Items.TOTEM_OF_UNDYING, "Totems");
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
                supportSyncFailed = true;
            }
        } catch (Throwable t) {
            // savedSupport bewusst NICHT nullen: es ist der zuletzt GELESENE Wert von CrystalAura und
            // der einzige Weg zurueck zum Original. Verwischt, sichert der naechste erfolgreiche
            // Aufruf den selbst geschriebenen Wert als "Original" — der Nutzerwert waere dann weg.
            supportSyncFailed = true;
            error("CrystalAura-Support-Mode konnte nicht gesetzt werden (Meteor-Version geaendert?) - Obsidian-Unterbau bei freier Luft laeuft evtl. nicht automatisch. Weiche auf Anchor-Vorzug aus, solange das so bleibt.");
        }
        syncSupportDelay(ca);
    }

    private void syncSupportDelay(CrystalAura ca) {
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
            if (savedSupportDelay < 0) error("CrystalAura-Support-Delay konnte nicht gesetzt werden (Meteor-Version geaendert?) - Crystal-Platzierung nach Obsidian-Unterbau kann dadurch auf langsameren Servern manchmal fehlschlagen.");
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

    private boolean safeEnable(Modules m, Class<? extends Module> clazz) {
        Module mod = m.get(clazz);
        if (mod != null && !mod.isActive()) {
            mod.toggle();
            return true;
        }
        return false;
    }

    private void safeDisable(Modules m, Class<? extends Module> clazz) {
        Module mod = m.get(clazz);
        if (mod != null && mod.isActive()) mod.toggle();
    }
}
