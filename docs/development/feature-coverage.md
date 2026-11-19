# Target feature coverage

Forge 1.12.2 remains the default and behavioral reference. NeoForge 1.21.1 integration is incremental; the native conversion remains incomplete and has no release-gate acceptance on `master`.

The [machine-readable inventory](feature-coverage.json) preserves historical implementation descriptions from `feature/neoforge-1.21.1` at `aa1477a3c08ab109b4bdfbada5a52ab82aeb9088`. Historical partial adapters and recorded checks belong to that branch. They do not establish integrated support or current-checkout acceptance, and no tests are claimed rerun by this inventory update.

Complete non-vehicle parity first, including standalone arms, ramps, Segmented Doors, Copy Controllers and turntables. Shared surfaces, snapshots and recovery remain in scope even where vehicle code also consumes them. Vehicle entities, driving, articulation and vehicle-specific carried/cockpit/gear integration are deferred. Seating, glass and navigation retain their standalone scope; their vehicle-specific acceptance follows later.

The source inventory includes 58 active generated-catalog registrations and explicit manual families. Programmable-only textures, retired entries and internal model templates are excluded from that count. Registry IDs may occur in multiple behavioral families: standalone arms and vehicle landing gear share parts. Counts are source inventory, not a runtime registry dump.

| Family | Integration on master | Historical native status | Scope |
| --- | --- | --- | --- |
| Programmable cube and matter block | partial (cube and plain matter) | partial | non-vehicle first |
| Slabs and stairs | partial | partial | non-vehicle first |
| Walls, portholes and diagonals | partial (all six wall/porthole/diagonal blocks enabled) | partial | non-vehicle first |
| Programmable viewscreens | partial (seven roles enabled; focused runtime checks pass) | partial | non-vehicle first |
| Console, input and diagonal screen variants | partial (seven roles enabled; focused runtime checks pass) | partial | non-vehicle first |
| Ordinary programmable Glass | partial (public Glass, dedicated editor and source-matched rendering enabled) | unimplemented | non-vehicle first |
| Canopy and cockpit glass | standalone Canopies enabled | canonical standalone family | Vehicle/carried integration deferred |
| Programmable and large doors (Segmented Doors tracked separately) | partial (ordinary and Large Doors enabled) | unimplemented | non-vehicle first |
| Trapdoors and diagonal trapdoors | partial (both enabled) | unimplemented | non-vehicle first |
| Programmable lights and propulsion | partial (Lights and standalone propulsion enabled) | unimplemented | non-vehicle first |
| Chairs and seats | partial (four stationary seats, native mounting and configured items enabled) | unimplemented | standalone first; carried/Vehicle seating deferred |
| Triggers, buttons, rockers, levers and throttles | partial (Trigger and mounted control families) | partial | non-vehicle first |
| Wireless transmitter and receiver | partial (handheld and world endpoints) | partial | non-vehicle first |
| Token slots and design tokens | partial (readers and Programmer enabled) | unimplemented | non-vehicle first |
| Programmable storage | partial (public inventory, dedicated editor and source-matched world/item models enabled) | unimplemented | non-vehicle first |
| Constructors, matter infuser, battery and tank | partial (three formed builders, Synthesis and Digitize, five Assembly Press recipes, eight Enclosed Gantry recipes, four standalone drives, both Navigation Computers, Matter Infuser, Battery and Hexagonal Fuel Tank) | unimplemented | non-vehicle first |
| Resource transfer ports | partial (Power, Fluid and Matter Ports enabled; focused transfer, UI and item ownership checks pass) | unimplemented | non-vehicle first |
| Navigation and observers | standalone flat/angled tables, Screen navigation, Survey Dishes and Sentinel Arrays with live sweeps | unimplemented | non-vehicle first |
| Ramp controllers and moving endpoints | partial (Controllers and Slabs enabled; bounded actor recovery) | unimplemented | non-vehicle first |
| Vehicles and controller/navigation blocks | pending | unimplemented | vehicle deferred |
| Cockpits and peripherals | stationary Cockpit seating, Advanced Pilot Chair armrest sliders, and Yoke and Screen Redstone; full Cockpit console controls pending | unimplemented | standalone first; carried integration deferred |
| Modular landing gear and wheels | partial (modular arms/feet, Track and stationary side wheels enabled; opt-in imported wheel and inventory conversion) | unimplemented | standalone first; vehicle effects deferred |
| Turntables | Programmable Surfaces public; opt-in block-only Scan, rendered rotation with swept collision, Redstone, upward-facing riders and quarter-turn Restore; captured interactions pending | unimplemented | non-vehicle first |
| Industrial Table and Programmable Pressure Plate | both enabled for stationary use | partial | non-vehicle first |
| Configuration/copy tools and basic materials | partial (materials and Duplifier enabled) | partial | non-vehicle first |
| Programmable armor | four pieces, crafting, role/company, general material and inventory sample picker, armor stands, equipped and inventory artwork, generic world-face sampling and Duplifier transfer. Special renderer samples pending | unimplemented | non-vehicle first |
| Filesystem and custom material workflows | partial (cube PNGs and samples) | partial | non-vehicle first |
| Compatibility integrations | native JEI, WorldEdit wedge rotations, selected resource ports and experimental static Dynmap renderers | unimplemented | non-vehicle first |
| Segmented Doors | partial (enabled; terrain handoff checked) | unimplemented | non-vehicle first |
| Copy Controllers and shared surfaces | public Surfaces and transactional Copy Controllers, including stationary Navigation Table groups, observer frames, passive Programmable Screen layouts in all seven roles and empty idle production builders | unimplemented | non-vehicle first |
| Standalone Extenders and rotating arms | partial (regular/Large joined and independent arms enabled) | unimplemented | non-vehicle first |
| Track assemblies | partial (canonical Track enabled; vehicle integration deferred) | unimplemented | non-vehicle first |

