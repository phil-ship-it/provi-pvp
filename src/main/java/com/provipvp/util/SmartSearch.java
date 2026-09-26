package com.provipvp.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Reine Suchlogik fuer die ClickGUI-Suche - bewusst frei von Minecraft-/Meteor-Klassen,
 * damit sie isoliert getestet werden kann (gleiches Muster wie {@link PvpMath}).
 *
 * <p>Meteor sortiert Suchtreffer nur nach roher Levenshtein-Distanz ueber <i>alle</i> Module
 * und filtert ueberhaupt nicht: ein Modul, das inhaltlich passt, kann dadurch hinter
 * einem sachfremden Modul landen, dessen Name zufaelligAehnlich viele Zeichen hat. Diese
 * Klasse bewertet stattdessen <i>wie</i> ein Treffer zustande kam, filtert echte
 * Fehltreffer weg und liefert begruendete Treffer zur Anzeige.
 *
 * <p>Zwei-Stufen-Logik: Treffer, die <i>alle</i> Suchbegriffe enthalten ("starke Treffer"),
 * kommen zuerst und allein. Findet sich nichts, gibt es als Rueckfallebene die schwachen
 * Treffer, die nur einen Teil der Begriffe treffen - lieber eine schwache Liste als gar keine.
 */
public final class SmartSearch {
    /**Bewertungsskala: kleiner ist besser. {@link #NO_MATCH} heisst "kein Treffer". */
    public static final int NO_MATCH = Integer.MAX_VALUE;

    private static final int SCORE_EXACT = 0;
    private static final int SCORE_PREFIX = 1;
    private static final int SCORE_CONTAINS = 2;
    private static final int SCORE_ALL_TOKENS_IN_TITLE = 3;
    private static final int SCORE_ALIAS = 4;
    private static final int SCORE_ALL_TOKENS_IN_FIELD = 5;
    private static final int SCORE_ONE_TOKEN_IN_TITLE = 6;
    private static final int SCORE_ONE_TOKEN_IN_FIELD = 7;
    private static final int SCORE_DESCRIPTION = 8;
    private static final int SCORE_CATEGORY = 9;
    private static final int SCORE_ADDON = 10;
    private static final int SCORE_TYPO_TITLE = 20;
    private static final int SCORE_TYPO_FIELD = 25;

    /**Ein Setting (oder eine Gruppe) eines Moduls, durchsuchbar nach Titel und Beschreibung.*/
    public record Field(String title, String description, String group) {}

    /**Ein durchsuchbares Modul. Bewusst ein Interface, damit die Logik testbar bleibt.*/
    public interface Entry {
        String title();
        String[] aliases();
        String category();
        String addon();
        List<Field> fields();
    }

    /**Ein bewerteter Treffer mit Begruendung fuer die Anzeige ("Setting: min-damage").*/
    public record Hit<T>(T entry, int score, String reason, boolean strong) {}

    /**Treibwerte der Suche - pro Zeichensatz aus dem Modul lesbar.*/
    public static final class Options {
        public int typoTolerance = 2;
        public boolean searchDescriptions = true;
        public int limit = 0;
    }


    private SmartSearch() {}
    /**
     * Bewertet alle Kandidaten gegen die Suchanfrage.
     *
     * @param query    Suchtext, wird getrimmt und in Kleinbuchstaben verglichen
     * @param entries  Kandidatenmenge
     * @param options  Tippfehler-Toleranz, Beschreibungen, Ergebnisgrenze
     * @return sortierte Trefferliste: starke Treffer zuerst, danach thematisch passende
     *         schwache Treffer (Kategorie/Addon/einzelner Begriff). Reine Tippfehler-Treffer
     *         erscheinen nur, wenn es sonst gar nichts gibt.
     */
    public static <T extends Entry> List<Hit<T>> search(String query, Collection<T> entries, Options options) {
        Options o = options != null ? options : new Options();
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty() || entries == null || entries.isEmpty()) return Collections.emptyList();

