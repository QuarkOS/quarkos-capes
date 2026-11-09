import java.util.zip.ZipFile

// Applied by every loader node. The release assertion scans archive names AND bytecode/resources.
val developmentCatalog = providers.gradleProperty("devCatalog").orNull == "true"
val catalogClasses = listOf("LabyCatalog", "LabyBrowse", "LabyWear", "LabyCompare", "ClientHarness\$LabyFrame")
tasks.withType<Jar>().configureEach {
    if (!developmentCatalog) catalogClasses.forEach { exclude("**/$it.class", "**/$it\$*.class") }
}
tasks.withType<ProcessResources>().configureEach {
    inputs.property("devCatalog", developmentCatalog)
    if (!developmentCatalog) filesMatching("assets/quarkos_capes/lang/*.json") {
        filter { line -> if (line.contains("\"text.quarkos_capes.laby.")) "" else line }
    }
}
val releaseArchives = tasks.withType<org.gradle.api.tasks.bundling.AbstractArchiveTask>().matching { it.name == "jar" || it.name == "remapJar" }
val verifyReleaseJar = tasks.register("verifyReleaseJar") {
    group = "verification"
    description = "Reject developer catalog classes, references and translations in release jars."
    dependsOn(releaseArchives)
    val archives = releaseArchives.map { it.archiveFile }
    inputs.files(archives)
    inputs.property("devCatalog", developmentCatalog)
    val dev = developmentCatalog
    val banned = catalogClasses.toList()
    doLast {
        if (!dev) for (archive in inputs.files.files) {
            ZipFile(archive).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.isDirectory) continue
                    val data = zip.getInputStream(entry).use { it.readBytes().toString(Charsets.ISO_8859_1) }
                    check(banned.none { entry.name.contains(it) || data.contains(it) }
                            && !data.contains("text.quarkos_capes.laby.")) {
                        "Developer catalog found in ${archive.name}: ${entry.name}"
                    }
                }
            }
        }
    }
}
tasks.named("build") { dependsOn(verifyReleaseJar) }
