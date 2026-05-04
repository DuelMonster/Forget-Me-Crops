# CHANGELOG

## 0.12.0

- Rename project from FastHarvester to Forget-Me-Crops: display name, mod ID (`forgetmecrops`), Java package root (`com.forgetmecrops`), metadata, resources, and documentation updated throughout.
- Unify Fabric and NeoForge class naming for loader entrypoints and helper bridges: use shared names such as `ModInitializer`, `FarmTicker`, `PlatformHelper`, `ClothConfigBridge`, and `ConfigScreenFactoryBridge` with updated loader metadata/service declarations.
- Replace all ignored-exception catch blocks with explicit `logTrace`/`logDebug` calls in `PlatformHelper` (Fabric/NeoForge), `FarmTicker`, and `FrameScanner`.
- Remove redundant null checks on non-null context objects in `HarvestUtils` and `FarmScanTask`.
- Harden `Config.configFileForKey` to use literal file name strings, eliminating a potential null-deref SpotBugs warning.
- Fix `FrameDiscovery` debug log to use resolved `be.getClass().getName()` instead of conditional null check.
- Related-mod install tasks now default to optional behavior and use loader-specific subdirectories for Fabric and NeoForge dependencies.
- Fixed empty-farm repair sweep behavior so chest-backed replanting occurs during scheduled spiral scans.
- Simplified `FarmScanTask` to a single spiral pass (removed constructor maturity pre-scan and end-of-task neighbour-only repair pass).
- Added scan-pass player feedback: planting/tilling/harvest sounds, spiral trail dust particles, and crop-colored harvest dust burst particles.
- Tuned harvest dust burst behavior to rise higher with tighter horizontal spread.
- Fixed spiral trail dust filtering so particles render only on valid farm tiles (crops/fruit/stems and prepared farmland/soul sand), not on off-farm traversal connectors such as dirt/grass links.
- Restored dirt/grass repair tiling by including adjacent tillable repair targets in BFS farm position membership.
- Expanded BFS farm traversal to include connected air-over-dirt/grass repair tiles across the farm area (within configured scan bounds), improving full-farm retill coverage.
- Fixed scheduled scan continuity when a frame is empty: scan now attempts chest-driven hoe replacement inline instead of immediately aborting.
- Fixed anchor resumption latency after chest-driven hoe replacement by scheduling immediate scan run (`ticksUntilNextRun = 0`) when an anchor transitions from no hoe to has hoe.
- Fixed TOML string parsing for quoted enum values so `rotationMode` and other string-backed options load correctly instead of silently falling back.
- Fixed `FULL_ROTATION` timing to start with spiral scan progression and complete exactly one paced `0..7` cycle over the configured scan duration.
- Config UI: tooltips for all option types (integer fields, boolean toggles, and enum selectors) now appear only when hovering the option label text, not when hovering the value widget or reset button. Implemented via per-loader `LabelHitbox` helper and `LabelTooltipIntegerListEntry` / `LabelTooltipBooleanListEntry` / `LabelTooltipEnumListEntry` custom entry subclasses; `ConfigEntryBuilder` is no longer used in either loader's config screen.
- Config UI refactor: moved Cloth Config screen construction into shared `com.forgetmecrops.client.config.ConfigScreen` (common module) with shared `ConfigTooltipFactory`, `LabelHitbox`, and `LabelTooltip*ListEntry` classes.
- Removed now-redundant loader wrappers (`ConfigScreens` and `ClothConfigBridge` in Fabric/NeoForge); loader entrypoints now delegate directly to `ConfigScreen.create(...)`.
- Maintenance refactor/cleanup pass: deduplicated replant helper logic in `HarvestUtils`, centralized anchor resolution helpers, and removed a now-unused durability import from `FrameScanner`.

## 0.11.0

- Refactor `FrameScanner` to deduplicate anchor validation, planting consensus logic, and shared spiral generation paths.
- Extract `FarmScanTask` and `SpiralStep` from `FrameScanner` into top-level package-private classes in `com.forgetmecrops.frame`.

## 0.10.0

- fix `FrameScanner` null-safety and crop-age handling, and remove unused imports.
- Fix NeoForge replant and crop maturity handling.
- Optimization: remove dead code, streamline map lookups, and clean up imports.

