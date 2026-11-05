# Multiversion development baseline

Forge 1.12.2 remains the default development target on `master`, at mod version 2.9. NeoForge 1.21.1 is experimental and incomplete. Continue ordinary 1.12.2 development here; extract common behavior into `src/portable/java` when it can preserve the existing behavior and costs. Keep platform lifecycle, rendering APIs, registries and transport in their adapters.

## Integrated scope

- Independent Java 8/Gradle 4.9 and Java 21/modern Gradle builds, portable dependency checks and dual-JDK contracts.
- Shared catalog/material identities, housing persistence, geometry policies, channel indices and latch policies.
- Shared dialog dimensions, picker input, inventory sampling, option rows, reverse cycling and explicit immediate-versus-confirmed edit behavior.
- Native matter/materials, programmable cubes and filesystem PNGs; Trigger and handheld transmitters; buttons, rockers, power levers, four-step throttles and Wall Sliders; directional wireless transmitter/receiver blocks.
- Current glass persistence and dialog policy, stair persistence and exact geometry, and slab placement/merge decisions. Native glass, slabs and stairs are still pending.
- A generation-wide bounded native mounted-model cache, and a regression baseline for the existing compact, lazy fluid geometry.

The [coverage inventory](feature-coverage.md) distinguishes integrated slices from complete family parity. The [architecture](multiversion.md) describes shared ownership boundaries. No automatic whole-world conversion or complete native compatibility is promised.

## Resuming native work

Start from `master`, not the old conversion branch. Use `feature/neoforge-1.21.1` as a historical reference; its slabs, walls, screens and fluid code predate current Forge behavior. Additional historical references are `wip/industrial-table-port` and `wip/wall-item-goldens`. Review and adapt their behavior rather than merging them wholesale.

The next building-family work is current slab/stair texture and UV policy, contained fluids, and then coherent native block/entity/model/dialog adapters. Preserve these audited distinctions:

- Slab item artwork ignores six-face overrides; stair items honor them and unrolled faces.
- A stair item with no `BlockEntityTag` differs from one with an empty tag in its layout fallback. Preserve typed raw item layout behavior separately from normalized world settings.
- Slab UP/DOWN facing uses the existing horizontal UV fallback; its housing stays horizontal. Reuse `ScreenHousingMesh` side crops and keep cube image-plane joining out of slab behavior.
- Current fluid geometry coalesces runs and defers face creation. The historical eager Cartesian implementation is not a suitable replacement. The experimental `wip/fluid-run-kernel-performance` branch is deferred because its generic extraction increased warmed allocation cost; it is not accepted production code.

Remaining large families include screens, walls, glass, doors, lights, storage, machines, navigation, tools, armor, compatibility adapters and standalone mechanisms. Arms, ramps, Segmented Doors, Copy Controllers and turntables remain part of non-vehicle parity, including their durable recovery requirements. Native vehicle driving, articulation and carried-specific behavior remain deferred until non-vehicle parity and its final gate are complete.

## Validation and release boundary

Focused checks cover actual legacy NBT, shared Java 8/21 contracts, dedicated-server linkage, current dialogs, exact emitted geometry and native persistence/restart. Native mounted controls and wireless dialogs additionally have exact standard-JAR ModDev interaction checks. Software rendering and ModDev checks do not establish hardware performance or standalone installer acceptance.

This stopping point freezes the integrated compatibility work for the Forge release gate; it does not mark native conversion complete. Before the gate, retain the focused checks for mounted dialogs, glass dialogs and stairs, plus the fluid compaction baseline and linkage/contracts. The full gate covers the existing rendering, fluid, mechanism, recovery-related gameplay and shader-startup fixtures on the standard Forge JAR.

Build Forge with Java 8 using the root wrapper. Build NeoForge with Java 21 using the wrapper in `platforms/neoforge-1.21.1`. Keep standard output in `build/libs/`; do not publish an original-textures artifact. A change to only the native adapter must not make the root Forge build depend on the modern Gradle plugin or JDK.

Release-gate status: **PASS** on 2026-11-05 against frozen runtime candidate `c0fa17f64`. The Java 8 `verifyRelease` run completed on its first attempt in 53 minutes, including the shader-startup and all configured live regression suites. Final standard Forge artifact: `vandorlabs-2.9.jar`, SHA-256 `56068103fb3ea1e343bd02f5b3a0b151d6cc73d11307ba7687c62fcc09cbdeef`. This establishes the Forge compatibility checkpoint; native 1.21.1 remains partial. Documentation updates after that candidate do not change its runtime artifact.
