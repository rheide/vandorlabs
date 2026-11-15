# Running release checks

Start release review by **collecting failures, not by fixing the first failure**:

```sh
./build.sh
./tools/verify_release.sh --jar build/libs/vandorlabs-2.10.0.jar --jobs 2
```

`--jar` freezes an existing standard artifact and never rebuilds it. Without that option, the helper assembles once before starting validation. It records the SHA-256, source-check logs, per-focus logs and a merged `report.json` under `build/release-checks/`.

The first pass runs the full CPU/non-rendering/diagonal/shader checks with Gradle `--continue`, then runs all required live focuses even if independent CPU checks failed. Live save/recovery, arms and vehicle checks are prioritized. Two isolated game/world/output lanes run functional checks concurrently; timing benchmarks have exclusive access. A multi-phase restart/recovery test remains one sequential group. The artifact must stay unchanged throughout.

**Do not edit packaged code or rebuild after each failure during this pass.** Finish the inventory, then batch coherent fixes: data loss/duplication/crashes, gameplay, rendering/performance, then stale fixtures. Fix a broken launcher or mixed artifact hashes before continuing, because that invalidates results. Merely failing a gameplay assertion does not prevent independent checks from running.

After a batch, build the new candidate once and rerun the reported failed checks:

```sh
# Use the actual task names listed in report.json.
JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64 ./gradlew testNonRendering --continue --no-daemon
python3 tools/release_live_checks.py --jar build/libs/vandorlabs-2.10.0.jar \
    --focus arm-wall --focus vehicle-taxi --jobs 2 --report build/focused-release.json
```

Focused reports are explicitly marked targeted. **After fixes, rerun only failed and affected checks**, including applicable historical regressions; do not restart the whole suite. Reuse the baseline's passing checks only where code and relevant inputs remain unchanged. Record the old/new candidate hashes, the changed behavior and why each retest was selected. If shared infrastructure invalidates the original inventory, explain that before broadening the rerun. A clean initial inventory needs no rerun.

Release acceptance combines the complete baseline inventory with the justified repair results and requires no unresolved mandatory failures. Describe this evidence accurately; targeted reruns are not a fresh full-suite pass on the new JAR. Every individual run still uses one frozen artifact.

Use `--check` to validate dependencies without building or starting Minecraft. `--skip-dynmap` records an explicit omission of the separate Dynmap smoke test; otherwise that test includes WorldEdit and runs after the live lanes finish. Mekanism is required so the cold-restart group is included. The helper never deploys to a server.

A prepared full suite has historically taken roughly an hour serially. Two live lanes reduce independent runtime work; fixture failures, downloads, world setup and exclusive benchmarks can still dominate. The console reports completed focuses and the logs retain exact commands and hashes. Do not promise a completion time based only on the approximately 30-second compile/package step.

The Linux wrapper requires a Java 8 JDK, Python 3 with Pillow and Xvfb. The cross-platform live helper uses the configured Windows launcher on Windows. The existing `gradlew verifyRelease` task remains the Gradle all-green/publishing path and uses the same complete live coverage; prefer the shell helper for review because it also collects live results after CPU failures.

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

Each invocation writes a separate directory under `build/release-checks/`, including the release log, Dynmap log and artifact hash. Captures and generated worlds remain available. Successful runs discard their copied mod installations and regenerable caches; set `VANDOR_LABS_TEST_KEEP_ARTIFACTS=1` only when full runtimes must be retained. Failed runs keep the full installation for debugging. Review or remove completed evidence directories periodically: screenshots and worlds can still accumulate.

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

## Segmented panel collision

`VANDOR_SEGMENTED_COLLISIONS_ONLY=true` with the `utility26` focus checks actual server/client world collision and entity movement through retracted panels across a chunk boundary. Its server matrix covers 7,200 cases across all facings, travel directions, Straight/Perpendicular motion, tread sizes, placement depths and heights up to 32. It checks removal and verifies that repeated stationary queries do not rebuild geometry.

The native `test_segmented_world_client.py --xvfb --collisions-only` focus separately checks dedicated-server and client world collision and entity movement, including 2,400 fully open server cases. It does not claim the broader editor, rendering or player-interaction matrices. Shared Java 8/21 coverage checks that collision retains the panel/tread corners. Existing `segmented-door` and `segmented-door-legacy` focuses cover adjacent interactions, reversal, reload and mining; shared changes also require dedicated-server linkage.

Both adapters discover loaded owners beyond the vanilla block scan, including the longer straight-down reach of tall doors. Angled panels use at most one-pixel collision slices; aligned pieces merge. Per-pose snapshots use weak owner keys and retained-byte bounds, invalidate on geometry changes and retire on owner removal or unload. Authoritative world timelines drive collision; cosmetic interpolation remains separate.
