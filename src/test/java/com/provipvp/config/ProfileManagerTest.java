package com.provipvp.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Deckt genau den Fehler ab, der die mitgelieferten Profile lautlos stilllegte: die Gruppen heissen
 * {@code "1 · Angriff & Auras"}, die Profile benutzen {@code "combat"}, und der alte Vergleich war
 * ein reiner Substring-Test. Ein Profil anzuwenden aenderte damit nichts und meldete nur
 * "fehlgeschlagen" - ohne Exception, also ohne sichtbares Signal.
 */
class ProfileManagerTest {

    // "General" ist die elfte Gruppe: Meteor erzeugt sie in Settings#getDefaultGroup() mit genau diesem
    // Namen. Sie taucht nicht unter den createGroup(...)-Namen auf, gehoert aber zum Modul.
    // Die Liste ist die Vereinigung beider Combat-Module: GodmodePvP hat zusaetzlich "Stadt & Traps"
    // und "Mobs", HumanPvP dafuer "Human-Profil". Ein Profil gehoert genau einem Modul, die Aufloesung
    // der Schluessel darf aber nicht davon abhaengen, welches davon gerade laeuft.
    private static final String[] REAL_GROUPS = {
        "General",
        "1 · Angriff & Auras",
        "2 · Schutz & Recovery",
        "3 · Stadt & Traps",
        "3 · Navigation",
        "4 · Navigation",
        "4 · Inventar",
        "5 · Inventar",
        "5 · Human-Profil",
        "6 · Turtle-Master",
        "6 · Perlen & Flucht",
        "7 · Perlen & Flucht",
        "7 · Heilung",
        "8 · Heilung",
        "8 · QA & Erweitert",
        "9 · QA & Erweitert",
        "10 · Mobs",
    };

    private static boolean anyGroupMatches(String key) {
        for (String group : REAL_GROUPS) {
            if (ProfileManager.groupMatches(group, key)) return true;
        }
        return false;
    }

    @Test
    void englishProfileKeysResolveToRealGroups() {
        for (String key : new String[] { "combat", "defense", "city", "navigation",
            "inventory", "turtle", "pearls", "healing", "qa", "general" }) {
            assertTrue(anyGroupMatches(key), "Profilschluessel '" + key + "' muss eine Gruppe finden");
        }
    }

    @Test
    void aPlainEnglishKeyNeverResolvesBySubstringAlone() {
        // "combat" steckt in keiner Gruppentitel - genau deshalb braucht es die Alias-Tabelle.
        for (String group : REAL_GROUPS) {
            assertFalse(group.toLowerCase().contains("combat"), group + " enthaelt unerwartet 'combat'");
        }
    }

    @Test
    void numberingIsStrippedOnBothSides() {
        assertTrue(ProfileManager.groupMatches("1 · Angriff & Auras", "Angriff & Auras"));
        assertTrue(ProfileManager.groupMatches("1 · Angriff & Auras", "7 · Angriff & Auras"));
    }

    @Test
    void renumberingDoesNotBreakAProfile() {
        // Eine Gruppe eingefuegt -> alles verschiebt sich. Die Profilzuordnung muss trotzdem halten.
        String renumbered = "4 · Angriff & Auras";
        assertTrue(ProfileManager.groupMatches(renumbered, "combat"));
    }

    @Test
    void realGroupTitlesStillMatchExactly() {
        for (String group : REAL_GROUPS) {
            assertTrue(ProfileManager.groupMatches(group, group), group + " muss sich selbst matchen");
        }
    }

    @Test
    void unknownKeysAreRejected() {
        assertFalse(anyGroupMatches("gibtesnicht"));
        assertFalse(anyGroupMatches(""));
    }

    @Test
    void nullsDoNotThrow() {
        assertFalse(ProfileManager.groupMatches(null, "combat"));
        assertFalse(ProfileManager.groupMatches("1 · Angriff & Auras", null));
        assertFalse(ProfileManager.groupMatches(null, null));
    }

    @Test
    void nonNumericPrefixIsNotStripped() {
        // "QA & Erweitert" enthaelt kein '·'; ein Punkt inmitten eines Titels darf nichts abschneiden.
        assertTrue(ProfileManager.groupMatches("9 · QA & Erweitert", "qa"));
        assertFalse(ProfileManager.groupMatches("A · B", "A"));
    }

    /**
     * Jeder Gruppen-Schluessel in jedem mitgelieferten Profil muss auf eine echte Gruppe zeigen.
     *
     * <p>Ohne diesen Test faellt genau der Fehler durch, der gerade behoben wurde: ein erfundener Key
     * wie "hardening" sieht voellig legitim aus, ergibt aber {@code findSettingGroup == null}, und
     * das Profil meldet nur "fehlgeschlagen", ohne Ausnahme. Genau dieser Zustand ist beim Anwenden
     * fuer den Spieler nicht von "ich habe es nur nicht bemerkt" zu unterscheiden.
     */
    @Test
    void everyShippedProfileUsesResolvableGroupKeys() throws Exception {
        String[] profiles = { "2b2t.json", "2b2t-human.json", "5b5t.json", "donutsmp.json", "arena-aggressive.json" };
        List<String> unresolved = new ArrayList<>();

        for (String name : profiles) {
            try (var in = ProfileManagerTest.class.getResourceAsStream("/config/provipvp/profiles/" + name)) {
                assertNotNull(in, "Profil nicht im Klassenpfad: " + name);
                com.google.gson.JsonObject root = com.google.gson.JsonParser
                    .parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8))
                    .getAsJsonObject();

                for (var group : root.getAsJsonObject("settings").entrySet()) {
                    if (group.getKey().startsWith("_")) continue; // Kommentar, kein Gruppen-Key
                    if (!anyGroupMatches(group.getKey())) {
                        unresolved.add(name + " -> \"" + group.getKey() + "\"");
                    }
                }
            }
        }
        assertTrue(unresolved.isEmpty(), "Gruppen-Schluessel ohne passende Gruppe: " + unresolved);
    }
}
