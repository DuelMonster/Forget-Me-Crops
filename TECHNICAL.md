# Technical Reference — Forget-Me-Crops

This document covers the full implementation of Forget-Me-Crops: architecture, scanning algorithms, rotation internals, hoe handling, loot logic, platform differences, configuration, build instructions, and credits.

For the user-facing setup guide and config reference see [README.md](README.md).

---

## Architecture Overview

Forget-Me-Crops is structured as a Gradle multi-module project:

| Module    | Role                                                                         |
|-----------|------------------------------------------------------------------------------|
| `common`  | All game logic — scanning, harvesting, registry, config, helpers             |
| `fabric`  | Fabric glue — ticker, event hooks, Mod Menu integration, FIF mixin            |
| `neoforge`| NeoForge glue — ticker, event hooks, config-screen SPI registration           |

All gameplay decisions live in `common`. The platform modules only wire up tickers and platform-specific config APIs.

### Package Structure

| Package                              | Contents                                                                          |
|--------------------------------------|-----------------------------------------------------------------------------------|
| `com.forgetmecrops.frame`            | `FrameScanner`, `FrameRegistry`, `FrameDiscovery`, `FarmScanTask`, `SpiralStep`, `CatchupManager` |
| `com.forgetmecrops.harvest`          | `HarvestUtils`, `HarvestContext`, `CropRegistry`                                  |
| `com.forgetmecrops.config`           | `Config`, `ConfigDescriptors`                                                     |
| `com.forgetmecrops.client.config`    | Shared config UI classes: `ConfigScreen`, tooltip builders, label-hitbox entries |
| `com.forgetmecrops.enums`            | `DurabilityMode`, `RotationMode`, `SeedClutterMode`                               |
| `com.forgetmecrops.util.chest`       | `ChestUtils` — insert/remove helpers with reserve enforcement                     |
| `com.forgetmecrops.util.durability`  | `DurabilityLogic` — enchantment-aware hoe damage                                  |
| `com.forgetmecrops.util.hoe`         | `FrameHoeReplacement` — broken-hoe replacement and frame sync                     |
| `com.forgetmecrops.util.loot`        | `LootLogic` — Fortune/Silk Touch aware drop calculation                           |
| `com.forgetmecrops.util.log`         | `LogUtils` — gated debug/trace logging                                            |
| `com.forgetmecrops.platform`         | `Services`, platform adapter interfaces                                           |
| `com.forgetmecrops.platform.adapter` | `FIF`, `FastItemFrameAdapterImpl` — FastItemFrames integration                    |
| `com.forgetmecrops.mixin`            | Fabric-side accessor mixins for chunk enumeration                                 |

---

## Anchor Rules

A harvest anchor is the combination of:

1. A chest (or any `Container` block entity)
2. An item frame attached to the **top face** of that chest (or at the same position as the chest top)
3. A hoe held in the item frame

Chest requirements:

- Normal crop anchors (wheat, carrots, etc.) require the chest to be **waterlogged**.
- Nether Wart farms on soul sand may use a **non-waterlogged** chest.
- Scanning only covers the **same Y level** as the item frame. Crops above or below that layer are ignored entirely.

The anchor is validated at scan time. If the item frame entity or FIF block entity is gone, or if the chest block entity has been replaced, the anchor is immediately unregistered via `FrameRegistry.unregisterFrame(...)` and the scan is aborted.

---

## Frame Discovery and Registry

### Discovery

`FrameDiscovery` centralises anchor detection for both vanilla item frames and FastItemFrames (FIF) block entities. Discovery runs in two modes:

- **Vanilla:** iterates `ItemFrame` entities in the loaded level, validates `direction == UP`, checks for an attached `Container` below, and registers valid anchors.
- **FIF:** iterates loaded chunk block entities, identifies FIF block entities, performs the same container check.

`CatchupManager` queues discovered candidate positions and drains them gradually across ticks so initial discovery work is spread rather than spiking on world load.

### Registry

`FrameRegistry` is the authoritative in-memory store for all discovered anchors, keyed by dimension ID and block position.

