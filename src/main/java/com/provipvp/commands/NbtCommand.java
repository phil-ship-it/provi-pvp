package com.provipvp.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/** Debug-Werkzeug: zeigt Components (Items, seit 1.20.5 kein rohes NBT mehr) bzw. NBT (Entities,
 *  Block-Entities) im Chat an. Rein lesend, keine Seiteneffekte. */
public class NbtCommand extends Command {
    public NbtCommand() {
        super("nbt", "Zeigt Components/NBT von Item, Entity oder Block im Chat.");
    }

    @Override
    public void build(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {
        builder.then(literal("item").executes(context -> {
            item();
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("entity").executes(context -> {
            entity();
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("block").executes(context -> {
            block();
            return SINGLE_SUCCESS;
        }));

        builder.executes(context -> {
            HitResult hit = mc.hitResult;
            if (hit instanceof EntityHitResult) entity();
            else if (hit instanceof BlockHitResult) block();
            else item();
            return SINGLE_SUCCESS;
        });
    }

    private void item() {
        ItemStack stack = mc.player.getMainHandItem();
        if (stack.isEmpty()) {
            ChatUtils.error("Kein Item in der Hand.");
            return;
        }

        ChatUtils.info("§6Components von %s (x%d):", stack.getDisplayName().getString(), stack.getCount());
        for (TypedDataComponent<?> c : stack.getComponents()) {
            ChatUtils.info("§7  %s = %s", c.type(), c.value());
        }
    }

    private void entity() {
        if (!(mc.hitResult instanceof EntityHitResult hit)) {
            ChatUtils.error("Kein Entity im Fadenkreuz.");
            return;
        }

        Entity e = hit.getEntity();
        TagValueOutput out = TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
        e.saveWithoutId(out);
        ChatUtils.info("§6NBT von %s:", e.getName().getString());
        ChatUtils.info("§7%s", out.buildResult().toString());
    }

    private void block() {
        if (!(mc.hitResult instanceof BlockHitResult hit)) {
            ChatUtils.error("Kein Block im Fadenkreuz.");
            return;
        }

        BlockEntity be = mc.level.getBlockEntity(hit.getBlockPos());
        if (be == null) {
            ChatUtils.error("Block hat keine Block-Entity (kein Container/keine gespeicherten Daten).");
            return;
        }

        TagValueOutput out = TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
        be.saveWithId(out);
        ChatUtils.info("§6NBT von %s:", hit.getBlockPos().toShortString());
        ChatUtils.info("§7%s", out.buildResult().toString());
    }
}
