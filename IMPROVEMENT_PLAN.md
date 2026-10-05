# ProviPvP Improvement Plan

Stand: 1. Oktober 2026 · Alle Statusangaben unten sind gegen den Code geprüft, nicht aus dem Gedächtnis übernommen.

## Target Servers: 2b2t, 5b5t, donutsmp
## Config: JSON | Debug: In-HUD-Overlay | Team: Custom

Add-on: `com.provipvp` auf Meteor Client 26.2 (Java 25, Mojmap-Mappings, Fabric Loader 0.19.3, Loom 1.17-SNAPSHOT).

**Reichweite der Anti-Cheat-Arbeit — bitte nicht verwechseln:**

| Server | Anti-Cheat | Konsequenz |
|---|---|---|
| 2b2t | eigene AC, Schwerpunkt Bewegung, Cheats ausdrücklich erlaubt | Härtung bringt dort nichts |
| 5b5t | **keine** In-Game-AC, nur Whitelist-Antibot | Härtung bringt dort nichts |
| donutsmp | Grim-Familie | **hier gehört die gesamte Härtung hin** |

Deshalb liegt die Härtung ausschließlich im `donutsmp`-Profil. `2b2t`, `5b5t` und `arena-aggressive`
schalten sie explizit ab — dort ist jede Reaktionszeit, die sie kostet, verschenkter Schaden.

---

## ✅ WEEK 1: Critical Fixes — IMPLEMENTIERT

- [x] Rotation-Queue-Starvation (`realActionThisTick`)
- [x] D-Tap-Doppelzündler-Race — wartet auf Server-`EntityAdded`
- [x] Anchor-Charge-Leak beim Dimensionswechsel
- [x] `bedSelfDamageMultiplier` in beiden Modulen
- [x] `InvHelper.find()`-Offhand-Race (`isHotbarOrOffhand`)
- [x] `ProfileManager` + `ProfileCommand` + 5 Default-Profile

---

## ✅ WEEK 2: Architecture — ABGESCHLOSSEN, aber anders als geplant

Der Plan sah vor, `CombatCore` als abstrakte Basis einzuziehen. **Das ist nicht geschehen und wäre
auch nicht möglich gewesen:** `CombatCore` wurde nie benutzt. Die Extraktion erzeugte rund 600 Zeilen
Klasse plus 42 Unit-Tests, die **keinen einzigen Laufzeitpfad erreichten** — `GodmodePvP` baute sich
seinen eigenen `ExplosionScanner` und rief ihn nie ab, `HumanPvP` hatte eigene Inline-Strahlprüfungen.

Belegt per Referenzzählung über den gesamten Quellbaum, vor dem Aufräumen:

| Klasse | Referenzen außerhalb der eigenen Datei |
|---|---|
| `CombatPipeline`, `CombatCore` | 0 |
| `TargetSelector`, `InventoryManager` | 0 |
| `RotationQueue`, `CombatExecutor` | 0 |
| `PearlSolver`, `PearlAim`, `PearlScenario` | 0 (nur ein Javadoc-`{@code}`-Verweis in `GodmodePvP`) |
| `ExplosionScanner` | 0 Abfragen — nur `markTick` + `onBlockUpdate` |

**Erledigt:**

- [x] `CombatCore`, `CombatPipeline`, `TargetSelector`, `InventoryManager`, `TerrainProbe`, `core/events/*` — **gelöscht**
- [x] `RotationQueue`, `CombatExecutor`, `pearl/*` — **gelöscht**
- [x] `terrain/ExplosionScanner` + `RaycastCache` — **behalten und lebendig gemacht** (siehe WEEK 3)
- [x] `ExplosionScanner.countNearbyBlocks` war entgegen seinem eigenen Javadoc boxend (`IntStream.boxed()`
      allozierte 1331 `Integer` pro Aufruf) — auf indizierte Schleifen umgestellt
- [x] `PvpMath`, `InvHelper`, `PitchVariance`, `SmartSearch`, `ProfileManager` — waren und sind verdrahtet

