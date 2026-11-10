# Modrinth and CurseForge draft

Do not upload this file. It is the description to paste later.

## Summary

QuarkOS Capes is a Fabric, Forge, and NeoForge client mod for Minecraft 1.20.1, 1.21.1, 1.21.4, 1.21.8, and 1.21.11. NeoForge jars start at 1.21.1. Pick a local cape, play a frame strip, sim cloth, or wear a cube cloak. Read public cape APIs when you turn a provider on. Share a cape through your own gallery service. Browse the website catalog, wear or unwear a cape, claim a website link code, and display other players’ website wearing references. Animated capes are free. The per-file game versions and loaders are in `docs/RELEASE.md`.

The seven tabs are My Cape, Style, Physics, Sources, Gallery, Advanced, and Browse. Offline mode is on by default; Browse and Gallery offer Go online. Set the production default with `-PserviceUrl=<URL>`; `CapeCalls.DEFAULT_SERVICE_URL` reads the generated jar resource. `verifyReleaseJar` rejects localhost/example defaults unless `-PallowLocalService=true` is explicitly supplied for local validation. See [the release checklist](docs/RELEASE-CHECKLIST.md) and [privacy disclosure](docs/PRIVACY.md).

## Required

- Minecraft 1.20.1, 1.21.1, 1.21.4, 1.21.8, or 1.21.11, matching the jar
- Fabric Loader 0.16.0 or newer and Fabric API, or the Forge / NeoForge build named for that jar in `docs/RELEASE.md`
- Java 21
- Cloth Config on Fabric and Forge 1.21.1 (already inside the jar; GNU LGPLv3, author shedaniel); Forge 1.21.11 uses native settings

## Optional

- Mod Menu, if you want the mods list to open the same screen. The O key works without it.

## Embedded

- OkHttp 3.14.9 and Okio 1.17.5, Apache-2.0
- Cloth Config on Fabric and Forge 1.21.1, GNU LGPLv3, not relicensed

Gson is Minecraft's own copy. Architectury is not required. GeckoLib is not required.

## Links

Author: Emilio Schwaiger. License: MIT. This build is not published.
