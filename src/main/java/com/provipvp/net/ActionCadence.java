package com.provipvp.net;

/**
 * Das Aktions-Ledger eines Ticks: erlaubt hoechstens eine Platzierung, einen Angriff, einen Swing
 * und einen Blick pro Bewegungspaket — und verhindert, dass sich ein Slot-Wechsel mit einer Aktion
 * verschachtelt.
 *
 * <p><b>Warum ein explizites Ledger statt Zaehler im Modul:</b> die drei Grim-Checks, gegen die
 * hier gebaut wird, zaehlen nicht Aktionen ueber den ganzen Kampf, sondern innerhalb <i>eines</i>
 * Bewegungspakets:
 * <ul>
 *   <li>{@code MultiPlace} — mehr als eine Blockplatzierung zwischen zwei Movement-Paketen. Der
 *       Bot platziert an Crystal-, Anchor-, Bett- und Surround-Stellen leicht viermal pro Tick, weil
 *       jeder dieser Zweige unabhaengig voneinander feuert; aus der Bot-Sicht sind das vier
 *       geplante Aktionen, fuer Grim sind es vier gleichzeitige Platzierungen.</li>
 *   <li>{@code DuplicateRotLook} — mehr als eine Blickrichtung pro Bewegungspaket.</li>
 *   <li>{@code MultiActionsA} / {@code PacketOrderE/F/I} — eine Aktion waehrend {@code isUsingItem},
 *       bzw. ein Slot-Wechsel <i>zwischen</i> zwei Aktionen.</li>
 * </ul>
 * Ein Ledger an einer Stelle macht die Buchhaltung pruefbar, statt sie ueber zehn Aufrufstellen im
 * Modul zu verteilen.
 *
 * <p><b>Warum die Reihenfolge beim Slot zaehlt:</b> Grim sieht nicht, <i>dass</i> der Bot den Slot
 * wechselt, sondern dass zwischen zwei Aktionen ein Slot-Paket liegt. Ein Slot-Wechsel muss also
 * <b>vor</b> der Aktion stehen, die er vorbereitet — nicht zwischen zwei Aktionen und nicht danach,
 * gefolgt von einer weiteren Aktion. Ein Slot-Wechsel ganz am Tick-Ende (das Ruecksetzen des
 * Combat-Slots nach dem letzten Schlag) ist erlaubt: da folgt keine Aktion mehr, es ist kein
 * Verschachteln. Genau diese Unterscheidung ist der Grund, warum die Regel hier ueber die
 * nachfolgende Aktion laeuft und nicht ueber den Slot-Wechsel selbst.
 *
 * <p>Zustandsautomat ueber {@code (Aktion, isUsingItem, Tick)} — kein {@code mc.*}, keine Settings
 * (R1). Der Integrations-Agent liest {@code player.isUsingItem()} und ruft {@link #onTick()} einmal
 * pro {@code TickEvent.Pre}.
 */
public final class ActionCadence {

    /** Die Aktionen, die das Ledger zaehlt. */
    public enum Action {
        /** Platzierung eines Blocks (Crystal, Anchor, Bett, Obsidian, Web). */
        PLACE,
        /** Angriff auf Entity (Crystal, Mob, Spieler). */
        ATTACK,
        /** Rechtsklick auf einen Block, der keine Platzierung ausloest (z.B. Schild, Interact). */
        RIGHT_CLICK,
        /** Blockabbau starten. */
        DIG,
        /** Reiner Armschwung, ohne Interact-Paket. */
        SWING,
        /** Blickrichtung im Bewegungspaket. */
        LOOK
    }

