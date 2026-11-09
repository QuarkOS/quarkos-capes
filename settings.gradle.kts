pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/")
        maven("https://maven.kikugie.dev/releases") { name = "KikuGie Releases" }
        maven("https://maven.kikugie.dev/snapshots") { name = "KikuGie Snapshots" }
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.9.8"
    // Picks the Loom variant this Minecraft line needs.
    id("dev.kikugie.loom-back-compat") version "0.4.2"
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

stonecutter {
    create(rootProject) {
        // Fabric project paths stay :1.21.11. Forge is a sibling branch.
        versions("1.20.1", "1.21.1", "1.21.4", "1.21.8", "1.21.11")
        vcsVersion = "1.21.11"
        branch("forge") {
            versions("1.20.1", "1.21.1", "1.21.4", "1.21.8", "1.21.11")
        }
        branch("neoforge") {
            versions("1.21.1", "1.21.4", "1.21.8", "1.21.11")
        }
    }
}

rootProject.name = "quarkos-capes"

gradle.projectsEvaluated {
    val releaseAll = rootProject.tasks.register("releaseAll") {
        group = "build"
        description = "Build every Fabric, Forge, and NeoForge jar into dist/ with SHA-256 checksums."
        notCompatibleWithConfigurationCache("Copies version jars into dist/.")
    }
    // Fabric ships remapJar (intermediary names, nested Cloth and OkHttp).
    // Forge and NeoForge ship their own jar tasks. Loom's RemapJarTask is not a Jar subclass.
    val jars = rootProject.subprojects.mapNotNull { project ->
        if (!project.path.matches(Regex(":(?:forge:|neoforge:)?\\d+\\.\\d+(?:\\.\\d+)?"))) return@mapNotNull null
        project.tasks.findByName("remapJar") ?: project.tasks.findByName("jar")
    }.distinctBy { it.project.path }.toList()
    fun archiveFile(task: org.gradle.api.Task): java.io.File {
        val prop = task.property("archiveFile") as org.gradle.api.file.RegularFileProperty
        return prop.get().asFile
    }
    fun archiveVersion(task: org.gradle.api.Task): String {
        val prop = task.property("archiveVersion") as org.gradle.api.provider.Property<String>
        return prop.get()
    }
    releaseAll.configure {
        dependsOn(jars)
        doLast {
            val dist = rootProject.layout.projectDirectory.dir("dist").asFile
            dist.mkdirs()
            dist.listFiles()?.forEach { if (it.isFile) it.delete() }
            val sums = mutableListOf<String>()
            val seen = mutableSetOf<String>()
            for (jar in jars) {
                val file = archiveFile(jar)
                if (!file.isFile) continue
                val archiveVersion = archiveVersion(jar)
                val minecraft = archiveVersion.substringAfter('+', archiveVersion)
                val loader = when {
                    jar.project.path.startsWith(":neoforge:") -> "neoforge"
                    jar.project.path.startsWith(":forge:") -> "forge"
                    else -> "fabric"
                }
                val name = "quarkos-capes-${archiveVersion.substringBefore('+')}+$loader-$minecraft.jar"
                if (!seen.add(name)) continue
                val dest = dist.resolve(name)
                file.copyTo(dest, overwrite = true)
                val digest = java.security.MessageDigest.getInstance("SHA-256")
                dest.inputStream().use { input ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        digest.update(buffer, 0, read)
                    }
                }
                val hash = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
                sums.add("$hash  $name")
            }
            sums.sort()
            dist.resolve("SHA256SUMS").writeText(sums.joinToString("\n", postfix = "\n"))
            sums.forEach { println("dist/ $it") }
        }
    }
}
