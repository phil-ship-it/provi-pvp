# Features & Settings Reference

Every behavior listed here is a real, individually toggleable Meteor setting under the module's ClickGUI entry —
nothing described in the [README](README.md) is hardcoded. Defaults are the values the module ships with.

## GodmodePvP (`.pvp`)

### General

| Setting | Default | Description |
|---|---|---|
| `follow` | `true` | Automatically pursues the target with Baritone once it's within `engage-distance`. |
| `follow-range` | `40` | Maximum distance at which a player is even recognized/watched as a target. |
| `engage-distance` | `16` | Only within this distance does the bot actually walk/pearl toward the target. Beyond it (up to `follow-range`) it just watches — prevents the bot from sprinting across the map the instant it's activated. Sticky in two ways: a hard knockback that briefly throws the distance back out mid-fight won't cause it to give up, and once actually engaged the bot keeps fighting the *same* target (by identity, not just distance) until it dies, leaves, or goes out of `follow-range` — a third player briefly wandering closer no longer steals the fight. |
| `attack-range` | `3.6` | Maximum distance for melee hits (pre-hit, melee-fallback, pop-burst). Some servers/anti-cheats tolerate more or less than the vanilla-ish default. |
| `smart-targeting` | `true` | Prefers an isolated target (no other player within `backup-range`) over pure distance when first picking a target — a lone player is a safer, faster kill than one with backup nearby, even if slightly farther away. Only affects *initial* target choice; once actually engaged, the bot commits to that target (see below) rather than re-evaluating every tick and flip-flopping whenever a third player briefly looks closer/more isolated. |
| `backup-range` | `10.0` | How close another player has to be to a candidate target to count as "has backup" for `smart-targeting`. |
| `pop-threshold` | `8.0` | HP drop counted as a totem pop. |
| `prediction-ticks` | `5` | How far ahead enemy movement is predicted for attacks. |
| `ignore-fire` | `true` | Walks straight through ground fire in melee range instead of pathing around it (Baritone otherwise treats fire as hard-impassable). |
| `free-look` | `false` | Silent rotations: the bot still aims/turns correctly for attacks, placements, and target tracking (the outgoing packet carries the correct look direction), but your own camera stays free to look around. **Off by default** — separate rotation packets with no matching camera movement are one of the most classic anti-cheat detection signatures (Vulcan/Grim/Matrix/NCP all have explicit rotation checks for exactly this), and can cause movement corrections/rubberbanding on servers with active anti-cheat. |

### Combat