    /** Welche Regel eine Aktion abgelehnt hat. */
    public enum Refusal {
        /** Erlaubt. */
        NONE,
        /** D19: Es wird bereits ein Item benutzt — waehrenddessen trifft weder Angriff noch Platzierung durch. */
        USING_ITEM,
        /** D19: Zwischen der vorherigen Aktion und dieser liegt ein Slot-Wechsel. Der Slot muss vorher gewechselt werden. */
        SLOT_ORDER,
        /** D18: Zweite Platzierung im selben Bewegungspaket — Grim {@code MultiPlace}. */
        MULTI_PLACE,
        /** D18: Zweiter Angriff im selben Bewegungspaket — Grim {@code MultiActionsA}. */
        DUPLICATE_ATTACK,
        /** D18: Zweiter Armschwung im selben Bewegungspaket. */
        DUPLICATE_SWING,
        /** D18: Zweite Blickrichtung im selben Bewegungspaket — Grim {@code DuplicateRotLook}. */
        DUPLICATE_ROT_LOOK
    }

    /**
     * Ergebnis einer Anfrage. Der {@link Refusal} ist kein Debug-Text, sondern die Information, die
     * der Aufrufer braucht: er unterscheidet "dieser Tick ist voll" (normal, einfach naechsten Tick
     * nochmal) von "gerade wird ein Item benutzt" (der Slot ist belegt, Aktion faellt aus) und von
     * "falsche Reihenfolge" (Programmierfehler im Ablauf, nicht Taktung).
     */
    public record Decision(boolean allowed, Refusal refusal) {
        private static final Decision ALLOWED = new Decision(true, Refusal.NONE);

        /** @return die erlaubte Entscheidung mit {@link Refusal#NONE} */
        public static Decision allow() {
            return ALLOWED;
        }
    }

    /** Platzierungen dieses Ticks — Grim {@code MultiPlace} feuert ab zwei. */
    private boolean placed;

    /** Angriffe dieses Ticks. */
    private boolean attacked;

    /** Rechte Klicks / Abbauversuche dieses Ticks. */
    private boolean interacted;

    /** Armschwuenge dieses Ticks. */
    private boolean swung;

    /** Blickrichtungen dieses Ticks. */
    private boolean looked;

    /** In diesem Tick lief bereits eine Aktion, auf die ein Slot-Wechsel folgen koennte. */
    private boolean actionThisTick;

    /** Ein Slot-Wechsel lief bereits <i>nach</i> einer Aktion — die eigentliche Verschachtelung. */
    private boolean slotChangeAfterAction;

    /** {@code player.isUsingItem()} — Item wird gerade benutzt (Essen, Trank, Bogen, Schild). */
    private boolean usingItem;

    /**
     * Setzt den Zustand des {@code isUsingItem}-Flags fuer den laufenden Tick.
     *
     * <p><b>Warum nicht ueber einen Supplier:</b> das Flag kann mitten im Tick kippen — der Bot
     * startet in diesem Tick den Trankwurf und will danach noch ein Crystal setzen. Wird es erst am
     * Anfang des Ticks gelesen, sieht die Anfrage am Ende einen veralteten Wert. Der Aufrufer
     * aktualisiert es daher unmittelbar vor jeder Aktion.
     */
    public void setUsingItem(boolean usingItem) {
        this.usingItem = usingItem;
    }