Key operations:

| Method                              | Purpose                                                                          |
|-------------------------------------|----------------------------------------------------------------------------------|
| `registerFrame(...)`                | Add or refresh an anchor entry                                                   |
| `unregisterFrame(...)`              | Remove an anchor (called on frame/chest removal or validation failure)           |
| `markChunkInactive(dimId, chunkKey)`| Mark all anchors in a chunk inactive when the chunk unloads                      |
| `markChunkActive(dimId, chunkKey)`  | Re-activate anchors when a chunk reloads                                         |
| `tickAndCollectReady(dimId, level)` | Decrement per-anchor countdown timers; return anchors due for scanning           |
| `scheduleRotation(...)`             | Queue a rotation request for the pending-rotation map                            |
| `tryRotation(...)`                  | Record that a rotation was attempted (cooldown gate)                             |
| `setAnimating(dimId, pos, flag)`    | Mark/unmark a frame as currently animating; clears pending rotations when set    |
| `updateHoe(dimId, pos, hoe)`        | Update the stored hoe in an existing registry entry                              |
| `clearAll()`                        | Purge all entries (called on server/world unload)                                |

#### Chunk-based lifecycle

Anchors are indexed by chunk key in `CHUNK_INDEX`. When a chunk unloads, all its anchors are marked `inactive` — they remain in the registry but are skipped by `tickAndCollectReady`. When the chunk reloads or a rediscovery pass runs, they are re-activated. This avoids repeated register/unregister churn on chunks that load and unload frequently.

Newly registered anchors start with `ticksUntilNextRun = tickInterval` so they wait a full cycle before doing any work. This prevents harvesting, tilling, or hoe replacement from firing immediately on world load.

`updateHoe(dimId, pos, hoe)` includes a recovery fast-path: when an existing anchor transitions from no hoe to a valid hoe (for example after chest-driven replacement into a rebuilt empty frame), `ticksUntilNextRun` is set to `0` so scanning resumes immediately.

#### Rotation batching

Rotation requests are not applied immediately. They are enqueued in `PENDING_ROTATIONS` (a per-dimension map) and applied in a single flush pass during `tickAndCollectReady`. This concentrates world mutation into one point per tick and avoids conflicting concurrent rotation writes.

When `animating == true` for a frame entry, `scheduleRotation` skips adding new requests for that frame and the flush pass skips applying any pending rotation for it, so in-progress animations are never interrupted.

---

## Scan Model

### BFS Farm Discovery

`FrameScanner.bfsDiscoverFarm(center, level, rangeX, rangeZ)` builds a connected set of farm tiles using breadth-first search from the anchor position. Traversal rules:

- A position is a valid farm tile if `isFarmPosition(level, pos)` returns true.
- `isFarmPosition` accepts: crop blocks, melon/pumpkin stems, melon/pumpkin fruit, farmland (with or without a crop above), soul sand (with or without nether wart above), and air-over-dirt/grass repair tiles.
- Traversal stops at the configured range limits `[center ± rangeX, center ± rangeZ]`.
- Connectivity is constrained by BFS reachability from the anchor and by configured range limits, so repair traversal remains local to the connected farm area.

If BFS yields an empty result (e.g. brand-new empty farm), the scanner falls back to a full rectangular grid over `[center ± rangeX, center ± rangeZ]` so the repair/planting pass still runs.

### Spiral Sweep

`FrameScanner.generateSpiral(center, rangeX, rangeZ)` generates an outward-expanding spiral of `SpiralStep` records, each holding a `BlockPos` and approach `Direction`. The `FarmScanTask` constructor filters this against the BFS candidate set so only valid farm positions remain.

The spiral is used in preference to a simple row/column sweep because it naturally matches the outward-ring structure used by the rotation animation — each ring of the farm corresponds to one rotation step.

### Incremental Execution (FarmScanTask)

`FarmScanTask` is a package-private top-level class in `com.forgetmecrops.frame`. One task is created per anchor per scan cycle and stored in `FrameScanner.activeScans`. Each server tick `FrameScanner.tickScans(dimId, level)` calls `task.tick()` on every active task.