Historical partial work includes programmable cubes, slabs/stairs, flat walls/portholes, screens, mounted controls, wireless endpoints, Industrial Tables and filesystem materials. The JSON retains their branch-specific descriptions and remaining gaps under explicitly historical fields. Registration, compilation and shared fixtures alone do not establish parity.

Current master adds Segmented Doors, Copy Controllers, tracks and expanded standalone arm behavior beyond the historical inventory. Segmented Doors and the supported standalone arm family now have native adapters and focused acceptance; Tracks and stationary side wheels are public. The Turntable Controller is in development and the Copy Controller is public for supported targets. Turntables and Copy Controllers must share surface topology handling without stale drawing roots or continuous rescanning. Standalone arms require joined and independent assemblies, all mounting faces, materialized endpoint edits, client ownership, reversal and recovery coverage. Preserve current registry IDs, saved defaults and failed-load snapshots.

Every integrated family requires shared policy, native persistence and permissions, bounded server-authoritative round trips, subsequent-process reload, and inspected client geometry/dialog/animation output. Ownership transfers additionally require durable recovery and abrupt-restart/partial-save checks. Use focused Forge regressions as changes land; preserve the final exact-artifact release gate. Dual-JDK contracts and modern startup checks remain distinct from runtime and visual acceptance. Compatibility integrations need native equivalents or an explicit unsupported status.

See [the architecture and sequence](multiversion.md) for build isolation and ownership boundaries. The root build orchestrator runs the independent Java 8 and Java 21 targets with their own Gradle processes and collects standard artifacts. Shared contracts, target builds and focused runtime checks remain distinct; orchestration success does not establish complete family parity or a full release gate.

The integrated native material slice provides the plain matter block, matter/alloy ingots, three recipes and canonical artwork. Dedicated save/restart and live client placement/mining, emitted-model and item-padding checks cover this slice. These are development-runtime checks; exact packaged-JAR and hardware acceptance remain pending. The native programmable cube now uses the current shared housing and Appearance contracts, built-in material search, six face overrides, all three texture layouts and bounded joined images. Creative shift-click and the main-hand Configurizer share its dedicated authenticated menu. Actual native geometry and item transforms are compared with the current Forge exporter; native save/restart, configured placement/survival mining, GUI scales 2/3/320, cache bounds and resource/world unload are covered by focused checks.

