# ProviPvP – Projekt-, Installations- und Sitzungsprotokoll

**Stand:** 20. September 2026  
**Repository:** `provipvp`  
**Branch:** `master`  
**Letzter Projekt-Commit:** `0870b0e Pursue stationary targets beyond engage range`

Dieses Dokument ist ein technischer Übergabe- und Fehlerstand. Es trennt bestätigte Tatsachen von Tests, die wegen der Arena-Automatik nicht belastbar isoliert werden konnten. Es enthält außerdem den arbeitsrelevanten Chatverlauf dieser Sitzung. Zugangsdaten und Server-Secrets werden absichtlich nicht dokumentiert.

---

## 1. Kurzstatus

### Was funktioniert / ist umgesetzt

- Fabric-/Meteor-Client-Addon **ProviPvP**, Version `0.9.3`, für Minecraft `26.2` und Java `25`.
- Zwei Combat-Module:
  - `GodmodePvP` (`.pvp`): aggressives Crystal-/Anchor-/Bed-PvP mit Baritone-Verfolgung.
  - `HumanPvP` (`.hpvp`): zurückhaltenderes Profil.
- Reine Ballistik-/Geometrie-Logik ist in `PvpMath` mit JUnit getestet.
- Der aktuelle Build und der Testlauf sind erfolgreich.
- Der zuletzt gebaute Addon-Jar wurde für Laufzeittests in die getrennten Mod-Ordner von `TestBot_1` und `TestBot_2` kopiert.
- Der lokale Testserver sowie beide Testclients wurden nach dem Test wieder beendet.
- RCON wurde für die Tests kurzzeitig aktiviert und danach wieder deaktiviert; `server.properties` enthält derzeit kein RCON-Passwort.

### Was aktuell offen oder unsicher ist

- Der lokale Testserver enthält Arena-/Datapack-Automatik. Diese teleportiert Testspieler während des Tests ungefragt. Daher lässt sich dort ein über viele Sekunden **starr fixiertes** Ziel nicht zuverlässig als isolierter Regressionstest halten.
- Der reale Laufzeitversuch zeigte Annäherung, ist wegen dieser externen Teleports aber kein sauberer Beweis ausschließlich für `pursue-stationary-targets`.
- `./gradlew` funktioniert in der verwendeten Shell nicht; auf Windows muss `gradlew.bat` über `cmd.exe /c` benutzt werden.
- Die Gradle-Task `remapJar` existiert im Projekt nicht. Der funktionierende Paketweg ist `gradlew.bat build`, der `jar` ausführt.
- Im Repository liegt die unversionierte Datei-/Ordneränderung `TrouserStreak/`. Sie wurde nicht angefasst und gehört nicht zu dieser Arbeit.

---

## 2. Projektaufbau

```text
provipvp/
├── src/main/java/com/provipvp/
│   ├── ProviPvPAddon.java
│   ├── modules/
│   │   ├── GodmodePvP.java         # Hauptprofil, größter Combat-/Navigationscode
│   │   ├── HumanPvP.java           # Human-/vorsichtigeres Profil
│   │   ├── TrainingDummy.java
│   │   ├── AutoArmor.java
│   │   ├── MacroTriggerModule.java
│   │   ├── PacketFilter.java
│   │   ├── ExploitGuard.java
│   │   ├── BrandSpoof.java
│   │   ├── PacketLogger.java
│   │   └── Auto5b5tDupe.java
│   ├── util/
│   │   ├── PvpMath.java            # zustandslose Ballistik-/Mathematiklogik
│   │   └── InvHelper.java
│   ├── mixin/AutoMendMixin.java
│   └── commands/
│       ├── ProxyCommand.java
│       ├── NbtCommand.java
│       ├── HumanPvpCommand.java
│       └── PvpCommand.java
├── src/test/java/com/provipvp/util/PvpMathTest.java
├── src/main/resources/fabric.mod.json
├── build.gradle.kts
├── gradle/libs.versions.toml
├── FEATURES.md
└── README.md
```

### Build- und Laufzeitbasis

| Bestandteil | Bestätigter Stand |
|---|---|
| Minecraft | `26.2` |
| Java-Toolchain | `25` |
| Fabric Loader | `0.19.3` |
| Fabric Loom | `1.17-SNAPSHOT` |
| Meteor Client | `26.2-SNAPSHOT` |
| JUnit | `5.11.3` |
| Addon-ID | `provi-pvp` |
| Addon-Version | `0.9.3` |
| Entry Point | `com.provipvp.ProviPvPAddon` |
| Baritone | `libs/baritone-api.jar`, compile-only; Client hat zusätzlich `baritone-fabric-26.2.jar` |

