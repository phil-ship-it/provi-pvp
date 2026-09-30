package com.provipvp.pearl;

/** Szenario-Kennung fuer Logging und Tests: welcher taktische Fall einen Perlwurf ausgeloest hat.
 *
 *  <p>Die Aufzaehlung ist bewusst rein beschreibend - {@link PearlSolver} enthaelt pro Wert genau
 *  eine Wurfentscheidung, und die Ausfuehrungsschicht (Werfen, Timing, Slotwechsel) liest dieses
 *  Enum ausschliesslich, um Meldungen unterscheidbar zu machen. */
public enum PearlScenario {
    /** Szenario 1: das Ziel entkommt, der Wurf wird praediziert auf die Fluchtlinie geworfen. */
    GAP_CLOSER,

    /** Szenario 2: die direkte Sichtlinie ist blockiert, der Wurf geht als Bogen ueber das Hindernis. */
    TERRAIN_BYPASS,

    /** Szenario 3: Anti-Fall-Damage nach einer Explosion, der Wurf geht nach unten. */
    ANTI_FALL
}
