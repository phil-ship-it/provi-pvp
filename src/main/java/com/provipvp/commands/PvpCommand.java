package com.provipvp.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.provipvp.modules.GodmodePvP;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;

public class PvpCommand extends Command {
    public PvpCommand() {
        super("pvp", "ProviPvP Kampf-Bot steuern.");
    }

    @Override
    public void build(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {
        builder.executes(context -> {
            toggle();
            return SINGLE_SUCCESS;
        });

        builder.then(literal("on").executes(context -> {
            GodmodePvP m = get();
            if (m != null && !m.isActive()) m.toggle();
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("off").executes(context -> {
            GodmodePvP m = get();
            if (m != null && m.isActive()) m.toggle();
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("toggle").executes(context -> {
            toggle();
            return SINGLE_SUCCESS;
        }));
    }

    private GodmodePvP get() {
        return Modules.get().get(GodmodePvP.class);
    }

    private void toggle() {
        GodmodePvP m = get();
        if (m != null) m.toggle();
    }
}
