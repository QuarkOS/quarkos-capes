# QuarkOS Capes 1.0.0

Client mod. Java 21. Nothing in this repository is uploaded. `scripts/release.sh` (and `./gradlew releaseAll -Prelease=true`) only writes `dist/`.

Release jars are named `quarkos-capes-1.0.0+<loader>-<minecraft>.jar`. `dist/SHA256SUMS` is the checksum list. Those jars compile with the dev harness and the LabyMod try-on catalog removed: `DevFlags.HARNESS` is false, the try-on classes are behind `//? if dev_catalog`, and the jar also omits `client/harness/**`, `LabyCatalog`, `LabyBrowse`, `LabyWear`, `ShotCameraMixin`, and `ShotSkinMixin`. `verifyReleaseJar` checks the baked service URL, the license notices, and that those catalog classes are absent. The default URL is `http://127.0.0.1:8787`. That check rejects localhost unless the build passes `-PallowLocalService=true`. A published jar needs `-PserviceUrl=<production URL>` and must not use the local override. The current verification matrix is in `docs/STATUS.md`.

## Version matrix

| Minecraft | Fabric | Forge | NeoForge | Pack format | Fabric API | Cloth Config | Mod Menu | Forge build | NeoForge build |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1.20.1 | yes | yes | no | 15 | 0.92.12+1.20.1 | 11.1.136 (Fabric and Forge) | 7.2.2 optional | 47.4.29 | — |
| 1.21.1 | yes | yes | yes | 34 | 0.116.17+1.21.1 | 15.0.140 (Fabric, Forge, and NeoForge) | 11.0.5 optional | 52.1.20 | 21.1.265 |
| 1.21.4 | yes | yes | yes | 46 | 0.119.4+1.21.4 | 17.0.144 (Fabric jar only) | 13.0.4 optional | 54.1.22 | 21.4.161 |
| 1.21.8 | yes | yes | yes | 64 | 0.136.1+1.21.8 | 19.0.147 (Fabric jar only) | 15.0.2 optional | 58.1.26 | 21.8.57 |
| 1.21.11 | yes | yes | yes | 75 (64–94) | 0.141.6+1.21.11 | 21.11.153 (Fabric jar only) | 17.0.1 optional | 61.2.4 | 21.11.48 |

Fabric Loader is 0.19.5. Every Fabric jar requires Fabric API and embeds Cloth Config (GNU LGPLv3, shedaniel) plus OkHttp 3.14.9 and Okio 1.17.5. Mod Menu is optional and is not bundled. Forge 1.20.1 and 1.21.1, and NeoForge 1.21.1, embed Cloth Config the same way. Forge and NeoForge 1.21.4, 1.21.8, and 1.21.11 draw the six categories with the mod's own screen, because those builds do not embed Cloth Config. NeoForge is a ModDevGradle project (`neoforge/`) with `net.neoforged` events and `neoforge.mods.toml`. It reuses the chiseled cape sources.

Feature code is the same on every version. Version-specific cape layers, model parts, render types, blit calls, and screens go through `McBridge` and the stonecutter branches in the mixins.

### Not built

NeoForge 1.20.1 is not in this set. The NeoForge project is ModDevGradle for 1.21.1, 1.21.4, 1.21.8, and 1.21.11. Minecraft 1.20.1 stays Fabric and Forge only.

Forge 1.20.1 ships Mixin 0.8.5, which does not accept a mixin config compatibility level of `JAVA_21`. That one jar's mixin config says `JAVA_17`. The classes are still the Java 21 build. Fabric 1.20.1 keeps `JAVA_21` because Fabric Loader's Mixin understands it.

A version or loader that fails to build, fails its unit tests, or cannot show a third-person cape in a headless client is removed from `dist/` and noted in the launch report. The table above is the intended set; the report is the set that shipped.

## Build

```
./gradlew :1.21.11:test
scripts/release.sh -PallowLocalService=true
```

`scripts/release.sh` is `./gradlew releaseAll -Prelease=true`. Until a production service URL exists, add `-PallowLocalService=true` or `verifyReleaseJar` (wired to `build`) fails on the localhost default. Fabric entries in `dist/` are the intermediary `remapJar` outputs, with Cloth Config, OkHttp, and Okio nested under `META-INF/jars`. Forge entries are the ForgeGradle jars. The Forge 1.20.1 jar is rewritten to SRG names after compile. NeoForge entries are the ModDevGradle jars, with OkHttp and Okio under `META-INF/jarjar` (and Cloth Config on 1.21.1). Dev runs (`runClient`, unit tests) keep the harness. Do not pass `-Prelease=true` for a client screenshot: the harness is gone from that compile.

The 25-flow client harness is `./gradlew :1.21.11:runClient -Ponly=flows -PserverUrl=http://127.0.0.1:8787 -Pshots=<absolute dir>`. It expects the local site on that URL. Shots land in the directory you pass. Link claiming is `PASS-SKIP` unless `-PlinkCode` is set.

GitHub Actions (`.github/workflows/build.yml`) runs `build` for every version on push, with `-PallowLocalService=true` so the unset production URL does not fail the jar check. Gradle on that runner is Java 25; the mod still compiles for Java 21. The release job runs only on manual `workflow_dispatch`, uploads `dist/` as an artifact, and does not publish. The workflow has no tokens.

## Modrinth

One file per game version per loader. Side: client. Environment: client. Do not mark a file as supporting any other game version.

