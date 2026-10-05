package com.provipvp.rotation;

import java.util.Random;

/**
 * GCD-Quantisierung der Blickrichtung — der Addon-seitige Ersatz dafuer, dass Meteors
 * {@code Rotations.rotate()} die Winkel NICHT quantisiert.
 *
 * <p>Meteor schreibt rohe {@code float}s nach {@code serverYaw}/{@code serverPitch}; Grim rechnet
 * dagegen fuer jede gesendete Rotation den ggT aufeinanderfolgender Winkel-Deltas und schlaegt an,
 * wenn dieser unter {@link #MINIMUM_DIVISOR} faellt. Die einzige fuer Grim gueltige Form einer
 * Rotation ist deshalb eine, deren Winkel auf dem Gitter des aktuellen Maus-Sensitivitaets-Divisors
 * liegen. Genau dieses Gitter baut {@link #quantizeToLattice} auf.
 *
 * <p>Zwei Ergaenzungen, weil reines Aufrunden immer noch maschinenhaft aussieht:
 * <ul>
 *     <li><b>Duplikat-Vermeidung</b> — ein Ziel, das drei Mal hintereinander exakt dieselbe Rotation
 *         ansteuert, erzeugt drei Mal exakt dasselbe Delta; Grim flaggt das als
 *         {@code DuplicateRotPlace}. {@link AngleDeltaAccumulator#quantize} schiebt das Ergebnis
 *         deshalb um einen Gitterschritt, sobald zwei aufeinanderfolgende Deltas innerhalb
 *         {@link #DUPLICATE_EPSILON} gleich sind.</li>
 *     <li><b>Snap-Begrenzung</b> — der Sprung von +179 auf -179 ist fuer einen Menschen zwei Grad,
 *         fuer das Spiel aber eine 358-Grad-Drehung. {@link #easeYaw} nimmt deshalb immer den kurzen
 *         Weg und kappt ihn auf {@link #MAX_YAW_STEP} Grad.</li>
 * </ul>
 *
 * <p><b>Warum das Wickeln gittergenau passiert:</b> 360 Grad ist beim Divisor 0.0751
 * ({@code sensitivity = 0.5}) kein Vielfaches. Wer auf [-180, 180) wickelt, erzeugt genau an der
 * +-180-Grenze einen Sprung von fast 360 Grad und damit einen Wert, der nicht mehr auf dem Gitter
 * liegt — also genau die Rotation, die Grim als {@code AimModulo360} und als GCD-Verstoss sieht.
 * Deshalb wird auf die <em>gitternaechste</em> Periode gewickelt ({@link #wrapOnLattice}); sie
 * unterscheidet sich von 360 Grad um weniger als einen halben Gitterschritt.
 *
 * <p>Kein {@code mc.*} und keine Modul-Settings: die Empfindlichkeit kommt als Parameter, die Welt
 * gibt es gar nicht. Dadurch headless testbar (siehe {@code GcdRotatorTest}).
 */
public final class GcdRotator {

    private GcdRotator() {}

    /**
     * Grims {@code RotationGcd}-Schwelle: liegt der ggT zweier aufeinanderfolgender Winkel-Deltas
     * darunter, gilt die Rotation als nicht ueber die Maus zustande gekommen.
     */
    public static final double MINIMUM_DIVISOR = 0.0086;

    /** Groesster erlaubter Yaw-Sprung pro gesendeter Rotation (Grim {@code AimModulo360}). */
    public static final double MAX_YAW_STEP = 320.0;

    /** Zwei Deltas innerhalb dieser Spanne gelten als identisch (Grim {@code DuplicateRotPlace}). */
    public static final double DUPLICATE_EPSILON = 1e-4;

    /** Gesendete Blickrichtung in Grad.
     *
     *  <p>Der Yaw darf ausserhalb von [-180, 180) liegen. Das ist beabsichtigt: nur so bleibt jede
     *  gesendete Differenz exakt ein Vielfaches des Divisors, und der Server normalisiert selbst. */
    public record Rotation(double yaw, double pitch) {}

    /**
     * Der Divisor, auf den die Maus die Rotation abtastet: Vanillas {@code MouseHandler} skaliert
     * jeden Maus-Pixel mit {@code sensitivity * 0.15 + 1.0E-4} und rundet auf ganze Pixel. Damit ist
     * der kleinste legal erreichbare Winkelschritt genau dieser Wert.
     *
     * @param mouseSensitivity Rohwert des Options-Sliders (0.02 … 1.0)
     */
    public static double sensitivityDivisor(double mouseSensitivity) {
        return mouseSensitivity * 0.15 + 1.0E-4;
    }

    /** Normalisiert einen Winkel auf [-180, 180) — dieselbe Konvention wie {@code PvpMath.wrapDelta}. */
    public static double wrapDegrees(double degrees) {
        double wrapped = degrees % 360.0;
        if (wrapped >= 180.0) wrapped -= 360.0;
        if (wrapped < -180.0) wrapped += 360.0;
        return wrapped;
    }

