"""Offline-Analyse eines aufgezeichneten Bot-Laufs.

Liest die Ausgaben von `GodmodePvP.debug-trace` (Bot-Log) und die
Resource-Zaehler des Test-Datapacks `infinite_kit` (als JSON-Delta) und
schlaegt Settings-Aenderungen vor. **Nur Vorschlaege** — der Lauf aendert
selbst nichts; wer uebernimmt, entscheidet.

Aufruf:
    python tools/analyse.py <datenverzeichnis> [--out bericht.md]

Reine Standardbibliothek, damit das Werkzeug ohne pip-Installation laeuft.
"""
from __future__ import annotations

import argparse
import json
import re
import statistics
import sys
from collections import Counter
from dataclasses import dataclass, field
from pathlib import Path

# Eine Trace-Zeile aus GodmodePvP.traceCombatState():
#   [ProviPvP] tick=2600 action=beobachten target=- dist=-1.0 engaged=false
#              follow=false auraMode=-1 dtap=0 held=End Crystal
TRACE = re.compile(
    r"\[ProviPvP\] tick=(?P<tick>-?\d+)"
    r" action=(?P<action>\S+)"
    r" target=(?P<target>\S+)"
    r" dist=(?P<dist>-?[\d,.]+)"
    r" engaged=(?P<engaged>true|false)"
    r" follow=(?P<follow>true|false)"
    r" auraMode=(?P<aura>-?\d+)"
    r" dtap=(?P<dtap>\d+)"
    r" held=(?P<held>.*)$"
)

# Aktionen, die keinen Schaden bringen — reine Beobachtung/Positionierung.
PASSIVE = {"beobachten", "-", "hole", "boden-sichern", "rueckzugsschritt"}


@dataclass
class Sample:
    tick: int
    action: str
    target: str
    dist: float
    engaged: bool
    follow: bool
    aura: int
    dtap: int
    held: str


@dataclass
class Profile:
    name: str
    samples: list[Sample] = field(default_factory=list)
    usage: dict[str, int] = field(default_factory=dict)

    # ---- abgeleitete Kennzahlen ----
    @property
    def span_ticks(self) -> int:
        if len(self.samples) < 2:
            return 0
        return self.samples[-1].tick - self.samples[0].tick + 1

    @property
    def engaged_share(self) -> float:
        return _share([s for s in self.samples if s.engaged], self.samples)

    @property
    def passive_share(self) -> float:
        return _share([s for s in self.samples if s.action in PASSIVE], self.samples)

    @property
    def follow_share_while_engaged(self) -> float:
        eng = [s for s in self.samples if s.engaged]
        return _share([s for s in eng if s.follow], eng)

    @property
    def dist_median(self) -> float:
        d = [s.dist for s in self.samples if s.dist >= 0]
        return statistics.median(d) if d else -1.0

    @property
    def dist_p90(self) -> float:
        d = sorted(s.dist for s in self.samples if s.dist >= 0)
        return d[int(len(d) * 0.9)] if d else -1.0

    @property
    def engaged_but_far_share(self) -> float:
        """engaged, aber weiter als 6 Bloecke — der Bot laeuft, kommt aber nicht in Reichweite."""
        eng = [s for s in self.samples if s.engaged and s.dist >= 0]
        return _share([s for s in eng if s.dist > 6.0], eng)

    @property
    def exploded(self) -> bool:
        """Explosions-Zaehler im Kampf-Fenster."""
        return (self.usage.get("ik_used_crystal", 0)
                + self.usage.get("ik_used_anchor", 0)) > 0


def _share(part: list, whole: list) -> float:
    return len(part) / len(whole) if whole else 0.0

# ---------------------------------------------------------------- Settings
SETTING = re.compile(
    r'\.name\("(?P<name>[a-z0-9-]+)"\)'
    r'(?:(?!\.build\(\)).)*?\.defaultValue\((?P<def>[^)]+)\)',
    re.S,
)
BOUND = re.compile(r'\.(?:range|min|max|sliderRange)\((?P<a>[^,()]+),\s*(?P<b>[^,()]+)\)')


