package com.provipvp.core;

import com.provipvp.core.events.TargetChangeEvent;
import meteordevelopment.orbit.IEventBus;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

/** Isolierte Zielwahl, aus {@code GodmodePvP.handleTargeting}/{@code findTarget} herausgeloest.
 *
 *  Bewusst frei von jedem {@code mc.*}-Zugriff: Kandidaten kommen ueber {@link CandidateSource},
 *  die Gueltigkeit ueber einen injizierten {@link Predicate} und die Distanz ueber
 *  {@link Player#distanceToSqr}. Dadurch ist die Auswahlentscheidung ohne laufenden Client
 *  testbar (siehe {@link Sticky} und {@link #pickBest}) und die Modul-spezifischen Anteile
 *  (Creative-/FakePlayer-/Mob-Filter, Smart-Weighting) bleiben beim Modul. */
public final class TargetSelector {

    /** Liefert die Kandidaten fuer einen Aufruf. Das Modul kapselt hier {@code mc.level.players()}
     *  bzw. {@code mc.level.getEntitiesOfClass(...)}, damit diese Klasse den Client nicht kennt. */
    @FunctionalInterface
    public interface CandidateSource {
        List<LivingEntity> candidates(Player self);
    }

    /** Entity-neutraler Kern der Zielverfolgung: haelt das aktuelle Ziel und meldet echte Wechsel.
     *  Generisch, damit die Wechsel-/Halt-Semantik mit einfachen Werten testbar ist, ohne eine
     *  Minecraft-Entity zu erzeugen. */
    public static final class Sticky<T> {
        private final BiConsumer<T, T> onChange;
        private T current;

        public Sticky(BiConsumer<T, T> onChange) {
            this.onChange = onChange;
        }

        public T current() {
            return current;
        }

        /** Uebernimmt {@code next} als Ziel. Der Callback feuert ausschliesslich bei einem echten
         *  Instanzwechsel ({@code previous == next} ist kein Wechsel) - nicht bei jeder Auswahl. */
        public void update(T next) {
            T previous = current;
            if (previous == next) return;
            current = next;
            onChange.accept(previous, next);
        }

        /** Loest das Ziel. Meldet genau dann einen Wechsel, wenn es zuvor ein Ziel gab. */
        public void reset() {
            update(null);
        }
    }

    private final Predicate<LivingEntity> filter;
    private final double followRangeSq;
    private final CandidateSource candidates;
    private final Sticky<LivingEntity> sticky;

    public TargetSelector(IEventBus bus, Predicate<LivingEntity> filter, double followRange,
                          CandidateSource candidates) {
        this.filter = filter;
        this.followRangeSq = followRange * followRange;
        this.candidates = candidates;
        // Im Konstruktor, nicht als Feld-Initialisierer: der Lambda-koerper laeuft erst beim ersten
        // update(), braucht aber das final gesetzte bus.
        this.sticky = new Sticky<>((from, to) -> bus.post(new TargetChangeEvent(from, to)));
    }

    /** Das aktuell verfolgte Ziel, oder null. */
    public LivingEntity current() {
        return sticky.current();
    }

    /** Zustand leeren (z.B. bei Weltwechsel) - postet ein {@code TargetChangeEvent}, wenn zuvor
     *  ein Ziel gehalten wurde. */
    public void reset() {
        sticky.reset();
    }

    /** Waehlt bzw. haelt das Ziel fuer diesen Tick.
     *
     *  <p><b>Halt-Semantik (portiert aus {@code GodmodePvP.engaged}):</b> ein Ziel, das einmal
     *  innerhalb der Follow-Range lag, wird danach ohne Neuauswahl weiter verfolgt, bis es die
     *  Follow-Range verlaesst, stirbt oder vom Filter faellt. Ohne das riss eine Crystal-Explosion
     *  das Ziel mitten im Gefecht ab, sobald der Knockback kurzzeitig aus der Range schiesst.
     *
     *  <p>Der Aufrufer reicht sein bisheriges Ziel als {@code previous} ein (typisch
     *  {@code selector.current()}); faellt er auf {@code null} zurueck, gilt das zuletzt von hier
     *  gehaltene Ziel. Genau bei einem echten Instanzwechsel wird {@link TargetChangeEvent} gepostet.
     *
     *  @return das gehaltene oder neu gewaehlte Ziel, oder null wenn es keins gibt */
    public LivingEntity select(Player self, LivingEntity previous) {
        LivingEntity keep = previous != null ? previous : sticky.current();
        if (keep != null && holdable(self, keep)) {
            sticky.update(keep);
            return keep;
        }

        LivingEntity best = pickBest(candidates.candidates(self),
            e -> acquirable(self, e), e -> self.distanceToSqr(e));
        sticky.update(best);
        return best;
    }

    /** Halten: das Ziel ist noch zulaessig und noch innerhalb der Follow-Range (Rand inklusive,
     *  genau wie die alte Engaged-Suche in {@code findTarget}). */
    private boolean holdable(Player self, LivingEntity e) {
        return eligible(self, e) && self.distanceToSqr(e) <= followRangeSq;
    }

    /** Neu waehlen: zulaessig und strikt innerhalb der Follow-Range (wie dort das
     *  {@code d >= followRangeSq -> continue}). */
    private boolean acquirable(Player self, LivingEntity e) {
        return eligible(self, e) && self.distanceToSqr(e) < followRangeSq;
    }

    private boolean eligible(Player self, LivingEntity e) {
        return e != null && e != self && e.isAlive() && !e.isSpectator() && filter.test(e);
    }

    /** Waehlt den zulaessigen Kandidaten mit der kleinsten Distanz; null wenn keiner zulaessig ist.
     *  Rein und entity-neutral, damit die Filter-/Reihenfolgeregel ohne Client testbar ist. */
    public static <T> T pickBest(List<T> candidates, Predicate<T> allowed, ToDoubleFunction<T> distanceSq) {
        T best = null;
        double bestDist = Double.MAX_VALUE;
        for (T candidate : candidates) {
            if (!allowed.test(candidate)) continue;
            double d = distanceSq.applyAsDouble(candidate);
            if (d >= bestDist) continue;
            bestDist = d;
            best = candidate;
        }
        return best;
    }
}