## 0.9.0

- Implement seed-clutter policies: `NONE` removes supported seed drops before insertion (except crop-fruit), `NORMAL` consumes one seed per harvest from drops and inserts remaining seeds, `REDUCED` consumes one seed and halves remaining seed drops before insertion; `seedReservePerType` prevents chest removals that would drop below the configured reserve. Docs updated to reflect behavior.
- Scan optimization: `FarmScanTask` now performs a quick maturity pre-check before starting the spiral. If no mature crops or harvestable fruit are present the spiral pass exits early and no frame rotation/animation is scheduled for that cycle. This reduces unnecessary work on idle farms.

## 0.8.0

- Update .gitignore and workspace launch paths
- Enable debugLogging and defer FastItemFrames API probe during init
- API first FastItemFrames write path and block state fallback; invoke apiMarkUpdated when available
- Defer frame validation and add FIF catch up queues; register empty frames as inactive anchors
- Harden replacement flow, add skipNextDamage, verify persistence and add rotation readback logging
- Use defensive copy for HarvestContext in FarmScanTask to avoid mutating registry ItemStack
- Store defensive copy in FrameRegistry.registerFrame and pass copy in syncFrameHoe to avoid shared ItemStack mutation
- Defensive copy in FastItemFrameAdapterImpl.extractHeldItem
- Centralize config descriptors and update platform config screens
- Centralize title screen logging and use helper in mixins
- Add PlatformReflective and delegate reflection/fallbacks; pass defensive ItemStack.copy for BE writes
- Split scanRange into scanRangeX/scanRangeZ and update scanning logic
- Expose scanRangeX/scanRangeZ in Fabric and NeoForge config screens; update NeoForge config mapping
- Add scanRangeX/scanRangeZ keys to server TOML and language entries
- Document scanRangeX/scanRangeZ semantics and migration
- Remove legacy scanRange; use scanRangeX/scanRangeZ exclusively
- Remove legacy scanRange; document migration to scanRangeX/scanRangeZ
- Try FastItemFrameAdapterImpl before reflection when updating frame items
- Provide IConfigScreenFactory via service loader to enable Mods to Config button
- Match IConfigScreenFactory signature
- Enable Mods to Config screen via SPI and robust reflective registration
- Commit automated formatting and helper updates

## 0.7.0

- Add Javadocs to common frame and util classes; tidy FrameScanner imports
- Add minimal Javadocs and constructors in Fabric and NeoForge modules to fix doclint warnings
- Add unused imports scanner script
- Centralize logging wrappers; docs: add Javadocs for log wrappers; fix enchantment registry usage
- Avoid scheduled rotation conflicts during full rotation animation
- Lower noisy INFO logs and tidy rotation logging
- Lower routine INFO logs to DEBUG
- Reduce CatchupManager, SweetBerry, and mixin info logs to DEBUG
- Reduce noisy INFO logs to DEBUG across registry, scanner, discovery, catchup, chest and harvest code
- Update README, TECHNICAL, and CHANGELOG
- Prefer FastItemFrames API when available, fall back to heuristics
- Log detected FastItemFrames API class for debug verification
- Make FIF detection log informational for runtime verification
- Force load FastItemFrameAdapterImpl at init to run API probe
- Add discovered FIF BE classname to API probe candidates
- Use runs/client and runs/server for NeoForge run dirs to match Fabric
- Use gameDirectory property for run configuration

## 0.6.0

- Harvest & loot â€” integrate LootLogic and HarvestUtils; chest/hoe handling, seed policies, and frame scanning updates
- Fabric & NeoForge â€” tickers, platform helpers, lifecycle hooks and Javadocs
- Javadoc pass and minor cleanups across modules
- Resume anchor scans when hoe returned to frame or chest
- Batch frame rotations per tick to reduce world updates
- Normalize line endings via .gitattributes; ignore related mods/ and releases/; remove embedded releases/original from index
- Related mods/
- Remove duplicate placeholder CropRouter; use crops.CropRouter implementation
- Add Javadocs and mark utility classes non instantiable
- Clarify HarvestUtils behavior; seed clutter, replant and hoe replace docs
- Implement per tick rotation batching and frame registry flush path
- Mark utility classes non instantiable
- Move FastItemFrameAdapterImpl to platform.adapter and update refs; move TestUtils to test sources
- Move FrameScanner/FrameRegistry/FrameDiscovery to com.forgetmecrops.frame and update callers
- Move LootLogic to com.forgetmecrops.util.loot; preserve vanilla fortune/silk touch behavior
- Move HoeUtils, ChestUtils, DurabilityLogic to com.forgetmecrops.util.*; update callers and tests
- Update callers to new com.forgetmecrops.frame and com.forgetmecrops.util.* packages

