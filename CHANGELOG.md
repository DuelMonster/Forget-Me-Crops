# Changelog

All notable changes to this project will be documented in this file.

## Unreleased

- Fix(rotation): deterministic full-rotation animation and per-frame `animating` guard to avoid scheduling conflicts; rotations are batched per-tick via `FrameRegistry.PENDING_ROTATIONS`.
- Fix(loot): block-drop calculation now respects `Fortune` and `Silk Touch` using server-side `LootContext` where available, with safe reflective fallbacks for loader differences.
- Fix(hoe): hardened broken-hoe replacement and frame-held-hoe synchronization between `HarvestUtils` and `FrameRegistry`.
- Chore(logging): centralized runtime logging via `Constants.log*` wrappers; lowered noisy INFO messages to DEBUG and added `[ROT]` rotation tags behind `debugLogging`.
- Feature(scan): `FarmScanTask` precomputes a deterministic `fullAnimationSequence` (8 steps) and performs a single neighbor/repair pass per scan; auto-plant and auto-till are executed during the spiral + neighbor pass.
- Chore(tickers): Fabric and NeoForge tickers now call `FrameRegistry.clearAll()` on world/server unload to avoid stale references.
- Refactor: consolidate frame and utility helpers, add safer FastItemFrames adapters, and separate loader-specific logic into platform adapters.
- Build/test: Gradle packaging, CI pipelines, and release metadata improvements.

## [1.2.0] - 2026-04-22
- Remove Forge support; keep Fabric and NeoForge only.
- Implement platform-specific loot/durability logic with native LootContext usage.
- Harden enchantment extraction by moving loader-specific logic into platform helpers.
- Remove shared reflective `ReflectionUtils` and consolidate logic per-loader.
- Add unit tests for core logic and CI for build + tests.
- Add release packaging tasks and CHANGELOG.

