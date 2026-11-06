# Target feature coverage

Forge 1.12.2 remains the default and behavioral reference. NeoForge 1.21.1 integration is incremental; no modern family is complete or release-ready on `master`.

The [machine-readable inventory](feature-coverage.json) preserves historical implementation descriptions from `feature/neoforge-1.21.1` at `aa1477a3c08ab109b4bdfbada5a52ab82aeb9088`. Historical partial adapters and recorded checks belong to that branch. They do not establish integrated support or current-checkout acceptance, and no tests are claimed rerun by this inventory update.

Complete non-vehicle parity first, including standalone arms, ramps, Segmented Doors, Copy Controllers and turntables. Shared surfaces, snapshots and recovery remain in scope even where vehicle code also consumes them. Vehicle entities, driving, articulation and vehicle-specific carried/cockpit/gear integration are deferred. Seating, glass and navigation retain their standalone scope; their vehicle-specific acceptance follows later.

The source inventory includes 58 active generated-catalog registrations and explicit manual families. Programmable-only textures, retired entries and internal model templates are excluded from that count. Registry IDs may occur in multiple behavioral families: standalone arms and vehicle landing gear share parts. Counts are source inventory, not a runtime registry dump.

| Family | Integration on master | Historical native status | Scope |
| --- | --- | --- | --- |
| Programmable cube and matter block | partial (cube and plain matter) | partial | non-vehicle first |
| Slabs and stairs | partial | partial | non-vehicle first |
| Walls, portholes and diagonals | partial (all six wall/porthole/diagonal blocks enabled) | partial | non-vehicle first |
| Programmable viewscreens | pending | partial | non-vehicle first |
| Console, input and diagonal screen variants | pending | partial | non-vehicle first |
| Glass, canopy and cockpit glass | pending | unimplemented | non-vehicle first |
| Programmable and large doors (Segmented Doors tracked separately) | partial (ordinary Door enabled; Large activation pending) | unimplemented | non-vehicle first |
| Trapdoors and diagonal trapdoors | pending | unimplemented | non-vehicle first |
| Programmable lights and propulsion | pending | unimplemented | non-vehicle first |
| Chairs and seats | pending | unimplemented | non-vehicle first |
| Triggers, buttons, rockers, levers and throttles | partial (Trigger and mounted control families) | partial | non-vehicle first |
| Wireless transmitter and receiver | partial (handheld and world endpoints) | partial | non-vehicle first |
| Token slots and design tokens | pending | unimplemented | non-vehicle first |
| Programmable storage | pending | unimplemented | non-vehicle first |
| Constructors, matter infuser, battery and tank | pending | unimplemented | non-vehicle first |
| Navigation tables | pending | unimplemented | non-vehicle first |
| Ramp controllers and moving endpoints | pending | unimplemented | non-vehicle first |
| Vehicles and controller/navigation blocks | pending | unimplemented | vehicle deferred |
| Cockpits and peripherals | pending | unimplemented | standalone first; carried integration deferred |
| Modular landing gear and wheels | pending | unimplemented | standalone first; vehicle effects deferred |
| Turntables | pending | unimplemented | non-vehicle first |
| Industrial table | pending | partial | non-vehicle first |
| Configuration/copy tools and basic materials | partial (materials only) | partial | non-vehicle first |
| Programmable armor | pending | unimplemented | non-vehicle first |
| Filesystem and custom material workflows | partial (cube PNGs and samples) | partial | non-vehicle first |
| Compatibility integrations | pending | unimplemented | non-vehicle first |
| Segmented Doors | pending | unimplemented | non-vehicle first |
| Copy Controllers and shared surfaces | pending | unimplemented | non-vehicle first |
| Standalone Extenders and rotating arms | pending | unimplemented | non-vehicle first |
| Track assemblies | pending | unimplemented | non-vehicle first |

Historical partial work includes programmable cubes, slabs/stairs, flat walls/portholes, screens, mounted controls, wireless endpoints, Industrial Tables and filesystem materials. The JSON retains their branch-specific descriptions and remaining gaps under explicitly historical fields. Registration, compilation and shared fixtures alone do not establish parity.

