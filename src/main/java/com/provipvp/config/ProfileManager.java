package com.provipvp.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.provipvp.ProviPvPAddon;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;

import java.io.*;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * Laedt und wendet JSON-Profile auf GodmodePvP / HumanPvP an.
 * Profile liegen in config/provipvp/profiles/*.json
 * Anwendung: /provipvp profile <name>  oder  .profile <name>
 */
public final class ProfileManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PROFILE_DIR = Paths.get("config", "provipvp", "profiles");
    private static final Map<String, JsonObject> profileCache = new HashMap<>();

    private ProfileManager() {}

    /** Initialisiert das Profil-Verzeichnis und kopiert Default-Profile aus Resources. */
    public static void init() {
        try {
            Files.createDirectories(PROFILE_DIR);
            copyDefaultProfiles();
        } catch (IOException e) {
            MeteorClient.LOG.error("Failed to init profile directory", e);
        }
    }

    /** Kopiert Default-Profile aus src/main/resources/config/provipvp/profiles/ nach config/provipvp/profiles/. */
    private static void copyDefaultProfiles() throws IOException {
        String[] defaults = { "2b2t.json", "5b5t.json", "donutsmp.json", "2b2t-human.json" };
        for (String name : defaults) {
            Path target = PROFILE_DIR.resolve(name);
            if (!Files.exists(target)) {
                try (InputStream in = ProviPvPAddon.class.getClassLoader().getResourceAsStream("config/provipvp/profiles/" + name)) {
                    if (in != null) {
                        Files.copy(in, target);
                        MeteorClient.LOG.info("Copied default profile: " + name);
                    }
                }
            }
        }
    }

    /** Laedt ein Profil nach Name (ohne .json) und wendet es auf das angegebene Modul an. */
    public static boolean applyProfile(String name, Module module) {
        JsonObject profile = loadProfile(name);
        if (profile == null) {
            ChatUtils.error("Profil nicht gefunden: " + name);
            return false;
        }

        String targetModule = profile.get("module").getAsString();
        String moduleName = getModuleName(module);
        if (!moduleName.equals(targetModule)) {
            ChatUtils.error("Profil %s ist fuer Modul %s, nicht fuer %s", name, targetModule, moduleName);
            return false;
        }

        JsonObject settings = profile.getAsJsonObject("settings");
        int applied = 0, failed = 0;

        for (Map.Entry<String, com.google.gson.JsonElement> groupEntry : settings.entrySet()) {
            String groupName = groupEntry.getKey();
            com.google.gson.JsonElement groupSettingsElement = groupEntry.getValue();
            if (!groupSettingsElement.isJsonObject()) {
                failed++;
                continue;
            }
            JsonObject groupSettings = groupSettingsElement.getAsJsonObject();

            // SettingGroup finden (case-insensitive, Teilstring)
            SettingGroup sg = findSettingGroup(module, groupName);
            if (sg == null) {
                failed += groupSettings.size();
                continue;
            }

            for (Map.Entry<String, com.google.gson.JsonElement> settingEntry : groupSettings.entrySet()) {
                String settingName = settingEntry.getKey();
                Setting<?> setting = sg.get(settingName);
                if (setting == null) {
                    failed++;
                    continue;
                }

                try {
                    applySettingValue(setting, settingEntry.getValue());
                    applied++;
                } catch (Exception e) {
                    MeteorClient.LOG.warn("Failed to apply setting {}: {}", settingName, e.getMessage());
                    failed++;
                }
            }
        }

        ChatUtils.info("§aProfil §f%s §aangewendet: %d Settings gesetzt, %d fehlgeschlagen", name, applied, failed);
        return failed == 0;
    }

    private static SettingGroup findSettingGroup(Module module, String name) {
        // Exakte Uebereinstimmung
        for (SettingGroup sg : getSettingGroups(module)) {
            if (getSettingGroupName(sg).equalsIgnoreCase(name)) return sg;
        }
        // Teilstring-Match (z.B. "combat" matcht "1 · Angriff & Auras")
        for (SettingGroup sg : getSettingGroups(module)) {
            String sgName = getSettingGroupName(sg);
            if (sgName.toLowerCase().contains(name.toLowerCase()) ||
                name.toLowerCase().contains(sgName.toLowerCase())) {
                return sg;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static void applySettingValue(Setting<?> setting, com.google.gson.JsonElement value) {
        Object currentValue = setting.get();
        Class<?> type = currentValue.getClass();

        if (type == Boolean.class) {
            ((Setting<Boolean>) setting).set(value.getAsBoolean());
        } else if (type == Integer.class) {
            ((Setting<Integer>) setting).set(value.getAsInt());
        } else if (type == Double.class || type == Float.class) {
            ((Setting<Double>) setting).set(value.getAsDouble());
        } else if (type == String.class) {
            ((Setting<String>) setting).set(value.getAsString());
        } else {
            // Fallback: use reflection to call set(Object)
            try {
                java.lang.reflect.Method setMethod = Setting.class.getDeclaredMethod("set", Object.class);
                setMethod.setAccessible(true);
                String json = GSON.toJson(value);
                Object parsed = GSON.fromJson(json, type);
                setMethod.invoke(setting, parsed);
            } catch (Exception e) {
                MeteorClient.LOG.warn("Failed to apply setting via reflection: {}", e.getMessage());
            }
        }
    }

    /** Laedt ein Profil aus Cache oder Datei. */
    private static JsonObject loadProfile(String name) {
        if (profileCache.containsKey(name)) return profileCache.get(name);

        Path file = PROFILE_DIR.resolve(name + ".json");
        if (!Files.exists(file)) {
            // Versuche aus Resources zu laden
            try (InputStream in = ProviPvPAddon.class.getClassLoader().getResourceAsStream("config/provipvp/profiles/" + name + ".json")) {
                if (in != null) {
                    JsonObject obj = GSON.fromJson(new InputStreamReader(in), JsonObject.class);
                    profileCache.put(name, obj);
                    return obj;
                }
            } catch (IOException ignored) {}
            return null;
        }

        try (Reader reader = Files.newBufferedReader(file)) {
            JsonObject obj = GSON.fromJson(reader, JsonObject.class);
            profileCache.put(name, obj);
            return obj;
        } catch (IOException e) {
            MeteorClient.LOG.error("Failed to load profile: " + name, e);
            return null;
        }
    }

    /** Speichert die aktuellen Settings eines Moduls als neues Profil. */
    public static void saveProfile(String name, Module module) {
        JsonObject profile = new JsonObject();
        profile.addProperty("name", name);
        profile.addProperty("module", getModuleName(module));
        profile.addProperty("description", "Custom profile saved at " + java.time.LocalDateTime.now());

        JsonObject settingsObj = new JsonObject();
        for (SettingGroup sg : getSettingGroups(module)) {
            JsonObject groupObj = new JsonObject();
            String groupName = getSettingGroupName(sg);
            for (Setting<?> setting : getSettings(sg)) {
                Object val = setting.get();
                String settingName = getSettingName(setting);
                if (val != null && settingName != null) groupObj.addProperty(settingName, String.valueOf(val));
            }
            if (groupName != null) settingsObj.add(groupName, groupObj);
        }
        profile.add("settings", settingsObj);

        // Ein Profil gehoert genau einem Modul. Ohne diese Pruefung wuerde '.profile save arena' bei
        // aktivem HumanPvP das unter gleichem Namen gespeicherte GodmodePvP-Profil stillschweigend
        // ersetzen — inklusive des profileCache-Eintrags. Das Profil des anderen Moduls waere weg,
        // ohne dass irgendetwas darauf hinweist.
        JsonObject existing = loadProfile(name);
        if (existing != null && existing.has("module")) {
            String owner = existing.get("module").getAsString();
            String mine = getModuleName(module);
            if (!owner.equals(mine)) {
                ChatUtils.error("Profil %s gehoert zu %s - nicht ueberschrieben. Bitte anderen Namen waehlen.", name, owner);
                return;
            }
        }

        Path file = PROFILE_DIR.resolve(name + ".json");
        try (Writer writer = Files.newBufferedWriter(file)) {
            GSON.toJson(profile, writer);
            profileCache.put(name, profile);
            ChatUtils.info("§aProfil §f%s §agespeichert", name);
        } catch (IOException e) {
            ChatUtils.error("Fehler beim Speichern: " + e.getMessage());
        }
    }

    private static String getModuleName(Module module) {
        try {
            Field nameField = Module.class.getDeclaredField("name");
            nameField.setAccessible(true);
            return (String) nameField.get(module);
        } catch (Exception e) {
            return module.getClass().getSimpleName();
        }
    }

    @SuppressWarnings("unchecked")
    private static java.util.List<SettingGroup> getSettingGroups(Module module) {
        try {
            Field settingsField = Module.class.getDeclaredField("settings");
            settingsField.setAccessible(true);
            Object settingsObj = settingsField.get(module);
            Field groupsField = settingsObj.getClass().getDeclaredField("groups");
            groupsField.setAccessible(true);
            return (java.util.List<SettingGroup>) groupsField.get(settingsObj);
        } catch (Exception e) {
            return java.util.Collections.emptyList();
        }
    }

    /** Extrahiert den Namen eines Settings via Reflection (Meteor Setting hat privates name-Feld). */
    private static String getSettingName(Setting<?> setting) {
        try {
            Field nameField = Setting.class.getDeclaredField("name");
            nameField.setAccessible(true);
            return (String) nameField.get(setting);
        } catch (Exception e) {
            return setting.toString();
        }
    }

    /** Extrahiert den Namen einer SettingGroup via Reflection. */
    private static String getSettingGroupName(SettingGroup sg) {
        try {
            Field nameField = SettingGroup.class.getDeclaredField("name");
            nameField.setAccessible(true);
            return (String) nameField.get(sg);
        } catch (Exception e) {
            return sg.toString();
        }
    }

    /** Holt die Settings einer SettingGroup via Reflection. */
    @SuppressWarnings("unchecked")
    private static java.util.List<Setting<?>> getSettings(SettingGroup sg) {
        try {
            Field settingsField = SettingGroup.class.getDeclaredField("settings");
            settingsField.setAccessible(true);
            return (java.util.List<Setting<?>>) settingsField.get(sg);
        } catch (Exception e) {
            return java.util.Collections.emptyList();
        }
    }

    /** Listet alle verfuegbaren Profile. */
    public static void listProfiles() {
        ChatUtils.info("§6Verfuegbare Profile:");
        try {
            Files.list(PROFILE_DIR).filter(p -> p.toString().endsWith(".json")).forEach(p -> {
                String name = p.getFileName().toString().replace(".json", "");
                JsonObject obj = loadProfile(name);
                String desc = obj != null && obj.has("description") ? obj.get("description").getAsString() : "";
                ChatUtils.info("  §f%s §7- %s", name, desc);
            });
        } catch (IOException e) {
            ChatUtils.error("Fehler beim Lesen der Profile: " + e.getMessage());
        }
    }
}