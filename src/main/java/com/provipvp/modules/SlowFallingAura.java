package com.provipvp.modules;

import com.provipvp.ProviPvPAddon;
import com.provipvp.broker.ActionBroker.ActionKind;
import com.provipvp.broker.ConflictRegistry.Mode;
import com.provipvp.broker.PvpServices;
import com.provipvp.mechanics.SlowFallingArrow;
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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.phys.Vec3;

import java.util.Random;

/**
 * Die auf der Wiki empfohlene Gegenmassnahme gegen Crystal-PvP: ein Slow-Falling-Pfeil.
 *
 * <p><b>Warum ueberhaupt ein eigenes Modul.</b> Ein Slow-Falling-Pfeil nimmt dem Getroffenen drei
 * Dinge gleichzeitig und kostet dafuer keinen einzigen Hotbar-Slot im Kampfgeschehen: er kann nicht
 * mehr critten (der Sprung traegt nichts bei), er kann nicht mehr mit dem Mace smashen (Slow Falling
 * macht den Smash unerreichbar, weil der Bonus ein reiner Fallhoehen-Bonus ist), und er haengt rund
 * 31x laenger in der Luft als ein normaler Fall. Aus einem Pop-Plan wird dadurch ein Fenster, in
 * das mehrere Pop-Zyklen passen. Das ist keine Verstaerkung des Crystal-Angriffs, sondern eine
 * Umstellung, und sie verdient deshalb einen eigenen Taktgeber statt einer Zeile im Aura-Code.
 *
 * <p><b>Die Entscheidung, die dieses Modul trifft.</b> {@link SlowFallingArrow#evaluate} beantwortet
 * zwei Fragen gleichzeitig, und nur eine davon ist "soll ich schieessen". Die zweite lautet: ist
 * das Ziel gerade in einem Fenster, in dem der Crystal-Pop mehrfach sitzt? Wenn ja, wird
 * <b>nicht</b> geschossen. Ein weiterer Pfeil wuerde das Opfer ein zweites Mal in die Luft schiessen,
 * den Pop-Zyklus also genau dort unterbrechen, wo er gerade Geld einbringt. In diesem Zustand haelt
 * das Modul stattdessen seine Position und meldet das offene Fenster - die Platzierung selbst
 * gehoert dem Combat-Modul, das den Crystal besitzt.
 *
 * <p><b>Warum dieses Modul den Combat-Modulen nachgibt.</b> Beide Combat-Profile setzen Crystals,
 * und der Crystal-Slot ist die knappste Ressource im Crystal-PvP. Ein Pfeil, der im selben Tick
 * rausgeht wie eine Platzierung, kostet entweder den Slot oder den Pop. Deshalb laeuft der Konflikt
 * im Modus {@link Mode#DEFERS} gegen {@link GodmodePvP} und {@link HumanPvP} mit einer
 * {@link ConflictRegistry}-Prioritaet <i>unter</i> der von P_COMBAT: sobald ein Kampf-Modul laeuft,
 * tritt dieses Modul zurueck und tut in diesem Tick nichts.
 *
 * <p><b>Belegung.</b> {@link ActionKind#PROJECTILE} mit {@link PvpServices#P_SUPPORT} fuer den
 * Schuss, {@link ActionKind#SWAP} mit derselben Prioritaet fuer den Slotwechsel. Zwei Belegungen
 * statt einer, weil der Broker genau einen Besitzer je Art je Tick kennt und der Slotwechsel
 * <b>vor</b> der Aktion liegen muss - sonst schiesst dieses Modul aus dem falschen Slot.
 *
 * @see SlowFallingArrow
 */
public class SlowFallingAura extends Module {

    /**
     * Absichtlich {@link PvpServices#P_OPPORTUNIST} und nicht P_SUPPORT: die ConflictRegistry
     * vergleicht diese Zahl mit der des laufenden Combat-Moduls, und ein Combat-Profil soll diesen
     * Gegner nie verdraengen koennen. P_OPPORTUNIST liegt auf der dokumentierten Skala und ist
     * damit ein gueltiger Wert - aber der niedrigste darauf.
     */
    private static final int CONFLICT_PRIORITY = PvpServices.P_OPPORTUNIST;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgShot = settings.createGroup("1 · Schuss");
    private final SettingGroup sgPop = settings.createGroup("2 · Pop-Fenster");

