package com.provipvp.core.events;

import net.minecraft.world.entity.LivingEntity;

/** Wird gepostet, wenn ein Combat-Modul sein Ziel wechselt. {@code from} bzw. {@code to} ist
 *  {@code null}, wenn es davor bzw. danach kein Ziel gab.
 *
 *  Kein {@code ICancellable}: das Event ist eine reine Beobachtung, kein Befehl - ein Abonnent
 *  kann den Zielwechsel nicht unterbinden (und soll das auch nicht). */
public record TargetChangeEvent(LivingEntity from, LivingEntity to) {}
