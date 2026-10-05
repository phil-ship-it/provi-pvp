package com.provipvp.ray;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Leitet aus einer Rotation den Blockplatzierungs-Strahl ab: Treffpunkt, angelickte Flaeche, Zielzelle
 * und den Cursor, den das Server-Paket daraus rekonstruiert.
 *
 * <p>Der Hintergrund ist ein Vanilla-Detail, das man leicht falsch macht: der Server bekommt mit
 * {@code ServerboundUseItemOnPacket} einen kompletten {@code BlockHitResult} — Position, Flaeche,
 * BlockPos — und rekonstruiert daraus ueber {@code BlockPlaceContext} die Zielzelle als
 * {@code clickedPos.relative(clickedDirection)}. Baut man den Treffer von Hand und nimmt die falsche
 * Flaeche, zuegt Vanilla schlicht an einen anderen Ort: der Crystal erscheint einen Block daneben
 * oder gar nicht. Diese Klasse loest deshalb die Flaeche aktiv auf, statt sie zu raten.
 *
 * <p>Der Cursor ist die zweite Fehlerquelle. Grim berechnet ihn als lokale Koordinaten des
 * Treffpunkts auf der getroffenen Flaeche und erwartet beide Anteile in {@code [0,1]} — bzw.
 * {@code [0,1.5]} fuer Lectern und Scaffolding, deren Treffpunkt bewusst ueber die Blockkante
 * hinausragen darf. Ein {@code BlockHitResult}, dessen Position auf einem anderen Block liegt als
 * der, den er angibt, erzeugt sofort einen Cursor ausserhalb dieses Fensters
 * ({@code FabricatedPlace}/{@code InvalidPlaceCursor}).
 *
 * <p>Kein {@code mc.*}: die Welt kommt als {@link World}. Dadurch headless testbar.
 */
public final class PlaceCursorSolver {

    private PlaceCursorSolver() {}

    /** Untere Cursor-Grenze; Vanilla-Clip ist [0, 1]. */
    public static final double CURSOR_MIN = 0.0;

    /** Obere Cursor-Grenze fuer Bloecke, deren Treffpunkt im Block liegt. */
    public static final double CURSOR_MAX = 1.0;

    /**
     * Obere Cursor-Grenze fuer Lectern und Scaffolding — deren Platzierung ragt im Vanilla um einen
     * halben Block ueber die eigene Flaeche hinaus, der Cursor darf das abbilden.
     */
    public static final double CURSOR_MAX_EXTENDED = 1.5;

    /**
     * Rand-Einzug des berechneten Treffpunkts in die getroffene Flaeche.
     *
     * <p>Der Gitterlauf landet mathematisch exakt auf der Blockgrenze; in {@code float}-Praxis ist
     * das eine Winzigkeit daneben, und ein Cursor von {@code -1e-9} ist fuer Grim ausserhalb des
     * Fensters. Der Einzug faengt das ab und kostet 1 Mikrometer Zielgenauigkeit.
     */
    private static final double FACE_INSET = 1.0E-6;

    /** Weltzugriff fuer die Platzierungs-Geometrie. Bewusst ohne Block-Registries. */
    public interface World {

        /** Blockiert die Position einen Strahl (der Block hat eine Kollisionsform)? */
        boolean isSolid(BlockPos pos);

        /** Darf der zu setzende Block diese Zelle ersetzen? Luft, Gras, Wasser, … */
        default boolean isReplaceable(BlockPos pos) {
            return !isSolid(pos);
        }
    }

