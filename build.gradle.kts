// 🏗️ Central build script: runs once for every Stonecutter version node (1.21.11-fabric,
// 1.21.11-neoforge, etc.). Modstitch reads the node's gradle.properties to decide which
// platform toolchain (Loom vs ModDevGradle) to activate. One script to rule them all.

import java.io.DataInputStream
import java.util.zip.ZipFile

plugins {
    // Modstitch: the unified build plugin that abstracts Fabric Loom and NeoForge MDG.
    // Only one platform is "active" per node — determined by modstitch.platform in
    // the node's versioned gradle.properties.
    id("dev.isxander.modstitch.base") version "0.8.5"
    // mod-publish-plugin: Modrinth + CurseForge publishing (applied conditionally below)
    id("me.modmuss50.mod-publish-plugin") version "2.2.0" apply false
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
val javaRelease = if (minecraft.startsWith("26.")) 25 else 21
val javaToolchains = extensions.getByType(org.gradle.jvm.toolchain.JavaToolchainService::class.java)

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
        modAuthor = "DuelMonster"

        // Extra tokens available inside src/main/templates/
        replacementProperties.putAll(mapOf(
            "mod_id"                  to "forgetmecrops",
            "mod_name"                to "Forget-Me-Crops",
            "mod_description"         to "Automates crop harvesting for both Fabric and NeoForge.",
            "mod_license"             to "MIT",
            "mod_author"              to "DuelMonster",
            "mod_homepage"            to "https://github.com/duelmonster/Forget-Me-Crops",
            "mod_issue_tracker"       to "https://github.com/duelmonster/Forget-Me-Crops/issues",
            "minecraft_version_range" to "[1.21.11,)",
            "cloth_config_version_range" to "[21.11.153,)",
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
                ideConfigGenerated(false)
                runDir("runs/client")
                programArgs("--username", "fmc_dev", "--width", "1960", "--height", "1080")
            }
            runs.named("server") {
                setConfigName("Fabric Server")
                ideConfigGenerated(false)
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

        // Registers the default client + server run configurations.
        defaultRuns()

        configureNeoForge {
            // Match the Fabric layout so both loaders use the same run-dir convention.
            runs {
                named("client") {
                    gameDirectory = project.file("runs/client")
                    programArgument("--username")
                    programArgument("fmc_dev")
                    programArgument("--width")
                    programArgument("1960")
                    programArgument("--height")
                    programArgument("1080")
                }
                named("server") {
                    gameDirectory = project.file("runs/server")
                }
            }
            val parchmentMc = findProperty("deps.parchment_mc") as? String
            val parchmentMappings = findProperty("deps.parchment") as? String
            if (!parchmentMc.isNullOrBlank() && !parchmentMappings.isNullOrBlank()) {
                parchment {
                    minecraftVersion = parchmentMc
                    mappingsVersion = parchmentMappings
                }
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

// Java bytecode target per release line.
// 1.21.x stays on Java 21; 26.x requires Java 25.
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(javaRelease))
    }
}

tasks.withType<JavaCompile> {
    options.release.set(javaRelease)
}

// Keep game runtime JVM aligned with the MC line:
// - 1.21.x nodes launch with Java 21
// - 26.x nodes launch with Java 25
// Gradle itself may still run on Java 25 for plugin compatibility.
tasks.withType<JavaExec>().configureEach {
    if (name.startsWith("run")) {
        javaLauncher.set(javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(javaRelease))
        })
    }
}

// Verify that the built production jar uses the expected class file major version:
// Java 21 -> 65, Java 25 -> 69.
tasks.register("verifyJarBytecode") {
    group = "verification"
    description = "Verifies that production jar bytecode matches the expected Java target for this node."

    val productionJarTaskName = if (tasks.findByName("remapJar") != null) "remapJar" else "jar"
    dependsOn(productionJarTaskName)

    doLast {
        val jarTask = tasks.named<org.gradle.jvm.tasks.Jar>(productionJarTaskName).get()
        val jarFile = jarTask.archiveFile.get().asFile
        check(jarFile.exists()) { "Expected production jar not found: ${jarFile.absolutePath}" }

        val expectedMajor = javaRelease + 44
        var checkedClassCount = 0

        ZipFile(jarFile).use { zip ->
            val entries = zip.entries().asSequence().filter { !it.isDirectory && it.name.endsWith(".class") }
            entries.forEach { entry ->
                zip.getInputStream(entry).use { input ->
                    val data = DataInputStream(input)
                    val magic = data.readInt()
                    check(magic == 0xCAFEBABE.toInt()) { "Invalid class header in ${entry.name} (${jarFile.name})" }
                    data.readUnsignedShort() // minor version
                    val major = data.readUnsignedShort()
                    check(major == expectedMajor) {
                        "Unexpected class file major version in ${entry.name}: got $major, expected $expectedMajor (${jarFile.name})"
                    }
                    checkedClassCount++
                }
            }
        }

        check(checkedClassCount > 0) { "No .class files found in ${jarFile.name}" }
        logger.lifecycle("Verified ${jarFile.name}: $checkedClassCount classes at major version $expectedMajor")
    }
}