Die Abhängigkeiten kommen aus dem Meteor-Maven-Release-/Snapshot-Repository. `fabric.mod.json` deklariert das Addon als **client-only** und verlangt mindestens die konfigurierte Java-Version sowie Meteor Client.

---

## 3. Installierte Testumgebung

### Lokaler Fabric-Testserver

| Eigenschaft | Stand |
|---|---|
| Pfad | `C:\Users\philf\Desktop\Ordner\pvp\26.2 hacks\bottest-server` |
| Start-Jar | `fabric-server-launch.jar` |
| Welt | `world` |
| Leveltyp | Flat (`minecraft:flat`) |
| Port | `25565` |
| Online-Modus | `false` |
| Schwierigkeit | `peaceful` |
| Gamemode | `survival` |
| RCON in persistenter Konfiguration | **deaktiviert** |
| RCON-Passwort in persistenter Konfiguration | **leer** |

Für die RCON-gesteuerten Testpositionen wurde RCON nur temporär aktiviert. Nach dem Serverstopp wurden `enable-rcon=false` und ein leeres Passwort zurückgeschrieben.

### Lokale Testclients

| Client | Pfad | Zweck |
|---|---|---|
| `TestBot_1` | `%APPDATA%\.minecraft-bots\TestBot_1` | aktiver Prüfling / Verfolger |
| `TestBot_2` | `%APPDATA%\.minecraft-bots\TestBot_2` | Gegenüber / Ziel |

Beide Testclients enthalten jeweils den frisch gebauten Jar:

```text
%APPDATA%\.minecraft-bots\TestBot_1\mods\provi-pvp-0.9.3.jar
%APPDATA%\.minecraft-bots\TestBot_2\mods\provi-pvp-0.9.3.jar
```

Weitere beobachtete, bereits installierte Client-Mods: `meteor-client-26.2`, `fabric-api-0.154.2+26.2`, `baritone-fabric-26.2`, `ViaFabricPlus`, `MeteorAdditions`, `fumo-utils`, `1trouser-streak`, `ikea-addon`, `wurst-meteor-addon`, `minehop-meteor`, `nora-tweaks`, `opsec`, `6Bees`, `Seija-Printer`, `villager-roller` und `damagenumbers-addon`.

**Wichtig:** Die Testclients enthalten zahlreiche weitere Addons. Deren Eingaben, Pathfinding, Dummy-/Arena-Funktionen oder Netzwerkänderungen können das Verhalten des getesteten Addons beeinflussen. Für eine absolut reproduzierbare Combat-Regression ist ein minimales Profil mit nur Fabric API, Meteor, Baritone und ProviPvP besser.

### Bot-Startskript

- Testskript: `C:\tmp\spawn_bots.py`
- Es startet Windows-Clients mit `javaw.exe`, getrennten `gameDir`s und direktem Multiplayer-Quick-Play.
- Es reaktiviert bei jedem Start das gewünschte Meteor-Modul in `modules.nbt`, ohne die sonstigen Einstellungen absichtlich zurückzusetzen.
- Verwendeter Start:

```bat
python C:\tmp\spawn_bots.py --names TestBot_1,TestBot_2 --server 127.0.0.1:25565 --module godmode-pvp
```

Für einen manuellen Serverstart wurde Java aus dem Eclipse-Adoptium-JDK 25 verwendet. Der aktuelle Serverprozess ist beendet.

---

## 4. Verarbeitete Änderungen – neueste Arbeit zuerst

### `0870b0e` – Stationäre Ziele außerhalb der Engage-Distanz verfolgen

**Problem:**

`engage-distance` (`16`) war eine harte Kaltstartschwelle. Ein neu gesehenes, stationäres Ziel innerhalb von `follow-range` (`40`), aber außerhalb von `16`, blieb im Zustand `beobachten-fern`. Das war insbesondere bei Trainings-Dummies und nach einer Kampfdistanz-Trennung unerwünscht.

**Änderung in `GodmodePvP`:**

```java
Vec3 targetVelocity = target.getDeltaMovement();
boolean targetStandingStill = target.onGround()
    && targetVelocity.x * targetVelocity.x + targetVelocity.z * targetVelocity.z <= 0.0025;
if (dist <= engageDistance.get() || pursueStationaryTargets.get() && targetStandingStill) engaged = true;
```

Neue Meteor-Einstellung:

| Einstellung | Standard | Wirkung |
|---|---:|---|
| `pursue-stationary-targets` | `true` | Ein stehendes Bodenziel wird innerhalb von `follow-range` auch jenseits von `engage-distance` aktiv verfolgt. |

**Beabsichtigte Abgrenzung:**

