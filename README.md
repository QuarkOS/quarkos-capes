# QuarkOS Capes

Fabric and Forge client for Minecraft 1.20.1, 1.21.1, 1.21.4, 1.21.8, and 1.21.11, plus NeoForge for 1.21.1, 1.21.4, 1.21.8, and 1.21.11. It draws a cape you pick — flat, a frame strip, cloth, or cubes — and can read public cape APIs, a shared gallery, and the website catalog. The mod id is `quarkos_capes`. Author: Emilio Schwaiger. License: MIT. The 1.0.0 file list is in [docs/RELEASE.md](docs/RELEASE.md). The verification matrix is in [docs/STATUS.md](docs/STATUS.md).

No file in this tree is copied from Capes, Wavey Capes, or Better Capes. Those mods were read so the behavior could be reimplemented. Capes is LGPL-2.1-only, so its source is not included. Wavey Capes uses the tr7zw Protective License, which does not allow relicensing into MIT, so its source is not included. Better Capes is all rights reserved and has no source grant. This client ships no Mojang, Minecon, or Migrator textures.

Animated capes are free. There is no paywall.

## Run

Gradle runs on Java 25 (Loom 1.18). The jars target Java 21.

```
./gradlew :1.21.11:build
./gradlew :1.21.11:runClient
scripts/release.sh
```

Dev jars are named `quarkos_capes-fabric-<version>+<minecraft>.jar`. `scripts/release.sh` writes `dist/quarkos-capes-1.0.0+fabric-<minecraft>.jar`, the Forge jars, and the NeoForge jars, plus `dist/SHA256SUMS`. A localhost service URL is rejected unless you pass `-PallowLocalService=true`. `scripts/prod-smoke.sh` launches each of those jars in a production client and writes `docs/PROD-SMOKE.md`. It does not publish.

Press O in game, or use Mod Menu when it is installed, to open the settings screen. The seven tabs are My Cape, Style, Physics, Sources, Gallery, Advanced, and Browse. Fabric and Forge 1.21.1 use Cloth Config; Forge 1.21.11 uses native controls. Style and physics choices use single selectors. The live player preview hides on compact settings layouts to leave room for the list.

Offline mode is on by default. While it is on, the client makes no HTTP call, including provider reads and the gallery. Browse and Gallery offer a Go online button while offline. My Cape offers the bundled samples (bands, slide, and the cloak). Equipping a shared cape, once you are online, copies it locally and does not change who published it.

QuarkOS keeps your selected cape when Capes by CaelTheColher (`capes`), Wavey Capes (`waveycapes`), or MinecraftCapes (`minecraftcapes`) is installed. If a mod removes vanilla's cape layer, QuarkOS adds one fallback to each player renderer that lacks it; an existing cape layer prevents the fallback. The fallback uses the same selection, visibility and preview rules. There is no reliable per-player ownership check for the other mods, so their independent cape layers can still overlap ours; disable their cape drawing if that happens. 3D Skin Layers (`skinlayers3d`) does not disable cape drawing.

On 1.21.11 custom wave/model/frame meshes use the entity cutout no-cull pipeline for shader compatibility. Single-frame flat capes keep vanilla's entity-solid model path. First-person world rendering hides the camera entity's cape; inventory/settings previews remain visible. Recognized Iris/OptiFine shadow passes retain vanilla's renderer-driven visibility. Shader packs and combined cape mods still need in-game verification. The 1.21.1 custom elytra skin is cached per player and rebuilt only when its base skin or texture changes.

Advanced accepts HTTP or HTTPS service URLs and keeps the last valid URL while an invalid edit is shown. Service failures appear in the screen status; retries back off exponentially (up to one minute, or up to five minutes for a server Retry-After).

## Libraries

Our code stays MIT. These libraries are not relicensed.

| Library | Use | License | How it ships |
| --- | --- | --- | --- |
| Cloth Config (shedaniel) | Settings tabs and preview host (Fabric and Forge 1.21.1) | GNU LGPLv3 | Required, jar-in-jar |
| Mod Menu (TerraformersMC) | Optional button that opens the same screen | MIT | Optional, not bundled |
| OkHttp 3.14.9 and Okio 1.17.5 | Cape and gallery HTTP | Apache-2.0 | Jar-in-jar |
| Gson | JSON, already provided by Minecraft | Apache-2.0 | Not bundled |

Stonecutter builds both Minecraft versions. Architectury is not a dependency. GeckoLib is not used: the cloak is a few cubes in a small JSON model, and a skeletal library would not draw the frame strip any better.

## Forge

