package com.provipvp.mixin;

import com.mojang.datafixers.util.Pair;
import com.provipvp.gui.ModuleEntry;
import com.provipvp.modules.ProviClickGui;
import com.provipvp.util.SmartSearch;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Ersetzt Meteors Suchprimitiven durch die Ranklisten-Suche. Meteors Original sortiert
 * <i>alle</i> Module nach roher Levenshtein-Distanz und filtert nicht - dadurch erscheinen
 * sachfremde Module vor inhaltlich passenden, und Setting-Beschreibungen werden gar nicht
 * durchsucht. Der Hook sitzt bewusst an {@code Modules}, damit auch der Modul-Screen und
 * jedes Theme, das diese Primitiven nutzt, profitiert.
 */
@Mixin(Modules.class)
public class ModulesSearchMixin {
    @Inject(method = "searchTitles", at = @At("HEAD"), cancellable = true)
    private void provi$rankedTitles(String query, CallbackInfoReturnable<List<Pair<Module, String>>> cir) {
        if (!ProviClickGui.searchOn()) return;

        List<Pair<Module, String>> result = new ArrayList<>();
        for (SmartSearch.Hit<ModuleEntry> hit : search(query)) {
            Module module = hit.entry().module();
            result.add(Pair.of(module, hit.reason() == null ? module.title : module.title + " · " + hit.reason()));
        }
        cir.setReturnValue(result);
    }

    @Inject(method = "searchSettingTitles", at = @At("HEAD"), cancellable = true)
    private void provi$rankedSettingTitles(String query, CallbackInfoReturnable<Set<Module>> cir) {
        if (!ProviClickGui.searchOn()) return;

        Set<Module> result = new LinkedHashSet<>();
        for (SmartSearch.Hit<ModuleEntry> hit : search(query)) {
            result.add(hit.entry().module());
        }
        cir.setReturnValue(result);
    }

    private static List<SmartSearch.Hit<ModuleEntry>> search(String query) {
        SmartSearch.Options options = new SmartSearch.Options();
        options.typoTolerance = ProviClickGui.typoTolerance();
        options.searchDescriptions = ProviClickGui.descriptionsOn();
        options.limit = ProviClickGui.maxResults();

        List<ModuleEntry> entries = new ArrayList<>();
        for (Module module : Modules.get().getAll()) entries.add(new ModuleEntry(module));

        return SmartSearch.search(query, entries, options);
    }
}
