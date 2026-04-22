# Changelog

All notable changes to this project will be documented in this file.

## [1.2.0] - 2026-04-22
- Remove Forge support; keep Fabric and NeoForge only.
- Implement platform-specific loot/durability logic with native LootContext usage.
- Harden enchantment extraction by moving loader-specific logic into platform helpers.
- Remove shared reflective `ReflectionUtils` and consolidate logic per-loader.
- Add unit tests for core logic and CI for build + tests.
- Add release packaging tasks and CHANGELOG.
# Changelog

## Unreleased

- Fix: Resolve multiple compilation errors in the `common` module (removed stray braces, implemented missing helpers).
- Feature: Add conservative common-side helpers: `ChestUtils`, `LootLogic.getBlockDrops`, `HarvestUtils` helpers, and `DurabilityLogic.applyDamage`.
- Build: Bump project and mod version to `1.1.0`.

These changes are primarily internal and provide conservative, loader-agnostic placeholders
so the project builds and the common harvesting flow can be iterated on further.
