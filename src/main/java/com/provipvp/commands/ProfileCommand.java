package com.provipvp.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.provipvp.config.ProfileManager;
import com.provipvp.modules.GodmodePvP;
import com.provipvp.modules.HumanPvP;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;

/**
 * Profil-Befehl: /provipvp profile <name|list|save> [modul]
 * Beispiele:
 *   .profile 2b2t          - wendet 2b2t Profil auf aktives PvP-Modul an
 *   .profile 5b5t godmode  - wendet 5b5t Profil auf GodmodePvP an
 *   .profile list          - listet alle Profile
 *   .profile save myconfig - speichert aktuelle Settings als myconfig.json
 */
public class ProfileCommand extends Command {
    public ProfileCommand() {
        super("profile", "Laedt/verwaltet JSON-Profile fuer GodmodePvP/HumanPvP", "prof");
    }

    @Override
    public void build(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {
        builder.executes(context -> {
            // .profile ohne Argumente -> liste Profile
            ProfileManager.listProfiles();
            return SINGLE_SUCCESS;
        });

        // .profile list
        builder.then(literal("list").executes(context -> {
            ProfileManager.listProfiles();
            return SINGLE_SUCCESS;
        }));

        // .profile save <name> [modul]
        builder.then(literal("save")
            .then(argument("name", StringArgumentType.string())
                .executes(context -> {
                    String name = StringArgumentType.getString(context, "name");
                    Module module = getTargetModule(null);
                    if (module != null) ProfileManager.saveProfile(name, module);
                    return SINGLE_SUCCESS;
                })
                .then(literal("godmode").executes(context -> {
                    String name = StringArgumentType.getString(context, "name");
                    Module module = Modules.get().get(GodmodePvP.class);
                    if (module != null) ProfileManager.saveProfile(name, module);
                    return SINGLE_SUCCESS;
                }))
                .then(literal("human").executes(context -> {
                    String name = StringArgumentType.getString(context, "name");
                    Module module = Modules.get().get(HumanPvP.class);
                    if (module != null) ProfileManager.saveProfile(name, module);
                    return SINGLE_SUCCESS;
                }))
            )
        );

        // .profile <name> [modul] - Profil anwenden
        builder.then(argument("name", StringArgumentType.string())
            .executes(context -> {
                String name = StringArgumentType.getString(context, "name");
                Module module = getTargetModule(null);
                if (module != null) ProfileManager.applyProfile(name, module);
                return SINGLE_SUCCESS;
            })
            .then(literal("godmode").executes(context -> {
                String name = StringArgumentType.getString(context, "name");
                Module module = Modules.get().get(GodmodePvP.class);
                if (module != null) ProfileManager.applyProfile(name, module);
                return SINGLE_SUCCESS;
            }))
            .then(literal("human").executes(context -> {
                String name = StringArgumentType.getString(context, "name");
                Module module = Modules.get().get(HumanPvP.class);
                if (module != null) ProfileManager.applyProfile(name, module);
                return SINGLE_SUCCESS;
            }))
        );
    }

    private Module getTargetModule(String ignored) {
        // Auto-detect: welches PvP-Modul ist aktiv?
        GodmodePvP god = Modules.get().get(GodmodePvP.class);
        HumanPvP human = Modules.get().get(HumanPvP.class);

        if (god != null && god.isActive()) return god;
        if (human != null && human.isActive()) return human;

        // Keins aktiv -> Standard GodmodePvP
        return Modules.get().get(GodmodePvP.class);
    }
}