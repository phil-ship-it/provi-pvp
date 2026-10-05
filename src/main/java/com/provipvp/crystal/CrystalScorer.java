package com.provipvp.crystal;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.ToDoubleFunction;

/** Wertet ALLE in Reichweite liegenden Crystals aus und liefert den besten (D6).
 *
 *  <p>Der bisherige Ablauf bricht beim naechsten Kandidaten ab, den er findet, bzw. greift sich den
 *  ersten Entity-Treffer einer Box. Das ist eine Entweder-oder-Entscheidung ohne Blick auf die
 *  Qualitaet: von zehn sichtbaren Crystals wird der naechste genommen, auch wenn er 0.3 Schaden
 *  macht, waehrend zwei Meter weiter hinten einer mit vollem Schaden steht — und der teure Angriffs-
 *  Cooldown ist fuer die 0.3 verbraucht. Der Zweifel an dieser Regel ist berechtigt: die Schadens-
 *  funktion selbst rechnet in beiden Modulen bereits pro Kandidat, sie wurde nur nie zum
 *  Entscheidungskriterium gemacht.
 *
 *  <p>Zusaetzlich zwei Filter, die vorher gar nicht existierten:
 *
 *  <ul>
 *    <li><b>Nur eigene Crystals.</b> Ein Crystal, den der Gegner gesetzt hat, zu zerstoeren ist
 *        entgegen der eigenen Absicht — er gibt dem Gegner den Pop, und der Bot handelt damit an
 *        seiner Stelle. Genau deshalb wird hier ueber die Entity-ID gearbeitet und nicht ueber
 *        Position oder Sichtbarkeit: die Entity-ID ist das einzige, was einen Crystal zweifelsfrei
 *        dem setzenden Spieler zuordnet.</li>
 *    <li><b>Mindestalter ({@code tickCount}).</b> Ein Crystal, den der Bot gerade erst gesetzt hat,
 *        ist noch nicht serverseitig bestaetigt; sein Trägerblock kann die Vorhersage verlassen
 *        haben. Ein Schlag darauf ist eine Wette. Meteors CrystalAura hat dafuer schon ein
 *        {@code ticksExisted}-Setting, das GodmodePvP im Sofort-Modus auf 0 herunterstellt — mit
 *        0 gewinnt wieder die Sorte, die hier vermieden werden soll.</li>
 *  </ul>
 *
 *  <p>Die Schadensfunktion kommt als {@link ToDoubleFunction} herein (R1) — sie braucht das Ziel und
 *  {@code DamageUtils}, beides Dinge, die es nur im laufenden Client gibt. Die Sortierlogik
 *  selbst braucht davon nichts und ist dadurch direkt testbar.
 */
public final class CrystalScorer {
    private CrystalScorer() {}

    /**
     * Ein bewerteter Crystal.
     *
     * @param entityId  {@code entity.getId()} — der Schluessel fuer "von uns selbst gesetzt"
     * @param tickCount {@code entity.tickCount}}, also das Alter in Ticks
     * @param x,y,z    Weltposition, damit die Schadensfunktion daraus ihre Explosionsstelle bauen
     *                 kann, ohne eine zweite Map von Entity-ID auf Position zu unterhalten
     */
    public record Candidate(int entityId, int tickCount, double x, double y, double z) {}

    /**
     * @param entityId  Entity-ID des zu zzuendenden Crystals
     * @param tickCount sein Alter zum Zeitpunkt der Auswahl
     * @param damage    der projizierte Schaden beim Ziel
     */
    public record CrystalChoice(int entityId, int tickCount, double damage) {}

