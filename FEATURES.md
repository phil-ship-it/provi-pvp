# Features & Settings Reference

Every behavior listed here is a real, individually toggleable Meteor setting under the module's ClickGUI entry —
nothing described in the [README](README.md) is hardcoded. Defaults are the values the module ships with.

## GodmodePvP (`.pvp`)

### General

| Setting | Default | Description |
|---|---|---|
| `follow` | `true` | Automatically pursues the target with Baritone once it's within `engage-distance`. |
| `follow-range` | `40` | Maximum distance at which a player is even recognized/watched as a target. |
| `engage-distance` | `16` | Only within this distance does the bot actually walk/pearl toward the target. Beyond it (up to `follow-range`) it just watches — prevents the bot from sprinting across the map the instant it's activated. Sticky: a hard knockback that briefly throws the distance back out mid-fight won't cause it to give up. |
| `pop-threshold` | `8.0` | HP drop counted as a totem pop. |
| `prediction-ticks` | `5` | How far ahead enemy movement is predicted for attacks. |
| `ignore-fire` | `true` | Walks straight through ground fire in melee range instead of pathing around it (Baritone otherwise treats fire as hard-impassable). |

### Combat

| Setting | Default | Description |
|---|---|---|
| `smart-auras` | `true` | Chooses Crystal or Anchor based on a real damage calculation. |
| `anchor-mode` | `1` | `0` = automatic (always max damage), `1` = use Anchor even on a damage tie, `2` = off. |
| `use-anchors` | `true` | Allow Anchors at all (costs 1 Glowstone per detonation). |
| `pre-hit` | `true` | Melees the target right before the explosion for extra damage. |
| `prefer-axe-melee` | `true` | Automatically swaps to the axe for melee hits (axe-swap meta). |
| `shield-breaker` | `true` | Swaps to the axe against a blocking target. |
| `melee-strafe` | `true` | Faces the target and circle-strafes in melee — harder to hit, varies the explosion angle. Direction switches on a randomized interval, not a fixed period. |
| `sprint-reset` | `true` | W-tap: briefly cancels and re-enables sprint before every melee hit so *every* hit gets the sprint-knockback bonus, not just the first of a sprint sequence. |
| `track-target` | `true` | Keeps looking at the target's predicted position outside melee-strafe range, instead of only during a single aim action. |
| `crit-jump` | `true` | Jumps right before swinging so the hit lands while falling (+50% damage) — the same thing real top-tier players do. |
| `d-tap` | `true` | After a knockback hit, places obsidian in the predicted flight path and detonates two Crystals spaced at the hit-invulnerability window, for a fast double-totem-pop. |
| `use-mace` | `true` | Uses the Mace over axe/sword for finishing hits while falling (Smash Attack bonus). |
| `elytra-combat` | `true` | Firework boost when gliding speed drops too low during elytra combat. |
| `zero-delay` | `true` | Sets CrystalAura's placement delay to 0 (instant reaction). |
| `kill-aura` | `false` | Also runs Meteor's KillAura for melee. Mob filter is shared with the `Mobs` group. Off by default since the built-in axe-melee logic already covers it. |
| `escape-pearl` | `true` | Pearls away at low HP with an enemy nearby. |
| `knockback-pearl` | `true` | If the bot itself gets launched into the air by knockback (hit or explosion), immediately pearls straight down — controlled descent instead of falling helplessly or hanging in the air as an easy target. |

### Defense

