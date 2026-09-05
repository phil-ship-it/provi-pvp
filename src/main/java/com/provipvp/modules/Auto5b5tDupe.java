package com.provipvp.modules;

import com.provipvp.ProviPvPAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.network.protocol.game.ServerboundPlaceRecipePacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Portiert von github.com/mmvanheusden/meteor-5b5t-addon (GPL-3.0) - Original zielte auf MC 1.21.5/Yarn-Mappings,
 *  hier 1:1 auf 26.2/Mojang-Mappings uebertragen (StackedContents/RecipeFinder-API wurde seitdem komplett
 *  umgebaut - Recipe-Book-Zugriff, Ergebnis-Aufloesung und das Platzierungs-Paket haben alle neue Namen bekommen).
 *  Quellcode vor der Portierung vollstaendig gelesen: keine Netzwerk-Calls, keine Fremd-Auth, keine Telemetrie -
 *  reiner Crafting-Exploit (Item faellt, Craft-Request-Paket referenziert die Zutat, die der Server in der Luecke
 *  zwischen Drop und Anfrage noch als vorhanden fuehrt).
 *
 *  WICHTIG: Original zuletzt am 19.05.2025 getestet - ueber ein Jahr her. Ob 5b5t diese Race Condition inzwischen
 *  gepatcht hat, ist ungeprueft. Erst mit "single" + einem wertlosen Item selbst verifizieren, bevor man sich
 *  darauf verlaesst. */
