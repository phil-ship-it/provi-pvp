package com.provipvp.core;

import com.provipvp.core.events.ExplosionDetectedEvent;
import com.provipvp.core.events.TargetChangeEvent;
import meteordevelopment.orbit.IEventBus;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Abstrakte Basisklasse fuer die neuen Combat-Klassen (Ziel-/Perlen-/Explosionslogik).
 *
 *  <p>Enthaelt bewusst KEINE Kampf-Logik: hier liegen nur die drei Bausteine, die jede neue Klasse
 *  braucht - {@link TargetSelector}, {@link InventoryManager} und der Zugriff auf Meteors Orbit-Bus.
 *  Die konkrete Entscheidung, wann geworfen, angegriffen oder ausgewichen wird, bleibt in den
 *  Modulen.
 *
 *  <p>Bewusst KEIN {@code mc.*}: der Spieler kommt ueber Methoden-Parameter, der EventBus ueber den
 *  Konstruktor. {@link TerrainProbe} ist die Schnittstelle zu der Terrain-/Explosionsschicht
 *  ({@code com.provipvp.terrain.ExplosionScanner}); sie wird von aussen gesetzt, weil die
 *  konkrete Implementierung erst parallel zu diesem Paket entsteht. */
public abstract class CombatCore {

    protected final IEventBus bus;
    protected final TargetSelector targets;
    protected final InventoryManager inventory;

    private TerrainProbe terrain;

    protected CombatCore(IEventBus bus, TargetSelector targets, InventoryManager inventory) {
        this.bus = bus;
        this.targets = targets;
        this.inventory = inventory;
    }

    /** Terrain-/Sichtbarkeitsschicht injizieren (muss vor der ersten Nutzung gesetzt sein). */
    protected void setTerrain(TerrainProbe terrain) {
        this.terrain = terrain;
    }

    /** @return null, solange {@link #setTerrain} nicht aufgerufen wurde - Aufrufer pruefen das. */
    protected TerrainProbe terrain() {
        return terrain;
    }

    protected void publishExplosion(Vec3 source, double selfDamage, double victimDamage,
                                    LivingEntity victim, int tick) {
        bus.post(new ExplosionDetectedEvent(source, selfDamage, victimDamage, victim, tick));
    }

    protected void publishTargetChange(LivingEntity from, LivingEntity to) {
        bus.post(new TargetChangeEvent(from, to));
    }
}
