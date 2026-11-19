# Experimental NeoForge 1.21.1 target

This independent Java 21 build includes the Matter Infuser, Synthesis Tower,
Assembly Press and Enclosed Gantry, standalone navigation and appliances,
programmable building families and controls, armor, Copy Controllers and
standalone arm and Ramp mechanisms. Current limits and ongoing work are in
the [feature inventory](../../docs/development/feature-coverage.md). Use a
new NeoForge world. Whole-world 1.12.2 migration and Vehicle Mode remain
outside this build's supported scope.

The Programmable Block supports built-in material choices, six face
overrides, Stretch/Tile/Fit layouts, joined images, configured item drops and
picks, and non-consuming inventory samples. Creative shift-click and a
main-hand Configurizer open the same dedicated dialog. Installed filesystem
PNGs preserve stable saved IDs and use fallback artwork when unavailable.

Run `python3 tools/build_all.py --target neoforge-1.21.1` from the repository root. Set `VANDOR_JAVA21_HOME` to a Java 21 JDK when automatic discovery does not find one. The orchestration script runs shared contracts on the selected target’s JDK (both JDKs when building both targets), then builds the selected target and places its standard JAR under the root `build/libs/`. Java 8 and Java 21 builds run in separate Gradle processes. The legacy root Gradle target remains independent.

`./gradlew runClient` and `./gradlew runServer` in this directory are development launch tasks; neither constitutes automated live acceptance. Use the builds on master and the focused family checks described below. The historical branch is an earlier conversion reference.


Resources are generated from existing canonical artwork, models, recipes and translations by `generate_matter_resources.py`; generated assets remain under `build/`. Runtime artwork uses the established 128-pixel resolution, including the compact alloy ingot icon. No duplicate artwork source is maintained here.

The development smoke source set is excluded from the production JAR. Its opt-in `matterPhase=write` and `matterPhase=read` server runs verify native recipes, harvesting tags and actual saved block/chest contents across process restart. `matterPhase=client` enables an isolated scene and native client model, placement/mining and screenshot checks. These checks use development launch classpaths; they do not establish exact-JAR launch, hardware FPS, or legacy upgrade acceptance.

Run `python3 platforms/neoforge-1.21.1/test_matter.py` from the repository root for the two-process native save/restart checks. Add `--client` for a real client; on a Linux host without a visible display add `--xvfb` (requires Xvfb and Pillow). The runner allocates disposable worlds and logs under `build/test-artifacts/`, checks each PASS marker and process exit, and retains the screenshot for inspection. It also checks all three native rendered item icons for visible slot padding and checks server observation of client placement/mining. Software rendering establishes this focused visual/interaction behavior, not hardware performance.


## Focused test artifact retention

Native Python launchers finalize generated game runtimes after the entire check completes, including all save/restart and client phases. Successful runtime payloads are removed. Top-level logs, reports and exact-JAR hashes remain; compact runtime logs, crash reports and screenshots are preserved under each run's `evidence/` directory and remain accessible at their original relative paths in thin evidence-only trees.

Failed runs retain at most two runtime payloads, limited to 256 MiB per run and 512 MiB in total. Older or oversized payloads are removed after bounded diagnostics are saved. Active processes and unfinished runs block cleanup. Set `VANDOR_LABS_TEST_KEEP_ARTIFACTS=1` before launching a check to retain its runtime explicitly; that choice also protects it from later automatic pruning.

New native launchers use `artifact_directory` inside a `@retain_artifacts` main function. Keep all restart phases inside that lifetime and use the common process launcher so child processes are tracked. Per-phase `run()` calls never finalize artifacts.

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

The native Trigger reuses shared saved-state and dialog rules, and the same material picker as the cube. Handheld transmitter sources remain stationary and dimension-local. Mounted buttons, rockers and power levers also support inline transmitter settings. World transmitter/receiver blocks are covered below; other control families remain pending.

Run `python3 platforms/neoforge-1.21.1/test_trigger.py` for server persistence and actual receiver/origin chunk unload, `test_transmitter.py` for tier recipes and three-process ON/reload/OFF persistence, and `test_trigger_client.py --xvfb` for actual dialog and handheld packets on Linux. Omit `--xvfb` for a normal client window. `test_trigger_models.py` covers emitted Trigger geometry, OFF item previews, particles and reloads; its optional Forge reference compares the existing cube geometry too.

These focused runners use disposable development-classpath instances. They do not establish the full release gate or hardware shader acceptance.

`test_transmitter_copy.py --jar build/libs/vandorlabs-neoforge-1.21.1-2.9.jar --client --xvfb` tests a previously built standard JAR in disposable dedicated/client instances with a separate fixture mod and empty source-mod folders. It verifies class provenance and the JAR hash, table pickup/shift crafting and cached invalid/valid input transitions. The runtime is launched through ModDev; this is exact-mod-JAR evidence, not standalone-installer or hardware acceptance.

