# Capes bughunt audit

Initial audit recorded before implementation. Evidence line numbers refer to integ-base; final status and validation are appended below. No Gradle or Minecraft execution is available in this sandbox. All four Stonecutter targets require parent validation.

| ID | Rank / track | Title / evidence | Root cause and fix plan |
|---|---|---|---|
| V01 | P1 Visual | Custom capes bypass visibility and equipment rules (`CapeLayerMixin.java:42-66,94-137`). | Injection precedes vanilla invisible/spectator/elytra/cape-part checks; NONE falls through to vanilla. Guard custom paths, retain vanilla when eligible, test via world/inventory harness. |
| V02 | P1 Visual | Preview orientations disagree and mutate the live player (`McBridge.java:183-269`). | Two implementations, opposite back rotations, four rotations overwritten without restoration. Share preview implementation, restore entity pose, show 3/4 back by default; screen/inventory hooks. |
| V03 | P1 Visual | Custom cape attachment differs across versions (`CapeLayerMixin.java:61-65,133-137`; `CapeGeometry.java:145-154`). | 1.21.1 adds a pivot/6-degree transform while 1.21.11 adds none; simulated cloth adds another 3-pixel shoulder drop. Align attachment and crouching/chest armor offset; world shots plus pose checks need in-game verification. |
| V04 | P1 Visual | Cloth moves in world axes and can swing into torso (`ClothSim.java:67-82`; `WaveCloth.java:256-257`). | dx/dz supplied directly as forward/lateral; reverse motion points chain forward. Project velocity by player yaw, reject teleport deltas, constrain cloth behind body. Offline motion/stability tests. |
| V05 | P2 Visual/perf | Cloth updates only at 20 Hz; render builds a fresh pose (`ClothSim.java:29-49`; `CapeGeometry.java:139-183`). | Previous state already exists but is unused. Interpolate tick state, preserve fixed tick simulation, reuse scratch arrays; offline geometry and allocation tests. |
| V06 | P2 Visual | Cube lighting uses constant normals (`McBridge.java:293-300`). | All faces submitted with +Z normal and baked shade. Restore geometric normals and inspect day/night cubes; winding/intersection artifacts require parent screenshots. |
| V07 | P2 Visual | Arbitrary image padding distorts UVs; replaced GPU textures leak (`CapeTextures.java:50-91,113-116,158-175`). | Non-atlas PNGs padded without scaling UVs; clear removes only map references. Upload real dimensions, release dynamic textures on replacement/clear, preserve bundled resources. Harness repeated equip and HD/provider inspection. |
| U01 | P1 UX | Forge Gallery has no search/list/equip (`ForgeCapeScreen.java:94-98`; `ClothCapeScreen.java:92-96`). Fabric shared rows cannot equip (`CapeScreen.java:205-207,630-638`). | Integration predates Forge and leaves shared rows inert. Add minimal shared gallery screen with top search/local and shared rows/actions on Forge; connect Fabric shared equip and feedback. Harness per tab. |
| U02 | P1 UX | Forge physics controls overflow small screens (`ForgeCapeScreen.java:41-78`). | 12 x 22px rows starting at 56, fixed six 70px tabs (420px) at GUI width as low as 214. Add scrolling and responsive tab/preview layout. Test at requested sizes/scales with screen hooks. |
| U03 | P1 UX | Settings apply but disappear on Esc; action status silently absent (`CapeBoard.java:112-145`; `CapeHttp.java:94-95,117-118,143-151`; `ClothCapeScreen.java:331-349`). | Most mutations only touch memory; offline calls return without callback; Cloth discards callbacks. Persist on close/apply and show translated pending/error/success feedback. Harness reopen checks. |
| U04 | P2 UX | Hardcoded user text, clipped rows, missing tooltips (`CapeBoard.java:36,150-193`; `ForgeCapeScreen.java:69-71,106-113`; `CapeScreen.java:631-646,714`). | Literal labels/status, unbounded title+author, English search suggestion. Move text through en_us, trim rows, add action tooltips. Preserve existing GalleryRows English API tests. |
| U05 | P2 UX | Search races reopen closed screens (`CapeScreen.java:274-295`). | Async callbacks reference global query and always setScreen. Capture query/generation and ignore stale or closed requests; explicit loading/empty/offline state. Offline request logic tests/harness. |
| P01 | P1 Performance | Model parsed and tessellated each player/frame (`CapeDraw.java:54-55`; `CapeBook.java:152-161`). | No geometry cache. Bounded cache keyed by model/frame dimensions/frame; flat geometry cache; offline cache/allocation microbench. |
| P02 | P1 Performance | Per-frame cloth creates >60 side arrays, pose arrays (`CapeGeometry.java:139-141,171-174`; `WaveCloth.java:143-145,166-168`). | Disposable scratch work. Reuse per-state scratch and eliminate side arrays; only compute classic pose when selected; allocation microbench. |
| P03 | P1 Performance | Repeated polling queues unbounded work and uploads assets repeatedly (`CapeHttp.java:36-40,69-92,294-309`). | Single executor has unbounded queue; every 2 seconds enqueues refresh and another asset download. Single in-flight refresh, bounded work queue, avoid unchanged asset uploads and back off provider misses. Offline scheduling test. |
| P04 | P2 Performance | Player, cloth, texture, provider caches survive departure/disconnect (`PlayerFacts.java:44-75`; `ClothSim.java:34-40`; `CapeBook.java:41-42`). | Entries never removed, stale entity ids reused, dormant cloth retained forever. Prune by active world players and release related textures; reset on world replacement. Harness churn needed. |
| B01 | P1 Build | Configuration cache fails resource transforms and Forge JarJar (`build.gradle.kts:119-122`; `forge/build.gradle.kts:174-199,251-254`). | Execution closures capture Gradle script/Configuration/custom script classes. Capture primitive values, resolved input file collection and metadata text at configuration; keep archive paths/content unchanged. Parent runs all four builds twice with cache. |
| B02 | P2 Build/harness | Capture paths fixed to /cursor and missing isolated audit modes (`build.gradle.kts:128-168`; `forge/build.gradle.kts:277-310`; `ClientHarness.java:65-106`). | Add shots override after all defaults, mode/window/guiScale properties; isolated screen, inventory, world, bench harness entirely under harness package plus existing call wiring. |
| D01 | P1 Dev isolation | Catalog currently gated exactly true (`LabyCatalog.java:29-34`; all three settings sources guards). | Preserve gate, test false/missing/TRUE/1/yes produces no URLs/requests; prove UI uses same gate. Do not enable catalog in new audit modes. |

Additional inspection: animation uses wall-clock milliseconds (`StripFrames.index`), not frame count; fixed tick cloth cannot explode from a render frame-time spike, but needs teleport/world reset and interpolation. LabySource already contains TTL/rate-limit caching and uses background CapeHttp work; preserve normal worn-cape services independent of dev catalog. F1 should continue to hide HUD only (vanilla capes remain visible). Sleeping/riding/crawling/swimming/flight, armor fit, glowing outlines, first-person camera, thumbnail appearance, GUI auto scaling, and HD filtering need real client evidence; no unsupported claim of visual verification will be made.

## Implementation status

Audit was completed before code changes. The harness was implemented before fixes; its isolated patch is retained alongside this document. Final statuses follow below.

## Additional findings during implementation

| ID | Rank / track | Evidence and cause | Fix |
|---|---|---|---|
| V08 | P1 Visual | Original `CapeGeometry.flat:22-26` and `cloth:158-186` sampled the entire atlas, including unused padding and neighboring artwork. Static flat capes used vanilla UVs, so changing to animated/wavy changed the picture. | Sample columns 1–10 and rows 1–16 within each horizontal/vertical frame; scale thumbnails from that same panel. Offline UV tests; world/screens capture hooks. No sample PNG or model JSON changed. |
| U06 | P2 UX | Original `CapeActions.style:45-49` retained strip playback on selecting Flat; original `CapeBoard.sampleOn:63` used an in-memory sample id that did not follow reloads/gallery equips. | Set the explicit animation choice for every style; determine sample selection from the equipped image and gallery ownership. Strip playback regression test and settings screenshots. |
| P05 | P2 Performance | Original `OwnerCape.load:82,85` / `GalleryPages.detail:71-72` allocated delay arrays using an unchecked external frame count. | Bound metadata frame arrays to 256. Oversized gallery metadata regression test. |
| V09 | P2 Visual | `CapeLayerMixin` submits custom geometry without outline metadata; the 1.21.11 `SubmitNodeCollector.submitCustomGeometry` API exposes no outline-color argument. | NOT FIXED: a separate outline render pass needs client verification and is beyond a safe small change without graphics execution. Check glowing wavy/model capes in the parent client. |

