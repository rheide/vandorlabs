# Glass pipeline checks and matched rendering comparison

The in-game check is `/vandorglassprofile start 600`. Hold the same camera and
graphics settings for both builds. The client writes JSON below
`vandorlabs-profiles` in its game directory after deferred GPU results arrive.
`/vandorglassprofile status` prints capture state and native/retained coverage
with fallback reasons. No startup flag is required.
The earlier JAR lacks this command. Use the same existing FPS/profiler capture
on both builds for the whole-world comparison, with this additional report on
the new build. A `legacy` option run is not an exact previous-JAR baseline.

The profile reports frame intervals, Forge render-event CPU/GPU intervals, and
retained pane main/shadow submissions. A retained shadow interval measures the
glass submissions only. It is **not the complete OptiFine shadow pass**. Nested
intervals overlap and must not be added. Reports include camera position and
orientation at the endpoints, settings, GL renderer, vertex layout, coverage,
sample limits and dropped intervals. CPU submission time and GPU elapsed time
are distinct; neither is an FPS prediction by itself.

## Optional isolated Linux runtime

These sources exercise actual mapped Minecraft/Forge render code in a real EGL
OpenGL compatibility context. Requirements are Java 8, Python 3, `gcc`, Linux
`libEGL.so.1` with a surfaceless compatibility implementation, and the matching
LWJGL 2 Linux native libraries. Build dependencies must already be available.
The runner downloads nothing and changes no system packages.

With the project's Java 8/Gradle environment, export its runtime classpath:

```sh
./gradlew classes exportGlassCheckClasspath
```

If comparing a previous version, preserve its **deobfuscated** compiled classes
before compiling the changed version. A distribution JAR has obfuscated names
and cannot replace mapped classes in this harness. Keep its source revision
alongside the preserved classes. The mod JAR remains in `build/libs`.

For each invocation below, supply the same Java home, native library directory,
and exported runtime classpath:

```sh
python3 testclient/glass_egl/run.py \
  --java-home /path/to/jdk8 \
  --classpath-file build/glass-egl/runtime-classpath.txt \
  --natives /path/to/lwjgl2/linux-natives \
  --mode pipeline
```

Modes:

| Mode | What it exercises |
| --- | --- |
| `pipeline` | Actual `RenderChunk` worker rebuild, extended block state and model dispatch, layer VBO upload, native proof and dispatcher admission. Includes joined emissive frames, ordinary pane lighting, holes, removal, stable stationary scenes, cold frame registration/upload ordering, and blended emissive border frames retained as fullbright frame-only TESRs. |
| `retained` | Production retained manager, actual chunk/frame upload, one anchor per coplanar section, compiled tile-list pruning, opaque-pass no-op, stationary VBO reuse, incompatible format retirement and recovery, and removal. The isolated JVM explicitly selects the grouped renderer to exercise fallback. |
| `before --baseline-classes /path/to/preserved/classes` | The previous build's real main-view collection/sorting/batching path and its direct per-tile shadow fallback, using the same fixed scene and camera as `after`. |
| `after` | The changed build's actual retained manager and anchor collection/sorting/draw path for the same scene. |

Run `before`, then `after`, with the same `--output-dir` (default
`build/test-artifacts/glass-performance`). Keep other builds and benchmarks idle.
To reproduce the native-coverage regression, `pipeline` also accepts
`--baseline-classes`; the previous build fails the native pane assertion for the
same emissive-frame scene that passes after the fix.
The benchmark uses 1,533 occupied cells across three overlapping planes, with
one aperture per plane, six section/plane groups, authored Gold artwork, and two
separate framebuffers. It warms up before measuring 120 frames per target.
The two targets select production main and shadow branches. Main-view tests
include the existing opaque-pass collection and translucent-pass ordering; the
baseline uses its existing batch and reports its actual draw counter. Retained
tests use the actual manager's six anchors. The benchmark uses
actual GLSL 150 shaders for atlas and lightmap sampling. It checks framebuffer
agreement within three RGB levels, preserving the aperture and glimmer while
allowing 8-bit alpha compensation rounding. Draw counts, raw samples and
mean/median/p95/p99/max CPU/GPU intervals are saved as JSON, with matching PNGs.

`shader_facade` provides only optional pass-selection fields for this isolated
benchmark. It is compiled into its own `build/glass-egl/before` or `after`
classpath, never the mod JAR or ordinary tests. **It is not OptiFine**, does not
emulate OptiFine's 56-byte vertex augmentation, and cannot establish shader-pack
compatibility. All benchmark JSON records `actual_optifine=false`. Unsupported
extended layouts are exercised as safe fallback in `retained`; their geometry
payload checks live separately in `testGlassPaneRetained`.

Mesa software rendering is forced by default. `--hardware` permits the supplied
EGL implementation to choose another device; inspect the recorded GL renderer.
Software GPU timings are useful for same-environment implementation comparisons,
not predictions for a player's GPU or complete Minecraft frame. The benchmark
does not include full-world traversal, actual OptiFine/Complementary passes, or
the user's world. The actual manager/dispatcher path is checked separately by
`pipeline` and `retained`, so a fast direct mesh benchmark alone is insufficient.

Each iteration has the same two-millisecond host pacing after the measured
intervals, keeping the synthetic producer from outrunning the bounded query
ring. This pacing is included in frame-start intervals, not draw CPU/GPU timing.
GPU timestamp pairs are read only after availability reports completion.
There is no `glFinish` in the measured loop. Image readback and final query
draining occur outside measured intervals. The timer ring is bounded, and a
benchmark fails if it drops intervals or cannot obtain GPU timings.