def load_settings(source: Path) -> dict[str, dict]:
    """Liest die aktuellen Settings-Defaults direkt aus GodmodePvP.java.

    Ohne das riet die Regel "balance-resources steht auf 0" oder schlug "pearl-min-dist senken"
    vor, obwohl der Wert bereits am Minimum der Skala klebt. Vorschlage, die sich nicht aus dem
    Code belegen lassen, sind schlimmer als keine.
    """
    out: dict[str, dict] = {}
    if not source.exists():
        return out
    for m in SETTING.finditer(source.read_text(encoding="utf-8", errors="replace")):
        body = source.read_text(encoding="utf-8", errors="replace")[m.start():m.end() + 200]
        entry: dict = {"default": m.group("def").strip()}
        b = BOUND.search(body)
        if b:
            try:
                entry["min"] = float(b.group("a"))
                entry["max"] = float(b.group("b"))
            except ValueError:
                pass
        out[m.group("name")] = entry
    return out


def parse_trace(text: str) -> list[Sample]:
    out: list[Sample] = []
    for line in text.splitlines():
        m = TRACE.search(line)
        if not m:
            continue
        raw = m.group("dist").replace(",", ".")
        try:
            dist = float(raw)
        except ValueError:
            dist = -1.0
        out.append(Sample(
            tick=int(m.group("tick")),
            action=m.group("action"),
            target=m.group("target"),
            dist=dist,
            engaged=m.group("engaged") == "true",
            follow=m.group("follow") == "true",
            aura=int(m.group("aura")),
            dtap=int(m.group("dtap")),
            held=m.group("held").strip(),
        ))
    return out


def load_profiles(folder: Path) -> dict[str, Profile]:
    delta_path = folder / "_delta.json"
    usage: dict[str, dict[str, int]] = {}
    if delta_path.exists():
        for key, val in json.loads(delta_path.read_text(encoding="utf-8")).items():
            bot, obj = key.split("|", 1)
            usage.setdefault(bot, {})[obj] = int(val)

    profiles: dict[str, Profile] = {}
    for log in sorted(folder.glob("*.log")):
        if log.name == "server.log":
            continue
        bot = log.stem
        p = Profile(bot)
        p.samples = parse_trace(log.read_text(encoding="utf-8", errors="replace"))
        p.usage = usage.get(bot, {})
        profiles[bot] = p
    return profiles


def _fmt(share: float) -> str:
    return f"{share * 100:.0f} %"


def _lever(settings: dict[str, dict], name: str) -> str:
    """Beschreibt einen Setting-Wert samt Skala, damit ein Vorschlag pruefbar ist."""
    s = settings.get(name)
    if not s:
        return f"`{name}` (nicht in GodmodePvP.java gefunden)"
    scale = ""
    if "min" in s and "max" in s:
        scale = f", Skala {s['min']:g}-{s['max']:g}"
    return f"`{name}` steht auf {s['default']}{scale}"


