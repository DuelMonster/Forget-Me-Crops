# FastHarvester

FastHarvester automates crop harvesting for Fabric and NeoForge.

> Note: Forge support was removed from this project on 2026-04-22. FastHarvester now targets Fabric and NeoForge only.

Put a hoe in an item frame on top of a chest, grow crops around it, and the farm will harvest into that chest automatically.

If you want the implementation details, config reference, and technical behavior notes, see [TECHNICAL.md](TECHNICAL.md).

## Quick Setup

1. Place a chest at the same height as the farm you want to automate.
2. For normal crop farms, waterlog that chest.
3. Put an item frame on the top face of the chest.
4. Put any hoe in the item frame.
5. Grow supported crops around the chest on that same level.

That setup is your harvest anchor.

### Important placement rules

- The chest should sit on the same level as the crop blocks you want harvested.
- FastHarvester only scans and harvests crops on the same level as the item frame.
- It will not harvest crops above or below that layer.
- Normal crop farms require a waterlogged chest.
- Nether Wart farms are an exception and can use a normal chest.

## What It Does

FastHarvester checks farms every so often, harvests mature crops, puts the drops into the chest, and replants when it can.

It also repairs common farm problems automatically:

- Replants empty farmland from items stored in the chest
- Replants Nether Wart from chest stock on Soul Sand
- Retills dirt or grass back into farmland when a small gap appears
- Replaces a broken hoe from chest stock if another hoe is available
- Loads a spare hoe from the chest into an empty frame when possible
- Avoids guessing across ambiguous border tiles, which helps stop neighboring farms from bleeding into each other

Anchors run whenever their chunks are loaded. That means vanilla chunk loaders and modded chunk loaders can keep farms active even when no player is standing nearby.

## Supported Crops

- Wheat
- Carrots
- Potatoes
- Beetroots
- Torchflower
- Sweet Berries
- Nether Wart
- Melons
- Pumpkins

Sweet berry bushes are harvested without breaking the bush. Melons and pumpkins are harvested while leaving the stems in place so they can regrow normally.

## Configuration

Config files: `config/fastharvester-server.toml` and `config/fastharvester-client.toml`

On Fabric, if Mod Menu is installed, these settings can also be edited in-game from the mod's Configure button.
On NeoForge, the mod now uses NeoForge's native config system and exposes the same config files through the built-in Mods list Config button.

Here are the main settings you can change:

| Option                  | Default     | What it does                                                                                               |
|-------------------------|-------------|-------------------------------------------------------------------------------------------------------------|
| `tickInterval`          | `300`       | How often each anchor runs. `300` ticks is 15 seconds.                                                     |
| `frameRediscoveryInterval` | `100`    | How often loaded chunks are rescanned to refresh the recorded frame list.                                  |
| `scanRangeX`            | `4`         | How far the farm scans along the X axis from the anchor. A value of `4` covers 4 blocks in each X direction. |
| `scanRangeZ`            | `4`         | How far the farm scans along the Z axis from the anchor. A value of `4` covers 4 blocks in each Z direction. |
| `durabilityMode`        | `normal`    | Controls how much wear the hoe takes while harvesting and repairing the farm.                               |
| `mendingNegation`       | `true`      | If enabled, a hoe with Mending will not lose durability from this mod's actions.                            |
| `chestFullCooldownTicks`| `100`       | How long the anchor waits before trying again when the chest is full.                                       |
| `maxSpiralDurationTicks`| `100`       | Spreads farm work across multiple ticks to reduce lag spikes on larger farms.                               |
| `rotationMode`         | `follow_harvest_spiral`     | Controls how the frame rotates during a harvest cycle.                                      |
| `debugLogging`          | `false`     | Writes extra farm activity information to the log for troubleshooting.                                      |
| `seedClutterMode`       | `reduced`   | Controls how extra seeds are handled. See "Seed Clutter Mode" below for full behavior.                     |
| `seedReservePerType`    | `80`        | Minimum number of seeds to reserve per seed type in the chest; removals for replanting will not reduce the chest below this threshold. |

Client config:

| Option             | Default | What it does                                   |
|--------------------|---------|------------------------------------------------|
| `harvestParticles` | `true`  | Turns the harvest particle effects on or off.  |

### Durability Mode

| Mode                | Behavior                                                                    |
|---------------------|-----------------------------------------------------------------------------|
| `normal`            | Uses vanilla-style durability loss and respects Unbreaking.                 |
| `ignore_unbreaking` | Damages the hoe normally but ignores the Unbreaking enchantment.            |
| `none`              | Disables durability loss from harvesting and farm maintenance entirely.     |

### Rotation Mode