- Ein **bewegtes** neues Ziel außerhalb `engage-distance` bleibt Beobachtungsziel. Das verhindert, dass der Bot nach dem Aktivieren quer über die Karte auf einen vorbeilaufenden Spieler losläuft.
- Ein **stehendes** Bodenziel innerhalb `follow-range` wird verfolgt.
- Ein bereits engagiertes Ziel war bereits vorher per UUID sticky, solange es lebt und innerhalb `follow-range` bleibt.

**Dokumentation:**

- `README.md` erklärt die neue Unterscheidung zwischen bewegten und stationären Zielen.
- `FEATURES.md` enthält die neue Einstellung und ihre Standardwirkung.

**Risiko / Kante:**

- Die Stillstandserkennung betrachtet ausschließlich horizontale Geschwindigkeit und `onGround()`. Ein Spieler, der am Boden kurz stehen bleibt, ist absichtlich ein gültiger Verfolgungsgrund.
- Ein Ziel in der Luft mit fast null horizontaler Bewegung löst diese Regel nicht aus.

### `acb08a5` – Stehende Dummies wieder sinnvoll angreifen

- Mindestabstand für offensives Nachsetzen (`MIN_ATTACK_DIST=1.4`) ergänzt.
- Explosionsschaden wird erst ab einer sinnvollen Mindesthöhe (`MIN_MEANINGFUL_DAMAGE=1.0`) als echte Gefahr/Entscheidungsgrundlage behandelt.
- Ziel: Der Bot soll am stationären Dummy weder grundlos festhängen noch ineffektive Explosionen als relevantes Combat-Signal werten.

### `83d912d` – Perlen, Feuer und Cobwebs

- Perlenballistik berücksichtigt eigene Bewegung.
- Feuer-Durchlauf erhielt Fortschrittsbegrenzung; vermeidet unbegrenztes Festlaufen.
- Cobweb-Trap nur gegen sich bewegende Ziele.

### `e88b5f2` – Angriff durch Wände

- `through-walls` wurde im Godmode-Profil standardmäßig aktiviert.

### `0bd545d` – Anarchy-Verteidigung und Piston-PvP

- Surround.
- Anti-Bett.
- Anti-Kolben.
- Bodensicherung.
- Piston-Aura.

### `433fd8f` – Dimensionsregeln

- Keine Anchor-/Bed-Entscheidung für Waffen, die in der aktuellen Dimension nicht explodieren.

### `867c240` – Aggressiveres Standardprofil / Ressourcenbalance

- Aggressivere Defaults und Ressourcen-Ausgleich.
- Laut Commit-Nachricht live A/B gemessen.

### Weitere vorherige relevante Korrekturen

| Commit | Kernänderung |
|---|---|
| `2506c94` | Bed-Aura-Gating und Hotbar-Starvation, die Explosivwaffen blockieren konnte. |
| `29cf788` | Mindestschaden für Bed Aura in GodmodePvP und HumanPvP. |
| `3cae91b` | Heiltrank-Zielpunkt und Rückzugsschwelle mit Beds korrigiert. |
| `9628155` | Heiltrankwurf auf erreichbare Oberfläche, kein Wurf ohne passende Oberfläche. |
| `7ae7536` | Perlen, die am Kopf statt auf berechneter Flugbahn landeten, korrigiert. |

---

## 5. Was gut zusammenarbeitet

1. **Sticky Target-ID + neue Stillstandsregel**
   - Nach einem bereits begonnenen Kampf bleibt das Ziel über seine UUID gebunden.
   - Bei einem neu erkannten, stehenden Ziel schließt die neue Regel die Lücke der Kaltstartschwelle.

2. **Ballistik + Rotation-Slot-Schutz**
   - Die Perlenlogik berechnet Flugbahn inklusive Eigenbewegung.
   - Der Combat-Code verhindert, dass kosmetisches Look-Tracking den Rotationsslot einer echten Aktion wie Perlenwurf oder Anchor-Interaktion überschreibt.

3. **Dimensionsgating + Ressourcenlogik**
   - Anchor-/Bed-Entscheidungen behandeln eine in der Dimension nicht explodierende Waffe effektiv wie nicht verfügbar.
   - Das verhindert sinnlose Wechsel und falsche Rückzugsentscheidungen.

4. **Baritone-Follow + Fire-Walk-Fallback**
   - Baritone verfolgt kontinuierlich.
   - Wenn Feuer den Weg im Nahbereich blockiert, kann der Bot kontrolliert selbst hindurchlaufen; bei fehlendem Fortschritt wird der Fallback gesperrt und Baritone übernimmt wieder.

5. **PvpMath + JUnit**
   - Zustandslose, schwer per Client testbare Mathematik ist außerhalb des Minecraft-Clients testbar.

