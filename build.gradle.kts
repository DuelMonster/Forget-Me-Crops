// 🏗️ Central build script: runs once for every Stonecutter version node (1.21.11-fabric,
// 1.21.11-neoforge, etc.). Modstitch reads the node's gradle.properties to decide which
// platform toolchain (Loom vs ModDevGradle) to activate. One script to rule them all.

plugins {
    // Modstitch: the unified build plugin that abstracts Fabric Loom and NeoForge MDG.
    // Only one platform is "active" per node — determined by modstitch.platform in
    // the node's versioned gradle.properties.
    id("dev.isxander.modstitch.base") version "0.8.5"
    // mod-publish-plugin: Modrinth + CurseForge publishing (applied conditionally below)
    id("me.modmuss50.mod-publish-plugin") version "0.8.4" apply false
    // Maven publish (standard Gradle plugin — no version needed)
    id("maven-publish")
}

// ────────────────────────────────────────────────────────────
//  Helper: read a versioned property and pass it to a consumer
//  only if it exists. Keeps the block below readable.
// ────────────────────────────────────────────────────────────
fun prop(name: String, consumer: (prop: String) -> Unit) {
    (findProperty(name) as? String?)?.let(consumer)
}

// The Minecraft version for this node (e.g. "1.21.11")
val minecraft = property("deps.minecraft") as String

// Which loader is this node for? Extracted from the project name (e.g. "1.21.11-fabric" → "fabric")
val loader = name.substringAfterLast("-")
val isFabric = loader == "fabric"
val isNeoForge = loader == "neoforge"

// ────────────────────────────────────────────────────────────
//  Modstitch core configuration
// ────────────────────────────────────────────────────────────
modstitch {
    minecraftVersion = minecraft

    // ── Metadata: populates fabric.mod.json and neoforge.mods.toml templates ──
    metadata {
        modId = "forgetmecrops"
        modName = "Forget-Me-Crops"
        modVersion = property("mod_version") as String
        modGroup = "com.forgetmecrops"
        modAuthor = "Jared"

        // Extra tokens available inside src/main/templates/
        replacementProperties.putAll(mapOf(
            "mod_id"                  to "forgetmecrops",
            "mod_name"                to "Forget-Me-Crops",
            "mod_description"         to "Automates crop harvesting for both Fabric and NeoForge.",
            "mod_license"             to "CC0-1.0",
            "mod_author"              to "Jared",
            "mod_homepage"            to "https://github.com/duelmonster/Forget-Me-Crops",
            "mod_issue_tracker"       to "https://github.com/duelmonster/Forget-Me-Crops/issues",
            "minecraft_version_range" to "[1.21.11, 1.22)",
            "yacl_version_range"      to "[3.8.2,)",
            "neoforge_loader_range"   to "[10,)"
        ))
    }

    // ── Fabric Loom platform (active when modstitch.platform=loom) ──
    loom {
        // Fabric Loader version — intentionally not stored in versioned properties
        // since Fabric Loader is largely version-independent and rarely needs a per-MC pin.
        fabricLoaderVersion = property("deps.fabric_loader") as String

        configureLoom {
            // Runs for development (matches the VS Code task setup the project already uses)
            runs.named("client") {
                setConfigName("Fabric Client")
                ideConfigGenerated(true)
                runDir("runs/client")
            }
            runs.named("server") {
                setConfigName("Fabric Server")
                ideConfigGenerated(true)
                runDir("runs/server")
            }
            // Access-widener (if present). Modstitch also auto-converts AWs → ATs for NeoForge.
            val aw = project.file("src/main/resources/forgetmecrops.accesswidener")
            if (aw.exists()) accessWidenerPath.set(aw)
        }
    }

    // ── NeoForge ModDevGradle platform (active when modstitch.platform=moddevgradle) ──
    moddevgradle {
        prop("deps.neoforge") { neoForgeVersion = it }

        // Registers the default client + server run configurations that
        // VS Code tasks (":neoforge:runClient" etc.) relied on in the old setup.
        defaultRuns()

        configureNeoForge {
            parchment {
                minecraftVersion = findProperty("deps.parchment_mc") as? String ?: minecraft
                mappingsVersion = findProperty("deps.parchment") as? String ?: ""
            }
        }
    }

    // ── Mixin configuration ──
    mixin {
        // Modstitch's FletchingTable automatically inserts the mixin config file references
        // into fabric.mod.json ("mixins" array) and neoforge.mods.toml ([[mixins]]).
        // No manual manifest edits needed.
        addMixinsToModManifest = true

        // One unified mixin config covers both platforms.
        // Platform-specific mixin classes (MixinTitleScreen) live in the shared package;
        // loader-only mixins can be registered here with isLoom/isModDevGradle guards.
        configs.register("forgetmecrops")
    }
}

