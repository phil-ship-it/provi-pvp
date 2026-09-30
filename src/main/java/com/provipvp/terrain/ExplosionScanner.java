package com.provipvp.terrain;

import com.provipvp.core.TerrainProbe;
import com.provipvp.util.PvpMath;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.function.Predicate;
import java.util.stream.IntStream;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/** Die Welt-Geometrie-Schicht: alles, was "ist diese Strecke frei?" heisst, und nichts darueber hinaus.
 *
 *  Zustaendig ist hier genau eine Sache: der {@link RaycastCache} und der aktuelle Tick. Die
 *  taktischen Entscheidungen (wohin werfen, wohin stellen) liegen in {@code PearlSolver} und den
 *  Aura-Modulen - dieser Scanner trifft keine Entscheidung, er liefert Rohbefunde. Damit bleibt die
 *  teure Voxel-Arbeit an einer Stelle, statt in jedem Modul ein eigenes Raycast-Verfahren zu haben.
 *
 *  Der Raycast selbst ist derselbe, den die Module schon benutzen: {@code Level#clip} mit
 *  {@code ClipContext.Block.COLLIDER} und {@code ClipContext.Fluid.NONE} - "blockiert die Kollisionsform
 *  eines Blocks die Strecke", unabhaengig von Flussig-/Fuellstand. Ein Wurf durch Wasser muss
 *  darum genauso als blockiert gelten wie einer durch eine Wand.
 *
 *  Lebenszyklus: einmal pro Tick {@link #markTick(int)} aufrufen (die Zahl muss monoton steigen, z.B.
 *  der Modul-Zaehler), bei jedem {@code ClientboundBlockUpdatePacket} {@link #onBlockUpdate(BlockPos)}.
 *  Ohne {@code markTick} arbeitet der Scanner korrekt, nur ohne Cache - {@code markTick} ist also
 *  keine Initialisierung, sondern die Gueltigkeitsverwaltung. */
public final class ExplosionScanner implements TerrainProbe {
    /** Abstand, innerhalb dessen ein Raycast-Treffer am Endpunkt noch als "frei" gilt. Ein exakter
     *  MISS ist bei einem Ziel, das direkt an einer Wand oder Stufenkante liegt, nicht der Normalfall -
     *  der Strahl trifft dann die Nachbarflaeche kurz vor dem Zielpunkt. Uebernommen aus
     *  {@code GodmodePvP.blastRayClear} (0.15), bewusst strenger als die 0.6 der Sichtlinien-Pruefung:
     *  hier geht es um die Bahn einer Perle, die an einem Kantenstreffer sofort zurueckprallt. */
    private static final double ENDPOINT_TOLERANCE_SQ = 0.15 * 0.15;

    private final RaycastCache cache = new RaycastCache();
    private int tick = -1;

    /** Zugriff auf den Cache nur zum Nachsehen (Debug-Overlay, {@link RaycastCache#stats()});
     *  Befuellen und Invalidieren laufen ueber die Methoden weiter unten. */
    public RaycastCache cache() {
        return cache;
    }

    /** Der zuletzt gemeldete Tick, oder -1 vor dem ersten {@link #markTick(int)}. */
    public int currentTick() {
        return tick;
    }

    /** Einmal pro Tick, VOR allen Raycast-Abfragen. Rueumt abgelaufene Cache-Eintraege auf und
     *  leert den Cache bei Dimensionswechsel (oder wenn der Tickzaehler zurueckgesetzt wurde).
     *
     *  Der Zaehler wird bewusst nicht selbst erhoeht: der Aufrufer entscheidet, was ein "Tick" ist.
     *  Sonst muesste der Scanner ein Tick-Event abonnieren oder selbst zaehlen - beides hiesse,
     *  dass zwei Zaehler derselben Sache im Client laufen. */
    public void markTick(int tick) {
        this.tick = tick;
        Level level = level();
        cache.markTick(tick, level != null ? level.dimension() : null);
    }

    /** Verwirft den gesamten Cache. Bei einem Serverwechsel sinnvoll, bevor der neue Server
     *  ueberhaupt Tickzahlen schickt; {@link #markTick(int)} faengt den Normalfall bereits ab. */
    public void invalidate() {
        cache.invalidate();
    }