        String[] tokens = q.split("\\s+");
        List<Hit<T>> strong = new ArrayList<>();
        List<Hit<T>> weak = new ArrayList<>();

        for (T entry : entries) {
            Hit<T> hit = evaluate(entry, q, tokens, o);
            if (hit == null) continue;
            (hit.strong() ? strong : weak).add(hit);
        }

        strong.sort((a, b) -> Integer.compare(a.score(), b.score()));
        weak.sort((a, b) -> Integer.compare(a.score(), b.score()));

        // Der eigentliche Fix gegen "passende Module fehlen": sortieren statt filtern, aber
        // Fehltreffer konsequent entfernen.
        // Aufbau in zwei Ebenen: starke Treffer plus thematisch passende schwache Treffer
        // (Alias, Setting, Beschreibung, Kategorie, Addon). Reine Teilbegriffs- und
        // Tippfehler-Treffer sind Rueckfall und erscheinen nur, wenn sonst nichts passt -
        // genau die Balance, die Meteor mit seiner reinen Distanzsortierung verliert.
        List<Hit<T>> result = new ArrayList<>(strong);
        if (result.isEmpty()) {
            result.addAll(weak);
        } else {
            for (Hit<T> hit : weak) {
                if (hit.score() == SCORE_ONE_TOKEN_IN_TITLE || hit.score() == SCORE_ONE_TOKEN_IN_FIELD) continue;
                if (hit.score() >= SCORE_TYPO_TITLE) continue;
                result.add(hit);
            }
        }
        if (o.limit > 0 && result.size() > o.limit) result = new ArrayList<>(result.subList(0, o.limit));
        return result;
    }

    /**Bewertet genau einen Kandidaten; {@code null} heisst "kein Treffer".*/
    private static <T extends Entry> Hit<T> evaluate(T entry, String q, String[] tokens, Options o) {
        String title = lower(entry.title());
        int best = NO_MATCH;
        String reason = null;

        // 1) Modulname - exakt / Praefix / Enthalten / alle Begriffe
        if (title.equals(q)) {
            best = SCORE_EXACT;
        } else if (title.startsWith(q)) {
            best = SCORE_PREFIX;
        } else if (title.contains(q)) {
            best = SCORE_CONTAINS;
        } else if (containsAllTokens(title, tokens)) {
            best = SCORE_ALL_TOKENS_IN_TITLE;
        }
        if (best != NO_MATCH) return new Hit<>(entry, best, null, true);

        // 2) Aliase (Meteor unterstuetzt sie ueber Config.moduleAliases)
        String[] aliases = entry.aliases();
        if (aliases != null) {
            for (String alias : aliases) {
                String a = lower(alias);
                if (a.equals(q) || a.startsWith(q) || a.contains(q)) {
                    return new Hit<>(entry, SCORE_ALIAS, "Alias: " + alias, true);
                }
            }
        }

        // 3) Settings - Titel und (optional) Beschreibung
        String fieldReason = null;
        int fieldScore = NO_MATCH;
        List<Field> fields = entry.fields();
        if (fields != null) {
            for (Field f : fields) {
                String ft = lower(f.title());
                if (ft.isEmpty()) continue;

                if (best == NO_MATCH && containsAllTokens(ft, tokens)) {
                    fieldScore = SCORE_ALL_TOKENS_IN_FIELD;
                    fieldReason = "Setting: " + f.title();
                } else if (best == NO_MATCH && ft.contains(q)) {
                    fieldScore = SCORE_ALL_TOKENS_IN_FIELD;
                    fieldReason = "Setting: " + f.title();
                } else if (fieldScore > SCORE_ONE_TOKEN_IN_FIELD && containsAnyToken(ft, tokens)) {
                    fieldScore = SCORE_ONE_TOKEN_IN_FIELD;
                    fieldReason = "Setting: " + f.title();
                }

                if (o.searchDescriptions && f.description() != null && best == NO_MATCH) {
                    String fd = lower(f.description());
                    if (!fd.isEmpty() && (fd.contains(q) || containsAnyToken(fd, tokens))) {
                        if (fieldScore > SCORE_DESCRIPTION) {
                            fieldScore = SCORE_DESCRIPTION;
                            fieldReason = "Beschreibung: " + f.title();
                        }
                    }
                }
            }
        }

        if (best == NO_MATCH && fieldScore != NO_MATCH) {
            return new Hit<>(entry, fieldScore, fieldReason, fieldScore == SCORE_ALL_TOKENS_IN_FIELD);
        }

        // 4) Schwache Treffer: einzelne Begriffe im Modulnamen
        if (best == NO_MATCH && containsAnyToken(title, tokens)) {
            return new Hit<>(entry, SCORE_ONE_TOKEN_IN_TITLE, null, false);
        }

        // 5) Kategorie und Addon
        String category = lower(entry.category());
        if (best == NO_MATCH && !category.isEmpty() && (category.contains(q) || containsAnyToken(category, tokens))) {
            return new Hit<>(entry, SCORE_CATEGORY, "Kategorie: " + entry.category(), false);
        }
        String addon = lower(entry.addon());
        if (best == NO_MATCH && !addon.isEmpty() && (addon.contains(q) || containsAnyToken(addon, tokens))) {
            return new Hit<>(entry, SCORE_ADDON, "Addon: " + entry.addon(), false);
        }

        // 6) Tippfehler-Toleranz - bewusst ganz hinten, damit sie nie einen echten Treffer verdraengt
        int tolerance = Math.max(0, o.typoTolerance);
        if (best == NO_MATCH && tolerance > 0) {
            int d = windowDistance(title, q);
            if (d <= tolerance) return new Hit<>(entry, SCORE_TYPO_TITLE + d, "Aehnlich: " + entry.title(), false);
            if (fields != null) {
                int bestField = NO_MATCH;
                String bestFieldName = null;
                for (Field f : fields) {
                    int fd = windowDistance(lower(f.title()), q);
                    if (fd < bestField) {
                        bestField = fd;
                        bestFieldName = f.title();
                    }
                }
                if (bestField <= tolerance) {
                    return new Hit<>(entry, SCORE_TYPO_FIELD + bestField, "Aehnlich: " + bestFieldName, false);
                }
            }
        }

        return null;
    }

    /**
     * Kleinste Levenshtein-Distanz zwischen der Suchanfrage und einem gleich langen
     * Teilfenster des Textes. Ohne Fenstervergleich kann ein langer Modulname nie als
     * Tippfehler-Treffer gelten ("CrystalAura" gegen "crysatl" waere 4 statt 2).
     */
    static int windowDistance(String text, String query) {
        if (text == null || query == null || text.isEmpty() || query.isEmpty()) return NO_MATCH;

        int best = distance(text, query);
        int n = query.length();
        for (int start = 0; start + n <= text.length() && best > 0; start++) {
            best = Math.min(best, distance(text.substring(start, start + n), query));
        }
        return best;
    }

    private static String lower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }

    private static boolean containsAllTokens(String haystack, String[] tokens) {
        for (String t : tokens) if (!haystack.contains(t)) return false;
        return tokens.length > 0;
    }

    private static boolean containsAnyToken(String haystack, String[] tokens) {
        for (String t : tokens) if (haystack.contains(t)) return true;
        return false;
    }

    /**Levenshtein-Distanz mitAbbruch, sobald die Zeile minimal moegliche Distanz erreicht.*/
    public static int distance(String a, String b) {
        if (a == null || b == null) return NO_MATCH;
        if (a.equals(b)) return 0;
        if (a.isEmpty()) return b.length();
        if (b.isEmpty()) return a.length();

        int[] prev = new int[b.length() + 1];
        int[] curr = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;

        for (int i = 1; i <= a.length(); i++) {
            curr[0] = i;
            int rowMin = curr[0];
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
                rowMin = Math.min(rowMin, curr[j]);
            }
            if (rowMin > b.length()) return NO_MATCH;
            int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }
        return prev[b.length()];
    }
}
