# Forget-Me-Crops

> *Because nobody ever got rich standing next to a wheat field with a hoe.*

Forget-Me-Crops automates crop harvesting for Fabric and NeoForge. Put a hoe in an item frame on top of a chest, grow crops around it, and the farm handles itself — harvesting mature crops, stowing the drops in the chest, replanting from stock, and even fixing the occasional dirt gap you accidentally trampled or hoed into existence.

For the full implementation deep-dive — scan internals, package structure, build instructions, and everything else that starts with the word "BFS" — see [TECHNICAL.md](TECHNICAL.md).

---

## Overview

Forget-Me-Crops scans the crops around a designated item-frame anchor every few seconds, harvests anything ripe, and deposits the drops straight into the attached chest. No player interaction needed. The frame gently rotates as it works so you can tell at a glance that something is actually happening.

Farms stay active as long as their chunks are loaded, so vanilla chunk loaders and any modded loaders you already have work fine.

---

## Features

- Fully automatic harvesting into an attached chest
- Replants crops from chest stock after harvest
- Self-repairs: retills dirt/grass back into farmland, replants empty patches
- Replaces a broken hoe from chest stock automatically
- If an anchor frame is rebuilt empty, pulls a hoe from chest stock and resumes scanning immediately
- Detects when the frame or chest is removed and unregisters cleanly
- Single-pass spiral scan combines harvesting, replanting, and farm repair actions
- Incremental scanning spreads work across multiple ticks to keep lag spikes small
- BFS-based connected-farm discovery avoids cross-contaminating neighboring farms
- In-world feedback during scan work: planting/tilling/harvest sounds plus dust/harvest particles
- Fortune and Silk Touch enchantments are respected for drop calculations
- Shared Cloth Config screen implementation for both loaders
- Fabric Mod Menu config screen when Mod Menu is installed
- Works alongside [FastItemFrames by Fuzss](https://modrinth.com/mod/fastitemframes)

---

## How It Works

1. Every `tickInterval` ticks the mod checks each registered anchor.
2. If a hoe is present and the chest has space, a `FarmScanTask` is created.
3. The task discovers the farm area with a BFS pass, then sweeps it in an outward spiral.
4. Mature crops are harvested and drops inserted into the chest; the seed/replant item is consumed from the drops (or chest) and the crop is immediately replanted.
5. The same spiral pass also repairs farm gaps in-line: empty farmland/soul-sand tiles are replanted and connected dirt/grass patches (air above, within scan bounds) are retilled and replanted.
6. The item frame rotates as work progresses so you can see which ring is currently being processed.
7. Scan work produces local feedback (tilling/plant/harvest sounds, spiral dust, and harvest dust bursts) so activity is visible during the pass.
8. When the task finishes (or the chest fills), it cleans up and the anchor goes back on its cooldown timer.
9. If a frame is restored empty and a hoe is available in the chest, the registry marks the anchor ready immediately instead of waiting a full `tickInterval`.

---

## Supported Crops

| Crop            | Harvest condition            | Replant behaviour                            |
|-----------------|------------------------------|----------------------------------------------|
| Wheat           | Fully grown (age 7)          | One wheat seed consumed from drops           |
| Carrots         | Fully grown (age 7)          | One carrot consumed from drops               |
| Potatoes        | Fully grown (age 7)          | One potato consumed from drops               |
| Beetroots       | Fully grown (age 3)          | One beetroot seed consumed from drops        |
| Torchflower     | Fully grown                  | Replanted; seed not consumed from drops      |
| Sweet Berries   | Stage 3 (age 3)              | Bush reset to stage 1; not broken            |
| Nether Wart     | Fully grown (age 3)          | One nether wart consumed from drops          |
| Melon           | Fruit block present          | Stem left in place; regrows naturally        |
| Pumpkin         | Fruit block present          | Stem left in place; regrows naturally        |

Sweet berry bushes are harvested without destroying the bush — the age is simply rolled back to 1. Melon and pumpkin fruits are broken and the drops collected; the stems are never touched so they regrow on their own timeline.

---

## Installation

### Requirements

| Component   | Version  |
|-------------|----------|
| Minecraft   | 1.21.11  |
| Java        | 21       |

### Fabric

| Component   | Version              |
|-------------|----------------------|
| Fabric Loader | 0.19.2             |
| Fabric API  | 0.139.5+1.21.11      |

Drop `Forget-Me-Crops-1.21.11-<version>-Fabric.jar` into your `mods/` folder alongside Fabric API.
Mod Menu is optional but recommended — it gives you an in-game config screen.

### NeoForge

| Component  | Version  |
|------------|----------|
| NeoForge   | 21.11.42 |

Drop `Forget-Me-Crops-1.21.11-<version>-NeoForge.jar` into your `mods/` folder.
The built-in NeoForge config screen is available from the Mods list in the main menu.

### Quick Setup

1. Place a chest at the same height as the crop layer you want automated.
2. Waterlog that chest (required for normal crop farms; Nether Wart farms may use a dry chest).
3. Attach an item frame to the **top face** of the chest.
4. Put any hoe into the item frame.
5. Grow supported crops around the chest on that same Y level.

**Important placement rules:**

- Forget-Me-Crops only scans on the same Y level as the item frame. Crops above or below that layer are ignored.
- Normal crop anchors need a waterlogged chest. Nether Wart farms on soul sand do not.
- The scan range is controlled by `scanRangeX` and `scanRangeZ` in the config (default: 4 blocks in each direction).
- Anchor validation runs at scan time — if the frame or chest disappears the anchor is automatically unregistered.

---

## Configuration

Config files are written to `config/forgetmecrops-server.toml` and `config/forgetmecrops-client.toml` in your instance directory.

On **Fabric**, if Mod Menu is installed, these can also be edited in-game via the mod's Configure button.
On **NeoForge**, the Mods list Configure button opens the same shared Cloth Config screen.

Both loaders delegate to the same `ConfigScreen` builder in `common`, with custom entry widgets for every option so tooltips only appear when the mouse is over the option label lane (not over the value field or reset button).
Enum-mode tooltips in that screen use localized player-friendly labels from the language file (for example `Single Step` instead of `SINGLE_STEP`).

Implementation note: in Cloth Config `21.11.153`, the low-level `BooleanListEntry`, `IntegerListEntry`, and `EnumListEntry` constructors used by custom subclasses are marked deprecated upstream. Forget-Me-Crops keeps these subclasses for label-only tooltip behavior and wraps callbacks with null-safe adapters to satisfy strict null-analysis diagnostics.

### Server Config

| Option                     | Default                     | What it does                                                                                    |
|----------------------------|-----------------------------|-------------------------------------------------------------------------------------------------|
| `tickInterval`             | `300`                       | How often each anchor runs, in ticks. `300` is 15 seconds at 20 TPS.                           |
| `frameRediscoveryInterval` | `600`                       | How often loaded chunks are rescanned to refresh the frame registry, in ticks.                  |
| `scanRangeX`               | `4`                         | Scan radius along X from the anchor. `4` covers 4 blocks in each direction (9 blocks wide).    |
| `scanRangeZ`               | `4`                         | Scan radius along Z from the anchor. `4` covers 4 blocks in each direction (9 blocks wide).    |
| `durabilityMode`           | `normal`                    | Controls hoe wear during harvesting and repairs. See table below.                               |
| `mendingNegation`          | `true`                      | When `true`, a hoe with Mending takes no durability loss from this mod's actions.               |
| `chestFullCooldownTicks`   | `300`                       | How many ticks to wait before retrying when the attached chest is full.                         |
| `maxSpiralDurationTicks`   | `200`                       | Maximum ticks to spread one scan cycle across. Higher values reduce per-tick load.              |
| `rotationMode`             | `FULL_ROTATION`             | How the item frame rotates during a harvest cycle. See table below.                             |
| `debugLogging`             | `false`                     | Writes detailed farm activity to the log. Useful for troubleshooting; leave off normally.       |
| `seedClutterMode`          | `reduced`                   | Controls how excess seed drops are handled before chest insertion. See section below.           |
| `seedReservePerType`       | `80`                        | Minimum seeds to keep per type in the chest. Replanting will not pull seeds below this amount.  |

### Client Config

| Option             | Default | What it does                                          |
|--------------------|---------|-------------------------------------------------------|
| `harvestParticles` | `true`  | Toggles scan visual particles (spiral dust + harvest burst particles). |

### Durability Mode

| Mode                | Behaviour                                                               |
|---------------------|-------------------------------------------------------------------------|
| `normal`            | Vanilla-style durability loss; Unbreaking enchantment is respected.     |
| `ignore_unbreaking` | Normal durability loss but the Unbreaking enchantment is ignored.       |
| `none`              | No durability loss from harvesting or farm maintenance whatsoever.      |

### Rotation Mode

| Mode                        | Behaviour                                                                                        |
|-----------------------------|--------------------------------------------------------------------------------------------------|
| `SINGLE_STEP`               | Advances the frame by one step for each harvest cycle that collects at least one crop.           |
| `FULL_ROTATION`             | Animates through all 8 frame positions once across the full harvest pass.                        |
| `FOLLOW_ROTATION`           | Rotates through all 8 positions multiple times, roughly tracking the outward ring being scanned. |

### Seed Clutter Mode

Controls how extra seed drops are filtered before being put into the chest, and how the replant logic draws seeds.

| Mode      | Behaviour                                                                                                                                                     |
|-----------|---------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `none`    | Seed drops are discarded before chest insertion. Replanting still pulls one seed from the fresh drops first; if none, the chest is used (subject to reserve). |
| `normal`  | One seed from the fresh drops is consumed for replanting. Remaining seed drops go into the chest unchanged. Chest removals respect `seedReservePerType`.      |
| `reduced` | Same as `normal` (one drop seed consumed for replant), then the remaining seed drops of that harvest are halved (rounded down) before chest insertion.         |

The halving in `reduced` mode does **not** apply when the seed item is also the crop fruit (carrots, potatoes, nether wart, torchflower-type crops). The `seedReservePerType` threshold is enforced whenever the mod removes seeds from the chest for replanting — it will refuse to pull seeds if doing so would drop any type below the reserve.

---

## Compatibility

### FastItemFrames

Forget-Me-Crops works with [FastItemFrames by Fuzss](https://modrinth.com/mod/fastitemframes). When that mod is installed, Forget-Me-Crops automatically uses its block-entity based frame lookup instead of the vanilla entity query, which is faster on large farms. The Fabric build uses accessor mixins for chunk access so the remapped production jar works correctly — not just in dev runs.

### Other Mods

- Any vanilla-compatible chunk loader keeps anchors ticking while no player is nearby.
- Forget-Me-Crops does not modify any block behaviour directly; it reads and sets blocks through the normal level API, so it should coexist cleanly with crop growth mods.
- The mod does not register custom blocks, items, or entities — there is nothing to conflict with at registry time.

---

## Technical Documentation

For implementation internals, package structure, scan algorithm details, rotation system internals, loot handling, and build instructions, see **[TECHNICAL.md](TECHNICAL.md)**.

---

## License

MIT. See [LICENSE](LICENSE).

---

## Credits

Developed by **Jared**.
Built with [MultiLoader Template](https://github.com/jaredlll08/MultiLoader-Template) targeting Fabric and NeoForge.
Optional integration with [FastItemFrames](https://modrinth.com/mod/fastitemframes) by Fuzss.