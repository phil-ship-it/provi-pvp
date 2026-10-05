# ProviPvP — Testplan

Stand: 1. Oktober 2026. 280 Tests grün, Build sauber. **Nichts davon ist im Gefecht gesehen worden.**
Dieser Plan beschreibt, was in welcher Reihenfolge zu prüfen ist und woran du siehst, dass es stimmt.

## Vorbemerkung: das Debug-Overlay ist das Werkzeug, nicht der Schmuck

`ProviPvP Debug` (Hud-Element, Keybind `RSHIFT+D`) ist **strikt lesend**: es belegt keine Aktion, hält
Baritone nicht fest und fasst die Hotbar nicht an. Genau deshalb misst es das, was es anzeigt. Ohne
dieses Panel sind die Tests unten praktisch blind — man sieht Fehlverhalten im Bild, aber nicht die
Zahl dahinter.

---

## Phase 0 — Lädt es überhaupt (5 Minuten, zwingend vor allem anderen)

1. Jar bauen, in `mods/` legen, Client starten.
2. **Kommt der Client hoch?** Wenn nicht, ist es ein Classpath- oder Mappingsproblem, kein
   Kampfproblem. Sofort stoppen, Logs lesen.
3. Steht `ProviPvP` in der Modulliste, und sind **14 Module** da (11 alt + 3 neu)?
4. Reicht der HUD-Tab zwei neue Elemente: `ProviPvP Debug` und `ProviPvP Session`?
5. Schalte `ProviPvP Debug` per `RSHIFT+D` an.

**Wenn Phase 0 nicht sauber ist, alles Weitere ist sinnlos.**

---

## Phase 0b — DER WICHTIGSTE TEST (5 Minuten, vor allem anderen)

Der Bot hat in der letzten Runde **nichts mehr platziert**. Grund war ein binäres
Selbstschaden-Gate: es verlangte, dass alle neun Punkte der Spieler-Hitbox hinter Deckung liegen,
um eine Platzierung zu erlauben. Auf offenem Feld — also im Nahkampf — ist das nie der Fall. Die
Anker-Kandidatenliste war dadurch permanent leer **und** die Schadenssuche lieferte für jede
Crystal-Position `0`, weil sie dieselbe Regel benutzte. Das ist der wahrscheinlichste Grund dafür,
dass der Bot auf 5b5t gegen jeden Gegner verliert.

Behoben: Deckung ist jetzt ein Anteil, kein Ausschluss. `max-self-damage` entscheidet wieder.

| Prüfung | Erwartung |
|---|---|
| Bot gegen Gegner, offenes Feld, GodmodePvP | **Crystal-Pops**. Vorher: nichts, außer er stand komplett eingemauert. |
| Overlay: steht Crystal in der Aktions-Zeile? | ja, mindestens `crystal` |
| Legt er in der Overworld jetzt Anker? | ja — auch wenn er auf offenem Feld steht |
| Legt er im Nether Anker? | **nein, und das ist richtig** — Anker explodieren dort nicht |
| Legt er im Nether Betten? | ja — dort ist Bett-PvP die richtige Waffe |
| Nimmt er mehr Eigen-Schaden als vorher? | ja, leicht. `max-self-damage` ist jetzt überhaupt erst wirksam. Bei 12.0 zu niedrig: auf 8 senken. |

**Wenn hier weiterhin nichts passiert, ist es ein anderes Problem** — dann zuerst `score-crystals`
aus und `max-self-damage` auf 20 testen, und mir die Overlay-Zeilen mitteilen.