---

## 6. Was sich blockiert oder stören kann

### A. Explizite Logik-Konflikte / Prioritäten

| Beteiligte Funktion | Wirkung / Konflikt |
|---|---|
| `engage-distance` vs. `pursue-stationary-targets` | Die neue Einstellung überstimmt die Distanzschwelle nur bei Bodenstillstand. Bei bewegtem Ziel bleibt die Schwelle wirksam. |
| `follow=false` | Deaktiviert Baritone-Follow; ein Ziel kann engagiert sein, ohne dass Baritone darauf zuläuft. |
| Rückzug / Explosion / Mindestabstand | Während Rückzug oder zu kleinem Abstand wird Follow absichtlich abgebrochen. Das ist kein Targeting-Fehler. |
| Fire-Walk vs. Baritone | Fire-Walk kündigt Baritone temporär; bei ausbleibendem Fortschritt wird Fire-Walk gesperrt und Baritone wieder verwendet. |
| Zielwechsel / Smart Targeting | Ein engagiertes UUID-Ziel hat Vorrang, um Flackern durch kurzfristig bessere Kandidaten zu verhindern. |
| Bewegte Web-Trap | Stehende Gegner werden absichtlich nicht mehr eingewoben; das reduziert nutzlose Platzierungen, bedeutet aber keine Web-Reaktion gegen Dummies. |
| Dimension vs. Explosivwaffe | Anchor/Bett kann absichtlich als nicht verfügbar behandelt werden, auch wenn das Item im Inventar liegt. |

### B. Externe Teststörer

| Störer | Beobachtete Auswirkung | Konsequenz |
|---|---|---|
| Arena-/Datapack-Automatik auf dem Testserver | Teleportiert die Clients während eines Positionsversuchs. | Entfernungs- und Stillstandsmessungen sind nicht isoliert. |
| Viele Client-Mods im Botprofil | Können Bewegung, Rotation, UI, Netzwerk und Ziele beeinflussen. | Für Regressionen Minimalprofil erstellen. |
| RCON nur temporär | Gut für Sicherheitszustand, aber Testsetup muss vor jeder RCON-Prüfung wieder aktiviert werden. | Testablauf dokumentiert halten. |
| `remapJar` nicht vorhanden | Ein erwarteter Standard-Task schlägt fehl. | Ausschließlich `gradlew.bat build` verwenden. |
| Bash auf Windows | `./gradlew` wird nicht gefunden. | Immer `cmd.exe /c gradlew.bat ...` verwenden. |
| Bestehendes `TrouserStreak/` | Unversionierte fremde/unklare Arbeit im Repository. | Nicht löschen, nicht committen, bis Herkunft entschieden ist. |

---

## 7. Verifikation und gemessene Ergebnisse

### Erfolgreich

```bat
cd provipvp
cmd.exe /c gradlew.bat test
```

Ergebnis:

```text
BUILD SUCCESSFUL
```

Der Lauf führte `compileJava`, `compileTestJava` und die JUnit-Tests aus. Es bestehen sieben bereits vorhandene Deprecation-Warnungen für `BlockStateBase.blocksMotion()` in `GodmodePvP.java`; sie sind nicht durch die letzte Stillstandsänderung verursacht.

Paketbau:

```bat
cmd.exe /c gradlew.bat build
```

Ergebnis: erfolgreich; erzeugter Jar:

```text
provipvp/build/libs/provi-pvp-0.9.3.jar
```

### Laufzeitversuch

1. Jar in beide Bot-Mod-Ordner kopiert.
2. Lokalen Fabric-Server gestartet.
3. Zwei Clients `TestBot_1` und `TestBot_2` verbunden.
4. Per RCON auf rund 30 Blöcke Abstand positioniert.
5. In einem ersten Messfenster fiel der Abstand von ungefähr 30 auf ungefähr 1,7 Blöcke in 12 Sekunden.
6. Nachfolgende Versuche, das Ziel per RCON dauerhaft auf einer festen Position zu halten, wurden von der Arena-Automatik überschrieben; die Spieler wurden auf andere Positionen teleportiert.
7. Server und Clients beendet; Test-RCON und temporäre Client-Konfiguration zurückgesetzt.

**Bewertung:** Build und Unit-Tests sind belastbar. Der Laufzeitversuch zeigt, dass der neue Jar die Clients erreicht und aktive Annäherung stattfindet. Er ist wegen externer Arena-Teleports **kein sauber isolierter Nachweis** ausschließlich für das Verhalten gegen ein dauerhaft stationäres Ziel.

### Empfohlener sauberer Regressionstest

