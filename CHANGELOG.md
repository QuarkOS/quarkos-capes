# Changelog

## 1.0.0

- Fabric and Forge client for Minecraft 1.21.1 and 1.21.11.
- Local cape with flat, frame-strip, cloth, and cube styles. Bundled samples are bands, slide, and a fitted cloak.
- Seven tabs: My Cape, Style, Physics, Sources, Gallery, Advanced, and Browse. Cloth Config on Fabric and Forge 1.21.1; native controls on Forge 1.21.11. Single style and physics selectors, with a player preview on roomy layouts.
- Mod Menu can open the same screen. It is optional and not bundled.
- Read-only OptiFine, LabyMod, MinecraftCapes, Cosmetica, and Cloaks+ toggles, default off. Offline mode is default on and sends no HTTP. LabyMod reads another player's equipped cape or cloak at runtime and caches the PNG locally. A vanilla-ratio texture is kept, a 22×17 net is placed on that atlas, and the vanilla cape draws the outside, the inside, and the edges. A dev catalog of those public lists is off unless `CAPES_DEV_CATALOG` is exactly `true`.
- Gallery search, equip, publish, unpublish, and report against the configured sharing service.
- Website catalog browsing, source filters, preview, wear/unwear, website link-code claiming, and other-player wearing references.
- Offline Browse and Gallery states offer Go online. Offline remains the default.
- The default service URL remains `http://127.0.0.1:8787` in `CapeCalls.DEFAULT_SERVICE_URL` until a production site URL is known.
- Cape image on the elytra when the image is 2:1. The cape mesh hides while elytra is equipped.
- OkHttp and Okio are jar-in-jar. Gson is the copy Minecraft already provides.