// ────────────────────────────────────────────────────────────
//  Stonecutter loader constants
//  These constants let the Stitcher comment processor know which
//  loader is active, so //? if fabric { … } blocks work correctly.
// ────────────────────────────────────────────────────────────
stonecutter {
    constants.match(loader, "fabric", "neoforge")
}

// Java 21 bytecode target — required for MC 1.20.6+.
// In Modstitch 0.8.x, javaTarget was removed from the modstitch{} extension;
// configure it directly on the compile tasks instead.
tasks.withType<JavaCompile> {
    options.release.set(21)
}

// Ensure Stonecutter preprocessing runs before Java compilation so
// //? if ... blocks are resolved into valid Java source files.
tasks.withType<JavaCompile> {
    dependsOn("stonecutterGenerate")
}

// ────────────────────────────────────────────────────────────
//  Version string for the built artifact
//  e.g.  0.15.0+1.21.11-fabric
// ────────────────────────────────────────────────────────────
version = "${property("mod_version")}+${minecraft}-${loader}"
base.archivesName.set("Forget-Me-Crops_${property("mod_version")}+${minecraft}-${loader}")

// ────────────────────────────────────────────────────────────
//  Dependencies
// ────────────────────────────────────────────────────────────
dependencies {
    // ── Fabric-only dependencies ──
    modstitch.loom {
        modstitchModImplementation("net.fabricmc.fabric-api:fabric-api:${property("deps.fabric_api")}")
        // YACL config library (Fabric flavour)
        modstitchModImplementation("dev.isxander:yet-another-config-lib:${property("deps.yacl")}-fabric") {
            exclude(group = "net.fabricmc.fabric-api")
        }
        // ModMenu — shows a "Config" button in the Fabric mods list
        modstitchModImplementation("com.terraformersmc:modmenu:${property("deps.modmenu")}")
    }

    // ── NeoForge-only dependencies ──
    modstitch.moddevgradle {
        // YACL config library (NeoForge flavour)
        modstitchModImplementation("dev.isxander:yet-another-config-lib:${property("deps.yacl")}-neoforge")
    }

    // ── Common test dependencies ──
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// ────────────────────────────────────────────────────────────
//  Maven publishing
//  Publishes the remapped production JAR (no -dev, no -sources)
//  to a local Maven directory so it can be consumed by other
//  Gradle projects or uploaded to an external Maven host.
// ────────────────────────────────────────────────────────────
publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            groupId = "com.forgetmecrops"
            artifactId = "forgetmecrops-${loader}"
            version = "${property("mod_version")}+${minecraft}"

            from(components["java"])

            pom {
                name.set("Forget-Me-Crops (${loader.replaceFirstChar { it.uppercase() }})")
                description.set("Automates crop harvesting for Fabric and NeoForge.")
                url.set("https://github.com/duelmonster/Forget-Me-Crops")
                licenses {
                    license {
                        name.set("CC0-1.0")
                        url.set("https://creativecommons.org/publicdomain/zero/1.0/")
                    }
                }
                scm {
                    connection.set("scm:git:git://github.com/duelmonster/Forget-Me-Crops.git")
                    url.set("https://github.com/duelmonster/Forget-Me-Crops")
                }
            }
        }
    }

    repositories {
        // Local Maven repository (always available, no token required)
        maven {
            name = "local"
            url = uri(rootProject.layout.buildDirectory.dir("maven-local"))
        }

        // Modrinth Maven (publish JARs as Maven artifacts for dependency use)
        val modrinthToken = System.getenv("MODRINTH_TOKEN")
        if (!modrinthToken.isNullOrBlank()) {
            maven {
                name = "Modrinth"
                url = uri("https://api.modrinth.com/maven")
                credentials {
                    username = "forgetmecrops"
                    password = modrinthToken
                }
            }
        }

        // GitHub Packages (optional — needs GITHUB_ACTOR + GITHUB_TOKEN env vars)
        val ghToken = System.getenv("GITHUB_TOKEN")
        val ghActor = System.getenv("GITHUB_ACTOR")
        if (!ghToken.isNullOrBlank() && !ghActor.isNullOrBlank()) {
            maven {
                name = "GitHubPackages"
                url = uri("https://maven.pkg.github.com/duelmonster/Forget-Me-Crops")
                credentials {
                    username = ghActor
                    password = ghToken
                }
            }
        }
    }
}

