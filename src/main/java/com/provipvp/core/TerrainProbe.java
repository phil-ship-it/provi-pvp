package com.provipvp.core;

import net.minecraft.world.phys.Vec3;

/** Minimale Sichtbarkeits-Schnittstelle, die {@code com.provipvp.terrain.ExplosionScanner}
 *  implementiert. Bewusst ein Interface in {@code core} statt ein direkter Typ-Import auf den
 *  Scanner: dadurch baut {@code com.provipvp.pearl.PearlSolver} gegen eine stabile API, ohne dass
 *  {@code core} die konkrete Implementierung kennen muss. */
public interface TerrainProbe {
    /** True, wenn die direkte Linie von {@code from} nach {@code to} frei von Blocken ist. */
    boolean clearShot(Vec3 from, Vec3 to);

    /** True, wenn die komplette Perlenbahn abgeschnitten durch {@code maxTicks} frei bleibt. */
    boolean trajectoryClear(Vec3 origin, double yaw, double pitch, Vec3 extraVel, int maxTicks);
}
