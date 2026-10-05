package com.provipvp.crystal;

/**
 * Rechnet den Blast-Anteil der eigenen AABB in eine Schadenszahl um — als <b>Anteil</b>, nicht als
 * Ja/Nein.
 *
 * <p>Vanilla trifft eine Explosion die gesamte Hitbox, nicht deren Mittelpunkt. Wer hinter einer
 * Blockecke steht, hat seinen Mittelpunkt verdeckt und trotzdem Kopf und Schultern offen. Der
 * naheliegende Schluss daraus ist "irgendein Punkt exponiert heisst selbstschaedlich" — und genau
 * dieser Schluss hat jeden Kandidaten im Nahkampf verworfen: auf offenem Feld sind alle neun Stichproben
 * exponiert, also gab es dort nie eine gueltige Anchor-, Crystal- oder Bett-Plaetzung. Der Bot tat
 * nichts und starb.
 *
 * <p>Der Anteil ist die richtige Groesse, weil Vanilla die Schadensreduktion durch Deckung
 * proportional zur freien Hitbox-Flaeche rechnet. Volle Exposition heisst voller Schaden, halbe
 * Exposition etwa der halbe, gar keine keinen. Damit entscheidet wieder der einstellbare Deckel
 * {@code max-self-damage} — und nicht ein Test, dessen Grenzen man nicht einstellen kann.
 *
 * <p>Die Neun Stichproben selbst (Mittelpunkt + acht Ecken der AABB) nimmt das Modul per
 * Block-Raycast; diese Klasse rechnet nur noch. Reine Zahleneingaben, kein {@code mc.*} (R1).
 */
public final class SelfDamageExposure {
    private SelfDamageExposure() {}

    /** Mittelpunkt plus acht Ecken der Spieler-AABB. */
    public static final int SAMPLE_COUNT = 9;

    /**
     * Der Anteil der Stichproben mit freiem Explosionsstrahl, {@code 0.0} bis {@code 1.0}.
     * Werte ausserhalb der Stichprobenzahl werden geklemmt, damit ein Zaehlfehler aus einem
     * Aufrufbereich nicht zu einer Freigabe fuehrt.
     */
    public static double fraction(int exposedSamples) {
        if (exposedSamples <= 0) return 0.0;
        if (exposedSamples >= SAMPLE_COUNT) return 1.0;
        return exposedSamples / (double) SAMPLE_COUNT;
    }

    /**
     * Der Schaden, mit dem die Deckel rechnen.
     *
     * <p><b>Bekannte Grenze:</b> {@code baseDamage} stammt aus Meteors Schadensmodell und haengt am
     * Mittelpunkt der AABB. Ist genau der verdeckt, {@code baseDamage} ist fast null — dann greift
     * diese Korrektur nicht, auch wenn acht Ecken offen sind. Der Anteil kann einen zu niedrigen
     * Ausgangswert nur nach oben korrigieren, wenn er mit einem Schadenswert gefuettert wird, der die
     * volle Exposition abbildet; das tut er hier nicht. Der Fall ist selten (der Spieler muss hinter
     * einer nur einen Block hohen Deckung stehen und die Explosion von oben treffen) und war vorher
     * durch die Verweigerung ohnehin abgedeckt — jetzt bleibt er unbelegt, statt alles zu blockieren.
     *
     * @param baseDamage     von {@code DamageUtils} berechneter Schaden am Mittelpunkt
     * @param exposedSamples Anzahl exponierter Stichproben, {@code 0} bis {@link #SAMPLE_COUNT}
     */
    public static double effective(double baseDamage, int exposedSamples) {
        if (baseDamage <= 0) return 0;
        return baseDamage * fraction(exposedSamples);
    }

    /**
     * Der Schaden, mit dem die Deckel rechnen — Kurzform mit dem Anteil statt der Stichprobenzahl.
     */
    public static double effective(double baseDamage, double exposure) {
        if (baseDamage <= 0) return 0;
        return baseDamage * Math.min(1.0, Math.max(0.0, exposure));
    }

    /** Liegt der wirksame Schaden unter dem Deckel? */
    public static boolean withinCap(double baseDamage, int exposedSamples, double cap) {
        return effective(baseDamage, exposedSamples) <= cap;
    }
}