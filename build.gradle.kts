import java.nio.file.Files
import java.util.UUID
import net.fabricmc.loom.task.prod.ClientProductionRunTask

plugins {
    id("dev.kikugie.loom-back-compat")
}

// Stonecutter nodes share a coordinate space; setting `group` here makes the version nodes collide.
version = "${property("mod.version")}+${sc.current.version}"
base.archivesName = "quarkos_capes-fabric"

val releaseBuild = providers.gradleProperty("release").orNull == "true" ||
    gradle.startParameter.taskNames.any { it.substringAfterLast(':') == "releaseAll" }

val unobfuscated = loomx.isUnobfuscated
val requiredJava = if (sc.current.parsed >= "26.1") JavaVersion.VERSION_25 else JavaVersion.VERSION_21
val accessWidener = if (unobfuscated) "accesswidener/26.3/quarkos_capes.accesswidener" else "quarkos_capes.accesswidener"

fun quarkosUrl(name: String): String {
    val value = providers.gradleProperty("quarkos.$name").orNull?.trim().orEmpty()
    if (value.isEmpty()) {
        throw GradleException("Set quarkos.$name in gradle.properties")
    }
    return value
}

repositories {
    mavenCentral()
    maven("https://maven.shedaniel.me/")
    maven("https://maven.terraformersmc.com/releases/")
    if (unobfuscated) {
        maven("https://api.modrinth.com/maven") {
            content { includeModule("maven.modrinth", "modmenu") }
        }
    }
}

// Client gametests ship in Fabric API from 1.21.5. 1.21.1 uses the tick harness.
if (sc.current.parsed >= "1.21.5") {
    extensions.configure<net.fabricmc.loom.api.fabricapi.FabricApiExtension>("fabricApi") {
        configureTests {
            createSourceSet.set(true)
            modId.set("quarkos_capes_test")
            enableGameTests.set(false)
            enableClientGameTests.set(true)
            eula.set(true)
            clearRunDirectory.set(false)
        }
    }
}

