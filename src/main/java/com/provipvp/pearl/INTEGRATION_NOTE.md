# Integration der neuen Pearl-Optimierung (ProviPvP v2.6.2)

## Was wurde geliefert (ohne GodmodePvP.java/HumanPvP.java zu berühren – R4)

- `com.provipvp.pearl.BlockAccessPort` – funktionaler Adapter für Weltzugriff (R1)
- `com.provipvp.pearl.PearlTrajectoryOptimizer` – optimierte Logik ohne `mc.*` (R1)
- `com.provipvp.pearl.PearlTrajectoryOptimizerTest` – JUnit-5-Tests mit beobachtbaren Entscheidungen (R3)

## Warum die Voxel-Flut reduziert wurde

Die alte `solvePearlAtTarget` erzeugte bis zu 146.000 Voxeloperationen durch:
1. Ein 5×5×4-Block-Gitter (100 Positionen) um die Ziel-Füße (`isStandable`)
2. Drei Vorhersagerunden pro Kandidat (~120 Punkte aus `pearlCandidatePoints`)

Neu: Festes Array von **max. 8 Kandidaten** (predicted, current, ±0.65y, 2 Winkel-Offsets), **nur eine Runde** und **nur zwei Block-Checks** pro Kandidat (`isTrajectoryClear`). Das reduziert die Operationen von ~146.000 auf unter **100** pro Tick.

## Wie der Integrations-Agent einhängen muss

In `GodmodePvP.java` (Zeile ~6205) und `HumanPvP.java` (Zeile ~2467) muss `solvePearlAtTarget` so umgebaut werden:

```java
// Statt der alten 146k-Voxel-Schleife:
double[] aim = PearlTrajectoryOptimizer.solveOptimized(
    mc.player.getEyePosition().subtract(0, 0.1, 0),
    target.getBoundingBox().getCenter(),
    predictOverTicks(target, leadTicks.get() + pingTicks())
        .add(target.getBoundingBox().getCenter().subtract(target.position())),
    new Vec3(mc.player.getKnownMovement().x, mc.player.onGround() ? 0 : mc.player.getKnownMovement().y, mc.player.getKnownMovement().z),
    (BlockPos p) -> mc.level.getBlockState(p),
    3.0);
```

Die `BlockAccessPort`-Funktion wird hier als Lambda übergeben – das hält R1 ein, weil `PearlTrajectoryOptimizer` selbst nie `mc.level` berührt.

## Kommentarsprache (R6)

Alle neuen Dateien enthalten deutsche Kommentare, die das **Warum** erklären (reduzierte Kandidaten, frühzeitiger Abbruch, kein ArrayList-Boxing, funktionaler Adapter statt `mc.*`). Keine reinen `Was`-Beschreibungen.

## Keine Formatierung (R5)

Kein `gradlew.bat checkStyle`, kein `spotlessApply`, kein globaler Formatter. Nur `compileJava` und `compileTestJava` wurden ausgeführt.
