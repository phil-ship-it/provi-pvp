package com.provipvp.net;

/**
 * Haelt den Bot an, wenn der Server hinkt.
 *
 * <p><b>Warum ueberhaupt:</b> bei Lag laeuft der Bot <i>schneller</i>, nicht langsamer. Der
 * Client holt die zurueckgehaltenen Ticks nicht nach, er schickt in der Wanduhrzeit dieselbe
 * Aktionsdichte — der Bot ist also mit einem Schlag pro <b>jedem</b> Bewegungspaket unterwegs,
 * waehrend der Server nur noch jede zweite Bewegung korrekt zuordnet. Grim sieht damit in der
 * Auswertung genau das, was ein Lag-Bot sieht: eine Aktion pro Server-Tick ohne jede Begruendung
 * dafuer. Die Folge ist nicht nur ein Flag, sondern echter Schaden: wer im Spike weiterhaust,
 * serviert dem Angreifer Gratis-Treffer, weil die i-Frames des Servers laenger als die sichtbare
 * Animation des Bots sind.
 *
 * <p><b>Die Messgroesse:</b> {@code TickRate.INSTANCE.getTimeSinceLastTick()} ist im Normalbetrieb
 * 1.0 (50 ms zwischen zwei Server-Ticks) und <b>wird bei Lag groesser als 1.0</b>. Genau dieses
 * Verhaeltnis — und nicht die absolute Zeit — ist hier die Eingabe, damit der Gate unabhaengig von
 * der tatsaechlichen Tickrate des Servers funktioniert.
 *
 * <p><b>Die zweite Bedingung — die eigene HP-Schwelle:</b> dieselbe Verzoegerung ist bei 20 HP
 * deutlich entspannter als bei 3. Bei vollem Leben wird nur <b>gedrosselt</b> (Aktionen alle n
 * Ticks), bei niedriger Gesundheit wird <b>pausiert</b>: der Bot darf sich bei knappen HP keinen
 * kostenlosen Treffer mehr einfangen. Ein harter Spike pausiert unabhaengig von den HP, weil dann
 * auch das Slot-Management und die Rotations-Zuordnung deserialisieren — jede Aktion waere
 * schlicht dem Zufall ueberlassen.
 *
 * <p>Rein funktional ueber {@code (timeSinceLastTick, healthFraction, Schwellen)} — kein
 * {@code mc.*}, keine Settings (R1). Der Integrations-Agent liest {@code TickRate.INSTANCE} und
 * {@code player.getHealth() / player.getMaxHealth()} und reicht beide Werte hier herein.
 */
public final class TickRateGate {

    /** {@code getTimeSinceLastTick()} im Normalbetrieb — der Wert, den ein gesunder Server liefert. */
    public static final double NOMINAL = 1.0;

    /**
     * Ab hier gilt der Server als gehaemmt. 1.2 entspricht 60 ms statt 50 ms pro Tick, also
     * 16 statt 20 Ticks pro Sekunde — noch unauffaellig, aber schon deutlich unter Norm.
     */
    public static final double DEFAULT_LAG_THRESHOLD = 1.2;

    /**
     * Ab hier gilt der Server als blockiert: 2.0 heisst 100 ms pro Tick, der Bot haengt zwei
     * volle Sekunden hinterher. Ab hier nicht mehr drosseln, sondern gar nichts mehr tun.
     */
    public static final double DEFAULT_SPIKE_THRESHOLD = 2.0;

    /**
     * Gesundheitsanteil, unterhalb dessen bei Lag hart pausiert wird. 0.3 entspricht 6 HP bei
     * vollen 20 — in dem Bereich kostet jeder eingefangene Treffer ein Totem.
     */
    public static final double DEFAULT_LOW_HEALTH_FRACTION = 0.3;

    /** Was der Gate ueber eine Aktion entscheidet. */
    public enum Verdict {
        /** Normalbetrieb: Aktionen wie geplant. */
        RUN,
        /** Gedaempft: nur noch jede {@code n}-te Aktion. */
        THROTTLE,
        /** Komplett blockiert: keine Aktion, kein Slot-Wechsel. */
        BLOCK
    }

    private final double lagThreshold;
    private final double spikeThreshold;
    private final double lowHealthFraction;
    private final int throttleEvery;

    /** Gate mit den Standard-Schwellen und Drosselung auf jede zweite Aktion. */
    public TickRateGate() {
        this(DEFAULT_LAG_THRESHOLD, DEFAULT_SPIKE_THRESHOLD, DEFAULT_LOW_HEALTH_FRACTION, 2);
    }

    /**
     * @param lagThreshold     {@code getTimeSinceLastTick()}, ab dem gehaemm wird
     * @param spikeThreshold   Wert, ab dem blockiert wird (muss groesser als {@code lagThreshold} sein)
     * @param lowHealthFraction Gesundheitsanteel, unterhalb dessen bei Lag blockiert wird
     * @param throttleEvery    {@code >= 1}: nur jede {@code n}-te Aktion wird bei
     *                         {@link Verdict#THROTTLE} ausgefuehrt
     */
    public TickRateGate(double lagThreshold, double spikeThreshold, double lowHealthFraction, int throttleEvery) {
        this.lagThreshold = lagThreshold;
        this.spikeThreshold = spikeThreshold;
        this.lowHealthFraction = lowHealthFraction;
        this.throttleEvery = Math.max(1, throttleEvery);
    }

    /**
     * @param timeSinceLastTick {@code TickRate.INSTANCE.getTimeSinceLastTick()}, normalerweise
     *                          {@value #NOMINAL}
     * @param healthFraction    {@code health / maxHealth} als Wert in {@code [0, 1]}; der
     *                          Todesfall ({@code 0}) wird wie jede andere niedrige Fraktion behandelt
     * @return das Urteil fuer diesen Moment
     */
    public Verdict evaluate(double timeSinceLastTick, double healthFraction) {
        if (timeSinceLastTick < lagThreshold) return Verdict.RUN;

        // Ein harter Spike ist unabhaengig vom HP ein Blocker: die Zuordnung von Bewegung, Rotation
        // und Aktion ist dann so unzuverlaessig, dass jede weitere Aktion dem Zufall ueberlassen waere.
        if (timeSinceLastTick >= spikeThreshold) return Verdict.BLOCK;

        // Gedaempft, aber gesund genug: reduzieren statt stoppen. Knappe HP heisst: gar nichts.
        return healthFraction < lowHealthFraction ? Verdict.BLOCK : Verdict.THROTTLE;
    }

    /**
     * Setzt das Urteil in eine konkrete Erlaubnis um — inklusive der Drosselung.
     *
     * <p>Der Aufrufer zaehlt die Aktionen selbst (z.B. ueber die Tick-Nummer), weil der Gate
     * zustandslos bleibt: sonst muesste er wissen, ob der aktuelle Tick der erlaubte ist.
     *
     * @param verdict     Ergebnis von {@link #evaluate}
     * @param actionIndex monoton steigender Aktionszaehler des aufrufenden Moduls
     * @return {@code true}, wenn die Aktion jetzt tatsaechlich ausgefuehrt werden darf
     */
    public boolean allows(Verdict verdict, int actionIndex) {
        return switch (verdict) {
            case RUN -> true;
            case THROTTLE -> actionIndex % throttleEvery == 0;
            case BLOCK -> false;
        };
    }

    /** @return {@code true}, wenn unter diesem Urteil <i>irgendeine</i> Aktion erlaubt ist */
    public boolean allowsAnyAction(Verdict verdict) {
        return verdict != Verdict.BLOCK;
    }
}