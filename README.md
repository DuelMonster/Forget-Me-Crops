# Forget-Me-Crops

> *Because your crops should fear your efficiency, not your attention span.*

Forget-Me-Crops is a farm automation mod for Fabric and NeoForge.
Set up one anchor (waterlogged chest + top-mounted item frame + hoe), plant around it, and the mod handles harvesting, replanting, drop storage, and basic farm repair automatically.

If you want implementation internals and developer docs, head to [TECHNICAL.md](TECHNICAL.md). This README is intentionally player-focused.

---
## Overview

Forget-Me-Crops turns a normal crop area into a self-running farm zone centered on an anchor.

In short: you build the farm once, the mod does the repetitive part forever, and your knees are saved from crouch-harvest duty.

![Example usage](https://cdn.modrinth.com/data/7YR2UVAs/images/700b4e3678ad20f59d2f38b6db2681b2f50da9fd.gif)

---
## Spotlight
<iframe src="https://www.youtube.com/embed/ztDyyf2CC9Y" title="This Mod Harvests Your Crops AUTOMATICALLY #minecraft #mcyt #farming #mod" frameborder="0" style="width: 240px; height: 430px" referrerpolicy="strict-origin-when-cross-origin" allowfullscreen></iframe>

---
## Features

- Harvests mature supported crops automatically
- Sends ripe drops and extra seeds to nearby chests/barrels first, then uses the anchor chest as overflow
- Replants using fresh drops first, then chest stock
- Retills nearby dirt/grass gaps back into farmland when possible
- Replants empty farmland and soul-sand tiles using nearby crop consensus
- Replaces broken (or missing) hoes from the anchor chest below the frame when available
- Rotates the frame during scanning so activity is visible

---
## How It Works

Each anchor runs on a timer (`tickInterval`, default 300 ticks / 15s):

1. The anchor is validated (frame still present, chest still valid, hoe available).
2. The mod discovers connected farm tiles around that anchor.
3. It scans in an outward spiral, spread across multiple ticks to avoid lag spikes.
4. During that same pass, it harvests ripe crops, replants, and repairs eligible gaps.
5. If all nearby output storage plus the anchor chest fill up, it pauses that anchor and retries after `chestFullCooldownTicks`.
6. When done, it returns to cooldown and waits for the next cycle.

Notes:

- Farms run only while their chunks are loaded.
- If a frame exists but is empty, the mod can pull a hoe from the anchor chest below the frame and resume automatically.
- If the frame or chest is removed, that anchor is unregistered cleanly.
- Nearby chests and barrels inside the farm radius are treated as harvest-output storage only.

---
## Create An Anchor (Quick Setup)

1. Place a chest at the same height as your crop layer.
2. Waterlog the chest (required for normal crop farms).
3. Attach an item frame to the top face of that chest.
4. Put any hoe in the frame.
5. Plant supported crops around that same Y-level.

Optional: place extra chests or barrels on frame Y or frame Y-1. Extra output discovery uses `scanRangeX + 1` and `scanRangeZ + 1`, so storage one block outside the crop scan footprint can still be used.

That is it. Your farm now has a tiny spinning foreman.

### Anchor Rules (Important)

- Scanning only happens on the same Y level as the item frame.
- Crops above or below that layer are ignored.
- Normal farmland crop anchors require a waterlogged chest.
- Nether Wart farms on soul sand can use a dry chest.
- Scan bounds come from `scanRangeX` and `scanRangeZ` (default 4 each direction).

### Common Setup Mistakes

- Frame placed on the side of the chest instead of the top.
- Chest not waterlogged for normal crops.
- Farm planted one block above or below the anchor layer.
- No valid hoe in the frame or chest.

---
## Supported Crops

| Crop          | Harvest condition   | Replant behavior                         |
| ------------- | ------------------- | ---------------------------------------- |
| Wheat         | Fully grown (age 7) | 1 wheat seed consumed                    |
| Carrots       | Fully grown (age 7) | 1 carrot consumed                        |
| Potatoes      | Fully grown (age 7) | 1 potato consumed                        |
| Beetroots     | Fully grown (age 3) | 1 beetroot seed consumed                 |
| Torchflower   | Fully grown         | Replanted (torchflower seed item)        |
| Sweet Berries | Stage 3 (age 3)     | Bush reset to age 1 (bush is not broken) |
| Nether Wart   | Fully grown (age 3) | 1 nether wart consumed                   |
| Melon         | Fruit block present | Fruit harvested, stem remains            |
| Pumpkin       | Fruit block present | Fruit harvested, stem remains            |

Special behavior:

- Sweet berry bushes are harvested in-place (age rolls back to 1).
- Melon and pumpkin stems are never broken; only the fruit is harvested.

---
## Installation

### Requirements

| Component | Version                |
| --------- | ---------------------- |
| Minecraft | 1.21.11, 26.1.2, 26.2  |
| Java      | 21 (1.21.x), 25 (26.x) |

### Fabric

| Minecraft Line | Fabric Loader | Fabric API      | Mod Menu      |
| -------------- | ------------- | --------------- | ------------- |
| 1.21.11        | 0.19.2        | 0.140.0+1.21.11 | 17.0.0        |
| 26.1.2         | 0.19.2        | 0.148.0+26.1.2  | 18.0.0-beta.1 |
| 26.2           | 0.19.3        | 0.152.1+26.2    | 20.0.0-beta.2 |

Drop the matching `Forget-Me-Crops_<version>+<minecraft>-fabric.jar` into your `mods/` folder with Fabric API.
Mod Menu is optional, but recommended for in-game config editing.

### NeoForge

| Minecraft Line | NeoForge       |
| -------------- | -------------- |
| 1.21.11        | 21.11.42       |
| 26.1.2         | 26.1.2.43-beta |
| 26.2           | 26.2.0.7-beta  |

Drop the matching `Forget-Me-Crops_<version>+<minecraft>-neoforge.jar` into your `mods/` folder.
Use the Mods list Configure button for the config UI.

Cloth Config is pinned per line for compatibility:

- `21.11.153` on 1.21.11
- `26.1.154` on 26.1.2
- `26.2.155` on 26.2

Both loaders use the same unified mixin config: `forgetmecrops.mixins.json`.

---
## Configuration

Config files are stored in your instance folder:

- `config/forgetmecrops-server.toml`
- `config/forgetmecrops-client.toml`

On Fabric, Mod Menu opens the same shared config screen.
On NeoForge, the Mods menu Configure button opens that same screen.

### Server Config

| Option                     | Default         | Config Screen Range | What it does                                                                  |
| -------------------------- | --------------- | ------------------- | ----------------------------------------------------------------------------- |
| `tickInterval`             | `300`           | `1` to `1200`       | How often an anchor runs, in ticks (`300` = 15 seconds at 20 TPS).            |
| `frameRediscoveryInterval` | `300`           | `15` to `1200`      | How often loaded chunks are rescanned to refresh anchor discovery.            |
| `scanRangeX`               | `4`             | `1` to `16`         | Radius along X from anchor (`4` means 4 blocks each direction, 9 wide total). |
| `scanRangeZ`               | `4`             | `1` to `16`         | Radius along Z from anchor (`4` means 4 blocks each direction, 9 wide total). |
| `durabilityMode`           | `normal`        | n/a                 | Hoe durability behavior.                                                      |
| `mendingProtection`        | `true`          | n/a                 | If true, hoes with Mending take no mod-caused durability loss.                |
| `chestFullCooldownTicks`   | `300`           | `10` to `300`       | Wait time before retrying when chest is full.                                 |
| `maxSpiralDurationTicks`   | `200`           | `10` to `400`       | Maximum ticks to spread one scan across (higher = less per-tick load).        |
| `rotationMode`             | `FULL_ROTATION` | n/a                 | Frame rotation behavior during scans.                                         |
| `debugLogging`             | `false`         | n/a                 | Enables verbose farm logging in your logs (troubleshooting use).              |
| `seedClutterMode`          | `reduced`       | n/a                 | Controls extra seed handling before chest insertion.                          |
| `seedReservePerType`       | `80`            | `0` to `1152`       | Minimum seeds kept per seed type when pulling from chest for replanting.      |

### Client Config

| Option             | Default | What it does                                                      |
| ------------------ | ------- | ----------------------------------------------------------------- |
| `harvestParticles` | `true`  | Toggles scan particles (spiral trail plus harvest burst effects). |

### Durability Mode

| Mode                | Behavior                                                        |
| ------------------- | --------------------------------------------------------------- |
| `normal`            | Vanilla-style loss; Unbreaking is respected.                    |
| `ignore_unbreaking` | Durability loss is applied while ignoring Unbreaking reduction. |
| `none`              | No durability loss from this mod's harvest/repair actions.      |

### Rotation Mode

| Mode              | Behavior                                                                        |
| ----------------- | ------------------------------------------------------------------------------- |
| `SINGLE_STEP`     | Moves 1 frame step when a cycle harvested at least one crop.                    |
| `FULL_ROTATION`   | Runs one full 8-step rotation across the scan cycle.                            |
| `FOLLOW_ROTATION` | Rotates repeatedly to track outward scan progression (ring-following behavior). |

### Seed Clutter Mode

Controls how extra seed drops are handled before insertion and how replanting pulls seeds.

| Mode      | Behavior                                                                                                                                                                                                           |
| --------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `none`    | Seed drops are discarded before insertion. Replant still uses one fresh drop seed first; if needed, it then pulls from the anchor chest (subject to reserve).                                                      |
| `normal`  | Replant consumes one fresh drop seed first. Remaining seed drops are inserted into nearby output storage first, then the anchor chest as overflow. Anchor chest pulls for replanting respect `seedReservePerType`. |
| `reduced` | Same as `normal`, then remaining seed drops from that harvest are halved (rounded down) before insertion.                                                                                                          |

In `reduced` mode, halving does not apply when the seed item is also the crop fruit (carrot, potato, nether wart, torchflower-type crops).

---
## Compatibility

### FastItemFrames

Forget-Me-Crops supports [FastItemFrames by Fuzss](https://modrinth.com/mod/fastitemframes).
When installed, frame lookup and rotation logic uses FastItemFrames integration paths for better large-farm performance.

### Other Mods

- Any chunk loader that keeps chunks loaded will keep anchors active.
- The mod uses normal world/block APIs (no custom block/item/entity registries), so registry conflicts are minimal.
- It generally coexists cleanly with growth-rate and crop-behavior mods, though balance may differ depending on your pack.

---
## FAQ

### Does it work in unloaded chunks?

No. Chunks must be loaded.

### Why is my farm not running?

Check this quick list:

- Frame is on top face of the chest
- Chest is waterlogged (unless this is a Nether Wart farm)
- Hoe exists in frame (or in chest for auto-replace)
- Crops are on the same Y level as the anchor
- Chest has free space
- Crop type is supported

### Can it handle mixed crops?

It can harvest supported crops in range, but replanting uses nearby crop consensus per tile. Cleaner single-crop zones give cleaner results.

---
## Technical Documentation

For implementation internals, package structure, scan algorithms, platform glue, and build details, see [TECHNICAL.md](TECHNICAL.md).

---
## License

MIT. See [LICENSE](LICENSE).

---
## Credits

Developed by **DuelMonster**.
Built with [Stonecutter](https://stonecutter.kikugie.dev/) + [Modstitch](https://github.com/isXander/modstitch) targeting Fabric and NeoForge.
Optional integration with [FastItemFrames](https://modrinth.com/mod/fastitemframes) by Fuzss.
