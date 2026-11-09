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