    /** Runde auf das naechste Vielfache des Divisors — der eine Schritt, den Grim akzeptiert. */
    public static double quantizeToLattice(double angle, double divisor) {
        double d = sanitizeDivisor(divisor);
        return Math.round(angle / d) * d;
    }

    /**
     * Begrenzt einen Yaw-Sprung auf {@code maxStepDegrees}, immer entlang des kurzen Weges.
     *
     * <p>Entscheidend ist das {@link #wrapDegrees} in der Differenz: der Sprung von +179 auf -179
     * ist fuer einen Menschen zwei Grad, fuer Grim aber eine 358-Grad-Drehung. Genau die verhindert
     * diese Begrenzung.
     *
     * @return {@code fromYaw} plus dem gekappten Schritt; die gewickelte Differenz zum Eingangswert
     *         ueberschreitet {@code maxStepDegrees} nie. Der Rueckgabewert ist deshalb nicht auf
     *         [-180, 180) beschnitten — ein abschneidendes {@code wrapDegrees} waere genau die
     *         +-180-Grenze, die diese Klasse vermeiden soll.
     */
    public static double easeYaw(double fromYaw, double toYaw, double maxStepDegrees) {
        return fromYaw + clampAbs(wrapDegrees(toYaw - fromYaw), maxStepDegrees);
    }

    /**
     * Quantisiert eine Zielrotation auf das Mause-Gitter.
     *
     * <p>{@code prevYaw} muss die zuletzt GESENDETE Yaw-Rotation sein, nicht die Kamera — nur die
     * Differenz zwischen zwei gesendeten Rotationen sieht Grim. Voraussetzung fuer die
     * Gitter-Fracht ist, dass auch diese Basis selbst auf dem Gitter liegt; dafuer sorgt
     * {@link AngleDeltaAccumulator#seed}.
     *
     * <p>{@code prevPitch} gibt es absichtlich nicht: der Pitch wird nicht snap-begrenzt (das
     * Limit von 90 Grad macht einen Sprung ueber die Senkrechte hinaus unmoeglich, und ein Sprung
     * auf 90 Grad nach unten ist die ganz normale Ausrichtung auf ein Ziel am Boden — kein
     * {@code AimModulo360}-Fall), also aendert der Vorgabewert am Ergebnis nichts.
     *
     * @param jitterDegrees symmetrische Streuung um das Ziel; {@code <= 0} heisst "kein Jitter"
     * @param rng          vom Aufrufer gelieferte Zufallsquelle
     */
    public static Rotation quantize(double desiredYaw, double desiredPitch, double prevYaw,
                                    double divisor, double jitterDegrees, Random rng) {
        double d = sanitizeDivisor(divisor);

        double yaw = wrapOnLattice(quantizeToLattice(wrapDegrees(desiredYaw + spread(jitterDegrees, rng)), d), d);
        double pitch = clampToLattice(quantizeToLattice(clampPitch(desiredPitch + spread(jitterDegrees, rng)), d), d);

        // Nicht endliche Vorgabe (erster Aufruf) gilt als "noch nichts gesendet".
        double base = Double.isFinite(prevYaw) ? prevYaw : yaw;
        return new Rotation(easeYawOnLattice(base, yaw, MAX_YAW_STEP, d), pitch);
    }

    /**
     * Zustandsbehaltender Teil: kennt die zuletzt gesendete Rotation und deren Deltas und liefert
     * fuer den naechsten Aufruf eine Quantisierung, deren Delta sich vom vorigen unterscheidet.
     *
     * <p>Der Addon behaelt pro Kampf-Teilnehmer eine Instanz; wird das Ziel gewechselt, genuegt
     * {@link #reset()} — der naechste {@link #quantize} seedet neu.
     */
    public static final class AngleDeltaAccumulator {

        private boolean seeded;
        private double yaw;
        private double pitch;
        private double deltaYaw;
        private double deltaPitch;
        private boolean hasDelta;

        /** Ohne {@link #seed} angesetzt? Dann ist die Basis noch nicht auf dem Gitter. */
        public boolean isSeeded() {
            return seeded;
        }

        /** Zuletzt gesendete Rotation. */
        public Rotation last() {
            return new Rotation(yaw, pitch);
        }

        /** Gewickeltes Yaw-Delta der zuletzt gesendeten Rotation; 0 ohne vorherige Rotation. */
        public double lastYawDelta() {
            return deltaYaw;
        }

        /** Pitch-Delta der zuletzt gesendeten Rotation. */
        public double lastPitchDelta() {
            return deltaPitch;
        }

        /** Setzt die Gitter-Basis auf die aktuelle Kamera-Rotation. Einmal pro Kampfstart noetig. */
        public void seed(double currentYaw, double currentPitch, double divisor) {
            double d = sanitizeDivisor(divisor);
            this.yaw = wrapOnLattice(quantizeToLattice(wrapDegrees(currentYaw), d), d);
            this.pitch = clampToLattice(quantizeToLattice(clampPitch(currentPitch), d), d);
            this.deltaYaw = 0;
            this.deltaPitch = 0;
            this.hasDelta = false;
            this.seeded = true;
        }