## Final status

“FIXED” means implemented and covered by offline checks or the named harness observation; it does not claim an executed Minecraft capture. “NEEDS-IN-GAME-CHECK” identifies a visual/UI result or Gradle execution that this sandbox cannot verify.

| ID | Status | Result / evidence / remaining check |
|---|---|---|
| V01 | NEEDS-IN-GAME-CHECK | Invisible/spectator guards precede custom drawing on both versions. Elytra/cape-part gates retained; ordinary vanilla capes now keep their vanilla layer instead of becoming cloth. World mode adds invisible, elytra, spectator, first-person and crouched armor captures. |
| V02 | NEEDS-IN-GAME-CHECK | Both preview entry points share bounded rendering, restore all current/previous player rotations, and show a 3/4 back view. Version-specific inventory bases accounted for. Validate `screen-preview.png` and six tabs on both loaders. |
| V03 | NEEDS-IN-GAME-CHECK | Removed the duplicate shoulder/pivot offset; custom layers share the six-degree hang, crouch pivot and chest armor separation across versions. Standing/walking/crouch-armor captures available. Swimming/crawling/flying/riding/sleeping still require manual client pose checks. |
| V04 | FIXED | Player-space velocity and teleport/nonfinite displacement rejection; backwards movement and ripples stay behind torso. Direction, finite-state and 500-tick reverse-motion/length tests pass. |
| V05 | FIXED | Fixed-tick state is interpolated at render time; renders do not advance simulation. Per-state target/render/edge scratch reused. Interpolation and allocation tests pass; compare low/high FPS cloth visually. |
| V06 | NEEDS-IN-GAME-CHECK | Actual face normals submitted; removed duplicate baked directional shading; cloth normals follow bends. Day/night cube captures supplied. Inspect night darkness and overlapping faces before accepting lighting. |
| V07 | NEEDS-IN-GAME-CHECK | Uploads retain image dimensions; dynamic textures and split frames released on replacement/clear; bundled textures preserved. Inventory mode writes `texture-replacement.txt`: expected fixed result `retained=1 after_clear=0` for eight identical-pixel dynamic uploads (base retains eight). HD provider filtering still needs real textures/client. |
| V08 | FIXED | Frame-relative cape panel UVs and scaled thumbnail crops implemented. Horizontal/vertical strip and cloth UV tests pass; world/screens expose visible output. |
| V09 | NOT FIXED | Glowing outlines on custom geometry need a separate verified render pass; see additional findings. F1 retains vanilla behavior (HUD hidden, capes visible). |
| U01 | NEEDS-IN-GAME-CHECK | Forge Gallery now has a top search field, local sample thumbnails/equip, shared list/equip, paging, selected-item reporting, publish/unpublish and six-tab navigation. Fabric shared rows have real Equip actions. Both loaders use existing CapeHttp/GalleryPages and show offline/loading/error feedback. Online interaction needs the parent's service harness. |
| U02 | NEEDS-IN-GAME-CHECK | Forge tabs fit available width; physics rows page within screen height; both Cloth layouts reserve a preview column. Gallery rows and notes truncate to available width. Run the GUI sweep below and click through Forge Physics pages. |
| U03 | FIXED | Settings changes snapshot while the settings UI is open and when it closes, including Esc; writes run on a coalescing background writer. Explicit Save reports completion/failure; Save & Quit closes on successful completion. Offline/invalid actions always return feedback. Snapshot reload test passes; screen/inventory hooks exercise real UI. |
| U04 | FIXED | Labels, statuses, hints, source help and preview/physics names use en_us; action/toggle tooltips added. Existing public GalleryRows English formatting helpers remain unchanged for compatibility but UI search feedback uses translations. Literal translation-key audit finds no missing keys. |
| U05 | FIXED | Search requests capture query, generation, screen and category; stale/closed/switched-category results cannot reopen the UI. Loading and failure clear/replace stale results; error callbacks leave the user on the active screen. Exercise online search and Esc with parent service harness. |
| U06 | FIXED | Selecting Flat stops playback while preserving the strip texture; sample indicators follow equipped content instead of startup defaults. Regression test passes. |
| P01 | FIXED | Immutable model/flat meshes use a 128-entry cache; model JSON is absent from the steady-state draw path. Geometry equivalence, eviction, identity and allocation tests pass. |
| P02 | FIXED | Cloth step reuses target arrays; geometry reuses center/edge arrays; classic bend only computed when selected. Model <256 B/call, simulation <64 B/tick, smooth cloth <3000 B/draw verified with ThreadMXBean in generous regression assertions. |
| P03 | FIXED | One queued/running refresh; bounded service queue (64) and separate bounded two-worker download pool (128); per-player asset/provider in-flight deduplication; 60-second asset/provider backoff. UI work cannot queue behind the entire serial provider scan. Refresh gate test passes. HTTP execution was not exercised here. |
| P04 | FIXED | Player/entity maps prune departed players and clear on world change; cloth slots retire after 100 unseen ticks or missing facts. Corresponding remote/provider textures and metadata released. Provider-choice/model caches bounded at 128; remote texture key strings cached for known textures. Bench scene and texture-replacement observation available; long server churn remains a parent soak check. |
| P05 | FIXED | Gallery/local frame counts bounded before delay-array allocation; oversized metadata test passes. |
| B01 | NEEDS-IN-GAME-CHECK | Resource transforms capture local booleans/maps; Forge JarJar captures file inputs, primitive filename mapping and precomputed metadata text; Forge pack action captures text instead of script-defined node. Archive paths and metadata text preserved. Parent must run the four build tasks twice with configuration cache enabled. No Gradle executed here. |
| B02 | FIXED | `-Pshots` overrides all capture/compare defaults on both loaders. `only`, `guiScale`, width/height and bench properties forwarded. Four offline harness modes added, with asynchronous screenshot completion checked before exit. Standalone patch applies to integ-base and compiles for all four Java targets. |
| D01 | FIXED | Exact-true catalog gate preserved; no audit mode turns it on. JUnit covers false/empty/TRUE/1/yes/whitespace with zero planned URLs or requests. Screen harness inspects real widgets and all Cloth categories for hidden catalog controls, and asserts zero HTTP sends in isolated modes. |

Remaining observations: Sources explains offline/background behavior but has no per-provider progress or error telemetry; adding service diagnostics is deferred (P3). Persistent LabySource disk-cache retention and the dev-only catalog history were not redesigned to avoid interfering with the concurrent catalog work. Animation already uses elapsed milliseconds, and LabySource already rate-limits negative results; those mechanisms were preserved. No graphics claim has been made for armor fit, swimming/crawling/sleeping/riding, glowing outlines, HD filtering, auto GUI scale or resource reloads.

## Validation performed here

- Direct `javac --release 21 -proc:none` compilation using the cached official Minecraft/dependency jars: Fabric 1.21.1, Fabric 1.21.11, Forge 1.21.1, Forge 1.21.11. Stonecutter arms were selected in temporary source copies; no Gradle/Minecraft invocation.
- The same four direct compilations against **integ-base plus only the harness patch** passed. The patch's `git apply --check` against isolated base copies passed.
- Offline JUnit suite executed directly through JUnit Platform on both Fabric classpaths; **36 tests passed on each Fabric classpath (72 executions, zero failures/skips)**. Allocation regressions run on the local JDK through ThreadMXBean.
- `git diff --check` and en_us JSON/literal-key validation. Existing tests and sample assets retained.
- Parent still owns actual Stonecutter generation, Mixin annotation processing, Gradle configuration-cache reuse, client runs, and image comparison. No commits, pushes or history changes made.

## Parent commands

On a separate integ-base working copy, apply **only** `docs/bughunt-harness.patch` for “before” captures. It includes the two build scripts, four harness package files, and the single CapeHttp tick suppression call-site. The working-tree `QuarkosCapes.java` additionally contains the autosave fix; do not copy that entire file to base.

Build and test after applying the full working-tree fixes; repeat the first command unchanged to check cache reuse:

```bash
./gradlew :1.21.1:build :1.21.11:build :forge:1.21.1:build :forge:1.21.11:build --configuration-cache
./gradlew :1.21.1:build :1.21.11:build :forge:1.21.1:build :forge:1.21.11:build --configuration-cache
./gradlew :1.21.1:test :1.21.11:test --configuration-cache
```

Exact four-mode capture/benchmark matrix (set `capture_root` to `/tmp/capes-before` on base, `/tmp/capes-after` on the fixed tree):

