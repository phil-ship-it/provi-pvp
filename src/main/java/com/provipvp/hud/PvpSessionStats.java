package com.provipvp.hud;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.entity.EntityDestroyEvent;
import meteordevelopment.meteorclient.events.entity.EntityRemovedEvent;
import meteordevelopment.meteorclient.events.entity.player.AttackEntityEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudGroup;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

/**
 * Sitzungszaehler fuer den Kampf: Kills, Tode, Schaden aus/ein, Serie, Crystals.
 *
 * <p><b>Rein lesend.</b> Das Element belegt nichts, fordert nichts an, haelt Baritone nicht fest und
 * fasst die Hotbar nicht an. Es schreibt genau eine Sache: seine eigenen Zaehler. Wer diese Werte
 * beim Testen sieht, sieht damit auch, dass das Overlay selbst nichts am Kampf veraendert.
 *
 * <p><b>Warum die Zahlen nur Naeherungen sind - und das nicht abstellen kann.</b> Meteor 26.2 hat
 * <i>kein</i> Schadens-, Treffer- oder Todes-Event. Es gibt nur EntityAdded/Removed/Destroyed, das
 * Angriffs-Event und den Tick. Also bleibt nichts anderes uebrig als der Vergleich der eigenen
 * Lebenspunkte von Tick zu Tick. Daraus folgt alles, was hier unscharf bleibt:
 *
 * <ul>
 *   <li><b>Schaden ist clientseitig zugeordnet.</b> Ein Lebenspunktabfall des naechsten Gegners wird
 *       uns gutgeschrieben, sobald wir ihn selbst angegriffen haben. Wer ihn wirklich getroffen hat,
 *       sieht der Client nicht - ein Crystal-Explosionsschaden landet zudem erst mehrere Ticks nach
 *       dem Platzieren. Fuer "habe ich in diesem Kampf ueberhaupt Schaden gemacht" reicht das, fuer
 *       eine Schadensbilanz pro Treffer nicht.</li>
 *   <li><b>Ein KILL ist ein Lebenspunkte-Nullpunkt.</b> Verschwindet der Gegner, ohne dass der
 *       Client 0 Lebenspunkte gesehen hat, zaehlt es nicht als Kill. Das ist Absicht: ein Gegner, der
 *       sich heilt oder das Feld verlaesst, soll keine Punkte schreiben.</li>
 *   <li><b>Tode sind exakt.</b> Eigene Lebenspunkte auf 0, mehr braucht der Client dafuer nicht.</li>
 *   <li><b>Crystals sind exakt.</b> Getroffen zaehlt das Angriffs-Event, gepoppt erst das
 *       Verschwinden der Entity - damit steht in der Zeile nicht "getroffen", was "gesprengt"
 *       behauptet haette.</li>
 * </ul>
 *
 * <p><b>Warum der Zaehler statisch ist.</b> Meteor ruft {@link HudElement#tick(HudRenderer)} nur fuer
 * gerade sichtbare Elemente auf; ein Element, das beim Rausschalten des HUDs aufhoert mitzuzaehlen,
 * zeigte beim Wiedereinschalten falsche Zahlen. Der Zaehler haengt darum nicht am Element, sondern an
 * einer statischen Instanz, die sich genau einmal am Event-Bus anmeldet. Damit zaehlt er weiter,
 * auch wenn gerade nichts von ihm zu sehen ist - und beim Entfernen des Elements bleibt kein
 * zweiter Zuhörer zurueck, der dieselben Ereignisse ein zweites Mal verbucht.
 *
 * <p>Registrierung: {@code Hud.get().register(PvpSessionStats.INFO)} und, damit es sofort
 * sichtbar ist, {@code Hud.get().add(PvpSessionStats.INFO, 0, 0)} in
 * {@code ProviPvPAddon#onInitialize()}.
 */
public final class PvpSessionStats extends HudElement {