    /** Anschlussstelle fuer {@code ClientboundBlockUpdatePacket}:
     *  {@code scanner.onBlockUpdate(packet.getPos())}.
     *
     *  Verworfen wird jeder gecachte Strahl, der die Zelle oder einen ihrer 26 Nachbarn schneidet -
     *  nicht nur die, deren Endpunkt genau darauf zeigt. Ein Ziel, das hinter einer Wand stand und
     *  jetzt im offenen steht, hat sonst bis zum TTL-Ablauf eine veraltete "blockiert"-Antwort. */
    public void onBlockUpdate(BlockPos pos) {
        if (pos == null) return;
        cache.invalidateCell(pos.getX(), pos.getY(), pos.getZ());
    }

    /** Ist die Strecke {@code from} -> {@code to} frei? Gecacht, siehe {@link RaycastCache}.
     *
     *  Ohne geladene Welt gilt die Strecke als frei: es gibt nichts, was blockieren koennte, und
     *  "false" wuerde in einem laufenden Kampf jede Sichtlinie blockieren, nur weil der Chunk
     *  gerade nachlaedt. */
    @Override
    public boolean clearShot(Vec3 from, Vec3 to) {
        if (from == null || to == null) return false;
        Level level = level();
        if (level == null) return true;

        ResourceKey<Level> dimension = level.dimension();
        Boolean cached = cache.lookup(dimension, from, to);
        if (cached != null) return cached;

        boolean clear = clipClear(level, from, to);
        cache.store(dimension, from, to, clear);
        return clear;
    }

    /** Prueft die Perlenbahn, ohne die Physik zu kennen: {@link PvpMath} simuliert die Bahn tickweise,
     *  {@link #segmentClear} beantwortet pro Abschnitt "frei oder nicht". Die Ballistik bleibt damit an
     *  EINER Stelle im Repo - eine zweite Kopie hier waere die dritte in diesem Projekt.
     *
     *  Bewusst nicht gecacht: der Schluessel waere ein Tupel aus Ursprung, Yaw, Pitch, Eigenbewegung
     *  und Tickfenster, und die Rechnung selbst ist billig gegenueber den Raycasts, die
     *  {@link #segmentClear} darin abfeuert - die profitieren ueber {@link #clearShot}-Aufrufe der
     *  Aufrufer, nicht ueber diesen Cache. */
    @Override
    public boolean trajectoryClear(Vec3 origin, double yaw, double pitch, Vec3 extraVel, int maxTicks) {
        return PvpMath.trajectoryClear(origin, yaw, pitch, extraVel, this::segmentClear, maxTicks);
    }

    /** Ein Abschnitt gilt als frei, wenn ihn kein Block blockiert. Das ist die Pruef-Naht, die
     *  {@link PvpMath#trajectoryClear} ueber {@code BiPredicate} erwartet - und zugleich die
     *  ungecachte Einzelabfrage, falls jemand genau eine Strecke pruefen will.
     *
     *  Gleiche Regeln wie {@link #clearShot}: keine Welt heisst "nichts blockiert", {@code null}-Endpunkte
     *  heissen "nicht bestaetigt frei" - sonst wuerden die beiden Methoden am selben Strahl
     *  widersprechen. {@link PvpMath#trajectoryClear} liefert selbst nie {@code null} an die Naht. */
    public boolean segmentClear(Vec3 from, Vec3 to) {
        Level level = level();
        if (level == null) return true;
        return from != null && to != null && clipClear(level, from, to);
    }

