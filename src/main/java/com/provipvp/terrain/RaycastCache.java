package com.provipvp.terrain;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Kurzlebiger Cache fuer Block-Raycasts ({@code Level#clip}).
 *
 *  Warum ueberhaupt: die taktische Ebene ({@code ExplosionScanner}) fragt pro Tick
 *  dieselben Strecken mehrfach ab - Sichtlinie zum Ziel, Sichtlinie zur Explosionsposition, Flugbahn-
 *  abschnitte einer Perle. Jede dieser Abfragen ist ein echter Voxel-Raycast durch die Welt; pro Tick
 *  summieren sich Dutzende davon zu zweistelligen Millisekunden. Die Abfragen sind innerhalb eines
 *  Ticks (und bei statischem Terrain ueber mehrere Ticks) exakt gleich - das ist der einzige Grund,
 *  warum man das Ergebnis zwischenspeichern darf.
 *
 *  Drei Dinge entscheiden ueber die Gueltigkeit eines Eintrags, alle drei sind hier abgesichert:
 *  - <b>TTL</b>: nach {@link #DEFAULT_TTL_TICKS} Ticks faellt der Eintrag weg, auch wenn kein
 *    Blockupdate ankam. Das faengt alles ab, was der Client gar nicht meldet (eigene Platzierungen,
 *    Entity-Bewegung, Chunk-Nachladung).
 *  - <b>Groesse</b>: {@link #MAX_ENTRIES} ist hart; das aelteste Element fliegt zuerst. Ohne diese
 *    Grenze waechst die Map ueber einen langen Kampf unbegrenzt, weil TTL und Blockupdates nur
 *    Eintraege treffen, die jemand auch wirklich angefragt hat.
 *  - <b>Blockupdates</b>: {@link #invalidateCell} wirft jeden Eintrag weg, dessen Segment die
 *    geaenderte Zelle schneidet - nicht nur die, deren Endpunkt darauf liegt. Ein Raycast von einer
 *    Wandseite zur anderen laeuft mitten hindurch und waere sonst stale.
 *
 *  Absichtlich <b>keine</b> Abhaengigkeit von einem Modul-Tickzaehler: der Besitzer ruft pro Tick
 *  {@link #markTick} mit seiner eigenen monotonen Zahl. Damit bleibt der Cache koppelbar an die
 *  Reihenfolge, in der der Aufrufer denkt, statt eine eigene Takt- oder Thread-Infrastruktur
 *  aufzubauen. */
public final class RaycastCache {
    /** Harte Obergrenze der Eintragszahl. 2048 reicht fuer mehrere Ziel-Explosions-Abfragen pro
     *  Tick bei weitem und ist klein genug, dass der Invalidierungs-Durchlauf (O(n) pro
     *  Blockupdate) nie zum Flaschenhals wird. */
    public static final int MAX_ENTRIES = 2048;

    /** Standard-Lebensdauer eines Eintrags in Ticks. Kurz genug, dass nach einem Crystal- oder
     *  Bett-Explosions-Refresh nichts Altes uebrig bleibt, lang genug, dass die mehrfache Abfrage
     *  derselben Strecke innerhalb eines Ticks (und der naechste Tick) einen Treffer landen. */
    public static final int DEFAULT_TTL_TICKS = 5;

    /** Blockquantisierung fuer Schluessel und Invalidierung: 1/16 Block. Genug Aufloesung, damit
     *  eine Sichtlinie aus denselben Augen-/Zielkoordinaten wirklich denselben Schluessel trifft,
     *  aber noch keine Zelle - die Zelle waere zu grob (Blockgrenzen wuerden Standpositionen
     *  systematisch danebenfallen). */
    private static final int QUANT = 16;
    /** Startwert des Tickzaehlers. Halbiertes MIN_VALUE, damit die Subtraktion
     *  {@code currentTick - eingetragenerTick} auch vor dem ersten {@link #markTick} nicht ueberlaeuft. */
    private static final long NEVER = Long.MIN_VALUE / 2;
    /** Zaehler fuer ein spaeteres Debug-Overlay. Kumulativ, wird von {@link #invalidate()} bewusst
     *  NICHT zurueckgesetzt - die Zahl soll sagen, wie viel der Cache ueber eine Sitzung gebracht hat. */
    public record Stats(long hits, long misses, long evictions) {}

    /** Schluessel: Welt-Identitaet plus beide Endpunkte auf 1/16 Block gerundet. Ohne Dimension im
     *  Schluessel waeren nach einem Portalwechsel Overworld- und Nether-Strahlen nicht mehr
     *  unterscheidbar - der gleiche Zahlenkoerper, voellig andere Welt. */
    private record Key(ResourceKey<Level> dimension, int ax, int ay, int az, int bx, int by, int bz) {}

    private record Entry(boolean clear, long tick) {}

    private final int ttlTicks;
    /** Einfuege-Reihenfolge, nicht Zugriffs-Reihenfolge: der Cache wird gelesen, aber nur bei einem
     *  Miss geschrieben. Beim {@link #store} wird ein vorhandener Schluessel bewusst entfernt und
     *  neu eingefuegt, damit er wieder als "jung" gilt - das macht die Politik praktisch zu LRU. */
    private final Map<Key, Entry> entries = new LinkedHashMap<>();

    private long hits;
    private long misses;
    private long evictions;
    private long currentTick = NEVER;
    private ResourceKey<Level> dimension;

    public RaycastCache() {
        this(DEFAULT_TTL_TICKS);
    }

    /** @param ttlTicks Lebensdauer in Ticks; negative Werte bedeuten "nur im selben Tick gueltig". */
    public RaycastCache(int ttlTicks) {
        this.ttlTicks = Math.max(0, ttlTicks);
    }

    /** Setzt den Cache auf einen neuen Tick und raeumt abgelaufene Eintraege auf. EINMAL pro Tick
     *  aufrufen - mehrfach Aufrufen mit derselben Zahl ist unschaedlich, kostet aber den
     *  Durchlauf unnoetig.
     *
     *  Ein Dimensionswechsel leert den Cache: Blockkoordinaten bedeuten in einer anderen Dimension
     *  etwas voellig anderes, jeder alte Eintrag waere eine Falschaussage. Ebenso ein
     *  zurueckgesetzter Tickzaehler (neuer Server/Disconnect) - dann ist auch die Zeitachse hin.
     *  {@code dimension == null} steht fuer "gerade keine Welt geladen" und leert ebenfalls. */
    public void markTick(int tick, ResourceKey<Level> dimension) {
        if (tick < currentTick || !Objects.equals(dimension, this.dimension)) {
            entries.clear();
        }
        this.currentTick = tick;
        this.dimension = dimension;
        evictExpired();
    }

    /** Wirft alle Eintraege weg, behaelt aber die {@link #stats()}-Zaehler. */
    public void invalidate() {
        entries.clear();
    }

    /** Verwirft jeden Eintrag, dessen Segment die Blockzelle {@code (cx, cy, cz)} oder eine ihrer 26
     *  Nachbarzellen schneidet. Nachbarn mitzunehmen ist Pflicht: {@code Level#clip} laeuft ueber
     *  Block-Shapes, und ein Block kann die Kollisionsform seiner Nachbarn veraendern (Fence,
     *  Treppe, Slab), ohne dass der Server fuer diese Zelle ueberhaupt ein Update schickt.
     *
     *  Der Test ist ein echter Segment-gegen-AABB-Schnitt, kein Endpunkt-Bounding: ein langer Strahl
     *  quer durch die Map hat seine Endpunkte weit ausserhalb und muss trotzdem fallen. */
    public void invalidateCell(int cx, int cy, int cz) {
        if (entries.isEmpty()) return;
        // Block (cx) belegt quantisiert [cx*16, cx*16+15]; mit allen 26 Nachbarn [cx*16-16, cx*16+31].
        long minX = (long) cx * QUANT - QUANT, maxX = (long) cx * QUANT + (QUANT - 1);
        long minY = (long) cy * QUANT - QUANT, maxY = (long) cy * QUANT + (QUANT - 1);
        long minZ = (long) cz * QUANT - QUANT, maxZ = (long) cz * QUANT + (QUANT - 1);

        // Ein einziges Intervall-Array fuer alle Eintraege: pro Eintrag neu zu allozieren waere
        // genau die Sorte Allokation, die der Cache eigentlich einsparen soll.
        double[] range = { 0.0, 1.0 };
        Iterator<Map.Entry<Key, Entry>> it = entries.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Key, Entry> cached = it.next();
            Key k = cached.getKey();
            range[0] = 0.0;
            range[1] = 1.0;
            if (intersects(k, minX, minY, minZ, maxX, maxY, maxZ, range)) it.remove();
        }
    }

    /** @return {@code Boolean.TRUE}/{@code Boolean.FALSE} bei Treffer, {@code null} bei Miss. */
    public Boolean lookup(ResourceKey<Level> dimension, Vec3 a, Vec3 b) {
        if (dimension == null || a == null || b == null || !cacheable(a) || !cacheable(b)) return null;

        Key key = new Key(dimension, quantize(a.x), quantize(a.y), quantize(a.z),
            quantize(b.x), quantize(b.y), quantize(b.z));
        Entry entry = entries.get(key);
        if (entry == null) {
            misses++;
            return null;
        }
        if (currentTick - entry.tick() > ttlTicks) {
            entries.remove(key);
            misses++;
            return null;
        }
        hits++;
        return entry.clear();
    }

    /** Legt ein Ergebnis ab. Ueberschreitet der Cache {@link #MAX_ENTRIES}, fliegt zuerst der am
     *  laengsten nicht mehr geschriebene Eintrag raus - und das zaehlt als Eviction. */
    public void store(ResourceKey<Level> dimension, Vec3 a, Vec3 b, boolean clear) {
        if (dimension == null || a == null || b == null || !cacheable(a) || !cacheable(b)) return;

        Key key = new Key(dimension, quantize(a.x), quantize(a.y), quantize(a.z),
            quantize(b.x), quantize(b.y), quantize(b.z));
        // entfernen + neu einfuegen statt nur putzen: LinkedHashMap behaelt bei put auf einem
        // vorhandenen Schluessel seine Position, der Eintrag waere also dauerhaft "aeltester".
        entries.remove(key);
        entries.put(key, new Entry(clear, currentTick));

        while (entries.size() > MAX_ENTRIES) {
            Iterator<Map.Entry<Key, Entry>> it = entries.entrySet().iterator();
            it.next();
            it.remove();
            evictions++;
        }
    }

    public Stats stats() {
        return new Stats(hits, misses, evictions);
    }

    public int size() {
        return entries.size();
    }

    public int ttlTicks() {
        return ttlTicks;
    }

    private void evictExpired() {
        if (entries.isEmpty()) return;
        Iterator<Map.Entry<Key, Entry>> it = entries.entrySet().iterator();
        while (it.hasNext()) {
            if (currentTick - it.next().getValue().tick() > ttlTicks) it.remove();
        }
    }

    /** {@code floor(x*16)} statt {@code round}: die Quantisierung muss symmetrisch und
     *  verlustfrei-zuordenbar sein - jeder reale Blockkoordinate wird auf genau ein Quantumsraster
     *  abgebildet, ohne dass Nachbarzellen konkurrieren. */
    private static int quantize(double value) {
        return (int) Math.floor(value * QUANT);
    }

    /** NaN/Inf koennen nicht quantisiert werden (Ergebnis waere 0 und damit ein kollidierender
     *  Schluessel) - solche Abfragen laufen am Cache vorbei. */
    private static boolean cacheable(Vec3 v) {
        return Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z);
    }

    /** Segment (ax,ay,az)->(bx,by,bz) gegen die Achsen-Box, quantisierte Laengen, {@code range} als
     *  [tMin, tMax]-Intervall in 0..1. Das ist der klassische Slab-Test, hier ueber drei Achsen
     *  gefaltet; am Ende zaehlt nur noch, ob das Intervall nicht leer ist. */
    private static boolean intersects(Key k, long minX, long minY, long minZ,
                                      long maxX, long maxY, long maxZ, double[] range) {
        return clipAxis(k.ax(), (double) k.bx() - k.ax(), minX, maxX, range)
            && clipAxis(k.ay(), (double) k.by() - k.ay(), minY, maxY, range)
            && clipAxis(k.az(), (double) k.bz() - k.az(), minZ, maxZ, range);
    }

    private static boolean clipAxis(int origin, double direction, long min, long max, double[] range) {
        if (direction == 0) {
            // Parallel zur Achse: nur innerhalb des Kastens kann das Segment den Kasten treffen.
            return origin >= min && origin <= max;
        }
        double t1 = (min - origin) / direction;
        double t2 = (max - origin) / direction;
        if (t1 > t2) {
            double swap = t1;
            t1 = t2;
            t2 = swap;
        }
        if (t1 > range[0]) range[0] = t1;
        if (t2 < range[1]) range[1] = t2;
        return range[0] <= range[1];
    }
}