        /**
         * Quantisiert die Zielrotation und uebernimmt das Ergebnis als "zuletzt gesendet".
         *
         * <p>Neben {@link GcdRotator#quantize} passiert hier die Duplikat-Vermeidung: faellt das
         * gewickelte Delta auf 1e-4 genau so aus wie das vorige, wird das Ergebnis um einen
         * Gitterschritt verschoben — in die Richtung, die vom gewuenschten Winkel weniger abweicht.
         */
        public Rotation quantize(double desiredYaw, double desiredPitch,
                                 double divisor, double jitterDegrees, Random rng) {
            if (!seeded) seed(desiredYaw, desiredPitch, divisor);

            double d = sanitizeDivisor(divisor);
            Rotation base = GcdRotator.quantize(desiredYaw, desiredPitch, yaw, d, jitterDegrees, rng);
            Rotation result = hasDelta ? avoidDuplicate(base, desiredYaw, desiredPitch, d) : base;

            deltaYaw = wrapOnLattice(result.yaw() - yaw, d);
            deltaPitch = result.pitch() - pitch;
            yaw = result.yaw();
            pitch = result.pitch();
            hasDelta = true;
            return result;
        }

        /** Vergisst die Historie — bei Zielwechsel, damit die alte Rotation das Delta nicht kontaminiert. */
        public void reset() {
            seeded = false;
            hasDelta = false;
            deltaYaw = 0;
            deltaPitch = 0;
        }

        private Rotation avoidDuplicate(Rotation candidate, double desiredYaw, double desiredPitch, double divisor) {
            Rotation fixed = candidate;

            if (Math.abs(Math.abs(wrapOnLattice(fixed.yaw() - yaw, divisor)) - Math.abs(deltaYaw))
                < DUPLICATE_EPSILON) {
                double up = fixed.yaw() + divisor;
                double down = fixed.yaw() - divisor;
                double errorUp = Math.abs(wrapOnLattice(up - desiredYaw, divisor));
                double errorDown = Math.abs(wrapOnLattice(down - desiredYaw, divisor));
                fixed = new Rotation(errorUp <= errorDown ? up : down, fixed.pitch());
            }

            if (Math.abs(Math.abs(fixed.pitch() - pitch) - Math.abs(deltaPitch)) < DUPLICATE_EPSILON) {
                double up = clampToLattice(fixed.pitch() + divisor, divisor);
                double down = clampToLattice(fixed.pitch() - divisor, divisor);
                double errorUp = Math.abs(up - desiredPitch);
                double errorDown = Math.abs(desiredPitch - down);
                fixed = new Rotation(fixed.yaw(), errorUp <= errorDown ? up : down);
            }

            return fixed;
        }
    }

    // ---------- interne Mathematik ----------

    private static double sanitizeDivisor(double divisor) {
        if (!Double.isFinite(divisor) || divisor <= 0) return MINIMUM_DIVISOR;
        return divisor;
    }

    /** Gleichverteilte Streuung in [-range, range]; {@code range <= 0} oder fehlende Quelle = 0. */
    private static double spread(double range, Random rng) {
        if (!(range > 0) || rng == null) return 0;
        double offset = (2.0 * rng.nextDouble() - 1.0) * range;
        // u == 0.5 liefert exakt 0.0 und damit einen wirkungslosen Jitter; ausgeschlossen, weil
        // der Jitter hier nur eine von zwei Schutzschichten ist und nicht die einzige sein darf.
        return offset == 0.0 ? range : offset;
    }

    private static double clampPitch(double pitch) {
        if (!Double.isFinite(pitch)) return 0;
        return Math.max(-90.0, Math.min(90.0, pitch));
    }

    /** Haelt den Pitch innerhalb +-90 und zwingt das Ergebnis danach zurueck auf das Gitter. */
    private static double clampToLattice(double value, double divisor) {
        double limit = Math.floor(90.0 / divisor) * divisor;
        double clamped = Math.max(-limit, Math.min(limit, value));
        return Math.round(clamped / divisor) * divisor;
    }

    /** Wie {@link #easeYaw}, aber mit gittergenauem Wickeln und ganzzahliger Schrittweite. */
    private static double easeYawOnLattice(double fromYaw, double toYaw, double maxStepDegrees, double divisor) {
        // Ganze Gitterschritte erlauben, sonst liegt das gekappte Ergebnis neben dem Gitter.
        double step = Math.floor(maxStepDegrees / divisor) * divisor;
        if (step < divisor) step = divisor;
        return fromYaw + clampAbs(wrapOnLattice(toYaw - fromYaw, divisor), step);
    }

    /**
     * Wickelt auf die gitternaechste 360-Grad-Periode.
     *
     * <p>Das Ergebnis ist immer ein Vielfaches des Divisors — genau der Unterschied zu
     * {@link #wrapDegrees}, das das Gitter an jeder +-180-Grenze verlaesst.
     */
    private static double wrapOnLattice(double angle, double divisor) {
        double period = Math.round(360.0 / divisor) * divisor;
        return angle - Math.round(angle / period) * period;
    }

    private static double clampAbs(double value, double max) {
        return Math.max(-max, Math.min(max, value));
    }
}