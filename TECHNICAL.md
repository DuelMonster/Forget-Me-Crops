# Technical Reference

This file is the technical companion to README.md. It documents implementation details, scanning rules, configuration behavior, and development workflow.

> Note: Forge support was removed from this project on 2026-04-22. The documentation and codebase now target Fabric and NeoForge only.

## Anchor Rules

A harvest anchor is:

1. A chest
2. An item frame attached to the top face of that chest
3. A hoe in the item frame

Chest rules:

- Normal crop anchors require the chest below the frame to be waterlogged.
- Nether Wart / Soul Sand anchors may use a normal chest.
- Crop scanning happens only on the same Y level as the frame anchor.
- Crops above or below that layer are ignored.

## Activation Range

Anchors run whenever their chunks are loaded.

Behavior:

- Vanilla item-frame scanning sweeps the currently loaded item-frame entities in the level.
- FastItemFrames scanning iterates the currently loaded chunk holders and inspects their block entities.
- This allows vanilla chunk loaders and modded chunk loaders to keep anchors active without depending on nearby players, simulation distance, or view distance.

Relevant code:

- common/src/main/java/com/fastharvester/FrameScanner.java
- common/src/main/java/com/fastharvester/FastItemFrameAdapter.java

## Scan Model

FastHarvester builds a connected farm area from the frame position using BFS (breadth-first search).

Traversal rules:

- Standard crop farms expand through crop tiles and only through empty farmland when nearby crops give a clear local consensus.
- Soul Sand / Nether Wart positions are treated as farm tiles.
- Melon and pumpkin farms can traverse empty fruit lanes when those air blocks are clearly part of a real melon/pumpkin layout.
- Arbitrary air gaps and ambiguous border tiles are not treated as bridges.

Effect:

- Adjacent farms with a real separator line no longer leak harvests into each other.
- Irregular farm shapes are supported more naturally than a fixed 9x9 square.
- Frame rotation can now advance during the active spiral pass so harvest progress is more visible while the farm is working.

## Farm Maintenance

### Chest-based replanting

When an empty tile is found:

- Empty farmland can be replanted from chest stock.
- Empty Soul Sand can be replanted with Nether Wart from chest stock.

Mixed crop decision logic:

- For empty farmland, neighboring crop blocks are sampled.
- The dominant neighboring crop type is chosen.
- The required seed/item must exist in the chest.
- Supported replant-by-neighbor logic currently covers Wheat, Carrots, Potatoes, Beetroots, and Torchflower.

### Auto-till repair

If an air gap is found and:

- the block below is Dirt or Grass Block
- the position is surrounded on the four cardinal sides by farmland
- the gap is not part of a melon/pumpkin farm layout

then the block is converted back to Farmland.

Durability:

- Auto-till uses the same durability rules as harvesting.
- It respects `durabilityMode`, Unbreaking, and `mendingNegation`.
- When a hoe breaks, it is removed from the frame, break sound and item particles are played at the frame position, and the chest is checked for a replacement hoe.
- If a frame is empty and the chest already contains a spare hoe, the scanner can equip that hoe automatically before the next harvest cycle begins.

## Crop Handling

| Crop          | Harvest rule                 | Replant rule                               |
|---------------|------------------------------|--------------------------------------------|
| Wheat         | Harvest when fully grown     | Consume one wheat seed from drops          |
| Carrots       | Harvest when fully grown     | Consume one carrot from drops              |
| Potatoes      | Harvest when fully grown     | Consume one potato from drops              |
| Beetroots     | Harvest when fully grown     | Consume one beetroot seed from drops       |
| Torchflower   | Harvest when fully grown     | Replant without consuming a harvested drop |
| Sweet Berries | Harvest at age 3             | Reset bush to age 1                        |
| Nether Wart   | Harvest at age 3             | Consume one nether wart from drops         |
| Melon         | Harvest when fruit exists    | No direct replant; stem regrows fruit      |
| Pumpkin       | Harvest when fruit exists    | No direct replant; stem regrows fruit      |

## Enchantments

- Fortune: applied to crop drops
- Silk Touch: affects melons, causing melon block drops instead of slices
- Unbreaking: respected in `normal` durability mode
- Mending: can fully suppress durability loss when `mendingNegation=true`

## Performance Limits

Current hard limits in `FrameScanner`:

- Max frames processed per run: 24
- Max blocks processed per run: 3072

Work can be spread across multiple ticks using `maxSpiralDurationTicks`.

## Rotation & Animation Internals

FastHarvester's frame rotation system was recently refactored to avoid races and produce deterministic animations. Key implementation notes for contributors and debuggers:

