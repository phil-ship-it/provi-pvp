package com.provipvp.net;

/**
 * Erkennt einen Totem-Pop an der <b>Offhand des eigenen Spielers</b> — ereignisbasiert, nicht durch
 * Abfragen des Inventarzustands.
 *
 * <p><b>Warum das Inventar-Polling falsch ist:</b> ein Pop ist ein <i>Server</i>-Ereignis. Der
 * Client sieht erst, dass das Totem aus der Offhand verschwunden ist, wenn der Server den
 * Inventory-Slot aendert — das ist ein asynchroner, einen Tick verspaeteter Nebeneffekt. Wer in
 * derselben Tick noch {@code player.getOffhandItem()} liest, sieht das alte Totem und zaehlt den
 * Pop nicht; wer im naechsten Tick pollt, kann nicht mehr unterscheiden, ob das Totem geplatzt ist
 * oder der Bot es selbst weggelegt hat (was der Totem-Manager jeden Tick tut). Der
 * {@code ClientboundEntityEventPacket} mit {@code eventId 35} ist dagegen eindeutig: den sendet der
 * Server <i>nur</i> beim Totem-Gebrauch, und er traegt die Entity-ID des Betroffenen mit — ein
 * Crystal, der neben uns hochgeht, schickt ihn nicht.
 * <p><b>Zweiter Weg:</b> der Bot sieht seinen eigenen Offhand-Stack ebenfalls und kann daraus
 * ableiten, dass ein Pop stattgefunden hat, selbst wenn das Entity-Event-Paket im Kabel verloren
 * ging. Beide Wege werden auf <b>einen</b> Pop pro Tick zusammengefasst (ein Pop erzeugt beide
 * Signale) — sonst wuerde jeder Pop doppelt in die Combo-Bilanz einlaufen.
 *
 * <p><b>Warum der Zustandsautomat existiert:</b> Entity-Event-Pakete duplizieren in der Praxis
 * (Umbrella-Entity, Respawn-Race). Ohne Sperre wuerde ein einzelner Pop als zwei gezaehlt und das
 * Modul gluecklicher machen als es ist. Die Sperre ist <b>pro Tick</b> und nicht dauerhaft: zwei
 * echte Pops liegen mindestens 10 Ticks auseinander (i-Frames, siehe Vertrags-Fakten), ein
 * dauerhafter Latch wuerde den zweiten verschlucken.
 *
 * <p>Rein funktional ueber {@code (eventId, entityId, isSelf)} — kein {@code mc.*} (R1), testbar
 * ueber {@code TotemEventReaderTest}.
 */
public final class TotemEventReader {

    /** Entity-Event-ID, die der Server ausschliesslich beim Totem-Gebrauch sendet. */
    public static final int TOTEM_POP_EVENT_ID = 35;

    /** Ergebnis einer Beobachtung. */
    public enum Detection {
        /** Kein Pop — das Paket galt einem anderen Entity oder einer anderen Event-ID. */
        NONE,
        /** Ein Pop wurde in diesem Tick neu erkannt. */
        POP
    }

    /** Signatur des zuletzt gesehenen Event-Pakets, zum Duplikat-Vergleich. */
    private int lastEventId = Integer.MIN_VALUE;
    private int lastEntityId = Integer.MIN_VALUE;

    /** {@code true}, sobald in diesem Tick ein Pop gemeldet wurde — haengt an {@link #onTick()}. */
    private boolean popReported;

    /** letzter bekannter Offhand-Zustand; vor der ersten Beobachtung unbestimmt. */
    private boolean offhandTotemPresent;

    /** {@code true}, sobald der Offhand-Zustand mindestens einmal gesehen wurde. */
    private boolean offhandKnown;

    /**
     * Wertet ein eingehendes {@code ClientboundEntityEventPacket} aus.
     *
     * @param eventId  Byte-Wert des Entity-Events; nur {@value #TOTEM_POP_EVENT_ID} ist ein Totem-Pop
     * @param entityId Entity-ID des betroffenen Entities
     * @param isSelf   {@code true}, wenn {@code entityId} der eigene Spieler ist
     * @return {@link Detection#POP} genau einmal pro Pop, sonst {@link Detection#NONE}
     */
    public Detection onEntityEvent(int eventId, int entityId, boolean isSelf) {
        // Ein fremdes Entity (Gegner, der seinen Totem poppt) ist fuer den eigenen Vorrat irrelevant,
        // und eine andere Event-ID (Schaden, Tod) darf nichts ausloesen.
        if (!isSelf) return Detection.NONE;
        if (eventId != TOTEM_POP_EVENT_ID) return Detection.NONE;

        boolean duplicate = eventId == lastEventId && entityId == lastEntityId && popReported;
        lastEventId = eventId;
        lastEntityId = entityId;
        return reportPop(duplicate);
    }

    /**
     * Wertet den beobachteten Offhand-Stack aus: ist das Totem zwischen zwei Beobachtungen
     * <b>verschwunden</b>, wurde es verbraucht — es sei denn, der Bot hat es selbst weggelegt, was
     * der Aufrufer bereits abgefangen haben muss (z.B. weil er in diesem Tick selbst gewechselt hat).
     *
     * @param totemInOffhand {@code true}, solange ein Totem in der Offhand liegt
     * @return {@link Detection#POP} beim Uebergang vorhanden → weg, sonst {@link Detection#NONE}
     */
    public Detection onOffhandTotem(boolean totemInOffhand) {
        boolean vanished = offhandKnown && offhandTotemPresent && !totemInOffhand;

        offhandTotemPresent = totemInOffhand;
        offhandKnown = true;

        return reportPop(!vanished);
    }

    /**
     * Zentrale Sperre: meldet den Pop genau einmal pro Tick.
     *
     * @param duplicate {@code true}, wenn das Signal bereits (als Duplikat) gesehen wurde
     * @return {@link Detection#POP} beim ersten Signal dieses Ticks, sonst {@link Detection#NONE}
     */
    private Detection reportPop(boolean duplicate) {
        if (popReported || duplicate) return Detection.NONE;
        popReported = true;
        return Detection.POP;
    }

    /**
     * Tick-Grenze: hebt die Duplikatsperre auf. Muss einmal pro Client-Tick laufen, sonst verschluckt
     * die Sperre nach einem Pop jeden weiteren Pop — und zwei Pops sind durch die i-Frames getrennt,
     * fallen also garantiert in verschiedene Ticks.
     */
    public void onTick() {
        popReported = false;
        lastEventId = Integer.MIN_VALUE;
        lastEntityId = Integer.MIN_VALUE;
    }

    /** Vollstaendiger Reset — bei Deaktivierung des Moduls und beim Weltwechsel. */
    public void reset() {
        onTick();
        offhandKnown = false;
        offhandTotemPresent = false;
    }
}