Current master adds Segmented Doors, Copy Controllers, tracks and expanded standalone arm behavior beyond the historical inventory. These require native implementations and acceptance against current Forge behavior. Turntables and Copy Controllers must share surface topology handling without stale drawing roots or continuous rescanning. Standalone arms require joined and independent assemblies, all mounting faces, materialized endpoint edits, client ownership, reversal and recovery coverage. Preserve current registry IDs, saved defaults and failed-load snapshots.

Every integrated family requires shared policy, native persistence and permissions, bounded server-authoritative round trips, subsequent-process reload, and inspected client geometry/dialog/animation output. Ownership transfers additionally require durable recovery and abrupt-restart/partial-save checks. Use focused Forge regressions as changes land; preserve the final exact-artifact release gate. Dual-JDK contracts and modern startup checks remain distinct from runtime and visual acceptance. Compatibility integrations need native equivalents or an explicit unsupported status.

See [the architecture and sequence](multiversion.md) for build isolation and ownership boundaries. The root build orchestrator runs the independent Java 8 and Java 21 targets with their own Gradle processes and collects standard artifacts. Shared contracts, target builds and focused runtime checks remain distinct; orchestration success does not establish complete family parity or a full release gate.

The integrated native material slice provides the plain matter block, matter/alloy ingots, three recipes and canonical artwork. Dedicated save/restart and live client placement/mining, emitted-model and item-padding checks cover this slice. These are development-runtime checks; exact packaged-JAR and hardware acceptance remain pending. The native programmable cube now uses the current shared housing and Appearance contracts, built-in material search, six face overrides, all three texture layouts and bounded joined images. Creative shift-click and the main-hand Configurizer share its dedicated authenticated menu. Actual native geometry and item transforms are compared with the current Forge exporter; native save/restart, configured placement/survival mining, GUI scales 2/3/320, cache bounds and resource/world unload are covered by focused checks.

Native cubes support installed filesystem PNG discovery at startup, stable saved IDs, known-file fallback and same-open-picker portrait/wide reloads with immutable world/item model snapshots. Separate clients cover startup-missing entries and rediscovery after restart. The cube portrait oracle remains a separate smoke-only geometry fixture.

Native cube inventory samples now use the shared grid without consuming items. Original vanilla material IDs resolve through actual legacy metadata normalization and Minecraft data fixers; new default-state samples use a fixed identity preimage domain, with all known collisions rejected. Door previews retain both halves. Dedicated restart, configured item/NBT, custom face/dormant overrides, survival menu input and immutable model reload checks remain distinct from exact packaged-JAR acceptance.

Optional component-renderer sampling, configuration dispatch to other families, copy tools, carried assemblies and the full production chain remain incomplete. Reloads do not discover new files.

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

Full-depth and diagonal portholes are now registered with shared appearance/pane controls, configured items and separate-process saved-world checks. Diagonal rendering includes aperture projection, connector arms and solid crossings, with chunk-boundary fallback through the same bounded panel bake. These focused checks establish persistence and source geometry contracts; broader client, resource-pack and shader acceptance remains pending. Diagonal glass is now registered with the same bounded panel bake and housing editor, including its current compact glass controls and three frame faces. Dedicated placement, factory/light policy and separate-process reload checks pass. Broader visual acceptance remains pending. The ordinary programmable Door and retained rotating/sliding aliases are registered with their renderer, item adapter and dedicated editor; Large activation remains pending. Ordinary linked placement, dedicated editor transactions, paired credential ownership, opaque recovery items and moved-leaf targeting have focused runtime/client checks; Large structure, geometry and restart have separate coverage.

The ordinary Door editor has focused real-client coverage for Configurizer access, reverse cycling, invalid channel drafts, company artwork subgroups and independent frame/glass transactions. A separate survival-client focus exercises upper-leaf targeting beyond the original voxel and server-validated interaction with nearer-block occlusion. These checks use development-classpath software rendering and do not establish full world-artwork or standalone-release acceptance. The diagonal-glass recipe waits for its original native programmable-glass ingredient; the ingredient has not been substituted.

The ordinary Door is now enabled in the native catalog. Its retained aliases share one tile/menu type and canonical configured pick/drop item. Authored full-height artwork, pane/rail materials, reversible motion, numeric/credential pads and item variants use shared source-verified backends. The canonical native player focus covers real placement, reader ownership and mining, with a renderer-disabled comparison proving visibility when only the upper body is in view across a vertical render-section boundary. Large rendering and controls are prepared; per-part outlet supply and final activation acceptance remain pending.
