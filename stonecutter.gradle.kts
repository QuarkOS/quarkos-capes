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
        string(current.parsed >= "26.1") {
            replace("net.fabricmc.fabric.api.client.keybinding.v1", "net.fabricmc.fabric.api.client.keymapping.v1")
            replace("KeyBindingHelper", "KeyMappingHelper")
            replace("registerKeyBinding", "registerKeyMapping")
            replace("renderWidget", "extractWidgetRenderState")
            replace("renderContents", "extractContents")
            replace("renderBackground(", "extractBackground(")
            replace(".render(graphics,", ".extractRenderState(graphics,")
            replace("graphics.drawString(", "graphics.text(")
            replace(".resizeDisplay()", ".resizeGui()")
        }
        string(current.parsed >= "26.2") {
            replace(".getOverlay()", ".gui.overlay()")
            replace("client.setOverlay(", "client.gui.setOverlay(")
            replace(".gui.getChat()", ".gui.hud.getChat()")
            replace(".getToastManager()", ".gui.toastManager()")
        }
        string(current.parsed >= "26.3") {
            replace("world.getClientWorld().waitForChunksRender()", "world.getConnection().waitForChunksRender()")
        }
        regex(current.parsed >= "26.2") {
            // Match Minecraft receivers and keep the dev.quarkos.capes.client.screen package intact.
            val client = "(?<=(?<![A-Za-z0-9_.])(?:client|mc|minecraft)|Minecraft\\.getInstance\\(\\))"
            replace("$client\\.screen\\b(?!\\()", ".gui.screen()", "$client\\.gui\\.screen\\(\\)", ".screen")
            replace("$client\\.setScreen\\(", ".gui.setScreen(", "$client\\.gui\\.setScreen\\(", ".setScreen(")
        }
        regex(current.parsed >= "26.1") {
            replace(
                "\\bvoid render(?=\\(GuiGraphics(?:Extractor)? graphics,)",
                "void extractRenderState",
                "\\bvoid extractRenderState(?=\\(GuiGraphics(?:Extractor)? graphics,)",
                "void render",
            )
            replace(
                "(?<![A-Za-z0-9_])GuiGraphics\\b",
                "GuiGraphicsExtractor",
                "(?<![A-Za-z0-9_])GuiGraphicsExtractor\\b",
                "GuiGraphics",
            )
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
