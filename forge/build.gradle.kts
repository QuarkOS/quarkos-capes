import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.commons.ClassRemapper
import org.objectweb.asm.commons.Remapper
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Forge nodes for 1.20.1 (47.4.29), 1.21.1 (52.1.20), 1.21.4 (54.1.22),
 * 1.21.8 (58.1.26), and 1.21.11 (61.2.4).
 * Dev compiles official names. The 1.20.1 jar is rewritten to SRG before it ships.
 * Every node compiles the chiseled tree, not the Fabric checkout in src/.
 * Cloth Config Forge exists for 1.20.1 and 1.21.1. Later nodes draw the screen themselves.
 */

// Dev compiles against official names. Forge 1.20.1 production still runs SRG method names.
private val forge1201MixinTargets = mapOf(
    "render" to "m_6494_",
    "getElytraTextureLocation" to "m_108563_",
    "layers" to "f_115291_",
    "addLayer" to "m_115326_",
    "Lnet/minecraft/client/player/AbstractClientPlayer;isModelPartShown(Lnet/minecraft/world/entity/player/PlayerModelPart;)Z" to
        "Lnet/minecraft/client/player/AbstractClientPlayer;m_36170_(Lnet/minecraft/world/entity/player/PlayerModelPart;)Z",
    "Lnet/minecraft/client/player/AbstractClientPlayer;getCloakTextureLocation()Lnet/minecraft/resources/ResourceLocation;" to
        "Lnet/minecraft/client/player/AbstractClientPlayer;m_108561_()Lnet/minecraft/resources/ResourceLocation;"
)

// Official names -> SRG. Invokes name the compile-time type, so the lookup walks that
// type's parents until it finds the declaration. Class names stay official.
private val forge1201Methods = HashMap<String, String>(120_000)
private val forge1201Fields = HashMap<String, String>(80_000)
private val forge1201Parents = HashMap<String, List<String>>(20_000)

