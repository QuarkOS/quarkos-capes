import java.util.zip.ZipFile
import java.util.Properties

// The rules live in src/test/.../ReleaseServicePolicy.java so the unit test and this check cannot drift.
fun validateReleaseService(value: String, allowLocal: Boolean) {
            val source = rootProject.file("src/test/java/dev/quarkos/capes/release/ReleaseServicePolicy.java")
            val dir = rootProject.layout.buildDirectory.dir("policy-classes").get().asFile
            val classFile = dir.resolve("dev/quarkos/capes/release/ReleaseServicePolicy.class")
            val lockFile = dir.resolve(".compile.lock")
            dir.mkdirs()
            // Parallel nodes share one Gradle JVM: a FileChannel lock alone throws OverlappingFileLockException.
            synchronized("quarkos-capes-policy-compile".intern()) { java.nio.channels.FileChannel.open(
                lockFile.toPath(),
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.WRITE
            ).use { channel ->
                channel.lock().use {
                    if (!classFile.isFile || classFile.lastModified() < source.lastModified()) {
                        // Any JDK 21+ javac works (--release 21); Linux CI path first, then configured installs.
                        val homes = mutableListOf("/usr/lib/jvm/java-21-openjdk-amd64")
                        (providers.gradleProperty("org.gradle.java.installations.paths").orNull ?: "")
                            .split(',').map { it.trim() }.filter { it.isNotEmpty() }.forEach { homes.add(it) }
                        System.getenv("JAVA_HOME")?.let { homes.add(it) }
                        homes.add(System.getProperty("java.home"))
                        val javac = homes.flatMap { listOf(java.io.File(it, "bin/javac"), java.io.File(it, "bin/javac.exe")) }
                            .firstOrNull { it.isFile && it.canExecute() }
                        check(javac != null) { "Java 21 javac is required to check the service URL" }
                        val process = ProcessBuilder(
                            javac.absolutePath, "--release", "21", "-encoding", "UTF-8", "-d", dir.absolutePath, source.absolutePath
                        ).redirectErrorStream(true).start()
                        val log = process.inputStream.bufferedReader().readText()
                        check(process.waitFor() == 0) { "ReleaseServicePolicy did not compile:\n$log" }
                    }
                }
            } }
            val loader = java.net.URLClassLoader(arrayOf(dir.toURI().toURL()), null as ClassLoader?)
            try {
                val method = loader.loadClass("dev.quarkos.capes.release.ReleaseServicePolicy")
                    .getMethod("validate", String::class.java, java.lang.Boolean.TYPE)
                method.invoke(null, value, allowLocal)
            } catch (failure: java.lang.reflect.InvocationTargetException) {
                val cause = failure.targetException
                throw IllegalStateException(cause.message ?: "service URL rejected", cause)
            } finally {
                loader.close()
            }
}

fun verifyReleaseArchive(archive: java.io.File, expectedService: String, localAllowed: Boolean,
                          dev: Boolean, banned: List<String>) {
            ZipFile(archive).use { zip ->
                val serviceEntry = zip.getEntry("quarkos_capes-service.properties")
                check(serviceEntry != null) { "Missing service default in ${archive.name}" }
                val properties = Properties()
                zip.getInputStream(serviceEntry).reader(Charsets.UTF_8).use { properties.load(it) }
                val actualService = properties.getProperty("serviceUrl", "")
                check(actualService == expectedService) { "Stale service default in ${archive.name}: $actualService" }
                validateReleaseService(actualService, localAllowed)
                for (notice in listOf("NOTICE", "Apache-2.0.txt", "Cloth-Config-LGPL-3.0.md", "GPL-3.0.txt", "OkHttp-PublicSuffix-NOTICE.txt", "MPL-2.0.txt")) {
                    check(zip.getEntry("META-INF/licenses/$notice") != null) { "Missing $notice in ${archive.name}" }
                }
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.isDirectory) continue
                    val data = zip.getInputStream(entry).use { it.readBytes().toString(Charsets.ISO_8859_1) }
                    check(dev || (banned.none { entry.name.contains(it) || data.contains(it) }
                            && !data.contains("text.quarkos_capes.laby."))) {
                        "Developer catalog found in ${archive.name}: ${entry.name}"
                    }
                }
            }
}

// -PserviceUrl overrides the gradle.properties block. The block is the value Emilio replaces.
val releaseServiceUrl = providers.gradleProperty("serviceUrl")
    .orElse(providers.gradleProperty("quarkos.serviceUrl"))
    .orElse("http://127.0.0.1:8787")
    .map { it.trim() }
val allowLocalService = providers.gradleProperty("allowLocalService").orNull == "true"
val serviceResources = layout.buildDirectory.dir("generated/release-service")
val writeServiceDefault = tasks.register("writeServiceDefault") {
    inputs.property("serviceUrl", releaseServiceUrl)
    outputs.dir(serviceResources)
    val url = releaseServiceUrl
    val destination = serviceResources
    doLast {
        validateReleaseService(url.get(), true)
        val file = destination.get().file("quarkos_capes-service.properties").asFile
        file.parentFile.mkdirs()
        val properties = Properties().apply { setProperty("serviceUrl", url.get()) }
        file.writer(Charsets.UTF_8).use { properties.store(it, "Built with -PserviceUrl") }
    }
}

// Applied by every loader node. The release assertion scans archive names AND bytecode/resources.
val developmentCatalog = providers.gradleProperty("devCatalog").orNull == "true"
val catalogClasses = listOf("LabyCatalog", "LabyBrowse", "LabyWear", "LabyCompare", "ClientHarness\$LabyFrame")
tasks.withType<Jar>().configureEach {
    if (!developmentCatalog) catalogClasses.forEach { exclude("**/$it.class", "**/$it\$*.class") }
}
tasks.withType<ProcessResources>().configureEach {
    from(writeServiceDefault)
    inputs.property("devCatalog", developmentCatalog)
    if (!developmentCatalog) filesMatching("assets/quarkos_capes/lang/*.json") {
        filter { line -> if (line.contains("\"text.quarkos_capes.laby.")) "" else line }
    }
}
val releaseArchives = tasks.withType<org.gradle.api.tasks.bundling.AbstractArchiveTask>().matching { it.name == "jar" || it.name == "remapJar" }
val verifyReleaseJar = tasks.register("verifyReleaseJar") {
    group = "verification"
    description = "Verify release service default, bundled notices and absence of developer catalog code."
    dependsOn(releaseArchives)
    val archives = releaseArchives.map { it.archiveFile }
    inputs.files(archives)
    inputs.property("devCatalog", developmentCatalog)
    inputs.property("allowLocalService", allowLocalService)
    inputs.property("serviceUrl", releaseServiceUrl)
    val dev = developmentCatalog
    val localAllowed = allowLocalService
    val expectedService = releaseServiceUrl.get()
    val banned = catalogClasses.toList()
    doLast {
        for (archive in inputs.files.files) {
            verifyReleaseArchive(archive, expectedService, localAllowed, dev, banned)
        }
    }
}
tasks.named("build") { dependsOn(verifyReleaseJar) }