| Setting | Default | Description |
|---|---|---|
| `fast-totem` | `true` | Checks the offhand every tick and refills a totem the instant it's used. |
| `auto-mend` | `true` | Repairs armor with XP (Meteor's AutoMend). |
| `auto-eat` | `true` | Eats automatically (Meteor's AutoEat) when hunger is low — without enough saturation Minecraft itself disables sprinting, which breaks sprint-reset knockback and Baritone's movement speed. |
| `no-fall` | `true` | Prevents fall damage (Meteor's NoFall) — needed because Baritone here is deliberately tuned for aggressive jump/cliff pursuit (up to 20 blocks of fall height without water). |
| `auto-shield` | `true` | Briefly raises the shield when an enemy Crystal is freshly placed nearby, reducing explosion damage. |
| `anti-rubberband` | `true` | Detects server position corrections (rubberbanding) and discards the stale path instead of fighting against the correction. |
| `respect-friends` | `true` | Avoids explosions that would also hit a player on the Meteor friends list. |
| `hole-awareness` | `true` | Looks for a nearby one-block-deep, open-topped hole in close combat and uses it as a fighting position instead of standing in the open. |
| `height-advantage` | `true` | Prefers a position lower than the target — your own explosions deal more damage from there, the enemy's deal less. |
| `build-cover` | `true` | Places obsidian to close open sides when no natural hole is nearby. |
| `peek-tactic` | `true` | Crouches in cover while nothing is actively happening, only standing up briefly to attack. |
| `retreat-threshold` | `true` | Breaks off the fight (retreats) once totems drop below 2 **and** there are no Crystal/Anchor resources left. |
| `retreat-on-losing-trade` | `true` | Pearls away if the bot itself was just hard-hit (popped) but its own Crystal/Anchor explosions haven't damaged the target in a while — recognizes a losing trade instead of continuing pointlessly. |
| `multi-target-alarm` | `true` | Warns and becomes briefly more cautious when a second player shows up nearby during a fight. |
| `trap-mode` | `1` | Cobweb at the target's feet to slow them: `0` = off, `1` = only when the target is close (≤6 blocks), `2` = always. |
| `max-self-damage` | `12.0` | Maximum self-damage tolerated per placement spot. |

### Inventory

| Setting | Default | Description |
|---|---|---|
| `inv-manager` | `true` | Moves combat items (Crystals, Anchors, Glowstone, Pearls, Obsidian, Cobweb) that are running low from the main inventory into the hotbar. |
| `min-crystals` | `32` | Restock threshold for Crystals. |
| `min-anchors` | `4` | Restock threshold for Respawn Anchors. |
| `min-glowstone` | `8` | Restock threshold for Glowstone (Anchor fuel). |
| `min-pearls` | `8` | Restock threshold for Ender Pearls. |
| `min-obsidian` | `16` | Restock threshold for Obsidian (D-Tap, emergency cover). |
| `min-web` | `4` | Restock threshold for Cobweb (trap). |

### Mobs

| Setting | Default | Description |
|---|---|---|
| `attack-mobs` | `false` | Attacks mobs when no player is in range. |
| `mob-types` | — | Which mob types get attacked (also passed through to KillAura/CrystalAura). |
| `mob-range` | `10` | Range for mob attacks. |

### Ender Pearls

| Setting | Default | Description |
|---|---|---|
| `pearl-gapclose` | `true` | Pearls toward the target when it's too far away (with rotation onto the target). |
| `pearl-min-dist` | `4.0` | Distance beyond which a pearl is thrown — set to `4` this means as soon as melee range (3.6 blocks) is no longer enough. |

## HumanPvP (`.hpvp`)

A deliberately slower, imperfect profile built to look like manual play. Shares the same Crystal/Anchor/defense
core as `GodmodePvP`, with these differences:

| Setting | Default | Description |
|---|---|---|
| `follow-range` | `20` | Smaller detection range than `GodmodePvP` by default. |
| `engage-distance` | `14` | Same sticky-engagement behavior as `GodmodePvP`, tuned to a shorter range. |
| `reaction-min` / `reaction-max` | `3` / `9` ticks | Randomized reaction delay before engaging a newly acquired target — no instant snap-to-target. |
| `attack-chance` | `0.9` | Probability that a "ready" hit is actually thrown, simulating human misclicks. |
| `aim-tolerance` | `4.0°` | Aim tolerance before a hit or placement is executed. |
| `max-turn-speed` | `18.0°/tick` | Maximum camera rotation per tick — human-paced turning instead of an instant snap. |
| `max-self-damage` | `6.0` | More conservative self-damage cap than `GodmodePvP`'s `12.0`. |

All other Combat/Defense/Inventory/Pearl settings mirror `GodmodePvP` (same names, same purpose) unless listed
above.

## TrainingDummy

| Setting | Default | Description |
|---|---|---|
| `health` | `20` | Dummy's HP — can be changed live while it's running. |
| `kb-strength` | `0.5` | Horizontal knockback strength. |
| `kb-up` | `0.4` | Vertical knockback (launch height). |
| `auto-respawn` | `true` | Respawns the dummy when it dies or disappears. |
| `invincible` | `false` | HP never reaches 0 — no despawn/respawn needed, uninterrupted practice. |

## Commands

| Command | Effect |
|---|---|
| `.pvp` / `.pvp toggle` | Toggle `GodmodePvP` |
| `.pvp on` / `.pvp off` | Explicitly enable/disable `GodmodePvP` |
| `.hpvp` / `.hpvp toggle` | Toggle `HumanPvP` |
| `.hpvp on` / `.hpvp off` | Explicitly enable/disable `HumanPvP` |

## Third-party tools this project relies on

| Tool | Role |
|---|---|
| [Meteor Client](https://meteorclient.com/) | Host client / addon API, provides `CrystalAura`, `KillAura`, `AutoMend`, `AutoEat`, `NoFall`, the Friends system, and the settings/GUI framework this addon builds on. |
| [Baritone](https://github.com/cabaletta/baritone) | Pathfinding and movement execution (`FollowProcess`, `CustomGoalProcess`, `PathingBehavior`) — this addon configures and drives it, it doesn't reimplement movement itself. |
| [Fabric Loader](https://fabricmc.net/) / [Fabric API](https://modrinth.com/mod/fabric-api) | Mod loading platform. |
