# Status

One tree. The 14-target Fabric, Forge, and NeoForge line and the Sol `release/v1.0.0` line at `2d3214d` are merged. Nothing here is published.

Where the cape ports overlapped, one implementation stayed:

- 1.21.11 wave and model capes use `entityCutoutNoCull`. Other kinds stay on `armorCutoutNoCull`. A flat single-frame cape still uses the vanilla cape path.
- The fallback when another mod removes the vanilla cape layer is `MissingCapeLayer`, a `RenderLayer` that is not a `CapeLayer`. `MissingCapeHook` adds it on Fabric, Forge, and NeoForge.
- The 1.21.1 through 1.21.10 custom elytra skin uses Sol's `ElytraSkinCache` (`CapeBook.ELYTRA_SKINS`).

## Unit tests

`test` on every target, `-PallowLocalService=true -Prelease=true`. 81 tests each, 0 failures, 0 skips. 1,134 executions.

| Target | Tests | Result |
| --- | --- | --- |
| Fabric 1.20.1, 1.21.1, 1.21.4, 1.21.8, 1.21.11 | 81 | pass |
| Forge 1.20.1, 1.21.1, 1.21.4, 1.21.8, 1.21.11 | 81 | pass |
| NeoForge 1.21.1, 1.21.4, 1.21.8, 1.21.11 | 81 | pass |

## Release jars

`releaseAll -Prelease=true -PallowLocalService=true`, then `verifyReleaseJar` on all 14. Every jar baked `serviceUrl=http://127.0.0.1:8787`, passed the local-URL override, contained the license notices, and contained no `module-info.class`, harness classes, Laby try-on classes, or shot mixins. The Forge 1.20.1 jar is SRG (`m_6494_` and the access transformer). `dist/` is not committed. Checksums from `dist/SHA256SUMS`:

| File | SHA-256 |
| --- | --- |
| `quarkos-capes-1.0.0+fabric-1.20.1.jar` | `68aa7ea2cb126b116be770374e258889d942a7df395b60f48d2058462d3b4ef6` |
| `quarkos-capes-1.0.0+fabric-1.21.1.jar` | `16aa5584a5394ddcbef6ccac48a3191ef8c21ad95bfaa92b75101819afd6e6c6` |
| `quarkos-capes-1.0.0+fabric-1.21.4.jar` | `77c6f6faa5276598fa4b4c8d753cbbaad4b2851d33cb7a7f13c97931d5604d28` |
| `quarkos-capes-1.0.0+fabric-1.21.8.jar` | `fbb8e4808967ab3c3318109605cee00a193420e08cad7dbce6d7025c57cdeb42` |
| `quarkos-capes-1.0.0+fabric-1.21.11.jar` | `303a91b4f7ad38680be9637c2a05c075e6fb5ae1ff11ce97a5aef4202abac83b` |
| `quarkos-capes-1.0.0+forge-1.20.1.jar` | `209dca52f43a15bf7b19f9adf3f90614eae46319d9e05a465b60b8c868eef588` |
| `quarkos-capes-1.0.0+forge-1.21.1.jar` | `b19dc09a6c53113b3ac39b2a7abf24a5004ec740ec36ac3fce588051c59a72e9` |
| `quarkos-capes-1.0.0+forge-1.21.4.jar` | `03bfe6a6e71504cf356f197f381d537c12e65e33f0af53d222b9a7f502bef45b` |
| `quarkos-capes-1.0.0+forge-1.21.8.jar` | `1af0c74901bf5aeba4e941d0febceffafdd9e80b1b87a8a88c11da4700701b68` |
| `quarkos-capes-1.0.0+forge-1.21.11.jar` | `f5dce398f51901f44274a9293120afd8208d44a9a2a92d75acaa2196662ad161` |
| `quarkos-capes-1.0.0+neoforge-1.21.1.jar` | `d53e546a894ddeebb044a5c1aee459acf8d4db8a2ce39c3f11570cc78f429180` |
| `quarkos-capes-1.0.0+neoforge-1.21.4.jar` | `7145e1e3c7f98b3dd19b5a632dc0da0f4cc24751bb0e7844119a4dfe788a337c` |
| `quarkos-capes-1.0.0+neoforge-1.21.8.jar` | `272576e9feb154006df28788b864a0d0db5478a12b074d8002e5eaeb4f8c6403` |
| `quarkos-capes-1.0.0+neoforge-1.21.11.jar` | `277fbe22ec5363c10fb69931fb1728fae17198132c68c198f1f0e4dba82a8b3b` |

## Flow harness

`runClient -Ponly=flows -PserverUrl=http://127.0.0.1:8787`, shots under `out/flows/<loader>-<mc>/`. The bundle has no site server jar, so nothing was listening on port 8787. No `-PlinkCode`. Exit 0 on every run. 25 flows each.

| Client | Pass | Fail |
| --- | --- | --- |
| Fabric 1.21.1 | 18 | 7 |
| Fabric 1.21.11 | 18 | 7 |
| Forge 1.21.1 | 18 | 7 |
| Forge 1.21.11 | 18 | 7 |
| NeoForge 1.21.11 | 18 | 7 |

The same seven flows failed on every client, because they call the site or need a Mojang session: `publish-unpublish`, `gallery-search`, `browse-page`, `wear`, `unwear`, `other-player`, `browse-offline-cta`. `link-claim` is `PASS-SKIP` (no link code) and is one of the 18 passes. The other passes are the offline screens, samples, styles, physics, GUI sweep, survival inventory, first person, and the absent dev catalog.

## Forge release boots

portablemc, real installers, the merged release jars:

| Jar | Installer | Result |
| --- | --- | --- |
| `quarkos-capes-1.0.0+forge-1.21.1.jar` | Forge 1.21.1-52.1.20 | pass, third-person cloak |
| `quarkos-capes-1.0.0+forge-1.21.11.jar` | Forge 1.21.11-61.2.4 | pass, third-person cloak |

Shots: `docs/prod-smoke/forge-1.21.1.png`, `docs/prod-smoke/forge-1.21.11.png`. The other twelve production-smoke rows were not rerun after this merge.