    /**
     * Aufgeloeste Platzierung.
     *
     * @param hit     Treffpunkt auf der Flaeche von {@code clicked}
     * @param face    Normalenrichtung der getroffenen Flaeche (zeigt von {@code clicked} weg)
     * @param clicked getroffener Block
     * @param placed  daraus folgende Zielzelle, immer {@code clicked.relative(face)}
     * @param cursorX horizontaler Cursoranteil auf der Flaeche
     * @param cursorY vertikaler Cursoranteil auf der Flaeche
     */
    public record Placement(Vec3 hit, Direction face, BlockPos clicked, BlockPos placed,
                            double cursorX, double cursorY) {

        /** Erlaubt dieser Cursor die Platzierung? {@code extended} gilt fuer Lectern/Scaffolding. */
        public boolean cursorLegal(boolean extended) {
            return PlaceCursorSolver.cursorLegal(cursorX, cursorY, extended);
        }

        /** Liegt die Zielzelle in Block-Interaktionsreichweite (4.5)? */
        public boolean withinReach(Vec3 eye) {
            return PlaceCursorSolver.withinPlacementReach(eye, clicked);
        }
    }

    /**
     * Blickrichtung zu einer Rotation in Minecrafts Konvention: Yaw 0 zeigt nach +Z, steigender Yaw
     * dreht nach -X, der Pitch ist negativ nach oben.
     *
     * <p>Liegt bewusst hier und nicht in {@code ActionRayValidator}: die Platzierungs-Geometrie ist
     * die niedrigere Ebene, die Strahlvalidierung baut darauf auf.
     */
    public static Vec3 lookVector(double yaw, double pitch) {
        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);
        double horizontal = Math.cos(pitchRad);
        return new Vec3(-Math.sin(yawRad) * horizontal, -Math.sin(pitchRad), Math.cos(yawRad) * horizontal);
    }

    /**
     * Verfolgt den Strahl von {@code origin} entlang {@code direction} bis zum ersten soliden Block.
     *
     * <p>Gitterlauf nach Amanatides/Woo: bei jedem Schritt steht fest, welcher Block als naechster
     * betreten wird und ueber welche Flaeche — daraus ergibt sich die Richtung, ohne einen zweiten
     * Raycast gegen die Blockform.
     *
     * @param direction wird auf seinen Betrag normalisiert
     * @return {@code null}, wenn bis {@code maxDistance} nichts getroffen wird oder der Ausgangspunkt
     *         selbst in einem soliden Block liegt (dann gibt es keine unterscheidbare Flaeche)
     */
    public static Placement trace(Vec3 origin, Vec3 direction, World world, double maxDistance) {
        double len = direction.length();
        if (!(len > 1.0E-9) || !Double.isFinite(maxDistance) || maxDistance <= 0) return null;

        double dirX = direction.x / len;
        double dirY = direction.y / len;
        double dirZ = direction.z / len;

        BlockPos start = BlockPos.containing(origin.x, origin.y, origin.z);
        if (world.isSolid(start)) return null;

        int cellX = start.getX();
        int cellY = start.getY();
        int cellZ = start.getZ();

        int stepX = dirX > 0 ? 1 : -1;
        int stepY = dirY > 0 ? 1 : -1;
        int stepZ = dirZ > 0 ? 1 : -1;

        double tMaxX = boundary(origin.x, cellX, stepX, dirX);
        double tMaxY = boundary(origin.y, cellY, stepY, dirY);
        double tMaxZ = boundary(origin.z, cellZ, stepZ, dirZ);

        double deltaX = cellStride(dirX);
        double deltaY = cellStride(dirY);
        double deltaZ = cellStride(dirZ);

        double travelled = 0;
        Direction face = null;
        while (travelled <= maxDistance) {
            BlockPos cell = new BlockPos(cellX, cellY, cellZ);
            if (world.isSolid(cell)) {
                // Ohne vorherigen Schritt gaelte die Startzelle - die ist aber schon oben als
                // "nicht solid" abgewiesen, also steht hier immer eine echte Eintrittsflaeche fest.
                if (face == null) return null;
                return buildPlacement(origin, dirX, dirY, dirZ, travelled, cell, face);
            }

            if (tMaxX <= tMaxY && tMaxX <= tMaxZ) {
                cellX += stepX;
                travelled = tMaxX;
                tMaxX += deltaX;
                face = enteredFace(0, stepX);
            } else if (tMaxY <= tMaxZ) {
                cellY += stepY;
                travelled = tMaxY;
                tMaxY += deltaY;
                face = enteredFace(1, stepY);
            } else {
                cellZ += stepZ;
                travelled = tMaxZ;
                tMaxZ += deltaZ;
                face = enteredFace(2, stepZ);
            }
        }

        return null;
    }

    /** Bequemlichkeit: rechnet die Rotation erst in eine Blickrichtung um, siehe {@link #lookVector}. */
    public static Placement traceFromRotation(Vec3 origin, double yaw, double pitch, World world, double maxDistance) {
        return trace(origin, lookVector(yaw, pitch), world, maxDistance);
    }

    /**
     * Die Flaeche von {@code clicked}, die dem Spieler zugewandt ist.
     *
     * <p>Das ist die Flaeche, die Vanilla fuer eine Platzierung waehlen wuerde: diejenige, deren
     * Normale am staerksten zum Betrachter zeigt. Ein Block, der auf drei Seiten umgeben ist, hat
     * genau eine solche Seite — und genau die muss auch die im {@code BlockHitResult} stehen.
     *
     * @return {@code null}, wenn der Spieler exakt im Blockzentrum steht (keine Richtung dominiert)
     */
    public static Direction faceTowardPlayer(BlockPos clicked, Vec3 eye) {
        Vec3 toEye = eye.subtract(Vec3.atCenterOf(clicked));

        Direction best = null;
        double bestDot = 0;
        for (Direction dir : Direction.values()) {
            double dot = dir.getStepX() * toEye.x + dir.getStepY() * toEye.y + dir.getStepZ() * toEye.z;
            if (dot > bestDot) {
                bestDot = dot;
                best = dir;
            }
        }
        return best;
    }

    /**
     * Alle Flaechen, ueber die die Zielzelle {@code placed} von einem Nachbarblock aus erreichbar
     * waere — sortiert von der dem Spieler am staerksten zugewandten Flaeche ab.
     *
     * <p>Das ist die Aufloesung der Aufgabe "welche Seite nehme ich" ohne Raten: es werden alle sechs
     * Richtungen des Nachbarblocks durchprobiert, und uebrig bleiben genau die, die solid sind und
     * dem Spieler zugewandt sind. Der Aufrufer nimmt das erste Element und erhaelt damit eine
     * {@link Placement}, deren Cursor mitten auf der Flaeche liegt — die mit Abstand
     * unauffaelligste Variante.
     *
     * <p>Leer heisst: der Block ist auf keiner freien Seite erreichbar. Der Aufrufer soll dann das
     * gewaehlte {@code placed} verwerfen, statt eine Platzierung zu schicken, die ins Leere zeigt.
     */
    public static List<Placement> candidates(BlockPos placed, Vec3 eye, World world) {
        List<Placement> found = new ArrayList<>(6);

        for (Direction face : Direction.values()) {
            // Der Nachbarblock liegt der Zielzelle gegenueber, die Flaeche zeigt von ihm auf sie.
            BlockPos clicked = placed.relative(face.getOpposite());
            if (!world.isSolid(clicked)) continue;
            if (faceTowardPlayer(clicked, eye) != face) continue;

            Vec3 hit = Vec3.atCenterOf(clicked).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
            double[] cursor = cursor(hit, face, clicked);
            found.add(new Placement(hit, face, clicked, placed, cursor[0], cursor[1]));
        }

        Comparator<Placement> order = Comparator
            .comparingDouble((Placement p) -> -facingDot(p.clicked(), eye))
            .thenComparingDouble(p -> Vec3.atCenterOf(p.clicked()).distanceToSqr(eye));
        found.sort(order);
        return found;
    }

    /**
     * Lokale Cursor-Koordinaten des Treffpunkts auf der getroffenen Flaeche.
     *
     * <p>Die Zuordnung folgt Grims Konvention: {@code [0]} ist die horizontale, {@code [1]} die
     * vertikale Komponente der Flaeche. Auf einer UP/DOWN-Flaeche waagerecht also X gegen Z, auf den
     * vier senkrechten Flaechen X bzw. Z gegen Y.
     */
    public static double[] cursor(Vec3 hit, Direction face, BlockPos clicked) {
        double localX = hit.x - clicked.getX();
        double localY = hit.y - clicked.getY();
        double localZ = hit.z - clicked.getZ();

        return switch (face) {
            case UP, DOWN -> new double[]{localX, localZ};
            case NORTH, SOUTH -> new double[]{localX, localY};
            default -> new double[]{localZ, localY};
        };
    }

    /** Liegen beide Cursoranteile im von Vanilla bzw. Grim erlaubten Fenster? */
    public static boolean cursorLegal(double cursorX, double cursorY, boolean extended) {
        double max = extended ? CURSOR_MAX_EXTENDED : CURSOR_MAX;
        return inRange(cursorX, max) && inRange(cursorY, max);
    }

    /**
     * Liegt der getroffene Block in Block-Interaktionsreichweite?
     *
     * <p>Gemessen wird wie im Server: von der Augenposition zur Blockmitte, nicht zur naechsten
     * Flaeche. Eine Platzierung knapp hinter 4.5 Blöcken faellt hier durch, auch wenn der
     * Treffpunkt auf der dem Spieler zugewandten Seite noch erreichbar waere.
     */
    public static boolean withinPlacementReach(Vec3 eye, BlockPos clicked) {
        return ReachPolicy.allows(ReachPolicy.Action.PLACE_BLOCK, eye.distanceTo(Vec3.atCenterOf(clicked)));
    }

    // ---------- interne Geometrie ----------

    private static Placement buildPlacement(Vec3 origin, double dirX, double dirY, double dirZ,
                                            double travelled, BlockPos cell, Direction face) {
        Vec3 hit = new Vec3(
            clamp(origin.x + dirX * travelled, cell.getX() + FACE_INSET, cell.getX() + 1.0 - FACE_INSET),
            clamp(origin.y + dirY * travelled, cell.getY() + FACE_INSET, cell.getY() + 1.0 - FACE_INSET),
            clamp(origin.z + dirZ * travelled, cell.getZ() + FACE_INSET, cell.getZ() + 1.0 - FACE_INSET));

        double[] cursor = cursor(hit, face, cell);
        return new Placement(hit, face, cell, cell.relative(face), cursor[0], cursor[1]);
    }

    /** Wegstrecke bis zur naechsten Zellgrenze auf einer Achse; unendlich bei Nulkomponente. */
    private static double boundary(double coordinate, int cell, int stepSign, double direction) {
        if (direction == 0) return Double.POSITIVE_INFINITY;
        double plane = stepSign > 0 ? cell + 1 : cell;
        return (plane - coordinate) / direction;
    }

    /** Wegstrecke zwischen zwei Zellgrenzen auf einer Achse. */
    private static double cellStride(double direction) {
        return direction == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / direction);
    }

    /** Flaeche, durch die der Gitterlauf in die betretene Zelle eingetreten ist. */
    private static Direction enteredFace(int axis, int stepSign) {
        return switch (axis) {
            case 0 -> stepSign > 0 ? Direction.WEST : Direction.EAST;
            case 1 -> stepSign > 0 ? Direction.DOWN : Direction.UP;
            default -> stepSign > 0 ? Direction.NORTH : Direction.SOUTH;
        };
    }

    private static double facingDot(BlockPos clicked, Vec3 eye) {
        Vec3 toEye = eye.subtract(Vec3.atCenterOf(clicked));
        Direction face = faceTowardPlayer(clicked, eye);
        if (face == null) return 0;
        return face.getStepX() * toEye.x + face.getStepY() * toEye.y + face.getStepZ() * toEye.z;
    }

    private static boolean inRange(double value, double max) {
        return Double.isFinite(value) && value >= CURSOR_MIN && value <= max;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}