def proposals(profiles: dict[str, Profile], settings: dict[str, dict]) -> list[str]:
    """Regelbasierte Vorschlaege. Jeder nennt die Zahl, die ihn ausgeloest hat, und nur
    Aenderungen, die innerhalb der tatsaechlichen Skala liegen."""
    out: list[str] = []
    live = {n: p for n, p in profiles.items() if p.samples}

    for name, p in sorted(live.items()):
        if p.span_ticks < 200:
            continue

        # 1) Der Bot ist engaged, laeuft aber nie bis in Angriffsdistanz.
        if p.engaged_share > 0.20 and p.engaged_but_far_share > 0.45:
            out.append(
                f"`{name}`: engaged in {_fmt(p.engaged_share)} der Samples, aber "
                f"{_fmt(p.engaged_but_far_share)} davon mit Abstand > 6 (Median {p.dist_median:.1f}, "
                f"p90 {p.dist_p90:.1f}). Der Bot rennt, kommt aber nicht in Explosionsreichweite.\n"
                f"  -> {_lever(settings, 'pearl-min-dist')}. Ein Absenken ist damit "
                f"ausgeschlossen; der Hebel liegt beim Zulaeuern selbst (follow) oder beim "
                f"Perlenwurf, nicht an dieser Einstellung."
            )

        # 2) Follow wird staendig gerissen, obwohl engagiert — Pfad-Konflikte.
        if p.engaged_share > 0.20 and p.follow_share_while_engaged < 0.30:
            out.append(
                f"`{name}`: in nur {_fmt(p.follow_share_while_engaged)} der engaged-Samples war "
                f"follow=true. Der Baritone-Follow wird also laufend abgebrochen; die "
                f"Slot-Reserve (Baritone cancel) ist die wahrscheinlichste Ursache.\n"
                f"  -> `reserveCombatSlot` bzw. `withCombatSlot` pruefen: jede Reserve bricht Follow, "
                f"Goal und Pathing ab. Haeufigeres Einreihen heilt das."
            )

        # 3) Ueberwiegt Passiv-Aktion — der Bot arbeitet viel, trifft aber nicht.
        if p.passive_share > 0.55 and p.exploded:
            out.append(
                f"`{name}`: {_fmt(p.passive_share)} der Samples sind Passiv-Aktionen "
                f"(beobachten/boden-sichern/rueckzugsschritt), obwohl im Fenster "
                f"{p.usage.get('ik_used_crystal', 0)} Crystals und "
                f"{p.usage.get('ik_used_anchor', 0)} Anker verbraucht wurden.\n"
                f"  -> {_lever(settings, 'balance-resources')} — dokumentierte Stellschraube "
                f"fuer den Mix. {_lever(settings, 'min-support-delay')}: bereits am "
                f"aggressivsten Ende der Skala."
            )

        # 4) Aura-Mix stark einseitig.
        cr, an = p.usage.get("ik_used_crystal", 0), p.usage.get("ik_used_anchor", 0)
        tot = cr + an
        if tot >= 200:
            share = cr / tot
            if share > 0.85:
                out.append(
                    f"`{name}`: {share * 100:.0f} % aller Explosionen sind Crystals "
                    f"({cr} vs. {an}). Anker/Glowstone kommen praktisch nicht zum Einsatz "
                    f"({p.usage.get('ik_used_glowstone', 0)} Glowstone fuer {an} Anker).\n"
                    f"  -> {_lever(settings, 'balance-resources')}. Der Modul-Kommentar nennt "
                    f"14-26 % Anker als realistischen Anteil; Testwert waere eine Erhoehung "
                    f"im Bereich bis {settings.get('balance-resources', {}).get('max', '?')}."
                )
            elif share < 0.55:
                out.append(
                    f"`{name}`: nur {share * 100:.0f} % der Explosionen sind Crystals "
                    f"({cr} vs. {an}). Der Bot ist ankerlastig und damit langsamer — "
                    f"ein Anker-Zyklus dauert laenger als ein Crystal-Zyklus.\n"
                    f"  -> {_lever(settings, 'balance-resources')} erhoehen oder "
                    f"{_lever(settings, 'min-support-delay')} pruefen."
                )

        # 5) Perl-Einsatz auffaellig niedrig trotz grosser Distanz.
        if p.usage.get("ik_used_pearl", 0) < 5 and p.dist_p90 > 12:
            out.append(
                f"`{name}`: p90-Distanz {p.dist_p90:.1f} Bloecke, aber nur "
                f"{p.usage.get('ik_used_pearl', 0)} Perlen im Fenster. Der Bot bleibt auf Distanz "
                f"und schiesst nicht.\n"
                f"  -> {_lever(settings, 'pearl-min-dist')}, {_lever(settings, 'pearl-gapclose')} "
                f"pruefen; die Distanz-Verteilung deutet darauf hin, dass die Perle als "
                f"loesende Option gar nicht greift."
            )
    return out