Per-tick, the task processes `positionsPerTick` spiral positions. `positionsPerTick` is computed at construction time as `ceil(totalPositions / maxSpiralDurationTicks)`. The task self-removes when `currentIndex >= totalPositions` or when `ctx.chestFull` is set.

The current implementation is a **single-pass** model: harvest checks, replanting, and till/repair logic all run inside the main spiral iteration. There is no constructor-time maturity pre-scan and no separate end-of-task neighbour repair sweep.

#### Anchor re-validation per tick

At the start of every `tick()` call the task re-validates the anchor via `FrameScanner.isFrameStillPresent(level, center)` and `FrameScanner.isChestStillValid(level, anchor)`. It also re-reads the hoe from the frame to catch mid-scan hoe removal. If any of these checks fail the task exits early and the anchor is unregistered.

---

## Farm Maintenance

### Replanting

When an empty tile is encountered during the spiral or neighbour pass:

1. The four cardinal neighbours are examined for crop blocks.
2. Each neighbour's block is mapped to its canonical form via `CropRegistry.canonicalCropBlock(b)` (e.g. an attached melon stem maps to `MELON_STEM`).
3. A count map accumulates how many neighbours represent each canonical crop type.
4. The dominant type is selected; its seed/replant item is looked up via `CropRegistry.clutterSeed(block)`.
5. One unit of that item is removed from the chest via `ChestUtils.removeOne(chest, seed, false)` (the `false` bypasses seed reserve for replanting contexts).
6. The default block state of the crop is placed, then `FrameScanner.setAgeSafe(state, 0)` resets its age to 0 if it has an age property.

Soul sand positions follow the same logic but only consider `NETHER_WART` as a valid neighbour crop.

The helper `FrameScanner.tryPlantConsensus(counts, anchor, level, pos)` encapsulates steps 4–6 and is called from all three branches of `tryAutoPlantAndTill` (farmland, soul sand, and dirt/grass).

### Auto-till

If an air gap is found and the block below is `DIRT` or `GRASS_BLOCK`:

1. The four cardinal neighbours of the *block below* are checked for `FARMLAND`.
2. If at least one farmland neighbour is found, the dirt/grass is converted to `FARMLAND.defaultBlockState()`.
3. One durability point is applied to the hoe via `DurabilityLogic.applyDamage(...)`.
4. If the hoe breaks, `HarvestUtils.handleBrokenHoe(ctx, before)` is called.
5. A consensus replant pass is then run for the newly created farmland tile.

### Scan Feedback Effects

Scan actions generate local feedback effects in-world:

- Harvesting plays the harvested block's break sound.
- Replanting plays the placed crop block's place sound.
- Retilling dirt/grass to farmland plays hoe-till audio.
- Hoe breaks play item-break sound and item particles at the frame anchor.
- Spiral traversal emits subtle gray dust particles.
- Harvest events emit additional crop-colored dust bursts that rise higher than crop height.

All visual feedback is gated by client config `harvestParticles`.

---

## Crop Handling

### Maturity Thresholds

`FrameScanner.getMaturityThreshold(BlockState)` derives the maximum age by inspecting the block state's registered `IntegerProperty` values. It returns the highest value in `property.getPossibleValues()`. There are two fallbacks:

- Nether wart (`NETHER_WART`) and sweet berry bush (`SWEET_BERRY_BUSH`) return `3` if no property is found.
- All other crops return `7`.

This avoids hardcoded per-crop thresholds and correctly handles beetroot (max age 3), nether wart (max age 3), and full-age crops (max age 7).

### Crop table

