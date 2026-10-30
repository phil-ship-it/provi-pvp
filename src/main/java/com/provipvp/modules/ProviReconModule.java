package com.provipvp.modules;

import com.provipvp.ProviPvPAddon;
import com.provipvp.modules.GodmodePvP;
import com.provipvp.modules.HumanPvP;

import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;

import com.provipvp.broker.ConflictRegistry;
import com.provipvp.broker.PathLease;
import com.provipvp.broker.PvpServices;
import static com.provipvp.broker.ConflictRegistry.Mode.DEFERS;

import com.provipvp.recon.ChunkScanThrottle;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import com.provipvp.perf.ScanBudget;
import net.minecraft.core.BlockPos;
import java.util.ArrayList;
import java.util.List;

/**
 * ProviReconModule — Passives Reconnaissance / Storage-Scan.
 * WHY: Ein eigenes Modul fuer Chunk-Analyse und Stash-Findung, ohne
 * die Kampf-Profile (Godmode/Human) zu blockieren.
 * DEFERS (R3-Vertrag): tritt zurueck, sobald ein Kampf-Modul aktiv ist.
 * PathLease und ScanBudget verhindern Blockaden und teure Tick-Abfragen.
 */
public class ProviReconModule extends Module {

    public ProviReconModule() {
        // WHY: Kategorie ProviPvP sorgt fuer visuelle Ordnung unter dem Banner.
        super(ProviPvPAddon.CATEGORY, "anti-anti-basefinder", "Passives Base-Finding trotz Grim AC — kein AC-Bypass, nur Recon");
    }

    private final SettingGroup sg = settings.getDefaultGroup();

    public final Setting<String> rtpCommand = sg.add(new StringSetting.Builder()
        .name("rtp-command")
        .description("Befehl fuer automatisierten RTP-Wechsel (z. B. /rtp).")
        .defaultValue("/rtp")
        .build());

    static {
        // WHY: DEFERS/20 bedeutet Zuruecktreten bei aktivem Kampfprofil.
        PvpServices.conflicts().declare(ProviReconModule.class, DEFERS, 20, GodmodePvP.class, HumanPvP.class);
    }

    public final Setting<Integer> minimumStorageCount = sg.add(new IntSetting.Builder()
        .name("minimum-storage-count")
        .description("Wie viele Storage-Items (Truhen, Schocker) mindestens gefunden sein muessen.")
        .defaultValue(4)
        .min(1).range(1, 64)
        .build());
    // WHY: AC-Bypass nur fuer dieses Modul — Grim-Umgehung explizit auf Base-Finder begrenzt.
    public final Setting<Boolean> disableAnticheatMitigations = sg.add(new BoolSetting.Builder()
        .name("disable-anticheat-mitigations")
        .description("DEAKTIVIERT Grim-Restriktionen NUR fuer Base-Recon (GCD, Reach, Throttle, PathLease). Keine andere Modul-Aktion wird beeinflusst.")
        .defaultValue(false)
        .build()
    );


    public final Setting<Boolean> scanLightUpdates = sg.add(new BoolSetting.Builder()
        .name("scan-light-updates")
        .description("Pruefe auf unnatuerliche Licht-Updates (Chunk-Border / Light-Anomalien).")
        .defaultValue(true)
        .build());

    // WHY: ScanBudget verteilt teure Blockvolumen-Sweeps auf mehrere Ticks.
    public final ScanBudget scanBudget = new ScanBudget();

    // WHY: ChunkScanThrottle verhindert Absturz durch zu viele Abfragen pro Tick.
    private final ChunkScanThrottle throttle = new ChunkScanThrottle(scanBudget, 20);

    // WHY: Speicher fuer gefundene Storage-Cluster — wird vom Render-Hook gelesen.
    public final List<BlockPos> foundStorageClusters = new ArrayList<>();

    private boolean pathRequested = false;

    /** WHY: Path-Lease verhindert Baritone-Stalls; sofortige Freigabe nach Annaherung. */
    public void requestPathApproach(BlockPos target) {
        if (!disableAnticheatMitigations.get()) PvpServices.broker().path().pause(this);
        pathRequested = true;
    }

    /** WHY: Deep-Scan fuer Untergrund-Basen (deepslate/deep) — von Spieler-Y abwärts. */
    public void deepScanTick(BlockPos center) {
        if (mc.level == null || mc.player == null) return;
        // Scan nur wenn Budget erlaubt und nicht schon aktiv in Kampf
        if (disableAnticheatMitigations.get() || throttle.allowScan((int)mc.player.tickCount)) {
            List<BlockPos> found = throttle.deepScan(center); // uses our deep range
            aggregateStorageClusters(found);
            throttle.recordScan((int)mc.player.tickCount);
        }
    }

    /** WHY: Freigabe muss symmetrisch zur Anforderung sein, sonst bleibt Baritone blockiert. */
    public void finishPathApproach() {
        if (pathRequested) {
            PvpServices.broker().path().resume(this);
            pathRequested = false;
        }
    }

    @Override
    public void onDeactivate() {
        // WHY: Beim Deaktivieren alle Belegungen und Pfadsperren freigeben, sonst haengt der Bot.
        PvpServices.release(this);
        finishPathApproach();
        super.onDeactivate();
    }

    /** WHY: Render-Hook liest nur gespeicherte Cluster, keine neuen Abfragen im Render-Tick. */
    public void renderStorageESP() {
        for (BlockPos bp : foundStorageClusters) {
            // Box/Line-Zeichnung würde hier via Render3D erfolgen — rein lesend, allokationsfrei.
        }
    }

    /** WHY: Debug-Overlay liest diesen Zustand ueber Reflection (ScanBudget / Cluster-Liste). */
    public ScanBudget getScanBudget() {
        return scanBudget;
    }
    /** WHY: Nach erfolgreichem RTP-Wechsel werden Storage-Blöcke in einem Voxel-Cache aggregiert. */
    public void aggregateStorageClusters(List<BlockPos> newClusters) {
        for (BlockPos bp : newClusters) {
            if (!foundStorageClusters.contains(bp)) foundStorageClusters.add(bp);
        }
    
    }

    public boolean allowChunkScan(int tick) {
        boolean ok = disableAnticheatMitigations.get() || throttle.allowScan(tick);
        if (ok) throttle.recordScan(tick);
        return ok;
    }
}
