# ProviPvP

A [Meteor Client](https://meteorclient.com/) addon for Minecraft **26.2** that turns Crystal/Anchor PvP into a fully
automated, self-managing combat bot — target tracking, explosive damage optimization, defensive reflexes, and
inventory upkeep, all in one package. Two combat profiles are included: a maximally aggressive bot and a
slower, more human-like variant for situations where blatant automation would stand out.

> Built as a personal anarchy-server project. Read the [Disclaimer](#disclaimer) before using this on any server
> that isn't rule-free.

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

## Commands

| Command                | Effect                          |
|-------------------------|---------------------------------|
| `.pvp` / `.pvp toggle`  | Toggle `GodmodePvP`             |
| `.pvp on` / `.pvp off`  | Explicitly enable/disable       |
| `.hpvp` / `.hpvp toggle`| Toggle `HumanPvP`                |
| `.hpvp on` / `.hpvp off`| Explicitly enable/disable       |

Every behavior described above is a separate Meteor setting under the module's ClickGUI entry — nothing is
hardcoded that couldn't reasonably need tuning per server.

## Building from source

```bash
git clone https://github.com/<your-username>/provi-pvp.git
cd provi-pvp
./gradlew build
```

The compiled jar is written to `build/libs/`. To run a development client directly from your IDE, use the
`Minecraft Client` run configuration generated by Fabric Loom.

Every push builds automatically via GitHub Actions and publishes the resulting jar as a prerelease under the
[`snapshot` tag](../../releases/tag/snapshot) — that build always reflects the latest commit.

## Disclaimer

This project automates gameplay in ways that violate the terms of service of essentially every Minecraft server
that isn't an explicitly rule-free anarchy server. It was written for exactly that use case. Using it anywhere
else will get you banned, and deservedly so.

## License

[GPL-3.0](LICENSE), matching Meteor Client's own license.
