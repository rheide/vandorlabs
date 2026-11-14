# Glass, frame and porthole rendering checks

## In-game measurement

Use `/vandorglassprofile start 600` while holding the same camera and graphics
settings for both builds. The client writes JSON below `vandorlabs-profiles` in
its game directory after deferred GPU results arrive. `/vandorglassprofile status`
prints capture state and native/retained coverage with fallback reasons. The
`glasstmp2` baseline already contains this command. No startup flag is required
for the normal rendering optimizations.

The profile separates frame intervals, Forge render-event intervals, panes,
selected glass frames and porthole housing/pane submissions. A retained shadow
interval measures those submissions; it is **not the entire OptiFine shadow
pass**. Nested intervals overlap and must not be added. Reports include camera
and settings, GL renderer, vertex layout, coverage, sample limits and dropped
intervals. CPU submission time and GPU elapsed time are separate measurements;
neither is an FPS prediction by itself.

## Optional isolated Linux runtime

These sources exercise mapped Minecraft/Forge code in a real EGL OpenGL
compatibility context. Requirements are Java 8, Python 3, `gcc`, Linux
`libEGL.so.1` with a surfaceless compatibility implementation, and matching LWJGL
2 Linux native libraries. Build dependencies must already be available. The
runner downloads nothing and changes no system packages.

Export the project's mapped runtime classpath using Java 8:

```sh
./gradlew classes exportGlassCheckClasspath
```

For before/after comparisons, preserve the previous build's **deobfuscated
compiled classes** before compiling the changed sources. A distribution JAR has
obfuscated names and cannot replace mapped classes in this harness. Preserve the
source revision and class hashes too. The frame/porthole patch baseline is
`glasstmp2` commit `f494ecbc3db4a3efeeca6a26ece7fff73a6d0231`. It already includes
the earlier glass optimizations; comparing to public `master` would answer a
different question. Keep the standard mod JAR in `build/libs`.

Use an immutable snapshot of the current mapped classes if another process may
compile concurrently, and supply it with `--current-classes`. The runner excludes
both the ordinary build output and that override from dependency entries, so
current classes cannot silently fill missing classes in the baseline. Preserve
resources as well if a concurrent resource task can replace the exported paths.
Do not run other builds or GL probes during timing captures.

Every invocation uses the same Java home, native directory and runtime
classpath. For example:

```sh
python3 testclient/glass_egl/run.py \
  --java-home /path/to/jdk8 \
  --classpath-file build/glass-egl/runtime-classpath.txt \
  --natives /path/to/lwjgl2/linux-natives \
  --mode frames-pipeline
```

### Runtime and geometry modes

| Mode | Coverage |
| --- | --- |
| `pipeline` | Actual `RenderChunk` worker rebuild, extended state/model dispatch, VBO upload, native proof/admission, emissive lighting, joined interiors, holes/removal, cold frame registration, and one retained anchor for blended border frames. |
| `retained` | Actual pane manager/worker/VBO/admission lifecycle, stationary reuse, incompatible-format retirement/recovery and removal. The isolated JVM selects grouped panes. |
| `frames-pipeline` | All eight shipped partially transparent emissive front textures and their generated rail crops, with real mipmaps; palette/custom panes crossed with transparent/opaque finishes; animated panes and fluid fallback independent of frame grouping; independent frame/pane light and render passes; native-frame to retained-frame handoff and back; format, resource, light, relocation, recycled chunk, removal and world-unload cases. `--case handoff` runs only the attached-tile edit handoff. |
| `porthole-geometry` | Actual flat, full-depth, diagonal, bent and T-bar aperture geometry, all facings/depths/shapes, glimmer UVs, opaque/custom thickness and measured plane boundaries. Requires the classes produced by `compileNonRenderingChecks`. |
| `porthole-pipeline` | Real porthole worker/upload/admission, separate housing/pane anchors, palette/custom and opaque/transparent combinations, dynamic screen panes with retained housing, decoded animation, fluid admission, same-chunk anchor replacement and GL-resource lifetime. A pass-selection facade also checks dynamic shadow policy. Add `--frames-only` for the optional shadow policy in a separate JVM. |

