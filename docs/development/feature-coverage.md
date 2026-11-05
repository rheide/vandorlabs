# Target feature coverage

Forge 1.12.2 remains the default and behavioral reference. NeoForge 1.21.1 integration is incremental; no modern family is complete or release-ready on `master`.

The [machine-readable inventory](feature-coverage.json) preserves historical implementation descriptions from `feature/neoforge-1.21.1` at `aa1477a3c08ab109b4bdfbada5a52ab82aeb9088`. Historical partial adapters and recorded checks belong to that branch. They do not establish integrated support or current-checkout acceptance, and no tests are claimed rerun by this inventory update.

Complete non-vehicle parity first, including standalone arms, ramps, Segmented Doors, Copy Controllers and turntables. Shared surfaces, snapshots and recovery remain in scope even where vehicle code also consumes them. Vehicle entities, driving, articulation and vehicle-specific carried/cockpit/gear integration are deferred. Seating, glass and navigation retain their standalone scope; their vehicle-specific acceptance follows later.

The source inventory includes 58 active generated-catalog registrations and explicit manual families. Programmable-only textures, retired entries and internal model templates are excluded from that count. Registry IDs may occur in multiple behavioral families: standalone arms and vehicle landing gear share parts. Counts are source inventory, not a runtime registry dump.

| Family | Integration on master | Historical native status | Scope |
| --- | --- | --- | --- |
| Programmable cube and matter block | partial (cube and plain matter) | partial | non-vehicle first |
| Slabs and stairs | pending | partial | non-vehicle first |
| Walls, portholes and diagonals | pending | partial | non-vehicle first |
| Programmable viewscreens | pending | partial | non-vehicle first |
| Console, input and diagonal screen variants | pending | partial | non-vehicle first |
| Glass, canopy and cockpit glass | pending | unimplemented | non-vehicle first |
| Programmable and large doors (Segmented Doors tracked separately) | pending | unimplemented | non-vehicle first |
| Trapdoors and diagonal trapdoors | pending | unimplemented | non-vehicle first |
| Programmable lights and propulsion | pending | unimplemented | non-vehicle first |
| Chairs and seats | pending | unimplemented | non-vehicle first |
| Triggers, buttons, rockers, levers and throttles | partial (Trigger) | partial | non-vehicle first |
| Wireless transmitter and receiver | partial (handheld transmitter) | partial | non-vehicle first |
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

The current control increment adds the native Trigger and four handheld transmitter tiers. Dedicated save/restart and actual chunk unload/reload fixtures cover their channel delivery and saved configuration. Remaining mounted controls, world transmitter/receiver blocks, inline sources and latch families remain explicit work; this increment does not establish complete control-family parity. Focused client dialog, emitted-model/reload and ordinary interaction checks also pass. These are development-classpath/software-rendered results; exact packaged-JAR and hardware shader acceptance remain separate.
