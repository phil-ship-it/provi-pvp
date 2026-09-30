"""Zeichnet einen Bot-Lauf auf, damit `analyse.py` ihn auswerten kann.

Startet beide TestBots, misst die Resource-Zaehler des Test-Datapacks
`infinite_kit` vor und nach dem Kampffenster und legt alles in ein
Ausgabeverzeichnis.

Voraussetzungen:
  - der Arena-Server laeuft auf 127.0.0.1:25565 (`bottest-server`)
  - `GodmodePvP.debug-trace` steht auf `true`
  - `meteorist.jar` liegt NICHT in den Bot-Instanzen — der Add-on crasht den Client-Start

Waehrend der Bots laeuft, muessen Kopplung und Usage-Abfrage in der
Server-Konsole erfolgen (siehe `AUSWERFUNG` unten). Der Server laeuft als
eigener Prozess, deshalb bedient dieses Skript seine Konsole nicht.

Aufruf:  python tools/collect_run.py <dauer_sekunden> [ausgabe]
"""
from __future__ import annotations

import hashlib
import json
import os
import re
import subprocess
import sys
import time
import uuid as U
from pathlib import Path

BASE = Path(__file__).resolve().parents[2]
APPDATA = Path(os.environ["APPDATA"])
MINECRAFT = APPDATA / ".minecraft"
BOTS = ("TestBot_1", "TestBot_2")
JAVA = r"C:\Users\philf\.gradle\jdks\eclipse_adoptium-25-amd64-windows.2\bin\java.exe"
SERVER = BASE / "bottest-server"

AUSWERFUNG = """Waehrend die Bots laufen, in der Server-Konsole ausfuehren:

  tp TestBot_1 1000 -58 1000
  tp TestBot_2 1000 -58 1000
  scoreboard players get <bot> ik_used_crystal   (fuer beide Bots, alle Zaehler)

Das Kampffenster beginnt, sobald die Bots gekoppelt sind."""


def classpath() -> str:
    """Baut den Classpath aus Loader- und Vanilla-Bibliotheken der Haupt-Installation."""
    parts: list[Path] = []
    loader_json = MINECRAFT / "versions/fabric-loader-0.19.3-26.2/fabric-loader-0.19.3-26.2.json"
    for lib in json.loads(loader_json.read_text(encoding="utf-8")).get("libraries", []):
        art = (lib.get("downloads") or {}).get("artifact")
        p = (MINECRAFT / "libraries" / art["path"]) if art and art.get("path") else None
        if p is None:
            group, artifact, version = lib["name"].split(":")
            p = (MINECRAFT / "libraries" / Path(*group.split(".")) / artifact / version
                 / f"{artifact}-{version}.jar")
        if p.exists():
            parts.append(p)

    version_json = MINECRAFT / "versions/26.2/26.2.json"
    for lib in json.loads(version_json.read_text(encoding="utf-8")).get("libraries", []):
        rules = lib.get("rules") or []
        if rules and not any(r.get("os", {}).get("name") == "windows"
                             for r in rules if r.get("action") == "allow"):
            continue
        art = (lib.get("downloads") or {}).get("artifact")
        if art and art.get("path"):
            p = MINECRAFT / "libraries" / art["path"]
            if p.exists() and p not in parts:
                parts.append(p)

    parts.append(MINECRAFT / "versions/26.2/26.2.jar")
    return ";".join(str(p) for p in parts)


def offline_uuid(name: str) -> str:
    digest = hashlib.md5(("OfflinePlayer:" + name).encode()).digest()
    return str(U.UUID(bytes=digest[:16], version=3))


def launch(bot: str, cp: str, log_path: Path) -> subprocess.Popen:
    game_dir = APPDATA / ".minecraft-bots" / bot
    args = [
        "-Xmx2G", "-Xms512M", f"-Dfabric.gameDir={game_dir}", "-Dfabric.dli.env=client",
        "-Dfabric.dli.main=net.fabricmc.loader.impl.launch.knot.KnotClient",
        "-cp", cp, "net.fabricmc.loader.impl.launch.knot.KnotClient",
        "--username", bot, "--version", "26.2", "--gameDir", str(game_dir),
        "--assetsDir", str(MINECRAFT / "assets"), "--assetIndex", "32",
        "--uuid", offline_uuid(bot), "--accessToken", "0", "--userType", "legacy",
        "--versionType", "release", "--quickPlayMultiplayer", "127.0.0.1:25565",
        "--width", "854", "--height", "480",
    ]
    handle = log_path.open("w", encoding="utf-8", errors="replace")
    return subprocess.Popen([JAVA] + args, cwd=str(game_dir), stdout=handle, stderr=subprocess.STDOUT)


def read_usage() -> dict[str, int]:
    """Liest die aktuellen Zaehler aus dem Serverlog.

    `scoreboard players get` schreibt seinen Wert dorthin, sobald der Befehl in der
    Server-Konsole abgesetzt wurde.
    """
    log = SERVER / "logs/latest.log"
    text = log.read_text(encoding="utf-8", errors="replace")
    return {f"{bot}|{obj}": int(val)
            for bot, val, obj in re.findall(r"(TestBot_\d) has (\d+) \[(ik_used_\w+)\]", text)}


def park_meteorist() -> list[Path]:
    """Legt meteorist.jar zurueck — der Add-on crasht den Client-Start."""
    moved = []
    for bot in BOTS:
        jar = APPDATA / ".minecraft-bots" / bot / "mods" / "meteorist.jar"
        if jar.exists():
            aside = jar.with_suffix(".jar.geparkt")
            jar.replace(aside)
            moved.append(aside)
    return moved


def unpark_meteorist(moved: list[Path]) -> None:
    for aside in moved:
        target = aside.with_name("meteorist.jar")
        if not target.exists():
            aside.replace(target)


def main() -> int:
    if len(sys.argv) < 2:
        print(__doc__)
        return 1
    duration = int(sys.argv[1])
    out = Path(sys.argv[2]) if len(sys.argv) > 2 else BASE / "analysedaten"
    out.mkdir(parents=True, exist_ok=True)

    parked = park_meteorist()
    cp = classpath()
    procs = {bot: launch(bot, cp, out / f"{bot}.log") for bot in BOTS}
    for bot, proc in procs.items():
        print(f"{bot} gestartet, pid {proc.pid}")

    try:
        print("warte 100 s bis beide verbunden sind ...", flush=True)
        time.sleep(100)
        (out / "_baseline.json").write_text(json.dumps(read_usage()), encoding="utf-8")
        print("Baseline gesichert.\n")
        print(AUSWERFUNG)
        print(f"\nKampffenster laeuft {duration} s ...", flush=True)
        time.sleep(duration)
        (out / "_delta.json").write_text(json.dumps(read_usage()), encoding="utf-8")
    finally:
        for proc in procs.values():
            subprocess.run(["taskkill", "/PID", str(proc.pid), "/T", "/F"], capture_output=True)
        unpark_meteorist(parked)
        log = SERVER / "logs/latest.log"
        if log.exists():
            (out / "server.log").write_bytes(log.read_bytes())

    print(f"\nFertig. Auswertung mit:  python tools/analyse.py {out}")
    for f in sorted(out.iterdir()):
        print("  ", f.name, f.stat().st_size, "bytes")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
