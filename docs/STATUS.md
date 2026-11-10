# Status

One tree. The 14-target Fabric, Forge, and NeoForge line and the Sol `release/v1.0.0` line at `2d3214d` are merged. Nothing here is published.

Where the cape ports overlapped, one implementation stayed:

- 1.21.11 wave and model capes use `entityCutoutNoCull`. Other kinds stay on `armorCutoutNoCull`. A flat single-frame cape still uses the vanilla cape path.
- The fallback when another mod removes the vanilla cape layer is `MissingCapeLayer`, a `RenderLayer` that is not a `CapeLayer`. `MissingCapeHook` adds it on Fabric, Forge, and NeoForge.
- The 1.21.1 through 1.21.10 custom elytra skin uses Sol's `ElytraSkinCache` (`CapeBook.ELYTRA_SKINS`).

## Unit tests

`test` on every target, `-PallowLocalService=true -Prelease=true`. 85 tests each, 0 failures, 0 skips. 1,190 executions. The four new tests are `ReleaseServicePolicyTest` (the service-URL guard, including the local-host override) and `PlayerCopyTest` (player-facing strings).

| Target | Tests | Result |
| --- | --- | --- |
| Fabric 1.20.1, 1.21.1, 1.21.4, 1.21.8, 1.21.11 | 85 | pass |
| Forge 1.20.1, 1.21.1, 1.21.4, 1.21.8, 1.21.11 | 85 | pass |
| NeoForge 1.21.1, 1.21.4, 1.21.8, 1.21.11 | 85 | pass |

## Release jars

`releaseAll -Prelease=true -PallowLocalService=true`, then `verifyReleaseJar` on all 14. Every jar baked `serviceUrl=http://127.0.0.1:8787` from `quarkos.serviceUrl`, passed the local-URL override, contained the license notices, and contained no `module-info.class`, harness classes, Laby try-on classes, or shot mixins. `StoreCapture` is in the jar and stays idle unless `-Dquarkos_capes.store=true`. Homepage, source, and issues are the `gradle.properties` placeholders (`https://example.org/quarkos-capes` and the `/source` and `/source/issues` paths). The Forge 1.20.1 jar rewrites method and field calls to SRG, and mixin annotations to the SRG member for that owner and descriptor (`Gui.render` is `m_280421_`, `CapeLayer.render` is `m_6494_`). The access transformer stays SRG. `dist/` is not committed. Checksums are from this rebuild:

| File | SHA-256 |
| --- | --- |
| `quarkos-capes-1.0.0+fabric-1.20.1.jar` | `a9c7f1380957eed35ce7d5b3ff1dd26479634a7c40dd901c519b4c6274eb6275` |
| `quarkos-capes-1.0.0+fabric-1.21.1.jar` | `292c0fb1307331086694ce20c3e7edf2463f3a1ff1eea3af123a05bc8e017df8` |
| `quarkos-capes-1.0.0+fabric-1.21.4.jar` | `9f3e7a839c7513682e61b919ad30a0680a074c66511b251934866d2aca54ed5e` |
| `quarkos-capes-1.0.0+fabric-1.21.8.jar` | `243b72c91403043c01c5769febac0e634a1e47ef6c308a9ab617cc9c9c6428fe` |
| `quarkos-capes-1.0.0+fabric-1.21.11.jar` | `348eb5ff24b6477dd2b91aedd8d2298f6cf86f334abf8147e73b34e5dca9745f` |
| `quarkos-capes-1.0.0+forge-1.20.1.jar` | `9dace383e4f2fd74ace4569193290ef65e40d10cc65c936140ed0b2a6207824e` |
| `quarkos-capes-1.0.0+forge-1.21.1.jar` | `2a97fefa10366097f23fea2dd5a04527f96a5b839249c814fbe2b0ef978621d0` |
| `quarkos-capes-1.0.0+forge-1.21.4.jar` | `7e86dbdff56425cbd32e67c551f70f7f416249a587b23d56cf05a427198f176a` |
| `quarkos-capes-1.0.0+forge-1.21.8.jar` | `bafa33ebc3404369885f8c8baa0470ef6b2535507331034c75d48f74fe6029e7` |
| `quarkos-capes-1.0.0+forge-1.21.11.jar` | `b30bdf742c37074a851bf0c494c67f9a848bb6b7a0e40aa6cee83667f4a0c3ca` |
| `quarkos-capes-1.0.0+neoforge-1.21.1.jar` | `61143b52c64fd47b613026d264152e74e1b9e00c080b02e59e4fc313446447a9` |
| `quarkos-capes-1.0.0+neoforge-1.21.4.jar` | `7c8c6bc9eec149f574014d153c1d0e5bc17e005a686741cf658d15afe3ee9c64` |
| `quarkos-capes-1.0.0+neoforge-1.21.8.jar` | `382eba9c88beb0965d7f83087024d74cc614a6a01bf1e9ee950353ca17224d77` |
| `quarkos-capes-1.0.0+neoforge-1.21.11.jar` | `6e301e4b4a25ad7ee78894eda2ea3aa4cc1ff852c5d8cdb0570aba2641e80e51` |

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

## Two-client other-player

A vanilla 1.21.11 dedicated server on `127.0.0.1:25599` (`online-mode=false`, flat, creative) with the site on `127.0.0.1:8787`. Two portablemc processes, CapeStoreA and CapeStoreB, each loaded the Fabric 1.21.11 release jar. Wearing was seeded on the site (verdant-signal and violet-rift). Each client drew the other player's cape: the shipped frames logged 19 draws for A and 7 draws for B. `docs/store/shots/multiplayer-b.png` is A's back. `docs/store/shots/multiplayer-a.png` is B's back. No chat, no debug screen, no service URL.

## Store assets

`docs/store/icon-512.png` is the in-jar 64×64 icon scaled nearest-neighbor to 512×512. The seven 1920×1080 frames in `docs/store/shots/` come from that same Fabric 1.21.11 release jar: wardrobe, browse, wearing, elytra, shader (Iris, Sodium, Complementary Reimagined), and the two multiplayer frames. `StoreCapture` does not open Advanced, so the service URL field is not on screen. Nothing was published.