| File | Game versions | Loaders | Dependencies |
| --- | --- | --- | --- |
| `quarkos-capes-1.0.0+fabric-1.20.1.jar` | 1.20.1 | fabric | Fabric API required, Mod Menu optional |
| `quarkos-capes-1.0.0+forge-1.20.1.jar` | 1.20.1 | forge | none (Cloth Config is inside the jar) |
| `quarkos-capes-1.0.0+fabric-1.21.1.jar` | 1.21.1 | fabric | Fabric API required, Mod Menu optional |
| `quarkos-capes-1.0.0+forge-1.21.1.jar` | 1.21.1 | forge | none (Cloth Config is inside the jar) |
| `quarkos-capes-1.0.0+neoforge-1.21.1.jar` | 1.21.1 | neoforge | none (Cloth Config is inside the jar) |
| `quarkos-capes-1.0.0+fabric-1.21.4.jar` | 1.21.4 | fabric | Fabric API required, Mod Menu optional |
| `quarkos-capes-1.0.0+forge-1.21.4.jar` | 1.21.4 | forge | none |
| `quarkos-capes-1.0.0+neoforge-1.21.4.jar` | 1.21.4 | neoforge | none |
| `quarkos-capes-1.0.0+fabric-1.21.8.jar` | 1.21.8 | fabric | Fabric API required, Mod Menu optional |
| `quarkos-capes-1.0.0+forge-1.21.8.jar` | 1.21.8 | forge | none |
| `quarkos-capes-1.0.0+neoforge-1.21.8.jar` | 1.21.8 | neoforge | none |
| `quarkos-capes-1.0.0+fabric-1.21.11.jar` | 1.21.11 | fabric | Fabric API required, Mod Menu optional |
| `quarkos-capes-1.0.0+forge-1.21.11.jar` | 1.21.11 | forge | none |
| `quarkos-capes-1.0.0+neoforge-1.21.11.jar` | 1.21.11 | neoforge | none |

Project loaders: Fabric, Forge, NeoForge. Game versions: 1.20.1, 1.21.1, 1.21.4, 1.21.8, 1.21.11. NeoForge files start at 1.21.1. Client side only.

## CurseForge

Same files. Game versions use the CurseForge names `1.20.1`, `1.21.1`, `1.21.4`, `1.21.8`, and `1.21.11`. Each file gets exactly one of those versions.

| File | Game version | Mod loader | Java |
| --- | --- | --- | --- |
| `quarkos-capes-1.0.0+fabric-1.20.1.jar` | 1.20.1 | Fabric | 21 |
| `quarkos-capes-1.0.0+forge-1.20.1.jar` | 1.20.1 | Forge | 21 |
| `quarkos-capes-1.0.0+fabric-1.21.1.jar` | 1.21.1 | Fabric | 21 |
| `quarkos-capes-1.0.0+forge-1.21.1.jar` | 1.21.1 | Forge | 21 |
| `quarkos-capes-1.0.0+neoforge-1.21.1.jar` | 1.21.1 | NeoForge | 21 |
| `quarkos-capes-1.0.0+fabric-1.21.4.jar` | 1.21.4 | Fabric | 21 |
| `quarkos-capes-1.0.0+forge-1.21.4.jar` | 1.21.4 | Forge | 21 |
| `quarkos-capes-1.0.0+neoforge-1.21.4.jar` | 1.21.4 | NeoForge | 21 |
| `quarkos-capes-1.0.0+fabric-1.21.8.jar` | 1.21.8 | Fabric | 21 |
| `quarkos-capes-1.0.0+forge-1.21.8.jar` | 1.21.8 | Forge | 21 |
| `quarkos-capes-1.0.0+neoforge-1.21.8.jar` | 1.21.8 | NeoForge | 21 |
| `quarkos-capes-1.0.0+fabric-1.21.11.jar` | 1.21.11 | Fabric | 21 |
| `quarkos-capes-1.0.0+forge-1.21.11.jar` | 1.21.11 | Forge | 21 |
| `quarkos-capes-1.0.0+neoforge-1.21.11.jar` | 1.21.11 | NeoForge | 21 |

Relations: Fabric files require Fabric API. Mod Menu is optional on Fabric. Forge and NeoForge files have no required mod. The 1.20.1 and 1.21.1 Forge files, and the 1.21.1 NeoForge file, embed Cloth Config and do not list it as a separate relation. Environment: client.

## Changelog 1.0.0

First release of the client.

- Local cape: flat, frame strip, cloth, and a cube cloak. Bundled samples are bands, slide, and the fitted cloak.
- Settings with My Cape, Style, Physics, Sources, Gallery, Advanced, and Browse. Press O, or use Mod Menu on Fabric. Cloth Config on Fabric and on Forge/NeoForge before 1.21.2; native controls after that.
- Read-only OptiFine, LabyMod, MinecraftCapes, Cosmetica, and Cloaks+ toggles, default off. Offline mode is the default and sends no HTTP. Browse and Gallery offer Go online.
- Gallery search, equip, publish, unpublish, and report against a sharing service you run. Browse can search the website catalog, wear or unwear a cape, and claim a link code. Other players' website wearing references are shown when Show others is on.
- A 2:1 cape image is used on the elytra. On 1.21.1 through 1.21.10 the custom elytra skin is cached per player. The cape mesh hides while elytra is equipped.
- On 1.21.11, wave and model capes use `entityCutoutNoCull` so a shader pack does not drop them on the armor pass. Flat single-frame capes stay on the vanilla cape path. If another mod removes the vanilla cape layer, a fallback layer that is not itself a `CapeLayer` draws the selected cape.
- Fabric and Forge for 1.20.1, 1.21.1, 1.21.4, 1.21.8, and 1.21.11. NeoForge for 1.21.1 (21.1.265), 1.21.4 (21.4.161), 1.21.8 (21.8.57), and 1.21.11 (21.11.48).
- Release jars do not contain the dev client harness or the LabyMod try-on catalog. `verifyReleaseJar` rejects a localhost service URL unless `-PallowLocalService=true`.