**Konsequenz für künftige Arbeit:** Nach jedem Hinzufügen neuer Klassen wird per `grep` über
`src/main/java` geprüft, dass jede neue Klasse außerhalb der eigenen Datei referenziert wird. Genau
an dieser Prüfung ist der tote Cluster von 2026-09 sichtbar geworden.

---

## ✅ WEEK 3: Performance & Anti-Cheat — IMPLEMENTIERT

### Performance (gemessene Kosten, nicht geschätzt)
- [x] `maintainNearbyAnchors` — 9×7×9 = **567 `getBlockState` pro Tick**; jetzt über `ScanBudget`
      gedrosselt (jeder 4. Lauf) und mit wiederverwendetem `MutableBlockPos` allokationsfrei
- [x] `countNearbyBlocks(feet, 5)` **2× pro Tick = 2662 `getBlockState` + 2662 `BlockPos`**; jetzt
      gedrosselt und an den Scanner delegiert
- [x] Raycast-Ergebnisse in `ExplosionScanner`/`RaycastCache` — vorhanden, jetzt tatsächlich benutzt
- [x] `onDeactivate` dereferenzierte `mc.player` ungeschützt (NPE über `onGameLeft`) — behoben
- [x] `releaseCombatSlot` stellte Baritone-Follow nie wieder her (asymmetrisches Fenster) — behoben
      durch die referenzgezählte `PathLease`
- [x] `HumanPvP` löste die volle Perlen-Trajektorie **zweimal** pro Wurf — behoben
- [ ] `calcBestAnchor` alloziert pro Aufruf 3 `ArrayList` + Integer-Boxing (B12) — **offen**
- [ ] `solvePearlAtTarget` — bis ~117 000 `simulatePearl` + ~29 000 `Level#clip` in einem Tick
      (122 Kandidaten × 3 Runden × 8×40-Bisekktion) — **offen, der größte verbleibende Posten**

### Anti-Cheat
- [x] `GcdRotator` — Winkel auf das Sensitivity-Gitter quantisiert, Jitter gegen `DuplicateRotPlace`
- [x] `ActionRayValidator` — jede Aktion gegen die **gesendete** Rotation geprüft, nicht gegen die Kamera
- [x] `PlaceCursorSolver` — Cursor und Blockseite aus der gesendeten Rotation
- [x] `ReachPolicy` — Melee/Crystal-Abau **3.0** und Blockplatzierung **4.5** als benannte Konstanten;
      die Verwechslung war vorher latent vorhanden
- [x] `ActionCadence` — eine Aktion je Movement-Paket, Item-Use vorher freigeben, Slot-Swap vorher
- [x] `AttackDispatcher` — INTERACT vor ANIMATION
- [x] `TickRateGate` — Drosselung bei Lag
- [ ] `_RANDOMISIERUNG_` der D-Tap-/Anchor-Delays über `RandomBetween` — verdrahtet, Default-Spannen
      stehen in den Profilen

### Benchmarks & Tests
- [x] **88 → 253+ Tests.** Neu: `GcdRotatorTest`, `ActionRayValidatorTest`, `PlaceCursorSolverTest`,
      `ReachPolicyTest`, `CrystalScorerTest`, `AttackGateTest`, `SelfDamageGuardTest`,
      `CrystalToolPolicyTest`, `CrystalOwnershipTest`, `SpearModelTest`, `SlowFallingArrowTest`,
      `WindChargeModelTest`, `KnockbackModelTest`, `AttackDispatcherTest`, `TotemEventReaderTest`,
      `TickRateGateTest`, `ActionCadenceTest`, `RandomBetweenTest`, `ScanBudgetTest`,
      `ProfileManagerTest`, `ActionBrokerTest`, `BrokerCoordinationTest`
- [ ] JMH-Benchmarks — **offen**
- [ ] Integrationstest-Skript — **offen**

---

## ✅ WEEK 4: Kollisionsvermeidung zwischen Modulen

