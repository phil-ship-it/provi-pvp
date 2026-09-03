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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
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

    public final Setting<Boolean> preHit = sgCombat.add(new BoolSetting.Builder()
        .name("pre-hit")
        .description("Schlaegt den Gegner vor der Explosion fuer mehr Schaden.")
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
        .description("Wenn der Bot selbst durch Knockback (Schlag/Explosion) in die Luft geschleudert wird, sofort senkrecht nach unten perlen - kommt kontrolliert runter statt hilflos zu fallen/als Ziel in der Luft zu haengen.")
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
    private boolean outOfGlowstone;
    private int anchorPlaceFails;
    private int crystalForcedUntil;
    private int anchorUnreachableTicks;
    private BlockPos anchorCalcOrigin;
    private int lastAuraSwitch;

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
            if (zeroDelay.get()) {
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
        if (tickCounter % 20 == 0) syncMobFilter();

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

        boolean guiOpen = mc.gui.screen() != null;
        if (!guiOpen) {
            if (fastTotem.get()) ensureOffhandTotem();
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
            mc.player.setYRot((float) Rotations.getYaw(lookAt));
            mc.player.setXRot((float) Rotations.getPitch(lookAt));
        }

        // Stuck-Erkennung: eigene Position + Ziel-HP beobachten
        double selfMoved = lastSelfPos == null ? 999 : self.position().distanceTo(lastSelfPos);
        lastSelfPos = self.position();

        // Anti-Rubberband: ploetzlicher, nicht selbst verursachter Sprung -> Server hat uns korrigiert.
        // Baritones Pfad verwerfen und kurz bremsen statt gegen die Korrektur anzukaempfen.
        // Explosions-Knockback (spuerbarer HP-Verlust im selben Tick) zaehlt NICHT als Rubberband -
        // sonst wird genau der Moment ignoriert, in dem der Bot eigentlich fliehen muesste.
        boolean tookRealDamage = lastSelfHpForRubberband >= 0 && self.getHealth() < lastSelfHpForRubberband - 1.0f;
        lastSelfHpForRubberband = self.getHealth();
        if (rubberbandCooldown > 0) {
            rubberbandCooldown--;
        } else if (antiRubberband.get() && selfMoved > 2.5 && !tookRealDamage && tickCounter - lastPearlTick > 10) {
            BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().cancelEverything();
            followActive = false;
            rubberbandCooldown = 10;
            currentAction = "rubberband";
        }

        // Durch Knockback (Schlag/Explosion) in die Luft geschleudert: spuerbarer Schaden + starke
        // Aufwaerts-Geschwindigkeit im selben Tick - sofort senkrecht nach unten perlen, um kontrolliert
        // runterzukommen statt hilflos zu fallen oder als leichtes Ziel in der Luft zu haengen.
        if (knockbackPearl.get() && tookRealDamage && self.getDeltaMovement().y > 0.35
            && tickCounter - lastPearlTick > 15
            && (InvUtils.findInHotbar(Items.ENDER_PEARL).found() || InvUtils.find(Items.ENDER_PEARL).found())) {
            throwPearlDown();
            currentAction = "pearl-knockback";
            return;
        }

        boolean blockedByObstacle = dist <= 6.0 && !self.hasLineOfSight(target);
        obstacleStuckTicks = blockedByObstacle ? obstacleStuckTicks + 1 : 0;

        boolean targetHpChanged = lastTargetHpForStuck < 0 || Math.abs(target.getHealth() - lastTargetHpForStuck) > 0.01f;
        lastTargetHpForStuck = target.getHealth();
        watchdogStuckTicks = (selfMoved < 0.05 && !targetHpChanged) ? watchdogStuckTicks + 1 : 0;

        // Kein Sichtkontakt trotz Naehe (Hindernis im Weg): so gut wie sofort reagieren,
        // klappt das nicht, zum Gegner perlen statt festzustehen.
        if (obstacleStuckTicks > 1 && pearlThrow.get() && tickCounter - lastPearlTick > 20
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
            && escapePearl.get() && tickCounter - lastPearlTick > 15
            && (InvUtils.findInHotbar(Items.ENDER_PEARL).found() || InvUtils.find(Items.ENDER_PEARL).found())) {
            throwPearl(target, true);
            currentAction = "escape-cage";
            return;
        }

        // Notfall-Flucht: kritisches HP -> Perle weg statt sinnlos weiterzukaempfen, egal wie weit der Gegner ist
        if (escapePearl.get() && self.getHealth() <= 6.0f
            && tickCounter - lastPearlTick > 20
            && (InvUtils.findInHotbar(Items.ENDER_PEARL).found() || InvUtils.find(Items.ENDER_PEARL).found())) {
            throwPearl(target, true);
            currentAction = "escape-pearl";
            return;
        }

        // Rueckzugs-Schwelle: Totems knapp UND keine Explosiv-Ressourcen mehr -> Gefecht abbrechen statt
        // aussichtslos im reinen Nahkampf weiterzumachen.
        if (retreatThreshold.get() && lowOnTotems && warnedOutOfCrystals && warnedOutOfAnchorSupply) {
            cancelFollow();
            if (dist <= 10.0 && tickCounter - lastPearlTick > 30
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
            && tickCounter - lastTargetDamageTick > 60 && tickCounter - lastPearlTick > 30
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
            if (dist <= 3.6 && self.getAttackStrengthScale(0.5f) >= 0.9f && self.hasLineOfSight(target) && prepareCritAndCheck(dist)) attackMelee(target);
            selectAura(target);
            currentAction = "burst";
        }

        // Perlen-Gapclose (bei grosser Distanz schnellerer Cooldown) - nur wenn schon engaged (siehe unten),
        // sonst wuerde auch ein 35 Blocke entfernter Spieler beim Kaltstart sofort angeperlt.
        long pearlCooldown = dist > 15 ? 8 : 10;
        if (pearlThrow.get() && dist > pearlMinDist.get() && engaged
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
            } else if (auraMode == 0) {
                // Selbstheilung: falls CrystalAura extern/durch einen Fehler ausgegangen ist, wieder anschalten
                Module ca = Modules.get().get(CrystalAura.class);
                if (ca != null && !ca.isActive()) ca.toggle();
            }
        }

        if (shieldBreaker.get() && target instanceof Player p && p.isBlocking()) {
            breakShield(p);
            currentAction = "schild-brechen";
        } else if (preHit.get() && dist <= 3.6 && self.getAttackStrengthScale(0.5f) >= 0.9f
            && self.hasLineOfSight(target) && explosionImminent(target) && prepareCritAndCheck(dist)) {
            attackMelee(target);
            currentAction = "pre-hit";
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
            anchorPlaceCooldown = 6; // kurze Pause, damit maintainNearbyAnchors Zeit zum Laden hat
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
                        interactAnchorAt(pos, fir);
                        anchorMaintCooldown = 3;
                        return;
                    } else {
                        FindItemResult gs = InvUtils.findInHotbar(Items.GLOWSTONE);
                        if (!gs.found()) gs = InvUtils.find(Items.GLOWSTONE);
                        if (!gs.found()) {
                            outOfGlowstone = true;
                            continue;
                        }
                        interactAnchorAt(pos, gs);
                        anchorMaintCooldown = 3;
                        return;
                    }
                }
            }
        }
        anchorMaintCooldown = 1; // nichts gefunden - naechster voller Scan erst naechsten Tick statt jeden Tick doppelt
    }

    private void interactAnchorAt(BlockPos pos, FindItemResult item) {
        Vec3 center = Vec3.atCenterOf(pos);
        Rotations.rotate(Rotations.getYaw(center), Rotations.getPitch(center), () -> {
            boolean swapped = InvUtils.swap(item.slot(), true);
            BlockUtils.interact(new BlockHitResult(center, BlockUtils.getDirection(pos), pos, true), InteractionHand.MAIN_HAND, true);
            if (swapped) InvUtils.swapBack();
        });
    }

    // ---------- Aura-Steuerung ----------

    private void selectAura(LivingEntity target) {
        Vec3 predicted = predict(target);
        double crystalDmg = bestDamageAround(target, predicted, true);

        // Anchor-Kandidaten anhand der AKTUELLEN Position berechnen (nicht der Vorhersage - der Bot muss erst
        // noch hinlaufen, eine extrapolierte Position waere bei schnellen/fliegenden Zielen komplett daneben).
        // Neu berechnen, wenn die alte Liste durch ist ODER sich das Ziel > 2 Bloecke bewegt hat.
        if (useAnchors.get() && anchorMode.get() != 2) {
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
        if (anchorCandidateIndex < anchorCandidates.size()) {
            BlockPos best = anchorCandidates.get(anchorCandidateIndex);
            bestAnchorDmgCache = DamageUtils.anchorDamage(target, Vec3.atCenterOf(best));
        }

        Module ca = Modules.get().get(CrystalAura.class);
        if (ca == null) return;

        // Anchor nur im Nahbereich - sonst Crystal, damit es nie totlaeuft
        boolean inRange = mc.player.distanceToSqr(target) < 5.5 * 5.5;

        // Anchor-Plaetze unerreichbar? -> 2 s Crystal erzwingen (Anti-Stuck)
        boolean anchorForced = tickCounter < crystalForcedUntil;

        // Hysterese statt scharfer Schwelle: zum Wechsel IN den Anchor-Modus braucht es einen klaren
        // Vorsprung, zum Bleiben reicht Gleichstand. Ohne das kippt der Modus bei jedem winzigen
        // Schadens-Unterschied (z.B. durch die Ziel-Vorhersage) mehrfach pro Sekunde hin und her -
        // jedes Mal ein voller CrystalAura-Neustart, der wie ein Ruckeln/Haken wirkt.
        double margin = anchorMode.get() == 1 ? 0.0 : 0.15;
        double enterMargin = auraMode == 1 ? -0.3 : margin;

        boolean wantAnchor;
        if (anchorMode.get() == 1) {
            // Anchor schon bei Gleichstand (bricht Schilde)
            wantAnchor = !outOfGlowstone && !anchorForced && inRange
                && anchorCandidateIndex < anchorCandidates.size()
                && bestAnchorDmgCache >= crystalDmg + enterMargin;
        } else {
            wantAnchor = !outOfGlowstone && !anchorForced && inRange && bestAnchorDmgCache > crystalDmg + 0.15 + enterMargin;
        }

        // Deutlich seltener umschalten (0.5s statt 0.15s) - genug Zeit, damit eine begonnene
        // Platzierung/Ladung auch tatsaechlich fertig wird, statt staendig unterbrochen zu werden.
        if (tickCounter - lastAuraSwitch < 10) return;

        if (wantAnchor && auraMode != 1) {
            if (ca.isActive()) ca.toggle();
            auraMode = 1;
            lastAnchorProgressTick = tickCounter;
            lastAuraSwitch = tickCounter;
        } else if (!wantAnchor && auraMode != 0) {
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
        if (auraMode == 1) return true;
        if (auraMode == 0) {
            Module ca = Modules.get().get(CrystalAura.class);
            return ca != null && ca.isActive();
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

        BlockState below = mc.level.getBlockState(cell.below());
        if (crystal) return below.is(Blocks.OBSIDIAN) || below.is(Blocks.BEDROCK) || below.isAir();
        return below.blocksMotion() && mc.level.getBlockState(cell.above()).isAir();
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

                placeCrystal(dtapSpot, crystal);
                dtapStage = 2;
                dtapStageTick = tickCounter;
            }
            case 2 -> { // 1. Crystal steht - sofort zuenden
                EndCrystal ec = findCrystalAbove(dtapSpot);
                if (ec == null) {
                    if (tickCounter - dtapStageTick > 4) dtapStage = 0; // nie angekommen
                    return;
                }
                attackCrystal(ec);
                dtapStage = 3;
                dtapStageTick = tickCounter;
            }
            case 3 -> { // Trefferimmunitaet abwarten (~10 Ticks = 0.5s), dann 2. Crystal
                if (tickCounter - dtapStageTick < 10) return;
                if (tickCounter - dtapStageTick > 30 || !mc.level.getBlockState(dtapSpot.above()).isAir()) {
                    dtapStage = 0;
                    dtapCooldown = 30;
                    return;
                }

                FindItemResult crystal = InvUtils.findInHotbar(Items.END_CRYSTAL);
                if (!crystal.found()) crystal = InvUtils.find(Items.END_CRYSTAL);
                if (!crystal.found()) { dtapStage = 0; return; }

                placeCrystal(dtapSpot, crystal);
                dtapStage = 4;
                dtapStageTick = tickCounter;
            }
            case 4 -> { // 2. Crystal steht - zuenden, fertig
                EndCrystal ec = findCrystalAbove(dtapSpot);
                if (ec == null) {
                    if (tickCounter - dtapStageTick > 4) { dtapStage = 0; dtapCooldown = 30; }
                    return;
                }
                attackCrystal(ec);
                dtapStage = 0;
                dtapCooldown = 40;
            }
            default -> dtapStage = 0;
        }
    }

    private void placeCrystal(BlockPos floor, FindItemResult item) {
        Vec3 center = Vec3.atCenterOf(floor);
        Rotations.rotate(Rotations.getYaw(center), Rotations.getPitch(center), () -> {
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

    private void attackCrystal(EndCrystal ec) {
        Vec3 center = ec.getBoundingBox().getCenter();
        Rotations.rotate(Rotations.getYaw(center), Rotations.getPitch(center), () -> {
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

    /** Dreht sich im Nahkampf direkt zum Ziel und kreis-strafet - schwerer zu treffen, variiert den
     *  Explosionswinkel automatisch. Ausserhalb der Nahkampfreichweite werden die Strafe-Tasten geloest,
     *  damit Baritones eigene Bewegung nicht gestoert wird. */
    private void updateCombatMovement(LivingEntity target, double dist) {
        if (dist > 3.6) {
            Input.setKeyState(mc.options.keyLeft, false);
            Input.setKeyState(mc.options.keyRight, false);
            nextStrafeSwitchTick = -1; // frisches, zufaelliges Intervall beim naechsten Nahkampf-Eintritt
            return;
        }

        Vec3 center = target.getBoundingBox().getCenter();
        mc.player.setYRot((float) Rotations.getYaw(center));
        mc.player.setXRot((float) Rotations.getPitch(center));

        // Zufaellig getaktete Richtungswechsel (10-24 Ticks, 0.5-1.2s) statt eines starren 20-Tick-Rhythmus -
        // ein exakt periodisches Strafing ist leicht zu lesen (fuer Gegner UND Anti-Cheat-Heuristiken),
        // echte Spieler wechseln unregelmaessig.
        if (tickCounter >= nextStrafeSwitchTick) {
            strafeLeft = !strafeLeft;
            nextStrafeSwitchTick = tickCounter + 10 + rng.nextInt(15);
        }
        Input.setKeyState(mc.options.keyLeft, strafeLeft);
        Input.setKeyState(mc.options.keyRight, !strafeLeft);
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
            || currentAction.equals("anchor") || currentAction.equals("schild-brechen");
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
     *  aktuelle eigene Position und in vertretbarer Laufdistanz liegen, zaehlen. */
    private BlockPos findLowestAroundTarget(Player self, LivingEntity target) {
        BlockPos targetPos = target.blockPosition();
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

    /** Notdeckung: platziert einen Obsidian-Block an einer offenen Seite, wenn kein natuerliches Loch da ist. */
    private void buildOwnCover(Player self) {
        FindItemResult obsidian = InvUtils.findInHotbar(Items.OBSIDIAN);
        if (!obsidian.found()) obsidian = InvUtils.find(Items.OBSIDIAN);
        if (!obsidian.found()) return;

        BlockPos feet = self.blockPosition();
        for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.Plane.HORIZONTAL) {
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
        if ((!holeAwareness.get() && !heightAdvantage.get()) || dist > 8) {
            activeHole = null;
            return false;
        }

        BlockPos best = findBestPosition(mc.player, target);

        if (best == null) {
            activeHole = null;
            if (buildCover.get() && dist <= 4.5) {
                buildOwnCover(mc.player);
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
        Player bestReal = null;
        double bestRealDist = followRange.get() * followRange.get();
        Player bestFake = null;
        double bestFakeDist = followRange.get() * followRange.get();

        for (Player p : mc.level.players()) {
            if (p == self || !p.isAlive() || p.isSpectator()) continue;
            boolean isFake = p instanceof FakePlayerEntity;
            if (p.isCreative() && !isFake) continue;

            double d = self.distanceToSqr(p);
            if (isFake) {
                if (d < bestFakeDist) {
                    bestFakeDist = d;
                    bestFake = p;
                }
            } else if (d < bestRealDist) {
                bestRealDist = d;
                bestReal = p;
            }
        }
        return bestReal != null ? bestReal : bestFake;
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

        lastPearlTick = tickCounter;

        if (pearl.isOffhand()) {
            Rotations.rotate(yaw, pitch, () -> mc.gameMode.useItem(mc.player, InteractionHand.OFF_HAND));
        } else {
            boolean swapped = InvUtils.swap(pearl.slot(), true);
            Rotations.rotate(yaw, pitch, () -> {
                mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                if (swapped) InvUtils.swapBack();
            });
        }
    }

    /** Perle senkrecht nach unten - teleportiert bei Landung, kein unkontrolliertes Fallen nach Knockback. */
    private void throwPearlDown() {
        FindItemResult pearl = InvUtils.findInHotbar(Items.ENDER_PEARL);
        if (!pearl.found()) pearl = InvUtils.find(Items.ENDER_PEARL);
        if (!pearl.found()) return;

        lastPearlTick = tickCounter;

        if (pearl.isOffhand()) {
            Rotations.rotate(mc.player.getYRot(), 80, () -> mc.gameMode.useItem(mc.player, InteractionHand.OFF_HAND));
        } else {
            boolean swapped = InvUtils.swap(pearl.slot(), true);
            Rotations.rotate(mc.player.getYRot(), 80, () -> {
                mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                if (swapped) InvUtils.swapBack();
            });
        }
    }

    private void ensureOffhandTotem() {
        ItemStack off = mc.player.getItemInHand(InteractionHand.OFF_HAND);
        if (off.is(Items.TOTEM_OF_UNDYING)) return;

        FindItemResult totem = InvUtils.find(Items.TOTEM_OF_UNDYING);
        if (totem.found()) InvUtils.move().from(totem.slot()).toOffhand();
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

    private int totalItem(net.minecraft.world.item.Item item) {
        FindItemResult r = InvUtils.find(item);
        return r.found() ? r.count() : 0;
    }

    private int findMainSlotWith(net.minecraft.world.item.Item item) {
        for (int i = 9; i <= 35; i++) {
            if (mc.player.getInventory().getItem(i).is(item)) return i;
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

    private void refill(net.minecraft.world.item.Item item, int min) {
        if (countHotbar(item) >= min) return;
        if (totalItem(item) <= min) return;

        int src = findMainSlotWith(item);
        int dst = hotbarTargetSlot(item);
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
