# Experimental NeoForge 1.21.1 building slices

This independent Java 21 target provides the plain Programmable Matter Block, Programmable Matter Ingot and Industrial Alloy Ingot, with a creative tab, native models, loot, stone-or-better pickaxe harvesting, and the canonical block packing/unpacking and alloy recipes. The matter infuser and other acquisition systems are not implemented; matter ingots currently require creative mode or commands.

The plain Programmable Block now uses the current shared saved housing and dialog contracts. Its built-in materials, six face overrides, Stretch/Tile/Fit layouts, configuration-preserving item drops/picks and bounded joined images are supported. Creative shift-click and a main-hand Configurizer open the same dedicated dialog; survival and held-item Adventure permissions are enforced by the server. The Configurizer currently configures this cube only.

This remains a partial port, not a legacy world migration tool. Optional component-renderer sampling, copy tools, carried assemblies, other programmable families and the production chain remain deferred. Installed filesystem PNGs are supported on the native programmable cube. Saved external material IDs are retained and unavailable artwork renders with a visible fallback; the native cube also supports non-consuming inventory block samples with retained legacy vanilla aliases. Do not open existing Vandor Labs worlds with this target.

Run `python3 tools/build_all.py --target neoforge-1.21.1` from the repository root. Set `VANDOR_JAVA21_HOME` to a Java 21 JDK when automatic discovery does not find one. The orchestration script runs shared contracts on the selected target’s JDK (both JDKs when building both targets), then builds the selected target and places its standard JAR under the root `build/libs/`. Java 8 and Java 21 builds run in separate Gradle processes. The legacy root Gradle target remains independent.

`./gradlew runClient` and `./gradlew runServer` in this directory are development launch tasks; neither constitutes automated live acceptance. The historical feature branch contains broader adapters that require shared source extractions absent from the current portable tree. They are not imported here. Add adapters only with focused runtime checks and explicit coverage documentation.


Resources are generated from existing canonical artwork, models, recipes and translations by `generate_matter_resources.py`; generated assets remain under `build/`. Runtime artwork uses the established 128-pixel resolution, including the compact alloy ingot icon. No duplicate artwork source is maintained here.

The development smoke source set is excluded from the production JAR. Its opt-in `matterPhase=write` and `matterPhase=read` server runs verify native recipes, harvesting tags and actual saved block/chest contents across process restart. `matterPhase=client` enables an isolated scene and native client model, placement/mining and screenshot checks. These checks use development launch classpaths; they do not establish exact-JAR launch, hardware FPS, or legacy upgrade acceptance.

Run `python3 platforms/neoforge-1.21.1/test_matter.py` from the repository root for the two-process native save/restart checks. Add `--client` for a real client; on a Linux host without a visible display add `--xvfb` (requires Xvfb and Pillow). The runner allocates disposable worlds and logs under `build/test-artifacts/`, checks each PASS marker and process exit, and retains the screenshot for inspection. It also checks all three native rendered item icons for visible slot padding and checks server observation of client placement/mining. Software rendering establishes this focused visual/interaction behavior, not hardware performance.


## Native programmable cube checks

`generate_cube_resources.py` builds the native catalog and resources from the shared original finish list, current texture/menu metadata, canonical language and recipe files, and existing artwork. It preserves saved choice indices and current alias/detail redirects. No second artwork catalog is maintained.

Run `python3 platforms/neoforge-1.21.1/test_cube.py --client --xvfb` for isolated dedicated save/restart, authenticated native menu, real configured placement/survival mining and drop checks, renderer/cache lifecycle checks, and native GUI captures at scales 2/3 and a 320×240 logical viewport. Omit `--xvfb` for a visible desktop; omit `--client` for dedicated checks only. `--client-only` narrows renderer iteration without repeating already passing server matrices.

For an independent current-Forge geometry oracle, first run the legacy `cube-reference` client focus, then pass its exported JSON as `--reference <reference.json>`. The runner compares actual native emitted positions, normalized content UVs, materials, shading and GUI transforms with the exporter, in both traversal orders and again after resource reload. Its portrait artwork is a deterministic smoke-only asset; it does not establish external-file discovery, sampling or reload support. All smoke classes and artwork are excluded from the production JAR.

Joined traversal samples loaded native client worlds on the client thread and publishes immutable planes for section workers. It preserves the current centered Fit crop, a 256-member limit and 16×16 image spans. Worker requests carry positions and a generation token, with a bounded queue cleared on world unload. World-quad caches are bounded by estimated retained bytes, configured item models by a fixed geometry count, and visual-key projections use bounded weak identity keys so large opaque saved data is not retained or copied on every icon draw. Model replacement clears render caches on resource reload.