private fun forge1201Load() {
    if (forge1201Parents.isNotEmpty()) return
    val root = File(
        System.getProperty("user.home"),
        ".gradle/caches/minecraftforge/forgegradle/slime-launcher/cache/net/minecraftforge/forge/1.20.1-47.4.29/mappings/srgs"
    )
    val srg = root.walkTopDown().first { it.isFile && it.name == "mcp-srg.srg" }
    srg.forEachLine { line ->
        when {
            line.startsWith("MD: ") -> {
                val parts = line.split(' ')
                val slash = parts[1].lastIndexOf('/')
                val srgSlash = parts[3].lastIndexOf('/')
                val owner = parts[1].substring(0, slash)
                val name = parts[1].substring(slash + 1)
                forge1201Methods["$owner.$name${parts[2]}"] = parts[3].substring(srgSlash + 1)
            }
            line.startsWith("FD: ") -> {
                val parts = line.split(' ')
                val slash = parts[1].lastIndexOf('/')
                val srgSlash = parts[2].lastIndexOf('/')
                val owner = parts[1].substring(0, slash)
                val name = parts[1].substring(slash + 1)
                forge1201Fields["$owner.$name"] = parts[2].substring(srgSlash + 1)
            }
        }
    }
    val named = File(
        System.getProperty("user.home"),
        ".gradle/caches/minecraftforge/forgegradle/mavenizer/caches/forge/net/minecraftforge/forge/1.20.1-47.4.29/official/1.20.1/recompiled.jar"
    )
    ZipInputStream(named.inputStream()).use { zip ->
        var entry = zip.nextEntry
        while (entry != null) {
            if (entry.name.endsWith(".class")) {
                ClassReader(zip.readBytes()).accept(object : ClassVisitor(Opcodes.ASM9) {
                    override fun visit(
                        version: Int, access: Int, name: String, signature: String?,
                        superName: String?, interfaces: Array<out String>?
                    ) {
                        val parents = ArrayList<String>()
                        if (superName != null && superName != "java/lang/Object") parents.add(superName)
                        if (interfaces != null) parents.addAll(interfaces)
                        forge1201Parents[name] = parents
                    }
                }, ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
            }
            entry = zip.nextEntry
        }
    }
}

private fun forge1201Walk(owner: String): Sequence<String> = sequence {
    val seen = HashSet<String>()
    val queue = ArrayDeque<String>()
    queue.add(owner)
    while (queue.isNotEmpty()) {
        val type = queue.removeFirst()
        if (!seen.add(type)) continue
        yield(type)
        forge1201Parents[type]?.let { queue.addAll(it) }
    }
}

private fun forge1201Method(owner: String, name: String, descriptor: String): String {
    for (type in forge1201Walk(owner)) {
        forge1201Methods["$type.$name$descriptor"]?.let { return it }
    }
    return name
}

private fun forge1201Field(owner: String, name: String): String {
    for (type in forge1201Walk(owner)) {
        forge1201Fields["$type.$name"]?.let { return it }
    }
    return name
}

private val forge1201Remapper: Remapper = object : Remapper() {
    override fun mapMethodName(owner: String, name: String, descriptor: String): String {
        forge1201Load()
        return forge1201Method(owner, name, descriptor)
    }

    override fun mapFieldName(owner: String, name: String, descriptor: String): String {
        forge1201Load()
        return forge1201Field(owner, name)
    }
}

private val srgLeftovers = linkedSetOf<String>()

private fun rewriteForge1201MixinTargets(jar: File) {
    srgLeftovers.clear()
    val temp = File(jar.parentFile, jar.name + ".srg")
    ZipInputStream(jar.inputStream()).use { input ->
        ZipOutputStream(temp.outputStream()).use { output ->
            var entry = input.nextEntry
            while (entry != null) {
                val data = input.readBytes()
                val next = if (entry.name.startsWith("dev/quarkos/capes/") && entry.name.endsWith(".class")) {
                    rewriteQuarkosClass(entry.name, data)
                } else {
                    data
                }
                val copy = ZipEntry(entry.name)
                copy.time = entry.time
                output.putNextEntry(copy)
                if (!entry.isDirectory) output.write(next)
                output.closeEntry()
                entry = input.nextEntry
            }
        }
    }
    if (!temp.renameTo(jar)) {
        temp.copyTo(jar, overwrite = true)
        temp.delete()
    }
    if (srgLeftovers.isNotEmpty()) {
        throw GradleException(
            "Forge 1.20.1 jar still calls official Minecraft names:\n" + srgLeftovers.joinToString("\n")
        )
    }
}

private fun rewriteQuarkosClass(name: String, bytes: ByteArray): ByteArray {
    val reader = ClassReader(bytes)
    val writer = ClassWriter(0)
    reader.accept(ClassRemapper(writer, forge1201Remapper), 0)
    val remapped = writer.toByteArray()
    val finished = if (name.contains("/mixin/")) rewriteMixinAnnotations(remapped) else remapped
    ClassReader(finished).accept(object : ClassVisitor(Opcodes.ASM9) {
        override fun visitMethod(
            access: Int, methodName: String?, descriptor: String?, signature: String?, exceptions: Array<out String>?
        ): MethodVisitor {
            return object : MethodVisitor(Opcodes.ASM9) {
                override fun visitMethodInsn(
                    opcode: Int, owner: String, called: String, desc: String, isInterface: Boolean
                ) {
                    noteOfficial(name, owner, called, desc)
                }

                override fun visitFieldInsn(opcode: Int, owner: String, field: String, desc: String) {
                    noteOfficial(name, owner, field, desc)
                }
            }
        }
    }, 0)
    return finished
}

private fun noteOfficial(from: String, owner: String, member: String, descriptor: String) {
    if (member.startsWith("m_") || member.startsWith("f_") || member.startsWith("<")) return
    if (!owner.startsWith("net/minecraft/") && !owner.startsWith("com/mojang/blaze3d/") && !owner.startsWith("com/mojang/math/")) {
        return
    }
    val expected = if (descriptor.startsWith("(")) forge1201Method(owner, member, descriptor) else forge1201Field(owner, member)
    if (expected != member) srgLeftovers.add("$from -> $owner.$member$descriptor (srg $expected)")
}

private fun rewriteMixinAnnotations(bytes: ByteArray): ByteArray {
    val reader = ClassReader(bytes)
    val writer = ClassWriter(0)
    reader.accept(object : ClassVisitor(Opcodes.ASM9, writer) {
        override fun visitAnnotation(descriptor: String?, visible: Boolean): AnnotationVisitor? {
            return rewriteAnnotation(super.visitAnnotation(descriptor, visible))
        }

        override fun visitMethod(
            access: Int, name: String?, descriptor: String?, signature: String?, exceptions: Array<out String>?
        ): MethodVisitor {
            val method = super.visitMethod(access, name, descriptor, signature, exceptions)
            return object : MethodVisitor(Opcodes.ASM9, method) {
                override fun visitAnnotation(descriptor: String?, visible: Boolean): AnnotationVisitor? {
                    return rewriteAnnotation(super.visitAnnotation(descriptor, visible))
                }
            }
        }
    }, 0)
    return writer.toByteArray()
}

private fun rewriteAnnotation(visitor: AnnotationVisitor?): AnnotationVisitor? {
    if (visitor == null) return null
    return object : AnnotationVisitor(Opcodes.ASM9, visitor) {
        override fun visit(name: String?, value: Any?) {
            super.visit(name, if (value is String) forge1201MixinTargets[value] ?: value else value)
        }

        override fun visitAnnotation(name: String?, descriptor: String?): AnnotationVisitor? {
            return rewriteAnnotation(super.visitAnnotation(name, descriptor))
        }

        override fun visitArray(name: String?): AnnotationVisitor? {
            return rewriteAnnotation(super.visitArray(name))
        }
    }
}

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
    val cloth: String?,
)

