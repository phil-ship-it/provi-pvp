# ProviPvP Improvement Plan

Stand: 2026-09-28 · Alle Statusangaben unten wurden gegen den Code geprüft, nicht aus dem Gedächtnis übernommen.

## Target Servers: 2b2t, 5b5t, donutsmp
## Config: JSON | Debug: In-game custom | Team: Custom

Add-on: `com.provipvp` (11 Module, 5 Commands, 2 Testklassen) auf Meteor Client 26.2

---

## ✅ WEEK 1: Critical Fixes — IMPLEMENTIERT

### Day 1: Rotation Queue & D-Tap
- [x] Rotation-Queue-Starvation in `rotateAndRun()` / `pendingFreeLook` — Flag `realActionThisTick` (`GodmodePvP.java:892`, ausgewertet in `:1389`)
- [x] D-Tap-Doppelzündler-Race — warten auf Server-`EntityAdded`-Bestätigung
- [x] Anchor-Charge-Leak beim Dimensionswechsel — `anchorsChargedByUs.clear()` an zwei Stellen (`:1122`, `:1244`)

### Day 2: Bed Aura & Smart Targeting
- [x] `bedSelfDamageMultiplier` in **beiden** Modulen (`GodmodePvP.java:232`, `HumanPvP.java:205`), angewandt in `:3037-3039`
- [x] Smart-Targeting-`backupRange` — bereits korrekt durch `followRange` gefiltert
- [x] `InvHelper.find()`-Offhand-Race — `isHotbarOrOffhand()` ergänzt (`InvHelper.java:30`), genutzt in `GodmodePvP.java:2150`

### Day 3: Config-Profiles
- [x] `ProfileManager` (`config/ProfileManager.java`), Ziel `config/provipvp/profiles/*.json`
- [x] `ProfileCommand` — `list`, `save <name> [godmode|human]`, direkter Profilaufruf je Modul
- [x] 4 Default-Profile in `src/main/resources/config/provipvp/profiles/`: `2b2t.json`, `5b5t.json`, `donutsmp.json`, `2b2t-human.json`
- [x] Bestehende Tests grün: `PvpMathTest`, `SmartSearchTest`

### ⬜ Offen aus Week 1
- [ ] Test auf dem Testserver mit Test-Bots
- [ ] Regressionsprüfung gegen 2b2t / 5b5t / donutsmp

**Aktueller Diff:** 4 Dateien, +107/−47 —
`ProviPvPAddon.java` (+7/−?), `GodmodePvP.java` (+103/−?), `HumanPvP.java` (+35/−?), `InvHelper.java` (+9/−?)

---

## 🔶 WEEK 2: Architecture Extraction — IN ARBEIT

`GodmodePvP.java` ist mit ~5700 Zeilen weiterhin das Monolith, aber der Kern ist jetzt zerlegt.
Aus `GodmodePvP.rotateAndRun` (:3072) und `queueWithCombatSlot` (:3130) hervorgegangen — der
Rotations-Code war zwischen `GodmodePvP` und `HumanPvP` dupliziert, und genau dort sitzt der
Starvation-Bug aus Woche 1.

- [x] `RotationQueue` als Modul (`exec/RotationQueue.java`) — Warteschlange, `clientSide`-Zaehlung,
      `realActionThisTick` und alle Prioritaeten; 6 Tests in `RotationQueueTest`
- [x] `PearlSolver` als Modul (`pearl/`)
- [x] `InventoryManager` als Modul (`core/`)
- [x] `TargetSelector` als Modul (`core/`)
- [x] `ExplosionScanner` als Modul (`terrain/`)
- [ ] `SelfDamagePolicy` als Modul
- [x] Abstrakte Basisklasse `CombatCore` (`core/CombatCore.java`)
- [ ] `GodmodePvP extends CombatCore` + aggressive Features
- [ ] `HumanPvP extends CombatCore` + Human-Features
- [x] Event-getriebene Architektur: `TargetChangeEvent` + `ExplosionDetectedEvent` in `core/events/`,
      veroeffentlicht ueber `CombatCore` (Meteors Orbit-Bus)
- [ ] Strukturiertes Logging mit SLFLog4J-Markern

**Einschraenkung, die die Klassen-Zerlegung begrenzt:** `GodmodePvP` und `HumanPvP` muessen von
Meteors `Module` erben, Java erlaubt keine Mehrfachvererbung — sie koennen also nicht gleichzeitig
`CombatCore` erben. `CombatCore` ist deshalb abstrakte Basis der Kampf-*Logik*-Klassen; die Module
halten eine `CombatPipeline` per Komposition. `CombatCore` als reine Dekoration zu lassen waere
genau die Sorte tote Code, die diese Extraktion beseitigen soll.

**Noch nicht verdrahtet:** `CombatPipeline` wird von `GodmodePvP` noch nicht benutzt. Der
Slot-Mutex-Umstieg auf `InventoryManager` betrifft ~15 Aufrufstellen in nie im Kampf getestetem
Code und ist ein eigener Schritt.

