// 🧱 Stonecutter settings: the blueprint that tells Gradle which loader×version slices to bake.
// Without this, all the carefully structured build logic has nowhere to live. It all starts here.

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        // Modstitch — the unified Fabric Loom + ModDevGradle abstraction layer
        maven("https://maven.isxander.dev/releases/")
        // Fabric Loom — for Fabric-platform builds
        maven("https://maven.fabricmc.net/")
        // NeoForge ModDevGradle — for NeoForge-platform builds
        maven("https://maven.neoforged.net/releases/")
        // Stonecutter — the version-slicing plugin that glues everything together
        maven("https://maven.kikugie.dev/releases")
        maven("https://maven.kikugie.dev/snapshots")
        // mod-publish-plugin — Modrinth + CurseForge publishing by modmuss50
        maven("https://maven.modmuss50.me/")
    }
}

plugins {
    // Foojay JVM toolchain resolver — auto-provisions the right JDK
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
    // Stonecutter — the star of the show for multi-version/multi-loader magic
    id("dev.kikugie.stonecutter") version "0.9.3"
}

stonecutter {
    // Use Kotlin DSL for the generated stonecutter.gradle.kts controller
    kotlinController = true
    // All loader×version nodes share a single central build script at the root
    centralScript = "build.gradle.kts"

    create(rootProject) {
        // Helper that registers both a fabric and neoforge node for a given MC version.
        // Each node gets a name like "1.21.11-fabric" and targets the given MC version.
        fun mc(mcVersion: String, loaders: Iterable<String>) =
            loaders.forEach { version("$mcVersion-$it", mcVersion) }

        // The one Minecraft version this mod currently supports.
        // Add more mc(…) calls here when expanding to additional versions.
        mc("1.21.11", loaders = listOf("fabric", "neoforge"))

        // VCS reset point: the branch that "clean source" lives in.
        // Running the "Reset active version" Stonecutter task restores the source
        // to this state before committing — keeps the git history diff-friendly.
        vcsVersion = "1.21.11-fabric"
    }
}

rootProject.name = "forgetmecrops"