dependencies {
    minecraft("com.mojang:minecraft:${sc.current.version}")
    loomx.applyMojangMappings()

    modImplementation("net.fabricmc:fabric-loader:${property("deps.fabric_loader")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${sc.properties.get<String>("deps.fabric_api")}")

    val cloth = sc.properties.get<String>("deps.cloth")
    modImplementation("me.shedaniel.cloth:cloth-config-fabric:$cloth")
    include("me.shedaniel.cloth:cloth-config-fabric:$cloth")
    val modMenu = if (unobfuscated) "maven.modrinth:modmenu" else "com.terraformersmc:modmenu"
    modCompileOnly("$modMenu:${sc.properties.get<String>("deps.modmenu")}")

    implementation("com.squareup.okhttp3:okhttp:3.14.9")
    include("com.squareup.okhttp3:okhttp:3.14.9")
    implementation("com.squareup.okio:okio:1.17.5")
    include("com.squareup.okio:okio:1.17.5")

    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("com.google.code.gson:gson:2.11.0")
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Resolve the published API jar, never Loom's named development mods or Mod Menu.
val productionFabricApi = configurations.create("productionFabricApi") {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
}
dependencies {
    add(productionFabricApi.name, "net.fabricmc.fabric-api:fabric-api:${sc.properties.get<String>("deps.fabric_api")}")
}

tasks.register<ClientProductionRunTask>("prodClient") {
    group = "verification"
    description = "Boot the installable jar with Fabric API and no Mod Menu."
    dependsOn("verifyReleaseJar")
    mods.setFrom(loomx.modJar.flatMap { it.archiveFile }, productionFabricApi)
    runDir.set(layout.buildDirectory.dir("prod-run"))
    useXVFB.set(providers.gradleProperty("xvfb").map { it.toBoolean() }.orElse(false))
    val mode = providers.gradleProperty("only").orElse("boot")
    val releaseSmoke = unobfuscated && releaseBuild
    val smokeShot = runDir.file("prod-smoke.png")
    jvmArgs.add("-Dquarkos_capes.loader=fabric")
    if (releaseSmoke) {
        jvmArgs.add("-Dquarkos_capes.prodSmoke=true")
        jvmArgs.add(smokeShot.map { "-Dquarkos_capes.prodSmokeShot=${it.asFile.absolutePath}" })
    } else {
        jvmArgs.addAll("-Dquarkos_capes.harness=true", "-Dquarkos_capes.only=${mode.get()}")
    }
    programArgs.addAll("--version", sc.current.version, "--accessToken", "0", "--username", "CapeBoot", "--uuid", UUID.nameUUIDFromBytes("OfflinePlayer:CapeBoot".toByteArray()).toString(),
        "--width", "1280", "--height", "720")
    doFirst {
        check(mode.get() == "boot") { "prodClient supports -Ponly=boot" }
        check(mods.files.none { it.name.contains("modmenu", ignoreCase = true) }) { "Mod Menu must be absent from the production boot test" }
        if (releaseSmoke) {
            val shot = smokeShot.get().asFile
            Files.deleteIfExists(shot.toPath())
        }
    }
}

configurations.configureEach {
    resolutionStrategy {
        force("com.squareup.okio:okio:1.17.5")
    }
}

loom {
    fabricModJsonPath = rootProject.file("src/main/resources/fabric.mod.json")
    accessWidenerPath = rootProject.file("src/main/resources/$accessWidener")

    runConfigs.all {
        preferGradleTask = true
        generateRunConfig = true
        runDirectory = if (sc.current.version == "1.21.11") {
            rootProject.file("run")
        } else {
            rootProject.file("run/${sc.current.version}")
        }
    }
}

java {
    targetCompatibility = requiredJava
    sourceCompatibility = requiredJava
    toolchain {
        languageVersion = JavaLanguageVersion.of(requiredJava.majorVersion)
    }
}

tasks {
    processResources {
        fun MutableMap<String, String>.register(key: String, value: String) {
            inputs.property(key, value)
            set(key, value)
        }

        val minecraftVersion = sc.current.version
        inputs.property("releaseBuild", releaseBuild)
        val props = buildMap {
            register("id", sc.properties["mod.id"])
            register("name", sc.properties["mod.name"])
            register("version", "${sc.properties.get<String>("mod.version")}+$minecraftVersion")
            register("minecraft", sc.properties["mod.mc_compat"])
            register("java", ">=${requiredJava.majorVersion}")
            register("accessWidener", accessWidener)
            register("fabric_api", sc.properties["deps.fabric_api"])
            register("cloth", sc.properties["deps.cloth"])
            register("modmenu", sc.properties["deps.modmenu"])
            register("homepage", quarkosUrl("homepage"))
            register("source", quarkosUrl("source"))
            register("issues", quarkosUrl("issues"))
        }

        exclude("META-INF/mods.toml")
        exclude("META-INF/neoforge.mods.toml")
        exclude("META-INF/accesstransformer.cfg")
        eachFile {
            val path = relativePath.pathString
            if (path.endsWith("/quarkos_capes.accesswidener") || path == "quarkos_capes.accesswidener") {
                if (path != accessWidener) exclude()
            }
        }
        filesMatching("fabric.mod.json") {
            expand(props)
        }
        val legacyCapeMixin = sc.current.parsed < "1.21.11"
        filesMatching("quarkos_capes.mixins.json") {
            filter { line ->
                var next = line
                if (legacyCapeMixin) next = next.replace("ElytraTextureMixin", "ElytraTextureMixin121")
                if (unobfuscated) next = next.replace("JAVA_21", "JAVA_${requiredJava.majorVersion}")
                if (sc.current.parsed >= "26.3") next = next.replace("\"CapePauseButtonMixin\"", "\"CapePauseButtonMixin\", \"CapeKeyMixin\"")
                if (releaseBuild) {
                    next = next.replace(", \"ShotCameraMixin\", \"ShotSkinMixin\"", "")
                }
                next
            }
        }
    }

    withType<JavaExec>().configureEach {
        val cloakOnly = providers.gradleProperty("cloakOnly").orNull == "true"
        val captures = if (cloakOnly) {
            rootProject.file("build/captures/fabric/${sc.current.version}").absolutePath
        } else {
            rootProject.file("build/captures/${sc.current.version}").absolutePath
        }
        systemProperty("quarkos_capes.captures", captures)
        systemProperty("quarkos_capes.loader", "fabric")
        if (providers.gradleProperty("harness").orNull == "true" || cloakOnly) {
            systemProperty("quarkos_capes.harness", "true")
        }
        if (providers.gradleProperty("labyShots").orNull == "true") {
            systemProperty("quarkos_capes.harness", "true")
            systemProperty("quarkos_capes.labyShots", "true")
            systemProperty("CAPES_DEV_CATALOG", "true")
            environment("CAPES_DEV_CATALOG", "true")
            systemProperty(
                "quarkos_capes.captures",
                rootProject.file("build/captures/fabric/${sc.current.version}").absolutePath
            )
        }
        if (providers.gradleProperty("labyCompare").orNull == "true") {
            systemProperty("quarkos_capes.harness", "true")
            systemProperty("quarkos_capes.labyCompare", "true")
            systemProperty("CAPES_DEV_CATALOG", "true")
            environment("CAPES_DEV_CATALOG", "true")
            systemProperty(
                "quarkos_capes.captures",
                rootProject.file("build/laby-compare-raw/fabric/${sc.current.version}").absolutePath
            )
            systemProperty(
                "quarkos_capes.compare",
                rootProject.file("build/captures/fabric/${sc.current.version}").absolutePath
            )
        }
        if (cloakOnly) {
            systemProperty("quarkos_capes.cloakOnly", "true")
        }
        if (providers.gradleProperty("skipShots").orNull == "true") {
            systemProperty("quarkos_capes.skipShots", "true")
        }
        providers.gradleProperty("shots").orNull?.let {
            systemProperty("quarkos_capes.captures", it)
            systemProperty("quarkos_capes.compare", it)
        }
        for (key in listOf("only", "guiScale", "width", "height", "serverUrl", "linkCode")) {
            providers.gradleProperty(key).orNull?.let { systemProperty("quarkos_capes.$key", it) }
        }
        for (key in listOf("QUARKOS_CAPES_BENCH", "QUARKOS_CAPES_BENCH_PLAYERS")) {
            providers.gradleProperty(key).orNull?.let { systemProperty(key, it) }
        }
        systemProperty("quarkos_capes.serverUrl", providers.gradleProperty("serverUrl").orElse("http://127.0.0.1:8787").get())
        if (providers.gradleProperty("only").orNull in listOf("flows", "boot", "ui")) systemProperty("quarkos_capes.harness", "true")
        for (key in listOf("CAPES_DEV_AUTH", "CAPES_DEV_AUTH_SECRET")) {
            System.getenv(key)?.let { environment(key, it) }
        }
        if (!unobfuscated) {
            environment("LIBGL_ALWAYS_SOFTWARE", "1")
            environment("MESA_GL_VERSION_OVERRIDE", "3.3")
        }
        val display = providers.gradleProperty("display").orNull
        val xauth = providers.gradleProperty("xauth").orNull
        if (!display.isNullOrBlank()) {
            environment("DISPLAY", display)
        }
        if (!xauth.isNullOrBlank()) {
            environment("XAUTHORITY", xauth)
        }
    }

    test {
        useJUnitPlatform()
    }

    named<JavaExec>("runClient") {
        val role = providers.gradleProperty("role").orNull
        if (role == "a" || role == "b") {
            workingDir = rootProject.file("run/share-$role")
            systemProperty("quarkos_capes.role", role)
            systemProperty("quarkos_capes.server", providers.gradleProperty("server").orElse("127.0.0.1:25599").get())
            systemProperty("quarkos_capes.share", rootProject.file("run/share").absolutePath)
        }
        val flows = providers.gradleProperty("only").orNull == "flows"
        val user = providers.gradleProperty("username").orNull ?: if (flows) "CapeFlow" else null
        val uuid = providers.gradleProperty("uuid").orNull ?: if (flows) UUID.nameUUIDFromBytes("OfflinePlayer:$user".toByteArray()).toString() else null
        val shotWidth = providers.gradleProperty("width").orElse("1280").get()
        val shotHeight = providers.gradleProperty("height").orElse("720").get()
        doFirst {
            if (!user.isNullOrBlank()) {
                val updated = mutableListOf<String>()
                var skip = false
                for (arg in args) {
                    if (skip) {
                        skip = false
                        continue
                    }
                    if (arg == "--username" || arg == "--uuid" || arg == "--width" || arg == "--height") {
                        skip = true
                        continue
                    }
                    updated.add(arg)
                }
                updated.add("--username")
                updated.add(user)
                if (!uuid.isNullOrBlank()) {
                    updated.add("--uuid")
                    updated.add(uuid)
                }
                if (role == "a" || role == "b") {
                    updated.add("--width")
                    updated.add(shotWidth)
                    updated.add("--height")
                    updated.add(shotHeight)
                }
                args = updated
            }
            workingDir.mkdirs()
            workingDir.resolve("eula.txt").writeText("eula=true\n")
        }
    }

    withType<Jar> {
        val name = project.property("mod.id")
        inputs.property("mod_id", name)
        from(rootProject.file("LICENSE")) { rename { "${it}_$name" } }
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
}

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
sourceSets.named("main") {
    java.srcDir(devFlagsDir)
}
tasks.named("compileJava") { dependsOn(writeDevFlags) }
apply(from = rootProject.file("gradle/strip-module-info.gradle.kts"))
apply(from = rootProject.file("gradle/release.gradle.kts"))