// ────────────────────────────────────────────────────────────
//  Modrinth + CurseForge publishing via mod-publish-plugin
//  Only applied when the publishing API keys are available.
//  Tasks do NOT run during a normal build; invoke them explicitly:
//    ./gradlew :versions/1.21.11-fabric:publishModrinth
//    ./gradlew :versions/1.21.11-neoforge:publishCurseForge
//    ./gradlew chiseledPublishAll   (publishes all nodes)
// ────────────────────────────────────────────────────────────

// Determine the production JAR task name for this node
val prodJarTask: String = if (isFabric) "remapJar" else "jar"

val modrinthToken = System.getenv("MODRINTH_TOKEN")
val curseForgeToken = System.getenv("CURSEFORGE_TOKEN")

if (!modrinthToken.isNullOrBlank() || !curseForgeToken.isNullOrBlank()) {
    apply(plugin = "me.modmuss50.mod-publish-plugin")

    @Suppress("UnstableApiUsage")
    configure<me.modmuss50.mpp.ModPublishExtension> {
        val modVer = property("mod_version") as String

        if (!modrinthToken.isNullOrBlank()) {
            modrinth {
                accessToken = modrinthToken
                projectId = findProperty("modrinth_project_id") as? String ?: ""
                minecraftVersions.add(minecraft)
                modLoaders.add(loader)
                displayName = "Forget-Me-Crops $modVer+$minecraft-$loader"
                version = "$modVer+$minecraft-$loader"
                type = me.modmuss50.mpp.ReleaseType.STABLE
                file = tasks.named<AbstractArchiveTask>(prodJarTask).map { it.archiveFile.get() }
                changelog = providers.fileContents(rootProject.layout.projectDirectory.file("CHANGELOG.md"))
                    .asText.orElse("")
                requires("P7dR8mSH") // YACL Modrinth project ID
            }
        }

        if (!curseForgeToken.isNullOrBlank()) {
            curseforge {
                accessToken = curseForgeToken
                projectId = findProperty("curseforge_project_id") as? String ?: ""
                minecraftVersions.add(minecraft)
                modLoaders.add(loader)
                displayName = "Forget-Me-Crops $modVer+$minecraft-$loader"
                version = "$modVer+$minecraft-$loader"
                type = me.modmuss50.mpp.ReleaseType.STABLE
                file = tasks.named<AbstractArchiveTask>(prodJarTask).map { it.archiveFile.get() }
                changelog = providers.fileContents(rootProject.layout.projectDirectory.file("CHANGELOG.md"))
                    .asText.orElse("")
                requires("yacl")
            }
        }
    }
}

// ────────────────────────────────────────────────────────────
//  Release-packaging task (kept for backward compatibility with
//  the CI release workflow — copies the final JAR to releases/)
// ────────────────────────────────────────────────────────────
tasks.register<Copy>("packageRelease") {
    group = "release"
    description = "Copies the remapped production JAR for this loader into releases/."
    dependsOn(prodJarTask)
    from(tasks.named<AbstractArchiveTask>(prodJarTask).map { it.archiveFile })
    into(rootProject.layout.projectDirectory.dir("releases"))
}
