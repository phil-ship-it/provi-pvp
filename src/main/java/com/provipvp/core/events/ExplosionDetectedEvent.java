package com.provipvp.core.events;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Wird gepostet, wenn eine Explosion im Umfeld des Spielers erkannt wurde.
 *
 *  @param source       Explosionszentrum (Blockposition der Explosion)
 *  @param selfDamage   berechneter Schaden, den der Spieler dadurch bekommen haette
 *  @param victimDamage berechneter Schaden fuer {@code victim}; 0 wenn es keinen gab
 *  @param victim       getroffenes LivingEntity bzw. {@code null}
 *  @param tick         {@code mc.world.getTime()}-aehnlicher Tick-Zaehler beim Erkennen
 *
 *  Kein {@code ICancellable}: reine Beobachtung, kein abfangbarer Befehl. */
public record ExplosionDetectedEvent(Vec3 source, double selfDamage, double victimDamage,
                                    LivingEntity victim, int tick) {}
