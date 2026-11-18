import java.util.UUID

/**
 * NeoForge nodes for 1.21.1 (21.1.265), 1.21.4 (21.4.161), 1.21.8 (21.8.57),
 * and 1.21.11 (21.11.48). ModDevGradle, official mappings.
 * Every node compiles the chiseled tree, not the Fabric checkout in src/.
 * Cloth Config NeoForge is embedded on 1.21.1, matching the Forge screen.
 * Later nodes draw the screen themselves.
 */

buildscript {
    repositories {
        maven("https://maven.neoforged.net/releases")
        maven("https://maven.fabricmc.net/")
        mavenCentral()
        gradlePluginPortal()
    }
    dependencies {
        classpath("net.neoforged:moddev-gradle:2.0.148")
    }
}

data class NeoForgeNode(
    val neoVersion: String,
    val packMeta: String,
    val cloth: String?,
)

val modVersion = "1.0.0"
val releaseBuild = providers.gradleProperty("release").orNull == "true" ||
    gradle.startParameter.taskNames.any { it.substringAfterLast(':') == "releaseAll" }

fun quarkosUrl(name: String): String {
    val value = providers.gradleProperty("quarkos.$name").orNull?.trim().orEmpty()
    if (value.isEmpty()) {
        throw GradleException("Set quarkos.$name in gradle.properties")
    }
    return value
}

fun clothRange(version: String): String {
    val parts = version.split('.')
    return "[$version,${parts[0]}.${parts[1].toInt() + 1})"
}

fun legacyPack(format: Int): String = """
{
  "pack": {
    "description": "QuarkOS Capes",
    "pack_format": $format
  }
}
""".trimIndent() + "\n"

fun modernPack(major: Int): String = """
{
  "pack": {
    "description": "QuarkOS Capes",
    "pack_format": $major,
    "supported_formats": [64, 94],
    "min_format": [64, 0],
    "max_format": [94, 1]
  }
}
""".trimIndent() + "\n"

val minecraftVersion = project.name
val neoNode = when (minecraftVersion) {
    "1.21.1" -> NeoForgeNode("21.1.265", legacyPack(34), "15.0.140")
    "1.21.4" -> NeoForgeNode("21.4.161", legacyPack(46), null)
    "1.21.8" -> NeoForgeNode("21.8.57", legacyPack(64), null)
    "1.21.11" -> NeoForgeNode("21.11.48", modernPack(75), null)
    else -> null
}