`GlassAuthoredAtlas` loads shipped pixels and the actual generated rail sprites.
It generates and uploads four mip levels using Minecraft's sprite loader. These
checks do not substitute opaque white sprites for the emissive catalog.

### Representative before/after workloads

Run each before mode, then its after mode, with the same `--output-dir`. Before
requires `--baseline-classes /path/to/preserved/classes`.

| Pair | Fixed scene and measured path |
| --- | --- |
| `frames-before` / `frames-after` | 2,040 glass cells: eight joined 16×16 sections, one hole each, one actual emissive frame artwork per section. Both versions use retained panes so the comparison includes the previously remaining frame cost. The actual compiled tile lists and both pass 0 and pass 1 drive the real tile renderers. Baseline nonempty frame submissions are counted through its actual `Tessellator`; current retained frame and pane VBO draws are counted independently. |
| `portholes-before` / `portholes-after` | 2,048 actual joined portholes across eight 16×16 sections, all four aperture shapes and tint values, palette and static custom panes, and shipped housing textures. The comparison includes housing and aperture draws in both render passes. It distinguishes empty `Tessellator` calls from nonempty draw submissions. |
| `before` / `after` | Historical pane-focused fixture: 1,533 cells across overlapping planes and six section/plane groups. It exercises main-view pane collection/sorting/batching and retained groups, but does not establish emissive-frame or porthole housing cost. Use the representative pairs above for this patch. |

The representative workloads warm up and capture 80 frames per target. Separate
framebuffers select the production main and shadow branches. A GLSL 150 program
samples the actual atlas and lightmap. The after run compares its image to the
before PNG and requires no RGB difference above three levels, allowing bounded
8-bit alpha compensation rounding. Housing and aperture shapes, holes and
glimmer therefore remain part of the rendered comparison. JSON and PNG output
include actual admissions, pass setups, draw counts, image differences, raw
samples and mean/median/p95/p99/max CPU/GPU intervals.

`shader_facade` supplies only the optional pass-selection fields used by these
isolated fixtures. It is compiled into their own directories, never the mod JAR.
**It is not OptiFine** and does not augment the vertex writer to 56 bytes. These
benchmark JSON files explicitly record `actual_optifine=false`. Actual G5
writer/hook validation is a separate probe below.

## Actual OptiFine G5 binary probe

`run_optifine.py` uses a privately supplied official G5 patched client whose
classes and members have been mapped to this project's MCP names. It tests the
actual `BufferBuilder`, `SVertexFormat`, `SVertexBuilder` and `ShadersRender`
bytecode, without the pass-selection facade. It checks generated normal,
midpoint UV, tangent and entity records; the production retained merge; actual
VBO attribute rasterization; and shader program, framebuffer, blend, alpha and
depth preservation. See [the probe instructions](optifine/README.md) for local
binary preparation and the command.

The probe prints its exact binary hash and GL renderer. No OptiFine or Minecraft
binary is included in the repository or patch. It is an isolated binary/GL
compatibility check, not a full Forge world or shader-pack acceptance run.

## Interpreting results

Mesa software rendering is forced by default. `--hardware` permits the supplied
EGL implementation to choose another device; inspect the recorded renderer.
Software timings establish the behavior of these controlled workloads and their
submitted work, not the player's GPU cost or whole-world framerate. The tests
do not include complete world traversal, the user's world, or a real shader
pack's complete rendering passes.

The host pauses after each measured iteration to avoid outpacing the bounded
GPU-query ring: two milliseconds for the frame pair and eight for the more
heavily instrumented porthole pair, identical for both versions in each pair.
This pause belongs to frame-start
intervals, not draw CPU/GPU intervals. GPU timestamps are read only after their
availability reports completion. There is no `glFinish` in the measured loop;
image readback and final query draining occur outside it. A capture fails if
queries are unavailable or intervals are dropped.
