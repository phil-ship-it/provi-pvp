package com.provipvp.modules;

import com.provipvp.ProviPvPAddon;
import com.provipvp.broker.ActionBroker.ActionKind;
import com.provipvp.broker.ConflictRegistry.Mode;
import com.provipvp.broker.PvpServices;
import com.provipvp.mechanics.SpearModel;
import com.provipvp.rotation.GcdRotator;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.phys.Vec3;

import java.util.Random;

/**
 * Der 26er-Spear macht das ganze 3-Block-Nahkampfspiel kaputt - und genau deshalb braucht er ein
 * eigenes Modul statt einer Setting-Zeile.
 *
 * <p><b>Die Luecke, die dieses Modul schliesst.</b> Der konfigurierte {@code attack-range} der
 * Combat-Profile liegt bei 3.6 bzw. 3.4. Der Spear erreicht 4.5. Damit faellt genau der Distanzstreifen
 * von 3.6 bis 4.5 aus dem Angriffsfenster, in dem ein Spear <i>ohne Zeitverlust</i> zuschlaegt - der
 * Bot haelt den Gegner in genau diesem Streifen fuer "ausser Reichweite" und schlaegt nie zu. Der
 * zweite Grund ist der Angriffs-Cooldown: der Jab des Spears wird serverseitig auf 100 % gezwungen,
 * das normale 0.9-Gate des Bots gilt fuer ihn also nicht. Ein Bot, der nur das Gate kennt, verliert
 * hier jeden zweiten Schlag, ohne es zu merken. {@link SpearModel} beantwortet beides an einer Stelle.
 *
 * <p><b>Was dieses Modul bewusst NICHT tut.</b> Der Spear kann nicht critten und erzeugt keinen
 * Sprint-Knockback. Der Crit-Jump und das Sprint-Reset der Combat-Profile wuerden hier Zeit kosten und
 * nichts einbringen - dieses Modul loest deshalb keinen Sprung aus und setzt keinen
 * {@code keySprint}-Zustand. Der dritte Angriff (Charge) haengt an der Annaeherungsgeschwindigkeit:
 * unter 4.6 Bloecke/s geht gar kein Schaden raus, ab 5.1 kommt der Knockback dazu. Beide Schwellen
 * stehen in {@link SpearModel#CHARGE_DAMAGE_SPEED} und {@link SpearModel#CHARGE_KNOCKBACK_SPEED};
 * dieses Modul rechnet sie nicht selbst, es fragt das Modell.
 *
 * <p><b>Warum es den Combat-Modulen immer nachgibt.</b> Ein Jab ist ein netter Zusatzschlag. Eine
 * Crystal-Platzierung ist der Kampf. Der Konflikt laeuft deshalb im Modus {@link Mode#DEFERS} mit
 * einer {@link ConflictRegistry}-Prioritaet <i>unter</i> der der Combat-Profile: laeuft eines von
 * beiden, weicht dieses Modul aus und tut in diesem Tick nichts.
 *
 * <p><b>Belegung.</b> {@link ActionKind#MELEE} mit {@link PvpServices#P_COMBAT} fuer den Schlag,
 * {@link ActionKind#SWAP} mit {@link PvpServices#P_SUPPORT} fuer den Slotwechsel. Der Slotwechsel
 * bekommt absichtlich die niedrigere Prioritaet: er ist eine Vorbereitung, kein Angriff, und darf
 * einem echten Nahkampfschlag des Combat-Moduls niemals den Slot wegnehmen.
 *
 * @see SpearModel
 */
public class SpearModule extends Module {

    /**
     * Bewusst {@link PvpServices#P_OPPORTUNIST}: der Wert wird mit dem der Combat-Profile verglichen,
     * und der Spear-Jab soll sie nie verdraengen koennen.
     */
    private static final int CONFLICT_PRIORITY = PvpServices.P_OPPORTUNIST;

