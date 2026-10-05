package com.provipvp.crystal;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Die Buchhaltung, welcher End Crystal von DIESEM Client gesetzt wurde.
 *
 * <p>{@link CrystalScorer} filtert bereits ueber {@code ownedEntityIds} und trifft damit genau die
 * Entscheidung, um die es hier geht: einen Crystal des Gegners zu zerstoeren gibt ihm den Pop, also
 * darf der Bot nur eigene zunden. Diese Klasse liefert die Entscheidung nicht selbst — sie haelt nur
 * die Menge der eigenen Entity-IDs bereit und uebergibt sie per {@link #snapshot()} als
 * {@code Set<Integer>} an die {@code ScoreConfig}. Der Scorer macht daraus eine Entscheidung; die
 * Verwaltung der Zugehoerigkeit macht diese Klasse.
 *
 * <p><b>Warum das kein {@code Set} im Modul ist:</b> eine Entity-ID wird zuverlaessig beim
 * <i>Setzen</i> bekannt, aber nicht zuverlaessig beim Verschwinden. Ein Crystal, der gesprengt wird,
 * verschwindet in demselben Tick, in dem ein fremdes Paket ihn trifft; der Bot sieht es nur, wenn er
 * an der richtigen Stelle in der Entity-Liste steht — beim Chunk-Unload, im Todesablauf oder wenn
 * ein anderer Bot-Suffix schneller war, faellt es aus. Ein reines {@code HashSet} haelt solche
 * Eintraege fuer immer. Nach einer langen Session ist die "eigene" Menge dann groesser als die Menge
 * aller Crystals im Level und der Filter laeuft leer: der Bot zundet gar nichts mehr, ohne dass man
 * sieht, woran es liegt.
 *
 * <p><b>Deshalb zwei Schranken statt einer:</b> ein <i>Alter</i> (Standard {@value #DEFAULT_MAX_AGE_TICKS}
 * Ticks, also 10 s — laenger als das serverseitig bestaetigte Fenster, in dem ein Crystal ueberhaupt
 * interessant ist) und eine <i>harte Obergrenze</i> ({@value #DEFAULT_MAX_ENTRIES} Eintraege). Die
 * Obergrenze ist das Sicherheitsnetz fuer den Fall, dass jemand {@link #advance(int)} nie aufruft
 * (pausierte Welt, Tests): die Altersgrenze greift dann nicht, die Kapazitaetsgrenze schon.
 *
 * <p><b>Warum die Reihenfolge explizit ist:</b> Die Verdrängung bei Ueberlauf nimmt den aelteren
 * Eintrag, und die {@link #snapshot()} liefert von alt nach neu. Beides folgt aus der
 * {@link LinkedHashMap}-Reihenfolge, nicht aus der Hash-Ordnung — sonst waere die Frage "welcher
 * Crystal ist als naechster rausgeflogen" nicht beantwortbar, und genau diese Frage muss ein Test
 * koennen. Die Reihenfolge ist stabil, weil der Tickzaehler nur waechst und ein erneutes
 * {@link #notePlaced(int)} den Zeitstempel eines vorhandenen Eintrags nicht anfasst.
 *
 * <p>Kein {@code mc.*}, keine Settings (R1): der Tickzaehler kommt per {@link #advance(int)} herein.
 * Der Integrations-Agent ruft ihn einmal pro {@code TickEvent.Pre} und {@link #reset()} bei einem
 * Dimensionswechsel.
 */
public final class CrystalOwnership {

    /** Standard-Altersgrenze: 200 Ticks = 10 s. */
    public static final int DEFAULT_MAX_AGE_TICKS = 200;

    /**
     * Standard-Obergrenze der Eintraege. 32 liegt weit ueber der Zahl der Crystals, die im
     * Altersfenster von {@value #DEFAULT_MAX_AGE_TICKS} Ticks ueberhaupt entstehen koennen — die
     * Kapazitaetsgrenze soll also nie vor der Altersgrenze greifen, sondern nur verhindern, dass
     * eine nicht fortgeschriebene Instanz unbegrenzt waechst.
     */
    public static final int DEFAULT_MAX_ENTRIES = 32;

    /** Entity-ID -> Tick, an dem sie notiert wurde. Einfuegereihenfolge = Altersreihenfolge. */
    private final LinkedHashMap<Integer, Integer> placedAtTick = new LinkedHashMap<>();

    /** Harte Obergrenze; bei Ueberlauf wird der aelteste Eintrag verdraengt. */
    private final int maxEntries;

    /** Altersgrenze in Ticks, vom Modul aus dem Setting gesetzt. */
    private int maxAgeTicks;

    /** Seit dem letzten {@link #reset()} vergangene Ticks — der Alterstakt aller Eintraege. */
    private int tick;

    /** Standardgrenzen: {@value #DEFAULT_MAX_ENTRIES} Eintraege, {@value #DEFAULT_MAX_AGE_TICKS} Ticks. */
    public CrystalOwnership() {
        this(DEFAULT_MAX_ENTRIES, DEFAULT_MAX_AGE_TICKS);
    }

    /**
     * @param maxEntries   harte Obergrenze der Eintraege, mindestens 1
     * @param maxAgeTicks  Altersgrenze in Ticks, 0 heisst "im nächsten vorgerueckten Tick faellig"
     * @throws IllegalArgumentException wenn eine der Grenzen unbrauchbar ist — dann waere die
     *         Schranke still wirkungslos, statt sichtbar zu scheitern
     */
    public CrystalOwnership(int maxEntries, int maxAgeTicks) {
        if (maxEntries < 1) {
            throw new IllegalArgumentException("maxEntries muss mindestens 1 sein, nicht " + maxEntries);
        }
        if (maxAgeTicks < 0) {
            throw new IllegalArgumentException("maxAgeTicks darf nicht negativ sein: " + maxAgeTicks);
        }
        this.maxEntries = maxEntries;
        this.maxAgeTicks = maxAgeTicks;
    }

    /**
     * Die gesetzte Altersgrenze — damit das Modul sie in Setting-Beschreibungen nennen kann, ohne
     * die Konstante hier zu duplizieren.
     */
    public int maxAgeTicks() {
        return maxAgeTicks;
    }

    /**
     * Setzt die Altersgrenze neu (aus dem Setting). Wirkt sofort: Eintraege, die die neue Grenze
     * bereits ueberschritten haben, fallen beim naechsten {@link #advance(int)} weg.
     */
    public void setMaxAgeTicks(int maxAgeTicks) {
        if (maxAgeTicks < 0) {
            throw new IllegalArgumentException("maxAgeTicks darf nicht negativ sein: " + maxAgeTicks);
        }
        this.maxAgeTicks = maxAgeTicks;
    }

    /**
     * Vermerkt einen selbst gesetzten Crystal.
     *
     * <p><b>Ein zweites Mal dieselbe ID aendert nichts:</b> das ist kein zweiter Crystal, sondern
     * derselbe, der zum zweiten Mal als gesetzt gemeldet wurde (Paket-Echo, zweiter Aufruf derselben
     * Aufrufstelle). Ein Zeitstempel-Reset wuerde den Eintrag unsterblich machen — genau das Leak,
     * das diese Klasse verhindern soll. Aelterung wird darum bewusst <i>nicht</i> aufgefrischt.
     *
     * <p>Uebersteigt die Zahl der Eintraege {@code maxEntries}, wird der aelteste verdraengt.
     */
    public void notePlaced(int entityId) {
        placedAtTick.putIfAbsent(entityId, tick);

        // Der naechste Treffer der LinkedHashMap ist der aelterste Eintrag, weil der Tickzaehler
        // monoton waechst — kein Vergleich der Zeitstempel noetig, und das Ergebnis ist exakt das,
        // was ein Test vorherzusagen hat.
        while (placedAtTick.size() > maxEntries) {
            placedAtTick.remove(placedAtTick.keySet().iterator().next());
        }
    }

    /**
     * Vergisst einen Crystal: zerstoert, weggepusht oder einfach nicht mehr gesehen. Danach gilt er
     * nicht mehr als eigener Crystal.
     */
    public void forget(int entityId) {
        placedAtTick.remove(entityId);
    }

    /**
     * Gehoert dieser Crystal uns? Der Test kostet eine Hashsuche; das Alter wird hier
     * <i>absichtlich</i> nicht geprueft — abgelaufen heisst in einer laufenden Session ohnehin
     * "in diesem Tick nicht mehr gesehen", und die Auswertung gehoert an eine Stelle
     * ({@link #advance(int)}) statt in jede Abfrage.
     */
    public boolean owns(int entityId) {
        return placedAtTick.containsKey(entityId);
    }

    /**
     * Unabhaengige Kopie der eigenen Entity-IDs, von alt nach neu sortiert.
     *
     * <p>Eine Kopie und nicht die lebende Map: der Aufrufer iteriert die Menge waehrend er
     * Crystal-Kandidaten sammelt, und wuerde dabei sonst Eintraege entfernen, die er noch sehen
     * muss. {@code Collections.unmodifiableSet} statt {@code Set.copyOf}, weil die
     * Altersreihenfolge hier eine Zusage ist, {@code Set.copyOf} sie nicht haelt.
     */
    public Set<Integer> snapshot() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(placedAtTick.keySet()));
    }

    /**
     * Laesst die Zeit um {@code elapsedTicks} Ticks weiterlaufen und wirft alles weg, was zu alt ist.
     *
     * <p>Der Aufrufer uebergibt die <b>seit dem letzten Aufruf vergangenen</b> Ticks, nicht einen
     * absoluten Welt-Tick: damit funktioniert der Aufruf genauso, wenn das Modul einen eigenen
     * Zaehler hat wie ueber {@code 1} pro {@code TickEvent.Pre}. Nach genau
     * {@code maxAgeTicks} Ticks lebt ein Eintrag noch — "aelter als die Grenze" ist die Bedingung,
     * nicht "so alt wie die Grenze"; sonst faellt ein Crystal, der im Moment des Setzens die volle
     * Altersspanne noch zur Verfuegung hatte, schon einen Tick zu frue weg.
     *
     * @throws IllegalArgumentException bei negativen Ticks — ein zuruecklaufender Takt wuerde Eintraege
     *         wieder verjuengen und die Schranke aufheben
     */
    public void advance(int elapsedTicks) {
        if (elapsedTicks < 0) {
            throw new IllegalArgumentException(
                "advance erwartet vergangene Ticks, nicht " + elapsedTicks);
        }
        tick += elapsedTicks;

        for (Iterator<Map.Entry<Integer, Integer>> it = placedAtTick.entrySet().iterator(); it.hasNext();) {
            Map.Entry<Integer, Integer> entry = it.next();
            if (tick - entry.getValue() > maxAgeTicks) it.remove();
        }
    }

    /**
     * Leert alles — der Aufrufer bei einem Dimensionswechsel. Der Tickzaehler faengt wieder bei
     * null an: die alten Zeiten sind mit den Eintraegen weg, und ein stehengebliebener Zaehler wuerde
     * die ersten Eintraege der neuen Welt sofort als uralt einstufen.
     */
    public void reset() {
        placedAtTick.clear();
        tick = 0;
    }
}