    public final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("Maximale Distanz zum Gegner, in der der Pfeil ueberhaupt geschossen wird. Darueber waere der Schuss zwar moeglich, aber das Pop-Fenster laeuft schneller ab, als der Pfeil fliegt.")
        .defaultValue(8.0)
        .min(1.5)
        .max(16.0)
        .sliderMin(1.5)
        .sliderMax(16.0)
        .build()
    );

    public final Setting<Boolean> autoSwap = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-swap")
        .description("Wechselt die geladene Armbrust in die Mainhand. Ausgeschaltet feuert das Modul nur, wenn die Armbrust schon ohne Slotwechsel in der Hand liegt - dann kann es nie einen Slot gegen einen laufenden Kampf belegen.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Integer> swapDelay = sgShot.add(new IntSetting.Builder()
        .name("swap-delay")
        .description("Ticks, die nach dem Slotwechsel vergehen muessen, bevor geschossen wird. Der Server braucht den Slotwechsel bestaetigt, sonst feuert der Schuss noch aus dem vorherigen Slot.")
        .defaultValue(2)
        .min(1)
        .max(10)
        .build()
    );

    public final Setting<Integer> shotCooldown = sgShot.add(new IntSetting.Builder()
        .name("shot-cooldown")
        .description("Mindestabstand zwischen zwei Pfeilen in Ticks. Kuerzer als die 1.22x-Knockback-Erholung des Gegners bedeutet nur, dass der zweite Pfeil ein Ziel trifft, das gerade zurueckgeschoben wird.")
        .defaultValue(20)
        .min(1)
        .max(60)
        .build()
    );

    public final Setting<Double> rotationJitter = sgShot.add(new DoubleSetting.Builder()
        .name("rotation-jitter")
        .description("Streuung um den Zielwinkel in Grad. Meteor quantisiert Rotationen NICHT - das Gitter und damit die Duplikat-Vermeidung kommen aus dem GcdRotator dieses Addons.")
        .defaultValue(0.35)
        .min(0.0)
        .max(1.5)
        .sliderMin(0.0)
        .sliderMax(1.5)
        .build()
    );

    public final Setting<Integer> popWindowTicks = sgPop.add(new IntSetting.Builder()
        .name("pop-window-ticks")
        .description("Wie lange das Pop-Fenster mindestens offen sein muss, damit das Modul den Schuss zurueckhaelt. Das ist exakt die Zahl, die SlowFallingArrow.evaluate als popWindowTicks liefert.")
        .defaultValue(10)
        .min(1)
        .max(60)
        .build()
    );

    public final Setting<Integer> minPops = sgPop.add(new IntSetting.Builder()
        .name("min-pops")
        .description("Anzahl der Pop-Zyklen, die in das offene Fenster passen muessen, bevor es als Fenster gilt. 1 heisst: es lohnt sich, jetzt nicht zu schiessen. 0 heisst: jedes Fenster zaehlt, auch wenn dafuer kein zweiter Pop hineinpasst.")
        .defaultValue(1)
        .min(0)
        .max(4)
        .build()
    );


    private final GcdRotator.AngleDeltaAccumulator rotation = new GcdRotator.AngleDeltaAccumulator();
    private final Random rng = new Random();

    private int tickCounter;
    private int lastShotTick = -999;
    private int readyTick;
    /** true, solange dieses Modul den Mainhand-Slot haelt - dann erst ist die Pfad-Sperre noetig. */
    private boolean holdsSlot;
    private boolean swappedIn;
    private String currentAction = "-";

    public SlowFallingAura() {
        super(ProviPvPAddon.CATEGORY, "provi-slow-falling-aura",
            "Schiesst Slow-Falling-Pfeile (piercing-/Multishot-Armbrust) auf den Gegner und haelt den Schuss zurueck, solange bei ihm ein Pop-Fenster offen ist. Nimmt dem Getroffenen Crit und Mace-Smash und streckt sein Fallfenster rund 31x.");
    }

    @Override
    public void onActivate() {
        tickCounter = 0;
        lastShotTick = -999;
        readyTick = 0;
        holdsSlot = false;
        swappedIn = false;
        currentAction = "-";
        rotation.reset();

        // DEFERS, nicht EXCLUSIVE: beide Combat-Profile sollen laufen duerfen - dieses Modul ist
        // eine Ergaenzung, kein Ersatz. Die Prioritaet unter P_COMBAT macht den Rueckzug automatisch.
        PvpServices.conflicts().declare(getClass(), Mode.DEFERS, CONFLICT_PRIORITY,
            GodmodePvP.class, HumanPvP.class);
    }

    /**
     * Ohne diesen Aufruf bliebe der belegte Hotbar-Slot und die Baritone-Sperre ueber die Sitzung
     * stehen. Meteor kann {@code onDeactivate()} auch ohne vorangehendes {@code onActivate()}
     * aufrufen (Hauptmenue, Weltwechsel), deshalb wird nicht auf einen Zustand geprueft, sondern
     * beides bedingungslos freigegeben - beides ist idempotent.
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

        // Zuerst der Konflikt: ein laufendes Combat-Profil heisst "Crystal-Slot ist besetzt", und
        // diese eine Tatsache entscheidet den ganzen Tick - unabhaengig von allem, was danach kommt.
        if (PvpServices.conflicts().shouldYield(getClass(), PvpServices.activeModules())) {
            giveBackSlot();
            currentAction = "combat-modul-aktiv";
            return;
        }

        // Dann das eigene Budget. Ein Modul, das in diesem Tick schon gehandelt hat, darf nicht
        // noch einmal scannen - es kaeme ohnehin zu keinem Ergebnis mehr.
        if (PvpServices.spentThisTick(this)) return;

        Player target = findTarget(self);
        if (target == null) {
            giveBackSlot();
            currentAction = "kein-ziel";
            return;
        }

        if (self.distanceTo(target) > range.get()) {
            giveBackSlot();
            currentAction = "zu-weit";
            return;
        }

        // Die eine Entscheidung dieses Moduls. evaluate() liefert beide Antworten aus derselben
        // Zahl heraus: ob der Effekt sitzt und wie lang das Fallfenster dadurch geworden ist.
        SlowFallingArrow.Plan plan = SlowFallingArrow.evaluate(target.fallDistance, slowFallingTicks(target));

        if (popWindowOpen(plan)) {
            // Nur wenn dieses Modul den Slot auch wirklich haelt, wird Baritone angehalten -
            // zuschauen darf es, den Kampf nicht.
            if (swappedIn) holdSlot();
            currentAction = "pop-fenster-offen";
            return;
        }

        if (tickCounter - lastShotTick < shotCooldown.get()) {
            currentAction = "kein-pfeil-uebrig";
            return;
        }

        FindItemResult bow = findLoadedBow();
        if (!bow.found()) {
            giveBackSlot();
            currentAction = "keine-geladene-armbrust";
            return;
        }

        boolean needsSwap = !bow.isMainHand();
        if (needsSwap && !autoSwap.get()) {
            giveBackSlot();
            currentAction = "armbrust-nicht-in-der-hand";
            return;
        }

        // Slotwechsel ist eine eigene Belegung und ein eigener Tick. Wer den Slot belegt, muss
        // zwingend auch bestaetigt bekommen, dass der Wechsel durch ist - sonst schiesst der
        // Pfeil noch aus dem Slot, den gerade jemand anders braucht.
        if (needsSwap) {
            if (!PvpServices.claim(this, ActionKind.SWAP, PvpServices.P_SUPPORT)) {
                currentAction = "slot-belegt";
                return;
            }
            if (!InvUtils.swap(bow.slot(), true)) {
                PvpServices.broker().release(this);
                currentAction = "swap-fehlgeschlagen";
                return;
            }
            PvpServices.broker().spend(this, ActionKind.SWAP);
            swappedIn = true;
            holdSlot();
            readyTick = tickCounter + swapDelay.get();
            currentAction = "armbrust-herausgeholt";
            return;
        }

        if (tickCounter < readyTick) return;

        fire(self, target);
    }

    /**
     * Der Schuss selbst. Die Belegung wird erst <i>im Callback</i> verbraucht, nicht sofort nach dem
     * Einreihen der Rotation: {@code Rotations.rotate} fuehrt den Callback erst beim Senden des
     * Movement-Pakets aus. Wuerde hier schon "gespendet", waere der Tick fuer andere bereits vergeben,
     * obwohl das Paket noch gar nicht raus ist - genau die Belegung, die ein hoeher priorisiertes
     * Modul nicht mehr verdraengen duerfte.
     */
    private void fire(Player self, Player target) {
        if (!PvpServices.claim(this, ActionKind.PROJECTILE, PvpServices.P_SUPPORT)) {
            currentAction = "projektil-belegt";
            return;
        }

        Vec3 aim = target.getBoundingBox().getCenter();
        double yaw = Rotations.getYaw(aim);
        double pitch = Rotations.getPitch(aim);

        double divisor = GcdRotator.sensitivityDivisor(mc.options.sensitivity().get());
        GcdRotator.Rotation sent = rotation.quantize(yaw, pitch, divisor, rotationJitter.get(), rng);

        Rotations.rotate(sent.yaw(), sent.pitch(), PvpServices.P_SUPPORT, false, () -> {
            mc.gameMode.useItem(self, InteractionHand.MAIN_HAND);
            mc.gameMode.releaseUsingItem(self);
            lastShotTick = tickCounter;
            currentAction = "pfeil-unterwegs";
            PvpServices.broker().spend(this, ActionKind.PROJECTILE);
        });
    }

    /**
     * Ist ueberhaupt ein zweiter Pop in dieses Fenster hineinpassend? Ohne zweiten Pop waere das
     * "Fenster" nur ein einzelner Pop - und den soll der Crystal-Partner machen, nicht dieser Pfeil.
     */
    private boolean popWindowOpen(SlowFallingArrow.Plan plan) {
        return plan.popWindowTicks() >= popWindowTicks.get() && plan.maxRepeatedPops() >= minPops.get();
    }

    /** Restlaufzeit des Slow-Falling-Effekts am Ziel in Ticks; 0 = kein Effekt, kein Fenster. */
    private static int slowFallingTicks(Player target) {
        var effect = target.getEffect(MobEffects.SLOW_FALLING);
        if (effect == null) return 0;
        return Math.max(0, effect.getDuration());
    }

    /** Nur echte Spieler als Ziel - Freunde aus der Meteor-Liste werden nie angefasst. */
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
     * Eine Armbrust, die bereits mit Slow-Falling-Pfeilen geladen ist. Ein ungeladener oder mit
     * normalen Pfeilen geladener Schuss waere reine Verschwendung des Slots: der Effekt kommt
     * ausschliesslich aus der Tipped-Potion im Projektil.
     */
    private FindItemResult findLoadedBow() {
        // Bewusst nur die Hotbar: InvUtils.swap kann keinen Slot ausserhalb 0..8 in die Hand
        // bringen, und ein gefundenes Item aus dem Hauptinventar waere damit ein toter Treffer.
        return InvUtils.findInHotbar(this::isLoadedSlowFallingBow);
    }

    private boolean isLoadedSlowFallingBow(ItemStack stack) {
        if (!stack.is(Items.CROSSBOW) || !CrossbowItem.isCharged(stack)) return false;

        var charged = stack.get(DataComponents.CHARGED_PROJECTILES);
        if (charged == null) return false;

        for (ItemStack projectile : charged.itemCopies()) {
            if (isSlowFallingArrow(projectile)) return true;
        }
        return false;
    }

    private static boolean isSlowFallingArrow(ItemStack stack) {
        if (!stack.is(Items.TIPPED_ARROW)) return false;
        PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
        return contents != null && (contents.is(Potions.SLOW_FALLING) || contents.is(Potions.LONG_SLOW_FALLING));
    }

    /**
     * Solange dieses Modul den Mainhand-Slot haelt, muss Baritone stehen.
     *
     * <p>Der Grund ist nicht "Ordnung", sondern Geometrie: zwischen Slotwechsel und Schuss steht die
     * Armbrust in der Hand, und Baritones FollowProcess aendert Position und damit die Distanz zum
     * Ziel mitten in dieser Sequenz. Der Schuss ginge dann an eine andere Stelle als die Rotation.
     * Die Sperre wird deshalb erst beim tatsaechlichen Slotwechsel genommen und beim Zurueckgeben
     * des Slots wieder freigegeben - nie pauschal, waehrend der Bot nur zuschaut.
     */
    private void holdSlot() {
        holdsSlot = true;
        PvpServices.broker().path().pause(this);
    }

    /**
     * Slot und Pfad-Sperre zurueckgeben. Aufgerufen bei Zielverlust, Reichweite, Deaktivierung und
     * immer dann, wenn ein Combat-Modul laeuft - Baritone muss in dem Fall wieder laufen, auch wenn
     * dieses Modul eingeschaltet bleibt.
     */
    private void giveBackSlot() {
        if (swappedIn && mc.player != null) InvUtils.swapBack();
        swappedIn = false;
        holdsSlot = false;
        readyTick = 0;
        PvpServices.broker().path().resume(this);
    }

    /** Nur fuer Diagnose/Overlay; aendert nichts am Kampfverhalten. */
    public String currentAction() {
        return currentAction;
    }

    /** Nur fuer Diagnose/Overlay. */
    public boolean holdsCombatSlot() {
        return holdsSlot;
    }
}