    /** Erste blockierte Position auf der Strecke {@code origin} -> {@code to}, oder {@code null} wenn
     *  die Strecke frei ist. Das ist die eigentlich nuetzliche Form: "wo genau" laesst sich als
     *  Hindernishoehe auswerten (Bogenhoehe fuer eine Perle, Kantenhoehe fuer einen Sprung), waehrend
     *  ein reines Ja/Nein diese Information wegwirft.
     *
     *  Ein Treffer genau am Endpunkt zaehlt nicht als Blockade - dieselbe Toleranzregel wie in
     *  {@link #clearShot}, sonst widersprächen sich beide Methoden am selben Strahl. */
    public Vec3 firstObstruction(Vec3 origin, Vec3 to) {
        Level level = level();
        if (level == null || origin == null || to == null) return null;

        BlockHitResult hit = level.clip(new ClipContext(origin, to,
            ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
        if (hit.getType() == HitResult.Type.MISS) return null;

        Vec3 location = hit.getLocation();
        return location.distanceToSqr(to) < ENDPOINT_TOLERANCE_SQ ? null : location;
    }

    /** Erste Standflaeche 1..{@code maxAhead} Bloecke in Blickrichtung, oder {@code null} wenn keine
     *  existiert. Genau die Suche aus {@code GodmodePvP.solveFloorSnapPearlAim}, nur getrennt von der
     *  Wurfentscheidung: "wo koennte ich landen" ist eine Weltfrage, "wurf ich" eine taktische.
     *
     *  Von vorn nach hinten, nicht "die beste": fuer einen Fall nach einer Explosion ist die naechste
     *  erreichbare Flaeche die richtige, weiter entfernte Kandidaten wuerden nur aus dem Bild fallen. */
    public BlockPos standableAhead(Player self, Direction forward, int maxAhead) {
        if (self == null || forward == null || maxAhead < 1) return null;

        BlockPos feet = self.blockPosition();
        for (int distance = 1; distance <= maxAhead; distance++) {
            BlockPos candidate = feet.relative(forward, distance);
            if (isStandable(candidate)) return candidate;
        }
        return null;
    }

    /** Sind die {@code feet} eine begehbare Zelle - Beine frei, Kopf frei, Boden darunter fest?
     *
     *  {@code self.onGround()} ist dafuer NICHT verwendbar: der Spieler kann mitten im Fall ueber
     *  festem Boden stehen, und genau dieser Fall ist der interessante (Explosion nach oben, Fall nach
     *  unten) - {@code onGround()} liefert dann false, obwohl direkt unter den Fuessen ein Block
     *  steht. */
    public boolean isStandable(BlockPos feet) {
        Level level = level();
        if (level == null || feet == null) return false;
        return level.getBlockState(feet).isAir()
            && level.getBlockState(feet.above()).isAir()
            && level.getBlockState(feet.below()).blocksMotion();
    }

    /** Zaehlt Bloecke in einem Wuerfel um {@code center} (Kantenlaenge {@code 2*radius+1}), die
     *  {@code matcher} erfuellen - fuer die Anchor-/Bett-Delta-Erkennung von auto-shield.
     *
     *  Semantisch exakt die Schleife aus {@code GodmodePvP.countNearbyBlocks}: jeder Offset im Wuerfel
     *  wird genau einmal geprueft, gezaehlt wird jeder Treffer des {@code matcher}. Ein veraenderter
     *  Zaehlwert verschiebt hier direkt die Aura-Logik, deshalb wird an der Reihenfolge nichts
     *  "verbessert".
     *
     *  Was die Stream-Fassung gegenueber der Schleife wirklich bringt, sind zwei Dinge, die man im
     *  Ausdruck nicht sieht:
     *  - Das {@code Level} wird EINMAL geholt statt pro Zelle (bei Radius 4 sind das 9*9*9 = 729
     *    Feldzugriffe und 729 Nullpruefungen pro Aufruf, und der Aufruf laeuft pro Tick und Ziel).
     *  - Pro Zelle wird kein {@code BlockPos} mehr alloziert. Die Schleife ruft {@code offset(dx,dy,dz)},
     *    das 729 mal ein neues, unveraenderliches {@code BlockPos} erzeugt; hier wandert ein einziger
     *    {@code MutableBlockPos} durch den Wuerfel. Sicher ist das, weil {@code getBlockState} den
     *    Blockzustand unveraenderlich zurueckgibt und die Position nirgends zurueckbehaelt.
     *
     *  Ohne geladene Welt zaehlt 0 - der Aufrufer bekommt damit "kein Delta", statt einer Ausnahme mitten
     *  im Tick. */
    public int countNearbyBlocks(BlockPos center, int radius, Predicate<BlockState> matcher) {
        Level level = level();
        if (level == null || center == null) return 0;

        final int cx = center.getX();
        final int cy = center.getY();
        final int cz = center.getZ();
        final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        return (int) IntStream.rangeClosed(-radius, radius)
            .boxed()
            .flatMap(dx -> IntStream.rangeClosed(-radius, radius)
                .boxed()
                .flatMap(dy -> IntStream.rangeClosed(-radius, radius)
                    .mapToObj(dz -> level.getBlockState(cursor.set(cx + dx, cy + dy, cz + dz)))
                    .filter(matcher)))
            .count();
    }

    /** Der eigentliche Voxel-Raycast. {@code ClipContext.Fluid.NONE} heisst: Fluesse und Wasser
     *  blockieren die Strecke NICHT - entscheidend fuer Perlen, die ueber Wasserfluechte fliegen. */
    private static boolean clipClear(Level level, Vec3 from, Vec3 to) {
        BlockHitResult hit = level.clip(new ClipContext(from, to,
            ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
        return hit.getType() == HitResult.Type.MISS || hit.getLocation().distanceToSqr(to) < ENDPOINT_TOLERANCE_SQ;
    }

    private static Level level() {
        return mc.level;
    }
}