1. Leere Flatworld ohne Arena-Datapack starten.
2. Nur Fabric API, Meteor, Baritone und ProviPvP in einem Testprofil laden.
3. `TestBot_2` ohne Combat-Modul bzw. vollständig bewegungslos starten.
4. `TestBot_1` mit Defaults starten.
5. Beide auf gleicher Y-Höhe 20–30 Blöcke trennen.
6. Sicherstellen: `TestBot_1` reduziert die Distanz, obwohl der Startabstand über `engage-distance` und unter `follow-range` liegt.
7. Vergleich: `pursue-stationary-targets=false` setzen; dann muss der Prüfling in `beobachten-fern` bleiben.

---

## 8. Standardbefehle für die nächste Arbeit

```bat
:: Kompilieren und JUnit-Tests
cd C:\Users\philf\Desktop\Ordner\pvp\26.2 hacks\provipvp
cmd.exe /c gradlew.bat test

:: Paket bauen
cmd.exe /c gradlew.bat build

:: Testbots starten (Server muss laufen)
python C:\tmp\spawn_bots.py --names TestBot_1,TestBot_2 --server 127.0.0.1:25565 --module godmode-pvp
```

Der Bot-Jar muss nach einem Build erneut in beide Bot-`mods`-Ordner kopiert werden, falls der Testclient nicht direkt aus dem Build-Ordner lädt.

---

## 9. Arbeitschat dieser Sitzung

> Hinweis: Dies ist das vollständige **arbeitsrelevante Protokoll** der vorliegenden Sitzung, kein automatischer Export von UI-/System-/Tool-Ausgaben. Die originalen Nutzernachrichten sind wörtlich wiedergegeben, technische Antworten und ausgeführte Arbeit sind sachlich zusammengefasst.

### Vorheriger Arbeitskontext

- Das offene Verhalten war: Ein stationäres Ziel bzw. Trainings-Dummy außerhalb von `engage-distance` blieb trotz Erkennung innerhalb `follow-range` im Status `beobachten-fern`.
- Die bisherige Sticky-Logik hielt nur bereits engagierte Ziel-UUIDs. Ein neuer Kaltstart außerhalb von `engage-distance` setzte `engaged=false`.

### Nutzer

> mach weiter

### Darauf ausgeführte Arbeit

- `handleTargeting` sowie `findTarget`, `updateFollow`, Follow-/Engage-Einstellungen und die Baritone-Prioritäten geprüft.
- Ursache bestätigt: Der Kaltstart setzte bei einem neuen Ziel nur innerhalb `engage-distance` den Engage-Zustand.
- Neue Einstellung `pursue-stationary-targets` implementiert, Standard `true`.
- Deutsche In-Game-Beschreibung ergänzt; README und Feature-Tabelle aktualisiert.
- `gradlew.bat test` erfolgreich ausgeführt.
- Addon gebaut, in beide Testclients kopiert, Testserver und zwei Clients gestartet.
- RCON für Teststeuerung temporär aktiviert, nachher wieder ausgeschaltet.
- Zwei Laufzeitversuche ausgeführt. Der erste zeigte Annäherung; die Arena-Automatik verhinderte einen sauberen dauerhaft statischen Zielversuch.
- Testserver und Testclients gestoppt; temporäre TestBot_2-Option wieder entfernt; erzeugte Repository-Logs entfernt.
- Commit erstellt: `0870b0e Pursue stationary targets beyond engage range`.

### Nutzer

> erstelle mit eine detalierte datei mit allem was wir erarbeitet/ instaliert haben was hakt was gut ist was sich gegenseitig blockiert einfach alles auch den chat und mache das in eine datei

### Antwort / Ergebnis

- Dieses Dokument `PROJECT_HANDOFF_DE.md` wurde auf ausdrücklichen Wunsch erstellt.

---

## 10. Arbeitsbaum beim Erstellen dieses Dokuments

Vor dem Hinzufügen dieser Dokumentation lag als einzige unversionierte, nicht angefasste Repository-Änderung vor:

```text
?? TrouserStreak/
```

Diese Dokumentation ist neu und muss separat committed werden, wenn sie dauerhaft in Git liegen soll.

---

## 11. QA-Iteration GodmodePvP (2026-09-23)

### Umgesetzte Bereiche

