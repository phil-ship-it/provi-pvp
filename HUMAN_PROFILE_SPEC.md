# Human-Profil (H) — Konservativ / Manuell-imitierend
# Abgeleitet aus der Spezifikation "Explosions- und Schadensmechaniken"
# und den Unterschieden G vs H aus local_tables.md / notebook-quelle-alles.md

Profile-Kennzeichnung: H | Ziel: Grim-Sicherheit durch humanes Verhalten

=== KERN-ENTSCHEIDUNGEN ===
- aggressive = false            (halbiert Wartezeiten nicht; behält Mindest-Ticks)
- attack-range = 3.4            (unter 3.0 für Crystal-Zündung nicht sicher; 3.4 für Grim-Toleranz über 3.0005)
- enforce-reach = true          (Vanilla-Reichweiten streng: Nahkampf/Crystal 3.0, Block 4.5)
- strict-reach = true           (Grim-Interaktion ab 3.0005 als Vergehen)
- ray-validate-actions = true   (gegen serverseitige Rotation prüfen, nicht lokale Kamera)
- solve-place-cursor = true     (normierte Klickkoordinaten [0,1] für Place-Cursor-Check)

=== ROTATIONS-HÄRTUNG (GCD / Grim) ===
- gcd-rotation = true
- gcd-jitter-steps = 1           (1 Gitter-Schritt = 0.0086; verhindert stat. Auffälligkeit bei 0°-Delta)
- max-yaw-delta-per-tick = 18.0  (humanes Limit; kein sofortiger 180°-Snap)
- rotation-quantizer-divisor = 0.0086

=== ZEITVERZÖGERUNG / REAKTION ===
- reaction-delay-min = 3         (Ticks, ~150 ms)
- reaction-delay-max = 9         (Ticks, ~450 ms; simuliert manuelles Zielen)
- miss-click-probability = 0.10  (10 % Fehlklick bei Crystal/Anchor/Bed)
- dtap-delay-min / max = 28 / 44 (unverändert — Physik-basiert)
- anchor-delay-min / max = 5 / 9  (unverändert)

=== SELBSTSCHADEN-GATE ===
- max-self-damage = 6.0          (strenger als G; verhindert Selbstmord durch Bed/Anchor)
- bed-max-self-damage = 8.0     (Bett-Explosionen machen ~50 Rohschaden; separat deckeln)
- bed-self-damage-multiplier = 2.5  (Paper/Spigot erhöht auf 2–3×; hier mittig)
- lethal-self-damage-guard = true # Jeder Platz, der aktuelle HP erreicht/übertrifft, wird verworfen
- self-damage-exposure = true     (fraktionales Modell: 9-Punkt-AABB, 0.0–1.0)

=== EXPLOSIONS-MECHANIK ===
- smart-auras = true
- anchor-mode = 1                (bei Gleichstand bevorzugen)
- use-anchors = true
- use-beds = true                (Nether/End; Overworld = nur Schlafpunkt)
- bed-min-damage = 4.0           (höher als 3.0 G — nur bei echtem Schaden zünden)
- balance-resources = 1.5        (Ausgleich Crystal/Anchor/Bed)
- crystal-min-pick-damage = 3.0
- crystal-min-tick-age = 0

=== ANTI-CHEAT / PAKET-KADENZ ===
- action-cadence = true          (pro Movement-Paket: max 1 Blick, 1 Platz, 1 Angriff, 1 Schwung, 1 Slot-Wechsel)
- dispatch-attacks = true        (Interact + separate Animation; kein DuplicateSwing)
- packet-cadence = true          (keine MultiActionsA-Flags)
- tickrate-gate = true           (bei >1.2 s Tick-Dauer auf jede zweite Aktion drosseln)

=== SCHUTZ / UMGEBUNG ===
- height-advantage = true        (unter Gegner stehen — Explosion trifft höher, eigener Schaden gedämpft)
- surround = true                (Obsidian um eigene Füße, Seite zum Gegner offen)
- secure-footing = true          (sofort Obsidian unter Füße bei Luft/Feuer/Lava)
- hole-awareness = true
- build-cover = true             (nur wenn kein natürliches Loch)
- anti-bed = true
- anti-piston = true
- slow-falling-plan = true       (Mace-Smash bei Slow-Falling abbrechen)
- spear-aware = true             (Reichweite 4.5, kein Crit, Charge ab 5.1 b/s)

=== INVENTAR / SLOT ===
- combat-slot-ownership = true   (Slot reserviert, Pfadfindung pausiert während Angriff)
- path-lease = true              (Baritone kurz pausiert bei präzisen Platzierungen)
- crystal-tool-check = true      (nur taugliche Tools schwingen)
- hotbar-ballast-protected = true # Combat-Slot und Crystal-Tool geschützt vor Auto-Evict

=== PROFILE-VERGLEICH (G vs H) ===
G (Godmode):  attack-range 3.6 | aggressive true | reaction-delay 0-1 | mistake 0 % | gcd-jitter 0
H (Human):    attack-range 3.4 | aggressive false| reaction-delay 3-9 | mistake 10%| gcd-jitter 1 | max-rot 18°/t

=== MASTER-TOGGLE: ANTI-CHEAT-UMGEHUNGEN ===
Setting: `disable-anticheat-mitigations` (default `false`)
Ort: GodmodePvP.java:1008 (sgCombat)
Wirkung bei `true`:
  - GCD-Rotation (0.0086) → abgeschaltet (`gcdRotation` ignoriert)
  - Reach-Policy (3.0/4.5) → abgeschaltet (`enforceReach` ignoriert)
  - Ray-Validation (gesendete Rotation) → abgeschaltet (`rayValidateActions` ignoriert)
  - Action-Cadence (1 Aktion / Paket) → abgeschaltet (`actionCadence` ignoriert)
  - Packet-Dispatch (Interakt + Animation getrennt) → abgeschaltet (`dispatchAttacks` ignoriert)
  - Place-Cursor-Solver (normierte Klickkoordinaten) → abgeschaltet (`solvePlaceCursor` ignoriert)

Warum abschaltbar: Die Umgehungen limitieren Reaktionsgeschwindigkeit und Zielgenauigkeit (z.B. 18°/Tick, Gitter-Rauschen, Reach 3.0 statt 3.6). Mit `true` spielt der Bot ungehemmt roh — maximaler Schaden, aber leichter von Grim/Vulcan zu erkennen und zu töten.