| Crop            | Harvest condition                  | Post-harvest block state             | Replant item               |
|-----------------|------------------------------------|--------------------------------------|----------------------------|
| Wheat           | `age == maxAge` (7)                | Air (drops via loot)                 | `WHEAT_SEEDS`              |
| Carrots         | `age == maxAge` (7)                | Air (drops via loot)                 | `CARROT`                   |
| Potatoes        | `age == maxAge` (7)                | Air (drops via loot)                 | `POTATO`                   |
| Beetroots       | `age == maxAge` (3)                | Air (drops via loot)                 | `BEETROOT_SEEDS`           |
| Torchflower     | `age == maxAge`                    | Air (drops via loot)                 | `TORCHFLOWER_SEEDS` (item) |
| Sweet Berries   | `age == 3`                         | Reset to `age = 1` (bush stays)      | n/a                        |
| Nether Wart     | `age == 3`                         | Air (drops via loot)                 | `NETHER_WART`              |
| Melon           | Fruit block adjacent to stem       | `AIR` (fruit only; stem untouched)   | `MELON_SEEDS` (stem regrows) |
| Pumpkin         | Fruit block adjacent to stem       | `AIR` (fruit only; stem untouched)   | `PUMPKIN_SEEDS` (stem regrows) |

### CropRegistry

`CropRegistry` holds two `IdentityHashMap<Block, Item>` tables:

- `REPLANT_SEED`: block → seed item consumed for replanting.
- `CLUTTER_SEED`: superset of `REPLANT_SEED`, also covers melon/pumpkin stems and fruit blocks for the seed-clutter filter.

`canonicalCropBlock(Block)` normalises fruit and attached-stem blocks to their plain stem form so neighbour-consensus counting works consistently (`MELON` → `MELON_STEM`, `ATTACHED_MELON_STEM` → `MELON_STEM`, etc.).

---

## Enchantments

| Enchantment  | Behaviour                                                                                           |
|--------------|-----------------------------------------------------------------------------------------------------|
| Fortune      | Applied to all crop drop calculations via `LootLogic`                                               |
| Silk Touch   | Causes melons to drop the melon block instead of slices                                             |
| Unbreaking   | Respected in `normal` durability mode; ignored in `ignore_unbreaking` mode                          |
| Mending      | When `mendingNegation = true`, no durability loss is applied to a hoe bearing Mending               |

Loot calculation is performed by `LootLogic`, which prefers server-side `LootContext` builders where available and falls back to reflective compatibility paths when needed.

---

## Hoe Handling

### During a scan

At `FarmScanTask` construction and at the start of each `tick()`, the physical hoe is read from the frame via `FrameScanner.readHoeFromFrame(level, pos)`. This prefers the vanilla `ItemFrame` entity; if not found it falls back to `FIF.extractHeldItem(be)` for FastItemFrames block entities.

### On hoe break

When a hoe reaches 0 durability, `HarvestUtils.handleBrokenHoe(ctx, before)` is called:

1. The broken hoe is cleared from `ctx.hoe`.
2. A replacement hoe is searched in the chest via `FrameHoeReplacement.tryReplaceBrokenHoe(ctx)`.
3. If found, it is set in `ctx.hoe`, `FrameRegistry.updateHoe(...)` is called to update the in-memory entry, and `Services.PLATFORM.updateFrameItem(...)` syncs the new hoe into the physical frame.
4. Break sounds (`SoundEvents.ITEM_BREAK`) and item particles are played at the frame position.

### Idle frame re-equip

If a scan finds the frame empty but the chest contains a hoe, `FrameHoeReplacement.tryReplaceBrokenHoe(ctx)` also handles this case — it loads the chest hoe into the frame and `FrameRegistry.updateHoe(...)` schedules that anchor to run immediately (`ticksUntilNextRun = 0` on no-hoe to has-hoe transition).

---

## Rotation & Animation Internals

The frame-rotation system is designed to be race-free and deterministic across the multi-tick scan.

### Per-tick batching

Rotation requests from `FrameScanner.setFrameRotation(...)` are not applied immediately. They are queued in `FrameRegistry.PENDING_ROTATIONS` (a `Map<String, Map<BlockPos, Integer>>`) and flushed once per tick in `tickAndCollectReady`. This prevents multiple conflicting rotation writes within one tick and keeps world-mutation to a single flush point.

### Cooldown gate

