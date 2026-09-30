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

## ⬜ WEEK 2: Architecture Extraction — NICHT BEGONNEN

Im `src`-Baum existiert **keine** der geplanten Klassen. `GodmodePvP.java` ist mit ~3000+ Zeilen weiterhin das Monolith.

- [ ] `RotationQueue` als Modul
- [ ] `PearlSolver` als Modul
- [ ] `InventoryManager` als Modul
- [ ] `TargetSelector` als Modul
- [ ] `ExplosionScanner` als Modul
- [ ] `SelfDamagePolicy` als Modul
- [ ] Abstrakte Basisklasse `CombatCore`
- [ ] `GodmodePvP extends CombatCore` + aggressive Features
- [ ] `HumanPvP extends CombatCore` + Human-Features
- [ ] Event-getriebene Architektur (`TargetChangeEvent`, `ExplosionDetectedEvent`) — **es gibt aktuell kein `events`-Paket**
- [ ] Strukturiertes Logging mit SLFLog4J-Markern

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
- `KuiDisconnectedScreenMixin` hängt den Auto-Reconnect-Knopf bei `height - 38` ein — er erscheint auf dem `DisconnectedScreen` nicht
- `KuiWaypointsScreen:634` — Helfer nimmt einen `border`-Parameter, ignoriert ihn und setzt stattdessen einen fest codierten Wert
- `autism-client/` liegt als nicht getrackter Fork im Repo (entschieden: nicht löschen, nicht anfassen)

---

## 📝 Notes

- Alle Änderungen müssen rückwärtskompatibel mit bestehenden Settings bleiben
- Profile liegen in `config/provipvp/profiles/`
- Debug-Overlay per Keybind (Default: `RSHIFT + D`)
- Team-System nutzt eigene Pakete, nicht Meteor-Party
- `dih-src` ist **nicht** in git getrackt — vor jedem Ersetzen eines Jars sichern
- Tests: `PvpMathTest`, `SmartSearchTest` (ProviPvP) · `PanoramaRecolorTest`, `MisroutedPrefixTest`, `ClientCommandParsingTest` (dih-src)