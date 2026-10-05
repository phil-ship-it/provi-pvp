package com.provipvp.net;

import org.junit.jupiter.api.Test;

import static com.provipvp.net.TotemEventReader.Detection.NONE;
import static com.provipvp.net.TotemEventReader.Detection.POP;
import static com.provipvp.net.TotemEventReader.TOTEM_POP_EVENT_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Deckt ab, dass ein eigener Totem-Pop genau <b>einmal</b> gemeldet wird, ein fremder Pop und jedes
 * andere Entity-Event dagegen nie.
 */
class TotemEventReaderTest {

    private static final int SELF_ID = 1337;
    private static final int OTHER_ID = 2468;

    @Test
    void event35OnSelfYieldsPopExactlyOnce() {
        TotemEventReader reader = new TotemEventReader();

        assertEquals(POP, reader.onEntityEvent(TOTEM_POP_EVENT_ID, SELF_ID, true),
            "Event 35 auf der eigenen Entity ist ein Totem-Pop");
        assertEquals(NONE, reader.onEntityEvent(TOTEM_POP_EVENT_ID, SELF_ID, true),
            "ein dupliziertes Entity-Event darf denselben Pop nicht zweimal zaehlen");
        assertEquals(NONE, reader.onEntityEvent(TOTEM_POP_EVENT_ID, SELF_ID, true),
            "die Sperre muss auch beim dritten Duplikat halten");
    }

    @Test
    void event35OnAnotherEntityYieldsNone() {
        TotemEventReader reader = new TotemEventReader();

        assertEquals(NONE, reader.onEntityEvent(TOTEM_POP_EVENT_ID, OTHER_ID, false),
            "der Pop eines Gegners darf nicht als eigener Totem-Verlust gelten");
    }

    @Test
    void otherEventIdsOnSelfYieldNone() {
        TotemEventReader reader = new TotemEventReader();

        assertEquals(NONE, reader.onEntityEvent(2, SELF_ID, true), "Entity-Event 2 (Schaden) ist kein Pop");
        assertEquals(NONE, reader.onEntityEvent(0, SELF_ID, true), "Entity-Event 0 (Spawn) ist kein Pop");
    }

    @Test
    void aSecondPopInALaterTickIsReportedAgain() {
        // Zwei echte Pops liegen durch die i-Frames mindestens 10 Ticks auseinander — ein dauerhafter
        // Latch wuerde den zweiten verschlucken und das Modul zu frueh von Totem-Mangel ausgehen lassen.
        TotemEventReader reader = new TotemEventReader();
        reader.onEntityEvent(TOTEM_POP_EVENT_ID, SELF_ID, true);

        reader.onTick();

        assertEquals(POP, reader.onEntityEvent(TOTEM_POP_EVENT_ID, SELF_ID, true),
            "im naechsten Tick ist der naechste Pop wieder meldepflichtig");
    }

    @Test
    void vanishedOffhandTotemIsDetectedOnce() {
        TotemEventReader reader = new TotemEventReader();
        reader.onOffhandTotem(true); // Startzustand: Totem liegt in der Offhand

        assertEquals(POP, reader.onOffhandTotem(false), "das Verschwinden des Totems ist ein Pop");
        assertEquals(NONE, reader.onOffhandTotem(false),
            "ein leerer Offhand-Slot ist kein zweiter Pop — sonst zaehlt der Bot nach");
    }

    @Test
    void aRefilledOffhandIsNotAPop() {
        TotemEventReader reader = new TotemEventReader();
        reader.onOffhandTotem(true);
        reader.onOffhandTotem(false);

        assertEquals(NONE, reader.onOffhandTotem(true), "der Totem-Manager legt nach — das ist kein Pop");
    }

    @Test
    void eventAndOffhandSignalInTheSameTickCountOnce() {
        // Ein echter Pop erzeugt beide Signale im selben Tick: das Entity-Event und der leere
        // Offhand-Slot. Ohne Zusammenfassung laege jeder Pop doppelt in der Combo-Bilanz.
        TotemEventReader reader = new TotemEventReader();
        reader.onOffhandTotem(true);

        assertEquals(POP, reader.onEntityEvent(TOTEM_POP_EVENT_ID, SELF_ID, true));
        assertEquals(NONE, reader.onOffhandTotem(false),
            "das zweite Signal desselben Pops darf nicht noch einmal zaehlen");
    }

    @Test
    void unknownOffhandStateDoesNotFabricateAPop() {
        TotemEventReader reader = new TotemEventReader();

        assertEquals(NONE, reader.onOffhandTotem(false),
            "ohne vorherigen Zustand gibt es keinen Uebergang — ein leerer Offhand beim Start ist kein Pop");
    }
}