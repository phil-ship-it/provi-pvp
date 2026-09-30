package com.provipvp.exec;

import meteordevelopment.meteorclient.utils.player.Rotations;

/**
 * Die Rotations-Warteschlange des Kampfes — der eine Ort, an dem eine Blickrichtung fuer eine Aktion
 * beansprucht wird.
 *
 * <p>Aus {@code GodmodePvP.rotateAndRun} (:3072) herausgeloest, damit die Warteschlange nicht mehr im
 * Kampfmodul nebenbei mitlaeuft. Derselbe Code war zwischen {@code GodmodePvP} und {@code HumanPvP}
 * dupliziert, und genau dort sitzt der Starvation-Bug aus Woche 1.
 *
 * <p>Warum {@code clientSide} mitzaehlt: Meteors {@code Rotations.rotate} nimmt den
 * {@code clientSide}-Schalter fuer alle Eintraege ausser dem ersten dieses Ticks. Der erste laeuft ueber
 * den normalen Bewegungspaket-Pfad und setzt die echte Rotation, jeder weitere bekommt mit
 * {@code clientSide=true} ebenfalls kurzzeitig die echte Rotation gesetzt — exakt fuer die Dauer
 * seines eigenen Callbacks (siehe {@code Rotations.onSendMovementPacketsPost}).
 *
 * <p>Keine Modul-Settings und kein {@code mc.*}-Zugriff: die Klasse ist damit ohne laufenden Client
 * testbar (siehe {@code RotationQueueTest}).
 */
public final class RotationQueue {

    /** Prioritaet eines Perlenwurfs, portiert aus {@code GodmodePvP.PRIORITY_PEARL}. */
    public static final int PRIORITY_PEARL = 70;

    /** Prioritaet einer Crystal-Aktion, portiert aus {@code GodmodePvP.PRIORITY_CRYSTAL}. */
    public static final int PRIORITY_CRYSTAL = 100;

    /** Prioritaet einer Anker-Aktion, portiert aus {@code GodmodePvP.PRIORITY_ANCHOR}. */
    public static final int PRIORITY_ANCHOR = 90;

    /** Prioritaet einer Bett-Aktion, portiert aus {@code GodmodePvP.PRIORITY_BED}. */
    public static final int PRIORITY_BED = 80;
    public static final int PRIORITY_MISC = 60;

    /** Niedrigste Prioritaet: rein kosmetisches Free-Look, darf echte Aktionen nie verdraengen. */
    public static final int PRIORITY_LOOK = 0;

    /** Anzahl der diesen Tick bereits eingereihten Rotationen — steuert {@code clientSide}. */
    private int queuedThisTick;

    /**
     * Ob diesen Tick bereits eine "echte" Aktion gerendert wurde, siehe {@link #execute}.
     */
    private boolean realActionThisTick;

    /** Anzahl der bisher eingereihten Rotationen seit dem letzten {@link #onTick()}. */
    public int queuedThisTick() {
        return queuedThisTick;
    }

    /**
     * Reiht Rotation + Aktion in Meteors Rotations-Queue ein und gibt immer {@code true} zurueck
     * (der Erfolgsfall greift immer — frueheren Aufrufern, die auf {@code false} pruefen mussten,
     * bleibt das Verhalten unveraendert).
     *
     * <p><b>Rotations-Queue-Starvation:</b> {@link #queuedThisTick} allein reicht als
     * "diesen Tick ist die Rotation schon vergeben"-Merkmal nicht, weil der Callback asynchron in
     * {@code Rotations.onSendMovementPacketsPost} laeuft: eine echte Aktion kann bereits gequeued sein
     * ({@code queuedThisTick > 0}), ihr Callback aber noch gar nicht gelaufen sein. Genau dann wuerde der
     * kosmetische Free-Look-Tail-Flush (Prioritaet {@link #PRIORITY_LOOK}) als letzter Eintrag doch noch
     * die Rotation beanspruchen und die echte Aktion verdraengen bzw. hinter sich schieben. Deshalb
     * wird {@link #realActionThisTick} gesetzt, <b>wenn und nur wenn</b> {@code priority > PRIORITY_LOOK}
     * — nur diese Prioritaeten sind Aktionen, die den Slot wirklich verdienen; ein Free-Look selbst
     * setzt das Flag nicht und kann sich damit nicht selbst unterdruecken.
     *
     * @param callback darf {@code null} sein (reine Rotation ohne Aktion, z.B. Free-Look)
     */
    public boolean execute(double yaw, double pitch, int priority, Runnable callback) {
        Rotations.rotate(yaw, pitch, priority, queuedThisTick > 0, callback);
        queuedThisTick++;
        if (priority > PRIORITY_LOOK) realActionThisTick = true;
        return true;
    }

    /** Setzt die Tick-Zaehlung zurueck — einmal pro Tick, am Anfang. */
    public void onTick() {
        queuedThisTick = 0;
        realActionThisTick = false;
    }

    /** Free-Look-Tail darf diesen Tick nur laufen, wenn das {@code false} ist. */
    public boolean hasRealActionThisTick() {
        return realActionThisTick;
    }
}