    /** Attribute-Multiplikator pro Tick, mit dem eine Delta-Bewegung in Bloecke pro Sekunde wird. */
    private static final double TICKS_PER_SECOND = 20.0;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgAttack = settings.createGroup("1 · Angriff");

    public final Setting<Double> attackRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("attack-range")
        .description("Nahkampfreichweite dieses Moduls. Der Spear braucht 4.5; darunter schneidet man sich genau den Streifen ab, in dem er am gefaehrlichsten ist - daher die Warnung im Chat, sobald der Wert zu niedrig ist.")
        .defaultValue(SpearModel.requiredAttackRange())
        .min(SpearModel.MIN_REACH)
        .max(SpearModel.MAX_REACH)
        .sliderMin(SpearModel.MIN_REACH)
        .sliderMax(SpearModel.MAX_REACH)
        .build()
    );

    public final Setting<Boolean> autoSwap = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-swap")
        .description("Wechselt einen Spear aus der Hotbar in die Mainhand. Ausgeschaltet feuert das Modul nur mit dem Spear, der ohnehin schon in der Hand liegt.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Integer> swapDelay = sgGeneral.add(new IntSetting.Builder()
        .name("swap-delay")
        .description("Ticks bis zum Schlag nach dem Slotwechsel. Der Server muss den Wechsel bestaetigt haben, sonst schlaegt das Modul mit dem vorherigen Item.")
        .defaultValue(2)
        .min(1)
        .max(10)
        .build()
    );

    public final Setting<Boolean> useJab = sgAttack.add(new BoolSetting.Builder()
        .name("use-jab")
        .description("Der schnelle Stich. Wird serverseitig auf 100 % Angriffs-Cooldown gezwungen und trifft deshalb auch bei halb geladenem Cooldown voll - das normale Cooldown-Gate des Bots gilt fuer ihn nicht.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> useCharge = sgAttack.add(new BoolSetting.Builder()
        .name("use-charge")
        .description("Der gehaltene Angriff. Richtet Schaden erst ab 4.6 Bloecke/s Annaeherungsgeschwindigkeit aus, Knockback erst ab 5.1. Laeuft das Modul ohne Halten an, faellt er auf den Jab zurueck.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Integer> holdTicks = sgAttack.add(new IntSetting.Builder()
        .name("hold-ticks")
        .description("Wie viele Ticks der Spear gehalten werden muss, bevor der Charge-Angriff loest. Ab 10 Ticks ueberhaupt moeglich (TIRED), ab 40 voll geladen (ENGAGED) - danach kommt der Knockback erst noch mit der schnellen Annaeherung dazu.")
        .defaultValue(SpearModel.CHARGE_FULL_TICKS)
        .min(SpearModel.CHARGE_MIN_TICKS)
        .max(SpearModel.CHARGE_FULL_TICKS)
        .build()
    );

    public final Setting<Double> rotationJitter = sgAttack.add(new DoubleSetting.Builder()
        .name("rotation-jitter")
        .description("Streuung um den Zielwinkel in Grad. Meteor quantisiert Rotationen nicht - Gitter und Duplikat-Vermeidung kommen aus dem GcdRotator dieses Addons.")
        .defaultValue(0.3)
        .min(0.0)
        .max(1.5)
        .sliderMin(0.0)
        .sliderMax(1.5)
        .build()
    );

    private final GcdRotator.AngleDeltaAccumulator rotation = new GcdRotator.AngleDeltaAccumulator();
    private final Random rng = new Random();

    private int tickCounter;
    private int readyTick;
    private int lastStrikeTick = -999;
    private boolean swappedIn;
    private boolean rangeWarned;
    private String currentAction = "-";

    public SpearModule() {
        super(ProviPvPAddon.CATEGORY, "provi-spear",
            "Nahkampf mit dem 26er-Spear: Reichweite 4.5 statt 3.0, Jab mit erzwungenem 100-%Cooldown und ein Charge-Angriff ab 4.6 b/s Annaeherungsgeschwindigkeit. Tritt zurueck, sobald ein Crystal-Profil laeuft.");
    }

    @Override
    public void onActivate() {
        tickCounter = 0;
        readyTick = 0;
        lastStrikeTick = -999;
        swappedIn = false;
        rangeWarned = false;
        currentAction = "-";
        rotation.reset();

        PvpServices.conflicts().declare(getClass(), Mode.DEFERS, CONFLICT_PRIORITY,
            GodmodePvP.class, HumanPvP.class);
    }

    /**
     * Speer-Slot und Baritone-Sperre zurueckgeben. Ohne das haelt ein ausgeschaltetes Modul den
     * Baritone-Pfad weiter fest - und der Bot steht im naechsten Kampf still, ohne dass jemand den
     * Grund sieht. Beide Aufrufe sind idempotent, deshalb ist kein Zustands-Guard noetig.
     */
    @Override
    public void onDeactivate() {
        giveBackSlot();
        PvpServices.release(this);
        currentAction = "-";
        rotation.reset();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        Player self = mc.player;
        if (self == null || mc.level == null || !Utils.canUpdate()) return;

        tickCounter++;

        // Ein Combat-Profil im Kampf heisst: die Crystal-Position ist die wichtige Position.
        if (PvpServices.conflicts().shouldYield(getClass(), PvpServices.activeModules())) {
            giveBackSlot();
            currentAction = "combat-modul-aktiv";
            return;
        }

        // Schon gehandelt? Dann nichts mehr scannen - die teure Fallberechnung waere reine Arbeit
        // fuer eine Belegung, die es in diesem Tick nicht mehr gibt.
        if (PvpServices.spentThisTick(this)) return;

        if (!SpearModel.attackRangeCoversSpear(attackRange.get()) && !rangeWarned) {
            rangeWarned = true;
            info("Achtung: attack-range %.1f reicht fuer den Spear (4.5) nicht - der Streifen 3.6-4.5 bleibt unerreichbar.",
                attackRange.get());
        } else if (SpearModel.attackRangeCoversSpear(attackRange.get())) {
            rangeWarned = false;
        }

        ItemStack spear = self.getMainHandItem();
        if (!isSpear(spear)) {
            if (!ensureSpearInHand()) {
                currentAction = "kein-spear";
                return;
            }
            spear = mc.player.getMainHandItem();
            if (!isSpear(spear)) return;
        }

        Player target = findTarget(self);
        if (target == null) {
            giveBackSlot();
            currentAction = "kein-ziel";
            return;
        }

        double dist = self.distanceTo(target);
        if (!inSpearWindow(dist)) {
            currentAction = "ausser-reichweite";
            return;
        }

        if (tickCounter < readyTick) return;

        // Wie lange ist der Spear schon geladen? Das Modell braucht diesen Wert fuer die
        // Ladungsstufe, und der Client haelt ihn nur, solange tatsaechlich benutzt wird.
        int chargeTicks = self.isUsingItem() ? self.getTicksUsingItem() : 0;
        SpearModel.Attack attack = pickAttack(chargeTicks);
        if (attack == null) {
            currentAction = "keine-angriffsform";
            return;
        }

        SpearModel.Verdict verdict = SpearModel.resolve(attack, closingSpeed(self, target),
            chargeTicks, strengthLevel(self), cooldown(self), spearBaseDamage(spear));

        if (!verdict.damageDealt()) {
            currentAction = verdict.cooldownGatePassed() ? "zu-langsam" : "cooldown-nicht-voll";
            return;
        }

        strike(self, target, verdict);
    }

    /**
     * Holt einen Spear aus der Hotbar in die Mainhand.
     *
     * <p>Der Slotwechsel ist eine eigene Belegung ({@link ActionKind#SWAP}) mit bewusst niedrigerer
     * Prioritaet als der Schlag: er ist eine Vorbereitung, und ein laufendes Combat-Profil darf ihn
     * nicht verdraengen sehen. Misslingt der Wechsel, wird die Belegung wieder freigegeben - ein
     * haengender Slot waere schlimmer als gar keiner.
     *
     * @return true, wenn am Ende dieser Methode ein Spear in der Mainhand liegt
     */
    private boolean ensureSpearInHand() {
        if (!autoSwap.get()) return isSpear(mc.player.getMainHandItem());

        FindItemResult spear = InvUtils.findInHotbar(SpearModule::isSpear);
        if (!spear.found()) return false;
        if (spear.isMainHand()) return true;

        if (!PvpServices.claim(this, ActionKind.SWAP, PvpServices.P_SUPPORT)) {
            currentAction = "slot-belegt";
            return false;
        }
        if (!InvUtils.swap(spear.slot(), true)) {
            PvpServices.broker().release(this);
            currentAction = "swap-fehlgeschlagen";
            return false;
        }
        PvpServices.broker().spend(this, ActionKind.SWAP);
        swappedIn = true;
        // Zwischen Slotwechsel und Schlag darf Baritone den Bot nicht umpositionieren - sonst
        // geht der Schlag an eine andere Stelle als die gerade gesendete Rotation.
        PvpServices.broker().path().pause(this);
        readyTick = tickCounter + swapDelay.get();
        currentAction = "spear-herausgeholt";
        return true;
    }

    /** Der Schlag. Erst die Rotation einreihen, dann das Paket - in umgekehrter Reihenfolge geht
     *  der Angriff mit der vorherigen Blickrichtung raus. */
    private void strike(Player self, Player target, SpearModel.Verdict verdict) {
        if (!PvpServices.claim(this, ActionKind.MELEE, PvpServices.P_COMBAT)) {
            currentAction = "nahkampf-belegt";
            return;
        }

        Vec3 aim = target.getBoundingBox().getCenter();
        double divisor = GcdRotator.sensitivityDivisor(mc.options.sensitivity().get());
        GcdRotator.Rotation sent = rotation.quantize(
            Rotations.getYaw(aim), Rotations.getPitch(aim), divisor, rotationJitter.get(), rng);

        Rotations.rotate(sent.yaw(), sent.pitch(), PvpServices.P_COMBAT, false, () -> {
            // Der Angriff loest das Benutzen auf - danach ist die Ladung vorbei, und das ist genau
            // der Punkt: ein gehaltener Spear, der nie loslaesst, sammelt keine Schaden an.
            mc.gameMode.attack(self, target);
            lastStrikeTick = tickCounter;
            currentAction = verdict.knockedBack()
                ? "charge-mit-knockback"
                : verdict.attack() == SpearModel.Attack.JAB ? "jab" : "charge";
            PvpServices.broker().spend(this, ActionKind.MELEE);
        });
    }

    /**
     * Welche der beiden Angriffsformen. Der Charge-Angriff loest erst ab {@link #holdTicks} Ticks
     * Halten; davor, oder wenn {@code use-charge} aus ist, bleibt nur der Jab. Der Jab wird
     * <b>nicht</b> ueber das Cooldown-Gate gefiltert - genau darin liegt sein Vorteil.
     */
    private SpearModel.Attack pickAttack(int chargeTicks) {
        if (useCharge.get() && chargeTicks >= holdTicks.get()) return SpearModel.Attack.CHARGE;
        if (useJab.get()) return SpearModel.Attack.JAB;
        return null;
    }

    /**
     * Liegt die Distanz im Trefferfenster des Spears? Das Fenster ist nach oben weit (4.5) und nach
     * unten eng (2.0) - unterhalb von 2.0 trifft auch ein Spear nicht mehr, weshalb die reine
     * Einheitspruefung "bis attack-range" hier falsch waere.
     */
    private boolean inSpearWindow(double dist) {
        return SpearModel.withinReach(dist) && dist <= attackRange.get();
    }

    /**
     * Annaeherungsgeschwindigkeit in Bloecke pro Sekunde, auf die Achse Bot -> Gegner projiziert.
     *
     * <p>Genau dieser Wert entscheidet beim Charge-Angriff ueber Schaden und Knockback. Die rohe
     * horizontale Geschwindigkeit des Bots waere die falsche Groesse: wer im Kreis laeuft, ist schnell
     * und kommt trotzdem nicht naeher.
     */
    private static double closingSpeed(Player self, Player target) {
        Vec3 toTarget = target.position().subtract(self.position());
        double distance = toTarget.length();
        if (!(distance > 1.0E-6)) return 0.0;

        Vec3 axis = toTarget.scale(1.0 / distance);
        Vec3 relative = self.getDeltaMovement().subtract(target.getDeltaMovement());
        return Math.max(0.0, relative.dot(axis) * TICKS_PER_SECOND);
    }

    /**
     * Staerke-Stufe 0..5. Im 26.2 ist Staerke ein Effekt, kein Attribut: er addiert festen
     * Angriffsschaden auf {@code ATTACK_DAMAGE}. Der Wert wandert deshalb unveraendert in das Modell -
     * und der Charge-Angriff des Spears nutzt ihn dort ausdruecklich <i>nicht</i>.
     */
    private static int strengthLevel(Player self) {
        var effect = self.getEffect(MobEffects.STRENGTH);
        return effect == null ? 0 : effect.getAmplifier() + 1;
    }

    private static SpearModel.CooldownState cooldown(Player self) {
        return new SpearModel.CooldownState(self.getAttackStrengthScale(0.5f));
    }

    /**
     * Basis-Schaden des gehaltenen Spears, gelesen aus seinen eigenen Attribut-Modifikatoren.
     *
     * <p>Bewusst <i>nicht</i> {@code getAttributeValue(ATTACK_DAMAGE)}: das ist der Gesamtwert des
     * Spielers inklusive Staerke-Effekt, und das Modell addiert die Staerke bei einem Jab selbst dazu.
     * Aus dem Attributwert zu lesen wuerde den Staerkebonus also doppelt zaehlen und den Jab
     * ueberbewerten - genau die Zahl, an der ein Bot faelschlich einen Schlag plant.
     */
    private static double spearBaseDamage(ItemStack spear) {
        ItemAttributeModifiers mods = spear.get(DataComponents.ATTRIBUTE_MODIFIERS);
        if (mods == null) return 1.0;

        for (ItemAttributeModifiers.Entry entry : mods.modifiers()) {
            if (entry.matches(Attributes.ATTACK_DAMAGE, Item.BASE_ATTACK_DAMAGE_ID)) {
                double amount = entry.modifier().amount();
                return Double.isFinite(amount) ? amount : 1.0;
            }
        }
        return 1.0;
    }

    private static boolean isSpear(ItemStack stack) {
        return stack.is(ItemTags.SPEARS);
    }

    private Player findTarget(Player self) {
        Player best = null;
        double bestDist = Double.MAX_VALUE;

        for (Player p : mc.level.players()) {
            if (p == self || !p.isAlive() || p.isSpectator() || p.isCreative()) continue;
            if (!Friends.get().shouldAttack(p)) continue;

            double d = self.distanceToSqr(p);
            if (d < bestDist) {
                bestDist = d;
                best = p;
            }
        }
        return best;
    }

    /**
     * Slot und Pfad-Sperre zurueckgeben. Solange der Spear in der Hand liegt, darf Baritone den Bot
     * nicht zwischen Slotwechsel und Schlag umpositionieren - der Schlag ginge dann an eine andere
     * Stelle als die Rotation, die gerade gesendet wurde.
     */
    private void giveBackSlot() {
        if (swappedIn && mc.player != null) InvUtils.swapBack();
        swappedIn = false;
        readyTick = 0;
        PvpServices.broker().path().resume(this);
    }

    /** Nur fuer Diagnose/Overlay; aendert nichts am Kampfverhalten. */
    public String currentAction() {
        return currentAction;
    }

    /** Nur fuer Diagnose/Overlay: hat dieses Modul den letzten Tick mit einem Belegungsschlag verbracht? */
    public boolean struckLastTick() {
        return tickCounter - lastStrikeTick <= 1;
    }

}