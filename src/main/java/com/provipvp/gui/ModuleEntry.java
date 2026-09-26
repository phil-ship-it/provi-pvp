package com.provipvp.gui;

import com.provipvp.util.SmartSearch;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Adapter von einem Meteor-Modul auf die MC-freien Suchregeln aus {@link SmartSearch}.
 * Die Setting-Liste wird gecacht, weil die Suche bei jedem Tastendruck neu bewertet wird -
 * Settings koennen sich zur Laufzeit nur durch Addon-Neuaufbau aendern, was ueber die
 * Groesse der Settings erkannt wird.
 */
public class ModuleEntry implements SmartSearch.Entry {
    private static final Map<Module, Cached> CACHE = new IdentityHashMap<>();

    private final Module module;

    public ModuleEntry(Module module) {
        this.module = module;
    }

    public Module module() {
        return module;
    }

    @Override
    public String title() {
        return module.title;
    }

    @Override
    public String[] aliases() {
        return module.aliases;
    }

    @Override
    public String category() {
        return module.category.name;
    }

    @Override
    public String addon() {
        return module.addon == null ? "" : module.addon.name;
    }

    @Override
    public List<SmartSearch.Field> fields() {
        Cached cached = CACHE.get(module);
        int size = module.settings.sizeGroups();
        if (cached != null && cached.groups == size) return cached.fields;

        List<SmartSearch.Field> fields = new ArrayList<>();
        for (SettingGroup group : module.settings) {
            for (Setting<?> setting : group) {
                fields.add(new SmartSearch.Field(setting.title, setting.description, group.name));
            }
        }
        CACHE.put(module, new Cached(size, fields));
        return fields;
    }

    /**Leert den Cache - noetig, wenn ein Addon Settings zur Laufzeit neu anlegt.*/
    public static void invalidate() {
        CACHE.clear();
    }

    private record Cached(int groups, List<SmartSearch.Field> fields) {}
}