These are development-classpath checks with software rendering. They do not establish exact packaged-JAR startup, hardware FPS, shader-pack acceptance, production-world migration or complete family parity.


## Installed PNG materials on native cubes

The native cube uses the same [filesystem texture setup](../../docs/filesystem-textures.md) and shared startup discovery as Forge: categories under `config/vandorlabs/textures`, static PNGs up to 2048 pixels per dimension, path-based saved identifiers, and the same hidden example rule. Adding or renaming files requires a restart. F3+T rereads only files accepted at startup; it does not discover new entries.

A known file that disappears during reload uses the neutral dark fallback while retaining its picker row and saved ID. An ID absent from a client's startup catalog uses the default wall material; restoring that missing entry requires a restart. Already-open cube pickers retain their selected ID while thumbnails, world models and configured item previews adopt successful dimension changes after reload.

Resource loading pins dimensions per pack generation without retaining image caches. New model snapshots receive the matching atlas UV ranges; picker metadata publishes after atlas/model application. Old model and item UV records remain immutable. Filesystem catalog identities are persistent discovery entries rather than entries in the bounded transient native-asset cache.

Run `python3 platforms/neoforge-1.21.1/test_filesystem.py --client --xvfb` for the dedicated save/read/missing-file processes, installed-file reload matrix, real picker selection and scrolling, and separate missing-peer/restart clients. `--reference <reference.json>` also runs the independent current-Forge cube geometry/item oracle on the changed resource-bake path. The fixtures use their own PNGs and worlds under `build/test-artifacts/`. This establishes cube PNG behavior only; inventory block sampling uses the separate focus below. Other block families, shader packs and exact packaged-JAR runtime remain outside this slice.


## Inventory block samples

Open a cube with the main-hand Configurizer, scroll to **Custom...** at the end of the texture list, then choose a block from the inline inventory grid. Sampling does not consume or move the item. The same picker works for the main material and individual face overrides. Door thumbnails show upper and lower halves; cube faces use the lower artwork, matching the legacy cube. The header returns to the catalog, and compact grids scroll with the wheel.

Original vanilla saved IDs retain their legacy state interpretation. New samples use current named blocks and default properties with a fixed `flattened-block:` CRC preimage. Known ID collisions are refused; this is domain separation, not a collision-proof mathematical partition. An unavailable raw legacy integer cannot establish its original modded name. It is retained, and unresolved artwork falls back; explicit named provenance and optional component texture providers remain separate work. See [the identity and alias contract](../../docs/development/custom-material-aliases.md).

Run `python3 platforms/neoforge-1.21.1/test_sampled_materials.py --client --xvfb --reference <reference.json>` for dedicated save/restart, configured chest items, survival Configurizer inventory clicks, nested item data, custom face and dormant overrides, resize/back/wheel behavior and missing-source/restore reloads. It checks unchanged old model snapshots independently of the published GUI catalog and can run the independent current-Forge cube/item oracle. The worlds, resource-pack overrides and screenshots are disposable under `build/test-artifacts/`. These are development-classpath/software checks, not an exact-JAR release gate.

## Trigger and handheld transmitter checks

The native Trigger reuses shared saved-state and dialog rules, and the same material picker as the cube. Handheld transmitter sources remain stationary and dimension-local. Other control families, world transmitter/receiver blocks and inline transmitter settings are still pending.

Run `python3 platforms/neoforge-1.21.1/test_trigger.py` for server persistence and actual receiver/origin chunk unload, `test_transmitter.py` for tier recipes and three-process ON/reload/OFF persistence, and `test_trigger_client.py --xvfb` for actual dialog and handheld packets on Linux. Omit `--xvfb` for a normal client window. `test_trigger_models.py` covers emitted Trigger geometry, OFF item previews, particles and reloads; its optional Forge reference compares the existing cube geometry too.

These focused runners use disposable development-classpath instances. They do not establish the full release gate or hardware shader acceptance.

`test_transmitter_copy.py --jar build/libs/vandorlabs-neoforge-1.21.1-2.9.jar --client --xvfb` tests a previously built standard JAR in disposable dedicated/client instances with a separate fixture mod and empty source-mod folders. It verifies class provenance and the JAR hash, table pickup/shift crafting and cached invalid/valid input transitions. The runtime is launched through ModDev; this is exact-mod-JAR evidence, not standalone-installer or hardware acceptance.

The native copy recipe retains one primitive original-grid context field because vanilla trims empty grid borders before recipe matching. Measured warmed cropped-input construction increases from 488 to 496 bytes per input with this field. Only two-transmitter cache queries bypass the vanilla cache to retain position-sensitive matching; other recipes keep their normal cache path.
