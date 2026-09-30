package com.provipvp.pearl;

import com.provipvp.terrain.ExplosionScanner;
import com.provipvp.util.PvpMath;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.function.IntPredicate;

/**
 * Taktische Entscheidungsschicht fuer Perlenwuerfe. Diese Klasse beantwortet ausschliesslich die
 * Frage "Wohin werfen?" - sie enthaelt bewusst KEINE Ballistik. Jede Flugbahn, jede Wurfloesung und
 * jede Flugzeit kommt unveraendert aus {@link PvpMath}; diese Klasse liefert nur die
 * taktische Entscheidung darueber, welches Ziel geworfen wird, und wie belastbar das Ergebnis ist.
 *
 * <p><b>Bewusst nicht enthalten:</b> die {@code pearlPitchVariance} und alles Ausfuehrungsspezifische
 * (Slotwechsel, Wurf-Cooldown, Item-Suche, tatsaechliches {@code use}-Packet). Das gehoert in die
 * Ausfuehrungsschicht, die {@link PearlAim} lediglich als reinen Zielwert verbraucht.
 *
 * <p><b>Pro-Tick-Vertrag:</b> die Methoden sind zustandslos und pro Tick je Szenario hoechstens
 * einmal aufzurufen. {@link #antiFall} ist dabei strikt O(1) mit maximal EINEM Raycast-artigen
 * Aufruf ({@code standableAhead}), damit die Ausfuehrungsschicht das 50-ms-Budget nach einer
 * Explosion einhaelt - Fallschaden im Kampf ist teuer, ein Wurf darf ihn aber auch nicht teuer
 * machen.
 */
public final class PearlSolver {

    /** Startpunkt jedes Wurfes: Augenposition, 0.1 Bloecke abwaerts, exakt wie in GodmodePvP. */
    private static final double EYE_DROP = 0.1;

    /** Perlenwurf hebt die Augenposition nochmals an - Landepunkt knapp ueber dem Boden. */
    private static final double LANDING_LIFT = 0.1;

    /** Kontrollierter Fallback-Pitch: fast senkrecht nach unten (Grad, positiv = nach unten). */
    private static final double STRAIGHT_DOWN_PITCH = 88.5;

    /** Unterhalb dieser Fallstrecke zahlt Vanilla keinen Fallschaden - dann lohnt kein Wurf. */
    private static final double MIN_FALL_DISTANCE = 3.0;

    /** Obergrenze des Vorhalts: verteidigt gegen unplausibel grosse, clientseitig gemeldete
     *  Geschwindigkeiten (Rubberbanding, Serverkorrekturen), die sonst zu voelligen Fehlwuerfen fuehren. */
    private static final double MAX_LEAD_BLOCKS = 12.0;

    /** Obergrenze des vorrausberechneten Fallwegs eines Ziels in der Luft (siehe {@link #predictLead}). */
    private static final double MAX_AIR_DROP = 6.0;

    /** Horizontale Mindestgeschwindigkeit, ab der eine Bewegung als echte Flucht gilt. */
    private static final double MIN_TRACKED_SPEED = 1e-4;

    /** Aufsetzpunkte des Bogenwurfs, nach Versatz hinter dem Ziel aufsteigend sortiert - Index 0
     *  ist der Direktwurf, jeder weitere Index ein flacherer Bogen mit groesserem Versatz. */
    private static final List<Double> BYPASS_OVERSHOOTS = List.of(0.0, 1.5, 3.0, 5.0, 8.0, 12.0);

    /** Anzahl der Bogen-Kandidaten in {@link #BYPASS_OVERSHOOTS}. */
    public static int bypassCandidateCount() {
        return BYPASS_OVERSHOOTS.size();
    }

    private final ExplosionScanner scanner;

    public PearlSolver(ExplosionScanner scanner) {
        this.scanner = scanner;
    }

    /**
     * Eigenbewegung des Werfers, die Minecraft beim Abwurf auf die Startgeschwindigkeit der Perle
     * addiert. Portiert aus {@code GodmodePvP#pearlThrowVelocity}: der Y-Anteil faellt weg, sobald
     * der Werfer am Boden steht - am Boden uebernimmt die Reibung, in der Luft nicht.
     */
    public static Vec3 pearlThrowVelocity(Player self) {
        Vec3 own = self.getKnownMovement();
        return new Vec3(own.x, self.onGround() ? 0 : own.y, own.z);
    }

    // ------------------------------------------------------------------ Szenario 1: Gap Closer