// Ensure Stonecutter preprocessing runs before Java compilation so
// //? if ... blocks are resolved into valid Java source files.
tasks.withType<JavaCompile> {
    dependsOn("stonecutterGenerate")
}

// Compatibility alias: allows `:node:compile` to target main-source compilation
// without Gradle's name-abbreviation ambiguity (`compileJava` vs `compileTestJava`).
if (tasks.findByName("compile") == null) {
    tasks.register("compile") {
        group = "build"
        description = "Compiles main Java sources (alias of compileJava)."
        dependsOn("compileJava")
    }
}

// ────────────────────────────────────────────────────────────
//  Task: copyRelatedMods (copy dependency mods to run directories)
//  This copies jars from related_mods/{loader}/ into runs/client/mods
//  and runs/server/mods so that the development environment has the
//  required mod dependencies available.
// ────────────────────────────────────────────────────────────
tasks.register("copyRelatedMods") {
    group = "run"
    description = "Copy related_mods jars to run directory mods folders."

    doLast {
        // Use the actual root project's directory structure.
        val actualRoot = rootProject.projectDir
        val nodeVersionDir = project.projectDir // e.g., versions/1.21.11-fabric

        // Extract the loader name and MC version from the project name (e.g., "1.21.11-fabric")
        val projectLoader = project.name.substringAfterLast("-") // e.g., "fabric"
        val projectVersion = project.name.substringBeforeLast("-") // e.g., "1.21.11"
        val loaderModsDir = File(actualRoot, "related_mods/$projectLoader/$projectVersion")

        if (loaderModsDir.exists() && loaderModsDir.isDirectory) {
            val clientModsDir = File(nodeVersionDir, "runs/client/mods")
            val serverModsDir = File(nodeVersionDir, "runs/server/mods")

            // Ensure directories exist
            clientModsDir.mkdirs()
            serverModsDir.mkdirs()

            // Copy all jars from the loader-specific related_mods directory to both run directories
            val jarFiles = loaderModsDir.listFiles { file: File -> file.isFile && file.extension == "jar" }
            jarFiles?.forEach { jar ->
                jar.copyTo(File(clientModsDir, jar.name), overwrite = true)
                jar.copyTo(File(serverModsDir, jar.name), overwrite = true)
            }
        }
    }
}

// Configure dependencies after all tasks are created (delayed configuration)
afterEvaluate {
    tasks.matching { it.name.matches(Regex("run.*")) }.configureEach {
        dependsOn("copyRelatedMods")
    }
}

// Prevent ModDevGradle from auto-writing NeoForge launch entries into .vscode/launch.json.
// Use configureEach so the rule applies even if the task is registered after project evaluation.
tasks.configureEach {
    if (name == "neoForgeIdeSync") {
        enabled = false
    }
}

if (isNeoForge) {
    fun sanitizeVscodeLaunchJsonFile() {
        val launchFile = rootProject.file(".vscode/launch.json")
        if (!launchFile.exists()) return

        try {
            val original = launchFile.readText()
            val parsed = groovy.json.JsonSlurper().parseText(original)
            val root = parsed as? MutableMap<*, *> ?: return
            val configurations = root["configurations"] as? List<*> ?: return

            val filtered = configurations.filterNot { entry ->
                val mapEntry = entry as? Map<*, *> ?: return@filterNot false
                val name = mapEntry["name"] as? String ?: return@filterNot false
                name.startsWith("NeoForge ")
            }

            if (filtered.size != configurations.size) {
                val mutableRoot = root.toMutableMap()
                mutableRoot["configurations"] = filtered
                val cleaned = groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(mutableRoot))
                launchFile.writeText(cleaned + System.lineSeparator())
            }
        } catch (_: Exception) {
            // Never fail a build because of launch.json sanitization.
        }
    }

    tasks.register("sanitizeVscodeLaunchJson") {
        group = "ide"
        description = "Removes auto-generated NeoForge launch profiles from .vscode/launch.json."

        doLast {
            sanitizeVscodeLaunchJsonFile()
        }
    }

    tasks.matching {
        it.name == "neoForgeIdeSync" ||
        it.name == "prepareClientRun" ||
        it.name == "prepareServerRun"
    }.configureEach {
        finalizedBy("sanitizeVscodeLaunchJson")
    }

    gradle.buildFinished {
        sanitizeVscodeLaunchJsonFile()
    }
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
        // Cloth Config library (Fabric flavour)
        modstitchModImplementation("me.shedaniel.cloth:cloth-config-fabric:${property("deps.cloth_config")}") {
            exclude(group = "net.fabricmc.fabric-api")
        }
        // ModMenu — shows a "Config" button in the Fabric mods list
        modstitchModImplementation("com.terraformersmc:modmenu:${property("deps.modmenu")}")
    }

    // ── NeoForge-only dependencies ──
    modstitch.moddevgradle {
        // Cloth Config library (NeoForge flavour)
        modstitchModImplementation("me.shedaniel.cloth:cloth-config-neoforge:${property("deps.cloth_config")}")
    }

    // ── Common test dependencies ──
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")
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
                        name.set("MIT")
                        url.set("https://opensource.org/licenses/MIT")
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

