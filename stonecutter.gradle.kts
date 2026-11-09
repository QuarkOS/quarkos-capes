plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "1.21.11"

stonecutter parameters {
    // Root branch id is empty. Named branches use their id ("forge").
    val loaderName = branch.id.ifEmpty { "fabric" }
    constants.match(loaderName, "fabric", "forge")
    replacements {
        // Active sources are written for 1.21.11, which already calls the class Identifier.
        string(current.parsed < "1.21.11") {
            replace("Identifier", "ResourceLocation")
            replace(".identifier()", ".location()")
        }
        // Forge runs official names, so Mixin must not look up a Fabric refmap.
        string(branch.id == "forge") {
            replace("@Mixin(", "@Mixin(remap = false, value = ")
            replace("@Inject(", "@Inject(remap = false, ")
            replace("@Redirect(", "@Redirect(remap = false, ")
        }
    }
}
