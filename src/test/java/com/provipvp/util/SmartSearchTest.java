package com.provipvp.util;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Deckt die Suchbewertung der ClickGUI ab: Filter, Rangfolge, Begruendung, Tippfehler.
 *  Beachtet den gemeldeten Fehler - Meteor sortiert alle Module nur nach Levenshtein und
 *  laesst dadurch sachfremde Treffer vor passenden erscheinen bzw. passende ganz weg. */
class SmartSearchTest {

    private record Entry(String title, String[] aliases, String category, String addon,
                         List<SmartSearch.Field> fields) implements SmartSearch.Entry {
        static Entry of(String title, String category, String... settings) {
            List<SmartSearch.Field> fields = new ArrayList<>();
            for (int i = 0; i < settings.length; i += 2) {
                fields.add(new SmartSearch.Field(settings[i], settings[i + 1], "Allgemein"));
            }
            return new Entry(title, new String[0], category, "Meteor", fields);
        }
    }

    private static SmartSearch.Options options() {
        return new SmartSearch.Options();
    }

    private static List<String> titles(List<SmartSearch.Hit<Entry>> hits) {
        return hits.stream().map(hit -> hit.entry().title()).toList();
    }

    // ---------- Filter statt reiner Sortierung ----------

    @Test
    void unrelatedModulesAreDroppedInsteadOfJustSortedLast() {
        List<Entry> entries = List.of(
            Entry.of("CrystalAura", "Combat"),
            Entry.of("KillAura", "Combat"),
            Entry.of("ESP", "Render"),
            Entry.of("AntiBot", "Misc"));

        assertEquals(List.of("CrystalAura"), titles(SmartSearch.search("crystal", entries, options())));
    }

    @Test
    void substringMatchBeatsPrefixOfOtherModule() {
        // "AutoCrystal" enthaelt "crystal", "ClickGUI" faengt mit "c" an - Meteors
        // Distanzsortierung wuerde hier die falsche Reihenfolge liefern.
        List<Entry> entries = List.of(
            Entry.of("ClickGUI", "Meteor"),
            Entry.of("AutoCrystal", "Combat"));

        assertEquals(List.of("AutoCrystal"), titles(SmartSearch.search("crystal", entries, options())));
    }

    @Test
    void emptyQueryReturnsNoResults() {
        List<Entry> entries = List.of(Entry.of("CrystalAura", "Combat"));
        assertTrue(SmartSearch.search("   ", entries, options()).isEmpty());
    }

    // ---------- Rangfolge ----------

    @Test
    void exactNameRanksBeforePrefixBeforeSubstring() {
        List<Entry> entries = List.of(
            Entry.of("MyAura", "Combat"),
            Entry.of("Aura", "Combat"),
            Entry.of("SuperAuraPlus", "Combat"));

        assertEquals(List.of("Aura", "MyAura", "SuperAuraPlus"),
            titles(SmartSearch.search("aura", entries, options())));
    }

    @Test
    void nameMatchRanksBeforeSettingMatch() {
        List<Entry> entries = List.of(
            Entry.of("Range", "Combat", "places-range", "Wie weit der Aura reicht"),
            Entry.of("CrystalAura", "Combat"));

        assertEquals("CrystalAura", SmartSearch.search("crystal", entries, options()).get(0).entry().title());
    }

    // ---------- Settings und Begruendung ----------

    @Test
    void settingTitleMatchIsFoundAndExplained() {
        List<Entry> entries = List.of(Entry.of("GodmodePvP", "ProviPvP", "use-beds", "Bett-Aura aktivieren"));

        List<SmartSearch.Hit<Entry>> hits = SmartSearch.search("beds", entries, options());
        assertEquals(1, hits.size());
        assertEquals("Setting: use-beds", hits.get(0).reason());
    }