Native cubes support installed filesystem PNG discovery at startup, stable saved IDs, known-file fallback and same-open-picker portrait/wide reloads with immutable world/item model snapshots. Separate clients cover startup-missing entries and rediscovery after restart. The cube portrait oracle remains a separate smoke-only geometry fixture.

Native cube inventory samples now use the shared grid without consuming items. Original vanilla material IDs resolve through actual legacy metadata normalization and Minecraft data fixers; new default-state samples use a fixed identity preimage domain, with all known collisions rejected. Door previews retain both halves. Dedicated restart, configured item/NBT, custom face/dormant overrides, survival menu input and immutable model reload checks remain distinct from exact packaged-JAR acceptance.

Optional component-renderer sampling, configuration dispatch to unconverted families, carried assemblies and the full production chain remain incomplete. Reloads do not discover new files.

The native Duplifier now registers its canonical tool, dedicated options menu, original shaped recipe and copied-settings recipe. Focused actual-player checks cover options transport, connected application, Undo and stale windows. Both vanilla crafting containers pass ordinary pickup and quick-move consumption with exact outputs and the complete tool remainder; loaded production-recipe replay passes all 6,076 baseline and 7,872 configured Source cases. Canopy player placement, shared settings and Undo have focused coverage. The native Ramp settings and crafting adapters are in place; combined player acceptance remains open. See the [crafting contract](native-copifier-crafting.md).

The current control increment adds the native Trigger and four handheld transmitter tiers. Dedicated save/restart and actual chunk unload/reload fixtures cover their channel delivery and saved configuration. Subsequent increments below cover mounted controls and world endpoints; additional inline source families remain incomplete. Focused client dialog, emitted-model/reload and ordinary interaction checks also pass. These are development-classpath/software-rendered results; exact packaged-JAR and hardware shader acceptance remain separate.

Native Push Buttons and Rocker Switches now share mount geometry, saved channel-bank policy and dialog rows with Forge. Their inline transmission editor is reusable across native dialogs. Focused checks cover actual Forge NBT inputs, partial latch banks, independent twenty-tick pulses, configured pick/drop/placement, tool requirements, loaded support checks and subsequent-process persistence. Physical neighbor callbacks observe settled banks, and actual world collision and selection use the configured mount.

Binary-control emitted geometry and item transforms match the actual Forge renderer across all mount choices and resource reloads. A separate fixture mod validates the exact standard native JAR through ModDev, including ordinary survival shift-click, Configurizer access, immediate inline edits, Done/Escape drafts, GUI scales 2/3 and installed-world rendering. This is software-rendered acceptance, not a standalone installer test or the full release gate. Copy-tool workflows and carried integration remain incomplete.

Native power levers reuse the binary-control host, dialog, activation and renderer. Both sizes retain canonical items and hidden legacy registry IDs. Focused checks cover 4,436 runtime cases and subsequent-process persistence, 8,192 world and 3,584 item model cases before and after reload, and exact-JAR real-player size/mount edits with unchanged tile identity and partial banks. The same frozen JAR passes the binary-control dialog regression. Authored float compatibility preserves Forge face shading; exact voxel unions use a bounded retained-byte cache. Throttles and sliders are covered by the subsequent four-step slice. These are focused ModDev/software results, not the full release gate.

Four-step throttles and Wall Sliders reuse the mounted host and renderer, with separate numeric detent policy and one logical support plate. Dedicated checks cover current Forge scalar inputs, partial banks, type changes that retain tile identity, configured items and subsequent-process persistence. Emitted world and item comparisons retain authored face shading, all six mount faces and saved rotations before and after reload. Copy-tool and carried integration remain outside this increment.

