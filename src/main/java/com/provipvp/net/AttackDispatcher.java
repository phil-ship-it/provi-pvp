package com.provipvp.net;

/**
 * Versendet einen Angriff als ROHE Pakete, statt ueber {@code mc.gameMode.attack}.
 *
 * <p><b>Warum ueberhaupt umbauen:</b> {@code gameMode.attack} macht im Client unveraenderlich drei
 * Dinge hintereinander — Interact-Paket, lokale Swing-Animation, <i>und</i> das Swing-Paket, das die
 * lokale Animation selbst mitschickt. Damit haengt die sichtbare Animation am Schadenspaket. Fuer
 * Grim ist genau diese Kopplung das Problem: {@code PacketOrderB} verlangt INTERACT <b>vor</b>
 * ANIMATION, und ein Schlag ohne sauberes Timing sieht aus wie eine Fehlermeldung des Clients.
 * Hier sind die beiden Kanaele getrennt schaltbar, damit der Integrations-Agent genau die
 * Kombination waehlt, die sein Setting verlangt.
 *
 * <p><b>Warum {@link SwingMode#BOTH} trotzdem existiert:</b> der Vanilla-Pfad
 * {@code LocalPlayer.swing} schickt das Swing-Paket selbst mit. Wer {@code BOTH} waehlt, muss den
 * Client-Swing also ueber einen Aufruf ausfuehren, der <i>nur</i> lokal animiert (sonst geht das
 * Swing-Paket doppelt raus und Grim sieht genau das als {@code DuplicateSwing}). Der Default des
 {@code GodmodePvP}-Moduls ist deshalb {@link SwingMode#PACKET}.
 *
 * <p><b>Warum keine echten Pakete hier:</b> R1. Diese Klasse baut kein
 * {@code ServerboundInteractPacket} und kein {@code ServerboundSwingPacket} — sie meldet nur
 * <em>was</em> in welcher Reihenfolge rausgehen soll. Das Erzeugen der Pakete und das Verschicken
 * ueber den Network-Handler macht der Aufrufer ueber {@link PacketSink}. Dadurch ist die
 * Reihenfolge- und Kanal-Logik ohne laufenden Client testbar (siehe {@code AttackDispatcherTest}).
 */
public final class AttackDispatcher {

    /** {@code InteractionHand.MAIN_HAND} als Rohwert — {@code InteractionHand} waere eine {@code mc.}-Klasse. */
    public static final int MAIN_HAND = 0;

    /** {@code InteractionHand.OFF_HAND} als Rohwert, siehe {@link #MAIN_HAND}. */
    public static final int OFF_HAND = 1;

    /** Wie die Schwing-Animation transportiert wird, unabhaengig vom Schadenspaket. */
    public enum SwingMode {
        /** Nur die lokale Animation, kein Swing-Paket (z.B. wenn etwas anderes das Paket mitschickt). */
        CLIENT,
        PACKET,
        /** Beides, in der Reihenfolge ANIMATION-Paket zuerst, dann lokale Animation. */
        BOTH,
        /** Gar keine Animation. Der Angriff ist damit fuer andere Spieler unsichtbar. */
        NONE
    }

    /** Die zwei Pakete, die diese Klasse bestellen kann. */
    public enum Packet {
        /**
         * {@code ServerboundInteractPacket} mit der Entity-ID des Ziels.
         * Erzeugt der Aufrufer als {@code createAttackPacket(entityId, player.isShiftKeyDown())} —
         * das Sneak-Flag gehoert inhaltlich in dieses Paket, nicht in die Animation.
         */
        INTERACT,
        /** {@code ServerboundSwingPacket(hand)} — die reine Animation. */
        ANIMATION
    }

    /**
     * Packet-Fabrik. Bewusst als <b>funktionales</b> Interface mit <b>einer</b> Methode: der
     * Aufrufer erzeugt daraus das echte Paket und schickt es, diese Klasse kennt weder
     * {@code mc.*} noch den Network-Handler (R1).
     */
    @FunctionalInterface
    public interface PacketSink {
        /**
         * @param packet    welches der beiden Pakete rausgehen soll
         * @param argument  Entity-ID bei {@link Packet#INTERACT}, Hand-Ordinal bei
         *                  {@link Packet#ANIMATION}
         */
        void send(Packet packet, int argument);
    }

    private final PacketSink packets;

    /** Erzeugt die lokale, rein visuelle Animation. Darf {@code null} sein, wenn kein Client-Swing gewollt. */
    private final java.util.function.IntConsumer clientSwing;

    /**
     * @param packets     erzeugt und verschickt die echten Pakete
     * @param clientSwing erzeugt die lokale Animation (typisch {@code hand -> mc.player.swing(hand)}),
     *                    {@code null} erlaubt
     */
    public AttackDispatcher(PacketSink packets, java.util.function.IntConsumer clientSwing) {
        this.packets = packets;
        this.clientSwing = clientSwing;
    }

    /**
     * Greift eine Entity an: erst das Schadenspaket, danach die Animation in der vom Modul
     * gewaehlten Kanael-Kombination.
     *
     * <p><b>Grims {@code PacketOrderB} verlangt strikt INTERACT vor ANIMATION.</b> Diese Reihenfolge
     * ist hier fest verdrahtet und nicht konfigurierbar — sie ist eine Tatsache ueber das Protokoll,
     * keine Geschmacksfrage. Auch die lokale Animation wird bewusst <i>nach</i> dem Animationspaket
     * ausgeloest, damit der Server das Schadenspaket auch dann schon gesehen hat, wenn der lokale
     * Animationsbeginn mitprotokolliert wird.
     *
     * @param entityId Entity-ID des Ziels (Crystal, Mob oder Spieler)
     * @param hand     {@link #MAIN_HAND} oder {@link #OFF_HAND}
     * @param mode     Kanal-Kombination der Animation
     */
    public void attack(int entityId, int hand, SwingMode mode) {
        packets.send(Packet.INTERACT, entityId);
        animate(hand, mode);
    }

    /**
     * Schickt nur die Animation, ohne Schadenspaket.
     *
     * <p>Der zweite Grund fuer diese Klasse: nach einer Blockplatzierung (Crystal, Glowstone,
     * Bett) will der Bot den Arm schwingen, ohne zusaetzlich zuzuschlagen — der Schaden kommt ja
     * schon aus der Platzierung. Mit {@code gameMode} gibt es dafuer keinen Weg, der nicht auch
     * angreift.
     *
     * @param hand {@link #MAIN_HAND} oder {@link #OFF_HAND}
     * @param mode Kanal-Kombination der Animation
     */
    public void swing(int hand, SwingMode mode) {
        animate(hand, mode);
    }

    /** Der gemeinsame Kern von {@link #attack} und {@link #swing}: nur die Animation, kein Interact. */
    private void animate(int hand, SwingMode mode) {
        if (mode == SwingMode.NONE) return;

        // Paket zuerst: erst das, was der Server sieht, dann das, was der Client zeigt.
        if (mode == SwingMode.PACKET || mode == SwingMode.BOTH) packets.send(Packet.ANIMATION, hand);
        if (clientSwing != null && (mode == SwingMode.CLIENT || mode == SwingMode.BOTH)) clientSwing.accept(hand);
    }
}