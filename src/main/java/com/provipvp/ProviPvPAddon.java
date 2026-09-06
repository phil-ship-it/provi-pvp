package com.provipvp;

import com.provipvp.modules.Auto5b5tDupe;
import com.provipvp.modules.BrandSpoof;
import com.provipvp.modules.ExploitGuard;
import com.provipvp.modules.GodmodePvP;
import com.provipvp.modules.HumanPvP;
import com.provipvp.modules.PacketLogger;
import com.provipvp.modules.TrainingDummy;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import com.provipvp.commands.HumanPvpCommand;
import com.provipvp.commands.NbtCommand;
import com.provipvp.commands.PvpCommand;
import meteordevelopment.meteorclient.commands.Commands;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProviPvPAddon extends MeteorAddon {
    public static final Logger LOG = LoggerFactory.getLogger(ProviPvPAddon.class);
    public static final Category CATEGORY = new Category("ProviPvP");

    @Override
    public void onInitialize() {
        Modules.get().add(new GodmodePvP());
        Modules.get().add(new HumanPvP());
        Modules.get().add(new TrainingDummy());
        Modules.get().add(new Auto5b5tDupe());
        Modules.get().add(new PacketLogger());
        Modules.get().add(new BrandSpoof());
        Modules.get().add(new ExploitGuard());
        Commands.add(new PvpCommand());
        Commands.add(new HumanPvpCommand());
        Commands.add(new NbtCommand());
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public String getPackage() {
        return "com.provipvp";
    }
}
