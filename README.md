# ProviPvP

A [Meteor Client](https://meteorclient.com/) addon for Minecraft **26.2** that turns Crystal/Anchor PvP into a fully
automated, self-managing combat bot — target tracking, explosive damage optimization, defensive reflexes, and
inventory upkeep, all in one package. Two combat profiles are included: a maximally aggressive bot and a
slower, more human-like variant for situations where blatant automation would stand out.

> Built as a personal anarchy-server project. Read the [Disclaimer](#disclaimer) before using this on any server
> that isn't rule-free.

## Quick Install (no modding experience needed)

If the only two things you know how to do are "put a mod jar in the mods folder" and "extract a zip file", this
is for you:

1. Install Fabric Loader for Minecraft 26.2 — download the installer from
   **[fabricmc.net/use/installer](https://fabricmc.net/use/installer/)**, open it, pick **26.2** as the Minecraft
   version, click **Install**. This creates a new profile in your Minecraft Launcher — nothing else to configure.
2. Download **[provipvp-modpack-26.2.zip](../../releases/download/modpack/provipvp-modpack-26.2.zip)** — it has
   everything else already inside (Fabric API, Meteor Client, Baritone, and ProviPvP itself).
3. Extract every `.jar` from that zip straight into `%appdata%\.minecraft\mods` (press Win+R, type
   `%appdata%\.minecraft\mods`, create the `mods` folder if it doesn't exist yet, drop the files in).
4. Open the Minecraft Launcher, select the new Fabric 26.2 profile, play. In-game, **Right Shift** opens Meteor's
   menu — `GodmodePvP` and `HumanPvP` are under **Combat**.

Everything past this point is for anyone who wants to understand the settings, add more addons, or build from
source.

## Requirements

| Dependency     | Version           |
|----------------|-------------------|
| Minecraft      | 26.2              |
| Fabric Loader  | 0.19.3+           |
| Java           | 25+               |
| [Meteor Client](https://meteorclient.com/) | 26.2 |
| [Baritone](https://github.com/cabaletta/baritone) (standalone Fabric build) | 26.2 |

## Installation

1. Install [Fabric Loader](https://fabricmc.net/) for Minecraft 26.2.
2. Download and place in your `mods` folder:
    - [Fabric API](https://modrinth.com/mod/fabric-api)
    - [Meteor Client](https://meteorclient.com/)
    - [Baritone](https://github.com/cabaletta/baritone/releases) (standalone Fabric release)
3. Download the latest `provi-pvp-*.jar` from the [Releases](../../releases) page and drop it into the same
   `mods` folder.
4. Launch the game. Two new modules appear in Meteor's **Combat** category: `GodmodePvP` and `HumanPvP`.

## Full Addon Stack

`provi-pvp` only adds the combat bot itself — everything below is the rest of the Meteor addon stack this setup is
actually run with. None of these are required for `provi-pvp` to function, but this is what the author's own
`mods` folder looks like. Grab whichever you want from their respective releases pages and drop them in `mods`
alongside everything above.

| Addon | Repo | Purpose |
|---|---|---|
| Numby Hack | [cqb13/Numby-hack](https://github.com/cqb13/Numby-hack) | General-purpose utility modules addon. |
| Nora Tweaks | [noramibu/nora-tweaks](https://github.com/noramibu/nora-tweaks) | Quality-of-life modules for Meteor. |
| Meteor+ | [MeteorClientPlus/MeteorPlus](https://github.com/MeteorClientPlus/MeteorPlus) | Large "blatant features" addon (combat, movement, render, exploits). |
| Wurstmeteor Addon | [njlent/Wurstmeteor-Meteor-client-addon](https://github.com/njlent/Wurstmeteor-Meteor-client-addon) | Ports selected Wurst Client features (criticals, mace-dmg, infinite-reach, and more) to Meteor. |
| Damage Numbers Addon | [njlent/Damagenumbers-Meteor-client-addon](https://github.com/njlent/Damagenumbers-Meteor-client-addon) | Floating damage numbers and hit particles/chat feedback. |
| Minehop | [njlent/Minehop-Meteor-client-addon](https://github.com/njlent/Minehop-Meteor-client-addon) | Source-engine-style bunnyhop/air-strafe movement. |
| FumoUtils | [qnxt/fumo-utils](https://github.com/qnxt/fumo-utils) | Anarchy-focused utility modules, commands, and highway tools. Bundles a couple of dupe-glitch modules alongside the legitimate ones — all Meteor modules are off by default, so installing it doesn't enable anything on its own; leave those specific ones off if that matters to you. |
| Meteor Addons | [MCDxAI/meteor-addons-addon](https://github.com/MCDxAI/meteor-addons-addon) | In-game addon browser/installer/updater — manage the rest of this list from Meteor's own GUI. |
| Meteor Villager Roller | [maxsupermanhd/meteor-villager-roller](https://github.com/maxsupermanhd/meteor-villager-roller) | Automates villager profession-rerolling until a desired enchantment trade is found. |
| MeteorAdditions | [JFronny/MeteorAdditions](https://github.com/JFronny/MeteorAdditions) | Server-list discovery/cleanup tools, ModMenu integration. |
| Seija-Printer | [Nippaku-Zanmu/Seija-Printer](https://github.com/Nippaku-Zanmu/Seija-Printer) | Fast Litematica schematic printer. |
| IKEA Addon | [Nooniboi/Public-Ikea](https://github.com/Nooniboi/Public-Ikea) | Item-duplication-glitch toolkit (`DubCounter`, `AutoShulkerDrop`, `AutoItemMove`, and related anti-loss modules). Listed for completeness since it's part of the author's actual setup — duplication exploits are the single most heavily punished category of rule-breaking on almost every server, anarchy servers included. Use at your own risk. |

## Modules

### GodmodePvP (`.pvp`)

The fully aggressive profile. Optimized for winning trades as fast as possible, not for looking legitimate.

- **Target acquisition & movement** — tracks the closest valid player within `follow-range`, but only actively
  closes distance (walking or pearling) once inside a separate, smaller `engage-distance` — prevents the bot from
  sprinting across the map the instant it's turned on. Engagement is "sticky": once a fight is underway, a hard
  Crystal/Anchor knockback that briefly throws the distance back out won't cause the bot to give up the chase.
- **Damage-optimized Crystal/Anchor placement** — evaluates every reachable placement spot each tick, picks
  whichever deals more damage to the target than to itself, respects a self-damage cap, and avoids friendly fire
  against anyone on the Meteor friends list.
- **D-Tap** — after a knockback hit, places obsidian in the target's predicted flight path and detonates two
  Crystals spaced at the hit-invulnerability window, for a fast double-totem-pop.
- **Combat mechanics** — automatic axe/sword/mace swapping, shield-breaking, pre-hits before explosions,
  crit-jumping, W-tap sprint-reset for maximized knockback, circle-strafing with randomized (non-periodic)
  direction switching.
- **Positioning** — seeks natural or self-built one-block-deep cover, prefers standing lower than the target for
  a favorable explosion-damage ratio, builds emergency obsidian cover when nothing natural is nearby.
- **Defensive reflexes** — auto-shield on freshly placed enemy Crystals, knockback-recovery pearling, obstacle
  pearling, cage/critical-HP escape pearls, retreat on a losing trade or when out of totems and explosives.
- **Movement tuning** — Baritone is configured for aggressive pursuit (parkour, diagonal movement, cliff jumps,
  fire crossing) with `NoFall` and `AutoEat` wired in to make that survivable.
- **Inventory management** — restocks Crystals, Anchors, Glowstone, Pearls, Obsidian, and Cobweb from the main
  inventory into the hotbar as they run low, with configurable thresholds.

### HumanPvP (`.hpvp`)

A deliberately slower, imperfect variant designed to look like manual play: randomized reaction delay before
engaging a new target, capped turn speed instead of instant snapping, a chance to simply miss a "ready" hit, and
the same core Crystal/Anchor/defense logic as `GodmodePvP` tuned to a more conservative self-damage limit.

### TrainingDummy

A standalone module that spawns a fake player with configurable HP (including live adjustment while it's
active, or an invincible mode) to test attack, knockback, and combat-timing changes without needing a second
account or a live server.

### Auto5b5tDupe

A crafting-exploit dupe module for the 5b5t anarchy server, ported from
[mmvanheusden/meteor-5b5t-addon](https://github.com/mmvanheusden/meteor-5b5t-addon) (GPL-3.0) onto 26.2/Mojang
mappings - the original targeted 1.21.5/Yarn and its recipe-book API no longer exists in this form. Source was
read in full before porting: pure crafting-packet exploit, no network calls or third-party services involved.
Whether the underlying server-side race condition is still unpatched is unverified - the original was last
confirmed working in May 2025. Test with the `single` setting on a worthless item first.

## Commands

| Command                | Effect                          |
|-------------------------|---------------------------------|
| `.pvp` / `.pvp toggle`  | Toggle `GodmodePvP`             |
| `.pvp on` / `.pvp off`  | Explicitly enable/disable       |
| `.hpvp` / `.hpvp toggle`| Toggle `HumanPvP`                |
| `.hpvp on` / `.hpvp off`| Explicitly enable/disable       |

Every behavior described above is a separate Meteor setting under the module's ClickGUI entry — nothing is
hardcoded that couldn't reasonably need tuning per server. See **[FEATURES.md](FEATURES.md)** for the full
settings reference (every setting, its default, and what it does) plus the third-party tools this project builds on.

## Building from source

```bash
git clone https://github.com/phil-ship-it/provi-pvp.git
cd provi-pvp
./gradlew build
```

The compiled jar is written to `build/libs/`. To run a development client directly from your IDE, use the
`Minecraft Client` run configuration generated by Fabric Loom.

Every push builds automatically via GitHub Actions and publishes the resulting jar as a prerelease under the
[`snapshot` tag](../../releases/tag/snapshot) — that build always reflects the latest commit. Separately, a
**weekly build** runs every Sunday and publishes a dated, non-prerelease [release](../../releases) with an
auto-generated changelog of everything merged that week — that's the one to grab if you want something more
stable than the rolling snapshot.

## Disclaimer

This project automates gameplay in ways that violate the terms of service of essentially every Minecraft server
that isn't an explicitly rule-free anarchy server. It was written for exactly that use case. Using it anywhere
else will get you banned, and deservedly so.

## License

[GPL-3.0](LICENSE), matching Meteor Client's own license.