val modVersion = "1.0.0"
val releaseBuild = providers.gradleProperty("release").orNull == "true" ||
    gradle.startParameter.taskNames.any { it.substringAfterLast(':') == "releaseAll" }

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
    "1.20.1" -> ForgeNode("1.20.1-47.4.29", "47.4.29", legacyPack(15), "11.1.136")
    "1.21.1" -> ForgeNode("1.21.1-52.1.20", "52.1.20", legacyPack(34), "15.0.140")
    "1.21.4" -> ForgeNode("1.21.4-54.1.22", "54.1.22", legacyPack(46), null)
    "1.21.8" -> ForgeNode("1.21.8-58.1.26", "58.1.26", legacyPack(64), null)
    "1.21.11" -> ForgeNode("1.21.11-61.2.4", "61.2.4", modernPack(75, 0), null)
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

    version = "$modVersion+$minecraftVersion"
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
        main.output.setResourcesDir(main.java.destinationDirectory)
    }
    tasks.named("compileJava") {
        dependsOn("stonecutterGenerate")
        dependsOn(writeDevFlags)
    }
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
    val clothVersion = node.cloth
    if (clothVersion != null) {
        embed("me.shedaniel.cloth:cloth-config-forge:$clothVersion")
    }
    val embeddedJars = buildList {
        add(EmbeddedJar("com.squareup.okhttp3", "okhttp", "3.14.9", "[3.14.9,3.15)", "okhttp-3.14.9.jar"))
        add(EmbeddedJar("com.squareup.okio", "okio", "1.17.5", "[1.17.5,1.18)", "okio-1.17.5.jar"))
        if (clothVersion != null) {
            add(EmbeddedJar("me.shedaniel.cloth", "cloth-config-forge", clothVersion, clothRange(clothVersion), "cloth-config-forge-$clothVersion-forge.jar"))
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
        val clothDependency = if (node.cloth != null) {
            """
[[dependencies.quarkos_capes]]
modId = "cloth_config"
mandatory = true
versionRange = "${clothRange(node.cloth)}"
ordering = "AFTER"
side = "CLIENT"
"""
        } else {
            ""
        }
        val props = mapOf(
            "version" to "$modVersion+$minecraftVersion",
            "forge_dep" to node.forgeDep,
            "forge_spec" to node.forgeDep.substringBefore('.'),
            "minecraft" to minecraftVersion,
            "cloth_dependency" to clothDependency,
        )
        inputs.property("forge_dep", node.forgeDep)
        inputs.property("minecraft", minecraftVersion)
        inputs.property("releaseBuild", releaseBuild)
        inputs.property("mixinJava", if (minecraftVersion == "1.20.1") "JAVA_17" else "JAVA_21")
        filesMatching("META-INF/mods.toml") {
            expand(props)
        }
        val legacyCapeMixin = minecraftVersion != "1.21.11"
        filesMatching("quarkos_capes.mixins.json") {
            filter { line ->
                var next = if (legacyCapeMixin) line.replace("ElytraTextureMixin\"", "ElytraTextureMixin121\"") else line
                if (releaseBuild) {
                    next = next.replace(", \"ShotCameraMixin\", \"ShotSkinMixin\"", "")
                }
                if (minecraftVersion == "1.20.1") {
                    next = next.replace("JAVA_21", "JAVA_17")
                }
                next
            }
        }
        exclude("fabric.mod.json")
        exclude("**/*.accesswidener")
        exclude("META-INF/neoforge.mods.toml")
        from(forgePack)
        from(rootProject.file("LICENSE")) {
            rename { "LICENSE_quarkos_capes" }
        }
    }

    tasks.named<Jar>("jar") {
        dependsOn(prepareJarJar)
        doFirst {
            if (minecraftVersion == "1.20.1") {
                delete(layout.buildDirectory.dir("classes/java/main/META-INF/jarjar"))
            }
        }
        from(jarjarDir) {
            into("META-INF/jarjar")
        }
        manifest {
            attributes["MixinConfigs"] = "quarkos_capes.mixins.json"
        }
        if (minecraftVersion == "1.20.1") {
            doLast { rewriteForge1201MixinTargets(archiveFile.get().asFile) }
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
        val mediaRoot = file("/cursor/stores/bc-97e49978-8cdc-55b6-9065-d58f273ae502")
        val media = "/cursor/stores/bc-97e49978-8cdc-55b6-9065-d58f273ae502/media/capes/forge/$minecraftVersion"
        val localCaptures = rootProject.file("build/captures/forge/$minecraftVersion").absolutePath
        val captures = if (labyShots && mediaRoot.isDirectory) {
            "/cursor/stores/bc-97e49978-8cdc-55b6-9065-d58f273ae502/media/capes/labymod/forge/$minecraftVersion"
        } else if ((harness || cloakOnly) && mediaRoot.isDirectory) {
            media
        } else {
            localCaptures
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
            if (minecraftVersion == "1.20.1") {
                dependsOn(prepareJarJar)
            }
            doFirst {
                val eula = workingDir.resolve("eula.txt")
                eula.parentFile?.mkdirs()
                eula.writeText("eula=true\n")
                if (minecraftVersion == "1.20.1") {
                    val dest = layout.buildDirectory.dir("classes/java/main/META-INF/jarjar").get().asFile
                    dest.mkdirs()
                    copy {
                        from(jarjarDir)
                        into(dest)
                    }
                }
            }
        }
    }
}
apply(from = rootProject.file("gradle/strip-module-info.gradle.kts"))