The four-step candidate also passes exact standard-JAR real-player dialog/world checks through ModDev: all three throttle styles, slider limits, reverse cycling, inline Enter versus Done/Escape, GUI resize, Configurizer and ordinary survival access. Power and binary-control dialog regressions pass on the same frozen candidate. Dedicated fixtures cover 20,421 write checks and restart, while actual Forge model references cover 12,288 world and 4,608 item cases before and after reload. These are focused software-rendered results; full release, standalone installer and hardware acceptance remain pending.

Native world transmitters and receivers retain the current `_block` registry IDs, six-face ports, analog/digital modes and plain ordinary pick/drop items. Dedicated save/restart checks include current Forge tags, closed/unloaded world refusal and physical observers during removal inside a signal batch. Mixed slider/rocker fixtures verify receiver maximum output while mounted controls retain their partial-bank minimum. Canonical cube models, arrow orientation, textures and padded item transforms use ordinary native models. Other source families, copy-tool and carried integration remain incomplete.

The endpoint editor reuses the inline transmission widget and integer slider. Exact standard-JAR checks cover ordinary and sneaking Configurizer access, all ports and ranges, receiver channels and immediate levels, invalid drafts, disabled analog On controls and repeated slider values. The binary-control dialog regression passes on the same candidate. These are focused ModDev/software checks; ordinary endpoint pick/drop remains unconfigured, and preview payloads do not establish configured placement.

Current slabs/stairs have registered placement, collision, configured item persistence and survival merge behavior. Their dedicated registration check covers fourteen states and sixty-one assertions. Material renderer and same-screen configuration are integrated; these registrations do not establish complete family parity or broader native client acceptance.

The native programmable plain wall is now registered with its shared editor, captured-material world/item renderer, configured items and bounded collision cache. Its dedicated registration check covers twenty-four states, real survival placement, local T-corner collision/rays and model-data publication. Frame atlas checks cover canonical crops and source-only resource-pack overrides. Porthole and diagonal preparation remains distinct from activation; their full family acceptance is pending.

Thin porthole and plain diagonal walls are now registered. Diagonal factory, collision, configured pick/loot, bare versus configured survival placement, coherent neighbour callbacks and failed-stage rollback are covered by the focused registration check. Exact geometry and local topology comparisons cover 3,648 current Forge profiles; the diagonal material comparison covers 1,824 configured draws. Porthole pane shader binding, palette pixels, playback and emitted records have focused checks; broader in-world visual acceptance remains deferred. Center-owned T bodies and panes share the current float transformation and cap kernels, including the saved exterior material. Full-depth and diagonal portholes and glass contacts remain incomplete.

Full-depth and diagonal portholes are now registered with shared appearance/pane controls, configured items and separate-process saved-world checks. Diagonal rendering includes aperture projection, connector arms and solid crossings, with chunk-boundary fallback through the same bounded panel bake. These focused checks establish persistence and source geometry contracts; broader client, resource-pack and shader acceptance remains pending. Diagonal glass is now registered with the same bounded panel bake and housing editor, including its current compact glass controls and three frame faces. Dedicated placement, factory/light policy and separate-process reload checks pass. Broader visual acceptance remains pending. The ordinary and Large programmable Doors and retained rotating/sliding aliases are registered with their shared renderer, item adapters and dedicated editor. Ordinary linked placement, dedicated editor transactions, paired credential ownership, opaque recovery items and moved-leaf targeting have focused runtime/client checks; Large structure, geometry and restart have separate coverage.

The ordinary Door editor has focused real-client coverage for Configurizer access, reverse cycling, invalid channel drafts, company artwork subgroups and independent frame/glass transactions. A separate survival-client focus exercises upper-leaf targeting beyond the original voxel and server-validated interaction with nearer-block occlusion. These checks use development-classpath software rendering and do not establish full world-artwork or standalone-release acceptance. The diagonal-glass recipe now uses its original enabled native programmable-glass ingredient.

