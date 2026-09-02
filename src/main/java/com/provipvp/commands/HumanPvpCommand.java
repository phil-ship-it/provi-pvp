package com.provipvp.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.provipvp.modules.HumanPvP;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;

public class HumanPvpCommand extends Command {
    public HumanPvpCommand() {
        super("hpvp", "ProviPvP V2 (human) Kampf-Bot steuern.");
    }

    @Override
    public void build(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {
        builder.executes(context -> {
            toggle();
            return SINGLE_SUCCESS;
        });

        builder.then(literal("on").executes(context -> {
            HumanPvP m = get();
            if (m != null && !m.isActive()) m.toggle();
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("off").executes(context -> {
            HumanPvP m = get();
            if (m != null && m.isActive()) m.toggle();
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("toggle").executes(context -> {
            toggle();
            return SINGLE_SUCCESS;
        }));
    }

    private HumanPvP get() {
        return Modules.get().get(HumanPvP.class);
    }

    private void toggle() {
        HumanPvP m = get();
        if (m != null) m.toggle();
    }
}
