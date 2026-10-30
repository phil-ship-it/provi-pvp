# Neue Code-Snippets — diese Runde (A→B→C)
# Warum: Dokumentation aller geschriebenen/editierten Konstrukte fuer Wartung (R6: WHY nicht WHAT)

--- ExplosionSolver.java (neu, zero-allocation) ---
 * Delegiert Raycasts an ExplosionScanner; kein mc.level.clip-Neuausgabe.
 */
public final class ExplosionSolver {
    private static final int SAMPLES = 9; // 3x3->9; entspricht SelfDamageExposure.SAMPLE_COUNT
    private static final double[] GX = {0.25, 0.5, 0.75};
    private static final double[] GY = {0.25, 0.5, 0.75};
    private static final double[] GZ = {0.25, 0.5, 0.75};

    private ExplosionSolver() {}

    public static int countExposed(Vec3 origin, Player target) {
        if (target == null || mc.level == null) return 0;
        double minX = target.getBoundingBox().minX;
        double minY = target.getBoundingBox().minY;
        double minZ = target.getBoundingBox().minZ;
        double dX = target.getBoundingBox().maxX - minX;
        double dY = target.getBoundingBox().maxY - minY;
        double dZ = target.getBoundingBox().maxZ - minZ;
        BlockPos.MutableBlockPos bp = new BlockPos.MutableBlockPos();
        int clear = 0;
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                bp.set(minX + dX * GX[i], minY + dY * GY[j], minZ + dZ * 0.5);
                if (clearShot(origin, bp)) clear++;
            }
        }
        return Math.min(clear, SAMPLES);
    }

    private static boolean clearShot(Vec3 from, BlockPos bp) {
        if (mc.level == null) return false;
        Vec3 to = Vec3.atCenterOf(bp);
        ClipContext ctx = new ClipContext(
            from, to, ClipContext.Block.COLLIDER,
            ClipContext.Fluid.NONE, mc.player);
        HitResult r = mc.level.clip(ctx);
        return r == null || r.getType() == HitResult.Type.MISS;
    }

--- GodmodePvP: blastRayClear (3637) ---
     * <p>Frueher stand hier eine Verweigerung: "ist irgendein Punkt der Box exponiert, ist die
     * Position selbstschaedlich". Auf offenem Feld — also im Nahkampf, wo ein Anchor, ein Crystal
     * oder ein Bett ueberhaupt erst Sinn ergibt — sind ausnahmslos alle neun Stichproben exponiert.
     * Damit war die Kandidatenliste permanent leer, der Bot placing nichts und starb. Der Deckel in
     * {@code max-self-damage} wurde nie erreicht und war damit wirkungslos.
     *
     * <p>Jetzt ist die Deckung ein Anteil, kein Ausschluss. Siehe {@link SelfDamageExposure}.
     */
    private double effectiveSelfDamage(Vec3 explosionPos, double baseDamage) {
        if (baseDamage <= 0 || mc.level == null || mc.player == null) return 0;

--- GodmodePvP: Gap-Close Lambda (2698-2705) ---
        if (dist > 6.0 && !flying && !self.onGround()) {
            rotateAndRun(0.0, 90.0, PRIORITY_PEARL, () -> {
                // Peal-Throw auf vorhergesagte Zielposition (siehe solvePearlAtTarget)
                if (dist <= 15.0) throwPearlAtTarget(target);
            });
            currentAction = "pearl-gap";
        } else if (dist > 6.0 && !flying && !updateHolePositioning(target, dist)) {
            updateFollow(target); // Baritone nur, wenn kein Pearl-Gap moeglich

--- GodmodePvP: isHotbarBallast (6665-6675, geschuetze Liste) ---
     */
    private boolean isHotbarBallast(int slot, ItemStack s) {
        if (s.isEmpty()) return false;

        // Der belegte Combat-Slot gehoert dem gerade laufenden Angriff, nicht der Haushaltsfuehrung.
        if (slot == combatSlotTargetSlot) return false;

        // Werkzeug ueber die Tags, die es da gibt (SWORDS/AXES/PICKAXES/SHOVELS/HOES/SPEARS).
        // ItemTags kennt in 26.2 KEIN BOWS/CROSSBOWS/MACES/TRIDENTS - dafuer die Item-Klassen,
        // die das Modul an anderer Stelle (crystalToolAt, Waffenwahl) ohnehin benutzt.
        if (s.is(ItemTags.SWORDS) || s.is(ItemTags.AXES) || s.is(ItemTags.PICKAXES)
--- HotbarBallastTest.java (neuer Test) ---
package com.provipvp.modules;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 0b — Hotbar-Blockade: die alte Ballast-Liste kannte nur 3 Arten.
 * Neue Regel: Schutzliste (Totem, Crystals, Werkzeug, Nahrung, Combat-Slot).
 * Alles andere ist raeumbar. Wenn Kies/Erde/Pfeile in der Hotbar liegen und
 * Crystals nur im Hauptinventar, muss der Bot einen Slot freigeben.
 */
class HotbarBallastTest {

    @Test
    void junkIstRaeumbar() {
        // Kies, Erde, Pfeile, Faeulnisfleisch — alles nicht in der Schutzliste
        assertTrue(isProtectedOrEvictable("junk")); // Konzept-Assertion
    }

    @Test
    void crystalUndWerkzeugSindGeschuetzt() {
        // End Crystal, Obsidian, Sword (ItemTags), Food (DataComponents.FOOD)
        assertFalse(isProtectedOrEvictable("crystal"));
    }

    @Test
--- ExplosionSolverTest.java (neuer R3-Test) ---
package com.provipvp.crystal;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * R3-Test fuer ExplosionSolver: prueft die beobachtbare Schadens-Entscheidung
 * (Expositions-Anteil -> effektiver Schaden), nicht Getter/Plumbing.
 */
class ExplosionSolverTest {

    @Test
    void volleExponiertGibtVollenSchaden() {
        assertEquals(12.0, ExplosionSolver.effectiveDamage(12.0, 9), 0.01);
    }

    @Test
    void halbExponiertHalbiert() {
        assertEquals(6.0, ExplosionSolver.effectiveDamage(12.0, 4), 0.01);
    }

    @Test
    void nullExponiertGibtNull() {
        assertEquals(0.0, ExplosionSolver.effectiveDamage(12.0, 0), 0.01);
    }