public class Auto5b5tDupe extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public final Setting<Recipe> recipe = sgGeneral.add(new EnumSetting.Builder<Recipe>()
        .name("recipe")
        .description("Welches Rezept fuer den Exploit genutzt wird. Zutaten fuer mindestens 2 Stueck muessen im Inventar sein.")
        .defaultValue(Recipe.Stick)
        .build()
    );

    public final Setting<Boolean> single = sgGeneral.add(new BoolSetting.Builder()
        .name("single")
        .description("Nur der reine Exploit-Versuch, ohne Rotation/Drop-Automatik - zum Testen, ob die Luecke ueberhaupt noch offen ist.")
        .defaultValue(false)
        .build()
    );

    public final Setting<RotationMode> rotationMode = sgGeneral.add(new EnumSetting.Builder<RotationMode>()
        .name("rotation-mode")
        .description("Wie beim Exploit-Versuch rotiert wird.")
        .defaultValue(RotationMode.Silent)
        .visible(() -> !single.get())
        .build()
    );

    private Phase phase = Phase.PREPARE;
    private RecipeDisplayEntry targetRecipe;
    private float oldPitch;

    public Auto5b5tDupe() {
        super(ProviPvPAddon.CATEGORY, "auto-5b5t-dupe", "Craft-Exploit-Dupe fuer 5b5t (Item faellt + Craft-Request-Race). Vor ernsthaftem Einsatz selbst mit einem wertlosen Item testen.");
    }

    @Override
    public void onActivate() {
        if (mc.player == null || mc.level == null) {
            toggle();
            return;
        }

        if (single.get()) {
            if (!(mc.player.containerMenu instanceof CraftingMenu)) {
                ChatUtils.error("Fuer 'single' muss vorher schon ein Crafting Table offen sein (die Luecke steckt in dessen 3x3-Slot-Logik, nicht im 2x2-Inventar-Crafting).");
                toggle();
                return;
            }
            if (!findTarget()) {
                toggle();
                return;
            }
            sendCraftRequest();
            toggle();
            return;
        }

        if (mc.player.getInventory().getSelectedItem().isEmpty()) {
            ChatUtils.error("Halte das Item in der Hand, das dupliziert werden soll.");
            toggle();
            return;
        }

        phase = (mc.player.containerMenu instanceof CraftingMenu) ? Phase.PREPARE : Phase.OPEN_TABLE;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.level == null) {
            toggle();
            return;
        }

        switch (phase) {
            case OPEN_TABLE -> openCraftingTable();
            case PREPARE -> {
                if (!findTarget()) {
                    toggle();
                    return;
                }

                if (rotationMode.get() == RotationMode.Client) oldPitch = mc.player.getXRot();
                rotate();
                phase = Phase.DROP;
            }
            case DROP -> {
                mc.player.drop(false);
                ChatUtils.info("Item in der Hand fallen gelassen.");
                phase = Phase.CRAFT;
            }
            case CRAFT -> {
                if (rotationMode.get() == RotationMode.Client) mc.player.setXRot(oldPitch);
                sendCraftRequest();
                toggle();
            }
        }
    }

    /** Die eigentliche Race Condition steckt in CraftingMenu (3x3-Tisch), nicht im simpleren 2x2-Inventar-Crafting -
     *  ohne offenen Crafting Table referenziert das Paket die falsche, robustere Server-Logik und tut schlicht
     *  nichts. Oeffnet einen nahen Tisch, oder stellt einen aus dem Inventar auf, falls keiner in der Naehe steht. */
    private void openCraftingTable() {
        if (mc.player.containerMenu instanceof CraftingMenu) {
            phase = Phase.PREPARE;
            return;
        }

        BlockPos table = findNearbyCraftingTable();
        if (table != null) {
            Vec3 center = Vec3.atCenterOf(table);
            Rotations.rotate(Rotations.getYaw(center), Rotations.getPitch(center), () ->
                BlockUtils.interact(new BlockHitResult(center, BlockUtils.getDirection(table), table, true), InteractionHand.MAIN_HAND, true)
            );
            return;
        }

        FindItemResult tableItem = InvUtils.findInHotbar(Items.CRAFTING_TABLE);
        if (!tableItem.found()) tableItem = InvUtils.find(Items.CRAFTING_TABLE);
        if (!tableItem.found()) {
            ChatUtils.error("Kein Crafting Table in der Naehe oder im Inventar - fuer den Exploit zwingend noetig.");
            toggle();
            return;
        }

        BlockPos spot = findTablePlacementSpot();
        if (spot == null) {
            ChatUtils.error("Kein freier Platz zum Aufstellen eines Crafting Tables gefunden.");
            toggle();
            return;
        }

        BlockUtils.place(spot, tableItem, true, 50);
    }

    private BlockPos findNearbyCraftingTable() {
        BlockPos base = mc.player.blockPosition();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos cell = base.offset(dx, dy, dz);
                    if (mc.level.getBlockState(cell).is(Blocks.CRAFTING_TABLE)) return cell;
                }
            }
        }
        return null;
    }

    private BlockPos findTablePlacementSpot() {
        BlockPos base = mc.player.blockPosition();
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos cell = base.relative(dir);
            if (mc.level.getBlockState(cell).isAir() && mc.level.getBlockState(cell.below()).blocksMotion()) {
                return cell;
            }
        }
        return null;
    }

    private void rotate() {
        switch (rotationMode.get()) {
            case Silent -> Rotations.rotate(mc.player.getYRot(), 90f, () -> {});
            case Client -> mc.player.setXRot(90f);
        }
    }

    private void sendCraftRequest() {
        mc.player.connection.send(new ServerboundPlaceRecipePacket(mc.player.containerMenu.containerId, targetRecipe.id(), false));
    }

    /** Sucht im bekannten Recipe-Book nach dem eingestellten Zielitem und prueft, ob genug Zutaten dafuer da sind. */
    private boolean findTarget() {
        StackedItemContents contents = new StackedItemContents();
        mc.player.getInventory().fillStackedContents(contents);

        ClientRecipeBook book = mc.player.getRecipeBook();
        for (RecipeCollection collection : book.getCollections()) {
            for (RecipeDisplayEntry entry : collection.getRecipes()) {
                List<ItemStack> results = entry.resultItems(SlotDisplayContext.fromLevel(mc.level));
                for (ItemStack result : results) {
                    if (result.getItem() != recipe.get().item) continue;

                    if (!entry.canCraft(contents)) {
                        ChatUtils.error("Keine Zutaten fuer " + recipe.get().name() + " im Inventar.");
                        return false;
                    }

                    targetRecipe = entry;
                    return true;
                }
            }
        }

        ChatUtils.error("Rezept nicht im Recipe-Book gefunden.");
        return false;
    }

    private enum Phase {
        OPEN_TABLE, PREPARE, DROP, CRAFT
    }

    private enum RotationMode {
        Silent, Client
    }

    private enum Recipe {
        Stick(Items.STICK), CraftingTable(Items.CRAFTING_TABLE);

        final Item item;

        Recipe(Item item) {
            this.item = item;
        }
    }
}
