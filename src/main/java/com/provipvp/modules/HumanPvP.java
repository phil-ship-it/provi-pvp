package com.provipvp.modules;

import baritone.api.BaritoneAPI;
import meteordevelopment.meteorclient.MeteorClient;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
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
    private final SettingGroup sgCombat = settings.createGroup("Kampf");
    private final SettingGroup sgDefense = settings.createGroup("Schutz");
    private final SettingGroup sgInv = settings.createGroup("Inventar");
    private final SettingGroup sgPearl = settings.createGroup("Enderperlen");
    private final SettingGroup sgHeal = settings.createGroup("Heilung");

    // General
    public final Setting<Boolean> follow = sgGeneral.add(new BoolSetting.Builder()
        .name("follow")
        .description("Verfolgt das Ziel mit Baritone - vorsichtige, menschentaugliche Pfade.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Integer> followRange = sgGeneral.add(new IntSetting.Builder()
        .name("follow-range")
        .description("Maximale Distanz, ab der ein Spieler ueberhaupt als Ziel erkannt/beobachtet wird.")
        .defaultValue(20)
        .range(6, 48)
        .sliderRange(6, 32)
        .build()
    );

    public final Setting<Integer> engageDistance = sgGeneral.add(new IntSetting.Builder()
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
        .description("Bevorzugt bei der Zielwahl einen isolierten Gegner (ohne Mitspieler in Rueckendeckungs-Reichweite) vor reiner Distanz. Wirkt nur auf die ANFANGS-Zielwahl, ein bereits engagiertes Ziel wird nicht mehr gewechselt.")
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

    public final Setting<Integer> reactionMinTicks = sgGeneral.add(new IntSetting.Builder()
        .name("reaction-min")
        .description("Minimale Reaktionszeit (Ticks) auf ein neues Ziel, bevor angegriffen wird.")
        .defaultValue(3)
        .range(0, 15)
        .sliderRange(0, 12)
        .build()
    );

    public final Setting<Integer> reactionMaxTicks = sgGeneral.add(new IntSetting.Builder()
        .name("reaction-max")
        .description("Maximale Reaktionszeit (Ticks) auf ein neues Ziel.")
        .defaultValue(9)
        .range(1, 25)
        .sliderRange(1, 20)
        .build()
    );

    public final Setting<Boolean> freeLook = sgGeneral.add(new BoolSetting.Builder()
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

    public final Setting<Integer> minSupportDelay = sgCombat.add(new IntSetting.Builder()
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

    public final Setting<Boolean> knockbackPearl = sgCombat.add(new BoolSetting.Builder()
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

    public final Setting<Double> maxSelfDamage = sgCombat.add(new DoubleSetting.Builder()
        .name("max-self-damage")
        .description("Maximaler Eigenschaden pro Angriffsplatz (konservativer als V1).")
        .defaultValue(6.0)
        .range(2.0, 12.0)
        .sliderRange(2.0, 10.0)
        .build()
    );

    public final Setting<Double> attackChance = sgCombat.add(new DoubleSetting.Builder()
        .name("attack-chance")
        .description("Wahrscheinlichkeit, dass ein bereiter Schlag wirklich ausgefuehrt wird (menschliches Verklicken).")
        .defaultValue(0.9)
        .range(0.5, 1.0)
        .sliderRange(0.5, 1.0)
        .build()
    );

    public final Setting<Double> aimTolerance = sgCombat.add(new DoubleSetting.Builder()
        .name("aim-tolerance")
        .description("Ziel-Toleranz in Grad, bevor geschlagen oder platziert wird.")
        .defaultValue(4.0)
        .range(1.0, 10.0)
        .sliderRange(1.0, 8.0)
        .build()
    );

    public final Setting<Double> maxTurnPerTick = sgCombat.add(new DoubleSetting.Builder()
        .name("max-turn-speed")
        .description("Maximale Kamera-Drehung pro Tick in Grad (menschliches Tempo statt Sofort-Snap).")
        .defaultValue(18.0)
        .range(5.0, 45.0)
        .sliderRange(5.0, 40.0)
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

    public final Setting<Boolean> escapePearl = sgDefense.add(new BoolSetting.Builder()
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
        .description("Legt knapp gewordene Combat-Items (Crystals, Anchors, Glowstone, Perlen, Obsidian, Web) aus dem Hauptinventar in die Hotbar nach.")
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
        .description("Ab dieser Distanz wird eine Perle geworfen.")
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
        .description("Mindestens so viel HP muessen seit dem letzten Tick verloren gegangen sein, damit ueberhaupt ein Trank geworfen wird - verhindert Trankverschwendung bei jedem winzigen Kratzer.")
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
    private int tickCounter;
    private int lastErrorWarnTick = -999;
    private int lastPearlTick = -999;
    // Siehe GodmodePvP fuer die volle Erklaerung: Meteors Rotations-Queue fuehrt bei mehreren im selben
    // Tick angemeldeten Rotationen nur die ERSTE mit der tatsaechlich gesetzten Blickrichtung aus - jede
    // weitere bekommt beim Ausfuehren ihres Callbacks schon wieder die alte Rotation zurueckgesetzt.
    private boolean rotationQueuedThisTick;
    private boolean pendingFreeLook;
    private double pendingFreeLookYaw, pendingFreeLookPitch;
    private int sprintResetCooldown;
    private float lastSelfHpForKnockback = -1;
    private Vec3 lastSelfPos;
    private int rubberbandCooldown;
    private final Map<net.minecraft.world.item.Item, Boolean> warnedOutOfMisc = new HashMap<>();
    private String currentAction = "-";
    private boolean blocking;
    private boolean blockingSwapBack;
    private int shieldUntil;
    private int lastCrystalCount = -1;
    private int savedPlaceDelay = -1;
    private CrystalAura.SupportMode savedSupport;
    private int savedSupportDelay = -1;
    private boolean followActive;
    private UUID followedId;

    private UUID engagedId;
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

    private float hpAtHealWindowStart = -999;
    private int healWindowStartTick = -999;
    private int healPotionCooldown;

    private record BedSpot(BlockPos pos, Direction dir) {}

    private int lastTrapTick = -999;
    private int nextTotemCheckTick = -999;
    private boolean drinkingFireRes;
    private int fireResStartTick = -999;

    public HumanPvP() {
        super(Categories.Combat, "human-pvp", "ProviPvP V2: menschlich wirkender Kampf-Bot (Reaktionszeit, sichtbare Rotation, Klick-Jitter). Kein Unerkennbarkeits-Versprechen. Befehl: .hpvp");
    }

    @Override
    public void onActivate() {
        tickCounter = 0;
        lastErrorWarnTick = -999;
        drinkingFireRes = false;
        fireResStartTick = -999;
        sprintResetCooldown = 0;
        lastSelfHpForKnockback = -1;
        lastSelfPos = null;
        rubberbandCooldown = 0;
        blocking = false;
        blockingSwapBack = false;
        shieldUntil = 0;
        lastCrystalCount = -1;
        followActive = false;
        followedId = null;
        engagedId = null;
        engageAtTick = 0;
        nextClickTick = -1;
        auraMode = -1;
        lastAuraSwitch = -999;
        anchorStage = 0;
        anchorCooldown = 0;
        anchorCandidates.clear();
        anchorCandidateIndex = 0;
        anchorCalcOrigin = null;
        bedPos = null;
        bedStage = 0;
        bedStageDeadline = 0;
        bedCooldown = 0;
        bedCandidates.clear();
        bedCandidateIndex = 0;
        bedCalcOrigin = null;
        bestBedDmgCache = 0;
        hpAtHealWindowStart = -999;
        healWindowStartTick = -999;
        healPotionCooldown = 0;
        lastTrapTick = -999;
        nextTotemCheckTick = -999;
        lastPositions.clear();

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

        if (autoMendOn.get()) safeEnable(m, AutoMend.class);
        if (autoEatOn.get()) safeEnable(m, AutoEat.class);
        if (noFallOn.get()) safeEnable(m, NoFall.class);

        MeteorClient.EVENT_BUS.subscribe(this);

        info("ProviPvP V2 (human) aktiv. Rechtsklick auf das Modul im Meteor-Menue zum Keybind. Befehl: .hpvp");
    }

    @Override
    public void onDeactivate() {
        MeteorClient.EVENT_BUS.unsubscribe(this);

        Modules m = Modules.get();
        safeDisable(m, CrystalAura.class);
        safeDisable(m, AutoMend.class);
        safeDisable(m, AutoEat.class);
        safeDisable(m, NoFall.class);

        CrystalAura ca = m.get(CrystalAura.class);
        if (ca != null && savedPlaceDelay >= 0) ca.placeDelay.set(savedPlaceDelay);
        if (ca != null) restoreSupport(ca);
        savedPlaceDelay = -1;

        cancelFollow();

        if (blocking) {
            if (blockingSwapBack) InvUtils.swapBack();
            blocking = false;
            blockingSwapBack = false;
        }
        if (drinkingFireRes) {
            InvUtils.swapBack();
            drinkingFireRes = false;
        }
        warnedOutOfMisc.clear();
        Input.setKeyState(mc.options.keySprint, false);
        mc.player.setSprinting(false);

        info("ProviPvP V2 aus.");
    }

    @EventHandler
    public void onTick(TickEvent.Pre event) {
        if (!Utils.canUpdate()) return;

        tickCounter++;

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

    private void doTick() {
        Player self = mc.player;
        currentAction = "-";
        rotationQueuedThisTick = false;
        pendingFreeLook = false;
        boolean guiOpen = mc.gui.screen() != null;
        // Nur eine echte Fremd-Container-GUI hat ein anderes containerMenu als das normale Inventar -
        // Meteor-ClickGUI/eigenes Inventar teilen sich inventoryMenu, Totem-Nachlegen darf da weiterlaufen.
        boolean foreignContainerOpen = mc.player.containerMenu != mc.player.inventoryMenu;
        if (fastTotem.get() && tickCounter >= nextTotemCheckTick && !foreignContainerOpen) ensureOffhandTotem();
        if (!foreignContainerOpen) maintainHealPotions(self);
        maintainFireResistance();

        if (!guiOpen) {
            if (invManager.get() && tickCounter % 20 == 0) inventoryTick();
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
        if (target == null) {
            resetEngagement();
            cancelFollow();
            currentAction = "beobachten";
            return;
        }

        if (!target.getUUID().equals(engagedId)) {
            engagedId = target.getUUID();
            int span = Math.max(1, reactionMaxTicks.get() - reactionMinTicks.get() + 1);
            engageAtTick = tickCounter + reactionMinTicks.get() + rng.nextInt(span);
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
            rubberbandCooldown = 10;
            currentAction = "rubberband";
        }
        boolean launchedByHit = tookHit && self.getDeltaMovement().y > 0.35;
        boolean fallingDanger = !self.onGround() && self.fallDistance > 3.0f && self.getDeltaMovement().y < 0.05;
        if (knockbackPearl.get() && (launchedByHit || fallingDanger)
            && tickCounter - lastPearlTick > 15
            && (InvUtils.findInHotbar(Items.ENDER_PEARL).found() || InvUtils.find(Items.ENDER_PEARL).found())) {
            throwPearlDown();
            currentAction = launchedByHit ? "pearl-knockback" : "pearl-fallschutz";
            return;
        }
        manageSprintForKnockback(dist);

        if (escapePearl.get() && self.getHealth() <= 8.0f && dist <= 6.0
            && tickCounter - lastPearlTick > 30
            && (InvUtils.findInHotbar(Items.ENDER_PEARL).found() || InvUtils.find(Items.ENDER_PEARL).found())) {
            throwPearl(target, true);
            currentAction = "escape-pearl";
            return;
        }

        // Feindlicher Crystal frisch platziert (in 5 m)? -> kurz blocken
        java.util.List<net.minecraft.world.entity.boss.enderdragon.EndCrystal> nearCrystals =
            mc.level.getEntitiesOfClass(net.minecraft.world.entity.boss.enderdragon.EndCrystal.class, self.getBoundingBox().inflate(5));
        if (lastCrystalCount >= 0 && nearCrystals.size() > lastCrystalCount && !nearCrystals.isEmpty()
            && autoShield.get() && !blocking) {
            shieldUntil = tickCounter + 15; // Crystal zuendet praktisch sofort - kurzes, hartes Block-Fenster
            startBlock();
        }
        lastCrystalCount = nearCrystals.size();

        Vec3 aim = aimPoint(target);
        smoothLookAt(aim);
        double aimError = currentAimError(aim);

        if (follow.get() && pursuing) {
            updateFollow(target);
        } else {
            cancelFollow();
        }

        if (tickCounter < engageAtTick) {
            currentAction = "reagieren";
            return;
        }

        handleTrap(target);

        // Schwelle an attack-range gekoppelt (nie kleiner als Nahkampf-Reichweite+0.5) und Sichtlinie
        // Pflicht - sonst fliegt die Perle nur gegen die Wand/den Huegel dazwischen statt zum Gegner.
        double pearlReachThreshold = Math.max(pearlMinDist.get(), attackRange.get() + 0.5);
        if (pearlThrow.get() && dist > pearlReachThreshold && pursuing && self.hasLineOfSight(target)
            && tickCounter - lastPearlTick > pearlCooldown(dist) && !guiOpen) {
            throwPearl(target, false);
            currentAction = "pearl-gapclose";
        }

        selectAura(target);

        if (auraMode == 1) {
            runAnchorTick(target, aimError);
            currentAction = "anchor";
        } else if (auraMode == 2) {
            runBedTick(target, aimError);
            currentAction = "bed";
        }

        if (shieldBreaker.get() && target instanceof Player p && p.isBlocking()) {
            breakShield(p);
            currentAction = "schild-brechen";
        } else if (dist <= attackRange.get() && aimError <= aimTolerance.get() && self.hasLineOfSight(target)
            && self.getAttackStrengthScale(0.5f) >= 0.95f && readyToClick()) {
            attackMelee(target);
            currentAction = "schlagen";
        }

        if (currentAction.equals("-")) currentAction = auraMode == 0 ? "crystal" : "zielen";

        // Cosmetic Ziel-Verfolgung (free-look) nur anwenden, wenn diesen Tick noch keine echte
        // Kampfaktion (Perlwurf, Anchor-Interaktion, ...) den gemeinsamen Rotations-Slot belegt hat -
        // sonst wuerde diese rein optische Drehung lautlos vor der eigentlichen Aktion in Meteors
        // Rotations-Queue landen und deren Callback mit der alten, zurueckgesetzten Blickrichtung
        // ausfuehren (siehe rotateAndRun-Dokumentation).
        if (pendingFreeLook && !rotationQueuedThisTick) {
            rotationQueuedThisTick = true;
            Rotations.rotate(pendingFreeLookYaw, pendingFreeLookPitch);
        }
    }

    @Override
    public String getInfoString() {
        return currentAction;
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

    private boolean readyToClick() {
        if (nextClickTick < 0) {
            nextClickTick = tickCounter + 1 + rng.nextInt(3);
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
        delta %= 360f;
        if (delta >= 180f) delta -= 360f;
        if (delta < -180f) delta += 360f;
        return delta;
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
        return Math.max(-max, Math.min(max, v));
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

    private void ensureOffhandTotem() {
        ItemStack off = mc.player.getItemInHand(InteractionHand.OFF_HAND);
        if (off.is(Items.TOTEM_OF_UNDYING)) return;

        FindItemResult totem = InvUtils.find(Items.TOTEM_OF_UNDYING);
        if (totem.found()) {
            InvUtils.move().from(totem.slot()).toOffhand();
            nextTotemCheckTick = tickCounter + 1 + rng.nextInt(3); // kleine Verzoegerung statt Sofort-Reflex
        }
    }

    // ---------- Anchor-Executor (humanisiert: sichtbare Rotation + zufaellige Wartezeiten) ----------

    private void runAnchorTick(LivingEntity target, double aimError) {
        if (drinkingFireRes) return; // s.o. - Swap-Merkposten waehrend des Trinkens nicht anfassen
        if (anchorCooldown > 0) {
            anchorCooldown--;
            return;
        }

        Player self = mc.player;

        switch (anchorStage) {
            case 0 -> {
                BlockPos spot = nextAnchorCandidate();
                if (spot == null) return;

                double d = Math.sqrt(self.distanceToSqr(Vec3.atCenterOf(spot)));
                if (d > 4.0) return; // naechster Tick neuer Versuch, Baritone laeuft naeher

                smoothLookAt(Vec3.atCenterOf(spot));
                if (currentAimError(Vec3.atCenterOf(spot)) > aimTolerance.get()) return; // erst ausrichten

                FindItemResult anchor = InvUtils.findInHotbar(Items.RESPAWN_ANCHOR);
                if (!anchor.found()) anchor = InvUtils.find(Items.RESPAWN_ANCHOR);
                if (!anchor.found()) return;

                boolean swapped = InvUtils.swap(anchor.slot(), true);
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

                FindItemResult gs = InvUtils.findInHotbar(Items.GLOWSTONE);
                if (!gs.found()) gs = InvUtils.find(Items.GLOWSTONE);
                if (!gs.found()) {
                    anchorStage = 0;
                    anchorCandidates.clear();
                    anchorCandidateIndex = 0;
                    return;
                }

                if (interactAnchor(gs, aimError)) {
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

                FindItemResult fir = InvUtils.findInHotbar(itemStack -> !itemStack.isEmpty() && !itemStack.is(Items.GLOWSTONE));
                if (!fir.found()) fir = InvUtils.find(itemStack -> !itemStack.isEmpty() && !itemStack.is(Items.GLOWSTONE));
                if (!fir.found()) return;

                if (interactAnchor(fir, aimError)) {
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

    /** Interagiert nur, wenn die (sichtbare, tempolimitierte) Rotation schon nah genug am Ziel ist. */
    private boolean interactAnchor(FindItemResult item, double currentError) {
        if (drinkingFireRes) return false; // s.o. - Swap-Merkposten waehrend des Trinkens nicht anfassen
        Vec3 center = Vec3.atCenterOf(anchorPos);
        smoothLookAt(center);
        if (currentAimError(center) > aimTolerance.get()) return false;

        boolean swapped = InvUtils.swap(item.slot(), true);
        BlockUtils.interact(new BlockHitResult(center, BlockUtils.getDirection(anchorPos), anchorPos, true), InteractionHand.MAIN_HAND, true);
        if (swapped) InvUtils.swapBack();
        return true;
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

    private void runBedTick(LivingEntity target, double aimError) {
        if (bedCooldown > 0) {
            bedCooldown--;
            return;
        }

        Player self = mc.player;

        switch (bedStage) {
            case 0 -> {
                BedSpot spot = nextBedCandidate();
                if (spot == null) return;

                double d = Math.sqrt(self.distanceToSqr(Vec3.atCenterOf(spot.pos())));
                if (d > 4.0) return; // naechster Tick neuer Versuch, Baritone laeuft naeher

                // Grob in Richtung der gewuenschten Kopfteil-Ausrichtung drehen (menschliches Tempo via
                // smoothLookAt) - der virtuelle Zielpunkt liegt 10 Bloecke in "spot.dir()".
                Vec3 facePoint = self.getEyePosition().add(spot.dir().getStepX() * 10.0, -2.0, spot.dir().getStepZ() * 10.0);
                smoothLookAt(facePoint);
                if (currentAimError(facePoint) > aimTolerance.get()) return; // erst ausrichten

                if (rotationQueuedThisTick) return; // Rotations-Slot diesen Tick schon belegt - naechster Tick

                FindItemResult bed = InvUtils.findInHotbar(HumanPvP::isBed);
                if (!bed.found()) bed = InvUtils.find(HumanPvP::isBed);
                if (!bed.found()) return;
                FindItemResult foundBed = bed;

                // Die Platzierungsrichtung braucht die ECHTE Spieler-Rotation (das Bett-Placement liest
                // player.getYRot() direkt), nicht die bei free-look rein virtuelle Silent-Aim-Richtung -
                // deshalb hier ein kurzer, praeziser synchroner Snap statt der sonst ueblichen graduellen
                // Drehung, exakt fuer diesen einen Platzierungs-Tick.
                double yaw = spot.dir().toYRot();
                rotateAndRun(yaw, 55, () -> {
                    if (BlockUtils.place(spot.pos(), foundBed, false, 50)) {
                        bedPos = spot.pos();
                        bedStage = 1;
                        bedStageDeadline = tickCounter + 3 + rng.nextInt(5);
                    } else {
                        bedCandidateIndex++;
                    }
                });
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

                BlockUtils.interact(new BlockHitResult(center, BlockUtils.getDirection(bedPos), bedPos, true), InteractionHand.MAIN_HAND, true);
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
        boolean hasCrystals = totalItem(Items.END_CRYSTAL) > 0;
        boolean hasAnchorItem = totalItem(Items.RESPAWN_ANCHOR) > 0 && totalItem(Items.GLOWSTONE) > 0;
        boolean hasBedItem = useBeds.get() && totalItem(HumanPvP::isBed) > 0;
        Module ca = Modules.get().get(CrystalAura.class);
        if (!hasCrystals && !hasAnchorItem && !hasBedItem) {
            if (ca != null && ca.isActive()) ca.toggle();
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
            bestAnchorDmgCache = DamageUtils.anchorDamage(target, Vec3.atCenterOf(best));
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
        if (hasBedItem && bedCandidateIndex < bedCandidates.size()) {
            BedSpot best = bedCandidates.get(bedCandidateIndex);
            bestBedDmgCache = DamageUtils.bedDamage(target, Vec3.atCenterOf(best.pos()));
        }

        if (ca == null) return;

        boolean inRange = mc.player.distanceToSqr(target) < 5.5 * 5.5;

        // Kleines Rauschen auf der Vergleichsschwelle - vermeidet ein starres, immer-gleiches
        // Umschalt-Verhalten bei exakt derselben Schadensdifferenz (wirkt sonst wie ein Taschenrechner).
        double noise = (rng.nextDouble() - 0.5) * 0.3;

        boolean wantAnchor;
        if (anchorMode.get() == 1) {
            wantAnchor = hasAnchorItem && inRange && anchorCandidateIndex < anchorCandidates.size()
                && bestAnchorDmgCache >= crystalDmg + noise;
        } else {
            wantAnchor = hasAnchorItem && inRange && bestAnchorDmgCache > crystalDmg + 0.3 + noise;
        }

        boolean wantBed = hasBedItem && inRange && bedCandidateIndex < bedCandidates.size()
            && bestBedDmgCache > crystalDmg + 0.3 + noise;

        // Bei beiden verfuegbar gewinnt die schadenstaerkere Option.
        if (wantAnchor && wantBed) {
            if (bestBedDmgCache > bestAnchorDmgCache) wantAnchor = false; else wantBed = false;
        }

        // Deutlich groessere Umschalt-Traegheit als V1 - ein Mensch wechselt nicht alle paar Ticks die Taktik.
        if (tickCounter - lastAuraSwitch < 25) return;

        if (wantAnchor && auraMode != 1) {
            if (ca.isActive()) ca.toggle();
            auraMode = 1;
            lastAuraSwitch = tickCounter;
        } else if (wantBed && auraMode != 2) {
            if (ca.isActive()) ca.toggle();
            auraMode = 2;
            lastAuraSwitch = tickCounter;
        } else if (!wantAnchor && !wantBed && auraMode != 0) {
            if (!ca.isActive()) ca.toggle();
            auraMode = 0;
            lastAuraSwitch = tickCounter;
        }
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

    private boolean validExplosionSpot(BlockPos cell, boolean crystal) {
        if (mc.level == null) return false;
        if (!mc.level.getBlockState(cell).isAir()) return false;
        if (avoidLava.get() && isNearLava(cell)) return false;

        BlockState below = mc.level.getBlockState(cell.below());
        if (crystal) return below.is(Blocks.OBSIDIAN) || below.is(Blocks.BEDROCK) || below.isAir();
        return below.blocksMotion() && mc.level.getBlockState(cell.above()).isAir();
    }

    private boolean isNearLava(BlockPos cell) {
        if (mc.level.getBlockState(cell).is(Blocks.LAVA)) return true;
        for (Direction dir : Direction.values()) {
            if (mc.level.getBlockState(cell.relative(dir)).is(Blocks.LAVA)) return true;
        }
        return false;
    }

    /** Alle gueltigen Bett-Plaetze um das Ziel, sortiert nach Schaden (absteigend). Ein Bett braucht anders
     *  als Crystal/Anchor KEINE feste Unterlage - wirkt aber nur ausserhalb der Overworld (Nether/End). */
    private void calcBestBed(LivingEntity target, Vec3 center) {
        bedCandidates.clear();
        bedCandidateIndex = 0;

        int bx = (int) Math.floor(center.x);
        int by = (int) Math.floor(center.y);
        int bz = (int) Math.floor(center.z);

        List<BedSpot> found = new ArrayList<>();
        List<Double> dmgs = new ArrayList<>();

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = 0; dy <= 1; dy++) {
                    BlockPos foot = new BlockPos(bx + dx, by + dy, bz + dz);
                    if (!validBedCell(foot)) continue;

                    Direction dir = findFreeBedDirection(foot);
                    if (dir == null) continue;

                    Vec3 pos = Vec3.atCenterOf(foot);
                    if (target.getBoundingBox().intersects(new net.minecraft.world.phys.AABB(foot))) continue;
                    if (mc.player.getBoundingBox().intersects(new net.minecraft.world.phys.AABB(foot))) continue;

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

    /** Erste Himmelsrichtung, in der neben dem Fussteil noch eine zweite freie Zelle fuer das Kopfteil liegt. */
    private Direction findFreeBedDirection(BlockPos foot) {
        for (Direction dir : BED_DIRECTIONS) {
            if (validBedCell(foot.relative(dir))) return dir;
        }
        return null;
    }

    private static boolean isBed(ItemStack stack) {
        return stack.getItem() instanceof BedItem;
    }

    /** @return true, wenn eingereiht wurde (Rotations-Slot diesen Tick noch frei war). Siehe GodmodePvP
     *  fuer die volle Erklaerung, warum das notwendig ist. */
    private boolean rotateAndRun(double yaw, double pitch, Runnable callback) {
        if (rotationQueuedThisTick) return false;
        rotationQueuedThisTick = true;
        Rotations.rotate(yaw, pitch, callback);
        return true;
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

        if (blocking) return; // Schild-Swap laeuft gerade - nicht mit einem eigenen Trank-Swap ueberschreiben
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

    // ---------- Tracking ----------

    private void updateTracking(LivingEntity target) {
        UUID id = target.getUUID();
        lastPositions.put(id, target.position());
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

    private void attackMelee(LivingEntity target) {
        if (drinkingFireRes) return; // s.o. - Swap-Merkposten waehrend des Trinkens nicht anfassen
        boolean swapped = false;
        if (preferAxeMelee.get()) {
            FindItemResult axe = InvUtils.findInHotbar(itemStack -> itemStack.getItem() instanceof AxeItem);
            if (axe.found() && !axe.isMainHand()) swapped = InvUtils.swap(axe.slot(), true);
        }
        boolean wasSprinting = mc.player.isSprinting();
        mc.gameMode.attack(mc.player, target);
        mc.player.swing(InteractionHand.MAIN_HAND);
        if (swapped) InvUtils.swapBack();

        if (sprintReset.get() && wasSprinting) sprintResetCooldown = 2;
    }

    private void breakShield(Player target) {
        if (drinkingFireRes) return; // s.o. - Swap-Merkposten waehrend des Trinkens nicht anfassen
        FindItemResult axe = InvUtils.findInHotbar(itemStack -> itemStack.getItem() instanceof AxeItem);
        if (!axe.found()) return;

        boolean swapped = InvUtils.swap(axe.slot(), true);
        mc.gameMode.attack(mc.player, target);
        mc.player.swing(InteractionHand.MAIN_HAND);
        if (swapped) InvUtils.swapBack();
    }

    private void throwPearl(LivingEntity aimAt, boolean away) {
        if (drinkingFireRes) return; // s.o. - Swap-Merkposten waehrend des Trinkens nicht anfassen
        FindItemResult pearl = InvUtils.findInHotbar(Items.ENDER_PEARL);
        if (!pearl.found()) pearl = InvUtils.find(Items.ENDER_PEARL);
        if (!pearl.found()) return;

        double yaw, pitch;
        if (away) {
            yaw = Rotations.getYaw(aimAt) + 180.0;
            pitch = -20;
        } else {
            yaw = Rotations.getYaw(aimAt);
            pitch = solvePearlPitch(mc.player.getEyePosition().subtract(0, 0.1, 0), yaw, aimAt.getBoundingBox().getCenter());
            // Ziel physisch ausserhalb der Perlen-Reichweite (z.B. gerade sehr hoch explosionsgeschleudert)
            // - lieber die Perle sparen als sie sicher danebenzuwerfen.
            if (Double.isNaN(pitch)) return;
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
                InvUtils.swapBack();
            }
        }
    }

    /** Simple "look directly at the target" pitch works fine up close, but a thrown Ender Pearl is a real
     *  projectile (power 1.5, gravity 0.03/tick, 0.99 air drag - see Meteor's own ProjectileEntitySimulator/
     *  Minecraft's ThrowableItemProjectile) - aimed dead-on at longer range it visibly falls short since
     *  gravity has more time to pull it down over the longer flight. Solves for the pitch that actually
     *  lands at the target's height by simulating Minecraft's own pearl physics and bisecting on it,
     *  instead of guessing a fixed arc offset. Falls back to the direct look-pitch if nothing in the
     *  bounded search range lands close (never happens in practice within pearl-gapclose's own range caps,
     *  purely a safety net). */
    private double solvePearlPitch(Vec3 from, double yaw, Vec3 to) {
        double dx = to.x - from.x, dz = to.z - from.z;
        double distXZ = Math.sqrt(dx * dx + dz * dz);
        double dy = to.y - from.y;
        double directPitch = Math.toDegrees(-Math.atan2(dy, distXZ));
        if (distXZ < 0.5) return directPitch; // praktisch am eigenen Fuss - keine Ballistik noetig

        // Pitch ist auf [-90,90] begrenzt - ohne diese Klammer suchte die Bisektion bei sehr steilen
        // Wuerfen (Ziel hoch UND nah) in physisch unmoeglichem Terrain jenseits von -90 Grad und
        // lieferte einen sinnlosen, viel zu flachen Pitch - die Perle landete weit vor dem Ziel.
        double lo = Math.max(-89, directPitch - 40);
        double hi = directPitch;

        for (int i = 0; i < 40; i++) {
            double mid = (lo + hi) / 2;
            double heightAtDist = simulatePearlHeightAt(yaw, mid, distXZ);
            // NaN (Distanz nie erreicht, zu steil nach oben verschossen) zaehlt wie "deutlich zu hoch" -
            // also wie beim Ueberschiessen weniger Korrektur nach oben nehmen.
            boolean overshootsHeight = Double.isNaN(heightAtDist) || heightAtDist > dy;
            if (overshootsHeight) lo = mid; else hi = mid;
        }
        double finalPitch = (lo + hi) / 2;
        double landedHeight = simulatePearlHeightAt(yaw, finalPitch, distXZ);
        if (Double.isNaN(landedHeight) || landedHeight < dy - 0.5) return Double.NaN;
        return finalPitch;
    }

    /** Simuliert einen Perlenwurf mit gegebenem Yaw/Pitch nach Minecrafts eigener Projektil-Physik
     *  (Richtungsvektor wie ThrowableProjectile#shootFromRotation, dann pro Tick: vy -= 0.03, v *= 0.99,
     *  pos += v) und liefert die Hoehe relativ zum Startpunkt, sobald die Perle horizontal targetDistXZ
     *  erreicht hat (zwischen den beiden umgebenden Ticks linear interpoliert). NaN, wenn sie die Distanz
     *  innerhalb von 300 Ticks (15s, weit jenseits jeder echten Wurfdistanz) nie erreicht. */
    private double simulatePearlHeightAt(double yaw, double pitch, double targetDistXZ) {
        double yawRad = Math.toRadians(yaw), pitchRad = Math.toRadians(pitch);
        double vx = -Math.sin(yawRad) * Math.cos(pitchRad);
        double vy = -Math.sin(pitchRad);
        double vz = Math.cos(yawRad) * Math.cos(pitchRad);
        double len = Math.sqrt(vx * vx + vy * vy + vz * vz);
        vx = vx / len * 1.5;
        vy = vy / len * 1.5;
        vz = vz / len * 1.5;

        double x = 0, y = 0, z = 0;
        for (int tick = 0; tick < 300; tick++) {
            double prevDistXZ = Math.sqrt(x * x + z * z);
            double prevY = y;

            vy -= 0.03;
            vx *= 0.99;
            vy *= 0.99;
            vz *= 0.99;
            x += vx;
            y += vy;
            z += vz;

            double distXZ = Math.sqrt(x * x + z * z);
            if (distXZ >= targetDistXZ) {
                double frac = distXZ > prevDistXZ ? (targetDistXZ - prevDistXZ) / (distXZ - prevDistXZ) : 1.0;
                return prevY + (y - prevY) * frac;
            }
        }
        return Double.NaN;
    }

    /** Perle senkrecht nach unten - teleportiert bei Landung, kein unkontrolliertes Fallen nach Knockback. */
    private void throwPearlDown() {
        if (drinkingFireRes) return; // s.o. - Swap-Merkposten waehrend des Trinkens nicht anfassen
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
        if (mc.gui.screen() != null) return;
        if (tickCounter - lastTrapTick < 8 + rng.nextInt(8)) return; // menschlich unregelmaessiger Rhythmus

        BlockPos feet = target.blockPosition();
        if (feet.equals(mc.player.blockPosition())) return; // sonst web(t) sich der Bot bei Ueberlappung selbst ein
        if (!mc.level.getBlockState(feet).isAir()) return;
        if (!mc.level.getBlockState(feet.below()).blocksMotion()) return;

        FindItemResult web = InvUtils.findInHotbar(Items.COBWEB);
        if (!web.found()) web = InvUtils.find(Items.COBWEB);
        if (web.found() && BlockUtils.place(feet, web, true, 50)) lastTrapTick = tickCounter;
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
        if (tickCounter - healWindowStartTick > 8 || hp > hpAtHealWindowStart) {
            hpAtHealWindowStart = hp;
            healWindowStartTick = tickCounter;
        }

        if (!healPotions.get() || blocking || drinkingFireRes || healPotionCooldown > 0) return;

        float dmg = hpAtHealWindowStart - hp;
        if (dmg < healMinDamage.get()) return;

        FindItemResult potion = InvUtils.findInHotbar(HumanPvP::isHealingSplash);
        if (!potion.found()) potion = InvUtils.find(HumanPvP::isHealingSplash);
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
            if (!thrown && swapped) InvUtils.swapBack();
        }
        if (!thrown) return;

        healPotionCooldown = healCooldown.get();
        hpAtHealWindowStart = hp;
        healWindowStartTick = tickCounter;
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

    private void refill(java.util.function.Predicate<ItemStack> pred, int min) {
        if (countHotbar(pred) >= min) return;
        if (totalItem(pred) <= min) return;

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
                savedSupport = s.get();
                if (s.get() == CrystalAura.SupportMode.Disabled) s.set(CrystalAura.SupportMode.Fast);
            }
        } catch (Throwable t) {
            savedSupport = null;
            error("CrystalAura-Support-Mode konnte nicht gesetzt werden (Meteor-Version geaendert?) - Obsidian-Unterbau bei freier Luft laeuft evtl. nicht automatisch.");
        }

        // support-delay: Tickabstand zwischen Obsidian-Platzierung und dem folgenden Crystal-Versuch. Bei 0
        // schickt CrystalAura beide Pakete im selben Tick - auf Servern mit spuerbarer Latenz kann der
        // Crystal-Versuch dann ankommen, bevor der Server das Obsidian registriert hat, und wird lautlos
        // abgelehnt. Nur anheben, nie senken.
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

    private void safeEnable(Modules m, Class<? extends Module> clazz) {
        Module mod = m.get(clazz);
        if (mod != null && !mod.isActive()) mod.toggle();
    }

    private void safeDisable(Modules m, Class<? extends Module> clazz) {
        Module mod = m.get(clazz);
        if (mod != null && mod.isActive()) mod.toggle();
    }
}