`FrameRegistry.tryRotation(dimId, pos, gameTime)` records the last rotation game-time per frame. Rapid repeated rotation requests (e.g. from multiple positions in the same tick) are gated to avoid visual flickering.

### Full-rotation animation

When `rotationMode = FULL_ROTATION`, `FarmScanTask` computes a deterministic 8-step sequence:

```
start = current frame rotation
sequence = [(start+1)&7, (start+2)&7, ..., (start+8)&7]
```

`FrameRegistry.setAnimating(dimId, pos, true)` is called, which also clears any pending rotation for that frame. Each tick the next step in the sequence is applied via `setFrameRotation(..., bypassCooldown=true)`. When the sequence is exhausted, `setAnimating(..., false)` is called.

The `animating` flag on `FrameEntry` guards both the pending-rotation flush (skips frames that are animating) and new `scheduleRotation` calls (ignores incoming requests while animating).

### Rotation modes

| Mode                        | Mechanism                                                                              |
|-----------------------------|----------------------------------------------------------------------------------------|
| `SINGLE_STEP`               | One `setFrameRotation` call after the full spiral completes, if anything was harvested |
| `FULL_ROTATION`             | 8-step animation sequence, spread evenly across `numberOfTicksNeeded`                 |
| `FOLLOW_ROTATION`           | Per-position rotation based on ring index and ring size, applied during the spiral     |

### Applying rotations

`FrameScanner.applyScheduledRotation(level, pos, newRotation)` attempts to set the rotation in this priority order:

1. Vanilla `ItemFrame` entity — reflective `setRotation(int)` or `setRotation(byte)` method call.
2. Vanilla `ItemFrame` entity — direct field write to `rotation`.
3. FIF block entity — `FIF.setRotation(be, newRotation)` with a follow-up `sendBlockUpdated`.

---

## Performance Limits

Hard limits in `FrameScanner`:

| Limit                          | Value | Notes                                                      |
|--------------------------------|-------|------------------------------------------------------------|
| Max frames processed per run   | 24    | `FrameScanner.MAX_FRAMES_PER_RUN`                          |
| Max scan spread                | configurable | `maxSpiralDurationTicks` (default 200)               |

Work is spread across ticks by `FarmScanTask`. One task instance per anchor per cycle; `positionsPerTick = ceil(totalPositions / maxSpiralDurationTicks)`.

---

## Tickers and Lifecycle

Both the Fabric and NeoForge tickers follow the same pattern:

1. **Server tick:** call `FrameRegistry.tickAndCollectReady(dimId, level)` to collect anchors due for scanning. For each, call `FrameScanner.submitScan(dimId, anchor, level)`. Then call `FrameScanner.tickScans(dimId, level)` to advance active `FarmScanTask` instances.
2. **Frame rotation flush:** `FrameRegistry.tickAndCollectReady` also applies pending rotations from `PENDING_ROTATIONS`.
3. **World unload:** call `FrameRegistry.clearAll()` and `FrameScanner.clearAllScans()` to release all in-memory state and avoid stale references across restarts.
4. **Chunk load/unload:** chunk-load events re-activate anchors in that chunk; chunk-unload events mark them inactive.
5. **Rediscovery:** every `frameRediscoveryInterval` ticks, `FrameDiscovery` rescans loaded chunks and refreshes the registry.

---

## FastItemFrames Compatibility

FastItemFrames (FIF) replaces vanilla item frame entities with block entities for performance. Forget-Me-Crops detects this mod reflectively at runtime via `FIF.isFastItemFrameBlockEntity(be)`.

When FIF is present:

- Frame discovery iterates loaded chunk block entities instead of vanilla entity queries.
- Held-item reads use `FIF.extractHeldItem(be)`.
- Rotation reads/writes use `FIF.getRotation(be)` / `FIF.setRotation(be, rot)`.
- Block update notifications use the client/listener-only path to avoid unnecessary neighbour-update churn.

On Fabric, `FastItemFrameAdapterImpl` uses accessor mixins generated at compile time for chunk enumeration. This ensures field access survives Loom's remapping in packaged production jars rather than relying on string-based reflection on Minecraft internals.