The native copy recipe retains one primitive original-grid context field because vanilla trims empty grid borders before recipe matching. Measured warmed cropped-input construction increases from 488 to 496 bytes per input with this field. Only two-transmitter cache queries bypass the vanilla cache to retain position-sensitive matching; other recipes keep their normal cache path.

## Mounted controls

Push Buttons, Rocker Switches and both power-lever families share the mounted dialog, inline transmission editor, latch host and renderer. General edits commit on Done; valid inline transmission edits apply immediately. Power levers retain the hidden Compact and Large saved variants through their Size option and canonical configured items.

Run `test_power_levers.py` for dedicated persistence and channel behavior, `test_power_models.py` for emitted geometry/reload comparisons, and `test_binary_dialog.py --power-levers --jar <standard.jar> --xvfb` for real-player dialog and world acceptance. Run the same dialog runner without `--power-levers` for buttons and rockers. Runners are under this directory and use disposable fixtures. Exact-JAR client checks use ModDev and a separate fixture mod; they do not establish standalone installer, hardware shader or full release acceptance.

Four-step Thruster Levers and Wall Sliders extend the same mounted host. Thrusters select their normal, Airliner or Fighter style on General; their low/high intermediate limits are on Redstone. Done commits the family draft atomically, while inline transmission edits remain immediate. Run `test_signal_controls.py` for dedicated persistence and numeric banks, and add `--signal-controls` to the exact-JAR dialog runner for real-player edits.

## World wireless endpoints

Directional transmitters sample their selected adjacent input; receivers use the strongest selected channel signal. Analog mode retains numeric levels, while digital mode uses the configured threshold/output behavior. Receiver On/Off levels and port direction are immediate edits. Transmitters use the same inline channel/range/Local editor as mounted controls. Ordinary picks and drops are plain endpoint items, matching Forge.

`test_wireless.py --legacy-reference <reference.json>` covers dedicated runtime/restart, current Forge saved inputs, closed/unloaded-world guards, removal observers and mixed mounted banks. `test_wireless_models.py --xvfb` covers canonical faces, arrows and padded items across reload. Native binary payloads bound channel count to 64 before allocation; the old receiver text-payload character limit remains specific to Forge1.12.2. Full release and hardware acceptance remain separate.

## Navigation tables and survey observers

Native flat and angled Navigation Tables use the shared fixed physical pixel layout. Joining tables increases the visible map area. A group has one Chip holder; groups with at least two members have one low Navigation Data Module cassette bay. Module ownership stays on its original physical table cell as the visible bay changes. Removing the bay ejects the stored module once; unloaded or reserved cells defer topology changes.

Navigation uses the generator's biome source without requesting terrain chunks. Bounded server tick work produces cached rasters; repeated views reuse geographic samples and raster results. Pan and zoom animate the current geographic image while newly exposed regions pulse. Tables show player and saved-point markers, a center target and a recenter crosshair button. Existing chip-capable Programmable Screens and Consoles can select Navigation; unsupported Screen families keep their existing choices.

A Navigation Data Chip stores one dimension and exact block X/Y/Z coordinates. A scanned vanilla map additionally retains its original 128 × 128 color bytes, 256-entry ARGB palette and scale. Tables and supported Screens can display the exact saved square, including blank pixels, without an observer; ordinary point programming clears that image. Public table updates retain location/scale/hash metadata and omit the pixel and palette arrays. A Navigation Data Module records each linked source's complete circular coverage, binds to its first dimension and keeps up to 1,024 areas. Contained circles are deduplicated; a full module preserves old data. The saved item retains compact circles, while a bounded square index accelerates exact coverage lookup. Saved coverage remains usable without a linked observer. Public table updates contain a bounded dimension/count projection, while extraction, world saves and arm snapshots retain the full item.

Survey Dishes and Sentinel Arrays require complete frames and can form upright or upside down. Dish frames have equal width, depth and height, with widths 3–16. Arrays have equal width/depth and double height, with widths 2–8. The core can occupy any horizontal corner of the bottom or top layer. Scanning uses physical height, including carried arm transforms. At Y=64 every size scans a radius of 1,000 blocks for a Dish or 250 for an Array. At Y=128 the base sizes reach 2,000/500; size adds a linear bonus capped at double that radius when the width reaches twice the base width. Larger frames retain that cap. Transmitter range controls the separate connection to nearby displays. The dialog reports the actual footprint and current scanning radius. Stationary tables also show live sweeps over loaded surface colours at fixed radii of 256 blocks for the Dish and 128 for the Array. Legacy source block/item IDs resolve to the standalone models.

Native manufacturing currently supports the three existing builder cores plus the two observer cores in the Synthesis Tower. Observer recipes require a permitting Catalog Chip; the existing builder recipe indices remain unchanged. The Assembly Press exposes the Navigation Data Module recipe: one Programmable Matter Ingot and 8,000 RF, with a permitting Catalog Chip. See the [native builder guide](../../docs/builders-1.21.md) for the complete Press and Gantry recipe lists and the Tower Ramp Controller recipe. Flat tables use four Matter Ingots, two Glass and one Redstone; angled tables use three Matter Ingots, two Glass and one Redstone.

