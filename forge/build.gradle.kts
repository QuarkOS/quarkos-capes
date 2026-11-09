/**
 * Forge nodes for 1.21.1 (52.1.20) and 1.21.11 (61.2.4).
 * Official mappings, so the jar is not reobfuscated.
 * Every node compiles the chiseled tree, not the Fabric checkout in src/.
 */

buildscript {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://maven.minecraftforge.net/")
        maven("https://maven.fabricmc.net/")
    }
    dependencies {
        classpath("net.minecraftforge.gradle:net.minecraftforge.gradle.gradle.plugin:7.0.40")
    }
}

data class EmbeddedJar(val group: String, val artifact: String, val version: String, val range: String, val fileName: String)

data class ForgeNode(
    val forgeCoordinate: String,
    val forgeDep: String,
    val packMeta: String,
)

fun legacyPack(format: Int): String = """
{
  "pack": {
    "description": "QuarkOS Capes",
    "pack_format": $format
  }
}
""".trimIndent() + "\n"

fun modernPack(major: Int, minor: Int): String = """
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
val forgeNode = when (minecraftVersion) {
    "1.21.1" -> ForgeNode("1.21.1-52.1.20", "52.1.20", legacyPack(34))
    "1.21.11" -> ForgeNode("1.21.11-61.2.4", "61.2.4", modernPack(75, 0))
    else -> null
}

if (forgeNode == null) {
    tasks.configureEach {
        if (name != "build") {
            enabled = false
        }
    }
    tasks.named("build") {
        group = "build"
        description = "This Forge node has no Minecraft Forge build."
        notCompatibleWithConfigurationCache("Refuses a Forge jar for an unmapped Minecraft version.")
        setDependsOn(emptyList<Any>())
        enabled = true
        val reason = "No Forge coordinate is configured for Minecraft $minecraftVersion."
        doFirst {
            throw GradleException(reason)
        }
    }
} else {
    val node = forgeNode
    apply(plugin = "java")
    apply(plugin = "net.minecraftforge.gradle")

    version = "0.1.0+$minecraftVersion"
    base.archivesName.set("quarkos_capes-forge")

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    val minecraftExt = extensions.getByName("minecraft")
    val fg = extensions.getByName("fg")
    val forgeMinecraft = minecraftExt as net.minecraftforge.gradle.MinecraftExtensionForProject
    forgeMinecraft.mappings("official", minecraftVersion)
    val accessTransformer = rootProject.file("src/main/resources/META-INF/accesstransformer.cfg")
    forgeMinecraft.accessTransformers.from(accessTransformer)
    val runs = (minecraftExt as groovy.lang.GroovyObject).getProperty("runs")
        as org.gradle.api.NamedDomainObjectContainer<net.minecraftforge.gradle.SlimeLauncherOptions>
    val runDir = rootProject.layout.projectDirectory.dir("run/forge/$minecraftVersion")
    runs.configureEach {
        workingDir.set(runDir)
        systemProperty("forge.logging.console.level", "info")
        args("--mixin.config=quarkos_capes.mixins.json")
    }
    val sourceSets = extensions.getByType(org.gradle.api.tasks.SourceSetContainer::class.java)
    val mainSource = sourceSets.named("main")
    val chiseledMain = layout.buildDirectory.dir("generated/stonecutter/main")
    gradle.projectsEvaluated {
        val main = mainSource.get()
        main.java.setSrcDirs(listOf(chiseledMain.get().dir("java").asFile))
        main.resources.setSrcDirs(listOf(chiseledMain.get().dir("resources").asFile))
        main.output.setResourcesDir(main.java.destinationDirectory)
    }
    tasks.named("compileJava") { dependsOn("stonecutterGenerate") }
    tasks.named("processResources") { dependsOn("stonecutterGenerate") }
    runs.register("client") {
        mods {
            register("quarkos_capes") {
                source(mainSource.get())
            }
        }
    }

    repositories {
        forgeMinecraft.mavenizer(this)
        maven((fg as net.minecraftforge.gradle.ForgeGradleExtension).forgeMaven)
        maven((fg as net.minecraftforge.gradle.ForgeGradleExtension).minecraftLibsMaven)
        maven("https://maven.minecraftforge.net/")
        maven("https://maven.shedaniel.me/") { name = "Shedaniel" }
        mavenCentral()
    }

    val jarjarDir = layout.buildDirectory.dir("generated/jarjar")
    val embedJars = configurations.create("embedJars") {
        isCanBeResolved = true
        isCanBeConsumed = false
        isTransitive = false
    }
    fun embed(notation: String) {
        val dependency = dependencies.create(notation) as org.gradle.api.artifacts.ModuleDependency
        dependency.isTransitive = false
        dependencies.add("implementation", dependency)
        dependencies.add("embedJars", dependency.copy())
    }
    embed("com.squareup.okhttp3:okhttp:3.14.9")
    embed("com.squareup.okio:okio:1.17.5")
    if (minecraftVersion == "1.21.1") {
        embed("me.shedaniel.cloth:cloth-config-forge:15.0.140")
    }
    val embeddedJars = buildList {
        add(EmbeddedJar("com.squareup.okhttp3", "okhttp", "3.14.9", "[3.14.9,3.15)", "okhttp-3.14.9.jar"))
        add(EmbeddedJar("com.squareup.okio", "okio", "1.17.5", "[1.17.5,1.18)", "okio-1.17.5.jar"))
        if (minecraftVersion == "1.21.1") {
            add(EmbeddedJar("me.shedaniel.cloth", "cloth-config-forge", "15.0.140", "[15.0.140,15.1)", "cloth-config-forge-15.0.140-forge.jar"))
        }
    }
    val embeddedNames = embeddedJars.associate { it.artifact to it.fileName }
    val embeddedMetadata = embeddedJars.joinToString(",\n") { item ->
        """
                    {
                      "identifier": {
                        "group": "${item.group}",
                        "artifact": "${item.artifact}"
                      },
                      "version": {
                        "range": "${item.range}",
                        "artifactVersion": "${item.version}"
                      },
                      "path": "META-INF/jarjar/${item.fileName}",
                      "isObfuscated": false
                    }
                """.trimIndent()
    }.let { "{\n  \"jars\": [\n$it\n  ]\n}\n" }
    val prepareJarJar = tasks.register("prepareJarJar") {
        inputs.files(embedJars)
        inputs.property("metadata", embeddedMetadata)
        outputs.dir(jarjarDir)
        doLast {
            val dir = jarjarDir.get().asFile
            dir.mkdirs()
            val files = inputs.files.files
            for ((artifact, fileName) in embeddedNames) {
                files.single { it.name.startsWith(artifact + "-") }.copyTo(dir.resolve(fileName), overwrite = true)
            }
            dir.resolve("metadata.json").writeText(embeddedMetadata)
        }
    }

    dependencies {
        add("implementation", forgeMinecraft.dependency("net.minecraftforge:forge:${node.forgeCoordinate}"))
        add("annotationProcessor", "org.spongepowered:mixin:0.8.7:processor")
        add("testImplementation", platform("org.junit:junit-bom:5.13.4"))
        add("testImplementation", "org.junit.jupiter:junit-jupiter")
        add("testRuntimeOnly", "org.junit.platform:junit-platform-launcher")
    }

    val forgePack = layout.buildDirectory.dir("generated/forge-pack")
    val packMetadata = node.packMeta
    val writeForgePack = tasks.register("writeForgePack") {
        outputs.dir(forgePack)
        doLast {
            val file = forgePack.get().asFile.resolve("pack.mcmeta")
            file.parentFile.mkdirs()
            file.writeText(packMetadata)
        }
    }

    tasks.named<ProcessResources>("processResources") {
        dependsOn(writeForgePack)
        val clothDependency = if (minecraftVersion == "1.21.1") {
            """