    @Test
    void descriptionIsSearchedOnlyWhenEnabled() {
        Entry entry = Entry.of("GodmodePvP", "ProviPvP", "use-beds", "Bett-Aura aktivieren");
        List<Entry> entries = List.of(entry);

        assertEquals(1, SmartSearch.search("aktivieren", entries, options()).size());

        SmartSearch.Options off = options();
        off.searchDescriptions = false;
        assertTrue(SmartSearch.search("aktivieren", entries, off).isEmpty());
    }

    @Test
    void categoryMatchIsWeakerThanAnyNameMatch() {
        List<Entry> entries = List.of(
            Entry.of("ESP", "Aura-Utilities"),
            Entry.of("KillAura", "Combat"));

        assertEquals(List.of("KillAura", "ESP"), titles(SmartSearch.search("aura", entries, options())));
    }

    // ---------- Mehrere Suchbegriffe ----------

    @Test
    void allTokenMatchesWinOverSingleTokenMatches() {
        List<Entry> entries = List.of(
            Entry.of("AuraThing", "Combat"),
            Entry.of("CrystalAura", "Combat"),
            Entry.of("Crawl", "Movement"));

        assertEquals(List.of("CrystalAura"), titles(SmartSearch.search("crystal aura", entries, options())));
    }

    @Test
    void partialMatchesAreFallbackWhenNothingMatchesEverything() {
        List<Entry> entries = List.of(
            Entry.of("AuraThing", "Combat"),
            Entry.of("AuraHelper", "Combat"),
            Entry.of("ESP", "Render"));

        // Kein Modul hat beide Begriffe - dann bleiben die Teiltreffer, sortiert
        assertEquals(List.of("AuraThing", "AuraHelper"), titles(SmartSearch.search("aura zzz", entries, options())));
    }

    // ---------- Tippfehler ----------

    @Test
    void typoToleranceFindsNearMissesButRanksThemLast() {
        List<Entry> entries = List.of(
            Entry.of("KillAura", "Combat"),
            Entry.of("CrystalAura", "Combat"),
            Entry.of("Movement", "Movement"));

        SmartSearch.Options tolerant = options();
        tolerant.typoTolerance = 2;
        assertEquals(List.of("CrystalAura"), titles(SmartSearch.search("crysatl", entries, tolerant)));

        SmartSearch.Options strict = options();
        strict.typoTolerance = 0;
        assertTrue(SmartSearch.search("crysatl", entries, strict).isEmpty());

        // Exakter Treffer verdraengt den Tippfehler-Treffer - Rauschen bleibt draussen
        List<Entry> mixed = List.of(Entry.of("CrystalAura", "Combat"), Entry.of("Crysatl", "Combat"));
        assertEquals(List.of("Crysatl"), titles(SmartSearch.search("crysatl", mixed, tolerant)));
    }

    // ---------- Grenzen ----------

    @Test
    void resultLimitIsRespected() {
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < 25; i++) entries.add(Entry.of("AuraMod" + i, "Combat"));

        SmartSearch.Options limited = options();
        limited.limit = 5;
        assertEquals(5, SmartSearch.search("aura", entries, limited).size());
    }

    @Test
    void aliasesAreSearched() {
        Entry entry = new Entry("AuraModule", new String[]{"killaura"}, "Combat", "Meteor", List.of());
        List<SmartSearch.Hit<Entry>> hits = SmartSearch.search("killaura", List.of(entry), options());

        assertEquals(1, hits.size());
        assertEquals("Alias: killaura", hits.get(0).reason());
    }

    @Test
    void distanceIsSymmetricAndCorrect() {
        assertEquals(0, SmartSearch.distance("aura", "aura"));
        assertEquals(4, SmartSearch.distance("aura", "xxxx"));
        assertEquals(Arrays.asList(2, 2), Arrays.asList(
            SmartSearch.distance("crystal", "crysatl"),
            SmartSearch.distance("crysatl", "crystal")));
        assertEquals(3, SmartSearch.distance("kitten", "sitting"));
    }
}
