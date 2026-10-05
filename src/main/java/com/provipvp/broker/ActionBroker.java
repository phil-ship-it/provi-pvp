package com.provipvp.broker;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Pro-Tick-Belegung aller Kampfhandlungen. Genau ein Besitzer pro {@link ActionKind} je Tick.
 *
 * <p><b>Warum es das gibt.</b> Bis hierhin hat jedes Modul seine eigene Logik: eigene Hotbar-Reserve,
 * eigene Rotations-Prioritaet, eigenes Baritone-Anhalten. Sobald zwei davon im selben Tick handeln
 * wollen, gibt es drei Probleme gleichzeitig, und keines davon ist sichtbar:
 *
 * <ul>
 *   <li><b>Hotbar-Kollision.</b> Beide greifen auf denselben Combat-Slot zu; einer davon kommt
 *       unterm Slottausch heraus und haelt danach das falsche Item.</li>
 *   <li><b>Abhaengigkeit.</b> Eine Platzierung, die von einem ausstehenden Item-Swap abhaengt, wird
 *       verworfen, der Slot bleibt aber belegt.</li>
 *   <li><b>Anti-Cheat.</b> Zwei Aktionen in einem Movement-Paket sind genau das, was Grim unter
 *       {@code MultiPlace}/{@code MultiActions} fuehrt.</li>
 * </ul>
 *
 * <p>Die Belegung macht daraus eine Entscheidung mit benanntem Gewinner statt eines Rennlaufs.
 *
 * <p><b>Preemption-Regel.</b> Ein hoeher priorisiertes Modul darf eine niedrigere Belegung
 * <i>verdrängen, solange diese noch nicht gefeuert hat</i>. Hat sie schon gefeuert, wird abgelehnt:
 * das Paket ist unterwegs, ein Verdrängen koennte es nicht mehr zurücknehmen und wuerde nur die
 * Ausfuehrungsreihenfolge im selben Tick verschieben, ohne am Ergebnis etwas zu aendern. Damit bleibt
 * das Verhalten eines Ticks reparierbar - ein hoeher priorisiertes Modul gewinnt ab dem naechsten Tick.
 *
 * <p>Ohne {@code mc.*}: der Broker kennt keine Entity, keinen Slot und keine Welt. Er entscheidet
 * ausschliesslich, wer in diesem Tick an welcher Stelle dran ist.
 */
public final class ActionBroker {

    /** Was in einem Tick konkurrieren kann. {@code SWAP} ist bewusst eine eigene Art: ein
     *  Slotwechsel muss VOR der Aktion kommen, nicht gleichzeitig mit ihr. */
    public enum ActionKind {
        MELEE,
        CRYSTAL,
        ANCHOR,
        BED,
        PEARL,
        BLOCK,
        PROJECTILE,
        USE_ITEM,
        SHIELD,
        SWAP
    }

    private record Claim(Object owner, int priority, boolean spent) {
        Claim spend() {
            return new Claim(owner, priority, true);
        }
    }


    private final Map<ActionKind, Claim> claims = new HashMap<>();
    private final Map<Object, Boolean> spentThisTick = new IdentityHashMap<>();
    private final PathLease pathLease = new PathLease();

    public PathLease path() {
        return pathLease;
    }

    /**
     * Fordert eine Handlung an.
     *
     * @param owner    das anfragende Modul; Identitaet, nicht Gleichheit - jedes Modul ist eine eigene
     *                 Instanz und wird ueber {@code ==} verglichen
     * @param priority hoeher gewinnt. Die bestehende Rotationsskala (Perle 70, Misc, Look) gilt auch
     *                 hier, damit die Zahlen nicht an zwei Stellen auseinanderlaufen
     * @return true, wenn die Handlung in diesem Tick gehoert; false, wenn ein anderer sie bereits hat
     */
    public boolean claim(Object owner, ActionKind kind, int priority) {
        if (owner == null) return false;
        if (Boolean.TRUE.equals(spentThisTick.get(owner))) return false; // eigenes Budget schon verbraucht

        Claim current = claims.get(kind);
        if (current == null) {
            claims.put(kind, new Claim(owner, priority, false));
            return true;
        }
        if (current.owner() == owner) return true; // dieselbe Handlung mehrfach anfragen ist erlaubt

        if (priority > current.priority() && !current.spent()) {
            // Der Aufrufer uebernimmt die Belegung. Ein blosses Anheben der Prioritaet beim alten
            // Besitzer waere eine Umbenennung ohne Uebernahme - der Testdeck hielt genau das fest.
            claims.put(kind, new Claim(owner, priority, false));
            return true;
        }
        return false;
    }

    /**
     * Meldet, dass die belegte Handlung tatsaechlich ausgefuehrt wurde. Erst danach verdraengt ein
     * hoeher priorisiertes Modul nicht mehr - vorher schon, weil dann noch nichts unterwegs ist.
     */
    public void spend(Object owner, ActionKind kind) {
        Claim current = claims.get(kind);
        if (current == null || current.owner() != owner) return;
        claims.put(kind, current.spend());
        spentThisTick.put(owner, Boolean.TRUE);
    }

    /** Gibt alle Belegungen eines Moduls frei, z.B. wenn es deaktiviert wird oder sein Ziel verliert. */
    public void release(Object owner) {
        claims.values().removeIf(c -> c.owner() == owner);
    }

    /** Am Tick-Anfang. Ohne Reset wuerde eine einmal belegte Art den ganzen Kampf blockieren. */
    public void tick() {
        claims.clear();
        spentThisTick.clear();
    }

    /** @return der aktuelle Besitzer der Handlung, oder {@code null} wenn frei */
    public Object holder(ActionKind kind) {
        Claim c = claims.get(kind);
        return c == null ? null : c.owner();
    }

    /** @return true, wenn {@code owner} in diesem Tick bereits gehandelt hat */
    public boolean hasSpent(Object owner) {
        return Boolean.TRUE.equals(spentThisTick.get(owner));
    }

    /** Nur fuer Diagnose/Overlay - liefert die Belegung als {@code Art=Besitzer}-Paare. */
    public Map<ActionKind, String> snapshot() {
        Map<ActionKind, String> out = new java.util.LinkedHashMap<>();
        claims.forEach((k, c) -> out.put(k, String.valueOf(c.owner())));
        return out;
    }
}
