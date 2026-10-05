package com.provipvp.perf;

import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Entscheidet, <b>wann</b> ein teurer Blockvolumen-Sweep laufen darf - und was der Aufrufer in der
 * Zwischenzeit als Antwort benutzt.
 *
 * <p><b>Warum es das ueberhaupt braucht.</b> Die Kampfmodule fragen die Welt nicht mit einzelnen
 * Blockabfragen ab, sondern mit geschlossenen Wuerfel-Sweeps - und zwar pro Tick:
 * <ul>
 *   <li>{@code countNearbyBlocks(feet, 5)} laeuft <b>zweimal</b> je Tick. Radius 5 heisst
 *       11x11x11 = 1331 Zellen, also 2662 {@code getBlockState}-Aufrufe pro Tick bzw. rund
 *       53 000 pro Sekunde bei 20 TPS.</li>
 *   <li>{@code maintainNearbyAnchors} sweeped 9x7x9 = 567 Zellen je Tick.</li>
 *   <li>{@code countDamagingAnchorExplosions} sweeped 9x5x9 = 405 Zellen je Tick.</li>
 * </ul>
 * Zusammen sind das ueber 3500 Blockabfragen <i>pro Tick</i>, obwohl sich das Ergebnis innerhalb
 * weniger Ticks praktisch nicht aendert: ein Anchor braucht 1200 Ticks bis zum naechsten
 * Ladestand, ein Bett flyst gar nicht. Das ist die teuerste Staelle im Modul und die, an der man
 * ohne Funktionsverlust zuerst drehen kann.
 *
 * <p><b>Zwei Entscheidungen, nicht eine.</b> {@link #allows} sagt, ob heute ueberhaupt gescannt
 * werden darf ({@link ScanKind#PER_TICK} ausgenommen, das ist per Definition billig). Und weil ein
 * verweigerter Scan eine leere Antwort liefern wuerde - "kein Anchor in der Naehe" waere eine
 * <i>falsche</i> Antwort, mit der der Bot seinen Schild gar nicht mehr hebt - liefert
 * {@link #cached} das Ergebnis des letzten erlaubten Laufs weiter. Ein uebersprungener Scan
 * kostet also Reaktionszeit von wenigen Ticks, nicht Korrektheit.
 *
 * <p><b>Wann ein Ergebnis stirbt.</b> Drei Faelle, alle drei ueber die oeffentliche API erreichbar
 * und keiner davon im Tick-Automatismus versteckt:
 * <ul>
 *   <li>Der Spieler wechselt den Chunk oder die Welt - dann bedeutet dieselbe Blockkoordinate etwas
 *       voellig anderes. Erkennt {@link #markContext} ueber {@link ChunkHint}.</li>
 *   <li>Ein Blockupdate kommt in der Naehe an. Der Aufrufer kennt die Scan-Mitte und weiss
 *       deshalb, welche Kinds es trifft: {@link #invalidate(ScanKind)}.</li>
 *   <li>Der Server zaehlt neu (Disconnect, neuer Server): dann ist die Zeitachse hin, was
 *       {@link #allows} beim Rueckwaertsgehen des Tickzaehlers mit erledigt.</li>
 * </ul>
 *
 * <p>Nach jeder Invalidierung gilt dieselbe Regel: <b>die Art, deren Ergebnis verworfen wurde,
 * darf sofort wieder laufen.</b> Wer nichts mehr im Cache hat, darf nicht noch drei Ticks auf
 * eine Erlaubnis warten - sonst haette der Aufrufer in diesem Fenster gar keine Antwort.
 *
 * <p>Die Zaehler in {@link Stats} sind kumulativ und ueberleben jede Invalidierung: sie sind der
 * Beleg, dass die Optimierung gewirkt hat, nicht etwa nur, dass sie nichts kaputt gemacht hat.
 *
 * <p>Rein funktional ueber {@code (ScanKind, Tick, ChunkHint)} - kein {@code mc.*}, keine Settings
 * (R1). Der Integrations-Agent reicht {@code tickCounter} sowie Chunk und Welt des Spielers hier
 * herein.
 */
public final class ScanBudget {

    /** Sentinel fuer "diese Art wurde seit dem letzten Reset noch nie ausgefuehrt". Halbiertes
     *  MIN_VALUE, damit die Subtraktion {@code currentTick - lastRun} schon beim allerersten
     *  Aufruf weit ueber jedem moeglichen Intervall liegt, statt zu ueberlaufen. */
    private static final int NEVER = Integer.MIN_VALUE / 2;

    /**
     * Die gebudgeteten Scan-Arten, jeweils mit dem Intervall in Ticks, das im Normalbetrieb gilt.
     *
     * <p><b>Warum diese Zahlen:</b> sie folgen aus Volumen und Reaktionszeit, nicht aus Gefuehl.
     * Ein Intervall {@code n} senkt die Kosten eines Sweeps auf {@code 1/n} und kostet genau so
     * viele Ticks an Verzoegerung. Bei {@link #ANCHOR_EXPLOSION} ist das Fenster eng, weil das
     * Ergebnis eine Verteidigung ausloest und die i-Frames nach einem Treffer 10 Ticks (0.5 s)
     * halten - 2 Ticks Leseverzoegerung bleiben darin uneingeholt, und der Sweep faellt von 405
     * auf rund 203 Zellen je Tick. Die beiden anderen Kinds antworten nur auf "ist da etwas
     * <i>neues</i> in der Naehe", und 4 Ticks (200 ms) sind gegenueber einem 1200-Tick-Ladezyklus
     * bzw. einem fliegenden Bett nicht unterscheidbar. {@link #NEARBY_BLOCK_COUNT} ist mit
     * 2662 Zellen je Tick der teuerste Scan im Modul und traegt die groesste Drosselung.
     */
    public enum ScanKind {
        /** Billig genug fuer jeden Tick: nie drosseln, egal was eingestellt ist. */
        PER_TICK(0),
        /** {@code countNearbyBlocks(feet, 5)} - Anker- und Bett-Delta, 2x 1331 Zellen je Tick. */
        NEARBY_BLOCK_COUNT(4),
        /** {@code maintainNearbyAnchors} - Ladestand der Anker im Umfeld, 567 Zellen je Tick. */
        ANCHOR_MAINTENANCE(4),
        /** {@code countDamagingAnchorExplosions} - 405 Zellen je Tick, steuert die Verteidigung. */
        ANCHOR_EXPLOSION(2);

        private final int defaultIntervalTicks;

        ScanKind(int defaultIntervalTicks) {
            this.defaultIntervalTicks = defaultIntervalTicks;
        }

        /**
         * @return das mitgelieferte Intervall in Ticks, {@code 0} fuer {@link #PER_TICK} - dort
         *         gibt es kein Fenster, das Intervall waere toter Code. */
        public int defaultIntervalTicks() {
            return defaultIntervalTicks;
        }
    }

    /**
     * Ortsbezug eines gecachten Ergebnisses: Chunk des Spielers plus Welt-Identitaet.
     *
     * <p><b>Warum ueberhaupt:</b> nach einem Chunkwechsel gilt dasselbe Terrain nicht mehr, und
     * nach einem Dimensionswechsel schon gar nicht (Bett-Explosionen sind im Nether ueberall, in
     * der Overworld nirgends). Ohne die Welt in der Gueltigkeitspruefung wuerde ein Ergebnis aus
     * dem Nether in der Overworld weiterleben und eine Antwort liefern, die es so nicht gibt.
     *
     * <p>Die Welt ist absichtlich {@link Object}: sie kommt vom Aufrufer als das, was er hat -
     * eine Dimension, ein Level-Objekt, ein Identifier. Diese Klasse darf an der Identitaet nichts
     * voraussetzen, sonst waere sie nicht mehr kopflos testbar.
     */
    public record ChunkHint(int chunkX, int chunkZ, Object worldIdentity) {
    }

    /**
     * Kumulierte Zaehler fuer das Debug-Overlay: wie oft durfte ein Scan laufen, wie oft wurde er
     * geschluckt. {@code skipped} ist die Zahl der eingesparten Blockvolumen-Sweeps - je Art und
     * ueber alle Arten hinweg die Masse, um die es hier geht.
     */
    public record Stats(long allowed, long skipped) {
    }

    /** Ergebnis plus der Ortsbezug, unter dem es gemessen wurde. */
    private record Entry(Object value, ChunkHint hint) {
    }

    private final int[] intervals = new int[ScanKind.values().length];
    private final int[] lastRun = new int[ScanKind.values().length];
    private final long[] allowed = new long[ScanKind.values().length];
    private final long[] skipped = new long[ScanKind.values().length];
    /** Zugriffsgeordnet: {@code get} rueckt einen Eintrag nach hinten, der aelteste fliegt zuerst. */
    private final LinkedHashMap<ScanKind, Entry> results;
    private final int maxEntries;
    private ChunkHint context;
    private int highestTick = NEVER;

    /** Budget mit den mitgelieferten Intervallen und einer Kapazitaeue von {@link ScanKind#values()}. */
    public ScanBudget() {
        this(Map.of());
    }

    /**
     * Budget mit eigenen Intervallen pro Art; nicht genannte Arten behalten ihre Defaults.
     *
     * @param intervals Intervall in Ticks je Art, {@code PER_TICK} wird ignoriert
     * @throws NullPointerException wenn {@code intervals} oder eine Zuordnung {@code null} enthaelt -
     *         ein stillschweigend verschlucktes Intervall waere genau die Art Fehler, die sich
     *         spaeter als "der Bot laeuft einfach langsamer" zeigt und nicht mehr zuzuordnen ist
     */
    public ScanBudget(Map<ScanKind, Integer> intervals) {
        this(intervals, ScanKind.values().length);
    }

    /**
     * @param maxEntries so viele Ergebnisse duerfen gleichzeitig gehalten werden. Der Wert ist
     *        bewusst so hoch wie die Zahl der Kinds, weil der Schluessel ein geschlossenes Enum ist
     *        und die Map gar nicht mehr wachsen kann. Ein kleinerer Wert macht die LRU-Politik
     *        sichtbar - und ist getestet, damit sie nicht verrottet, falls es spaeter doch
     *        dynamische Schluessel geben sollte.
     */
    public ScanBudget(Map<ScanKind, Integer> intervals, int maxEntries) {
        for (ScanKind kind : ScanKind.values()) {
            this.intervals[kind.ordinal()] = kind.defaultIntervalTicks();
        }
        intervals.forEach(this::setIntervalTicks);
        this.maxEntries = maxEntries;
        this.results = new LinkedHashMap<>(16, 0.75f, true);
        Arrays.fill(this.lastRun, NEVER);
    }

    /**
     * Die eine Entscheidungsstelle: darf diese Art in diesem Tick scannen?
     *
     * <p>Erlaubt wird, wenn das Fenster der Art abgelaufen ist, beim allerersten Aufruf und nach
     * jeder Invalidierung. {@link ScanKind#PER_TICK} ist nie gedrosselt.
     *
     * <p>Ein Tickzaehler, der <b>zurueckspringt</b>, gilt als neuer Server: Zeitachse und
     * gespeicherte Ergebnisse sind dann beide hin, also wird alles verworfen und der Aufruf
     * erlaubt. Sonst wuerde ein Disconnect mitten im Kampf die naechsten Ticks blockieren, weil der
     * Zaehler von 40 000 auf 0 zurueckspringt und kein Intervall von 40 000 Ticks je erreicht wird.
     */
    public boolean allows(ScanKind kind, int currentTick) {
        int idx = kind.ordinal();
        if (kind == ScanKind.PER_TICK) {
            allowed[idx]++;
            return true;
        }
        if (currentTick < highestTick) reset();
        if (currentTick > highestTick) highestTick = currentTick;

        if ((long) currentTick - lastRun[idx] >= intervals[idx]) {
            lastRun[idx] = currentTick;
            allowed[idx]++;
            return true;
        }
        skipped[idx]++;
        return false;
    }

    /**
     * Das Ergebnis des letzten erlaubten Laufs, solange es noch gueltig ist.
     *
     * <p>Der Ortsbezug wird gegen {@link #markContext} geprueft: ein Ergebnis aus einem anderen
     * Chunk oder einer anderen Welt wird auch dann nicht ausgegeben, wenn es noch im Map liegt -
     * lieber eine ehrliche Luecke als eine erfundene Antwort.
     *
     * @param <T> Boolean, Integer, BlockPosition oder was der Aufrufer sonst zurueckbekommt
     * @return {@link Optional#empty()}, wenn nie gescannt wurde, verworfen wurde oder der
     *         Aufrufer {@code null} gespeichert hat
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> cached(ScanKind kind) {
        Entry entry = results.get(kind);
        if (entry == null) return Optional.empty();
        if (!Objects.equals(entry.hint(), context)) {
            // Den Eintrag auch bei nur raumlich unbrauchbarem Treffer loeschen: ihn liegen zu
            // lassen hiesse, dass jeder folgende Aufruf ihn erneut auswertet.
            results.remove(kind);
            return Optional.empty();
        }
        return Optional.ofNullable((T) entry.value);
    }
    /**
     * Legt das Ergebnis eines erlaubten Laufs ab. Wird {@code null} gespeichert, gilt das als
     * "kein Ergebnis": {@link Boolean#FALSE} und {@code null} sind verschiedene Antworten, und ein
     * {@code null} im Cache wuerde beim naechsten {@link #cached} wie ein Treffer aussehen.
     *
     * <p>Ohne bekannten {@linkplain #markContext Ortsbezug} wird nichts abgelegt. Ein Ergebnis ohne
     * Ortsbezug koennte spaeter nicht mehr geprueft werden und wuerde damit jede Chunk- und
     * Weltinvalidierung stillschweigend umgehen - lieber nichts im Cache als eine Antwort, deren
     * Gueltigkeit niemand mehr beweisen kann.
     */
    public void store(ScanKind kind, Object result) {
        if (result == null || context == null) {
            results.remove(kind);
            return;
        }
        results.put(kind, new Entry(result, context));
        while (results.size() > maxEntries) {
            Iterator<Entry> eldest = results.values().iterator();
            eldest.next();
            eldest.remove();
        }
    }

    /**
     * Setzt den aktuellen Ortsbezug des Spielers. Ein Wechsel von Chunk <b>oder</b> Welt verwirft
     * alle Ergebnisse und gibt allen Arten sofort wieder ihr Budget - siehe Klassenkommentar.
     *
     * <p>Auch {@code null} ist ein gueltiger Ortsbezug und bedeutet "gerade keine Welt geladen".
     * Das ist kein Sonderfall ohne Folgen: ein Ergebnis aus der gerade verlassenen Welt ist nicht
     * besser, sondern genauso hin wie bei jedem anderen Wechsel.
     */
    public void markContext(ChunkHint hint) {
        if (Objects.equals(context, hint)) return;
        context = hint;
        invalidateAll();
    }

    /**
     * Verwirft <b>jedes</b> Ergebnis und gibt allen Arten sofort wieder ihr volles Budget.
     * Die Zaehler in {@link #stats} bleiben unangetastet - sie sollen die Sitzung zusammenfassen,
     * nicht den letzten Blockupdate.
     */
    public void invalidateAll() {
        results.clear();
        Arrays.fill(lastRun, NEVER);
    }

    /**
     * Verwirft das Ergebnis genau dieser Art - ein Blockupdate in der Naehe, oder der Aufrufer
     * weiss aus anderem Grund, dass nur diese eine Zahl nicht mehr gilt. Andere Arten und ihre
     * Fristen bleiben unberuehrt, sonst wuerde ein einzelner Blockupdate im Umfeld den ganzen
     * Cache wegwerfen und genau die Drosselung aufheben, die hier gebaut wird.
     */
    public void invalidate(ScanKind kind) {
        results.remove(kind);
        lastRun[kind.ordinal()] = NEVER;
    }

    /** @return die kumulierten Zaehler dieser Art, unveraendert durch {@link #invalidateAll} */
    public Stats stats(ScanKind kind) {
        int idx = kind.ordinal();
        return new Stats(allowed[idx], skipped[idx]);
    }

    /** @return das aktuell geltende Intervall in Ticks, {@code 0} fuer {@link ScanKind#PER_TICK} */
    public int intervalTicks(ScanKind kind) {
        return kind == ScanKind.PER_TICK ? 0 : intervals[kind.ordinal()];
    }

    /**
     * Setzt das Intervall einer Art nachtraeglich. Werte unter 1 werden auf 1 gehoben, weil ein
     * Intervall von 0 oder negativ nichts drosseln wuerde - es waere genau das Verhalten, das man
     * durch die Drosselung eigentlich abschaffen wollte.
     *
     * <p>Der Aufrufer darf das zur Laufzeit tun: eine Einstellung, die den Sweep verlangsamt,
     * soll ohne Neustart greifen.
     */
    public void setIntervalTicks(ScanKind kind, int ticks) {
        if (kind == ScanKind.PER_TICK) return;
        intervals[kind.ordinal()] = Math.max(1, ticks);
    }

    /** Neuer Server: die Zeitachse ist hin, damit auch alles, was auf ihr gemessen wurde. */
    private void reset() {
        results.clear();
        Arrays.fill(lastRun, NEVER);
        highestTick = NEVER;
    }
}
