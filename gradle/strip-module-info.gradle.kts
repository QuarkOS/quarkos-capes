import org.gradle.api.GradleException

// A shaded dependency's module-info.class must not land in the mod jar or in a nested jar.
// The stripper has no library dependencies, so the classes directory is the whole classpath.
fun stripArchive(taskName: String) {
    tasks.matching { it.name == taskName }.configureEach {
        inputs.file(rootProject.file("src/main/java/dev/quarkos/capes/jar/ModuleInfoStrip.java"))
        doLast {
            logger.lifecycle("strip module-info: ${this.name}")
            val file = (property("archiveFile") as org.gradle.api.file.RegularFileProperty).get().asFile
            if (!file.isFile) {
                return@doLast
            }
            val classes = project.layout.buildDirectory.dir("classes/java/main").get().asFile
            val java = File(System.getProperty("java.home"), "bin/java")
            val code = ProcessBuilder(
                java.absolutePath,
                "-cp",
                classes.absolutePath,
                "dev.quarkos.capes.jar.ModuleInfoStrip",
                file.absolutePath
            ).inheritIO().start().waitFor()
            if (code != 0) {
                throw GradleException("module-info strip failed for ${file} (exit ${code})")
            }
        }
    }
}

stripArchive("jar")
stripArchive("remapJar")