    /**
     * Das Ziel entkommt. Der Wurf zielt auf die praedizierte Position, nicht auf die aktuelle:
     * Vorhalt aus dem Bewegungsvektor, gedrosselt nach Blickrichtung (ein strafendes Ziel korrigiert
     * sooner als ein vorwärts weglaufendes) und gedeckelt (siehe {@link #MAX_LEAD_BLOCKS}).
     *
     * <p><b>Rueckgabe {@code null}</b>, wenn die Sichtlinie zur Vorhersage blockiert ist - dann ist
     * Szenario 2 ({@link #terrainBypass}) zustaendig, bzw. wenn das Ziel physisch unerreichbar ist.
     */
    public PearlAim gapCloser(Player self, LivingEntity target) {
        if (self == null || target == null || scanner == null) return null;

        Vec3 from = eyeOrigin(self);
        Vec3 extraVel = pearlThrowVelocity(self);
        Vec3 center = target.getBoundingBox().getCenter();

        // Runde 1 ohne Vorhalt: liefert die Flugzeit, die als Vorhaltfenster dient.
        double[] direct = PvpMath.solvePearlAim(from, center, extraVel);
        if (direct == null) return null;

        Vec3 aimPoint = predictedIntercept(center, target.position(), target.getDeltaMovement(),
            target.getYRot(), !target.onGround(), (int) Math.ceil(direct[2]));
        double[] aim = PvpMath.solvePearlAim(from, aimPoint, extraVel);
        if (aim == null) aim = direct;

        if (!scanner.clearShot(from, aimPoint)) return null;

        int flight = Math.max(1, (int) Math.ceil(aim[2]));
        if (!scanner.trajectoryClear(from, aim[0], aim[1], extraVel, flight + 1)) return null;

        return new PearlAim(aim[0], aim[1], flight, PearlScenario.GAP_CLOSER, true);
    }

    /**
     * Das Ziel ist hinter Terrain: der direkte Wurf laeuft in die Deckung, also wird ein BOGEN
     * geworfen, der ueber das Hindernis hinweg aufsetzt.
     *
     * <p>Bei fester Wurfgeschwindigkeit (1.5) gibt es zu einem festen Landepunkt genau EINEN
     * moeglichen Wurfwinkel - der Bogen ist also ueber seinen Aufsetzpunkt parametrisiert, nicht
     * ueber einen frei gewaehlten Pitch. Die Kandidatenliste {@link #BYPASS_OVERSHOOTS} ist
     * deshalb nach hinten versetztem Aufsetzpunkt sortiert, und es zaehlt der ERSTE freie Kandidat:
     * je naeher der Aufsetzpunkt am Ziel, desto flacher der Bogen, und je steiler der Bogen, desto
     * weiter landet er - ein "steilstes freies Fenster" schoebe den Aufsetzpunkt so weit nach hinten,
     * dass er selbst wieder an Terrain scheitert.
     *
     * <p><b>confident = false</b>, wenn kein Kandidat frei war: dann wird der direkte (blockierte)
     * Wurf als bestmoegliche Naeherung zurueckgegeben, damit die Ausfuehrungsschicht entscheiden
     * kann, statt in dieser Lage gar nichts zu werfen.
     */
    public PearlAim terrainBypass(Player self, LivingEntity target) {
        if (self == null || target == null || scanner == null) return null;

        Vec3 from = eyeOrigin(self);
        Vec3 extraVel = pearlThrowVelocity(self);
        Vec3 center = target.getBoundingBox().getCenter();

        double directX = center.x - from.x, directZ = center.z - from.z;
        double flatDist = Math.hypot(directX, directZ);
        if (!Double.isFinite(flatDist) || flatDist < 1e-6) return null;
        double dirX = directX / flatDist, dirZ = directZ / flatDist;

        // Ohne Hindernis ist der Direktwurf bereits durch den Scanner verifiziert.
        if (scanner.firstObstruction(from, center) == null) {
            double[] aim = PvpMath.solvePearlAim(from, center, extraVel);
            if (aim == null) return null;
            return new PearlAim(aim[0], aim[1], Math.max(1, (int) Math.ceil(aim[2])),
                PearlScenario.TERRAIN_BYPASS, true);
        }

        int chosen = selectLowestClear(bypassCandidateCount(), index -> {
            Vec3 landing = candidateLanding(center, dirX, dirZ, index);
            double[] aim = PvpMath.solvePearlAim(from, landing, extraVel);
            if (aim == null) return false;
            int flight = Math.max(1, (int) Math.ceil(aim[2]));
            return scanner.trajectoryClear(from, aim[0], aim[1], extraVel, flight + 1);
        });

        if (chosen < 0) {
            double[] fallback = PvpMath.solvePearlAim(from, center, extraVel);
            if (fallback == null) return null;
            return new PearlAim(fallback[0], fallback[1], Math.max(1, (int) Math.ceil(fallback[2])),
                PearlScenario.TERRAIN_BYPASS, false);
        }

        Vec3 landing = candidateLanding(center, dirX, dirZ, chosen);
        double[] aim = PvpMath.solvePearlAim(from, landing, extraVel);
        if (aim == null) return null;
        return new PearlAim(aim[0], aim[1], Math.max(1, (int) Math.ceil(aim[2])),
            PearlScenario.TERRAIN_BYPASS, true);
    }

