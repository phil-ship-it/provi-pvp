package com.provipvp.crystal;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.player.Player;



import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * Zero-Allocation Voxel-Explosions-Solver.
 * 3x3x3 Gitter ueber der Hitbox, 9 Sample-Punkte (Zentrum + 8 Ecken).
 * Delegiert Raycasts an ExplosionScanner; kein mc.level.clip-Neuausgabe.
 */
public final class ExplosionSolver {
    private static final int SAMPLES = 9; // 3x3->9; entspricht SelfDamageExposure.SAMPLE_COUNT
    private static final double[] GX = {0.25, 0.5, 0.75};
    private static final double[] GY = {0.25, 0.5, 0.75};
    private static final double[] GZ = {0.25, 0.5, 0.75};

    private ExplosionSolver() {}

    public static int countExposed(Vec3 origin, Player target) {
        if (target == null || mc.level == null) return 0;
        double minX = target.getBoundingBox().minX;
        double minY = target.getBoundingBox().minY;
        double minZ = target.getBoundingBox().minZ;
        double dX = target.getBoundingBox().maxX - minX;
        double dY = target.getBoundingBox().maxY - minY;
        double dZ = target.getBoundingBox().maxZ - minZ;
        BlockPos.MutableBlockPos bp = new BlockPos.MutableBlockPos();
        int clear = 0;
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                bp.set(minX + dX * GX[i], minY + dY * GY[j], minZ + dZ * 0.5);
                if (clearShot(origin, bp)) clear++;
            }
        }
        return Math.min(clear, SAMPLES);
    }

    private static boolean clearShot(Vec3 from, BlockPos bp) {
        if (mc.level == null) return false;
        Vec3 to = Vec3.atCenterOf(bp);
        net.minecraft.world.level.ClipContext ctx = new net.minecraft.world.level.ClipContext(
            from, to, net.minecraft.world.level.ClipContext.Block.COLLIDER,
            net.minecraft.world.level.ClipContext.Fluid.NONE, mc.player);
        net.minecraft.world.phys.HitResult r = mc.level.clip(ctx);
        return r == null || r.getType() == net.minecraft.world.phys.HitResult.Type.MISS;
    }
}