Falls back cleanly to vanilla paths when FIF is not installed.

---

## Fabric Platform Notes

- Ticker: `ServerTickEvents.END_SERVER_TICK` drives the per-tick scan and rotation flush.
- Config screen: Mod Menu opens `com.forgetmecrops.client.config.ConfigScreen.create(parent)` from `common`. All option rows use shared custom entry subclasses (`LabelTooltipIntegerListEntry`, `LabelTooltipBooleanListEntry`, `LabelTooltipEnumListEntry`) with `LabelHitbox` lane hit-testing so tooltips are suppressed unless the cursor is over the option label lane.
- FIF mixin: accessor mixin targets the FIF chunk holder collection for safe compile-time-resolved field access.
- Metadata: `fabric.mod.json` version is set from the Gradle `mod_version` property to avoid `${version}` appearing as `unspecified` in release jars.
- Debug logging: frame-discovery changes are logged only when the discovered count changes or on periodic summaries, and slow-run warnings are rate-limited.

## NeoForge Platform Notes

- Ticker: `ServerTickEvent.Post` drives the per-tick scan.
- Config files: uses the same shared TOML loader/saver as Fabric (`Config.load()` / `Config.save()` writing `forgetmecrops-client.toml` and `forgetmecrops-server.toml`).
- Config screen: registered via `IConfigScreenFactory` SPI so the Configure button appears in NeoForge's Mods list. Delegates to the same `com.forgetmecrops.client.config.ConfigScreen` used by Fabric.

---

## Configuration Reference

### Server Config (`forgetmecrops-server.toml`)

| Option                     | Default                     | Type          | Description                                                                 |
|----------------------------|-----------------------------|---------------|-----------------------------------------------------------------------------|
| `tickInterval`             | `300`                       | int           | Ticks between anchor runs                                                   |
| `frameRediscoveryInterval` | `600`                       | int           | Ticks between loaded-chunk rediscovery passes                               |
| `scanRangeX`               | `4`                         | int           | Scan radius along X from the anchor (1–5 clamped in discovery)              |
| `scanRangeZ`               | `4`                         | int           | Scan radius along Z from the anchor (1–5 clamped in discovery)              |
| `durabilityMode`           | `normal`                    | enum string   | `normal` / `ignore_unbreaking` / `none`                                     |
| `mendingNegation`          | `true`                      | boolean       | Suppress durability loss on hoes with Mending                               |
| `debugLogging`             | `false`                     | boolean       | Verbose server log output                                                   |
| `chestFullCooldownTicks`   | `300`                       | int           | Cooldown when chest is full                                                 |
| `maxSpiralDurationTicks`   | `200`                       | int           | Max ticks to spread one scan cycle across                                   |
| `rotationMode`             | `FULL_ROTATION`             | enum string   | `SINGLE_STEP` / `FULL_ROTATION` / `FOLLOW_ROTATION`                         |
| `seedClutterMode`          | `reduced`                   | enum string   | `none` / `normal` / `reduced`                                               |
| `seedReservePerType`       | `80`                        | int           | Minimum seeds per type kept in chest when pulling for replanting            |

### Client Config (`forgetmecrops-client.toml`)

| Option             | Default | Type    | Description                              |
|--------------------|---------|---------|------------------------------------------|
| `harvestParticles` | `true`  | boolean | Enable scan visual particles (spiral trail + harvest burst) |

### `durabilityMode`

| Value               | Behaviour                                                               |
|---------------------|-------------------------------------------------------------------------|
| `normal`            | Vanilla durability loss; Unbreaking enchantment is respected            |
| `ignore_unbreaking` | Durability loss without Unbreaking reduction                            |
| `none`              | No durability loss                                                      |

### `rotationMode`

| Value                       | Behaviour                                                                              |
|-----------------------------|----------------------------------------------------------------------------------------|
| `SINGLE_STEP`               | One step after each completed harvest cycle with at least one crop                     |
| `FULL_ROTATION`             | 8-step animation across the full harvest pass                                          |
| `FOLLOW_ROTATION`           | Per-ring rotation tracking the outward spiral, up to 8 steps per ring                 |

