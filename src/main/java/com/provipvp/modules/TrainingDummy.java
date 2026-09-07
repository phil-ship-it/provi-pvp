package com.provipvp.modules;

import meteordevelopment.meteorclient.events.entity.player.AttackEntityEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.entity.DamageUtils;
import meteordevelopment.meteorclient.utils.entity.fakeplayer.FakePlayerEntity;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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

    public final Setting<Boolean> realExplosionHits = sgGeneral.add(new BoolSetting.Builder()
        .name("real-explosion-hits")
        .description("Der Dummy reagiert auch auf Crystal-/Anchor-/Bett-Explosionen in der Naehe mit echtem Schaden und Rueckstoss (nicht nur auf Nahkampf-Treffer) - erkannt daran, dass das jeweilige Crystal/der Anchor/das Bett zwischen zwei Ticks verschwindet.")
        .defaultValue(true)
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

    // Explosions-Erkennung: pro Tick verglichen mit dem vorherigen Snapshot - ein Crystal/Anchor/Bett, das
    // zwischen zwei Ticks verschwindet (bzw. beim Anchor: dessen Ladung sinkt), hat gerade detoniert.
    private final Set<Integer> trackedCrystalIds = new HashSet<>();
    private final Map<Integer, Vec3> lastCrystalPos = new HashMap<>();
    private final Map<BlockPos, Integer> trackedAnchorCharges = new HashMap<>();
    private final Set<BlockPos> trackedBeds = new HashSet<>();

    public TrainingDummy() {
        super(com.provipvp.ProviPvPAddon.CATEGORY, "training-dummy", "Spawnt einen Dummy mit einstellbarer HP - Nahkampf-Knockback UND Crystal-/Anchor-/Bett-Explosionen wirken wie bei einem echten Spieler, ausgeloest vom PvP-Bot oder manuell.");
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
        // Neuer Dummy -> alte Explosions-Snapshots sind wertlos (koennten sonst faelschlich eine laengst
        // verschwundene Crystal/einen laengst geladenen Anchor vom VORHERIGEN Dummy als Treffer werten).
        trackedCrystalIds.clear();
        lastCrystalPos.clear();
        trackedAnchorCharges.clear();
        trackedBeds.clear();
        info("Dummy gespawnt (HP %d) - Nahkampf UND Crystal/Anchor/Bett-Explosionen machen echten Schaden + Knockback.", dummyHealth.get());
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

    /** Schaden + Knockback, wenn IRGENDETWER (Bot, KillAura, du) den Dummy schlaegt - echte Vanilla-
     *  Rueckstoss-Formel (siehe LivingEntity#knockback): bestehende Geschwindigkeit wird halbiert statt
     *  ersetzt, der neue Schub kommt oben drauf; der vertikale Anteil wird nur dann auf 0.4 angehoben,
     *  wenn der Dummy gerade am Boden steht (in der Luft getroffen behaelt er seine Fallgeschwindigkeit -
     *  exakt wie bei einem echten Spieler). Beruecksichtigt Sprint-Bonus und die Knockback-Verzauberung
     *  der tatsaechlich gehaltenen Waffe. */
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

        applyMeleeKnockback(mc.player);
    }

    /** Vanilla-Basiswert 0.4 (siehe LivingEntity#knockback), +0.4 bei Sprint-Treffer ("sprint doubles
     *  knockback" - Minecraft Wiki), + pro Knockback-Stufe der Waffe skaliert auf denselben Faktor wie
     *  die von der Wiki dokumentierte Reichweiten-Erhoehung (2.586/1.552 Bloecke Zusatzstrecke pro Stufe
     *  gegenueber der 1.552-Bloecke-Basisreichweite). */
    private void applyMeleeKnockback(Player attacker) {
        double strength = 0.4;
        if (attacker.isSprinting()) strength += 0.4;
        int kbLevel = Utils.getEnchantmentLevel(attacker.getMainHandItem(), Enchantments.KNOCKBACK);
        strength += kbLevel * 0.4 * (2.586 / 1.552);

        Vec3 towardAttacker = attacker.position().subtract(dummy.position());
        towardAttacker = new Vec3(towardAttacker.x, 0, towardAttacker.z);
        towardAttacker = towardAttacker.lengthSqr() > 1.0E-4 ? towardAttacker.normalize() : new Vec3(0, 0, 1);

        double newVy = dummy.onGround() ? Math.min(0.4, velocity.y / 2.0 + strength) : velocity.y;
        velocity = new Vec3(velocity.x / 2.0 - towardAttacker.x * strength, newVy, velocity.z / 2.0 - towardAttacker.z * strength);
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

        checkExplosions();

        // Eigene Physik: Remote-Player integrieren deltaMovement nicht selbst
        Vec3 pos = dummy.position();
        double nx = pos.x + velocity.x;
        double nz = pos.z + velocity.z;
        double ny = pos.y + velocity.y;

        if (velocity.y < 0) {
            // Swept statt Einzelpunkt-Check: nach Knockback faellt der Dummy gravitationsbeschleunigt
            // (konvergiert gegen ~3.9 Bloecke/Tick), da reicht ein Bodencheck nur an der ZIEL-Position
            // dieses Ticks nicht - bei duennen Plattformen/Boeden ueber einer Hoehle sprang die Zielposition
            // schon mal komplett unter den soliden Block, ohne dass der einzelne Check-Punkt ihn je traf
            // (der Dummy fiel dann sichtbar durch ein paar Bloecke durch). Jetzt wird jede Block-Grenze
            // zwischen alter und neuer Y-Position der Reihe nach geprueft, von oben nach unten, und beim
            // ersten soliden Treffer gestoppt.
            int fromY = (int) Math.floor(pos.y - 0.05);
            int toY = (int) Math.floor(ny - 0.05);
            int bx = (int) Math.floor(nx);
            int bz = (int) Math.floor(nz);
            boolean landed = false;
            for (int by = fromY; by >= toY; by--) {
                if (mc.level.getBlockState(new BlockPos(bx, by, bz)).blocksMotion()) {
                    ny = by + 1.0;
                    velocity = new Vec3(velocity.x * 0.6, 0, velocity.z * 0.6);
                    landed = true;
                    break;
                }
            }
            if (!landed) velocity = new Vec3(velocity.x * 0.91, (velocity.y - 0.08) * 0.98, velocity.z * 0.91);
        } else {
            velocity = new Vec3(velocity.x * 0.91, (velocity.y - 0.08) * 0.98, velocity.z * 0.91);
        }

        dummy.setPos(nx, ny, nz);
    }

    /** Scannt Crystals/Anchors/Betten im 16-Block-Radius; verschwindet eines zwischen zwei Ticks (bzw.
     *  sinkt bei einem Anchor die Ladung), ist genau das die Detonation. Schaden kommt aus Meteors eigener
     *  DamageUtils - dieselbe Berechnung, die GodmodePvP/HumanPvP auch fuer die eigene Zielauswahl nutzen,
     *  inklusive echter Explosions-Sichtlinien-Pruefung. Der Rueckstoss wird aus der bereits berechneten
     *  Schadenszahl zurueckgerechnet (damage = (impact^2+impact)/2*84+1 -> impact), derselben Groesse, die
     *  Vanilla intern auch fuer den tatsaechlichen Explosions-Rueckstoss verwendet - der Dummy traegt
     *  planmaessig keine Ruestung, sonst waere die Ruecktransformation nicht mehr exakt. */
    private void checkExplosions() {
        if (!realExplosionHits.get()) return;

        Vec3 center = dummy.position();
        AABB scanBox = new AABB(center.x - 16, center.y - 16, center.z - 16, center.x + 16, center.y + 16, center.z + 16);

        Set<Integer> currentCrystals = new HashSet<>();
        for (EndCrystal ec : mc.level.getEntitiesOfClass(EndCrystal.class, scanBox)) {
            currentCrystals.add(ec.getId());
            lastCrystalPos.put(ec.getId(), ec.position());
        }
        for (int id : trackedCrystalIds) {
            if (!currentCrystals.contains(id)) {
                Vec3 pos = lastCrystalPos.remove(id);
                if (pos != null) applyExplosion(pos, DamageUtils.crystalDamage(dummy, pos));
            }
        }
        trackedCrystalIds.clear();
        trackedCrystalIds.addAll(currentCrystals);

        BlockPos dCenter = dummy.blockPosition();
        Map<BlockPos, Integer> currentAnchors = new HashMap<>();
        Set<BlockPos> currentBeds = new HashSet<>();
        for (int dx = -5; dx <= 5; dx++) {
            for (int dy = -5; dy <= 5; dy++) {
                for (int dz = -5; dz <= 5; dz++) {
                    BlockPos p = dCenter.offset(dx, dy, dz);
                    var state = mc.level.getBlockState(p);
                    if (state.is(Blocks.RESPAWN_ANCHOR)) {
                        currentAnchors.put(p, state.getValue(BlockStateProperties.RESPAWN_ANCHOR_CHARGES));
                    } else if (state.getBlock() instanceof BedBlock) {
                        currentBeds.add(p);
                    }
                }
            }
        }
        for (Map.Entry<BlockPos, Integer> entry : trackedAnchorCharges.entrySet()) {
            Integer now = currentAnchors.get(entry.getKey());
            if (entry.getValue() > 0 && (now == null || now < entry.getValue())) {
                Vec3 pos = Vec3.atCenterOf(entry.getKey());
                applyExplosion(pos, DamageUtils.anchorDamage(dummy, pos));
            }
        }
        trackedAnchorCharges.clear();
        trackedAnchorCharges.putAll(currentAnchors);

        for (BlockPos p : trackedBeds) {
            if (!currentBeds.contains(p)) {
                Vec3 pos = Vec3.atCenterOf(p);
                applyExplosion(pos, DamageUtils.bedDamage(dummy, pos));
            }
        }
        trackedBeds.clear();
        trackedBeds.addAll(currentBeds);
    }

    // Kalibriert per Simulation ueber dieselbe Luft-Physik wie oben (vy -= 0.08, *0.98; vx/vz *0.91):
    // impact=1.0 (Volltreffer aus naechster Naehe) launcht ~9 Bloecke hoch/~10 Bloecke weit - deckt sich
    // mit ueblichen Crystal-PvP-Pops, statt einem willkuerlich gegriffenen Wert.
    private static final double EXPLOSION_KNOCKBACK_SCALE = 1.6;

    private void applyExplosion(Vec3 explosionPos, float damage) {
        if (damage <= 0) return; // zu weit weg / komplett verdeckt - kein Treffer, kein Rueckstoss

        float remaining = damage;
        float abs = dummy.getAbsorptionAmount();
        if (abs > 0) {
            float used = Math.min(abs, remaining);
            dummy.setAbsorptionAmount(abs - used);
            remaining -= used;
        }
        if (remaining > 0) dummy.setHealth(Math.max(invincible.get() ? 1.0f : 0.0f, dummy.getHealth() - remaining));

        double impact = Math.max(0, Math.min(1, (-1 + Math.sqrt(Math.max(0, 1 + 8.0 * (damage - 1) / 84.0))) / 2.0));

        Vec3 away = dummy.position().add(0, dummy.getBbHeight() / 2.0, 0).subtract(explosionPos);
        away = away.lengthSqr() > 1.0E-6 ? away.normalize() : new Vec3(0, 1, 0);

        velocity = velocity.add(away.scale(impact * EXPLOSION_KNOCKBACK_SCALE));
    }

    @Override
    public String getInfoString() {
        return dummy != null ? "HP: " + Math.max(0, (int) Math.ceil(dummy.getHealth() + dummy.getAbsorptionAmount())) : "-";
    }
}