    /** Eigene HUD-Gruppe, damit beide Panels nebeneinander in einem Reiter stehen. */
    public static final HudGroup GROUP = new HudGroup("ProviPvP");

    public static final HudElementInfo<PvpSessionStats> INFO = new HudElementInfo<>(GROUP,
        "provi-session-stats",
        "ProviPvP Session",
        "Kills, Tode, Schaden und Crystals der aktuellen Sitzung. Rein lesend; die Schadenszuordnung ist clientseitig und deshalb eine Naeherung (siehe Quelltext).",
        PvpSessionStats::new);

    private static final int MAX_LINES = 4;

    /** Der Zaehler. Statisch, damit er unabhaengig von der Sichtbarkeit weiterlaeuft. */
    private static final Session SESSION = new Session();

    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Boolean> background = sg.add(new BoolSetting.Builder()
        .name("background")
        .description("Hintergrund hinter den Zahlen - ohne ihn ist das Panel auf hellem Himmel kaum lesbar.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> textColor = sg.add(new ColorSetting.Builder()
        .name("text-color")
        .description("Farbe der Zahlen.")
        .defaultValue(new SettingColor(255, 255, 255))
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

    public PvpSessionStats() {
        super(INFO);
    }

    // ---------- Was ProviDebugOverlay liest ----------

    /** Kills, Tode, Schaden, Serie, Crystal-Treffer und -Pops - allesamt kumuliert fuer die Sitzung. */
    public record Counters(int kills, int deaths, double damageDealt, double damageTaken,
                           int streak, int bestStreak, int crystalsHit, int crystalsPopped) {
    }

    /**
     * Der naechste lebende Gegner, einmal pro Tick aufgeloest. Bewusst ein Wertsnapshot und keine
     * Entity: der Renderer darf nie auf eine Entity greifen, die seit drei Ticks aus der Welt ist.
     *
     * @param ageTicks            Ticks seit dem Erfassen dieses Ziels
     * @param reactionPending     ob noch kein Schlag auf dieses Ziel fiel
     * @param attacked            ob ueberhaupt schon geschlagen wurde
     */
    public record Target(String name, float health, float maxHealth, float distance,
                         int ageTicks, boolean reactionPending, boolean attacked) {
    }

    /** @return die Zaehler; pro Tick neu gebildet, damit beide Panels denselben Stand zeigen */
    public static Counters counters() {
        return SESSION.counters();
    }

    /** @return der Ziel-Snapshot des letzten Ticks, oder {@code null} wenn gerade keiner im Bild ist */
    public static Target target() {
        return SESSION.target();
    }

    // ---------- Zeichnen ----------

    @Override
    public void render(HudRenderer renderer) {
        ProviDebugOverlay.paint(renderer, this, lines, colors, lineCount,
            Hud.get().getTextScale(), isInEditor(), background.get(), backgroundColor.get());
    }

    @Override
    public void tick(HudRenderer renderer) {
        Counters c = counters();
        lineCount = 0;

        put(String.format("Kills %d   Tode %d   Serie %d (best %d)",
            c.kills(), c.deaths(), c.streak(), c.bestStreak()), textColor.get());
        put(String.format("Schaden aus %.0f   ein %.0f", c.damageDealt(), c.damageTaken()), textColor.get());
        put(String.format("Crystals getroffen %d   gepoppt %d", c.crystalsHit(), c.crystalsPopped()),
            textColor.get());

        Target t = target();
        if (t != null) {
            put(String.format("Ziel %s  %.1f/%.0f hp  %.2f m%s",
                t.name(), t.health(), t.maxHealth(), t.distance(),
                t.reactionPending() ? "  Reaktion offen" : ""), textColor.get());
        }
    }

    private void put(String text, Color color) {
        if (lineCount >= MAX_LINES) return;
        lines[lineCount] = text;
        colors[lineCount] = color;
        lineCount++;
    }

    // ---------- Der Zaehler ----------

    /**
     * Der Zaehler selbst. Ein einziges Objekt, statisch, mit genau einer Anmeldung am Event-Bus -
     * siehe die Klassen-Dokumentation: alles andere ergaebe entweder doppelte Buchungen oder
     * Zaehlstaende, die mit der Sichtbarkeit des HUDs stehen und fallen.
     */
    private static final class Session {

        /**
         * Wie lange nach dem Erfassen eines Ziels noch kein Schlag liegen darf, bevor die Zeile
         * "Reaktion offen" wieder verschwindet. 20 Ticks sind eine Sekunde und decken das
         * Reaktionsfenster des Humanisierungsprofils (Standard 3-9 Ticks, einstellbar bis 25)
         * ab, ohne eine haengende Zielerfassung endlos als "reagierend" auszugeben.
         */
        private static final int REACTION_WINDOW_TICKS = 20;

        /**
         * Wie lange ein eigener Angriff als "ich war im Kampf" gilt. Ohne dieses Gedächtnis würde
         * jeder Lebenspunkteverlust eines anderen Spielers in der Nähe dem Bot gutgeschrieben.
         */
        private static final int COMBAT_MEMORY_TICKS = 12;

        private int kills;
        private int deaths;
        private double damageDealt;
        private double damageTaken;
        private int streak;
        private int bestStreak;
        private int crystalsHit;
        private int crystalsPopped;

        private Object world;
        private int tick;
        private float selfHealth = Float.NaN;

        private UUID targetId;
        private Target target;
        private float lastTargetHealth;
        private boolean targetAttacked;
        private int targetAge;
        private int lastCombatTick = Integer.MIN_VALUE;

        /** Zuletzt getroffene Crystal-Entity-IDs; {@link #dropHit(int)} verkürzt die Liste. */
        private final int[] hits = new int[32];
        private int hitCount;

        Session() {
            MeteorClient.EVENT_BUS.subscribe(this);
        }

        Counters counters() {
            return new Counters(kills, deaths, damageDealt, damageTaken,
                streak, bestStreak, crystalsHit, crystalsPopped);
        }

        Target target() {
            return target;
        }

        @EventHandler
        private void onTick(TickEvent.Post event) {
            tick++;
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer self = mc.player;
            ClientLevel level = mc.level;

            if (self == null || level == null) {
                // Hauptmenue oder Verbindungsabbau: nichts zaehlen, aber die letzte Health-Baseline
                // verwerfen, sonst waere der erste Tick der naechsten Welt ein Schein-Schaden.
                selfHealth = Float.NaN;
                target = null;
                targetId = null;
                targetAge = 0;
                targetAttacked = false;
                return;
            }

            if (level != world) {
                world = level;
                reset();
            }

            sampleSelfHealth(self);
            sampleTarget(self, level);
        }

        @EventHandler
        private void onAttack(AttackEntityEvent event) {
            Entity entity = event.entity;
            if (entity == null) return;

            lastCombatTick = tick;

            if (entity instanceof EndCrystal) {
                crystalsHit++;
                rememberHit(entity.getId());
            } else if (targetId != null && targetId.equals(entity.getUUID())) {
                targetAttacked = true;
            }
        }

        @EventHandler
        private void onEntityRemoved(EntityRemovedEvent event) {
            countPop(event.entity);
        }

        @EventHandler
        private void onEntityDestroyed(EntityDestroyEvent event) {
            // Beide Ereignisse koennen fuer dieselbe Entity feuern; dropHit() entfernt den Eintrag,
            // also wird derselbe Pop nicht zweimal gezaehlt.
            countPop(event.entity);
        }

        @EventHandler
        private void onGameLeft(GameLeftEvent event) {
            reset();
            selfHealth = Float.NaN;
            world = null;
            target = null;
            targetId = null;
        }

        /** Eine neue Sitzung ist eine andere Sitzung: alles auf null, inklusive der Beobachtung. */
        private void reset() {
            kills = 0;
            deaths = 0;
            damageDealt = 0;
            damageTaken = 0;
            streak = 0;
            bestStreak = 0;
            crystalsHit = 0;
            crystalsPopped = 0;
            target = null;
            targetId = null;
            targetAge = 0;
            targetAttacked = false;
            lastTargetHealth = 0;
            lastCombatTick = Integer.MIN_VALUE;
            hitCount = 0;
        }

        private void sampleSelfHealth(LocalPlayer self) {
            float hp = self.getHealth();
            if (!Float.isNaN(selfHealth)) {
                float lost = selfHealth - hp;
                // Regeneration ist ein Anstieg, kein Schaden; die Schwelle filtert das Zappeln der
                // angezeigten Lebenspunkte bei serverseitigem Nachziehen heraus.
                if (lost > 0.01f) damageTaken += lost;
                // Nur der Uebergang zaehlt: selfHealth ist hier noch der Wert des Vorticks, sonst
                // wuerde jeder der rund 20 Ticks im Respawn-Fenster einen weiteren Tod buchen.
                if (hp <= 0.01f && selfHealth > 0.01f) {
                    deaths++;
                    streak = 0;
                }
            }
            selfHealth = hp;
        }

        private void sampleTarget(LocalPlayer self, ClientLevel level) {
            Player nearest = nearestOpponent(self, level);

            if (nearest == null) {
                target = null;
                targetId = null;
                targetAge = 0;
                targetAttacked = false;
                return;
            }

            if (nearest.getUUID().equals(targetId)) {
                targetAge++;
            } else {
                targetId = nearest.getUUID();
                targetAge = 0;
                targetAttacked = false;
                lastTargetHealth = nearest.getHealth();
            }

            float now = nearest.getHealth();
            if (fighting() && now < lastTargetHealth - 0.01f) {
                damageDealt += lastTargetHealth - now;
                if (now <= 0.01f) {
                    kills++;
                    streak++;
                    bestStreak = Math.max(bestStreak, streak);
                }
            }
            lastTargetHealth = now;

            target = new Target(nearest.getName().getString(), now, nearest.getMaxHealth(),
                nearest.distanceTo(self), targetAge,
                targetAge < REACTION_WINDOW_TICKS && !targetAttacked, targetAttacked);
        }

        private Player nearestOpponent(LocalPlayer self, ClientLevel level) {
            Player best = null;
            float bestDistance = Float.MAX_VALUE;
            for (Player p : level.players()) {
                if (p == self || !p.isAlive()) continue;
                float distance = p.distanceTo(self);
                if (distance >= bestDistance) continue;
                bestDistance = distance;
                best = p;
            }
            return best;
        }

        /**
         * Waren wir in den letzten {@link #COMBAT_MEMORY_TICKS} Ticks selbst am Kampf? Ohne diese
         * Bedingung wuerde jedes Herzschlagen eines Fremden in Sichtweite als eigener Schaden
         * verbucht - das wuerde die Zeile auf jedem Server falsch aussehen lassen.
         */
        private boolean fighting() {
            return lastCombatTick != Integer.MIN_VALUE && tick - lastCombatTick <= COMBAT_MEMORY_TICKS;
        }

        private void countPop(Entity entity) {
            if (entity instanceof EndCrystal && dropHit(entity.getId())) crystalsPopped++;
        }

        private void rememberHit(int id) {
            if (hitCount == hits.length) {
                // Voller Puffer: das aelteste Trefferpaar vergessen. 32 Hits ohne Pop kommen nicht
                // vor, und ein Verlust ist hier folgenlos - nur ein Pop ginge verloren, kein Fehlzaehler.
                System.arraycopy(hits, 1, hits, 0, hits.length - 1);
                hitCount--;
            }
            hits[hitCount++] = id;
        }

        private boolean dropHit(int id) {
            for (int i = 0; i < hitCount; i++) {
                if (hits[i] != id) continue;
                System.arraycopy(hits, i + 1, hits, i, hitCount - i - 1);
                hitCount--;
                return true;
            }
            return false;
        }
    }
}