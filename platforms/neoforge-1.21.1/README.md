# Experimental NeoForge 1.21.1 building slices

This independent Java 21 target provides the plain Programmable Matter Block, Programmable Matter Ingot and Industrial Alloy Ingot, with a creative tab, native models, loot, stone-or-better pickaxe harvesting, and the canonical block packing/unpacking and alloy recipes. The matter infuser and other acquisition systems are not implemented; matter ingots currently require creative mode or commands.

The plain Programmable Block now uses the current shared saved housing and dialog contracts. Its built-in materials, six face overrides, Stretch/Tile/Fit layouts, configuration-preserving item drops/picks and bounded joined images are supported. Creative shift-click and a main-hand Configurizer open the same dedicated dialog; survival and held-item Adventure permissions are enforced by the server. The Configurizer currently configures this cube only.

This remains a partial port, not a legacy world migration tool. Filesystem/custom material discovery, custom block sampling, copy tools, carried assemblies, other programmable families and the production chain remain deferred. Saved external material IDs are retained and unavailable artwork renders with a visible fallback; this does not implement the custom-material workflow. Do not open existing Vandor Labs worlds with this target.

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
