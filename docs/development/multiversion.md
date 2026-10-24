# Forge 1.12.2 and NeoForge 1.21.1 architecture

## Target and baseline

Forge 1.12.2 remains the default development and behavioral reference. Both versions belong in this repository on `master`; use short-lived feature branches that change shared behavior and both adapters together. Do not maintain permanent Minecraft-version branches that require repeated feature cherry-picks. Release tags identify the mod version and target; publish target-qualified artifacts only after their validation gates pass.

The starting implementation contains 800 Java files (83,689 lines) under `src/main/java`, plus an existing `src/portable/java` core. Existing portable code already covers animation timing/decoding, ramp and door geometry, channel lists, primitive persistence schemas and mesh construction. Most other classes depend directly or transitively on Minecraft. An absence of Minecraft imports alone does not establish portability: CockpitScreenLayout, for example, accepts CockpitData.Surface containing Minecraft vectors.

## Repository and builds

Retain the root Gradle 4.9/ForgeGradle 3 build and `src/main/java` as the Forge adapter to preserve existing tooling. Expand `src/portable/java` as the single Java 8-compatible behavior implementation, compiled by both targets. Add `platforms/neoforge-1.21.1` with an independently pinned modern Gradle wrapper, NeoForge dependency and Java 21 toolchain. Do not load the modern plugin into the legacy Gradle process. A root orchestration script selects the JDK for each process, runs both builds, fails on either failure and collects only standard mod jars into root `build/libs/` with distinct names. Default legacy commands continue to work.

Keep canonical catalogs, texture packs and generators in their existing locations. Generate target resource layouts rather than copy source artwork or hand-maintain a second catalog. Modern language JSON, recipes, loot tables, block states and metadata need target-specific transformation. Do not include legacy loader metadata or incompatible legacy model definitions blindly. Keep generated output and runtime worlds isolated per target.

## Ownership boundaries

| Shared implementation | Platform adapter |
| --- | --- |
| Settings, defaults, operation IDs, bounds and validation | Registry and lifecycle hooks |
| Dialog layout, option order, labels, cycling, scrolling and hit regions | Screen widgets, font measurement, drawing and input events |
| Geometry, collision intent, animation timelines and mesh descriptions | AABB/VoxelShape conversion, tessellation, buffers and render passes |
| Channel/latch state machines and consumer policy | Loaded world lookup, server scheduling and notifications |
| Primitive saved schemas and migration decisions | NBT, item components and registry-aware codecs |
| Commands and authorization policy | Payload codecs, menus, player identity and transport |
| Assembly traversal and transactional movement policy | Block/entity access, chunk lifecycle, collision and mounting |

Use small domain-specific interfaces at real world/render/storage boundaries; do not emulate the entire Minecraft API. Shared code must have no Minecraft, Forge, NeoForge or LWJGL dependencies. Preserve package names where practical during extraction. Guard this boundary with compilation under Java 8 and Java 21 and a forbidden-dependency check.

## Behavioral parity

The same feature must execute the same shared policy on both targets. Extract existing behavior before adding its modern adapter. Preserve registry IDs, saved defaults, dialog operation indices, left/right cycling, wheel/drag scrolling, six/three cockpit rows, hit mapping, per-channel levels and pause-aware animation. Platform drawing backends may differ; labels, layout, controls and interaction results must match. Compare fixtures at identical logical GUI dimensions, including narrow windows and GUI scales.

Preserve server-thread execution, loaded-chunk limits, container/permission checks and bounded decode allocation. Modern payload transport is an adapter, not a reason to change permissions. Keep deferred loading, inert failed snapshots, original snapshot preservation, and parent-world carried-control authority. Keep collision and saved motion authoritative on the server. Do not promise binary packet compatibility between different Minecraft versions or automatic conversion of complete vanilla/modpack worlds; semantic mod data compatibility is the scope.

## Implementation sequence

1. Establish independently buildable targets, core dependency checks, one build command and a machine-readable feature coverage inventory. Retain the legacy release gate unchanged.
2. Extract genuinely portable classes and dialog layout/input policy, wiring legacy callers to the extracted implementation. Add shared contract fixtures that run under both JDKs.
3. Implement modern catalog registration and resource transformation, then real programmable block/screen/door vertical slices: persistence, server-authoritative settings, rendering, UI and interaction. A registered placeholder block does not count as a ported feature.
4. Migrate all building families, texture/configuration tools, screens, redstone, storage/constructors, security, gear and navigation through shared policy and target adapters. Track each family explicitly, including compatibility integrations.
5. Migrate moving assemblies, ramps, canopies, cockpits, vehicles and turntables with transactional snapshot/recovery and collision tests. These are high-risk API changes and require dedicated-server and live-client evidence.
6. Run full Forge verification against the exact standard jar, modern dedicated-server startup and focused live parity checks for every migrated family. Inspect dialogs and rendered geometry; compilation and registration counts alone cannot establish parity. Keep unsupported integrations and outstanding families visible; do not label the NeoForge build complete until coverage and runtime evidence support that claim.

Implement independent builds, shared behavior, and dialog extraction separately; integrate and test each feature family before expanding coverage. Run conversion tests against disposable worlds.

## Validation and release policy

Run shared fixtures on Java 8 and 21, the existing Forge dedicated-server linkage check, non-rendering matrices and relevant live client checks. Preserve `verifyRelease` before handing off a release jar. For NeoForge, use disposable worlds, startup and save/reload checks, GUI click/scroll tests, client/server round trips and geometry comparison. Require explicit per-family parity status; scaffolding is not full support. External integrations need native modern equivalents or documented unsupported status and must not silently alter core behavior.

## Platform references

- [NeoForge 1.21.1 setup and Java 21](https://docs.neoforged.net/docs/1.21.1/gettingstarted/)
- [NeoForge 1.21.1 screens](https://docs.neoforged.net/docs/1.21.1/gui/screens/)
- [NeoForge 1.21.1 payload networking](https://docs.neoforged.net/docs/1.21.1/networking/)
