package com.provipvp.crystal;

import java.util.List;

/** Entscheidet, ob das aktuell gehaltene Werkzeug einen End Crystal ueberhaupt beschädigen kann, und
 *  welcher Hotbar-Slot dafuer noetig waere.
 *
 *  <p>Warum das ueberhaupt eine eigene Entscheidung ist: eine Crystal-Explosion kostet einen Angriffs-
 *  Cooldown des Gegners und ein Item. Trifft der Schlag aus dem Grund, dass das gehaltene Werkzeug den
 *  Crystal gar nicht beschaedigt, ist beides fuer nichts verbraucht — der Bot steht mit leerem
 *  Cooldown neben einem intakten Crystal und hat sich dabei sichtbar positioniert. Die Auswahl der
 *  Reihenfolge im Modul (Schlag -> Rotation -> Slot-Wechsel) laeuft genau in diese Falle hinein,
 *  weil dort geprueft wird, OB ueberhaupt ein Crystal da ist, nicht ob das aktuelle Werkzeug ihn
 *  erreicht.
 *
 *  <p><b>Die Werkzeug-Regel ist bewusst injizierbar</b> ({@link DamageRule}) und nicht als Konstante
 *  fest verdrahtet: welche Items einen Crystal beschaedigen, ist server-/versionabhaengig (Java und
 *  Bedrock verhalten sich hier unterschiedlich, und Anti-Cheat-Server pflegen das gelegentlich nach).
 *  Eine hier festgeschriebene Liste waere eine Behauptung ohne Quelle und wuerde beim ersten Server,
 *  der anders tickt, still den falschen Slot waehlen. Das Modul liefert die Regel aus seiner
 *  Item-Zuordnung; diese Klasse besitzt die Entscheidung, die Regeln nicht.
 *
 *  <p>Kein {@code mc.*}: Das Werkzeug kommt als {@link Tool}-Enum, die Hotbar als Liste davon. Damit
 *  ist die Slotlogik direkt testbar, ohne einen laufenden Client.
 */
public final class CrystalToolPolicy {

    /**
     * Werkzeug-Klassen, die das Modul einer {@link DamageRule} zuordnet. Bewusst GROSSZUEBIG und ohne
     * Minecraft-Bezug: {@link #OTHER} faengt alles auf, damit ein neues Item keine {@code switch}-Luecke
     * bekommt und stillschweigend als brauchbar durchgeht.
     */
    public enum Tool {
        /** Leere Hand / Offhand ohne Werkzeug. */
        HAND,
        /** Schwert. */
        SWORD,
        /** Axt. */
        AXE,
        /** Spitzhacke. */
        PICKAXE,
        /** Schaufel. */
        SHOVEL,
        /** Alles, was die Regel nicht kennt. */
        OTHER
    }

    /** Beantwortet die Vanilla-Frage "beschaedigt dieses Werkzeug einen End Crystal?". */
    @FunctionalInterface
    public interface DamageRule {
        boolean breaksCrystal(Tool tool);
    }

    /**
     * Voreinstellung fuer die klassische Java-Regel: jedes haltbare Werkzeug mit eigener Treffer-
     *Flaeche greift den Crystal an, die leere Hand nicht. Bewusst konservativ — die leere Hand als
     * "koennte gehen" zu behandeln waere ein Optimismus, den das Modul mit einem vergeichten
     * Angriffs-Cooldown bezahlen wuerde.
     */
    public static final DamageRule DURABLE_TOOLS = tool ->
        tool == Tool.SWORD || tool == Tool.AXE || tool == Tool.PICKAXE;

    /** "Kein Wechsel noetig" bzw. "kein passender Slot" — Hotbar-Slots sind 0..8. */
    public static final int NO_SLOT = -1;

    /**
     * @param canAttack     ein Angriff darf ueberhaupt stattfinden
     * @param switchToSlot   Ziel-Slot fuer den Slot-Wechsel, {@link #NO_SLOT} wenn keiner noetig ist
     * @param held          das gehaltene Werkzeug
     * @param needed        das Werkzeug, das den Crystal beschaedigen wuerde, {@code null} wenn
     *                      nichts in der Hotbar es kann
     */
    public record Verdict(boolean canAttack, int switchToSlot, Tool held, Tool needed) {
        /** Muss vor dem Schlag der Slot gewechselt werden. */
        public boolean needsSwitch() {
            return switchToSlot != NO_SLOT;
        }
    }

    private final DamageRule rule;

    public CrystalToolPolicy() {
        this(DURABLE_TOOLS);
    }

    public CrystalToolPolicy(DamageRule rule) {
        this.rule = rule;
    }

    /**
     * Kurzform fuer den haeufigsten Fall: das gehaltene Werkzeug wird ueber {@code heldSlot} aus der
     * Hotbar gelesen, {@code -1} steht fuer "nichts in der Hand".
     *
     * @param heldSlot  Index in {@code hotbar}, oder {@link #NO_SLOT} fuer leere Hand
     * @param hotbar    Slot 0..8, {@code null} = leerer Slot
     * @return {@link Verdict}: bei brauchbarem Werkzeug in der Hand {@code switchToSlot == NO_SLOT},
     *         sonst der erste passende Slot, sonst {@code canAttack == false}
     */
    public Verdict evaluate(int heldSlot, List<Tool> hotbar) {
        Tool held = heldSlot >= 0 && heldSlot < hotbar.size() ? hotbar.get(heldSlot) : null;
        if (held == null) held = Tool.HAND; // leere Hand und Out-of-Range-Index sind dieselbe Lage
        return evaluate(held, hotbar);
    }

    /**
     * @param held   das aktuell gehaltene Werkzeug, {@code null} = leere Hand
     * @param hotbar Slot 0..8, {@code null} = leerer Slot
     * @return siehe {@link #evaluate(int, List)}
     */
    public Verdict evaluate(Tool held, List<Tool> hotbar) {
        Tool inHand = held == null ? Tool.HAND : held;
        if (rule.breaksCrystal(inHand)) {
            // Der gehaltene Slot bleibt unangetastet: ein Wechsel wuerde den Angriffs-Cooldown des
            // Crystal-Kampfes verschwenden, obwohl bereits geschlagen werden koennte.
            return new Verdict(true, NO_SLOT, inHand, inHand);
        }

        // Erster passender Slot, nicht der "beste" — die Reihenfolge der Hotbar ist die einzige
        // Information, die es hier gibt, und ein zufaelliger Wechsel waere schlimmer als der
        // naheliegendste.
        for (int slot = 0; slot < hotbar.size(); slot++) {
            Tool candidate = hotbar.get(slot);
            if (candidate != null && rule.breaksCrystal(candidate)) {
                return new Verdict(true, slot, inHand, candidate);
            }
        }

        // Nichts Brauchbares in der Hotbar: nicht schwingen. Der Angriffs-Cooldown waere fuer einen
        // Schlag verbraucht, der nichts beschaedigt — und der Bot saehe aus, als wuerde er den
        // Crystal "verfehlen".
        return new Verdict(false, NO_SLOT, inHand, null);
    }
}