The ordinary Door is now enabled in the native catalog. Its retained aliases share one tile/menu type and canonical configured pick/drop item. Authored full-height artwork, pane/rail materials, reversible motion, numeric/credential pads and item variants use shared source-verified backends. The canonical native player focus covers real placement, reader ownership and mining, with a renderer-disabled comparison proving visibility when only the upper body is in view across a vertical render-section boundary. Large placement, all-part ownership and reader timing, emitted previous-schema layouts, per-part outlet ownership and separate-process reload have canonical dedicated acceptance. A real-client focus covers outer-member editing, X/vertical motion, moved-leaf use, mining and upper-only rendering outside the anchor render section. Broader fluid/material/copy-tool and hardware acceptance remain incomplete.

Segmented Doors are enabled with canonical topology, authoritative controls, per-section editing, configured picks, stationary contained fluids and global moved-panel interactions. Source comparisons cover panel/window/header geometry, saved defaults, edit operations and sequenced clocks. Canonical dedicated restart and real-client acceptance cover survival placement, all direction/angle profiles, sneak/Configurator configuration, leading-client buckets, stale requests, section-boundary rendering and mining. Closed terrain-render handoff passes focused upload, fallback and pixel checks; broader workflows and native release acceptance remain pending. Regular and diagonal trapdoors are enabled with dedicated editing, moved-leaf interactions, source-matched formation, configured items and analytic contained-fluid rendering. Canonical dedicated/restart and real-client checks cover placement, actual menu payloads, picking and harvest. Broader material/copy workflows, hardware/shader rendering and final native release acceptance remain pending.


Programmable Screens now register all seven original roles with the preserved shared entity ID, dedicated editor, authored configured items and one delegating renderer binding. Shared activation, artwork migration, row banks and chip mounting preserve Forge behavior. Standalone capacities remain eight/four rows; cockpit capacities remain six/three and their Vehicle integration is deferred. Source comparisons cover fresh/loaded defaults, ordinary and CHIP geometry/rays, special body/deck artwork and whole-render joined journal placement. Failed snapshots retain their original data; public updates exclude physical chip stacks. Screens retain opaque saved fluid data while refusing waterlogging, matching Source.

Focused canonical checks pass actual Configurizer/ordinary editing, current/stale payloads, Creative/Survival middle-click, configured placement/harvest, inventory models and full process restart. Separate ordinary-media/ROWS and CHIP/ROWS placed-world checks use the actual shared renderer, with disabled-renderer negatives, Source culling, Survival controls, lighting and resource reload. Real Survival tray removal/insertion and joined page navigation also pass their packet and saved-world replay. See [canonical Screen acceptance](native-screen-canonical-checks.md) for measurements and limits. Broader copy-tool, optional-material, resource-pack and final native release acceptance remain pending; the full release gate remains deferred.


Programmable Light, Light Frame and Light Slab are enabled with a shared canonical entity ID, source-matched grouping, configured items and dedicated ON/OFF/Sides/Redstone editing. Focused canonical checks cover recipes, Survival interactions, exact saved owners, production item/world models, neighbor lighting, reload and recovery of failed component/attachment data. Standalone propulsion and particle families are also enabled: 27 block IDs, seven catalog items/recipes and 21 configured item variants. Focused canonical checks cover saved topology, Survival placement/harvest, client ON/OFF light publication, natural particle lifetime, world rendering and resource reload. Vehicle forces remain deferred. These focused checks do not establish the deferred full non-Vehicle release gate or hardware shader acceptance.

Standalone Card and Data Chip readers now register their original IDs and shared `token_slot` entity identity. The canonical dedicated/client checks cover actual placement, correct/wrong-tool and Creative mining, known/opaque ownership recovery, ordinary and Configurizer editing, private credential publication and saved restart. Source inventory/wrapper/hopper behavior and authored mounted models have separate exact references. Reader fluid material protection preserves credentials under buckets and actual water/lava flow; collision stays authored. The Card / Chip Programmer is now enabled with four-cell ownership, Program/Copy, dedicated Journal editing and authored docks/items. Canonical placement, exact Source harvest/contact cases, failed rollback/retry/restart and actual player/menu/render/reload checks pass. See the [Programmer contract](programmer-native-contract.md) and [construction guide](../card-chip-programmer-neoforge.md). Broader optional-mod and final native release acceptance remain pending. See [reader contract and acceptance](reader-native-render-contract.md).