| Mode                        | Behavior                                                                 |
|-----------------------------|--------------------------------------------------------------------------|
| `step_per_harvest`          | Advances the frame by 1 step for each full farm harvest that gets at least one crop. |
| `full_rotation_per_harvest` | Rotates through all 8 frame steps once across the full farm harvest.     |
| `follow_harvest_spiral`     | Rotates through all 8 frame steps multiple times across the harvest, matching the number of rings processed around the frame. |

### Seed Clutter Mode

These modes control how excess seed items from harvested crops are handled before they are inserted into the chest and how replanting draws from drops or the chest.

- `none` — Remove supported seed drops before insertion. Replant still prefers seeds found in the freshly-harvested drops; if none are available the chest will be used (but chest removals are prevented when doing so would reduce the chest below `seedReservePerType`). Exception: when the crop's seed item is also the crop's fruit (for example torchflower-like crops, carrots, potatoes), those items are treated as crop-fruit and are allowed into the chest.
- `normal` — For each mature crop harvested the harvester will preferentially consume one seed from the freshly-harvested drops for replanting (if present). Any remaining seed drops are inserted into the chest unchanged. Chest removals for replant will not reduce a seed type below `seedReservePerType`.
- `reduced` — Same as `normal` (one seed consumed from drops for replant), then the remaining seed drops of that harvest are reduced by half (rounded down) before insertion into the chest. Do not halve when the seed item is the crop fruit.

Supported seed types for these policies include wheat seeds, beetroot seeds, melon/pumpkin seeds, carrot, potato, nether wart, and torchflower-like crops when present. Reserve enforcement is performed when removing seeds from the chest for replanting (the chest will refuse to provide seeds if doing so would drop the total at-or-below `seedReservePerType`).

## Compatibility

FastHarvester works with [FastItemFrames by Fuzss](https://modrinth.com/mod/fastitemframes). If that mod is installed, FastHarvester will use its faster frame lookup path automatically.
On Fabric, that FastItemFrames lookup path is implemented in a remap-safe way so packaged release jars can enumerate loaded FIF block entities correctly, not just dev runs.

On Fabric, FastHarvester exposes an in-game config screen through Mod Menu when Mod Menu is present.
On NeoForge, FastHarvester exposes NeoForge's built-in config screen from the Mods list.

Release metadata now embeds the configured mod version correctly, and Fabric debug logging now reports frame-discovery changes without repeating the same discovery line every tick.
The Fabric Mod Menu config screen now avoids the duplicate per-frame blur call that could crash heavily modded clients while rendering UI overlays.
The active Fabric runtime ticker now also restores the same empty-gap farmland maintenance as the shared scanner path, so retilling and replanting work again around established crop plots.
Newly loaded anchors now wait for the first configured `tickInterval` before doing any harvest, till, replant, or hoe-replacement work.
FastHarvester now records frames as chunks load, rescans loaded chunks on the configurable `frameRediscoveryInterval`, and marks recorded frames inactive when their chunk unloads so inactive anchors are skipped until they are seen again.

## Versions

- Minecraft 1.21.11
- Fabric Loader 0.18.6
- Fabric API 0.141.3+1.21.11
- NeoForge 21.11.42

## Building

```bash
./gradlew.bat clean build
```

Release jars are copied into `releases/`.

## License

MIT. See LICENSE.

## Developer & Debugging Notes

Recent internal fixes and refactors improved rotation animation correctness, logging, and harvest stability. For developers and server operators who need to diagnose behavior, the following notes are useful:

- Rotation and animation: the `FULL_ROTATION_PER_HARVEST` mode now uses a deterministic 8-step animation sequence computed from the frame's starting rotation. Per-frame animation state is guarded so scheduled rotations do not conflict with active animations.
- Rotation logs are now behind the debug flag and use a `[ROT]` tag; enable `debugLogging=true` in `fastharvester-server.toml` to see detailed rotation scheduling and flush events.
- Debounced per-tick rotation batching: `FrameRegistry` batches requested rotations in `PENDING_ROTATIONS` and applies them once per tick to avoid races and renderer hiccups.
- Logging: routine and per-block INFO logs were lowered to DEBUG behind `Constants.logDebug(...)`. Keep `debugLogging` off for normal servers to reduce log volume.
- Loot logic: block-drop calculation was hardened to honor Fortune and Silk Touch using server-side `LootContext` where available and safe reflective fallbacks when needed.
- Hoe handling: broken-hoe replacement and frame-hoeing sync were hardened so replacements are persisted and `FrameRegistry` is updated when a spare is loaded into a frame.
- Tickers: Fabric and NeoForge ticker implementations now clear the `FrameRegistry` on world/server unload to avoid stale references.

See [TECHNICAL.md](TECHNICAL.md) for implementation details and where to look in the codebase.