```bash
capture_root=/tmp/capes-after
for target in :1.21.1 :1.21.11 :forge:1.21.1 :forge:1.21.11; do
  label="${target#:}"
  label="${label//:/-}"
  for mode in screen inventory world bench; do
    ./gradlew "${target}:runClient" -Pharness=true -Ponly="$mode" \
      -PguiScale=2 -Pwidth=1280 -Pheight=720 \
      -Pshots="$capture_root/$label/$mode" \
      -PQUARKOS_CAPES_BENCH=20 -PQUARKOS_CAPES_BENCH_PLAYERS=16 \
      -Pdisplay="$DISPLAY" -Pxauth="$XAUTHORITY"
  done
done
```

GUI sweep, including minimum window, auto and explicit scales:

```bash
for target in :1.21.1 :1.21.11 :forge:1.21.1 :forge:1.21.11; do
  label="${target#:}"
  label="${label//:/-}"
  for size in 854x480 1280x720 1920x1080 2560x1440; do
    for scale in 0 1 2 3 4; do
      ./gradlew "${target}:runClient" -Pharness=true -Ponly=screen \
        -PguiScale="$scale" -Pwidth="${size%x*}" -Pheight="${size#*x}" \
        -Pshots="/tmp/capes-ui/$label/$size/scale-$scale" \
        -Pdisplay="$DISPLAY" -Pxauth="$XAUTHORITY"
    done
  done
done
```

The client clamps GUI scales that would go below Minecraft's minimum logical resolution. `screen` writes exactly the six requested tab PNGs. `inventory` writes `inventory.png`, `screen-preview.png`, and the GPU replacement observation. `world` writes 16 `world-{flat,wavy,cube,animated}-{standing,walking}-{back,three-quarter}.png` images, `world-cube-night.png`, plus invisible/elytra/crouch-armor/spectator/first-person diagnostics. `bench` writes `bench-scene.png` and `bench.txt`, prints one `CAPES_BENCH` and one `CAPES_BENCH_OFF` line, with independent five-second warmups and default 20-second measurement windows, then exits 0. Allocation is render-thread allocation (including normal world/tick work); unsupported JVM counters report -1. It uses 16 client-side players by default; use `-PQUARKOS_CAPES_BENCH_PLAYERS=64` for a larger scene. Environment variables with the same names also work. No audit mode enables the dev catalog or sends HTTP requests.

## Changed files

Harness hooks (the build files also contain the configuration-cache fixes):

- `build.gradle.kts`
- `forge/build.gradle.kts`
- `src/main/java/dev/quarkos/capes/client/harness/AuditHarness.java`
- `src/main/java/dev/quarkos/capes/client/harness/ClientHarness.java`
- `src/main/java/dev/quarkos/capes/client/harness/HarnessChecks.java`
- `src/main/java/dev/quarkos/capes/client/harness/ShotView.java`
- `src/main/java/dev/quarkos/capes/QuarkosCapes.java`: only the single CapeHttp suppression line is part of the before patch.

Fixes:

- `src/main/java/dev/quarkos/capes/QuarkosCapes.java`
- `src/main/java/dev/quarkos/capes/client/McBridge.java`
- `src/main/java/dev/quarkos/capes/client/local/ClientSettings.java`
- `src/main/java/dev/quarkos/capes/client/local/LocalFiles.java`
- `src/main/java/dev/quarkos/capes/client/local/LocalSnapshot.java`
- `src/main/java/dev/quarkos/capes/client/local/OwnerCape.java`
- `src/main/java/dev/quarkos/capes/client/mixin/CapeLayerMixin.java`
- `src/main/java/dev/quarkos/capes/client/net/CapeHttp.java`
- `src/main/java/dev/quarkos/capes/client/net/GalleryPages.java`
- `src/main/java/dev/quarkos/capes/client/net/LabyBrowse.java`
- `src/main/java/dev/quarkos/capes/client/net/RefreshGate.java`
- `src/main/java/dev/quarkos/capes/client/render/CapeBook.java`
- `src/main/java/dev/quarkos/capes/client/render/CapeDraw.java`
- `src/main/java/dev/quarkos/capes/client/render/CapeTextures.java`
- `src/main/java/dev/quarkos/capes/client/render/ClothSim.java`
- `src/main/java/dev/quarkos/capes/client/render/PlayerFacts.java`
- `src/main/java/dev/quarkos/capes/client/screen/CapeActions.java`
- `src/main/java/dev/quarkos/capes/client/screen/CapeBoard.java`
- `src/main/java/dev/quarkos/capes/client/screen/CapeGalleryScreen.java`
- `src/main/java/dev/quarkos/capes/client/screen/CapeScreen.java`
- `src/main/java/dev/quarkos/capes/client/screen/CapeScreens.java`
- `src/main/java/dev/quarkos/capes/client/screen/CapeThumbs.java`
- `src/main/java/dev/quarkos/capes/client/screen/ClothCapeScreen.java`
- `src/main/java/dev/quarkos/capes/client/screen/ForgeCapeScreen.java`
- `src/main/java/dev/quarkos/capes/client/screen/GalleryScreen.java`
- `src/main/java/dev/quarkos/capes/sim/CapeGeometry.java`
- `src/main/java/dev/quarkos/capes/sim/WaveCloth.java`
- `src/main/resources/assets/quarkos_capes/lang/en_us.json`
- `src/test/java/dev/quarkos/capes/BughuntTest.java`

Audit/evidence: `docs/bughunt-audit.md`, `docs/bughunt-harness.patch`. No sample texture/model resource changed.

## Release pass

Scope: working tree only, based on the supplied bughunt and preview-orientation fixes. No Git command, Gradle invocation, Minecraft launch, network request, or server-repository edit was performed in this pass. The existing audit above remains the record of the previous fixes. Here, FIXED means implementation plus offline evidence; NEEDS-IN-GAME-CHECK means the implementation exists but requires the parent's actual build/service/client run.

| Item | Status | Implementation / remaining evidence |
| --- | --- | --- |
| A1 Catalog client | FIXED | `CatalogClient` implements all eight encoded filters, encoded detail paths, bounded 128-entry ETag/304 caches, concurrent GET dedupe, Retry-After/exponential 429/5xx backoff, permissive response fields, and injectable transport/clock. `CapeHttp.siteExchange` has 3-second connect / 8-second total call limits and a response-size bound. All calls use the existing bounded service/download pools. Fixture tests cover the contract. |
| A2 Browse | NEEDS-IN-GAME-CHECK | Seventh Browse category on Fabric/Cloth and Forge, source/kind/sort chips, search, paged thumbnail grid, wheel paging, Tab/Enter/Esc, loading/empty/offline/error/retry feedback, detail fetch and live back preview, Wear/Unwear. Direct texture URLs and actual decoded LabyMod atlas dimensions; LabyMod credit/non-affiliation in detail and tooltips. Parent must inspect layout and interaction. |
| A3 Wearing/auth | NEEDS-IN-GAME-CHECK | Read wearing when Browse opens; PUT/DELETE wearing and translated feedback. Production reuses challenge/Mojang join/complete. Harness-only dev auth calls `HarnessAuth.complete` and uses its returned session token. Sessions are scoped to service/player, expire locally, and invalidate on 401. Wear saves the downloaded item locally; Unwear clears it. Server interaction remains unexecuted here. |
| A4 Other players | NEEDS-IN-GAME-CHECK | Thirty-second wearing batches, every UUID chunked at 50, conditional GET, departure/world pruning, source/visibility/offline gates, metadata and hash-checked texture resolution. Legacy `/v1/capes` uploads and real `LabySource` remain fallback features. Parent must verify remote rendering and live moderation/removal. |
| A5 Linking | NEEDS-IN-GAME-CHECK | Browse opens the website-link screen; eight-character field, button and Enter claim with Minecraft session. Linked Minecraft name, invalid/expired code (400), and conflict (409) are translated. A fresh code is single use; parent supplies it per run. |
| A6 Service setting | FIXED | All new site operations use the Advanced service URL. Flow mode applies `quarkos_capes.serverUrl`, default `http://127.0.0.1:8787`. Both launch scripts forward serverUrl/linkCode and dev-auth environment. |
| A7 Fixtures/tests | FIXED | Six small API.md-derived fixtures under `src/test/resources/site`; 14 new tests for parsing/unknown fields, wearing/null players, filter/detail encoding, empty continuation pages, chunking, ETags/origin/auth isolation, error mapping, dedupe, backoff/recovery, bounded frame metadata and JSON writes. |
| B Version/metadata | NEEDS-IN-GAME-CHECK | Version 1.0.0, loader archive bases, authors/name/license/icon/contact placeholders, version-specific Fabric dependency minimums and Forge/Cloth dependencies. Expected outputs: `quarkos_capes-{fabric,forge}-1.0.0+{1.21.1,1.21.11}.jar`. Parent must inspect produced archives and expanded metadata. |
| B Dev isolation | NEEDS-IN-GAME-CHECK | Stonecutter `dev_catalog` constant follows only `-PdevCatalog=true`. All shipped call sites/UI compile out without it; Jar exclusions remove LabyCatalog/LabyBrowse/LabyWear/LabyCompare and nested classes, and resource filtering removes the two catalog-only translations. Existing exact-true runtime gate remains in dev code. `verifyReleaseJar`, attached to build, scans archive entry names, bytecode and resources. Offline release-class scans pass; actual Gradle task and remap output remain a parent check. |
| B Capture defaults | FIXED | Removed every hardcoded `/cursor/stores` default from both build scripts; root-relative build capture defaults and `-Pshots` retained. |
| D Preview | NEEDS-IN-GAME-CHECK | Retained the supplied c78143a math: back is 25 degrees on 1.21.1 and 155 degrees with the 1.21.11 inventory basis. Shared browser uses that same path. Parent must inspect the three-quarter cape view on both versions. |
| D Chat/tutorial | NEEDS-IN-GAME-CHECK | Harness completes/disables the tutorial and clears chat and toasts each tick before captures. No mod debug/chat-message API calls were added. Parent screenshots must establish clean output. |
| D Physics wording | FIXED | Model: Classic / Cloth / Cloth (soft) / Cloth (stiff); Look: Smooth / Blocky; Wind: On / Off, with explanatory tooltips. Stored movement/mesh/wind ids are unchanged. |
| D Loader parity | NEEDS-IN-GAME-CHECK | Shared Browse and Link screens on all four targets; native Forge My Cape now includes Publish/Unpublish; seven-category navigation and shared actions retained. Parent must inspect all tabs, especially Cloth scroll rows at small logical resolutions. |
| Prior V09 | NOT FIXED | Custom-geometry glowing outline limitation from the previous audit is unchanged; this release pass does not add an unverified outline pass. |