Ordinary programmable Glass is now enabled with its canonical block/item/entity/menu identities, Source recipe, two-sided contained fluids, dedicated four-tab editor and source-matched chunk/pane/item rendering. Canonical focused checks cover 193 write/13 restart assertions, 52 real editor checks, actual L/T picks and filled-diagonal contacts, independent terrain/pane render negatives, all 304 padded item variants, tiny resource-pack overrides and reload/unload retirement. The standalone Canopy family is separately enabled; Vehicle/carried integration and broader native release acceptance remain deferred.

Native standalone arms now provide regular and Large Extenders, Rotating Arms and mounts with Plates, Skis and Wheels, six mounting faces, joined platforms, dedicated settings, configured items and Duplifier Undo. Focused player and restart checks cover native/captured ownership, active-foot adoption, captured mining, native riders and safe obstruction return. See the [native construction guide](../native-joined-arm-platforms.md). Horizontal bare Extenders support journaled contact pushing for independent and joined platforms. Tracks and stationary side wheels are public with authored geometry, settings copying/Undo and focused player/motion checks; older integrated landing gear and Vehicle Mode remain unavailable; unsupported vanilla endpoint orientations stay captured and selectable. This does not establish complete non-Vehicle conversion or a release gate.

## Three native builder machines

Synthesis Tower, Assembly Press and Enclosed Gantry now have native Core items
and the same frame layouts as Forge. The Synthesis Tower includes RF production,
its Battery Module and chip mounts, inventory dialog, physical item/RF ports,
and receiving-channel activation. Other Core multiblocks remain outside this slice. See the [native builder guide](../builders-1.21.md).

Focused checks cover all 48 design/facing/Core-corner layouts, durable Survival
mining, three production recipes, malformed-data retention, and abrupt formation,
mining and owned unformed-Core restart recovery. Connected-client checks verify
all 45 tower cells, part ownership, both dialog paths, physical inventory and the
actual world renderer. Separate software rendering covers all three full meshes,
fabrication, nearby culling, animation and resource reload. These are development
runtime/software checks, not a full release gate or hardware shader acceptance.

## Optional integrations

WorldEdit 7.3.8 uses the native propulsion orientation adapter for quarter
turns that change a wedge's block ID. Dedicated checks use a real clipboard
rotate and paste, and the mod also starts with WorldEdit absent.
[Native WorldEdit compatibility](native-worldedit-rotation.md)

Dynmap's official platforms currently exclude NeoForge. The experimental
upstream branch rendered default Programmable Surface and Copy Controller
textures from the native JAR. Configured and moving artwork still use the
Forge 1.12.2 renderers.
[Native fallback checks](native-dynmap-surface.md)
[Dynmap supported platforms](https://github.com/webbukkit/dynmap#what-platforms-are-supported) · [NeoForge port](https://github.com/webbukkit/dynmap/pull/4183)

## Native Matter Infuser

The Matter Infuser retains the Source iron-to-Matter recipe, catalyst durations,
solar pauses, sided automation and physical Battery Module charging. Synchronous
loaded-column sky admission prevents first-tick work beneath a newly placed roof;
transparent cover and rain retain the Source behavior. Typed native inventory
and legacy furnace NBT reads preserve failed snapshots inertly.

Focused dedicated checks cover processing, malformed data and a subsequent-process
partial-charge continuation. Actual Survival client checks cover placement, both
dialog paths, shift-clicking, mining and exact module ownership. Both targets
render the same shared standard Infuser panel, all 39 slots, branded header,
progress and sunlight states without furnace flames. Native acceptance remains
ModDev/software coverage, separate from a full native release gate.