## 0.5.0

- Apply config defaults and runtime fixes
- Respect seedReservePerType, mark chests changed, relax auto till
- Insert drops then draw seed from chest

## 0.4.0

- Catch up, discovery, FIF adapter, comments
- Refactor ticker, integrate catch up, comments
- Refactor ticker, integrate catch up, comments
- Guard FarmTicker init against modEventBus registration errors
- Register runtime listeners on container.getEventBus
- Register FarmTicker on global NeoForge.EVENT BUS
- Deduplicate ClothConfig builders; add platform helpers and Javadoc
- Prevent spiral generator infinite loop; implement spiral ordered tick sliced FarmScanTask and add debug logging

## 0.3.0

- Implement improved in game config UI with vanilla widgets and ModMenu integration for MC 1.21.11; no external dependencies required
- Fix common compile errors; implement basic harvest helpers; bump version to 1.1.0
- Record removal of Forge support
- Commit workspace changes
- Lowercase mod id/group and mixin packages for NeoForge dev run
- Lower java version and neoforge mixin compatibility to JAVA 18 for dev loader compatibility
- Align toolchain languageVersion with java version property
- Set java version back to 21 and restore neoforge mixin compatibility to JAVA 21
- Relax NeoForge loader version range for dev runs
- Correct mixin package names to com.forgetmecrops.mixin
- Add tests, CI, packaging; bump version to 1.2.0; remove forge placeholders; update fabric mod metadata; add changelog
- Add pure Java TestUtils and update tests to avoid Minecraft classes
- Commit remaining platform/loot/durability changes
- Add integration/runtime smoke tests for Fabric and NeoForge
- Add release workflow
- Chunk load discovery + NeoForge ticker + FastItemFrames detection
- Seed clutter trimming in REDUCED mode
- Melon/pumpkin fruit & auto plant; add ChestUtils.removeOne
- Auto till repair and frame rotation rules
- BFS farm discovery; chunk unload cleanup; waterlogged chest enforcement; Nether Wart & Torchflower support
- Add playful inline comments to Java files
- Add one time catch up discovery on first server tick; check chest below frame; add diagnostic logging

## 0.2.0

- Remove PuzzlesLib/ForgeConfigAPIPort dependencies and registration. Refactor config to plain POJO and update loader entrypoints for manual config handling
- Implement TOML based config loading and update loader entrypoints to call Config.load
- Implement NeoForge config sync logic and expand Fabric config UI to cover all options
- Migrate config registration and reload to ModConfigSpec and event bus for NeoForge 21.11.x compatibility; resolve all build errors and ensure loader agnostic config sync
- Add friendly and emotional class level Javadoc to config and enums for full documentation compliance

## 0.1.0

- Add friendly, emotional, and occasionally funny comments to all Java files as required by project rules. Every class and method is now documented in a human, engaging style
- Add explicit default constructors and Javadoc comments to resolve all Javadoc warnings; update utility and logic classes in common module
- Add PuzzlesLib, ForgeConfigAPIPort, and ModMenu dependencies for Fabric and NeoForge
- Refactor Config.java to use PuzzlesLib @Config and ConfigCore
- Register config with PuzzlesLib in Fabric and NeoForge initializers
- Switch Fabric to intermediary mappings and update PuzzlesLib/ForgeConfigAPIPort versions for 1.21.11 compatibility. Fixes access widener namespace error

## 0.0.0

- Initial commit of Forget-Me-Crops mod skeleton
- Implement core farm scanning and automation logic in common
- Recreate common module structure, config, and core files
- Add Fabric and NeoForge entrypoints and update loader metadata