Flow mode is `-Ponly=flows` and enables the harness automatically. It runs the following numbered flows in order, writes a canonical `flow-<nn>-<name>.png` for each, and writes additional scene PNGs for sample/style/GUI sweeps. It prints one `CAPES_FLOW <name> PASS|FAIL <detail>` per numbered flow and final `CAPES_FLOWS pass=N fail=M`. Missing linkCode reports `PASS PASS-SKIP linkCode absent`. Observation failures are collected while later scenes/flows continue; final exit is 0 even with failures. Harness crashes or missing screenshot writes exit nonzero. Render observations include vanilla flat cape paths as well as custom meshes; counters are tracked per player in harness mode.

| Flow | Status | Observation implemented |
| --- | --- | --- |
| 01 open-keybind | NEEDS-IN-GAME-CHECK | Inject an O keymapping click and require a settings screen. |
| 02 My Cape | NEEDS-IN-GAME-CHECK | Open, assert widget bounds versus screen and preview, capture. |
| 03 Style | NEEDS-IN-GAME-CHECK | Same bounds checks and capture. |
| 04 Physics | NEEDS-IN-GAME-CHECK | Same bounds checks and capture. |
| 05 Sources | NEEDS-IN-GAME-CHECK | Same bounds checks and capture. |
| 06 Gallery | NEEDS-IN-GAME-CHECK | Same bounds checks and capture. |
| 07 Advanced | NEEDS-IN-GAME-CHECK | Same bounds checks and capture. |
| 08 bundled-samples | NEEDS-IN-GAME-CHECK | Equip bands/slide/cloak, preview and world captures, require local-player draw count >0 for each scene. |
| 09 styles | NEEDS-IN-GAME-CHECK | Flat/frames/wave/model world scenes with local draws >0. |
| 10 save-reopen | NEEDS-IN-GAME-CHECK | Actual async settings save, reload from disk, reopen, compare serialized settings. |
| 11 physics-slider | NEEDS-IN-GAME-CHECK | Find the actual gravity slider, click minimum, assert gravity=5. |
| 12 source-toggles | NEEDS-IN-GAME-CHECK | Toggle every provider off/on while Offline; assert no HTTP send-count increase, restore online/provider defaults. |
| 13 publish-unpublish | NEEDS-IN-GAME-CHECK | Publish local bands and unpublish through existing UI actions with translated success checks. Publishing includes a searchable title and frame metadata. |
| 14 gallery-search | NEEDS-IN-GAME-CHECK | Republish after flow 13, find own id, search its title and require that id in results. |
| 15 browse-page | NEEDS-IN-GAME-CHECK | Open Browse directly with source=ours; require a real nonempty page and check bounds. |
| 16 wear | NEEDS-IN-GAME-CHECK | Select and load detail/preview, use Browse Wear, require GET wearing source/id match, require world draw. |
| 17 unwear | NEEDS-IN-GAME-CHECK | DELETE wearing then GET must return null. |
| 18 other-player | NEEDS-IN-GAME-CHECK | Complete a second dev session, PUT its reference, spawn RemotePlayer with that UUID, request public batch, require remote cape draw. |
| 19 link-claim | NEEDS-IN-GAME-CHECK | Fresh `-PlinkCode` claim or explicit PASS-SKIP if absent. |
| 20 show-others | NEEDS-IN-GAME-CHECK | Reset draw counts, switch Show others off, require zero remote draws. |
| 21 gui-sweep | NEEDS-IN-GAME-CHECK | Six original tabs at 854x480 and 1920x1080, requested GUI scales 1/3/4, assert bounds and capture every scene; restore 1280x720 scale 2. Minecraft may clamp requested scales. |
| 22 survival-inventory | NEEDS-IN-GAME-CHECK | Send survival command and wait before constructing InventoryScreen; require it stays InventoryScreen and the player's cape draws. Previous inventory audit mode also now waits for survival before opening. |
| 23 first-person | NEEDS-IN-GAME-CHECK | Start third-person-front, inject F5, require first-person camera and no local cape draws. |
| 24 dev-catalog-absent | NEEDS-IN-GAME-CHECK | Inspect all original settings categories/widgets for dev catalog controls in normal builds. Build verification independently checks classes/references/translations. |

Validation in this pass:

- Direct Java 21 compilation of Stonecutter-selected release arms against cached Minecraft/dependency jars for all four targets; no Gradle, client, network or annotation processor execution. Javac outputs and arguments: `/tmp/capes-release-final/{fabric,forge}/{1.21.1,1.21.11}/`.
- Offline JUnit through JUnit Platform: **50/50 on Fabric 1.21.1 and 50/50 on Fabric 1.21.11; 100 executions, zero failures/skips**. This includes the previous 36 tests unchanged plus 14 new catalog tests. No server requests were made by these tests.
- Release bytecode/reference scans, release language filtering/JSON validation, complete literal translation-key validation, whitespace inspection. Actual jar verification, Stonecutter/Gradle configuration and runtime input/render/Mixin behavior still require parent execution.

Changed files for this release pass:

- Build/metadata: `build.gradle.kts`, `forge/build.gradle.kts`, `gradle/release.gradle.kts`, `stonecutter.gradle.kts`, `stonecutter.properties.toml`, `src/main/resources/fabric.mod.json`, `src/main/resources/META-INF/mods.toml`.
- Site integration: new `client/net/CatalogClient.java`, `client/net/SiteApi.java`, `client/screen/BrowseScreen.java`, `client/screen/LinkScreen.java`; changed `QuarkosCapes.java`, `client/net/CapeHttp.java`, `client/render/CapeBook.java`, and `client/screen/{CapeBoard,CapeScreen,CapeScreens,CapeGalleryScreen,ClothCapeScreen,ForgeCapeScreen}.java`. Java paths here are under `src/main/java/dev/quarkos/capes/`.
- Harness/isolation/counters: new `client/harness/FlowHarness.java`; changed `client/harness/{AuditHarness,ClientHarness,HarnessAuth,HarnessChecks}.java`, `client/render/CapeDraw.java`, `client/mixin/CapeLayerMixin.java`.
- Strings/tests/audit: `src/main/resources/assets/quarkos_capes/lang/en_us.json`, `src/test/java/dev/quarkos/capes/CatalogClientTest.java`, `src/test/resources/site/{catalog,error,link,players,wear-request,wearing}.json`, this document. No bundled cape PNG/model was changed. `McBridge.java`'s supplied preview fix was inspected and retained.