**Zwei weitere Blocker sind inzwischen behoben** — beide erzeugten dasselbe Bild („der Bot tut
nichts") und sind im Detail schwerer zu sehen:

| Prüfung | Erwartung |
|---|---|
| Hotbar mit Kies/Erde/Pfeilen voll, Crystals nur im Hauptinventar | Der Bot **räumt einen Slot frei** und zieht Crystals nach. Vorher: stille Blockade, bis zufällig eine Flasche im Weg war. |
| Beobachtest du im Kampf Ballast wandern? | ja, aus der Hotbar ins Hauptinventar |
| **Bleibt die Axt in der Hand, wenn ein Slot geräumt wird?** | muss ja — der Combat-Slot ist geschützt. Wenn er die Waffe verliert, ist das ein Fehler in der Ballast-Regel. |
| Anker-Takt gegen einen Gegner, der keine Anker nutzt | deutlich dichter als vorher. Wartung und Platzierung konkurrierten nicht mehr um den LOOK-Slot. |

## Phase 1 — Profile (10 Minuten) — VOR allen Kampftests

**Wichtig:** `.profile <name>` ohne Modulangabe gilt für das **gerade aktive** PvP-Modul, sonst für
GodmodePvP. Vorher also das passende Modul einschalten — sonst testest du versehentlich nur eines.

Das ist neu und dringend, weil es **noch nie funktioniert hat**: die ausgelieferten Profile suchten
ihre Gruppen unter den Schlüsseln `combat` / `defense`, die echten Gruppen heißen aber
`1 · Angriff & Auras`. Jedes Setting wurde als „fehlgeschlagen" gezählt, ohne Fehlermeldung. Ein
Profil anzuwenden tat schlicht nichts.

| Prüfung | Erwartung |
|---|---|
| `.profile donutsmp` | Chat meldet **0 fehlgeschlagen**. Vorher: alle. |
| `.profile 2b2t` | 0 fehlgeschlagen |
| `.profile 5b5t` | 0 fehlgeschlagen |
| `.profile 2b2t-human` | 0 fehlgeschlagen |
| Nach `.profile donutsmp`: ist `gcd-rotation` **an**? | ja |
| Nach `.profile 2b2t`: ist `gcd-rotation` **aus**? | ja — das ist der Server-Split |
| Steht im HUD-Tab ein neues `hardening`/`Gitter-Rotation`-Setting? | nur in `donutsmp` aktiv |

**Melden sich Profile mit Fehlschlägen, ist das ein echter Fund** — dann stimmt ein Gruppenschlüssel
nicht. Der Test `ProfileManagerTest` prüft das für die Gruppen, aber nicht für jeden einzelnen
Setting-Namen.

---

## Phase 2 — Die riskantesten Verhaltensänderungen (P0)

Das sind die Stellen, an denen ich zuerst Fehlverhalten erwarte. Jede verändert, **wann oder ob**
überhaupt gehandelt wird.

### P0.1 `score-crystals` — bricht der Bot noch Crystals?

**Risiko: der Bot hört auf, Crystals zu brechen.** Der Filter beschränkt auf selbst platzierte
Crystals. `CrystalOwnership` bekommt seine Einträge an der Platzierstelle. Wenn das nicht greift,
liefert `pick()` konstant „nichts" — der Bot schlägt gar nicht mehr.

Beobachte im Overlay die Zeile **Crystals**: `0 eigene` bei dauerhaft aktivem Kampf ist das Warnzeichen.

- Crystal platzieren lassen, Overlay an: steigt der Zähler?
- Bricht der Bot **eigene** Crystals? ja
- Bricht er **gegnerische**? Darf er **nicht** — das war der Zweck.
- `crystal-min-tick-age` steht im `donutsmp`-Profil auf 0. Auf 2 stellen, dann darf ein gerade
  platzierter Crystal noch nicht gebrochen werden.

Falls nichts gebrochen wird: `score-crystals` aus, dann geht es wieder — und die Ursache ist
bestimmt die fehlende Platzierbuchhaltung, nicht der Scorer.

### P0.2 `gate-attacks` — blockiert das Angriffs-Gate den Kampf?

Drei Gates in einem: Pop-Fenster (`hurtTime`), Angriffsstärke (≥ 0.9) und Lethality-Crit. Zu streng
eingestellt hieße das **gar kein Nahkampf**.

- Normaler Kampf gegen einen Testbot: schlägt der Bot weiterhin zu?
- Schlägt er *schneller* als vorher? Dann ist der Lethality-Crit zu eng.
- Overlay-Zeile `ProviPvP GodmodePvP: <Aktion>`: springt der Text ständig zwischen `crystal` /
  `surround` / `rueckzugsschritt`? Ruckelnde Aktionswechsel deuten auf ein Gate, das nie greift.
- Springt die Zeile auf `combat-modul-aktiv`? Dann hat ein anderes ProviPvP-Modul den MELEE-Slot —
  das ist der Broker beim Arbeiten, kein Fehler.

### P0.3 GCD-Rotation (D1) — richtet der Bot noch auf sein Ziel?

Das ist die größte Einzeländerung an der Rotation. Fehler heißt: der Bot zielt daneben.

- Greift er Gegner an, die weiter als 3.6 Blöcke weg sind, und **trifft er**? Die Quantisierung darf
  maximal einen halben Gitter-Schritt versetzen (~0.04°), sonst nichts.
- Overlay-Zeile **Rotation**: das **GCD-Delta** sollte in Grad nie exakt 0 sein und nie über ~5°.
  Beides wäre genau das, was Grim ohnehin nicht wertet.
- Springt die Kamera sichtbar oder ruckelt der Bot beim Drehen? Dann arbeitet der Akkumulator
  falsch.

### P0.4 `action-cadence` — behandelt der Bot noch Aktionen?

Ein Takt-Gate, das zu streng ist, lässt den Bot **stehen**.

- Legt er weiter Crystals, Anker, Betten, wirft er weiter Perlen?
- Overlay-Zeile **Broker**: siehst du regelmäßig Belegungen? Siehst du **ständig** `MELEE=...` und
  trotzdem keine Handlung, ist das Gate zu eng.

### P0.5 Crystal-Abbaureichweite 3.0 statt 4.5

`ReachPolicy` trennt jetzt Melee/Crystal-Abau (3.0) von Blockplatzierung (4.5). Vorher war die
Verwechslung latent vorhanden. Der Bot könnte **weniger** Crystals brechen, weil Angriffe bei >3.0
jetzt abgelehnt werden — das ist korrekt, kostet aber Reichweite.

- Bricht der Bot Crystals aus ~3 Blöcken Entfernung? ja
- Aus 3.5? Sollte **nein** sein — Vanilla bricht dort nicht.


## Phase 3 — Reaktion sichtbar machen (P1)

### P1.1 `lag-throttle` — friert der Bot bei Lag ein?
Nur in `donutsmp` aktiv. Testen: künstliche Latenz erzeugen (viele Entities / laggy Server). Der Bot
soll **weniger** Aktionen senden, nicht aufhören.

### P1.2 `ray-validate-actions` / `solve-place-cursor`
Prüfen, dass Crystals/Anchor **tatsächlich** dort landen, wo sie sollen — die Cursor-Berechnung ist
neu und ein Fehler bedeutet: Platzierung schlägt fehl, Bot steht mit Crystal-Item da.

### P1.3 Scan-Budget (B2/B3)
Overlay-Zeile **Scan-Budget**: die „skipped"-Zähler müssen **steigen**. Bleiben sie bei 0, arbeitet
die Drosselung nicht.

- Reagiert der Bot auf einen frisch platzierten Gegner-Anker noch rechtzeitig? Die Erkennung
  verliert jetzt bis zu 3 Ticks.

### P1.4 Schildfenster (C7)
Beide Kampfmodule rechnen das Fenster jetzt über `mechanics/ShieldWindow` — vorher hatte nur
`GodmodePvP` die 5 Aktivierungs-Ticks eingerechnet, `HumanPvP` nicht. Beide halten jetzt
5 Ticks länger.

- Taucht der Schild **vor** der ersten Explosion auf und ist er danach schon wieder weg? Dann ist das
  Fenster zu kurz.
- Steht der Bot mit Schild in der Hand da und blockt **nichts**? Das ist genau der Fehler, den die
  Aktivierungs-Ticks verhindern — 5 Ticks lang sieht es aus, als würde er blocken.

---

## Phase 4 — Kollisionen zwischen Modulen (das war dein Auftrag)

### P4.1 Drei Module gleichzeitig
`GodmodePvP` + `SpearModule` + `WindChargeModule` + `SlowFallingAura` **alle an**.

Erwartung: Die drei Spezialmodule treten zurück, weil ein Kampfprofil läuft. Im Overlay muss zu sehen
sein, dass sie keine Belegung bekommen.

- Feuert `SpearModule` trotzdem? Falsch — es hat `DEFERS` gegen beide Kampfprofile.
- Hält eines davon Baritone fest, obwohl es nichts tut? Das wäre ein **Lease-Leak** — beim
  Deaktivieren muss `PvpServices.release(this)` laufen.

### P4.2 Toggle-Zyklus (der alte C2/C4-Fall)
Modul einschalten → 30 Sekunden Kampf → ausschalten → wieder einschalten, **mehrmals**.

Erwartung: identisches Verhalten. Früher überlebten Zustände den Toggle, weil `onDeactivate` weniger
aufräumte als `onActivate`.

### P4.3 Deaktivieren im Kampf
Modul mitten im Kampf ausschalten (nicht im Frieden).

Erwartung: Baritone **läuft wieder**. Erscheint der Bot danach wie festgefahren, hält noch jemand die
Pfad-Sperre — das wäre der exakte C3-Fehler, den `PathLease` beheben soll.

### P4.4 Weltwechsel
Server verlassen, neuen betreten. Baritone darf **nicht** hängen bleiben.

---

## Phase 5 — Die neuen Module einzeln (P1)

### `provi-spear`
Der Addon-`attack-range` von 3.6 erreicht eine Speer-Reichweite von 4.5 **nicht**. Dieses Modul ist
der Grund dafür, dass Spear-Support überhaupt existiert.

- Trifft es auf 4.0–4.5 Blöcke Entfernung, wo der Nahkampf nicht mehr hinkommt?
- Erzwungener 100%-Cooldown beim Jab: spürst du den Unterschied?
- Wirft es **keinen** Crit und **keinen** Sprint-Knockback? Falls ja, `SpearModel` falsch verdrahtet.

### `provi-slow-falling-aura`
Der dokumentierte Gegenangriff: verlangsamt den Gegner, nimmt ihm Crit **und** Mace-Smash.

- Bekommt der Gegner Slow Falling? (Bei einem Testbot: Bewegung deutlich träger)
- Schießt das Modul **nicht**, wenn das Pop-Fenster gerade offen ist? Genau das ist die Logik —
  ein zweiter Pfeil unterbricht das Fenster, in das der Crystal-Pop passt.

### `provi-wind-charge`
Wind Charges kollidieren **seit 1.20.5 nicht mehr mit End Crystals** — deshalb konfligiert dieses
Modul nicht mit den Kampfprofilen. Prüfen, dass Crystal-Kampf nebenbei weiterläuft.

---

## Was ich nicht testen kann und warum

Der lokale Testserver in `bottest-server/` **teleportiert Testspieler während des Laufs**. Damit lässt
sich kein stationäres Ziel isoliert testen — genau daran ist der Versuch vom 20. September
gescheitert. Für Phase 2 brauchst du entweder:

- einen Server **ohne** Arena-/Datapack-Automatik, oder
- zwei Clients auf einem Friedensserver, wobei du das Ziel von Hand stillhalten lässt.

`TrainingDummy` ist für Phase 3/4 brauchbar (HP, `real-explosion-hits`, `auto-respawn`), für Phase 2
aber **nicht** — es ist eine Entity, kein Spieler, und verhält sich bei Hitbox/Pop anders.

---

## Reihenfolge, wenn du nur eine Stunde hast

1. **Phase 0b** (5 min) — platziert der Bot überhaupt noch etwas. Vorher war das die Wurzel dafür,
   dass er auf 5b5t gegen jeden Gegner verliert.
2. **Phase 0** (5 min) — lädt es überhaupt
3. **Phase 1** (10 min) — Profile, weil sie noch nie funktioniert haben
4. **P0.1 + P0.2** (20 min) — Crystal brechen und angreifen
5. **P0.3** (10 min) — GCD, weil es jede Rotation betrifft
6. **P4.3 + P4.4** (10 min) — Toggle im Kampf und Weltwechsel; die Fehlerklasse, die vorher
   unentdeckt blieb