    // ---------------------------------------------------------- Szenario 3: Anti-Fall-Damage

    /**
     * Anti-Fall-Damage nach einer Explosion: steil nach unten werfen, damit die Perlen-Teleport-
     * Landung den Fall abbremst, bevor der Schaden verrechnet wird.
     *
     * <p><b>Hartes Zeitbudget:</b> genau EIN {@code standableAhead}-Aufruf, danach nur noch
     * {@code isStandable} (reine Blockzustandsabfrage, kein Raycast) und {@link PvpMath}. Kein
     * Raycast-Backtracking, keine Schleife, keine Suche ueber Kandidatenpositionen - die Methode
     * ist O(1). Der Aufrufer muss sie im selben Tick nach der Explosion aufrufen koennen.
     *
     * <p>Aufsetzpunkte 1-3 Bloecke vor dem Spieler werden bevorzugt; nur wenn es dort keine
     * Standflaeche gibt, aber direkt unter den Fuessen doch, greift der kontrollierte
     * 88.5-Grad-Fallback (portiert aus {@code GodmodePvP#solveFloorSnapPearlAim}).
     *
     * <p><b>Rueckgabe {@code null}</b>, wenn die Fallstrecke unterhalb von {@link #MIN_FALL_DISTANCE}
     * liegt (Vanilla zahlt dann keinen Schaden) oder nirgends Boden existiert.
     */
    public PearlAim antiFall(Player self, double fallDistance) {
        if (self == null || scanner == null) return null;
        if (!Double.isFinite(fallDistance) || fallDistance < MIN_FALL_DISTANCE) return null;

        Direction forward = Direction.fromYRot(self.getYRot());
        BlockPos landing = scanner.standableAhead(self, forward, 3);

        if (landing != null) {
            Vec3 from = eyeOrigin(self);
            Vec3 target = Vec3.atBottomCenterOf(landing).add(0, LANDING_LIFT, 0);
            double[] aim = PvpMath.solvePearlAim(from, target, pearlThrowVelocity(self));
            if (aim != null) {
                return new PearlAim(aim[0], aim[1], Math.max(1, (int) Math.ceil(aim[2])),
                    PearlScenario.ANTI_FALL, true);
            }
        }

        // Kontrollierter Fallback: senkrecht nach unten, sofern direkt unter den Fuessen Boden liegt.
        if (!scanner.isStandable(self.blockPosition())) return null;
        return new PearlAim(self.getYRot(), STRAIGHT_DOWN_PITCH,
            straightDownFlightTicks(self), PearlScenario.ANTI_FALL, false);
    }

    // ------------------------------------------------------------------ reine Hilfsmathematik
    //  Alles ab hier ist frei von Minecraft-Zustaend und damit ohne laufenden Client testbar.

    /** Augenposition minus 0.1 - der Wurfpunkt, exakt wie in GodmodePvP. */
    private static Vec3 eyeOrigin(Player self) {
        return self.getEyePosition().subtract(0, EYE_DROP, 0);
    }

    /**
     * Prädiktionsversatz eines fliehenden Ziels ueber {@code ticks} Ticks. Am Boden rein linear
     * (Schwerkraft irrelevant, das Ziel bleibt auf Hoehe), in der Luft mit Vanillas
     * Entity-Gravitation von 0.08 Bloecken pro Tick integriert.
     *
     * <p>Der Fallweg ist auf {@link #MAX_AIR_DROP} Bloecke gedeckelt und der Gesamtversatz auf
     * {@link #MAX_LEAD_BLOCKS}: ohne Weltzugriff laesst sich der echte Boden nicht bestimmen, und ein
     * Ziel, das im Perlflug 20 Bloecke faellt, ist mit einer Perle ohnehin nicht mehr sauber zu
     * treffen - der niedrigere, naeher an der Koerperhoehe liegende Wurf ist dann der bessere.
     */
    public static Vec3 predictLead(Vec3 position, Vec3 velocity, int ticks, boolean airborne) {
        if (position == null || velocity == null || ticks <= 0) return position;

        double x = position.x + velocity.x * ticks;
        double z = position.z + velocity.z * ticks;

        double dx = x - position.x, dz = z - position.z;
        double flat = Math.hypot(dx, dz);
        if (flat > MAX_LEAD_BLOCKS) {
            double scale = MAX_LEAD_BLOCKS / flat;
            x = position.x + dx * scale;
            z = position.z + dz * scale;
        }

        if (!airborne) return new Vec3(x, position.y, z);

        double y = 0, vy = velocity.y;
        for (int tick = 0; tick < ticks; tick++) {
            vy -= 0.08;
            y += vy;
        }
        return new Vec3(x, position.y + Math.max(y, -MAX_AIR_DROP), z);
    }

