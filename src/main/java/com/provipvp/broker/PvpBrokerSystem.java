package com.provipvp.broker;

import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.System;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.Minecraft;

/**
 * Lebenszyklus des {@link ActionBroker}: einmal pro Client-Tick zuruecksetzen, bei Weltwechsel die
 * Pfad-Sperre loesen. Sonst nichts.
 *
 * <p><b>Warum ein System und kein Modul.</b> Ein Modul laeuft nur, solange es eingeschaltet ist -
 * der Broker muss aber laufen, <i>weil</i> Module laufen. Ohne seinen Reset bleibt jede Belegung
 * eines Kampfes stehen, und jedes Modul bekommt danach fuer den Rest der Sitzung "gehoert dir
 * nicht". Meteor initialisiert Systeme selbst, abonniert sie selbst am Event-Bus und speichert sie
 * beim Beenden; genau diese drei Dinge bringt kein Modul von selbst mit.
 *
 * <p><b>Reihenfolge statt Hoeflichkeit.</b> {@link PvpServices#tick()} muss laufen, <i>bevor</i> die
 * Kampfmodule ihren {@code TickEvent.Pre} bekommen. Orbit ruft den hoeher priorisierten Listener
 * zuerst auf, deshalb laeuft der Reset mit {@link #RESET_PRIORITY} weit oberhalb der Vorgabe 0,
 * mit der die Module ihre Tick-Handler annotieren.
 *
 * <p><b>Der Weltwechsel ist der gefaehrliche Fall.</b> Die Pfad-Sperre ist ueber Ticks hinweg
 * referenzzaehlend: Baritone laeuft erst weiter, wenn der letzte Besitzer freigibt. Faellt der
 * Besitzer weg - weil das Modul gar nicht mehr existiert, weil die Welt weg ist, weil der Server
 * die Verbindung kappt - bleibt Baritone fuer den Rest der Sitzung stehen, ohne dass irgendwo ein
 * Fehler sichtbar wird. Deshalb loest genau dieser Lebenszyklus die Sperre, und zwar vor dem ersten
 * Tick der neuen Welt.
 *
 * <p>Der Weltwechsel wird ueber die Identitaet des {@code ClientLevel} erkannt und nicht ueber
 * {@code GameJoinedEvent}: ein Dimensionstorch feuert kein Weltwechsel-Event, das Level-Objekt
 * tauscht sich aber. {@link GameLeftEvent} bleibt als eigener Fall, weil dort {@code level} noch
 * einen Tick lang das alte Objekt liefern kann.
 *
 * <p>Registrierung: {@code Systems.add(new PvpBrokerSystem())} in {@code ProviPvPAddon#onInitialize()}.
 */
public final class PvpBrokerSystem extends System<PvpBrokerSystem> {

    /**
     * Reset vor allen Modulen. Orbit sortiertabsteigend nach Prioritaet, die Module stehen auf dem
     * Vorgabewert 0 - 1000 ist damit nicht "hoch", sondern "sicher davor".
     */
    private static final int RESET_PRIORITY = 1000;

    /**
     * Welt, in der zuletzt getaktet wurde. Bewusst nur als {@link Object} gehalten: dieses System
     * soll die Level-Objekte besuchter Dimensionen nicht festhalten, nur ihre Identitaet vergleichen.
     */
    private Object world;

    public PvpBrokerSystem() {
        super("ProviPvP-Broker");
    }

    @EventHandler(priority = RESET_PRIORITY)
    private void onTick(TickEvent.Pre event) {
        Object level = Minecraft.getInstance().level;
        if (level != world) {
            world = level;
            pathLeasesLoesen();
        }

        PvpServices.tick();
    }

    @EventHandler(priority = RESET_PRIORITY)
    private void onGameLeft(GameLeftEvent event) {
        // Ohne das wartet Baritone hier noch bis zum naechsten Tick - und wenn der Disconnect der
        // letzte Zustand der Sitzung ist, kommt dieser Tick nie.
        world = null;
        pathLeasesLoesen();
    }

    /**
     * Baritone freigeben und die Belegungen des letzten Ticks loeschen.
     *
     * <p>{@link PvpServices#tick()} ist genau der Reset, den der Broker anbietet. Die Pfad-Sperre
     * steht absichtlich nicht darin: sie ist ueber Ticks hinweg gedacht und wird von keinem Modul
     * automatisch zurueckgesetzt - das ist der Grund, warum diese Klasse ueberhaupt existiert.
     */
    private void pathLeasesLoesen() {
        PvpServices.broker().path().clear();
        PvpServices.broker().tick();
    }
}