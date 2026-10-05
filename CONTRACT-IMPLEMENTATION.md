# ProviPvP — Implementierungs-Contract

Gilt für ALLE Subagenten. Bei Widerspruch zu einer Task-Beschreibung gilt diese Datei.

## Projektkontext

- Addon `com.provipvp` für **Meteor Client 26.2-SNAPSHOT**, Minecraft **26.2**, **Java 25**, Fabric Loader 0.19.3, Loom 1.17-SNAPSHOT.
- **Mappings: Mojang official, NICHT Yarn.** `net.minecraft.core.BlockPos`, `net.minecraft.world.level.BlockGetter`, `net.minecraft.world.level.chunk.LevelChunk`, `net.minecraft.client.Minecraft`. Yarn endet bei 1.21.11 — es gibt kein 26.2.
- Bauen: `cd provipvp && cmd.exe /c gradlew.bat build --console=plain --no-daemon` (nicht `./gradlew`, das existiert unter dieser Shell nicht).
- Tests: `src/test/java/com/provipvp/**`, JUnit 5.11.3. Bestand 88 Tests, alle grün — die dürfen nicht rot werden.

## Harte Regeln

### R1 — Keine `mc.*` und keine Modul-Settings in neuen Klassen
Jede neue Klasse ist **kopflos testbar**. Konkret:
- Kein Zugriff auf `mc.*`, `Minecraft.getInstance()`, `mc.player`, `mc.level`.
- Keine Meteor-`Setting`-Felder. Werte kommen per Konstruktor oder Methodenparameter.
- Weltzugriffe (Block-/Strahl-/Sichtprüfung) kommen als **funktionale Interfaces** herein, z. B. `BiPredicate<Vec3,Vec3> clearShot`, `Function<BlockPos,BlockState> blockAt`.

Begründung: das ist die bestehende Konvention in diesem Repo. `RotationQueue` und `PitchVariance` sind genau so gebaut und dadurch headless testbar.

### R2 — Keine Abhängigkeit vom toten Cluster
`core/CombatCore`, `core/CombatPipeline`, `core/TargetSelector`, `core/InventoryManager`, `core/TerrainProbe`, `core/events/*`, `exec/RotationQueue`, `exec/CombatExecutor`, `pearl/PearlSolver`, `pearl/PearlAim`, `pearl/PearlScenario` werden **gelöscht**. Neue Klassen dürfen sie **nicht** importieren oder referenzieren.

Ausnahmen, die ausdrücklich bleiben: `terrain/ExplosionScanner`, `terrain/RaycastCache` (werden durch B2/B3 lebendig), `exec/PitchVariance` (ist bereits lebendig).

### R3 — Jede neue Klasse braucht Tests
Test muss eine **beobachtbare Entscheidung** prüfen: ein berechneter Winkel, eine Ja/Nein-Gate-Entscheidung, ein Ranking, ein Fehlerfall. **Keine** Tests die prüfen, dass ein Feld existiert oder ein Getter seinen Wert zurückgibt. Keine Tests, die die Implementierung nachstellen.

### R4 — GodmodePvP.java und HumanPvP.java gehören dem Integrations-Agenten
In der Parallelphase wird **keine** der beiden Dateien angefasst. Neue Klassen werden dort nur referenziert, nie editiert.

### R5 — Kein Formatieren, kein Linten, kein Testlauf
Agenten bauen nicht, führen nicht aus und starten keine Projekt-weiten Tasks. Der Integrations-Agent baut einmal am Ende.

### R6 — Kommentarstil
Deutsch, `//` für Zeilenerklärungen, `/** */` für Klassen/Methoden. Eine Begründung, **warum** — nicht was der Code offensichtlich tut.

## Paketstruktur

```
com/provipvp/rotation/    D1  GCD + Jitter
com/provipvp/ray/         D2,D3,D5,D18,D19,D20  Aktions-/Strahlvalidierung
com/provipvp/crystal/     D6,D7,D8,D9,D10       Crystal-Kampfqualität
com/provipvp/mechanics/   D4,D11,D12,D13        Neue PvP-Mechaniken
com/provipvp/net/         D15,D16,D17            Paketebene
com/provipvp/util/        D14                    RandomBetween
com/provipvp/perf/        B2,B3,B11              Scan-Budgetierung
```

com/provipvp/rotation/    D1                GCD-Quantisierung + Jitter
com/provipvp/ray/         D2,D3,D5,D20      Aktions-/Strahlvalidierung, Reichweite, Blockseite
com/provipvp/crystal/     D6,D7,D8,D9,D10   Crystal-Kampfqualität und Gates
com/provipvp/mechanics/   D4,D11,D12,D13    Neue PvP-Mechaniken
com/provipvp/net/         D15,D16,D17,D18,D19  Paketebene, Kadenz, Reihenfolge
com/provipvp/util/        D14                RandomBetween

