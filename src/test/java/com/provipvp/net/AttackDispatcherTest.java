package com.provipvp.net;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Deckt die Kanal-Trennung und die Paketreihenfolge des Angriffsversands ab.
 *
 * <p>Der Test arbeitet mit einer aufzeichnenden {@link AttackDispatcher.PacketSink} und einem
 * aufzeichnenden Client-Swing — so bleibt alles ohne laufenden Minecraft-Client (R1).
 */
class AttackDispatcherTest {

    /** Nimmt die bestellten Pakete in Reihenfolge auf, formatiert als {@code NAME(argument)}. */
    private static final class Recorder {
        final List<String> packets = new ArrayList<>();
        final List<Integer> clientSwings = new ArrayList<>();

        AttackDispatcher build() {
            return new AttackDispatcher(
                (packet, argument) -> packets.add(packet.name() + "(" + argument + ")"),
                clientSwings::add);
        }
    }

    @Test
    void packetModeSendsAnimationWithoutClientSwing() {
        Recorder r = new Recorder();

        r.build().attack(4242, AttackDispatcher.MAIN_HAND, AttackDispatcher.SwingMode.PACKET);

        assertEquals(List.of("INTERACT(4242)", "ANIMATION(0)"), r.packets,
            "PACKET muss Interact und Animationspaket senden");
        assertTrue(r.clientSwings.isEmpty(),
            "PACKET darf keinen lokalen Swing ausloesen - der wuerde das Swing-Paket doppelt schicken");
    }

    @Test
    void noneModeSendsNeitherAnimationNorClientSwing() {
        Recorder r = new Recorder();

        r.build().attack(4242, AttackDispatcher.MAIN_HAND, AttackDispatcher.SwingMode.NONE);

        assertEquals(List.of("INTERACT(4242)"), r.packets,
            "NONE darf nur das Schadenspaket senden - der Angriff muss unsichtbar bleiben");
        assertTrue(r.clientSwings.isEmpty(), "NONE darf keine lokale Animation erzeugen");
    }

    @Test
    void interactAlwaysPrecedesAnimation() {
        // Grim PacketOrderB: INTERACT vor ANIMATION. Das ist Protokoll, keine Geschmacksfrage,
        // und darf ueber kein Setting umstellbar sein.
        Recorder r = new Recorder();

        r.build().attack(99, AttackDispatcher.OFF_HAND, AttackDispatcher.SwingMode.PACKET);

        assertEquals("INTERACT(99)", r.packets.get(0), "das Schadenspaket muss zuerst raus");
        assertEquals("ANIMATION(1)", r.packets.get(1), "das Animationspaket muss danach raus");
    }

    @Test
    void bothModeSendsPacketFirstThenLocalAnimation() {
        Recorder r = new Recorder();

        r.build().attack(7, AttackDispatcher.MAIN_HAND, AttackDispatcher.SwingMode.BOTH);

        assertEquals(List.of("INTERACT(7)", "ANIMATION(0)"), r.packets);
        assertEquals(List.of(AttackDispatcher.MAIN_HAND), r.clientSwings,
            "BOTH erzeugt zusaetzlich die lokale Animation - mit der Hand des Angriffs");
    }

    @Test
    void clientModeSendsNoAnimationPacketAtAll() {
        Recorder r = new Recorder();

        r.build().attack(7, AttackDispatcher.OFF_HAND, AttackDispatcher.SwingMode.CLIENT);

        assertEquals(List.of("INTERACT(7)"), r.packets,
            "CLIENT ist fuer Faelle, in denen das Swing-Paket schon anderswo mitgeht");
        assertEquals(List.of(AttackDispatcher.OFF_HAND), r.clientSwings);
    }

    @Test
    void swingOnlySendsAnimationWithoutInteract() {
        // Der zweite Grund fuer die Klasse: nach einer Platzierung will der Bot den Arm schwingen,
        // ohne zusaetzlich zuzuschlagen - mit gameMode gaebe es dafuer keinen Weg.
        Recorder r = new Recorder();

        r.build().swing(AttackDispatcher.MAIN_HAND, AttackDispatcher.SwingMode.PACKET);

        assertEquals(List.of("ANIMATION(0)"), r.packets,
            "swing() darf niemals ein Interact-Paket erzeugen");
    }

    @Test
    void interactCarriesTheTargetEntityIdUnchanged() {
        Recorder r = new Recorder();

        r.build().attack(-12345, AttackDispatcher.MAIN_HAND, AttackDispatcher.SwingMode.NONE);

        assertEquals("INTERACT(-12345)", r.packets.get(0),
            "die Entity-ID muss unveraendert durchgereicht werden - negative IDs sind normal");
    }

    @Test
    void dispatcherToleratesAMissingClientSwing() {
        // Ohne Client-Swing darf die Paketschicht nicht abbrechen — sonst bricht ein Modul, das
        // SwingMode.PACKET fährt und keinen lokalen Client-Swing will, mitten im Kampf ab.
        List<String> packets = new ArrayList<>();
        AttackDispatcher d = new AttackDispatcher((packet, argument) -> packets.add(packet.name()), null);

        d.attack(1, AttackDispatcher.MAIN_HAND, AttackDispatcher.SwingMode.BOTH);

        assertEquals(List.of("INTERACT", "ANIMATION"), packets);
    }
}