### `seedClutterMode`

| Value     | Behaviour                                                                                                           |
|-----------|---------------------------------------------------------------------------------------------------------------------|
| `none`    | Discard seed drops before insertion. Replanting draws from drops first, then chest (subject to `seedReservePerType`) |
| `normal`  | One drop seed consumed for replanting; all remaining go into the chest unchanged                                    |
| `reduced` | One drop seed consumed for replanting; remaining drops halved (rounded down) before insertion                       |

Halving in `reduced` does not apply when the seed item is also the crop fruit (carrot, potato, nether wart, torchflower-type). Chest removal for replanting is always gated by `seedReservePerType`.

Current scope for seed filtering (clutter/reserve logic):

- **Included:** Wheat Seeds, Beetroot Seeds, Melon Seeds, Pumpkin Seeds
- **Treated as crop-fruit (not halved):** Carrot, Potato, Nether Wart, Torchflower Seeds

---

## Key Source Files

| File                                                                     | Purpose                                                    |
|--------------------------------------------------------------------------|------------------------------------------------------------|
| `common/.../frame/FrameScanner.java`                                     | Scan orchestration, hoe I/O, rotation apply               |
| `common/.../frame/FarmScanTask.java`                                     | Incremental tick-based spiral scanner                      |
| `common/.../frame/SpiralStep.java`                                       | Immutable (pos, direction) spiral record                   |
| `common/.../frame/FrameRegistry.java`                                    | Anchor registry, chunk lifecycle, rotation batching        |
| `common/.../frame/FrameDiscovery.java`                                   | Frame discovery for vanilla and FIF frames                 |
| `common/.../frame/CatchupManager.java`                                   | Gradual discovery queue across ticks                       |
| `common/.../harvest/HarvestUtils.java`                                   | Harvest execution, drop handling, hoe break events         |
| `common/.../harvest/HarvestContext.java`                                 | Per-scan mutable state (hoe, chest, counters)              |
| `common/.../harvest/CropRegistry.java`                                   | Crop → seed mappings, canonical block normalisation        |
| `common/.../util/loot/LootLogic.java`                                    | Fortune/Silk Touch drop calculation                        |
| `common/.../util/hoe/FrameHoeReplacement.java`                           | Broken hoe replacement and frame sync                      |
| `common/.../util/chest/ChestUtils.java`                                  | Chest insert/remove with reserve enforcement               |
| `common/.../util/durability/DurabilityLogic.java`                        | Enchantment-aware hoe damage                               |
| `common/.../config/Config.java`                                          | Runtime config state and TOML I/O                          |
| `common/.../client/config/ConfigScreen.java`                             | Shared Cloth Config screen builder for both loaders        |
| `common/.../client/config/ConfigTooltipFactory.java`                     | Shared tooltip content suppliers for config entries        |
| `common/.../platform/adapter/FIF.java`                                   | FastItemFrames adapter interface                           |

---

## Build Instructions

### Prerequisites

- JDK 21
- Gradle (wrapper included)

### Commands

```bash
# Full build (both loaders, runs tests)
.\gradlew.bat clean build

# Compile only
.\gradlew.bat :common:compileJava :fabric:compileJava :neoforge:compileJava

# Run tests
.\gradlew.bat :common:test

# Run Fabric dev client
.\gradlew.bat :fabric:runClient

# Run NeoForge dev client
.\gradlew.bat :neoforge:runClient
```

### Output

Release jars are produced in the `releases/` directory:

```
releases/Forget-Me-Crops-1.21.11-<version>-Fabric.jar
releases/Forget-Me-Crops-1.21.11-<version>-NeoForge.jar
```

---

## License

MIT. See [LICENSE](LICENSE).

---

## Credits

Developed by **Jared**.
Built with [MultiLoader Template](https://github.com/jaredlll08/MultiLoader-Template) targeting Fabric and NeoForge.
Optional integration with [FastItemFrames](https://modrinth.com/mod/fastitemframes) by Fuzss.