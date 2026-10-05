package com.provipvp.modules;

import com.provipvp.ProviPvPAddon;
import com.provipvp.broker.ActionBroker.ActionKind;
import com.provipvp.broker.ConflictRegistry.Mode;
import com.provipvp.broker.PvpServices;
import com.provipvp.mechanics.WindChargeModel;
import com.provipvp.rotation.GcdRotator;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.Vec3;

import java.util.Random;

/**
 * Wind als Angriff und als Mobilitaet — zwei voellig verschiedene Dinge, die beide "Wind" heissen.
 *
 * <p><b>Die von Hand geworfene Wind Charge</b> ist ein Item und der eigentliche Grund fuer dieses
 * Modul: 1 Schaden, eine 2.4-Block-Kugel und ein Knockback-Multiplikator von <b>1.22</b> — also mehr
 * Schub als ein Windschlag. Dafuer kostet jeder Wurf 10 Ticks, in denen kein zweiter rausgeht. Das
 * ist ein burstweiliger, sehr gezielter Einzelschaden: er loest sich aus, wenn der Gegner ohnehin
 * ueber einer Kante oder in einem Loch steht, und laeuft sonst nur ins Leere.
 *
 * <p><b>Warum dieses Modul neben einem Crystal-Kampf ueberhaupt sicher ist.</b> Wind Charges
 * detonieren seit <b>1.20.5</b> keine End Crystals mehr. Es gibt also keinen Konflikt, in dem dieses
 * Modul den Crystal eines Combat-Profils wegschiessen koennte — genau der Grund, warum hier kein
 * {@link ActionKind#PROJECTILE} und kein Crystal-Konflikt deklariert wird. Der Konflikt gegen die
 * Combat-Profile besteht trotzdem, aber er betrifft nicht die Sache selbst, sondern die
 * Hotbar-Ressource: beide brauchen dieselbe Mainhand, und zwei Aktionen in einem Tick sind genau
 * das, was Grim unter {@code MultiActions} fuehrt.
 *
 * <p><b>Der Mace-Delikat Wind Burst</b> ist davon getrennt und wird hier nur <i>gelesen</i>: 8 / 16
 * / 24 Bloecke Selbstschuss auf I / II / III, Multiplikator 1.2 / 1.75 / 2.2. Er wirkt
 * ausschliesslich beim Smash-Angriff und <b>negiert den Fallschaden nicht</b>. Wer sich damit in die
 * Luft schiesst, nimmt beim Landen vollen Schaden — deshalb rechnet dieses Modul den Burst nie als
 * Fluchtmittel ein, sondern nur als Abstandsmanagement fuer einen Smash, den ohnehin geschlagen wird.
 *
 * <p><b>Belegung.</b> {@link ActionKind#USE_ITEM} mit {@link PvpServices#P_SUPPORT} fuer den Wurf,
 * {@link ActionKind#SWAP} mit derselben Prioritaet fuer den Slotwechsel. Der Slotwechsel laeuft
 * bewusst nicht mit P_COMBAT: ein Wind-Wurf ist eine Unterstuetzungsaktion, und er darf keinen
 * Crystal-Slot gegen sich stellen.
 *
 * @see WindChargeModel
 */
public class WindChargeModule extends Module {

    /**
     * {@link PvpServices#P_OPPORTUNIST}: der Wert wird mit dem der Combat-Profile verglichen, und
     * dieses Modul soll sie nie verdraengen koennen — ein Windschub ist nie einen Crystal wert.
     */
    private static final int CONFLICT_PRIORITY = PvpServices.P_OPPORTUNIST;

    /**
     * Der Nahkampfabstand, mit dem ein Wind Burst noch etwas abkuerzt: der Selbstschuss schiesst
     * nach <i>oben</i>, er schliesst keine Distanz. Er zahlt sich nur aus, wenn der Smash, der ihn
     * ausloest, ohnehin noch stattfindet.
     */
    private static final double BURST_MELEE_WINDOW = 3.0;

    /** Was dieses Modul benutzt. */
    public enum WindMode {
        /** Nur die von Hand geworfene Wind Charge. */
        Charge,
        /** Nur der Mace-Delikat Wind Burst als Abstandsmanagement. */
        WindBurst,
        /** Beides; im selben Tick gewinnt der Wurf, weil er sofort Schaden macht. */
        Both
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgWind = settings.createGroup("1 · Wind");
    private final SettingGroup sgBurst = settings.createGroup("2 · Wind Burst");

