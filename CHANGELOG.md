# Changelog

## 0.1.0

- Fabric client for Minecraft 1.21.1 and 1.21.11.
- Local cape with flat, frame-strip, cloth, and cube styles. Bundled samples are bands, slide, and a fitted cloak.
- Cloth Config screen with My Cape, Style, Physics, Sources, Gallery, and Advanced. The player preview stays on every category. Toggles read On and Off.
- Mod Menu can open the same screen. It is optional and not bundled.
- Read-only OptiFine, LabyMod, MinecraftCapes, Cosmetica, and Cloaks+ toggles, default off. Offline mode is default on and sends no HTTP. LabyMod reads another player's equipped cape or cloak at runtime and caches the PNG locally. A vanilla-ratio texture is kept, a 22×17 net is placed on that atlas, and the vanilla cape draws the outside, the inside, and the edges. A dev catalog of those public lists is off unless `CAPES_DEV_CATALOG` is exactly `true`.
- Gallery search, equip, publish, unpublish, and report against the local sharing service.
- Cape image on the elytra when the image is 2:1. The cape mesh hides while elytra is equipped.
- OkHttp and Okio are jar-in-jar. Gson is the copy Minecraft already provides.