- Per-tick rotation batching: rotation requests are enqueued per-dimension in `FrameRegistry.PENDING_ROTATIONS` and flushed once per server tick by `FrameRegistry.tickAndCollectReady(...)`. This reduces conflicting concurrent rotation attempts and concentrates world updates into a single flush pass.
- `FrameRegistry.scheduleRotation(...)` records a tentative `lastRotationGameTime` and places the requested rotation in the pending map. `tryRotation(...)` simply records the attempt time.
- `FrameEntry.animating`: each frame entry has an `animating` boolean set while a full multi-step animation is running. When `animating==true`, `scheduleRotation(...)` will skip adding new rotation requests and the tick flush will skip applying pending rotations for that frame.
- `FrameRegistry.setAnimating(...)` marks a frame animating/unanimating and proactively removes any pending rotation for that frame when animation starts so previously queued rotations do not interfere.
- Deterministic full-rotation animation: `FrameScanner.FarmScanTask` computes a deterministic 8-step `fullAnimationSequence` derived from the current frame rotation (start+1..start+8 mod 8). The task marks the frame animating at animation start and unmarks it on completion. This eliminates the previously observed bouncing and out-of-order stepping.
- Synchronous bypass removed: earlier forced synchronous rotation application caused renderer-freeze issues. The implementation now prefers scheduled per-tick application and relies on the `animating` guard to avoid visual races.

## FarmScanTask: spiral + neighbor pass behavior

- The `FarmScanTask` executes a spiral pass over farm tiles and performs an additional neighbor/repair pass exactly once per task to handle auto-planting and tilling. This ensures repair actions (auto-plant/till) run during the same logical scan and do not repeat unnecessarily across ticks.
- Auto-plant and auto-till logic are executed during the spiral and neighbor pass so replanting decisions can consider local context and avoid removing valid seeds or overwriting neighboring farms.

- Pre-scan maturity check: `FarmScanTask` now performs a quick maturity pre-check across the spiral candidate positions before beginning the harvest spiral. If no mature crops or harvestable fruit are present the task exits early for that cycle and no frame rotation/animation or neighbor repairs are scheduled. This reduces unnecessary world updates and frame rotations on idle farms.

## Loot & Enchantment Handling

- Block drop calculation now prefers server-side `LootContext` builders so `Fortune` and `Silk Touch` enchantments are respected for drop determination where available. When running in environments that require compatibility fallbacks, the code uses guarded reflection paths that safely degrade to conservative drop estimates.
- Enchantment lookups and handler logic were moved into safer, loader-adaptable helpers under `util/loot` and `util/hoe` to centralize platform differences and reduce reflective fragility.

## Hoe replacement and chest interactions

- Broken-hoe handling now persists replacements by updating `FrameRegistry` and calling into platform adapters to sync the frame-held item. `HarvestUtils.handleBrokenHoe(...)` attempts to load a replacement hoe from the chest, updates the registry via `FrameRegistry.updateHoe(...)`, and calls `Services.PLATFORM.updateFrameItem(...)` where supported.
- Chest insert/remove debug messages were moved behind `Constants.logDebug(...)` to avoid noisy per-item INFO messages on busy farms.

## Tickers & Lifecycle

- Fabric and NeoForge tickers were updated to call `FrameRegistry.clearAll()` when the server/world unloads so memory is freed and stale references are not kept across restarts.

See code references in the following files for the canonical implementation:

- `common/src/main/java/com/fastharvester/frame/FrameRegistry.java`
- `common/src/main/java/com/fastharvester/frame/FrameScanner.java`
- `common/src/main/java/com/fastharvester/util/loot/LootLogic.java`
- `common/src/main/java/com/fastharvester/HarvestUtils.java`


## Configuration Reference

Runtime config file:

- `config/fastharvester-server.toml`
- `config/fastharvester-client.toml`

Fabric integration:

- When Mod Menu is installed, FastHarvester provides a Fabric-side config screen for editing these values in game.
- The screen writes back to the same TOML files used by the normal file-based config flow.
- Fabric's FastItemFrames block-entity scan now uses accessor mixins for chunk access instead of reflective field-name lookups, so the packaged remapped jar still finds loaded FIF chunks in production.
- Fabric release metadata now gets its version from the shared Gradle `mod_version` property instead of leaving `${version}` as `unspecified`.
- The Fabric config screen now relies on the base `Screen.render` background pass and does not call `renderBackground` separately, which avoids the `Can only blur once per frame` crash on modded clients with extra screen hooks.
- The active Fabric ticker now mirrors the shared farm-maintenance branch for empty farmland, Soul Sand, and dirt/grass gap repair instead of only harvesting mature crop blocks.

NeoForge integration:

- NeoForge now registers native `ModConfigSpec` client and server configs using the same `fastharvester-client.toml` and `fastharvester-server.toml` file names.
- NeoForge syncs those native config values back into the shared `Config` fields that the common gameplay code reads.
- NeoForge exposes its built-in config screen from the Mods list instead of using a custom screen.

