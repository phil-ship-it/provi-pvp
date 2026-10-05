package com.provipvp.ray;

import com.provipvp.rotation.GcdRotator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * Die eine Pruefung, die vor jeder Kampf-Aktion faellt: zeigt der Strahl aus der WIRKLICH
 * gesendeten Rotation ueberhaupt auf das beabsichtigte Ziel?
 *
 * <p>Der Punkt ist, dass fast alle stillen Fehlgriffe im Kampf dieselbe Ursache haben. Ein
 * Rotations-Queue-Eintrag, der nicht (oder spaeter als gedacht) ausgefuehrt wird, ein
 * {@code BlockHitResult} von Hand auf der falschen Flaeche, ein Crystal auf 4.2 Bloecke Entfernung:
 * in allen drei Faellen schickt der Client eine plausible Rotation und eine plausible Aktion, aber
 * die beiden gehoeren nicht zusammen. Grim wertet genau diese Diskrepanz aus — als
 * {@code RotationPlace}, {@code Reach}, {@code Hitboxes} oder {@code InvalidInteractCursor}. Diese
 * Klasse verhindert alle vier mit derselben Abfrage.
 *
 * <p><b>Warum die Kamera nicht zaehlt:</b> bei stillem Zielen schickt Meteor eine Rotation, die von
 * der Bildschirmrotation unabhaengig ist. Eine Validierung, die die Kamera liest, prueft dann etwas
 * voellig anderes als das, was der Server sieht — und faellt immer durch oder immer durch, je
 * nachdem, wohin der Spieler gerade schaut. Deshalb ist {@code sent} die einzige Groesse, aus der das
 * Urteil entsteht; die Kamera wird nur noch als Diagnose mitgegeben und schlaegt in
 * {@link Verdict#cameraWouldHit()} durch.
 *
 * <p>Kein {@code mc.*}: die Welt kommt als {@link World}, die Reichweiten aus {@link ReachPolicy}.
 */
public final class ActionRayValidator {

    private ActionRayValidator() {}

    /**
     * Aufweitung der Entity-Trefferbox, mit der der Server Interaktionen prueft.
     *
     * <p>Vanilla sucht Interaktionen nicht gegen die nackte Bounding Box, sondern gegen die um 0.3
     * aufgeweitete. Wer gegen die nackte Box prueft, verschlaegt echte Treffer um bis zu 0.3 Bloecke —
     * und wer sie weglässt, bekommt Grims {@code Hitboxes} nicht zu sehen.
     */
    public static final double PICK_INFLATION = 0.3;

    /** Abtastschrittweite der Sichtpruefung in Bloecken. Klein genug fuer Diagonalwaende. */
    private static final double OCCLUSION_STEP = 0.05;

    /** Weltzugriff fuer die Sichtpruefung. Bewusst ohne Block-Registries. */
    public interface World {
        boolean isSolid(BlockPos pos);
    }

    /** Achsenparallele Box in Weltkoordinaten; der Konstruktor sortiert die Ecken. */
    public record Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {

        public Box {
            double swap;
            if (minX > maxX) { swap = minX; minX = maxX; maxX = swap; }
            if (minY > maxY) { swap = minY; minY = maxY; maxY = swap; }
            if (minZ > maxZ) { swap = minZ; minZ = maxZ; maxZ = swap; }
        }

        public static Box of(Vec3 min, Vec3 max) {
            return new Box(min.x, min.y, min.z, max.x, max.y, max.z);
        }

        public Vec3 center() {
            return new Vec3((minX + maxX) / 2, (minY + maxY) / 2, (minZ + maxZ) / 2);
        }

        public Box inflate(double amount) {
            return new Box(minX - amount, minY - amount, minZ - amount,
                maxX + amount, maxY + amount, maxZ + amount);
        }

        public double distanceTo(Vec3 point) {
            return point.distanceTo(center());
        }
    }

    /** Warum eine Aktion nicht abgeschickt werden darf. */
    public enum Failure {

        /** Alles in Ordnung. */
        NONE,

        /** Der Strahl der gesendeten Rotation verfehlt das Ziel (Grim {@code RotationPlace}). */
        AIM_MISS,

        /** Ein Block steht zwischen Auge und Ziel. */
        OCCLUDED,

        /** Ausserhalb der Reichweite der Aktion (Grim {@code Reach}). */
        OUT_OF_REACH,

        /** Der Strahl trifft einen anderen Block oder eine andere Flaeche als beabsichtigt
         *  (Grim {@code InvalidInteractCursor}). */
        FACE_MISS,

        /** Der Strahl trifft ueberhaupt nichts. */
        NO_HIT
    }

    /**
     * Urteil zu einer Aktion.
     *
     * @param valid            {@code true} genau dann, wenn {@code failure == NONE}
     * @param angleErrorDegrees Winkel zwischen gesendeter Blickrichtung und der Achse Auge-Ziel
     * @param cameraWouldHit    nur Diagnose: haette die <em>Kamera</em> das Ziel getroffen? Wenn ja
     *                          und {@code valid} trotzdem {@code false} ist, laeuft die Aktion mit
     *                          falscher oder veralteter Rotation — das ist der Rotations-Queue-Bug.
     */
    public record Verdict(boolean valid, Failure failure, double angleErrorDegrees,
                          double distance, boolean cameraWouldHit) {

        static Verdict ok(double angleError, double distance, boolean cameraWouldHit) {
            return new Verdict(true, Failure.NONE, angleError, distance, cameraWouldHit);
        }

        static Verdict fail(Failure failure, double angleError, double distance, boolean cameraWouldHit) {
            return new Verdict(false, failure, angleError, distance, cameraWouldHit);
        }
    }

    /**
     * Prueft einen Angriff auf eine Entity (End Crystal zuenden, Spieler schlagen).
     *
     * <p>Reihenfolge der Pruefungen ist bewusst so gewaehlt: erst die Reichweite, dann der Treffer,
     * dann die Sicht. Die Reichweite ist die billigste und haeufigste Ursache — ein Crystal auf
     * 4.1 Bloecken faellt durch, unabhaengig davon, wie sauber die Rotation aussieht.
     *
     * @param sent   die tatsaechlich gesendete Rotation — das einzige, was zaehlt
     * @param camera die aktuelle Bildschirmrotation, rein diagnostisch
     * @param action {@link ReachPolicy.Action#MELEE} oder {@link ReachPolicy.Action#BREAK_ENTITY}
     * @param world  Sichtpruefung; {@code null} ueberspringt die Verdeckung
     */
    public static Verdict validateAttack(GcdRotator.Rotation sent, GcdRotator.Rotation camera, Vec3 eye,
                                         Box target, ReachPolicy.Action action, World world) {
        Vec3 center = target.center();
        double distance = eye.distanceTo(center);
        boolean cameraWouldHit = intersects(eye, PlaceCursorSolver.lookVector(camera.yaw(), camera.pitch()),
            target.inflate(PICK_INFLATION));

        if (!ReachPolicy.allows(action, distance) || !ReachPolicy.withinGrimReach(action, distance)) {
            return Verdict.fail(Failure.OUT_OF_REACH, angleError(eye, center, sent), distance, cameraWouldHit);
        }

        Vec3 direction = PlaceCursorSolver.lookVector(sent.yaw(), sent.pitch());
        Box pickable = target.inflate(PICK_INFLATION);
        double entry = rayBoxEntry(eye, direction, pickable);
        if (entry < 0) {
            return Verdict.fail(Failure.AIM_MISS, angleError(eye, center, sent), distance, cameraWouldHit);
        }

        if (world != null && occluded(eye, direction, entry, world)) {
            return Verdict.fail(Failure.OCCLUDED, angleError(eye, center, sent), distance, cameraWouldHit);
        }

        return Verdict.ok(angleError(eye, center, sent), distance, cameraWouldHit);
    }

    /** Bequemlichkeit fuer Aufrufer ohne diagnostische Kamera-Rotation. */
    public static Verdict validateAttack(GcdRotator.Rotation sent, Vec3 eye, Box target,
                                         ReachPolicy.Action action, World world) {
        return validateAttack(sent, sent, eye, target, action, world);
    }

    /**
     * Prueft eine Block-Interaktion (Crystal, Anker, Bett, Kolben setzen).
     *
     * <p>Hier ist die Frage nicht „trifft der Strahl ueberhaupt einen Block", sondern „trifft er
     * genau den Block und die Flaeche, die im {@code BlockHitResult} stehen wird". Beide werden
     * deshalb aus dem Gitterlauf der gesendeten Rotation abgeleitet und mit den beabsichtigten
     * verglichen — eine Rotation, die einen Nachbarblock trifft, faellt durch, auch wenn der
     * Nachbarblock nur 10 Zentimeter daneben liegt.
     *
     * @param face die beabsichtigte Flaeche, {@code null} heisst "irgendeine"
     */
    public static Verdict validateBlockInteraction(GcdRotator.Rotation sent, GcdRotator.Rotation camera,
                                                   Vec3 eye, BlockPos target, Direction face, World world) {
        double limit = ReachPolicy.limit(ReachPolicy.Action.PLACE_BLOCK);
        double distance = eye.distanceTo(Vec3.atCenterOf(target));
        boolean cameraWouldHit = PlaceCursorSolver.trace(eye,
            PlaceCursorSolver.lookVector(camera.yaw(), camera.pitch()), probe(world), limit) != null;

        if (!PlaceCursorSolver.withinPlacementReach(eye, target)) {
            return Verdict.fail(Failure.OUT_OF_REACH, Double.NaN, distance, cameraWouldHit);
        }

        Vec3 direction = PlaceCursorSolver.lookVector(sent.yaw(), sent.pitch());
        PlaceCursorSolver.Placement hit = PlaceCursorSolver.trace(eye, direction, probe(world), limit);
        if (hit == null) {
            return Verdict.fail(Failure.NO_HIT, Double.NaN, distance, cameraWouldHit);
        }

        double angleError = angleError(eye, hit.hit(), sent);
        if (!hit.clicked().equals(target) || (face != null && hit.face() != face)) {
            return Verdict.fail(Failure.FACE_MISS, angleError, distance, cameraWouldHit);
        }
        if (!hit.cursorLegal(false)) {
            return Verdict.fail(Failure.FACE_MISS, angleError, distance, cameraWouldHit);
        }

        return Verdict.ok(angleError, distance, cameraWouldHit);
    }

    /** Bequemlichkeit fuer Aufrufer ohne diagnostische Kamera-Rotation. */
    public static Verdict validateBlockInteraction(GcdRotator.Rotation sent, Vec3 eye,
                                                   BlockPos target, Direction face, World world) {
        return validateBlockInteraction(sent, sent, eye, target, face, world);
    }

    /**
     * Erste Schnittdistanz eines Strahls mit einer Box, oder {@code -1} ohne Treffer.
     *
     * <p>Slab-Verfahren: auf jeder Achse das Intervall bestimmen, in dem der Strahl innerhalb der
     * Box liegt, und die drei Ueberlappungen schneiden. Eine Box, die hinter dem Auge liegt, ergibt
     * kein Intervall und damit {@code -1}.
     */
    public static double rayBoxEntry(Vec3 origin, Vec3 direction, Box box) {
        double tMin = 0;
        double tMax = Double.POSITIVE_INFINITY;

        double[] axes = new double[]{origin.x, origin.y, origin.z};
        double[] dirs = new double[]{direction.x, direction.y, direction.z};
        double[] lows = new double[]{box.minX(), box.minY(), box.minZ()};
        double[] highs = new double[]{box.maxX(), box.maxY(), box.maxZ()};

        for (int axis = 0; axis < 3; axis++) {
            if (Math.abs(dirs[axis]) < 1.0E-12) {
                if (axes[axis] < lows[axis] || axes[axis] > highs[axis]) return -1;
                continue;
            }
            double inverse = 1.0 / dirs[axis];
            double near = (lows[axis] - axes[axis]) * inverse;
            double far = (highs[axis] - axes[axis]) * inverse;
            if (near > far) {
                double swap = near;
                near = far;
                far = swap;
            }
            tMin = Math.max(tMin, near);
            tMax = Math.min(tMax, far);
            if (tMin > tMax) return -1;
        }
        return tMin;
    }

    /** Winkelfehler der gesendeten Rotation gegen die Achse Auge-Ziel, in Grad. */
    public static double angleError(Vec3 eye, Vec3 point, GcdRotator.Rotation sent) {
        Vec3 toPoint = point.subtract(eye);
        double target = toPoint.length();
        if (!(target > 1.0E-9)) return 0;

        Vec3 direction = PlaceCursorSolver.lookVector(sent.yaw(), sent.pitch());
        double dot = (direction.x * toPoint.x + direction.y * toPoint.y + direction.z * toPoint.z) / target;
        return Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, dot))));
    }

    // ---------- interne Geometrie ----------

    private static boolean intersects(Vec3 eye, Vec3 direction, Box box) {
        return rayBoxEntry(eye, direction, box) >= 0;
    }

    /**
     * Liegt vor dem ersten Treffer ein Block im Strahl?
     *
     * <p>{@code entry} ist in derselben Parametrisierung angegeben wie der Strahlvektor selbst,
     * deshalb wird die Schrittweite vorher durch dessen Laenge geteilt. Das Ergebnis ist unabhaengig
     * davon, ob der Aufrufer einen normalisierten oder einen rohen Richtungsvektor reingibt.
     */
    private static boolean occluded(Vec3 eye, Vec3 direction, double entry, World world) {
        double length = direction.length();
        if (!(length > 1.0E-9)) return false;

        for (double travelled = OCCLUSION_STEP; travelled < entry; travelled += OCCLUSION_STEP) {
            double travelledFraction = travelled / length;
            Vec3 point = new Vec3(
                eye.x + direction.x * travelledFraction,
                eye.y + direction.y * travelledFraction,
                eye.z + direction.z * travelledFraction);
            if (world.isSolid(BlockPos.containing(point.x, point.y, point.z))) return true;
        }
        return false;
    }

    /** Die beiden Welt-Interfaces sind getrennt typisiert; hier ist die Bruecke zwischen ihnen. */
    private static PlaceCursorSolver.World probe(World world) {
        return world == null ? pos -> false : world::isSolid;
    }

}