Der Nutzerauftrag lautete: neue Module dürfen sich nicht gegenseitig blockieren. Daraus:

- [x] `ActionBroker` — pro Tick genau ein Besitzer je `ActionKind`. Preemption nur, solange der
      bisherige Besitzer **noch nicht gefeuert** hat; danach wird abgelehnt, weil das Paket unterwegs ist
- [x] `PathLease` — referenzgezählte Baritone-Sperre (behebt das asymmetrische Fenster aus WEEK 3)
- [x] `ConflictRegistry` — zwei Modi: `EXCLUSIVE` (das neue Modul wird abgewiesen) und `DEFERS`
      (beide laufen, das schwächere tritt zurück)
- [x] `PvpServices` — einzige Instanz, damit sich zwei Module überhaupt sehen
- [x] `PvpBrokerSystem` — Tick-Reset und Freigabe der Pfad-Sperre bei Weltwechsel

---

## 🔴 Offen und wichtig

**1. Nichts davon ist im Gefecht gesehen.** Build und Tests sind grün — das ist ein Compile- und
Testbeweis, kein Kampfbeweis. Der lokale Testserver teleportiert Testspieler, taugt also nicht für
isolierte Prüfungen. Siehe `TESTPLAN.md`.

**2. `solvePearlAtTarget` ist weiterhin der teuerste Pfad im Bot.** Bis ~146 000 Voxeloperationen in
einem einzigen Tick, alle acht Ticks. Das ist der nächste Performance-Posten, der sich lohnt.

**3. Nicht verdrahtete Settings in `HumanPvP`.** Das Modul hat 89 Settings weniger als `GodmodePvP`.
Ob das Absicht ( schlankeres Profil) oder Versehen ist, war nie entschieden — siehe `FEATURES.md`.

**4. `BrandSpoof`** ist nach Grim-Default-Konfiguration nutzlos (Grim ignoriert `fabric`/`vanilla`).
Entfernen oder nur mit Mod-List-Gate behalten. Nicht entschieden.

---

## 📌 Stand vom 2026-09-30 (Session dih-src)

Diese Arbeiten betreffen **nicht** das ProviPvP-Add-on, sondern den DIH/Kui-Client in `dih-src/`.
Hier festgehalten, damit sie nicht verloren gehen. Details siehe `dih-src/MANUAL.md` und
`dih-src/README.md`.

### Design: Catppuccin Mocha
- `UiColors`, `CompactTheme`, `KuiColors` auf Catppuccin Mocha umgestellt; ~60 Literale in 21 Dateien nachgezogen
- Fenster: 4px Eckenradius + Haarlinien-Rand. `UiRenderer.roundRect` ist eine Pill-Sprite-9-Slice,
  deren Endkappen mit der Höhe skalieren — `roundedRect`/`roundedFrame`/`roundedRectTop` ergänzt
- Markenname `夔` (U+5914) statt „Kui" an 162 Stellen in 60 Dateien

### Intent-Exposure (CrystalAura / AnchorAura)
Der Code war nicht kompilierbar und ist repariert: `AnchorAuraModule`, `KuiExplosionDamage`,
`PlanDefend` → `ObsidianPlan`.

### Chat-Befehle
`KuiChatSuggestMixin` trennt jetzt Nutzungszeile, Vorschlagsliste und Fehlerbehandlung.

### Verteilstand
`gradlew build` grün, 1221 Tests. Build-Artefakt installiert als `%APPDATA%/.minecraft-262pvp/mods/kui-5.0.1.jar`.

### Noch offen
- [ ] `KuiDisconnectedScreenMixin` — Sichtbarkeit per Screenshot **nicht** verifiziert
- [ ] `autism-client/` liegt als nicht getrackter Fork im Repo (entschieden: nicht löschen, nicht anfassen)

**Sicherung:** beide `dih-src`-Dateien liegen als `*.bak-sprint` daneben (`dih-src` ist nicht in git).