---

## ⬜ WEEK 3: Performance & Anti-Cheat — NICHT BEGONNEN

### Performance
- [ ] Raycast-Ergebnisse in `validExplosionSpot()` cachen
- [ ] Inkrementelle Anchor-/Bed-Kandidaten
- [ ] `countNearbyBlocks()` mit Streams optimieren
- [ ] Inventarzählungen cachen, `ContainerChangedEvent` abhören
- [ ] `maintainNearbyAnchors`-Scanradius reduzieren

### Anti-Cheat
- [ ] `RotationObfuscator` (Micro-Jitter, Ease-Kurven, Timing-Varianz)
- [ ] `tickRateMonitor` gegen Timer-Erkennung
- [ ] `attackRangeJitter`, `swingHandRandomDelay`
- [ ] `pearlPitchVariance`
- [ ] Server-spezifische Defaults in den Profilen

### Benchmarks & Tests
- [ ] JMH-Benchmarks für die Hotspots
- [ ] Unit-Tests für die extrahierten Module
- [ ] Integrationstest-Skript

---

## ⬜ WEEK 4: Features & Polish — NICHT BEGONNEN

- [ ] `ProviDebugOverlay` (Ziel-HP, vorhergesagte Position, beste Spots, Baritone-Pfad, Rotation-Queue, Ressourcen-Zähler)
- [ ] Team-System (geteilter Gegner, Ressourcen-Teilen, Combo-Koordination)
- [ ] `/provipvp tune` (5-Minuten-Sessions, misst K/D, Kristalle/Sek. usw.)
- [ ] Dokumentation: `ARCHITECTURE.md`, `SETTINGS_REFERENCE.md`, `PROFILE_GUIDE.md`, `ANTICHEAT_GUIDE.md`, `DEVELOPER_GUIDE.md`

---

## 🎯 Nächste Schritte

1. **Woche 1 abschließen** — Testserver-Lauf mit Bots, Regressionsprüfung. Alles andere ist unbelegt, solange kein Kampf getestet wurde.
2. **Woche 2 starten** — zuerst `CombatCore` + `RotationQueue`, weil der Rotations-Code in beiden Modulen dupliziert ist und der Starvation-Bug aus Woche 1 genau dort wohnt.
3. **`GodmodePvP.java` aufteilen** — ~3000 Zeilen in einem Modul ist der Hauptgrund, warum Week 1 schwer zu verifizieren war.

---

## 📌 Stand vom 2026-09-28 (Session dih-src)

Diese Arbeiten betreffen **nicht** das ProviPvP-Add-on, sondern den DIH/Kui-Client in `dih-src/`. Hier festgehalten, damit sie nicht verloren gehen.

### Design: Catppuccin Mocha
Der Client war auf einer Rot-Schwarz-Art, die nie umgefärbt wurde. Ursache: `KuiTheme.recolor()` lässt Farben unverändert, solange der Kanal inaktiv ist — mit der Standard-Config war **kein** Kanal aktiv.

- `UiColors`, `CompactTheme`, `KuiColors` auf Catppuccin Mocha umgestellt (base/mantle/crust/surface0-2, Akzent mauve `#CBA6F7`, Text `#CDD6F4`)
- ~60 Literale in 21 weiteren Dateien nachgezogen (Screens, Overlays, HUD, Matchmaking)
- Fenster: 4px Eckenradius + Haarlinien-Rand. Dabei aufgefallen: `UiRenderer.roundRect` ist eine **Pill-Sprite-9-Slice**, deren Endkappen mit der Höhe skalieren — für große Fenster unbrauchbar. `roundedRect`/`roundedFrame`/`roundedRectTop` ergänzt
- Headerleiste von der grellen Akzentplatte auf gedämpftes Mauve mit Titel und Unterstrich
- Logos `kui_client_logo.png` / `loading_logo.png` von `#3B6EFF` auf `#D2ABFF` (Hue auf Mauve, V-Struktur erhalten)

### Markenname
`夔` (U+5914) statt „Kui" an 162 Stellen in 60 Dateien, sichtbar u. a. als „夔 Modules". Ersetzt, inkl. 4 fehlender Leerzeichen. Nur ein Laufzeit-Config-*Wert* betroffen, keine persistierten Schlüssel.

### Intent-Exposure (CrystalAura / AnchorAura)
Der Code war nicht kompilierbar und ist repariert:
- `AnchorAuraModule` — dupliziertes Fragment, Methodenblock hinter der Klassen-Klammer, `sameRotation` statisch mit Instanzaufruf
- `KuiExplosionDamage.calculateDamage` existierte im Projekt nicht → ersetzt durch die echte API `damageTo(target, pos, POWER, options)`
- `PlanDefend` existierte nicht → `ObsidianPlan`