- **AutoPearl 2.0:** `anti-anchor-disengage` und `explosion-floor-snap`; prior-Y-Delta, 20-Tick-Ankerfenster, offene 1×1-Lochprüfung, PvpMath-Perlenziel und Same-Tick-Floor-Snap.
- **Prediction:** CrystalAura `placeDelay`/`breakDelay`/`ticksExisted`/`fastBreak` auf sichere 0-Werte; keine erfundene Entity-ID. Eigene Blockplatzierungen werden über `ClientboundBlockUpdatePacket` bestätigt; nach `2 × RTT` wird nur lokale Ghost-Hitbox entfernt und Baritone-Cache neu geladen.
- **City/Traps:** `insta-city` mit Hotbar-Pickaxe, serverseitig bestätigter Bruch-Lücke und Crystal-Selbstschadenprüfung; `anti-escape-trap` für echte 1×1-Löcher mit Cobweb/Obsidian.
- **Turtle-Master:** geladene Crossbow mit Turtle-Master-Tipped-Arrow, Offhand-Staging, `useItem()` + `releaseUsingItem()` am selben Tick, 40%-Health-Schwelle.
- **Blocking Bugs:** UUID-Positionsdelta invalidiert Follow/CustomGoal und Baritone-Cache; `selfDamageAllowed()` raytraced Explosion-zur-eigener-Hitbox; Combat-Slot-Mutex reserviert Mainhand-Slots und stoppt Baritone; Fire-Walk-Fallback nach 40 Ticks; Ressourcen-Refill funktioniert bei exaktem Threshold und priorisiert nur actionable Hotbar/Offhand-Ressourcen.

### Verifikation

- `cmd.exe /c gradlew.bat test` → `BUILD SUCCESSFUL`.
- `cmd.exe /c gradlew.bat build` → `BUILD SUCCESSFUL`.
- Finaler Jar `provipvp/build/libs/provi-pvp-0.9.3.jar` in `TestBot_1` und `TestBot_2` deployed.
- Isolierter Fabric-Server: `bottest-qa`, leere Flatworld, `peaceful`, kein Arena-Datapack.
- Wandtest: `bottest-qa/logs/latest.log:218-229` — Wand gesetzt, 39 Sekunden observed, beide Health `20.0`; kein Splash-Selbstschaden.
- 15-TPS-Test: Serverlog `20:53:14-20:53:52` — 15 TPS für 20 Sekunden, anschließend 20 TPS; kein Spam-/Exception-Loop.
- Kaltstart-/Teleport-Tests mit Server-Konsolen-Teleports erzeugen temporären Client/Server-Desync durch alte Movement-Pakete. Der UUID-Pfad wurde in `bottest-qa/logs/latest.log` dennoch als `Pearl-Teleport erkannt` erkannt; für belastbare Live-Messung muss der Arena-Server den Teleport selbst senden, nicht die Testkonsole.

### Bekannte Grenzen

- Eine Crystal-Entity-ID kann vor dem serverseitigen `AddEntity`-Paket nicht seriös vorhergesagt werden; Meteor greift deshalb auf die reale ID zurück.
- Survival-Insta-City kann keinen handgestarteten Abbau legal in einen letzten Pickaxe-Tick umwandeln; der Code bricht mit der Pickaxe fort und bestätigt die Lücke serverseitig.
- Turtle-Master benötigt eine bereits geladene Crossbow mit Turtle-Master-Pfeil; der Code erfindet keine geladene Item-DataPipe.
- Offline-401-, optionale Mixin- und Google-Translate-Fehler der Testclients stammen aus der Testumgebung, nicht aus ProviPvP.

### Testkonfiguration nach dem Lauf

- TestBot_2-Modulkonfiguration wurde aus `C:\tmp\TestBot_2.modules.before-qa.nbt` wiederhergestellt.
- Testclients und `godmode-qa-server` wurden beendet.
- `TrouserStreak/` bleibt unversionierte Nutzerarbeit und wurde nicht verändert.

### Gezielte Feature-Läufe

- **Anti-Anker:** `bottest-qa/logs/latest.log:46-55` — drei geladene Ankerblocks nacheinander entfernt, `TestBot_1 Inventory=[]`, Health `20.0`; der Pearl-Flug wurde aus dem Loch ausgelöst.
- **Floor-Snap:** `bottest-qa/logs/latest.log:56-61` — Primed TNT mit `fuse=0`, eine Pearl, Inventory anschließend `[]`, Health `20.0`; der kontrollierte Pearl-Flug lief.
- **Prediction:** `bottest-qa/logs/latest.log:62-67` — 64 Crystals/64 Obsidian, `Test passed. Count: 2` (zwei End-Crystal-Entities), Target- und Bot-Health `20.0`.
- **City-Isolation:** Der erste City-Test wurde durch normale Crystal-/Melee-Damage vor dem Pickaxe-Pfad beendet; die vier Wandabfragen blieben `Test passed`. Ein zweiter Versuch mit unveränderlichem Target-Datenstand war nicht möglich, weil der Server-Command für die zusätzliche Max-Health-Property in 26.2 abgewiesen wurde. Der Code-Pfad ist damit compile- und setup-seitig geprüft, aber nicht als isolierter Pickaxe-Durchbruch bewiesen.
- **Turtle:** Die 26.2-Item-Component-Synthese wurde mit `item replace entity ... weapon.offhand with minecraft:crossbow[minecraft:charged_projectiles=...]` erfolgreich erzeugt; `equipment` zeigte `charged_projectiles` mit `minecraft:turtle_master`. Der nachfolgende `/damage`-Befehl wurde vom Server mit 13 Schaden quittiert, ließ Health jedoch auf 20 und erzeugte keine Active Effects. Der Turtle-Schuss selbst ist deshalb nicht als Effektlauf bestätigt.