[[dependencies.quarkos_capes]]
modId = "cloth_config"
mandatory = true
versionRange = "[15.0.140,15.1)"
ordering = "AFTER"
side = "CLIENT"
"""
        } else {
            ""
        }
        val props = mapOf(
            "version" to "0.1.0+$minecraftVersion",
            "forge_dep" to node.forgeDep,
            "forge_spec" to node.forgeDep.substringBefore('.'),
            "minecraft" to minecraftVersion,
            "cloth_dependency" to clothDependency,
        )
        inputs.property("forge_dep", node.forgeDep)
        inputs.property("minecraft", minecraftVersion)
        filesMatching("META-INF/mods.toml") {
            expand(props)
        }
        val legacyCapeMixin = minecraftVersion != "1.21.11"
        filesMatching("quarkos_capes.mixins.json") {
            if (legacyCapeMixin) {
                filter { line -> line.replace("ElytraTextureMixin\"", "ElytraTextureMixin121\"") }
            }
        }
        exclude("fabric.mod.json")
        exclude("**/*.accesswidener")
        from(forgePack)
        from(rootProject.file("LICENSE")) {
            rename { "LICENSE_quarkos_capes" }
        }
    }

    tasks.named<Jar>("jar") {
        dependsOn(prepareJarJar)
        from(jarjarDir) {
            into("META-INF/jarjar")
        }
        manifest {
            attributes["MixinConfigs"] = "quarkos_capes.mixins.json"
        }
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }

    tasks.withType<JavaExec>().configureEach {
        val labyShots = providers.gradleProperty("labyShots").orNull == "true"
        val cloakOnly = providers.gradleProperty("cloakOnly").orNull == "true"
        val harness = providers.gradleProperty("harness").orNull == "true"
        val media = "/cursor/stores/bc-97e49978-8cdc-55b6-9065-d58f273ae502/media/capes/forge/$minecraftVersion"
        val captures = if (labyShots) {
            "/cursor/stores/bc-97e49978-8cdc-55b6-9065-d58f273ae502/media/capes/labymod/forge/$minecraftVersion"
        } else if (harness || cloakOnly) {
            media
        } else {
            rootProject.file("build/captures/forge/$minecraftVersion").absolutePath
        }
        systemProperty("quarkos_capes.captures", captures)
        systemProperty("quarkos_capes.loader", "forge")
        systemProperty("quarkos_capes.timeoutScale", "8")
        if (harness || cloakOnly || labyShots) {
            systemProperty("quarkos_capes.harness", "true")
        }
        if (labyShots) {
            systemProperty("quarkos_capes.labyShots", "true")
            systemProperty("CAPES_DEV_CATALOG", "true")
            environment("CAPES_DEV_CATALOG", "true")
        }
        val labyCompare = providers.gradleProperty("labyCompare").orNull == "true"
        if (labyCompare) {
            systemProperty("quarkos_capes.harness", "true")
            systemProperty("quarkos_capes.labyCompare", "true")
            systemProperty("CAPES_DEV_CATALOG", "true")
            environment("CAPES_DEV_CATALOG", "true")
            systemProperty(
                "quarkos_capes.captures",
                rootProject.file("build/laby-compare-raw/forge/$minecraftVersion").absolutePath
            )
            systemProperty(
                "quarkos_capes.compare",
                "/cursor/stores/bc-97e49978-8cdc-55b6-9065-d58f273ae502/media/capes/labymod/compare/forge/$minecraftVersion"
            )
        }
        if (cloakOnly) {
            systemProperty("quarkos_capes.cloakOnly", "true")
        }
        providers.gradleProperty("shots").orNull?.let {
            systemProperty("quarkos_capes.captures", it)
            systemProperty("quarkos_capes.compare", it)
        }
        for (key in listOf("only", "guiScale", "width", "height")) {
            providers.gradleProperty(key).orNull?.let { systemProperty("quarkos_capes.$key", it) }
        }
        for (key in listOf("QUARKOS_CAPES_BENCH", "QUARKOS_CAPES_BENCH_PLAYERS")) {
            providers.gradleProperty(key).orNull?.let { systemProperty(key, it) }
        }
        environment("LIBGL_ALWAYS_SOFTWARE", "1")
        val display = providers.gradleProperty("display").orNull ?: System.getenv("DISPLAY")
        val xauth = providers.gradleProperty("xauth").orNull ?: System.getenv("XAUTHORITY")
        if (!display.isNullOrBlank()) {
            environment("DISPLAY", display)
        }
        if (!xauth.isNullOrBlank()) {
            environment("XAUTHORITY", xauth)
        }
        if (name == "runClient") {
            doFirst {
                val eula = workingDir.resolve("eula.txt")
                eula.parentFile?.mkdirs()
                eula.writeText("eula=true\n")
            }
        }
    }
}
