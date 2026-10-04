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
`1.4-alpha` tag (`2d18b157`), before diagonal chunk rendering. Current development
uses version `1.4`; identify newer results by commit and artifact hash.
