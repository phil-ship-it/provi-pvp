package com.provipvp.modules;

import meteordevelopment.meteorclient.events.entity.player.AttackEntityEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import net.minecraft.core.BlockPos;
import meteordevelopment.meteorclient.settings.SettingGroup;
import net.minecraft.world.phys.Vec3;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.entity.DamageUtils;
import meteordevelopment.meteorclient.utils.entity.fakeplayer.FakePlayerEntity;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

public class TrainingDummy extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public final Setting<Integer> dummyHealth = sgGeneral.add(new IntSetting.Builder()
        .name("health")
        .description("HP des Dummies - laesst sich waehrend des Betriebs live aendern.")
        .defaultValue(20)
        .range(1, 100)
        .sliderRange(1, 40)
        .build()
    );

    public final Setting<Double> kbStrength = sgGeneral.add(new DoubleSetting.Builder()
        .name("kb-strength")
        .description("Staerke des horizontalen Knockbacks.")
        .defaultValue(0.5)
        .range(0.1, 1.5)
        .sliderRange(0.1, 1.0)
        .build()
    );

    public final Setting<Double> kbUp = sgGeneral.add(new DoubleSetting.Builder()
        .name("kb-up")
        .description("Vertikaler Knockback (Hoehe).")
        .defaultValue(0.4)
        .range(0.0, 1.0)
        .sliderRange(0.0, 0.8)
        .build()
    );

    public final Setting<Boolean> autoRespawn = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-respawn")
        .description("Spawnt den Dummy neu, wenn er stirbt oder verschwindet.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> invincible = sgGeneral.add(new BoolSetting.Builder()
        .name("invincible")
        .description("HP faellt nie auf 0 - kein Despawn/Respawn mehr noetig, endloses Ueben ohne Unterbrechung.")
        .defaultValue(false)
        .build()
    );

    private FakePlayerEntity dummy;
    private Object spawnLevel; // Welt/Verbindung, in der der Dummy zuletzt (re-)gespawnt wurde
    private int appliedHealth = -1;
    private Vec3 velocity = Vec3.ZERO;

    public TrainingDummy() {
        super(com.provipvp.ProviPvPAddon.CATEGORY, "training-dummy", "Spawnt einen Dummy mit einstellbarer HP, Schaden und Knockback - auch vom PvP-Bot.");
    }

    @Override
    public void onActivate() {
        MeteorClient.EVENT_BUS.subscribe(this);
        spawnDummy();
    }

    @Override
    public void onDeactivate() {
        MeteorClient.EVENT_BUS.unsubscribe(this);
        if (dummy != null) {
            dummy.despawn();
            dummy = null;
        }
    }

    private void spawnDummy() {
        dummy = new FakePlayerEntity(mc.player, "Dummy", dummyHealth.get(), true);
        dummy.spawn();
        spawnLevel = mc.level;
        appliedHealth = dummyHealth.get();
        info("Dummy gespawnt (HP %d) - Bot und Manuell-Schlaege machen Schaden + Knockback.", dummyHealth.get());
    }

    /** HP live aendern: ueber 20 HP wird Absorption genutzt. */
    private void applyHealth(float hp) {
        if (hp <= 20) {
            dummy.setHealth(hp);
            dummy.setAbsorptionAmount(0);
        } else {
            dummy.setHealth(20);
            dummy.setAbsorptionAmount(hp - 20);
        }
    }

    /** Schaden + Knockback, wenn IRGENDETWER (Bot, KillAura, du) den Dummy schlaegt. */
    @EventHandler
    public void onAttack(AttackEntityEvent event) {
        if (dummy == null || event.entity != dummy) return;

        float dmg = DamageUtils.getAttackDamage(mc.player, dummy);
        if (dmg <= 0) dmg = 1.0f;

        // Absorption zuerst abbauen
        float abs = dummy.getAbsorptionAmount();
        if (abs > 0) {
            float used = Math.min(abs, dmg);
            dummy.setAbsorptionAmount(abs - used);
            dmg -= used;
        }
        if (dmg > 0) dummy.setHealth(Math.max(invincible.get() ? 1.0f : 0.0f, dummy.getHealth() - dmg));

        // Knockback als Eigengeschwindigkeit (eigene Physik integriert sie pro Tick)
        Vec3 push = dummy.position().subtract(mc.player.position());
        push = new Vec3(push.x, 0, push.z);
        if (push.lengthSqr() > 0.01) {
            push = push.normalize();
            velocity = new Vec3(push.x * kbStrength.get(), kbUp.get(), push.z * kbStrength.get());
        } else {
            velocity = new Vec3(0, kbUp.get(), 0);
        }
    }

    @EventHandler
    public void onTick(TickEvent.Pre event) {
        if (!Utils.canUpdate() || dummy == null) return;

        // Welt/Server seit dem letzten Spawn gewechselt (Disconnect, Server-Wechsel im selben Netzwerk,
        // Warp in ein Match o.ae.) - NICHT blind an der neuen Position respawnen. Ein liegen gelassener
        // Trainings-Dummy, der in eine echte Begegnung/ein echtes Match hineinspawnt, wuerde von GodmodePvP/
        // HumanPvP als gueltiges (aber voellig harmloses) Ziel erkannt und die Zielwahl kapern.
        if (mc.level != spawnLevel) {
            dummy.despawn();
            dummy = null;
            toggle();
            error("Welt/Server gewechselt - Training Dummy deaktiviert statt in der neuen Umgebung neu zu spawnen.");
            return;
        }

        if (dummy.isRemoved() || (!dummy.isAlive() && dummy.getHealth() <= 0)) {
            if (autoRespawn.get()) {
                if (dummy.isRemoved()) dummy.despawn();
                spawnDummy();
            }
            return;
        }

        // Live-HP-Aenderung: die "health"-Einstellung wirkt sofort auf den laufenden Dummy,
        // ohne ihn despawnen/neu spawnen zu muessen.
        if (dummyHealth.get() != appliedHealth) {
            applyHealth(dummyHealth.get());
            appliedHealth = dummyHealth.get();
        }

        // Eigene Physik: Remote-Player integrieren deltaMovement nicht selbst
        Vec3 pos = dummy.position();
        double nx = pos.x + velocity.x;
        double ny = pos.y + velocity.y;
        double nz = pos.z + velocity.z;

        BlockPos ground = BlockPos.containing(nx, ny - 0.05, nz);
        if (velocity.y < 0 && mc.level.getBlockState(ground).blocksMotion()) {
            ny = ground.getY() + 1.0;
            velocity = new Vec3(velocity.x * 0.6, 0, velocity.z * 0.6);
        } else {
            velocity = new Vec3(velocity.x * 0.91, (velocity.y - 0.08) * 0.98, velocity.z * 0.91);
        }

        dummy.setPos(nx, ny, nz);
    }

    @Override
    public String getInfoString() {
        return dummy != null ? "HP: " + Math.max(0, (int) Math.ceil(dummy.getHealth() + dummy.getAbsorptionAmount())) : "-";
    }
}
