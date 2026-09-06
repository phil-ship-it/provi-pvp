package com.provipvp.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.systems.proxies.Proxies;
import meteordevelopment.meteorclient.systems.proxies.Proxy;
import meteordevelopment.meteorclient.systems.proxies.ProxyType;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;

/** Duenner Wrapper um Meteors EIGENES, bereits vorhandenes Proxy-System (Proxies/Proxy-Klassen, sonst nur
 *  ueber eine eigene GUI-Screen erreichbar) - fuer schnellen Wechsel per Chat-Befehl statt Menue-Navigation.
 *  Kein eigenes Proxy-Handling gebaut, nur Zugriff auf das bestehende. */
public class ProxyCommand extends Command {
    public ProxyCommand() {
        super("proxy", "Proxies verwalten und wechseln (Meteors eingebautes Proxy-System).");
    }

    @Override
    public void build(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {
        builder.executes(context -> {
            list();
            return SINGLE_SUCCESS;
        });

        builder.then(literal("list").executes(context -> {
            list();
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("check").executes(context -> {
            Proxies.get().checkProxies(true);
            info("Pruefe alle Proxies (Ergebnis erscheint in der Proxy-Liste)...");
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("add")
            .then(argument("name", StringArgumentType.word())
            .then(argument("address", StringArgumentType.word())
            .then(argument("port", IntegerArgumentType.integer(1, 65535))
            .executes(context -> {
                add(
                    StringArgumentType.getString(context, "name"),
                    StringArgumentType.getString(context, "address"),
                    IntegerArgumentType.getInteger(context, "port"),
                    ProxyType.Socks5
                );
                return SINGLE_SUCCESS;
            })
            .then(argument("type", StringArgumentType.word()).executes(context -> {
                ProxyType type = ProxyType.parse(StringArgumentType.getString(context, "type"));
                add(
                    StringArgumentType.getString(context, "name"),
                    StringArgumentType.getString(context, "address"),
                    IntegerArgumentType.getInteger(context, "port"),
                    type != null ? type : ProxyType.Socks5
                );
                return SINGLE_SUCCESS;
            }))))));

        builder.then(literal("switch")
            .then(argument("name", StringArgumentType.greedyString()).executes(context -> {
                switchTo(StringArgumentType.getString(context, "name"));
                return SINGLE_SUCCESS;
            })));

        builder.then(literal("remove")
            .then(argument("name", StringArgumentType.greedyString()).executes(context -> {
                remove(StringArgumentType.getString(context, "name"));
                return SINGLE_SUCCESS;
            })));
    }

    private void list() {
        Proxies proxies = Proxies.get();
        if (proxies.isEmpty()) {
            info("Keine Proxies konfiguriert. Beispiel: .proxy add home 12.34.56.78 1080 socks5");
            return;
        }

        for (Proxy p : proxies) {
            String marker = p.enabled.get() ? "§a[aktiv]" : "§7[ ]";
            info("%s %s - %s:%d (%s) %s", marker, p.name.get(), p.address.get(), p.port.get(), p.type.get(), p.status);
        }
    }

    private void add(String name, String address, int port, ProxyType type) {
        Proxy proxy = new Proxy.Builder().name(name).address(address).port(port).type(type).build();
        if (Proxies.get().add(proxy)) {
            info("Proxy \"%s\" hinzugefuegt (%s:%d, %s).", name, address, port, type);
        } else {
            error("Ein Proxy mit dieser Adresse+Port existiert schon.");
        }
    }

    private void switchTo(String name) {
        for (Proxy p : Proxies.get()) {
            if (p.name.get().equalsIgnoreCase(name)) {
                Proxies.get().setEnabled(p, true);
                info("Proxy gewechselt zu \"%s\" - wirkt erst bei der naechsten Verbindung, aktuelle Session bleibt unveraendert.", name);
                return;
            }
        }
        error("Kein Proxy mit dem Namen \"%s\" gefunden. .proxy list zeigt alle an.", name);
    }

    private void remove(String name) {
        for (Proxy p : Proxies.get()) {
            if (p.name.get().equalsIgnoreCase(name)) {
                Proxies.get().remove(p);
                info("Proxy \"%s\" entfernt.", name);
                return;
            }
        }
        error("Kein Proxy mit dem Namen \"%s\" gefunden.", name);
    }
}
