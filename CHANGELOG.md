# Changelog

## Unreleased

- Fix: Resolve multiple compilation errors in the `common` module (removed stray braces, implemented missing helpers).
- Feature: Add conservative common-side helpers: `ChestUtils`, `LootLogic.getBlockDrops`, `HarvestUtils` helpers, and `DurabilityLogic.applyDamage`.
- Build: Bump project and mod version to `1.1.0`.

These changes are primarily internal and provide conservative, loader-agnostic placeholders
so the project builds and the common harvesting flow can be iterated on further.