Parent commands for this release pass (run from this working tree except the server command, which uses the parent's server jar location):

```bash
export CAPES_DEV_AUTH=true
export CAPES_DEV_AUTH_SECRET='replace-with-your-local-secret-at-least-16-characters'
CAPES_SEED=demo CAPES_DEV_AUTH=true CAPES_DEV_AUTH_SECRET="$CAPES_DEV_AUTH_SECRET" \
  java -jar quarkos-capes-server-0.1.0.jar
```

Leave the service running on 127.0.0.1:8787. In a second shell export the same dev-auth values, then build/test and verify the four release archives (repeat build unchanged to check configuration-cache reuse):

```bash
./gradlew :1.21.1:build :1.21.11:build :forge:1.21.1:build :forge:1.21.11:build --configuration-cache
./gradlew :1.21.1:build :1.21.11:build :forge:1.21.1:build :forge:1.21.11:build --configuration-cache
./gradlew :1.21.1:test :1.21.11:test --configuration-cache
./gradlew :1.21.1:verifyReleaseJar :1.21.11:verifyReleaseJar :forge:1.21.1:verifyReleaseJar :forge:1.21.11:verifyReleaseJar
```

Exact flow launches, one per target. These choose a consistent offline harness identity (`CapeFlow`) on both loaders, forward the environment, and keep the developer catalog disabled. `-Pshots` should be absolute. Each run creates its own capture directory.

```bash
./gradlew :1.21.1:runClient -Ponly=flows -PserverUrl=http://127.0.0.1:8787 -Pshots=/tmp/capes-release/fabric-1.21.1 -Pdisplay="$DISPLAY" -Pxauth="$XAUTHORITY"
./gradlew :1.21.11:runClient -Ponly=flows -PserverUrl=http://127.0.0.1:8787 -Pshots=/tmp/capes-release/fabric-1.21.11 -Pdisplay="$DISPLAY" -Pxauth="$XAUTHORITY"
./gradlew :forge:1.21.1:runClient -Ponly=flows -PserverUrl=http://127.0.0.1:8787 -Pshots=/tmp/capes-release/forge-1.21.1 -Pdisplay="$DISPLAY" -Pxauth="$XAUTHORITY"
./gradlew :forge:1.21.11:runClient -Ponly=flows -PserverUrl=http://127.0.0.1:8787 -Pshots=/tmp/capes-release/forge-1.21.11 -Pdisplay="$DISPLAY" -Pxauth="$XAUTHORITY"
```

To exercise flow 19, append `-PlinkCode=<fresh-website-code>` to that run. Generate a fresh code for each target; consumed codes cannot be reused. Without it, the other 23 flows still run and link is reported PASS-SKIP. The gallery fixture is republished for search and intentionally remains available after flow 14. Developer try-on comparisons now additionally require `-PdevCatalog=true` alongside their existing runtime gate; normal release/flow runs must omit that property.


## Run 03

Working-tree edits only. No git, Gradle, Minecraft, server, or network commands were run. Fabric 1.21.11 remains the active source state: Forge, legacy-version, and dev-catalog arms stay commented. Direct javac also checks the unprocessed active `src/`, in addition to preprocessing and compiling all four targets.

| Parent item | Status | Change / remaining observation |
| --- | --- | --- |
| Flow 11: physics slider | FIXED | Finds Gravity by the Cloth entry’s field name and uses its real slider-entry setter, including when scrolled out of view. Native Forge uses the visible slider. Waits for application and checks the value after reopening. |
| Flow 12: provider toggles and online transition | FIXED | Presses the real provider Boolean-entry buttons, waits for render-time application, checks every provider and zero offline requests, then disables providers. Opens Advanced, presses the actual Offline toggle, asserts OFF, and saves before flows 13–20. |
| Flows 13–20: service/catalog/wearing | NEEDS-IN-GAME-CHECK | API.md and openapi.yaml use `source=ours`. Flow 14 republishes the gallery fixture; flow 15 checks the upload catalog and creates a valid `/v1/uploads/{id}` fixture if that catalog is empty. Service callbacks include HTTP status or exception type; each wait uses a 20-second wall-clock deadline and reports flow, action, screen, offline state and available error text. Parent must rerun service flows; no claim of runtime passes here. |
| Flow 25: browse-offline-cta | NEEDS-IN-GAME-CHECK | Added the named flow. Asserts offline Browse sends nothing and exposes Go online, presses the real button, asserts online access and catalog results, then checks returning to settings does not restore stale Offline state. Restores and saves the original settings at the end, including after an earlier step fails. |
| UI 1: offline Browse / Gallery | NEEDS-IN-GAME-CHECK | Centered offline title, explanatory line and Go online action in Browse, Fabric Gallery and shared Forge Gallery. Actions disable Offline, save and load. Parent should inspect positioning and click each loader’s Gallery CTA. |
| UI 2: Browse finish / preview / wearing | NEEDS-IN-GAME-CHECK | Added Browse capes heading, empty-preview instruction, applicability checks on Wear/Unwear, and Wearing title/None near actions (also on compact layouts). Parent should check live preview and current website wearing state. |
| UI 3: single selectors | FIXED | Replaced Style radio toggles with one enum selector; replaced Model, Look and Wind groups likewise on Fabric Cloth, Forge 1.21.1 Cloth and native Forge 1.21.11. Each choice has its own translated help. Existing ids/enums remain unchanged. Shared Flat selection now stops playback without replacing the strip PNG; regression tests cover this and physics persistence. |
| UI 4: truthful status | FIXED | My Cape summary renders from current settings each frame: Style, Source, Others visible/hidden, Online On/Off. Online uses the inverse of Offline. Changing Offline rebuilds Fabric categories; returning from Browse/Gallery refreshes retained Cloth settings widgets. |
| UI 5: menu background | NEEDS-IN-GAME-CHECK | Added a full-screen dark backing before Cloth controls on Fabric and Forge 1.21.1; both request Cloth’s standard blurred menu background. Native Forge and shared Gallery add backing over the standard Screen background; Browse/Link retain their standard Screen background plus dark surface. Live previews render above the backing. Parent should confirm HUD/name tags no longer interfere, especially on 1.21.11. |
| UI 6: compact layouts / tooltips | NEEDS-IN-GAME-CHECK | Settings and Browse hide the preview column below 480 GUI pixels wide or 300 high and give controls the full list width. Compact Cloth and native Forge selector/toggle help appears in the footer instead of a floating tooltip over controls. Link controls shrink within narrow screens. Parent must inspect the 854×480 scale-4 screenshots and hover Physics controls. |
| UI 7: sweep coverage | FIXED | Flow 21 now visits all seven tabs plus Link at both window sizes and scales 1, 3 and 4; each scene runs bounds/preview-overlap checks. Browse preview bounds match the drawn pane and compact layouts report no preview. |
| UI 8: release docs / service default | FIXED | README, CHANGELOG and DISTRIBUTION describe 1.0.0, both loaders/versions, seven tabs, website wearing/linking and other-player references. One URL literal lives in `CapeCalls.DEFAULT_SERVICE_URL`, currently `http://127.0.0.1:8787` pending the production URL; settings and harness defaults reference it. `DEFAULT_BASE` remains an alias for existing callers/tests. User-Agent now identifies 1.0.0. |
| UI 9: chat | FIXED | Source audit found no mod calls to sendSystemMessage, displayClientMessage or sendChatMessage. Flow results remain stdout diagnostics; no chat-output code was added. |

Changed files (Java paths are beneath `src/main/java/dev/quarkos/capes/`):

- UI: `client/screen/{BrowseScreen,CapeGalleryScreen,CapeScreen,ClothCapeScreen,ForgeCapeScreen,LinkScreen,CapeScreens,CapeBoard}.java`; new `client/screen/CapeChoices.java`; `QuarkosCapesForge.java` for the Forge 1.21.1 render hook; `src/main/resources/assets/quarkos_capes/lang/en_us.json`.
- Harness/service: `client/harness/{FlowHarness,HarnessAuth,ShareHarness}.java`, `client/net/{CapeCalls,CapeHttp,SiteApi}.java`, `client/local/ClientSettings.java`. ShareHarness and ClientSettings only switch their default URL references to the shared constant.
- Tests/docs: new `src/test/java/dev/quarkos/capes/CapeChoicesTest.java`; `README.md`, `CHANGELOG.md`, `DISTRIBUTION.md`, this audit.

Validation: direct javac (`--release 21`, cached dependencies, no Gradle) passes for Fabric/Forge × 1.21.1/1.21.11. Direct javac of active, unprocessed Fabric 1.21.11 source also passes. Offline JUnit: **52/52 on each of the four targets**, including all previous 50 and two new regressions; 208 successful executions, zero failures/skips. Literal translation keys are complete, JSON parses, and the default service URL occurs as one Java string literal. Compile/test logs and argument files are under `/tmp/capes-run03/`; the direct runners are `/tmp/capes_run03_compile.py` and `/tmp/capes_run03_tests.py`. Parent still owns actual Stonecutter/Gradle builds, release-jar checks, all 25 in-game flows and screenshot review.

## Run 04

Working-tree edits only; no git, Gradle, Minecraft, server, or network execution. Active sources remain Fabric 1.21.11 with Forge, older-version and dev-catalog arms commented.

| Item | Status | Change / remaining check |
| --- | --- | --- |
| Bands pointing over the head in preview | FIXED | `CapeBook` chooses QUAD for Bands, and `CapeLayerMixin` delegates its single frame to vanilla. Slide and Cloak instead use custom geometry. Cached 1.21.11 bytecode confirms `AvatarRenderer.extractCapeState` derives cape angles from the world cloak position, and `PlayerCapeModel` pitches by `6 + capeLean / 2 + capeFlap`. The preview changed the body orientation but retained those angles: vanilla permits lean=150 and flap=32, giving 113 degrees. `McBridge.preview` now clears `capeFlap`, `capeLean`, and `capeLean2` **after extraction, before GUI submission**, every frame, including the first frame after equipping a sample. 1.21.1 temporarily neutralizes current/previous cloak coordinates and walk distance, restoring them in `finally`. No world swing clamp was added. |
| Preview cloth simulation | FIXED | The GUI uses a fresh settled still-motion chain, independent of the player's UUID simulation, world velocity, cached gravity and previous sample. Legacy rendering has an explicit GUI scope; 1.21.11 tracks the extracted GUI state by identity and captures that decision for deferred geometry. GUI rendering no longer records its gravity into the world simulation or marks that simulation drawn. The regression test covers every movement mode, a falling world state, a corrupt previous preview, interpolation, and preservation of the world chain. |
| Bands / Slide / Cloak screenshots | NEEDS-IN-GAME-CHECK | Parent must inspect preview and world images on both versions/loaders, including switching Cloak → Bands and Slide → Bands while moving/falling. The vanilla reset is checked by source/bytecode inspection; direct tests do not execute Minecraft's renderer. |
| Native Forge My Cape actions / samples | FIXED | Samples have a thumbnail, name and Equip / Equipped button, with the equipped button disabled. Save, Publish and Unpublish are plain actions using the existing translated asynchronous status callbacks. Report and developer-only Previous / Next also use the action helper. Native Gallery already used plain actions; it now marks equipped sample/shared rows too. Flow 08 presses the native screen's identified sample button and checks the resulting equipped sample. Existing Save / Publish / Unpublish label lookup still matches the plain labels. |
| Native Forge footer | FIXED | Shared translation now says “Revert changes”; native button has a translated tooltip explaining restoration of the saved cape/settings. Footer widths fit the screen. Settings surfaces are opaque through the entire footer/hotbar region. |
| HUD / player names over settings, Browse and Link | FIXED | Opaque backing on all settings implementations. A required `Gui.render` injection suppresses HUD rendering while any cape settings screen is open. A required version-specific player-renderer injection suppresses name and scoreboard submission before either is drawn, including Browse/Link and GUI previews. Both injections are independent of harness flags. Parent must check the new injections actually apply and inspect remote-player screenshots. |
| Forge 1.21.1 “Widget overlaps preview: Browse” | NEEDS-IN-GAME-CHECK | The old preview started at y=44, in the tab strip. Forge Cloth now shares its actual draw bounds with `CapeScreens.previewBounds`: top=max(80, list top+8), bottom=height−84, with no preview on compact screens. This removes the tab-strip collision in the source geometry; parent flow 21 must verify every GUI scale. |
| Fabric production launcher / minimal boot mode | NEEDS-IN-GAME-CHECK | Added `prodClient` to each Fabric node using Loom's real production task. It loads the **remapJar output** and the published Fabric API distribution jar; it explicitly replaces the default mods collection and never supplies development class directories or Mod Menu. Embedded Cloth/HTTP dependencies come from the release jar. Boot waits for title/overlay completion, handles initial accessibility onboarding, injects the registered settings key mapping, requires the resulting screen/widgets, waits 11 more client ticks, resolves all configured Mixin targets, requires a production loader with Fabric API present and Mod Menu absent, requires six successful mod Mixin applications, prints `CAPES_BOOT PASS mixins=6 screen=<class>` and stops. Harness-detected failures print `CAPES_BOOT FAIL reason=…` and exit 1; loader/Mixin failures before harness entry still fail the production process with their original diagnostics. Neither a production boot nor Gradle task configuration was executed here. |
| Harness isolation in normal release use | FIXED | The client tick, camera hook, skin hook and Mixin application counter test `quarkos_capes.harness` before calling harness classes. The counter has no Minecraft references, so early Mixin application does not load the boot driver. Boot is routed before the existing world-creation harness; audit mode excludes boot. Diagnostics go only to stdout/stderr; no harness translation/UI strings were added. The ordinary settings key handler also accepts the title screen, enabling the same registered-key route without loading a world or requiring Mod Menu. |

Loom inspection: `stonecutter.properties.toml` requests `loomx.loom_version = "1.18-SNAPSHOT"`; the locally cached resolved plugin is `/home/box/.gradle/caches/modules-2/files-2.1/net.fabricmc/fabric-loom/1.18.4/8a1e6af4579c6c8e7a0069f222e376e360d78395/fabric-loom-1.18.4.jar`. ZIP inspection and direct `javap` confirm `net.fabricmc.loom.task.prod.ClientProductionRunTask`, its `mods`, `jvmArgs`, `programArgs`, `runDir`, and `useXVFB` APIs, and `RemapJarTask`. Its implementation launches Knot with the production Minecraft jar, loader, intermediary mappings and runtime libraries. Cached Minecraft `Main` bytecode on both versions also confirms that `--version` and `--accessToken` are required: the task supplies the node version and an offline token alongside its deterministic boot identity. No fallback install or simulated pass is used.

Changed files (Java paths below are under `src/main/java/dev/quarkos/capes/`):

- Build/resources: `build.gradle.kts`; `src/main/resources/quarkos_capes.mixins.json`; `src/main/resources/assets/quarkos_capes/lang/en_us.json`.
- Preview/render: `client/McBridge.java`, `client/render/{CapeBook,CapeDraw}.java`, `client/mixin/CapeLayerMixin.java`; new `sim/PreviewCloth.java`.
- Screens: `client/screen/{ForgeCapeScreen,ClothCapeScreen,CapeScreen,CapeScreens,CapeGalleryScreen,BrowseScreen,LinkScreen}.java`; `QuarkosCapesForge.java` (opaque Forge Cloth backing).
- Isolation/boot: `QuarkosCapes.java`, `client/harness/{ClientHarness,AuditHarness,FlowHarness}.java`, `client/mixin/{ShotCameraMixin,ShotSkinMixin}.java`; new `client/harness/{BootHarness,HarnessMixins}.java` and `client/mixin/{CapeMixinPlugin,SettingsHudMixin,SettingsNameTagMixin}.java`.
- Regression/audit: new `src/test/java/dev/quarkos/capes/PreviewClothTest.java`; this document.

Parent production commands, from the repository root, with `DISPLAY` / `XAUTHORITY` inherited from the parent's working graphical environment:

```bash
LIBGL_ALWAYS_SOFTWARE=1 ./gradlew :1.21.1:prodClient -Ponly=boot --no-configuration-cache
LIBGL_ALWAYS_SOFTWARE=1 ./gradlew :1.21.11:prodClient -Ponly=boot --no-configuration-cache
```

For an installed Xvfb environment, append `-Pxvfb=true` instead of supplying a display. Each node uses its own `build/prod-run` game directory and `logs/latest.log`. `prodClient` depends on `verifyReleaseJar` (which builds the archive). The passing output must include `CAPES_BOOT PASS mixins=6 screen=me.shedaniel.clothconfig2.gui.ClothConfigScreen` and a normal process exit. Mod Menu must be absent from the loader's mod list. Run the two commands separately; preserve complete console/loader logs on failure. Do not pass `-PdevCatalog=true`. The production task accepts only boot mode; existing 25-flow runs still use the `runClient` commands from the earlier audit sections.

Forge release boot procedure (parent execution): create a fresh Minecraft Launcher installation/game directory for Minecraft 1.21.1 + Forge **52.1.20**, and separately 1.21.11 + Forge **61.2.4**, using Java 21. Build `:forge:1.21.1:build :forge:1.21.11:build` and the matching `verifyReleaseJar` tasks, then copy only each node's release `quarkos_capes-forge-1.0.0+<minecraft>.jar` into that installation's `mods/` folder. The node archives live under `forge/versions/<minecraft>/build/libs/`; do not use sources/test archives or a Fabric jar. HTTP libraries and the legacy Cloth dependency are embedded, so start with an otherwise empty mods directory. Launch the matching Forge profile normally (no dev classpath, Gradle `runClient`, or harness properties), inspect `logs/latest.log` for Mixin/injection/dependency errors, open settings with O in-game and through Forge's config-screen entry, equip each sample in a world, and close/reopen settings. Check Bands hang, native actions/footer, name tags/HUD suppression and Forge 1.21.1 Browse tab geometry. A normal Forge launch must not print `CAPES_BOOT` or create harness worlds. This is a manual production verification procedure, **not a reported boot pass**.

Validation: `/tmp/capes_run03_compile.py` passes direct `javac --release 21` against cached dependencies for **Fabric / Forge × 1.21.1 / 1.21.11**. `/tmp/capes_run04_active.py` independently compiles all **unprocessed active `src/main/java`** for Fabric 1.21.11 successfully, including the new source files. `/tmp/capes_run03_tests.py` passes **53/53 tests on each of the four targets** (212 successful executions; zero failures/skips). Logs: `/tmp/capes-run04-{compile,active,tests}.log`; per-target arguments/classes/logs remain under `/tmp/capes-run03/`. JSON parses, all literal translation keys resolve, the language file contains no harness diagnostic strings, six Mixin entries are configured, and active/VCS version remains 1.21.11. These checks do not validate Gradle Kotlin configuration, release remapping, actual Mixin application or runtime screenshots; parent production boots and all 25 flows remain required.

## Run 05

Working-tree edits only; no git, Gradle, Minecraft, server or network execution. Fabric 1.21.11 remains active. Forge, legacy and dev-catalog arms remain commented; new comments inside an inactive arm use Stonecutter's escaped block-comment syntax.

| Item | Status | Change / remaining check |
| --- | --- | --- |
| Cloth sample picker and actions | FIXED | Forge 1.21.1 now uses custom Cloth entries with thumbnails, names and real Equip buttons. Both Fabric versions mark current samples Equipped and disable their buttons; Forge does likewise. Choosing a Forge sample rebuilds held selectors so stale Style widgets cannot overwrite it. Save, Publish, Unpublish, Report and developer Previous/Next use plain buttons, without Boolean values, Reset controls or redundant action tooltips. Fabric already used custom entries for these actions. Shared Gallery actions were inspected and already use buttons. |
| Flows 08/13/14 (plus Save in 10) | FIXED | Flow 08 presses the sample row's actual Button on Cloth; native Forge retains its identified sample button. Publish/Unpublish and republish press the action row's Button and wait for the translated screen status. Save in flow 10 also presses the real action. Service reads still verify the resulting catalog/search data. Actual in-game flows remain for the parent. |
| Browse header / search / card titles | FIXED | Header buttons use font measurements and an 8-pixel edge inset. Link shortens to Link account, then Link on the smallest layouts; the full label is available as help. The title stays centered within the remaining space, with ellipsis when necessary. Search and its measured button share aligned edges and an 8-pixel gap. Card names end in an ellipsis, preferring a word boundary, and their tooltip contains the full title, source and author. |
| Physics wording | FIXED | Existing ids now display Use gravity direction, Lift when moving up/down and Sway when strafing. Toggles/sliders have translated help on Fabric, legacy Cloth and native Forge. |
| Other cape renderers | FIXED | Startup uses FabricLoader.isModLoaded or Forge ModList for capes, waveycapes and minecraftcapes. One warning is logged; our cape-layer override yields to the other renderer. Settings, persistence and website features remain usable. skinlayers3d does not trigger suppression. README documents the visible-cape interaction. Parent must test alongside each available mod, including Mixin application. |
| Unreachable / HTML / garbage service responses | FIXED | Legacy calls, catalog, authentication and image requests share bounded back-off by origin. Connection errors, 429/5xx and invalid successful bodies delay retries exponentially; Retry-After is capped at five minutes. Catalog transport also has independent injectable back-off and rejects invalid bodies before ETag caching. Reads are bounded to 8 MiB. JSON shape/API checks and PNG signature/header/decode checks reject proxy HTML and corrupt assets. Failed thumbnails wait for a new load instead of retrying on every rebuild. Errors stay in translated screen status; background failures do not log each frame. |
| Invalid Advanced service URL | FIXED | One validator accepts HTTP(S) URLs with a host and valid port, rejecting credentials, queries, fragments and malformed values. Cloth shows field validation; native Forge shows the translated validation message. Invalid edits keep the last valid setting, and invalid persisted values fall back to the default. |
| Texture thread / lifetime review | FIXED | GPU upload and release assert the render thread. Downloads and image validation use background pools; decoded validation images close with try-with-resources. Legacy metadata/removal callbacks now apply on the client thread. Replacement decode images close in finally; failed DynamicTexture construction/registration closes its owned image/texture; failed frame splitting releases earlier frames. Successful replacement releases the previous dynamic texture, while bundled resources remain owned by Minecraft. Removing remote references now releases their textures immediately. Existing leave/world-change cleanup releases remote/provider textures and removes simulation state. New site image requests deduplicate by identity and discard stale responses across replacement/world/config changes. Rejected local PNGs no longer decode every frame. |
| Visual/runtime confirmation | NEEDS-IN-GAME-CHECK | Parent should rerun all four flow sets, inspect 1280×720 scale 2 plus compact layouts, hover title/physics help, and confirm sample actions and statuses. Direct Java compilation cannot prove Mixin remapping/application, actual pose placement, third-party injection compatibility or GPU resource behavior. No headless GPU test was added because it would require starting Minecraft/GL. |

Pose review (both loaders; behavior is shared per Minecraft version):

| Player state | Result / guard |
| --- | --- |
| Elytra | Cape hidden using current equipment, independently of the last tick snapshot. 1.21.11 uses vanilla's equipment WINGS-layer predicate, including modded wing equipment; 1.21.1 uses vanilla's Elytra item predicate. Elytra texture selection remains separate. |
| Chestplate | Cape remains visible. Single-frame flat capes retain vanilla placement. Custom geometry keeps the legacy chest clearance; 1.21.11 uses vanilla's HUMANOID-layer predicate and exact armor offset (0, -0.053125, 0.06875). |
| No cape / cape option disabled | With no selected/provider/vanilla texture there is no custom draw. Equipped samples can render on a skin without an official cape. Disabling the cape model part hides it; local checks use the current client option, remote checks use the current player/render state. |
| Spectator | Hidden before resolution/drawing. |
| Invisible | Hidden before resolution/drawing. |
| Sleeping | Explicitly hidden to avoid drawing custom geometry through a bed. |
| Riding | Visible, inheriting the player's renderer pose and the posed model torso transform. No independent standing translation is applied. |
| Swimming | Visible, inheriting vanilla renderer swimming rotation and the model torso transform; cloth still receives swimming motion. |
| Crawling | Visible, inheriting the swimming/crawling renderer pose and torso attachment even when isSwimming is false. |
| Crouching | Custom geometry follows the posed torso instead of a separate fixed crouch offset/angle; vanilla flat capes retain their vanilla path. |

Placement evidence: inspected cached vanilla CapeLayer bytecode for both versions and 1.21.11 PlayerCapeModel/HumanoidModel. Vanilla's newer cape model attaches beneath the posed torso and checks actual equipment render layers. The custom path now applies that torso transform before submitting geometry. Each visible pose still needs the parent's screenshots, especially swimming/crawling, riding, crouching with armor and switching back to Bands.

Changed files (Java paths below are under `src/main/java/dev/quarkos/capes/`):

- UI/harness: `client/screen/{CapeScreen,ClothCapeScreen,ForgeCapeScreen,BrowseScreen,CapeBoard}.java`, `client/harness/FlowHarness.java`, `src/main/resources/assets/quarkos_capes/lang/en_us.json`.
- Compatibility/render: `QuarkosCapes.java`, `client/mixin/CapeLayerMixin.java`, `client/render/{CapeCompatibility,CapeDraw,CapeTextures,CapeBook}.java` (`CapeCompatibility` is new).
- Service/settings: `client/net/{CapeHttp,SiteApi,CatalogClient,ServiceBackoff,ServiceUrl}.java`, `client/local/ClientSettings.java` (`ServiceBackoff` and `ServiceUrl` are new).
- Tests/docs: `src/test/java/dev/quarkos/capes/CatalogClientTest.java`, `README.md`, this audit.

Validation: direct `javac --release 21` against cached dependencies passes for **Fabric / Forge × 1.21.1 / 1.21.11**, and for the **unprocessed active Fabric 1.21.11 sources**. Full compilations were followed by direct checks of the final changed sources; all generated source snapshots match the final working tree. Offline JUnit passes **57/57 on each of the four targets** (228 successful executions; zero failures/skips). Four new regressions cover transport failure/back-off growth/recovery, HTML/garbage/invalid catalog bodies and ETag rejection, origin-wide delays across endpoints with Retry-After, and invalid service URLs preserving the last valid value. The existing write-transport test now returns the valid wearing fixture required by the response contract. Language JSON parses without duplicate keys; all literal translation keys resolve; active version remains 1.21.11.

Direct runners: `/tmp/capes_run05_{compile,active,tests}_runner.py`; final-source refresh/check runner: `/tmp/capes_run05_finish_check.py`. Classes, per-target arguments and logs are under `/tmp/capes-run05/`. Top-level logs: `/tmp/capes-run05-final-compile.log`, `/tmp/capes-run05-active.log`, `/tmp/capes-run05-final-update.log`, `/tmp/capes-run05-service-update.log`, `/tmp/capes-run05-final-tests.log`; the final active-source service update is also recorded in `/tmp/capes-run05/active-fabric/final-service.log`. Parent still owns actual Gradle/Stonecutter builds, release archive checks, all in-game flows, third-party mod launches and screenshot/GPU review. No runtime pass is claimed here.

## Run 06 — settings state sync and final release audit

| Item | Status | Evidence / result |
| --- | --- | --- |
| Browse Offline CTA restores stale Offline on Back / Save | FIXED | The current Fabric Cloth code unconditionally copied all entries during rendering and also ran unconditional Boolean save consumers. Forge 1.21.1 Cloth had the same render-time vulnerability and a direct service-URL save consumer. This is a general Cloth state-ownership problem, not a Fabric 1.21.11 API distinction. Added MC-free `client/screen/SettingsEdits.java`: snapshot widget values at screen creation, apply only subsequent changes, consume each change once. Both Cloth implementations use it for style, animation, preview direction, every physics field, provider switches, Offline, Show others, elytra and service URL. Unchanged widgets cannot overwrite settings/capes changed by Browse/Gallery Go online, Wear/Unwear, Link, sample Equip or harness code. Explicit Save and Save & Quit flush pending entry edits through the same tracker; unconditional Cloth save consumers were removed. |
| Native Forge 1.21.11 parity | FIXED | Audited `client/screen/ForgeCapeScreen.java`: toggle/choice/button/slider callbacks write only on input; the service responder is installed after its initial value; init/rebuild reads current settings. Save persists the current model without rereading old controls. No native rewrite was needed. Existing child-return refresh behavior remains; the new Cloth tracker also protects retained screens that are not rebuilt. |
| Settings regression coverage | FIXED | `SettingsEditsTest.browseGoOnlineSurvivesReturningRenderingAndSaving`, `appliedEditsAreNotReplayedOverLaterExternalChanges`, `changingOneFieldPreservesOtherExternallyChangedFields`, `unchangedStringsUseValueEqualityAndFreshScreensStartClean`. These tests have no Minecraft dependency and cover an external change after a previously applied user edit, editing back to the original value, and preserving untouched physics fields. Live flow 25 remains a parent check. |
| Packaged service default | FIXED | `gradle/release.gradle.kts` now generates `quarkos_capes-service.properties` from `-PserviceUrl=` for all nodes. `CapeCalls.DEFAULT_SERVICE_URL` reads it; `DEFAULT_BASE`, ClientSettings and fallback request planning agree. Source-only development fallback is still localhost. `verifyReleaseJar` reads each archive's actual resource, checks it matches the requested build value, rejects invalid/missing/stale defaults and loopback/example URLs unless `-PallowLocalService=true`. Numeric/expanded/mapped IPv6 loopbacks and numeric/octal IPv4 spellings are covered. A dev catalog build does not bypass the service guard. |
| LabyMod server availability | FIXED | Audit found Browse only reported `unavailable` and did not filter explicit disabled-source items. `CatalogClient.page` now excludes entries from sources marked disabled/unavailable; Browse clears old selection/detail generations on reload and displays disabled-source feedback. A disabled-source detail response fails without a stale cached success/direct fallback. Added `CatalogClientTest.disabledOrUnavailableSourcesNeverProduceBrowsableTextures` and `serverDisableDoesNotFallBackToCachedDetail`. Actual site disable, wearing/player-reference enforcement and already downloaded local textures still need parent observation. |
| Metadata / licenses | FIXED | Added Forge `displayTest=IGNORE_SERVER_VERSION` alongside existing `clientSideOnly=true`. Added top-level jar notices/full license texts under `src/main/resources/META-INF/licenses/`: Apache-2.0 for OkHttp/Okio; Cloth's original LGPL-3.0 text copied from the cached published Cloth jar plus incorporated GPL-3.0; original OkHttp Public Suffix List notice plus MPL-2.0. NOTICE identifies versions, authors and source locations. `verifyReleaseJar` requires all six notice/license files. Final expanded metadata and packaged/nested dependency inspection remain parent checks. |
| Release audit / privacy / store copy | FIXED | Added the single publication checklist `docs/RELEASE-CHECKLIST.md`, with DONE evidence, PARENT-CHECK tasks and EMILIO-DECISION blockers. Added factual `docs/PRIVACY.md` covering defaults, UUID/name/authentication, searches, upload/link/wear payloads, IP/provider exposure and local persistence. Store drafts correct the every-tab preview claim, identity/sharing claims and LabyMod sorting limits. README, DISTRIBUTION and CREDITS link/describe current release preparation. Production service URL, metadata contact placeholders and LabyMod legal/ToS clearance are Emilio decisions. |

Validation (no Gradle, Minecraft, network or git execution):

- Direct `javac --release 21` passes for Fabric / Forge × 1.21.1 / 1.21.11 and unprocessed active Fabric 1.21.11 sources. All **255** generated nonempty Java source snapshots match the final working tree. Stonecutter remains active Fabric 1.21.11; commented arms are preserved and no plain line comments were added inside versioned blocks. The parent's ClientHarness world-load tick fix was left intact.
- Direct JUnit passes **63/63 on every target and active src**: 315 successful executions, zero failures/skips. This includes four new settings tests and two new source-disable tests above, plus the existing 57 tests per target.
- Compiled the exact standalone Kotlin policy/archive-audit class from `gradle/release.gradle.kts` with the cached Kotlin compiler directly (no Gradle invocation). Offline policy tests passed: 16 loopback/example rejects with explicit local overrides, four accepted origin shapes, seven invalid URL rejects. Nine fixture jars verified normal/default override success, missing/stale resource failure, missing notice failure, developer code/translation rejection and developer-mode behavior. This validates guard logic; Gradle task/resource wiring remains unexecuted here.
- Fifteen Java subprocess probes passed across all four targets plus active src: a packaged production-shaped URL is read, missing/invalid resources use the development fallback, and the default alias, new ClientSettings and request fallback agree. No host was contacted.
- Language JSON parses without duplicate keys; the new source-disabled key exists; the in-mod icon is a valid 64×64 PNG. Store-size art is still a publishing decision.

Runners: `/tmp/capes_run06_{compile,active,tests}_runner.py`, `/tmp/capes_run06_active_tests_runner.py`, `/tmp/capes_run06_policy.py`, `/tmp/capes_run06_service_probe.py`. Generated sources/classes, compiler arguments and per-target logs: `/tmp/capes-run06/`. Aggregate evidence: `/tmp/capes-run06-compile.log`, `/tmp/capes-run06-active.log`, `/tmp/capes-run06-tests.log`, `/tmp/capes-run06-active-tests.log`, `/tmp/capes-run06-policy.log`, `/tmp/capes-run06-service-probe.log`.

**Parent follow-up:** use `docs/RELEASE-CHECKLIST.md` for final jar/metadata/license inspections, four flow runs, release boots, clean-config default checks, GUI/renderer observation and Emilio's decisions. Earlier build/prodClient/verifyReleaseJar commands in this audit now require **`-PallowLocalService=true`** while testing with localhost. Published builds must instead set **`-PserviceUrl=<real production URL>`** and verify without the local override. No production packaging or runtime pass is claimed by this run; additional version merges need separate validation.