### Chat-Befehle
- `KuiChatSuggestMixin`: Nutzungszeile und Vorschlagsliste lagen in einem `try`; im `catch` rief `kui$clearActiveSuggestions()` eine bereits berechnete Liste ab. Vanillas `fillNodeUsage` liest `minecraft.player.connection`. Getrennt abgesichert. Verifiziert: `.` → 61 Einträge, `.t` → 3, `.tp` → 0
- Prefix-Verwechslung: Meteor belegt `.`, Kui wird auf `%` umgeschrieben. Neuer Hinweis `explainMisroutedCommand` (verbraucht die Nachricht **nicht**), Regel testbar über `commandNamedUnder`, Test `MisroutedPrefixTest`
- `PanoramaRecolorTest.stockThemeRecolorsTheUiBlue` pinnte den alten Vertrag (Standard = Blau, Art = Rot) → ersetzt durch den neuen

### Verteilstand
`gradlew build` grün, 1221 Tests. Build-Artefakt `build/libs/Kui-5.0.1-26.2-dev.jar` ist installiert als `%APPDATA%/.minecraft-262pvp/mods/kui-5.0.1.jar`; das vorherige liegt als `kui-5.0.1.jar.bak` daneben.

### Offen aus dieser Session
- [x] `KuiDisconnectedScreenMixin` — der Auto-Reconnect-Knopf lag bei `height - 38` deckungsgleich ueber
      der Vanilla-Button-Zeile (die sitzt bei `height - 32`) und war dadurch nicht zu sehen. Jetzt
      oberhalb der Zeile, mit benannten Konstanten und Begrenzung gegen kleine Bildschirme.
      Sichtbarkeit per Screenshot **nicht** verifiziert — der DisconnectedScreen ist ohne laufenden
      Disconnect nicht erzeugbar.
- [x] `KuiWaypointsScreen:634` — der Helfer `frame(...)` nahm einen `border`-Parameter, verwarf ihn und
      setzte stattdessen fest `recolor(0x99585B70, OUTLINE)`. Beide Aufrufer reichen `THEME.borderSoft()`,
      also war der Parameter wirkungslos und die Rahmenfarbe folgte nicht dem Theme. Wird jetzt benutzt.
- `autism-client/` liegt als nicht getrackter Fork im Repo (entschieden: nicht löschen, nicht anfassen)

**Sicherung:** beide `dih-src`-Dateien liegen als `*.bak-sprint` daneben (`dih-src` ist nicht in git).

---

## 🔧 Werkzeuge: Offline-Auswertung eines Laufs

Der Bot erzeugt mit `debug-trace` (Standard **aus**, zum Aufzeichnen einschalten) bereits alle
Entscheidungen, und das Test-Datapack `infinite_kit` zaehlt den Ressourcenverbrauch. Damit ist ein
Feedback-Loop vorhanden, aus dem sich Settings-Aenderungen ableiten lassen — ohne die KI in den
Gefechts-Tick zu haengen, der nur 50 ms hat.

- `tools/collect_run.py` — startet beide TestBots, sichert die Usage-Zaehler vor und nach einem
  Kampffenster, legt `<bot>.log` und `_delta.json` ab
- `tools/analyse.py` — wertet Trace + Usage aus und macht **begruendete Settings-Vorschlaege**.
  Aendert selbst nichts; wer uebernimmt, entscheidet.

Aufruf: `python tools/collect_run.py 360`, danach
`python tools/analyse.py ../analysedaten --out bericht.md`

**Absichtliche Grenze:** kein Modellaufruf pro Tick. Eine Frontier-Antwort braucht 300 ms-3 s,
ein Tick hat 50 ms — fuer Dodge und Anti-Fall-Perle ist das keine Optimierung, sondern ein
Ausschluss. An den Stellen, an denen eine KI helfen soll, ist die analytische Loesung
(Bisektion ueber die Flugzeit, Raycast-Cache) schneller und exakter. Die KI gehoert zwischen die
Sessions, nicht in den Kampf.

**Noch nicht erfasst:** Selbstschaden und Schaden pro Runde. Dafuer muesste das Datapack
Schadensereignisse zaehlen; ohne diese Groesse sind Vorschlaege zu `max-self-damage` nicht
belastbar, also auch nicht enthalten.

## 📝 Notes

- Alle Änderungen müssen rückwärtskompatibel mit bestehenden Settings bleiben
- Profile liegen in `config/provipvp/profiles/`
- Debug-Overlay per Keybind (Default: `RSHIFT + D`)
- Team-System nutzt eigene Pakete, nicht Meteor-Party
- `dih-src` ist **nicht** in git getrackt — vor jedem Ersetzen eines Jars sichern
- Beide `dih-src`-UI-Fixes sind in `*.bak-sprint` gesichert (Stand 2026-09-30)
- ProviPvP-Tests: 88 Testfaelle in 6 Klassen (`gradlew test`)
- Tests: `PvpMathTest`, `SmartSearchTest` (ProviPvP) · `PanoramaRecolorTest`, `MisroutedPrefixTest`, `ClientCommandParsingTest` (dih-src)