    /**
     * @param damage          projizierter Schaden einer Position auf dem Ziel
     * @param ownedEntityIds  Entity-IDs der von uns selbst gesetzten Crystals. <b>Leer heisst: es
     *                        wurde nichts von uns erkannt, also wird auch nichts gefeuert.</b> Das ist
     *                        bewusst so und nicht als "Filter aus": lieber diesen Tick keinen Crystal
     *                        zuenden, als dem Gegner seinen eigenen zu zerstoeren.
     * @param minTicksExisted Mindestalter in Ticks (Meteors {@code ticksExisted})
     * @param minDamage       Schadensschwelle inklusiv ("mindestens so viel Schaden"). Der Wert 0
     *                        ist damit eine echte Option, aber auch der Standard: unabhaengig davon
     *                        fliegt ein Crystal mit 0 Schaden immer heraus — er erreicht das Ziel
     *                        nicht und verbraucht nur den Angriffs-Cooldown
     */
    public record ScoreConfig(ToDoubleFunction<Candidate> damage, Set<Integer> ownedEntityIds,
                              int minTicksExisted, double minDamage) {
        public ScoreConfig {
            if (damage == null) throw new IllegalArgumentException("damage-Funktion fehlt");
            ownedEntityIds = ownedEntityIds == null ? Set.of() : ownedEntityIds;
            if (minTicksExisted < 0) {
                throw new IllegalArgumentException("minTicksExisted darf nicht negativ sein: " + minTicksExisted);
            }
        }
    }

    /**
     * Bewertet jeden Kandidaten und gibt den besten zurueck.
     *
     * <p>Gleichstand wird bewusst aufgeloest statt die Liste zu durchlaufen und den ersten zu
     * nehmen: zwei exakt gleich starke Crystals kommen im Crystal-PvP ständig vor (derselbe
     * Block, zweimal gesetzt, oder derselbe Schaden an zwei Seiten). Ohne Regel wertet der Bot das
     * als Signal und wechselt von Tick zu Tick — dieselbe Erkenntnis, die in den Modulen schon zur
     * sortierten Kandidatenliste mit Tie-Break gefuehrt hat.
     *
     * @return der beste zulaessige Crystal, oder {@link Optional#empty()} wenn keiner die Filter
     *         passiert (fremd, zu jung, kein Schaden, leere Liste)
     */
    public static Optional<CrystalChoice> pick(List<Candidate> candidates, ScoreConfig config) {
        if (candidates == null || candidates.isEmpty()) return Optional.empty();

        CrystalChoice best = null;
        for (Candidate candidate : candidates) {
            if (!config.ownedEntityIds().contains(candidate.entityId())) continue;
            if (candidate.tickCount() < config.minTicksExisted()) continue;

            // Die erste Bedingung ist absichtlich gegen NaN formuliert: bei NaN ist JEDER Vergleich
            // false, ein "damage <= 0" wuerde NaN also durchwinken und der Kandidat wuerde jede
            // weitere Vergleichsentscheidung still zugunsten des kaputten Werts kippen. Die
            // Schwellenpruefung ist inklusiv ("mindestens minDamage"), 0 Schaden fliegt aber
            // unabhaengig von der Schwelle heraus.
            double damage = config.damage().applyAsDouble(candidate);
            if (!(damage > 0.0) || damage < config.minDamage()) continue;

            if (best == null || beats(candidate, damage, best)) {
                best = new CrystalChoice(candidate.entityId(), candidate.tickCount(), damage);
            }
        }
        return Optional.ofNullable(best);
    }

    /**
     * Reihenfolge bei Gleichstand: aelterer Crystal zuerst — er hat die meiste Zeit gehabt, vom
     * Server bestaetigt zu werden, und ist damit der verlaesslichere. Danach die kleinere
     * Entity-ID, damit die Auswuahl deterministisch ist und nicht von der Reihenfolge der
     * Entity-Liste im Level abhaengt (die aendert sich beim Entladen und Neuladen von Chunks).
     */
    private static boolean beats(Candidate candidate, double damage, CrystalChoice best) {
        if (damage != best.damage()) return damage > best.damage();
        if (candidate.tickCount() != best.tickCount()) return candidate.tickCount() > best.tickCount();
        return candidate.entityId() < best.entityId();
    }
}