// Determine the production JAR task name for this node.
// Older Fabric Loom nodes expose "remapJar" as the final artifact; newer ones
// (and all NeoForge nodes) use plain "jar". Check at configuration time so that
// both old and new Loom versions are handled correctly.
val prodJarTask: String = if (tasks.findByName("remapJar") != null) "remapJar" else "jar"

val modrinthToken = System.getenv("MODRINTH_TOKEN")
val curseForgeToken = System.getenv("CURSEFORGE_TOKEN")

if (!modrinthToken.isNullOrBlank() || !curseForgeToken.isNullOrBlank()) {
    apply(plugin = "me.modmuss50.mod-publish-plugin")

    @Suppress("UnstableApiUsage")
    configure<me.modmuss50.mpp.ModPublishExtension> {
        val modVer = property("mod_version") as String

        if (!modrinthToken.isNullOrBlank()) {
            modrinth {
                accessToken.set(modrinthToken)
                projectId.set(findProperty("modrinth_project_id") as? String ?: "")
                minecraftVersions.add(minecraft)
                modLoaders.add(loader)
                displayName.set("Forget-Me-Crops $modVer+$minecraft-$loader")
                version.set("$modVer+$minecraft-$loader")
                type.set(me.modmuss50.mpp.ReleaseType.STABLE)
                file.set(tasks.named<AbstractArchiveTask>(prodJarTask).map { it.archiveFile.get() })
                changelog.set(
                    providers.fileContents(rootProject.layout.projectDirectory.file("CHANGELOG.md"))
                        .asText.orElse("")
                )
                requires("cloth-config")
            }
        }

        if (!curseForgeToken.isNullOrBlank()) {
            curseforge {
                accessToken.set(curseForgeToken)
                projectId.set(findProperty("curseforge_project_id") as? String ?: "")
                minecraftVersions.add(minecraft)
                modLoaders.add(loader)
                // CurseForge now requires an environment selection in addition to version/loader.
                client.set(true)
                server.set(true)
                displayName.set("Forget-Me-Crops $modVer+$minecraft-$loader")
                version.set("$modVer+$minecraft-$loader")
                type.set(me.modmuss50.mpp.ReleaseType.STABLE)
                file.set(tasks.named<AbstractArchiveTask>(prodJarTask).map { it.archiveFile.get() })
                changelog.set(
                    providers.fileContents(rootProject.layout.projectDirectory.file("CHANGELOG.md"))
                        .asText.orElse("")
                )
                requires("cloth-config")
            }
        }
    }
}

// ────────────────────────────────────────────────────────────
//  Release-packaging task (kept for backward compatibility with
//  the CI release workflow — copies the final JAR to releases/)
// ────────────────────────────────────────────────────────────

// Register cleanReleases on the root project once (the first node to configure
// creates it; subsequent nodes simply look it up). This guarantees it runs
// exactly once — before any node copies its JAR into releases/.
val cleanReleasesTask = if (rootProject.tasks.findByName("cleanReleases") == null) {
    rootProject.tasks.register("cleanReleases") {
        group = "release"
        description = "Deletes all *.jar files from releases/ before packaging new ones."
        doLast {
            val releasesDir = rootProject.layout.projectDirectory.dir("releases").asFile
            releasesDir.listFiles { f: File -> f.isFile && f.extension == "jar" }
                ?.forEach { it.delete() }
        }
    }
} else {
    rootProject.tasks.named("cleanReleases")
}

tasks.register<Copy>("packageRelease") {
    group = "release"
    description = "Copies the remapped production JAR for this loader into releases/."
    dependsOn(prodJarTask, cleanReleasesTask)
    from(tasks.named<AbstractArchiveTask>(prodJarTask).map { it.archiveFile })
    into(rootProject.layout.projectDirectory.dir("releases"))
}

tasks.named("build") {
    finalizedBy("packageRelease")
}
