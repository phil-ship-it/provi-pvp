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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
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
        .description("Wenn der Bot selbst durch Knockback in die Luft geschleudert wird, sofort senkrecht nach unten perlen.")
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

    // ---------- State ----------
    private final Random rng = new Random();
    private final Map<UUID, Vec3> lastPositions = new HashMap<>();
    private int tickCounter;
    private int lastErrorWarnTick = -999;
    private int lastPearlTick = -999;
    private int sprintResetCooldown;
    private float lastSelfHpForKnockback = -1;
    private final Map<net.minecraft.world.item.Item, Boolean> warnedOutOfMisc = new HashMap<>();
    private String currentAction = "-";
    private boolean blocking;
    private boolean blockingSwapBack;
    private int shieldUntil;
    private int lastCrystalCount = -1;
    private int savedPlaceDelay = -1;
    private CrystalAura.SupportMode savedSupport;
    private boolean followActive;
    private UUID followedId;

    private UUID engagedId;
    private int engageAtTick;
    private int nextClickTick = -1;
    private boolean pursuing; // sticky: einmal in Engage-Distanz gekommen, bleibt es auch nach Explosions-Knockback ueber diese Distanz hinaus (bis follow-range/Zielverlust) - sonst reisst eine Crystal-Explosion die Verfolgung mitten im Kampf ab.

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

    private int lastTrapTick = -999;
    private int nextTotemCheckTick = -999;

    public HumanPvP() {
        super(Categories.Combat, "human-pvp", "ProviPvP V2: menschlich wirkender Kampf-Bot (Reaktionszeit, sichtbare Rotation, Klick-Jitter). Kein Unerkennbarkeits-Versprechen. Befehl: .hpvp");
    }

    @Override
    public void onActivate() {
        tickCounter = 0;
        lastErrorWarnTick = -999;
        sprintResetCooldown = 0;
        lastSelfHpForKnockback = -1;
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
        boolean guiOpen = mc.gui.screen() != null;

        if (!guiOpen) {
            if (fastTotem.get() && tickCounter >= nextTotemCheckTick) ensureOffhandTotem();
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
        if (knockbackPearl.get() && tookHit && self.getDeltaMovement().y > 0.35
            && tickCounter - lastPearlTick > 15
            && (InvUtils.findInHotbar(Items.ENDER_PEARL).found() || InvUtils.find(Items.ENDER_PEARL).found())) {
            throwPearlDown();
            currentAction = "pearl-knockback";
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

        if (pearlThrow.get() && dist > pearlMinDist.get() && pursuing
            && tickCounter - lastPearlTick > pearlCooldown(dist) && !guiOpen) {
            throwPearl(target, false);
            currentAction = "pearl-gapclose";
        }

        selectAura(target);

        if (auraMode == 1) {
            runAnchorTick(target, aimError);
            currentAction = "anchor";
        }

        if (shieldBreaker.get() && target instanceof Player p && p.isBlocking()) {
            breakShield(p);
            currentAction = "schild-brechen";
        } else if (dist <= 3.4 && aimError <= aimTolerance.get() && self.hasLineOfSight(target)
            && self.getAttackStrengthScale(0.5f) >= 0.95f && readyToClick()) {
            attackMelee(target);
            currentAction = "schlagen";
        }

        if (currentAction.equals("-")) currentAction = auraMode == 0 ? "crystal" : "zielen";
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
        anchorStage = 0;
        pursuing = false;
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

        float yawDelta = wrapDelta(targetYaw - mc.player.getYRot());
        float pitchDelta = targetPitch - mc.player.getXRot();

        float maxTurn = (float) (double) maxTurnPerTick.get();
        float smoothing = (float) (0.28 + rng.nextDouble() * 0.14); // 0.28-0.42 Anteil des Restwinkels pro Tick

        float appliedYaw = clampAbs(yawDelta * smoothing, maxTurn);
        float appliedPitch = clampAbs(pitchDelta * smoothing, maxTurn);

        mc.player.setYRot(mc.player.getYRot() + appliedYaw);
        mc.player.setXRot(Math.max(-90f, Math.min(90f, mc.player.getXRot() + appliedPitch)));
    }

    private static float clampAbs(float v, float max) {
        return Math.max(-max, Math.min(max, v));
    }

    private double currentAimError(Vec3 point) {
        float targetYaw = (float) Rotations.getYaw(point);
        float targetPitch = (float) Rotations.getPitch(point);
        double yawErr = Math.abs(wrapDelta(targetYaw - mc.player.getYRot()));
        double pitchErr = Math.abs(targetPitch - mc.player.getXRot());
        return Math.max(yawErr, pitchErr);
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

    // ---------- Aura-Steuerung (groessere, verrauschte Hysterese) ----------

    private void selectAura(LivingEntity target) {
        Vec3 center = target.position();
        double crystalDmg = bestDamageAround(target, center, true);

        if (useAnchors.get() && anchorMode.get() != 2) {
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
        if (anchorCandidateIndex < anchorCandidates.size()) {
            BlockPos best = anchorCandidates.get(anchorCandidateIndex);
            bestAnchorDmgCache = DamageUtils.anchorDamage(target, Vec3.atCenterOf(best));
        }

        Module ca = Modules.get().get(CrystalAura.class);
        if (ca == null) return;

        boolean inRange = mc.player.distanceToSqr(target) < 5.5 * 5.5;

        // Kleines Rauschen auf der Vergleichsschwelle - vermeidet ein starres, immer-gleiches
        // Umschalt-Verhalten bei exakt derselben Schadensdifferenz (wirkt sonst wie ein Taschenrechner).
        double noise = (rng.nextDouble() - 0.5) * 0.3;

        boolean wantAnchor;
        if (anchorMode.get() == 1) {
            wantAnchor = inRange && anchorCandidateIndex < anchorCandidates.size()
                && bestAnchorDmgCache >= crystalDmg + noise;
        } else {
            wantAnchor = inRange && bestAnchorDmgCache > crystalDmg + 0.3 + noise;
        }

        // Deutlich groessere Umschalt-Traegheit als V1 - ein Mensch wechselt nicht alle paar Ticks die Taktik.
        if (tickCounter - lastAuraSwitch < 25) return;

        if (wantAnchor && auraMode != 1) {
            if (ca.isActive()) ca.toggle();
            auraMode = 1;
            lastAuraSwitch = tickCounter;
        } else if (!wantAnchor && auraMode != 0) {
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

        BlockState below = mc.level.getBlockState(cell.below());
        if (crystal) return below.is(Blocks.OBSIDIAN) || below.is(Blocks.BEDROCK) || below.isAir();
        return below.blocksMotion() && mc.level.getBlockState(cell.above()).isAir();
    }

    // ---------- Tracking ----------

    private void updateTracking(LivingEntity target) {
        UUID id = target.getUUID();
        lastPositions.put(id, target.position());
    }

    // ---------- Zielauswahl ----------

    private LivingEntity findTarget(Player self) {
        if (mc.level == null) return null;

        LivingEntity best = null;
        double bestDist = followRange.get() * followRange.get();

        for (Player p : mc.level.players()) {
            if (p == self || !p.isAlive() || p.isSpectator()) continue;
            if (p.isCreative() && !(p instanceof FakePlayerEntity)) continue;
            double d = self.distanceToSqr(p);
            if (d < bestDist) {
                bestDist = d;
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
        if (away) {
            yaw = Rotations.getYaw(aimAt) + 180.0;
            pitch = -20;
        } else {
            yaw = Rotations.getYaw(aimAt);
            pitch = Rotations.getPitch(aimAt.getBoundingBox().getCenter());
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

    private void inventoryTick() {
        refill(Items.END_CRYSTAL, minCrystals.get());
        refill(Items.RESPAWN_ANCHOR, minAnchors.get());
        refill(Items.GLOWSTONE, minGlowstone.get());
        refill(Items.ENDER_PEARL, minPearls.get());
        refill(Items.OBSIDIAN, minObsidian.get());
        refill(Items.COBWEB, minWeb.get());

        warnIfEmpty(Items.END_CRYSTAL, "End Crystals");
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
        }
    }

    private void restoreSupport(CrystalAura ca) {
        if (savedSupport == null) return;
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

    private void safeEnable(Modules m, Class<? extends Module> clazz) {
        Module mod = m.get(clazz);
        if (mod != null && !mod.isActive()) mod.toggle();
    }

    private void safeDisable(Modules m, Class<? extends Module> clazz) {
        Module mod = m.get(clazz);
        if (mod != null && mod.isActive()) mod.toggle();
    }
}
