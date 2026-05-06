// ⚙️ Stonecutter controller: auto-generated-ish file that tracks the active version
// and registers aggregate tasks. Stonecutter regenerates this on sync; manual edits
// to the `stonecutter active` line are overwritten — use the IDE task instead.

plugins {
    id("dev.kikugie.stonecutter")
}

// The currently active development node.
// Switch via the IDE "Stonecutter > Switch version" task or Stonecutter Gradle tasks.
stonecutter active "1.21.11-fabric"

// chiseledBuild: builds ALL registered nodes in one shot.
// Run with:  ./gradlew chiseledBuild
stonecutter registerChiseled tasks.register("chiseledBuild", stonecutter.chiseled) {
    group = "project"
    description = "Builds ALL Stonecutter nodes (all loader×version combinations)."
    ofTask("build")
}

// chiseledPublishAll: publishes ALL nodes to Modrinth + CurseForge (when tokens present).
stonecutter registerChiseled tasks.register("chiseledPublishAll", stonecutter.chiseled) {
    group = "publishing"
    description = "Publishes ALL Stonecutter nodes to configured platform stores."
    ofTask("publishMods")
}

// chiseledPackageRelease: copies the production JAR for every node into releases/.
// Run with:  ./gradlew chiseledPackageRelease
stonecutter registerChiseled tasks.register("chiseledPackageRelease", stonecutter.chiseled) {
    group = "release"
    description = "Copies the production JAR for ALL Stonecutter nodes into releases/."
    ofTask("packageRelease")
}

// Shared repository declarations for ALL sub-projects (nodes).
// Loader-specific repos (Fabric, NeoForge) are resolved here; each node
// inherits them via allprojects so their dependency resolution doesn't fail.
allprojects {
    repositories {
        mavenCentral()
        mavenLocal()
        // NeoForge Maven — NeoForge and MDG artifacts
        maven("https://maven.neoforged.net/releases")
        // Fabric Maven — Fabric Loader and Fabric API
        maven("https://maven.fabricmc.net/")
        // Xander Maven — YACL
        maven("https://maven.isxander.dev/releases")
        // TerraformersMC — ModMenu
        maven("https://maven.terraformersmc.com/releases/")
        // SpongePowered — Mixin
        maven("https://repo.spongepowered.org/repository/maven-public")
        // ParchmentMC — human-readable parameter mappings
        maven("https://maven.parchmentmc.org/")
        // Fuzss mod resources — PuzzlesLib etc.
        maven("https://raw.githubusercontent.com/Fuzss/modresources/main/maven/")
        // BlameJared — misc CurseForge-hosted mods
        maven("https://maven.blamejared.com")
        // mod-publish-plugin
        maven("https://maven.modmuss50.me/")
    }
}