Forge is the `forge` Stonecutter branch. It shares `src` through `forge/src`. Dev compiles official names. The 1.20.1 production jar is rewritten to SRG before it ships. Forge versions are 47.4.29 (1.20.1), 52.1.20 (1.21.1), 54.1.22 (1.21.4), 58.1.26 (1.21.8), and 61.2.4 (1.21.11).

NeoForge is the `neoforge` branch, a ModDevGradle project that shares the same sources. It uses `net.neoforged` events and `neoforge.mods.toml`. Versions are 21.1.265 (1.21.1), 21.4.161 (1.21.4), 21.8.57 (1.21.8), and 21.11.48 (1.21.11). There is no 1.20.1 NeoForge jar.

The 1.20.1 and 1.21.1 Forge jars embed [Cloth Config](https://github.com/shedaniel/cloth-config) with Jar-in-Jar (`11.1.136` and `15.0.140`). Cloth Config is © shedaniel, LGPL-3.0-only, and is not copied into this MIT tree. Later Forge builds draw the same tabs, selectors, and live cape preview themselves. NeoForge 1.21.1 embeds Cloth Config the same way.

```
./gradlew :forge:1.21.1:build :forge:1.21.11:build
./gradlew :forge:1.21.11:runClient -Pharness=true
```

Press O in game to open the settings screen.

## Browse, wearing, and linking

Browse searches the website catalog, filters by source, previews a selection, and wears or unwears it using `/v1/me/wearing`. Website wearing references for other players are read from `/v1/players/wearing`; Show others controls their display. LabyMod references also require its provider toggle. The Link screen claims a single-use website code using the Minecraft session. See [the site API](docs/site-api/API.md) for endpoints, ownership, and authentication.

## Gallery

The default service URL is `http://127.0.0.1:8787` until the production site URL is known. It is defined once in `CapeCalls.DEFAULT_SERVICE_URL`; users can override `baseUrl` in Advanced. The client calls these routes on that base URL. Every JSON body uses `"api": 1`.

- `GET /v1/gallery?q=&style=&limit=&cursor=` returns `{ "api": 1, "items": [ { "id", "title", "style", "animateFrames", "frameCount" } ], "nextCursor" }`. `style` may be empty. Flat, frames, wave, and model entries are all listed.
- `GET /v1/gallery/{id}` returns the same metadata shape as a cape, plus `id` and `title`.
- `GET /v1/gallery/{id}/asset` returns the PNG.
- `PUT /v1/gallery/{id}` publishes the local player's own cape. `{id}` is that player's UUID. Multipart parts are `file`, `style`, `animateFrames`, and `model`.
- `DELETE /v1/gallery/{id}` unpublishes that player's cape.
- `POST /v1/gallery/{id}/report` sends `{ "reason": "Copied without credit." }`. The service at `475c104` rejects any field other than `reason`.

Provider reads, used only when offline mode is off, show-others is on, and that provider's toggle is on:

- OptiFine `http://s.optifine.net/capes/{name}.png`
- LabyMod equipped cosmetic `https://dl.labymod.net/capes/{uuid}` (the host redirects to the texture). If that image is missing, `https://laby.net/api/v3/user/{uuid}/textures` and then `https://texture.laby.net/{hash}.png` for the active cloak, or the active cape when there is no cloak. Responses are cached under `config/quarkos_capes/labymod/` and are not shipped in the jar. `X-RateLimit-*` is honored. The toggle stays off until a player turns it on.
- A dev catalog can page the public lists `https://laby.net/api/v3/search/textures/cape` and `https://laby.net/api/v3/search/textures/cloak` and preview one texture on the local player. It exists only when `CAPES_DEV_CATALOG` is exactly `true`. Release builds and `config/quarkos_capes.json` leave it unset, offline mode still sends no request, and no LabyMod texture is bundled.
- MinecraftCapes `https://api.minecraftcapes.net/profile/{uuid without dashes}`
- Cosmetica `https://api.cosmetica.cc/users/{uuid}/cape`
- Cloaks+ `http://161.35.130.99/capes/{name}.png`

The user agent is `QuarkOS-Capes/1.0.0`. Website wearing references take priority over legacy accepted capes, followed by enabled providers. The Mojang access token is sent only to `https://sessionserver.mojang.com/session/minecraft/join` during authenticated service login. It is not logged and not sent to a cape host.

## Credits

See [CREDITS.md](CREDITS.md). A draft listing for Modrinth and CurseForge is in [DISTRIBUTION.md](DISTRIBUTION.md). Nothing in this repository is uploaded there.

Release preparation: [checklist](docs/RELEASE-CHECKLIST.md) · [privacy](docs/PRIVACY.md). Set the packaged service default with `-PserviceUrl=<production URL>`; local parent validation builds require `-PallowLocalService=true` until that URL exists.
