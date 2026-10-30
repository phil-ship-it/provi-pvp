package com.provipvp.recon;

import static meteordevelopment.meteorclient.MeteorClient.mc;

import com.provipvp.perf.ScanBudget;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;

import java.util.ArrayList;
import java.util.List;

/** Minimal stub mit DEEP-SCAN — sucht Basen unterhalb des Spielers (deepslate/deep). */
public class ChunkScanThrottle {
    private final ScanBudget budget;
    private final int throttleRate;
    private int lastTick = -1;

    public ChunkScanThrottle(ScanBudget budget, int throttleRate) {
        this.budget = budget;
        this.throttleRate = throttleRate;
    }

    public boolean allowScan(int tick) {
        if (tick == 0) return true;
        return tick - lastTick >= throttleRate;
    }

    public void recordScan(int tick) {
        lastTick = tick;
    }

    /** DEEP-SCAN: von Spieler-Y abwärts bis zu -40 (Básen sind tiefer als Deepslate-Start). */
    public List<BlockPos> deepScan(BlockPos center) {
        List<BlockPos> found = new ArrayList<>();
        int cx = center.getX();
        int cy = center.getY();
        int cz = center.getZ();
        // Radius 4 horizontal, tief von cy bis cy-40
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                for (int dy = 0; dy >= -70; dy--) {
                    int y = cy + dy;
                    BlockPos bp = new BlockPos(cx + dx, y, cz + dz);
                    // Storage-Check (vereinfacht — echte Prüfung via Module-Callback)
                    if (isStorage(bp)) found.add(bp);
                }
            }
        }
        return found;
    }

    private boolean isStorage(BlockPos bp) {
        if (mc == null || mc.level == null) return false;
        net.minecraft.world.level.block.entity.BlockEntity be = mc.level.getBlockEntity(bp);
        return be instanceof ChestBlockEntity || be instanceof ShulkerBoxBlockEntity || be instanceof EnderChestBlockEntity;
    }
}
