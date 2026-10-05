package com.provipvp.mechanics;

/**
 * Slow-Falling-Pfeile aus der piercing-/Multishot-Crossbow sind die auf der Wiki empfohlene Gegenmassnahme
 * gegen Crystal-PvP: der Getroffene kann nicht mehr critten und nicht mehr mit dem Mace smashen — und
 * haengt viel laenger in der Luft, also trifft ihn derselbe Pop-Plan ein zweites und drittes Mal.
 *
 * <p>Warum ueberhaupt ein Modell statt eines {@code if (target.hasEffect(SLOW_FALLING))}: das Addon hat an
 * drei unabhaengigen Stellen dieselbe Frage gestellt — darf ich den Crit-Jump planen, darf ich die Mace
 * ziehen, wann ist der naechste Pop dran — und an jeder Stelle eine eigene, leicht abweichende Antwort.
 * Hier steht die Antwort einmal, in Ticks und Bloecken, damit der Bot aus einer Zahl statt aus einer
 * {@code EffectInstance} heraus entscheidet.
 *
 * <p>Reine Logik, kein {@code mc.*}: der einzige Weltbezug ist die Fallhoehe, die der Aufrufer abliest.
 */
public final class SlowFallingArrow {

    private SlowFallingArrow() {}

    /**
     * Endgeschwindigkeit unter Slow Falling in Bloecke pro Tick. Der Effekt begrenzt den Fall auf
     * ein Zehntel der normalen Endgeschwindigkeit — genau das ist der Grund fuer alles Weitere.
     */
    public static final double SLOW_FALLING_TERMINAL_SPEED = 0.125;

    /** Endgeschwindigkeit eines normalen Falls in Bloecke pro Tick (Vanilla, inkl. Luftwiderstand). */
    public static final double NORMAL_TERMINAL_SPEED = 3.92;

    /** Mindest-Fallhoehe fuer den Mace-Smash-Bonus. Unter Slow Falling ist er nicht erreichbar. */
    public static final double SMASH_FALL_BLOCKS = 1.5;

    /** i-Frames nach jedem Schaden — bestimmt, wie viele Pop-Zyklen in das Fenster passen. */
    public static final int I_FRAME_TICKS = 10;

    /**
     * Der Zustand aller Angriffsplaene, die der Bot gegen dieses Ziel hat.
     *
     * @param critAvailable        Darf der Bot den Crit-Timed-Jump ueberhaupt einplanen?
     * @param critDelayTicks       Wann fruehestens der Crit-Plan wieder greift (Restlaufzeit des Effekts)
     * @param smashAvailable       Ist der Mace ueberhaupt eine Option?
     * @param smashFallBlocks      Erforderliche Fallhoehe fuer den Smash — unter Slow Falling unendlich
     * @param popWindowTicks       Wie lange das Ziel vorhersagbar in der Luft haengt
     * @param maxRepeatedPops      Wie viele Pop-Zyklen in dieses Fenster passen (popWindow / i-Frames)
     */
    public record Plan(
        boolean critAvailable,
        int critDelayTicks,
        boolean smashAvailable,
        double smashFallBlocks,
        int popWindowTicks,
        int maxRepeatedPops) {}

    /**
     * Bewertet die Angriffsplaene gegen ein Ziel, das gerade von einem Slow-Falling-Pfeil getroffen wurde.
     *
     * @param fallDistance             Fallhoehe des Ziels in Bloecken (0 = am Boden)
     * @param slowFallingTicksRest     Restlaufzeit des Slow-Falling-Effekts in Ticks; 0 = kein Effekt
     */
    public static Plan evaluate(double fallDistance, int slowFallingTicksRest) {

        boolean slowed = slowFallingTicksRest > 0;

        // Kritischer Treffer verlangt echten Fall. Ein Opfer, das mit 0.125 b/t sinkt, ist praktisch nie
        // schnell genug unten, um dem Bot ein Crit-Fenster zu geben — also faellt der Crit-Plan flach.
        // Ohne den Effekt gilt der normale Fall, dort ist das Fenster normal offen.
        boolean critAvailable = !slowed;
        int critDelayTicks = slowed ? slowFallingTicksRest : 0;

        // Der Smash-Bonus ist ein reiner Fallhoehen-Bonus; Slow Falling verhindert ihn vollstaendig.
        // Unendlich als Schwellwert heisst: "mit dieser Waffe geht es gar nicht" — statt einem Flag, das
        // der Aufrufer beim Vergleich von double-Werten doch noch uebersehen kann.
        boolean smashAvailable = !slowed;
        double smashFallBlocks = slowed ? Double.POSITIVE_INFINITY : SMASH_FALL_BLOCKS;

        // Das ist die eigentliche Belohnung fuer den Pfeil: das Ziel haengt ~31x laenger in der Luft,
        // also passt vielfach mehr als ein Pop in dasselbe Fenster.
        double terminal = slowed ? SLOW_FALLING_TERMINAL_SPEED : NORMAL_TERMINAL_SPEED;
        int popWindowTicks = fallDistance <= 0 ? 0 : (int) Math.ceil(fallDistance / terminal);
        int maxRepeatedPops = popWindowTicks / I_FRAME_TICKS;

        return new Plan(critAvailable, critDelayTicks, smashAvailable, smashFallBlocks,
            popWindowTicks, maxRepeatedPops);
    }

    /**
     * Ist es ueberhaupt sinnvoll, auf den Fall zu warten? Ohne Fallhoehe gibt es weder Crit noch Smash,
     * unabhaengig vom Effekt — das trennt "Effekt blockt meinen Plan" von "es gibt gar keinen Fall".
     */
    public static boolean hasFallToExploit(double fallDistance) {
        return fallDistance >= SMASH_FALL_BLOCKS;
    }
}