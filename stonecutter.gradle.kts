plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "1.21.11"

stonecutter parameters {
    // Root branch id is empty. Named branches use their id ("forge").
    val loaderName = branch.id.ifEmpty { "fabric" }
    constants.match(loaderName, "fabric", "forge", "neoforge")
    constants.put("dev_catalog", providers.gradleProperty("devCatalog").orNull == "true")
    replacements {
        // Active sources are written for 1.21.11, which already calls the class Identifier.
        string(current.parsed < "1.21.11") {
            replace("Identifier", "ResourceLocation")
            replace(".identifier()", ".location()")
        }
        // Forge and NeoForge run official names in dev. Forge 1.20.1 production still
        // runs SRG; the 1.20.1 jar task rewrites these mixin targets after compile.
        string(branch.id == "forge" || branch.id == "neoforge") {
            replace("@Mixin(", "@Mixin(remap = false, value = ")
            replace("@Inject(", "@Inject(remap = false, ")
            replace("@Redirect(", "@Redirect(remap = false, ")
            replace("@Accessor(\"", "@Accessor(remap = false, value = \"")
            replace("@Invoker(\"", "@Invoker(remap = false, value = \"")
        }
    }
}
