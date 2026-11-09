# QuarkOS Capes 1.0.0

Client mod. Java 21. Nothing in this repository is uploaded. `scripts/release.sh` (and `./gradlew releaseAll -Prelease=true`) only writes `dist/`.

Release jars are named `quarkos-capes-1.0.0+<loader>-<minecraft>.jar`. `dist/SHA256SUMS` is the checksum list. Those jars compile with the dev harness and the LabyMod try-on catalog removed: `DevFlags.HARNESS` and `DevFlags.CATALOG` are false, so the call sites are eliminated, and the jar also omits `client/harness/**`, `LabyCatalog`, `LabyBrowse`, `LabyWear`, `ShotCameraMixin`, and `ShotSkinMixin`.

## Version matrix

| Minecraft | Fabric | Forge | NeoForge | Pack format | Fabric API | Cloth Config | Mod Menu | Forge |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1.20.1 | yes | yes | dropped | 15 | 0.92.12+1.20.1 | 11.1.136 (embedded on both loaders) | 7.2.2 optional | 47.4.29 |
| 1.21.1 | yes | yes | dropped | 34 | 0.116.17+1.21.1 | 15.0.140 (embedded on both loaders) | 11.0.5 optional | 52.1.20 |
| 1.21.4 | yes | yes | dropped | 46 | 0.119.4+1.21.4 | 17.0.144 (Fabric jar only) | 13.0.4 optional | 54.1.22 |
| 1.21.8 | yes | yes | dropped | 64 | 0.136.1+1.21.8 | 19.0.147 (Fabric jar only) | 15.0.2 optional | 58.1.26 |
| 1.21.11 | yes | yes | dropped | 75 (64–94) | 0.141.6+1.21.11 | 21.11.153 (Fabric jar only) | 17.0.1 optional | 61.2.4 |

Fabric Loader is 0.19.5. Every Fabric jar requires Fabric API and embeds Cloth Config (GNU LGPLv3, shedaniel) plus OkHttp 3.14.9 and Okio 1.17.5. Mod Menu is optional and is not bundled. Forge 1.20.1 and 1.21.1 embed Cloth Config Forge the same way. Forge 1.21.4, 1.21.8, and 1.21.11 draw the six categories with the mod's own screen, because those builds do not embed Cloth Config.

Feature code is the same on every version. Version-specific cape layers, model parts, render types, blit calls, and screens go through `McBridge` and the stonecutter branches in the mixins.

### Dropped

NeoForge is not in this release. The Forge project is ForgeGradle with `net.minecraftforge` events, `mods.toml`, and an access transformer. A NeoForge jar needs ModDevGradle, `net.neoforged` packages, a different event bus, and `neoforge.mods.toml`. That is a second loader, not a small shim, so it is left out rather than shipped half-wired. The versions that exist on NeoForge (20.4.x, 21.1.x, 21.4.x, 21.8.x, 21.11.x) are not built.

Forge 1.20.1 ships Mixin 0.8.5, which does not accept a mixin config compatibility level of `JAVA_21`. That one jar's mixin config says `JAVA_17`. The classes are still the Java 21 build. Fabric 1.20.1 keeps `JAVA_21` because Fabric Loader's Mixin understands it.

A version or loader that fails to build, fails its unit tests, or cannot show a third-person cape in a headless client is removed from `dist/` and noted in the launch report. The table above is the intended set; the report is the set that shipped.

## Build

```
./gradlew :1.21.11:test
scripts/release.sh
```

`scripts/release.sh` is `./gradlew releaseAll -Prelease=true`. Fabric entries in `dist/` are the intermediary `remapJar` outputs, with Cloth Config, OkHttp, and Okio nested under `META-INF/jars`. Forge entries are the ForgeGradle jars. Dev runs (`runClient`, unit tests) keep the harness and the catalog. Do not pass `-Prelease=true` for a client screenshot: the harness is gone from that compile.

GitHub Actions (`.github/workflows/build.yml`) runs `build` for every version on push. Gradle on that runner is Java 25; the mod still compiles for Java 21. The release job runs only on manual `workflow_dispatch`, uploads `dist/` as an artifact, and does not publish. The workflow has no tokens.

## Modrinth

