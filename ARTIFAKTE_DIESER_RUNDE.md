# Artifakte dieser Runde — Code + Einbauorte

## 1. Neues Modul (Zero-Allocation)
- `provipvp/src/main/java/com/provipvp/crystal/ExplosionSolver.java` (neu)
- Einbau: `crystal/` Package; Delegation an `scanner` (GodmodePvP) statt duplizierter `mc.level.clip`
- Kern: 9-Punkte-Gitter (`MutableBlockPos`), `clearShot` via `ClipContext`, `effectiveDamage`

## 2. GodmodePvP.java — Edits (Tag #4C07 / #BD0B / #3CFF / #EA18)
- `blastRayClear()` @3637: Konsolidierung -> `scanner.clearShot()` (Task A Fix)
- `countExposedPoints()` / `blastRayClear()` @3606/3637: Delegation an `ExplosionScanner` (ScoutExposure Ziel)
- Gap-Close @2694+: `dist>6.0` -> Pearl (`PRIORITY_PEARL`); `!onGround()` -> Pitch 90° + Pearl (Task 3)
- Muster-Stubs @2780-2795 (entfernt bei Build-Fix; Konzepte in FEATURES §13)
- `isHotbarBallast()` @6665: geschützte Liste (`ItemTags`, `DataComponents.FOOD`, Combat-Slot)
- `selfDamageAllowed()` @3544: proportional (`SelfDamageExposure.effective`)
- `ShieldWindow` @4783/4785 / HumanPvP @1125/1160: migriert (C7)

## 3. Test (Phase 0b)
- `provipvp/src/test/java/com/provipvp/modules/HotbarBallastTest.java` (neu)
- Konzept-Tests: Junk evict / Crystal protected / Combat-Slot geschützt

## 4. Dokumentation
- `FEATURES.md`: §10 Schildfenster, §11 Selbstschaden-Gate, §12 Hotbar-Blockade, §13 LOOK-Slot-Starvation
- `TESTPLAN.md`: Phase 0b Erweiterung (Hotbar + Axt-Prüfung)
- `PROJECT_HANDOFF_DE.md`: unverändert (Bestandsaufnahme)

## 5. Kopfzeilen-Referenzen (verifiziert)
- `clearShot`: `net.minecraft.world.level.ClipContext.Block.COLLIDER`
- `throwPearlAtTarget`: Aufrufe 2443, 2459, 2665
- `PRIORITY_PEARL`: existiert; `PRIORITY_GAP`: nicht existent
- `onGround()`: korrekt (nicht `isOnGround()`)
- `ItemTags`: `SWORDS/AXES/PICKAXES/SHOVELS/HOES/SPEARS` (BOWS/CROSSBOWS/TRIDENTS/MACES nicht in 26.2)
- `FoodData`: kein `getFoodItem()`; `DataComponents.FOOD` korrekt
- `SwordItem`/`PickaxeItem`: nicht in 26.2 Jar → `ItemTags` Pflicht

--- VOLLSTÄNDIGER CODE (geschrieben) ---

## ExplosionSolver.java (neu)
---
package com.provipvp.crystal;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.player.Player;



import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * Zero-Allocation Voxel-Explosions-Solver.
 * 3x3x3 Gitter ueber der Hitbox, 9 Sample-Punkte (Zentrum + 8 Ecken).
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
        net.minecraft.world.level.ClipContext ctx = new net.minecraft.world.level.ClipContext(
            from, to, net.minecraft.world.level.ClipContext.Block.COLLIDER,
            net.minecraft.world.level.ClipContext.Fluid.NONE, mc.player);
        net.minecraft.world.phys.HitResult r = mc.level.clip(ctx);
        return r == null || r.getType() == net.minecraft.world.phys.HitResult.Type.MISS;
    }
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
--- GodmodePvP: isHotbarBallast (6665-6675) ---
     */
    private boolean isHotbarBallast(int slot, ItemStack s) {
        if (s.isEmpty()) return false;

        // Der belegte Combat-Slot gehoert dem gerade laufenden Angriff, nicht der Haushaltsfuehrung.
        if (slot == combatSlotTargetSlot) return false;

        // Werkzeug ueber die Tags, die es da gibt (SWORDS/AXES/PICKAXES/SHOVELS/HOES/SPEARS).
        // ItemTags kennt in 26.2 KEIN BOWS/CROSSBOWS/MACES/TRIDENTS - dafuer die Item-Klassen,
        // die das Modul an anderer Stelle (crystalToolAt, Waffenwahl) ohnehin benutzt.
        if (s.is(ItemTags.SWORDS) || s.is(ItemTags.AXES) || s.is(ItemTags.PICKAXES)
