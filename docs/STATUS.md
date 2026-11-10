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

`releaseAll -Prelease=true -PallowLocalService=true`, then `verifyReleaseJar` on all 14. Every jar baked `serviceUrl=http://127.0.0.1:8787`, passed the local-URL override, contained the license notices, and contained no `module-info.class`, harness classes, Laby try-on classes, or shot mixins. The Forge 1.20.1 jar rewrites method and field calls to SRG, and mixin annotations to the SRG member for that owner and descriptor (`Gui.render` is `m_280421_`, `CapeLayer.render` is `m_6494_`). The access transformer stays SRG. `dist/` is not committed. The Forge 1.20.1 checksum below is the rebuilt jar. The other thirteen checksums are unchanged:

| File | SHA-256 |
| --- | --- |
| `quarkos-capes-1.0.0+fabric-1.20.1.jar` | `68aa7ea2cb126b116be770374e258889d942a7df395b60f48d2058462d3b4ef6` |
| `quarkos-capes-1.0.0+fabric-1.21.1.jar` | `16aa5584a5394ddcbef6ccac48a3191ef8c21ad95bfaa92b75101819afd6e6c6` |
| `quarkos-capes-1.0.0+fabric-1.21.4.jar` | `77c6f6faa5276598fa4b4c8d753cbbaad4b2851d33cb7a7f13c97931d5604d28` |
| `quarkos-capes-1.0.0+fabric-1.21.8.jar` | `fbb8e4808967ab3c3318109605cee00a193420e08cad7dbce6d7025c57cdeb42` |
| `quarkos-capes-1.0.0+fabric-1.21.11.jar` | `303a91b4f7ad38680be9637c2a05c075e6fb5ae1ff11ce97a5aef4202abac83b` |
| `quarkos-capes-1.0.0+forge-1.20.1.jar` | `b72e8aa2d43dbdabebf25e23afcb9717bbb44e4d4ee992422f74369f5bbe36c2` |
| `quarkos-capes-1.0.0+forge-1.21.1.jar` | `b19dc09a6c53113b3ac39b2a7abf24a5004ec740ec36ac3fce588051c59a72e9` |
| `quarkos-capes-1.0.0+forge-1.21.4.jar` | `03bfe6a6e71504cf356f197f381d537c12e65e33f0af53d222b9a7f502bef45b` |
| `quarkos-capes-1.0.0+forge-1.21.8.jar` | `1af0c74901bf5aeba4e941d0febceffafdd9e80b1b87a8a88c11da4700701b68` |
| `quarkos-capes-1.0.0+forge-1.21.11.jar` | `f5dce398f51901f44274a9293120afd8208d44a9a2a92d75acaa2196662ad161` |
| `quarkos-capes-1.0.0+neoforge-1.21.1.jar` | `d53e546a894ddeebb044a5c1aee459acf8d4db8a2ce39c3f11570cc78f429180` |
| `quarkos-capes-1.0.0+neoforge-1.21.4.jar` | `7145e1e3c7f98b3dd19b5a632dc0da0f4cc24751bb0e7844119a4dfe788a337c` |
| `quarkos-capes-1.0.0+neoforge-1.21.8.jar` | `272576e9feb154006df28788b864a0d0db5478a12b074d8002e5eaeb4f8c6403` |
| `quarkos-capes-1.0.0+neoforge-1.21.11.jar` | `277fbe22ec5363c10fb69931fb1728fae17198132c68c198f1f0e4dba82a8b3b` |

## Flow harness

The site (`quarkos-capes` server) was running on `127.0.0.1:8787` with `CAPES_SEED=demo`, `CAPES_DEV_AUTH=true`, and a 16+ character `CAPES_DEV_AUTH_SECRET`. Clients were `CapeFlow`. `other-player` used the harness fake player `CapeFlowRemote` (offline UUID, signed dev complete, then `PUT /v1/me/wearing`). No `-PlinkCode`. Shots are under `out/flows/<loader>-<mc>/` (85 PNGs each). Exit 0. `CAPES_FLOWS pass=25 fail=0` on every client.

| Client | Pass | Fail | Link |
| --- | --- | --- | --- |
| Fabric 1.21.1 | 25 | 0 | PASS-SKIP |
| Fabric 1.21.11 | 25 | 0 | PASS-SKIP |
| Forge 1.21.1 | 25 | 0 | PASS-SKIP |
| Forge 1.21.11 | 25 | 0 | PASS-SKIP |
| NeoForge 1.21.11 | 25 | 0 | PASS-SKIP |

Publish, gallery search, browse, wear, unwear, the remote player, and the offline Browse "Go online" path passed. NeoForge `runClient` now forwards `CAPES_DEV_AUTH` and `CAPES_DEV_AUTH_SECRET`, the same way Fabric and Forge already did.

## Production smoke

`scripts/prod-smoke.sh` on all 14 `dist/` jars, portablemc, real installers. Each client loads a flat world, wears the bundled cloak, logs `quarkos_capes prod smoke ok`, and writes a third-person shot. 14 pass, 0 fail. Table and shots: `docs/PROD-SMOKE.md`, `docs/prod-smoke/`.

The first Forge 1.20.1 boot crashed in Mixin: annotations had been rewritten with one shared name for `render` (`m_6494_`, which is `CapeLayer.render`, not `Gui.render`), and the installer jar does not load a refmap. The jar rewrite now maps each mixin target through `mcp-srg.srg` for that owner and descriptor. After that rebuild, Forge 1.20.1-47.4.29 passed.