---

## 12. Review-Follow-up (Through-Walls / Slot-Lockout)

- **CustomGoal-Lifecycle:** `reserveCombatSlot()` und `cancelFollow()` rufen jetzt `onLostControl()` und `resetPositioningState()` auch dann auf, wenn `followActive=false`; `activeHole` kann keinen stillen alten Baritone-Pfad mehr unterdrücken.
- **Lease-Contention:** `combatSlotBusyFor()` verhindert, dass Anchor-/Bed-Kandidaten bei temporär belegtem Combat-Slot dauerhaft übersprungen werden.
- **Insta-City:** Pickaxe-Lease läuft jetzt über `reserveCombatSlot()`, `instaCityOwnsCombatSlot` und `releaseCombatSlot()`; kein separater Direkt-Swap-Pfad mehr.
- **Blast-Exposure:** `selfDamageAllowed()` prüft neun Punkte der Spieler-AABB statt nur den Mittelpunkt mit 0.6-Block-Toleranz.
- **Delegierte Pfade:** Piston-Aura, D-Tap Stage 1/3 und EntityAdded-End-Crystals werden erneut gegen `crystalPlacementSafe()`/`selfDamageAllowed()` geprüft.
- **Dynamischer Wall-Range:** `syncWallsRange()` läuft alle 20 Ticks und stellt bei deaktiviertem `through-walls` die gespeicherten Meteor-Wandreichweiten wieder her.

Verifikation: `cmd.exe /c gradlew.bat test` und `cmd.exe /c gradlew.bat build` erneut `BUILD SUCCESSFUL`; Review-Retest `bottest-qa/logs/latest.log:32-35` zeigt beide Clients im finalen Jar, ohne internen ProviPvP-Fehler.

---

## 13. Addon- und GUI-Audit (2026-09-25)

### Addon-Bestand und Konflikte

- `TestBot_1` und `TestBot_2` enthalten jeweils 19 Jars und laden 119 Fabric-Mods. Meteor Client, Baritone, Fabric API und ProviPvP sind vorhanden; die Addon-Kette (Numby, Wurst, Meteor+, Trouser, Fumo, Nora, ViaFabricPlus, OpSec usw.) bringt konkurrierende Aura-, Slot-, Keybind-, Packet- und Baritone-Mixin-Pfade mit. Fuer eine attributierbare Combat-Messung ist nur Fabric API + Meteor + **eine** Baritone-Version + ProviPvP minimal.
- `bottest-qa` ist der isoliertePfad (flat, peaceful, survival, keine Datapacks). `bottest-server` ist wegen `infinite_kit` (Teleport/Refill/Reset pro Tick) kein Isolations- oder Reproduktionsserver.
- Eindeutige Modulnamen korrigiert: ProviPvP registriert jetzt `provi-auto-armor` und `provi-packet-logger` statt die Meteor-Kernnamen `auto-armor`/`packet-logger` zu ueberschreiben. Die Kernmodule bleiben dadurch im Meteor-UI erhalten.

### GUI- und Laufzeitfixes

- GodmodePvP-Gruppen: `1 · Angriff & Auras`, `2 · Schutz & Recovery`, `3 · Stadt & Traps`, `4 · Navigation`, `5 · Inventar`, `6 · Turtle-Master`, `7 · Perlen & Flucht`, `8 · Heilung`, `9 · QA & Erweitert`, `10 · Mobs`; Turtle-Schwellen/-Cooldowns sind nur sichtbar, wenn Turtle-Master aktiv ist.
- HumanPvP-Gruppen: `1 · Angriff & Auras`, `2 · Schutz & Recovery`, `3 · Navigation`, `4 · Inventar`, `5 · Human-Profil`, `6 · Perlen & Flucht`, `7 · Heilung`, `8 · QA & Erweitert`.
- `GodmodePvP` und `HumanPvP` schliessen sich nun auch nach dem Meteor-Config-Laden gegenseitig aus; das zuletzt aktivierte Profil bleibt aktiv, ein bereits laufendes Profil wird nicht blind umgeschaltet.
- crystal/anchor/bed candidate paths, delegated CrystalAura damage/support settings, mob filters, piston stage confirmation, Human pearl/heal/fire-resistance/anchor hand handling, refill thresholds and anti-rubberband follow suppression wurden gegen die aktuellen Findings nachgezogen. Eigene Abhaengigkeiten werden nur deaktiviert, wenn das Modul sie selbst aktiviert hat.

