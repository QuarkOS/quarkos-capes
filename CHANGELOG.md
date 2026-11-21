# Changelog

## 1.0.0 — first public release as Quark's Capes

- Name: Quark's Capes (mod id `quarkos_capes`, jar names and slugs unchanged). New icon.
- Fabric 1.20.1, 1.21.1, 1.21.4, 1.21.8, 1.21.11 and 26.3; Forge 1.20.1, 1.21.1, 1.21.4, 1.21.8, 1.21.11; NeoForge 1.21.1, 1.21.4, 1.21.8, 1.21.11. Client only.
- Browse opens on a LabyMod gallery: LabyMod cloaks are loaded live from laby.net while you browse (online access on). Search, Popular/Newest/Name sort, paging. LabyMod cloaks are try-on only: shown on your own player, never uploaded or shown to others. Each one names its laby.net page. Official Minecraft capes are not offered as a gallery, and cloak uploads that copy Minecon/Mojang capes are hidden.
- Featured and Community sources come from the Quark's Capes service (api.emilioschwaiger.com): static and animated capes, Try on, Wear/Unwear for capes others with the mod can see after you sign in with your Minecraft account.
- New About screen (wardrobe tab) with credits, license, LabyMod attribution and the not-affiliated notices.
- Smoother 2D/3D cape motion (interpolated lift, cached animated mesh, smoother Wavy cloth). Wardrobe from the pause menu and the O key.
- Offline mode is still the default: no network requests until you choose Go online.
- Source and issues: https://github.com/QuarkOS/quarkos-capes

## 1.0.0 (internal pre-release notes)

- Fabric and Forge for 1.20.1, 1.21.1, 1.21.4, 1.21.8, and 1.21.11. NeoForge for 1.21.1, 1.21.4, 1.21.8, and 1.21.11. The file list, store versions, and loaders are in `docs/RELEASE.md`.
- Release jars (`scripts/release.sh`) omit the dev harness and the LabyMod try-on catalog. `verifyReleaseJar` rejects a localhost service URL unless `-PallowLocalService=true`.
- Website catalog, wear/unwear, link-code claiming, and other-player wearing. The current matrix is in `docs/STATUS.md`.

## 0.1.0

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