Build instructions and construction images are shared with [Navigation displays](../../docs/navigation-displays.md), the [Survey Dish](../../docs/multiblocks/standalone-dish/README.md) and the [Sentinel Array](../../docs/multiblocks/standalone-antenna/README.md).

The native Copy Controller supports one complete stationary Navigation Table group with its physical chip and module. Partial or neighboring groups are refused. Complete stationary Survey Dish and Sentinel Array frames are also supported with exact Core and Matter pricing. Regular stationary Programmable Screens support complete appearances and joins when they have no chip, private media or configured channel controls. Complete formed Synthesis Towers, Assembly Presses and Enclosed Gantries support stationary copying when idle, disconnected and empty. Native arm carrying has dedicated ownership adapters, including module/chip contents, physical orientation and mining receipts. Carrying retains the existing payload count and decoded-data budgets: a valid stationary frame can exceed an arm payload budget and is then refused before ownership changes. The maximum stationary frame size does not raise those global arm limits.

Focused native checks are `test_navigation_data.py`, `test_navigation_observers.py`, `test_navigation_arms.py` and `test_navigation_client.py --xvfb`. Their frozen-input manifests compare production runtime classes/resources byte for byte with the standard JAR. These ModDev runtime checks use separate fixture classes; standalone installer, full release and hardware shader acceptance remain separate.


## Air conditioners, Printer and Scanner

The four air conditioner presets are configured ordinary Programmable Block or horizontal Slab stacks. Their six 64 × 64 face textures, localized names and inventory previews survive placement, configuration, mining and picking; they do not add separate block IDs. Printer and Scanner keep their existing horizontal-facing block IDs and now own embedded inventory and controls.

Both machines have a standard Chip Mount at the upper left of the front and a half-size Push Button at the upper right. The Printer inventory dialog accepts ink sacs and paper. A successful print consumes one of each and retains the inserted chip. Journals become vanilla written books; Navigation Chips produce prefilled scale-zero vanilla maps using the navigation seed sampler and any imported pixels that cover the output. Seed sampling uses bounded server tick work without requesting terrain chunks. Catalog books contain complete construction instructions for the native builder and observer multiblocks that their chip permits. Unsupported chip types and refused output do not consume supplies.

The Scanner has an integrated upward frame, with no separate Item Frame entity. It accepts filled maps, written books and enchanted books; scanning retains the original and rewrites the inserted Data Chip. Maps retain their complete original pixel snapshot and dimension. Book text is flattened and bounded to eight 512-character journal entries. Enchanted books create Latin text marked for the enchanting font in read-only chip readers; ordinary journal editing clears that font choice.

World saves, extraction and supported arm snapshots retain full items. Public machine updates contain appearance and map IDs, with vanilla map packets hydrating tracked viewers. Unknown or malformed ownership data leaves the machine inert and preserves its original NBT. Focused checks are `test_appliance_products.py`, `test_appliance_client.py --xvfb` and `test_appliance_arms.py`; they compare the runtime production inputs with the standard JAR and keep restart phases in one isolated world.


## Synthesis Tower digitizing

The Production tab switches between Synthesize and Digitize. Digitize accepts one eligible held block through the formed enclosure, or through the input slot. It retains that original until 8,000 RF completes the reverse construction animation, then consumes it and replaces the inserted chip's Catalog recipe with that configured block. Removing or changing the input or chip cancels the job without consuming the original. Partial jobs retain their identity across saves; malformed job data leaves the original saved tile inert and intact.

Digitized recipes appear in the Tower's production list and in Catalog previews on supported Screens. Synthesis captures the selected recipe when work starts, so replacing the chip cannot change an active product. The four horizontal battery slots and player inventory appear on Production; Redstone retains its separate receiver controls.

Supported inputs include regular single-block vanilla models, stairs, slabs and trapdoors, plus the native programmable block, slab, Screen, Console, builder/observer core, propulsion and Ramp Controller families. Plants, logs, leaves, paired blocks and inventory or ownership-bearing data are refused. Empty regular-model containers can qualify; their contents cannot. Native Vehicle Controllers and drive-core families that are not registered by this platform remain unavailable. This does not add a native Vehicle runtime.

Run `test_digitize.py` for dedicated save/restart, admission, malformed-data retention, recipe immutability and durable mining checks. `test_builder_client.py --xvfb` exercises actual Survival enclosure interactions, both tabs, mode changes and reverse animation; `test_builder_render.py --xvfb` checks emitted geometry and resource reloads. Existing `test_builder_runtime.py` and `test_builder_recovery.py` cover adjacent formation, production and abrupt partial-save recovery. These focused checks use frozen production inputs matching the standard JAR; they are separate from a full release gate.
