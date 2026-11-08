# Existing-world rendering comparison

`benchmark_world.sh JAR WORLD OUTPUT` copies a supplied save and its `mods/`
subfolder into a fresh disposable game directory. `OUTPUT` must be outside the
repository. Neither the save nor its screenshots are committed. It runs only
with an instrumented standard Vandor Labs JAR containing `WorldSceneBenchmark`.
The instrumentation is inactive during ordinary play.

The fixed camera presets cover the structure near x=14, y=69–84, z=70–130 from
the east, west and an elevated overview. Each camera waits at least 15 seconds
and 90 frames, requires the measured area to be loaded, and then records 240
rendered frames. Render distance is six chunks, FOV is 70, GUI and view bobbing
are disabled, and the player is a stationary spectator. Daylight, weather and
new mob spawning are held constant on the disposable copy; existing entities,
block entities and redstone continue running.

The CSV separates render submission wall time, completed render wall time,
render-thread CPU time and render-thread allocation. Completion uses `glFinish`;
the integrated-server column averages its tick-time ring buffer. These are
software-renderer measurements, not hardware FPS predictions. Warm-up and
loading are excluded; chunk-rebuild spikes are not characterized by this test.

All compared revisions must receive the same `WorldSceneBenchmark.java` and the
two opt-in `ReproLab` hooks, preserving their production renderer. Build the
standard JAR under Java 8. Keep baseline builds in their worktree `build/libs/`
directories and use the same launcher, supplied mods and graphics settings for
every run. Record the production commit separately from harness modifications.
Run clients sequentially and avoid concurrent builds during sampling. Confirm
the screenshots frame the intended structure and compare the block-count CSVs
before interpreting timings. Repeated runs are needed to assess variability.

Set `VANDOR_LABS_WORLD_RELOAD=true` to repeat each camera after a renderer reload
(the same rebuild used by F3+A). The second CSV row and screenshot have a
`_reloaded` suffix. Compare the structure before and after rebuilding to detect
stale geometry; moving entities, animation and clouds can change independently.

The comparison baselines are `release_1.3` (`9c433a63`) and the original
`1.4-alpha` tag (`2d18b157`), before diagonal chunk rendering. Identify newer comparisons by their version, commit and artifact hash.

## Large Vehicle Controller benchmark

`benchmark_large_vehicle.sh JAR WORLD OUTPUT` uses an extracted area containing
its original large controller assembly. Its opt-in preset visits the hall and
controller, converts the actual saved blocks to Taxi mode through
`VehicleService.screenMode`, boards the original Pilot Seat and uses normal forward/reverse input within a
short clear stretch. The integrated server must record at least half a block
of travel and five moving ticks during the sampling window.
The captured structure is retained as compressed NBT in the output, including
previous-version tile data. The input fixture is private and is never committed.

Package the copied world metadata, complete region files, dimension metadata,
`data/` and player data with the corresponding `mods/`, `config/` and `Flan/`
folders. Take that copy only after the source server has saved and stopped.
The launcher creates a fresh game directory and never operates the source server.
Provide `VANDOR_LABS_BENCH_LIBRARIES` when the modpack requires additional Forge
library JARs. Python's `nbtlib` is required to adjust only the disposable spawn.

`VANDOR_LABS_BENCH_OPTIFINE` and `VANDOR_LABS_BENCH_SHADERPACK` select the optional
renderer and shader archive. An adjacent shader `.zip.txt` settings file is copied
as well. `VANDOR_LABS_BENCH_COMPARE=true` adds a stationary reference stage with
the previous broad canopy collision search and cubic terrain grid, then enables
the conservative bounds and adaptive terrain sections
in the same loaded world. This switch is confined to the diagnostic process.
`VANDOR_LABS_BENCH_SAMPLES` and `VANDOR_LABS_BENCH_WARM_FRAMES` control sample and
minimum warm-up counts; `VANDOR_LABS_BENCH_MOVING_SAMPLES` can extend the
movement-only profile; every stage also waits at least eight seconds.

The CSV includes completed rendering, render-thread CPU and allocation,
integrated-server tick time, full frame intervals and measured craft travel.
Full intervals include client ticks between draws. They matter when a slow shader
frame causes several prediction ticks to run before the next draw. Record both
metrics; rendering time alone can miss that feedback. Retain the artifact hash,
matching pack/settings and screenshots. Run benchmarks sequentially without
concurrent builds. Mesa timings establish comparisons in this test environment;
they do not establish hardware FPS or owner acceptance of a shader pack.

## Custom camera and glazing controls

`-Dvandorlabs.worldBenchmarkCamera=x,y,z,yaw,pitch` replaces the default camera
sequence with one stationary view. The custom inventory and loaded-area checks
cover the full build height and an 80-block radius in X/Z.
`-Dvandorlabs.worldBenchmarkSamples=120` overrides the default 240 samples.
The JVM properties can be supplied through `JAVA_TOOL_OPTIONS` to the isolated
launcher. Keep the same copied region data, mods, settings and camera across runs.

`-Dvandorlabs.benchmarkLegacyGlazing=true` restores the previous overlap-sorting
and empty-frame submission behavior for a diagnostic baseline. It defaults off.
`-Dvandorlabs.worldBenchmarkOmitGlazing=true` omits Programmable Glass tile
rendering and fixed-canopy translucent chunk geometry **only while the world
benchmark is enabled**. This changes the image and is a diagnostic cost bound,
not an optimization or a valid visual-parity comparison. Glazing graph diagnostics
are printed alongside the completed camera measurements.
