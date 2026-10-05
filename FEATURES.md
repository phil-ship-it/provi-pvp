# ProviPvP — Feature-Referenz

**Stand: 1. Oktober 2026.** Dieses Dokument ist aus dem Quellcode erzeugt, nicht aus einer früheren
Fassung. Jede Setting-Namen-Angabe wurde gegen die `.name("…")`-Aufrufe im Code geprüft.

Zahlenstand, maschinell aus den Moduldateien gezählt:

| Modul | Settings |
|---|---:|
| GodmodePvP | 117 |
| HumanPvP | 56 |
| WindChargeModule | 8 |
| SlowFallingAura | 7 |
| SpearModule | 7 |
| TrainingDummy | 4 |
| Auto5b5tDupe | 3 |
| PacketLogger | 3 |
| MacroTriggerModule | 2 |
| PacketFilter | 2 |
| AutoArmor | 1 |
| ExploitGuard | 1 |
| BrandSpoof | 1 |
| ProviClickGui | 7 |
| **Summe Module** | **219** |
| ProviDebugOverlay (HUD) | 7 |
| PvpSessionStats (HUD) | 3 |

> **Korrektur einer alten Falschaussage.** Eine frühere Fassung dieses Dokuments behauptete, die beiden
> Kampfmodule hätten „denselben Funktionsumfang". Das ist falsch: GodmodePvP hat **117** Settings,
> HumanPvP **56**. Genau **72** Setting-Namen gibt es nur in GodmodePvP. Die vollständige, geprüfte
> Aufteilung dieser 72 steht in [Setting-Lücke HumanPvP](#setting-lücke-humanpvp).

---

## 1. Aufbau

```
ProviPvPAddon
  ├── Systeme
  │     └── PvpBrokerSystem          tickt den Broker VOR allen Modulen
  ├── Module (14)
  │     ├── GodmodePvP ─────────┐
  │     ├── HumanPvP ───────────┤  Kampfprofile
  │     ├── SpearModule ────────┤  Spezialmodule
  │     ├── WindChargeModule ───┤  (DEFERS gegen die Kampfprofile)
  │     ├── SlowFallingAura ────┘
  │     ├── ProviClickGui, AutoArmor, Auto5b5tDupe, TrainingDummy
  │     └── PacketLogger, PacketFilter, ExploitGuard, BrandSpoof, MacroTriggerModule
  ├── HUD
  │     ├── ProviDebugOverlay      RSHIFT+D
  │     └── PvpSessionStats
  └── Befehle: /pvp, /hpvp, /profile, /proxy, /nbt
```

Reine Entscheidungslogik liegt in headless-testbaren Klassen ohne `mc.*`-Bezug. Die Module selbst
sind dünn: sie sammeln Weltzustand, rufen eine Entscheidungsklasse und senden Pakete.

| Paket | Aufgabe |
|---|---|
| `broker/` | Wer darf in diesem Tick was tun. Kein Weltbezug. |
| `rotation/` | GCD-Quantisierung (`GcdRotator`) |
| `ray/` | Reichweiten (`ReachPolicy`), Sichtlinie (`ActionRayValidator`), Platzierungs-Cursor (`PlaceCursorSolver`) |
| `crystal/` | Auswahl (`CrystalScorer`), Besitz (`CrystalOwnership`), Angriffs-Gate (`AttackGate`), Werkzeugprüfung (`CrystalToolPolicy`), Selbsttod (`SelfDamageGuard`) |
| `mechanics/` | `SpearModel`, `KnockbackModel`, `SlowFallingArrow`, `WindChargeModel`, `ShieldWindow` |
| `net/` | `AttackDispatcher`, `ActionCadence`, `TickRateGate`, `TotemEventReader` |
| `perf/` | `ScanBudget` |
| `terrain/` | `ExplosionScanner`, `RaycastCache` |
| `util/` | `RandomBetween`, `InvHelper`, `SmartSearch`, `PvpMath` |
| `exec/` | `PitchVariance` |

---

## 2. Anti-Blocking: der Broker

Das ist der Teil, der verhindert, dass sich Module gegenseitig blockieren. Er besteht aus vier
Klassen in `broker/`, die **keinen** `mc.*`-Bezug haben und deshalb komplett headless getestet sind.

### `ActionBroker` — wer darf was, in diesem Tick

Pro `ActionKind` gibt es genau **einen** Inhaber. Kinds: `MELEE, CRYSTAL, ANCHOR, BED, PEARL, BLOCK,
PROJECTILE, USE_ITEM, SHIELD, SWAP`.

- `claim(owner, kind, priority)` — belegen. Höhere Priorität darf **nur** verdrängen, solange der
  aktuelle Inhaber noch nicht `spend()` aufgerufen hat. Danach ist die Aktion irreversibel, ein
  Modul, das schon sendet, wird nicht mehr ausgetauscht.
- `spend(owner, kind)` — die Aktion ist raus. Ab hier ist der Slot für den Tick verbraucht.
- `release(owner)` — Modul aus: alle ihre Belegungen und ihre Pfad-Sperre weg.
- `tick()` — Anfang des Ticks, setzt den Zähler zurück.

Der Punkt von `spend` ist der entscheidende: Ein Modul, das gerade einen Crystal zündet, wird **nicht**
mehr von einem schnelleren Modul verdrängt. Sonst würde man den Crystal doppelt zünden oder den
Angriff verlieren.

### `PathLease` — Baritone, referenzgezählt

Mehrere Module wollen Baritone anhalten. `PathLease` zählt mit: derselbe Inhaber, der zweimal
pausiert, zählt einmal. Erst wenn der letzte Inhaber aufgibt, läuft Baritone wieder.
`releaseAll(owner)` räumt bei Deaktivierung und Weltwechsel.

### `ConflictRegistry` — Module, die gar nicht erst starten

`declare(ownerClass, Mode, priority, conflictClasses...)`:

- **`EXCLUSIVE`** — das neue Modul wird abgewiesen.
- **`DEFERS`** — beide laufen, aber das schwächere gibt pro Aktion nach.

Zwei Regeln, die den Testplan nicht überraschen lassen: ein **nicht registriertes** aktives Modul
gewinnt immer, und bei **gleicher** Priorität gewinnt das bereits aktive Modul. Reihenfolge im
Modulmenü spielt also keine Rolle.

### `PvpServices` — der einzige Zugang

Eine gemeinsame Instanz. Prioritäten: `P_CRITICAL=100`, `P_COMBAT=70`, `P_SUPPORT=40`,
`P_OPPORTUNIST=20`. `release(module)` räumt Broker-Belegungen und Pfad-Sperre.

### Reihenfolge in `onInitialize()`

```java
Systems.add(new PvpBrokerSystem());   // MUSS vor allen Modulen
...
Modules.get().add(new GodmodePvP());
```

Steht der Broker **nach** der Modulregistrierung, entscheidet er einen Tick zu spät — und genau im
entscheidenden Moment (Kampfesbeginn) greift die Kollisionsvermeidung dann nicht.

---

## 3. GodmodePvP — 117 Settings

Kampfprofil „maximal". Läuft mit Baritone-Verfolgung, Crystal-/Anchor-/Bed-Aura, D-Tap, Stadtkampf.

### General (5)
`attack-range` (3.6) · `smart-targeting` · `backup-range` · `pop-threshold` · `prediction-ticks`

### 1 · Angriff & Auras (41)
`smart-auras` · `anchor-mode` · `use-anchors` · `use-beds` · `bed-min-damage` ·
`bed-max-self-damage` · `bed-self-damage-multiplier` · `balance-resources` · `aggressive` ·
`piston-aura` · `pre-hit` · `melee-fallback` · `prefer-axe-melee` · `shield-breaker` · `melee-strafe` ·
`sprint-reset` · `track-target` · `crit-jump` · `d-tap` · `use-mace` · `elytra-combat` ·
`gcd-rotation` · `gcd-jitter-steps` · `ray-validate-actions` · `enforce-reach` · `solve-place-cursor` ·
`score-crystals` · `crystal-min-pick-damage` · `crystal-min-tick-age` · `gate-attacks` ·
`min-attack-strength` · `crystal-tool-check` · `spear-aware` · `per-entity-knockback` ·
`slow-falling-plan` · `dispatch-attacks` · `action-cadence` · `dtap-delay-min` · `dtap-delay-max` ·
`anchor-delay-min` · `anchor-delay-max`

#### Die Härtungs-Settings (D-Gruppe, alle Standard **an**)

Das ist der Teil, der Vanilla-Mechanik von geratenen Werten trennt:

| Setting | Was es tut |
|---|---|
| `gcd-rotation` | Quantisiert Yaw/Pitch auf das Mausraster (Divisor 0.0086). Ohne das sendet das Modul rohe Float-Winkel. |
| `gcd-jitter-steps` | Zusätzliche Streuung in Rasterschritten (0 = exakt). |
| `ray-validate-actions` | Prüft jede Aktion gegen die **gesendete** Rotation statt gegen die Kamera. |
| `enforce-reach` | Echte Vanilla-Reichweiten: Nahkampf und **Crystal-Zündung 3.0**, Blockplatzierung 4.5. Die beiden werden nie vermischt. |
| `solve-place-cursor` | Löst die echte Klickfläche für Anker/Bett/Crystal aus der gesendeten Rotation. Ein geratener `BlockHitResult` zeigt bei schrägen Wänden in die Nachbarfläche. |
| `score-crystals` | Wählt den Crystal nach projiziertem Schaden statt nach Listenposition. |
| `crystal-min-pick-damage` | Mindest-projizierter Schaden für die Auswahl (3.0). |
| `crystal-min-tick-age` | Mindestalter, bevor ein eigener Crystal angegriffen wird (0). |
| `gate-attacks` | Gated Angriff durch `hurtTime` und vollen Angriffs-Cooldown; Sprung-Crit nur wenn er töten würde. |
| `min-attack-strength` | Mindest-Angriffsstärke 0..1 (0.9). |
| `lethal-self-damage-guard` | Verwirft jede Platzierung, deren projizierter Eigenschaden die **eigenen aktuellen HP** erreichen würde — unabhängig von `max-self-damage`. Der Deckel ist einstellbar, der eigene Tod nicht. |
| `crystal-tool-check` | Schwingt nicht mit einem Werkzeug, das einem End Crystal nichts zufügt. |
| `spear-aware` | Rechnet mit dem 26er-Spear: 4.5 Reichweite, kein Crit, kein Sprint-KB, Charge ab 4.6 b/s. |
| `per-entity-knockback` | Knockback pro Ziel aus Netherite, Blast-Protection und Resistenz. |
| `slow-falling-plan` | Plant gegen Ziele unter Slow Falling (Mace-Smash unerreichbar). |
| `wind-charge-awareness` | Wind Charge: 1 Schaden, Radius 2.4, KB ×1.22, 10 Ticks Abkling. *(Standard **aus**)* |
| `dispatch-attacks` | Zündung/Nahkampf/Schildbrechen als Interact + separate Animation; der Vanilla-Weg schickt das Swing-Paket doppelt. |
| `totem-event-detection` | Erkennt den eigenen Pop am Entity-Event-Paket 35 statt über den Health-Drop (zählte zweimal). |
| `action-cadence` | Pro Movement-Paket höchstens eine Platzierung, ein Angriff, ein Rechtsklick, ein Schwung, eine Blickrichtung. |
| `protect-own-crystals` | Prüft die Bahn von Perlentrank **gegen eigene Crystals** — beide kollidieren. |
| `ghost-block-mitigation` | Verfolgt eigene Platzierungen bis zum Server-Update. |
| `lag-throttle` / `lag-throttle-every` | Drosselt die Aktionsrate bei Server-Ticks > 1.2 s. |

### 2 · Schutz & Recovery (29)
`fast-totem` · `auto-mend` · `auto-eat` · `no-fall` · `auto-shield` · `anti-rubberband` ·
`respect-friends` · `hole-awareness` · `height-advantage` · `avoid-lava` · `auto-fire-res` ·
`build-cover` · `surround` · `anti-bed` · `anti-piston` · `secure-footing` · `peek-tactic` ·
`watchdog-ticks` · `retreat-threshold` · `retreat-on-losing-trade` · `multi-target-alarm` ·
`trap-mode` · `max-self-damage` · `lethal-self-damage-guard` · `wind-charge-awareness` ·
`totem-event-detection` · `lag-throttle` · `lag-throttle-every` · `protect-own-crystals`

### 3 · Stadt & Traps (2)
`insta-city` · `anti-escape-trap`

### 4 · Navigation (5)
`follow` · `follow-range` · `engage-distance` · `pursue-stationary-targets` · `ignore-fire`

### 5 · Inventar (9)
`inv-manager` · `min-crystals` · `min-anchors` · `min-glowstone` · `min-pearls` · `min-obsidian` ·
`min-web` · `min-beds` · `min-heal-potions`

### 6 · Turtle-Master (3)
`turtle-master-defense` · `turtle-master-health` · `turtle-master-cooldown`

### 7 · Perlen & Flucht (9)
`escape-pearl` · `knockback-pearl` · `anti-anchor-disengage` · `explosion-floor-snap` ·
`pearl-pitch-variance` · `pearl-gapclose` · `pearl-min-dist` · `pearl-delay-min` · `pearl-delay-max`

### 8 · Heilung (3)
`heal-potions` · `heal-min-damage` · `heal-cooldown`

### 9 · QA & Erweitert (8)
`free-look` · `through-walls` · `zero-delay` · `ghost-block-mitigation` · `min-support-delay` ·
`no-delay` · `kill-aura` · `debug-trace`

### 10 · Mobs (3)
`attack-mobs` · `mob-types` · `mob-range`

---

## 4. HumanPvP — 56 Settings

Kampfprofil „menschlich": Reaktionszeit, Verklicken, begrenzte Drehgeschwindigkeit, Sichtlinien-Pflicht.
Vorsichtiger bei Eigenschaden (`max-self-damage` 6.0 statt 12.0), `min-support-delay` 4 statt 1.

### General (3)
`attack-range` (3.4) · `smart-targeting` · `backup-range`

### 1 · Angriff & Auras (11)
`use-anchors` · `anchor-mode` · `use-beds` · `bed-min-damage` · `bed-max-self-damage` ·
`bed-self-damage-multiplier` · `prefer-axe-melee` · `sprint-reset` · `shield-breaker` · `melee-strafe` ·
`max-self-damage`

### 2 · Schutz & Recovery (9)
`trap-mode` · `anti-rubberband` · `auto-mend` · `auto-eat` · `no-fall` · `auto-shield` · `fast-totem` ·
`avoid-lava` · `auto-fire-res`

### 3 · Navigation (3)
`follow` · `follow-range` · `engage-distance`

### 4 · Inventar (9)
`inv-manager` · `min-crystals` · `min-anchors` · `min-glowstone` · `min-pearls` · `min-obsidian` ·
`min-web` · `min-beds` · `min-heal-potions`

### 5 · Human-Profil (7)
`reaction-min` · `reaction-max` · `attack-chance` · `aim-tolerance` · `max-turn-speed` ·
`click-delay-max` · `rotation-jitter`

### 6 · Perlen & Flucht (5)
`knockback-pearl` · `pearl-down-variance` · `escape-pearl` · `pearl-gapclose` · `pearl-min-dist`

### 7 · Heilung (3)
`heal-potions` · `heal-min-damage` · `heal-cooldown`

### 8 · QA & Erweitert (6)
`free-look` · `min-support-delay` · `strict-reach` · `packet-cadence` · `lag-throttle` ·
`crystal-ownership`

---

## Setting-Lücke HumanPvP {#setting-lücke-humanpvp}

Die 72 Setting-Namen, die nur in GodmodePvP vorkommen. Aufteilung nach **Ursache**, nicht nach Gruppe.

### A — Absichtliche Design-Auswahl (22)

Diese Features machen den Bot zum Godmode. Ein Profil, das „menschlich" spielen soll, darf sie nicht
haben, sonst ist es kein Human-Profil mehr.

`pursue-stationary-targets` · `pop-threshold` · `prediction-ticks` · `smart-auras` ·
`balance-resources` · `aggressive` · `piston-aura` · `pre-hit` · `melee-fallback` · `track-target` ·
`crit-jump` · `d-tap` · `use-mace` · `elytra-combat` · `zero-delay` · `no-delay` · `kill-aura` ·
`attack-mobs` · `mob-types` · `mob-range` · `dtap-delay-min` · `dtap-delay-max`

### B — Funktionell abgedeckt, anderer Name (2)

`pearl-pitch-variance` → gibt es als `pearl-down-variance`.
`through-walls` → HumanPvP macht das Gegenteil: `strict-reach` **verlangt** Sichtlinie.

### C — Stadt & Traps (2)

HumanPvP hat keine Stadt-Gruppe. `insta-city` · `anti-escape-trap`

### D — Verteidigungstiefe (16)

Alle vorhanden, HumanPvP hat keine Entsprechung. Der Bot in Human-PvP steht damit sichtbar freier:
`ignore-fire` · `respect-friends` · `hole-awareness` · `height-advantage` · `build-cover` · `surround` ·
`anti-bed` · `anti-piston` · `secure-footing` · `peek-tactic` · `watchdog-ticks` · `retreat-threshold` ·
`retreat-on-losing-trade` · `multi-target-alarm` · `anti-anchor-disengage` · `explosion-floor-snap`

Besonders folgenreich: `surround` und `secure-footing`. Im Nether ist Fehlende Boden der
Tod, und `anti-bed` ist laut 2b2t-Wiki die häufigste Todesursache im Nether-PvP.

### E — Turtle-Master (3)

` turtle-master-defense` · `turtle-master-health` · `turtle-master-cooldown`

### F — Timing-Streuung (4)

Ohne diese drei gleiche Cooldowns in gleicher Folge. `pearl-delay-min` · `pearl-delay-max` ·
`anchor-delay-min` · `anchor-delay-max`

### G — Diagnose (1)

`debug-trace`. Ohne das gibt es für HumanPvP keinen Kampf-Trace; Fehlverhalten ist nicht lokalisierbar.

### H — Härtung / Anti-Cheat-Korrektheit (22)

**Das ist die eigentliche Lücke.** Das sind keine Aggressions-Features, sondern Korrektheit — und sie
fehlen in genau dem Profil, das sich als das unauffälligere verkauft.

`gcd-rotation` · `gcd-jitter-steps` · `ray-validate-actions` · `enforce-reach` · `solve-place-cursor` ·
`score-crystals` · `crystal-min-pick-damage` · `crystal-min-tick-age` · `gate-attacks` ·
`min-attack-strength` · `lethal-self-damage-guard` · `crystal-tool-check` · `spear-aware` ·
`per-entity-knockback` · `slow-falling-plan` · `wind-charge-awareness` · `dispatch-attacks` ·
`totem-event-detection` · `lag-throttle-every` · `action-cadence` · `protect-own-crystals` ·
`ghost-block-mitigation`

Teilweise abgedeckt, aber **nicht gleich**:

| GodmodePvP | HumanPvP | Unterschied |
|---|---|---|
| `enforce-reach` + `ray-validate-actions` | `strict-reach` | prüft Reach und Sichtlinie, aber nur Nahkampf/Schildbrechen — **nicht** die Crystal-Zündung |
| `action-cadence` | `packet-cadence` | Human zählt auch den Slot-Wechsel, Godmode zusätzlich Item-Use |
| `gcd-rotation` | `rotation-jitter` | Human streut die Rotation, **quantisiert sie aber nicht** — die Winkel landen nicht auf dem Mausraster |
| `score-crystals` + `CrystalOwnership` | `crystal-ownership` | Human merkt sich eigene Crystals nur, um `auto-shield` nicht reflexhaft auszulösen; die **Auswahl** des zu brechenden Crystals ist weiterhin listenbasiert |
| `lag-throttle` + `lag-throttle-every` | `lag-throttle` | fest auf „jede zweite Aktion" verdrahtet |

**Fazit:** HumanPvP ist kleiner, aber nicht schlechter geschützt, wo es drauf ankommt — nur an fünf
genannten Stellen. Das ist der ehrliche Stand.

---

## 5. Die drei neuen Module

Alle drei melden sich beim `ConflictRegistry` mit **`DEFERS`** gegen `GodmodePvP` und `HumanPvP` an,
Priorität 20 (unter `P_COMBAT=70`). Solange ein Kampfprofil läuft, bekommen sie keine Belegung und
treten zurück. Sie sind also **kein** Konfliktfall — im Gegenteil, sie sind nur dann aktiv, wenn
gerade kein Profil läuft.

### `provi-slow-falling-aura` — 7 Settings
`range` · `auto-swap` · `swap-delay` · `shot-cooldown` · `rotation-jitter` · `pop-window-ticks` ·
`min-pops`

Entscheidung über `SlowFallingArrow`. Der dokumentierte Gegenangriff: verlangsamt den Gegner und
nimmt ihm damit **beides** — den Crit (kein Fall) und den Mace-Smash (Slow Falling verhindert ihn).

Die eigentliche Logik ist das **Pop-Fenster**: Ist gerade ein Pop-Fenster offen
(`popWindowTicks >= pop-window-ticks && maxRepeatedPops >= min-pops`), wird der Schuss **zurückgehalten**.
Ein zweiter Pfeil würde genau das Fenster unterbrechen, in das der Crystal-Pop des Profils passen
soll. Sonst wird SLOW_FALLING bzw. LONG_SLOW_FALLING gefeuert.

### `provi-spear` — 7 Settings
`attack-range` · `auto-swap` · `swap-delay` · `use-jab` · `use-charge` · `hold-ticks` ·
`rotation-jitter`

Entscheidung über `SpearModel`. **Der Grund, warum es dieses Modul gibt:** der Addon-`attack-range` von
3.6 erreicht die Spear-Reichweite 4.5 nicht. In GodmodePvP bleibt das ein Setting
(`spear-aware`), das die Reichweitenprüfung repariert — aber nicht das, was ein Profil mit
Reichweite 4.5 anstellt.

Charge-Angriff greift ab **5.1 b/s**, nicht 4.6: die 4.6 ist die Schwelle für den *geladenen* Zustand,
nicht für einen Treffer mit vollem Schaden.

### `provi-wind-charge` — 8 Settings
`mode` · `auto-swap` · `swap-delay` · `rotation-jitter` · `throw-range` · `throw-cooldown` ·
`only-near-drop` · `min-burst-level`

Entscheidung über `WindChargeModel`. **Wind Charges sprengen seit 1.20.5 keine End Crystals.** Deshalb
konfligiert dieses Modul nicht mit den Crystal-Auren — es zählt nur als Schaden (1) und Knockback
(×1.22, Radius 2.4, 10 Ticks Abkling).

---

## 6. Die beiden HUD-Elemente

Beide sind **strikt lesend**: keine Belegung, keine Baritone-Sperre, kein Hotbar-Zugriff. Genau
deshalb zeigen sie den Broker-Zustand unverfälscht.

### `provi-debug-overlay` — RSHIFT+D
7 Settings: `show` · `bind` · `background` · `text-color` · `muted-color` · `warn-color` ·
`background-color`

Zeilen:

| Zeile | Inhalt |
|---|---|
| `ProviPvP <Modul>: <Aktion>` | jedes aktive ProviPvP-Modul mit `getInfoString()`. Mehrere gleichzeitig = genau der Fall, den der Broker verhindern soll. |
| `Ziel <Name> hp/…hp <m> …` | Ziel, Distanz, Reaktionsfenster |
| `Rotation yaw … dYaw … dPitch …` | **die gesendete** Rotation (`Rotations.serverYaw`), nicht die Kamera. Rot, wenn dYaw und dPitch beide 0 sind oder einer > 5° — genau die Werte, die Grim nicht erst wertet. |
| `Scan-Budget <Art> erlaubt/verschluckt` | Zähler der Drosselung je Scan-Art |
| `<KIND>=<Modul> … Baritone frei/angehalten (n)` | Broker-Halter je `ActionKind` und der Pfad-Lease-Zähler |
| `Crystals <n> eigene, <n> aus dem Fenster gefallen` | Größe des Besitzesfensters und wie viele Einträge herausgefallen sind |
| `Sitzung <Kills> <Tode> …` | Kurzform der Zähler; `PvpSessionStats` zeigt dieselben Zahlen ausführlich |

Beim Weltwechsel wird der abgeleitete Zustand (letzte Rotation, Crystal-Besitz) verworfen — sonst
rechnete der erste Tick nach einem Dimensionstorch ein GCD-Delta gegen den Winkel der alten Dimension.

### `provi-session-stats` — kein Keybind
5 Settings. Zählt für die Sitzung: Kills, Tode, Serie (beste Serie), Schaden aus/ein, Crystals
getroffen/gepoppt. Die Schadenszuordnung ist **clientseitig und damit eine Näherung**.

---

## 7. Profile

| Profil | Härtung | Gedacht für |
|---|---|---|
| `donutsmp` | **an** | Grim-Server — hier greift das GCD- und Reach-Grid |
| `2b2t` | aus | eigener Anti-Cheat, historische Vanilla-Nähe |
| `5b5t` | aus | AntiCheatPlus, eigener AC |
| `arena-aggressive` | aus | Maximale Aggression |
| `2b2t-human` | aus | HumanPvP auf 2b2t |

**Wichtig — dieser Bereich war bis eben kaputt.** Die ausgelieferten Profile suchten ihre Gruppen
unter den Schlüsseln `combat` und `defense`. Die echten Gruppen heißen aber `1 · Angriff & Auras` und
`2 · Schutz & Recovery`. Der Vergleich lieferte immer `null`, jedes Setting wurde als
„fehlgeschlagen" gezählt, **ohne Fehlermeldung**. Ein Profil anzuwenden tat nichts.

Behoben über eine Alias-Tabelle (`combat`→`1 · Angriff & Auras`, `defense`→`2 · Schutz & Recovery`,
`city`/`traps`→`3 · Stadt & Traps`, `human`→`5 · Human-Profil`, mit `stripIndex` für den
`N · `-Präfix) plus einen gruppenübergreifenden `findSettingAnywhere`-Fallback.

**Test `ProfileManagerTest.everyShippedProfileUsesResolvableGroupKeys`** schützt das dauerhaft.

---

## 8. Befehle

| Befehl | Wirkung |
|---|---|
| `.pvp on / off / toggle` | GodmodePvP steuern |
| `.hpvp on / off / toggle` | HumanPvP steuern |
| `.profile <name> [godmode / human]` | Profil anwenden, meldet Erfolge **und Fehlschläge** |
| `.profile list` | vorhandene Profile |
| `.profile save <name> [godmode / human]` | aktuelle Settings als Profil speichern |
| `.prof` | Kurzform von `.profile` |
| `.proxy list / check / add / remove / switch` | Meteors Proxy-System (nicht selbst implementiert) |
| `.nbt item / entity / block` | Components/NBT im Chat |

**Wichtig für den Test:** `.profile <name>` **ohne** Modulangabe wendet das Profil auf das
**gerade aktive** PvP-Modul an — sonst auf GodmodePvP. Für Phase 1 des Testplans heißt das: erst das
richtige Modul einschalten, dann `.profile donutsmp`.

---

## 9. Bewusst nicht gebaut

Nichts hiervon ist Platzhalter — es ist nicht implementiert:

- **B12** — Allokationen in `calcBestAnchor` / `bestDamageAround`
- **F5** — Error Prone / NullAway in der Build-Konfiguration
- **F6** — automatisierte Integrationstests
- **E2 BrandSpoof-Löschung** — vom Nutzer aus dem Umfang genommen
- Die **Abtastung im Gefecht**. 290 Tests prüfen Entscheidungslogik headless. Kein Test prüft, ob der
  Bot in einer Arena auch wirklich zuschlägt. Dafür ist [TESTPLAN.md](TESTPLAN.md) da.


## 10. Gefundener und behobener Fehler: Schildfenster

Bei der Durchsicht fiel auf, dass die Korrektur aus **C7** nur in `GodmodePvP` angekommen war.
`HumanPvP` hielt den Schild weiterhin `tickCounter + 20` bzw. `+ 15`, **ohne** die 5
Aktivierungs-Ticks zu rechnen.

Vanilla blockt mit einem Schild erst ab dem **6. Tick** des Item-Use (250 ms). Ein Fenster von 15
Ticks bedeutet also: 5 Ticks hält der Bot einen Schild in der Hand, der nichts abwehrt, und kann in
dieser Zeit nicht angreifen — 10 Ticks echter Schutz statt 15.

Ursache war nicht ein Tippfehler, sondern die Struktur: die Verzögerung stand als Konstante in
**beiden** Modulen. Behoben über `mechanics/ShieldWindow` — eine headless Klasse, die beide Module
jetzt gemeinsam benutzen. `until(now, heldTicks)` hebt jedes Fenster auf mindestens
`MIN_USEFUL_TICKS` (= 10) an, sodass „hält, schützt nicht und legt nach" nicht mehr möglich ist.
10 Tests decken die Arithmetik ab.

---

## 11. Gefundener und behobener Fehler: das Selbstschaden-Gate

Das ist der Grund, warum der Bot auf 5b5t gegen jeden Gegner verlor und keine Anker benutzte.

`selfDamageAllowed()` prüfte den Explosionsstrahl an **neun Punkten** der Spieler-Hitbox und
verlangte, dass **alle neun** verdeckt sind:

```java
return !blastRayClear(centre) && !blastRayClear(minX,minY,minZ) && ... // acht Ecken
```

Auf offenem Feld — also im Nahkampf, wo ein Crystal, ein Anker oder ein Bett überhaupt erst Sinn
ergibt — sind ausnahmslos alle neun exponiert. Die Funktion lieferte damit **immer** `false`.

Was das ausgelöst hat, war eine Kette:

| Stelle | Folge |
|---|---|
| `calcBestAnchor` :3353 | `anchorCandidates` blieb leer → **keine Anker, ever** |
| `bestDamageAround` :3311 | `continue` für jede Zelle → **Rückgabe 0** → die Aura-Wahl sah überhaupt keine brauchbare Crystal-Position mehr |
| `crystalPlacementSafe` :3594 | Crystal-Platzierung auf offenem Feld ebenfalls tot |
| `calcBestBed`, `maintainNearbyBeds`, `tryPlaceBed` | Betten im Nether ebenfalls tot |
| `onEntityAdded` :1801 | *Umgekehrt*: der Bot brach reflexhaft **jeden** gegnerischen Crystal auf offenem Feld ab |
| `calcBestBed` / Anti-Bed / D-Tap-Scan / Piston / InstaCity | dieselbe Verweigerung, sechs weitere Stellen |

Zwei Folgen, die den Ausfall verlängert haben:

- **`crystalForcedUntil`**: nach **zwei** fehlgeschlagenen Platzierungen wurde 40 Ticks lang
  Crystal erzwungen. Bei leerer Kandidatenliste ist aber gar nichts fehlgeschlagen — es gab nichts
  zu versuchen. Der Bot wechselte also zu einer Waffe, die es ebenfalls nicht gab.
- Im **Nether** ist diese Reaktion besonders schädlich: Anker können dort nicht explodieren, also
  gibt es auch keinen Ausweichweg — der Bot wechselte von der einen Nicht-Waffe zur anderen.

**Behoben** über `crystal/SelfDamageExposure`. Deckung ist jetzt ein Anteil (0 bis 1), kein
Ausschluss, und wird in die Schadensrechnung eingespeist statt eine Platzierung zu verweigern:

```java
private double effectiveSelfDamage(Vec3 explosionPos, double baseDamage) {
    return SelfDamageExposure.effective(baseDamage, countExposedPoints(explosionPos));
}
```

Alle 13 Aufrufstellen wurden auf den wirksamen Schaden umgestellt, damit Deckel
(`max-self-damage`), Bett-Deckel (`bed-max-self-damage`) und Todesgrenze
(`lethal-self-damage-guard`) dieselbe Zahl sehen. `selfDamageAllowed()` hatte danach keine
Aufrufer mehr und ist entfernt.

**Anker im Nether:** `anchorsExplodeHere()` = `dimension() != Level.NETHER`. Das ist Vanillas
Verhalten — Anker explodieren im Nether nicht, sie laden nur. Die Funktion schaltet dort vier
Stellen ab: Platzierung (`tryPlaceAnchor`), Laden/Zünden (`maintainNearbyAnchors`),
Aura-Auswahl (`hasAnchorItem`) und Nachfüllen (`refill`). **Keine Anker im Nether ist korrekt.**
Dort ist Bett-PvP die richtige Waffe, und `bedsExplodeHere()` =
`dimension() != Level.OVERWORLD` lässt sie zu.

---

## 12. Gefundener und behobener Fehler: Hotbar-Blockade

`hasActionableItem()` zählt einen Stack nur, wenn er in der **Hotbar oder Offhand** liegt:

```java
return result.found() && (result.isHotbar() || result.isOffhand());
```

`selectAura()` fragt danach Crystal, Anker und Bett. Liegt eine davon nur im Hauptinventar, gilt sie
als „nicht vorhanden" — und wenn **alle drei** fehlen, setzt `auraMode = -1` und der Bot tut nichts.
Laut eigener Javadoc ist genau das live passiert: **95 Sekunden lang keine einzige Explosion**, bei
vollen Vorräten im Inventar.

Der Mechanismus, der das hätte verhindern sollen, war `evictHotbarBallast()` — und der kannte nur
drei Ballast-Arten:

```java
boolean ballast = s.is(Items.GLASS_BOTTLE)
    || (bedsUseless && isBed(s))
    || (anchorsUseless && s.is(Items.RESPAWN_ANCHOR));
```

Die eigentliche Blockade erreichte er nie: Kies, Erde, Netherrack, Pfeile, Fäulnisfleisch,
Baublöcke. Füllt sich die Hotbar damit — was im Laufe eines Kampfes passiert —, liefert
`evictHotbarBallast()` `false`, `hotbarTargetSlot()` `-1`, und **jedes** `refill()` läuft ins Leere.

**Behoben, in zwei Schritten:**

1. **Die Ballast-Regel ist umgedreht.** Statt aufzuzählen, was Ballast *ist*, wird aufgezählt, was
   **geschützt** ist: verwaltete Ressourcen, Totem, Schild, Nahrung (`DataComponents.FOOD`),
   Werkzeug (`ItemTags.SWORDS/AXES/PICKAXES/SHOVELS/HOES/SPEARS` plus Bogen, Armbrust, Dreizack,
   Mace als Item-Klassen), der Offhand-Inhalt und der gerade belegte `combatSlotTargetSlot`.
   Alles andere ist räumbar. Eine Ballast-Liste muss jede unnötige Sache kennen — inklusive der,
   die morgen jemand dem Bot in die Hand gibt. Eine Schutzliste nicht.
2. **`refill()` räumt seinen Slot selbst frei**, wenn keiner da ist, statt es zu hoffen. Vorher
   wurde nur einmal pro Sekunde pauschal geräumt, jetzt gezielt dann, wenn eine Ressource
   nachfragt.

Nebenbei mitgezogen: `Items.FIRE_RESISTANCE_POTION` gibt es seit 1.20.4 nicht mehr. Die
Feuerresistenz-Prüfung lag an zwei Stellen als Lambda kopiert; jetzt ist sie `isFireResPotion()`,
der dritte Nutzer ist die Ballast-Regel.

---

## 13. Gefundener und behobener Fehler: LOOK-Slot-Starvation

Pro Movement-Paket ist **genau eine** Blickrichtung erlaubt (`ActionCadence.LOOK`, Standard **an**).
Das ist keine Vorsicht, sondern Korrektheit: zwei verschiedene Yaw-Werte in einem Paket produziert
kein echter Client.

Wartung und Platzierung brauchen beide eine Drehung. Sie standen in der falschen Reihenfolge:

```java
maintainNearbyAnchors();   // stand VOR der Aura-Platzierung
maintainNearbyBeds();
...
tryPlaceAnchor();           // rotateAndRun(...) → bekam den Slot nie
```

Sobald **irgendein** Anker in den 4.2-Blöcke-Radius lag, nahm `maintainNearbyAnchors()` den Slot
über `interactAnchorAt()`. Und ein frisch platzierter, noch ungeladener Anker ist genau das — er
lag einen Tick später im Weg und lud sich im nächsten, während die nächste Platzierung ins Leere
lief. Nach zwei Ticks Belegung und Pause war die Frequenz halbiert.

**Behoben:** Wartung läuft jetzt **hinter** dem Aura-Block, auf allen drei Zweigen (D-Tap,
`interceptEnemyBoxing`, Aura-Platzierung). Sie behält ihren Cooldown, läuft also weiterhin regelmäßig
— sie kommt nur nicht mehr vor der Platzierung dran.

**Korrigiert wurde auch ein Kommentar, der das Gegenteil behauptete.** Er sagte, die Erstladung
feuere „dank Mehrfachaktionen-pro-Tick noch im SELBEN Tick". Das ist unmöglich: der äußere
`rotateAndRun()` hat den LOOK-Slot bereits verbraucht, der verschachtelte Aufruf in
`interactAnchorAt()` wird abgelehnt — zusätzlich fehlt ihm der Combat-Slot, der noch auf dem Anker
parkt. Der Versuch ist damit nur bei ausgeschaltetem `action-cadence` erfolgreich. Der Kommentar
sagte jetzt, was tatsächlich gilt.
