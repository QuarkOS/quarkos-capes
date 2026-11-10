plugins {
    id("dev.kikugie.loom-back-compat")
}

// Stonecutter nodes share a coordinate space; setting `group` here makes the version nodes collide.
version = "${property("mod.version")}+${sc.current.version}"
base.archivesName = "quarkos_capes-fabric"

val releaseBuild = providers.gradleProperty("release").orNull == "true" ||
    gradle.startParameter.taskNames.any { it.substringAfterLast(':') == "releaseAll" }

val requiredJava: JavaVersion = JavaVersion.VERSION_21

repositories {
    mavenCentral()
    maven("https://maven.shedaniel.me/")
    maven("https://maven.terraformersmc.com/releases/")
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
    modCompileOnly("com.terraformersmc:modmenu:${sc.properties.get<String>("deps.modmenu")}")

    implementation("com.squareup.okhttp3:okhttp:3.14.9")
    include("com.squareup.okhttp3:okhttp:3.14.9")
    implementation("com.squareup.okio:okio:1.17.5")
    include("com.squareup.okio:okio:1.17.5")

    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("com.google.code.gson:gson:2.11.0")
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

configurations.configureEach {
    resolutionStrategy {
        force("com.squareup.okio:okio:1.17.5")
    }
}

loom {
    fabricModJsonPath = rootProject.file("src/main/resources/fabric.mod.json")
    accessWidenerPath = rootProject.file("src/main/resources/quarkos_capes.accesswidener")

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
            register("java", ">=21")
        }

        exclude("META-INF/mods.toml")
        exclude("META-INF/neoforge.mods.toml")
        exclude("META-INF/accesstransformer.cfg")
        filesMatching("fabric.mod.json") {
            expand(props)
        }
        val legacyCapeMixin = sc.current.parsed < "1.21.11"
        filesMatching("quarkos_capes.mixins.json") {
            filter { line ->
                var next = if (legacyCapeMixin) line.replace("ElytraTextureMixin", "ElytraTextureMixin121") else line
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
            "/cursor/stores/bc-97e49978-8cdc-55b6-9065-d58f273ae502/media/capes/fabric/${sc.current.version}"
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
                "/cursor/stores/bc-97e49978-8cdc-55b6-9065-d58f273ae502/media/capes/labymod/fabric/${sc.current.version}"
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
                "/cursor/stores/bc-97e49978-8cdc-55b6-9065-d58f273ae502/media/capes/labymod/compare/fabric/${sc.current.version}"
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
        for (key in listOf("only", "guiScale", "width", "height")) {
            providers.gradleProperty(key).orNull?.let { systemProperty("quarkos_capes.$key", it) }
        }
        for (key in listOf("QUARKOS_CAPES_BENCH", "QUARKOS_CAPES_BENCH_PLAYERS")) {
            providers.gradleProperty(key).orNull?.let { systemProperty(key, it) }
        }
        environment("LIBGL_ALWAYS_SOFTWARE", "1")
        environment("MESA_GL_VERSION_OVERRIDE", "3.3")
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
        val user = providers.gradleProperty("username").orNull
        val uuid = providers.gradleProperty("uuid").orNull
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