### Verifikation und Grenzen

- `cmd.exe /c gradlew.bat test` und `cmd.exe /c gradlew.bat build`: **BUILD SUCCESSFUL**. Der finale Jar enthaelt `provi-pvp.mixins.json`/`AutoMendMixin.class`; Build-, Bot- und Main-Ordner haben denselben SHA-256.
- Finaler Lauf auf `bottest-qa`: Client verband sich, lud den finalen Jar und aktivierte jeweils genau ein PvP-Profil; keine internen ProviPvP-Fehler. 401/Google-Translate/optionale ModMenu-/Baritone-/Litematica-Mixin-Meldungen bleiben Testumgebungs- bzw. Addon-Rauschen.
- Die GUI-Pruefung wurde nur auf Monitor 2 durchgefuehrt (Monitor-Bounds `X=-1440,Y=-712,W=1440,H=2560`); der Minecraft-Client wurde dorthin verschoben und der Desktop-Screenshot geprueft. Die ClickGUI selbst liess sich in dieser Headless-/Second-Monitor-Umgebung nicht zuverlaessig per synthetischem Tastatureingang oeffnen; die Gruppen-/Sichtbarkeitsvertrage wurden deshalb statisch gegen den finalen Quellcode und die kompilierte Klasse geprueft, nicht als visueller KlickGUI-Screenshot.
- Offene, bewusst nicht als erledigt behauptete Punkte: einzelne Human-Global-Slot-/Offhand-Rennen sowie die oben genannten externen Addon-Mixins. Die zuvor offenen `PvpMath`-Kanten sind durch die Regressionstests und den Umgebungsscan abgedeckt; fuer die Combat-Matrix bleibt ein minimales Jar-Set noetig.

---

## 14. Loch-Verfolgung und Perlen-Ballistik (2026-09-25)

- Reproduktion in `PvpMathTest`: `simulatePearl()` lieferte bei nicht normierter Richtungslinie ein anderes Ergebnis als bei `(0,1)`; `solvePearlAim()` akzeptierte ein 0.4 Blöcke horizontales / 30 Blöcke hohes Ziel per Shortcut, obwohl der Wurf physisch nicht ankommt. Beide Tests schlug vor dem Fix fehl.
- `PvpMath` normalisiert jetzt die Zielrichtung, entfernt den unphysikalischen Shortcut und bietet `trajectoryClear()` für segmentweise Umgebungs-/Blockpruefung. Die Regressionstests decken Richtung, Unerreichbarkeit und Kollisionspruefung ab.
- GodmodePvP und HumanPvP scannen jetzt Ziel-Hitbox, vorhergesagte Position, nahe Hoehen-/Horizontaloffsets und begehbare Zellen in der Umgebung. Ein Wurf wird nur gewaehlt, wenn die berechnete Bahn im aktuellen Level frei ist; der alte gerade LOS-Gate wurde entfernt.
- GodmodePvP verlassen veraltete Kampf-/Lochziele jetzt sofort. Ist der Bot selbst eingeschlossen, sucht `findEscapePosition()` eine offene Nachbarzelle priorisiert nach weniger Waenden und naeher am Gegner; ein 30-Tick-Stillstands-Timeout gibt den Weg fuer FollowProcess oder Perlenwurf frei.
- Live-Smoke auf dem Arena-Server: `TestBot_1` wurde in eine 1x1-Zelle bei `1010.5, -59, 1010.5` gesetzt, `TestBot_2` stand draussen bei `1016.5, -59, 1010.5`; nach etwa 16 Sekunden war `TestBot_1` bei `1032.67, -59, 996.53` und hatte die Zelle verlassen. Beide Clients liefen mit dem finalen Jar ohne internen ProviPvP-Fehler. Der Arena-Reset/Refill des Test-Datapacks bleibt eine externe Stoergroesse.
- `cmd.exe /c gradlew.bat test` und `cmd.exe /c gradlew.bat build`: **BUILD SUCCESSFUL**; finaler Jar-SHA-256 `1d35f9457337427d4a1fea0c469b583e6b06cf70b426c0b1252439560c4d6402`. Testclients und Arena-Server wurden danach beendet.
