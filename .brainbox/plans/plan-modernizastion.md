You are assisting with a full modernization and restructuring of an existing MULTI‑LOADER Minecraft mod project.

The current project structure is:
- common/  (shared code)
- fabric/  (Fabric loader module)
- neoforge/ (NeoForge loader module)
- settings.gradle includes all three modules
- Uses Loom + NeoForgeGradle OR Architectury OR a custom multi-loader setup

Your task is to perform ALL of the following transformations, while also managing Git workflow and adhering to project rules.

====================================================================
0. GIT WORKFLOW REQUIREMENTS
====================================================================
Before making any changes:
- Create a new branch from master named: `feature/build-publish-modernization`

During the work:
- Periodically commit changes when it makes sense (not too often, not too rarely).
- Each commit MUST follow `.brainbox\rules\commit.rules.md`.
- If any code is modified or added, ensure all comments follow `.brainbox\rules\comment.rules.md`.
- If any documentation is modified or added, ensure it follows `.brainbox\rules\documentation.rules.md`.

At the end:
- Ensure all docs are updated to reflect the new build system, structure, and publishing pipeline.

====================================================================
1. Convert the project to use STONECUTTER for version slicing
====================================================================
- Add Stonecutter to settings.gradle and root build.gradle.
- Replace the existing multi-loader module structure with Stonecutter’s versioned workspace layout.
- Create the required Stonecutter directories:
  versions/<mc-version>/common/
  versions/<mc-version>/fabric/
  versions/<mc-version>/neoforge/
- Move or map existing source sets into the Stonecutter structure.
- Ensure Stonecutter generates the correct combined workspace.
- Preserve version catalogs, gradle.properties, and existing version logic.

====================================================================
2. Convert the project to use MODSTITCH for unified loader builds
====================================================================
- Add the Modstitch Gradle plugin to the root project.
- Remove loader-specific build logic from fabric/ and neoforge/ modules.
- Replace it with Modstitch’s unified configuration blocks.
- Configure:
  - mod metadata
  - loader-specific settings
  - remapping
  - run configs
  - dependencies
  - publishing integration
- Ensure Modstitch correctly handles:
  - Fabric Loom tasks
  - NeoForgeGradle tasks
  - Remapped JAR outputs
  - Loader-specific entrypoints
  - Mixins, access wideners, and metadata files

====================================================================
3. Add MAVEN publishing support for the mod
====================================================================
- Add a Maven publishing block that publishes:
  - common artifact (if needed)
  - fabric artifact
  - neoforge artifact
- Support publishing to:
  - Modrinth Maven (public)
  - GitHub Packages (optional)
  - GitHub Pages Maven (optional)
- Ensure:
  - groupId, artifactId, version are consistent
  - remapped production JARs are published
  - POM metadata is generated correctly
  - multi-loader artifacts are published separately

====================================================================
4. Add MODRINTH publishing support
====================================================================
Use Minotaur OR Modstitch’s built-in Modrinth block.

Configure:
- projectId
- versionNumber
- versionName
- changelog
- gameVersions
- loaders (fabric, neoforge)
- upload file = remapped production JAR for each loader
- dependencies (optional/required)

Add tasks:
- publishModrinthFabric
- publishModrinthNeoForge
- publishModrinthAll (aggregator)

====================================================================
5. Add CURSEFORGE publishing support
====================================================================
Use the CurseGradle plugin.

Configure:
- apiKey from environment variable
- projectId
- releaseType
- gameVersions
- relations (optional/required)
- upload file = remapped production JAR for each loader

Add tasks:
- publishCurseforgeFabric
- publishCurseforgeNeoForge
- publishCurseforgeAll (aggregator)

====================================================================
6. CI INTEGRATION (GitHub Actions)
====================================================================
Create or modify workflow(s) to:
- Build all Stonecutter slices
- Build Fabric + NeoForge artifacts via Modstitch
- Publish to Maven (Modrinth Maven + GitHub Packages)
- Publish to Modrinth on GitHub release creation
- Publish to CurseForge on GitHub release creation

Use secrets:
- MODRINTH_TOKEN
- CURSEFORGE_TOKEN
- GITHUB_TOKEN (for GitHub Packages)

====================================================================
7. Developer Experience Requirements
====================================================================
- Add clear Gradle tasks:
  publishModrinthFabric
  publishModrinthNeoForge
  publishCurseforgeFabric
  publishCurseforgeNeoForge
  publishAll
- Ensure tasks fail gracefully if tokens are missing.
- Ensure tasks do not run during normal builds unless explicitly invoked.
- Ensure `./gradlew build` still produces correct remapped JARs for both loaders.

====================================================================
8. Documentation Requirements
====================================================================
- Update all relevant documentation to reflect:
  - Stonecutter structure
  - Modstitch configuration
  - Maven publishing
  - Modrinth + CurseForge publishing
  - New build commands
  - New CI workflow
- All documentation MUST follow `.brainbox\rules\documentation.rules.md`.

====================================================================
9. Output Format
====================================================================
Provide:
- Updated settings.gradle
- Updated root build.gradle
- Updated module build.gradle files
- Stonecutter directory structure
- Modstitch configuration blocks
- Maven publishing blocks
- Modrinth + CurseForge publishing blocks
- GitHub Actions workflow YAML
- Instructions for environment variables
- Migration notes for metadata files (fabric.mod.json, mods.toml)
- Any required cleanup of old multi-loader logic
- A commit plan showing logical commit boundaries following commit.rules.md

Do not remove or rewrite unrelated parts of the project.
Modify only what is required to convert the project to Stonecutter + Modstitch and add full Maven + Modrinth + CurseForge publishing support, while following all .brainbox rules and proper Git workflow.

Begin by inspecting the existing build files and then propose the necessary modifications.