def report(profiles: dict[str, Profile], settings: dict[str, dict]) -> str:
    lines = ["# ProviPvP — Auswertung eines aufgezeichneten Laufs", ""]
    lines.append("Nur Vorschlaege. Nichts davon ist angewendet.")
    lines.append("")

    lines.append("## Rohdaten je Profil")
    lines.append("")
    lines.append("| Profil | Ticks | Trace-Zeilen | engaged | Passiv | follow\\|engaged | engaged & >6 | Distanz Median / p90 | Explosionen |")
    lines.append("|---|---:|---:|---:|---:|---:|---:|---:|---:|")
    for name, p in sorted(profiles.items()):
        expl = p.usage.get("ik_used_crystal", 0) + p.usage.get("ik_used_anchor", 0)
        lines.append(
            f"| {name} | {p.span_ticks} | {len(p.samples)} | {_fmt(p.engaged_share)} | "
            f"{_fmt(p.passive_share)} | {_fmt(p.follow_share_while_engaged)} | "
            f"{_fmt(p.engaged_but_far_share)} | {p.dist_median:.1f} / {p.dist_p90:.1f} | {expl} |"
        )
    lines.append("")

    lines.append("## Verbrauch im Kampffenster")
    lines.append("")
    keys = ["ik_used_crystal", "ik_used_anchor", "ik_used_glowstone", "ik_used_obsidian", "ik_used_pearl"]
    lines.append("| Profil | " + " | ".join(k.split("_")[-1] for k in keys) + " |")
    lines.append("|---" * (len(keys) + 1) + "|")
    for name, p in sorted(profiles.items()):
        lines.append(f"| {name} | " + " | ".join(str(p.usage.get(k, 0)) for k in keys) + " |")
    lines.append("")

    lines.append("## Aktionsverteilung")
    lines.append("")
    for name, p in sorted(profiles.items()):
        if not p.samples:
            continue
        c = Counter(s.action for s in p.samples)
        top = ", ".join(f"{a} {n}" for a, n in c.most_common(6))
        lines.append(f"- **{name}**: {top}")
    lines.append("")

    lines.append("## Vorschlaege")
    lines.append("")
    ps = proposals(profiles, settings)
    if not ps:
        lines.append("Keine Regel hat ausgeloest. Das kann bedeuten, dass die Laufzeit zu kurz war "
                     "oder dass kein Engpass auffaellt — dann taugen mehr Daten, nicht mehr Regeln.")
    else:
        lines.extend(f"{i}. {p}\n" for i, p in enumerate(ps, 1))

    lines.append("")
    lines.append("## Grenzen")
    lines.append("")
    lines.append("- `debug-trace` schreibt **bei Wechsel und alle 100 Ticks**, nicht jeden Tick. "
                 "Anteile sind damit Momentanaufnahmen, keine tickgenauen Zaehlungen.")
    lines.append("- Selbstschaden wird nicht direkt gemessen. Die Vorschlaege zu `max-self-damage` "
                 "sind deshalb bewusst nicht enthalten — dafuer braucht es Schadensdaten des Servers.")
    lines.append("- Ein Lauf auf einer Arena mit zwei Bots ist kein Gegnerbild. Vor uebernahme "
                 "gegen einen echten Server nachmessen.")
    return "\n".join(lines)


def main() -> int:
    ap = argparse.ArgumentParser(description="ProviPvP-Laufauswertung")
    ap.add_argument("daten", type=Path, help="Verzeichnis mit <bot>.log und _delta.json")
    ap.add_argument("--out", type=Path, default=None, help="Markdown-Bericht schreiben")
    args = ap.parse_args()

    if not args.daten.is_dir():
        print(f"Kein Verzeichnis: {args.daten}", file=sys.stderr)
        return 1

    profiles = load_profiles(args.daten)
    if not any(p.samples for p in profiles.values()):
        print("Keine Trace-Zeilen gefunden. Laeuft debug-trace, oder ist "
              "'<bot>.log' im Verzeichnis?", file=sys.stderr)
        return 1

    source = Path(__file__).resolve().parents[1] / 'src/main/java/com/provipvp/modules/GodmodePvP.java'
    settings = load_settings(source)
    text = report(profiles, settings)
    if args.out:
        args.out.write_text(text, encoding="utf-8")
        print(f"Bericht: {args.out}")
    else:
        print(text)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