| Setting | Default | Description |
|---|---|---|
| `smart-auras` | `true` | Chooses Crystal or Anchor based on a real damage calculation. With zero End Crystals AND zero (Respawn Anchor + Glowstone) in the inventory, skips the whole damage/position simulation and forces melee-only instead of endlessly re-simulating and toggling Meteor's CrystalAura for items that don't exist (that dead-weight simulation was itself a source of visible movement stutter). |
| `anchor-mode` | `1` | `0` = automatic (always max damage), `1` = use Anchor even on a damage tie, `2` = off. |
| `use-anchors` | `true` | Allow Anchors at all (costs 1 Glowstone per detonation). |
| `use-beds` | `false` | Bed Aura: places and detonates beds as an explosive (damage value 5.0, same as Anchor). Only works outside the Overworld (Nether/End — e.g. portal camping on 5b5t); the client can't verify this ahead of time, only the server decides. Off by default so beds aren't wasted in the Overworld (where using one just sleeps/sets your spawn point instead of exploding). When both an Anchor and a Bed are viable, whichever deals more damage wins — Anchor only needs 1 Glowstone, so it's usually the more efficient default on an exact tie. |
| `no-delay` | `false` | Instant mode: strips out every remaining artificial wait — Anchor/Bed placement and maintenance pauses, D-Tap cooldown, all ender pearl throw cooldowns. Pure speed over caution: can burn through pearls/Anchors/Beds faster than the server can actually process the resulting actions. Two exceptions stay active regardless — the aura-switch hysteresis and the `min-support-delay` floor — because those aren't caution, they're technical requirements: removing them broke Crystal placement's sequence-number prediction entirely (0 damage from any source, confirmed in testing) rather than just making things faster. |
| `pre-hit` | `true` | Melees the target right before the explosion for extra damage. |
| `melee-fallback` | `true` | Melees normally whenever no explosion is actually about to land (e.g. Crystal mode is on but there's no obsidian left for a support block in open air, or no valid spot at all) — without this, the bot previously just stood there once every explosive option stopped being genuinely achievable, even while `pre-hit`'s own condition kept reporting "explosion imminent" just because CrystalAura was switched on. |
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
| `min-support-delay` | `4` | Minimum tick gap between placing an obsidian support block and the following crystal placement (CrystalAura's `support-delay`). Both actions use Minecraft's own sequence-numbered block-prediction system (since 1.19) — sending them too close together, before the first sequence is server-acknowledged, can desync the prediction ("crystal hitbox appears, but no crystal actually spawns"). Needs more headroom on high-latency or cross-version-translated (e.g. ViaVersion) connections than Meteor's own default. Only ever raised, never lowered. |
| `kill-aura` | `false` | Also runs Meteor's KillAura for melee. Mob filter is shared with the `Mobs` group. Off by default since the built-in axe-melee logic already covers it. |
| `escape-pearl` | `true` | Pearls away at low HP with an enemy nearby. |
| `knockback-pearl` | `true` | Pearls straight down for a controlled landing whenever the bot is in real danger from a fall: either just launched by knockback (hit or explosion) with strong upward velocity, or generally airborne and already 3+ blocks into a fall (the same height Minecraft itself starts counting fall damage from) — not just the post-hit case, so walking off a ledge or getting launched by something else entirely still gets caught. |

### Defense

| Setting | Default | Description |
|---|---|---|
| `fast-totem` | `true` | Checks the offhand every tick and refills a totem the instant it's used. Keeps working while the Meteor ClickGUI or your own inventory (E) is open — only pauses while a genuine foreign container (chest, ender chest, anvil, shulker box, ...) is open, since that GUI remaps inventory slot IDs and blind swaps there could move the wrong item. If no totem is left anywhere, this is detected the same tick (not on the next periodic inventory scan) and reported once in chat. |
| `auto-mend` | `true` | Repairs armor with XP (Meteor's AutoMend). |
| `auto-eat` | `true` | Eats automatically (Meteor's AutoEat) when hunger is low — without enough saturation Minecraft itself disables sprinting, which breaks sprint-reset knockback and Baritone's movement speed. |
| `no-fall` | `true` | Prevents fall damage (Meteor's NoFall) — needed because Baritone here is deliberately tuned for aggressive jump/cliff pursuit (up to 20 blocks of fall height without water). |
| `auto-shield` | `true` | Briefly raises the shield when an enemy Crystal is freshly placed nearby, reducing explosion damage. |
| `anti-rubberband` | `true` | Detects a server position correction and drops the stale path instead of fighting it. A moderate jump only counts outside of combat (normal explosion knockback shouldn't trigger it); a genuinely extreme jump triggers regardless, since real knockback rarely covers that much distance in one tick and rubberbanding is most common during actual Crystal/Anchor fights. |
| `respect-friends` | `true` | Avoids explosions that would also hit a player on the Meteor friends list. |
| `hole-awareness` | `true` | Looks for a nearby one-block-deep, open-topped hole in close combat and uses it as a fighting position instead of standing in the open. |
| `height-advantage` | `true` | Prefers a position lower than the target — your own explosions deal more damage from there, the enemy's deal less. |
| `avoid-lava` | `true` | Skips crystal/anchor placement spots directly next to lava (Nether lakes, bedrock pools) — prevents self-ignition and unleashing a flood of lava after the explosion. |
| `auto-fire-res` | `true` | Drinks a Fire Resistance potion automatically whenever you're in the Nether and don't already have one active — makes lava contact, fire, and burning explosion damage irrelevant. Runs independently of whether a fight is happening. |
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
| `min-beds` | `4` | Restock threshold for Beds (Bed Aura). Works correctly regardless of the item's actual max stack size — reads it dynamically instead of assuming vanilla's default, so it's unaffected by servers that raise beds to a 64-stack (e.g. 5b5t). |
| `min-heal-potions` | `8` | Restock threshold for Splash Healing Potions. Same stack-size-agnostic handling as `min-beds`. |

### Mobs

| Setting | Default | Description |
|---|---|---|
| `attack-mobs` | `false` | Attacks mobs when no player is in range. |
| `mob-types` | — | Which mob types get attacked (also passed through to KillAura/CrystalAura). |
| `mob-range` | `10` | Range for mob attacks. |

### Ender Pearls

| Setting | Default | Description |
|---|---|---|
| `pearl-gapclose` | `true` | Pearls toward the target when it's too far away to melee-hit or deal damage. The threshold is coupled to `attack-range` (never lower than `attack-range + 0.5`, regardless of `pearl-min-dist`) so it can't fire while melee could still connect, and requires a clear line of sight to the target — without it, this used to throw straight into whatever wall or hill was in between over long distances instead of holding the pearl for a clear shot. (The separate close-range "obstacle" pearl still deliberately throws through thin obstructions — that one's an intentional clip trick, not a mistake.) |
| `pearl-min-dist` | `4.0` | Distance beyond which a pearl is thrown — acts as a floor on top of `attack-range` (see `pearl-gapclose`), not an independent value, so lowering it below your configured `attack-range` has no effect. |

### Healing

| Setting | Default | Description |
|---|---|---|
| `heal-potions` | `true` | Throws a Splash Potion of Healing/Strong Healing at your own feet the instant fresh damage is detected — it shatters on the ground right there and heals immediately. Works regardless of combat/engage state, so it also covers fall/fire/environmental damage, not just hits taken mid-fight. Needs a Splash Healing potion in inventory (works fine as a 64-stack on servers with expanded stack sizes). |
| `heal-min-damage` | `3.0` | HP lost within a short rolling window (0.4s) must reach this before a potion is thrown — prevents wasting a potion on every tiny scratch, while still catching a hit whose damage/knockback ticks land a frame or two apart (which a strict single-tick comparison used to miss entirely, making throws feel late/skipped). |
| `heal-cooldown` | `12` | Minimum ticks between two thrown potions — stops a single multi-hit combo from burning several potions at once, without stalling badly under sustained pressure (multiple pops in quick succession). Skipped entirely while actively shield-blocking or drinking Fire Resistance, since both hold Meteor's shared hotbar-swap-back slot for several ticks — throwing a potion in the middle would silently corrupt that slot and leave the module stuck on the wrong item once the block/drink ends. |

## HumanPvP (`.hpvp`)

A deliberately slower, imperfect profile built to look like manual play. Shares the same Crystal/Anchor/defense
core as `GodmodePvP`, with these differences:

| Setting | Default | Description |
|---|---|---|
| `follow-range` | `20` | Smaller detection range than `GodmodePvP` by default. |
| `engage-distance` | `14` | Same sticky-engagement behavior as `GodmodePvP`, tuned to a shorter range. |
| `attack-range` | `3.4` | Same as `GodmodePvP`'s `attack-range`, tuned slightly shorter by default. |
| `smart-targeting` / `backup-range` | `true` / `10.0` | Same isolated-target preference and target-identity stickiness as `GodmodePvP`. |
| `free-look` | `false` | Same silent-rotation behavior as `GodmodePvP` — off by default for the same anti-cheat-detection reason. |
| `reaction-min` / `reaction-max` | `3` / `9` ticks | Randomized reaction delay before engaging a newly acquired target — no instant snap-to-target. |
| `attack-chance` | `0.9` | Probability that a "ready" hit is actually thrown, simulating human misclicks. |
| `aim-tolerance` | `4.0°` | Aim tolerance before a hit or placement is executed. |
| `max-turn-speed` | `18.0°/tick` | Maximum camera rotation per tick — human-paced turning instead of an instant snap. |
| `max-self-damage` | `6.0` | More conservative self-damage cap than `GodmodePvP`'s `12.0`. |
| `anti-rubberband` | `true` | Detects a server position correction and drops the stale path instead of fighting it. A moderate jump only counts outside of combat (normal explosion knockback shouldn't trigger it); a genuinely extreme jump triggers regardless, since real knockback rarely covers that much distance in one tick and rubberbanding is most common during actual Crystal/Anchor fights. |
| `pearl-min-dist` | `10.0` | Higher default than `GodmodePvP`'s `4.0` — still just a floor on top of `attack-range` (see `GodmodePvP`'s `pearl-gapclose` entry), a human plays more conservatively with pearls than the fully aggressive profile. |

All other Combat/Defense/Inventory/Pearl/Healing settings mirror `GodmodePvP` (same names, same purpose, same
defaults) unless listed above — including `use-beds` and the three `heal-*` settings. One mechanical difference:
placing a bed needs an exact 90°-aligned facing (for the head-part direction), so unlike every other action in
this module it uses one brief, precise rotation snap instead of the usual gradual human-paced turn, regardless
of `free-look`.

## TrainingDummy

| Setting | Default | Description |
|---|---|---|
| `health` | `20` | Dummy's HP — can be changed live while it's running. |
| `kb-strength` | `0.5` | Horizontal knockback strength. |
| `kb-up` | `0.4` | Vertical knockback (launch height). |
| `auto-respawn` | `true` | Respawns the dummy when it dies or disappears. |
| `invincible` | `false` | HP never reaches 0 — no despawn/respawn needed, uninterrupted practice. |

## AutoArmor

Automatically equips the strongest available armor piece per slot from your entire inventory. Scores candidates
by their real `Attributes.ARMOR`/`ARMOR_TOUGHNESS` attribute value (armor points weighted 10x over toughness as
a tiebreaker), not by guessing from material name - so an enchanted Diamond chestplate correctly beats an
unenchanted Netherite one if it actually protects more.

| Setting | Default | Description |
|---|---|---|
| `announce` | `true` | Chats when an armor piece gets upgraded. |

## Auto5b5tDupe

Ported from [mmvanheusden/meteor-5b5t-addon](https://github.com/mmvanheusden/meteor-5b5t-addon) (GPL-3.0), which
targeted MC 1.21.5/Yarn mappings - rewritten against 26.2/Mojang mappings here since the recipe-book API was
completely redesigned between those versions. Source was read in full before porting: no network calls, no
third-party auth, no telemetry - a pure crafting exploit (drop the held item, then send a craft-request packet
that references the ingredient in the gap before the server processes the drop).

**Requires an actual Crafting Table (`CraftingMenu`) open, not the player's own 2x2 inventory grid** - the race
condition lives in the table's 3x3 slot-shifting logic, which differs from the simpler 2x2 path and doesn't
trigger it there. The module opens a nearby table automatically, or places one from your inventory if none is
nearby, before attempting the exploit.

**Original last verified working 19/05/2025 - over a year old. Whether 5b5t has since patched this race condition
is unverified. Test with `single` on a worthless item before relying on it.**

| Setting | Default | Description |
|---|---|---|
| `recipe` | `Stick` | Which recipe to exploit (`Stick` or `CraftingTable`). Needs ingredients for at least 2 in inventory. |
| `single` | `false` | Just the raw exploit attempt (no rotation/drop automation, no auto-opening a table) - for testing whether the gap is even still open. Requires a Crafting Table to already be open. |
| `rotation-mode` | `Silent` | `Silent` sends a rotation packet without moving your camera; `Client` actually snaps your pitch down and back. |

## PacketLogger, BrandSpoof, ExploitGuard, PacketFilter, MacroTrigger

Small, standalone debug/OpSec modules - honestly scoped, no silent-fail claims.

| Module | Setting | Default | Description |
|---|---|---|---|
| PacketLogger | `log-receive` | `true` | Logs incoming packet class names to chat. Pure observation. |
| PacketLogger | `log-send` | `false` | Logs outgoing packet class names to chat. |
| PacketLogger | `filter` | (empty) | Only logs packet class names containing this text (case-insensitive). Empty = everything. |
| BrandSpoof | `spoofed-brand` | `vanilla` | Client brand reported to the server instead of `fabric` — the simplest automated modded-client detection. Doesn't defend against behavioral analysis. |
| ExploitGuard | `max-component-depth` | `200` | Cancels incoming chat packets whose text-component tree nests deeper than this — a known client-crash vector via malicious server broadcasts. Can't protect against crashes during packet decoding itself, only validly-decoded but pathological content. |
| PacketFilter | `blocked-outgoing` | (empty list) | Outgoing packets whose class name contains any of these texts (case-insensitive) are never sent to the server. |
| PacketFilter | `announce` | `true` | Chats when a packet gets blocked. |
| MacroTrigger | `triggers` | (empty list) | One entry per line, format `trigger=>command` - e.g. `gg=>.pvp off`. Runs the Meteor command whenever an incoming chat message contains the trigger text. Chat-only; no packet- or inventory-state triggers (would need dedicated infrastructure this project doesn't have yet). |
| MacroTrigger | `announce` | `true` | Chats when a macro fires. |


## Commands

| Command | Effect |
|---|---|
| `.pvp` / `.pvp toggle` | Toggle `GodmodePvP` |
| `.pvp on` / `.pvp off` | Explicitly enable/disable `GodmodePvP` |
| `.hpvp` / `.hpvp toggle` | Toggle `HumanPvP` |
| `.hpvp on` / `.hpvp off` | Explicitly enable/disable `HumanPvP` |
| `.nbt` | Dumps components/NBT of whatever's under your crosshair (entity or block), falls back to your held item if neither |
| `.nbt item` / `.nbt entity` / `.nbt block` | Same, explicitly targeted |
| `.proxy` / `.proxy list` | List configured proxies (uses Meteor's built-in proxy system) and which one is active |
| `.proxy add <name> <ip> <port> [socks4\|socks5]` | Add a proxy, defaults to socks5 |
| `.proxy switch <name>` | Switch the active proxy - takes effect on the *next* connection, not the current session |
| `.proxy remove <name>` | Remove a proxy |
| `.proxy check` | Health-check every configured proxy |

## Third-party tools this project relies on

| Tool | Role |
|---|---|
| [Meteor Client](https://meteorclient.com/) | Host client / addon API, provides `CrystalAura`, `KillAura`, `AutoMend`, `AutoEat`, `NoFall`, the Friends system, and the settings/GUI framework this addon builds on. |
| [Baritone](https://github.com/cabaletta/baritone) | Pathfinding and movement execution (`FollowProcess`, `CustomGoalProcess`, `PathingBehavior`) — this addon configures and drives it, it doesn't reimplement movement itself. |
| [Fabric Loader](https://fabricmc.net/) / [Fabric API](https://modrinth.com/mod/fabric-api) | Mod loading platform. |
