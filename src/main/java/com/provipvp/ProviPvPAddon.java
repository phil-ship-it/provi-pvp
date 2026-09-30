package com.provipvp;

import com.provipvp.config.ProfileManager;
import com.provipvp.modules.Auto5b5tDupe;
import com.provipvp.modules.AutoArmor;
import com.provipvp.modules.BrandSpoof;
import com.provipvp.modules.ExploitGuard;
import com.provipvp.modules.GodmodePvP;
import com.provipvp.modules.HumanPvP;
import com.provipvp.modules.MacroTriggerModule;
import com.provipvp.modules.PacketFilter;
import com.provipvp.modules.PacketLogger;
import com.provipvp.modules.ProviClickGui;
import com.provipvp.modules.TrainingDummy;
import com.provipvp.commands.HumanPvpCommand;
import com.provipvp.commands.NbtCommand;
import com.provipvp.commands.ProfileCommand;
import com.provipvp.commands.ProxyCommand;
import com.provipvp.commands.PvpCommand;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.commands.Commands;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProviPvPAddon extends MeteorAddon {
    public static final Logger LOG = LoggerFactory.getLogger(ProviPvPAddon.class);
    public static final Category CATEGORY = new Category("ProviPvP");

    /**
     * Reentrancy-Schutz: {@code Module.disable()} delegiert intern an {@code toggle()}. Ohne diese
     * Marke wuerde der Waechter in {@link #enforceExclusiveProfile} sich selbst wieder aufrufen.
     */
    private static final ThreadLocal<Boolean> SWITCHING = ThreadLocal.withInitial(() -> Boolean.FALSE);

    /**
     * Erzwingt die Invariante "die beiden PvP-Profile sind nie gleichzeitig aktiv".
     *
     * <p>Nennt die bisherigen Waechter in {@code onActivate()} und {@code onTick()} nicht
     * ausreichen: Meteor ueberspringt in {@code Module.toggle()} sowohl {@code EVENT_BUS.subscribe} als
     * auch {@code onActivate()}, wenn {@code Utils.canUpdate()} false ist — also im Hauptmenue oder
     * waehrend der Welt geladen wird. Der Keybind-Pfad hat keinen {@code canUpdate}-Waechter. Wer im
     * Hauptmenue beide Profile einschaltet, laeuft mit zwei {@code isActive()==true} und ohne
     * Event-Handler; beim Weltbeitritt feuert dann ein ungepaartes {@code onDeactivate()}.
     *
     * <p>Der Toggle-Pfad laeuft dagegen immer, deshalb wird hier eingegriffen.
     *
     * @param activating das Profil, das gerade eingeschaltet wird
     */
    public static void enforceExclusiveProfile(Class<?> activating) {
        if (SWITCHING.get()) return;
        SWITCHING.set(Boolean.TRUE);
        try {
            Modules modules = Modules.get();
            GodmodePvP godmode = modules.get(GodmodePvP.class);
            HumanPvP human = modules.get(HumanPvP.class);
            if (godmode == null || human == null) return;
            if (!godmode.isActive() || !human.isActive()) return;

            if (activating == HumanPvP.class) godmode.disable();
            else human.disable();
        } finally {
            SWITCHING.set(Boolean.FALSE);
        }
    }

    @Override
    public void onInitialize() {
        ProfileManager.init();

        Modules.get().add(new GodmodePvP());
        Modules.get().add(new HumanPvP());
        Modules.get().add(new TrainingDummy());
        Modules.get().add(new Auto5b5tDupe());
        Modules.get().add(new PacketLogger());
        Modules.get().add(new BrandSpoof());
        Modules.get().add(new ExploitGuard());
        Modules.get().add(new PacketFilter());
        Modules.get().add(new MacroTriggerModule());
        Modules.get().add(new AutoArmor());
        Modules.get().add(new ProviClickGui());
        Commands.add(new PvpCommand());
        Commands.add(new HumanPvpCommand());
        Commands.add(new NbtCommand());
        Commands.add(new ProxyCommand());
        Commands.add(new ProfileCommand());
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
