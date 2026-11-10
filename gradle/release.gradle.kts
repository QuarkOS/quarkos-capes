import java.util.zip.ZipFile
import java.net.URI
import java.util.Properties

class ReleaseServicePolicy {
    companion object {
        fun validate(value: String, allowLocal: Boolean) {
            val uri = runCatching { URI(value) }.getOrNull()
            check(uri != null && uri.scheme in listOf("http", "https") && !uri.host.isNullOrBlank()
                    && uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null
                    && (uri.port == -1 || uri.port in 1..65535)) { "Invalid release serviceUrl: $value" }
            val host = uri.host.lowercase().removePrefix("[").removeSuffix("]").trimEnd('.')
            val labels = host.split('.')
            val ipv6 = host.contains(':')
            // Numeric IPv6 literals require no DNS lookup; this catches expanded and mapped loopbacks too.
            val localAddress = ipv6 && runCatching {
                val address = java.net.InetAddress.getByName(host)
                address.isLoopbackAddress || address.isAnyLocalAddress
            }.getOrDefault(true)
            val local = localAddress || host == "localhost" || host.endsWith(".localhost")
                    || host.endsWith(".local") || host == "0.0.0.0" || host.startsWith("127.")
                    || host.matches(Regex("[0-9]+")) || host.startsWith("0x")
                    || labels.any { it.length > 1 && it.startsWith('0') && it.all(Char::isDigit) }
                    || labels.any { it == "example" } || labels.last() in listOf("test", "invalid")
            check(allowLocal || !local) {
                "Release jar has a loopback/example service URL ($value). Set -PserviceUrl=<production URL>; " +
                        "use -PallowLocalService=true only for local parent validation."
            }
        }

        fun verifyArchive(archive: java.io.File, expectedService: String, localAllowed: Boolean,
                          dev: Boolean, banned: List<String>) {
            ZipFile(archive).use { zip ->
                val serviceEntry = zip.getEntry("quarkos_capes-service.properties")
                check(serviceEntry != null) { "Missing service default in ${archive.name}" }
                val properties = Properties()
                zip.getInputStream(serviceEntry).reader(Charsets.UTF_8).use { properties.load(it) }
                val actualService = properties.getProperty("serviceUrl", "")
                check(actualService == expectedService) { "Stale service default in ${archive.name}: $actualService" }
                ReleaseServicePolicy.validate(actualService, localAllowed)
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

    }
}

val releaseServiceUrl = providers.gradleProperty("serviceUrl").orElse("http://127.0.0.1:8787").map { it.trim() }
val allowLocalService = providers.gradleProperty("allowLocalService").orNull == "true"
val serviceResources = layout.buildDirectory.dir("generated/release-service")
val writeServiceDefault = tasks.register("writeServiceDefault") {
    inputs.property("serviceUrl", releaseServiceUrl)
    outputs.dir(serviceResources)
    val url = releaseServiceUrl
    val destination = serviceResources
    doLast {
        ReleaseServicePolicy.validate(url.get(), true)
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
            ReleaseServicePolicy.verifyArchive(archive, expectedService, localAllowed, dev, banned)
        }
    }
}
tasks.named("build") { dependsOn(verifyReleaseJar) }
