# Target feature coverage

Forge 1.12.2 remains the default and behavioral reference. NeoForge 1.21.1 is under conversion; no modern family is marked complete. A native registration, a successful build, or a shared fixture alone does not establish parity.

The [machine-readable inventory](feature-coverage.json) includes all 57 active generated-catalog registrations and the manually registered block/item families. Programmable-only textures, retired IDs and internal model templates are excluded from registered-block counts. Manual constructor designs, cockpit parts, gear, turntables, navigation tables and items are tracked explicitly.

| Family | NeoForge status | Remaining behavior and acceptance |
| --- | --- | --- |
| Programmable cube and matter block | partial | unregistered programmable_matter_block static adapter; joined multi-block Stretch/Fit faces; custom and filesystem materials; complete creative/search catalog configuration; rendered parity acceptance |
| Slabs and stairs | unimplemented | native collision/models; shared settings and per-face material rendering; configured items and placement; live parity |
| Walls, portholes and diagonals | unimplemented | all native adapters; connected and filled geometry/contact matrices; custom materials and configuration; live parity |
| Programmable viewscreens | partial | custom/filesystem material adapters and reload acceptance; vehicle overlay selections; complete original settings schema and all family variants; full-family rendered acceptance |
| Console, input and diagonal screen variants | unimplemented | all native block variants and surface mappings; shared dialog and input rows; light/fluid/controls; live parity |
| Glass, canopy and cockpit glass | unimplemented | native geometry/reservations; transparent and shader rendering; contact/collision and fluid behavior; live parity |
| Programmable and large doors | unimplemented | native moving geometry; shared saved timeline/channel policy; collision/ray targets and reversal; all authored door models and live parity |
| Trapdoors and diagonal trapdoors | unimplemented | native adapter and shared geometry; assembly/channel behavior; configuration/custom materials; live parity |
| Programmable lights and propulsion | unimplemented | native shape/light adapters; joined light/channel policy; particle and propulsion behavior; live parity |
| Chairs and seats | unimplemented | native shape/entity mounting; configured artwork; safe dismount and carried seats; live parity |
| Triggers, buttons, rockers, levers and throttles | unimplemented | native control adapters; shared latch/pulse/slider policy; directional interaction and saved levels; live parity |
| Wireless transmitter and receiver | partial | plain-item drop/pick/placement parity acceptance; carried assembly interaction |
| Token slots and design tokens | unimplemented | slot inventories and native token components; authorization semantics; all token designs/models; live parity |
| Programmable storage | unimplemented | inventory-preserving native menu/storage; face materials and storage model mapping; custom materials and configuration; live parity |
| Constructors, matter infuser, battery and tank | unimplemented | native multiblock assembly/inventory/fluid adapters; transaction and container semantics; all frame models and surface input; live parity |
| Navigation tables | unimplemented | native table rendering and input; shared navigation/display policy; custom display sources; live parity |
| Ramp controllers and moving endpoints | unimplemented | native world snapshot/movement adapters; transactional recovery and data retention; all six scan faces and travel modes; dedicated and live collision parity |
| Vehicles and controller/navigation blocks | unimplemented | native entity/world view and movement; shared transactional discovery/snapshot policy; parent-world carried controls; main-view/shader rendering and live parity |
| Cockpits and peripherals | unimplemented | native assembly/reservations and seating; six/three-row shared controls; square-pixel surface hit mapping; pause-aware motion and live parity |
| Modular landing gear and wheels | unimplemented | native parts and collision/placement; shared gear policy and carried animation; all item models with padding; live parity |
| Turntables | unimplemented | native controller/surface/entity; transactional assembly/snapshot recovery; geometry/channel behavior; live parity |
| Industrial table | unimplemented | native geometry and interactions; configured items and persistence; live parity |
| Configuration/copy tools and basic materials | partial | full Configurizer dispatch to every family; Duplifier connected copy/application; matter production/core crafting chain; all target-specific resources and live parity |
| Programmable armor | unimplemented | native equipment/components/models; shared material configuration; slot synchronization and all armor variants; live parity |
| Filesystem and custom material workflows | unimplemented | native resource discovery/reload; stable external material identities; shared metadata/picker policies; resource-pack parity and live reload |
| Compatibility integrations | unimplemented | native JEI creative/search enumeration; native WorldEdit rotation adapter; Dynmap or documented native replacement; shader format and main-view visibility acceptance |

## Current native adapters

The programmable cube has a native block entity, stable housing choices, per-face material selection, shared dialog controls, server-owned menu authorization, bounded settings transport, configuration-preserving drops and creative picks. Joined multi-block Stretch/Fit layouts and custom/filesystem materials remain required.

The programmable viewscreen uses the shared saved schema, display policy, compact animation decoder, pause-aware clock, housing/surface meshes and dialog controls. Native Redstone overlays and the eight-row editor use shared toggle/slider/pulse policy, partial-channel latch banks and native ray interaction. Native wireless endpoints use shared channel membership/network/signal policies, deferred loaded-world registration, directional analog/digital ports and exact editing sessions. Their remaining integration and rendering requirements are recorded above.

Basic matter/alloy ingots and the Configurizer item exist in the modern target. The full matter-production chain and every Configurizer target are separate unfinished work; these items do not imply those machines or workflows are available.

## Acceptance gates

Every family needs the same shared behavior on both targets, native persistence and permissions, a subsequent-process save/reload, native client/server round trips, and inspected geometry/dialog/animation output. Broad moving-assembly families additionally require transactional failure recovery, original-snapshot retention, loaded-chunk bounds and dedicated-server checks. External integrations need native equivalents or explicit unsupported status.

Run the root [build orchestrator](../../tools/build_all.py) for both independent Gradle builds and dual-JDK shared contracts. Preserve the existing Forge `verifyRelease` gate against the exact standard artifact. NeoForge startup, runtime, visual and full-family coverage checks remain distinct from compilation. The target is not release-ready while any required family remains partial or unimplemented.