| Option                   | Default   | Description                                         |
|--------------------------|-----------|-----------------------------------------------------|
| `tickInterval`           | `300`     | Time between anchor runs in ticks                   |
| `frameRediscoveryInterval` | `100`   | Time between loaded-chunk rediscovery passes for the recorded frame registry |
| `scanRangeX`             | `4`       | Max scan radius along the X axis from the frame; value 4 covers 4 blocks in each X direction |
| `scanRangeZ`             | `4`       | Max scan radius along the Z axis from the frame; value 4 covers 4 blocks in each Z direction |
| `durabilityMode`         | `normal`  | Hoe durability behavior                             |
| `mendingNegation`        | `true`    | If true, Mending prevents durability loss entirely  |
| `debugLogging`           | `false`   | Enables verbose server log output                   |
| `chestFullCooldownTicks` | `100`     | Delay before retrying a full chest                  |
| `maxSpiralDurationTicks` | `100`     | Max number of ticks to spread one scan cycle across |
| `rotationMode`          | `full_rotation_per_harvest`     | Controls how frame rotation progresses during a harvest cycle |
| `seedClutterMode`        | `reduced` | Controls extra seed-drop filtering                  |
| `seedReservePerType`     | `80`      | Reserve kept per supported seed type in `reduced` mode |

Client-only config:

| Option             | Default | Description                       |
|--------------------|---------|-----------------------------------|
| `harvestParticles` | `true`  | Enables colored harvest particles |

### `durabilityMode`

| Mode                | Behavior                                               |
|---------------------|--------------------------------------------------------|
| `normal`            | Vanilla-like durability loss with Unbreaking respected |
| `ignore_unbreaking` | Durability loss without Unbreaking reduction           |
| `none`              | No durability loss                                     |

### `rotationMode`

| Mode                        | Behavior                                                         |
|-----------------------------|------------------------------------------------------------------|
| `step_per_harvest`          | Advances the frame by 1 step for each completed harvest cycle with at least one crop |
| `full_rotation_per_harvest` | Rotates through all 8 frame steps once across the whole harvest pass |
| `follow_harvest_spiral`     | Rotates through all 8 frame steps multiple times across the harvest pass, matching the number of rings processed around the frame |

### `seedClutterMode`

| Mode      | Behavior                                            |
|-----------|-----------------------------------------------------|
| `normal`  | Keep all supported seed drops                       |
| `reduced` | Keep only up to `seedReservePerType` per supported seed type |
| `none`    | Discard supported seed drops entirely               |

Current scope:

- Included: Wheat Seeds, Beetroot Seeds
- Excluded: Torchflower Seeds, Nether Wart, Sweet Berries

Config implementation notes:

- `durabilityMode`, `rotationMode`, and `seedClutterMode` are backed by dedicated enums in separate files
- The runtime config is split into separate server and client TOML files
- Enum-backed config values are still stored as lowercase strings for readability and stability
- Fabric-side Mod Menu support is implemented with a custom config screen because FastHarvester does not use PuzzlesLib's generated config UI
- Fabric FastItemFrames chunk enumeration avoids string-based reflection on Minecraft internals because those names do not survive remapping in release jars
- Fabric debug logging now emits loaded-frame discovery only when counts change or on periodic summaries, and slow-run warnings are rate-limited unless a tick is significantly over budget
- Both the common and Fabric scanners now defer a newly discovered anchor's first real work cycle until `tickInterval` has elapsed, using a per-frame tick countdown so world load cannot immediately trigger harvest, tilling, replanting, or hoe replacement without starving the anchor indefinitely
- Frames are now recorded per chunk as they are discovered; chunk unloads mark those recorded frames inactive instead of pruning them, and inactive entries are ignored by harvest execution until chunk load or a rediscovery pass marks them active again
- NeoForge uses its native `ModConfigSpec` registration and built-in config screen while continuing to feed the shared common `Config` state

## Compatibility

FastItemFrames integration:

- Detected reflectively at runtime
- Uses block-entity iteration instead of vanilla entity queries
- Paces configurable frame rotation so one scan step produces at most one visual frame-rotation advance
- Uses client/listener-only block update notifications for FastItemFrames item and rotation sync to avoid unnecessary neighbor-update churn
- Falls back safely when the mod is not installed

## Build Notes

Useful commands:

```bash
./gradlew.bat clean build
./gradlew.bat :common:compileJava :fabric:compileJava :neoforge:compileJava
./gradlew.bat :common:test
./gradlew.bat :fabric:runClient
./gradlew.bat :neoforge:runClient
```

Release outputs:

- `releases/FastHarvester-1.21.11-0.10.0-Fabric.jar`
- `releases/FastHarvester-1.21.11-0.10.0-NeoForge.jar`