    public final Setting<WindMode> mode = sgGeneral.add(new EnumSetting.Builder<WindMode>()
        .name("mode")
        .description("Welche der beiden Wind-Quellen dieses Modul benutzt. Der Burst ist reine Mobilitaet und macht selbst keinen Schaden.")
        .defaultValue(WindMode.Both)
        .build()
    );

    public final Setting<Boolean> autoSwap = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-swap")
        .description("Wechselt das noetige Item in die Mainhand. Ausgeschaltet benutzt das Modul nur, was ohne Slotwechsel schon in der Hand liegt - dann kann es nie einen Slot gegen einen laufenden Kampf belegen.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Integer> swapDelay = sgGeneral.add(new IntSetting.Builder()
        .name("swap-delay")
        .description("Ticks bis zur Aktion nach dem Slotwechsel. Der Server muss den Wechsel bestaetigt haben, sonst wird noch aus dem vorherigen Slot benutzt.")
        .defaultValue(2)
        .min(1)
        .max(10)
        .build()
    );

    public final Setting<Double> rotationJitter = sgGeneral.add(new DoubleSetting.Builder()
        .name("rotation-jitter")
        .description("Streuung um den Zielwinkel in Grad. Meteor quantisiert Rotationen nicht - Gitter und Duplikat-Vermeidung kommen aus dem GcdRotator dieses Addons.")
        .defaultValue(0.3)
        .min(0.0)
        .max(1.5)
        .sliderMin(0.0)
        .sliderMax(1.5)
        .build()
    );

    public final Setting<Double> throwRange = sgWind.add(new DoubleSetting.Builder()
        .name("throw-range")
        .description("Distanz, bis zu der geworfen wird. Das Modell gibt 2.4 Bloecke Wirkungsradius vor; alles darueber trifft die Charge nicht mehr. Der Wert kann deshalb nur kleiner sein, nie groesser.")
        .defaultValue(WindChargeModel.PLAYER_CHARGE_RADIUS)
        .min(0.5)
        .max(WindChargeModel.PLAYER_CHARGE_RADIUS)
        .sliderMin(0.5)
        .sliderMax(WindChargeModel.PLAYER_CHARGE_RADIUS)
        .build()
    );

    public final Setting<Integer> throwCooldown = sgWind.add(new IntSetting.Builder()
        .name("throw-cooldown")
        .description("Mindestabstand zwischen zwei Wind-Charge-Wuerfen in Ticks. Das Modell gibt 10 vor; darunter waere jeder zweite Wurf einer, den die Abklingzeit ohnehin verschluckt.")
        .defaultValue(WindChargeModel.PLAYER_CHARGE_COOLDOWN_TICKS)
        .min(1)
        .max(60)
        .build()
    );

