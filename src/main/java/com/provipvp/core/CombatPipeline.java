package com.provipvp.core;

import com.provipvp.core.TargetSelector.CandidateSource;
import com.provipvp.exec.CombatExecutor;
import com.provipvp.pearl.PearlSolver;
import com.provipvp.exec.RotationQueue;
import com.provipvp.terrain.ExplosionScanner;
import meteordevelopment.orbit.IEventBus;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.Random;
import java.util.function.Predicate;

/**
 * Konkrete Wurzel des Kampf-Objektgraphs: baut genau einmal alle Bausteine auf und haelt sie
 * konsistent zusammen.
 *
 * <p><b>Warum es diese Klasse gibt.</b> {@link CombatCore} ist abstrakt, ein Meteor-Modul muss aber
 * von {@code Module} erben — Java erlaubt keine Mehrfachvererbung. Ein Modul kann also nicht
 * gleichzeitig {@code Module} und {@code CombatCore} sein. Statt die Vererbung zu ueberlisten und
 * {@code CombatCore} zur toten Dekoration zu machen, haelt ein Modul eine {@code CombatPipeline} per
 * Komposition. Die geforderte „alle neuen Module erben von CombatCore" gilt damit fuer die
 * Kampf-*Logik*-Klassen; das Modul selbst delegiert an die Pipeline.
 *
 * <p>Der Graph wird pro Weltwechsel neu gebaut, weil {@link InventoryManager} den Spieler ueber den
 * Konstruktor bekommt und {@code mc.player} beim Deaktivieren bereits null sein kann.
 */
public final class CombatPipeline extends CombatCore {

    private final ExplosionScanner scanner;
    private final PearlSolver pearls;
    private final RotationQueue rotations;
    private final CombatExecutor executor;

    public RotationQueue rotations() {
        return rotations;
    }
    /**
     * @param bus             Meteors Event-Bus; {@code TargetChangeEvent} und
     *                        {@code ExplosionDetectedEvent} werden darueber veroeffentlicht
     * @param self            der lokale Spieler; die Pipeline haelt keine {@code mc.player}-Referenz
     * @param targetFilter    erlaubte Gegner (Team-/Tot-/Sichtfilter)
     * @param candidates      liefert die Kandidatenliste fuer den Tick
     * @param followRange     Reichweite, ab der ein Ziel gehalten wird
     * @param pauseAutonomy   haelt Baritone/Follow waehrend einer Slot-Reserve an
     * @param rescueVariance  +/- Spanne des {@code pearlPitchVariance} fuer Rettungswuerfe
     * @param reservePriority Rotationsprioritaet der Combat-Aktionen
     */
    public CombatPipeline(IEventBus bus,
                          Player self,
                          Predicate<LivingEntity> targetFilter,
                          CandidateSource candidates,
                          double followRange,
                          Runnable pauseAutonomy,
                          double rescueVariance,
                          int reservePriority) {
        super(bus, new TargetSelector(bus, targetFilter, followRange, candidates),
            new InventoryManager(self, pauseAutonomy));

        this.scanner = new ExplosionScanner();
        this.pearls = new PearlSolver(scanner);
        this.rotations = new RotationQueue();
        this.executor = new CombatExecutor(inventory, rotations, rescueVariance, reservePriority, new Random());
        setTerrain(scanner);
    }

    /** Baut die Pipeline gegen den aktuell verbundenen Spieler neu auf. */
    public static CombatPipeline create(IEventBus bus,
                                         Player self,
                                         Predicate<LivingEntity> targetFilter,
                                         CandidateSource candidates,
                                         double followRange,
                                         Runnable pauseAutonomy,
                                         double rescueVariance,
                                         int reservePriority) {
        return new CombatPipeline(bus, self, targetFilter, candidates, followRange,
            pauseAutonomy, rescueVariance, reservePriority);
    }

    public ExplosionScanner scanner() {
        return scanner;
    }

    public PearlSolver pearls() {
        return pearls;
    }

    public CombatExecutor executor() {
        return executor;
    }

    /**
     * Einmal pro Tick VOR der ersten Szenario-Abfrage aufrufen. Ohne das liefert
     * {@link ExplosionScanner#trajectoryClear} veraltete Cache-Treffer und die Bogenauswahl im
     * {@code terrainBypass} schaltet zwischen Ticks.
     */
    public void onTick(int tick) {
        rotations.onTick();
        scanner.markTick(tick);
    }

    /** Blockaenderung melden, damit gecachte Strahlen, die durch die Zelle laufen, verfaellen. */
    public void onBlockUpdate(BlockPos pos) {
        scanner.onBlockUpdate(pos);
    }
}