One file per game version per loader. Side: client. Environment: client. Do not mark a file as supporting any other game version.

| File | Game versions | Loaders | Dependencies |
| --- | --- | --- | --- |
| `quarkos-capes-1.0.0+fabric-1.20.1.jar` | 1.20.1 | fabric | Fabric API required, Mod Menu optional |
| `quarkos-capes-1.0.0+forge-1.20.1.jar` | 1.20.1 | forge | none (Cloth Config is inside the jar) |
| `quarkos-capes-1.0.0+fabric-1.21.1.jar` | 1.21.1 | fabric | Fabric API required, Mod Menu optional |
| `quarkos-capes-1.0.0+forge-1.21.1.jar` | 1.21.1 | forge | none (Cloth Config is inside the jar) |
| `quarkos-capes-1.0.0+fabric-1.21.4.jar` | 1.21.4 | fabric | Fabric API required, Mod Menu optional |
| `quarkos-capes-1.0.0+forge-1.21.4.jar` | 1.21.4 | forge | none |
| `quarkos-capes-1.0.0+fabric-1.21.8.jar` | 1.21.8 | fabric | Fabric API required, Mod Menu optional |
| `quarkos-capes-1.0.0+forge-1.21.8.jar` | 1.21.8 | forge | none |
| `quarkos-capes-1.0.0+fabric-1.21.11.jar` | 1.21.11 | fabric | Fabric API required, Mod Menu optional |
| `quarkos-capes-1.0.0+forge-1.21.11.jar` | 1.21.11 | forge | none |

Project loaders: Fabric, Forge. Game versions: 1.20.1, 1.21.1, 1.21.4, 1.21.8, 1.21.11. Client side only.

## CurseForge

Same files. Game versions use the CurseForge names `1.20.1`, `1.21.1`, `1.21.4`, `1.21.8`, and `1.21.11`. Each file gets exactly one of those versions.

| File | Game version | Mod loader | Java |
| --- | --- | --- | --- |
| `quarkos-capes-1.0.0+fabric-1.20.1.jar` | 1.20.1 | Fabric | 21 |
| `quarkos-capes-1.0.0+forge-1.20.1.jar` | 1.20.1 | Forge | 21 |
| `quarkos-capes-1.0.0+fabric-1.21.1.jar` | 1.21.1 | Fabric | 21 |
| `quarkos-capes-1.0.0+forge-1.21.1.jar` | 1.21.1 | Forge | 21 |
| `quarkos-capes-1.0.0+fabric-1.21.4.jar` | 1.21.4 | Fabric | 21 |
| `quarkos-capes-1.0.0+forge-1.21.4.jar` | 1.21.4 | Forge | 21 |
| `quarkos-capes-1.0.0+fabric-1.21.8.jar` | 1.21.8 | Fabric | 21 |
| `quarkos-capes-1.0.0+forge-1.21.8.jar` | 1.21.8 | Forge | 21 |
| `quarkos-capes-1.0.0+fabric-1.21.11.jar` | 1.21.11 | Fabric | 21 |
| `quarkos-capes-1.0.0+forge-1.21.11.jar` | 1.21.11 | Forge | 21 |

Relations: Fabric files require Fabric API. Mod Menu is optional on Fabric. Forge files have no required mod. The 1.20.1 and 1.21.1 Forge files embed Cloth Config and do not list it as a separate relation. Environment: client.

## Changelog 1.0.0

First release of the client.

- Local cape: flat, frame strip, cloth, and a cube cloak. Bundled samples are bands, slide, and the fitted cloak.
- Settings with My Cape, Style, Physics, Sources, Gallery, and Advanced. The player preview stays on every category. Toggles read On and Off. Press O, or use Mod Menu on Fabric.
- Read-only OptiFine, LabyMod, MinecraftCapes, Cosmetica, and Cloaks+ toggles, default off. Offline mode is the default and sends no HTTP.
- Gallery search, equip, publish, unpublish, and report against a sharing service you run.
- A 2:1 cape image is used on the elytra. The cape mesh hides while elytra is equipped.
- Fabric and Forge for 1.20.1, 1.21.1, 1.21.4, 1.21.8, and 1.21.11.
- Release jars do not contain the dev client harness or the LabyMod try-on catalog.