    /**
     * Fragt eine Aktion an und bucht sie bei Erfolg sofort im Ledger ein.
     *
     * <p><b>Reihenfolge der Pruefungen ist bedeutsam:</b> erst das Benutzen-Item, dann die
     * Verschachtelung, erst zuletzt die Taktung. Ein abgelehnter Angriff waehrend
     * {@code isUsingItem} soll {@link Refusal#USING_ITEM} melden und nicht in der Taktung
     * untergehen — die Taktung waere naemlich schon beim naechsten Tick wieder in Ordnung, der
     * belegte Item-Slot nicht.
     *
     * @param action die gewuenschte Aktion
     * @return {@link Decision}; bei Ablehnung bleibt das Ledger unveraendert, damit die Aktion im
     *         naechsten Tick erneut versucht werden kann
     */
    public Decision request(Action action) {
        // D19, Teil 1 — waehrend isUsingItem geht weder ein Angriff noch eine Platzierung durch.
        if (usingItem && isWorldInteraction(action)) return new Decision(false, Refusal.USING_ITEM);

        // D19, Teil 2 — ein Slot-Wechsel zwischen zwei Aktionen ist die Verschachtelung, die
        // Grim in PacketOrderE/F sieht. Ein Slot-Wechsel ganz am Tick-Ende bleibt erlaubt.
        if (isWorldInteraction(action) && slotChangeAfterAction) return new Decision(false, Refusal.SLOT_ORDER);

        switch (action) {
            case PLACE -> {
                if (placed) return new Decision(false, Refusal.MULTI_PLACE);
            }
            case ATTACK -> {
                if (attacked) return new Decision(false, Refusal.DUPLICATE_ATTACK);
            }
            case RIGHT_CLICK, DIG -> {
                if (interacted) return new Decision(false, Refusal.DUPLICATE_ATTACK);
            }
            case SWING -> {
                if (swung) return new Decision(false, Refusal.DUPLICATE_SWING);
            }
            case LOOK -> {
                if (looked) return new Decision(false, Refusal.DUPLICATE_ROT_LOOK);
            }
        }

        book(action);
        return Decision.allow();
    }

    /**
     * Kurzbau fuer Aufrufstellen, die nur ein Ja/Nein brauchen (typisch: der Zweig in einem
     * Rotations-Callback, der ohnehin schon alles andere geprueft hat).
     */
    public boolean permits(Action action) {
        return request(action).allowed();
    }

    /**
     * Meldet einen Slot-Wechsel an — aufgerufen im Moment, in dem der Bot den Hotbar-Slot wechselt,
     * also <b>vor</b> der Aktion, die der Slot vorbereitet.
     *
     * <p>Der Wechsel wird nicht abgelehnt, sondern markiert: lehnt man hier ab, wuerde auch das
     * korrekte Ruecksetzen am Tick-Ende blockiert und der Bot behielte dauerhaft den Combat-Slot in
     * der Hand. Die Verschachtelung wird stattdessen bei der <i>Folge</i>-aktion erkannt.
     */
    public void onSlotChange() {
        if (actionThisTick) slotChangeAfterAction = true;
    }

    /**
     * @return {@code true}, wenn in diesem Tick bereits eine weltwirksame Aktion gebucht wurde —
     *         der Aufrufer kann daran einen Combat-Slot-Rueckbau haengen, ohne doppelt zu buchen
     */
    public boolean hasActionThisTick() {
        return actionThisTick;
    }

    /** Tick-Grenze: leert das Ledger. Einmal pro {@code TickEvent.Pre} aufrufen. */
    public void onTick() {
        placed = false;
        attacked = false;
        interacted = false;
        swung = false;
        looked = false;
        actionThisTick = false;
        slotChangeAfterAction = false;
        usingItem = false;
    }

    /** Bucht eine erlaubte Aktion — der einzige Ort, an dem die Zaehler wandern. */
    private void book(Action action) {
        switch (action) {
            case PLACE -> placed = true;
            case ATTACK -> attacked = true;
            case RIGHT_CLICK, DIG -> interacted = true;
            case SWING -> swung = true;
            case LOOK -> looked = true;
        }
        if (isWorldInteraction(action)) actionThisTick = true;
    }

    /**
     * @return {@code true} fuer die Aktionen, die D19 ueberhaupt bindet. {@link Action#SWING} und
     *         {@link Action#LOOK} fallen bewusst durch: ein Armschwung oder eine Drehung bricht
     *         keinen Item-Gebrauch ab und ist kein Verschachtelungskandidat.
     */
    private static boolean isWorldInteraction(Action action) {
        return action == Action.PLACE || action == Action.ATTACK
            || action == Action.RIGHT_CLICK || action == Action.DIG;
    }
}