Jeder Agent besitzt **ausschließlich** die Dateien seines Pakets. Kein Paket wird von zwei Agenten angefasst.
Jeder Agent **rührt** `terrain/` nicht an — das gehört Agent 1 (A1, Löschung).
| Crystal **Abbau**-Reichweite | `ENTITY_INTERACTION_RANGE` / `attack_range` = **3.0**, NICHT 4.5 — zwei verschiedene Attribute |
| Melee-Reichweite Survival | **3.0** (Grim `Reach` Schwelle 3.0005) |
| i-Frames nach Schaden | **0.5 s (10 Ticks)**, Knockback darin komplett ignoriert |
| Schild aktiv nach | **5 Ticks (250 ms)**; Pitch wird ignoriert (kein vertikaler Schutz) |
| Axe-Stun | **5 s**, unabhängig vom Cooldown-Anteil, deaktiviert alle Schilde des Opfers |
| End-Crystal-Explosion | power **6**, entity range 12, block range 10.2, max centre damage 85, Explosionszentrum am **Boden** des Crystals |
| Anchor-Explosion | power **5**, entity range 10, block range 8.4. **Ladelevel ändert den Schaden NICHT** — 1 Glowstone = voller Pop |
| Bett-Explosion | power 5, gleiche Reichweiten. Zentrum auf der **Kopfhälfte**. Explodiert in Nether, End und jeder Dimension mit deaktivierten Betten — **Overworld ist sicher** |
| Obsidian/Anchor/Bedrock/Enderchest | Blast Resistance **1200** → explosionsimmun |
| Perlen **1 s (20 Ticks)** Use-Cooldown |
| Splash-Tränke: 8.25×8.25×4.25 Quader **und** ≤4 Blocks euklidisch; Instant-Effekte fallen linear auf 0 % bei 4 Blocks |
| **Perlen und Splash-Tränke kollidieren mit End Crystals** (Entity-Box ~0.3 aufgeweitet) — eigener Perlenwurf kann eigene Crystals sprengen |
| Wind Charges detonieren seit **1.20.5 keine Crystals** mehr |
| 26.2 „Chaos Cubed" — **keine** PvP-Mechanik geändert (Schwefel/Zinnober, Vulkan, Freundesliste) |
| Angriffs-Cooldown | T = 20/attack_speed; Schwert 12 Ticks, Axt 20–25, Mace 33 |
| Mace-Smash | ≥ **1.5** Blocks Fallhöhe; +4 HP/Block (erste 3), +2 (nächste 5), +1 danach. Slow Falling verhindert Smash vollständig |
| Spear (26er-Area) | Reichweite 4.5, **erzwungener 100 %-Cooldown** beim Jab, kein Crit, kein Sprint-KB, Charge-Angriff braucht ≥4.6 b/s zum Schaden, ≥5.1 b/s zum Knockback, **nicht** durch Stärke verstärkt |
| Grim GCD-Prüfung | `MINIMUM_DIVISOR = 0.0086`; Deltas **>5 oder ==0** werden von der Statistik gar nicht erst gezählt |
| Grim Attack-Reichweite | 3.0005 |
| Grim Place-Cursor | muss in `[0,1]` liegen (1.5 für Lectern/Scaffolding) |
| Grim Interact-Cursor | ±0.3001 horizontal, −0.0001…1.8001 vertikal |
| Meteor `Rotations.rotate()` | **quantisiert NICHT** — speichert rohe `float`s in `serverYaw`/`serverPitch`. GCD muss der Addon selbst machen. |

## Anti-Cheat-Fakten pro Server

| Server | Anti-Cheat | Konsequenz |
|---|---|---|
| **2b2t** | eigene AC, Bewegungs-Fokus, Cheats erlaubt | Hardening nahezu wertlos |
| **5b5t** | **keine** In-Game-AC, nur Whitelist-Antibot | Hardening nahezu wertlos |
| **donutsmp** | Grim-Familie | **hier gehört das Hardening hin** |

E2 (BrandSpoof-Löschung) ist **nicht** im Scope — der User hat E1 gewählt, nicht E2.

## Nicht-detektierbar (nicht dagegen bauen)

- Maus-Input vs. Mod-Rotation: Grim sieht nur Yaw/Pitch-Floats im Movement-Paket. Silent-Rotation ist **nicht direkt beweisbar**, nur ihre statistische Form.
- Keybinds, Settings, GUI, welches Addon ein Paket erzeugt hat.
- Inventar-Swap-Timing innerhalb des Klick-Paketstroms (PacketOrder A/K/L sind experimentell, standardmäßig aus).
- Crystal-Cadence: es gibt keinen dedizierten Cadence-Check, nur das Vanilla-20-TPS-Modell.

## Definition of Done pro Agent

1. Alle Klassen des eigenen Pakets existieren, kompilieren konzeptionell gegen R1/R2.
2. Tests für jede neue Klasse, die eine beobachtbare Entscheidung prüfen.
3. `grep` über das eigene Paket: keine Referenz auf einen geloschten Cluster, keine `mc.*`-Nutzung, keine Settings.
4. Bericht: Liste der erstellten Dateien, der Entscheidungen die sie treffen, und der **exakten Aufrufsite-Stelle** (`GodmodePvP.java:NNNN`), wo der Integrations-Agent einhängen muss.