    public final Setting<Boolean> onlyNearDrop = sgWind.add(new BoolSetting.Builder()
        .name("only-near-drop")
        .description("Wirft nur, wenn unter dem Gegner kein Boden ist. Das ist der eigentliche Nutzen des 1.22x-Knockbacks: er schiebt ueber eine Kante hinweg. Ohne Kante bleiben 1 Schaden und die 10-Ticks-Sperre.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Integer> minBurstLevel = sgBurst.add(new IntSetting.Builder()
        .name("min-burst-level")
        .description("Ab welcher Wind-Burst-Stufe der Selbstschuss eingeplant wird. Stufe I schiesst 8 Bloecke mal 1.2, Stufe III 24 mal 2.2 - unterhalb der eingestellten Stufe fehlt schlicht die Reichweite, um eine Luecke zu ueberbruecken.")
        .defaultValue(1)
        .min(1)
        .max(WindChargeModel.WIND_BURST_LEVELS)
        .build()
    );

    private final GcdRotator.AngleDeltaAccumulator rotation = new GcdRotator.AngleDeltaAccumulator();
    private final Random rng = new Random();

    private int tickCounter;
    private int readyTick;
    private int lastThrowTick = -999;
    private boolean swappedIn;
    private String currentAction = "-";

    public WindChargeModule() {
        super(ProviPvPAddon.CATEGORY, "provi-wind-charge",
            "Wind-Charge-Wurf (1 Schaden, 2.4-Block-Kugel, 1.22x Knockback) als Schubs ueber eine Kante und der Mace-Delikat Wind Burst als Abstandsmanagement. Sprengt seit 1.20.5 keine End Crystals - deshalb stoert es einen Crystal-Kampf nicht.");
    }

    @Override
    public void onActivate() {
        tickCounter = 0;
        readyTick = 0;
        lastThrowTick = -999;
        swappedIn = false;
        currentAction = "-";
        rotation.reset();

        PvpServices.conflicts().declare(getClass(), Mode.DEFERS, CONFLICT_PRIORITY,
            GodmodePvP.class, HumanPvP.class);
    }

    /**
     * Slot, Baritone-Sperre und Broker-Belegungen loesen. Ohne das haelt ein ausgeschaltetes Modul den
     * Mainhand-Slot weiter fest, und weil Baritones Sperre referenzgezaehlt ist, laeuft erst wieder
     * etwas, wenn irgendein anderes Modul sie aus Versehen mit freigibt.
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

        // Ein Combat-Profil laeuft: dann ist die Mainhand die Crystal-Waffe, und ein Windschub waere
        // eine Waffe gegen sich selbst. Zuruecktreten und den Slot wieder hergeben.
        if (PvpServices.conflicts().shouldYield(getClass(), PvpServices.activeModules())) {
            giveBackSlot();
            currentAction = "combat-modul-aktiv";
            return;
        }

        // Das eigene Budget zuerst: wer diesen Tick schon geworfen hat, hat fuer einen zweiten Wurf
        // keine Belegung mehr. Diese Pruefung ist billiger als der Zielscan.
        if (PvpServices.spentThisTick(this)) return;

        Player target = findTarget(self);
        if (target == null) {
            giveBackSlot();
            currentAction = "kein-ziel";
            return;
        }

        if (mode.get() != WindMode.WindBurst && throwCharge(self, target)) return;

        if (mode.get() != WindMode.Charge) planWindBurst(self, target);
    }

    /**
     * Der Wurf. Zwei Belegungen und damit zwei Ticks: erst der Slot, dann das Item. Ein Wurf im
     * Wechsel-Tick benutzt noch das vorherige Item — und "ich habe geworfen" waere dann eine halbe
     * Wahrheit, weil keine Wind Charge im Spiel war.
     *
     * @return true, wenn dieser Tick fuer den Wurf belegt wurde
     */
    private boolean throwCharge(Player self, Player target) {
        WindChargeModel.WindCharge charge = WindChargeModel.playerCharge();
        double dist = self.distanceTo(target);

        if (dist > throwRange.get() || !charge.hits(dist)) {
            currentAction = "ausser-wirkradius";
            return false;
        }
        if (!WindChargeModel.playerChargeReady(tickCounter - lastThrowTick)) {
            currentAction = "wind-charge-abklingt";
            return false;
        }
        if (onlyNearDrop.get() && !dropBelow(target)) {
            currentAction = "kein-abgrund";
            return false;
        }

        if (!self.getMainHandItem().is(Items.WIND_CHARGE)) {
            return prepareItem(Items.WIND_CHARGE, "wind-charge");
        }

        if (tickCounter < readyTick) return false;

        if (!PvpServices.claim(this, ActionKind.USE_ITEM, PvpServices.P_SUPPORT)) {
            currentAction = "item-belegt";
            return false;
        }

        Vec3 aim = target.getBoundingBox().getCenter();
        double divisor = GcdRotator.sensitivityDivisor(mc.options.sensitivity().get());
        GcdRotator.Rotation sent = rotation.quantize(
            Rotations.getYaw(aim), Rotations.getPitch(aim), divisor, rotationJitter.get(), rng);

        Rotations.rotate(sent.yaw(), sent.pitch(), PvpServices.P_SUPPORT, false, () -> {
            mc.gameMode.useItem(self, InteractionHand.MAIN_HAND);
            lastThrowTick = tickCounter;
            currentAction = "wind-charge-geworfen";
            PvpServices.broker().spend(this, ActionKind.USE_ITEM);
        });
        return true;
    }

    /**
     * Der Mace-Delikat Wind Burst.
     *
     * <p>Diese Methode <b>wirft nichts und belegt nichts</b>. Sie beantwortet eine Frage, die sich
     * ohne sie niemand stellt: haelt der Bot einen Mace mit Wind Burst, und wuerde der Selbstschuss
     * aus {@link WindChargeModel#windBurst} die Luecke zum Gegner ueberbruecken? Der Smash-Angriff
     * selbst gehoert dem Combat-Profil und dessen {@link ActionKind#MELEE}-Belegung — dieses Modul
     * liefert nur die Zahl, nach der sich die Entfernung richtet.
     *
     * <p>Der Weg ueber das Modell ist auch eine Absicherung: es liefert {@code smashOnly == true}
     * und {@code negatesFallDamage == false}. Ein Bot, der den Selbstschuss als Fluchtmittel
     * einplant, nimmt beim Landen vollen Schaden — die beiden Felder machen genau das unmoeglich,
     * ohne dass hier eine zweite Kopie dieser Heuristik entsteht.
     */
    private void planWindBurst(Player self, Player target) {
        ItemStack mace = self.getMainHandItem();
        int level = Utils.getEnchantmentLevel(mace, Enchantments.WIND_BURST);
        if (level < minBurstLevel.get()) {
            currentAction = "kein-wind-burst";
            return;
        }

        WindChargeModel.WindBurst burst = WindChargeModel.windBurst(level);
        if (burst == null || !burst.smashOnly()) {
            currentAction = "kein-wind-burst";
            return;
        }

        // Der Selbstschuss traegt den Bot nach oben, nicht zum Gegner: er schliesst keine Distanz,
        // er beendet den Smash-Kontakt. Laeuft der Bot bereits aufwaerts, gibt es nichts zu beenden.
        if (!self.onGround() && self.getDeltaMovement().y >= 0) {
            currentAction = "schon-in-der-luft";
            return;
        }

        double launch = burst.totalLaunchBlocks();
        if (self.distanceTo(target) > launch + BURST_MELEE_WINDOW) {
            currentAction = "burst-reicht-nicht";
            return;
        }

        currentAction = "wind-burst-bereit (" + Math.round(launch) + " Bloecke)";
    }

    /**
     * Holt ein Item in die Mainhand. Scheitert der Wechsel, wird die Belegung sofort wieder
     * freigegeben — ein haengender SWAP-Claim nähme jedem anderen Modul den Slot, ohne dass hier
     * etwas passiert.
     *
     * @return true, wenn dieser Tick der Slotwechsel war
     */
    private boolean prepareItem(Item item, String label) {
        if (!autoSwap.get()) {
            currentAction = label + "-nicht-in-der-hand";
            return false;
        }

        FindItemResult found = InvUtils.findInHotbar(item);
        if (!found.found()) {
            currentAction = label + "-nicht-vorhanden";
            return false;
        }
        if (found.isMainHand()) return false;

        if (!PvpServices.claim(this, ActionKind.SWAP, PvpServices.P_SUPPORT)) {
            currentAction = "slot-belegt";
            return false;
        }
        if (!InvUtils.swap(found.slot(), true)) {
            PvpServices.broker().release(this);
            currentAction = "swap-fehlgeschlagen";
            return false;
        }
        PvpServices.broker().spend(this, ActionKind.SWAP);
        swappedIn = true;
        // Der Slotwechsel ist der einzige Grund, Baritone anzuhalten: zwischen ihm und dem Wurf
        // aendert sich sonst die Position, und der Wurf ginge an eine andere Stelle als die Rotation.
        PvpServices.broker().path().pause(this);
        readyTick = tickCounter + swapDelay.get();
        currentAction = label + "-herausgeholt";
        return true;
    }

    /**
     * Steht unter dem Gegner noch Boden? Ohne Kante ist der 1.22x-Knockback ein Schaden von 1 HP und
     * sonst nichts — der haeufigste Grund, warum ein Wind-Charge-Wurf ins Leere "nichts gebracht"
     * hat. Geprueft wird die Zelle unter den Fuessen, weil genau dorthin der Schub zurueck will.
     */
    private boolean dropBelow(Player target) {
        if (!target.onGround()) return true;
        return mc.level == null || !mc.level.getBlockState(target.blockPosition().below()).isSolidRender();
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
     * Slot und Pfad-Sperre zurueckgeben. Aufgerufen bei Zielverlust, Reichweite, Deaktivierung und
     * immer dann, wenn ein Combat-Profil laeuft — Baritone muss wieder laufen, sobald dieses Modul den
     * Bot nicht mehr fuehrt, auch wenn es eingeschaltet bleibt und nur wartet.
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
}