# Plan: Replace Cloth Config with YACL

**TL;DR:** Swap `cloth-config` v21.11.153 for `yet-another-config-lib` v3.8.2+1.21.1 across all three subprojects. The four `LabelTooltip*` / `LabelHitbox` wrapper classes exist solely to work around Cloth Config's limited tooltip hit-testing — YACL shows tooltips natively on hover anywhere over the option row, so they're deleted outright. `ConfigTooltipFactory` is adapted (return type change), and `ConfigScreen` is fully rewritten in YACL's builder API.

---

## Phase 1 — Git branch

1. Create branch `feature/yacl` from `master`

---

## Phase 2 — Gradle setup *(all steps parallel)*

2. `gradle.properties` — add `yacl_version=3.8.2+1.21.1`

3. `buildSrc/src/main/groovy/multiloader-common.gradle` — in `repositories {}`: remove `maven { url 'https://maven.shedaniel.me/' }`, add `maven { name = "Xander Maven"; url = "https://maven.isxander.dev/releases" }`. This propagates to all three subprojects since both loader build files apply the common plugin.

4. `common/build.gradle` — replace the `compileOnly "me.shedaniel.cloth:cloth-config-neoforge:21.11.153"` dep with `compileOnly "dev.isxander:yet-another-config-lib:${yacl_version}-neoforge"`

5. `fabric/build.gradle` — remove the redundant `maven { url "https://maven.shedaniel.me/" }` (Xander Maven now covers it via multiloader-common); replace `modApi("me.shedaniel.cloth:cloth-config-fabric:21.11.153") { exclude group: "net.fabricmc.fabric-api" }` with `modImplementation("dev.isxander:yet-another-config-lib:${yacl_version}-fabric") { exclude group: "net.fabricmc.fabric-api" }`

6. `neoforge/build.gradle` — remove `maven { url "https://maven.shedaniel.me/" }`; replace `implementation("me.shedaniel.cloth:cloth-config-neoforge:21.11.153")` with `implementation("dev.isxander:yet-another-config-lib:${yacl_version}-neoforge")`

---

## Phase 3 — Java source changes *(depends on Phase 2)*

7. **Delete** 4 Cloth Config wrapper classes (all under `common/src/main/java/com/forgetmecrops/client/config/`):
   - `LabelTooltipBooleanListEntry.java`
   - `LabelTooltipIntegerListEntry.java`
   - `LabelTooltipEnumListEntry.java`
   - `LabelHitbox.java`

8. **Rewrite `ConfigTooltipFactory.java`** — change all return types from `Supplier<Optional<Component[]>>` to `OptionDescription` (import `dev.isxander.yacl3.api.OptionDescription`):
   - `plain(key)` → `return OptionDescription.of(Component.translatable(key))`
   - Rich multi-line methods (`durabilityMode`, `rotationMode`, `seedClutterMode`) — split the existing single `Component` with `\n` literals into multiple `Component` arguments to `OptionDescription.of(...)`, since YACL renders each argument as a separately wrapped line

9. **Rewrite `ConfigScreen.java`** — replace Cloth Config imports and builder with YACL:
   - New imports: `dev.isxander.yacl3.api.*`, `dev.isxander.yacl3.api.controller.*`
   - Top-level: `YetAnotherConfigLib.createBuilder().title(Component.literal(ModCommon.MOD_NAME)).save(Config::save)` (replaces `builder.setSavingRunnable`)
   - Categories: `ConfigCategory.createBuilder().name(...).option(...).build()` — options placed directly on the category (no groups needed)
   - Each `Option<T>` uses: `.name(...)`, `.description(ConfigTooltipFactory.X())`, `.binding(default, getter, setter)`, `.controller(...)`
   - **Boolean** → `.controller(TickBoxControllerBuilder::create)`
   - **Integer** → `.controller(opt -> IntegerFieldControllerBuilder.create(opt).range(ConfigDefaults.X_MIN, Integer.MAX_VALUE))`
   - **Enum** → `.controller(opt -> EnumControllerBuilder.create(opt).enumClass(X.class).formatValue(v -> localizedEnumName(prefix, v)))`
   - `create(Screen parent)` still returns `Screen` — `ModMenuEntrypoint` and `ConfigScreenFactoryBridge` are **unchanged**

---

## Phase 4 — Mod manifests *(parallel with Phase 3)*

10. `fabric/src/main/resources/fabric.mod.json` — add `"yet_another_config_lib_v3": ">=3.8.2"` to the `"depends"` object

11. `neoforge/src/main/resources/META-INF/neoforge.mods.toml` — add a `[[dependencies.forgetmecrops]]` entry with `modId = "yet_another_config_lib_v3"`, `type = "required"`, `versionRange = "[3.8.2,)"`, `side = "CLIENT"`

---

## Relevant files

| File                                                        | Change                                       |
| ----------------------------------------------------------- | -------------------------------------------- |
| `gradle.properties`                                         | Add `yacl_version` property                  |
| `buildSrc/src/main/groovy/multiloader-common.gradle`        | Swap maven repo                              |
| `common/build.gradle`                                       | Swap `compileOnly` dep                       |
| `fabric/build.gradle`                                       | Remove shedaniel maven, swap to `modImplementation` |
| `neoforge/build.gradle`                                     | Remove shedaniel maven, swap `implementation` |
| `common/.../config/ConfigScreen.java`                       | Full rewrite                                 |
| `common/.../config/ConfigTooltipFactory.java`               | Return type change to `OptionDescription`    |
| `common/.../config/LabelTooltipBooleanListEntry.java`       | **Deleted**                                  |
| `common/.../config/LabelTooltipIntegerListEntry.java`       | **Deleted**                                  |
| `common/.../config/LabelTooltipEnumListEntry.java`          | **Deleted**                                  |
| `common/.../config/LabelHitbox.java`                        | **Deleted**                                  |
| `fabric/.../resources/fabric.mod.json`                      | Add YACL to `depends`                        |
| `neoforge/.../resources/META-INF/neoforge.mods.toml`        | Add YACL dependency                          |
| `ModMenuEntrypoint.java`, `ConfigScreenFactoryBridge.java`  | No changes                                   |

---

## Verification

1. `./gradlew :common:compileJava` — zero Cloth Config import errors, YACL API resolves
2. `./gradlew :fabric:compileJava` and `:neoforge:compileJava` — clean compile
3. Fabric client run → ModMenu config button → YACL screen with both categories and all 12 options visible
4. NeoForge client run → mods list Config button → YACL screen opens
5. Verify each option type renders: tick box, integer field, cycling enum
6. Verify tooltips appear on hover over any part of an option row (not just widget side)
7. Change a value, save → confirm config file on disk reflects the new value
8. VS Code Java Problems panel shows zero errors

---

## Decisions

- **No config migration** (explicitly out of scope — unreleased mod)
- YACL `3.8.2+1.21.1` — latest release for MC 1.21.1 on both loaders
- Integer options use `IntegerFieldController` (text field) to match current Cloth Config UX; slider option is a future consideration
- Integer options with only a minimum use `.range(min, Integer.MAX_VALUE)` as the upper bound
- YACL is `required` in both manifests (it was never optional before)
- `modImplementation` (not `modApi`) for YACL in fabric — no JiJ, no transitive exposure needed
