# Testliste — selbst durchführen (zweiter Bildschirm / eigene Maus)
# WHY: R1-R6; keine Remote-Maus-Steuerung möglich; Nutzer startet Bot/Client selbst.

## Phase 0b — Hotbar + Slot-Starvation (5 Min)
1. Hotbar mit Kies/Erde/Pfeilen füllen; Crystals nur im Hauptinventar → Bot räumt Slot frei.
2. Axt (Combat-Slot) bleibt in Hand (geschützt durch isHotbarBallast) — wenn nicht: Fehler.
3. Overlay-Zeile `ProviPvP <Modul>: <Aktion>` prüfen (siehe TESTPLAN.md).

## Phase 1 — Fix-Verifikation (A/B/C)
4. Build `gradlew.bat build --console=plain --no-daemon` → SUCCESS (bereits geprüft).
5. `gradlew.bat test --tests "com.provipvp.modules.ProviReconModuleTest"` → überprüft DEFERS/20.

## Phase 2 — Headless Heuristic
6. `gradlew.bat test --tests "com.provipvp.recon.TerrainHeuristicEvaluatorTest"` → 3 Entscheidungen grün.

## Phase 3 — Performance / Throttle
7. ChunkScanThrottle: Beobachte, ob Scan-Nutzung bei großer Umgebung nicht alle 20 Ticks explodiert (dynamisches Interval aktiv).
8. Debug-Overlay (`RSHIFT+D`) zeigt Scan-Zustand / gefundene Anomalien (ProviDebugOverlay 7 Zeilen).

## Wichtige Hinweise
- Keine `mc.*` Aufrufe in neuem Code (R1 geprüft via grep).
- `ProviReconModule` DEFERS/20: nur aktiv, wenn Godmode/Human nicht aktiv.
- Netzwerk / Storm-Server (5b5t): Test nicht auf Produktions-Server; nur lokal/Flatworld.
- Maus nicht im Game-Fenster während automatischer Bot-Tests — Bot startest DU selbst via Meteor Client (ClickGui -> Module -> ProviRecon aktivieren), nicht via Remote.
