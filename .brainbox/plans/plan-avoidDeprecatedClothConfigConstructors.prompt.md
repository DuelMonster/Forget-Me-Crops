## Plan: Avoid Deprecated Cloth Config Constructors

**TL;DR**: Three custom config entry classes (`LabelTooltip*ListEntry`) each call `@ApiStatus.Internal @Deprecated` super-constructors in Cloth Config, suppressing warnings with `@SuppressWarnings("deprecation")`. The fix involves bypassing the deprecated `EnumListEntry<T>` wrapper (clean fix, unblocked) and determining what's available for `BooleanListEntry`/`IntegerListEntry` in cloth-config 21.11.153 (needs jar inspection first).

---

### Discovery Summary

**The deprecated pattern (same in all 3 files)**:
- Each class extends an internal Cloth Config entry class (`BooleanListEntry`, `IntegerListEntry`, `EnumListEntry<T>`) only to override `render()` (updates `LabelHitbox`) and `getTooltip()` (gates tooltip to label area). All have `@SuppressWarnings("deprecation")`.

**Key finding — `EnumListEntry` is a thin wrapper**:
In the cloth-config source, `EnumListEntry<T>` has zero logic of its own — all its constructors just forward to `SelectionListEntry<T>` after converting `Class<T>→T[]` and wrapping `Function<T, Component>` to the raw `Function<Enum, Component>`. `SelectionListEntry` does have a **non-deprecated 8-argument constructor** (omits `requiresRestart`). This makes the enum fix clean and unblocked.

**Key uncertainty — boolean and integer**:
In the available cloth-config source (v8/v15 branches), `BooleanListEntry` and `IntegerListEntry` have *all* constructors marked `@ApiStatus.Internal @Deprecated`. Whether cloth-config-neoforge **21.11.153** (Dec 2025) adds non-deprecated alternatives is unknown without inspecting the jar.

---

### Steps

**Phase 1 — Jar inspection** *(unblocks Phase 3)*
1. Run `javap -p` against `cloth-config-neoforge-21.11.153-neoforge.jar` to list all constructors of `BooleanListEntry`, `IntegerListEntry`, and (for confidence) `SelectionListEntry`. Jar is at `C:\Users\DuelM\.gradle\caches\modules-2\files-2.1\me.shedaniel.cloth\cloth-config-neoforge\21.11.153\[hash]\cloth-config-neoforge-21.11.153-neoforge.jar`.

**Phase 2 — Fix `LabelTooltipEnumListEntry`** *(unblocked, parallel with Phase 1)*
2. Change `extends EnumListEntry<T>` → `extends SelectionListEntry<T>`.
3. In the `super(...)` call, replace `enumClass` (the `Class<T>` arg) with `enumClass.getEnumConstants()` to produce the `T[]` expected by `SelectionListEntry`'s constructor.
4. Change the `enumNameProvider` parameter type from `Function<Enum, Component>` (raw) to `Function<T, Component>` — pass it directly to super without the `legacyEnumNameProvider()` adapter.
5. Delete the `legacyEnumNameProvider()` static helper method (entirely obsolete).
6. Remove `@SuppressWarnings({"rawtypes", "unchecked"})` from that method and `@SuppressWarnings("deprecation")` from the constructor.

**Phase 3 — Fix `LabelTooltipBooleanListEntry` and `LabelTooltipIntegerListEntry`** *(depends on Phase 1)*

- **Path A** — non-deprecated constructors found in 21.11.153: update the `super(...)` call in each class to use the non-deprecated signature; remove the `@SuppressWarnings("deprecation")`.
- **Path B** — all constructors still deprecated: use `ConfigEntryBuilder` to build the underlying entry (avoids our code calling deprecated constructors directly), then apply a **delegation wrapper**: a class extending an appropriate non-deprecated base (`AbstractConfigListEntry<T>` is `@ApiStatus.Internal` but not deprecated) that holds the builder-built entry, delegates all required methods, and overrides `render()` + `getTooltip()` for label-hitbox behavior. This is more boilerplate but completely eliminates the deprecated call at our layer.
  - **Path B-alt** (if delegation proves fragile): Accept the current `@SuppressWarnings("deprecation")` as intentional and document that `BooleanListEntry`/`IntegerListEntry` offer no public construction API in this cloth-config version, making the suppression unavoidable. Restrict the suppression to the minimum (constructor only, not the whole class).

**Phase 4 — Verification**
7. Temporarily remove all `@SuppressWarnings("deprecation")` annotations and confirm no new deprecation warnings appear in VS Code Java diagnostics.
8. Run the mod client (`debug fabric client` or `debug neoforge client`) and open the config screen; verify all tooltips still appear only when hovering the label text for all 3 entry types.

---

### Relevant Files
- `common/src/main/java/com/forgetmecrops/client/config/LabelTooltipEnumListEntry.java` — `extends EnumListEntry<T>` → `extends SelectionListEntry<T>`; remove `legacyEnumNameProvider`; change `Function<Enum,Component>` to `Function<T,Component>`
- `common/src/main/java/com/forgetmecrops/client/config/LabelTooltipBooleanListEntry.java` — Path A or B depending on jar inspection
- `common/src/main/java/com/forgetmecrops/client/config/LabelTooltipIntegerListEntry.java` — Path A or B depending on jar inspection
- `common/src/main/java/com/forgetmecrops/client/config/LabelHitbox.java` — no changes needed
- `common/src/main/java/com/forgetmecrops/client/config/ConfigScreen.java` — no changes needed (call sites are unchanged; enum fix does not change the user-facing constructor signature)

---

### Decisions
- **Scope**: Only the 3 `LabelTooltip*Entry` classes and no other files. `ConfigScreen.java` call sites don't change.
- **Phase 2 is a clear win** regardless of Phase 1 findings — should be done first.
- **Phase 3 Path B delegation** is scoped only if needed; it would also introduce a new `AbstractLabelTooltipEntry<T>` base class to de-duplicate hitbox logic shared across the two wrappers.

---

### Further Consideration

**Path B complexity**: A full delegation wrapper over `AbstractConfigListEntry<T>` in Cloth Config requires forwarding a non-trivial interface surface (save, reset, error handling, narration, children widgets, etc.). Before choosing this path, it is worth confirming whether cloth-config 21.11.153 simply added clean constructors (which the Dec 2025 release date suggests is quite possible). The Phase 1 jar inspection should be done first and is fast.
