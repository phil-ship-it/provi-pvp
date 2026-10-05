package com.provipvp.hud;

import com.provipvp.ProviPvPAddon;
import com.provipvp.broker.ActionBroker;
import com.provipvp.broker.PathLease;
import com.provipvp.broker.PvpServices;
import com.provipvp.crystal.CrystalOwnership;
import com.provipvp.perf.ScanBudget;
import com.provipvp.rotation.GcdRotator;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.KeybindSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.client.Minecraft;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 * Das Panel, an dem der User den Zustand des Kampfes abliest. Es ist das Werkzeug, mit dem D1, B2/B3
 * und die Belegungsregeln überhaupt sichtbar werden - und aus genau diesem Grund darf es nichts tun
 * ausser lesen.
 *
 * <p><b>Rein lesend, und das ist die eigentliche Anforderung.</b> Das Element fordert keine Belegung
 * an, haelt Baritone nicht fest, beruehrt die Hotbar nicht und schreibt in keine Rotation. Ein
 * Diagnosewerkzeug, das selbst erst belegt, leert genau die Belegung, die es belegen soll: der
 * Nutzer sähe "Broker: CRYSTAL=ProviDebugOverlay" und wüsste nichts mehr ueber den eigentlichen Kampf.
 *
 * <p><b>Was die Zeilen beweisen sollen.</b>
 * <ul>
 *   <li><b>Modul + Aktion</b> - {@link Module#getInfoString()} ist der oeffentliche Aktions-String der
 *       beiden Profile; darum steht kein eigener Zustand daneben, der auseinanderlaufen koennte.</li>
 *   <li><b>Ziel und Reaktionszeit</b> - kommt aus {@link PvpSessionStats}, das Ziel einmal pro Tick
 *       aufloest und daraus misst, wie lange es bis zum ersten Schlag dauert. Genau das ist die
 *       Spur, an der man die Humanisierung sieht: eine sichtbare Pause, bevor ueberhaupt gehauen wird.</li>
 *   <li><b>Rotation und GCD-Delta</b> - {@link Rotations#serverYaw} / {@link Rotations#serverPitch} sind
 *       das, was wirklich im Movement-Paket landet. Meteor quantisiert <i>nicht</i>, das Gitter macht
 *       {@link GcdRotator}. Die Zeile zeigt die Differenz zum Vortick; genau 0 oder groesser 5 Grad
 *       faellt Grim gar erst an, also faerbt sie genau diese zwei Faelle rot.</li>
 *   <li><b>Scan-Budget</b> - {@code erlaubt/verschluckt} je {@link ScanBudget.ScanKind}. Das ist der
 *       Beleg fuer B2/B3: "verschluckt" zaehlt die ersparten Blockvolumen-Sweeps.</li>
 *   <li><b>Broker</b> - die lebende Belegung. Man sieht hier, wer eine Handlung bekommen hat und wer
 *       nicht, statt es aus dem Verhalten zu raten.</li>
 *   <li><b>Crystal-Besitz</b> - wie viele Crystals das Modul als eigene fuehrt und wie viele aus dem
 *       Alters- oder Kapazitaetsfenster gefallen sind.</li>
 * </ul>
 *
 * <p><b>Wieso der Zustand der Module gelesen wird statt gemeldet zu werden.</b> Die Zaehler von
 * {@link ScanBudget} und die Zugehoerigkeit aus {@link CrystalOwnership} liegen als private Felder in
 * den Kampfmodulen. Sie auszulesen ist hier billiger und ehrlicher als eine zweite Wahrheit: es gibt
 * keinen Pfad, auf dem ein Modul "seinen" Zustand an ein HUD haengen muesste, und damit auch keinen,
 * auf dem die Anzeige etwas anzeigen koennte, das der Broker nicht auch benutzt. Gesucht wird
 * deshalb nach dem <b>Feldtyp</b>, nicht nach dem Feldnamen - eine Umbenennung im Modul darf die
 * Anzeige nicht ausschalten. Findet sich kein solches Feld, faellt die Zeile ersatzlos weg, statt
 * eine Zahl zu erfinden.
 *
 * <p><b>Kosten.</b> Das Panel wird jeden Frame gezeichnet, darf also pro Zeile nichts neu erzeugen.
 * Alle Texte entstehen deshalb einmal pro Client-Tick in {@link #tick(HudRenderer)}; gezeichnet wird
 * nur noch gelesen. Kein Blockabruf, kein Raycast, keine Weltiteration - die beiden teueren Stellen,
 * {@link CrystalOwnership#snapshot()} und {@link ActionBroker#snapshot()}, kosten einmal pro Tick eine
 * kleine Kopie und werden im Renderer nie wieder angefasst.
 *
 * <p>Registrierung: {@code Hud.get().register(ProviDebugOverlay.INFO)} und, damit es nicht erst von
 * Hand eingeschaltet werden muss, {@code Hud.get().add(ProviDebugOverlay.INFO, 0, 0)} in
 * {@code ProviPvPAddon#onInitialize()}. Zugehoerige Gruppe: {@link PvpSessionStats#GROUP}.
 */
public final class ProviDebugOverlay extends HudElement {

    /** Aktive Module, die gleichzeitig genannt werden; der Rest nur als Anzahl. */
    private static final int MODULE_LINES = 3;
    /** Broker-Arten, die gleichzeitig genannt werden; der Rest nur als Anzahl. */
    private static final int CLAIM_LINES = 3;
    /** Obergrenze: bis zu 4 Modulzeilen plus die sechs festen Zeilen - mehr wird nie erzeugt. */
    private static final int MAX_LINES = 12;
    /** {@code values()} kopiert das Array bei jedem Aufruf - einmal holen, dann nur noch lesen. */
    private static final ActionBroker.ActionKind[] ACTION_KINDS = ActionBroker.ActionKind.values();
    private static final ScanBudget.ScanKind[] SCAN_KINDS = ScanBudget.ScanKind.values();

    /** Marker fuer "dieses Modul fuehrt den Zustand nicht". */
    private static final Field[] NO_FIELD = new Field[0];

    /** GLFW-Tastencode fuer D - {@link Keybind#fromKeys(int, int)} rechnet in GLFW-Koordinaten. */
    private static final int GLFW_KEY_D = 0x44;
    /** GLFW-Modifikator fuer die Umschaltaste. */
    private static final int GLFW_MOD_SHIFT = 0x0001;

    public static final HudElementInfo<ProviDebugOverlay> INFO = new HudElementInfo<>(
        PvpSessionStats.GROUP,
        "provi-debug-overlay",
        "ProviPvP Debug",
        "Liest den Kampf-Zustand: aktive Module und Aktion, Ziel und Reaktionszeit, gesendete Rotation mit GCD-Delta, Scan-Budget, Broker-Belegung, Crystal-Besitz, Sitzung. Aendert nichts am Kampf.",
        ProviDebugOverlay::new);

    private final SettingGroup sg = settings.getDefaultGroup();

    /**
     * Der Schalter im HUD-Editor. Er ist zugleich der Zustand, den der Keybind umlegt - zwei
     * Schalter fuer eine Sache wuerden auseinanderlaufen, sobald der Nutzer einen von beiden
     * benutzt und den anderen nicht.
     */
    private final Setting<Boolean> show = sg.add(new BoolSetting.Builder()
        .name("show")
        .description("Blendet das Panel aus. Auch per RSHIFT+D umschaltbar.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Keybind> bind = sg.add(new KeybindSetting.Builder()
        .name("bind")
        .description("Blendet das Panel ein und aus. Standard: RSHIFT+D.")
        .defaultValue(Keybind.fromKeys(GLFW_KEY_D, GLFW_MOD_SHIFT))
        .build()
    );

    private final Setting<Boolean> background = sg.add(new BoolSetting.Builder()
        .name("background")
        .description("Hintergrund hinter dem Panel - ohne ihn ist es auf hellem Himmel kaum lesbar.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> textColor = sg.add(new ColorSetting.Builder()
        .name("text-color")
        .defaultValue(new SettingColor(255, 255, 255))
        .build()
    );

    private final Setting<SettingColor> mutedColor = sg.add(new ColorSetting.Builder()
        .name("muted-color")
        .description("Zeilen, die nur Kontext liefern - kein Ziel, nichts belegt.")
        .defaultValue(new SettingColor(150, 150, 150))
        .build()
    );

    private final Setting<SettingColor> warnColor = sg.add(new ColorSetting.Builder()
        .name("warn-color")
        .description("Faerbt Werte, die Grim gar nicht erst wertet: GCD-Delta 0 oder groesser 5 Grad.")
        .defaultValue(new SettingColor(255, 90, 90))
        .build()
    );

    private final Setting<SettingColor> backgroundColor = sg.add(new ColorSetting.Builder()
        .name("background-color")
        .defaultValue(new SettingColor(25, 25, 25, 140))
        .build()
    );

    private final String[] lines = new String[MAX_LINES];
    private final Color[] colors = new Color[MAX_LINES];
    private int lineCount;

    /** Wird pro Tastendruck neu gesetzt; {@link Keybind#isPressed()} meldet den Zustand, nicht die Kante. */
    private boolean bindDown;

    // --- einmal pro Tick berechnet, im Renderer nur noch gelesen ---
    private final StringBuilder scratch = new StringBuilder();
    private final Map<Class<?>, Field[]> stateFields = new HashMap<>();

    private Object seenLevel;
    private float lastYaw = Float.NaN;
    private float lastPitch = Float.NaN;
    /** Letzte beobachtete Groesse des Crystal-Besitzes; {@code -1} heisst "noch nie gesehen". */
    private int lastOwnership = -1;
    /** Aus dem Besitzesfenster gefallene Eintraege - die CrystalOwnership selbst nicht mitzaehlt. */
    private int ownershipDropped;

    public ProviDebugOverlay() {
        super(INFO);
    }

    // ---------- Anzeige ----------

    /**
     * Sichtbarkeit umschalten. Heisst bewusst <i>nicht</i> {@code toggle()}: die Methode der Basis-
     * klasse schaltet das HUD-Element selbst ein und aus, das waere ein zweiter, anderer Schalter.
     */
    public void flip() {
        show.set(!show.get());
    }

    @Override
    public void tick(HudRenderer renderer) {
        // Keybind.isPressed() liefert den Tastenzustand, nicht die Flanke - ohne diese eine Zeile
        // wuerde der Schalter im Takt des gehaltenen Keys hin und her springen.
        boolean down = bind.get().isPressed();
        if (down && !bindDown) flip();
        bindDown = down;

        if (!show.get()) {
            lines[0] = "ProviPvP Debug - ausgeblendet (Standard: RSHIFT+D)";
            colors[0] = mutedColor.get();
            lineCount = 1;
            return;
        }

        refresh();
    }

    @Override
    public void render(HudRenderer renderer) {
        if (!show.get() && !isInEditor()) return;
        paint(renderer, this, lines, colors, lineCount, Hud.get().getTextScale(),
            isInEditor(), background.get(), backgroundColor.get());
    }

    /**
     * Gemeinsame Zeichenschicht beider Panels. Sie steht hier, weil dieses Element zuerst da war und
     * beide Panels exakt dieselben Zeilenarten, dieselbe Schriftgroesse und denselben Hintergrund
     * brauchen - zwei Kopien wuerden beim naechsten Farbsetting sofort auseinanderlaufen.
     *
     * <p>Misst erst, zeichnet dann den Hintergrund, zeichnet zuletzt den Text. In der anderen
     * Reihenfolge laege der Hintergrund ueber der Schrift.
     */
    static void paint(HudRenderer renderer, HudElement element, String[] lines, Color[] colors, int count,
                      double scale, boolean inEditor, boolean background, Color backgroundColor) {
        double lineHeight = renderer.textHeight(true, scale);
        double width = 0;
        for (int i = 0; i < count; i++) {
            width = Math.max(width, renderer.textWidth(lines[i], scale));
        }

        // Im Editor wird der Hintergrund immer gezeichnet: sonst waere ein Panel mit abgeschaltetem
        // Hintergrund dort nicht greifbar und damit nicht verschiebbar.
        double height = lineHeight * count;

        if (background || inEditor) renderer.quad(element.x, element.y, width, height, backgroundColor);

        double y = element.y;
        for (int i = 0; i < count; i++) {
            renderer.text(lines[i], element.x, y, colors[i], true, scale);
            y += lineHeight;
        }

        element.setSize(width, height);
    }

    // ---------- Der einmal-pro-Tick-Zustand ----------

    private void refresh() {
        lineCount = 0;
        Minecraft mc = Minecraft.getInstance();

        if (mc.player == null || mc.level == null) {
            seenLevel = null;
            forgetWorldState();
            put("keine Welt - Panel wartet", mutedColor.get());
            return;
        }

        // Der abgeleitete Zustand (letzte Rotation, Crystal-Besitz) gehoert zu einer Welt. Ohne diese
        // Trennung wuerde der erste Tick nach einem Dimensionstorch ein GCD-Delta gegen den Winkel der
        // alten Dimension rechnen und einen Pop zaehlen, den es nie gab.
        if (mc.level != seenLevel) {
            seenLevel = mc.level;
            forgetWorldState();
        }

        Module combat = showModules();
        showTarget();
        showRotation();
        showScanBudget(combat);
        showBroker();
        showCrystalOwnership(combat);
        showSession();
    }

    private void forgetWorldState() {
        lastYaw = Float.NaN;
        lastPitch = Float.NaN;
        lastOwnership = -1;
        ownershipDropped = 0;
    }

    /**
     * Aktive Module der eigenen Kategorie mit ihrer Aktion. Mehrere Module gleichzeitig sind genau der
     * Fall, den der Broker verhindern soll - deshalb werden sie alle genannt statt nur dem ersten
     * gefolgt.
     *
     * @return das erste aktive Modul mit Aktion, weil Scan-Budget und Crystal-Besitz daran haengen
     */
    private Module showModules() {
        Module combat = null;
        int total = 0;

        for (Module m : Modules.get().getAll()) {
            if (!m.isActive() || m.category != ProviPvPAddon.CATEGORY) continue;
            String info = m.getInfoString();
            if (info == null || info.isBlank()) continue;

            if (combat == null) combat = m;
            if (total < MODULE_LINES) {
                put((total == 0 ? "ProviPvP  " : "         ") + m.name + ": " + info, textColor.get());
            }
            total++;
        }

        if (total == 0) {
            put("ProviPvP  kein Modul aktiv", mutedColor.get());
        } else if (total > MODULE_LINES) {
            put(String.format("         (+%d weitere aktive Module)", total - MODULE_LINES), mutedColor.get());
        }

        return combat;
    }

    private void showTarget() {
        PvpSessionStats.Target t = PvpSessionStats.target();
        if (t == null) return;

        String reaction = t.reactionPending()
            ? "Reaktion " + t.ageTicks() + " Ticks offen"
            : "Reaktion erledigt";

        put(String.format("Ziel  %s  %.1f/%.0f hp  %.2f m  %s",
            t.name(), t.health(), t.maxHealth(), t.distance(), reaction), textColor.get());
    }

    /**
     * Was tatsaechlich gesendet wurde, nicht was die Kamera zeigt. Das Delta zum Vortick ist der
     * GCD-Beweis: Meteor quantisiert nicht, und Grim wertet Deltas von genau 0 Grad (DuplicateRotPlace)
     * sowie alles ueber 5 Grad gar nicht erst.
     */
    private void showRotation() {
        float yaw = Rotations.serverYaw;
        float pitch = Rotations.serverPitch;

        if (Float.isNaN(lastYaw) || (yaw == lastYaw && pitch == lastPitch)) {
            put(String.format("Rotation  yaw %.2f  pitch %.2f  (unveraendert)", yaw, pitch),
                mutedColor.get());
        } else {
            double dYaw = GcdRotator.wrapDegrees(yaw - lastYaw);
            double dPitch = pitch - lastPitch;
            boolean suspect = (dYaw == 0.0 && dPitch == 0.0)
                || Math.abs(dYaw) > 5.0 || Math.abs(dPitch) > 5.0;

            put(String.format("Rotation  yaw %.2f  pitch %.2f  dYaw %.3f  dPitch %.3f%s",
                yaw, pitch, dYaw, dPitch, suspect ? "  <- Grim wertet 0 und >5 nicht" : ""),
                suspect ? warnColor.get() : textColor.get());
        }

        lastYaw = yaw;
        lastPitch = pitch;
    }

    private void showScanBudget(Module combat) {
        Object state = stateOf(combat, ScanBudget.class);
        if (!(state instanceof ScanBudget budget)) return;

        scratch.setLength(0);
        scratch.append("Scan-Budget  ");
        for (int i = 0; i < SCAN_KINDS.length; i++) {
            if (i > 0) scratch.append("  ");
            ScanBudget.Stats s = budget.stats(SCAN_KINDS[i]);
            scratch.append(shortLabel(SCAN_KINDS[i]))
                .append(' ').append(s.allowed()).append('/').append(s.skipped());
        }
        put(scratch.toString(), textColor.get());
    }

    /** {@code erlaubt/verschluckt} - der zweite Zaehler ist die Zahl der ersparten Sweeps. */
    private static String shortLabel(ScanBudget.ScanKind kind) {
        return switch (kind) {
            case PER_TICK -> "tick";
            case NEARBY_BLOCK_COUNT -> "bloecke";
            case ANCHOR_MAINTENANCE -> "anker";
            case ANCHOR_EXPLOSION -> "anker-exp";
        };
    }

    private void showBroker() {
        ActionBroker broker = PvpServices.broker();

        scratch.setLength(0);
        int total = 0;
        int shown = 0;
        for (ActionBroker.ActionKind kind : ACTION_KINDS) {
            Object holder = broker.holder(kind);
            if (holder == null) continue;
            total++;
            if (shown++ >= CLAIM_LINES) continue;
            if (scratch.length() > 0) scratch.append("  ");
            scratch.append(kind.name()).append('=').append(ownerName(holder));
        }
        if (total == 0) return;

        if (total > CLAIM_LINES) scratch.append("  (+").append(total - CLAIM_LINES).append(')');

        PathLease path = broker.path();
        scratch.append("   Baritone ").append(path.isPaused() ? "angehalten (" + path.holders() + ")" : "frei");
        put(scratch.toString(), textColor.get());
    }

    private static String ownerName(Object owner) {
        return owner instanceof Module m ? m.name : String.valueOf(owner);
    }

    private void showCrystalOwnership(Module combat) {
        Object state = stateOf(combat, CrystalOwnership.class);
        if (!(state instanceof CrystalOwnership ownership)) return;

        int size = ownership.snapshot().size();
        if (lastOwnership >= 0 && lastOwnership > size) ownershipDropped += lastOwnership - size;
        lastOwnership = size;

        put(String.format("Crystals  %d eigene, %d aus dem Fenster gefallen", size, ownershipDropped),
            textColor.get());
    }

    private void showSession() {
        PvpSessionStats.Counters c = PvpSessionStats.counters();
        put(String.format("Sitzung  %d Kills  %d Tode  %.0f Schaden aus  %.0f ein  %d Crystals gepoppt",
            c.kills(), c.deaths(), c.damageDealt(), c.damageTaken(), c.crystalsPopped()), textColor.get());
    }

    // ---------- Zustand der Module ----------

    /**
     * Das erste Feld des Moduls vom gesuchten Typ. Bewusst nach <b>Typ</b> gesucht und nicht nach
     * Namen: der Zaehler gehoert dem Modul, und eine Umbenennung wuerde die Anzeige stumm schalten.
     *
     * <p>Das Ergebnis ist pro Typ gecacht, inklusive des negativen Falls - sonst wuerde das Panel
     * jedes Tick die komplette Feldliste des Moduls durchsuchen, nur um wieder nichts zu finden.
     */
    private Object stateOf(Module module, Class<?> type) {
        if (module == null) return null;

        Field[] cached = stateFields.get(type);
        if (cached == null) {
            Field found = null;
            for (Field f : module.getClass().getDeclaredFields()) {
                if (!type.isAssignableFrom(f.getType())) continue;
                try {
                    f.setAccessible(true);
                } catch (RuntimeException e) {
                    // Zugriff verweigert (Modulmodul-Abgrenzung): Zeile weglassen statt raten.
                    break;
                }
                found = f;
                break;
            }
            cached = found == null ? NO_FIELD : new Field[]{found};
            stateFields.put(type, cached);
        }

        if (cached.length == 0) return null;
        try {
            return cached[0].get(module);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private void put(String text, Color color) {
        if (lineCount >= MAX_LINES) return;
        lines[lineCount] = text;
        colors[lineCount] = color;
        lineCount++;
    }
}