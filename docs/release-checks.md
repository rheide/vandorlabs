# Running release checks

On Linux, run the standard release checks from the repository:

```sh
./tools/verify_release.sh
```

Use `./tools/verify_release.sh --check` to check dependencies without building or starting Minecraft. The script requires a Java 8 JDK, Python 3 with Pillow, and Xvfb. The existing client harness prepares its Forge runtime as needed.

Allow roughly an hour for the standard gate on a prepared runtime; downloads, graphics and hardware can change that duration. The console and retained log show each completed focus.

The script checks the texture browser, builds the standard JAR and runs `verifyRelease`: portable and non-rendering matrices, dedicated-server linkage, shader startup/reload, rendered regressions and the live gameplay/recovery fixtures. It then runs the isolated exact-JAR Dynmap test with WorldEdit and compares artifact hashes. Mekanism is required by this wrapper so its cold-restart fixture is included. It does not update the production server.

The default dependency location is a sibling `MC-Forge12-2` installation. Override these environment variables for another installation:

| Variable | Input |
| --- | --- |
| `JAVA_HOME` | Java 8 JDK |
| `VANDOR_LABS_RELEASE_SERVER` | Forge installation used only to locate dependencies |
| `VANDOR_LABS_COMPAT_MODS` | Installed compatibility-mod directory |
| `VANDOR_LABS_TEST_OPTIFINE` | OptiFine G5 JAR |
| `VANDOR_LABS_TEST_SHADERPACK` | Shader pack ZIP |
| `VANDOR_LABS_TEST_MEKANISM` | Mekanism JAR |
| `VANDOR_LABS_TEST_FORGE` | Forge 1.12.2 universal JAR |
| `VANDOR_LABS_TEST_DYNMAP` | Dynmap JAR |
| `VANDOR_LABS_TEST_WORLDEDIT` | WorldEdit 6.1.10 compatibility JAR |
| `VANDOR_LABS_TEST_HEAP` | Per-client heap, default `8G` |

The default shader ZIP belongs in the ignored runtime dependency cache at `testclient/runtime/shaderpacks/ComplementaryUnbound_r5.6.1.zip`; shader packs and third-party JARs are not distributed by this repository. `--help` lists the overrides.

Each invocation writes a separate directory under `build/release-checks/`, including the release log, Dynmap log and artifact hash. Captures and generated worlds remain available. Successful runs discard their copied mod installations and regenerable caches; set `VANDOR_LABS_KEEP_TEST_INSTALLS=1` to retain those too. Failed runs keep the full installation for debugging. Review or remove completed evidence directories periodically: screenshots and worlds can still accumulate.

This is the standard release gate, not every diagnostic or benchmark in the repository. Specialized previous-release native-world upgrades, abrupt-process-termination matrices and hardware shader/FPS acceptance remain additional checks selected for the changes being released. Windows uses `gradlew.bat verifyRelease` and the Windows client harness; this Bash wrapper is for Linux.

## 2.9 Door candidate checks

Changed player paths include Segmented Door placement, authored gate top selection, the dedicated dialog, smaller bending treads, Spiral pane rendering and ordinary/Large Reactor glazing. Run `segmented-door`, the Utility gate/artwork focuses and the optional Segmented render matrix before freezing the candidate. Exercise the installed building-wand and renderer integrations rather than relying only on synthetic geometry. Documentation gate captures use their own opt-in run and are excluded from release assertions.

`testSegmentedTreads` compares Java 8/21 clipping, closed alignment and moved-strip rays. The renderer checks all five tread sizes, travel directions and horizontal facings in vanilla and extended formats. Existing saves omit the new field and retain sixteen-pixel motion; the legacy settings golden remains unchanged. Settings transport appends one tread byte, with the previous 14/15-byte extensions still decoded. A three-channel full settings message grows from 39 to 40 bytes, with complete round-trip and malformed-length checks. Registry IDs, existing operation indices and failed-load snapshots retain their existing contracts.

The full Forge release gate and exact-JAR Dynmap check are separate from experimental native acceptance. Native gate and dialog checks cover authored bars, selected glazing, resource reload, reversed controls and bounded prepared rendering. They do not establish complete 1.21.1 feature parity or hardware shader performance.

## Ramp compiler and carried-track regressions

`testRampJit` drives the production ramp face emitter through C2 compilation and checks its vertex output. The release gate runs it on the selected Java 8 runtime. To test the legacy 8u51 compiler separately, set `VANDOR_LABS_TEST_JAVA8_51` to that runtime's Java executable while keeping Gradle on the normal Java 8 JDK. The retained `build/test-artifacts/ramp-jit/compilation.log` must show successful level-four compilation, without a skipped compile or malformed control flow. The old renderer triggered this compiler defect; this regression does not require a Minecraft window.

Carried-track collision snapshots follow the full tile writer because tracks have a distinct registry identity and topology despite inheriting the modular gear base class. `testGearCollisionDescription` checks that identity and complete tile data while retaining the arm fast-path's opaque capability and allocation assertions. Run `utility26` and `vehicle-taxi` for actual installation, motion and parking; run `ramp-axis` and `common-render` for ramp geometry, material mapping and rendering after face-emitter changes.

## Reactor glazing, Spiral layouts and the Segmented open stop

The focused `door-appearance` client with `VANDOR_REACTOR_GLASS_ONLY=true` checks ordinary and Large Reactor dialogs through actual picker clicks and packets, including native/general glass, tint, layout, Original, saved tiles and configured items. The artwork ID and Glass tab must survive every glass edit. The native Door dialog client additionally checks the Reactor selection before exercising the existing glass designs.

The `utility26` focus with `VANDOR_SEGMENTED_TILE_ONLY=true` captures a 3×3 Spiral assembly in all three layouts and checks actual atlas coordinates. Tile must differ from Stretch, and square Fit must match Stretch. `VANDOR_SEGMENTED_RENDER_MATRIX=true` adds the vanilla/extended record and tread matrices. The native gate render check exercises full-sheet Spiral Stretch, pane layering, reload and warmed allocations; the original PNG pixels remain unchanged.

`testSegmentedTreads` asserts that every tread size stops as the last tread completes the bend. The geometry reference retains exact closed and Straight records; perpendicular fully open records intentionally remove the previous half-block of extra travel for sixteen-pixel treads. Run `segmented-door` and `segmented-door-legacy` for interaction, reversal, reload and mining, plus dedicated linkage and the exact-JAR Dynmap check for the shared geometry paths.