    /**
     * Drosselung des Vorhalts nach Blickrichtung des Ziels. Laeuft ein Ziel weitgehend in seiner
     * Blickrichtung (vorwaerts oder rueckwaerts), ist die Fluchtlinie stabil und der volle Vorhalt
     * lohnt. Straft es quer zur Blickrichtung, korrigiert es in den naechsten Ticks - der Vorhalt
     * wird dann nur zur Haelfte angesetzt, sonst schiesst der Wurf systematisch daneben.
     */
    public static double leadFactor(double velocityYaw, double facingYaw) {
        double offset = Math.abs(PvpMath.wrapDelta((float) (velocityYaw - facingYaw)));
        return 0.5 + 0.5 * Math.abs(Math.cos(Math.toRadians(offset)));
    }

    /**
     * Laufrichtung eines Bewegungsvektors in Minecraft-Yaw-Konvention.
     *
     * <p>Das Negieren der x-Komponente muss die Vorzeichen-Null vermeiden: bei exakt
     * nordwaertiger Bewegung ({@code (0, 0, -1)}) ergibt {@code -0.0} sonst
     * {@code atan2(-0.0, -1.0) == -180} statt {@code +180}. Beide beschreiben dieselbe
     * Richtung, aber unterschiedliche Zahlen - und {@code ==} auf Yaw-Vergleiche laeuft
     * dadurch still ins Leere.
     */
    public static double movementYaw(Vec3 velocity) {
        double negatedX = velocity.x == 0.0 ? 0.0 : -velocity.x;
        return Math.toDegrees(Math.atan2(negatedX, velocity.z));
    }

    /**
     * Waehlt den NIEDRIGSTEN freien Bogen-Kandidaten: der kleinste Index, fuer den
     * {@code isClear} zutrifft - {@code -1}, wenn keiner frei ist. Bewusst NICHT der steilste
     * (groesste) freie Kandidat.
     */
    public static int selectLowestClear(int candidateCount, IntPredicate isClear) {
        for (int index = 0; index < candidateCount; index++) {
            if (isClear.test(index)) return index;
        }
        return -1;
    }

    /**
     * Aufsetzpunkt des Bogens: das Ziel, um {@code BYPASS_OVERSHOOTS[index]} Bloecke nach hinten
     * versetzt. Der Index muss aufsteigend nach Versatz sortiert sein.
     */
    public static Vec3 candidateLanding(Vec3 targetCenter, double dirX, double dirZ, int index) {
        if (index < 0 || index >= BYPASS_OVERSHOOTS.size()) {
            throw new IndexOutOfBoundsException("Bogen-Kandidat " + index);
        }
        double overshoot = BYPASS_OVERSHOOTS.get(index);
        return targetCenter.add(dirX * overshoot, 0, dirZ * overshoot);
    }

    /**
     * Prädizierter Treffpunkt des Gap Closers: Zielmitte plus Vorhalt. Der Vorhalt wird nach
     * Blickrichtung gedrosselt und entfaellt fuer ein stehendes Ziel vollstaendig. Ein Ziel in der
     * Luft wird beim Vorhalt zusaetzlich mit dem Fallweg mitgenommen.
     */
    public static Vec3 predictedIntercept(Vec3 targetCenter, Vec3 position, Vec3 velocity,
                                          double facingYaw, boolean airborne, int flightTicks) {
        if (horizontalSpeed(velocity) < MIN_TRACKED_SPEED || flightTicks <= 0) return targetCenter;
        int lead = (int) Math.round(flightTicks * leadFactor(movementYaw(velocity), facingYaw));
        if (lead <= 0) return targetCenter;
        return targetCenter.add(predictLead(position, velocity, lead, airborne).subtract(position));
    }

    /** Horizontale Geschwindigkeit, normiert fuer Y-unabhaengige Vergleiche. */
    public static double horizontalSpeed(Vec3 velocity) {
        return Math.hypot(velocity.x, velocity.z);
    }

    /** Flugzeit eines fast senkrechten Downward-Wurfs, geschaetzt ueber PvpMath. */
    private static int straightDownFlightTicks(Player self) {
        double yaw = self.getYRot();
        double yawRad = Math.toRadians(yaw);
        PvpMath.PearlArrival arrival = PvpMath.simulatePearl(yaw, STRAIGHT_DOWN_PITCH,
            pearlThrowVelocity(self), 0.5, -Math.sin(yawRad), Math.cos(yawRad));
        return arrival == null ? 1 : Math.max(1, (int) Math.ceil(arrival.ticks()));
    }
}
