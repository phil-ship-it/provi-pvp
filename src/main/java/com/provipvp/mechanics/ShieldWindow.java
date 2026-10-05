package com.provipvp.mechanics;

/**
 * Rechnet aus, wie lange ein Schild wirklich haelt.
 *
 * <p>Der Punkt ist die Verzoegerung: Vanilla blockt mit einem Schild erst ab dem <b>6.</b> Tick des
 * Item-Use (5 Ticks = 250 ms). Ein Fenster von {@code tickCounter + 15} haelt den Schild also 15
 * Ticks in der Hand, aber nur 10 davon sind Schutz — und in den ersten 5 kann der Bot nicht
 * gleichzeitig angreifen, weil die Hand am Schild ist. Das ist der Grund, warum beide Kampfmodule
 * ihre Block-Fenster ueber diese Klasse beziehen statt ueber eine eigene Konstante: der Fehler war
 * genau zweimal vorhanden, einmal je Profil, weil er an zwei Stellen stand.
 *
 * <p>Reine Berechnung ohne {@code mc.*} und ohne Settings — beide Module haben dieselbe Physik, nur
 * unterschiedliche Fensterlaengen.
 */
public final class ShieldWindow {

    private ShieldWindow() {}

    /**
     * Ticks bis der Schild ueberhaupt blockt. Vanilla: 5 Ticks (250 ms). Wer das nicht abwartet,
     * haelt ein "Schild" in der Hand, das genau nichts abwehrt.
     */
    public static final int ACTIVATION_TICKS = 5;

    /**
     * Kuerzestes sinnvolles Block-Fenster: die 5 Aktivierungs-Ticks plus 5 Ticks echter Schutz.
     * Alles darunter ist ein Fenster, in dem der Bot haelt, nichts abbekommt und nichts nachlegt.
     */
    public static final int MIN_USEFUL_TICKS = ACTIVATION_TICKS + 5;

    /** Notfall-Fenster bei kritischem HP und nahem Gegner: 20 Ticks gehalten, 15 davon Schutz. */
    public static final int EMERGENCY_HELD_TICKS = 20;

    /** Fenster bei frisch gesetztem Crystal/Anchor/Bett: kurz und hart, 15 gehalten, 10 Schutz. */
    public static final int FRESH_EXPLOSION_HELD_TICKS = 15;

    /**
     * Bis wann der Schild gehalten werden soll, absolut in Ticks seit Session-Start.
     *
     * @param now        aktueller {@code tickCounter}
     * @param heldTicks  gewuenschte Haltezeit <b>ohne</b> die Aktivierungsverzoegerung
     * @return           {@code now + Haltezeit}, nie kuerzer als {@link #MIN_USEFUL_TICKS}
     */
    public static int until(int now, int heldTicks) {
        return now + Math.max(MIN_USEFUL_TICKS, heldTicks);
    }

    /** Wie viele der gehaltenen Ticks wirklich Schutz bringen. */
    public static int usefulTicks(int heldTicks) {
        return Math.max(0, heldTicks - ACTIVATION_TICKS);
    }

    /**
     * Ist das Block-Fenster zum-jetzt-Tick schon vorbei? Bewusst {@code <} und nicht {@code <=}:
     * im letzten gehaltenen Tick wird noch blockt, sonst verliert das Fenster genau einen Tick.
     *
     * @param now      aktueller {@code tickCounter}
     * @param until    Ergebnis von {@link #until}
     */
    public static boolean expired(int now, int until) {
        return now >= until;
    }
}