if (neoNode == null) {
    tasks.configureEach {
        if (name != "build") {
            enabled = false
        }
    }
    tasks.named("build") {
        group = "build"
        description = "This NeoForge node has no Minecraft NeoForge build."
        notCompatibleWithConfigurationCache("Refuses a NeoForge jar for an unmapped Minecraft version.")
        setDependsOn(emptyList<Any>())
        enabled = true
        val reason = "No NeoForge coordinate is configured for Minecraft $minecraftVersion."
        doFirst {
            throw GradleException(reason)
        }
    }
} else {
    val node = neoNode
    apply(plugin = "net.neoforged.moddev")

    version = "$modVersion+$minecraftVersion"
    base.archivesName.set("quarkos_capes-neoforge")

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    val neoForge = extensions.getByType(net.neoforged.moddevgradle.dsl.NeoForgeExtension::class.java)
    neoForge.version = node.neoVersion
    // ModDevGradle recompiles Minecraft with the access transformer applied. Widening
    // Screen.addRenderableWidget leaves CreateWorldScreen's override protected on 1.21.1,
    // and javac then rejects the override. The runtime transformer does not have that check,
    // so the shipped cfg stays as it is and only this dev copy widens the override.
    val devAccessTransformer = layout.buildDirectory.file("generated/accesstransformer.cfg")
    val devAtFile = devAccessTransformer.get().asFile
    devAtFile.parentFile.mkdirs()
    val shippedAt = rootProject.file("src/main/resources/META-INF/accesstransformer.cfg").readText().trimEnd() + "\n"
    val createWorldWidget = if (minecraftVersion == "1.21.1") {
        "public net.minecraft.client.gui.screens.worldselection.CreateWorldScreen addRenderableWidget(Lnet/minecraft/client/gui/components/events/GuiEventListener;)Lnet/minecraft/client/gui/components/events/GuiEventListener;\n"
    } else {
        ""
    }
    devAtFile.writeText(shippedAt + createWorldWidget)
    neoForge.setAccessTransformers(devAtFile)

    val sourceSets = extensions.getByType(org.gradle.api.tasks.SourceSetContainer::class.java)
    val mainSource = sourceSets.named("main")
    val chiseledMain = layout.buildDirectory.dir("generated/stonecutter/main")
    val devFlagsDir = layout.buildDirectory.dir("generated/devflags")
    val writeDevFlags = tasks.register("writeDevFlags") {
        val out = devFlagsDir.map { it.file("dev/quarkos/capes/DevFlags.java") }
        outputs.file(out)
        inputs.property("release", releaseBuild)
        doLast {
            val file = out.get().asFile
            file.parentFile.mkdirs()
            val enabled = !releaseBuild
            file.writeText(
                """
                package dev.quarkos.capes;
                public final class DevFlags {
                    public static final boolean HARNESS = $enabled;
                    public static final boolean CATALOG = $enabled;
                    private DevFlags() {}
                }
                """.trimIndent() + "\n"
            )
        }
    }
    gradle.projectsEvaluated {
        val main = mainSource.get()
        main.java.setSrcDirs(listOf(chiseledMain.get().dir("java").asFile, devFlagsDir.get().asFile))
        main.resources.setSrcDirs(listOf(chiseledMain.get().dir("resources").asFile))
    }
    tasks.named("compileJava") {
        dependsOn("stonecutterGenerate")
        dependsOn(writeDevFlags)
    }
    tasks.named("processResources") { dependsOn("stonecutterGenerate") }

    val runDir = rootProject.layout.projectDirectory.dir("run/neoforge/$minecraftVersion")
    neoForge.runs.register("client") {
        client()
        gameDirectory.set(runDir)
        logLevel.set(org.slf4j.event.Level.INFO)
    }
    neoForge.mods.register("quarkos_capes") {
        sourceSet(mainSource.get())
    }
    neoForge.addModdingDependenciesTo(sourceSets.getByName("test"))

    repositories {
        maven("https://maven.shedaniel.me/") { name = "Shedaniel" }
        mavenCentral()
    }

    fun embed(group: String, artifact: String, artifactVersion: String, range: String) {
        val compileDep = dependencies.create("$group:$artifact:$artifactVersion") as org.gradle.api.artifacts.ModuleDependency
        compileDep.isTransitive = false
        dependencies.add("implementation", compileDep)
        val jarDep = dependencies.create("$group:$artifact") as org.gradle.api.artifacts.ExternalModuleDependency
        jarDep.version {
            strictly(range)
            prefer(artifactVersion)
        }
        dependencies.add("jarJar", jarDep)
    }
    embed("com.squareup.okhttp3", "okhttp", "3.14.9", "[3.14.9,3.15)")
    embed("com.squareup.okio", "okio", "1.17.5", "[1.17.5,1.18)")
    val clothVersion = node.cloth
    if (clothVersion != null) {
        embed("me.shedaniel.cloth", "cloth-config-neoforge", clothVersion, clothRange(clothVersion))
    }

    dependencies {
        add("testImplementation", platform("org.junit:junit-bom:5.13.4"))
        add("testImplementation", "org.junit.jupiter:junit-jupiter")
        add("testRuntimeOnly", "org.junit.platform:junit-platform-launcher")
    }

    val neoPack = layout.buildDirectory.dir("generated/neoforge-pack")
    val packMetadata = node.packMeta
    val writeNeoPack = tasks.register("writeNeoPack") {
        outputs.dir(neoPack)
        doLast {
            val file = neoPack.get().asFile.resolve("pack.mcmeta")
            file.parentFile.mkdirs()
            file.writeText(packMetadata)
        }
    }

    tasks.named<ProcessResources>("processResources") {
        dependsOn(writeNeoPack)
        val clothDependency = if (node.cloth != null) {
            """
[[dependencies.quarkos_capes]]
modId = "cloth_config"
type = "required"
versionRange = "${clothRange(node.cloth)}"
ordering = "AFTER"
side = "CLIENT"
"""
        } else {
            ""
        }
        val clothNote = if (node.cloth != null) {
            " This jar bundles Cloth Config by shedaniel (LGPL-3.0-only) with Jar-in-Jar."
        } else {
            ""
        }
        val props = mapOf(
            "version" to "$modVersion+$minecraftVersion",
            "neo_dep" to node.neoVersion,
            "minecraft" to minecraftVersion,
            "cloth_dependency" to clothDependency,
            "cloth_note" to clothNote,
            "homepage" to quarkosUrl("homepage"),
            "source" to quarkosUrl("source"),
            "issues" to quarkosUrl("issues"),
        )
        inputs.property("neo_dep", node.neoVersion)
        inputs.property("minecraft", minecraftVersion)
        inputs.property("homepage", props.getValue("homepage"))
        inputs.property("source", props.getValue("source"))
        inputs.property("issues", props.getValue("issues"))
        inputs.property("cloth_note", clothNote)
        inputs.property("releaseBuild", releaseBuild)
        filesMatching("META-INF/neoforge.mods.toml") {
            expand(props)
        }
        val legacyCapeMixin = minecraftVersion != "1.21.11"
        filesMatching("quarkos_capes.mixins.json") {
            filter { line ->
                var next = if (legacyCapeMixin) line.replace("ElytraTextureMixin\"", "ElytraTextureMixin121\"") else line
                if (releaseBuild) {
                    next = next.replace(", \"ShotCameraMixin\", \"ShotSkinMixin\"", "")
                }
                next
            }
        }
        exclude("fabric.mod.json")
        exclude("META-INF/mods.toml")
        exclude("**/*.accesswidener")
        from(neoPack)
        from(rootProject.file("LICENSE")) {
            rename { "LICENSE_quarkos_capes" }
        }
    }

    tasks.named<Jar>("jar") {
        doFirst {
            delete(layout.buildDirectory.dir("classes/java/main/META-INF/jarjar"))
        }
        manifest {
            attributes["MixinConfigs"] = "quarkos_capes.mixins.json"
        }
        if (releaseBuild) {
            exclude("dev/quarkos/capes/client/harness/**")
            exclude("dev/quarkos/capes/client/net/LabyCatalog.class")
            exclude("dev/quarkos/capes/client/net/LabyCatalog\$*.class")
            exclude("dev/quarkos/capes/client/net/LabyBrowse.class")
            exclude("dev/quarkos/capes/client/net/LabyBrowse\$*.class")
            exclude("dev/quarkos/capes/client/render/LabyWear.class")
            exclude("dev/quarkos/capes/client/mixin/ShotCameraMixin.class")
            exclude("dev/quarkos/capes/client/mixin/ShotSkinMixin.class")
        }
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }

    tasks.withType<JavaExec>().configureEach {
        val labyShots = providers.gradleProperty("labyShots").orNull == "true"
        val cloakOnly = providers.gradleProperty("cloakOnly").orNull == "true"
        val harness = providers.gradleProperty("harness").orNull == "true"
        val localCaptures = rootProject.file("build/captures/neoforge/$minecraftVersion").absolutePath
        systemProperty("quarkos_capes.captures", localCaptures)
        systemProperty("quarkos_capes.loader", "neoforge")
        systemProperty("quarkos_capes.timeoutScale", "8")
        if (harness || cloakOnly || labyShots) {
            systemProperty("quarkos_capes.harness", "true")
        }
        if (labyShots) {
            systemProperty("quarkos_capes.labyShots", "true")
            systemProperty("CAPES_DEV_CATALOG", "true")
            environment("CAPES_DEV_CATALOG", "true")
        }
        if (cloakOnly) {
            systemProperty("quarkos_capes.cloakOnly", "true")
        }
        for (key in listOf("only", "guiScale", "width", "height", "serverUrl", "linkCode")) {
            providers.gradleProperty(key).orNull?.let { systemProperty("quarkos_capes.$key", it) }
        }
        providers.gradleProperty("shots").orNull?.let {
            systemProperty("quarkos_capes.captures", it)
            systemProperty("quarkos_capes.compare", it)
        }
        systemProperty("quarkos_capes.serverUrl", providers.gradleProperty("serverUrl").orElse("http://127.0.0.1:8787").get())
        if (providers.gradleProperty("only").orNull in listOf("flows", "boot", "ui")) {
            systemProperty("quarkos_capes.harness", "true")
        }
        for (key in listOf("CAPES_DEV_AUTH", "CAPES_DEV_AUTH_SECRET")) {
            System.getenv(key)?.let { environment(key, it) }
        }
        environment("LIBGL_ALWAYS_SOFTWARE", "1")
        environment("MESA_GL_VERSION_OVERRIDE", "3.3")
        val display = providers.gradleProperty("display").orNull ?: System.getenv("DISPLAY")
        val xauth = providers.gradleProperty("xauth").orNull ?: System.getenv("XAUTHORITY")
        if (!display.isNullOrBlank()) {
            environment("DISPLAY", display)
        }
        if (!xauth.isNullOrBlank()) {
            environment("XAUTHORITY", xauth)
        }
        if (name == "runClient") {
            dependsOn("jarJar")
            val flows = providers.gradleProperty("only").orNull == "flows"
            val flowUser = providers.gradleProperty("username").orElse("CapeFlow").get()
            val flowUuid = providers.gradleProperty("uuid").orNull
                ?: UUID.nameUUIDFromBytes("OfflinePlayer:$flowUser".toByteArray()).toString()
            doFirst {
                if (flows) {
                    val updated = mutableListOf<String>()
                    var skip = false
                    for (arg in args) {
                        if (skip) { skip = false; continue }
                        if (arg == "--username" || arg == "--uuid") { skip = true; continue }
                        updated.add(arg)
                    }
                    updated.addAll(listOf("--username", flowUser, "--uuid", flowUuid))
                    args = updated
                }
                val eula = workingDir.resolve("eula.txt")
                eula.parentFile?.mkdirs()
                eula.writeText("eula=true\n")
                // Older NeoForge dev launches do not put plain Jar-in-Jar libraries on the module path.
                val dest = layout.buildDirectory.dir("classes/java/main/META-INF/jarjar").get().asFile
                delete(dest)
                dest.mkdirs()
                copy {
                    from(layout.buildDirectory.dir("generated/jarJar/META-INF/jarjar"))
                    into(dest)
                }
            }
        }
    }
}
apply(from = rootProject.file("gradle/strip-module-info.gradle.kts"))
apply(from = rootProject.file("gradle/release.gradle.kts"))
